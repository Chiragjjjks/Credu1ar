package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The learning loop: payments the app could not place with confidence. One tap files it and teaches the rule
 * ("Raju Kumar is always the provision store"), so the same payee never appears here twice.
 */
@Composable
fun ReviewScreen(s: AppModel.UiState, model: AppModel, nav: Nav, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Black).statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("←", T.Title, Modifier.pressable(onClick = onBack).padding(end = 14.dp, top = 4.dp, bottom = 4.dp))
            Eyebrow("Needs a look", null, live = s.review.isNotEmpty(), trailing = { Txt("${s.reviewGroups.size} LEFT", T.Eyebrow) })
        }
        if (s.reviewGroups.isEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Headline("All caught up.", "Nothing needs a look.")
                Txt("New unknown payees will show up here the moment they're captured.", T.Body, color = C.Mid)
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Txt("One tap files every payment to that payee, past and future. You'll only be asked once.", T.Small) }
            items(s.reviewGroups, key = { it.key }) { g ->
                val t = g.latest
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(t)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(g.name, T.Title, maxLines = 1)
                                Txt("${g.count} payment${if (g.count == 1) "" else "s"} · latest ${Fmt.dayShort(t.occurredAtMillis, s.now)}", T.Small)
                            }
                            Txt(rupees(g.totalPaise), T.AmountBig, maxLines = 1)
                        }
                        ChipFlow {
                            suggestions(t).forEach { c -> Chip(c.label, selected = c == t.category, tint = C.of(c.group)) { model.setCategory(t.id, c, remember = true) } }
                            Chip("More…") { nav.openTxn(t.id) }
                        }
                    }
                }
            }
        }
    }
}

