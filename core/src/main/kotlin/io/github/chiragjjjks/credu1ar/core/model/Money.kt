package io.github.chiragjjjks.credu1ar.core.model

/** Amounts are integer paise everywhere. Formatting happens only at the UI edge. */
object Money {
    fun rupees(paise: Long): Long = paise / 100

    /** Indian digit grouping: 12345678 paise -> "1,23,456.78" (decimals only if non-zero). */
    fun format(paise: Long, withSymbol: Boolean = true): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val whole = abs / 100
        val frac = (abs % 100).toInt()
        val s = whole.toString()
        val grouped = if (s.length <= 3) s else {
            val head = s.dropLast(3)
            val tail = s.takeLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }
        val body = if (frac == 0) grouped else "$grouped.${frac.toString().padStart(2, '0')}"
        return (if (negative) "-" else "") + (if (withSymbol) "₹" else "") + body
    }
}
