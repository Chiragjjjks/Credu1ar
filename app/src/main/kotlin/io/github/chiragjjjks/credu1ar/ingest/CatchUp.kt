package io.github.chiragjjjks.credu1ar.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.HistoryWindow
import io.github.chiragjjjks.credu1ar.core.ImportWindow
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Reads the SMS inbox. Three kinds of read, one at a time:
 *  - **first link**: the moment SMS access works, read this month (plus all of last month in week 1, see
 *    [HistoryWindow.firstLink]) so the app opens full. Runs once, from the app, never inside a broadcast.
 *  - **catch-up**: anything newer than what we already saw. On every app open, and every few hours on an SMS wake-up.
 *  - **earlier**: one more month back, when the owner taps "Add <month>".
 * Duplicates are harmless: the inbox row id is a unique key, and live SMS dedup against it by time and amount.
 */
object CatchUp {
    private const val DUE_AFTER = 6L * 3600 * 1000
    private const val OVERLAP = 60_000L
    /** Bank alerts can land hours after the payment. An earlier-month read looks this far past its month for them. */
    private const val LATE_ALERTS = 2L * 24 * 3600 * 1000
    private const val CHUNK = 200
    private val lock = ReentrantLock()

    /** [window] is set only for the first-link read. */
    data class Result(val read: Int, val fromMillis: Long, val window: ImportWindow?)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** True until SMS history has been read once. Installs from before v0.1.2 already have a `last_scan_date`. */
    fun needsFirstLink(context: Context): Boolean {
        val repo = Graph.repo(context)
        return repo.metaLong("history_from") == 0L && repo.metaLong("last_scan_date") == 0L
    }

    /** From the SMS receiver: a quick catch-up if one is due. Skips if a read is running or history was never read. */
    fun runIfDue(context: Context) {
        if (needsFirstLink(context)) return
        if (System.currentTimeMillis() - Graph.repo(context).metaLong("last_scan_at") <= DUE_AFTER) return
        if (lock.tryLock()) try { read(context, null) } finally { lock.unlock() }
    }

    /** Catch-up, or the first-link read if history was never read. Waits for any read already running. */
    fun scan(context: Context, onProgress: (read: Int, total: Int) -> Unit = { _, _ -> }): Result =
        lock.withLock { read(context, null, onProgress) }

    /** Reads what arrived from [fromMillis] up to where history already starts. */
    fun scanFrom(context: Context, fromMillis: Long, onProgress: (read: Int, total: Int) -> Unit = { _, _ -> }): Result =
        lock.withLock { read(context, fromMillis, onProgress) }

    private fun read(context: Context, earlier: Long?, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        if (!hasPermission(context)) return Result(0, 0, null)
        val repo = Graph.repo(context)
        val now = System.currentTimeMillis()
        val last = repo.metaLong("last_scan_date")
        val historyFrom = repo.metaLong("history_from")
        val first = if (earlier == null && last == 0L && historyFrom == 0L) HistoryWindow.firstLink(now) else null
        val history = earlier != null || first != null
        val since = earlier ?: first?.startMillis ?: (maxOf(last, historyFrom) - OVERLAP) // small overlap; the unique key dedups
        // "Earlier" stops just past where history already starts, so a tap reads one month, not everything again,
        // yet still finds a 31 Oct payment whose alert arrived on 1 Nov (the first read dropped it as too old).
        val until = if (earlier != null && historyFrom > 0) historyFrom + LATE_ALERTS else Long.MAX_VALUE
        if (first != null) repo.clearSamples() // fictional rows must not mix into real totals

        var newest = last
        var seen = 0
        val cursor = runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?", arrayOf(since.toString(), until.toString()), "${Telephony.Sms.DATE} ASC",
            )
        }.getOrNull() ?: return Result(0, since, first)
        cursor.use { c ->
            val total = c.count
            val rules = repo.rules()
            onProgress(0, total)
            var more = true
            while (more) {
                more = repo.batch {
                    var n = 0
                    while (n < CHUNK && c.moveToNext()) {
                        val date = c.getLong(3)
                        Ingest.handle(
                            context, RawSignal(Source.SMS, c.getString(1).orEmpty(), c.getString(2).orEmpty(), date),
                            sourceId = c.getLong(0).toString(), quiet = true, // old messages must not each raise a notification
                            rules = rules, notBefore = if (history) since else 0L,
                        )
                        if (date > newest) newest = date
                        n++
                        seen++
                    }
                    n == CHUNK
                }
                onProgress(seen, total)
                repo.bump()
            }
        }
        if (newest > last) repo.setMeta("last_scan_date", newest.toString())
        // Remember where history starts even if the window held no SMS, so the first-link read never repeats.
        if (history) repo.setMeta("history_from", (if (historyFrom > 0) minOf(historyFrom, since) else since).toString())
        repo.setMeta("last_scan_at", now.toString())
        repo.setMeta("last_scan_read", seen.toString())
        return Result(seen, since, first)
    }
}
