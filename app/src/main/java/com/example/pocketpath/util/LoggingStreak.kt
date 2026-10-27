package com.example.pocketpath.util

import java.util.TimeZone

// works out which days the user logged spending on so habits and streaks
// can be counted
object LoggingStreak {

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    // turns a time into a whole day number in the phone's own time zone
    // the time zone offset is added first so a day always starts at local
    // midnight and a daylight saving change never splits or merges two days
    fun dayNumber(timeMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        (timeMillis + timeZone.getOffset(timeMillis)).floorDiv(MILLIS_PER_DAY)

    // the different days that have at least one expense up to and including
    // today, expenses dated in the future are left out as they have not
    // happened yet
    fun loggedDays(
        dates: List<Long>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Set<Long> {
        val today = dayNumber(nowMillis, timeZone)

        return dates
            .map { dayNumber(it, timeZone) }
            .filter { it <= today }
            .toSet()
    }

    // the longest run of days in a row that each have an expense
    fun longestStreak(days: Set<Long>): Int {
        var longest = 0
        var current = 0
        var previous: Long? = null

        for (day in days.sorted()) {
            current = if (previous != null && day == previous + 1) current + 1 else 1
            longest = maxOf(longest, current)
            previous = day
        }

        return longest
    }

    // the run of days in a row that reaches today
    // a run that ended yesterday still counts so the streak does not reset
    // first thing in the morning before anything has been bought
    fun currentStreak(days: Set<Long>, today: Long): Int {
        var day = when {
            days.contains(today) -> today
            days.contains(today - 1) -> today - 1
            else -> return 0
        }

        var streak = 0
        while (days.contains(day)) {
            streak++
            day--
        }

        return streak
    }

    // how many of the last few days including today have an expense
    fun activeDaysInLast(days: Set<Long>, today: Long, window: Int): Int {
        val firstDay = today - window.coerceAtLeast(1) + 1
        return days.count { it in firstDay..today }
    }
}
