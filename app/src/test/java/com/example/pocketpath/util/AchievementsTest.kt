package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

// tests for the badges that unlock from the user's data
// today is always 15 october 2026
class AchievementsTest {

    @Test
    fun `a brand new user has every badge locked`() {
        val statuses = evaluate(snapshot())

        assertTrue(statuses.none { it.isUnlocked })
        assertEquals(Badge.entries.size, statuses.size)
    }

    @Test
    fun `logging the first expense unlocks first step`() {
        val status = statusOf(Badge.FIRST_STEP, snapshot(expenses = listOf(expense(day = 3))))

        assertTrue(status.isUnlocked)
    }

    @Test
    fun `creating a category unlocks organiser`() {
        val status = statusOf(
            Badge.ORGANISER,
            snapshot(categories = listOf(CategoryLimit("food", "Groceries", 0.0)))
        )

        assertTrue(status.isUnlocked)
    }

    @Test
    fun `setting a monthly goal unlocks goal setter`() {
        val status = statusOf(Badge.GOAL_SETTER, snapshot(maximumGoal = 5000.0))

        assertTrue(status.isUnlocked)
    }

    @Test
    fun `seven days in a row unlocks on a roll`() {
        val week = (1..7).map { expense(day = it) }

        assertTrue(statusOf(Badge.ON_A_ROLL, snapshot(expenses = week)).isUnlocked)
    }

    @Test
    fun `six days in a row shows progress but stays locked`() {
        val sixDays = (1..6).map { expense(day = it) }

        val status = statusOf(Badge.ON_A_ROLL, snapshot(expenses = sixDays))

        assertFalse(status.isUnlocked)
        assertEquals(6, status.progress)
    }

    @Test
    fun `ten receipt photos unlock receipt keeper`() {
        val expenses = (1..12).map { expense(day = it, hasReceipt = it <= 10) }

        assertTrue(statusOf(Badge.RECEIPT_KEEPER, snapshot(expenses = expenses)).isUnlocked)
    }

    @Test
    fun `progress never goes past the target`() {
        val expenses = (1..12).map { expense(day = it, hasReceipt = true) }

        assertEquals(10, statusOf(Badge.RECEIPT_KEEPER, snapshot(expenses = expenses)).progress)
    }

    @Test
    fun `a past month under the maximum unlocks month mastered`() {
        val snapshot = snapshot(
            expenses = listOf(expense(day = 10, month = Calendar.SEPTEMBER, amount = 4000.0)),
            maximumGoal = 5000.0
        )

        assertTrue(statusOf(Badge.MONTH_MASTERED, snapshot).isUnlocked)
    }

    @Test
    fun `a past month over the maximum does not unlock month mastered`() {
        val snapshot = snapshot(
            expenses = listOf(
                expense(day = 10, month = Calendar.SEPTEMBER, amount = 4000.0),
                expense(day = 20, month = Calendar.SEPTEMBER, amount = 2000.0)
            ),
            maximumGoal = 5000.0
        )

        assertFalse(statusOf(Badge.MONTH_MASTERED, snapshot).isUnlocked)
    }

    @Test
    fun `the month still going does not count for month mastered`() {
        val snapshot = snapshot(
            expenses = listOf(expense(day = 10, amount = 100.0)),
            maximumGoal = 5000.0
        )

        assertFalse(statusOf(Badge.MONTH_MASTERED, snapshot).isUnlocked)
    }

    @Test
    fun `month mastered needs a goal to be set`() {
        val snapshot = snapshot(expenses = listOf(expense(day = 10, month = Calendar.SEPTEMBER)))

        assertFalse(statusOf(Badge.MONTH_MASTERED, snapshot).isUnlocked)
    }

    @Test
    fun `a score of eighty unlocks path finder`() {
        assertFalse(statusOf(Badge.PATH_FINDER, snapshot(), pathScore = 79).isUnlocked)
        assertTrue(statusOf(Badge.PATH_FINDER, snapshot(), pathScore = 80).isUnlocked)
    }

    @Test
    fun `fifty expenses unlock dedicated tracker`() {
        val expenses = (1..50).map { expense(day = (it % 28) + 1) }

        assertTrue(statusOf(Badge.DEDICATED_TRACKER, snapshot(expenses = expenses)).isUnlocked)
    }

    @Test
    fun `a saved badge stays unlocked after its data is deleted`() {
        val saved = mapOf(Badge.FIRST_STEP.id to SAVED_AT)

        val status = statusOf(Badge.FIRST_STEP, snapshot(), saved = saved)

        assertTrue(status.isUnlocked)
        assertFalse(status.earnedNow)
        assertEquals(SAVED_AT, status.unlockedAtMillis)
    }

    @Test
    fun `only badges that are earned and not saved yet are new`() {
        val snapshot = snapshot(
            expenses = listOf(expense(day = 3)),
            maximumGoal = 5000.0
        )
        val saved = mapOf(Badge.FIRST_STEP.id to SAVED_AT)

        val newBadges = Achievements.newlyEarned(Achievements.evaluate(snapshot, 0, NOW, saved))

        assertEquals(listOf(Badge.GOAL_SETTER), newBadges)
    }

    @Test
    fun `every badge has its own id`() {
        val ids = Badge.entries.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        Badge.entries.forEach { assertEquals(it, Badge.fromId(it.id)) }
    }

    private fun evaluate(
        snapshot: BudgetSnapshot,
        pathScore: Int = 0,
        saved: Map<String, Long> = emptyMap()
    ): List<BadgeStatus> = Achievements.evaluate(snapshot, pathScore, NOW, saved)

    private fun statusOf(
        badge: Badge,
        snapshot: BudgetSnapshot,
        pathScore: Int = 0,
        saved: Map<String, Long> = emptyMap()
    ): BadgeStatus = evaluate(snapshot, pathScore, saved).first { it.badge == badge }

    private fun snapshot(
        expenses: List<TrackedExpense> = emptyList(),
        categories: List<CategoryLimit> = emptyList(),
        maximumGoal: Double = 0.0
    ) = BudgetSnapshot(
        expenses = expenses,
        categories = categories,
        minimumGoal = if (maximumGoal > 0.0) 1000.0 else 0.0,
        maximumGoal = maximumGoal
    )

    private fun expense(
        day: Int,
        month: Int = Calendar.OCTOBER,
        amount: Double = 50.0,
        hasReceipt: Boolean = false
    ): TrackedExpense {
        val date = Calendar.getInstance().apply {
            clear()
            set(2026, month, day)
        }.timeInMillis

        return TrackedExpense(null, amount, date, hasReceipt)
    }

    private companion object {
        const val SAVED_AT = 1_790_000_000_000L

        val NOW: Long = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 15, 12, 0)
        }.timeInMillis
    }
}
