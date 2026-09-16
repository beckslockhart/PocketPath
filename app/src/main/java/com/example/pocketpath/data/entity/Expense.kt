package com.example.pocketpath.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an expense recorded by a PocketPath user.
 *
 * Each expense belongs to a user and may be linked to a category.
 * It stores the information required by the brief, including the date,
 * times, description, amount and optional photograph.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        // Indices improve queries that filter expenses by user, category or date.
        Index(value = ["userId"]),
        Index(value = ["categoryId"]),
        Index(value = ["expenseDate"])
    ]
)
data class Expense(

    // Unique identifier automatically generated for each expense.
    @PrimaryKey(autoGenerate = true)
    val expenseId: Long = 0,

    // Identifies the user who created the expense.
    val userId: Long,

    /**
     * Links the expense to a category.
     *
     * This value is nullable because an expense remains stored when
     * its category is deleted and will then appear as uncategorised.
     */
    val categoryId: Long?,

    // Monetary value of the expense.
    val amount: Double,

    // User-provided explanation of what the expense was for.
    val description: String,

    // Selected expense date stored as milliseconds for period filtering.
    val expenseDate: Long,

    // Start time stored using the app's selected time format.
    val startTime: String,

    // End time stored using the app's selected time format.
    val endTime: String,

    // Optional URI pointing to the photograph attached to the expense.
    val photoUri: String? = null
)