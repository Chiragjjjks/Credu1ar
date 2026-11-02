package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.category.REVIEW_THRESHOLD
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.statement.StatementKit.row
import io.github.chiragjjjks.credu1ar.core.statement.StatementKit.statement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnNameTest {
    @Test fun theHoldersNameInAnyOrderOrWithInitialsMatches() {
        assertTrue(OwnName.matches("ASHA K", "Asha K"))
        assertTrue(OwnName.matches("K ASHA", "Asha K"))
        assertTrue(OwnName.matches("ASHA KUMARI", "Asha K"))
        assertTrue(OwnName.matches("K. ASHA", "Asha Kumari"))
        assertTrue(OwnName.matches("ASHA KUMARI SHA", "Asha Kumari Sharma"), "a cut-off surname is still a prefix")
        assertTrue(OwnName.matches("MRS ASHA K", "Asha K"))
    }

    @Test fun otherPeopleNeverMatch() {
        assertFalse(OwnName.matches("RAVI KUMAR", "Asha K"))
        assertFalse(OwnName.matches("ASHA RAO", "Asha Kumari"), "two different full surnames")
        assertFalse(OwnName.matches("PRIYA KUMARI", "Asha Kumari"), "a shared surname alone is not enough")
        assertFalse(OwnName.matches("ASHA", "Asha K"), "one word is too little to go on")
        assertFalse(OwnName.matches("ASHAK ENTERPRISES", "Asha K"))
        assertFalse(OwnName.matches("ASHA K", null))
        assertFalse(OwnName.matches(null, "Asha K"))
    }

    @Test fun ownNameTransfersAreSelfTransfersThatTheOwnerConfirms() {
        val s = statement(
            "Asha K",
            row("UPI/ASHA K/HDFC/610000000201/UPI", 500_000),
            row("UPI/K ASHA/ICIC/610000000202/UPI", 200_000),
            row("UPI/ASHA KUMARI/SBIN/610000000203/UPI", 100_000),
            row("UPI/RAVI KUMAR/HDFC/610000000204/UPI", 120_000),
        )
        val items = StatementTxns.accept(s)
        for (i in items.take(3)) {
            val a = i.accepted
            assertEquals(Category.SELF_TRANSFER, a.categorization.category)
            assertTrue(a.categorization.confidence < REVIEW_THRESHOLD)
            assertTrue(a.needsReview)
        }
        // The holder's printed name, in either order: "Own account", and the name is gone from the row.
        for (i in items.take(2)) {
            assertEquals("Own account", i.accepted.merchant.name)
            assertEquals("own_account", i.accepted.merchant.key)
            assertTrue("Own account" in i.row.narration)
        }
        // Only similar (could be someone else): keeps its own name and key, so a rule taught on it stays with that payee.
        assertEquals("Asha Kumari", items[2].accepted.merchant.name)
        assertTrue(items[2].accepted.merchant.key != "own_account")
        assertEquals(Category.SENT_TO_PEOPLE, items[3].accepted.categorization.category)
        val printed = Regex("""(?i)\basha\s+k\b|\bk\s+asha\b""")
        for (i in items) {
            assertFalse(printed.containsMatchIn(i.accepted.toString()), "holder name leaked into ${i.row.index}")
            assertFalse(printed.containsMatchIn(i.row.narration), "holder name left in the narration of ${i.row.index}")
        }
    }

    @Test fun theHoldersNameIsScrubbedFromEveryKindOfRow() {
        assertEquals("IMPS-612345678901-Own account-HDFC-XX5678-self", OwnName.scrub("IMPS-612345678901-ASHA K-HDFC-XX5678-self", "Asha K"))
        assertEquals("NEFT DR Own account HDFC", OwnName.scrub("NEFT DR K. ASHA HDFC", "Asha K"))
        assertEquals("UPI/ASHA KAPOOR/HDFC/1/UPI", OwnName.scrub("UPI/ASHA KAPOOR/HDFC/1/UPI", "Asha K"), "a longer name is someone else's")
    }

    @Test fun aRuleOnPaymentsWithoutAPayeeIsIgnored() {
        val c = io.github.chiragjjjks.credu1ar.core.category.Categorizer(mapOf("unknown" to Category.ATM_CASH))
        assertEquals(null, c.rule("unknown", io.github.chiragjjjks.credu1ar.core.model.Direction.DEBIT))
    }

    @Test fun noPaymentFromTheFixturesCarriesTheHoldersName() {
        for (name in listOf(StatementKit.SEP, StatementKit.OCT)) {
            val s = StatementKit.fixture(name)
            val holder = s.header.holderName!!
            for (i in StatementTxns.accept(s)) {
                assertFalse(i.accepted.toString().contains(holder, ignoreCase = true), "holder name in row ${i.row.index} of $name")
                assertFalse(i.row.narration.contains(holder, ignoreCase = true), "holder name in narration of row ${i.row.index} of $name")
            }
            assertTrue(StatementTxns.accept(s).count { it.accepted.categorization.category == Category.SELF_TRANSFER } == (if (name == StatementKit.SEP) 1 else 0))
        }
    }
}
