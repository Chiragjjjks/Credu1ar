package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Money
import java.time.YearMonth

class Nav(
    val openTxn: (Long) -> Unit,
    val openReview: () -> Unit,
    val openLedger: (Group?) -> Unit,
    val arm: () -> Unit,
    val loadSample: () -> Unit,
)

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit) {
    val current = YearMonth.now(io.github.chiragjjjks.credu1ar.core.insights.Insights.IST)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt("‹", T.Title, Modifier.pressable { onChange(month.minusMonths(1)) }.padding(end = 12.dp, top = 4.dp, bottom = 4.dp), color = C.Mid)
        Txt(Fmt.month(month), T.Eyebrow.copy(color = C.Ink))
        if (month.isBefore(current)) Txt("›", T.Title, Modifier.pressable { onChange(month.plusMonths(1)) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), color = C.Mid)
    }
}

@Composable
fun LiveStrip(s: AppModel.UiState) {
    val a = s.access
    val armed = a.sms || a.notifListener || s.counts.sample > 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LiveDot(if (armed) C.MoneyIn else C.Attention, pulse = armed)
        Spacer(Modifier.width(8.dp))
        Txt(if (armed) "LISTENING" else "NOT ARMED", T.Eyebrow.copy(color = if (armed) C.Ink else C.Attention))
        Spacer(Modifier.width(12.dp))
        Txt("SMS ${if (a.sms) "✓" else "✕"}", T.Eyebrow.copy(color = if (a.sms) C.Mid else C.Faint))
        Spacer(Modifier.width(10.dp))
        Txt("NOTIF ${if (a.notifListener) "✓" else "✕"}", T.Eyebrow.copy(color = if (a.notifListener) C.Mid else C.Faint))
        Spacer(Modifier.weight(1f))
        val last = maxOf(s.diag.lastSmsAt, s.diag.lastNotifAt)
        if (last > 0) Txt(Fmt.ago(last, s.now).uppercase(), T.Eyebrow)
    }
}

@Composable
fun PulseScreen(s: AppModel.UiState, model: AppModel, nav: Nav) {
    val sum = s.summary
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
    ) {
        item { LiveStrip(s) }
        item { Spacer(Modifier.height(18.dp)); Eyebrow("Pulse", "01", trailing = { MonthSwitcher(s.month) { model.setMonth(it) } }) }

        if (sum == null || s.txns.isEmpty()) {
            item { EmptyPulse(nav) }
            return@LazyColumn
        }

        item { Spacer(Modifier.height(14.dp)); Headline(s.headline.line1, s.headline.line2) }
        item {
            Spacer(Modifier.height(20.dp))
            val shown by animateFloatAsState(sum.spentPaise.toFloat(), tween(900, easing = Ease.Out), label = "hero")
            Txt(rupees(shown.toLong()), T.Display, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("SPENT THIS MONTH", T.Eyebrow)
                val pct = sum.changePct
                if (pct != null && pct != 0) {
                    Spacer(Modifier.width(10.dp))
                    Txt(if (pct > 0) "▲ $pct%" else "▼ ${-pct}%", T.Eyebrow.copy(color = if (pct > 0) C.Attention else C.MoneyIn))
                    Txt("  VS SAME POINT LAST MONTH", T.Eyebrow)
                }
            }
        }

        // Forecast meter
        item {
            Spacer(Modifier.height(18.dp))
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow("Pace", live = sum.daysElapsed in 1 until sum.daysInMonth)
                    val scale = maxOf(sum.prevMonthTotalPaise, sum.forecastPaise, sum.spentPaise)
                    TickMeter(sum.spentPaise, scale, sum.forecastPaise)
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Txt(if (sum.forecastPaise > sum.spentPaise) "ON PACE FOR" else "MONTH TOTAL", T.Eyebrow)
                            Txt(rupees(sum.forecastPaise), T.Amount)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Txt("LAST MONTH", T.Eyebrow)
                            Txt(if (sum.prevMonthTotalPaise > 0) rupees(sum.prevMonthTotalPaise) else "—", T.Amount)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Panel {
                Row(Modifier.fillMaxWidth()) {
                    StatCell("In", rupees(sum.incomePaise), Modifier.weight(1.1f), if (sum.incomePaise > 0) C.MoneyIn else C.Ink)
                    StatCell("Daily burn", rupees(sum.dailyBurnPaise), Modifier.weight(1.1f))
                    StatCell("Payments", sum.spendCount.toString(), Modifier.weight(0.8f))
                }
            }
        }

        // Where it went
        item {
            Section("Where it went", "02") {
                Panel {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        GroupRing(sum.byGroup, rupees(sum.spentPaise), "spent")
                        Spacer(Modifier.height(14.dp))
                        val top = sum.byGroup.filter { it.paise > 0 }.sortedByDescending { it.paise }
                        val max = top.firstOrNull()?.paise?.toFloat() ?: 1f
                        top.forEach { g ->
                            RankedBar(g.group.label, rupees(g.paise), g.paise / max, C.of(g.group), "${g.paise * 100 / sum.spentPaise.coerceAtLeast(1)}%", onClick = { nav.openLedger(g.group) })
                        }
                    }
                }
            }
        }

        if (s.split.total > 0) item {
            Section("Groceries, split", "03") {
                Panel {
                    StackedSplit(
                        listOf("Quick commerce" to s.split.quickCommerce, "Provision store" to s.split.provision, "Supermarket" to s.split.supermarket, "Fresh & dairy" to s.split.fresh),
                        C.of(Group.GROCERIES),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)); Ticker(s.ticker) }

        if (s.review.isNotEmpty()) item {
            Spacer(Modifier.height(14.dp))
            Panel(hot = true, onClick = nav.openReview) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(C.Attention)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("${s.reviewGroups.size} payee${if (s.reviewGroups.size == 1) "" else "s"} need a quick look", T.Body)
                        Txt("${s.review.size} payments · tell it once and it remembers.", T.Small)
                    }
                    Txt("→", T.Title, color = C.Mid)
                }
            }
        }

        item { Section("Latest", "04") {
            Column {
                s.txns.take(6).forEachIndexed { i, t ->
                    if (i > 0) Hairline()
                    TxnRow(t, s.now, showDay = true) { nav.openTxn(t.id) }
                }
            }
        } }
    }
}

@Composable
private fun EmptyPulse(nav: Nav) {
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Headline("Waiting for your", "first payment.")
        Txt("Credu1ar files each payment the moment its SMS lands. Arm it once, or look around with fictional data first.", T.Body, color = C.Mid)
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow("Start here")
                Box(Modifier.fillMaxWidth()) { PillButton("Arm it", primary = true, onClick = nav.arm) }
                PillButton("Try with sample data", onClick = nav.loadSample)
            }
        }
    }
}

fun Category.shortLabel(): String = label
fun Long.rupeesRound(): String = Money.format(this)
