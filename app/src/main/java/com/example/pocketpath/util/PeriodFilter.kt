package com.example.pocketpath.util

import java.util.Calendar

// holds the start and end of a chosen period
data class Period(
    val startMillis: Long,
    val endMillis: Long
) {
    // true when the given date falls inside this period
    fun contains(dateMillis: Long): Boolean =
        dateMillis in startMillis..endMillis
}

// helpers for working out the start and end of a period
object PeriodFilter {
    // midnight at the start of the given day
    fun startOfDay(timeMillis: Long): Long = calendarAt(timeMillis).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // last millisecond of the given day
    fun endOfDay(timeMillis: Long): Long = calendarAt(timeMillis).apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    // builds a period that covers both days in full
    fun periodBetween(firstDayMillis: Long, lastDayMillis: Long): Period =
        Period(startOfDay(firstDayMillis), endOfDay(lastDayMillis))

    // the whole month that the given time falls in
    fun currentMonth(nowMillis: Long = System.currentTimeMillis()): Period {
        val firstDay = calendarAt(nowMillis).apply {
            set(Calendar.DAY_OF_MONTH, getActualMinimum(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        val lastDay = calendarAt(nowMillis).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        return periodBetween(firstDay, lastDay)
    }

    // checks the end day is not before the start day
    fun isValidSelection(firstDayMillis: Long, lastDayMillis: Long): Boolean =
        startOfDay(firstDayMillis) <= startOfDay(lastDayMillis)

    // makes a calendar set to the given time
    private fun calendarAt(timeMillis: Long): Calendar =
        Calendar.getInstance().apply { this.timeInMillis = timeMillis }
}
