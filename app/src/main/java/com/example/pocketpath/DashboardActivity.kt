package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.firebase.FirebaseRepository
import com.example.pocketpath.data.firebase.FirebaseRepository.CategoryData
import com.example.pocketpath.data.firebase.FirebaseRepository.ExpenseData
import com.example.pocketpath.data.firebase.FirebaseRepository.GoalData
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var progressMonthlyBudget: ProgressBar
    private lateinit var tvBudgetStatus: TextView
    private lateinit var tvMinimumGoal: TextView
    private lateinit var tvMaximumGoal: TextView
    private lateinit var tvRecentExpenses: TextView

    private var currentUserId: String? = null

    companion object {
        private const val TAG = "PocketPathDashboard"
        private const val RECENT_EXPENSE_LIMIT = 5

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        currentUserId = FirebaseRepository.currentUserId

        if (currentUserId == null) {
            returnToLogin()
            return
        }

        connectViews()
        loadUserDetails()
        observeMonthlyOverview()
        observeRecentExpenses()
        configureButtons()
    }

    private fun connectViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvTotalSpent = findViewById(R.id.tvTotalSpent)
        progressMonthlyBudget = findViewById(R.id.progressMonthlyBudget)
        tvBudgetStatus = findViewById(R.id.tvBudgetStatus)
        tvMinimumGoal = findViewById(R.id.tvMinimumGoal)
        tvMaximumGoal = findViewById(R.id.tvMaximumGoal)
        tvRecentExpenses = findViewById(R.id.tvRecentExpenses)
    }

    private fun loadUserDetails() {
        val uid = currentUserId ?: return

        lifecycleScope.launch {
            try {
                val username = FirebaseRepository.getUsername(uid)
                tvWelcome.text = "Hello, $username!"
                Log.d(TAG, "Dashboard loaded for user ID: $uid")
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load user details", exception)
            }
        }
    }

    private fun observeMonthlyOverview() {
        val uid = currentUserId ?: return
        val (startOfMonth, endOfMonth) = monthRange()

        lifecycleScope.launch {
            try {
                combine(
                    FirebaseRepository.observeTotalSpentForPeriod(uid, startOfMonth, endOfMonth),
                    FirebaseRepository.observeMonthlyGoal(uid)
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

    private fun renderMonthlyOverview(totalSpent: Double, goal: GoalData?) {
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

    private fun observeRecentExpenses() {
        val uid = currentUserId ?: return

        lifecycleScope.launch {
            try {
                combine(
                    FirebaseRepository.observeExpenses(uid),
                    FirebaseRepository.observeCategories(uid)
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

    private fun renderRecentExpenses(expenses: List<ExpenseData>, categories: List<CategoryData>) {
        if (expenses.isEmpty()) {
            tvRecentExpenses.gravity = Gravity.CENTER
            tvRecentExpenses.text = "No expenses recorded yet"
            return
        }

        val categoryNamesById = categories.associate { it.categoryId to it.name }

        val lines = expenses
            .sortedByDescending { it.expenseDate }
            .take(RECENT_EXPENSE_LIMIT)
            .map { expense ->
                val categoryName = expense.categoryId
                    ?.let { categoryNamesById[it] }
                    ?: "Uncategorised"

                val dateText = DATE_FORMAT.format(Date(expense.expenseDate))

                "${expense.description}  ·  R%.2f".format(expense.amount) +
                        "\n$categoryName  ·  $dateText"
            }

        tvRecentExpenses.gravity = Gravity.START
        tvRecentExpenses.text = lines.joinToString("\n\n")
    }

    private fun configureButtons() {
        val btnAddExpense = findViewById<MaterialButton>(R.id.btnAddExpense)
        val btnExpenseHistory = findViewById<MaterialButton>(R.id.btnExpenseHistory)
        val btnCategories = findViewById<MaterialButton>(R.id.btnCategories)
        val btnMonthlyGoals = findViewById<MaterialButton>(R.id.btnMonthlyGoals)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)

        btnAddExpense.setOnClickListener {
            startActivity(Intent(this, AddExpensesActivity::class.java))
        }

        btnExpenseHistory.setOnClickListener {
            startActivity(Intent(this, ExpenseHistoryActivity::class.java))
        }

        btnCategories.setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        btnMonthlyGoals.setOnClickListener {
            startActivity(Intent(this, MonthlyGoalsActivity::class.java))
        }

        btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        FirebaseRepository.logout()
        Log.d(TAG, "User logged out")
        returnToLogin()
    }

    private fun returnToLogin() {
        val intent = Intent(this, MainActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

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