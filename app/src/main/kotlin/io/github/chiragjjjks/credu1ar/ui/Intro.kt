package io.github.chiragjjjks.credu1ar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shown once per cold start: the logo ring draws itself while a counter runs 000 to 100, then the screen wipes up. */
@Composable
fun Intro(onFinished: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1100, easing = Ease.Out))
        delay(160)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(C.Black).pressable(onClick = onFinished)) {
        Canvas(Modifier.size(96.dp).align(Alignment.Center).alpha(p.value.coerceIn(0f, 1f)).scale(0.82f + 0.18f * p.value)) {
            val w = 9.dp.toPx()
            val inset = w / 2
            drawArc(C.Brand, -90f, 270f * p.value, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            drawCircle(C.Cyan, 5.dp.toPx(), Offset(size.width * 0.27f, size.height * 0.27f), alpha = p.value)
        }
        Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp)) {
            Txt("%03d".format((p.value * 100).toInt()), T.MonoSmall.copy(color = C.Ink))
            Txt("  %", T.MonoSmall, Modifier.width(24.dp))
        }
        Txt("CREDU1AR", T.Eyebrow, Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(24.dp))
    }
}
