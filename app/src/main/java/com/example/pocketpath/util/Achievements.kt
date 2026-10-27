package com.example.pocketpath.util

import java.util.Calendar

// every badge the user can earn and how much of something it needs
// the id is what gets saved in firestore so it must never change
enum class Badge(val id: String, val target: Int) {
    FIRST_STEP("first_expense", 1),
    ORGANISER("first_category", 1),
    GOAL_SETTER("monthly_goal_set", 1),
    ON_A_ROLL("seven_day_streak", 7),
    RECEIPT_KEEPER("ten_receipts", 10),
    MONTH_MASTERED("month_under_maximum", 1),
    PATH_FINDER("score_80", 80),
    DEDICATED_TRACKER("fifty_expenses", 50);

    companion object {
        fun fromId(id: String): Badge? = entries.firstOrNull { it.id == id }
    }
}

// where the user stands with one badge
data class BadgeStatus(
    val badge: Badge,
    // how far along the user is, never more than the target
    val progress: Int,
    // true when the user's data meets the badge right now
    val earnedNow: Boolean,
    // when the unlock was saved to firestore, null if it has not been saved
    val unlockedAtMillis: Long?
) {
    // once saved a badge stays unlocked even if the data that earned it is
    // later deleted, so the user never loses a badge
    val isUnlocked: Boolean get() = earnedNow || unlockedAtMillis != null
}

// decides which badges the user has earned from their firebase data
object Achievements {

    // works out every badge in the order they are shown
    // savedUnlocks maps a badge id to the time it was first unlocked
    fun evaluate(
        snapshot: BudgetSnapshot,
        pathScore: Int,
        nowMillis: Long,
        savedUnlocks: Map<String, Long>
    ): List<BadgeStatus> {
        val loggedDays = LoggingStreak.loggedDays(snapshot.expenses.map { it.dateMillis }, nowMillis)
        val hasGoal = snapshot.maximumGoal > 0.0

        return Badge.entries.map { badge ->
            val rawProgress = when (badge) {
                Badge.FIRST_STEP -> snapshot.expenses.size
                Badge.ORGANISER -> snapshot.categories.size
                Badge.GOAL_SETTER -> if (hasGoal) 1 else 0
                Badge.ON_A_ROLL -> LoggingStreak.longestStreak(loggedDays)
                Badge.RECEIPT_KEEPER -> snapshot.expenses.count { it.hasReceipt }
                Badge.MONTH_MASTERED -> if (hasFinishedMonthUnderMaximum(snapshot, nowMillis)) 1 else 0
                Badge.PATH_FINDER -> pathScore
                Badge.DEDICATED_TRACKER -> snapshot.expenses.size
            }

            val savedAt = savedUnlocks[badge.id]
            val earnedNow = rawProgress >= badge.target

            BadgeStatus(
                badge = badge,
                progress = if (savedAt != null) badge.target else rawProgress.coerceIn(0, badge.target),
                earnedNow = earnedNow,
                unlockedAtMillis = savedAt
            )
        }
    }

    // badges the data has earned that have not been saved yet
    fun newlyEarned(statuses: List<BadgeStatus>): List<Badge> =
        statuses
            .filter { it.earnedNow && it.unlockedAtMillis == null }
            .map { it.badge }

    // true when a whole calendar month before this one had spending that
    // stayed at or under the maximum goal
    // only months with at least one expense count so an empty month is not
    // a free badge, and the goal used is the one set now because firestore
    // only keeps the latest goal
    fun hasFinishedMonthUnderMaximum(snapshot: BudgetSnapshot, nowMillis: Long): Boolean {
        if (snapshot.maximumGoal <= 0.0) return false

        val currentMonth = monthKey(nowMillis)

        return snapshot.expenses
            .groupBy { monthKey(it.dateMillis) }
            .filterKeys { it < currentMonth }
            .any { (_, expenses) -> expenses.sumOf { it.amount } <= snapshot.maximumGoal }
    }

    // a number that is the same for every day in one calendar month and
    // goes up by one each month so months can be compared
    private fun monthKey(timeMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
    }
}
