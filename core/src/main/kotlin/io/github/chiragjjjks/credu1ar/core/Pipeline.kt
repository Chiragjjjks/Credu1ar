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
            is ParseResult.Parsed -> {
                var merchant = MerchantNormalizer.resolve(r.txn.payeeName, r.txn.vpa, r.txn.payeeKind)
                val categorizer = Categorizer(rules)
                val cat = categorizer.categorize(merchant, r.txn)
                // No payee in the message ("Payment received on your credit card", "ATM withdrawal"): name it by what it is.
                if (merchant.key == "unknown" && cat.category != Category.UNCATEGORISED) {
                    merchant = merchant.copy(key = cat.category.id, name = cat.category.label)
                }
                Outcome.Txn(Accepted(r.txn, merchant, cat, categorizer.needsReview(cat, r.txn.direction)))
            }
        }

    /** Re-categorise an already stored payee after the owner teaches a rule. */
    fun recategorize(merchant: Merchant, parsed: ParsedTxn, rules: Map<String, Category>): Categorization =
        Categorizer(rules).categorize(merchant, parsed)
}
