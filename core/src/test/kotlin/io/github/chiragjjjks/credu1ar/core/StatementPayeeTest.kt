package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.category.Categorizer
import io.github.chiragjjjks.credu1ar.core.merchant.MerchantDirectory
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Category.*
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Direction.CREDIT
import io.github.chiragjjjks.credu1ar.core.model.Direction.DEBIT
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.ParseResult
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.PayeeKind
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.parse.SmsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Payee names as bank statements print them: cut short at ~14–18 characters, so the last word may be a fragment.
 * Every name here is made up or a public brand. Each case states what it must (or must never) become.
 */
class StatementPayeeTest {
    private data class Case(
        val payee: String?,
        val category: Category? = null,
        val merchant: String? = null,
        val review: Boolean? = null,
        val direction: Direction = DEBIT,
        val hints: Set<String> = emptySet(),
        val truncated: Boolean = true,
        /** Must never become this category or any brand: checked with and without the cut-off flag unless [neverOnlyCut]. */
        val never: Category? = null,
        val neverOnlyCut: Boolean = false,
    )

    private fun parsed(c: Case, truncated: Boolean = c.truncated) = ParsedTxn(
        direction = c.direction, amountPaise = 10_000, payeeName = c.payee, vpa = null, payeeKind = PayeeKind.UNKNOWN,
        accountTail = "4321", instrument = Instrument.UPI, bank = "Kotak", reference = null, occurredAtMillis = 0L,
        balancePaise = null, hints = c.hints, confidence = 0.9f, payeeTruncated = truncated,
    )

    private val cases = listOf(
        // Cut-off last words completed from the directory's own phrase words.
        Case("BANGALORE METR", METRO_BUS, "Namma Metro", review = false),
        Case("Bangalore Metr", METRO_BUS, "Namma Metro", review = false),
        Case("SWIGGY INSTAMAR", QUICK_COMMERCE, "Swiggy Instamart", review = false),
        Case("AVENUE SUPERMAR", SUPERMARKET, "DMart", review = false),
        Case("Third Wave Coff", CAFES, "Third Wave Coffee", review = false),
        // ... and from keyword words, as a guess that stays in review.
        Case("Sri Lakshmi Stor", PROVISION, "Sri Lakshmi Stor", review = true),
        Case("SRI SAI RAM MED", PHARMACY, review = true),
        // Plain words, but the order of keyword rules matters.
        Case("THE CAKE FACTO", CAFES, "The Cake Facto", review = false),
        Case("Fresh Juice Cor", CAFES, "Fresh Juice Cor", review = false),
        // Brands that are whole words before the cut.
        Case("Zomato Media P", FOOD_DELIVERY, "Zomato"),
        Case("NETFLIX COM", STREAMING, "Netflix", truncated = false),
        Case("Google Play", SUBSCRIPTIONS, "Google Play", hints = setOf("autopay"), truncated = false),
        // A fragment must never make a single-word brand match.
        Case("MANOJ GROW", never = INVESTMENTS),
        Case("SRI DEVI DECA", never = FASHION),
        // A short fragment can equal a short alias by accident ("Amulya" cut to "AMUL"). Whole, "AMUL" may be Amul.
        Case("PRIYANKA AMUL", never = FRESH_DAIRY, neverOnlyCut = true),
        // A shop is not a person, even when its name is cut.
        Case("SHOPRITE RET", UNCATEGORISED, review = true, truncated = false),
        Case("THE GREEN LEAF", UNCATEGORISED, review = true),
        Case("RAVI KUMAR", SENT_TO_PEOPLE, "Ravi Kumar", review = true, truncated = false),
        // Statement hints.
        Case(null, BANK_CHARGES, "Bank charges", review = false, hints = setOf("charges"), truncated = false),
        Case(null, INTEREST, "Interest", direction = CREDIT, hints = setOf("interest"), truncated = false),
        Case(null, REFUND, "Cashback", direction = CREDIT, hints = setOf("cashback", "refund"), truncated = false),
        Case("BANGALORE METR", REFUND, "Namma Metro", direction = CREDIT, hints = setOf("refund")),
        Case("ACME SOFTWARE PRIVATE LIMITED IC", MONEY_IN, direction = CREDIT, hints = setOf("neft")),
        Case("GLOBEX LABS", MONEY_IN, direction = CREDIT, hints = setOf("neft"), truncated = false),
        // An employer whose name holds a brand is income, not a refund from that brand.
        Case("AMAZON DEVELOPMENT CENTRE", MONEY_IN, direction = CREDIT, hints = setOf("neft")),
    )

    @Test fun everyStatementPayeeLandsWhereItShould() {
        val failures = mutableListOf<String>()
        for (c in cases) {
            val a = Pipeline.accept(parsed(c))
            val label = "${c.payee ?: "<no payee>"} (${c.direction}, ${c.hints})"
            fun check(what: String, expected: Any?, actual: Any?) {
                if (expected != null && expected != actual) failures += "$label: $what expected <$expected> but was <$actual>"
            }
            check("category", c.category, a.categorization.category)
            check("merchant", c.merchant, a.merchant.name)
            check("needsReview", c.review, a.needsReview)
            if (c.never != null) for (t in if (c.neverOnlyCut) listOf(true) else listOf(true, false)) {
                val b = Pipeline.accept(parsed(c, truncated = t))
                if (b.categorization.category == c.never || b.merchant.brand != null) {
                    failures += "$label (truncated=$t): must not match a brand or ${c.never}, got ${b.merchant.name} / ${b.categorization.category}"
                }
            }
        }
        assertTrue(failures.isEmpty(), "\n${failures.size} problem(s):\n" + failures.joinToString("\n") { " - $it" })
    }

    @Test fun completionsComeFromTheDirectorysOwnWords() {
        assertEquals(listOf("store", "stores"), MerchantDirectory.completions("stor", Categorizer.keywordWords))
        listOf("grocery", "groceries", "parlour", "parlor", "supermarket", "general", "cakes").forEach {
            assertTrue(it in Categorizer.keywordWords, "$it missing from keyword words")
        }
        assertEquals(emptyList(), MerchantDirectory.completions("p", Categorizer.keywordWords), "fragments under 3 letters are never completed")
        assertEquals(emptyList(), MerchantDirectory.completions("store", listOf("store")), "a whole word needs no completion")
    }

    @Test fun shopsAreNotPeople() {
        assertFalse(Categorizer.looksLikePerson("The Cake Facto"))
        assertFalse(Categorizer.looksLikePerson("Shoprite Ret"))
        assertFalse(Categorizer.looksLikePerson("Sri Lakshmi Stor"))
        assertFalse(Categorizer.looksLikePerson("Ganesh Retail"))
        assertFalse(Categorizer.looksLikePerson("Navira Syst", truncated = true))
        assertTrue(Categorizer.looksLikePerson("Ravi Kumar"))
        assertTrue(Categorizer.looksLikePerson("Priya M"))
        assertTrue(Categorizer.looksLikePerson("Manoj Grow", truncated = true))
    }

    @Test fun aRuleTaughtForAPayeelessRowApplies() {
        val cashback = Case(null, direction = CREDIT, hints = setOf("cashback", "refund"), truncated = false)
        val a = Pipeline.accept(parsed(cashback), mapOf("cashback" to MONEY_IN))
        assertEquals(MONEY_IN, a.categorization.category)
        assertEquals("your rule", a.categorization.reason)
        assertEquals("cashback", a.merchant.key)
    }

    @Test fun smsPaymentsAreNeverMarkedCutShort() {
        val now = 1_800_000_000_000L
        val parsed = (ParserFixtures.all.map { RawSignal(it.source, it.sender, it.body, now) } +
            AdversarialFixtures.all.map { RawSignal(it.source, it.sender, it.body, now) })
            .mapNotNull { (SmsParser.parse(it) as? ParseResult.Parsed)?.txn }
        assertTrue(parsed.size > 50)
        assertTrue(parsed.none { it.payeeTruncated })
    }
}
