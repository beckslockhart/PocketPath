package com.example.pocketpath.util

import com.example.pocketpath.data.dao.CategorySpendingTotal

// totals for each category plus the overall period total
data class CategoryTotalsSummary(
    val rows: List<CategorySpendingTotal>,
    val periodTotal: Double
) {
    // true when nothing was spent in the period
    val isEmpty: Boolean get() = rows.isEmpty()
}

// turns the raw category sums into what the screen shows
object CategoryTotals {
    // drops empty categories orders by spend and adds up the total
    fun summarise(totals: List<CategorySpendingTotal>): CategoryTotalsSummary {
        val spentRows = totals
            // ignore categories with nothing spent
            .filter { it.totalAmount > 0.0 }
            // biggest spend first then by name
            .sortedWith(
                compareByDescending<CategorySpendingTotal> { it.totalAmount }
                    .thenBy { it.categoryName.lowercase() }
            )

        return CategoryTotalsSummary(
            rows = spentRows,
            periodTotal = spentRows.sumOf { it.totalAmount }
        )
    }
}
