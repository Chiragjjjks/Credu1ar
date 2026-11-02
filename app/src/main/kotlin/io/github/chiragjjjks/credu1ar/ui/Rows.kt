package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.insights.Coverage
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.insights.Reports
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.data.StatementRecord
import io.github.chiragjjjks.credu1ar.ingest.ImportProgress
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    private val zone = Insights.IST
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun dayLabel(ms: Long, now: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> d.format(dayFmt) }
    }

    fun dayKey(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)
    fun shortDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(shortDay)

    /** "Today", "Yesterday" or "27 Oct": short enough for a table column. */
    fun dayShort(ms: Long, now: Long): String {
        val l = dayLabel(ms, now)
        return if (l == "Today" || l == "Yesterday") l else shortDate(ms)
    }
    fun month(ym: YearMonth): String = ym.atDay(1).format(monthFmt).uppercase()

    /** "September", or "December 2025" when it is not this year. */
    fun monthName(ym: YearMonth): String {
        val name = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return if (ym.year == YearMonth.now(zone).year) name else "$name ${ym.year}"
    }

    /** One line for the SMS-history read: live progress, then what it found. Null when nothing is happening. */
    fun history(h: AppModel.History): String? = when (h) {
        AppModel.History.Idle -> null
        is AppModel.History.Reading -> "READING SMS · ${shortDate(h.fromMillis).uppercase()} → TODAY" + if (h.total > 0) " · ${h.read} / ${h.total}" else ""
        is AppModel.History.Done -> "${shortDate(h.fromMillis).uppercase()} → TODAY · ${h.payments} ${if (h.payments == 1) "PAYMENT" else "PAYMENTS"} FILED"
    }

    fun ago(ms: Long, now: Long): String {
        if (ms == 0L) return "never"
        val m = (now - ms) / 60_000
        return when {
            m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 60 * 24 -> "${m / 60} h ago"; else -> "${m / (60 * 24)} d ago"
        }
    }

    /**
     * Under a row's amount: the time, or the day and time. Statement rows carry no time (`timeKnown == false`), so
     * they show the date only, never a made-up "12:00".
     */
    fun rowWhen(t: Txn, now: Long, showDay: Boolean): String = when {
        !t.timeKnown -> dayShort(t.occurredAtMillis, now)
        showDay -> "${dayShort(t.occurredAtMillis, now)} ${time(t.occurredAtMillis)}"
        else -> time(t.occurredAtMillis)
    }

    /** The payment sheet's date line: "Wed, 16 Sep · 14:05", or the date alone for statement rows. */
    fun sheetWhen(t: Txn, now: Long): String =
        if (t.timeKnown) "${dayLabel(t.occurredAtMillis, now)} · ${time(t.occurredAtMillis)}" else dayLabel(t.occurredAtMillis, now)

    // ------------------------------------------------------------------------------------------- statements

    private val dayMon = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private fun thisYear() = LocalDate.now(zone).year

    /** "16 Sep". */
    fun dayMonth(d: LocalDate): String = d.format(dayMon)

    /** "16 Sep → 16 Oct", plus the year when asked for or when it isn't this year ("16 Sep → 16 Oct 2026"). */
    fun period(from: LocalDate, to: LocalDate, withYear: Boolean = false): String {
        val left = if (from.year != to.year) "${dayMonth(from)} ${from.year}" else dayMonth(from)
        return "$left → ${dayMonth(to)}" + if (withYear || to.year != thisYear()) " ${to.year}" else ""
    }

    /** The switcher's month: "OCT", or "OCT 2025" when it isn't this year. */
    fun monthShort(ym: YearMonth): String {
        val m = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
        return if (ym.year == thisYear()) m else "$m ${ym.year}"
    }

    /** "KOTAK ••1234": the bank and the account's last four digits, never more. */
    fun account(bank: String?, tail: String?): String =
        listOfNotNull(bank?.uppercase(), tail?.let { "••$it" }).joinToString(" ").ifEmpty { "STATEMENT" }

    /** System's list line: "16 SEP → 16 OCT · 202 ROWS ✓". */
    fun statementLine(r: StatementRecord): String =
        "${period(r.from, r.to).uppercase()} · ${r.rows} ${if (r.rows == 1) "ROW" else "ROWS"}${if (r.complete) " ✓" else ""}"

    /** "READING STATEMENT · PAGE 3 OF 8 · 143 ROWS" (or "FILE 2 OF 3 · …" when several were picked). */
    fun importProgress(p: ImportProgress?): String {
        if (p == null) return "OPENING STATEMENT"
        val head = if (p.files > 1) "FILE ${p.file} OF ${p.files}" else "READING STATEMENT"
        val page = if (p.pages > 0) " · PAGE ${p.page} OF ${p.pages}" else ""
        return "$head$page · ${p.rows} ${if (p.rows == 1) "ROW" else "ROWS"}"
    }

    fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

    // ------------------------------------------------------------------------------------------- report copy

    private fun full(ym: YearMonth) = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** BY MONTH's label: `OCT`, or `SEP · 16–30` when only part of the month is covered. */
    fun monthLabel(ym: YearMonth, c: Coverage?): String {
        val first = c?.firstCovered; val last = c?.lastCovered
        return if (c?.partial == true && first != null && last != null) "${monthShort(ym)} · ${first.dayOfMonth}–${last.dayOfMonth}" else monthShort(ym)
    }

    /** Under the big total: `SPENT IN OCTOBER`, `SPENT · 16–30 SEP` (part month) or `SPENT · 16 SEP → 16 OCT`. */
    fun spentCaption(r: Reports.Report): String {
        val c = r.coverage
        val first = c?.firstCovered; val last = c?.lastCovered
        return when {
            r.isCalendarMonth && c?.partial == true && first != null && last != null ->
                "SPENT · ${first.dayOfMonth}–${last.dayOfMonth} ${first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()}"
            r.isCalendarMonth -> "SPENT IN ${monthName(YearMonth.from(r.from)).uppercase()}"
            else -> "SPENT · ${period(r.from, r.to).uppercase()}"
        }
    }

    /** What a ▲▼ compares with. Only ever shown between two fully covered periods. */
    fun versus(r: Reports.Report): String =
        if (r.isCalendarMonth) "VS ${monthName(YearMonth.from(r.from).minusMonths(1)).uppercase()}"
        else "VS THE ${r.days} DAYS BEFORE"

    /** "29%", or "<1%" so a real but tiny share never reads as nothing. */
    fun share(part: Long, total: Long): String {
        if (total <= 0) return "0%"
        val p = part * 100 / total
        return if (p == 0L && part > 0) "<1%" else "$p%"
    }

    /** Not-spending line labels: "card bill ₹12,300 · own accounts ₹5,000 · investments ₹3,000". */
    fun moveLabel(c: Category): String = when (c) {
        Category.CARD_BILL -> "card bill"
        Category.SELF_TRANSFER -> "own accounts"
        Category.INVESTMENTS -> "investments"
        else -> c.label.lowercase()
    }

    /** Line one of the report headline, without the amount formatting (see [reportHeadline]). */
    internal fun spentLine(r: Reports.Report, spent: String): String {
        val c = r.coverage
        val first = c?.firstCovered; val last = c?.lastCovered
        val ym = YearMonth.from(r.from)
        return when {
            r.spentPaise == 0L && r.isCalendarMonth -> "No spending in ${monthName(ym)}."
            r.spentPaise == 0L -> "No spending from ${dayMonth(r.from)} to ${dayMonth(r.to)}."
            r.isCalendarMonth && c?.partial == true && first != null && last != null ->
                "You spent $spent from ${first.dayOfMonth} to ${last.dayOfMonth} ${full(ym)}" + if (ym.year != thisYear()) " ${ym.year}." else "."
            r.isCalendarMonth -> "You spent $spent in ${monthName(ym)}."
            else -> "You spent $spent from ${dayMonth(r.from)} to ${dayMonth(r.to)}."
        }
    }

    fun cadence(days: Int) = when (days) { 7 -> "weekly"; 30 -> "monthly"; 91 -> "quarterly"; else -> "yearly" }
}

/**
 * The report's two-line headline. Line two names the group that led; "led" ignores the Not sorted bar. A part month
 * says how many days it covers instead, because it can't be compared with anything.
 */
@Composable
fun reportHeadline(r: Reports.Report): Pair<String, String?> {
    val line1 = Fmt.spentLine(r, rupees(r.spentPaise))
    val c = r.coverage
    val lead = r.byGroup.firstOrNull()
    val line2 = when {
        r.isCalendarMonth && c?.partial == true -> "That's ${c.coveredDays} of ${c.totalDays} days."
        r.spentPaise == 0L -> if (r.incomePaise > 0) "${rupees(r.incomePaise)} came in." else null
        lead == null -> "None of it is sorted yet."
        else -> "${lead.group.label} led at ${rupees(lead.paise)}."
    }
    return line1 to line2
}

@Composable
fun signed(t: Txn): String = (if (t.direction == Direction.DEBIT) "−" else "+") + rupees(t.amountPaise)

/** Rounded monogram tinted with the category group's colour. */
@Composable
fun Avatar(t: Txn, size: Dp = 40.dp) {
    val tint = C.of(t.category.group)
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(size).background(tint.copy(alpha = 0.16f), shape).border(1.dp, tint.copy(alpha = 0.35f), shape), contentAlignment = Alignment.Center) {
        Txt(t.merchantName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•", T.Title)
    }
}

@Composable
fun TxnRow(t: Txn, now: Long, showDay: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(t)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(t.merchantName, T.Body, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(t.category.label, T.Small, maxLines = 1)
                if (t.needsReview) Txt("  ·  needs a look", T.Small, color = C.Attention, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Txt(signed(t), T.Amount, color = if (t.direction == Direction.CREDIT && t.isIncome) C.MoneyIn else if (!t.isSpend && t.direction == Direction.DEBIT) C.Mid else C.Ink, maxLines = 1)
            Txt(Fmt.rowWhen(t, now, showDay), T.MonoSmall, maxLines = 1)
        }
    }
}

@Composable
fun DayHeader(label: String, spendPaise: Long) {
    Row(Modifier.fillMaxWidth().background(C.Black).padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(label.uppercase(), T.Eyebrow, Modifier.weight(1f))
        if (spendPaise > 0) Txt(rupees(spendPaise), T.MonoSmall)
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = C.Ink) {
    Column(modifier) {
        Txt(label.uppercase(), T.Eyebrow)
        Spacer(Modifier.size(4.dp))
        Txt(value, T.AmountBig.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)), color = valueColor, maxLines = 1)
    }
}

@Composable
fun Section(title: String, number: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(title, number)
        content()
    }
}
