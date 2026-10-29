package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.model.Money
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(s: AppModel.UiState, model: AppModel, nav: Nav) {
    val sum = s.summary
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
    ) {
        item { Eyebrow("Insights", "03", trailing = { MonthSwitcher(s.month) { model.setMonth(it) } }) }
        if (sum == null || sum.spendCount == 0) {
            item {
                Spacer(Modifier.height(24.dp))
                Headline("Nothing to analyse", "for ${Fmt.month(s.month).lowercase()} yet.")
                Spacer(Modifier.height(10.dp))
                Txt("Once a few payments are captured, this page shows where the money went, what changed, and what repeats.", T.Body, color = C.Mid)
            }
            return@LazyColumn
        }

        item { Spacer(Modifier.height(14.dp)); Headline(s.headline.line1, s.headline.line2) }

        item {
            Section("Day by day", "01") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DailyBars(sum.dailySpendPaise, sum.daysElapsed)
                        Row {
                            Txt("1", T.Eyebrow, Modifier.weight(1f))
                            Txt("${sum.daysInMonth}", T.Eyebrow)
                        }
                        Txt("Dashed line = your daily average (${rupees(sum.dailyBurnPaise)}).", T.Small)
                    }
                }
            }
        }

        item {
            Section("Where it went", "02") {
                Panel {
                    Column {
                        val max = sum.byCategory.firstOrNull()?.paise?.toFloat() ?: 1f
                        sum.byCategory.take(9).forEach { c ->
                            RankedBar(c.category.label, rupees(c.paise), c.paise / max, C.of(c.category.group), "${c.count}×")
                        }
                        val rest = sum.byCategory.drop(9)
                        if (rest.isNotEmpty()) Txt("+ ${rest.size} smaller categories · ${rupees(rest.sumOf { it.paise })}", T.Small, Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        if (s.leaks.isNotEmpty()) item {
            Section("Leaks · small, frequent, adds up", "03") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.leaks.take(4).forEach { l ->
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Txt(l.label, T.Title, maxLines = 1)
                                    Txt("${l.count} ${if (l.isCategoryLevel) "orders" else "visits"} · average ${rupees(l.avgPaise)}", T.Small)
                                }
                                Txt(rupees(l.totalPaise), T.AmountBig, color = C.Attention)
                            }
                        }
                    }
                }
            }
        }

        if (s.movers.isNotEmpty()) item {
            Section("Movers · vs same point last month", "04") {
                Panel {
                    Column {
                        s.movers.forEachIndexed { i, m ->
                            if (i > 0) Hairline()
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Txt(m.category.label, T.Body, Modifier.weight(1f), maxLines = 1)
                                val up = m.deltaPaise > 0
                                Txt((if (up) "▲ " else "▼ ") + rupees(kotlin.math.abs(m.deltaPaise)) + (m.pct?.let { "  ${if (up) "+" else ""}$it%" } ?: ""),
                                    T.Amount, color = if (up) C.Attention else C.MoneyIn, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        if (s.recurring.isNotEmpty()) item {
            val monthly = s.recurring.sumOf { it.monthlyPaise }
            Section("Recurring · ≈ ${Money.format(monthly)} a month", "05") {
                Panel {
                    Column {
                        s.recurring.take(8).forEachIndexed { i, r ->
                            if (i > 0) Hairline()
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Txt(r.name, T.Body, maxLines = 1)
                                    Txt("${Fmt.cadence(r.cadenceDays)} · next ~${Fmt.shortDate(r.nextAtMillis)}", T.Small, maxLines = 1)
                                }
                                Txt(rupees(r.amountPaise), T.Amount)
                            }
                        }
                    }
                }
            }
        }

        item {
            Section("Rhythm · when you spend", "06") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Heatmap(s.rhythm)
                        s.rhythm.busiestCell?.let { (d, h) ->
                            val day = DayOfWeek.of(d + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                            Txt("Busiest slot over the last 90 days: $day, ${hourLabel(h)}.", T.Small)
                        }
                    }
                }
            }
        }

        item {
            Section("Top payees", "07") {
                Panel {
                    Column {
                        val max = sum.topMerchants.firstOrNull()?.paise?.toFloat() ?: 1f
                        sum.topMerchants.forEach { m -> RankedBar(m.name, rupees(m.paise), m.paise / max, C.of(m.category.group), "${m.count}×") }
                    }
                }
            }
        }
    }
}

private fun hourLabel(h: Int): String {
    val hr = if (h % 12 == 0) 12 else h % 12
    return "$hr${if (h < 12) "am" else "pm"}–${if ((h + 1) % 12 == 0) 12 else (h + 1) % 12}${if ((h + 1) % 24 < 12) "am" else "pm"}"
}

