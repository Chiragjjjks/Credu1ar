package io.github.chiragjjjks.credu1ar.core.insights

import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Money
import io.github.chiragjjjks.credu1ar.core.model.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/** Everything the analysis screens need, computed from a plain list of transactions. Pure and fast (hundreds of rows). */
object Insights {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun Txn.date(zone: ZoneId = IST): LocalDate = Instant.ofEpochMilli(occurredAtMillis).atZone(zone).toLocalDate()
    private fun Txn.month(): YearMonth = YearMonth.from(date())

    // ------------------------------------------------------------------------------------------------ month summary

    data class GroupTotal(val group: Group, val paise: Long, val count: Int)
    data class CategoryTotal(val category: Category, val paise: Long, val count: Int)
    data class MerchantTotal(val key: String, val name: String, val category: Category, val paise: Long, val count: Int)

    data class MonthSummary(
        val month: YearMonth,
        val spentPaise: Long,
        val incomePaise: Long,
        val spendCount: Int,
        /** Spending groups in the fixed chart order (not sorted by size); groups with no spend are included with 0. */
        val byGroup: List<GroupTotal>,
        /** Non-zero categories, biggest first. */
        val byCategory: List<CategoryTotal>,
        val topMerchants: List<MerchantTotal>,
        /** Index 0 = day 1. */
        val dailySpendPaise: LongArray,
        val daysElapsed: Int,
        val daysInMonth: Int,
        val dailyBurnPaise: Long,
        /** Month-end projection at the current pace; equals [spentPaise] once the month is over. */
        val forecastPaise: Long,
        /** Spend in the previous month up to the same day, for an honest "vs last month" comparison. */
        val prevSamePointPaise: Long,
        val prevMonthTotalPaise: Long,
    ) {
        /** Percentage change vs the same point last month, or null when there is nothing to compare. */
        val changePct: Int? get() = if (prevSamePointPaise >= 50_000) (((spentPaise - prevSamePointPaise) * 100.0) / prevSamePointPaise).toInt() else null
        val isEmpty: Boolean get() = spendCount == 0 && incomePaise == 0L
    }

    fun monthSummary(txns: List<Txn>, month: YearMonth, nowMillis: Long, zone: ZoneId = IST): MonthSummary {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val inMonth = txns.filter { YearMonth.from(it.date(zone)) == month }
        val spend = inMonth.filter { it.isSpend }
        val daysInMonth = month.lengthOfMonth()
        val thisMonthNow = YearMonth.from(today) == month
        val daysElapsed = if (thisMonthNow) today.dayOfMonth else if (month.isBefore(YearMonth.from(today))) daysInMonth else 0

        val daily = LongArray(daysInMonth)
        spend.forEach { daily[it.date(zone).dayOfMonth - 1] += it.amountPaise }
        val spent = spend.sumOf { it.amountPaise }

        val byGroupMap = spend.groupBy { it.category.group }
        val byGroup = Group.spendOrder.map { g -> GroupTotal(g, byGroupMap[g].orEmpty().sumOf { it.amountPaise }, byGroupMap[g].orEmpty().size) }
        val byCategory = spend.groupBy { it.category }
            .map { (c, l) -> CategoryTotal(c, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.paise }
        val topMerchants = spend.groupBy { it.merchantKey }
            .map { (k, l) -> MerchantTotal(k, l.first().merchantName, l.maxByOrNull { it.amountPaise }!!.category, l.sumOf { it.amountPaise }, l.size) }
            .sortedByDescending { it.paise }.take(8)

        val burn = if (daysElapsed > 0) spent / daysElapsed else 0
        val forecast = if (thisMonthNow && daysElapsed in 3 until daysInMonth) spent * daysInMonth / daysElapsed else spent

        val prev = month.minusMonths(1)
        val prevSpend = txns.filter { it.isSpend && YearMonth.from(it.date(zone)) == prev }
        val prevSame = prevSpend.filter { it.date(zone).dayOfMonth <= (if (daysElapsed == 0) daysInMonth else daysElapsed) }.sumOf { it.amountPaise }

        return MonthSummary(
            month = month, spentPaise = spent, incomePaise = inMonth.filter { it.isIncome }.sumOf { it.amountPaise },
            spendCount = spend.size, byGroup = byGroup, byCategory = byCategory, topMerchants = topMerchants,
            dailySpendPaise = daily, daysElapsed = daysElapsed, daysInMonth = daysInMonth, dailyBurnPaise = burn,
            forecastPaise = forecast, prevSamePointPaise = prevSame, prevMonthTotalPaise = prevSpend.sumOf { it.amountPaise },
        )
    }

    // ------------------------------------------------------------------------------------------------ groceries split

    data class GroceriesSplit(val quickCommerce: Long, val provision: Long, val supermarket: Long, val fresh: Long) {
        val total get() = quickCommerce + provision + supermarket + fresh
    }

    fun groceriesSplit(s: MonthSummary): GroceriesSplit {
        fun of(c: Category) = s.byCategory.firstOrNull { it.category == c }?.paise ?: 0
        return GroceriesSplit(of(Category.QUICK_COMMERCE), of(Category.PROVISION), of(Category.SUPERMARKET), of(Category.FRESH_DAIRY))
    }

    // ------------------------------------------------------------------------------------------------ movers

    data class Mover(val category: Category, val nowPaise: Long, val prevPaise: Long) {
        val deltaPaise get() = nowPaise - prevPaise
        val pct: Int? get() = if (prevPaise >= 30_000) ((deltaPaise * 100.0) / prevPaise).toInt() else null
    }

    /** Categories that moved most vs the same point last month. Ignores moves under ₹500. */
    fun movers(txns: List<Txn>, month: YearMonth, nowMillis: Long, limit: Int = 4, zone: ZoneId = IST): List<Mover> {
        val s = monthSummary(txns, month, nowMillis, zone)
        val upTo = if (s.daysElapsed == 0) s.daysInMonth else s.daysElapsed
        val prev = month.minusMonths(1)
        val now = txns.filter { it.isSpend && YearMonth.from(it.date(zone)) == month }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val before = txns.filter { it.isSpend && YearMonth.from(it.date(zone)) == prev && it.date(zone).dayOfMonth <= upTo }
            .groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        return (now.keys + before.keys).map { Mover(it, now[it] ?: 0, before[it] ?: 0) }
            .filter { abs(it.deltaPaise) >= 50_000 }
            .sortedByDescending { abs(it.deltaPaise) }.take(limit)
    }

    // ------------------------------------------------------------------------------------------------ leaks (small + frequent)

    data class Leak(val label: String, val category: Category, val count: Int, val avgPaise: Long, val totalPaise: Long, val isCategoryLevel: Boolean)

    private val habitCategories = setOf(Category.FOOD_DELIVERY, Category.QUICK_COMMERCE, Category.CAFES, Category.CAB_AUTO)

    /**
     * Money that disappears in small, frequent purchases: whole habit categories (5+ orders) and any single merchant
     * visited 5+ times at an average under ₹500, outside those categories. Biggest total first.
     */
    fun leaks(txns: List<Txn>, month: YearMonth, zone: ZoneId = IST): List<Leak> {
        val spend = txns.filter { it.isSpend && YearMonth.from(it.date(zone)) == month }
        val habits = spend.filter { it.category in habitCategories }.groupBy { it.category }
            .filter { it.value.size >= 5 }
            .map { (c, l) -> Leak(c.label, c, l.size, l.sumOf { it.amountPaise } / l.size, l.sumOf { it.amountPaise }, true) }
        val others = spend.filter { it.category !in habitCategories }.groupBy { it.merchantKey }
            .filter { (_, l) -> l.size >= 5 && l.sumOf { it.amountPaise } / l.size <= 50_000 }
            .map { (_, l) -> Leak(l.first().merchantName, l.first().category, l.size, l.sumOf { it.amountPaise } / l.size, l.sumOf { it.amountPaise }, false) }
        return (habits + others).sortedByDescending { it.totalPaise }
    }

    // ------------------------------------------------------------------------------------------------ recurring

    data class Recurring(
        val merchantKey: String, val name: String, val category: Category,
        val amountPaise: Long, val cadenceDays: Int, val count: Int,
        val lastAtMillis: Long, val nextAtMillis: Long,
    ) {
        /** Rough monthly cost so weekly and yearly items rank fairly. */
        val monthlyPaise: Long get() = amountPaise * 30 / cadenceDays
    }

    /** Merchants paid on a steady rhythm (weekly, monthly, quarterly, yearly) with a steady-ish amount. Needs 3+ payments. */
    fun recurring(txns: List<Txn>, nowMillis: Long): List<Recurring> {
        val day = 24L * 3600 * 1000
        // People are included on purpose: the same amount to the same person every month is almost always rent.
        return txns.filter { it.isSpend && it.category != Category.ATM_CASH }
            .groupBy { it.merchantKey }
            .mapNotNull { (key, list) ->
                if (list.size < 3) return@mapNotNull null
                val sorted = list.sortedBy { it.occurredAtMillis }
                val gaps = sorted.zipWithNext { a, b -> (b.occurredAtMillis - a.occurredAtMillis) / day }
                val medianGap = gaps.sorted()[gaps.size / 2].toInt()
                val cadence = when (medianGap) {
                    in 6..8 -> 7
                    in 26..35 -> 30
                    in 85..95 -> 91
                    in 355..375 -> 365
                    else -> return@mapNotNull null
                }
                val amounts = sorted.map { it.amountPaise }.sorted()
                val median = amounts[amounts.size / 2]
                val steady = amounts.count { abs(it - median) <= median / 4 }
                if (steady * 100 / amounts.size < 70) return@mapNotNull null
                val last = sorted.last().occurredAtMillis
                val next = last + cadence * day
                if (nowMillis > last + (cadence * 1.6 * day).toLong()) return@mapNotNull null // lapsed
                Recurring(key, sorted.last().merchantName, sorted.last().category, median, cadence, sorted.size, last, next)
            }
            .sortedByDescending { it.monthlyPaise }
    }

    // ------------------------------------------------------------------------------------------------ rhythm

    /** [spendPaise] and [count] indexed `[dayOfWeek 0=Mon..6=Sun][hour 0..23]` over the given months. */
    class Rhythm(val spendPaise: Array<LongArray>, val count: Array<IntArray>) {
        val maxSpend: Long get() = spendPaise.maxOf { it.maxOrNull() ?: 0 }
        val busiestCell: Pair<Int, Int>? get() {
            var best: Pair<Int, Int>? = null
            var v = 0L
            for (d in 0..6) for (h in 0..23) if (spendPaise[d][h] > v) { v = spendPaise[d][h]; best = d to h }
            return best
        }
    }

    fun rhythm(txns: List<Txn>, sinceMillis: Long, zone: ZoneId = IST): Rhythm {
        val spend = Array(7) { LongArray(24) }
        val cnt = Array(7) { IntArray(24) }
        for (t in txns) {
            if (!t.isSpend || t.occurredAtMillis < sinceMillis) continue
            val z = Instant.ofEpochMilli(t.occurredAtMillis).atZone(zone)
            spend[z.dayOfWeek.value - 1][z.hour] += t.amountPaise
            cnt[z.dayOfWeek.value - 1][z.hour]++
        }
        return Rhythm(spend, cnt)
    }

    // ------------------------------------------------------------------------------------------------ plain-English headline

    data class Headline(val line1: String, val line2: String)

    fun headline(s: MonthSummary, split: GroceriesSplit = GroceriesSplit(0, 0, 0, 0)): Headline {
        if (s.spendCount == 0) return Headline("Nothing captured yet.", "Payments you make will appear here the moment the SMS lands.")
        val spent = Money.format(s.spentPaise)
        val change = s.changePct
        val line1 = when {
            change == null -> "You've spent $spent so far this month."
            change > 3 -> "You've spent $spent — $change% more than this time last month."
            change < -3 -> "You've spent $spent — ${-change}% less than this time last month."
            else -> "You've spent $spent — about the same as this time last month."
        }
        val top = s.byGroup.filter { it.paise > 0 }.maxByOrNull { it.paise }
        val share = if (top != null && s.spentPaise > 0) (top.paise * 100 / s.spentPaise).toInt() else 0
        val line2 = when {
            top == null -> ""
            top.group == Group.GROCERIES && split.total > 0 -> {
                val qc = (split.quickCommerce * 100 / split.total).toInt()
                val pr = (split.provision * 100 / split.total).toInt()
                "Groceries led at ${Money.format(top.paise)} ($share%): $qc% quick commerce, $pr% provision store."
            }
            top.group == Group.FOOD -> {
                val delivery = s.byCategory.firstOrNull { it.category == Category.FOOD_DELIVERY }
                if (delivery != null && delivery.paise > 0) "Food led at ${Money.format(top.paise)} ($share%), ${Money.format(delivery.paise)} of it delivery."
                else "Food & Drink led at ${Money.format(top.paise)} ($share%)."
            }
            else -> "${top.group.label} led at ${Money.format(top.paise)} ($share% of spending)."
        }
        return Headline(line1, line2)
    }
}
