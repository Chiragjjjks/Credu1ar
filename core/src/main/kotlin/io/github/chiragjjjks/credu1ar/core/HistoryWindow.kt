package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.insights.Insights
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * What the first SMS link reads from the inbox: everything received from [startMillis] until now.
 * [openOn] is the month Pulse should show right after, so the owner lands on real numbers, not an empty month.
 */
data class ImportWindow(val startMillis: Long, val includesPreviousMonth: Boolean, val openOn: YearMonth)

/**
 * The owner's rule (ADR-013): link SMS on the 15th and you get the 1st–15th; link it in the first week and the
 * month so far is too thin to stand alone, so the whole previous calendar month comes too. Calendar-based on
 * purpose: the same date always gives the same answer, whatever the parser can or cannot read. Always IST.
 */
object HistoryWindow {
    /** Linking on or before this day of the month also reads the whole previous month. */
    const val PREVIOUS_MONTH_THROUGH_DAY = 7

    /** Linking on or before this day opens Pulse on the previous month: the current one has nothing to show yet. */
    const val OPEN_PREVIOUS_THROUGH_DAY = 3

    fun firstLink(nowMillis: Long, zone: ZoneId = Insights.IST): ImportWindow {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val thisMonth = YearMonth.from(today)
        val withPrevious = today.dayOfMonth <= PREVIOUS_MONTH_THROUGH_DAY
        val from = if (withPrevious) thisMonth.minusMonths(1) else thisMonth
        val openOn = if (today.dayOfMonth <= OPEN_PREVIOUS_THROUGH_DAY) thisMonth.minusMonths(1) else thisMonth
        return ImportWindow(monthStart(from, zone), withPrevious, openOn)
    }

    /** The nearest month start strictly before [fromMillis]: one tap of "Add <month>" (a mid-month start completes its own month first). */
    fun monthEarlier(fromMillis: Long, zone: ZoneId = Insights.IST): Long {
        val own = monthStart(monthOf(fromMillis, zone), zone)
        return if (own < fromMillis) own else monthStart(monthOf(fromMillis, zone).minusMonths(1), zone)
    }

    fun monthOf(millis: Long, zone: ZoneId = Insights.IST): YearMonth = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    fun monthStart(month: YearMonth, zone: ZoneId = Insights.IST): Long = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
