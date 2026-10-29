package io.github.chiragjjjks.credu1ar.core.category

import io.github.chiragjjjks.credu1ar.core.merchant.Merchant
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.PayeeKind

data class Categorization(val category: Category, val confidence: Float, val reason: String)

/** Below this a debit goes to the "Needs review" queue. */
const val REVIEW_THRESHOLD = 0.65f

/**
 * Decides a category. Order of trust:
 *  1. a rule the owner taught us (merchant key -> category),
 *  2. message hints (ATM, card-bill, salary, refund...),
 *  3. the brand directory,
 *  4. words in the merchant name ("kirana", "pharma", "restaurant"...),
 *  5. "looks like a person" -> Sent to people,
 *  6. Uncategorised.
 */
class Categorizer(private val rules: Map<String, Category> = emptyMap()) {

    fun categorize(m: Merchant, t: ParsedTxn): Categorization {
        val debit = t.direction == Direction.DEBIT

        rules[m.key]?.let { c ->
            val fits = if (debit) c.group.isSpend || c.group == Group.MOVES else c.group == Group.INCOME || c.group == Group.MOVES
            if (fits) return Categorization(c, 1.0f, "your rule")
        }

        if ("card_bill" in t.hints || m.brand?.category == Category.CARD_BILL) return Categorization(Category.CARD_BILL, 0.9f, "credit-card payment")
        if (debit && t.instrument == Instrument.ATM) return Categorization(Category.ATM_CASH, 0.95f, "ATM withdrawal")
        if ("self" in t.hints) return Categorization(Category.SELF_TRANSFER, 0.8f, "own-account transfer")
        if (debit && "invest" in t.hints) return Categorization(Category.INVESTMENTS, 0.85f, "investment")

        if (!debit) return creditCategory(m, t)

        m.brand?.let { b -> return Categorization(b.category, 0.95f, "known brand: ${b.name}") }
        if ("emi" in t.hints) return Categorization(Category.EMI_LOANS, 0.8f, "EMI / loan debit")
        if ("rent" in t.hints) return Categorization(Category.RENT, 0.75f, "mentions rent")

        val text = m.name.lowercase()
        for (rule in keywordRules) {
            if (rule.pattern.containsMatchIn(text)) return Categorization(rule.category, rule.confidence, "name says “${rule.pattern.find(text)!!.value}”")
        }

        val personLike = m.kind == PayeeKind.PERSON || (m.kind == PayeeKind.UNKNOWN && looksLikePerson(m.name))
        if (personLike) return Categorization(Category.SENT_TO_PEOPLE, 0.5f, "paid to a person")
        return Categorization(Category.UNCATEGORISED, 0.25f, "unknown merchant")
    }

    private fun creditCategory(m: Merchant, t: ParsedTxn): Categorization = when {
        "salary" in t.hints -> Categorization(Category.SALARY, 0.9f, "salary credit")
        "refund" in t.hints -> Categorization(Category.REFUND, 0.9f, "refund / cashback")
        m.brand != null -> Categorization(Category.REFUND, 0.75f, "credit from ${m.brand.name}")
        "interest" in t.hints -> Categorization(Category.MONEY_IN, 0.8f, "interest")
        else -> Categorization(Category.MONEY_IN, 0.7f, "money received")
    }

    fun needsReview(c: Categorization, direction: Direction): Boolean = direction == Direction.DEBIT && c.confidence < REVIEW_THRESHOLD

    private class KeywordRule(val pattern: Regex, val category: Category, val confidence: Float)

    private companion object {
        /**
         * [words] must be whole words. [glued] are long, unambiguous stems that also count inside a longer
         * token, because UPI handles run words together ("rajukirana@okaxis", "sriganeshpharma@ybl").
         */
        private fun kw(words: String, c: Category, conf: Float, glued: String? = null): KeywordRule {
            val whole = """\b(?:$words)\b"""
            return KeywordRule(Regex(if (glued == null) whole else "$whole|(?:$glued)"), c, conf)
        }

        /** Order matters: specific before generic. */
        val keywordRules = listOf(
            kw("kirana|kiranaa|provision|provisions|general\\s*stores?|grocer(?:y|ies)|departmental|dept\\s*store|ration", Category.PROVISION, 0.8f, glued = "kirana|provision"),
            kw("petrol|fuel|bunk|filling\\s*station|service\\s*station|petroleum|diesel", Category.FUEL, 0.8f, glued = "petrol"),
            kw("pharma|pharmacy|medical|medicals|chemist|drug|drugs|medicos", Category.PHARMACY, 0.8f, glued = "pharma|medical"),
            kw("hospital|clinic|diagnostics?|dental|doctor|nursing|labs?", Category.DOCTOR, 0.75f, glued = "hospital|clinic|diagnostic"),
            kw("gym|fitness|yoga|crossfit", Category.FITNESS, 0.75f),
            kw("salon|saloon|barber|beauty|spa|parlou?r|trimmers|hair", Category.PERSONAL_CARE, 0.75f),
            kw("school|college|university|academy|tuition|institute|coaching|classes", Category.EDUCATION, 0.75f),
            kw("insurance|assurance|premium", Category.INSURANCE, 0.75f),
            kw("recharge|broadband|fibre|fiber|postpaid|prepaid|dth|tata\\s*play|dish\\s*tv", Category.MOBILE_INTERNET, 0.75f),
            kw("electricity|water|bescom|bwssb", Category.UTILITIES, 0.7f),
            kw("parking|toll", Category.PARKING_TOLL, 0.8f),
            kw("milk|dairy|vegetables?|veg|fruits?|fresh|meat|chicken|mutton|fish|eggs?|poultry|farm", Category.FRESH_DAIRY, 0.65f),
            kw("cafe|café|coffee|tea|chai|bakery|bakers|sweets?|juice|ice\\s*cream|snacks|bake", Category.CAFES, 0.65f),
            kw("restaurants?|hotel|dhaba|biryani|kitchen|tiffin|mess|bhojanalay|bhavan|pub|brewery|brew|bar|grill|eatery|foods?|pizza|burger", Category.DINING_OUT, 0.6f, glued = "restaurant|biryani"),
            kw("super\\s*market|hyper\\s*market|mart|bazaar|bazar", Category.SUPERMARKET, 0.65f, glued = "supermarket"),
            kw("cabs?|taxi|auto|travels|tours", Category.CAB_AUTO, 0.6f),
            kw("stores?", Category.PROVISION, 0.6f),
        )

        private val businessWords = Regex("""\b(?:pvt|ltd|limited|llp|enterprises|traders|trading|agencies|industries|services|solutions|technologies|systems|company|corporation|centre|center|shop|mall|market)\b""")

        /** 1–3 alphabetic words, none looking like a business: "Raju Kumar", "Priya", "Rahul S" (bank names often use initials). */
        fun looksLikePerson(name: String): Boolean {
            val words = name.trim().split(' ').filter { it.isNotBlank() }
            if (words.isEmpty() || words.size > 3) return false
            if (businessWords.containsMatchIn(name.lowercase())) return false
            if (!words.all { w -> w.length <= 15 && w.all { it.isLetter() || it == '.' } }) return false
            return words.any { it.length >= 3 } // at least one real word, not just "A B"
        }
    }
}
