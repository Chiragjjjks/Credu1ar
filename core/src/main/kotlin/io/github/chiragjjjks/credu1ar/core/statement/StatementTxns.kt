package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.Accepted
import io.github.chiragjjjks.credu1ar.core.Pipeline
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.PayeeKind
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.model.Txn
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Statement rows → the same [Accepted] payments the SMS path produces, so one categoriser and one set of rules serve both. */
object StatementTxns {
    /**
     * One row ready to store. [key] identifies the row across files (see [key]); [occurredAtMillis] = noon IST + its
     * place in the day, in seconds. In [row]'s narration a payee in the holder's own name is replaced by "Own account".
     */
    data class Item(val row: StatementRow, val key: String, val occurredAtMillis: Long, val accepted: Accepted)

    fun accept(s: Statement, rules: Map<String, Category> = emptyMap(), zone: ZoneId = Insights.IST): List<Item> {
        val seenKeys = HashMap<String, Int>()
        val placeInDay = HashMap<LocalDate, Int>()
        return s.rows.map { row ->
            val base = key(s.header, row)
            val k = (seenKeys[base] ?: 0) + 1
            seenKeys[base] = k
            val place = placeInDay.getOrDefault(row.date, 0)
            placeInDay[row.date] = place + 1
            val at = occurredAt(row.date, place, zone)
            Item(withoutHolder(s.header, row), if (k == 1) base else "$base#$k", at, Pipeline.accept(parsed(s.header, row, at), rules))
        }
    }

    /** The row with the holder's printed name replaced by "Own account" wherever it appears, in every row type (UPI, IMPS, NEFT…). */
    private fun withoutHolder(h: StatementHeader, row: StatementRow): StatementRow {
        val clean = OwnName.scrub(row.narration, h.holderName)
        return if (clean == row.narration) row else row.copy(narration = clean)
    }

    /** A stored-looking [Txn] for an item, for reports and tests before the database assigns an id. */
    fun txn(item: Item, id: Long = 0L): Txn {
        val a = item.accepted
        return Txn(
            id = id, occurredAtMillis = item.occurredAtMillis, direction = a.parsed.direction, amountPaise = a.parsed.amountPaise,
            merchantKey = a.merchant.key, merchantName = a.merchant.name, category = a.categorization.category,
            confidence = a.categorization.confidence, instrument = a.parsed.instrument, bank = a.parsed.bank,
            accountTail = a.parsed.accountTail, needsReview = a.needsReview, source = Source.STATEMENT, timeKnown = false,
        )
    }

    /**
     * Stable across downloads and overlapping statements: the same row has the same date, amount, direction and
     * balance after it in every file. Row number, page and narration are left out because they change between files.
     * Identical keys inside one statement get `#2`, `#3`… in bank order.
     */
    fun key(h: StatementHeader, row: StatementRow): String =
        "${h.bank.lowercase()}:${h.accountTail ?: "-"}:${row.date}:${if (row.direction == Direction.DEBIT) "D" else "C"}:${row.amountPaise}:${row.balancePaise}"

    /** Statement rows carry no time: stamp them at 12:00 plus their place in the day (seconds), so the bank's order survives. */
    fun occurredAt(date: LocalDate, placeInDay: Int, zone: ZoneId = Insights.IST): Long =
        date.atTime(12, 0).plusSeconds(placeInDay.toLong()).atZone(zone).toInstant().toEpochMilli()

    /**
     * A payee in the holder's own name ([OwnName]) becomes a payee-less own-account transfer ("self" + "own_name"
     * hints, named "Own account", left for review): the holder's name never reaches the merchant key or name.
     */
    fun parsed(h: StatementHeader, row: StatementRow, occurredAtMillis: Long): ParsedTxn {
        val n = narration(h, row)
        // Exactly the holder's printed name: an own-account transfer named "Own account" (the name is never kept).
        // Only similar ("ASHA KAPOOR" for "Asha K"): maybe their own account, maybe someone else, so the payee keeps its
        // own name and key and the owner confirms; a rule taught on it then can't refile real own-account transfers.
        val own = OwnName.exact(n.payee, h.holderName)
        val similar = !own && OwnName.matches(n.payee, h.holderName)
        return ParsedTxn(
            direction = row.direction,
            amountPaise = row.amountPaise,
            payeeName = if (own) null else n.payee,
            vpa = null,
            payeeKind = PayeeKind.UNKNOWN,
            accountTail = h.accountTail,
            instrument = n.instrument,
            bank = h.bank,
            reference = n.rrn ?: row.ref,
            occurredAtMillis = occurredAtMillis,
            balancePaise = row.balancePaise,
            hints = when {
                own -> n.hints + setOf("self", "own_name")
                similar -> n.hints + setOf("self", "own_name_guess")
                else -> n.hints
            },
            confidence = 0.9f,
            payeeTruncated = !own && n.payeeTruncated,
        )
    }

    fun narration(h: StatementHeader, row: StatementRow): NarrationInfo = when (h.bank) {
        KotakLayout.bank -> KotakLayout.narration(row.narration)
        else -> NarrationInfo(null, false, io.github.chiragjjjks.credu1ar.core.model.Instrument.UNKNOWN, null, emptySet())
    }
}

/**
 * Pairs statement rows with payments already captured from SMS or notifications, so one payment is never counted twice.
 * One-to-one: each existing row is claimed at most once.
 */
object StatementMatch {
    data class Candidate(
        val id: Long,
        val direction: Direction,
        val amountPaise: Long,
        val date: LocalDate,
        val reference: String?,
        val accountTail: String?,
    )

    /**
     * Index into [items] → id of the existing row it is the same payment as. Pure; each candidate is claimed once.
     *  1. Same UPI reference (RRN), direction and amount: the same payment whatever the dates say.
     *  2. Otherwise same direction and amount, account tails that don't clash, and dates within ±1 day, same-day
     *     pairs first. Two ₹40 chais on one day with one SMS: one is matched, the other stays a new row.
     *     Two known RRNs that differ are never the same payment.
     */
    fun assign(items: List<StatementTxns.Item>, candidates: List<Candidate>): Map<Int, Long> {
        val out = LinkedHashMap<Int, Long>()
        val claimed = HashSet<Int>()
        fun sameMoney(i: StatementTxns.Item, c: Candidate) =
            i.accepted.parsed.direction == c.direction && i.accepted.parsed.amountPaise == c.amountPaise
        fun claim(itemIndex: Int, ok: (Candidate) -> Boolean) {
            val ci = candidates.indices.firstOrNull { it !in claimed && ok(candidates[it]) } ?: return
            claimed += ci
            out[itemIndex] = candidates[ci].id
        }

        items.forEachIndexed { ii, item ->
            val ref = rrn(item.accepted.parsed.reference) ?: return@forEachIndexed
            claim(ii) { c -> rrn(c.reference) == ref && sameMoney(item, c) }
        }
        for (gap in 0L..1L) items.forEachIndexed { ii, item ->
            if (ii in out) return@forEachIndexed
            val ref = rrn(item.accepted.parsed.reference)
            claim(ii) { c ->
                sameMoney(item, c) && !tailsClash(item.accepted.parsed.accountTail, c.accountTail) &&
                    abs(ChronoUnit.DAYS.between(item.row.date, c.date)) == gap &&
                    !(ref != null && rrn(c.reference).let { it != null && it != ref })
            }
        }
        return out
    }

    /** A UPI reference: 10–14 digits and nothing else (Kotak's own `UPI-7000…` column is not one). */
    private fun rrn(ref: String?): String? = ref?.trim()?.takeIf { it.length in 10..14 && it.all(Char::isDigit) }

    /** Both tails known and neither ends with the other ("7890" vs "890" is fine; "7890" vs "1234" clashes). */
    private fun tailsClash(a: String?, b: String?): Boolean {
        val x = a?.filter(Char::isDigit).orEmpty()
        val y = b?.filter(Char::isDigit).orEmpty()
        return x.isNotEmpty() && y.isNotEmpty() && !x.endsWith(y) && !y.endsWith(x)
    }
}
