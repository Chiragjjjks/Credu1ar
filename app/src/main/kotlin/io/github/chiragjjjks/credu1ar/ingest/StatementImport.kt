package io.github.chiragjjjks.credu1ar.ingest

import android.content.Context
import android.net.Uri
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.statement.StatementParse
import io.github.chiragjjjks.credu1ar.core.statement.StatementTxns
import io.github.chiragjjjks.credu1ar.core.statement.Statements
import io.github.chiragjjjks.credu1ar.data.Repo
import java.time.LocalDate

/** Progress for the UI line "READING STATEMENT · PAGE 3 OF 8 · 143 ROWS". */
data class ImportProgress(val file: Int, val files: Int, val page: Int, val pages: Int, val rows: Int)

/** Why a picked file gave no (or not all) payments. Each maps to one plain sentence in the UI. */
enum class ImportProblem { NOT_PDF, NO_TEXT, NOT_A_STATEMENT, UNKNOWN_LAYOUT, PHONE_TOO_OLD, UNREADABLE }

/** A row left out because its amount didn't match the change in balance: shown to the owner by date and amount only. */
data class LeftOutRow(val date: LocalDate, val amountPaise: Long, val direction: Direction)

/** What happened to one picked file. [complete] = every check passed ("adds up to the closing balance ✓"). */
data class FileResult(
    val bank: String?,
    val accountTail: String?,
    val from: LocalDate?,
    val to: LocalDate?,
    val rows: Int,
    val added: Int,
    val alreadyHere: Int,
    val matchedExisting: Int,
    val complete: Boolean,
    /** Rows left out because their balance didn't add up; listed to the owner by date and amount. */
    val leftOut: Int,
    val problem: ImportProblem?,
    /** This exact file was added before: nothing new was read ("Already added"). */
    val sameFile: Boolean = false,
    /** The [leftOut] rows themselves, in the bank's order. */
    val leftOutRows: List<LeftOutRow> = emptyList(),
    /** For [ImportProblem.UNKNOWN_LAYOUT]: the bank the text seems to come from, if any ("HDFC"). */
    val bankGuess: String? = null,
)

sealed interface ImportOutcome {
    data class Done(val files: List<FileResult>) : ImportOutcome

    /**
     * A locked PDF: ask for its password, then call [StatementImport.run] again with the same files and the password.
     * Files before [fileIndex] were already imported ([done]); on the second run they come back as [FileResult.sameFile],
     * so passing only `uris.drop(fileIndex)` works too (needed when files have different passwords).
     */
    data class NeedsPassword(val fileIndex: Int, val wrongPassword: Boolean, val done: List<FileResult> = emptyList()) : ImportOutcome
}

/**
 * Bank statements in, payments out. Reads each picked PDF with Android's own PdfRenderer text API (API 35+, or
 * PdfRendererPreV on 31–34 with SDK extension 13), parses it in :core, matches rows against payments already captured,
 * and writes each statement in one transaction. Never copies the file to disk, never logs its text, never keeps the password.
 */
object StatementImport {
    /** Blocking: call off the main thread. The password, if any, is tried on every file and then forgotten. */
    fun run(context: Context, uris: List<Uri>, password: String? = null, onProgress: (ImportProgress) -> Unit = {}): ImportOutcome {
        val repo = Graph.repo(context)
        val keepRaw = Graph.settings(context).keepRaw
        val rules = repo.rules()
        val results = ArrayList<FileResult>(uris.size)
        try {
            uris.forEachIndexed { i, uri ->
                val file = i + 1
                onProgress(ImportProgress(file, uris.size, 0, 0, 0))
                val read = PdfText.read(context, uri, password) { page, pages -> onProgress(ImportProgress(file, uris.size, page, pages, 0)) }
                val result = when (read) {
                    is PdfText.Result.Locked -> return ImportOutcome.NeedsPassword(i, read.wrongPassword, results.toList())
                    is PdfText.Result.Failed -> problem(read.problem)
                    is PdfText.Result.Pages -> try {
                        importPages(repo, read, file, uris.size, rules, keepRaw, onProgress)
                    } catch (e: RuntimeException) {
                        // Never the message: it could quote the file. The class name is enough to find the bug.
                        android.util.Log.w("Credu1ar", "statement import failed: ${e.javaClass.simpleName}")
                        problem(ImportProblem.UNREADABLE)
                    }
                }
                results += result
            }
        } finally {
            repo.bump() // once, after every file: the UI recomputes one time
        }
        return ImportOutcome.Done(results)
    }

    private fun importPages(
        repo: Repo, read: PdfText.Result.Pages, file: Int, files: Int, rules: Map<String, Category>,
        keepRaw: Boolean, onProgress: (ImportProgress) -> Unit,
    ): FileResult = when (val parsed = Statements.parse(read.pages)) {
        StatementParse.NoText -> problem(ImportProblem.NO_TEXT)
        StatementParse.NotAStatement -> problem(ImportProblem.NOT_A_STATEMENT)
        is StatementParse.UnknownLayout -> problem(ImportProblem.UNKNOWN_LAYOUT).copy(bankGuess = parsed.bankGuess)
        is StatementParse.Parsed -> {
            val s = parsed.statement
            val pages = read.pages.size
            onProgress(ImportProgress(file, files, pages, pages, s.rows.size))
            val items = StatementTxns.accept(s, rules)
            val r = repo.importStatement(s, items, read.sha256, keepRaw)
            FileResult(
                bank = s.header.bank, accountTail = s.header.accountTail, from = s.header.from, to = s.header.to,
                rows = s.rows.size, added = r.added, alreadyHere = r.alreadyHere, matchedExisting = r.matched,
                complete = s.complete, leftOut = s.excluded.size, problem = null, sameFile = r.sameFile,
                leftOutRows = s.excluded.map { LeftOutRow(it.date, it.amountPaise, it.direction) },
            )
        }
    }

    private fun problem(p: ImportProblem) = FileResult(null, null, null, null, 0, 0, 0, 0, false, 0, p)
}
