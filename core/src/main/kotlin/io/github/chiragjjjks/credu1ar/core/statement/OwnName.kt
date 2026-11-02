package io.github.chiragjjjks.credu1ar.core.statement

/**
 * Is a statement payee the account holder (a transfer to their own account at another bank)? Banks print names in
 * different orders and with initials: holder "Asha K" pays "ASHA K", "K ASHA" or "ASHA KUMARI".
 *
 * Match when both names have 2+ words, they share a word of 3+ letters, and every other word of the shorter name is a
 * prefix of a different remaining word of the longer one, or the other way round (initials: "K" ~ "Kumari").
 * So word order is loose, but two different full surnames never match ("Asha Rao" ≠ "Asha Kumari").
 *
 * Runs in memory only. The holder's name is never stored, logged or put into a merchant name or key.
 */
object OwnName {
    private val honorifics = setOf("mr", "mrs", "ms", "miss", "dr", "smt", "shri", "kum")

    private fun words(name: String?): List<String> =
        name.orEmpty().lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() && it !in honorifics }

    /** The same words as the holder's printed name, in any order ("K ASHA" for "Asha K"): it IS the printed name. */
    fun exact(payee: String?, holder: String?): Boolean {
        val p = words(payee)
        return p.size >= 2 && p.sorted() == words(holder).sorted()
    }

    /**
     * Every place the holder's printed name appears in [text] (either word order, any case, whole words) replaced by
     * [with]. Used on narrations before anything is stored, so the name can't be kept by accident in any row type.
     */
    fun scrub(text: String, holder: String?, with: String = "Own account"): String {
        val h = words(holder)
        if (h.size < 2) return text
        var out = text
        for (order in listOf(h, h.reversed()).distinct()) {
            val pattern = order.joinToString("""[\s.]+""", prefix = """(?i)(?<![A-Za-z])""", postfix = """(?![A-Za-z])""") { Regex.escape(it) }
            out = out.replace(Regex(pattern), with)
        }
        return out
    }

    fun matches(payee: String?, holder: String?): Boolean {
        val p = words(payee)
        val h = words(holder)
        if (p.size < 2 || h.size < 2) return false
        val (short, long) = if (p.size < h.size || (p.size == h.size && p.sumOf { it.length } <= h.sumOf { it.length })) p to h else h to p
        for (anchor in short.filter { it.length >= 3 }.distinct()) {
            if (anchor !in long) continue
            val restShort = short.toMutableList().apply { remove(anchor) }.sortedByDescending { it.length }
            val restLong = long.toMutableList().apply { remove(anchor) }
            val allClaimed = restShort.all { w ->
                val i = restLong.indexOfFirst { it.startsWith(w) || w.startsWith(it) }
                if (i >= 0) { restLong.removeAt(i); true } else false
            }
            if (allClaimed) return true
        }
        return false
    }
}
