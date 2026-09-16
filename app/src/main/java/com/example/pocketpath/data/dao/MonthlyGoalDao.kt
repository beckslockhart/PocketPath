package com.example.pocketpath.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.pocketpath.data.entity.MonthlyGoal
import kotlinx.coroutines.flow.Flow

/**
 * Contains the Room operations used to save, retrieve and delete
 * a user's monthly minimum and maximum spending goals.
 */
@Dao
interface MonthlyGoalDao {

    /**
     * Inserts a new monthly goal or updates the user's existing goal.
     *
     * Upsert is appropriate because each user has only one active
     * MonthlyGoal record.
     */
    @Upsert
    suspend fun saveMonthlyGoal(monthlyGoal: MonthlyGoal)

    /**
     * Observes the user's monthly goal as a Flow.
     *
     * The Dashboard updates automatically whenever the saved goal changes.
     */
    @Query(
        """
        SELECT * FROM monthly_goals
        WHERE userId = :userId
        LIMIT 1
        """
    )
    fun getMonthlyGoal(userId: Long): Flow<MonthlyGoal?>

    /**
     * Deletes the monthly goal belonging to the selected user.
     */
    @Query(
        """
        DELETE FROM monthly_goals
        WHERE userId = :userId
        """
    )
    suspend fun deleteMonthlyGoal(userId: Long)
}