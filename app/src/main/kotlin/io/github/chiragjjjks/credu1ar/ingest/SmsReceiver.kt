package io.github.chiragjjjks.credu1ar.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import kotlin.concurrent.thread

/**
 * Woken by Android for each incoming SMS, runs for ~100 ms, then the process may sleep again.
 * This is why Credu1ar needs no always-on service (ADR-003).
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val app = context.applicationContext
        val pending = goAsync()
        thread(name = "credu1ar-sms") {
            try {
                // A long SMS arrives in several parts from the same sender: glue them back together.
                parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, pieces) ->
                    val body = pieces.joinToString("") { it.messageBody.orEmpty() }
                    // Use the SMS's own timestamp so the catch-up scan later recognises it as the same message.
                    val at = pieces.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    Ingest.handle(app, RawSignal(Source.SMS, sender, body, at))
                }
                // Cheap safety net: if the OS killed us earlier and we missed something, pick it up now.
                CatchUp.runIfDue(app)
            } finally {
                pending?.finish() // null only when a test calls onReceive directly instead of via the system
            }
        }
    }
}
