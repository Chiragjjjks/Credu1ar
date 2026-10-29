package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdversarialFixtureTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 29, 15, 0).atZone(ist).toInstant().toEpochMilli()

    /**
     * Exact name-set ratchet: a newly broken fixture AND an unexpectedly fixed fixture fail this test.
     * Keep ground truth unchanged when fixing production code; remove fixed names from this set instead.
     * This is a debt inventory, not a list of fixtures to skip: every assertion runs on every invocation.
     */
    private val knownFailures: Set<String> = setOf(
    )

    @Test fun corpusHasUniqueNamesAndCompleteTransactionExpectations() {
        val fixtures = AdversarialFixtures.all
        assertTrue(fixtures.size >= 70, "The adversarial corpus must have at least 70 fixtures")
        assertEquals(fixtures.size, fixtures.map { it.name }.toSet().size, "Fixture names must be unique")
        assertTrue(knownFailures.all { name -> fixtures.any { it.name == name } }, "Unknown ratchet name")
        for (f in fixtures) {
            assertTrue(!f.unreadable || f.direction == null, "${f.name}: conflicting outcome expectations")
            if (f.direction != null) {
                assertTrue((f.paise ?: 0L) > 0L, "${f.name}: missing positive integer-paise amount")
                assertTrue(f.category != null, "${f.name}: missing category")
                assertTrue(f.instrument != null, "${f.name}: missing instrument")
                assertTrue(f.review != null, "${f.name}: missing review expectation")
            }
        }
    }

    @Test fun everyFixtureMatchesTheNamedFailureRatchet() {
        val failures = linkedMapOf<String, String>()
        for (f in AdversarialFixtures.all) {
            // A thrown exception is also a deviation, and must not hide subsequent cases.
            val result = runCatching { Pipeline.process(RawSignal(f.source, f.sender, f.body, now)) }
            val outcome = result.getOrNull()
            val differences = mutableListOf<String>()
            when {
                result.isFailure -> differences += "exception: ${result.exceptionOrNull()}"
                f.unreadable -> if (outcome !is Outcome.Unreadable) differences += "expected Unreadable"
                f.ignored -> if (outcome !is Outcome.Ignored) differences += "expected Ignored"
                outcome !is Outcome.Txn -> differences += "expected transaction: ${expected(f)}"
                else -> {
                    val a = outcome.accepted
                    fun check(label: String, expected: Any?, actual: Any?) {
                        if (expected != null && expected != actual) differences += "$label expected <$expected> but was <$actual>"
                    }
                    check("direction", f.direction, a.parsed.direction)
                    check("amount (paise)", f.paise, a.parsed.amountPaise)
                    check("merchant", f.merchant, a.merchant.name)
                    check("category", f.category, a.categorization.category)
                    check("account tail", f.tail, a.parsed.accountTail)
                    check("instrument", f.instrument, a.parsed.instrument)
                    check("reference", f.reference, a.parsed.reference)
                    check("needsReview", f.review, a.needsReview)
                    check("balance (paise)", f.balancePaise, a.parsed.balancePaise)
                    f.occurredAt?.let {
                        val timestamp = LocalDateTime.parse(it).atZone(ist).toInstant().toEpochMilli()
                        check("occurredAt (IST)", timestamp, a.parsed.occurredAtMillis)
                    }
                }
            }
            if (differences.isNotEmpty()) failures[f.name] = buildString {
                appendLine(f.name)
                // Only synthetic corpus data is printed, never a phone message or a production log.
                appendLine("  sender: ${f.sender}")
                appendLine("  message: ${f.body.replace("\n", "\\n").replace("\t", "\\t")}")
                appendLine("  expected: ${expected(f)}")
                appendLine("  actual: ${describe(outcome)}")
                append("  differences: ${differences.joinToString("; ")}")
            }
        }
        val actualFailures = failures.keys.toSet()
        val newlyBroken = actualFailures - knownFailures
        val unexpectedlyFixed = knownFailures - actualFailures
        val details = buildString {
            appendLine("Adversarial corpus: ${AdversarialFixtures.all.size} fixtures, " +
                "${AdversarialFixtures.all.size - failures.size} passing, ${failures.size} failing")
            appendLine("New failures (investigate regressions): ${newlyBroken.sorted()}")
            appendLine("Fixed failures (remove these names from knownFailures): ${unexpectedlyFixed.sorted()}")
            append(failures.values.joinToString("\n\n"))
        }
        println(details)
        assertEquals(knownFailures, actualFailures, "\n$details")
    }

    private fun expected(f: AdversarialFixture): String = when {
        f.unreadable -> "Unreadable"
        f.ignored -> "Ignored"
        else -> "${f.direction} ${f.paise} paise; merchant=${f.merchant ?: "<unchecked>"}; category=${f.category}; " +
            "tail=${f.tail ?: "<unchecked>"}; instrument=${f.instrument}; review=${f.review}" +
            (f.reference?.let { "; reference=$it" } ?: "") +
            (f.occurredAt?.let { "; occurredAt=$it IST" } ?: "") +
            (f.balancePaise?.let { "; balance=$it paise" } ?: "")
    }

    private fun describe(o: Outcome?): String = when (o) {
        is Outcome.Txn -> with(o.accepted) {
            "${parsed.direction} ${parsed.amountPaise} paise; merchant=${merchant.name}; category=${categorization.category}; " +
                "tail=${parsed.accountTail}; instrument=${parsed.instrument}; review=$needsReview; " +
                "reference=${parsed.reference}; occurredAt=${java.time.Instant.ofEpochMilli(parsed.occurredAtMillis).atZone(ist)}; " +
                "balance=${parsed.balancePaise} paise"
        }
        else -> o.toString()
    }
}
