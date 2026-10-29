package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Source
import kotlin.math.abs

/** The few fields needed to decide whether two signals describe the same payment. */
data class DedupKey(
    val direction: Direction,
    val amountPaise: Long,
    val accountTail: String?,
    val reference: String?,
    val occurredAtMillis: Long,
    val source: Source,
)

/**
 * One payment often arrives twice: as the bank SMS and as the UPI app's notification (and again if the
 * catch-up scan re-reads the inbox). Two signals are the same payment when amount and direction match and
 *  - both carry a reference: the references are equal (any time gap up to 15 min, SMS can lag), or
 *  - one lacks a reference: they are within 15 min, and account tails do not contradict, or
 *  - neither has a reference: within 2 min (two ₹20 chais back to back must stay two rows).
 */
object Dedup {
    private const val WINDOW_REF_MS = 15L * 60 * 1000
    private const val WINDOW_NO_REF_MS = 2L * 60 * 1000

    fun isDuplicate(a: DedupKey, b: DedupKey): Boolean {
        if (a.direction != b.direction || a.amountPaise != b.amountPaise) return false
        val gap = abs(a.occurredAtMillis - b.occurredAtMillis)
        val tailsClash = a.accountTail != null && b.accountTail != null && a.accountTail != b.accountTail
        if (tailsClash) return false
        return when {
            a.reference != null && b.reference != null -> a.reference == b.reference
            a.reference != null || b.reference != null -> gap <= WINDOW_REF_MS
            else -> gap <= WINDOW_NO_REF_MS
        }
    }

    /** SMS has the full text, so it wins over a (possibly truncated) notification; samples never win. */
    fun prefer(existing: Source, incoming: Source): Boolean = rank(incoming) > rank(existing)

    private fun rank(s: Source) = when (s) { Source.SMS -> 2; Source.NOTIFICATION -> 1; Source.SAMPLE -> 0 }
}
