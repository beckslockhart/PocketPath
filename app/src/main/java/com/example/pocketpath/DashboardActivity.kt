package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.Category
import com.example.pocketpath.data.entity.Expense
import com.example.pocketpath.data.entity.MonthlyGoal
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Displays the active user's monthly financial overview.
 *
 * The Dashboard observes RoomDB data so spending totals, goals and recent
 * expenses update automatically whenever the underlying data changes.
 */
class DashboardActivity : AppCompatActivity() {

    // Shared Room database used to access users, expenses, categories and goals.
    private lateinit var database: PocketPathDatabase

    // Dashboard controls that are updated using information from RoomDB.
    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var progressMonthlyBudget: ProgressBar
    private lateinit var tvBudgetStatus: TextView
    private lateinit var tvMinimumGoal: TextView
    private lateinit var tvMaximumGoal: TextView
    private lateinit var tvRecentExpenses: TextView

    // ID of the user who is currently logged into PocketPath.
    private var currentUserId: Long = -1L

    companion object {
        private const val TAG = "PocketPathDashboard"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
        private const val RECENT_EXPENSE_LIMIT = 5

        // Formats stored expense dates for display on the Dashboard.
        private val DATE_FORMAT =
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        database = PocketPathDatabase.getDatabase(this)

        // Retrieve the active user's ID from local SharedPreferences.
        currentUserId = getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        ).getLong(KEY_USER_ID, -1L)

        // Return to Login if there is no valid active session.
        if (currentUserId == -1L) {
            returnToLogin()
            return
        }

        connectViews()
        loadUserDetails()
        observeMonthlyOverview()
        observeRecentExpenses()
        configureButtons()
    }

    /**
     * Connects the Kotlin variables to the controls in activity_dashboard.xml.
     */
    private fun connectViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvTotalSpent = findViewById(R.id.tvTotalSpent)
        progressMonthlyBudget = findViewById(R.id.progressMonthlyBudget)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        tvMinimumGoal = findViewById(R.id.tvMinimumGoal)
        tvMaximumGoal = findViewById(R.id.tvMaximumGoal)
        tvRecentExpenses = findViewById(R.id.tvRecentExpenses)
    }

    /**
     * Loads the current user's information for the welcome message.
     */
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

    /**
     * Combines the user's total spending for the current month with their
     * saved monthly goal. Changes are observed live using Kotlin Flow.
     */
    private fun observeMonthlyOverview() {
        val (startOfMonth, endOfMonth) = monthRange()

        lifecycleScope.launch {
            try {
                combine(
                    database.expenseDao().getTotalSpentForPeriod(
                        currentUserId,
                        startOfMonth,
                        endOfMonth
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

    /**
     * Updates the spending total, goal values, progress bar and status message.
     */
    private fun renderMonthlyOverview(
        totalSpent: Double,
        goal: MonthlyGoal?
    ) {
        tvTotalSpent.text = "R%.2f".format(totalSpent)

        // Display default values until the user creates a monthly goal.
        if (goal == null) {
            tvMinimumGoal.text = "Not set"
            tvMaximumGoal.text = "Not set"
            progressMonthlyBudget.progress = 0
            tvBudgetStatus.text =
                "Set your monthly goals to track your progress"
            return
        }

        tvMinimumGoal.text = "R%.2f".format(goal.minimumAmount)
        tvMaximumGoal.text = "R%.2f".format(goal.maximumAmount)

        // Calculate spending as a percentage of the maximum monthly goal.
        val percent = if (goal.maximumAmount > 0) {
            ((totalSpent / goal.maximumAmount) * 100)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        progressMonthlyBudget.progress = percent

        tvBudgetStatus.text = when {
            totalSpent > goal.maximumAmount ->
                "You've gone over your maximum goal"

            totalSpent < goal.minimumAmount ->
                "Below your minimum spending goal so far"

            else ->
                "On track with your monthly goals"
        }
    }

    /**
     * Observes expenses and categories together so recent expense entries
     * can display their corresponding category names.
     */
    private fun observeRecentExpenses() {
        lifecycleScope.launch {
            try {
                combine(
                    database.expenseDao()
                        .getAllExpensesForUser(currentUserId),

                    database.categoryDao()
                        .getCategoriesForUser(currentUserId)
                ) { expenses, categories ->
                    expenses to categories
                }.collectLatest { (expenses, categories) ->
                    renderRecentExpenses(expenses, categories)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load recent expenses", exception)
            }
        }
    }

    /**
     * Displays the five most recent expenses or an empty-state message.
     */
    private fun renderRecentExpenses(
        expenses: List<Expense>,
        categories: List<Category>
    ) {
        if (expenses.isEmpty()) {
            tvRecentExpenses.gravity = Gravity.CENTER
            tvRecentExpenses.text = "No expenses recorded yet"
            return
        }

        // Create a lookup table so each category ID can be shown as a name.
        val categoryNamesById = categories.associate {
            it.categoryId to it.name
        }

        val lines = expenses
            .take(RECENT_EXPENSE_LIMIT)
            .map { expense ->
                val categoryName = expense.categoryId
                    ?.let { categoryNamesById[it] }
                    ?: "Uncategorised"

                val dateText =
                    DATE_FORMAT.format(Date(expense.expenseDate))

                "${expense.description}  ·  R%.2f".format(expense.amount) +
                        "\n$categoryName  ·  $dateText"
            }

        tvRecentExpenses.gravity = Gravity.START
        tvRecentExpenses.text = lines.joinToString("\n\n")
    }

    /**
     * Connects each Dashboard button to its corresponding feature screen.
     */
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

    /**
     * Removes the saved user session and returns to the Login screen.
     */
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

    /**
     * Clears the previous activity stack so a logged-out user cannot return
     * to the Dashboard by pressing the Android Back button.
     */
    private fun returnToLogin() {
        val intent = Intent(this, MainActivity::class.java)

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    /**
     * Calculates the first and last millisecond of the current month.
     * These timestamps are used to query the month's expenses.
     */
    private fun monthRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(
                Calendar.DAY_OF_MONTH,
                getActualMaximum(Calendar.DAY_OF_MONTH)
            )
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return start to end
    }
}