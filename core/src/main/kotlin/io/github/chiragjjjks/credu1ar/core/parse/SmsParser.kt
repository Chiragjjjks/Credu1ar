package io.github.chiragjjjks.credu1ar.core.parse

import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.ParseResult
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.PayeeKind
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.model.Source
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Turns one SMS / notification into a [ParsedTxn], or says why not.
 *
 * Design: a message is a *completed payment* only if it has (1) an INR amount that is not a balance,
 * (2) a past-tense money verb (debited, spent, credited, received...), and none of the "not an event"
 * signals (OTP, failed, requested, upcoming, due, promo). Everything else is discarded unread.
 * The parser is a pure function of its input so it can be tested with fixtures. See docs/PARSING.md.
 */
object SmsParser {
    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    // ---------------------------------------------------------------- amounts
    private const val CUR = """(?:rs\.?|inr\.?|₹|rupees?)"""
    private const val NUM = """(\d[\d,]*(?:\.\d{1,2})?)"""
    private val AMOUNT = Regex("""(?i)(?<![a-z0-9])$CUR[\s|]*$NUM""")
    private val VERB_AMOUNT = Regex("""(?i)\b(?:debited|credited|withdrawn|deducted)\s+(?:by|for|with|of)\s+(?:$CUR\s*)?$NUM""")
    private val BALANCE_CTX = Regex("""(?i)\b(?:avl|avail|available|bal|balance|limit|lmt|outstanding|due|minimum|min|total)\b\W{0,12}$""")
    /**
     * "Available balance in A/c XX1234 is Rs 9000", "Available credit limit on Card XX1234: Rs 80000", "Avl Bal INR 1,20,000".
     * The first amount after such a phrase is a balance or limit, never the payment.
     */
    private val BALANCE_SPAN = Regex("""(?i)\b(?:avl|avail(?:able)?|current|closing|ledger|total)\.?\s*(?:credit\s+)?(?:bal(?:ance)?|lmt|limit)\b(?:\s+(?:in|on|of|for)\s+(?:your\s+)?(?:a/c|acct?\.?|account|card)\b[^.\d]{0,8}[x*\d]{0,10})?[\s:=\-]*(?:is|in|of)?[\s:=\-]*$CUR[\s|]*$NUM""")
    private val BALANCE = Regex("""(?i)\b(?:avl|avail(?:able)?)\.?\s*(?:bal(?:ance)?|lmt|limit)?\W{0,6}$CUR\s*$NUM|\bbal(?:ance)?\W{0,6}$CUR\s*$NUM""")
    private val FOREIGN = Regex("""(?i)\b(?:usd|eur|gbp|aed|sgd|aud|cad|jpy|chf|thb|myr|\$)\s*\d""")

    // ---------------------------------------------------------------- "this is not a payment event"
    private val OTP = Regex("""(?i)\b(?:otp|one[- ]time[- ]password|verification code|passcode|login code|cvv)\b""")
    private val FAILED = Regex("""(?i)\b(?:failed|failure|declined|unsuccessful|not successful|could not be|unable to|insufficient)\b|\bnot\s+(?:been\s+)?(?:debited|credited|processed)\b""")
    private val REFUND_POSTED = Regex("""(?i)\brefund(?:ed)?\b.{0,40}\bcredited\b|\bcredited\b.{0,60}\brefund""")
    private val REQUESTED = Regex("""(?i)\b(?:has\s+)?requested\s+(?:money|rs|₹|inr)|\bcollect request\b|\bpayment request\b|\brequesting\b|\bplease pay\b|\bpay now\b""")
    private val UPCOMING = Regex("""(?i)\bwill\s+(?:only\s+)?be\s+(?:auto[- ]?)?(?:debited|deducted|charged|credited)\b|\bis due\b|\bdue (?:on|by|date)\b|\b(?:minimum|total)\s+(?:amount\s+)?due\b|\bstatement\b|\bbill (?:is )?generated\b|\breminder\b|\bupcoming\b|\bscheduled\b|\bto be debited\b""")
    private val PROMO = Regex("""(?i)\b(?:pre-?approved|apply now|loan up to|limited period|congratulations|you(?:'ve| have) won|use code|coupon|exclusive offer|cashback up to|unlock\b|shop(?:ping)? again|off on your next|convert (?:it |this )?to (?:easy )?emi|easy emi|fee waived|(?:complete|update) kyc|security awareness)\b""")
    /** Wording that says nothing happened: "No amount debited", "No money received yet", "no new transaction". */
    private val NEGATED = Regex("""(?i)\bno\s+(?:new\s+)?(?:amount|money|debit|credit|transaction|payment)\b[^.|]{0,20}\b(?:debited|credited|received|made|posted|yet|done)\b|\bno\s+new\s+transaction\b""")
    /** "If Rs 500 is debited without your consent..." is a warning, not an event. */
    private val HYPOTHETICAL = Regex("""(?i)\bif\s+$CUR\s*[\d,.]+\s+(?:is|was|gets|has\s+been)\b""")
    private val PENDING_REFUND = Regex("""(?i)\brefund\s+(?:initiated|pending|requested|processing|in\s+progress)\b|\bcredit\s+expected\b""")
    /** Merchant / telco / support messages that quote an amount but are not bank alerts. Only trusted if an account is named. */
    private val NON_BANK = Regex("""(?i)\b(?:order\s+confirmation|service\s+activation|receipt:|your\s+order|recharge\s+successful|plan\s+credited|validity\s+\d+\s+days|thank you for shopping|complaint|investigat\w*)\b""")
    /** An OTP challenge carries the code right next to the word: "Delivery OTP 640281", "123456 is your OTP". A mere warning does not. */
    private val OTP_CHALLENGE = Regex("""(?i)\botp\b[^.|]{0,25}\b\d{4,8}\b|\b\d{4,8}\b[^.|]{0,25}\botp\b""")

    // ---------------------------------------------------------------- direction cues
    private val DEBIT_CUE = Regex("""(?i)\b(?:debited|spent|paid|payment of|purchase|purchased|withdrawn|withdrawal|sent|transferred|trf to|deducted|charged)\b|\bdebit(?=\s+(?:inr|rs|₹))|(?<=/)dr(?=/)|\bdr(?=\s+(?:inr|rs|₹)\b)|\b(?:txn|transaction)\b[^.|]{0,40}\bcompleted\b""")
    private val CREDIT_CUE = Regex("""(?i)\b(?:credited|received|deposited|refunded|refund|cashback)\b|\bcredit(?=\s+(?:inr|rs|₹))|(?<=/)cr(?=/)|\bcr(?=\s+(?:inr|rs|₹)\b)""")
    /** Verbs that only appear once something has actually happened ("payment of" and "purchase" also appear in OTP texts). */
    private val STRONG_CUE = Regex("""(?i)\b(?:debited|credited|spent|withdrawn|deducted|received|refunded|sent|paid|transferred)\b|\bdebit(?=\s+(?:inr|rs|₹))|\bcredit(?=\s+(?:inr|rs|₹))|\bdr(?=\s+(?:inr|rs|₹)\b)|\b(?:txn|transaction)\b[^.|]{0,40}\bcompleted\b|\bcr(?=\s+(?:inr|rs|₹)\b)""")
    private val UPI_APPS = Regex("""(?i)google pay|gpay|phonepe|paytm|bhim|cred|amazon pay|whatsapp|paisa|navi|super\.money|mobikwik|freecharge""")
    private val GENERIC_PAYEE = setOf("emi", "bill", "payment", "charges", "fee", "fees", "purchase", "upi", "pos", "atm", "loan", "you", "your")
    private val PAREN_PAYEE = Regex("""\(([A-Za-z][A-Za-z0-9&' .\-]{2,30})\)""")
    /** "A/c XX1234 has been debited": the owner's account verb beats a counterparty's verb that happens to be nearer the amount. */
    private val OWNER_DIRECTION = Regex("""(?i)\b(?:a/c|acct?\.?|account)\s*(?:no\.?)?\s*[x*]*\d{3,6}\s+(?:has\s+been\s+|is\s+|was\s+)?(debited|credited)\b""")
    private val PAID_YOU = Regex("""(?i)^\s*(.{2,40}?)\s+(?:paid|sent)\s+you\s+(?:$CUR)\s*$NUM""")
    private val CARD_PAYMENT_RECEIVED = Regex("""(?i)payment\s+(?:of\s+.{0,25}?)?(?:has\s+been\s+)?received\b.{0,60}\bcredit\s*card|thank you for (?:the |your )?payment.{0,60}\bcredit\s*card|\bcredit\s*card.{0,60}payment.{0,30}\b(?:received|successful|credited)""")

    // ---------------------------------------------------------------- other fields
    private val TAIL = Regex("""(?i)\b(?:a/c|acct?\.?|account|card|ac)\s*(?:no\.?|number|ending(?:\s+with)?|#)?\s*[:\-]?\s*(?:[x*]+\s*)?(\d{3,6})\b|[x*]{2,}(\d{3,4})\b|\bending\s+(?:with\s+)?(\d{3,4})\b""")
    private val REF = Regex("""(?i)(?:upi\s*ref(?:erence)?\s*(?:no\.?|number|id)?|ref(?:erence)?\s*(?:no\.?|number|id)?|refno|rrn|utr|txn\s*(?:id|no\.?|number)|upi:|upi/(?:p2[map]|dr|cr)/)\s*[:\-#]?\s*([0-9]{8,18}|[A-Z]{4}[0-9A-Z]{8,20})""")
    private val VPA = Regex("""(?i)\b([a-z0-9][a-z0-9._\-]{1,40})@([a-z][a-z0-9]{1,20})\b(?!\.[a-z])""")
    private val SLASH_PAYEE = Regex("""(?i)UPI/(?:(P2M|P2A|P2P|DR|CR|MOB)/)?\d{8,}/([^/\n)(]{2,40})""")

    private val DATE_ISO = Regex("""\b(20\d{2})-(\d{2})-(\d{2})(?:[:T ](\d{2}):(\d{2})(?::(\d{2}))?)?""")
    private val DATE_NUM = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""")
    private val DATE_MON = Regex("""(?i)\b(\d{1,2})[-/ ]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-/ ,]*(\d{2,4})\b""")
    private val TIME = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?\b""")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Cut-off for payee search: everything after these is boilerplate ("Not you? Call...", balances). */
    private val BOILERPLATE = Regex("""(?i)\b(?:not you|if not you|if you did not|if not done by you|not done by you|call\s+\d|sms block|to block|dial\s+\d|download|avl\.?\s*(?:bal|lmt|limit)|available|for dispute|trxn\.? not|report at|bal(?:ance)?\s*[:\-]?\s*(?:rs|inr|₹))\b""")

    private const val NAME = """([A-Za-z0-9][A-Za-z0-9&'.\-@ ]{1,38}?)"""
    private const val END = """(?=\s+on\s+\d|\s+from\s+(?:a/c|acct?|account|your|vpa)\b|\s+(?:via|ref|refno|upi|txn|using|dated|dt|date|info|avl|bal|not|if|call|sms|block|rrn|utr|thru|through|for|id|successful|successfully|is|was|has|have|will|done|completed|credited|debited|and)\b|\s*[|(\[;,]|\.\s|\.$|\s+\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\s*$)"""
    private const val BAD_START = """(?!vpa\b|a/c\b|acct\b|account\b|card\b|your\b|the\b|you\b|a\b|an\b|date\b|bank\b|upi\b|self\b|rs\b|inr\b|₹|\d{1,2}[-/]|\d+\s)"""
    private val DEBIT_PAYEE = Regex("""(?i)\b(?:trf to|transfer to|paid to|sent to|to|at|towards|merchant)\s+$BAD_START$NAME$END""")
    private val CREDIT_PAYEE = Regex("""(?i)\b(?:from|by)\s+$BAD_START$NAME$END""")
    private val ON_PAYEE = Regex("""(?i)\bon\s+$BAD_START$NAME$END""")
    private val SEMI_PAYEE = Regex("""(?i);\s*([A-Za-z0-9&'.\- ]{2,40}?)\s+credited""")
    /** "SWIGGY credited; Rs 489 debited from your A/c ..." */
    private val LEAD_CREDITED = Regex("""(?i)(?:^|[|;]\s*)([A-Za-z][A-Za-z0-9&'.\- ]{1,38}?)\s+credited\s*[;,]""")

    fun parse(sig: RawSignal): ParseResult {
        if (sig.source == Source.SMS && SenderDirectory.isPhoneNumber(sig.sender)) return ParseResult.NotFinancial
        // Indian SMS headers end in -S/-T/-G for service/transactional and -P for promotional: never a payment alert.
        if (sig.source == Source.SMS && sig.sender.trim().endsWith("-P", ignoreCase = true)) return ParseResult.NotFinancial
        val body = sig.body.trim()
        if (body.length < 12) return ParseResult.NotFinancial
        val text = body.replace(Regex("""\s*\n\s*"""), " | ").replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase()

        val hasDebit = DEBIT_CUE.containsMatchIn(text)
        val hasCredit = CREDIT_CUE.containsMatchIn(text)
        if (!hasDebit && !hasCredit) return ParseResult.NotFinancial

        // An OTP text talks about "payment of Rs 450" but nothing has happened yet. A real alert that merely
        // ends with "never share your OTP" always has a completed-event verb (debited, spent, sent, credited...).
        if (OTP.containsMatchIn(text) && !STRONG_CUE.containsMatchIn(text)) return ParseResult.NotFinancial
        // An OTP challenge that comes BEFORE the money verb is a checkout step, whatever the verb says afterwards.
        val challenge = OTP_CHALLENGE.find(text)
        val completed = STRONG_CUE.find(text)
        if (challenge != null && completed != null && challenge.range.first < completed.range.first) return ParseResult.NotFinancial
        if ((FAILED.containsMatchIn(text) && !REFUND_POSTED.containsMatchIn(text)) || REQUESTED.containsMatchIn(text) || UPCOMING.containsMatchIn(text)) return ParseResult.NotFinancial
        if (NEGATED.containsMatchIn(text) || HYPOTHETICAL.containsMatchIn(text)) return ParseResult.NotFinancial
        if (PENDING_REFUND.containsMatchIn(text) && !REFUND_POSTED.containsMatchIn(text)) return ParseResult.NotFinancial
        val namesAnAccount = TAIL.containsMatchIn(text) || VPA.containsMatchIn(text)
        if (NON_BANK.containsMatchIn(text) && !namesAnAccount) return ParseResult.NotFinancial

        // "Rajesh paid you ₹500": a credit whose payer is the text before "paid".
        val paidYou = PAID_YOU.find(text)
        val cardPaymentIn = CARD_PAYMENT_RECEIVED.containsMatchIn(text)

        val amountMatch = chooseAmount(text) ?: run {
            return if (FOREIGN.containsMatchIn(text) && (hasDebit || hasCredit)) ParseResult.Unparsed("foreign currency") else ParseResult.NotFinancial
        }
        val (amountPaise, amountStart, amountEnd) = amountMatch
        if (amountPaise <= 0) return ParseResult.NotFinancial
        if (holdsSeveralPayments(text, paymentAmounts(text))) return ParseResult.Unparsed("more than one payment in one message")

        // Promo wording rejects the message when it sits at or before the sentence that holds the amount ("Cashback up to
        // Rs 500 credited ...") or when no account is named at all. A marketing footer AFTER a real, account-bearing debit
        // ("Rs 450 debited from A/c XX1234 ... Apply now for our new card.") must not hide the payment.
        PROMO.find(text)?.let { promo ->
            val sentenceEnd = text.indexOf('.', amountEnd).let { if (it < 0) text.length else it }
            if (promo.range.first <= sentenceEnd || !namesAnAccount) return ParseResult.NotFinancial
        }

        val ownerDirection = OWNER_DIRECTION.find(text)?.groupValues?.get(1)?.lowercase()
        val direction = when {
            paidYou != null || cardPaymentIn -> Direction.CREDIT
            ownerDirection == "debited" -> Direction.DEBIT
            ownerDirection == "credited" -> Direction.CREDIT
            else -> nearestDirection(text, amountStart, amountEnd) ?: return ParseResult.NotFinancial
        }

        val core = text.substring(0, BOILERPLATE.find(text)?.range?.first?.takeIf { it > amountEnd } ?: text.length)
        val vpa = VPA.find(core)?.value?.lowercase()
        val tail = TAIL.find(text)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }?.takeLast(4)
        val reference = REF.find(text)?.groupValues?.get(1)
        val fromUpiApp = sig.source == Source.NOTIFICATION && UPI_APPS.containsMatchIn(sig.sender + " " + sig.packageName.orEmpty())
        val instrument = if (fromUpiApp && !lower.contains("card")) Instrument.UPI else instrumentOf(lower, vpa)
        val bank = SenderDirectory.bankFor(sig.sender, body)
        val (payeeName, payeeKind) = if (paidYou != null) cleanName(paidYou.groupValues[1]) to PayeeKind.UNKNOWN else extractPayee(body, core, direction, vpa)
        val hints = hintsOf(lower, instrument, direction)
        val occurredAt = occurredAt(text, sig.receivedAtMillis)
        val balance = (BALANCE_SPAN.find(text) ?: BALANCE.find(text))?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }?.let(::toPaise)

        var confidence = 0.55f
        if (tail != null) confidence += 0.15f
        if (reference != null) confidence += 0.10f
        if (bank != null) confidence += 0.10f
        if (payeeName != null || vpa != null) confidence += 0.10f

        return ParseResult.Parsed(
            ParsedTxn(
                direction = direction,
                amountPaise = amountPaise,
                payeeName = payeeName,
                vpa = vpa,
                payeeKind = if (payeeKind == PayeeKind.UNKNOWN && vpa != null && isPhoneVpa(vpa)) PayeeKind.PERSON else payeeKind,
                accountTail = tail,
                instrument = instrument,
                bank = bank,
                reference = reference,
                occurredAtMillis = occurredAt,
                balancePaise = balance,
                hints = hints,
                confidence = confidence.coerceAtMost(1f),
            ),
        )
    }

    // ------------------------------------------------------------------------------------------------

    private data class Amt(val paise: Long, val start: Int, val end: Int)

    /** Every currency amount that is not a balance, limit or due figure, in order. */
    private fun paymentAmounts(text: String): List<Amt> {
        val balanceSpans = BALANCE_SPAN.findAll(text).map { it.range }.toList()
        return AMOUNT.findAll(text).filter { m ->
            balanceSpans.none { m.range.first in it } &&
                !BALANCE_CTX.containsMatchIn(text.substring(maxOf(0, m.range.first - 26), m.range.first))
        }.map { m -> Amt(toPaise(m.groupValues[1]), m.range.first, m.range.last + 1) }.toList()
    }

    /** "Rs 150 debited to ZEPTO ...; Rs 275 debited to SWIGGY ...": two different amounts, each next to its own verb. */
    private fun holdsSeveralPayments(text: String, amounts: List<Amt>): Boolean {
        if (amounts.map { it.paise }.distinct().size < 2) return false
        val verbs = STRONG_CUE.findAll(text).toList()
        fun gap(v: MatchResult, a: Amt) = if (v.range.last < a.start) a.start - v.range.last else if (v.range.first > a.end) v.range.first - a.end else 0
        val owners = amounts.map { a -> verbs.minByOrNull { gap(it, a) }?.takeIf { gap(it, a) <= 45 }?.range?.first }
        return owners.all { it != null } && owners.toSet().size == amounts.size
    }

    private fun chooseAmount(text: String): Amt? {
        paymentAmounts(text).firstOrNull()?.let { return it }
        // SBI style: "debited by 312.0" with no currency symbol.
        VERB_AMOUNT.find(text)?.let { return Amt(toPaise(it.groupValues[1]), it.range.first, it.range.last + 1) }
        return null
    }

    private fun toPaise(s: String): Long {
        val clean = s.replace(",", "")
        val whole = clean.substringBefore('.').toLongOrNull() ?: return 0
        val frac = clean.substringAfter('.', "").padEnd(2, '0').take(2).toLongOrNull() ?: 0
        return whole * 100 + frac
    }

    /** The money verb closest to the amount decides direction ("debited ... ; SWIGGY credited" is a debit). */
    private fun nearestDirection(text: String, start: Int, end: Int): Direction? {
        fun dist(m: MatchResult): Int = if (m.range.last < start) start - m.range.last else if (m.range.first > end) m.range.first - end else 0
        val d = DEBIT_CUE.findAll(text).minOfOrNull(::dist)
        val c = CREDIT_CUE.findAll(text).minOfOrNull(::dist)
        return when {
            d == null && c == null -> null
            c == null -> Direction.DEBIT
            d == null -> Direction.CREDIT
            d <= c -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    private fun instrumentOf(lower: String, vpa: String?): Instrument = when {
        Regex("""\batm\b""").containsMatchIn(lower) && Regex("""withdraw|cash""").containsMatchIn(lower) -> Instrument.ATM
        lower.contains("wallet") -> Instrument.WALLET
        lower.contains("upi") || vpa != null -> Instrument.UPI
        Regex("""\b(?:neft|imps|rtgs)\b""").containsMatchIn(lower) -> Instrument.TRANSFER
        Regex("""net ?banking""").containsMatchIn(lower) -> Instrument.NETBANKING
        Regex("""\bcard\b""").containsMatchIn(lower) -> Instrument.CARD
        else -> Instrument.ACCOUNT
    }

    private fun extractPayee(body: String, core: String, direction: Direction, vpa: String?): Pair<String?, PayeeKind> {
        // 1. Axis / SBI style "UPI/P2M/<ref>/NAME": also tells us merchant vs person.
        SLASH_PAYEE.find(body)?.let { m ->
            val kind = when (m.groupValues[1].uppercase()) {
                "P2M" -> PayeeKind.MERCHANT
                "P2A", "P2P" -> PayeeKind.PERSON
                else -> PayeeKind.UNKNOWN
            }
            cleanName(m.groupValues[2])?.let { return it to kind }
        }
        // 2. "to NAME" / "at NAME" (debit) or "from NAME" / "by NAME" (credit).
        val primary = if (direction == Direction.DEBIT) DEBIT_PAYEE else CREDIT_PAYEE
        primary.find(core)?.let { m -> cleanName(m.groupValues[1])?.let { return it to PayeeKind.UNKNOWN } }
        if (direction == Direction.DEBIT) {
            SEMI_PAYEE.find(core)?.let { m -> cleanName(m.groupValues[1])?.let { return it to PayeeKind.UNKNOWN } }
            LEAD_CREDITED.find(core)?.let { m -> cleanName(m.groupValues[1])?.let { return it to PayeeKind.UNKNOWN } }
            ON_PAYEE.find(core)?.let { m -> cleanName(m.groupValues[1])?.let { return it to PayeeKind.UNKNOWN } }
            // "...towards EMI for loan 123 (BAJAJ FINANCE)": the payee is the bracketed name.
            for (m in PAREN_PAYEE.findAll(core)) {
                if (Regex("""(?i)\b(?:upi|ref|refno|utr|rrn)\b""").containsMatchIn(m.groupValues[1])) continue
                cleanName(m.groupValues[1])?.let { return it to PayeeKind.UNKNOWN }
            }
        }
        return null to PayeeKind.UNKNOWN
    }

    /** Drops rail prefixes and VPAs ("NEFT-ACME LTD" -> "ACME LTD"); returns null if nothing useful is left. */
    private fun cleanName(raw: String): String? {
        var s = raw.trim().trim('.', ',', '-', ':', ';', '|').trim()
        if (s.contains('@')) return null
        s = s.replace(Regex("""(?i)^(?:neft|imps|rtgs|upi|ach|nach|ecs|pos|ecom|ib)[-/ ]+"""), "")
        s = s.replace(Regex("""\s+"""), " ").trim()
        if (s.length < 2 || s.all { !it.isLetter() }) return null
        if (s.lowercase() in GENERIC_PAYEE) return null
        return s
    }

    private fun isPhoneVpa(vpa: String): Boolean = Regex("""^(?:\+?91)?\d{10}(?:-\d)?$""").matches(vpa.substringBefore('@'))

    private fun hintsOf(lower: String, instrument: Instrument, direction: Direction): Set<String> = buildSet {
        if (Regex("""\b(?:salary|sal cr|payroll)\b""").containsMatchIn(lower)) add("salary")
        if (Regex("""\b(?:refund|refunded|reversal|reversed|cashback)\b""").containsMatchIn(lower)) add("refund")
        if (CARD_PAYMENT_RECEIVED.containsMatchIn(lower) || Regex("""credit\s*card.{0,30}(?:bill|payment|repayment|autopay)|(?:bill|payment|repayment|autopay).{0,30}credit\s*card""").containsMatchIn(lower)) add("card_bill")
        if (Regex("""\b(?:emi|loan|nach|ecs|ach d)\b""").containsMatchIn(lower)) add("emi")
        if (Regex("""\brent\b""").containsMatchIn(lower)) add("rent")
        if (Regex("""\b(?:sip|mutual fund|ppf|nps|demat)\b""").containsMatchIn(lower)) add("invest")
        if (Regex("""\b(?:own|self|same)\s+(?:account|a/c)\b""").containsMatchIn(lower)) add("self")
        if (Regex("""\binterest\b|int\.?\s*pd|int\.?\s*credit""").containsMatchIn(lower)) add("interest")
        if (Regex("""\b(?:merchandise|store\s+order)\b""").containsMatchIn(lower)) add("merch")
        if (instrument == Instrument.ATM) add("atm")
        if (Regex("""\bmandate\b|standing instruction|autopay""").containsMatchIn(lower)) add("mandate")
        if (direction == Direction.CREDIT && lower.contains("upi")) add("upi_in")
    }

    // ---------------------------------------------------------------------------------- dates

    private fun occurredAt(text: String, receivedAt: Long): Long {
        val received = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(receivedAt), IST)
        var date: LocalDate? = null
        var time: LocalTime? = null

        DATE_ISO.find(text)?.let { m ->
            date = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            if (m.groupValues[4].isNotEmpty()) time = safeTime(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toIntOrNull() ?: 0)
        }
        if (date == null) DATE_MON.find(text)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
            date = safeDate(year(m.groupValues[3]), month, m.groupValues[1].toInt())
        }
        if (date == null) DATE_NUM.find(text)?.let { m ->
            date = safeDate(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        val d = date ?: return receivedAt
        if (time == null) TIME.find(text)?.let { m -> time = safeTime(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0) }
        val t = time

        val at = when {
            t != null -> LocalDateTime.of(d, t)
            d == received.toLocalDate() -> received
            else -> LocalDateTime.of(d, LocalTime.NOON)
        }
        val millis = at.atZone(IST).toInstant().toEpochMilli()
        // A date far from "now" is a parsing accident (or a stale delayed SMS): trust the arrival time.
        return if (abs(millis - receivedAt) > 400L * 24 * 3600 * 1000 || millis > receivedAt + 24L * 3600 * 1000) receivedAt else millis
    }

    private fun year(s: String): Int = s.toInt().let { if (it < 100) 2000 + it else it }
    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()
    private fun safeTime(h: Int, m: Int, s: Int): LocalTime? = runCatching { LocalTime.of(h, m, s) }.getOrNull()
}
