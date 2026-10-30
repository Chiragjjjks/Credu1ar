package io.github.chiragjjjks.credu1ar.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryWindowTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 10, min: Int = 0, s: Int = 0, nanos: Int = 0) =
        LocalDateTime.of(y, m, d, h, min, s, nanos).atZone(ist).toInstant().toEpochMilli()
    private fun day(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(ist).toLocalDate()

    /** (now) -> (first day read, previous month included, month Pulse opens on) */
    private fun check(now: Long, start: LocalDate, previous: Boolean, open: YearMonth) {
        val w = HistoryWindow.firstLink(now)
        assertEquals(start, day(w.startMillis), "start for ${day(now)}")
        assertEquals(start.atStartOfDay(ist).toInstant().toEpochMilli(), w.startMillis, "starts at IST midnight")
        assertEquals(previous, w.includesPreviousMonth, "previous month for ${day(now)}")
        assertEquals(open, w.openOn, "Pulse month for ${day(now)}")
        assertTrue(w.startMillis <= now)
    }

    @Test fun ownersExamples() {
        check(at(2026, 11, 1), LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 10)) // Nov 1: all of October
        check(at(2026, 11, 15), LocalDate.of(2026, 11, 1), false, YearMonth.of(2026, 11)) // Nov 15: only Nov 1-15
    }

    @Test fun firstWeekIncludesThePreviousMonth() {
        check(at(2026, 11, 1, 0, 0), LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 10))
        check(at(2026, 11, 1, 0, 5), LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 10))
        check(at(2026, 11, 3, 23, 59, 59), LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 10))
        check(at(2026, 11, 4, 0, 0), LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 11)) // has 3 days of its own now
        check(at(2026, 11, 7, 23, 59, 59, 999_000_000), LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 11))
    }

    @Test fun fromTheEighthOnlyThisMonth() {
        check(at(2026, 11, 8, 0, 0), LocalDate.of(2026, 11, 1), false, YearMonth.of(2026, 11))
        check(at(2026, 10, 31, 23, 59, 59), LocalDate.of(2026, 10, 1), false, YearMonth.of(2026, 10))
        check(at(2028, 2, 29, 12), LocalDate.of(2028, 2, 1), false, YearMonth.of(2028, 2))
    }

    @Test fun monthAndYearEdges() {
        check(at(2027, 1, 3), LocalDate.of(2026, 12, 1), true, YearMonth.of(2026, 12)) // year rollover
        check(at(2027, 3, 1), LocalDate.of(2027, 2, 1), true, YearMonth.of(2027, 2)) // 28-day February
        check(at(2028, 3, 5), LocalDate.of(2028, 2, 1), true, YearMonth.of(2028, 3)) // leap February
        check(at(2026, 12, 1), LocalDate.of(2026, 11, 1), true, YearMonth.of(2026, 11)) // 30-day month before
        check(at(2026, 8, 1), LocalDate.of(2026, 7, 1), true, YearMonth.of(2026, 7)) // 31-day month before
    }

    @Test fun alwaysIstWhateverThePhoneZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            // 18:30 UTC on Oct 31 is already 00:00 on Nov 1 in India.
            val now = ZonedDateTime.of(2026, 10, 31, 18, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
            check(now, LocalDate.of(2026, 10, 1), true, YearMonth.of(2026, 10))
            val later = ZonedDateTime.of(2026, 11, 7, 18, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli() // Nov 8 IST
            check(later, LocalDate.of(2026, 11, 1), false, YearMonth.of(2026, 11))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test fun eachEarlierTapAddsOneWholeMonth() {
        assertEquals(LocalDate.of(2026, 9, 1), day(HistoryWindow.monthEarlier(at(2026, 10, 1, 0, 0))))
        assertEquals(LocalDate.of(2026, 10, 1), day(HistoryWindow.monthEarlier(at(2026, 10, 13)))) // finish a half-read month first
        assertEquals(LocalDate.of(2026, 12, 1), day(HistoryWindow.monthEarlier(at(2027, 1, 1, 0, 0))))
        assertEquals(LocalDate.of(2028, 2, 1), day(HistoryWindow.monthEarlier(at(2028, 3, 1, 0, 0))))
        val start = HistoryWindow.monthEarlier(at(2026, 10, 1, 0, 0))
        assertEquals(start, LocalDate.of(2026, 9, 1).atStartOfDay(ist).toInstant().toEpochMilli())
    }
}
