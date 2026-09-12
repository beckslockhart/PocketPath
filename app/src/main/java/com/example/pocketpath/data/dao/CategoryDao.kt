package com.example.pocketpath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pocketpath.data.entity.Category
import kotlinx.coroutines.flow.Flow


@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query(
        """
        SELECT * FROM categories
        WHERE userId = :userId
        ORDER BY name ASC
        """
    )
    fun getCategoriesForUser(userId: Long): Flow<List<Category>>

    @Query(
        """
        SELECT * FROM categories
        WHERE categoryId = :categoryId
        LIMIT 1
        """
    )
    suspend fun getCategoryById(categoryId: Long): Category?

    @Query(
        """
        SELECT COUNT(*) FROM categories
        WHERE userId = :userId
        AND LOWER(name) = LOWER(:name)
        """
    )
    suspend fun countCategoriesWithName(userId: Long, name: String): Int
}