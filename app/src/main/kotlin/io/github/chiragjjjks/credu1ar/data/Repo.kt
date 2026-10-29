package io.github.chiragjjjks.credu1ar.data

import android.content.ContentValues
import android.database.Cursor
import io.github.chiragjjjks.credu1ar.core.Accepted
import io.github.chiragjjjks.credu1ar.core.Dedup
import io.github.chiragjjjks.credu1ar.core.DedupKey
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.model.Txn
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface InsertResult {
    data class Inserted(val id: Long) : InsertResult
    data class Duplicate(val id: Long, val upgraded: Boolean) : InsertResult
}

/** Everything the transaction sheet shows beyond the list row. */
data class TxnDetail(
    val sender: String?, val raw: String?, val reference: String?, val vpa: String?,
    val source: Source, val userSet: Boolean, val receivedAtMillis: Long,
)

data class Unparsed(val id: Long, val receivedAtMillis: Long, val sender: String, val reason: String, val body: String)

/** The only class that talks SQL. Writes bump [version] so the UI knows to recompute. */
class Repo(private val db: Db) {
    val version = MutableStateFlow(0L)
    private fun bump() { version.value = System.nanoTime() }

    // ------------------------------------------------------------------------------------------------ rules

    fun rules(): Map<String, Category> {
        val out = HashMap<String, Category>()
        db.readableDatabase.rawQuery("SELECT merchant_key, category FROM rule", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = Category.byId(c.getString(1))
        }
        return out
    }

    // ------------------------------------------------------------------------------------------------ write

    @Synchronized
    fun insert(signal: RawSignal, a: Accepted, sourceId: String?, keepRaw: Boolean): InsertResult {
        val w = db.writableDatabase
        if (sourceId != null) {
            w.rawQuery("SELECT id FROM txn WHERE source = ? AND source_id = ?", arrayOf(signal.source.id, sourceId)).use { c ->
                if (c.moveToFirst()) return InsertResult.Duplicate(c.getLong(0), upgraded = false)
            }
        }
        val p = a.parsed
        val incoming = DedupKey(p.direction, p.amountPaise, p.accountTail, p.reference, p.occurredAtMillis, signal.source)
        val window = 15L * 60 * 1000
        w.rawQuery(
            "SELECT id, source, occurred_at, account_tail, reference, user_set FROM txn WHERE direction = ? AND amount_paise = ? AND occurred_at BETWEEN ? AND ?",
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

    /** SMS carries the full text and reference; fill in what a notification-only row was missing. */
    private fun upgradeRow(id: Long, signal: RawSignal, a: Accepted, keepRaw: Boolean, keepCategory: Boolean) {
        val p = a.parsed
        val v = ContentValues().apply {
            put("source", signal.source.id)
            put("bank", p.bank)
            put("account_tail", p.accountTail)
            put("reference", p.reference)
            put("vpa", p.vpa)
            put("sender", signal.sender)
            put("balance_paise", p.balancePaise)
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

    /** Teach the app: file this payment (and, if [remember], every payment to the same payee) under [category]. */
    @Synchronized
    fun setCategory(id: Long, category: Category, remember: Boolean) {
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
            if (remember && k != null) {
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

    fun deleteEverything() {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            listOf("txn", "rule", "unparsed", "meta").forEach { w.delete(it, null, null) }
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        bump()
    }

    // ------------------------------------------------------------------------------------------------ read

    fun all(): List<Txn> {
        val out = ArrayList<Txn>(512)
        db.readableDatabase.rawQuery(
            "SELECT id, occurred_at, direction, amount_paise, merchant_key, merchant_name, category, confidence, instrument, bank, account_tail, review FROM txn ORDER BY occurred_at DESC",
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
        "SELECT COUNT(*), SUM(CASE WHEN review=1 THEN 1 ELSE 0 END), SUM(CASE WHEN source='sample' THEN 1 ELSE 0 END), SUM(CASE WHEN source='sms' THEN 1 ELSE 0 END), SUM(CASE WHEN source='notif' THEN 1 ELSE 0 END) FROM txn",
        null,
    ).use { c -> c.moveToFirst(); Counts(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4)) }

    data class Counts(val total: Int, val review: Int, val sample: Int, val sms: Int, val notif: Int)

    // ------------------------------------------------------------------------------------------------ unparsed

    fun addUnparsed(signal: RawSignal, reason: String) {
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
