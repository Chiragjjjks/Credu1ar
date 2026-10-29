package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.core.model.Txn
import io.github.chiragjjjks.credu1ar.data.TxnDetail

/** Bottom sheet for one payment: fix its category once, see exactly what was read from the message, or delete it. */
@Composable
fun TxnSheet(t: Txn, model: AppModel, now: Long, onClose: () -> Unit) {
    var detail by remember(t.id) { mutableStateOf<TxnDetail?>(null) }
    var rememberRule by remember(t.id) { mutableStateOf(true) }
    var showRaw by remember(t.id) { mutableStateOf(false) }
    var confirmDelete by remember(t.id) { mutableStateOf(false) }
    LaunchedEffect(t.id) { detail = model.detail(t.id) }

    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(shape).background(C.Panel).border(1.dp, C.LineHot, shape)
            .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).size(width = 40.dp, height = 4.dp).background(C.LineHot, CircleShape))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(t, 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Txt(t.merchantName, T.Title, maxLines = 2)
                Txt("${Fmt.dayLabel(t.occurredAtMillis, now)} · ${Fmt.time(t.occurredAtMillis)}", T.Small)
            }
            Txt(signed(t), T.AmountBig, color = if (t.isIncome) C.MoneyIn else C.Ink, maxLines = 1)
        }

        Hairline()
        Eyebrow("Filed under", "01")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(C.of(t.category.group), CircleShape)); Spacer(Modifier.width(8.dp))
            Txt("${t.category.group.label} › ${t.category.label}", T.Body)
        }
        if (t.needsReview) Txt("Not sure about this one. Pick the right category and it will remember.", T.Small, color = C.Attention)

        // Category picker, grouped
        val groups = Group.spendOrder + listOf(Group.MOVES, Group.INCOME)
        groups.forEach { g ->
            val cats = Category.entries.filter { it.group == g }
            if (cats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(C.of(g), CircleShape)); Spacer(Modifier.width(8.dp))
                        Txt(g.label.uppercase(), T.Eyebrow)
                    }
                    ChipFlow { cats.forEach { c -> Chip(c.label, selected = c == t.category, tint = C.of(g)) { model.setCategory(t.id, c, rememberRule) } } }
                }
            }
        }
        SettingRow("Always file “${t.merchantName}” here", "Applies to this payee from now on, and to earlier payments.") { Toggle(rememberRule) { rememberRule = it } }

        Hairline()
        Eyebrow("What was read", "02")
        val d = detail
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Fact("Source", when (d?.source) { Source.SMS -> "SMS"; Source.NOTIFICATION -> "Notification"; Source.SAMPLE -> "Sample data"; null -> "…" })
            Fact("Instrument", t.instrument.name.lowercase().replaceFirstChar { it.uppercase() })
            Fact("Bank", t.bank ?: "—")
            Fact("Account / card", t.accountTail?.let { "••$it" } ?: "—")
            Fact("Reference", d?.reference ?: "—")
            Fact("UPI handle", d?.vpa ?: "—")
            Fact("Confidence", if (t.confidence >= 0.95f) "taught by you" else "${(t.confidence * 100).toInt()}%")
        }
        if (d?.raw != null) {
            PillButton(if (showRaw) "Hide original message" else "Show original message") { showRaw = !showRaw }
            if (showRaw) Panel(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) { Txt(d.raw, T.MonoSmall) }
        } else if (d != null) Txt("Original text was not kept (see System → Privacy).", T.Small)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Done", primary = true, modifier = Modifier.weight(1f), onClick = onClose)
            PillButton(if (confirmDelete) "Tap again to delete" else "Delete", onClick = { if (confirmDelete) { model.deleteTxn(t.id); onClose() } else confirmDelete = true })
        }
        Spacer(Modifier.size(6.dp))
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Txt(label, T.Small, Modifier.weight(1f))
        Txt(value, T.MonoSmall.copy(color = C.Ink), maxLines = 1)
    }
}

/** Direction-aware shortlist for the Review cards: the guesses most likely to be right for an unknown payee. */
fun suggestions(t: Txn): List<Category> {
    val base = if (t.direction == Direction.DEBIT)
        listOf(Category.PROVISION, Category.FRESH_DAIRY, Category.DINING_OUT, Category.CAFES, Category.RENT, Category.PERSONAL_CARE, Category.SENT_TO_PEOPLE, Category.SELF_TRANSFER)
    else listOf(Category.MONEY_IN, Category.SALARY, Category.REFUND, Category.SELF_TRANSFER)
    return (listOf(t.category) + base).distinct().take(7)
}
