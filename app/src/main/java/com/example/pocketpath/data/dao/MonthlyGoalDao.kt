package com.example.pocketpath.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.pocketpath.data.entity.MonthlyGoal
import kotlinx.coroutines.flow.Flow


@Dao
interface MonthlyGoalDao {


    @Upsert
    suspend fun saveMonthlyGoal(monthlyGoal: MonthlyGoal)

    @Query(
        """
        SELECT * FROM monthly_goals
        WHERE userId = :userId
        LIMIT 1
        """
    )
    fun getMonthlyGoal(userId: Long): Flow<MonthlyGoal?>

    @Query(
        """
        DELETE FROM monthly_goals
        WHERE userId = :userId
        """
    )
    suspend fun deleteMonthlyGoal(userId: Long)
}