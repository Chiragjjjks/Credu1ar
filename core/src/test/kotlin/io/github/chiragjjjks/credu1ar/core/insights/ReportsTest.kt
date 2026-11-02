package io.github.chiragjjjks.credu1ar.core.insights

import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.core.statement.StatementKit
import io.github.chiragjjjks.credu1ar.core.statement.StatementTxns
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reports over the two fictional Kotak statements (16 Sep–16 Oct and 16 Oct–16 Nov 2026) and made-up rows. */
class ReportsTest {
    private val first = StatementKit.fixture(StatementKit.SEP)
    private val second = StatementKit.fixture(StatementKit.OCT)
    private val firstRange = CoveredRange(first.header.from, first.header.to)
    private val secondRange = CoveredRange(second.header.from, second.header.to)

    /** What the database would hold: one row per key, so the shared 16 Oct rows count once. */
    private fun txns(vararg statements: io.github.chiragjjjks.credu1ar.core.statement.Statement): List<Txn> =
        statements.flatMap { StatementTxns.accept(it) }.distinctBy { it.key }.mapIndexed { i, item -> StatementTxns.txn(item, i + 1L) }

    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    @Test fun theFirstStatementCoversHalfOfSeptember() {
        val r = Reports.month(txns(first), sep, listOf(firstRange))
        val cov = assertNotNull(r.coverage)
        assertTrue(cov.partial)
        assertEquals(LocalDate.of(2026, 9, 16), cov.firstCovered)
        assertEquals(LocalDate.of(2026, 9, 30), cov.lastCovered)
        assertEquals(15, cov.coveredDays)
        assertEquals(30, cov.totalDays)
        assertEquals(15, r.days)
        assertNull(r.previousPaise)
        assertNull(r.changePct)
        // 16 debits that count as spending (the own-account transfer does not): ₹4,079.47.
        assertEquals(407_947, r.spentPaise)
        assertEquals(16, r.spendCount)
        assertEquals(27_200, r.perDayPaise, "₹4,079.47 over the 15 covered days, in whole rupees")
        assertEquals(7_534_900, r.incomePaise)
        assertEquals(listOf(Insights.CategoryTotal(Category.SELF_TRANSFER, 500_000, 1)), r.moves)
        assertEquals(Insights.Headline("You spent ₹4,079 from 16 to 30 September.", "That's 15 of 30 days."), Reports.headline(r))
    }

    @Test fun withOnlyTheFirstStatementOctoberIsCoveredUpToThe16th() {
        val r = Reports.month(txns(first), oct, listOf(firstRange))
        val cov = assertNotNull(r.coverage)
        assertTrue(cov.partial)
        assertEquals(LocalDate.of(2026, 10, 1), cov.firstCovered)
        assertEquals(LocalDate.of(2026, 10, 16), cov.lastCovered)
        assertEquals(16, cov.coveredDays)
        assertEquals(1_751_315, r.spentPaise)
        assertNull(r.previousPaise)
        assertEquals(Insights.Headline("You spent ₹17,513 from 1 to 16 October.", "That's 16 of 31 days."), Reports.headline(r))
    }

    @Test fun bothStatementsCoverAllOfOctober() {
        val t = txns(first, second)
        assertEquals(41, t.size)
        val ranges = listOf(firstRange, secondRange)
        val r = Reports.month(t, oct, ranges)
        val cov = assertNotNull(r.coverage)
        assertTrue(cov.full, "16 Sep–16 Oct plus 16 Oct–16 Nov covers every day of October")
        assertEquals(31, r.days)
        assertEquals(1_793_715, r.spentPaise)
        assertNull(r.previousPaise, "September is only partly covered, so there is no comparison")
        // People paid by UPI and the cut-off shop name are the unsorted bar; "led at" ignores it.
        assertEquals(1_559_515, r.unsortedPaise)
        assertEquals(3, r.unsortedCount)
        assertEquals(Group.SHOPPING, r.byGroup.first().group)
        assertFalse(r.byGroup.any { it.group == Group.PEOPLE_CASH || it.group == Group.OTHER })
        assertEquals(r.spentPaise, r.byGroup.sumOf { it.paise } + r.unsortedPaise)
        assertEquals(Insights.Headline("You spent ₹17,937 in October.", "₹15,595 of it isn't sorted yet."), Reports.headline(r))

        assertTrue(assertNotNull(Reports.month(t, sep, ranges).coverage).partial)
        val n = assertNotNull(Reports.month(t, nov, ranges).coverage)
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 16), n.firstCovered to n.lastCovered)
    }

    @Test fun aStatementPeriodIsToldInDays() {
        val r = Reports.report(txns(first), first.header.from, first.header.to, listOf(firstRange), isCalendarMonth = false)
        assertEquals(31, r.days)
        assertEquals(2_159_262, r.spentPaise)
        assertEquals(Insights.Headline("You spent ₹21,593 in these 31 days.", "₹16,795 of it isn't sorted yet."), Reports.headline(r))
    }

    // ---------------------------------------------------------------- made-up rows

    private fun spend(day: LocalDate, rupees: Long, c: Category, review: Boolean = false, key: String = c.id) = Txn(
        0, LocalDateTime.of(day, java.time.LocalTime.NOON).atZone(Insights.IST).toInstant().toEpochMilli(), Direction.DEBIT,
        rupees * 100, key, key, c, if (review) 0.5f else 0.9f, Instrument.UPI, "Kotak", "7890", review, Source.STATEMENT, timeKnown = false,
    )

    @Test fun twoFullyCoveredMonthsAreCompared() {
        val t = listOf(spend(LocalDate.of(2026, 9, 10), 1_000, Category.PROVISION), spend(LocalDate.of(2026, 10, 10), 1_500, Category.PROVISION))
        val r = Reports.month(t, oct, listOf(CoveredRange(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 11, 16))))
        assertTrue(assertNotNull(r.coverage).full)
        assertEquals(100_000, r.previousPaise)
        assertEquals(50, r.changePct)
        assertEquals(Insights.Headline("You spent ₹1,500 in October.", "Groceries led at ₹1,500."), Reports.headline(r))
        // The same rows with October only half covered: no comparison.
        assertNull(Reports.month(t, oct, listOf(CoveredRange(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 10, 16)))).previousPaise)
    }

    @Test fun ledAtIgnoresUnsortedUnlessUnsortedIsBigOrBiggest() {
        val from = LocalDate.of(2026, 9, 16)
        val to = LocalDate.of(2026, 10, 16)
        val ranges = listOf(CoveredRange(from, to))
        val metro = spend(from.plusDays(1), 900, Category.METRO_BUS)
        val friend = spend(from.plusDays(2), 100, Category.SENT_TO_PEOPLE, review = true, key = "a_friend")
        val small = Reports.report(listOf(metro, friend), from, to, ranges, isCalendarMonth = false)
        assertEquals(Insights.Headline("You spent ₹1,000 in these 31 days.", "Transport led at ₹900 (90%)."), Reports.headline(small))

        val quarter = Reports.report(listOf(metro, friend.copy(amountPaise = 30_000)), from, to, ranges, isCalendarMonth = false)
        assertEquals("₹300 of it isn't sorted yet.", Reports.headline(quarter).line2, "25% or more unsorted")

        val biggest = Reports.report(listOf(metro, friend.copy(amountPaise = 200_000)), from, to, ranges, isCalendarMonth = false)
        assertEquals(listOf(Group.TRANSPORT), biggest.byGroup.map { it.group })
        assertEquals("₹2,000 of it isn't sorted yet.", Reports.headline(biggest).line2)

        // A person the owner already confirmed is sorted: it is People & Cash, not unsorted.
        val confirmed = Reports.report(listOf(metro, friend.copy(amountPaise = 200_000, needsReview = false)), from, to, ranges, isCalendarMonth = false)
        assertEquals(0, confirmed.unsortedPaise)
        assertEquals("People & Cash led at ₹2,000 (69%).", Reports.headline(confirmed).line2)
    }

    @Test fun smsOnlyMonthsAndEmptyPeriodsReadNaturally() {
        val t = listOf(spend(LocalDate.of(2026, 10, 3), 250, Category.CAFES))
        assertEquals(Insights.Headline("You spent ₹250 in October.", "Food & Drink led at ₹250."), Reports.headline(Reports.month(t, oct, null)))
        assertEquals(Insights.Headline("You spent nothing in November.", ""), Reports.headline(Reports.month(t, nov, null)))
        val oneDay = Reports.month(t, oct, listOf(CoveredRange(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3))))
        assertEquals("You spent ₹250 on 3 October.", Reports.headline(oneDay).line1)
    }

    @Test fun statementRowsStayOutOfTheTimeOfDayChart() {
        val noon = spend(LocalDate.of(2026, 10, 3), 250, Category.CAFES)
        assertEquals(0L, Insights.rhythm(listOf(noon), 0).maxSpend)
        assertEquals(25_000L, Insights.rhythm(listOf(noon.copy(timeKnown = true)), 0).maxSpend)
    }
}
