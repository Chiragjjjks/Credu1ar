package io.github.chiragjjjks.credu1ar.core.model

enum class Direction { DEBIT, CREDIT }

enum class Instrument { UPI, CARD, ACCOUNT, ATM, TRANSFER, NETBANKING, WALLET, UNKNOWN }

/** Where a signal came from. Stored so the pipeline can prefer SMS (full body) over notifications. */
enum class Source(val id: String) {
    SMS("sms"), NOTIFICATION("notif"), SAMPLE("sample");

    companion object { fun byId(id: String): Source = entries.firstOrNull { it.id == id } ?: SMS }
}

/** Who got paid, as far as the message tells us. */
enum class PayeeKind { MERCHANT, PERSON, UNKNOWN }

/**
 * One raw message or notification before any interpretation.
 * [sender] is the SMS header (e.g. "VM-HDFCBK-S") or the notification title/app label.
 */
data class RawSignal(
    val source: Source,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val packageName: String? = null,
)

/** What the parser pulled out of a financial message. Pure data, no category yet. */
data class ParsedTxn(
    val direction: Direction,
    val amountPaise: Long,
    val payeeName: String?,
    val vpa: String?,
    val payeeKind: PayeeKind,
    val accountTail: String?,
    val instrument: Instrument,
    val bank: String?,
    val reference: String?,
    val occurredAtMillis: Long,
    val balancePaise: Long?,
    /** Short lowercase hints the categoriser may use, e.g. "salary", "refund", "credit card", "emi". */
    val hints: Set<String>,
    val confidence: Float,
)

sealed interface ParseResult {
    data class Parsed(val txn: ParsedTxn) : ParseResult

    /** Not a completed payment (OTP, promo, balance, failed, upcoming). Never stored, never logged. */
    data object NotFinancial : ParseResult

    /** Looks financial but we could not read it. Kept (redacted by the user) so we can add a rule. */
    data class Unparsed(val reason: String) : ParseResult
}

/** A stored transaction as the UI and insights see it. */
data class Txn(
    val id: Long,
    val occurredAtMillis: Long,
    val direction: Direction,
    val amountPaise: Long,
    val merchantKey: String,
    val merchantName: String,
    val category: Category,
    val confidence: Float,
    val instrument: Instrument,
    val bank: String?,
    val accountTail: String?,
    val needsReview: Boolean,
) {
    /** Counts toward "spent": a debit in a spending category. */
    val isSpend: Boolean get() = direction == Direction.DEBIT && category.group.isSpend

    /** Counts toward "money in": a credit in the income group. Card-bill and self-transfer credits do not. */
    val isIncome: Boolean get() = direction == Direction.CREDIT && category.group == Group.INCOME
}
