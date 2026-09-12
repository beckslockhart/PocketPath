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

    abstract fun userDao(): UserDao

    abstract fun categoryDao(): CategoryDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun monthlyGoalDao(): MonthlyGoalDao

    companion object {

        @Volatile
        private var INSTANCE: PocketPathDatabase? = null


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