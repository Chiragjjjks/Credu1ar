package io.github.chiragjjjks.credu1ar.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.sample.SampleData
import io.github.chiragjjjks.credu1ar.ingest.CatchUp
import io.github.chiragjjjks.credu1ar.ingest.Ingest
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
 *         catchup=true -> run the inbox read (the first-link window if history was never read);  wipe=true -> delete everything.
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
                val sender = intent.getStringExtra("sender")
                val body = intent.getStringExtra("body")
                if (sender != null && body != null) {
                    if (intent.getBooleanExtra("notif", false)) {
                        Ingest.handle(app, RawSignal(Source.NOTIFICATION, sender, body, System.currentTimeMillis(), intent.getStringExtra("pkg")))
                    } else {
                        val sms = Intent("android.provider.Telephony.SMS_RECEIVED")
                            .putExtra("pdus", SmsPdu.deliverPdus(sender, body, System.currentTimeMillis()).toTypedArray())
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
}
