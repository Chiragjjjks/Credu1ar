package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.model.Direction
import java.time.LocalDate

/**
 * What a statement's header says. [holderName] exists only in memory, to recognise transfers to the owner's own
 * accounts; it is never stored. [accountTail] is the last 4 digits; the full number is never kept.
 */
data class StatementHeader(
    val bank: String,
    val from: LocalDate,
    val to: LocalDate,
    val accountTail: String?,
    val holderName: String?,
)

/** One row of the transactions table, with its direction settled by the running balance. */
data class StatementRow(
    /** The bank's own row number ("#"). */
    val index: Int,
    /** 1-based page the row starts on. */
    val page: Int,
    val date: LocalDate,
    /** Wrapped lines joined with single spaces. Never contains header, footer or holder lines. */
    val narration: String,
    /** The bank's reference column, e.g. `UPI-700000000101` or `NEFTINW-1000000001`; null when the row has none. */
    val ref: String?,
    val amountPaise: Long,
    val direction: Direction,
    val balancePaise: Long,
)

/** Why a statement is not provably complete. Messages never contain narration text. */
sealed interface StatementIssue {
    /** The row's amount does not equal the change in balance from the row before it. */
    data class BadDelta(val index: Int, val date: LocalDate, val amountPaise: Long) : StatementIssue
    /** Row numbers jump (rows missing, e.g. a page left out). */
    data class IndexGap(val expected: Int, val found: Int) : StatementIssue
    data class OpeningMismatch(val summaryPaise: Long, val rowPaise: Long) : StatementIssue
    data class ClosingMismatch(val summaryPaise: Long, val lastRowPaise: Long) : StatementIssue
    /** "Page x of N" says N pages but a different number was read. */
    data class PageCount(val expected: Int, val found: Int) : StatementIssue
    /** A page with no text at all (a scanned image inside an otherwise text PDF). */
    data class EmptyPage(val page: Int) : StatementIssue
    /** A line inside the table that could not be read as part of a row. [why] is a fixed phrase, never input text. */
    data class Unread(val page: Int, val why: String) : StatementIssue
}

data class Statement(
    val header: StatementHeader,
    val openingPaise: Long,
    /** From the account summary, when the statement has one. */
    val closingPaise: Long?,
    /** Rows to import, in the bank's order. */
    val rows: List<StatementRow>,
    /** Rows whose direction could not be settled (see [StatementIssue.BadDelta]); listed to the owner, never counted. */
    val excluded: List<StatementRow>,
    val issues: List<StatementIssue>,
) {
    /** Every check passed: the owner can be told "adds up to the closing balance ✓". */
    val complete: Boolean get() = issues.isEmpty()
}

sealed interface StatementParse {
    data class Parsed(val statement: Statement) : StatementParse

    /** No text on any page: a scanned or photographed statement. */
    data object NoText : StatementParse

    /** Text, but nothing like a bank statement. */
    data object NotAStatement : StatementParse

    /** Looks like a statement from a bank whose layout we can't read yet. */
    data class UnknownLayout(val bankGuess: String?) : StatementParse
}

/** One bank's layout. Adding a bank = one object here + fictional fixtures in core/src/test/resources/statements. */
interface StatementLayout {
    val bank: String
    fun recognises(pages: List<String>): Boolean
    fun read(pages: List<String>): Statement
}

/** Entry point: the text of each page (as Android's PdfRenderer returns it) → a statement. Pure. */
object Statements {
    val layouts: List<StatementLayout> = listOf(KotakLayout)

    private val statementWords = Regex("""(?i)\b(statement|opening balance|closing balance|withdrawal|deposit|narration|particulars)\b""")

    fun parse(rawPages: List<String>): StatementParse {
        val pages = rawPages.map(StatementText::normalize)
        if (pages.all { it.isBlank() }) return StatementParse.NoText
        layouts.firstOrNull { it.recognises(pages) }?.let { layout ->
            val s = layout.read(pages)
            // The bank's table header but not a single row: nothing to import, and no junk statement to list.
            if (s.rows.isEmpty() && s.excluded.isEmpty()) return StatementParse.NotAStatement
            return StatementParse.Parsed(s)
        }
        val all = pages.joinToString("\n")
        if (statementWords.findAll(all).count() < 3) return StatementParse.NotAStatement
        return StatementParse.UnknownLayout(StatementText.guessBank(all))
    }
}
