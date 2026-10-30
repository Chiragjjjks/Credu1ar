package io.github.chiragjjjks.credu1ar.ingest

import android.content.Context
import io.github.chiragjjjks.credu1ar.Graph
import io.github.chiragjjjks.credu1ar.core.Outcome
import io.github.chiragjjjks.credu1ar.core.Pipeline
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import io.github.chiragjjjks.credu1ar.data.InsertResult
import io.github.chiragjjjks.credu1ar.notify.Notifier

/**
 * The single front door for every signal: live SMS, a notification, the catch-up scan, sample data.
 * Privacy invariant: a message that is not a payment is counted and dropped, never stored or logged.
 */
object Ingest {
    /**
     * @param rules the owner's merchant rules, passed in by bulk reads so they are loaded once, not per message.
     * @param notBefore history reads drop a payment dated before the window the owner asked for (its alert arrived late).
     */
    fun handle(
        context: Context, signal: RawSignal, sourceId: String? = null, quiet: Boolean = false,
        rules: Map<String, Category>? = null, notBefore: Long = 0L,
    ) {
        val repo = Graph.repo(context)
        val settings = Graph.settings(context)
        when (signal.source) {
            Source.SMS -> { repo.inc("n_sms"); repo.setMeta("last_sms_at", signal.receivedAtMillis.toString()) }
            Source.NOTIFICATION -> { repo.inc("n_notif"); repo.setMeta("last_notif_at", System.currentTimeMillis().toString()) }
            Source.SAMPLE -> Unit
        }
        when (val outcome = Pipeline.process(signal, rules ?: repo.rules())) {
            Outcome.Ignored -> repo.inc("n_ignored")
            is Outcome.Txn if outcome.accepted.parsed.occurredAtMillis < notBefore -> repo.inc("n_ignored")
            is Outcome.Unreadable -> { repo.inc("n_unreadable"); repo.addUnparsed(signal, outcome.reason) }
            is Outcome.Txn -> when (val r = repo.insert(signal, outcome.accepted, sourceId, settings.keepRaw)) {
                is InsertResult.Inserted -> {
                    repo.inc("n_accepted")
                    if (!quiet && settings.notifyOnCapture && signal.source != Source.SAMPLE) Notifier.captured(context, r.id, outcome.accepted)
                }
                is InsertResult.Duplicate -> repo.inc("n_duplicate")
            }
        }
    }
}
