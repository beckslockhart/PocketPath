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
 */
data class CategorySpendingTotal(
    val categoryName: String,
    val totalAmount: Double
)


@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query(
        """
        SELECT * FROM expenses
        WHERE userId = :userId
        ORDER BY expenseDate DESC, startTime DESC
        """
    )
    fun getAllExpensesForUser(userId: Long): Flow<List<Expense>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE expenseId = :expenseId
        LIMIT 1
        """
    )
    suspend fun getExpenseById(expenseId: Long): Expense?


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