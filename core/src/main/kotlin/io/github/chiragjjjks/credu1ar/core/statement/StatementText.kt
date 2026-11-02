package io.github.chiragjjjks.credu1ar.core.statement

import java.text.Normalizer
import java.time.LocalDate

/** Small, locale-independent text helpers shared by statement layouts. Never throw with input text in the message. */
object StatementText {
    /** NFKC (ligatures, full-width digits), every kind of space → ' ', Unicode minus → '-', soft hyphens and zero-widths removed. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .replace(Regex("[\\u00A0\\u2007\\u202F\\u2009\\u200A\\u3000]"), " ")
            .replace(Regex("[\\u00AD\\u200B\\u200C\\u200D\\uFEFF]"), "")
            .replace('−', '-')
            .replace("\r\n", "\n").replace('\r', '\n')

    /** One money token: `1,23,456.78`, `20,195.00`, `5.00`, optional leading `-`. */
    const val MONEY = """-?(?:\d{1,3}(?:,\d{2,3})+|\d+)\.\d{2}"""

    /** "1,23,456.78" → 12345678 paise without going through Double. Null if it isn't a money token. */
    fun paise(token: String): Long? {
        val t = token.trim()
        if (!Regex("^$MONEY$").matches(t)) return null
        val neg = t.startsWith("-")
        val digits = t.removePrefix("-").replace(",", "")
        val (r, f) = digits.split('.')
        val v = r.toLong() * 100 + f.toLong()
        return if (neg) -v else v
    }

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    /** "16 Sep 2026" (any case, "Sept" too) → date; null if invalid. Independent of the phone's locale. */
    fun date(dmy: String): LocalDate? {
        val m = Regex("""^(\d{1,2})[ -]([A-Za-z]{3,4})[ -](\d{4})$""").matchEntire(dmy.trim()) ?: return null
        val month = months[m.groupValues[2].lowercase()] ?: return null
        return runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
    }

    private val bankNames = listOf(
        "hdfc" to "HDFC", "icici" to "ICICI", "sbi" to "State Bank of India", "axis" to "Axis", "kotak" to "Kotak",
        "yes bank" to "Yes Bank", "idfc" to "IDFC First", "indusind" to "IndusInd", "federal" to "Federal",
        "canara" to "Canara", "bank of baroda" to "Bank of Baroda", "union bank" to "Union Bank", "au small" to "AU",
    )

    fun guessBank(text: String): String? {
        val t = text.lowercase()
        return bankNames.firstOrNull { (k, _) -> t.contains(k) }?.second
    }
}
