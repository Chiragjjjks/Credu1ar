package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class KotakLayoutTest {
    private fun pages(name: String): List<String> =
        (javaClass.getResource("/statements/$name") ?: fail("missing fixture $name")).readText().split("\n\u000C\n")

    private val sep = "kotak-asha-2026-09-16_2026-10-16.txt"
    private val oct = "kotak-asha-2026-10-16_2026-11-16.txt"

    private fun parse(p: List<String>): Statement = when (val r = Statements.parse(p)) {
        is StatementParse.Parsed -> r.statement
        else -> fail("not parsed: $r")
    }

    @Test fun readsEveryRowAndProvesTheStatementComplete() {
        val s = parse(pages(sep))
        assertEquals("Kotak", s.header.bank)
        assertEquals(LocalDate.of(2026, 9, 16), s.header.from)
        assertEquals(LocalDate.of(2026, 10, 16), s.header.to)
        assertEquals("7890", s.header.accountTail)
        assertTrue(s.header.holderName.equals("Asha K", ignoreCase = true))
        assertEquals(35, s.rows.size)
        assertEquals((1..35).toList(), s.rows.map { it.index })
        assertTrue(s.issues.isEmpty(), "issues: ${s.issues}")
        assertTrue(s.complete)
        assertEquals(125_040, s.openingPaise)
        assertEquals(4_221_778, s.closingPaise)
        assertEquals(3_504_262, s.rows.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise })
        assertEquals(7_601_000, s.rows.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise })
    }

    @Test fun wrappedNarrationsAreJoinedAndRefsSplitOff() {
        val rows = parse(pages(sep)).rows
        assertEquals("UPI/Google Play/utib/610000000001/MandateExecu", rows[0].narration)
        assertEquals("UPI-700000000101", rows[0].ref)
        assertEquals("NEFT IN N260000000000001 ACME SOFTWARE PRIVATE LIMITED IC", rows[1].narration)
        assertEquals(Direction.CREDIT, rows[1].direction)
        val tiffin = rows.first { it.narration.contains("Kiran") }
        assertEquals("UPI/Kiran Tiffin C/YESB/610000000016/UPI(Value Date: 26-09- 2026)", tiffin.narration)
        val cashback = rows.first { it.narration.startsWith("CASHBACK") }
        assertEquals("CASHBACK EARNED", cashback.narration)
        assertEquals("ONBF-a1b2c3d4e5f6a7b", cashback.ref)
        val fee = rows.first { it.narration.startsWith("CHRG") }
        assertEquals("CHRG:SMS ALERT FEE FOR THE MONTH OF AUG26", fee.narration)
        assertEquals(1558, fee.amountPaise)
        assertEquals(Direction.DEBIT, fee.direction)
    }

    @Test fun headerAndFooterLinesNeverLeakIntoRows() {
        val s = parse(pages(sep))
        for (r in s.rows) {
            assertFalse(r.narration.contains("1234567890"), "full account number in row ${r.index}")
            assertFalse(r.narration.contains("Account No") || r.narration.contains("Account Statement"), "footer in row ${r.index}")
            assertFalse(r.narration.contains("Statement Generated"), "footer in row ${r.index}")
        }
        assertEquals("Int.Pd:XX7890:01-07-2026 to 30-09-2026", s.rows.first { it.narration.startsWith("Int.Pd") }.narration)
    }

    @Test fun twoIdenticalPaymentsOnOneDayStayTwoWithDifferentKeys() {
        val s = parse(pages(sep))
        val chai = s.rows.filter { it.narration.contains("Chai Point") }
        assertEquals(2, chai.size)
        assertNotEquals(StatementTxns.key(s.header, chai[0]), StatementTxns.key(s.header, chai[1]))
    }

    @Test fun overlappingStatementsGiveTheSameKeysForTheSharedDay() {
        val a = parse(pages(sep))
        val b = parse(pages(oct))
        assertTrue(b.complete, "issues: ${b.issues}")
        assertEquals(8, b.rows.size)
        val aKeys = StatementTxns.accept(a).map { it.key }.toSet()
        val bKeys = StatementTxns.accept(b).map { it.key }
        assertEquals(2, bKeys.count { it in aKeys })
        assertEquals(41, (aKeys + bKeys).size)
    }

    @Test fun narrationsTellPayeeInstrumentAndReference() {
        val s = parse(pages(sep))
        val swiggy = s.rows.first { it.narration.contains("Swiggy") }
        val n = KotakLayout.narration(swiggy.narration)
        assertEquals("Swiggy Limited", n.payee)
        assertEquals(Instrument.UPI, n.instrument)
        assertEquals("610000000006", n.rrn)
        val metroRefund = s.rows.first { it.narration.contains("Refund for U") }
        assertEquals(Direction.CREDIT, metroRefund.direction)
        assertTrue("refund" in KotakLayout.narration(metroRefund.narration).hints)
        assertTrue("autopay" in KotakLayout.narration(s.rows[0].narration).hints)
    }

    @Test fun rowsAreStampedAtNoonInBankOrder() {
        val items = StatementTxns.accept(parse(pages(sep)))
        val sameDay = items.filter { it.row.date == LocalDate.of(2026, 9, 17) }
        assertEquals(sameDay.sortedBy { it.occurredAtMillis }, sameDay)
        assertEquals(1000, sameDay[1].occurredAtMillis - sameDay[0].occurredAtMillis)
    }

    @Test fun aWrongAmountIsOneIssueNotACascade() {
        val p = pages(sep).toMutableList()
        p[0] = p[0].replace("UPI-700000000102 1,200.00 45,040.40", "UPI-700000000102 1,300.00 45,040.40")
        val s = parse(p)
        assertEquals(1, s.issues.size, "issues: ${s.issues}")
        assertTrue(s.issues[0] is StatementIssue.BadDelta)
        assertEquals(34, s.rows.size)
        assertEquals(1, s.excluded.size)
    }

    @Test fun aMissingPageIsReported() {
        val p = pages(sep).toMutableList().also { it.removeAt(1) }
        val s = parse(p)
        assertFalse(s.complete)
        assertTrue(s.issues.any { it is StatementIssue.IndexGap })
        assertTrue(s.issues.any { it is StatementIssue.PageCount })
        assertTrue(s.rows.size >= 20, "rows after the gap must still be read, got ${s.rows.size}")
    }

    @Test fun phoneLocaleDoesNotMatter() {
        val before = Locale.getDefault()
        try {
            for (l in listOf(Locale.forLanguageTag("en-GB"), Locale.forLanguageTag("hi-IN"), Locale.forLanguageTag("kn-IN"))) {
                Locale.setDefault(l)
                assertEquals(35, parse(pages(sep)).rows.size)
            }
        } finally { Locale.setDefault(before) }
    }

    @Test fun blankOrForeignTextIsNotAStatement() {
        assertEquals(StatementParse.NoText, Statements.parse(listOf("", "  ")))
        assertEquals(StatementParse.NotAStatement, Statements.parse(listOf("Dear guest, your table for two is confirmed for 8 pm.")))
        val other = Statements.parse(listOf("HDFC BANK Statement of account\nDate Narration Withdrawal Amt. Deposit Amt. Closing Balance\nOpening Balance 100.00"))
        assertEquals(StatementParse.UnknownLayout("HDFC"), other)
    }
}
