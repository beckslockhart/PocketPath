package com.example.pocketpath.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Stores the minimum and maximum monthly spending goals for a user.
 *
 * The user ID is used as the primary key because each user can have
 * one active set of monthly spending goals.
 */
@Entity(
    tableName = "monthly_goals",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MonthlyGoal(

    /**
     * Identifies the user who owns these goals.
     *
     * Deleting the user automatically deletes their goals because
     * the foreign key uses CASCADE.
     */
    @PrimaryKey
    val userId: Long,

    // Minimum amount the user intends to spend during the month.
    val minimumAmount: Double,

    // Maximum amount the user wants to avoid exceeding during the month.
    val maximumAmount: Double
)