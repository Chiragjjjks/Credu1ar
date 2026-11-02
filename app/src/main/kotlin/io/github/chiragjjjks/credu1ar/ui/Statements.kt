package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.data.StatementRecord
import io.github.chiragjjjks.credu1ar.ingest.FileResult
import io.github.chiragjjjks.credu1ar.ingest.ImportProblem
import java.time.Instant
import java.time.LocalDate

/** The promise under every "Add bank statement" button. Broken where a phone's width would break it anyway. */
const val STATEMENT_FINE_LINE = "KOTAK PDF · ANY DATE RANGE\nREAD ON THIS PHONE, NEVER SENT"

/**
 * "00 — QUICKEST START": the statement route at the top of Arm it. Works without SMS, so it is the first thing a new
 * owner (or anyone whose phone blocks SMS access) can do.
 */
@Composable
fun StatementStart(onAdd: () -> Unit) {
    Panel(hot = true) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow("Quickest start", "00")
            Headline("See where your money went.", "Add your bank statement.")
            PillButton("Add bank statement", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onAdd)
            Txt(STATEMENT_FINE_LINE, T.Eyebrow.copy(lineHeight = T.Eyebrow.fontSize * 1.6f))
        }
    }
}

// ---------------------------------------------------------------------------------------------- import sheet

/** One plain sentence (and a mono status line) for what happened to one picked file. */
data class ResultCopy(val status: String, val ok: Boolean, val body: String)

object StatementCopy {
    fun problem(p: ImportProblem): Pair<String, String> = when (p) {
        ImportProblem.NO_TEXT -> "NO TEXT TO READ" to "This PDF is a picture of a statement, so there's no text to read. Download it again from net banking."
        ImportProblem.NOT_PDF -> "NOT A PDF" to "This file isn't a PDF. Pick the statement PDF your bank emailed you, or download it again from net banking."
        ImportProblem.NOT_A_STATEMENT -> "NOT A STATEMENT" to "This doesn't look like a bank statement. Pick the PDF your bank emailed you."
        ImportProblem.UNKNOWN_LAYOUT -> "BANK NOT SUPPORTED YET" to "Only Kotak statements can be read so far."
        ImportProblem.PHONE_TOO_OLD -> "CAN'T READ PDFS YET" to "This phone can't read PDF text yet. Update Google Play system updates and try again."
        ImportProblem.UNREADABLE -> "COULDN'T OPEN IT" to "This file couldn't be opened. Download it again and pick it once more."
    }

    /** [toLook] = payees in this statement's period that still need a quick look. */
    fun result(f: FileResult, toLook: Int): ResultCopy {
        f.problem?.let { val (s, b) = problem(it); return ResultCopy(s, false, b) }
        val what = buildString {
            append(listOfNotNull(f.bank, f.accountTail?.let { "••$it" }).joinToString(" ").ifEmpty { "Statement" })
            if (f.from != null && f.to != null) append(", ").append(Fmt.period(f.from, f.to, withYear = true))
            append(".")
        }
        val look = when (toLook) { 0 -> "Everything is sorted."; 1 -> "1 payee needs a quick look."; else -> "$toLook payees need a quick look." }
        return when {
            f.added == 0 && f.alreadyHere > 0 && f.leftOut == 0 ->
                ResultCopy("0 NEW · ${f.alreadyHere} ALREADY HERE", true, "You've added this statement before. Nothing changed.")
            f.leftOut > 0 -> ResultCopy(
                "${f.leftOut} ${if (f.leftOut == 1) "ROW DIDN'T" else "ROWS DIDN'T"} ADD UP", false,
                "$what What was read is saved. The ${if (f.leftOut == 1) "row that didn't" else "rows that didn't"} add up ${if (f.leftOut == 1) "was" else "were"} left out and listed under System → Couldn't read.",
            )
            f.alreadyHere > 0 -> ResultCopy("${f.added} NEW · ${f.alreadyHere} ALREADY HERE", true, "$what $look")
            f.complete -> ResultCopy("${f.rows} ${if (f.rows == 1) "ROW" else "ROWS"} · ADDS UP TO THE CLOSING BALANCE ✓", true, "$what $look")
            else -> ResultCopy(
                "${f.rows} ${if (f.rows == 1) "ROW" else "ROWS"} · DOESN'T REACH THE CLOSING BALANCE", false,
                "$what What was read is saved, but some rows may be missing. Check the totals against your bank's summary.",
            )
        }
    }
}

/** Payees still under review with a payment between [from] and [to] (inclusive, IST). */
fun payeesToLook(s: AppModel.UiState, from: LocalDate?, to: LocalDate?): Int {
    if (from == null || to == null) return 0
    return s.review.filter {
        val d = Instant.ofEpochMilli(it.occurredAtMillis).atZone(Insights.IST).toLocalDate()
        !d.isBefore(from) && !d.isAfter(to)
    }.map { it.merchantKey }.distinct().size
}

/**
 * The one sheet an import lives in, wherever it was started (Arm it, Pulse or System): page-by-page progress, the
 * password prompt for a locked PDF, then the result card.
 */
@Composable
fun ImportSheet(s: AppModel.UiState, import: AppModel.Import, model: AppModel, onSeeIt: () -> Unit, onSort: () -> Unit, onPickAgain: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(shape).background(C.Panel).border(1.dp, C.LineHot, shape)
            .navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(C.LineHot, CircleShape))
        when (val i = import) {
            AppModel.Import.Idle -> Unit
            is AppModel.Import.Reading -> Reading(i)
            is AppModel.Import.Locked -> Locked(i, model)
            is AppModel.Import.Done -> Done(s, i, model, onSeeIt, onSort, onPickAgain)
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun Reading(i: AppModel.Import.Reading) {
    val p = i.progress
    Eyebrow("Bank statement", "00", live = true)
    Headline("Reading your", "statement.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val f by animateFloatAsState(if (p != null && p.pages > 0) p.page.toFloat() / p.pages else 0.06f, tween(400, easing = Ease.Out), label = "pages")
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Line)) {
            Box(Modifier.fillMaxWidth(f.coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(C.Brand))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(C.Attention); Spacer(Modifier.width(10.dp))
            Txt(Fmt.importProgress(p), T.Eyebrow.copy(color = C.Ink))
        }
    }
    Txt("Read on this phone. The file itself is not kept; only the payments read from it are saved.", T.Small)
}

@Composable
private fun Locked(i: AppModel.Import.Locked, model: AppModel) {
    // remember, never rememberSaveable: the password must not outlive this screen or land in saved state.
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() { if (pw.isNotEmpty()) { val p = pw; pw = ""; model.unlockStatement(p) } }

    Eyebrow("Bank statement", "00", trailing = { if (i.files > 1) Txt("FILE ${i.fileIndex + 1} OF ${i.files}", T.Eyebrow) })
    Headline("This statement", "is locked.")
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = pw, onValueChange = { pw = it }, singleLine = true,
        textStyle = T.Amount.copy(color = C.Ink), cursorBrush = SolidColor(C.Cyan),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).background(C.Black, shape).border(1.dp, if (i.wrongPassword) C.Attention else C.LineHot, shape).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (pw.isEmpty()) Txt("Statement password", T.Body, color = C.Faint)
                    inner()
                }
                Box(Modifier.heightIn(min = 48.dp).pressable { show = !show }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Txt(if (show) "HIDE" else "SHOW", T.Eyebrow.copy(color = C.Mid))
                }
            }
        },
    )
    if (i.wrongPassword) Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(C.Attention, CircleShape)); Spacer(Modifier.width(8.dp))
        Txt("That password didn't open it.", T.Body, color = C.Attention)
    }
    Txt("Your bank's statement password (often in the email). Used once to open the file, never saved.", T.Small)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Open", primary = true, enabled = pw.isNotEmpty(), modifier = Modifier.weight(1f)) { submit() }
        PillButton("Cancel") { pw = ""; model.cancelImport() }
    }
}

@Composable
private fun Done(s: AppModel.UiState, d: AppModel.Import.Done, model: AppModel, onSeeIt: () -> Unit, onSort: () -> Unit, onPickAgain: () -> Unit) {
    val read = d.files.filter { it.problem == null }
    Eyebrow("Bank statement", "00", trailing = { if (d.files.size > 1) Txt("${d.files.size} FILES", T.Eyebrow) })
    Headline(
        when {
            read.isEmpty() -> "Nothing was added."
            read.all { it.added == 0 } -> "Already here."
            else -> "Statement added."
        },
        when {
            read.isEmpty() -> if (d.files.size == 1) "This file can't be read." else "These files can't be read."
            read.all { it.added == 0 } -> null
            else -> "${Fmt.plural(read.sumOf { it.added }, "payment")} filed."
        },
    )
    d.files.forEachIndexed { n, f ->
        if (n > 0) Hairline()
        val c = StatementCopy.result(f, payeesToLook(s, f.from, f.to))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (c.ok) C.MoneyIn else C.Attention, CircleShape)); Spacer(Modifier.width(10.dp))
                Txt(c.status, T.Eyebrow.copy(color = C.Ink))
            }
            Txt(c.body, T.Body, color = C.Mid)
        }
    }
    val toLook = read.sumOf { payeesToLook(s, it.from, it.to) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (read.isNotEmpty()) {
            PillButton("See where it went", primary = true, modifier = Modifier.fillMaxWidth()) { model.dismissImport(); onSeeIt() }
            if (toLook > 0) PillButton("Sort payees", modifier = Modifier.fillMaxWidth()) { model.dismissImport(); onSort() }
        } else {
            PillButton("Pick another file", primary = true, modifier = Modifier.fillMaxWidth()) { model.dismissImport(); onPickAgain() }
            PillButton("Close", modifier = Modifier.fillMaxWidth()) { model.dismissImport() }
        }
    }
}

// ---------------------------------------------------------------------------------------------- System

/** System → Bank statements: what was imported, each removable, and a way to add another. */
@Composable
fun StatementsPanel(statements: List<StatementRecord>, onAdd: () -> Unit, onRemove: (Long) -> Unit) {
    var confirm by remember { mutableStateOf<Long?>(null) }
    Panel {
        Column {
            if (statements.isEmpty()) {
                Txt("No statements yet. Add the PDF your bank emails you to see where the money went, no SMS needed.", T.Small, Modifier.padding(bottom = 12.dp))
            }
            statements.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (r.complete) C.MoneyIn else C.Attention, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt(Fmt.account(r.bank, r.accountTail), T.Amount, maxLines = 1)
                        Txt(Fmt.statementLine(r), T.MonoSmall)
                    }
                    Spacer(Modifier.width(10.dp))
                    PillButton(if (confirm == r.id) "Tap again" else "Remove") {
                        if (confirm == r.id) { confirm = null; onRemove(r.id) } else confirm = r.id
                    }
                }
                Hairline()
            }
            if (statements.isNotEmpty()) Txt("Remove takes out that statement's payments, except ones another statement or an SMS also has.", T.Small, Modifier.padding(top = 10.dp, bottom = 12.dp))
            PillButton(if (statements.isEmpty()) "Add bank statement" else "Add another statement", primary = statements.isEmpty(), modifier = Modifier.fillMaxWidth(), onClick = onAdd)
            Txt(STATEMENT_FINE_LINE, T.Eyebrow.copy(lineHeight = T.Eyebrow.fontSize * 1.6f), Modifier.padding(top = 10.dp))
        }
    }
}
