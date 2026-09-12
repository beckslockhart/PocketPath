package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase
    private lateinit var tvWelcome: TextView

    private var currentUserId: Long = -1L

    companion object {
        private const val TAG = "PocketPathDashboard"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        database = PocketPathDatabase.getDatabase(this)

        currentUserId = getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        ).getLong(KEY_USER_ID, -1L)

        if (currentUserId == -1L) {
            returnToLogin()
            return
        }

        tvWelcome = findViewById(R.id.tvWelcome)

        loadUserDetails()
        configureButtons()
    }

    private fun loadUserDetails() {
        lifecycleScope.launch {
            try {
                val user = database.userDao().getUserById(currentUserId)

                if (user != null) {
                    tvWelcome.text = "Hello, ${user.username}!"
                    Log.d(TAG, "Dashboard loaded for user ID: $currentUserId")
                } else {
                    returnToLogin()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load user details", exception)
            }
        }
    }

    private fun configureButtons() {
        val btnAddExpense =
            findViewById<MaterialButton>(R.id.btnAddExpense)

        val btnExpenseHistory =
            findViewById<MaterialButton>(R.id.btnExpenseHistory)

        val btnCategories =
            findViewById<MaterialButton>(R.id.btnCategories)

        val btnMonthlyGoals =
            findViewById<MaterialButton>(R.id.btnMonthlyGoals)

        val btnLogout =
            findViewById<MaterialButton>(R.id.btnLogout)

        btnAddExpense.setOnClickListener {
            startActivity(
                Intent(this, AddExpensesActivity::class.java)
            )
        }

        btnExpenseHistory.setOnClickListener {
            startActivity(
                Intent(this, ExpenseHistoryActivity::class.java)
            )
        }

        btnCategories.setOnClickListener {
            startActivity(
                Intent(this, CategoriesActivity::class.java)
            )
        }

        btnMonthlyGoals.setOnClickListener {
            startActivity(
                Intent(this, MonthlyGoalsActivity::class.java)
            )
        }

        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        ).edit()
            .remove(KEY_USER_ID)
            .apply()

        Log.d(TAG, "User logged out")
        returnToLogin()
    }

    private fun returnToLogin() {
        val intent = Intent(this, MainActivity::class.java)

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}