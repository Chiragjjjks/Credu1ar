package io.github.chiragjjjks.credu1ar.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source

/**
 * Reads the SMS inbox for anything newer than what we already saw. Runs on every app open, every few hours on an
 * SMS wake-up, and once for history right after onboarding. Duplicates are harmless: the inbox row id is a unique key.
 */
object CatchUp {
    private const val DAY = 24L * 3600 * 1000
    private const val DUE_AFTER = 6L * 3600 * 1000

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun runIfDue(context: Context) {
        val repo = Graph.repo(context)
        if (System.currentTimeMillis() - repo.metaLong("last_scan_at") > DUE_AFTER) scan(context)
    }

    /** @param historyDays how far back to look on the very first scan (or when [forceHistory] is set). Returns messages read. */
    fun scan(context: Context, historyDays: Int = 90, forceHistory: Boolean = false): Int {
        if (!hasPermission(context)) return 0
        val repo = Graph.repo(context)
        val now = System.currentTimeMillis()
        val last = repo.metaLong("last_scan_date")
        val since = if (forceHistory || last == 0L) now - historyDays * DAY else last - 60_000 // small overlap; the unique key dedups
        var newest = last
        var read = 0
        val cursor = runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
            )
        }.getOrNull() ?: return 0
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val date = c.getLong(3)
                Ingest.handle(
                    context, RawSignal(Source.SMS, c.getString(1).orEmpty(), c.getString(2).orEmpty(), date),
                    sourceId = id.toString(), quiet = true, // old messages must not each raise a notification
                )
                if (date > newest) newest = date
                read++
            }
        }
        if (newest > 0) repo.setMeta("last_scan_date", newest.toString())
        repo.setMeta("last_scan_at", now.toString())
        repo.setMeta("last_scan_read", read.toString())
        return read
    }
}
