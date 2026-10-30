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
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Txn
import java.time.Instant
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

    fun cadence(days: Int) = when (days) { 7 -> "weekly"; 30 -> "monthly"; 91 -> "quarterly"; else -> "yearly" }
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
            Txt(if (showDay) "${Fmt.dayShort(t.occurredAtMillis, now)} ${Fmt.time(t.occurredAtMillis)}" else Fmt.time(t.occurredAtMillis), T.MonoSmall, maxLines = 1)
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
