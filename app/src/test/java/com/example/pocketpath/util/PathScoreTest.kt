package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

// tests for the path progress score and the tips that explain it
// today is always 15 october 2026 so the month has 17 days left including today
class PathScoreTest {

    @Test
    fun `a brand new user scores nothing and is told where to start`() {
        val empty = BudgetSnapshot(emptyList(), emptyList(), minimumGoal = 0.0, maximumGoal = 0.0)

        val result = PathScore.calculate(empty, NOW)

        assertEquals(0, result.score)
        assertEquals(PathLevel.STARTING_OUT, result.level)
        assertTrue(result.tips.contains(ScoreTip.SetMonthlyGoal(25)))
        assertTrue(result.tips.contains(ScoreTip.AddCategoryLimits(hasCategories = false, pointsAvailable = 25)))
        assertTrue(result.tips.contains(ScoreTip.LogMoreDays(daysNeeded = 7, pointsAvailable = 25)))
        assertTrue(result.tips.contains(ScoreTip.StartLogging(25)))
    }

    @Test
    fun `doing everything well scores full marks`() {
        val result = PathScore.calculate(perfectMonth(), NOW)

        assertEquals(100, result.score)
        assertEquals(PathLevel.PATH_MASTER, result.level)
        assertTrue(result.parts.all { it.isFullMarks })
        assertTrue(result.tips.none { it.pointsAvailable > 0 })
    }

    @Test
    fun `spending between the goals earns the whole goal part`() {
        val part = goalPart(spent = 2500.0)

        assertEquals(25, part.points)
        assertEquals(PillarOutcome.GOAL_WITHIN, part.outcome)
    }

    @Test
    fun `spending under the minimum so far earns most of the goal part`() {
        val result = PathScore.calculate(goalSnapshot(spent = 400.0), NOW)
        val part = result.parts.first { it.pillar == ScorePillar.MONTHLY_GOAL }

        assertEquals(20, part.points)
        assertEquals(PillarOutcome.GOAL_BELOW_MINIMUM, part.outcome)
        assertTrue(result.tips.contains(ScoreTip.CheckMinimum(shortBy = 600.0, pointsAvailable = 5)))
    }

    @Test
    fun `going a little over the maximum keeps some points`() {
        val part = goalPart(spent = 5400.0)

        assertEquals(10, part.points)
        assertEquals(PillarOutcome.GOAL_SLIGHTLY_OVER, part.outcome)
    }

    @Test
    fun `going exactly ten percent over still counts as a little over`() {
        val part = goalPart(spent = 5500.0)

        assertEquals(PillarOutcome.GOAL_SLIGHTLY_OVER, part.outcome)
    }

    @Test
    fun `going well over the maximum loses the goal part`() {
        val result = PathScore.calculate(goalSnapshot(spent = 6000.0), NOW)
        val part = result.parts.first { it.pillar == ScorePillar.MONTHLY_GOAL }

        assertEquals(0, part.points)
        assertEquals(PillarOutcome.GOAL_WELL_OVER, part.outcome)
        assertTrue(result.tips.contains(ScoreTip.CutBackToMaximum(overBy = 1000.0, pointsAvailable = 25)))
    }

    @Test
    fun `last month's spending does not count against this month's goal`() {
        val snapshot = BudgetSnapshot(
            expenses = listOf(
                expense(amount = 9000.0, date = dayAt(Calendar.SEPTEMBER, 20)),
                expense(amount = 2000.0, date = dayAt(Calendar.OCTOBER, 10))
            ),
            categories = emptyList(),
            minimumGoal = 1000.0,
            maximumGoal = 5000.0
        )

        val part = PathScore.calculate(snapshot, NOW).parts.first { it.pillar == ScorePillar.MONTHLY_GOAL }

        assertEquals(PillarOutcome.GOAL_WITHIN, part.outcome)
    }

    @Test
    fun `the daily allowance shares what is left over the rest of the month`() {
        val result = PathScore.calculate(goalSnapshot(spent = 1600.0), NOW)

        val allowance = result.tips.filterIsInstance<ScoreTip.DailyAllowance>().single()

        assertEquals(3400.0, allowance.remaining, TOLERANCE)
        assertEquals(17, allowance.daysLeft)
        assertEquals(200.0, allowance.perDay, TOLERANCE)
    }

    @Test
    fun `each category over its limit loses its share of the points`() {
        val snapshot = BudgetSnapshot(
            expenses = listOf(
                expense(categoryId = "food", amount = 1200.0, date = dayAt(Calendar.OCTOBER, 5)),
                expense(categoryId = "taxi", amount = 100.0, date = dayAt(Calendar.OCTOBER, 6))
            ),
            categories = listOf(
                CategoryLimit("food", "Groceries", 1000.0),
                CategoryLimit("taxi", "Transport", 500.0)
            ),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val result = PathScore.calculate(snapshot, NOW)
        val part = result.parts.first { it.pillar == ScorePillar.CATEGORY_LIMITS }

        // one of two categories within the limit is 12.5 which rounds up
        assertEquals(13, part.points)
        assertEquals(PillarOutcome.SOME_OVER_LIMITS, part.outcome)
        assertEquals(1, part.achieved)
        assertEquals(2, part.target)
        assertEquals(listOf("Groceries"), part.overLimit.map { it.name })

        val tip = result.tips.filterIsInstance<ScoreTip.CategoryOverLimit>().single()
        assertEquals(200.0, tip.usage.overBy, TOLERANCE)
    }

    @Test
    fun `categories without a limit are not judged`() {
        val snapshot = BudgetSnapshot(
            expenses = listOf(expense(categoryId = "fun", amount = 900.0, date = dayAt(Calendar.OCTOBER, 5))),
            categories = listOf(
                CategoryLimit("fun", "Entertainment", 0.0),
                CategoryLimit("food", "Groceries", 1000.0)
            ),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val part = PathScore.calculate(snapshot, NOW).parts.first { it.pillar == ScorePillar.CATEGORY_LIMITS }

        assertEquals(25, part.points)
        assertEquals(1, part.target)
    }

    @Test
    fun `categories with limits but no spending yet wait for spending`() {
        val snapshot = BudgetSnapshot(
            expenses = emptyList(),
            categories = listOf(CategoryLimit("food", "Groceries", 1000.0)),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val part = PathScore.calculate(snapshot, NOW).parts.first { it.pillar == ScorePillar.CATEGORY_LIMITS }

        assertEquals(0, part.points)
        assertEquals(PillarOutcome.NO_SPENDING_YET, part.outcome)
    }

    @Test
    fun `a category nearly at its limit gets a warning`() {
        val snapshot = BudgetSnapshot(
            expenses = listOf(expense(categoryId = "food", amount = 900.0, date = dayAt(Calendar.OCTOBER, 5))),
            categories = listOf(CategoryLimit("food", "Groceries", 1000.0)),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val warning = PathScore.calculate(snapshot, NOW).tips.filterIsInstance<ScoreTip.CategoryNearLimit>().single()

        assertEquals("Groceries", warning.usage.name)
        assertEquals(100.0, warning.usage.remaining, TOLERANCE)
    }

    @Test
    fun `logging on some days earns part of the habit points`() {
        val snapshot = BudgetSnapshot(
            expenses = listOf(13, 14, 15).map { expense(date = dayAt(Calendar.OCTOBER, it)) },
            categories = emptyList(),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val result = PathScore.calculate(snapshot, NOW)
        val part = result.parts.first { it.pillar == ScorePillar.LOGGING_HABIT }

        // three of seven days is 10.7 points which rounds to 11
        assertEquals(11, part.points)
        assertEquals(PillarOutcome.LOGGED_SOME, part.outcome)
        assertTrue(result.tips.contains(ScoreTip.LogMoreDays(daysNeeded = 4, pointsAvailable = 14)))
    }

    @Test
    fun `logging more than seven days does not go past full marks`() {
        val snapshot = BudgetSnapshot(
            expenses = (2..15).map { expense(date = dayAt(Calendar.OCTOBER, it)) },
            categories = emptyList(),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val part = PathScore.calculate(snapshot, NOW).parts.first { it.pillar == ScorePillar.LOGGING_HABIT }

        assertEquals(25, part.points)
        assertEquals(14, part.achieved)
    }

    @Test
    fun `receipts on half of the month's expenses earn full marks`() {
        val part = receiptPart(total = 4, withReceipt = 2)

        assertEquals(25, part.points)
        assertEquals(PillarOutcome.RECEIPTS_ENOUGH, part.outcome)
    }

    @Test
    fun `fewer receipts earn part of the points`() {
        val part = receiptPart(total = 4, withReceipt = 1)

        assertEquals(13, part.points)
        assertEquals(PillarOutcome.RECEIPTS_SOME, part.outcome)
    }

    @Test
    fun `no receipts earn no receipt points`() {
        val part = receiptPart(total = 3, withReceipt = 0)

        assertEquals(0, part.points)
        assertEquals(PillarOutcome.NO_RECEIPTS, part.outcome)
    }

    @Test
    fun `receipts still needed allows for the new expenses adding to the total`() {
        // 1 of 4 needs 2 more because 3 of 6 is half
        assertEquals(2, PathScore.receiptsStillNeeded(totalExpenses = 4, withReceipt = 1))
        assertEquals(3, PathScore.receiptsStillNeeded(totalExpenses = 3, withReceipt = 0))
        assertEquals(0, PathScore.receiptsStillNeeded(totalExpenses = 4, withReceipt = 2))
    }

    @Test
    fun `the tips put the biggest wins first and stop at five`() {
        val snapshot = BudgetSnapshot(
            expenses = listOf(13, 14, 15).map { expense(date = dayAt(Calendar.OCTOBER, it)) },
            categories = emptyList(),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        val tips = PathScore.calculate(snapshot, NOW).tips
        val points = tips.map { it.pointsAvailable }

        assertTrue(tips.size <= PathScore.MAX_TIPS)
        assertEquals(points.sortedDescending(), points)
    }

    @Test
    fun `the score is sorted into levels`() {
        assertEquals(PathLevel.STARTING_OUT, PathLevel.forScore(39))
        assertEquals(PathLevel.FINDING_YOUR_PATH, PathLevel.forScore(40))
        assertEquals(PathLevel.ON_THE_RIGHT_PATH, PathLevel.forScore(70))
        assertEquals(PathLevel.PATH_MASTER, PathLevel.forScore(90))
        assertEquals(PathLevel.PATH_MASTER, PathLevel.forScore(100))
    }

    @Test
    fun `reports how far the next level is`() {
        val result = PathScore.calculate(goalSnapshot(spent = 2500.0), NOW)

        // 25 for the goal plus 4 for one day logged is 11 short of 40
        assertEquals(29, result.score)
        assertEquals(11, result.pointsToNextLevel)
        assertNull(PathLevel.PATH_MASTER.next)
    }

    // one month with every part of the score done well
    private fun perfectMonth(): BudgetSnapshot {
        val expenses = (9..15).map { day ->
            expense(
                categoryId = "food",
                amount = 300.0,
                date = dayAt(Calendar.OCTOBER, day),
                hasReceipt = day % 2 == 1
            )
        }

        return BudgetSnapshot(
            expenses = expenses,
            categories = listOf(CategoryLimit("food", "Groceries", 3000.0)),
            minimumGoal = 1000.0,
            maximumGoal = 5000.0
        )
    }

    // one expense of the given amount this month with goals of 1000 and 5000
    private fun goalSnapshot(spent: Double): BudgetSnapshot = BudgetSnapshot(
        expenses = listOf(expense(amount = spent, date = dayAt(Calendar.OCTOBER, 10))),
        categories = emptyList(),
        minimumGoal = 1000.0,
        maximumGoal = 5000.0
    )

    private fun goalPart(spent: Double): ScorePart =
        PathScore.calculate(goalSnapshot(spent), NOW).parts.first { it.pillar == ScorePillar.MONTHLY_GOAL }

    private fun receiptPart(total: Int, withReceipt: Int): ScorePart {
        val snapshot = BudgetSnapshot(
            expenses = (1..total).map { index ->
                expense(date = dayAt(Calendar.OCTOBER, index), hasReceipt = index <= withReceipt)
            },
            categories = emptyList(),
            minimumGoal = 0.0,
            maximumGoal = 0.0
        )

        return PathScore.calculate(snapshot, NOW).parts.first { it.pillar == ScorePillar.RECEIPTS }
    }

    private fun expense(
        categoryId: String? = null,
        amount: Double = 50.0,
        date: Long,
        hasReceipt: Boolean = false
    ) = TrackedExpense(categoryId, amount, date, hasReceipt)

    // expenses are stored at midnight on the day they happened
    private fun dayAt(month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, month, day)
        }.timeInMillis

    private companion object {
        const val TOLERANCE = 0.0001

        val NOW: Long = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 15, 12, 0)
        }.timeInMillis
    }
}
