package io.github.chiragjjjks.credu1ar.ingest

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source

/**
 * Catches what SMS cannot: Google Messages RCS chats (Android never broadcasts those as SMS) and UPI-app alerts.
 * Android owns this service's lifetime once Notification access is granted; we are called only when something posts.
 *
 * Privacy: notifications from any app NOT in [Allowlist] are dropped before their text is touched.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onListenerConnected() {
        Graph.repo(this).setMeta("listener_connected_at", System.currentTimeMillis().toString())
    }

    override fun onListenerDisconnected() {
        Graph.repo(this).setMeta("listener_disconnected_at", System.currentTimeMillis().toString())
        // Some phone makers drop listeners when they "clean" memory. Ask Android to bind us again.
        requestRebind(ComponentName(this, NotificationCaptureService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || !Allowlist.accepts(pkg)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (text.isBlank()) return

        // In a messaging app the title is the sender (a bank header or contact); in a payments app it is part of the message.
        val signal = if (Allowlist.isMessaging(pkg)) RawSignal(Source.NOTIFICATION, title, text, sbn.postTime, pkg)
        else RawSignal(Source.NOTIFICATION, label(pkg), "$title $text".trim(), sbn.postTime, pkg)
        Ingest.handle(this, signal)
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg)
}

/** Apps whose notifications may contain a payment. Everything else is ignored unread. */
object Allowlist {
    private val messaging = setOf(
        "com.google.android.apps.messaging", // Google Messages (SMS + RCS)
        "com.samsung.android.messaging",
        "com.android.mms",
    )
    private val payments = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp", // BHIM
        "com.dreamplug.androidapp", // CRED
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.snapwork.hdfc",
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.axis.mobile",
        "com.msf.kmb.app",
    )

    fun isMessaging(pkg: String) = pkg in messaging
    fun accepts(pkg: String) = pkg in messaging || pkg in payments
}
