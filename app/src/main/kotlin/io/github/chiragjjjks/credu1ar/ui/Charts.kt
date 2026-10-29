package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.insights.Insights
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Money

/**
 * Ring of spending groups. Segments are drawn in the FIXED group order (colour slots 1..8, then Other), never sorted by
 * size: the validated palette is only colour-blind-safe for neighbours in that order. A 2-degree gap separates segments.
 */
@Composable
fun GroupRing(groups: List<Insights.GroupTotal>, totalLabel: String, caption: String, modifier: Modifier = Modifier) {
    val total = groups.sumOf { it.paise }.coerceAtLeast(1)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(total) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = Ease.Out)) }
    Box(modifier.size(188.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val d = Size(size.width - stroke, size.height - stroke)
            val present = groups.filter { it.paise > 0 }
            // Track so an empty ring still reads as a gauge.
            drawArc(C.Line, 0f, 360f, false, Offset(inset, inset), d, style = Stroke(stroke))
            val gap = if (present.size > 1) 2.5f else 0f
            val avail = 360f - gap * present.size
            var start = -90f + gap / 2
            for (g in present) {
                val sweep = avail * (g.paise.toFloat() / total) * progress.value
                drawArc(C.of(g.group), start, sweep.coerceAtLeast(0.5f), false, Offset(inset, inset), d, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += avail * (g.paise.toFloat() / total) + gap
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(totalLabel, T.AmountBig)
            Txt(caption.uppercase(), T.Eyebrow)
        }
    }
}

/** One ranked row: dot, label, value, and a thin bar that fills on entry. [fraction] is 0..1 of the largest row. */
@Composable
fun RankedBar(label: String, value: String, fraction: Float, color: Color, sub: String? = null, onClick: (() -> Unit)? = null) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = Ease.Out), label = "bar")
    Column(Modifier.fillMaxWidth().let { if (onClick != null) it.pressable(onClick = onClick) else it }.padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Txt(label, T.Body, Modifier.weight(1f), maxLines = 1)
            if (sub != null) { Txt(sub, T.MonoSmall); Spacer(Modifier.width(12.dp)) }
            Txt(value, T.Amount)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Line)) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

/** Stacked split of one group into its sub-kinds, as tints of the parent hue (never new hues). */
@Composable
fun StackedSplit(parts: List<Pair<String, Long>>, base: Color) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val alphas = listOf(1f, 0.72f, 0.48f, 0.3f)
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEachIndexed { i, (_, v) ->
                if (v > 0) Box(Modifier.weight(v.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(base.copy(alpha = alphas[i % alphas.size])))
            }
        }
        Spacer(Modifier.height(12.dp))
        // Two columns: full labels, percentage right-aligned, so nothing truncates.
        parts.withIndex().filter { it.value.second > 0 }.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (i, part) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(base.copy(alpha = alphas[i % alphas.size]), CircleShape)); Spacer(Modifier.width(8.dp))
                        Txt(part.first, T.Small, Modifier.weight(1f), maxLines = 1)
                        Txt("${part.second * 100 / total}%", T.Amount)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Level-meter style gauge: 44 ticks. Filled ticks = spent so far vs last month's total; the amber tick marks where
 * the current pace will land. Inspired by the per-channel meters on the reference page.
 */
@Composable
fun TickMeter(spent: Long, scale: Long, forecast: Long, modifier: Modifier = Modifier) {
    val ticks = 44
    val frac = if (scale > 0) (spent.toFloat() / scale).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(frac, tween(900, easing = Ease.Out), label = "meter")
    val forecastTick = if (scale > 0) ((forecast.toFloat() / scale).coerceIn(0f, 1f) * (ticks - 1)).toInt() else -1
    Canvas(modifier.fillMaxWidth().height(34.dp)) {
        val w = size.width / ticks
        val barW = w * 0.46f
        val filled = (animated * ticks).toInt()
        for (i in 0 until ticks) {
            val x = i * w + (w - barW) / 2
            val isFilled = i < filled
            val tall = i % 11 == 0
            val h = if (tall) size.height else size.height * 0.62f
            val top = size.height - h
            val color: Color = when {
                i == forecastTick && forecast > spent -> C.Attention
                isFilled -> lerpBrand(i / (ticks - 1f))
                else -> C.LineHot
            }
            drawRoundRect(color, Offset(x, if (i == forecastTick) 0f else top), Size(barW, if (i == forecastTick) size.height else h), CornerRadius(barW / 2))
        }
    }
}

private fun lerpBrand(t: Float): Color {
    val a = Color(0xFF5FD0E6); val b = Color(0xFF3B7FE0); val c = Color(0xFF2C3FB8)
    return if (t < 0.5f) androidx.compose.ui.graphics.lerp(a, b, t * 2) else androidx.compose.ui.graphics.lerp(b, c, (t - 0.5f) * 2)
}

/** Spend per day of the month. Past days in a neutral tone, today in the brand gradient, future days as quiet ticks. */
@Composable
fun DailyBars(daily: LongArray, today: Int, modifier: Modifier = Modifier) {
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1)
    val avg = if (today > 0) daily.take(today).sum() / today else 0
    Canvas(modifier.fillMaxWidth().height(86.dp)) {
        val n = daily.size
        val slot = size.width / n
        val bw = slot * 0.56f
        val base = size.height - 4.dp.toPx()
        daily.forEachIndexed { i, v ->
            val day = i + 1
            val x = i * slot + (slot - bw) / 2
            if (day > today && today > 0) {
                drawRoundRect(C.Line, Offset(x, base - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(bw / 2))
            } else {
                val h = (v.toFloat() / max * (size.height - 8.dp.toPx())).coerceAtLeast(if (v > 0) 3.dp.toPx() else 1.5f.dp.toPx())
                val brush = if (day == today) C.Brand else null
                if (brush != null) drawRoundRect(brush, Offset(x, base - h), Size(bw, h), CornerRadius(bw / 2))
                else drawRoundRect(if (v > 0) C.LineHot.copy(alpha = 0.5f) else C.Line, Offset(x, base - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        if (avg > 0) {
            val y = base - (avg.toFloat() / max * (size.height - 8.dp.toPx()))
            var x = 0f
            while (x < size.width) { drawLine(C.Attention.copy(alpha = 0.7f), Offset(x, y), Offset(minOf(x + 5.dp.toPx(), size.width), y), 1.dp.toPx()); x += 9.dp.toPx() }
        }
    }
}

/** 7 x 24 spend heatmap. One hue (brand blue), light-to-strong by amount: a sequential ramp, not a rainbow. */
@Composable
fun Heatmap(r: Insights.Rhythm, modifier: Modifier = Modifier) {
    val max = r.maxSpend.coerceAtLeast(1).toFloat()
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            days.forEach { Box(Modifier.height(14.dp), contentAlignment = Alignment.CenterStart) { Txt(it, T.Eyebrow) } }
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height((14 * 7 + 3 * 6).dp)) {
                val cw = size.width / 24f
                val ch = 14.dp.toPx(); val gap = 3.dp.toPx()
                for (d in 0..6) for (h in 0..23) {
                    val v = r.spendPaise[d][h]
                    val a = if (v == 0L) 0f else 0.14f + 0.86f * Math.pow((v / max).toDouble(), 0.6).toFloat()
                    drawRoundRect(
                        if (v == 0L) C.Line else C.Blue.copy(alpha = a),
                        Offset(h * cw + 1f, d * (ch + gap)), Size(cw - 2f, ch), CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12a", "6a", "12p", "6p", "11p").forEach { Txt(it, T.Eyebrow) }
            }
        }
    }
}

/** Slow marquee of recent captures. Scrolls only while composed, so it stops when the app is in the background. */
@Composable
fun Ticker(items: List<String>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val scroll = rememberScrollState()
    LaunchedEffect(items) {
        var last = 0L
        var pos = 0f
        while (true) {
            val now = withFrameNanos { it }
            // The row holds the list twice, so wrapping by half its width is seamless.
            if (last != 0L && scroll.maxValue > 0) {
                pos += (now - last) / 1_000_000_000f * 60f
                if (pos >= scroll.maxValue / 2f) pos -= scroll.maxValue / 2f
                scroll.scrollTo(pos.toInt())
            }
            last = now
        }
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Panel)) {
        Row(Modifier.horizontalScroll(scroll, enabled = false).padding(vertical = 11.dp)) {
            repeat(2) { Row { items.forEach { Txt("$it   ·   ", T.MonoSmall, maxLines = 1) } } }
        }
        Box(Modifier.align(Alignment.CenterStart).width(28.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(C.Panel, Color.Transparent))))
        Box(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color.Transparent, C.Panel))))
    }
}

fun Long.compact(): String = Money.format(this)
fun groupColor(g: Group): Color = C.of(g)
