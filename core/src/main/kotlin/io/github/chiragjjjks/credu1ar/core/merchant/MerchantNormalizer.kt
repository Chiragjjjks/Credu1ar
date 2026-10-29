package io.github.chiragjjjks.credu1ar.core.merchant

import io.github.chiragjjjks.credu1ar.core.model.PayeeKind

/** A cleaned-up payee. [key] is stable and lowercase: it is what category rules and recurring detection use. */
data class Merchant(val key: String, val name: String, val brand: Brand?, val kind: PayeeKind)

object MerchantNormalizer {
    private val railPrefix = Regex("""(?i)^(?:pyu|payu|raz|rzp|paytm|ptm|billdesk|bd|upi|pos|ecom|ib|neft|imps|rtgs|ach|mmt|cashfree|ccavenue)[\s*\-/]+""")
    private val suffixNoise = setOf("pvt", "ltd", "limited", "private", "llp", "india", "inc", "corp", "co")
    private val phoneLocal = Regex("""^(?:\+?91)?\d{10}(?:-\d)?$""")

    fun resolve(rawName: String?, vpa: String?, kindHint: PayeeKind = PayeeKind.UNKNOWN): Merchant {
        var cleaned = rawName.orEmpty().trim()
        repeat(2) { cleaned = cleaned.replace(railPrefix, "") }
        val vpaLocal = vpa?.substringBefore('@')?.lowercase()

        val brand = MerchantDirectory.match(cleaned, vpaLocal)
        if (brand != null) return Merchant(brand.key, brand.name, brand, PayeeKind.MERCHANT)

        val words = cleaned.split(Regex("""[^A-Za-z0-9&']+"""))
            .filter { it.isNotBlank() && !it.all(Char::isDigit) && it.lowercase() !in suffixNoise }
        val vpaWords = MerchantDirectory.tokens(vpaLocal)

        val display = when {
            words.isNotEmpty() -> words.take(5).joinToString(" ") { it.titleCase() }
            vpaWords.isNotEmpty() -> vpaWords.take(3).joinToString(" ") { it.titleCase() }
            else -> "Unknown"
        }
        val keyWords = if (words.isNotEmpty()) words.take(3).map { it.lowercase() } else vpaWords.take(3)
        val key = keyWords.joinToString("_").replace(Regex("[^a-z0-9_]"), "").ifBlank { "unknown" }

        val kind = when {
            kindHint != PayeeKind.UNKNOWN -> kindHint
            vpaLocal != null && phoneLocal.matches(vpaLocal) -> PayeeKind.PERSON
            else -> PayeeKind.UNKNOWN
        }
        // A phone-number handle has no human name: show it masked rather than as a bare number.
        val shown = if (words.isEmpty() && vpaLocal != null && phoneLocal.matches(vpaLocal)) "Person ••${vpaLocal.filter(Char::isDigit).takeLast(4)}" else display
        return Merchant(key, shown, null, kind)
    }

    private val acronyms = setOf("kfc", "lic", "atm", "upi", "sbi", "hdfc", "icici", "bsnl", "bmtc", "oyo", "pvr", "tvs", "itc", "dmrc", "emi", "gst", "nps", "ppf")

    /** Banks shout names in capitals ("SRI LAKSHMI STORES"); show "Sri Lakshmi Stores", but keep real acronyms. */
    private fun String.titleCase(): String =
        if (lowercase() in acronyms) uppercase()
        else lowercase().replaceFirstChar { it.uppercase() }
}
