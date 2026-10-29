package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertTrue

class ParserFixtureTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 29, 15, 0).atZone(ist).toInstant().toEpochMilli()

    /** Runs every fixture and reports all mismatches together, so one bad regex does not hide the rest. */
    @Test fun everyFixtureBehavesAsExpected() {
        val failures = mutableListOf<String>()
        for (f in ParserFixtures.all) {
            val outcome = Pipeline.process(RawSignal(f.source, f.sender, f.body, now))
            when {
                f.unreadable -> if (outcome !is Outcome.Unreadable) failures += "${f.name}: expected Unreadable, got $outcome"
                f.ignored -> if (outcome !is Outcome.Ignored) failures += "${f.name}: expected Ignored, got ${describe(outcome)}"
                outcome !is Outcome.Txn -> failures += "${f.name}: expected a transaction, got $outcome"
                else -> {
                    val a = outcome.accepted
                    fun check(label: String, expected: Any?, actual: Any?) {
                        if (expected != null && expected != actual) failures += "${f.name}: $label expected <$expected> but was <$actual>"
                    }
                    check("direction", f.direction, a.parsed.direction)
                    check("amount (paise)", f.paise, a.parsed.amountPaise)
                    check("merchant", f.merchant, a.merchant.name)
                    check("category", f.category, a.categorization.category)
                    check("account tail", f.tail, a.parsed.accountTail)
                    check("instrument", f.instrument, a.parsed.instrument)
                    check("reference", f.reference, a.parsed.reference)
                    check("needsReview", f.review, a.needsReview)
                }
            }
        }
        assertTrue(failures.isEmpty(), "\n${failures.size} fixture problem(s):\n" + failures.joinToString("\n") { " - $it" })
    }

    @Test fun messageDateWinsOverArrivalTimeWhenGiven() {
        val sig = RawSignal(
            io.github.chiragjjjks.credu1ar.core.model.Source.SMS, "VM-HDFCBK-S",
            "Spent Rs.1250 On HDFC Bank Card 4321 At SWIGGY On 2026-10-28:21:05:00.Not You?", now,
        )
        val parsed = (Pipeline.process(sig) as Outcome.Txn).accepted.parsed
        val expected = LocalDateTime.of(2026, 10, 28, 21, 5).atZone(ist).toInstant().toEpochMilli()
        kotlin.test.assertEquals(expected, parsed.occurredAtMillis)
    }

    @Test fun absurdDatesFallBackToArrivalTime() {
        val sig = RawSignal(
            io.github.chiragjjjks.credu1ar.core.model.Source.SMS, "VM-HDFCBK-S",
            "Rs.450.00 debited from a/c **1234 on 31-12-99 to VPA zepto@axisbank(UPI Ref No 630112345678)", now,
        )
        val parsed = (Pipeline.process(sig) as Outcome.Txn).accepted.parsed
        kotlin.test.assertEquals(now, parsed.occurredAtMillis)
    }

    private fun describe(o: Outcome) = when (o) {
        is Outcome.Txn -> "${o.accepted.parsed.direction} ${o.accepted.parsed.amountPaise} ${o.accepted.merchant.name} ${o.accepted.categorization.category}"
        else -> o.toString()
    }
}
