package com.example.pocketpath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pocketpath.data.entity.Expense
import kotlinx.coroutines.flow.Flow

/**
 * Holds the total amount spent in one category during a selected period.
 *
 * Room maps the names returned by the category-total query to these properties.
 */
data class CategorySpendingTotal(
    val categoryName: String,
    val totalAmount: Double
)

/**
 * Contains the Room database operations used to create, retrieve,
 * update, delete and report on expenses.
 */
@Dao
interface ExpenseDao {

    /**
     * Inserts a new expense and returns its automatically generated ID.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: Expense): Long

    /**
     * Updates an existing expense using its primary key.
     */
    @Update
    suspend fun updateExpense(expense: Expense)

    /**
     * Permanently removes the selected expense.
     */
    @Delete
    suspend fun deleteExpense(expense: Expense)

    /**
     * Observes all expenses belonging to a user.
     *
     * The newest expenses are returned first and the Flow updates
     * automatically when an expense is added, edited or deleted.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE userId = :userId
        ORDER BY expenseDate DESC, startTime DESC
        """
    )
    fun getAllExpensesForUser(userId: Long): Flow<List<Expense>>

    /**
     * Retrieves one expense by its unique ID.
     *
     * This can be used when opening an expense to view its full details
     * or attached photograph.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE expenseId = :expenseId
        LIMIT 1
        """
    )
    suspend fun getExpenseById(expenseId: Long): Expense?

    /**
     * Observes expenses recorded between the selected start and end dates.
     * This query is used by the filtered Expense History screen.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE userId = :userId
        AND expenseDate BETWEEN :startDate AND :endDate
        ORDER BY expenseDate DESC, startTime DESC
        """
    )
    fun getExpensesForPeriod(
        userId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<Expense>>

    /**
     * Calculates how much the user spent in each category during a
     * selected period.
     *
     * A LEFT JOIN preserves expenses whose original category was deleted.
     * Those expenses are displayed as Uncategorised.
     */
    @Query(
        """
        SELECT COALESCE(categories.name, 'Uncategorised') AS categoryName,
               SUM(expenses.amount) AS totalAmount
        FROM expenses
        LEFT JOIN categories
        ON expenses.categoryId = categories.categoryId
        WHERE expenses.userId = :userId
        AND expenses.expenseDate BETWEEN :startDate AND :endDate
        GROUP BY expenses.categoryId, categories.name
        ORDER BY totalAmount DESC
        """
    )
    fun getCategoryTotalsForPeriod(
        userId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<CategorySpendingTotal>>

    /**
     * Calculates the user's combined spending during a selected period.
     *
     * COALESCE returns 0.0 instead of null when no expenses exist.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0.0)
        FROM expenses
        WHERE userId = :userId
        AND expenseDate BETWEEN :startDate AND :endDate
        """
    )
    fun getTotalSpentForPeriod(
        userId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<Double>
}