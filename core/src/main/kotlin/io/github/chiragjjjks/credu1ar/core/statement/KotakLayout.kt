package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.statement.StatementText.MONEY
import java.time.LocalDate
import kotlin.math.abs

/**
 * Kotak Mahindra Bank savings statement (PDF made with OpenPDF), as Android's PdfRenderer text API returns it:
 * one block per page, lines in reading order. Layout notes and fictional samples: core/src/test/resources/statements.
 *
 * A row starts with `<#> <dd Mon yyyy> <narration…>`; the narration may wrap onto further lines; the row's last line
 * ends with `<amount> <balance>` (the Withdrawal and Deposit columns collapse into one amount in the text, so the
 * direction comes from the change in balance). Rows are found by their start line, never by where amounts appear,
 * so one odd row can't shift the rows after it.
 */
object KotakLayout : StatementLayout {
    override val bank = "Kotak"

    private val tableHeader = Regex("""^# Date Description Chq/Ref\. No\. Withdrawal \(Dr\.\) Deposit \(Cr\.\) Balance$""")
    private val rowStart = Regex("""^(\d{1,5}) (\d{1,2} [A-Za-z]{3,4} \d{4})(?: (.*))?$""")
    private val amountsAtEnd = Regex("""^(.*?) ?($MONEY) ($MONEY)$""")
    private val opening = Regex("""^- - Opening Balance\b.*? ($MONEY)$""")
    private val period = Regex("""^(?:Account Statement )?(\d{1,2} [A-Za-z]{3,4} \d{4}) - (\d{1,2} [A-Za-z]{3,4} \d{4})$""")
    private val accountNo = Regex("""^Account No\. ?(\d{6,20})$""")
    private val pageOf = Regex("""Page (\d+) of(?: (\d+))?$""")
    private val summary = Regex("""Savings Account \(SA\): ?($MONEY) ($MONEY)""")
    private val refColumn = Regex("""\b(?:UPI|NEFTINW|NEFT|IMPS|RTGS|NACH|ACH)-\d[\w]*\b""")
    private val cashbackRef = Regex("""\bONBF- ?([0-9A-Fa-f]{6,})\b""")
    private val nameLike = Regex("""^[A-Za-z][A-Za-z .']{1,40}$""")
    private val stopMarkers = setOf("End of Statement", "Commonly Used Narrations")

    override fun recognises(pages: List<String>): Boolean {
        val lines = pages.asSequence().flatMap { it.lineSequence() }.map { it.trim() }
        val hasTable = lines.any { tableHeader.matches(it) }
        val text = pages.joinToString("\n")
        return hasTable && (text.contains("KKBK") || text.contains("Kotak", ignoreCase = true))
    }

    private class Seg(val page: Int, val index: Int, val date: LocalDate, val lines: MutableList<String>)

    override fun read(pages: List<String>): Statement {
        val issues = mutableListOf<StatementIssue>()
        val segs = mutableListOf<Seg>()
        var openingRow: Long? = null
        var summaryOpening: Long? = null
        var summaryClosing: Long? = null
        var expectedPages: Int? = null
        var from: LocalDate? = null
        var to: LocalDate? = null
        var fullAccount: String? = null
        var holder: String? = null
        var page1NameNext = false
        var inTable = true
        var current: Seg? = null

        pages.forEachIndexed { pi, pageText ->
            val page = pi + 1
            if (pageText.isBlank()) { issues += StatementIssue.EmptyPage(page); return@forEachIndexed }
            val lines = pageText.split('\n').map { it.trim() }
            for (i in lines.indices) {
                val line = lines[i]
                if (line.isEmpty()) continue
                val next = lines.getOrNull(i + 1).orEmpty()
                val prev = lines.getOrNull(i - 1).orEmpty()

                // Facts that can appear anywhere.
                period.matchEntire(line)?.let { m ->
                    if (from == null) { from = StatementText.date(m.groupValues[1]); to = StatementText.date(m.groupValues[2]) }
                    if (page == 1 && !line.startsWith("Account Statement ")) page1NameNext = true
                    continue
                }
                if (page1NameNext) { page1NameNext = false; if (holder == null && nameLike.matches(line)) holder = line; if (current == null) continue }
                accountNo.matchEntire(line)?.let { m -> if (fullAccount == null) fullAccount = m.groupValues[1]; continue }
                pageOf.find(line)?.let { m ->
                    val n = m.groupValues[2].ifEmpty { next.takeIf { it.matches(Regex("""\d{1,3}""")) } ?: "" }
                    if (expectedPages == null) expectedPages = n.toIntOrNull()
                    continue
                }
                summary.find(line)?.let { m -> summaryOpening = StatementText.paise(m.groupValues[1]); summaryClosing = StatementText.paise(m.groupValues[2]); continue }
                // A footer's page number on its own line ("Page 1 of " / "8").
                if (prev.endsWith(" of") && line.matches(Regex("""\d{1,3}"""))) continue
                // Footer: holder name, then "Account No. …", then "Account Statement <period>". The name is kept in memory only.
                if (accountNo.matches(next) && lines.getOrNull(i + 2).orEmpty().startsWith("Account Statement")) {
                    if (holder == null && nameLike.matches(line)) holder = line
                    continue
                }
                if (line.startsWith("Account Statement") || line.startsWith("Statement Generated on")) continue
                if (line == "Savings Account Transactions" || tableHeader.matches(line)) continue
                if (line in stopMarkers || line.startsWith("Account Summary")) { inTable = false; current = null; continue }
                if (!inTable) continue

                opening.matchEntire(line)?.let { m -> openingRow = StatementText.paise(m.groupValues[1]); continue }
                val start = rowStart.matchEntire(line)
                val date = start?.let { StatementText.date(it.groupValues[2]) }
                if (start != null && date != null) {
                    current = Seg(page, start.groupValues[1].toInt(), date, mutableListOf(start.groupValues[3]))
                    segs += current!!
                } else {
                    current?.lines?.add(line)
                }
            }
        }

        val tail = fullAccount?.takeLast(4)
        val opening = openingRow ?: summaryOpening ?: 0L
        val rows = mutableListOf<StatementRow>()
        val excluded = mutableListOf<StatementRow>()
        var prevBalance = opening
        var expectedIndex = 1
        var lastBalance: Long? = null

        for (s in segs) {
            if (s.index != expectedIndex) issues += StatementIssue.IndexGap(expectedIndex, s.index)
            expectedIndex = s.index + 1
            val last = s.lines.lastOrNull().orEmpty()
            val m = amountsAtEnd.matchEntire(last)
            val amount = m?.let { StatementText.paise(it.groupValues[2]) }
            val balance = m?.let { StatementText.paise(it.groupValues[3]) }
            if (m == null || amount == null || balance == null) { issues += StatementIssue.Unread(s.page, "row without an amount"); continue }
            lastBalance = balance
            if (amount == 0L) { prevBalance = balance; continue }

            var text = (s.lines.dropLast(1) + m.groupValues[1]).filter { it.isNotBlank() }.joinToString(" ")
            var ref: String? = null
            refColumn.findAll(text).lastOrNull()?.let { r -> ref = r.value; text = text.removeRange(r.range) }
            cashbackRef.find(text)?.let { r -> ref = "ONBF-" + r.groupValues[1]; text = text.removeRange(r.range) }
            // The full account number appears in interest rows ("Int.Pd:<number>:…"): keep only the last 4 digits.
            fullAccount?.let { acct -> text = text.replace(acct, "XX${acct.takeLast(4)}") }
            text = text.replace(Regex("\\s+"), " ").trim()

            val delta = balance - prevBalance
            val direction = when {
                abs(delta) == amount -> if (delta < 0) Direction.DEBIT else Direction.CREDIT
                else -> {
                    issues += StatementIssue.BadDelta(s.index, s.date, amount)
                    directionFromWords(text)
                }
            }
            prevBalance = balance // re-anchor on the printed balance either way
            val row = StatementRow(s.index, s.page, s.date, text, ref, amount, direction ?: Direction.DEBIT, balance)
            if (direction == null) excluded += row else rows += row
        }

        val summaryOpen = summaryOpening
        if (openingRow != null && summaryOpen != null && openingRow != summaryOpen) issues += StatementIssue.OpeningMismatch(summaryOpen, openingRow!!)
        val close = summaryClosing
        if (close != null && lastBalance != null && close != lastBalance) issues += StatementIssue.ClosingMismatch(close, lastBalance!!)
        expectedPages?.let { if (it != pages.size) issues += StatementIssue.PageCount(it, pages.size) }
        if (segs.isEmpty()) issues += StatementIssue.Unread(1, "no rows found")

        val allDates = (rows + excluded).map { it.date }
        val header = StatementHeader(
            bank = bank,
            from = from ?: allDates.minOrNull() ?: LocalDate.MIN,
            to = to ?: allDates.maxOrNull() ?: LocalDate.MIN,
            accountTail = tail,
            holderName = holder,
        )
        return Statement(header, opening, summaryClosing, rows, excluded, issues)
    }

    /** Only when the balance can't settle it: narrations whose direction is never in doubt. Otherwise null (row excluded). */
    private fun directionFromWords(text: String): Direction? = when {
        text.startsWith("NEFT IN") || text.startsWith("CASHBACK") || text.startsWith("Int.Pd") || Regex("""(?i)/refund\b""").containsMatchIn(text) -> Direction.CREDIT
        text.startsWith("CHRG") || text.contains("MandateExecu") -> Direction.DEBIT
        else -> null
    }

    // ------------------------------------------------------------------------------------------------ narration

    private val upi = Regex("""^UPI/(.+)$""")
    private val neftIn = Regex("""^NEFT IN ?(\S+) (.+)$""")
    private val transfer = Regex("""^(NEFT|IMPS|RTGS)\b""")

    /** What a Kotak narration says about the payment. Unknown shapes give a payee-less, hint-less result. */
    fun narration(text: String): NarrationInfo {
        upi.matchEntire(text)?.let { m ->
            val parts = m.groupValues[1].split('/')
            if (parts.size >= 4) {
                val payee = parts[0].trim()
                val rrn = parts[2].replace(" ", "").takeIf { it.matches(Regex("""\d{10,14}""")) }
                val note = parts.drop(3).joinToString("/").trim().lowercase()
                val hints = buildSet {
                    if (note.startsWith("refund")) add("refund")
                    if (note.startsWith("mandateexecu") || note.startsWith("monthly auto")) add("autopay")
                }
                return NarrationInfo(payee.ifEmpty { null }, payee.length >= 13, Instrument.UPI, rrn, hints)
            }
            return NarrationInfo(null, false, Instrument.UPI, null, emptySet())
        }
        neftIn.matchEntire(text)?.let { m -> return NarrationInfo(m.groupValues[2].trim(), true, Instrument.TRANSFER, null, setOf("neft")) }
        if (transfer.containsMatchIn(text)) return NarrationInfo(null, false, Instrument.TRANSFER, null, emptySet())
        if (text.startsWith("CASHBACK")) return NarrationInfo(null, false, Instrument.ACCOUNT, null, setOf("cashback", "refund"))
        if (text.startsWith("CHRG")) return NarrationInfo(null, false, Instrument.ACCOUNT, null, setOf("charges"))
        if (text.startsWith("Int.Pd")) return NarrationInfo(null, false, Instrument.ACCOUNT, null, setOf("interest"))
        return NarrationInfo(null, false, Instrument.UNKNOWN, null, emptySet())
    }
}

/** What a narration says, in bank-independent terms. [rrn] is the 12-digit UPI reference that bank SMS also quote. */
data class NarrationInfo(
    val payee: String?,
    val payeeTruncated: Boolean,
    val instrument: Instrument,
    val rrn: String?,
    val hints: Set<String>,
)
