package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

// tests for counting the days the user logged spending on
class LoggingStreakTest {

    @Test
    fun `days in a row make a streak`() {
        val days = loggedDays(dayAt(10), dayAt(11), dayAt(12))

        assertEquals(3, LoggingStreak.longestStreak(days))
    }

    @Test
    fun `a missed day breaks the streak`() {
        val days = loggedDays(dayAt(1), dayAt(2), dayAt(4), dayAt(5), dayAt(6))

        assertEquals(3, LoggingStreak.longestStreak(days))
    }

    @Test
    fun `several expenses on one day count as one day`() {
        val days = loggedDays(dayAt(3), dayAt(3, hour = 14), dayAt(3, hour = 20))

        assertEquals(1, days.size)
        assertEquals(1, LoggingStreak.longestStreak(days))
    }

    @Test
    fun `no expenses means no streak`() {
        assertEquals(0, LoggingStreak.longestStreak(emptySet()))
    }

    @Test
    fun `the current streak counts back from today`() {
        val days = loggedDays(dayAt(13), dayAt(14), dayAt(15))

        assertEquals(3, LoggingStreak.currentStreak(days, today()))
    }

    @Test
    fun `a streak that ended yesterday still counts this morning`() {
        val days = loggedDays(dayAt(12), dayAt(13), dayAt(14))

        assertEquals(3, LoggingStreak.currentStreak(days, today()))
    }

    @Test
    fun `a streak that ended two days ago has been lost`() {
        val days = loggedDays(dayAt(11), dayAt(12), dayAt(13))

        assertEquals(0, LoggingStreak.currentStreak(days, today()))
    }

    @Test
    fun `expenses dated in the future are not counted yet`() {
        val days = loggedDays(dayAt(15), dayAt(16), dayAt(20))

        assertEquals(1, days.size)
    }

    @Test
    fun `active days only looks at the chosen window`() {
        // the 1st is fifteen days before the 15th so it falls outside 14 days
        val days = loggedDays(dayAt(1), dayAt(2), dayAt(10), dayAt(15))

        assertEquals(3, LoggingStreak.activeDaysInLast(days, today(), 14))
    }

    @Test
    fun `a daylight saving change does not break a streak`() {
        val london = TimeZone.getTimeZone("Europe/London")

        // the clocks went forward in the uk on 29 march 2026
        val dates = listOf(28, 29, 30).map { day ->
            Calendar.getInstance(london).apply {
                clear()
                set(2026, Calendar.MARCH, day)
            }.timeInMillis
        }
        val now = dates.last()

        val days = LoggingStreak.loggedDays(dates, now, london)

        assertEquals(3, LoggingStreak.longestStreak(days))
    }

    // the expenses are dated in october 2026 and today is the 15th
    private fun loggedDays(vararg dates: Long): Set<Long> =
        LoggingStreak.loggedDays(dates.toList(), NOW)

    private fun today(): Long = LoggingStreak.dayNumber(NOW)

    private fun dayAt(day: Int, hour: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, 0)
        }.timeInMillis

    private companion object {
        val NOW: Long = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 15, 9, 30)
        }.timeInMillis
    }
}
