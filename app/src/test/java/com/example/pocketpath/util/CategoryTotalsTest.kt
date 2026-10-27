package com.example.pocketpath.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// tests for the totals that feed the spending per category graph
class CategoryTotalsTest {

    @Test
    fun `adds up the expenses in each category`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("groceries", 200.0),
                ExpenseAmount("groceries", 150.0),
                ExpenseAmount("transport", 80.0)
            ),
            categories = listOf(
                CategoryName("groceries", "Groceries"),
                CategoryName("transport", "Transport")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(350.0, amountFor(summary, "Groceries"), TOLERANCE)
        assertEquals(80.0, amountFor(summary, "Transport"), TOLERANCE)
        assertEquals(430.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `orders categories from the largest spend downwards`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("transport", 300.0),
                ExpenseAmount("groceries", 1250.0),
                ExpenseAmount("fun", 700.0)
            ),
            categories = listOf(
                CategoryName("groceries", "Groceries"),
                CategoryName("transport", "Transport"),
                CategoryName("fun", "Entertainment")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(
            listOf("Groceries", "Entertainment", "Transport"),
            summary.rows.map { it.categoryName }
        )
    }

    @Test
    fun `keeps a category that had no spending so the graph shows it`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(ExpenseAmount("groceries", 200.0)),
            categories = listOf(
                CategoryName("groceries", "Groceries"),
                CategoryName("holidays", "Holidays")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        assertTrue(summary.rows.any { it.categoryName == "Holidays" })
        assertEquals(0.0, amountFor(summary, "Holidays"), TOLERANCE)
        assertEquals(200.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `an empty category is left out of the written breakdown`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(ExpenseAmount("groceries", 200.0)),
            categories = listOf(
                CategoryName("groceries", "Groceries"),
                CategoryName("holidays", "Holidays")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        val listed = CategoryTotals.withSpendingOnly(summary)

        assertEquals(listOf("Groceries"), listed.rows.map { it.categoryName })
        assertEquals(200.0, listed.periodTotal, TOLERANCE)
    }

    @Test
    fun `an expense with no category is grouped as uncategorised`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("groceries", 200.0),
                ExpenseAmount(null, 150.0)
            ),
            categories = listOf(CategoryName("groceries", "Groceries")),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(150.0, amountFor(summary, UNCATEGORISED), TOLERANCE)
        assertEquals(350.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `an expense whose category was deleted is still counted`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("groceries", 200.0),
                ExpenseAmount("deletedCategory", 90.0)
            ),
            categories = listOf(CategoryName("groceries", "Groceries")),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(90.0, amountFor(summary, UNCATEGORISED), TOLERANCE)
        assertEquals(290.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `uncategorised is left out when everything has a category`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(ExpenseAmount("groceries", 200.0)),
            categories = listOf(CategoryName("groceries", "Groceries")),
            uncategorisedLabel = UNCATEGORISED
        )

        assertFalse(summary.rows.any { it.categoryName == UNCATEGORISED })
    }

    @Test
    fun `a period with no expenses is empty and totals zero`() {
        val summary = CategoryTotals.summarise(
            expenses = emptyList(),
            categories = listOf(CategoryName("groceries", "Groceries")),
            uncategorisedLabel = UNCATEGORISED
        )

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `a user with no categories at all still totals their spending`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(ExpenseAmount(null, 75.0)),
            categories = emptyList(),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(75.0, summary.periodTotal, TOLERANCE)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun `breaks ties on equal spend by category name`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("transport", 500.0),
                ExpenseAmount("groceries", 500.0)
            ),
            categories = listOf(
                CategoryName("transport", "Transport"),
                CategoryName("groceries", "Groceries")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(
            listOf("Groceries", "Transport"),
            summary.rows.map { it.categoryName }
        )
    }

    @Test
    fun `reports the largest amount so the graph can be scaled`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("groceries", 1250.0),
                ExpenseAmount("transport", 300.0)
            ),
            categories = listOf(
                CategoryName("groceries", "Groceries"),
                CategoryName("transport", "Transport")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(1250.0, summary.largestAmount, TOLERANCE)
    }

    @Test
    fun `adds up amounts with cents without losing money`() {
        val summary = CategoryTotals.summarise(
            expenses = listOf(
                ExpenseAmount("a", 10.10),
                ExpenseAmount("b", 20.20),
                ExpenseAmount("c", 5.05)
            ),
            categories = listOf(
                CategoryName("a", "Groceries"),
                CategoryName("b", "Transport"),
                CategoryName("c", "Entertainment")
            ),
            uncategorisedLabel = UNCATEGORISED
        )

        assertEquals(35.35, summary.periodTotal, TOLERANCE)
    }

    // finds one category amount in the summary
    private fun amountFor(summary: CategoryTotalsSummary, name: String): Double =
        summary.rows.first { it.categoryName == name }.totalAmount

    private companion object {
        const val TOLERANCE = 0.0001
        const val UNCATEGORISED = "Uncategorised"
    }
}
