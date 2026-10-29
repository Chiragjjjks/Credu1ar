package io.github.chiragjjjks.credu1ar.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.core.model.Group

enum class Tab(val label: String) { PULSE("Pulse"), LEDGER("Ledger"), INSIGHTS("Insights"), SYSTEM("System") }

/** Lives for the process so the intro plays once per cold start, not on every rotation. */
private object IntroState { var shown = false }

@Composable
fun Credu1arRoot(model: AppModel, host: Host, pendingTxn: State<Long?>, onPendingConsumed: () -> Unit) {
    val s by model.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.PULSE) }
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    var showArm by rememberSaveable { mutableStateOf(false) }
    var ledgerGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var introDone by remember { mutableStateOf(IntroState.shown) }
    var autoArmChecked by rememberSaveable { mutableStateOf(false) }

    // A tap on the "captured" notification opens that payment.
    LaunchedEffect(pendingTxn.value) { pendingTxn.value?.let { sheetId = it; onPendingConsumed() } }

    // First launch: guide the owner through arming once the intro is over.
    LaunchedEffect(introDone, s.ready, s.prefs.onboarded) {
        if (introDone && s.ready && !s.prefs.onboarded && !autoArmChecked) { autoArmChecked = true; showArm = true }
    }

    // "Hide from recents & screenshots".
    val activity = LocalActivity.current
    LaunchedEffect(s.prefs.secure) {
        if (s.prefs.secure) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    val nav = remember(model) {
        Nav(
            openTxn = { sheetId = it },
            openReview = { showReview = true },
            openLedger = { g -> ledgerGroupId = g?.id; tab = Tab.LEDGER },
            arm = { showArm = true },
            loadSample = { model.loadSample(); model.setOnboarded(true) },
        )
    }

    BackHandler(enabled = sheetId != null || showReview || showArm || tab != Tab.PULSE) {
        when {
            sheetId != null -> sheetId = null
            showArm -> showArm = false
            showReview -> showReview = false
            else -> tab = Tab.PULSE
        }
    }

    CompositionLocalProvider(LocalHideAmounts provides s.prefs.privacy) {
        Box(Modifier.fillMaxSize().background(C.Black)) {
            Field()

            when (tab) {
                Tab.PULSE -> PulseScreen(s, model, nav)
                Tab.LEDGER -> LedgerScreen(s, ledgerGroupId?.let(Group::byId), { ledgerGroupId = it?.id }, nav)
                Tab.INSIGHTS -> InsightsScreen(s, model, nav)
                Tab.SYSTEM -> SystemScreen(s, model, host, nav)
            }

            BottomNav(tab, hasAttention = s.review.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter)) { tab = it }

            AnimatedVisibility(showReview, enter = slideInHorizontally(tween(320, easing = Ease.Out)) { it } + fadeIn(), exit = slideOutHorizontally(tween(240)) { it } + fadeOut()) {
                ReviewScreen(s, model, nav) { showReview = false }
            }
            AnimatedVisibility(showArm, enter = slideInVertically(tween(360, easing = Ease.Out)) { it }, exit = slideOutVertically(tween(260)) { it }) {
                ArmScreen(s, model, host) { showArm = false }
            }

            val open = sheetId?.let { id -> s.txns.firstOrNull { it.id == id } }
            AnimatedVisibility(open != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheetId = null })
            }
            AnimatedVisibility(open != null, modifier = Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(320, easing = Ease.Out)) { it }, exit = slideOutVertically(tween(220)) { it }) {
                if (open != null) TxnSheet(open, model, s.now) { sheetId = null }
            }

            AnimatedVisibility(!introDone, exit = slideOutVertically(tween(700, easing = Ease.InOut)) { -it } + fadeOut(tween(700))) {
                Intro { IntroState.shown = true; introDone = true }
            }
        }
    }
}

/** Two soft glows on pure black: the only "decoration" behind the content. Static, so it costs almost nothing. */
@Composable
private fun Field() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(C.Cyan.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.95f, 0f), radius = size.width * 0.95f))
        drawRect(Brush.radialGradient(listOf(C.Indigo.copy(alpha = 0.16f), Color.Transparent), center = Offset(0f, size.height * 0.62f), radius = size.width * 1.15f))
    }
}

@Composable
private fun BottomNav(current: Tab, hasAttention: Boolean, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(modifier.navigationBarsPadding().padding(bottom = 14.dp)) {
        Row(
            Modifier.clip(shape).background(Color(0xF5080808)).border(1.dp, C.LineHot, shape).padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier.clip(RoundedCornerShape(50)).pressable { onSelect(t) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(t.label.uppercase(), T.Eyebrow.copy(color = if (on) C.Ink else C.Dim), maxLines = 1)
                        if (t == Tab.PULSE && hasAttention) { Box(Modifier.padding(start = 5.dp).size(5.dp).background(C.Attention, CircleShape)) }
                    }
                    Box(Modifier.padding(top = 5.dp).width(if (on) 18.dp else 0.dp).height(2.dp).clip(CircleShape).background(C.Brand))
                }
            }
        }
    }
}
