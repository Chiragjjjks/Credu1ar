package io.github.chiragjjjks.credu1ar.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.chiragjjjks.credu1ar.BuildConfig
import kotlinx.coroutines.launch

/** What the Activity must do for us (permissions, system screens, file pickers). Keeps Compose free of Android plumbing. */
interface Host {
    fun requestSms()
    fun requestPostNotif()
    fun openAppInfo()
    fun openNotifAccess()
    fun openBattery()
    fun exportCsv()
    fun copy(label: String, text: String)
}

@Composable
private fun StatusRow(label: String, ok: Boolean, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveDot(if (ok) C.MoneyIn else C.Attention, pulse = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(label, T.Body)
            Txt(detail, T.Small)
        }
        if (!ok && action != null) PillButton(action, onClick = onAction)
    }
}

@Composable
fun SystemScreen(s: AppModel.UiState, model: AppModel, host: Host, nav: Nav) {
    val scope = rememberCoroutineScope()
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    val d = s.diag
    val a = s.access

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp)) {
        item { Eyebrow("System", "04", live = a.sms || a.notifListener) }
        item { Spacer(Modifier.height(14.dp)); Headline("Is it listening?", if (a.sms || a.notifListener) "Yes. Here's the proof." else "Not yet. Arm it below.") }

        item {
            Section("Capture", "01") {
                Panel {
                    Column {
                        StatusRow("Payment SMS", a.sms, if (a.sms) "Last SMS heard ${Fmt.ago(d.lastSmsAt, s.now)}" else "Needed to read bank alerts", "Allow") { host.requestSms() }
                        Hairline()
                        StatusRow("Notification access", a.notifListener,
                            if (a.notifListener) "Connected ${Fmt.ago(d.listenerUpAt, s.now)}${if (d.lastNotifAt > 0) " · last alert ${Fmt.ago(d.lastNotifAt, s.now)}" else ""}"
                            else "Catches Google Messages RCS and UPI-app alerts", "Open") { host.openNotifAccess() }
                        Hairline()
                        StatusRow("Capture alerts", a.postNotif, "A quiet note per captured payment", "Allow") { host.requestPostNotif() }
                        Hairline()
                        StatusRow("Battery unrestricted", a.battery, "Stops phone makers from freezing the listener", "Fix") { host.openBattery() }
                        if (!a.sms || !a.notifListener) {
                            Hairline()
                            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Txt("Greyed out or “Restricted setting”? Android blocks apps installed outside a store until you allow it once.", T.Small)
                                PillButton("Walk me through it", onClick = nav.arm)
                            }
                        }
                    }
                }
            }
        }

        item {
            Section("Traffic", "02") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            StatCell("SMS seen", d.nSms.toString(), Modifier.weight(1f))
                            StatCell("Alerts seen", d.nNotif.toString(), Modifier.weight(1f))
                            StatCell("Payments", d.nAccepted.toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatCell("Ignored", d.nIgnored.toString(), Modifier.weight(1f), C.Mid)
                            StatCell("Duplicates", d.nDuplicate.toString(), Modifier.weight(1f), C.Mid)
                            StatCell("Unreadable", d.nUnreadable.toString(), Modifier.weight(1f), if (d.nUnreadable > 0) C.Attention else C.Mid)
                        }
                        Txt("Ignored = OTPs, promos, balances, failed payments. They are counted and dropped; their text is never stored.", T.Small)
                    }
                }
            }
        }

        item {
            Section("Data", "03") {
                Panel {
                    Column {
                        val h = s.history
                        SettingRow("SMS history", when {
                            h is AppModel.History.Reading -> "Reading… ${h.read} of ${h.total} messages"
                            h is AppModel.History.Done -> "Read from ${Fmt.shortDate(h.fromMillis)} · ${h.payments} payments"
                            s.historyFromMillis > 0 -> "Read from ${Fmt.shortDate(s.historyFromMillis)}. Each tap reads one more month"
                            else -> "Old bank alerts already on this phone, a month at a time"
                        }) {
                            PillButton("Add ${s.earlierMonth.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}", enabled = a.sms && h !is AppModel.History.Reading) { model.addEarlierMonth() }
                        }
                        Hairline()
                        SettingRow("Sample data", if (s.counts.sample > 0) "${s.counts.sample} fictional payments loaded" else "Fictional bank messages, to look around") {
                            PillButton(if (s.counts.sample > 0) "Remove" else "Load") { if (s.counts.sample > 0) model.clearSample() else model.loadSample() }
                        }
                        Hairline()
                        SettingRow("Export", "All payments as a CSV file you choose where to save") { PillButton("CSV") { host.exportCsv() } }
                        Hairline()
                        SettingRow("Diagnostics report", "Copies a masked summary you can paste to get help") { PillButton("Copy") { host.copy("Credu1ar report", model.report()) } }
                    }
                }
            }
        }

        if (s.unparsed.isNotEmpty()) item {
            Section("Couldn't read · ${s.unparsed.size}", "04") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt("These looked like payments but no rule matched. Copy the masked report and share it to get a new bank format added.", T.Small)
                        s.unparsed.take(3).forEach { u -> Txt("${u.reason}: ${u.body.replace(Regex("\\d"), "9").take(140)}", T.MonoSmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Copy report") { host.copy("Credu1ar report", model.report()) }
                            PillButton("Clear") { model.clearUnparsed() }
                        }
                    }
                }
            }
        }

        item {
            Section("Privacy", "05") {
                Panel {
                    Column {
                        SettingRow("Hide amounts", "Shows ••••• until you turn this off") { Toggle(s.prefs.privacy) { model.setPrivacy(it) } }
                        Hairline()
                        SettingRow("Hide from recents & screenshots", "Blocks screen capture of this app") { Toggle(s.prefs.secure) { model.setSecure(it) } }
                        Hairline()
                        SettingRow("Captured-payment alerts", "A quiet note that disappears after 10 seconds") { Toggle(s.prefs.notify) { model.setNotify(it) } }
                        Hairline()
                        SettingRow("Keep original message", "Lets you see exactly what was read. Off = only the parsed fields are stored") { Toggle(s.prefs.keepRaw) { model.setKeepRaw(it) } }
                        Hairline()
                        Txt("Nothing leaves this phone. No account, no analytics, no cloud backup. The only network use is the update check you start below.", T.Small, Modifier.padding(top = 10.dp))
                    }
                }
            }
        }

        item {
            Section("About", "06") {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Txt("Credu1ar ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · debug" else ""}", T.Body, Modifier.weight(1f))
                            Txt("Android ${Build.VERSION.RELEASE}", T.MonoSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(updateMsg ?: "Checks GitHub for a newer release. Sends nothing about you.", T.Small, Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            PillButton("Check") { updateMsg = "Checking…"; scope.launch { updateMsg = model.checkUpdate() } }
                        }
                    }
                }
            }
        }

        item {
            Section("Danger zone", "07") {
                Panel {
                    SettingRow("Delete everything", "Payments, rules and counters. Cannot be undone. Old SMS are not re-read; new ones are still filed") {
                        PillButton(if (confirmWipe) "Tap again" else "Delete", primary = false) { if (confirmWipe) { model.deleteAll(); confirmWipe = false } else confirmWipe = true }
                    }
                }
            }
        }
    }
}

