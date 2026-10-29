@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.model.Money

/** Press feedback without Material ripple: a quick dim and shrink. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed) 1f else 0f, tween(90), label = "press")
    graphicsLayer { alpha = 1f - 0.4f * k; scaleX = 1f - 0.03f * k; scaleY = 1f - 0.03f * k }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Txt(text: String, style: TextStyle = T.Body, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE) {
    BasicText(
        text = text, modifier = modifier, maxLines = maxLines, overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = if (color == Color.Unspecified) style else style.copy(color = color),
    )
}

/** Formats paise, or masks it in privacy mode. */
@Composable
fun rupees(paise: Long, withSymbol: Boolean = true): String =
    if (LocalHideAmounts.current) (if (withSymbol) "₹" else "") + "•••••" else Money.format(paise, withSymbol)

@Composable
fun LiveDot(color: Color = C.MoneyIn, size: Dp = 7.dp, pulse: Boolean = true) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).alpha(if (pulse) a else 1f).background(color, CircleShape))
}

/** Mono, uppercase, optionally numbered label: "01 — PULSE". */
@Composable
fun Eyebrow(label: String, number: String? = null, live: Boolean = false, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (live) { LiveDot(); Spacer(Modifier.width(8.dp)) }
        if (number != null) { Txt(number, T.Eyebrow.copy(color = C.Faint)); Txt("  —  ", T.Eyebrow.copy(color = C.Faint)) }
        Txt(label.uppercase(), T.Eyebrow)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Two-line display heading; the second line is muted. */
@Composable
fun Headline(line1: String, line2: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(line1, T.Headline)
        if (!line2.isNullOrBlank()) Txt(line2, T.Headline, color = C.Dim)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), hot: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val m = modifier.fillMaxWidth().clip(shape).background(C.Panel).border(BorderStroke(1.dp, if (hot) C.LineHot else C.Line), shape)
    Box((if (onClick != null) m.pressable(onClick = onClick) else m).padding(padding)) { content() }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(C.Line)) }

@Composable
fun Chip(label: String, selected: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.clip(shape)
            .background(if (selected) (tint ?: C.Ink).copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) (tint ?: C.Ink).copy(alpha = 0.6f) else C.Line, shape)
            .pressable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tint != null) { Box(Modifier.size(7.dp).background(tint, CircleShape)); Spacer(Modifier.width(8.dp)) }
        Txt(label, T.Small, color = if (selected) C.Ink else C.Mid, maxLines = 1)
    }
}

@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun PillButton(label: String, primary: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = modifier.clip(shape)
    val styled = if (primary) base.background(C.Brand) else base.border(1.dp, C.LineHot, shape)
    Box(styled.pressable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp).alpha(if (enabled) 1f else 0.4f), contentAlignment = Alignment.Center) {
        Txt(label, T.Body.copy(fontFamily = Fonts.Sans), color = if (primary) Color.White else C.Ink, maxLines = 1)
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 20.dp else 2.dp, tween(180), label = "knob")
    Box(
        Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(50))
            .background(if (on) C.Blue.copy(alpha = 0.9f) else C.Lift)
            .border(1.dp, if (on) Color.Transparent else C.LineHot, RoundedCornerShape(50))
            .pressable { onChange(!on) },
    ) { Box(Modifier.padding(start = x, top = 3.dp).size(20.dp).background(Color.White, CircleShape)) }
}

@Composable
fun SettingRow(title: String, sub: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Txt(title, T.Body)
            if (sub != null) Txt(sub, T.Small)
        }
        trailing()
    }
}

/** Soft section spacing used across screens. */
@Composable fun Gap(h: Dp) = Spacer(Modifier.height(h))
