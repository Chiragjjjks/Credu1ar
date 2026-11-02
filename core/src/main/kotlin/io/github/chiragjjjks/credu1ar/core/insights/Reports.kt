package io.github.chiragjjjks.credu1ar.core.insights

import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Money
import io.github.chiragjjjks.credu1ar.core.model.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Days for which we hold a complete record (a statement's period, or the SMS history window). Inclusive. */
data class CoveredRange(val from: LocalDate, val to: LocalDate)

/** How much of [from]..[to] (inclusive) is covered by statements/SMS history. */
data class Coverage(val from: LocalDate, val to: LocalDate, val coveredDays: Int, val firstCovered: LocalDate?, val lastCovered: LocalDate?) {
    val totalDays: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    val full: Boolean get() = coveredDays >= totalDays
    val partial: Boolean get() = coveredDays in 1 until totalDays
}

/**
 * The simple "what happened" report behind Pulse's statement and month views: a total, one bar per group, top places,
 * money that moved without being spent, and the money not sorted yet. Pure.
 */
object Reports {
    data class Report(
        val from: LocalDate,
        val to: LocalDate,
        val isCalendarMonth: Boolean,
        /** Null when coverage isn't tracked (SMS-only data): treated as fully covered. */
        val coverage: Coverage?,
        val spentPaise: Long,
        val incomePaise: Long,
        val spendCount: Int,
        /** Sorted spending by group, biggest first; groups with nothing are left out; unsorted money is not in here. */
        val byGroup: List<Insights.GroupTotal>,
        /** Debits still under review that we could only guess as "a person" or not at all. Shown as their own bar. */
        val unsortedPaise: Long,
        val unsortedCount: Int,
        val unsortedPayees: Int,
        val topMerchants: List<Insights.MerchantTotal>,
        /** Card bills, own-account transfers, investments: shown, never counted as spending. */
        val moves: List<Insights.CategoryTotal>,
        /** Spent ÷ days covered, in whole rupees. */
        val perDayPaise: Long,
        /** The period before this one, only when both are fully covered; otherwise null (no comparison shown). */
        val previousPaise: Long?,
    ) {
        val days: Int get() = coverage?.coveredDays ?: (ChronoUnit.DAYS.between(from, to) + 1).toInt()
        val changePct: Int? get() = previousPaise?.takeIf { it >= 50_000 }?.let { (((spentPaise - it) * 100.0) / it).toInt() }
        val isEmpty: Boolean get() = spendCount == 0 && incomePaise == 0L && moves.isEmpty()
    }

    fun coverage(ranges: List<CoveredRange>, from: LocalDate, to: LocalDate): Coverage {
        var days = 0
        var first: LocalDate? = null
        var last: LocalDate? = null
        var d = from
        while (!d.isAfter(to)) {
            if (ranges.any { !d.isBefore(it.from) && !d.isAfter(it.to) }) { days++; if (first == null) first = d; last = d }
            d = d.plusDays(1)
        }
        return Coverage(from, to, days, first, last)
    }

    fun month(txns: List<Txn>, month: YearMonth, ranges: List<CoveredRange>?, zone: ZoneId = Insights.IST): Report =
        report(txns, month.atDay(1), month.atEndOfMonth(), ranges, isCalendarMonth = true, zone = zone)

    fun report(txns: List<Txn>, from: LocalDate, to: LocalDate, ranges: List<CoveredRange>?, isCalendarMonth: Boolean, zone: ZoneId = Insights.IST): Report {
        fun Txn.day(): LocalDate = Instant.ofEpochMilli(occurredAtMillis).atZone(zone).toLocalDate()
        fun inRange(t: Txn, a: LocalDate, b: LocalDate) = t.day().let { !it.isBefore(a) && !it.isAfter(b) }

        val inPeriod = txns.filter { inRange(it, from, to) }
        val spend = inPeriod.filter { it.isSpend }
        val unsorted = spend.filter { it.needsReview && (it.category == Category.UNCATEGORISED || it.category == Category.SENT_TO_PEOPLE) }
        val sorted = spend - unsorted.toSet()
        val spent = spend.sumOf { it.amountPaise }

        val byGroup = sorted.groupBy { it.category.group }
            .map { (g, l) -> Insights.GroupTotal(g, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.paise }
        val top = spend.groupBy { it.merchantKey }
            .map { (k, l) -> Insights.MerchantTotal(k, l.first().merchantName, l.maxByOrNull { it.amountPaise }!!.category, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.paise }.take(5)
        val moves = inPeriod.filter { it.category.group == Group.MOVES && it.direction == io.github.chiragjjjks.credu1ar.core.model.Direction.DEBIT }
            .groupBy { it.category }.map { (c, l) -> Insights.CategoryTotal(c, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.paise }

        val cov = ranges?.let { coverage(it, from, to) }
        val days = cov?.coveredDays ?: (ChronoUnit.DAYS.between(from, to) + 1).toInt()
        val perDay = if (days > 0) (spent / days + 50) / 100 * 100 else 0

        // Compare only like with like: the previous calendar month (or the same-length period before), both fully covered.
        val (pFrom, pTo) = if (isCalendarMonth) YearMonth.from(from).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            else (ChronoUnit.DAYS.between(from, to) + 1).let { len -> from.minusDays(len) to from.minusDays(1) }
        val bothFull = ranges == null || (cov?.full == true && coverage(ranges, pFrom, pTo).full)
        val prevSpend = txns.filter { it.isSpend && inRange(it, pFrom, pTo) }
        val previous = if (bothFull && prevSpend.isNotEmpty()) prevSpend.sumOf { it.amountPaise } else null

        return Report(
            from = from, to = to, isCalendarMonth = isCalendarMonth, coverage = cov,
            spentPaise = spent, incomePaise = inPeriod.filter { it.isIncome }.sumOf { it.amountPaise }, spendCount = spend.size,
            byGroup = byGroup, unsortedPaise = unsorted.sumOf { it.amountPaise }, unsortedCount = unsorted.size,
            unsortedPayees = unsorted.map { it.merchantKey }.distinct().size,
            topMerchants = top, moves = moves, perDayPaise = perDay, previousPaise = previous,
        )
    }

    /** A calendar month that statements cover only in part ("16 to 30 September"): no comparison, its own wording. */
    fun isPartMonth(r: Report): Boolean = r.isCalendarMonth && r.coverage?.partial == true

    /**
     * The two headline lines above the total. Amounts are whole rupees.
     *  - a fully covered month: "You spent ₹38,420 in October." / "Food & Drink led at ₹9,860."
     *  - a part month: "You spent ₹4,079 from 16 to 30 September." / "That's 15 of 30 days."
     *  - a statement period: "You spent ₹21,593 in these 31 days." / "Transport led at ₹3,100 (14%)."
     * When money not sorted yet is bigger than the leading group, or at least 25% of spending, line 2 says
     * "₹15,595 of it isn't sorted yet." instead (a part month keeps its days line). "Led at" never counts unsorted money.
     */
    fun headline(r: Report, monthName: (Month) -> String = { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }): Insights.Headline {
        val cov = r.coverage
        val part = isPartMonth(r) && cov?.firstCovered != null && cov.lastCovered != null
        val period = when {
            part -> {
                val a = cov!!.firstCovered!!
                val b = cov.lastCovered!!
                if (a == b) "on ${a.dayOfMonth} ${monthName(a.month)}" else "from ${a.dayOfMonth} to ${b.dayOfMonth} ${monthName(b.month)}"
            }
            r.isCalendarMonth -> "in ${monthName(r.from.month)}"
            else -> "in these ${r.days} days"
        }
        val line1 = if (r.spendCount == 0) "You spent nothing $period." else "You spent ${wholeRupees(r.spentPaise)} $period."
        val top = r.byGroup.firstOrNull()
        val unsortedLeads = r.unsortedPaise > 0 && (top == null || r.unsortedPaise > top.paise || r.unsortedPaise * 4 >= r.spentPaise)
        val line2 = when {
            part -> "That's ${cov!!.coveredDays} of ${cov.totalDays} days."
            r.spendCount == 0 -> ""
            unsortedLeads -> "${wholeRupees(r.unsortedPaise)} of it isn't sorted yet."
            top == null -> ""
            r.isCalendarMonth -> "${top.group.label} led at ${wholeRupees(top.paise)}."
            else -> "${top.group.label} led at ${wholeRupees(top.paise)} (${(top.paise * 100 + r.spentPaise / 2) / r.spentPaise}%)."
        }
        return Insights.Headline(line1, line2)
    }

    private fun wholeRupees(paise: Long): String = Money.format((paise + 50) / 100 * 100)
}
