package io.github.chiragjjjks.credu1ar.core.statement

import io.github.chiragjjjks.credu1ar.core.model.Direction
import java.time.LocalDate
import kotlin.test.fail

/** Shared helpers for statement tests. Every name, number and amount is fictional. */
object StatementKit {
    const val SEP = "kotak-asha-2026-09-16_2026-10-16.txt"
    const val OCT = "kotak-asha-2026-10-16_2026-11-16.txt"

    fun pages(name: String): List<String> =
        (StatementKit::class.java.getResource("/statements/$name") ?: fail("missing fixture $name")).readText().split("\n\u000C\n")

    fun fixture(name: String): Statement = when (val r = Statements.parse(pages(name))) {
        is StatementParse.Parsed -> r.statement
        else -> fail("not parsed: $r")
    }

    /** A made-up one-account Kotak statement with the given rows (balances are not checked here). */
    fun statement(holder: String?, vararg rows: StatementRow): Statement = Statement(
        header = StatementHeader("Kotak", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 10, 16), "7890", holder),
        openingPaise = 0, closingPaise = null, rows = rows.toList(), excluded = emptyList(), issues = emptyList(),
    )

    private var nextIndex = 1

    fun row(narration: String, paise: Long, direction: Direction = Direction.DEBIT, date: LocalDate = LocalDate.of(2026, 9, 21)): StatementRow =
        StatementRow(nextIndex++, 1, date, narration, null, paise, direction, 1_000_000L + nextIndex)
}
