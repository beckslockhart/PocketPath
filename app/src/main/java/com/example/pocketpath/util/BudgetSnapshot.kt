package com.example.pocketpath.util

// one expense with only the details the path score and the badges look at
data class TrackedExpense(
    val categoryId: String?,
    val amount: Double,
    val dateMillis: Long,
    val hasReceipt: Boolean
)

// a budget category and the most the user wants to spend on it each month
// a limit of zero means no limit was set
data class CategoryLimit(
    val categoryId: String,
    val name: String,
    val monthlyLimit: Double
)

// everything the path progress screen knows about the user at one moment
// kept free of firebase classes so the score and badges can be unit tested
data class BudgetSnapshot(
    val expenses: List<TrackedExpense>,
    val categories: List<CategoryLimit>,
    // a maximum of zero means the monthly goal has not been set
    val minimumGoal: Double,
    val maximumGoal: Double
)
