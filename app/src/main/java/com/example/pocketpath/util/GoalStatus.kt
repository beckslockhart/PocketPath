package com.example.pocketpath.util

// where the spending sits against the monthly goals
enum class GoalBand {
    NOT_SET,
    BELOW_MINIMUM,
    WITHIN_GOALS,
    ABOVE_MAXIMUM
}

// everything the goal summary needs to draw itself
data class GoalProgress(
    val band: GoalBand,
    val spent: Double,
    val minimum: Double,
    val maximum: Double
) {
    // how far along the track the marker sits where the track ends at the
    // maximum goal and anything over that is pinned to the end
    val fractionOfMaximum: Double
        get() = when {
            maximum <= 0.0 -> 0.0
            spent <= 0.0 -> 0.0
            else -> (spent / maximum).coerceIn(0.0, 1.0)
        }

    // where the minimum goal sits along the same track
    val minimumFraction: Double
        get() = if (maximum <= 0.0) 0.0 else (minimum / maximum).coerceIn(0.0, 1.0)

    // how much is left before the maximum goal is passed
    val remainingToMaximum: Double get() = maximum - spent

    // how much more needs spending to reach the minimum goal
    val remainingToMinimum: Double get() = minimum - spent
}

// works out whether the user stayed between their monthly goals
object GoalStatus {

    // a maximum of zero means the user has not set goals yet
    // a minimum above the maximum is bad data so the minimum is pulled down
    // to the maximum rather than reporting an impossible band
    fun evaluate(spent: Double, minimum: Double, maximum: Double): GoalProgress {
        if (maximum <= 0.0) {
            return GoalProgress(GoalBand.NOT_SET, spent, minimum, maximum)
        }

        val safeMinimum = minimum.coerceIn(0.0, maximum)

        val band = when {
            spent > maximum -> GoalBand.ABOVE_MAXIMUM
            spent < safeMinimum -> GoalBand.BELOW_MINIMUM
            else -> GoalBand.WITHIN_GOALS
        }

        return GoalProgress(band, spent, safeMinimum, maximum)
    }
}
