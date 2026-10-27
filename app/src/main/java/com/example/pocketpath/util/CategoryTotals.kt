package com.example.pocketpath.util

// the amount of one expense and the category it belongs to
data class ExpenseAmount(
    val categoryId: String?,
    val amount: Double
)

// a category id and the name shown for it
data class CategoryName(
    val categoryId: String,
    val name: String
)

// one bar of the spending per category graph
data class CategorySpend(
    val categoryName: String,
    val totalAmount: Double
)

// every category bar plus the overall total for the period
data class CategoryTotalsSummary(
    val rows: List<CategorySpend>,
    val periodTotal: Double
) {
    // true when nothing was spent in the period
    val isEmpty: Boolean get() = rows.all { it.totalAmount <= 0.0 }

    // the biggest single category amount used to scale the graph
    val largestAmount: Double get() = rows.maxOfOrNull { it.totalAmount } ?: 0.0
}

// groups expenses by category so they can be listed and graphed
object CategoryTotals {

    // adds up each category for the period
    // every category is kept even when nothing was spent on it so the graph
    // shows the full picture and expenses with no category are grouped under
    // the uncategorised label instead of being dropped
    fun summarise(
        expenses: List<ExpenseAmount>,
        categories: List<CategoryName>,
        uncategorisedLabel: String
    ): CategoryTotalsSummary {
        val namesById = categories.associate { it.categoryId to it.name }

        val spentById = mutableMapOf<String, Double>()
        var uncategorised = 0.0

        for (expense in expenses) {
            val categoryId = expense.categoryId

            // an expense keeps its spending even when its category was deleted
            if (categoryId == null || !namesById.containsKey(categoryId)) {
                uncategorised += expense.amount
            } else {
                spentById[categoryId] = (spentById[categoryId] ?: 0.0) + expense.amount
            }
        }

        val rows = mutableListOf<CategorySpend>()

        for (category in categories) {
            rows.add(
                CategorySpend(
                    categoryName = category.name,
                    totalAmount = spentById[category.categoryId] ?: 0.0
                )
            )
        }

        if (uncategorised > 0.0) {
            rows.add(CategorySpend(uncategorisedLabel, uncategorised))
        }

        // biggest spend first then by name so the order is stable
        val ordered = rows.sortedWith(
            compareByDescending<CategorySpend> { it.totalAmount }
                .thenBy { it.categoryName.lowercase() }
        )

        return CategoryTotalsSummary(
            rows = ordered,
            periodTotal = ordered.sumOf { it.totalAmount }
        )
    }

    // the same totals with empty categories removed for the written list
    fun withSpendingOnly(summary: CategoryTotalsSummary): CategoryTotalsSummary =
        CategoryTotalsSummary(
            rows = summary.rows.filter { it.totalAmount > 0.0 },
            periodTotal = summary.periodTotal
        )
}
