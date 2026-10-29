package io.github.chiragjjjks.credu1ar.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import io.github.chiragjjjks.credu1ar.MainActivity
import io.github.chiragjjjks.credu1ar.R
import io.github.chiragjjjks.credu1ar.core.Accepted
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Money

/** A quiet, self-dismissing confirmation: "−₹312 · Zepto · Quick commerce". Tap to review or re-file it. */
object Notifier {
    const val EXTRA_TXN_ID = "txn_id"
    private const val CHANNEL = "captured"

    fun captured(context: Context, id: Long, a: Accepted) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Captured payments", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "A quiet note each time a payment is captured"
                    setShowBadge(false)
                },
            )
        }
        val sign = if (a.parsed.direction == Direction.DEBIT) "−" else "+"
        val open = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TXN_ID, id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat_credu1ar))
            .setContentTitle("$sign${Money.format(a.parsed.amountPaise)} · ${a.merchant.name}")
            .setContentText(if (a.needsReview) "${a.categorization.category.label} · tap to confirm" else a.categorization.category.label)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(10_000)
            .setGroup("captured")
            .build()
        nm.notify(id.toInt(), n)
    }
}
