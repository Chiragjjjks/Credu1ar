package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.core.sample.SampleData
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleAndInsightsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 29, 15, 0).atZone(ist).toInstant().toEpochMilli()
    private val signals = SampleData.signals(now, months = 3)

    private val outcomes = signals.map { it to Pipeline.process(it) }

    private val txns: List<Txn> = outcomes.mapNotNull { (_, o) -> o as? Outcome.Txn }.mapIndexed { i, o ->
        val a = o.accepted
        Txn(
            i.toLong(), a.parsed.occurredAtMillis, a.parsed.direction, a.parsed.amountPaise, a.merchant.key, a.merchant.name,
            a.categorization.category, a.categorization.confidence, a.parsed.instrument, a.parsed.bank, a.parsed.accountTail, a.needsReview,
        )
    }

    private val sep = YearMonth.of(2026, 9)

    @Test fun everySampleMessageIsUnderstood() {
        val bad = outcomes.filter { it.second !is Outcome.Txn }.map { "${it.first.sender}: ${it.first.body.replace('\n', ' ')} -> ${it.second}" }
        assertTrue(bad.isEmpty(), "Sample messages the parser could not read:\n" + bad.joinToString("\n"))
        assertTrue(signals.size > 200, "expected a few hundred sample messages, got ${signals.size}")
    }

    @Test fun notSpendingKindsNeverCountAsSpend() {
        val s = Insights.monthSummary(txns, sep, now)
        val direct = txns.filter { it.isSpend && YearMonth.from(java.time.Instant.ofEpochMilli(it.occurredAtMillis).atZone(ist)) == sep }
        assertEquals(direct.sumOf { it.amountPaise }, s.spentPaise)
        assertTrue(direct.none { it.category.group == Group.MOVES || it.category.group == Group.INCOME })
        // Salary 85,000 + friend 1,200 + Zepto refund 312 are income; the card-bill payment credit is not.
        assertEquals(8_500_000L + 120_000L + 31_200L, s.incomePaise)
    }

    @Test fun groceriesAreSplitIntoTheKindsTheOwnerCaresAbout() {
        val s = Insights.monthSummary(txns, sep, now)
        val split = Insights.groceriesSplit(s)
        assertTrue(split.quickCommerce > 0, "quick commerce missing")
        assertTrue(split.provision > 0, "provision store missing")
        assertTrue(split.supermarket > 0, "supermarket missing")
        assertTrue(split.fresh > 0, "fresh & dairy missing")
        assertTrue(s.topMerchants.any { it.name == "Zepto" }, "Zepto should be a top merchant: ${s.topMerchants.map { it.name }}")
    }

    @Test fun chartOrderIsFixedNotSortedBySize() {
        val s = Insights.monthSummary(txns, sep, now)
        assertEquals(Group.spendOrder, s.byGroup.map { it.group })
        assertEquals(listOf(Group.FOOD, Group.GROCERIES, Group.TRANSPORT, Group.HOME_BILLS, Group.SHOPPING, Group.HEALTH, Group.FUN, Group.PEOPLE_CASH, Group.OTHER), Group.spendOrder)
    }

    @Test fun leaksFindTheHabits() {
        val leaks = Insights.leaks(txns, sep)
        assertTrue(leaks.any { it.category == Category.QUICK_COMMERCE && it.isCategoryLevel && it.count >= 15 }, "no quick-commerce leak: $leaks")
        assertTrue(leaks.any { it.category == Category.FOOD_DELIVERY }, "no food-delivery leak")
        assertEquals(leaks.sortedByDescending { it.totalPaise }, leaks)
    }

    @Test fun recurringDetectsSubscriptionsAndBills() {
        val names = Insights.recurring(txns, now).map { it.name }
        listOf("Netflix", "Spotify", "ACT Fibernet", "Airtel").forEach { assertTrue(it in names, "$it not detected as recurring: $names") }
        assertTrue("Zepto" !in names, "Zepto orders are irregular, not a subscription")
    }

    @Test fun peopleAndStoreNamesLandInReview() {
        val review = txns.filter { it.needsReview }
        assertTrue(review.any { it.category == Category.SENT_TO_PEOPLE }, "no person payments queued for review")
        // "RAHUL S" / "PRIYA M" use initials; they are still people, not "uncategorised".
        listOf("Rahul S", "Priya M", "Landlord Sharma").forEach { n ->
            assertEquals(Category.SENT_TO_PEOPLE, txns.first { it.merchantName == n }.category, n)
        }
        assertTrue(review.all { it.confidence < 0.65f })
        assertTrue(txns.none { it.needsReview && it.direction == io.github.chiragjjjks.credu1ar.core.model.Direction.CREDIT })
    }

    @Test fun oneBrandIsOneMerchantEvenWhenTheHandleRunsWordsTogether() {
        // "apollopharmacy@icici" and "APOLLO PHARMACY" must not become two merchants.
        val names = txns.filter { it.category == Category.PHARMACY }.map { it.merchantName }.distinct()
        assertEquals(listOf("Apollo Pharmacy"), names)
        assertTrue(txns.none { it.merchantName == "Unknown" }, "every sample payee should be named: ${txns.filter { it.merchantName == "Unknown" }}")
    }

    @Test fun monthlyRentToAPersonIsRecognisedAsRecurring() {
        val rent = Insights.recurring(txns, now).firstOrNull { it.name == "Landlord Sharma" }
        assertNotNull(rent, "monthly ₹28,000 to the same person should be recurring")
        assertEquals(2_800_000L, rent.amountPaise)
    }

    @Test fun forecastProjectsThisMonthOnlyWhileItIsRunning() {
        val oct = Insights.monthSummary(txns, YearMonth.of(2026, 10), now)
        assertEquals(29, oct.daysElapsed)
        assertTrue(oct.forecastPaise >= oct.spentPaise)
        val sepSummary = Insights.monthSummary(txns, sep, now)
        assertEquals(sepSummary.spentPaise, sepSummary.forecastPaise)
        assertEquals(30, sepSummary.daysElapsed)
    }

    @Test fun headlineIsPlainEnglishAndMentionsTheGrocerySplit() {
        val s = Insights.monthSummary(txns, sep, now)
        val h = Insights.headline(s, Insights.groceriesSplit(s))
        assertTrue(h.line1.startsWith("You've spent ₹"), h.line1)
        assertTrue(h.line2.isNotBlank())
        assertEquals("Nothing captured yet.", Insights.headline(Insights.monthSummary(emptyList(), sep, now)).line1)
    }

    @Test fun rhythmFindsLateNightFood() {
        val r = Insights.rhythm(txns, now - 90L * 24 * 3600 * 1000)
        val busiest = assertNotNull(r.busiestCell)
        assertTrue(busiest.second in 7..23, "busiest hour ${busiest.second}")
    }

    @Test fun userRuleOverridesTheGuess() {
        val sig = signals.first { it.body.contains("SRI LAKSHMI STORES", ignoreCase = true) }
        val plain = (Pipeline.process(sig) as Outcome.Txn).accepted
        assertEquals(Category.PROVISION, plain.categorization.category)
        val taught = (Pipeline.process(sig, mapOf(plain.merchant.key to Category.DINING_OUT)) as Outcome.Txn).accepted
        assertEquals(Category.DINING_OUT, taught.categorization.category)
        assertEquals(1.0f, taught.categorization.confidence)
        assertTrue(!taught.needsReview)
    }
}
