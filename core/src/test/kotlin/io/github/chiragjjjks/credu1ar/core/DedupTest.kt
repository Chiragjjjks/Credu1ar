package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Source
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DedupTest {
    private val t0 = 1_800_000_000_000L
    private fun key(ref: String? = null, tail: String? = "1234", at: Long = t0, amount: Long = 45_000, dir: Direction = Direction.DEBIT, src: Source = Source.SMS) =
        DedupKey(dir, amount, tail, ref, at, src)

    @Test fun sameReferenceIsTheSamePayment() =
        assertTrue(Dedup.isDuplicate(key(ref = "630112345678"), key(ref = "630112345678", at = t0 + 10 * 60_000, src = Source.NOTIFICATION)))

    @Test fun differentReferencesAreDifferentPayments() =
        assertFalse(Dedup.isDuplicate(key(ref = "630112345678"), key(ref = "630112345679")))

    @Test fun smsWithRefMatchesNotificationWithoutOne() =
        assertTrue(Dedup.isDuplicate(key(ref = "630112345678", tail = "1234"), key(ref = null, tail = null, at = t0 + 40_000, src = Source.NOTIFICATION)))

    @Test fun twoIdenticalChaisSixMinutesApartStayTwo() =
        assertFalse(Dedup.isDuplicate(key(), key(at = t0 + 6 * 60_000)))

    @Test fun differentAccountTailsNeverMerge() =
        assertFalse(Dedup.isDuplicate(key(ref = null, tail = "1234"), key(ref = null, tail = "9999")))

    @Test fun differentAmountOrDirectionNeverMerge() {
        assertFalse(Dedup.isDuplicate(key(), key(amount = 45_100)))
        assertFalse(Dedup.isDuplicate(key(), key(dir = Direction.CREDIT)))
    }

    @Test fun smsBeatsNotificationBeatsSample() {
        assertTrue(Dedup.prefer(Source.NOTIFICATION, Source.SMS))
        assertFalse(Dedup.prefer(Source.SMS, Source.NOTIFICATION))
        assertTrue(Dedup.prefer(Source.SAMPLE, Source.NOTIFICATION))
    }
}
