package io.github.chiragjjjks.credu1ar.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Arm it": the signal-chain walkthrough. Each step shows a live dot that turns green the instant Android grants it,
 * so the owner can see their progress without guessing. Step 2 exists because Android 13+ blocks sideloaded apps from
 * SMS and notification access until "Allow restricted settings" is chosen once in App info.
 */
@Composable
fun ArmScreen(s: AppModel.UiState, model: AppModel, host: Host, onClose: () -> Unit) {
    val a = s.access
    var importMsg by remember { mutableStateOf<String?>(null) }
    val needsUnlock = Build.VERSION.SDK_INT >= 33

    Column(Modifier.fillMaxSize().background(C.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Arm it", live = true, modifier = Modifier.weight(1f), trailing = { Txt("SKIP", T.Eyebrow.copy(color = C.Mid), Modifier.pressable { model.setOnboarded(true); onClose() }.padding(8.dp)) })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.padding(top = 6.dp))
            Headline("Arm it once and it", "listens for you.")
            Txt("Android asks for each permission separately, and asks extra when an app isn't from the Play Store. Do them in order; each dot turns green when it works.", T.Body, color = C.Mid)

            Step("01", "Read payment SMS", "Lets Credu1ar see bank alerts the moment they arrive. Nothing else is read: OTPs and chats are discarded unread.", a.sms, "Allow SMS") { host.requestSms() }
            if (needsUnlock) Step(
                "02", "Unlock restricted settings",
                "If “Allow” is greyed out or says Restricted setting: open App info, tap ⋮ (top right), choose Allow restricted settings, then come back and retry step 1 and 3.",
                a.sms && a.notifListener, "Open App info",
            ) { host.openAppInfo() }
            Step("03", "Notification access", "Catches Google Messages RCS chats and UPI-app alerts, which SMS can't see. Only banks, Messages and UPI apps are read.", a.notifListener, "Open settings") { host.openNotifAccess() }
            Step("04", "Quiet alerts", "A small self-dismissing note when a payment is filed. Optional.", a.postNotif, "Allow alerts") { host.requestPostNotif() }
            Step("05", "Keep it alive", "Some phones freeze background apps. Set Credu1ar to Unrestricted so alerts are never missed.", a.battery, "Open battery") { host.openBattery() }
            Spacer(Modifier.padding(top = 4.dp))
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (a.sms) {
                PillButton(importMsg ?: "Import last 90 days of bank SMS", modifier = Modifier.fillMaxWidth()) { importMsg = "Reading…"; model.importHistory(90) { importMsg = "Read $it messages — done" } }
            } else {
                PillButton("Try with sample data first", modifier = Modifier.fillMaxWidth()) { model.loadSample(); model.setOnboarded(true); onClose() }
            }
            PillButton(if (a.sms || a.notifListener) "Done" else "Continue without arming", primary = true, modifier = Modifier.fillMaxWidth()) { model.setOnboarded(true); onClose() }
        }
    }
}

@Composable
private fun Step(num: String, title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    Panel(hot = !done) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(num, T.Eyebrow.copy(color = C.Faint)); Spacer(Modifier.width(10.dp))
                Txt(title, T.Title, Modifier.weight(1f))
                if (done) { Txt("DONE", T.Eyebrow.copy(color = C.MoneyIn)); Spacer(Modifier.width(8.dp)) }
                LiveDot(if (done) C.MoneyIn else C.Attention, pulse = !done)
            }
            Txt(body, T.Small)
            if (!done) Box { PillButton(action, onClick = onAction) }
        }
    }
}
