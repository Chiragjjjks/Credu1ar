package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.Accepted
import io.github.chiragjjjks.credu1ar.core.Pipeline
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.PayeeKind
import java.time.LocalDate
import java.time.ZoneId

/** Statement rows → the same [Accepted] payments the SMS path produces, so one categoriser and one set of rules serve both. */
object StatementTxns {
    /** One row ready to store. [key] identifies the row across files (see [key]); [occurredAtMillis] = noon IST + its place in the day, in seconds. */
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
            Item(row, if (k == 1) base else "$base#$k", at, Pipeline.accept(parsed(s.header, row, at), rules))
        }
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

    fun parsed(h: StatementHeader, row: StatementRow, occurredAtMillis: Long): ParsedTxn {
        val n = narration(h, row)
        return ParsedTxn(
            direction = row.direction,
            amountPaise = row.amountPaise,
            payeeName = n.payee,
            vpa = null,
            payeeKind = PayeeKind.UNKNOWN,
            accountTail = h.accountTail,
            instrument = n.instrument,
            bank = h.bank,
            reference = n.rrn ?: row.ref,
            occurredAtMillis = occurredAtMillis,
            balancePaise = row.balancePaise,
            hints = n.hints,
            confidence = 0.9f,
            payeeTruncated = n.payeeTruncated,
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

    /** Index into [items] → id of the existing row it is the same payment as. */
    fun assign(items: List<StatementTxns.Item>, candidates: List<Candidate>): Map<Int, Long> = emptyMap() // TODO(agent A): RRN pass, then amount+direction+date±1 pass
}
