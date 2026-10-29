package io.github.chiragjjjks.credu1ar.core.merchant

import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Category.*

/**
 * A known brand. [aliases] are lowercase words/phrases as they appear in bank SMS or UPI handles.
 * Matching rule (see [MerchantDirectory.match]): a phrase alias must appear as a whole phrase;
 * a single-word alias matches an equal word, or (if >= 5 letters) a word that starts with it,
 * so "zepto" matches "zeptonow" but "ola" never matches "olanzapine".
 */
data class Brand(val key: String, val name: String, val category: Category, val aliases: List<String>)

object MerchantDirectory {
    private fun b(name: String, category: Category, vararg aliases: String): Brand =
        Brand(key = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'), name = name, category = category, aliases = aliases.toList())

    val brands: List<Brand> = listOf(
        // ---- Quick commerce (groceries in minutes) ----
        b("Zepto", QUICK_COMMERCE, "zepto", "kiranakart"),
        b("Blinkit", QUICK_COMMERCE, "blinkit", "grofers", "blink commerce"),
        b("Swiggy Instamart", QUICK_COMMERCE, "instamart", "swiggy instamart"),
        b("BigBasket", QUICK_COMMERCE, "bigbasket", "bbnow", "bb daily", "innovative retail concepts"),
        b("JioMart", QUICK_COMMERCE, "jiomart"),
        b("Flipkart Minutes", QUICK_COMMERCE, "flipkart minutes"),
        b("Amazon Fresh", QUICK_COMMERCE, "amazon fresh", "amazon now"),

        // ---- Supermarkets ----
        b("DMart", SUPERMARKET, "dmart", "avenue supermarts"),
        b("More Retail", SUPERMARKET, "more retail", "more megastore", "more supermarket"),
        b("Reliance Smart", SUPERMARKET, "reliance smart", "reliance fresh", "reliance retail"),
        b("Spencer's", SUPERMARKET, "spencers", "spencer"),
        b("Star Bazaar", SUPERMARKET, "star bazaar", "star market", "trent hypermarket"),
        b("Nature's Basket", SUPERMARKET, "natures basket", "nature s basket"),
        b("Big Bazaar", SUPERMARKET, "big bazaar", "bigbazaar"),
        b("Spar", SUPERMARKET, "spar hypermarket", "spar"),
        b("Ratnadeep", SUPERMARKET, "ratnadeep"),

        // ---- Fresh & dairy ----
        b("Country Delight", FRESH_DAIRY, "country delight", "countrydelight"),
        b("Licious", FRESH_DAIRY, "licious"),
        b("FreshToHome", FRESH_DAIRY, "freshtohome", "fresh to home"),
        b("Milkbasket", FRESH_DAIRY, "milkbasket"),
        b("Nandini", FRESH_DAIRY, "nandini", "kmf"),
        b("Amul", FRESH_DAIRY, "amul"),
        b("Heritage Fresh", FRESH_DAIRY, "heritage fresh"),

        // ---- Food delivery ----
        b("Swiggy", FOOD_DELIVERY, "swiggy", "bundl technologies"),
        b("Zomato", FOOD_DELIVERY, "zomato"),
        b("EatSure", FOOD_DELIVERY, "eatsure", "rebel foods", "faasos", "behrouz", "oven story"),
        b("Magicpin", FOOD_DELIVERY, "magicpin"),
        b("Domino's", FOOD_DELIVERY, "dominos", "domino s", "jubilant foodworks"),

        // ---- Dining out & cafés ----
        b("McDonald's", DINING_OUT, "mcdonalds", "mcdonald s", "hardcastle restaurants"),
        b("KFC", DINING_OUT, "kfc", "devyani international"),
        b("Burger King", DINING_OUT, "burger king"),
        b("Pizza Hut", DINING_OUT, "pizza hut"),
        b("Subway", DINING_OUT, "subway"),
        b("Haldiram's", DINING_OUT, "haldiram", "haldirams"),
        b("Barbeque Nation", DINING_OUT, "barbeque nation"),
        b("Starbucks", CAFES, "starbucks", "tata starbucks"),
        b("Cafe Coffee Day", CAFES, "cafe coffee day", "coffee day"),
        b("Third Wave Coffee", CAFES, "third wave coffee", "thirdwave"),
        b("Blue Tokai", CAFES, "blue tokai"),
        b("Chaayos", CAFES, "chaayos"),
        b("Theobroma", CAFES, "theobroma"),

        // ---- Transport ----
        b("Uber", CAB_AUTO, "uber", "uberindia"),
        b("Ola", CAB_AUTO, "ola", "ani technologies", "olacabs"),
        b("Rapido", CAB_AUTO, "rapido", "roppen transportation"),
        b("Namma Yatri", CAB_AUTO, "namma yatri", "nammayatri"),
        b("BluSmart", CAB_AUTO, "blusmart"),
        b("inDrive", CAB_AUTO, "indrive"),
        b("Namma Metro", METRO_BUS, "bmrcl", "bangalore metro", "namma metro", "dmrc", "delhi metro", "chennai metro", "metro rail"),
        b("BMTC", METRO_BUS, "bmtc", "ksrtc"),
        b("IRCTC", TRAVEL, "irctc"),
        b("redBus", TRAVEL, "redbus", "abhibus"),
        b("IndiGo", TRAVEL, "indigo", "interglobe aviation"),
        b("Air India", TRAVEL, "air india", "airindia"),
        b("Akasa Air", TRAVEL, "akasa"),
        b("MakeMyTrip", TRAVEL, "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo"),
        b("OYO", TRAVEL, "oyo rooms", "oyo"),
        b("FASTag", PARKING_TOLL, "fastag", "nhai"),
        b("Indian Oil", FUEL, "indian oil", "iocl", "indianoil"),
        b("Bharat Petroleum", FUEL, "bharat petroleum", "bpcl"),
        b("HP Petrol", FUEL, "hindustan petroleum", "hpcl"),
        b("Shell", FUEL, "shell india", "shell petrol"),
        b("Nayara", FUEL, "nayara"),

        // ---- Home & Bills ----
        b("Airtel", MOBILE_INTERNET, "airtel", "bharti airtel"),
        b("Jio", MOBILE_INTERNET, "reliance jio", "jio prepaid", "jio postpaid", "jiofiber"),
        b("Vi", MOBILE_INTERNET, "vodafone idea", "vodafone"),
        b("BSNL", MOBILE_INTERNET, "bsnl"),
        b("ACT Fibernet", MOBILE_INTERNET, "act fibernet", "atria convergence"),
        b("Hathway", MOBILE_INTERNET, "hathway"),
        b("BESCOM", UTILITIES, "bescom"),
        b("BWSSB", UTILITIES, "bwssb"),
        b("Tata Power", UTILITIES, "tata power"),
        b("Adani Electricity", UTILITIES, "adani electricity"),
        b("MSEDCL", UTILITIES, "msedcl", "mahavitaran"),
        b("Mahanagar Gas", UTILITIES, "mahanagar gas", "indane", "bharatgas", "hp gas"),
        b("NoBroker", RENT, "nobroker", "no broker"),
        b("Bajaj Finance", EMI_LOANS, "bajaj finance", "bajaj finserv", "bajajfinance", "bajajfinserv"),
        b("KreditBee", EMI_LOANS, "kreditbee", "moneyview", "money view", "navi finserv"),
        b("LIC", INSURANCE, "lic of india", "life insurance corporation", "lic premium"),
        b("HDFC Life", INSURANCE, "hdfc life"),
        b("ICICI Prudential", INSURANCE, "icici prudential", "icici lombard"),
        b("Star Health", INSURANCE, "star health"),
        b("Digit Insurance", INSURANCE, "go digit", "digit insurance"),
        b("Acko", INSURANCE, "acko"),
        b("PolicyBazaar", INSURANCE, "policybazaar"),

        // ---- Shopping ----
        b("Amazon", ONLINE_SHOPPING, "amazon", "amazon pay", "amazon seller", "amzn"),
        b("Flipkart", ONLINE_SHOPPING, "flipkart"),
        b("Meesho", ONLINE_SHOPPING, "meesho"),
        b("Myntra", FASHION, "myntra"),
        b("Ajio", FASHION, "ajio"),
        b("Nykaa", PERSONAL_CARE, "nykaa"),
        b("Tata CLiQ", ONLINE_SHOPPING, "tata cliq", "tatacliq"),
        b("Zudio", FASHION, "zudio", "trent limited"),
        b("Westside", FASHION, "westside"),
        b("Lifestyle", FASHION, "lifestyle international"),
        b("Pantaloons", FASHION, "pantaloons"),
        b("Max Fashion", FASHION, "max fashion", "max retail"),
        b("H&M", FASHION, "h&m", "h m hennes"),
        b("Decathlon", FASHION, "decathlon"),
        b("Croma", ELECTRONICS, "croma", "infiniti retail"),
        b("Reliance Digital", ELECTRONICS, "reliance digital"),
        b("Apple", ELECTRONICS, "apple store", "apple india", "apple services"),
        b("Lenskart", PERSONAL_CARE, "lenskart"),
        b("FirstCry", ONLINE_SHOPPING, "firstcry"),
        b("IKEA", ONLINE_SHOPPING, "ikea"),
        b("Pepperfry", ONLINE_SHOPPING, "pepperfry"),

        // ---- Health ----
        b("Apollo Pharmacy", PHARMACY, "apollo pharmacy", "apollo medicals"),
        b("PharmEasy", PHARMACY, "pharmeasy", "api holdings"),
        b("Tata 1mg", PHARMACY, "1mg", "tata 1mg"),
        b("Netmeds", PHARMACY, "netmeds"),
        b("MedPlus", PHARMACY, "medplus"),
        b("Practo", DOCTOR, "practo"),
        b("Apollo Hospitals", DOCTOR, "apollo hospitals", "apollo clinic"),
        b("Thyrocare", DOCTOR, "thyrocare", "dr lal", "redcliffe"),
        b("Cult.fit", FITNESS, "cult fit", "cure fit", "cultfit"),

        // ---- Fun & Subscriptions ----
        b("Netflix", STREAMING, "netflix"),
        b("Prime Video", STREAMING, "prime video", "amazon prime"),
        b("Disney+ Hotstar", STREAMING, "hotstar", "disney"),
        b("JioCinema", STREAMING, "jiocinema", "jiohotstar"),
        b("SonyLIV", STREAMING, "sonyliv", "sony liv"),
        b("ZEE5", STREAMING, "zee5"),
        b("YouTube", STREAMING, "youtube", "youtubepremium"),
        b("Spotify", STREAMING, "spotify"),
        b("Apple Music", STREAMING, "apple music", "itunes"),
        b("Gaana", STREAMING, "gaana"),
        b("BookMyShow", EVENTS, "bookmyshow", "big tree entertainment"),
        b("PVR INOX", EVENTS, "pvr", "inox"),
        b("Steam", GAMES, "steam purchase", "steamgames", "valve corporation"),
        b("Dream11", GAMES, "dream11", "dream sports"),
        b("MPL", GAMES, "mobile premier league"),
        b("Google Play", SUBSCRIPTIONS, "google play", "googleplay", "google one", "google storage"),
        b("OpenAI", SUBSCRIPTIONS, "openai", "chatgpt"),
        b("Anthropic", SUBSCRIPTIONS, "anthropic", "claude ai"),
        b("Notion", SUBSCRIPTIONS, "notion labs"),
        b("LinkedIn", SUBSCRIPTIONS, "linkedin"),
        b("Audible", SUBSCRIPTIONS, "audible"),
        b("GitHub", SUBSCRIPTIONS, "github"),
        b("Microsoft", SUBSCRIPTIONS, "microsoft", "msft"),

        // ---- Education ----
        b("BYJU'S", EDUCATION, "byjus", "byju s", "think and learn"),
        b("Unacademy", EDUCATION, "unacademy"),
        b("Coursera", EDUCATION, "coursera"),
        b("Udemy", EDUCATION, "udemy"),

        // ---- Money moves (not spending) ----
        b("CRED", CARD_BILL, "cred club", "cred", "dreamplug"),
        b("Zerodha", INVESTMENTS, "zerodha", "zerodha broking"),
        b("Groww", INVESTMENTS, "groww", "nextbillion technology"),
        b("Upstox", INVESTMENTS, "upstox", "rksv securities"),
        b("Kuvera", INVESTMENTS, "kuvera"),
        b("Coin by Zerodha", INVESTMENTS, "zerodha coin"),
        b("INDmoney", INVESTMENTS, "indmoney", "ind money"),
        b("NPS", INVESTMENTS, "national pension", "nps trust"),
    )

    private val noiseTokens = setOf(
        "payu", "razorpay", "razorpayx", "billdesk", "paytm", "upi", "pay", "cashfree", "juspay", "rzp", "ccavenue",
        "pg", "online", "store", "stores", "merchant", "qr", "bharatpe", "ybl", "okaxis", "oksbi", "okhdfcbank",
        "okicici", "axl", "ibl", "apl", "icici", "axisbank", "sbi", "hdfcbank", "kotak", "yesbank", "paytmqr",
        "gpay", "phonepe", "bank", "com", "in", "app", "pvt", "ltd", "limited", "private", "india", "marketplace",
        "technologies", "services", "retail", "enterprises", "solutions",
    )

    /** Lowercase words, apostrophes folded away ("domino's" -> "dominos"), digit-only words dropped. */
    private fun words(text: String?): List<String> =
        text.orEmpty().lowercase().replace("'", "")
            .replace(Regex("[^a-z0-9&]+"), " ")
            .split(' ')
            .filter { it.isNotBlank() && !it.all(Char::isDigit) }

    /** Words without payment-rail noise ("payu", "upi", "ybl", "pvt", "ltd"...): what a person would call the merchant. */
    fun tokens(text: String?): List<String> = words(text).filter { it !in noiseTokens }

    fun match(text: String?, vpaLocal: String? = null): Brand? {
        val all = words(text) + words(vpaLocal)
        if (all.isEmpty()) return null
        val clean = all.filter { it !in noiseTokens }
        // Phrase aliases see every word (so "shell india" can match); single-word aliases see only clean words.
        val padded = " ${all.joinToString(" ")} "
        val words = clean
        var best: Pair<Brand, Int>? = null
        for (brand in brands) {
            for (alias in brand.aliases) {
                val glued = alias.replace(" ", "")
                val hit = if (alias.contains(' ')) {
                    // A phrase also matches when the handle runs it together: "apollopharmacy", "swiggyinstamart".
                    padded.contains(" $alias ") || (glued.length >= 7 && words.any { w -> w == glued || w.startsWith(glued) })
                } else words.any { w -> w == alias || (alias.length >= 5 && w.startsWith(alias)) }
                if (hit) {
                    val score = alias.length
                    if (best == null || score > best.second) best = brand to score
                }
            }
        }
        return best?.first
    }
}
