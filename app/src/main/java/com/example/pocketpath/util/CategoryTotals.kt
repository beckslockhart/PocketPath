package com.example.pocketpath.util

import com.example.pocketpath.data.dao.CategorySpendingTotal

data class CategoryTotalsSummary(
    val rows: List<CategorySpendingTotal>,
    val periodTotal: Double
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

object CategoryTotals {
    fun summarise(totals: List<CategorySpendingTotal>): CategoryTotalsSummary {
        val spentRows = totals
            .filter { it.totalAmount > 0.0 }
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
