package io.github.chiragjjjks.credu1ar.ingest

import android.content.Context
import android.net.Uri
import java.time.LocalDate

/** Progress for the UI line "READING STATEMENT · PAGE 3 OF 8 · 143 ROWS". */
data class ImportProgress(val file: Int, val files: Int, val page: Int, val pages: Int, val rows: Int)

/** Why a picked file gave no (or not all) payments. Each maps to one plain sentence in the UI. */
enum class ImportProblem { NOT_PDF, NO_TEXT, NOT_A_STATEMENT, UNKNOWN_LAYOUT, PHONE_TOO_OLD, UNREADABLE }

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
)

sealed interface ImportOutcome {
    data class Done(val files: List<FileResult>) : ImportOutcome

    /** A locked PDF: ask for its password, then call [StatementImport.run] again with the same files and the password. */
    data class NeedsPassword(val fileIndex: Int, val wrongPassword: Boolean) : ImportOutcome
}

/**
 * Bank statements in, payments out. Reads each picked PDF with Android's own PdfRenderer text API (API 35+, or
 * PdfRendererPreV on 31–34 with SDK extension 13), parses it in :core, matches rows against payments already captured,
 * and writes each statement in one transaction. Never copies the file to disk, never logs its text, never keeps the password.
 */
object StatementImport {
    /** Blocking: call off the main thread. */
    fun run(context: Context, uris: List<Uri>, password: String? = null, onProgress: (ImportProgress) -> Unit = {}): ImportOutcome =
        // TODO(agent B): real implementation.
        ImportOutcome.Done(uris.map { FileResult(null, null, null, null, 0, 0, 0, 0, false, 0, ImportProblem.UNREADABLE) })
}
