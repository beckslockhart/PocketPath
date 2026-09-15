package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.MonthlyGoal
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase
    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var progressMonthlyBudget: ProgressBar
    private lateinit var tvBudgetStatus: TextView
    private lateinit var tvMinimumGoal: TextView
    private lateinit var tvMaximumGoal: TextView

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
        tvTotalSpent = findViewById(R.id.tvTotalSpent)
        progressMonthlyBudget = findViewById(R.id.progressMonthlyBudget)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        tvMinimumGoal = findViewById(R.id.tvMinimumGoal)
        tvMaximumGoal = findViewById(R.id.tvMaximumGoal)

        loadUserDetails()
        observeMonthlyOverview()
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

    private fun observeMonthlyOverview() {
        val (startOfMonth, endOfMonth) = monthRange()
        lifecycleScope.launch {
            try {
                combine(
                    database.expenseDao().getTotalSpentForPeriod(
                        currentUserId, startOfMonth, endOfMonth
                    ),
                    database.monthlyGoalDao().getMonthlyGoal(currentUserId)
                ) { totalSpent, goal ->
                    totalSpent to goal
                }.collectLatest { (totalSpent, goal) ->
                    renderMonthlyOverview(totalSpent, goal)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load monthly overview", exception)
            }
        }
    }

    private fun renderMonthlyOverview(totalSpent: Double, goal: MonthlyGoal?) {
        tvTotalSpent.text = "R%.2f".format(totalSpent)

        if (goal == null) {
            tvMinimumGoal.text = "Not set"
            tvMaximumGoal.text = "Not set"
            progressMonthlyBudget.progress = 0
            tvBudgetStatus.text = "Set your monthly goals to track your progress"
            return
        }

        tvMinimumGoal.text = "R%.2f".format(goal.minimumAmount)
        tvMaximumGoal.text = "R%.2f".format(goal.maximumAmount)

        val percent = if (goal.maximumAmount > 0) {
            ((totalSpent / goal.maximumAmount) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        progressMonthlyBudget.progress = percent

        tvBudgetStatus.text = when {
            totalSpent > goal.maximumAmount -> "You've gone over your maximum goal"
            totalSpent < goal.minimumAmount -> "Below your minimum spending goal so far"
            else -> "On track with your monthly goals"
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

    private fun monthRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }
}