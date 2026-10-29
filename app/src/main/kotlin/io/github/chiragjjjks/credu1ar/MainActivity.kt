package io.github.chiragjjjks.credu1ar

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import io.github.chiragjjjks.credu1ar.ingest.NotificationCaptureService
import io.github.chiragjjjks.credu1ar.notify.Notifier
import io.github.chiragjjjks.credu1ar.ui.AppModel
import io.github.chiragjjjks.credu1ar.ui.Credu1arRoot
import io.github.chiragjjjks.credu1ar.ui.Host
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), Host {
    private lateinit var model: AppModel
    private val pendingTxn = mutableStateOf<Long?>(null)

    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        model.refresh()
        // The moment SMS access is granted, read what is already in the inbox so the app is not empty.
        if (granted.values.all { it }) model.catchUpNow()
    }
    private val alertPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    private val csvPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) thread(name = "credu1ar-export") {
            runCatching { contentResolver.openOutputStream(uri)?.let { model.exportCsv(it) } }
                .onFailure { runOnUiThread { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        model = Graph.model(this)
        handleIntent(intent)
        setContent { Credu1arRoot(model, this, pendingTxn) { pendingTxn.value = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val id = i?.getLongExtra(Notifier.EXTRA_TXN_ID, -1L) ?: -1L
        if (id > 0) pendingTxn.value = id
    }

    override fun onResume() {
        super.onResume()
        // Coming back from a system settings screen: re-read what Android now allows, and catch up on anything missed.
        model.refresh()
        model.catchUpNow()
        model.rebindListener()
    }

    // ---------------------------------------------------------------------------------------------- Host

    override fun requestSms() { smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }

    override fun requestPostNotif() {
        if (Build.VERSION.SDK_INT >= 33) alertPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    override fun openNotifAccess() {
        val component = ComponentName(this, NotificationCaptureService::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        runCatching { startActivity(detail) }.onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    override fun openBattery() {
        runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            .onFailure { openAppInfo() }
    }

    override fun exportCsv() { csvPicker.launch("credu1ar-export.csv") }

    override fun copy(label: String, text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }
}
