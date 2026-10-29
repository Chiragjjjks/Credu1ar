package io.github.chiragjjjks.credu1ar.core.sample

import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * Fictional but realistic bank messages for "Try with sample data" and for tests. Every number, name and reference is
 * invented. The messages are generated in five bank styles and run through the real parser, so sample mode behaves
 * exactly like live mode. Deterministic for a given [seed].
 */
object SampleData {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val dSlash = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH)
    private val dDash = DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH)
    private val dMon = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)
    private val dMonTight = DateTimeFormatter.ofPattern("ddMMMyy", Locale.ENGLISH)
    private val dIso = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm:ss", Locale.ENGLISH)
    private val dFull = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss", Locale.ENGLISH)

    fun signals(nowMillis: Long, months: Int = 3, seed: Long = 7): List<RawSignal> {
        val now = Instant.ofEpochMilli(nowMillis).atZone(IST).toLocalDateTime()
        val out = mutableListOf<RawSignal>()
        for (m in 0 until months) {
            val ym = YearMonth.from(now).minusMonths(m.toLong())
            val current = m == 0
            val maxDay = if (current) now.dayOfMonth else ym.lengthOfMonth()
            val scale = if (current) maxOf(maxDay.toDouble() / ym.lengthOfMonth(), 0.15) else 1.0
            MonthGen(Random(seed + m * 31), ym, maxDay, scale, now, out).generate()
        }
        return out.sortedBy { it.receivedAtMillis }
    }

    private class MonthGen(
        val rng: Random, val ym: YearMonth, val maxDay: Int, val scale: Double,
        val now: LocalDateTime, val out: MutableList<RawSignal>,
    ) {
        private fun ref(): String = (630_000_000_000L + rng.nextLong(0, 9_999_999_999L)).toString()
        private fun amt(paise: Long): String = String.format(Locale.US, "%,.2f", paise / 100.0)
        private fun plain(paise: Long): String = String.format(Locale.US, "%.1f", paise / 100.0)
        private fun rupees(lo: Int, hi: Int): Long = rng.nextInt(lo, hi + 1) * 100L - (if (rng.nextInt(3) == 0) rng.nextInt(0, 20) * 100L else 0L).coerceAtLeast(0)

        private fun at(day: Int, hour: Int, minute: Int = rng.nextInt(0, 60)): LocalDateTime? {
            val d = LocalDateTime.of(ym.year, ym.month, day.coerceIn(1, ym.lengthOfMonth()), hour.coerceIn(0, 23), minute, rng.nextInt(0, 60))
            return if (d.isAfter(now)) null else d
        }

        private fun push(sender: String, body: String, t: LocalDateTime) {
            out += RawSignal(Source.SAMPLE, sender, body, t.atZone(IST).toInstant().toEpochMilli())
        }

        private fun count(n: Int) = maxOf(1, (n * scale + 0.5).toInt())
        private fun anyDay() = rng.nextInt(1, maxDay + 1)

        // ---- bank message styles --------------------------------------------------------------------------

        /** UPI debit in one of five bank styles, chosen by [style]. */
        private fun upi(style: Int, paise: Long, name: String, vpa: String, t: LocalDateTime) {
            val r = ref()
            when (style % 5) {
                0 -> push("VM-HDFCBK-S", "Sent Rs.${amt(paise)}\nFrom HDFC Bank A/c *1234\nTo ${name.uppercase()}\nOn ${t.format(dSlash)}\nRef $r\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", t)
                1 -> push("AD-HDFCBK-S", "Rs.${amt(paise)} debited from a/c **1234 on ${t.format(dDash)} to VPA $vpa(UPI Ref No $r). Not you? Call 18002586161", t)
                2 -> push("AD-ICICIB-S", "ICICI Bank Acct XX234 debited for Rs ${amt(paise)} on ${t.format(dMon)}; ${name.uppercase()} credited. UPI:$r. Call 18002662 for dispute. SMS BLOCK 234 to 9215676766.", t)
                3 -> push("VK-SBIUPI-S", "Dear UPI user A/C X1234 debited by ${plain(paise)} on date ${t.format(dMonTight)} trf to ${name.uppercase()} Refno $r. If not u? call 1800111109. -SBI", t)
                else -> push("AX-AXISBK-S", "Debit INR ${amt(paise)}\nAxis Bank A/c XX1234\n${t.format(dFull)}\nUPI/P2M/$r/${name.uppercase()}\nNot you? SMS BLOCK UPI to 7308080808", t)
            }
        }

        private fun card(style: Int, paise: Long, name: String, t: LocalDateTime) {
            if (style % 2 == 0) push("VM-HDFCBK-S", "Spent Rs.${amt(paise)} On HDFC Bank Card 4321 At ${name.uppercase()} On ${t.format(dIso)}.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 4321 to 7308080808", t)
            else push("VM-ICICIT-S", "INR ${amt(paise)} spent using ICICI Bank Card XX1234 on ${t.format(dMon)} on ${name.uppercase()}. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766.", t)
        }

        private fun repeat(n: Int, hours: IntRange, paise: () -> Long, emit: (Long, LocalDateTime) -> Unit) {
            repeat(count(n)) {
                val t = at(anyDay(), rng.nextInt(hours.first, hours.last + 1)) ?: return@repeat
                emit(paise(), t)
            }
        }

        fun generate() {
            var style = rng.nextInt(5)
            fun s() = style++

            // ---- fixed monthly rhythm ----
            at(1, 9, 12)?.let { t -> push("VM-HDFCBK-S", "UPDATE: INR 85,000.00 credited to HDFC Bank A/c XX1234 on ${t.format(dDash)} from NEFT-ACME TECHNOLOGIES SALARY. Avl bal INR 1,20,000.00", t) }
            at(2, 10, 5)?.let { upi(s(), 2_800_000, "Landlord Sharma", "landlord.sharma@ybl", it) }
            at(3, 11, 20)?.let { upi(s(), 500_000, "Groww Invest", "groww.sip@icici", it) }
            at(5, 8, 40)?.let { upi(s(), 105_000, "ACT Fibernet", "actfibernet@axisbank", it) }
            at(6, 9, 15)?.let { upi(s(), 39_900, "Airtel Prepaid", "airtel@icici", it) }
            at(7, 7, 2)?.let { card(s(), 64_900, "Netflix", it) }
            at(9, 7, 10)?.let { card(s(), 11_900, "Spotify", it) }
            at(12, 7, 30)?.let { card(s(), 13_000, "Google Play", it) }
            at(10, 18, 45)?.let { upi(s(), rupees(1100, 1850), "BESCOM", "bescom@icici", it) }
            at(14, 6, 55)?.let { upi(s(), 150_000, "Cult Fit", "cultfit@okaxis", it) }
            at(15, 20, 3)?.let { t -> push("VM-HDFCBK-S", "Rs.14,500.00 debited from a/c **1234 on ${t.format(dDash)} to VPA cred.club@axisb(UPI Ref No ${ref()}). Not you? Call 18002586161", t) }
            at(15, 20, 4)?.let { t -> push("VM-HDFCBK-S", "Payment of Rs.14,500.00 received on your HDFC Bank Credit Card ending 4321 on ${t.format(dDash)}. Thank you.", t) }
            at(18, 13, 10)?.let { t -> push("VM-SBIINB-S", "Rs.2000.00 withdrawn from ATM on ${t.format(dDash)} using Card XX1234. Avl Bal Rs 8,000.00", t) }
            at(20, 19, 25)?.let { t -> push("VM-KOTAKB-S", "Received Rs.1,200.00 in your Kotak Bank AC X1234 from friend@ybl on ${t.format(dDash)}.UPI Ref:${ref()}.", t) }
            at(21, 11, 40)?.let { t -> push("VM-HDFCBK-S", "Refund of Rs 312.00 for your order from ZEPTO has been credited to A/c XX1234", t) }

            // ---- groceries: quick commerce vs provision store vs supermarket vs fresh ----
            repeat(12, 7..22, { rupees(180, 680) }) { p, t -> upi(s(), p, "Zepto Marketplace", "zepto.payu@axisbank", t) }
            repeat(6, 7..22, { rupees(150, 520) }) { p, t -> upi(s(), p, "Blinkit", "blinkit@icici", t) }
            repeat(3, 8..21, { rupees(220, 640) }) { p, t -> upi(s(), p, "Swiggy Instamart", "swiggy.instamart@axisbank", t) }
            repeat(1, 9..12, { rupees(1300, 1800) }) { p, t -> upi(s(), p, "BigBasket", "bigbasket@hdfcbank", t) }
            repeat(5, 8..20, { rupees(60, 420) }) { p, t -> upi(0, p, "Sri Lakshmi Stores", "srilakshmistores@okaxis", t) }
            repeat(4, 8..20, { rupees(80, 260) }) { p, t -> upi(1, p, "Raju Kirana", "rajukirana@okaxis", t) }
            repeat(4, 6..10, { rupees(60, 240) }) { p, t -> upi(0, p, "Fresh Vegetables And Fruits", "freshveg@ybl", t) }
            repeat(8, 6..9, { rupees(24, 70) }) { p, t -> upi(1, p, "Nandini Milk Parlour", "nandinimilk@ybl", t) }
            repeat(1, 11..18, { rupees(1900, 2900) }) { p, t -> card(s(), p, "DMart", t) }

            // ---- food ----
            repeat(9, 19..23, { rupees(230, 720) }) { p, t -> upi(s(), p, "Swiggy", "swiggy@icici", t) }
            repeat(4, 20..23, { rupees(280, 640) }) { p, t -> upi(s(), p, "Zomato", "zomato@hdfcbank", t) }
            repeat(1, 20..22, { rupees(480, 560) }) { p, t -> card(s(), p, "Dominos", t) }
            repeat(3, 8..17, { rupees(320, 480) }) { p, t -> card(s(), p, "Starbucks", t) }
            repeat(3, 15..18, { rupees(90, 180) }) { p, t -> upi(s(), p, "Chaayos", "chaayos@okaxis", t) }

            // ---- transport ----
            repeat(6, 8..21, { rupees(140, 420) }) { p, t -> upi(s(), p, "Uber India", "uber@axisbank", t) }
            repeat(4, 8..20, { rupees(60, 140) }) { p, t -> upi(s(), p, "Rapido", "rapido@ybl", t) }
            repeat(8, 7..10, { rupees(30, 60) }) { p, t -> upi(s(), p, "BMRCL Namma Metro", "bmrcl@icici", t) }
            repeat(2, 10..19, { rupees(1500, 2000) }) { p, t -> card(s(), p, "Indian Oil", t) }

            // ---- shopping and health ----
            repeat(3, 10..22, { rupees(499, 2999) }) { p, t -> card(s(), p, "Amazon Pay", t) }
            repeat(1, 12..22, { rupees(1799, 1799) }) { p, t -> card(s(), p, "Myntra", t) }
            repeat(2, 9..20, { rupees(220, 680) }) { p, t -> upi(s(), p, "Apollo Pharmacy", "apollopharmacy@icici", t) }

            // ---- people (these land in "Needs review": the app cannot know what they were for) ----
            repeat(1, 10..21, { 50_000 }) { p, t -> upi(0, p, "Rahul S", "rahul.s@ybl", t) }
            repeat(1, 10..21, { 120_000 }) { p, t -> upi(1, p, "Priya M", "priya.m@oksbi", t) }
            repeat(1, 10..21, { 30_000 }) { p, t -> upi(1, p, "", "9876543210@ybl", t) }
        }
    }
}
