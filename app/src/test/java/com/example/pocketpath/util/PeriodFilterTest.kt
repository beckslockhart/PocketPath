package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PeriodFilterTest {
    @Test
    fun `start of day strips the time from a date`() {
        val afternoon = dateMillis(2026, 9, 14, hour = 15, minute = 42)

        val result = PeriodFilter.startOfDay(afternoon)

        assertEquals(dateMillis(2026, 9, 14), result)
    }

    @Test
    fun `end of day is the final millisecond of that day`() {
        val morning = dateMillis(2026, 9, 14, hour = 8, minute = 5)

        val result = PeriodFilter.endOfDay(morning)

        val nextMidnight = dateMillis(2026, 9, 15)
        assertEquals(nextMidnight - 1, result)
    }

    @Test
    fun `period includes an expense recorded on the closing day`() {
        val expenseOnLastDay = dateMillis(2026, 9, 30)

        val period = PeriodFilter.periodBetween(
            firstDayMillis = dateMillis(2026, 9, 1),
            lastDayMillis = dateMillis(2026, 9, 30)
        )

        assertTrue(period.contains(expenseOnLastDay))
    }

    @Test
    fun `period includes an expense recorded on the opening day`() {
        val expenseOnFirstDay = dateMillis(2026, 9, 1)

        val period = PeriodFilter.periodBetween(
            firstDayMillis = dateMillis(2026, 9, 1),
            lastDayMillis = dateMillis(2026, 9, 30)
        )

        assertTrue(period.contains(expenseOnFirstDay))
    }

    @Test
    fun `period excludes an expense from the day after it closes`() {
        val expenseNextDay = dateMillis(2026, 10, 1)

        val period = PeriodFilter.periodBetween(
            firstDayMillis = dateMillis(2026, 9, 1),
            lastDayMillis = dateMillis(2026, 9, 30)
        )

        assertFalse(period.contains(expenseNextDay))
    }

    @Test
    fun `period excludes an expense from the day before it opens`() {
        val expenseDayBefore = dateMillis(2026, 8, 31)

        val period = PeriodFilter.periodBetween(
            firstDayMillis = dateMillis(2026, 9, 1),
            lastDayMillis = dateMillis(2026, 9, 30)
        )

        assertFalse(period.contains(expenseDayBefore))
    }

    @Test
    fun `a single day period contains only that day`() {
        val theDay = dateMillis(2026, 9, 14)

        val period = PeriodFilter.periodBetween(theDay, theDay)

        assertTrue(period.contains(theDay))
        assertTrue(period.contains(dateMillis(2026, 9, 14, hour = 23, minute = 59)))
        assertFalse(period.contains(dateMillis(2026, 9, 15)))
    }

    @Test
    fun `current month runs from the first day to the last day`() {
        val midSeptember = dateMillis(2026, 9, 14, hour = 11)

        val period = PeriodFilter.currentMonth(midSeptember)

        assertEquals(dateMillis(2026, 9, 1), period.startMillis)
        assertTrue(period.contains(dateMillis(2026, 9, 30, hour = 23, minute = 59)))
        assertFalse(period.contains(dateMillis(2026, 10, 1)))
    }

    @Test
    fun `current month handles a leap year February`() {
        val midFebruary = dateMillis(2028, 2, 10)

        val period = PeriodFilter.currentMonth(midFebruary)

        assertTrue(period.contains(dateMillis(2028, 2, 29)))
        assertFalse(period.contains(dateMillis(2028, 3, 1)))
    }

    @Test
    fun `a period may span more than one month`() {
        val period = PeriodFilter.periodBetween(
            firstDayMillis = dateMillis(2026, 8, 15),
            lastDayMillis = dateMillis(2026, 10, 15)
        )

        assertTrue(period.contains(dateMillis(2026, 9, 20)))
        assertFalse(period.contains(dateMillis(2026, 8, 14)))
    }

    @Test
    fun `an ordered selection is valid`() {
        assertTrue(
            PeriodFilter.isValidSelection(
                dateMillis(2026, 9, 1),
                dateMillis(2026, 9, 30)
            )
        )
    }

    @Test
    fun `the same day at both ends is a valid selection`() {
        assertTrue(
            PeriodFilter.isValidSelection(
                dateMillis(2026, 9, 14, hour = 9),
                dateMillis(2026, 9, 14, hour = 17)
            )
        )
    }

    @Test
    fun `a selection ending before it starts is rejected`() {
        assertFalse(
            PeriodFilter.isValidSelection(
                dateMillis(2026, 9, 30),
                dateMillis(2026, 9, 1)
            )
        )
    }

    private fun dateMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute)
    }.timeInMillis
}
