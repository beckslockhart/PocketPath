package com.example.pocketpath

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pocketpath.data.firebase.FirebaseRepository
import com.example.pocketpath.data.firebase.FirebaseRepository.CategoryData
import com.example.pocketpath.data.firebase.FirebaseRepository.ExpenseData
import com.example.pocketpath.data.firebase.FirebaseRepository.GoalData
import com.example.pocketpath.util.CategoryName
import com.example.pocketpath.util.CategoryTotals
import com.example.pocketpath.util.CategoryTotalsSummary
import com.example.pocketpath.util.ExpenseAmount
import com.example.pocketpath.util.GoalBand
import com.example.pocketpath.util.GoalStatus
import com.example.pocketpath.util.Money
import com.example.pocketpath.util.Period
import com.example.pocketpath.util.PeriodFilter
import com.example.pocketpath.view.CategoryBarChartView
import com.example.pocketpath.view.GoalGaugeView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// shows the expenses for a chosen period with a spending per category graph
// a written breakdown and a summary of the past month against the goals
class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var expenseAdapter: ExpenseAdapter

    // the views on this screen
    private lateinit var fromDateLayout: TextInputLayout
    private lateinit var toDateLayout: TextInputLayout
    private lateinit var etFromDate: TextInputEditText
    private lateinit var etToDate: TextInputEditText
    private lateinit var tvPeriodTotal: TextView
    private lateinit var categoryTotalsContainer: LinearLayout
    private lateinit var recyclerExpenses: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var chartCategorySpending: CategoryBarChartView
    private lateinit var tvGraphEmpty: TextView
    private lateinit var tvGoalStatus: TextView
    private lateinit var goalGauge: GoalGaugeView
    private lateinit var tvGoalSpent: TextView

    private var currentUserId: String? = null

    // the two days the user picked for the period
    private var firstDayMillis = 0L
    private var lastDayMillis = 0L

    // watches firebase for the chosen period
    private var observeJob: Job? = null

    // sets the screen up when it opens
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_history)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = FirebaseRepository.currentUserId

        // nobody is signed in so go back to the login screen
        if (currentUserId == null) {
            returnToLogin()
            return
        }

        connectViews()
        restoreSelectedPeriod(savedInstanceState)
        configureList()
        configureInputs()
        observeSelectedPeriod()
    }

    // links the variables to the views in the layout
    private fun connectViews() {
        fromDateLayout = findViewById(R.id.fromDateLayout)
        toDateLayout = findViewById(R.id.toDateLayout)
        etFromDate = findViewById(R.id.etFromDate)
        etToDate = findViewById(R.id.etToDate)
        tvPeriodTotal = findViewById(R.id.tvPeriodTotal)
        categoryTotalsContainer = findViewById(R.id.categoryTotalsContainer)
        recyclerExpenses = findViewById(R.id.recyclerExpenses)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        chartCategorySpending = findViewById(R.id.chartCategorySpending)
        tvGraphEmpty = findViewById(R.id.tvGraphEmpty)
        tvGoalStatus = findViewById(R.id.tvGoalStatus)
        goalGauge = findViewById(R.id.goalGauge)
        tvGoalSpent = findViewById(R.id.tvGoalSpent)
    }

    // the default period is this month unless one was saved before a rotation
    private fun restoreSelectedPeriod(savedInstanceState: Bundle?) {
        val thisMonth = PeriodFilter.currentMonth()

        firstDayMillis = savedInstanceState?.getLong(STATE_FIRST_DAY)
            ?.takeIf { it > 0L }
            ?: thisMonth.startMillis

        lastDayMillis = savedInstanceState?.getLong(STATE_LAST_DAY)
            ?.takeIf { it > 0L }
            ?: thisMonth.endMillis

        updatePeriodText()
    }

    // sets up the list of expenses
    private fun configureList() {
        expenseAdapter = ExpenseAdapter(
            imageScope = lifecycleScope,
            onViewPhoto = ::openPhoto,
            onDelete = ::confirmDeleteExpense
        )

        recyclerExpenses.layoutManager = LinearLayoutManager(this)
        recyclerExpenses.adapter = expenseAdapter
    }

    // sets up the back button the date fields and the reset button
    private fun configureInputs() {
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        etFromDate.setOnClickListener { openPeriodPicker(isFromDate = true) }
        fromDateLayout.setEndIconOnClickListener { openPeriodPicker(isFromDate = true) }
        etToDate.setOnClickListener { openPeriodPicker(isFromDate = false) }
        toDateLayout.setEndIconOnClickListener { openPeriodPicker(isFromDate = false) }

        findViewById<MaterialButton>(R.id.btnThisMonth).setOnClickListener {
            val thisMonth = PeriodFilter.currentMonth()
            firstDayMillis = thisMonth.startMillis
            lastDayMillis = thisMonth.endMillis
            toDateLayout.error = null
            updatePeriodText()
            observeSelectedPeriod()
        }
    }

    // opens a date picker for the from field or the to field
    private fun openPeriodPicker(isFromDate: Boolean) {
        val current = if (isFromDate) firstDayMillis else lastDayMillis
        val calendar = Calendar.getInstance().apply { timeInMillis = current }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val chosen = Calendar.getInstance().apply {
                    clear()
                    set(year, month, day)
                }.timeInMillis

                applyChosenDate(chosen, isFromDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // uses the picked date and redraws everything for the new period
    private fun applyChosenDate(chosenMillis: Long, isFromDate: Boolean) {
        val proposedFirstDay = if (isFromDate) chosenMillis else firstDayMillis
        val proposedLastDay = if (isFromDate) lastDayMillis else chosenMillis

        // an end before the start can never match so it is reported instead
        if (!PeriodFilter.isValidSelection(proposedFirstDay, proposedLastDay)) {
            toDateLayout.error = getString(R.string.period_end_before_start)
            return
        }

        toDateLayout.error = null
        firstDayMillis = proposedFirstDay
        lastDayMillis = proposedLastDay

        updatePeriodText()
        observeSelectedPeriod()
    }

    // shows the chosen dates in the two fields
    private fun updatePeriodText() {
        etFromDate.setText(DATE_FORMAT.format(Date(firstDayMillis)))
        etToDate.setText(DATE_FORMAT.format(Date(lastDayMillis)))
    }

    // watches the expenses the categories and the goals for the chosen period
    // the totals are worked out here rather than read from firebase so that
    // expenses with no category are kept and categories with no spending are
    // still shown on the graph
    private fun observeSelectedPeriod() {
        val uid = currentUserId ?: return

        val period = PeriodFilter.periodBetween(firstDayMillis, lastDayMillis)

        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            try {
                combine(
                    FirebaseRepository.observeExpenses(uid),
                    FirebaseRepository.observeCategories(uid),
                    FirebaseRepository.observeMonthlyGoal(uid)
                ) { expenses, categories, goal ->
                    Triple(expenses, categories, goal)
                }.collectLatest { (expenses, categories, goal) ->
                    render(expenses, categories, goal, period)
                }
            } catch (cancellation: CancellationException) {
                // changing the period cancels the old watch which is normal
                throw cancellation
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load expense history", exception)
                Toast.makeText(
                    this@ExpenseHistoryActivity,
                    getString(R.string.unable_to_load_expenses),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // draws every part of the screen from one snapshot of the data
    private fun render(
        expenses: List<ExpenseData>,
        categories: List<CategoryData>,
        goal: GoalData?,
        period: Period
    ) {
        val expensesInPeriod = expenses
            .filter { period.contains(it.expenseDate) }
            .sortedByDescending { it.expenseDate }

        val summary = CategoryTotals.summarise(
            expenses = expensesInPeriod.map { ExpenseAmount(it.categoryId, it.amount) },
            categories = categories.map { CategoryName(it.categoryId, it.name) },
            uncategorisedLabel = getString(R.string.uncategorised)
        )

        renderExpenses(expensesInPeriod, categories)
        renderCategoryTotals(summary)
        renderGraph(summary, goal)
        renderGoalSummary(expenses, goal)
    }

    // builds the rows and shows a message when nothing matches
    private fun renderExpenses(expenses: List<ExpenseData>, categories: List<CategoryData>) {
        val categoryNamesById = categories.associate { it.categoryId to it.name }
        val uncategorised = getString(R.string.uncategorised)

        val items = expenses.map { expense ->
            ExpenseListItem(
                expense = expense,
                categoryName = expense.categoryId
                    ?.let { categoryNamesById[it] }
                    ?: uncategorised
            )
        }

        expenseAdapter.submitList(items)

        val isEmpty = items.isEmpty()
        tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerExpenses.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // writes out how much was spent in each category
    private fun renderCategoryTotals(summary: CategoryTotalsSummary) {
        val spending = CategoryTotals.withSpendingOnly(summary)

        tvPeriodTotal.text = Money.format(summary.periodTotal)
        categoryTotalsContainer.removeAllViews()

        // nothing was spent so show a short message instead of empty rows
        if (spending.rows.isEmpty()) {
            val emptyText = TextView(this).apply {
                setText(R.string.no_category_totals)
                setTextColor(SECONDARY_TEXT_COLOR)
                textSize = 13f
            }
            categoryTotalsContainer.addView(emptyText)
            return
        }

        val inflater = LayoutInflater.from(this)

        spending.rows.forEach { row ->
            val rowView = inflater.inflate(
                R.layout.item_category_total, categoryTotalsContainer, false
            )

            rowView.findViewById<TextView>(R.id.tvTotalCategoryName).text = row.categoryName
            rowView.findViewById<TextView>(R.id.tvTotalCategoryAmount).text =
                Money.formatWithShare(row.totalAmount, summary.periodTotal)

            categoryTotalsContainer.addView(rowView)
        }
    }

    // feeds the graph with the category totals and the goal lines
    private fun renderGraph(summary: CategoryTotalsSummary, goal: GoalData?) {
        val hasSpending = !summary.isEmpty

        chartCategorySpending.visibility = if (hasSpending) View.VISIBLE else View.GONE
        tvGraphEmpty.visibility = if (hasSpending) View.GONE else View.VISIBLE

        if (!hasSpending) return

        chartCategorySpending.setData(
            rows = summary.rows,
            minimumGoal = goal?.minimumAmount ?: 0.0,
            maximumGoal = goal?.maximumAmount ?: 0.0
        )
    }

    // sums the past month on its own so the summary always covers the same
    // stretch of time no matter which period is chosen above it
    private fun renderGoalSummary(expenses: List<ExpenseData>, goal: GoalData?) {
        val lastMonth = PeriodFilter.recentDays(GOAL_SUMMARY_DAYS)

        val spent = expenses
            .filter { lastMonth.contains(it.expenseDate) }
            .sumOf { it.amount }

        val progress = GoalStatus.evaluate(
            spent = spent,
            minimum = goal?.minimumAmount ?: 0.0,
            maximum = goal?.maximumAmount ?: 0.0
        )

        goalGauge.setProgress(progress)

        val statusText = when (progress.band) {
            GoalBand.NOT_SET -> R.string.goals_not_set
            GoalBand.BELOW_MINIMUM -> R.string.goal_status_below
            GoalBand.WITHIN_GOALS -> R.string.goal_status_within
            GoalBand.ABOVE_MAXIMUM -> R.string.goal_status_above
        }

        val statusColour = when (progress.band) {
            GoalBand.NOT_SET -> R.color.chart_value
            GoalBand.BELOW_MINIMUM -> R.color.goal_below
            GoalBand.WITHIN_GOALS -> R.color.goal_within
            GoalBand.ABOVE_MAXIMUM -> R.color.goal_above
        }

        tvGoalStatus.setText(statusText)
        tvGoalStatus.setTextColor(ContextCompat.getColor(this, statusColour))

        tvGoalSpent.text = if (progress.band == GoalBand.NOT_SET) {
            Money.format(spent)
        } else {
            getString(
                R.string.goal_spent_of_maximum,
                Money.format(spent),
                Money.format(progress.maximum)
            )
        }
    }

    // opens the photo that belongs to this expense
    private fun openPhoto(item: ExpenseListItem) {
        val photoBase64 = item.expense.photoBase64 ?: return

        startActivity(
            Intent(this, PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_PHOTO_BASE64, photoBase64)
                putExtra(PhotoViewerActivity.EXTRA_DESCRIPTION, item.expense.description)
                putExtra(PhotoViewerActivity.EXTRA_EXPENSE_DATE, item.expense.expenseDate)
            }
        )
    }

    // asks the user before deleting an expense
    private fun confirmDeleteExpense(item: ExpenseListItem) {
        val uid = currentUserId ?: return

        AlertDialog.Builder(this)
            .setTitle("Delete expense?")
            .setMessage("Delete \"${item.expense.description}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        FirebaseRepository.deleteExpense(uid, item.expense.expenseId)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Unable to delete expense", exception)
                        Toast.makeText(
                            this@ExpenseHistoryActivity,
                            "Unable to delete expense",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // sends the user back to the login screen
    private fun returnToLogin() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    // keeps the chosen period when the screen rotates
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_FIRST_DAY, firstDayMillis)
        outState.putLong(STATE_LAST_DAY, lastDayMillis)
    }

    // keys and settings used by this screen
    companion object {
        private const val TAG = "PocketPathHistory"
        private const val STATE_FIRST_DAY = "selected_first_day"
        private const val STATE_LAST_DAY = "selected_last_day"
        private const val SECONDARY_TEXT_COLOR = 0xFF666666.toInt()
        private const val GOAL_SUMMARY_DAYS = 30

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}
