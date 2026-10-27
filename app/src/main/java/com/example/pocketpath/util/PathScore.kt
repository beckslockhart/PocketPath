package com.example.pocketpath.util

import kotlin.math.roundToInt

// the four parts the path score is built from, each worth the same points
enum class ScorePillar {
    MONTHLY_GOAL,
    CATEGORY_LIMITS,
    LOGGING_HABIT,
    RECEIPTS
}

// why a part of the score earned the points it did
enum class PillarOutcome {
    GOAL_NOT_SET,
    GOAL_WITHIN,
    GOAL_BELOW_MINIMUM,
    GOAL_SLIGHTLY_OVER,
    GOAL_WELL_OVER,
    NO_LIMITS_SET,
    NO_SPENDING_YET,
    ALL_WITHIN_LIMITS,
    SOME_OVER_LIMITS,
    LOGGED_ENOUGH,
    LOGGED_SOME,
    NOT_LOGGED,
    RECEIPTS_ENOUGH,
    RECEIPTS_SOME,
    NO_RECEIPTS
}

// the levels the score is grouped into so it reads as progress along a path
enum class PathLevel(val minimumScore: Int) {
    STARTING_OUT(0),
    FINDING_YOUR_PATH(40),
    ON_THE_RIGHT_PATH(70),
    PATH_MASTER(90);

    // the level above this one or null when already at the top
    val next: PathLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forScore(score: Int): PathLevel =
            entries.last { score.coerceAtLeast(0) >= it.minimumScore }
    }
}

// how much of its monthly limit one category has used this month
data class CategoryUsage(
    val name: String,
    val spent: Double,
    val limit: Double
) {
    val overBy: Double get() = spent - limit
    val remaining: Double get() = limit - spent
    val fractionUsed: Double get() = if (limit <= 0.0) 0.0 else spent / limit
}

// one part of the score and the numbers that explain it
data class ScorePart(
    val pillar: ScorePillar,
    val points: Int,
    val outcome: PillarOutcome,
    // how much of the target was reached such as 5 of 7 days logged
    val achieved: Int = 0,
    val target: Int = 0,
    // only filled in for the monthly goal part
    val goal: GoalProgress? = null,
    // only filled in for the category limits part, biggest overspend first
    val overLimit: List<CategoryUsage> = emptyList()
) {
    val maxPoints: Int get() = PathScore.POINTS_PER_PILLAR
    val isFullMarks: Boolean get() = points >= maxPoints
    val pointsMissed: Int get() = maxPoints - points
}

// a specific thing the user can do to raise or protect their score
sealed class ScoreTip {
    // how many points following the tip is worth, zero for general advice
    abstract val pointsAvailable: Int

    data class SetMonthlyGoal(override val pointsAvailable: Int) : ScoreTip()

    data class CutBackToMaximum(
        val overBy: Double,
        override val pointsAvailable: Int
    ) : ScoreTip()

    data class CheckMinimum(
        val shortBy: Double,
        override val pointsAvailable: Int
    ) : ScoreTip()

    data class AddCategoryLimits(
        val hasCategories: Boolean,
        override val pointsAvailable: Int
    ) : ScoreTip()

    data class CategoryOverLimit(
        val usage: CategoryUsage,
        override val pointsAvailable: Int
    ) : ScoreTip()

    data class StartLogging(override val pointsAvailable: Int) : ScoreTip()

    data class LogMoreDays(
        val daysNeeded: Int,
        override val pointsAvailable: Int
    ) : ScoreTip()

    data class AddReceipts(
        val receiptsNeeded: Int,
        override val pointsAvailable: Int
    ) : ScoreTip()

    data class CategoryNearLimit(val usage: CategoryUsage) : ScoreTip() {
        override val pointsAvailable: Int get() = 0
    }

    data class DailyAllowance(
        val remaining: Double,
        val daysLeft: Int,
        val perDay: Double
    ) : ScoreTip() {
        override val pointsAvailable: Int get() = 0
    }
}

// the finished score with everything needed to explain it
data class PathScoreResult(
    val score: Int,
    val level: PathLevel,
    val parts: List<ScorePart>,
    val tips: List<ScoreTip>,
    val currentStreak: Int,
    val longestStreak: Int
) {
    // how far away the next level is or null at the top level
    val pointsToNextLevel: Int? get() = level.next?.let { it.minimumScore - score }
}

// works out the path score out of 100 for the current month
//
// the score is four parts worth 25 points each
// monthly goal     25 between the goals, 20 under the minimum so far,
//                  10 when no more than 10% over the maximum, otherwise 0
// category limits  25 times the share of limited categories still within
//                  their limit this month
// logging habit    25 times the days with an expense in the last 14 days,
//                  full marks at 7 days
// receipt photos   25 times the share of this month's expenses that have a
//                  photo, full marks once half of them do
object PathScore {

    const val POINTS_PER_PILLAR = 25
    const val MAX_SCORE = POINTS_PER_PILLAR * 4

    const val BELOW_MINIMUM_POINTS = 20
    const val SLIGHTLY_OVER_POINTS = 10

    // going over the maximum by no more than this share still earns some points
    const val SLIGHTLY_OVER_SHARE = 0.10

    const val LOGGING_WINDOW_DAYS = 14
    const val LOGGING_TARGET_DAYS = 7

    // a category this far through its limit gets a warning tip
    const val NEAR_LIMIT_SHARE = 0.8

    // the share of expenses that need a receipt is kept as a fraction of
    // whole numbers so the count still needed works out exactly
    private const val RECEIPT_SHARE_TOP = 1
    private const val RECEIPT_SHARE_BOTTOM = 2

    const val MAX_TIPS = 5
    private const val MAX_CATEGORY_TIPS = 2

    fun calculate(snapshot: BudgetSnapshot, nowMillis: Long): PathScoreResult {
        val month = PeriodFilter.currentMonth(nowMillis)
        val thisMonth = snapshot.expenses.filter { month.contains(it.dateMillis) }

        val today = LoggingStreak.dayNumber(nowMillis)
        val loggedDays = LoggingStreak.loggedDays(snapshot.expenses.map { it.dateMillis }, nowMillis)
        val daysLeftInMonth = (LoggingStreak.dayNumber(month.endMillis) - today + 1).toInt()

        val pointTips = mutableListOf<ScoreTip>()
        val adviceTips = mutableListOf<ScoreTip>()

        val goalPart = goalPart(snapshot, thisMonth, daysLeftInMonth, pointTips, adviceTips)
        val categoryPart = categoryPart(snapshot, thisMonth, pointTips, adviceTips)
        val loggingPart = loggingPart(loggedDays, today, pointTips)
        val receiptPart = receiptPart(thisMonth, pointTips)

        val parts = listOf(goalPart, categoryPart, loggingPart, receiptPart)

        // one tip covers every part that cannot score until something is logged
        if (thisMonth.isEmpty()) {
            val waitingOnSpending = parts
                .filter { it.outcome == PillarOutcome.NO_SPENDING_YET }
                .sumOf { it.pointsMissed }

            if (waitingOnSpending > 0) pointTips.add(ScoreTip.StartLogging(waitingOnSpending))
        }

        val score = parts.sumOf { it.points }.coerceIn(0, MAX_SCORE)

        // the biggest wins come first and general advice goes last
        val tips = (pointTips.sortedByDescending { it.pointsAvailable } + adviceTips)
            .take(MAX_TIPS)

        return PathScoreResult(
            score = score,
            level = PathLevel.forScore(score),
            parts = parts,
            tips = tips,
            currentStreak = LoggingStreak.currentStreak(loggedDays, today),
            longestStreak = LoggingStreak.longestStreak(loggedDays)
        )
    }

    // reuses the goal summary rules so this screen and the history screen
    // always agree on whether the month is between the goals
    private fun goalPart(
        snapshot: BudgetSnapshot,
        thisMonth: List<TrackedExpense>,
        daysLeftInMonth: Int,
        pointTips: MutableList<ScoreTip>,
        adviceTips: MutableList<ScoreTip>
    ): ScorePart {
        val spent = thisMonth.sumOf { it.amount }
        val progress = GoalStatus.evaluate(spent, snapshot.minimumGoal, snapshot.maximumGoal)

        val (points, outcome) = when (progress.band) {
            GoalBand.NOT_SET -> 0 to PillarOutcome.GOAL_NOT_SET
            GoalBand.WITHIN_GOALS -> POINTS_PER_PILLAR to PillarOutcome.GOAL_WITHIN
            GoalBand.BELOW_MINIMUM -> BELOW_MINIMUM_POINTS to PillarOutcome.GOAL_BELOW_MINIMUM
            GoalBand.ABOVE_MAXIMUM -> {
                val overShare = (spent - progress.maximum) / progress.maximum
                if (overShare <= SLIGHTLY_OVER_SHARE) {
                    SLIGHTLY_OVER_POINTS to PillarOutcome.GOAL_SLIGHTLY_OVER
                } else {
                    0 to PillarOutcome.GOAL_WELL_OVER
                }
            }
        }

        val part = ScorePart(ScorePillar.MONTHLY_GOAL, points, outcome, goal = progress)

        when (progress.band) {
            GoalBand.NOT_SET -> pointTips.add(ScoreTip.SetMonthlyGoal(part.pointsMissed))
            GoalBand.ABOVE_MAXIMUM -> pointTips.add(
                ScoreTip.CutBackToMaximum(-progress.remainingToMaximum, part.pointsMissed)
            )
            GoalBand.BELOW_MINIMUM -> pointTips.add(
                ScoreTip.CheckMinimum(progress.remainingToMinimum, part.pointsMissed)
            )
            GoalBand.WITHIN_GOALS -> Unit
        }

        // a daily budget helps the user stay under the maximum for the rest of the month
        val remaining = progress.remainingToMaximum
        if (progress.band != GoalBand.NOT_SET && remaining > 0.0 && daysLeftInMonth > 0) {
            adviceTips.add(ScoreTip.DailyAllowance(remaining, daysLeftInMonth, remaining / daysLeftInMonth))
        }

        return part
    }

    // only categories with a limit can be judged, and expenses with no
    // category or a deleted category are left out of this part
    private fun categoryPart(
        snapshot: BudgetSnapshot,
        thisMonth: List<TrackedExpense>,
        pointTips: MutableList<ScoreTip>,
        adviceTips: MutableList<ScoreTip>
    ): ScorePart {
        val limited = snapshot.categories.filter { it.monthlyLimit > 0.0 }

        if (limited.isEmpty()) {
            pointTips.add(ScoreTip.AddCategoryLimits(snapshot.categories.isNotEmpty(), POINTS_PER_PILLAR))
            return ScorePart(ScorePillar.CATEGORY_LIMITS, 0, PillarOutcome.NO_LIMITS_SET)
        }

        // nothing spent yet would be a free 25 points so it waits for spending
        if (thisMonth.isEmpty()) {
            return ScorePart(
                ScorePillar.CATEGORY_LIMITS, 0, PillarOutcome.NO_SPENDING_YET,
                target = limited.size
            )
        }

        val spentById = thisMonth
            .filter { it.categoryId != null }
            .groupBy { it.categoryId }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

        val usages = limited.map { CategoryUsage(it.name, spentById[it.categoryId] ?: 0.0, it.monthlyLimit) }

        val over = usages
            .filter { it.spent > it.limit }
            .sortedByDescending { it.overBy }

        val within = usages.size - over.size
        val points = (POINTS_PER_PILLAR * within.toDouble() / usages.size).roundToInt()

        val pointsPerCategory = (POINTS_PER_PILLAR.toDouble() / usages.size).roundToInt()
        over.take(MAX_CATEGORY_TIPS).forEach { usage ->
            pointTips.add(ScoreTip.CategoryOverLimit(usage, pointsPerCategory))
        }

        // warn about the category closest to its limit before it goes over
        usages
            .filter { it.spent > 0.0 && it.spent <= it.limit && it.fractionUsed >= NEAR_LIMIT_SHARE }
            .maxByOrNull { it.fractionUsed }
            ?.let { adviceTips.add(0, ScoreTip.CategoryNearLimit(it)) }

        return ScorePart(
            pillar = ScorePillar.CATEGORY_LIMITS,
            points = points,
            outcome = if (over.isEmpty()) PillarOutcome.ALL_WITHIN_LIMITS else PillarOutcome.SOME_OVER_LIMITS,
            achieved = within,
            target = usages.size,
            overLimit = over
        )
    }

    // rewards logging little and often rather than all at once
    private fun loggingPart(
        loggedDays: Set<Long>,
        today: Long,
        pointTips: MutableList<ScoreTip>
    ): ScorePart {
        val activeDays = LoggingStreak.activeDaysInLast(loggedDays, today, LOGGING_WINDOW_DAYS)
        val counted = activeDays.coerceAtMost(LOGGING_TARGET_DAYS)
        val points = (POINTS_PER_PILLAR * counted.toDouble() / LOGGING_TARGET_DAYS).roundToInt()

        val outcome = when {
            activeDays >= LOGGING_TARGET_DAYS -> PillarOutcome.LOGGED_ENOUGH
            activeDays > 0 -> PillarOutcome.LOGGED_SOME
            else -> PillarOutcome.NOT_LOGGED
        }

        val part = ScorePart(
            ScorePillar.LOGGING_HABIT, points, outcome,
            achieved = activeDays,
            target = LOGGING_TARGET_DAYS
        )

        if (activeDays < LOGGING_TARGET_DAYS) {
            pointTips.add(ScoreTip.LogMoreDays(LOGGING_TARGET_DAYS - activeDays, part.pointsMissed))
        }

        return part
    }

    // receipts back up the spending so a share of them earns full marks
    private fun receiptPart(
        thisMonth: List<TrackedExpense>,
        pointTips: MutableList<ScoreTip>
    ): ScorePart {
        if (thisMonth.isEmpty()) {
            return ScorePart(ScorePillar.RECEIPTS, 0, PillarOutcome.NO_SPENDING_YET)
        }

        val total = thisMonth.size
        val withReceipt = thisMonth.count { it.hasReceipt }
        val neededNow = receiptsForTarget(total)

        val share = (withReceipt.toDouble() / neededNow).coerceAtMost(1.0)
        val points = (POINTS_PER_PILLAR * share).roundToInt()

        val outcome = when {
            withReceipt >= neededNow -> PillarOutcome.RECEIPTS_ENOUGH
            withReceipt > 0 -> PillarOutcome.RECEIPTS_SOME
            else -> PillarOutcome.NO_RECEIPTS
        }

        val part = ScorePart(
            ScorePillar.RECEIPTS, points, outcome,
            achieved = withReceipt,
            target = total
        )

        if (outcome != PillarOutcome.RECEIPTS_ENOUGH) {
            pointTips.add(ScoreTip.AddReceipts(receiptsStillNeeded(total, withReceipt), part.pointsMissed))
        }

        return part
    }

    // how many receipts out of this many expenses reach the target share
    fun receiptsForTarget(totalExpenses: Int): Int =
        ceilDiv(totalExpenses * RECEIPT_SHARE_TOP, RECEIPT_SHARE_BOTTOM)

    // how many new expenses need a receipt to reach the target share
    // existing expenses cannot be edited so every new expense also adds to
    // the total, which is why this is more than the simple difference
    fun receiptsStillNeeded(totalExpenses: Int, withReceipt: Int): Int {
        val shortfall = totalExpenses * RECEIPT_SHARE_TOP - withReceipt * RECEIPT_SHARE_BOTTOM
        if (shortfall <= 0) return 0
        return ceilDiv(shortfall, RECEIPT_SHARE_BOTTOM - RECEIPT_SHARE_TOP)
    }

    private fun ceilDiv(top: Int, bottom: Int): Int = (top + bottom - 1) / bottom
}
