package io.github.chiragjjjks.credu1ar.core.parse

/** Maps SMS headers ("VM-HDFCBK-S") and message text to a readable bank label. */
object SenderDirectory {
    /** Prefix of the normalised header -> label. Longest prefix wins. */
    private val byPrefix: Map<String, String> = mapOf(
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "SBI" to "SBI", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Bank", "PNB" to "PNB", "YESB" to "Yes Bank", "YES" to "Yes Bank",
        "IDFC" to "IDFC First", "INDUS" to "IndusInd Bank", "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
        "UNION" to "Union Bank", "UBI" to "Union Bank", "BOB" to "Bank of Baroda", "BARODA" to "Bank of Baroda",
        "BOI" to "Bank of India", "AUBANK" to "AU Small Finance", "AUSFB" to "AU Small Finance",
        "FEDBNK" to "Federal Bank", "FEDERAL" to "Federal Bank", "IDBI" to "IDBI Bank", "RBL" to "RBL Bank",
        "PAYTM" to "Paytm Payments Bank", "PYTM" to "Paytm Payments Bank", "AMEX" to "American Express",
        "SCB" to "Standard Chartered", "HSBC" to "HSBC", "CITI" to "Citi", "JUPITER" to "Jupiter",
        "EPIFI" to "Fi Money", "FIBANK" to "Fi Money", "SLICE" to "Slice", "ONECRD" to "OneCard", "ONECARD" to "OneCard",
        "CENTBK" to "Central Bank", "IOB" to "Indian Overseas Bank", "SIB" to "South Indian Bank",
        "KVB" to "Karur Vysya Bank", "DCB" to "DCB Bank", "BANDHAN" to "Bandhan Bank", "UJJIVN" to "Ujjivan SFB",
        "AIRBNK" to "Airtel Payments Bank", "JIOPAY" to "Jio Payments Bank", "INDPOST" to "India Post Payments Bank",
    )

    private val bodyBanks: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\bhdfc\b""") to "HDFC Bank", Regex("""(?i)\bicici\b""") to "ICICI Bank",
        Regex("""(?i)\b(?:sbi|state bank)\b""") to "SBI", Regex("""(?i)\baxis\b""") to "Axis Bank",
        Regex("""(?i)\bkotak\b""") to "Kotak Bank", Regex("""(?i)\b(?:pnb|punjab national)\b""") to "PNB",
        Regex("""(?i)\byes bank\b""") to "Yes Bank", Regex("""(?i)\bidfc\b""") to "IDFC First",
        Regex("""(?i)\bindusind\b""") to "IndusInd Bank", Regex("""(?i)\bcanara\b""") to "Canara Bank",
        Regex("""(?i)\bunion bank\b""") to "Union Bank", Regex("""(?i)\bbank of baroda\b""") to "Bank of Baroda",
        Regex("""(?i)\bfederal bank\b""") to "Federal Bank", Regex("""(?i)\bidbi\b""") to "IDBI Bank",
        Regex("""(?i)\brbl\b""") to "RBL Bank", Regex("""(?i)\bau small finance\b""") to "AU Small Finance",
        Regex("""(?i)\bpaytm payments bank\b""") to "Paytm Payments Bank", Regex("""(?i)\bamerican express|amex\b""") to "American Express",
    )

    /** "VM-HDFCBK-S" -> "HDFCBK"; "AD-ICICIB" -> "ICICIB"; "+919876543210" stays digits. */
    fun code(sender: String): String {
        var s = sender.trim().uppercase()
        s = s.replace(Regex("^[A-Z]{2}-"), "")
        s = s.replace(Regex("-[A-Z]$"), "")
        return s.replace(Regex("[^A-Z0-9]"), "")
    }

    /** Bank SMS come from alphanumeric headers; a plain phone number is a person, never a bank alert. */
    fun isPhoneNumber(sender: String): Boolean = code(sender).let { it.isNotEmpty() && it.all(Char::isDigit) }

    fun bankFor(sender: String, body: String): String? {
        val c = code(sender)
        byPrefix.entries.filter { c.startsWith(it.key) }.maxByOrNull { it.key.length }?.let { return it.value }
        return bodyBanks.firstOrNull { it.first.containsMatchIn(body) }?.second
    }
}
