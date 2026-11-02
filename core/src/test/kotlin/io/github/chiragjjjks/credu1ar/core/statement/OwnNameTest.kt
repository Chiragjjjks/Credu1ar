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
            assertEquals("Own account", a.merchant.name)
            assertEquals("own_account", a.merchant.key)
            assertTrue("Own account" in i.row.narration)
        }
        assertEquals(Category.SENT_TO_PEOPLE, items[3].accepted.categorization.category)
        for (i in items) {
            assertFalse(i.accepted.toString().contains("asha", ignoreCase = true), "holder name leaked into ${i.row.index}")
            assertFalse(i.row.narration.contains("asha", ignoreCase = true), "holder name left in the narration of ${i.row.index}")
        }
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
