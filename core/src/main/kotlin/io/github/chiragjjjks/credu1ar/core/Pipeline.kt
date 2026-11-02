package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.category.Categorization
import io.github.chiragjjjks.credu1ar.core.category.Categorizer
import io.github.chiragjjjks.credu1ar.core.merchant.Merchant
import io.github.chiragjjjks.credu1ar.core.merchant.MerchantNormalizer
import io.github.chiragjjjks.credu1ar.core.model.Category
import io.github.chiragjjjks.credu1ar.core.model.ParseResult
import io.github.chiragjjjks.credu1ar.core.model.ParsedTxn
import io.github.chiragjjjks.credu1ar.core.model.RawSignal
import io.github.chiragjjjks.credu1ar.core.parse.SmsParser

/** A signal that became a transaction. */
data class Accepted(
    val parsed: ParsedTxn,
    val merchant: Merchant,
    val categorization: Categorization,
    val needsReview: Boolean,
)

sealed interface Outcome {
    data class Txn(val accepted: Accepted) : Outcome

    /** Not a payment. The caller must drop it: not stored, not logged. */
    data object Ignored : Outcome

    /** Looked like a payment but could not be read. */
    data class Unreadable(val reason: String) : Outcome
}

/** The whole "understand" half of the app as one pure function: signal in, decision out. */
object Pipeline {
    fun process(signal: RawSignal, rules: Map<String, Category> = emptyMap()): Outcome =
        when (val r = SmsParser.parse(signal)) {
            ParseResult.NotFinancial -> Outcome.Ignored
            is ParseResult.Unparsed -> Outcome.Unreadable(r.reason)
            is ParseResult.Parsed -> Outcome.Txn(accept(r.txn, rules))
        }

    /** A parsed payment from any source (SMS, notification, statement row) → merchant + category. */
    fun accept(parsed: ParsedTxn, rules: Map<String, Category> = emptyMap()): Accepted {
        var merchant = MerchantNormalizer.resolve(parsed.payeeName, parsed.vpa, parsed.payeeKind, parsed.payeeTruncated)
        val categorizer = Categorizer(rules)
        var cat = categorizer.categorize(merchant, parsed)
        // No payee in the message ("Payment received on your credit card", "ATM withdrawal"): name it by what it is,
        // and let a rule the owner taught for that name apply.
        if (merchant.key == "unknown" && cat.category != Category.UNCATEGORISED) {
            val (key, name) = unnamed(cat.category, parsed.hints)
            merchant = merchant.copy(key = key, name = name)
            categorizer.rule(key, parsed.direction)?.let { cat = it }
        }
        return Accepted(parsed, merchant, cat, categorizer.needsReview(cat, parsed.direction))
    }

    /**
     * Key and name for a payment whose message names no payee. Usually the category ("card_bill", "Credit-card bill");
     * statement rows get plainer words. "Own account" never carries the holder's name.
     */
    private fun unnamed(c: Category, hints: Set<String>): Pair<String, String> = when {
        c == Category.BANK_CHARGES -> "bank_charges" to "Bank charges"
        c == Category.REFUND && "cashback" in hints -> "cashback" to "Cashback"
        c == Category.SELF_TRANSFER && "own_name" in hints -> "own_account" to "Own account"
        else -> c.id to c.label
    }

    /** Re-categorise an already stored payee after the owner teaches a rule. */
    fun recategorize(merchant: Merchant, parsed: ParsedTxn, rules: Map<String, Category>): Categorization =
        Categorizer(rules).categorize(merchant, parsed)
}
