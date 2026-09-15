package com.example.pocketpath.util

import java.util.Calendar

data class Period(
    val startMillis: Long,
    val endMillis: Long
) {
    fun contains(dateMillis: Long): Boolean =
        dateMillis in startMillis..endMillis
}

object PeriodFilter {
    fun startOfDay(timeMillis: Long): Long = calendarAt(timeMillis).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(timeMillis: Long): Long = calendarAt(timeMillis).apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    fun periodBetween(firstDayMillis: Long, lastDayMillis: Long): Period =
        Period(startOfDay(firstDayMillis), endOfDay(lastDayMillis))

    fun currentMonth(nowMillis: Long = System.currentTimeMillis()): Period {
        val firstDay = calendarAt(nowMillis).apply {
            set(Calendar.DAY_OF_MONTH, getActualMinimum(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        val lastDay = calendarAt(nowMillis).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }.timeInMillis

        return periodBetween(firstDay, lastDay)
    }

    fun isValidSelection(firstDayMillis: Long, lastDayMillis: Long): Boolean =
        startOfDay(firstDayMillis) <= startOfDay(lastDayMillis)

    private fun calendarAt(timeMillis: Long): Calendar =
        Calendar.getInstance().apply { this.timeInMillis = timeMillis }
}
