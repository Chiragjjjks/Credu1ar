package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.Category.*
import io.github.chiragjjjks.credu1ar.core.model.Direction
import io.github.chiragjjjks.credu1ar.core.model.Direction.CREDIT
import io.github.chiragjjjks.credu1ar.core.model.Direction.DEBIT
import io.github.chiragjjjks.credu1ar.core.model.Instrument
import io.github.chiragjjjks.credu1ar.core.model.Source

/**
 * Synthetic messages modelled on the *styles* public bank alerts use. Real-world coverage grows by adding a case
 * here first (see .claude/skills/add-bank-parser). Never paste a real SMS: change names, numbers and references.
 *
 * `expect == null` means the message must be ignored (not a completed payment).
 */
data class Fixture(
    val name: String,
    val sender: String,
    val body: String,
    val direction: Direction? = null,
    val paise: Long? = null,
    val merchant: String? = null,       // resolved display name
    val category: Category? = null,
    val tail: String? = null,
    val instrument: Instrument? = null,
    val reference: String? = null,
    val review: Boolean? = null,
    val source: Source = Source.SMS,
    val unreadable: Boolean = false,    // Unparsed(...) instead of Ignored
) {
    val ignored: Boolean get() = direction == null && !unreadable
}

object ParserFixtures {
    val all: List<Fixture> = listOf(
        // ------------------------------------------------------------------ HDFC
        Fixture(
            "hdfc upi sent (new format)", "VM-HDFCBK-S",
            "Sent Rs.450.00\nFrom HDFC Bank A/c *1234\nTo ZEPTO MARKETPLACE\nOn 29/10/26\nRef 630112345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
            DEBIT, 45_000, "Zepto", QUICK_COMMERCE, "1234", Instrument.UPI, "630112345678", review = false,
        ),
        Fixture(
            "hdfc upi debit (vpa format)", "AD-HDFCBK-S",
            "Rs.450.00 debited from a/c **1234 on 29-10-26 to VPA zepto@axisbank(UPI Ref No 630112345678). Not you? Call 18002586161",
            DEBIT, 45_000, "Zepto", QUICK_COMMERCE, "1234", Instrument.UPI, "630112345678",
        ),
        Fixture(
            "hdfc card spend with iso timestamp", "VM-HDFCBK-S",
            "Spent Rs.1250 On HDFC Bank Card 4321 At SWIGGY On 2026-10-29:14:22:10.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 4321 to 7308080808",
            DEBIT, 125_000, "Swiggy", FOOD_DELIVERY, "4321", Instrument.CARD,
        ),
        Fixture(
            "hdfc salary credit with balance", "VM-HDFCBK-S",
            "UPDATE: INR 60,000.00 credited to HDFC Bank A/c XX1234 on 29-10-26 from NEFT-ACME TECHNOLOGIES SALARY. Avl bal INR 1,20,000.00",
            CREDIT, 6_000_000, category = SALARY, tail = "1234",
        ),
        Fixture(
            "hdfc store name -> provision, needs review", "VM-HDFCBK-S",
            "Sent Rs.85.00\nFrom HDFC Bank A/c *1234\nTo SRI LAKSHMI STORES\nOn 29/10/26\nRef 630112345679",
            DEBIT, 8_500, "Sri Lakshmi Stores", PROVISION, "1234", review = true,
        ),
        // ------------------------------------------------------------------ ICICI
        Fixture(
            "icici debit with trailing 'credited' merchant", "AD-ICICIB-S",
            "ICICI Bank Acct XX234 debited for Rs 489.00 on 29-Oct-26; SWIGGY credited. UPI:630112345678. Call 18002662 for dispute. SMS BLOCK 234 to 9215676766.",
            DEBIT, 48_900, "Swiggy", FOOD_DELIVERY, "234", Instrument.UPI, "630112345678",
        ),
        Fixture(
            "icici card spend", "VM-ICICIT-S",
            "INR 2,300.00 spent using ICICI Bank Card XX1234 on 29-Oct-26 on AMAZON PAY. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766.",
            DEBIT, 230_000, "Amazon", ONLINE_SHOPPING, "1234", Instrument.CARD,
        ),
        // ------------------------------------------------------------------ SBI
        Fixture(
            "sbi upi debit without currency symbol", "VK-SBIUPI-S",
            "Dear UPI user A/C X1234 debited by 312.0 on date 29Oct26 trf to ZEPTO Refno 630112345678. If not u? call 1800111109. -SBI",
            DEBIT, 31_200, "Zepto", QUICK_COMMERCE, "1234", Instrument.UPI, "630112345678",
        ),
        Fixture(
            "sbi imps credit from unnamed sender", "VK-SBIINB-S",
            "Your a/c no. XXXXXXXX1234 is credited by Rs.5000.00 on 29-10-26 by a/c linked to mobile no 98XXXXX210 (IMPS Ref no 630112345678)-SBI",
            CREDIT, 500_000, category = MONEY_IN, tail = "1234", reference = "630112345678",
        ),
        Fixture(
            "sbi credit card spend", "VK-SBICRD-S",
            "Rs.899.00 spent on your SBI Credit Card ending 4321 at NETFLIX on 29-10-26. Trxn. not done by you? Report at https://sbicard.com/fraud",
            DEBIT, 89_900, "Netflix", STREAMING, "4321", Instrument.CARD,
        ),
        // ------------------------------------------------------------------ Axis / Kotak / PNB / IDFC
        Fixture(
            "axis multi-line with P2M merchant", "AX-AXISBK-S",
            "Debit INR 1,499.00\nAxis Bank A/c XX1234\n29-10-26 14:22:10\nUPI/P2M/630112345678/BLINKIT\nNot you? SMS BLOCK UPI to 7308080808",
            DEBIT, 149_900, "Blinkit", QUICK_COMMERCE, "1234", Instrument.UPI, "630112345678",
        ),
        Fixture(
            "axis P2A payment to a person", "AX-AXISBK-S",
            "INR 620.00 debited from A/c no. XX1234 on 29-10-26 14:22:10 (UPI/P2A/630112345678/RAJU KUMAR). Avl Bal INR 5,000.00",
            DEBIT, 62_000, "Raju Kumar", SENT_TO_PEOPLE, "1234", Instrument.UPI, review = true,
        ),
        Fixture(
            "kotak upi to kirana handle", "VM-KOTAKB-S",
            "Sent Rs.250.00 from Kotak Bank AC X1234 to rajukirana@okaxis on 29-10-26.UPI Ref 630112345678. Not you, https://kotak.com/KBANKT/Fraud",
            DEBIT, 25_000, category = PROVISION, tail = "1234", instrument = Instrument.UPI, reference = "630112345678", review = false,
        ),
        Fixture(
            "kotak received from friend", "VM-KOTAKB-S",
            "Received Rs.1,000.00 in your Kotak Bank AC X1234 from friend@ybl on 29-10-26.UPI Ref:630112345678.",
            CREDIT, 100_000, category = MONEY_IN, tail = "1234", reference = "630112345678",
        ),
        Fixture(
            "pnb trf to zomato", "VM-PNBSMS",
            "A/c XX1234 debited INR 500.00 Dt 29-10-26 12:30 trf to ZOMATO Refno 630112345678 -PNB",
            DEBIT, 50_000, "Zomato", FOOD_DELIVERY, "1234",
        ),
        Fixture(
            "idfc pos purchase at cafe", "VM-IDFCFB-S",
            "Your A/C XXXXXX1234 is debited by INR 150.00 on 29-10-2026 for UPI-POS PURCHASE at CHAAYOS. Bal: INR 5,000.00",
            DEBIT, 15_000, "Chaayos", CAFES, "1234",
        ),
        // ------------------------------------------------------------------ ATM, card bill, EMI, refund, wallet
        Fixture(
            "atm withdrawal with balance", "VM-SBIINB-S",
            "Rs.2000.00 withdrawn from ATM on 29-10-26 using Card XX1234. Avl Bal Rs 8,000.00",
            DEBIT, 200_000, category = ATM_CASH, tail = "1234", instrument = Instrument.ATM,
        ),
        Fixture(
            "credit card bill payment received is not income", "VM-HDFCBK-S",
            "Payment of Rs.12,500.00 received on your HDFC Bank Credit Card ending 4321 on 29-10-26. Thank you.",
            CREDIT, 1_250_000, category = CARD_BILL, tail = "4321",
        ),
        Fixture(
            "emi debit names the lender in brackets", "VM-HDFCBK-S",
            "Rs 7,299.00 debited from A/c XX1234 on 05-10-26 towards EMI for loan 123456 (BAJAJ FINANCE)",
            DEBIT, 729_900, "Bajaj Finance", EMI_LOANS, "1234",
        ),
        Fixture(
            "refund from merchant", "VM-HDFCBK-S",
            "Refund of Rs 312.00 for your order from ZEPTO has been credited to A/c XX1234",
            CREDIT, 31_200, category = REFUND, tail = "1234",
        ),
        Fixture(
            "wallet payment", "PAYTMB",
            "Paid Rs.120 to Chaayos using Paytm Wallet. Balance Rs 50",
            DEBIT, 12_000, "Chaayos", CAFES, instrument = Instrument.WALLET,
        ),
        // ------------------------------------------------------------------ UPI-app notifications
        Fixture("gpay notification paid", "Google Pay", "Paid ₹312 to Zepto", DEBIT, 31_200, "Zepto", QUICK_COMMERCE, instrument = Instrument.UPI, source = Source.NOTIFICATION),
        Fixture("phonepe notification paid", "PhonePe", "Paid ₹489 to SWIGGY", DEBIT, 48_900, "Swiggy", FOOD_DELIVERY, source = Source.NOTIFICATION),
        Fixture("phonepe notification received", "PhonePe", "Received ₹1,000 from Rajesh Kumar", CREDIT, 100_000, category = MONEY_IN, source = Source.NOTIFICATION),
        Fixture("notification: someone paid you", "Google Pay", "Rajesh Kumar paid you ₹500", CREDIT, 50_000, category = MONEY_IN, source = Source.NOTIFICATION),
        // ------------------------------------------------------------------ must be ignored
        Fixture("upcoming mandate is not a payment", "VM-HDFCBK-S", "Mandate Alert: Rs.199.00 will be debited from A/c XX1234 on 01-11-26 for NETFLIX against mandate."),
        Fixture("otp that mentions an amount", "VM-AMAZON", "123456 is your OTP for txn of Rs 450.00 at AMAZON. Valid for 10 mins. Do not share with anyone."),
        Fixture("otp with 'payment of' wording", "VM-HDFCBK-S", "OTP is 123456 for payment of INR 450.00 on HDFC Bank Card ending 4321 at AMAZON. Valid for 10 min."),
        Fixture("loan promo", "VM-HDFCBK-P", "Get pre-approved loan up to Rs 5,00,000! Apply now http://x.co/abc"),
        Fixture("failed upi", "VM-HDFCBK-S", "Your UPI txn of Rs 450.00 to ZEPTO has failed. Amount not debited."),
        Fixture("collect request", "VM-HDFCBK-S", "Rajesh has requested Rs 500.00 from you on UPI. Pay now to complete."),
        Fixture("balance enquiry", "VM-HDFCBK-S", "Avl Bal in A/c XX1234 is Rs 12,345.00 as on 29-Oct-26."),
        Fixture("card statement due", "VM-HDFCBK-S", "Total due Rs 12,500.00 Minimum due Rs 625.00 payable by 05-11-26 on your HDFC Credit Card ending 4321."),
        Fixture("friend texting from a phone number", "+919876543210", "I paid Rs 500 to the shop, debited from my a/c XX1234 yesterday"),
        Fixture("plain chat", "VM-SWIGGY", "Your order is on the way! Track it in the app."),
        // ------------------------------------------------------------------ readable-looking but unsupported
        Fixture("foreign currency card spend", "VM-ICICIT-S", "USD 12.00 spent on ICICI Bank Card XX1234 at OPENAI on 29-Oct-26", unreadable = true),
    )
}
