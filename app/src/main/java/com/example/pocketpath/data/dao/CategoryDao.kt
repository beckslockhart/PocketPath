package com.example.pocketpath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pocketpath.data.entity.Category
import kotlinx.coroutines.flow.Flow

/**
 * Contains the Room operations used to create, retrieve,
 * update and delete expense categories.
 */
@Dao
interface CategoryDao {

    /**
     * Inserts a new category and returns its automatically generated ID.
     *
     * ABORT prevents the insertion if the user already has a category
     * with the same name.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: Category): Long

    /**
     * Updates the selected category's name or monthly limit.
     */
    @Update
    suspend fun updateCategory(category: Category)

    /**
     * Deletes the selected category.
     *
     * Previous expenses remain in the database and become uncategorised
     * because the Expense entity uses SET_NULL for its category foreign key.
     */
    @Delete
    suspend fun deleteCategory(category: Category)

    /**
     * Observes all categories belonging to a user in alphabetical order.
     *
     * The Flow updates automatically when categories are added,
     * edited or deleted.
     */
    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId
        ORDER BY name ASC
        """
    )
    fun getCategoriesForUser(userId: Long): Flow<List<Category>>

    /**
     * Retrieves one category using its unique ID.
     */
    @Query(
        """
        SELECT * FROM categories
        WHERE categoryId = :categoryId
        LIMIT 1
        """
    )
    suspend fun getCategoryById(categoryId: Long): Category?

    /**
     * Checks whether the user already has a category with the supplied name.
     *
     * LOWER makes the comparison case-insensitive, preventing names such as
     * "Groceries" and "groceries" from being treated as different categories.
     */
    @Query(
        """
        SELECT COUNT(*) FROM categories
        WHERE userId = :userId
        AND LOWER(name) = LOWER(:name)
        """
    )
    suspend fun countCategoriesWithName(
        userId: Long,
        name: String
    ): Int
}