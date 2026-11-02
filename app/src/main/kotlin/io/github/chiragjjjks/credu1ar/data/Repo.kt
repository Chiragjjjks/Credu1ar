package io.github.chiragjjjks.credu1ar.data

import android.content.ContentValues
import android.database.Cursor
import io.github.chiragjjjks.credu1ar.core.Accepted
import io.github.chiragjjjks.credu1ar.core.Dedup
import io.github.chiragjjjks.credu1ar.core.DedupKey
import io.github.chiragjjjks.credu1ar.core.insights.CoveredRange
import io.github.chiragjjjks.credu1ar.core.insights.Insights.IST
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.core.statement.Statement
import io.github.chiragjjjks.credu1ar.core.statement.StatementHeader
import io.github.chiragjjjks.credu1ar.core.statement.StatementIssue
import io.github.chiragjjjks.credu1ar.core.statement.StatementMatch
import io.github.chiragjjjks.credu1ar.core.statement.StatementTxns
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed interface InsertResult {
    data class Inserted(val id: Long) : InsertResult
    data class Duplicate(val id: Long, val upgraded: Boolean) : InsertResult
}

/** Everything the transaction sheet shows beyond the list row. */
data class TxnDetail(
    val sender: String?, val raw: String?, val reference: String?, val vpa: String?,
    val source: Source, val userSet: Boolean, val receivedAtMillis: Long,
)

/** One imported bank statement, as listed in System → Bank statements. */
data class StatementRecord(
    val id: Long,
    val bank: String,
    val accountTail: String?,
    val from: LocalDate,
    val to: LocalDate,
    val rows: Int,
    val complete: Boolean,
    val importedAtMillis: Long,
)

/** What [Repo.importStatement] did with one file. [alreadyHere] + [added] + [matched] = the rows offered. */
data class StatementImportResult(
    val statementId: Long,
    val added: Int,
    val alreadyHere: Int,
    val matched: Int,
    /** This exact file was imported before, so nothing was read into the ledger. */
    val sameFile: Boolean,
)

data class Unparsed(val id: Long, val receivedAtMillis: Long, val sender: String, val reason: String, val body: String)

/** The only class that talks SQL. Writes bump [version] so the UI knows to recompute. */
class Repo(private val db: Db) {
    val version = MutableStateFlow(0L)
    /**
     * Guards multi-step writes. Fair, so a live SMS waiting to [insert] gets in between two chunks of a long
     * history read ([batch]) instead of losing the race every time and running past its broadcast budget.
     */
    private val writeLock = ReentrantLock(true)
    /** Tell the UI the data changed (also called after bulk imports). */
    fun bump() { version.value = System.nanoTime() }

    // ------------------------------------------------------------------------------------------------ rules

    fun rules(): Map<String, Category> {
        val out = HashMap<String, Category>()
        db.readableDatabase.rawQuery("SELECT merchant_key, category FROM rule", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = Category.byId(c.getString(1))
        }
        return out
    }

    // ------------------------------------------------------------------------------------------------ write

    fun insert(signal: RawSignal, a: Accepted, sourceId: String?, keepRaw: Boolean): InsertResult = writeLock.withLock { insertLocked(signal, a, sourceId, keepRaw) }

    private fun insertLocked(signal: RawSignal, a: Accepted, sourceId: String?, keepRaw: Boolean): InsertResult {
        val w = db.writableDatabase
        if (sourceId != null) {
            w.rawQuery("SELECT id FROM txn WHERE source = ? AND source_id = ?", arrayOf(signal.source.id, sourceId)).use { c ->
                if (c.moveToFirst()) return InsertResult.Duplicate(c.getLong(0), upgraded = false)
            }
        }
        val p = a.parsed
        val incoming = DedupKey(p.direction, p.amountPaise, p.accountTail, p.reference, p.occurredAtMillis, signal.source)
        val window = 15L * 60 * 1000
        // Date-only statement rows (time_known = 0) carry a made-up noon time, so they are matched by day below, not here.
        w.rawQuery(
            "SELECT id, source, occurred_at, account_tail, reference, user_set FROM txn WHERE direction = ? AND amount_paise = ? AND occurred_at BETWEEN ? AND ? AND time_known = 1",
            arrayOf(p.direction.ordinal.toString(), p.amountPaise.toString(), (p.occurredAtMillis - window).toString(), (p.occurredAtMillis + window).toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val existing = DedupKey(p.direction, p.amountPaise, c.getStringOrNull(3), c.getStringOrNull(4), c.getLong(2), Source.byId(c.getString(1)))
                if (Dedup.isDuplicate(existing, incoming)) {
                    val id = c.getLong(0)
                    val upgrade = Dedup.prefer(existing.source, incoming.source)
                    if (upgrade) upgradeRow(id, signal, a, keepRaw, keepCategory = c.getInt(5) == 1)
                    return InsertResult.Duplicate(id, upgrade)
                }
            }
        }
        // The bank statement got here first: the SMS/notification is the same payment, and it knows the time.
        statementTwin(signal, a)?.let { id ->
            upgradeStatementRow(id, signal, a, sourceId, keepRaw)
            return InsertResult.Duplicate(id, upgraded = true)
        }
        val v = ContentValues().apply {
            put("source", signal.source.id)
            put("source_id", sourceId)
            put("occurred_at", p.occurredAtMillis)
            put("received_at", signal.receivedAtMillis)
            put("direction", p.direction.ordinal)
            put("amount_paise", p.amountPaise)
            put("merchant_key", a.merchant.key)
            put("merchant_name", a.merchant.name)
            put("category", a.categorization.category.id)
            put("confidence", a.categorization.confidence.toDouble())
            put("review", if (a.needsReview) 1 else 0)
            put("instrument", p.instrument.name)
            put("bank", p.bank)
            put("account_tail", p.accountTail)
            put("reference", p.reference)
            put("vpa", p.vpa)
            put("payee_kind", a.merchant.kind.name)
            put("hints", p.hints.joinToString(","))
            put("balance_paise", p.balancePaise)
            put("sender", signal.sender)
            put("raw", if (keepRaw) signal.body else null)
        }
        val id = w.insertWithOnConflict("txn", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        bump()
        return InsertResult.Inserted(id)
    }

    /** SMS carries the full text and reference; fill in what a notification-only row was missing. Never blanks a known field. */
    private fun upgradeRow(id: Long, signal: RawSignal, a: Accepted, keepRaw: Boolean, keepCategory: Boolean) {
        val p = a.parsed
        val v = ContentValues().apply {
            put("source", signal.source.id)
            p.bank?.let { put("bank", it) }
            p.accountTail?.let { put("account_tail", it) }
            p.reference?.let { put("reference", it) }
            p.vpa?.let { put("vpa", it) }
            put("sender", signal.sender)
            p.balancePaise?.let { put("balance_paise", it) }
            if (keepRaw) put("raw", signal.body)
            if (!keepCategory) {
                put("merchant_key", a.merchant.key); put("merchant_name", a.merchant.name)
                put("category", a.categorization.category.id); put("confidence", a.categorization.confidence.toDouble())
                put("review", if (a.needsReview) 1 else 0)
            }
        }
        db.writableDatabase.update("txn", v, "id = ?", arrayOf(id.toString()))
        bump()
    }

    /**
     * A date-only statement row that is the same payment as this SMS/notification, or null. One-to-one: a row the
     * SMS already upgraded has `time_known = 1` and is never claimed again, so two ₹40 chais stay two rows.
     *  1. the same reference (UPI RRN), direction and amount, dated up to 2 days apart;
     *  2. else the same direction and amount, a day either side, with no contradicting account tail or RRN.
     */
    private fun statementTwin(signal: RawSignal, a: Accepted): Long? {
        if (signal.source != Source.SMS && signal.source != Source.NOTIFICATION) return null
        val p = a.parsed
        val day = Instant.ofEpochMilli(p.occurredAtMillis).atZone(IST).toLocalDate()
        val r = db.readableDatabase
        val base = "SELECT id, account_tail, reference FROM txn WHERE source = ? AND time_known = 0 AND direction = ? AND amount_paise = ? AND occurred_at >= ? AND occurred_at < ?"
        fun query(sql: String, from: LocalDate, until: LocalDate, vararg extra: String): Long? =
            r.rawQuery(
                "$sql ORDER BY ABS(occurred_at - ?), id",
                arrayOf(Source.STATEMENT.id, p.direction.ordinal.toString(), p.amountPaise.toString(), startOf(from).toString(), startOf(until).toString(), *extra, p.occurredAtMillis.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    if (tailsClash(c.getStringOrNull(1), p.accountTail)) continue
                    if (rrnsClash(c.getStringOrNull(2), p.reference)) continue
                    return c.getLong(0)
                }
                null
            }
        p.reference?.let { ref -> query("$base AND reference = ?", day.minusDays(2), day.plusDays(3), ref)?.let { return it } }
        return query(base, day.minusDays(1), day.plusDays(2))
    }

    /** The statement row becomes the SMS/notification row: its time, id and text. The statement key and a known balance stay. */
    private fun upgradeStatementRow(id: Long, signal: RawSignal, a: Accepted, sourceId: String?, keepRaw: Boolean) {
        val w = db.writableDatabase
        var userSet = false
        var confidence = 0.0
        w.rawQuery("SELECT user_set, confidence FROM txn WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) { userSet = c.getInt(0) == 1; confidence = c.getDouble(1) }
        }
        val p = a.parsed
        val v = ContentValues().apply {
            put("source", signal.source.id)
            put("source_id", sourceId) // a live SMS has none; the catch-up scan then dedups it by time like any SMS row
            put("occurred_at", p.occurredAtMillis)
            put("received_at", signal.receivedAtMillis)
            put("time_known", 1)
            put("sender", signal.sender)
            p.bank?.let { put("bank", it) }
            p.accountTail?.let { put("account_tail", it) }
            p.reference?.let { put("reference", it) }
            p.vpa?.let { put("vpa", it) }
            p.balancePaise?.let { put("balance_paise", it) }
            if (keepRaw) put("raw", signal.body)
            // The statement's truncated narration can name the payee better than a terse alert: keep the surer of the two.
            if (!userSet && a.categorization.confidence >= confidence) {
                put("merchant_key", a.merchant.key); put("merchant_name", a.merchant.name); put("payee_kind", a.merchant.kind.name)
                put("category", a.categorization.category.id); put("confidence", a.categorization.confidence.toDouble())
                put("review", if (a.needsReview) 1 else 0)
            }
        }
        w.update("txn", v, "id = ?", arrayOf(id.toString()))
        bump()
    }

    /** Teach the app: file this payment (and, if [remember], every payment to the same payee) under [category]. */
    fun setCategory(id: Long, category: Category, remember: Boolean) = writeLock.withLock { setCategoryLocked(id, category, remember) }

    private fun setCategoryLocked(id: Long, category: Category, remember: Boolean) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            var key: String? = null
            var dir = 0
            w.rawQuery("SELECT merchant_key, direction FROM txn WHERE id = ?", arrayOf(id.toString())).use { c ->
                if (c.moveToFirst()) { key = c.getString(0); dir = c.getInt(1) }
            }
            val one = ContentValues().apply { put("category", category.id); put("confidence", 1.0); put("review", 0); put("user_set", 1) }
            w.update("txn", one, "id = ?", arrayOf(id.toString()))
            val k = key
            // Never a rule for "unknown": every payment without a payee shares that key (ADR-015 review).
            if (remember && k != null && k != "unknown") {
                val r = ContentValues().apply { put("merchant_key", k); put("category", category.id); put("created_at", System.currentTimeMillis()) }
                w.insertWithOnConflict("rule", null, r, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                val rest = ContentValues().apply { put("category", category.id); put("confidence", 1.0); put("review", 0) }
                w.update("txn", rest, "merchant_key = ? AND user_set = 0 AND direction = ?", arrayOf(k, dir.toString()))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        bump()
    }

    fun deleteTxn(id: Long) { db.writableDatabase.delete("txn", "id = ?", arrayOf(id.toString())); bump() }
    fun clearSamples() { db.writableDatabase.delete("txn", "source = ?", arrayOf(Source.SAMPLE.id)); bump() }

    /**
     * Wipes payments, imported statements, rules, unreadable messages and counters. Keeps `last_scan_date` and records `wiped_at`, so
     * catch-up reads only SMS that arrive after the wipe: deleted payments never come back by themselves.
     * New SMS are still filed, and "Add <month>" re-reads old ones on request.
     */
    fun deleteEverything() = writeLock.withLock {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            listOf("txn", "rule", "unparsed", "statement").forEach { w.delete(it, null, null) }
            w.delete("meta", "key != ?", arrayOf("last_scan_date"))
            setMeta("wiped_at", System.currentTimeMillis().toString())
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        bump()
    }

    /**
     * Runs [block] as one SQLite transaction, so a bulk read commits once per chunk instead of 3-4 times per message.
     * Holds [writeLock] too, taken before the SQLite connection like every other writer, so a live SMS arriving
     * mid-import waits for the chunk to finish instead of deadlocking against it.
     */
    fun <T> batch(block: () -> T): T = writeLock.withLock {
        val w = db.writableDatabase
        w.beginTransactionNonExclusive()
        try {
            val r = block()
            w.setTransactionSuccessful()
            r
        } finally { w.endTransaction() }
    }

    // ------------------------------------------------------------------------------------------------ read

    fun all(): List<Txn> {
        val out = ArrayList<Txn>(512)
        db.readableDatabase.rawQuery(
            "SELECT id, occurred_at, direction, amount_paise, merchant_key, merchant_name, category, confidence, instrument, bank, account_tail, review, source, time_known FROM txn ORDER BY occurred_at DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += Txn(
                    id = c.getLong(0), occurredAtMillis = c.getLong(1),
                    direction = if (c.getInt(2) == 0) Direction.DEBIT else Direction.CREDIT,
                    amountPaise = c.getLong(3), merchantKey = c.getString(4), merchantName = c.getString(5),
                    category = Category.byId(c.getString(6)), confidence = c.getFloat(7),
                    instrument = runCatching { Instrument.valueOf(c.getString(8)) }.getOrDefault(Instrument.UNKNOWN),
                    bank = c.getStringOrNull(9), accountTail = c.getStringOrNull(10), needsReview = c.getInt(11) == 1,
                    source = Source.byId(c.getString(12)), timeKnown = c.getInt(13) == 1,
                )
            }
        }
        return out
    }

    fun detail(id: Long): TxnDetail? =
        db.readableDatabase.rawQuery("SELECT sender, raw, reference, vpa, source, user_set, received_at FROM txn WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) null
            else TxnDetail(c.getStringOrNull(0), c.getStringOrNull(1), c.getStringOrNull(2), c.getStringOrNull(3), Source.byId(c.getString(4)), c.getInt(5) == 1, c.getLong(6))
        }

    fun counts(): Counts = db.readableDatabase.rawQuery(
        "SELECT COUNT(*), SUM(CASE WHEN review=1 THEN 1 ELSE 0 END), SUM(CASE WHEN source='sample' THEN 1 ELSE 0 END), SUM(CASE WHEN source='sms' THEN 1 ELSE 0 END), SUM(CASE WHEN source='notif' THEN 1 ELSE 0 END), SUM(CASE WHEN source='stmt' THEN 1 ELSE 0 END) FROM txn",
        null,
    ).use { c -> c.moveToFirst(); Counts(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5)) }

    /** [statement] = rows only a bank statement told us about (rows an SMS matched or upgraded count as [sms]/[notif]). */
    data class Counts(val total: Int, val review: Int, val sample: Int, val sms: Int, val notif: Int, val statement: Int = 0)

    /** Real (non-sample) payments dated on or after [fromMillis]: the "86 payments" in "1 Oct → today · 86 payments". */
    fun countSince(fromMillis: Long): Int = db.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM txn WHERE occurred_at >= ? AND source != ?", arrayOf(fromMillis.toString(), Source.SAMPLE.id),
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    /** When the oldest SMS-filed payment arrived; stands in for `history_from` on installs from before v0.1.2. */
    fun earliestSmsAt(): Long? = db.readableDatabase.rawQuery("SELECT MIN(received_at) FROM txn WHERE source = ?", arrayOf(Source.SMS.id))
        .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    // ------------------------------------------------------------------------------------------------ unparsed

    fun addUnparsed(signal: RawSignal, reason: String) {
        // History reads overlap by design; keep one entry per message.
        val seen = db.readableDatabase.rawQuery(
            "SELECT 1 FROM unparsed WHERE received_at = ? AND sender = ? LIMIT 1", arrayOf(signal.receivedAtMillis.toString(), signal.sender),
        ).use { it.moveToFirst() }
        if (seen) return
        val v = ContentValues().apply { put("received_at", signal.receivedAtMillis); put("sender", signal.sender); put("reason", reason); put("body", signal.body) }
        val w = db.writableDatabase
        w.insert("unparsed", null, v)
        w.execSQL("DELETE FROM unparsed WHERE id NOT IN (SELECT id FROM unparsed ORDER BY id DESC LIMIT 50)") // keep it small
        bump()
    }

    fun unparsed(): List<Unparsed> = buildList {
        db.readableDatabase.rawQuery("SELECT id, received_at, sender, reason, body FROM unparsed ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) add(Unparsed(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty()))
        }
    }

    fun clearUnparsed() { db.writableDatabase.delete("unparsed", null, null); bump() }

    // ------------------------------------------------------------------------------------------------ meta (counters, timestamps)

    fun meta(key: String): String? =
        db.readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    fun metaLong(key: String): Long = meta(key)?.toLongOrNull() ?: 0L

    fun setMeta(key: String, value: String) {
        val v = ContentValues().apply { put("key", key); put("value", value) }
        db.writableDatabase.insertWithOnConflict("meta", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun inc(key: String) {
        db.writableDatabase.execSQL(
            "INSERT INTO meta(key, value) VALUES(?, '1') ON CONFLICT(key) DO UPDATE SET value = CAST(value AS INTEGER) + 1",
            arrayOf(key),
        )
    }

    // ------------------------------------------------------------------------------------------------ export

    // ------------------------------------------------------------------------------------------------ statements

    /**
     * Stores one parsed statement: one call per file, all of it in one transaction under [writeLock].
     *  - the same file again (equal [contentHash]) → nothing written, every row "already here";
     *  - the same bank, account and period from a different file (re-downloaded) → merged into that statement's record;
     *  - a row whose key an earlier statement already stored → already here (overlapping statements);
     *  - a row [StatementMatch] pairs with a captured SMS/notification → that row gets the key and balance (matched);
     *  - otherwise a new date-only row: source `stmt`, `source_id` = key, `time_known` = 0, the narration as its text.
     * Stores no holder name, file name or URI. Does not [bump]: the caller does, once, after every file.
     */
    fun importStatement(
        statement: Statement, items: List<StatementTxns.Item>, contentHash: String, keepRaw: Boolean, now: Long = System.currentTimeMillis(),
    ): StatementImportResult = batch { importLocked(statement, items, contentHash, keepRaw, now) }

    private fun importLocked(s: Statement, items: List<StatementTxns.Item>, contentHash: String, keepRaw: Boolean, now: Long): StatementImportResult {
        val w = db.writableDatabase
        val h = s.header
        val tail = h.accountTail.orEmpty()
        w.rawQuery("SELECT id FROM statement WHERE content_hash = ?", arrayOf(contentHash)).use { c ->
            if (c.moveToFirst()) return StatementImportResult(c.getLong(0), added = 0, alreadyHere = items.size, matched = 0, sameFile = true)
        }
        val existingId = w.rawQuery(
            "SELECT id FROM statement WHERE bank = ? AND account_tail = ? AND period_from = ? AND period_to = ?",
            arrayOf(h.bank, tail, h.from.toString(), h.to.toString()),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        val sid = existingId ?: w.insertOrThrow("statement", null, ContentValues().apply {
            put("bank", h.bank); put("account_tail", tail); put("period_from", h.from.toString()); put("period_to", h.to.toString())
            put("opening_paise", s.openingPaise); put("closing_paise", s.closingPaise)
            put("row_count", items.size); put("complete", if (s.complete) 1 else 0)
            put("content_hash", contentHash); put("imported_at", now)
        })

        var already = 0
        val fresh = ArrayList<StatementTxns.Item>(items.size)
        for (item in items) {
            val seen = w.rawQuery("SELECT 1 FROM txn WHERE stmt_key = ? LIMIT 1", arrayOf(item.key)).use { it.moveToFirst() }
            if (seen) already++ else fresh += item
        }
        val candidates = if (fresh.isEmpty()) emptyList() else matchCandidates(h.from, h.to)
        val pairs = if (candidates.isEmpty()) emptyMap() else StatementMatch.assign(fresh, candidates)
        val claimed = HashSet<Long>()
        var matched = 0
        var added = 0
        fresh.forEachIndexed { i, item ->
            val target = pairs[i]?.takeIf { claimed.add(it) }
            when {
                target != null && attach(target, item, sid) -> matched++
                insertStatementRow(item, h, sid, keepRaw, now) -> added++
                else -> already++
            }
        }

        if (existingId == null) {
            w.execSQL("UPDATE statement SET added = ?, matched = ? WHERE id = ?", arrayOf<Any>(added, matched, sid))
        } else {
            w.execSQL(
                """UPDATE statement SET content_hash = ?, row_count = MAX(row_count, ?), added = added + ?, matched = matched + ?,
                   complete = MAX(complete, ?), opening_paise = ?, closing_paise = COALESCE(?, closing_paise), imported_at = ? WHERE id = ?""",
                arrayOf<Any?>(contentHash, items.size, added, matched, if (s.complete) 1 else 0, s.openingPaise, s.closingPaise, now, sid),
            )
        }
        if (!s.complete) noteIncomplete(s, now)
        return StatementImportResult(sid, added, already, matched, sameFile = false)
    }

    /** SMS/notification rows in the statement's period (a day either side) that no statement has claimed yet. */
    private fun matchCandidates(from: LocalDate, to: LocalDate): List<StatementMatch.Candidate> = buildList {
        db.readableDatabase.rawQuery(
            "SELECT id, direction, amount_paise, occurred_at, reference, account_tail FROM txn WHERE source IN (?, ?) AND stmt_key IS NULL AND occurred_at >= ? AND occurred_at < ?",
            arrayOf(Source.SMS.id, Source.NOTIFICATION.id, startOf(from.minusDays(1)).toString(), startOf(to.plusDays(2)).toString()),
        ).use { c ->
            while (c.moveToNext()) add(
                StatementMatch.Candidate(
                    id = c.getLong(0), direction = if (c.getInt(1) == 0) Direction.DEBIT else Direction.CREDIT, amountPaise = c.getLong(2),
                    date = Instant.ofEpochMilli(c.getLong(3)).atZone(IST).toLocalDate(), reference = c.getStringOrNull(4), accountTail = c.getStringOrNull(5),
                ),
            )
        }
    }

    /** The captured row keeps its time, text and category; it gains the statement key, the bank's balance, and a missing reference or tail. */
    private fun attach(id: Long, item: StatementTxns.Item, statementId: Long): Boolean {
        val p = item.accepted.parsed
        val st = db.writableDatabase.compileStatement(
            """UPDATE txn SET stmt_key = ?, statement_id = ?, balance_paise = ?, reference = COALESCE(reference, ?), account_tail = COALESCE(account_tail, ?)
               WHERE id = ? AND stmt_key IS NULL AND source IN (?, ?)""",
        )
        st.bindString(1, item.key)
        st.bindLong(2, statementId)
        st.bindLong(3, item.row.balancePaise)
        p.reference?.let { st.bindString(4, it) } ?: st.bindNull(4)
        p.accountTail?.let { st.bindString(5, it) } ?: st.bindNull(5)
        st.bindLong(6, id)
        st.bindString(7, Source.SMS.id)
        st.bindString(8, Source.NOTIFICATION.id)
        return st.use { it.executeUpdateDelete() } == 1
    }

    private fun insertStatementRow(item: StatementTxns.Item, h: StatementHeader, statementId: Long, keepRaw: Boolean, now: Long): Boolean {
        val a = item.accepted
        val p = a.parsed
        val v = ContentValues().apply {
            put("source", Source.STATEMENT.id)
            put("source_id", item.key)
            put("stmt_key", item.key)
            put("statement_id", statementId)
            put("time_known", 0)
            put("occurred_at", item.occurredAtMillis)
            put("received_at", now)
            put("direction", p.direction.ordinal)
            put("amount_paise", p.amountPaise)
            put("merchant_key", a.merchant.key)
            put("merchant_name", a.merchant.name)
            put("category", a.categorization.category.id)
            put("confidence", a.categorization.confidence.toDouble())
            put("review", if (a.needsReview) 1 else 0)
            put("instrument", p.instrument.name)
            put("bank", p.bank)
            put("account_tail", p.accountTail)
            put("reference", p.reference)
            put("vpa", p.vpa)
            put("payee_kind", a.merchant.kind.name)
            put("hints", p.hints.joinToString(","))
            put("balance_paise", p.balancePaise)
            put("sender", "${h.bank} statement")
            // The narration is financial text (no holder name or full account number: the layout masks those).
            put("raw", if (keepRaw) item.row.narration else null)
        }
        return db.writableDatabase.insertWithOnConflict("txn", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    /** "Couldn't read" gets one entry per incomplete import: what kind of problem and how many, never row text or amounts. */
    private fun noteIncomplete(s: Statement, now: Long) {
        val parts = buildList {
            s.issues.filterIsInstance<StatementIssue.BadDelta>().size.takeIf { it > 0 }?.let { add("$it row(s) didn't match the running balance") }
            s.issues.filterIsInstance<StatementIssue.IndexGap>().sumOf { (it.found - it.expected).coerceAtLeast(1) }.takeIf { it > 0 }?.let { add("$it row(s) missing") }
            s.issues.filterIsInstance<StatementIssue.Unread>().size.takeIf { it > 0 }?.let { add("$it part(s) unreadable") }
            s.issues.filterIsInstance<StatementIssue.EmptyPage>().size.takeIf { it > 0 }?.let { add("$it page(s) without text") }
            s.issues.firstOrNull { it is StatementIssue.PageCount }?.let { add("pages missing") }
            if (s.issues.any { it is StatementIssue.OpeningMismatch || it is StatementIssue.ClosingMismatch }) add("balances don't match the account summary")
        }
        val v = ContentValues().apply {
            put("received_at", now)
            put("sender", "${s.header.bank} statement")
            put("reason", "statement incomplete")
            put("body", "${s.header.from} to ${s.header.to}: " + parts.joinToString("; ").ifEmpty { "checks failed" })
        }
        val w = db.writableDatabase
        w.insert("unparsed", null, v)
        w.execSQL("DELETE FROM unparsed WHERE id NOT IN (SELECT id FROM unparsed ORDER BY id DESC LIMIT 50)")
    }

    /** Imported statements, newest period first. */
    fun statements(): List<StatementRecord> = buildList {
        db.readableDatabase.rawQuery(
            "SELECT id, bank, account_tail, period_from, period_to, row_count, complete, imported_at FROM statement ORDER BY period_to DESC, period_from DESC, id DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) add(
                StatementRecord(
                    id = c.getLong(0), bank = c.getString(1), accountTail = c.getStringOrNull(2)?.ifEmpty { null },
                    from = LocalDate.parse(c.getString(3)), to = LocalDate.parse(c.getString(4)),
                    rows = c.getInt(5), complete = c.getInt(6) == 1, importedAtMillis = c.getLong(7),
                ),
            )
        }
    }

    /** Days with a complete record: every statement's period, per account. Feeds Reports.coverage. */
    fun coveredRanges(): List<CoveredRange> = statements().map { CoveredRange(it.from, it.to) }

    /**
     * Remove one import. Its statement-only rows go, except on days another statement of the same account also covers
     * (those move to that statement). Rows it matched to an SMS or notification stay, detached (or moved the same way).
     */
    fun deleteStatement(id: Long) {
        batch {
            val w = db.writableDatabase
            val account = w.rawQuery("SELECT bank, account_tail FROM statement WHERE id = ?", arrayOf(id.toString()))
                .use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null } ?: return@batch
            data class Period(val id: Long, val from: LocalDate, val to: LocalDate)
            val others = buildList {
                w.rawQuery(
                    "SELECT id, period_from, period_to FROM statement WHERE bank = ? AND account_tail = ? AND id != ?",
                    arrayOf(account.first, account.second, id.toString()),
                ).use { c -> while (c.moveToNext()) add(Period(c.getLong(0), LocalDate.parse(c.getString(1)), LocalDate.parse(c.getString(2)))) }
            }
            val moves = ArrayList<Pair<Long, Long>>()
            val deletes = ArrayList<Long>()
            val detaches = ArrayList<Long>()
            w.rawQuery("SELECT id, source, occurred_at FROM txn WHERE statement_id = ?", arrayOf(id.toString())).use { c ->
                while (c.moveToNext()) {
                    val day = Instant.ofEpochMilli(c.getLong(2)).atZone(IST).toLocalDate()
                    val cover = others.firstOrNull { !day.isBefore(it.from) && !day.isAfter(it.to) }
                    when {
                        cover != null -> moves += c.getLong(0) to cover.id
                        c.getString(1) == Source.STATEMENT.id -> deletes += c.getLong(0)
                        else -> detaches += c.getLong(0)
                    }
                }
            }
            moves.forEach { (row, to) -> w.execSQL("UPDATE txn SET statement_id = ? WHERE id = ?", arrayOf<Any>(to, row)) }
            deletes.forEach { w.delete("txn", "id = ?", arrayOf(it.toString())) }
            detaches.forEach { w.execSQL("UPDATE txn SET stmt_key = NULL, statement_id = NULL WHERE id = ?", arrayOf<Any>(it)) }
            w.delete("statement", "id = ?", arrayOf(id.toString()))
        }
        bump()
    }

    fun exportCsv(out: Appendable) {
        out.append("date,direction,amount_inr,merchant,category,group,instrument,bank,account_tail,reference\n")
        db.readableDatabase.rawQuery(
            "SELECT occurred_at, direction, amount_paise, merchant_name, category, instrument, bank, account_tail, reference FROM txn ORDER BY occurred_at",
            null,
        ).use { c ->
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
            while (c.moveToNext()) {
                val cat = Category.byId(c.getString(4))
                val amount = c.getLong(2)
                out.append(fmt.format(java.util.Date(c.getLong(0)))).append(',')
                    .append(if (c.getInt(1) == 0) "debit" else "credit").append(',')
                    .append("${amount / 100}.${(amount % 100).toString().padStart(2, '0')}").append(',')
                    .append(csv(c.getString(3))).append(',').append(csv(cat.label)).append(',').append(csv(cat.group.label)).append(',')
                    .append(c.getString(5)).append(',').append(csv(c.getStringOrNull(6))).append(',')
                    .append(c.getStringOrNull(7).orEmpty()).append(',').append(c.getStringOrNull(8).orEmpty()).append('\n')
            }
        }
    }

    private fun csv(s: String?): String {
        val t = s.orEmpty()
        // Defuse spreadsheet formula injection from merchant names, then quote.
        val safe = if (t.isNotEmpty() && t[0] in "=+-@") "'$t" else t
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}

private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

/** Midnight IST at the start of [d], in epoch millis. */
private fun startOf(d: LocalDate): Long = d.atStartOfDay(IST).toInstant().toEpochMilli()

/** Two account tails that can't be the same account ("7890" and "XX7890" can). */
private fun tailsClash(a: String?, b: String?): Boolean = a != null && b != null && !a.endsWith(b) && !b.endsWith(a)

private val RRN = Regex("""^\d{12}$""")

/** Two different 12-digit UPI references are two different payments; other reference styles are not compared. */
private fun rrnsClash(a: String?, b: String?): Boolean = a != null && b != null && RRN.matches(a) && RRN.matches(b) && a != b
