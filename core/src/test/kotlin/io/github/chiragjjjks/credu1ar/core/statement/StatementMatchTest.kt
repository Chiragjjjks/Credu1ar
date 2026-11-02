package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.model.Direction.CREDIT
import io.github.chiragjjjks.credu1ar.core.model.Direction.DEBIT
import io.github.chiragjjjks.credu1ar.core.statement.StatementKit.row
import io.github.chiragjjjks.credu1ar.core.statement.StatementKit.statement
import io.github.chiragjjjks.credu1ar.core.statement.StatementMatch.Candidate
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatementMatchTest {
    private val items = StatementTxns.accept(StatementKit.fixture(StatementKit.SEP))
    private fun indexOf(payee: String) = items.indexOfFirst { it.row.narration.contains(payee) }
    private val sep21 = LocalDate.of(2026, 9, 21)

    @Test fun theSameUpiReferenceIsTheSamePaymentWhateverTheDate() {
        // Swiggy, ₹326 on 18 Sep, RRN 610000000006; the SMS row is dated two days later.
        val sms = Candidate(11, DEBIT, 32_600, LocalDate.of(2026, 9, 20), "610000000006", "7890")
        assertEquals(mapOf(indexOf("Swiggy Limited") to 11L), StatementMatch.assign(items, listOf(sms)))
    }

    @Test fun twoIdenticalPaymentsAndOneSmsMakeExactlyOneMatch() {
        val chai = items.indices.filter { items[it].row.narration.contains("Chai Point") }
        assertEquals(2, chai.size)
        val m = StatementMatch.assign(items, listOf(Candidate(21, DEBIT, 4_000, sep21, null, "7890")))
        assertEquals(mapOf(chai[0] to 21L), m)
        // With two SMS rows, both are matched, one each.
        val two = StatementMatch.assign(items, listOf(Candidate(21, DEBIT, 4_000, sep21, null, "7890"), Candidate(22, DEBIT, 4_000, sep21, null, null)))
        assertEquals(mapOf(chai[0] to 21L, chai[1] to 22L), two)
    }

    @Test fun anotherAccountsTailNeverMatches() {
        assertEquals(emptyMap(), StatementMatch.assign(items, listOf(Candidate(31, DEBIT, 4_000, sep21, null, "1111"))))
        // A shorter tail of the same account is fine.
        assertEquals(1, StatementMatch.assign(items, listOf(Candidate(32, DEBIT, 4_000, sep21, null, "890"))).size)
    }

    @Test fun twoDaysApartIsNotTheSamePayment() {
        assertEquals(emptyMap(), StatementMatch.assign(items, listOf(Candidate(41, DEBIT, 4_000, sep21.plusDays(2), null, "7890"))))
        assertEquals(1, StatementMatch.assign(items, listOf(Candidate(42, DEBIT, 4_000, sep21.plusDays(1), null, "7890"))).size)
    }

    @Test fun differentReferencesOrDirectionsNeverMatch() {
        assertEquals(emptyMap(), StatementMatch.assign(items, listOf(Candidate(51, DEBIT, 4_000, sep21, "699999999999", "7890"))))
        assertEquals(emptyMap(), StatementMatch.assign(items, listOf(Candidate(52, CREDIT, 4_000, sep21, null, "7890"))))
    }

    @Test fun aSameDayPairWinsOverANextDayPair() {
        val s = statement(
            "Asha K",
            row("UPI/SHOP ONE/YESB/-/UPI", 50_000, date = LocalDate.of(2026, 10, 5)),
            row("UPI/SHOP TWO/YESB/-/UPI", 50_000, date = LocalDate.of(2026, 10, 6)),
        )
        val m = StatementMatch.assign(StatementTxns.accept(s), listOf(Candidate(61, DEBIT, 50_000, LocalDate.of(2026, 10, 6), null, null)))
        assertEquals(mapOf(1 to 61L), m)
    }
}
