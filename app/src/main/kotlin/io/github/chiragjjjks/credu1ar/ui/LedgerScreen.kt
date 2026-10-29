@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.chiragjjjks.credu1ar.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Txn

private enum class LedgerFilter(val label: String) { ALL("All"), SPEND("Spending"), IN("Money in"), REVIEW("Needs a look") }

@Composable
fun LedgerScreen(s: AppModel.UiState, group: Group?, onGroup: (Group?) -> Unit, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LedgerFilter.ALL) }

    val rows: List<Txn> = remember(s.txns, query, filter, group) {
        val q = query.trim().lowercase()
        s.txns.filter { t ->
            (group == null || t.category.group == group) &&
                when (filter) {
                    LedgerFilter.ALL -> true
                    LedgerFilter.SPEND -> t.isSpend
                    LedgerFilter.IN -> t.direction == Direction.CREDIT
                    LedgerFilter.REVIEW -> t.needsReview
                } &&
                (q.isEmpty() || t.merchantName.lowercase().contains(q) || t.category.label.lowercase().contains(q) || t.category.group.label.lowercase().contains(q))
        }
    }
    val byDay = remember(rows) { rows.groupBy { Fmt.dayKey(it.occurredAtMillis) }.toList() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Ledger", "02", trailing = { Txt("${rows.size} PAYMENTS", T.Eyebrow) })
            Search(query) { query = it }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LedgerFilter.entries.toList()) { f -> Chip(f.label, selected = filter == f) { filter = f } }
                if (group != null) item { Chip(group.label + "  ✕", selected = true, tint = C.of(group)) { onGroup(null) } }
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.TopCenter) {
                Txt(if (s.txns.isEmpty()) "No payments yet." else "Nothing matches that.", T.Body, color = C.Dim)
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp)) {
            byDay.forEach { (_, list) ->
                stickyHeader { DayHeader(Fmt.dayLabel(list.first().occurredAtMillis, s.now), list.filter { it.isSpend }.sumOf { it.amountPaise }) }
                items(list, key = { it.id }) { t ->
                    TxnRow(t, s.now) { nav.openTxn(t.id) }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun Search(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = T.Body, cursorBrush = SolidColor(C.Cyan),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().background(C.Panel, shape).border(1.dp, C.Line, shape).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("⌕", T.Title, color = C.Dim)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Txt("Search merchant or category", T.Body, color = C.Faint)
                    inner()
                }
            }
        },
    )
}
