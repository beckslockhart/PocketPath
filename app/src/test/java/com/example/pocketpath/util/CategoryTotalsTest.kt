package com.example.pocketpath.util

import com.example.pocketpath.data.dao.CategorySpendingTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTotalsTest {
    @Test
    fun `orders categories from the largest spend downwards`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Transport", 300.0),
                total("Groceries", 1250.0),
                total("Entertainment", 700.0)
            )
        )

        assertEquals(
            listOf("Groceries", "Entertainment", "Transport"),
            summary.rows.map { it.categoryName }
        )
    }

    @Test
    fun `adds up the total for the period`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Groceries", 1250.0),
                total("Transport", 300.0),
                total("Entertainment", 700.0)
            )
        )

        assertEquals(2250.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `leaves out categories with nothing spent`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Groceries", 1250.0),
                total("Holidays", 0.0)
            )
        )

        assertEquals(listOf("Groceries"), summary.rows.map { it.categoryName })
        assertEquals(1250.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `keeps uncategorised spending in the totals`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Groceries", 200.0),
                total("Uncategorised", 150.0)
            )
        )

        assertTrue(summary.rows.any { it.categoryName == "Uncategorised" })
        assertEquals(350.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `breaks ties on equal spend by category name`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Transport", 500.0),
                total("Groceries", 500.0)
            )
        )

        assertEquals(
            listOf("Groceries", "Transport"),
            summary.rows.map { it.categoryName }
        )
    }

    @Test
    fun `a period with no expenses is empty and totals zero`() {
        val summary = CategoryTotals.summarise(emptyList())

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `a period where every category spent nothing is empty`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Groceries", 0.0),
                total("Transport", 0.0)
            )
        )

        assertTrue(summary.isEmpty)
        assertEquals(0.0, summary.periodTotal, TOLERANCE)
    }

    @Test
    fun `adds up amounts with cents without losing money`() {
        val summary = CategoryTotals.summarise(
            listOf(
                total("Groceries", 10.10),
                total("Transport", 20.20),
                total("Entertainment", 5.05)
            )
        )

        assertEquals(35.35, summary.periodTotal, TOLERANCE)
    }

    private fun total(categoryName: String, amount: Double) =
        CategorySpendingTotal(categoryName = categoryName, totalAmount = amount)

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
