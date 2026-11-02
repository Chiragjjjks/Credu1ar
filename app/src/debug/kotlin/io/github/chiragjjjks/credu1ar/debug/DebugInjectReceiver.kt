package io.github.chiragjjjks.credu1ar.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.sample.SampleData
import io.github.chiragjjjks.credu1ar.ingest.CatchUp
import io.github.chiragjjjks.credu1ar.ingest.ImportOutcome
import io.github.chiragjjjks.credu1ar.ingest.Ingest
import io.github.chiragjjjks.credu1ar.ingest.StatementImport
import io.github.chiragjjjks.credu1ar.ingest.SmsReceiver
import kotlin.concurrent.thread

/**
 * DEBUG ONLY (lives in src/debug). The emulator's `adb emu sms send` is unreliable on recent images, so tests and
 * screenshots drive the app through here instead:
 *
 *   adb shell am broadcast -a io.github.chiragjjjks.credu1ar.DEBUG_SMS -n io.github.chiragjjjks.credu1ar/.debug.DebugInjectReceiver \
 *       --es sender VM-HDFCBK-S --es body "Rs.450.00 debited from a/c **1234 ..."
 *
 * Extras: sender+body -> a real SMS_RECEIVED intent with genuine PDUs goes through the production [SmsReceiver];
 *         notif=true  -> same text as a notification signal;  sample=true -> load sample data;  sample=clear -> remove it;
 *         at=<epoch millis> (--el) -> the SMS/notification's own timestamp instead of now (date-independent tests);
 *         catchup=true -> run the inbox read (the first-link window if history was never read);  wipe=true -> delete everything;
 *         catchupfrom=<epoch millis> (--el) -> the "Add <month>" read, from that time to where history starts;
 *         pdftext=<file name in the app's files dir> -> dump what Android's PdfRenderer text API returns to files/<name>.txt;
 *         stmt=<file name in the app's files dir> [pw=<password>] [pipe=true] -> import it through StatementImport.run, exactly
 *             as a picked file, and write the counts (never text, amounts or the name) to files/stmt-result.txt and logcat.
 *             pipe=true serves the file through a pipe (DebugPipeProvider), which exercises the in-memory copy path;
 *         stmtdelete=<statement id> (--el) -> Repo.deleteStatement.
 */
class DebugInjectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        thread(name = "credu1ar-debug") {
            try {
                if (intent.getBooleanExtra("wipe", false)) Graph.repo(app).deleteEverything()
                when (intent.getStringExtra("sample")) {
                    "true" -> SampleData.signals(System.currentTimeMillis()).forEach { Ingest.handle(app, it, quiet = true) }
                    "clear" -> Graph.repo(app).clearSamples()
                }
                if (intent.getBooleanExtra("catchup", false)) CatchUp.scan(app)
                if (intent.hasExtra("catchupfrom")) CatchUp.scanFrom(app, intent.getLongExtra("catchupfrom", 0L))
                intent.getStringExtra("pdftext")?.let { dumpPdfText(app, it) }
                intent.getStringExtra("stmt")?.let { importStatement(app, it, intent.getStringExtra("pw"), intent.getBooleanExtra("pipe", false)) }
                if (intent.hasExtra("stmtdelete")) Graph.repo(app).deleteStatement(intent.getLongExtra("stmtdelete", -1))
                val sender = intent.getStringExtra("sender")
                val body = intent.getStringExtra("body")
                if (sender != null && body != null) {
                    val at = intent.getLongExtra("at", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                    if (intent.getBooleanExtra("notif", false)) {
                        Ingest.handle(app, RawSignal(Source.NOTIFICATION, sender, body, at, intent.getStringExtra("pkg")))
                    } else {
                        val sms = Intent("android.provider.Telephony.SMS_RECEIVED")
                            .putExtra("pdus", SmsPdu.deliverPdus(sender, body, at).toTypedArray())
                            .putExtra("format", "3gpp")
                        // Round-trip check: what does Android's own decoder make of our PDUs?
                        android.provider.Telephony.Sms.Intents.getMessagesFromIntent(sms)?.forEach {
                            android.util.Log.d("Credu1arDebug", "decoded from=${it.originatingAddress} ts=${it.timestampMillis} body=${it.messageBody}")
                        }
                        SmsReceiver().onReceive(app, sms) // the production receiver, unchanged
                        Thread.sleep(400)
                        android.util.Log.d("Credu1arDebug", "counts after: ${Graph.repo(app).counts()}")
                    }
                }
            } finally {
                pending?.finish()
            }
        }
    }

    /** Counts only: this line goes to logcat and to a file the smoke test reads. */
    private fun importStatement(app: Context, name: String, password: String?, pipe: Boolean) {
        val uri = if (pipe) DebugPipeProvider.uri(app, name) else android.net.Uri.fromFile(java.io.File(app.filesDir, name))
        val line = when (val o = StatementImport.run(app, listOf(uri), password)) {
            is ImportOutcome.NeedsPassword -> "outcome=needs_password wrong=${o.wrongPassword}"
            is ImportOutcome.Done -> o.files.joinToString(" | ") { f ->
                "outcome=done rows=${f.rows} added=${f.added} already=${f.alreadyHere} matched=${f.matchedExisting} complete=${f.complete} " +
                    "left_out=${f.leftOut} same_file=${f.sameFile} problem=${f.problem}"
            }
        }
        java.io.File(app.filesDir, "stmt-result.txt").writeText(line + "\n")
        android.util.Log.d("Credu1arDebug", "stmt $line")
    }

    /** What the platform text API gives us, page by page, with each content block's line boxes. API 35+. */
    private fun dumpPdfText(app: Context, name: String) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val file = java.io.File(app.filesDir, name)
        val out = StringBuilder()
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            android.graphics.pdf.PdfRenderer(fd).use { r ->
                for (i in 0 until r.pageCount) r.openPage(i).use { page ->
                    val contents = page.textContents
                    out.append("=== page ${i + 1} contents=${contents.size}\n")
                    contents.forEachIndexed { j, c ->
                        out.append("--- block $j boxes=${c.bounds.size} first=${c.bounds.firstOrNull()}\n")
                        out.append(c.text).append('\n')
                    }
                }
            }
        }
        java.io.File(app.filesDir, "$name.txt").writeText(out.toString())
        android.util.Log.d("Credu1arDebug", "pdftext wrote ${out.length} chars")
    }
}
