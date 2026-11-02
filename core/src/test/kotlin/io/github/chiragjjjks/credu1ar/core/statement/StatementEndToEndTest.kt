package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Category.*
import io.github.chiragjjjks.credu1ar.core.model.Group
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Both fictional Kotak statements, page text → rows → payments, the way the app imports them. Every row's category,
 * merchant name and review flag is pinned here, so a categoriser change that moves any row has to say so.
 * Nothing is printed.
 */
class StatementEndToEndTest {
    private data class Row(val index: Int, val category: Category, val merchant: String, val review: Boolean = false)

    private val first = listOf(
        Row(1, SUBSCRIPTIONS, "Google Play"),
        Row(2, MONEY_IN, "Acme Software Ic"),            // NEFT IN from a company
        Row(3, SENT_TO_PEOPLE, "Ravi Kumar", review = true),
        Row(4, METRO_BUS, "Namma Metro"),                  // "BANGALORE METR", cut short
        Row(5, METRO_BUS, "BMTC"),
        Row(6, STREAMING, "Netflix"),
        Row(7, FOOD_DELIVERY, "Swiggy"),
        Row(8, QUICK_COMMERCE, "Zepto"),
        Row(9, CAFES, "Fresh Juice Cor"),
        Row(10, MONEY_IN, "Priya M"),
        Row(11, PHARMACY, "Apollo Pharmacy"),
        Row(12, PROVISION, "Sri Lakshmi Stor", review = true), // "Stor" may be "Store": a guess, so review
        Row(13, CAFES, "Chai Point"),
        Row(14, CAFES, "Chai Point"),
        Row(15, MONEY_IN, "Navi"),
        Row(16, REFUND, "Cashback"),
        Row(17, CAB_AUTO, "Uber"),
        Row(18, DINING_OUT, "Kiran Tiffin C", review = true),
        Row(19, FOOD_DELIVERY, "Zomato"),
        Row(20, BANK_CHARGES, "Bank charges"),
        Row(21, SELF_TRANSFER, "Own account", review = true),  // paid to the holder's own name
        Row(22, MONEY_IN, "Globex Labs"),
        Row(23, INTEREST, "Interest"),
        Row(24, SENT_TO_PEOPLE, "Meena Rao", review = true),
        Row(25, CARD_BILL, "CRED"),
        Row(26, CAFES, "Ganesh Bakery"),
        Row(27, UNCATEGORISED, "Shoprite Ret", review = true),  // a shop, not a person
        Row(28, REFUND, "Namma Metro"),                    // metro "Refund for U"
        Row(29, FOOD_DELIVERY, "Swiggy"),
        Row(30, METRO_BUS, "Namma Metro"),
        Row(31, ONLINE_SHOPPING, "Amazon"),
        Row(32, REFUND, "Cashback"),
        Row(33, MONEY_IN, "Ravi Kumar"),
        Row(34, SUBSCRIPTIONS, "Google Play"),
        Row(35, SENT_TO_PEOPLE, "Priya M", review = true),
    )

    private val second = listOf(
        Row(1, SUBSCRIPTIONS, "Google Play"),
        Row(2, SENT_TO_PEOPLE, "Priya M", review = true),
        Row(3, METRO_BUS, "BMTC"),
        Row(4, FOOD_DELIVERY, "Swiggy"),
        Row(5, MONEY_IN, "Acme Software Ic"),
        Row(6, SENT_TO_PEOPLE, "Meena Rao", review = true),
        Row(7, QUICK_COMMERCE, "Zepto"),
        Row(8, CAFES, "Chai Point"),
    )

    private fun check(fixture: String, expected: List<Row>) {
        val s = StatementKit.fixture(fixture)
        assertTrue(s.complete, "$fixture: ${s.issues}")
        val items = StatementTxns.accept(s)
        assertEquals(expected.map { it.index }, items.map { it.row.index }, "$fixture: every row is pinned, in order")
        val failures = items.zip(expected).mapNotNull { (item, e) ->
            val a = item.accepted
            val got = Row(item.row.index, a.categorization.category, a.merchant.name, a.needsReview)
            if (got == e) null else "$fixture row ${e.index}: expected $e but was $got"
        }
        assertTrue(failures.isEmpty(), "\n" + failures.joinToString("\n"))
        // Credits are never queued for review; not-spending rows are never spending.
        assertTrue(items.none { it.accepted.needsReview && it.accepted.parsed.direction == io.github.chiragjjjks.credu1ar.core.model.Direction.CREDIT })
        assertTrue(items.map { StatementTxns.txn(it) }.none { it.isSpend && it.category.group == Group.MOVES })
    }

    @Test fun everyRowOfTheFirstStatementLandsInTheRightCategory() = check(StatementKit.SEP, first)

    @Test fun everyRowOfTheSecondStatementLandsInTheRightCategory() = check(StatementKit.OCT, second)
}
