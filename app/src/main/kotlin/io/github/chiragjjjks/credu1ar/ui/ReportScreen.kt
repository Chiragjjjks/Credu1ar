package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.insights.Reports
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Money the report shows in its own amber "Not sorted yet" bar. Mirrors Reports: a person-or-nothing guess under review. */
val Txn.isUnsorted: Boolean get() = isSpend && needsReview && (category == Category.UNCATEGORISED || category == Category.SENT_TO_PEOPLE)

private fun Txn.day(): LocalDate = Instant.ofEpochMilli(occurredAtMillis).atZone(Insights.IST).toLocalDate()

/** Which payments a report bar stands for, as a string so the open sheet survives rotation. */
object PaymentsKey {
    const val UNSORTED = "unsorted"
    fun group(g: Group) = "g:${g.id}"
    fun merchant(key: String) = "m:$key"

    class Selection(val title: String, val color: Color?, val unsorted: Boolean, val txns: List<Txn>, val from: LocalDate, val to: LocalDate) {
        val totalPaise: Long get() = txns.sumOf { it.amountPaise }
    }

    /** The spending behind one bar in [r]'s period, biggest first. */
    fun select(key: String, all: List<Txn>, r: Reports.Report): Selection {
        val spend = all.filter { it.isSpend && !it.day().isBefore(r.from) && !it.day().isAfter(r.to) }
        fun sel(title: String, color: Color?, unsorted: Boolean, l: List<Txn>) = Selection(title, color, unsorted, l.sortedByDescending { it.amountPaise }, r.from, r.to)
        return when {
            key == UNSORTED -> sel("Not sorted yet", null, true, spend.filter { it.isUnsorted })
            key.startsWith("g:") -> Group.byId(key.removePrefix("g:")).let { g -> sel(g.label, C.of(g), false, spend.filter { !it.isUnsorted && it.category.group == g }) }
            else -> key.removePrefix("m:").let { k ->
                val l = spend.filter { it.merchantKey == k }
                sel(l.firstOrNull()?.merchantName ?: "Payments", l.firstOrNull()?.let { C.of(it.category.group) }, l.isNotEmpty() && l.all { it.isUnsorted }, l)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- chrome

/** `BY STATEMENT · BY MONTH`: a hairline pill with two 48 dp halves. Shown once any statement exists. */
@Composable
fun PeriodToggle(by: AppModel.PulseBy, onChange: (AppModel.PulseBy) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.dp, C.LineHot, shape).padding(4.dp)) {
        listOf(AppModel.PulseBy.STATEMENT to "BY STATEMENT", AppModel.PulseBy.MONTH to "BY MONTH").forEach { (b, label) ->
            val on = b == by
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(shape).background(if (on) C.Lift else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, C.Line, shape) else Modifier).pressable { onChange(b) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(label, T.Eyebrow.copy(color = if (on) C.Ink else C.Dim), maxLines = 1)
                    Box(Modifier.padding(top = 4.dp).width(if (on) 18.dp else 0.dp).height(2.dp).clip(CircleShape).background(C.Brand))
                }
            }
        }
    }
}

/** `‹ 16 SEP → 16 OCT ›` or `‹ SEP · 16–30 ›`. Each arrow is a full 48 dp target; a missing step is not drawn. */
@Composable
fun PeriodSwitcher(label: String, hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp, 48.dp).alpha(if (hasPrev) 1f else 0f).pressable(enabled = hasPrev, onClick = onPrev), contentAlignment = Alignment.Center) { Txt("‹", T.Title, color = C.Mid) }
        Txt(label, T.Eyebrow.copy(color = C.Ink), maxLines = 1)
        Box(Modifier.size(40.dp, 48.dp).alpha(if (hasNext) 1f else 0f).pressable(enabled = hasNext, onClick = onNext), contentAlignment = Alignment.Center) { Txt("›", T.Title, color = C.Mid) }
    }
}

/** `FROM STATEMENT · KOTAK ••1234 · 202 ROWS ✓`: where the numbers below came from. Replaces the live strip. */
@Composable
private fun ReportStrip(s: AppModel.UiState) {
    val sts = s.reportStatements
    if (sts.isEmpty()) { LiveStrip(s); return }
    val complete = sts.all { it.complete }
    val accounts = sts.map { Fmt.account(it.bank, it.accountTail) }.distinct()
    val text = buildString {
        append(if (sts.size == 1) "FROM STATEMENT" else "${sts.size} STATEMENTS")
        append(" · ").append(accounts.first()); if (accounts.size > 1) append(" +${accounts.size - 1}")
        val rows = sts.sumOf { it.rows }
        append(" · $rows ${if (rows == 1) "ROW" else "ROWS"}")
        if (complete) append(" ✓")
    }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LiveDot(if (complete) C.MoneyIn else C.Attention, pulse = false)
            Spacer(Modifier.width(8.dp))
            Txt(text, T.Eyebrow.copy(color = C.Ink), maxLines = 2)
        }
        // Said in words, not just the amber dot: some rows didn't add up to the closing balance.
        if (!complete) Txt("NOT EVERY ROW ADDED UP · SEE SYSTEM", T.Eyebrow.copy(color = C.Attention), Modifier.padding(start = 15.dp, top = 4.dp), maxLines = 2)
    }
}

// ---------------------------------------------------------------------------------------------- report

/**
 * Pulse for a statement period or a past/part month: the plain "what happened" report. No ring, pace meter or
 * ticker. A part month never compares and works out per day over the days it covers.
 */
@Composable
fun ReportPulse(s: AppModel.UiState, r: Reports.Report, model: AppModel, nav: Nav) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
    ) {
        item { ReportStrip(s) }
        item { Spacer(Modifier.height(14.dp)); PeriodToggle(s.by) { model.setBy(it) } }
        item { Spacer(Modifier.height(6.dp)); Eyebrow("Pulse", "01", trailing = { Switcher(s, r, model) }) }

        if (r.isEmpty) {
            item { EmptyPeriod(s, r, nav) }
            return@LazyColumn
        }

        item {
            Spacer(Modifier.height(6.dp))
            val (l1, l2) = reportHeadline(r)
            Headline(l1, l2)
        }
        item {
            Spacer(Modifier.height(20.dp))
            val shown by animateFloatAsState(r.spentPaise.toFloat(), tween(900, easing = Ease.Out), label = "hero")
            Txt(rupeesWhole(shown.toLong()), T.Display, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Txt(Fmt.spentCaption(r), T.Eyebrow)
            val pct = r.changePct
            if (pct != null && pct != 0) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (pct > 0) "▲ $pct%" else "▼ ${-pct}%", T.Eyebrow.copy(color = if (pct > 0) C.Attention else C.MoneyIn))
                Txt("  ${Fmt.versus(r)}", T.Eyebrow)
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            Panel {
                Row(Modifier.fillMaxWidth()) {
                    StatCell("Money in", if (r.incomePaise > 0) "+${rupeesWhole(r.incomePaise)}" else rupeesWhole(0), Modifier.weight(1.15f), if (r.incomePaise > 0) C.MoneyIn else C.Ink)
                    StatCell("Payments", r.spendCount.toString(), Modifier.weight(0.8f))
                    StatCell("Per day", rupeesWhole(r.perDayPaise), Modifier.weight(1f))
                }
            }
        }

        val unsortedShare = if (r.spentPaise > 0) r.unsortedPaise * 100 / r.spentPaise else 0
        val banner = r.unsortedPaise > 0 && r.unsortedPaise * 10 >= r.spentPaise
        if (banner) item {
            Spacer(Modifier.height(12.dp))
            Panel(hot = true, onClick = nav.openReview) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(C.Attention)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("${rupeesWhole(r.unsortedPaise)} ($unsortedShare%) isn't sorted yet", T.Body)
                        Txt("${Fmt.plural(r.unsortedPayees, "payee")} · Sort them →", T.Small)
                    }
                }
            }
        }

        item {
            Section("Where it went", "02") {
                Panel {
                    Column {
                        val max = maxOf(r.byGroup.firstOrNull()?.paise ?: 0L, r.unsortedPaise).coerceAtLeast(1).toFloat()
                        r.byGroup.forEach { g ->
                            RankedBar(
                                g.group.label, rupeesWhole(g.paise), g.paise / max, C.of(g.group),
                                caption = "${Fmt.share(g.paise, r.spentPaise)} OF SPEND", captionEnd = Fmt.plural(g.count, "PAYMENT", "PAYMENTS"),
                                onClick = { nav.openPayments(PaymentsKey.group(g.group)) },
                            )
                        }
                        if (r.unsortedPaise > 0) UnsortedBar(
                            "Not sorted yet", rupeesWhole(r.unsortedPaise), r.unsortedPaise / max,
                            caption = "${Fmt.share(r.unsortedPaise, r.spentPaise)} OF SPEND", captionEnd = Fmt.plural(r.unsortedPayees, "PAYEE", "PAYEES"),
                            onClick = { nav.openPayments(PaymentsKey.UNSORTED) },
                        )
                    }
                }
            }
        }

        if (r.topMerchants.isNotEmpty()) item {
            Section("Top places", "03") {
                Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        r.topMerchants.forEachIndexed { i, m ->
                            if (i > 0) Hairline()
                            val unsorted = s.txns.any { it.merchantKey == m.key && it.isUnsorted }
                            PlaceRow(m, unsorted) { nav.openPayments(PaymentsKey.merchant(m.key)) }
                        }
                    }
                }
            }
        }

        if (r.moves.isNotEmpty()) item {
            Section("Not spending", "04") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt(rupeesWhole(r.moves.sumOf { it.paise }), T.Amount, maxLines = 1)
                            Txt("  moved, not counted above", T.Small, maxLines = 1)
                        }
                        val parts = r.moves.map { "${Fmt.moveLabel(it.category)} ${rupeesWhole(it.paise)}" }
                        Txt(parts.joinToString(" · "), T.Small)
                    }
                }
            }
        }

        if (!banner && s.reviewGroups.isNotEmpty()) item {
            Spacer(Modifier.height(16.dp))
            Panel(hot = true, onClick = nav.openReview) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(C.Attention)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("${Fmt.plural(s.reviewGroups.size, "payee")} need${if (s.reviewGroups.size == 1) "s" else ""} a quick look", T.Body)
                        Txt("${Fmt.plural(s.review.size, "payment")} · tell it once and it remembers.", T.Small)
                    }
                    Txt("→", T.Title, color = C.Mid)
                }
            }
        }
    }
}

@Composable
private fun Switcher(s: AppModel.UiState, r: Reports.Report, model: AppModel) {
    if (s.by == AppModel.PulseBy.STATEMENT) {
        val i = s.statements.indexOfFirst { it.id == s.statementId }.coerceAtLeast(0)
        val st = s.statements.getOrNull(i) ?: return
        // Newest first: ‹ steps back in time, › forward.
        PeriodSwitcher(
            Fmt.period(st.from, st.to).uppercase(), hasPrev = i < s.statements.size - 1, hasNext = i > 0,
            onPrev = { model.showStatement(s.statements[i + 1].id) }, onNext = { model.showStatement(s.statements[i - 1].id) },
        )
    } else {
        val current = YearMonth.now(Insights.IST)
        PeriodSwitcher(
            Fmt.monthLabel(s.month, r.coverage), hasPrev = s.month.isAfter(s.firstMonth), hasNext = s.month.isBefore(current),
            onPrev = { model.setMonth(s.month.minusMonths(1)) }, onNext = { model.setMonth(s.month.plusMonths(1)) },
        )
    }
}

@Composable
private fun EmptyPeriod(s: AppModel.UiState, r: Reports.Report, nav: Nav) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Headline("Nothing here", if (r.isCalendarMonth) "for ${Fmt.monthName(YearMonth.from(r.from))}." else "for ${Fmt.period(r.from, r.to)}.")
        Txt(
            if (s.by == AppModel.PulseBy.MONTH) "No payments were read for this month. Add the statement that covers it, or step to another month."
            else "This statement had no payments in it.",
            T.Body, color = C.Mid,
        )
        Box(Modifier.fillMaxWidth()) { PillButton("Add bank statement", primary = true, modifier = Modifier.fillMaxWidth(), onClick = nav.addStatement) }
    }
}

@Composable
private fun PlaceRow(m: Insights.MerchantTotal, unsorted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (unsorted) Box(Modifier.size(8.dp).border(1.5.dp, C.Attention, CircleShape))
        else Box(Modifier.size(8.dp).background(C.of(m.category.group), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(m.name, T.Body, maxLines = 1)
            Txt("${if (unsorted) "Not sorted yet" else m.category.label} · ${m.count}×", T.Small, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Txt(rupeesWhole(m.paise), T.Amount, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------- payments sheet

/** The payments behind one bar, biggest first. A row opens the payment sheet, where one tap teaches the category. */
@Composable
fun PaymentsSheet(p: PaymentsKey.Selection, now: Long, nav: Nav, onClose: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(shape).background(C.Panel).border(1.dp, C.LineHot, shape)
            .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(C.LineHot, CircleShape))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.unsorted || p.color == null) Box(Modifier.size(10.dp).border(1.5.dp, C.Attention, CircleShape))
            else Box(Modifier.size(10.dp).background(p.color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Txt(p.title, T.Title, Modifier.weight(1f), maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Txt(rupeesWhole(p.totalPaise), T.AmountBig, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Txt("${Fmt.plural(p.txns.size, "payment")} · ${Fmt.period(p.from, p.to)}".uppercase(), T.Eyebrow)
        if (p.unsorted) Txt("Tap one to file it. Each payee is asked once, and every statement you add remembers it.", T.Small, Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(6.dp))
        if (p.txns.isEmpty()) {
            Txt("Nothing left here. Everything in it has been filed.", T.Body, Modifier.padding(vertical = 20.dp), color = C.Mid)
        } else LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(p.txns, key = { _, t -> t.id }) { i, t ->
                if (i > 0) Hairline()
                TxnRow(t, now, showDay = true) { nav.openTxn(t.id) }
            }
        }
        PillButton("Done", primary = false, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), onClick = onClose)
    }
}
