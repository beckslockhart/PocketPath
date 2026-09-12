package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase

    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var tvMinimumGoal: TextView
    private lateinit var tvMaximumGoal: TextView
    private lateinit var tvBudgetStatus: TextView
    private lateinit var tvRecentExpenses: TextView
    private lateinit var progressMonthlyBudget: ProgressBar

    private var currentUserId: Long = -1

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
        ).getLong(KEY_USER_ID, -1)

        if (currentUserId == -1L) {
            returnToLogin()
            return
        }

        connectViews()
        configureButtons()
        loadUserDetails()
        loadMonthlySpending()
        loadRecentExpenses()
    }

    private fun connectViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvTotalSpent = findViewById(R.id.tvTotalSpent)
        tvMinimumGoal = findViewById(R.id.tvMinimumGoal)
        tvMaximumGoal = findViewById(R.id.tvMaximumGoal)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        tvRecentExpenses = findViewById(R.id.tvRecentExpenses)
        progressMonthlyBudget = findViewById(R.id.progressMonthlyBudget)

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
            showFeatureMessage("Add Expense")
        }

        btnExpenseHistory.setOnClickListener {
            showFeatureMessage("Expense History")
        }

        btnCategories.setOnClickListener {
            showFeatureMessage("Budget Categories")
        }

        btnMonthlyGoals.setOnClickListener {
            showFeatureMessage("Monthly Goals")
        }

        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun configureButtons() {
        Log.d(TAG, "Dashboard controls configured")
    }

    private fun loadUserDetails() {
        lifecycleScope.launch {
            try {
                val user = database.userDao().getUserById(currentUserId)

                if (user != null) {
                    tvWelcome.text = "Hello, ${user.username}!"
                    Log.d(TAG, "Dashboard loaded for user ID: $currentUserId")
                } else {
                    logout()
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load user details", exception)
                Toast.makeText(
                    this@DashboardActivity,
                    "Unable to load user details",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadMonthlySpending() {
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val endOfMonth = calendar.timeInMillis

        lifecycleScope.launch {
            database.expenseDao()
                .getTotalSpentForPeriod(
                    currentUserId,
                    startOfMonth,
                    endOfMonth
                )
                .collect { totalSpent ->
                    tvTotalSpent.text = formatCurrency(totalSpent)

                    if (totalSpent == 0.0) {
                        tvBudgetStatus.text =
                            "No expenses recorded this month"
                        progressMonthlyBudget.progress = 0
                    }
                }
        }
    }

    private fun loadRecentExpenses() {
        lifecycleScope.launch {
            database.expenseDao()
                .getAllExpensesForUser(currentUserId)
                .collect { expenses ->
                    val recentExpenses = expenses.take(3)

                    if (recentExpenses.isEmpty()) {
                        tvRecentExpenses.text =
                            "No expenses recorded yet"
                    } else {
                        val dateFormatter =
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

                        tvRecentExpenses.text = recentExpenses.joinToString(
                            separator = "\n\n"
                        ) { expense ->
                            val date =
                                dateFormatter.format(expense.expenseDate)

                            "${expense.description}\n" +
                                    "$date  •  ${formatCurrency(expense.amount)}"
                        }
                    }
                }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return String.format(
            Locale.getDefault(),
            "R%.2f",
            amount
        )
    }

    private fun showFeatureMessage(featureName: String) {
        Toast.makeText(
            this,
            "$featureName screen will be connected next",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun logout() {
        getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        ).edit().remove(KEY_USER_ID).apply()

        Log.d(TAG, "User logged out")
        returnToLogin()
    }

    private fun returnToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}