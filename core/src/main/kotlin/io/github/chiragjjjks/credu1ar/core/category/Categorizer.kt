package io.github.chiragjjjks.credu1ar.core.category

import io.github.chiragjjjks.credu1ar.core.merchant.Merchant
import io.github.chiragjjjks.credu1ar.core.merchant.MerchantDirectory
import io.github.chiragjjjks.credu1ar.core.merchant.MerchantNormalizer
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Group
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.PayeeKind

data class Categorization(val category: Category, val confidence: Float, val reason: String)

/** Below this a debit goes to the "Needs review" queue. */
const val REVIEW_THRESHOLD = 0.65f

/** Confidence for a category guessed from a cut-off word or from the holder's own name: always below [REVIEW_THRESHOLD]. */
const val GUESS_CONFIDENCE = 0.6f

/**
 * Decides a category. Order of trust:
 *  1. a rule the owner taught us (merchant key -> category),
 *  2. message hints (ATM, card-bill, own account, bank charges, salary, refund, interest...),
 *  3. the brand directory,
 *  4. words in the merchant name ("kirana", "pharma", "restaurant"...), then the words a cut-off last word may be,
 *  5. "looks like a person" -> Sent to people,
 *  6. Uncategorised.
 */
class Categorizer(private val rules: Map<String, Category> = emptyMap()) {

    /** The owner's rule for [key], if there is one and it fits the direction (a debit never becomes salary). */
    fun rule(key: String, direction: Direction): Categorization? {
        // Payments with no payee share the key "unknown": a rule on it would refile all of them at once.
        if (key == "unknown") return null
        val c = rules[key] ?: return null
        val fits = if (direction == Direction.DEBIT) c.group.isSpend || c.group == Group.MOVES else c.group == Group.INCOME || c.group == Group.MOVES
        return if (fits) Categorization(c, 1.0f, "your rule") else null
    }

    fun categorize(m: Merchant, t: ParsedTxn): Categorization {
        val debit = t.direction == Direction.DEBIT

        rule(m.key, t.direction)?.let { return it }

        // CRED also sells merchandise: only a card-bill payment is "not spending".
        if (debit && m.brand?.category == Category.CARD_BILL && "merch" in t.hints) return Categorization(Category.ONLINE_SHOPPING, 0.8f, "store purchase")
        if ("card_bill" in t.hints || m.brand?.category == Category.CARD_BILL) return Categorization(Category.CARD_BILL, 0.9f, "credit-card payment")
        if (debit && t.instrument == Instrument.ATM) return Categorization(Category.ATM_CASH, 0.95f, "ATM withdrawal")
        // "own_name": a statement payee that matches the holder's name. Likely their own account, but the owner confirms.
        if ("self" in t.hints) return if ("own_name" in t.hints || "own_name_guess" in t.hints) Categorization(Category.SELF_TRANSFER, GUESS_CONFIDENCE, "paid to your own name")
            else Categorization(Category.SELF_TRANSFER, 0.8f, "own-account transfer")
        if (debit && "invest" in t.hints) return Categorization(Category.INVESTMENTS, 0.85f, "investment")
        if (debit && "charges" in t.hints) return Categorization(Category.BANK_CHARGES, 0.9f, "bank charges")

        if (!debit) return creditCategory(m, t)

        m.brand?.let { b -> return Categorization(b.category, 0.95f, "known brand: ${b.name}") }
        if ("emi" in t.hints) return Categorization(Category.EMI_LOANS, 0.8f, "EMI / loan debit")
        if ("rent" in t.hints) return Categorization(Category.RENT, 0.75f, "mentions rent")

        val text = m.name.lowercase()
        for (rule in keywordRules) {
            if (rule.pattern.containsMatchIn(text)) return Categorization(rule.category, rule.confidence, "name says “${rule.pattern.find(text)!!.value}”")
        }
        // "Sri Lakshmi Stor": try the keyword words a cut-off last word may stand for. Only a guess, so it stays in review.
        if (t.payeeTruncated) {
            val guesses = cutOffGuesses(text, t.payeeName)
            for (rule in keywordRules) for ((guess, word) in guesses) {
                if (rule.pattern.containsMatchIn(guess)) return Categorization(rule.category, minOf(rule.confidence, GUESS_CONFIDENCE), "name may say “$word” (cut short)")
            }
        }

        val personLike = m.kind == PayeeKind.PERSON || (m.kind == PayeeKind.UNKNOWN && m.key != "unknown" && looksLikePerson(m.name, t.payeeTruncated))
        if (personLike) return Categorization(Category.SENT_TO_PEOPLE, 0.5f, "paid to a person")
        return Categorization(Category.UNCATEGORISED, 0.25f, "unknown merchant")
    }

    private fun creditCategory(m: Merchant, t: ParsedTxn): Categorization = when {
        "salary" in t.hints -> Categorization(Category.SALARY, 0.9f, "salary credit")
        "refund" in t.hints -> Categorization(Category.REFUND, 0.9f, "refund / cashback")
        "interest" in t.hints -> Categorization(Category.INTEREST, 0.85f, "interest")
        // An employer or client paying by NEFT is income, even when its name holds a brand ("Amazon Development Centre").
        "neft" in t.hints && isCompany(t.payeeName, t.payeeTruncated) -> Categorization(Category.MONEY_IN, 0.8f, "transfer from a company")
        m.brand != null -> Categorization(Category.REFUND, 0.75f, "credit from ${m.brand.name}")
        else -> Categorization(Category.MONEY_IN, 0.7f, "money received")
    }

    fun needsReview(c: Categorization, direction: Direction): Boolean = direction == Direction.DEBIT && c.confidence < REVIEW_THRESHOLD

    private class KeywordRule(val pattern: Regex, val category: Category, val confidence: Float, val words: List<String>)

    companion object {
        /**
         * [words] must be whole words. [glued] are long, unambiguous stems that also count inside a longer
         * token, because UPI handles run words together ("rajukirana@okaxis", "sriganeshpharma@ybl").
         */
        private fun kw(words: String, c: Category, conf: Float, glued: String? = null): KeywordRule {
            val whole = """\b(?:$words)\b"""
            return KeywordRule(Regex(if (glued == null) whole else "$whole|(?:$glued)"), c, conf, spellings(words) + glued?.split('|').orEmpty())
        }

        /** Order matters: specific before generic. */
        private val keywordRules = listOf(
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
            kw("milk|dairy|vegetables?|veg|fruits?|meat|chicken|mutton|fish|eggs?|poultry", Category.FRESH_DAIRY, 0.65f),
            kw("cafe|café|coffee|tea|chai|bakery|bakers|cakes?|sweets?|juice|ice\\s*cream|snacks|bake", Category.CAFES, 0.65f),
            // "Fresh" alone is a weak word: "Fresh Juice Corner" is a juice bar; "Fresh Vegetables" is caught above.
            kw("fresh", Category.FRESH_DAIRY, 0.65f),
            kw("restaurants?|hotel|dhaba|biryani|kitchen|tiffin|mess|bhojanalay|bhavan|pub|brewery|brew|bar|grill|eatery|foods?|pizza|burger", Category.DINING_OUT, 0.6f, glued = "restaurant|biryani"),
            kw("super\\s*market|hyper\\s*market|mart|bazaar|bazar", Category.SUPERMARKET, 0.65f, glued = "supermarket"),
            kw("cabs?|taxi|auto|travels|tours", Category.CAB_AUTO, 0.6f),
            kw("stores?", Category.PROVISION, 0.6f),
            // "SRI SAI MED": a medical store whose name the bank shortened. Weaker than "medical", so it stays in review.
            kw("med", Category.PHARMACY, 0.6f),
        )

        /** Every word the keyword rules know, spelled out ("stores?" → store, stores): what a cut-off word may complete to. */
        val keywordWords: Set<String> = keywordRules.flatMap { it.words }.toSet()

        /**
         * The whole words a [kw] pattern can match, for completing cut-off names. Understands only what the rules use:
         * `a|b`, an optional letter (`stores?`), `(?:y|ies)`, and `\s*` between words (both "super market" and "supermarket").
         */
        private fun spellings(pattern: String): List<String> {
            val alternatives = mutableListOf<String>()
            var depth = 0
            var start = 0
            pattern.forEachIndexed { i, ch ->
                when (ch) {
                    '(' -> depth++
                    ')' -> depth--
                    '|' -> if (depth == 0) { alternatives += pattern.substring(start, i); start = i + 1 }
                }
            }
            alternatives += pattern.substring(start)
            return alternatives.flatMap { alt ->
                var outs = listOf("")
                var i = 0
                while (i < alt.length) {
                    when {
                        alt.startsWith("(?:", i) -> {
                            val end = alt.indexOf(')', i)
                            val options = alt.substring(i + 3, end).split('|')
                            i = end + 1
                            val optional = alt.getOrNull(i) == '?'
                            if (optional) i++
                            outs = outs.flatMap { o -> options.map { o + it } + (if (optional) listOf(o) else emptyList()) }
                        }
                        alt.startsWith("\\s*", i) -> { outs = outs.map { "$it " }; i += 3 }
                        alt.getOrNull(i + 1) == '?' -> { val c = alt[i]; outs = outs.flatMap { listOf(it + c, it) }; i += 2 }
                        else -> { val c = alt[i]; outs = outs.map { it + c }; i++ }
                    }
                }
                outs.flatMap { phrase -> phrase.split(' ') + (if (' ' in phrase) listOf(phrase.replace(" ", "")) else emptyList()) }
            }.filter { it.isNotBlank() }
        }

        /** [text] with its last word swapped for each keyword word it may be cut from; only when that word is the payee's own last word. */
        private fun cutOffGuesses(text: String, rawName: String?): List<Pair<String, String>> {
            val words = text.split(' ').filter { it.isNotBlank() }
            val last = words.lastOrNull() ?: return emptyList()
            val rawLast = rawName?.lowercase()?.split(Regex("""[^a-z0-9&']+"""))?.lastOrNull { it.isNotBlank() }
            if (rawLast != last) return emptyList()
            return MerchantDirectory.completions(last, keywordWords).map { (words.dropLast(1) + it).joinToString(" ") to it }
        }

        private val businessWordList = listOf(
            "pvt", "ltd", "limited", "llp", "enterprises", "traders", "trading", "agencies", "industries", "services",
            "solutions", "technologies", "systems", "company", "corporation", "centre", "center", "shop", "mall", "market",
        )
        private val businessWords = Regex("""\b(?:${businessWordList.joinToString("|")})\b""")

        /** Last words that mark a shop, whole or as a usual cut: "SHOPRITE RET", "THE CAKE FACTO", "SRI LAKSHMI STOR". */
        private val shopEndings = setOf("stor", "store", "stores", "ret", "rtl", "retail", "retails", "retailers", "facto", "factory", "corner", "emporium", "depot", "mart")

        /** What a cut-off last word may be the start of when telling a shop from a person. */
        private val shopWords: Set<String> = shopEndings + businessWordList + MerchantNormalizer.suffixNoise

        /** Words that make a NEFT sender a company (an employer or client) rather than a person. */
        private val companyWords: Set<String> = businessWordList.toSet() + MerchantNormalizer.suffixNoise +
            setOf("technology", "tech", "labs", "lab", "software", "consulting", "consultants", "infotech", "infosystems", "global", "ventures")

        /** [raw] names a company: a company word anywhere, or (cut short) a last word that starts one ("…PRIVATE LIMI"). */
        fun isCompany(raw: String?, truncated: Boolean = false): Boolean {
            val words = raw.orEmpty().lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotBlank() }
            if (words.any { it in companyWords }) return true
            return truncated && words.size > 1 && MerchantDirectory.completions(words.last(), companyWords).isNotEmpty()
        }

        /**
         * 1–3 alphabetic words, none looking like a business: "Raju Kumar", "Priya", "Rahul S" (bank names often use initials).
         * Not a person: a leading "the" ("The Cake Facto"), a shop ending ("Shoprite Ret"), or, when the name was cut
         * short ([truncated]), a last word that starts a business word ("Uber India Syst").
         */
        fun looksLikePerson(name: String, truncated: Boolean = false): Boolean {
            val words = name.trim().split(' ').filter { it.isNotBlank() }
            if (words.isEmpty() || words.size > 3) return false
            val lower = words.map { it.lowercase().trim('.') }
            if (lower.size > 1 && (lower.first() == "the" || lower.last() in shopEndings)) return false
            if (truncated && lower.size > 1 && MerchantDirectory.completions(lower.last(), shopWords).isNotEmpty()) return false
            if (businessWords.containsMatchIn(name.lowercase())) return false
            if (!words.all { w -> w.length <= 15 && w.all { it.isLetter() || it == '.' } }) return false
            return words.any { it.length >= 3 } // at least one real word, not just "A B"
        }
    }
}
