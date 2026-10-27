package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Test

// tests for the summary that says whether the month stayed between the goals
class GoalStatusTest {

    @Test
    fun `spending under the minimum reads as below`() {
        val progress = GoalStatus.evaluate(spent = 400.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(GoalBand.BELOW_MINIMUM, progress.band)
    }

    @Test
    fun `spending between the goals reads as on track`() {
        val progress = GoalStatus.evaluate(spent = 2500.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(GoalBand.WITHIN_GOALS, progress.band)
    }

    @Test
    fun `spending over the maximum reads as above`() {
        val progress = GoalStatus.evaluate(spent = 6200.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(GoalBand.ABOVE_MAXIMUM, progress.band)
    }

    @Test
    fun `landing exactly on the minimum counts as on track`() {
        val progress = GoalStatus.evaluate(spent = 1000.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(GoalBand.WITHIN_GOALS, progress.band)
    }

    @Test
    fun `landing exactly on the maximum counts as on track`() {
        val progress = GoalStatus.evaluate(spent = 5000.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(GoalBand.WITHIN_GOALS, progress.band)
    }

    @Test
    fun `no goals set is reported separately rather than as below`() {
        val progress = GoalStatus.evaluate(spent = 400.0, minimum = 0.0, maximum = 0.0)

        assertEquals(GoalBand.NOT_SET, progress.band)
    }

    @Test
    fun `spending nothing with goals set reads as below`() {
        val progress = GoalStatus.evaluate(spent = 0.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(GoalBand.BELOW_MINIMUM, progress.band)
    }

    @Test
    fun `a minimum above the maximum is pulled down instead of breaking`() {
        val progress = GoalStatus.evaluate(spent = 4000.0, minimum = 9000.0, maximum = 5000.0)

        assertEquals(5000.0, progress.minimum, TOLERANCE)
        assertEquals(GoalBand.BELOW_MINIMUM, progress.band)
    }

    @Test
    fun `the marker sits halfway when half the maximum is spent`() {
        val progress = GoalStatus.evaluate(spent = 2500.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(0.5, progress.fractionOfMaximum, TOLERANCE)
    }

    @Test
    fun `the marker stops at the end of the track when the maximum is passed`() {
        val progress = GoalStatus.evaluate(spent = 9000.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(1.0, progress.fractionOfMaximum, TOLERANCE)
    }

    @Test
    fun `the minimum marker sits at its share of the track`() {
        val progress = GoalStatus.evaluate(spent = 2500.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(0.2, progress.minimumFraction, TOLERANCE)
    }

    @Test
    fun `no goals set leaves the marker at the start`() {
        val progress = GoalStatus.evaluate(spent = 400.0, minimum = 0.0, maximum = 0.0)

        assertEquals(0.0, progress.fractionOfMaximum, TOLERANCE)
        assertEquals(0.0, progress.minimumFraction, TOLERANCE)
    }

    @Test
    fun `reports how much is left before the maximum`() {
        val progress = GoalStatus.evaluate(spent = 3000.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(2000.0, progress.remainingToMaximum, TOLERANCE)
    }

    @Test
    fun `reports how much more is needed to reach the minimum`() {
        val progress = GoalStatus.evaluate(spent = 400.0, minimum = 1000.0, maximum = 5000.0)

        assertEquals(600.0, progress.remainingToMinimum, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
