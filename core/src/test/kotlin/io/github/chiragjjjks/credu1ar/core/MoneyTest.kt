package io.github.chiragjjjks.credu1ar.core

import io.github.chiragjjjks.credu1ar.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test fun indianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,000", Money.format(100_000))
        assertEquals("₹1,23,456.78", Money.format(12_345_678))
        assertEquals("₹1,23,45,678", Money.format(1_234_567_800))
        assertEquals("-₹450.50", Money.format(-45_050))
    }
}
