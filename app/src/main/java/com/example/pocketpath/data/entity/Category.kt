package com.example.pocketpath.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an expense category created by a PocketPath user.
 *
 * Categories organise expenses and contain a monthly spending limit
 * that can be used to calculate budget progress.
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        // Improves database queries that retrieve one user's categories.
        Index(value = ["userId"]),

        // Prevents a user from creating two categories with the same name.
        Index(
            value = ["userId", "name"],
            unique = true
        )
    ]
)
data class Category(

    // Unique identifier automatically generated for each category.
    @PrimaryKey(autoGenerate = true)
    val categoryId: Long = 0,

    /**
     * Identifies the user who owns the category.
     *
     * If the user is deleted, their categories are also removed
     * because the foreign key uses CASCADE.
     */
    val userId: Long,

    // User-defined category name, such as Groceries or Transport.
    val name: String,

    // Maximum amount allocated to this category for the month.
    val monthlyLimit: Double
)