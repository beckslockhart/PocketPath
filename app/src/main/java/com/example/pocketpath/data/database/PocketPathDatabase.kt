package com.example.pocketpath.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pocketpath.data.dao.CategoryDao
import com.example.pocketpath.data.dao.ExpenseDao
import com.example.pocketpath.data.dao.MonthlyGoalDao
import com.example.pocketpath.data.dao.UserDao
import com.example.pocketpath.data.entity.Category
import com.example.pocketpath.data.entity.Expense
import com.example.pocketpath.data.entity.MonthlyGoal
import com.example.pocketpath.data.entity.User

/**
 * Main Room database for the PocketPath application.
 *
 * The database stores users, expense categories, expenses and monthly
 * spending goals locally on the Android device.
 */
@Database(
    entities = [
        User::class,
        Category::class,
        Expense::class,
        MonthlyGoal::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PocketPathDatabase : RoomDatabase() {

    // Provides access to registration and login database operations.
    abstract fun userDao(): UserDao

    // Provides access to category-management database operations.
    abstract fun categoryDao(): CategoryDao

    // Provides access to expense and reporting database operations.
    abstract fun expenseDao(): ExpenseDao

    // Provides access to monthly spending-goal operations.
    abstract fun monthlyGoalDao(): MonthlyGoalDao

    companion object {

        /**
         * Volatile ensures that all threads see the most recent
         * value of the database instance.
         */
        @Volatile
        private var INSTANCE: PocketPathDatabase? = null

        /**
         * Returns the existing Room database or creates it when the app
         * requests it for the first time.
         *
         * A singleton is used so the application does not create multiple
         * database connections.
         */
        fun getDatabase(context: Context): PocketPathDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketPathDatabase::class.java,
                    "pocketpath_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}