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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pocketpath.data.dao.CategorySpendingTotal
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.Category
import com.example.pocketpath.data.entity.Expense
import com.example.pocketpath.util.CategoryTotals
import com.example.pocketpath.util.Money
import com.example.pocketpath.util.Period
import com.example.pocketpath.util.PeriodFilter
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

// shows the expenses for a chosen period and the totals per category
class ExpenseHistoryActivity : AppCompatActivity() {
    // the room database used to read expenses and categories
    private lateinit var database: PocketPathDatabase
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

    // id of the user who is logged in
    private var currentUserId = -1L

    // the two days the user picked for the period
    private var firstDayMillis = 0L
    private var lastDayMillis = 0L

    // watches the database for the chosen period
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

        database = PocketPathDatabase.getDatabase(this)
        currentUserId = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1L)

        // no user is logged in so go back to the login screen
        if (currentUserId == -1L) {
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
    }

    // starts on this month unless a period was saved before
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

    // uses the picked date if the period still makes sense
    private fun applyChosenDate(chosenMillis: Long, isFromDate: Boolean) {
        val proposedFirstDay = if (isFromDate) chosenMillis else firstDayMillis
        val proposedLastDay = if (isFromDate) lastDayMillis else chosenMillis

        // the end is before the start so show an error and stop
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

    // watches the expenses and the totals for the chosen period
    private fun observeSelectedPeriod() {
        val period: Period = PeriodFilter.periodBetween(firstDayMillis, lastDayMillis)

        // stop watching the period that was chosen before
        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            try {
                combine(
                    database.expenseDao().getExpensesForPeriod(
                        currentUserId, period.startMillis, period.endMillis
                    ),
                    database.categoryDao().getCategoriesForUser(currentUserId),
                    database.expenseDao().getCategoryTotalsForPeriod(
                        currentUserId, period.startMillis, period.endMillis
                    )
                ) { expenses, categories, totals ->
                    Triple(expenses, categories, totals)
                }.collectLatest { (expenses, categories, totals) ->
                    renderExpenses(expenses, categories)
                    renderCategoryTotals(totals)
                }
            // changing the period cancels the old watch which is normal
            } catch (cancellation: CancellationException) {
                throw cancellation
            // something really went wrong so tell the user
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

    // builds the rows and shows a message when nothing matches
    private fun renderExpenses(expenses: List<Expense>, categories: List<Category>) {
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

    // shows how much was spent in each category
    private fun renderCategoryTotals(totals: List<CategorySpendingTotal>) {
        val summary = CategoryTotals.summarise(totals)

        tvPeriodTotal.text = Money.format(summary.periodTotal)
        categoryTotalsContainer.removeAllViews()

        // nothing was spent so show a short message instead of rows
        if (summary.isEmpty) {
            val emptyText = TextView(this).apply {
                setText(R.string.no_category_totals)
                setTextColor(SECONDARY_TEXT_COLOR)
                textSize = 13f
            }
            categoryTotalsContainer.addView(emptyText)
            return
        }

        val inflater = LayoutInflater.from(this)
        summary.rows.forEach { row ->
            val rowView = inflater.inflate(
                R.layout.item_category_total, categoryTotalsContainer, false
            )

            rowView.findViewById<TextView>(R.id.tvTotalCategoryName).text = row.categoryName
            rowView.findViewById<TextView>(R.id.tvTotalCategoryAmount).text =
                Money.formatWithShare(row.totalAmount, summary.periodTotal)

            categoryTotalsContainer.addView(rowView)
        }
    }

    // opens the photo that belongs to this expense
    private fun openPhoto(item: ExpenseListItem) {
        val photoUri = item.expense.photoUri ?: return

        startActivity(
            Intent(this, PhotoViewerActivity::class.java).apply {
                putExtra(PhotoViewerActivity.EXTRA_PHOTO_URI, photoUri)
                putExtra(PhotoViewerActivity.EXTRA_DESCRIPTION, item.expense.description)
                putExtra(PhotoViewerActivity.EXTRA_EXPENSE_DATE, item.expense.expenseDate)
            }
        )
    }

    // asks the user before deleting an expense
    private fun confirmDeleteExpense(item: ExpenseListItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete expense?")
            .setMessage("Delete \"${item.expense.description}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        database.expenseDao().deleteExpense(item.expense)
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
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
        private const val STATE_FIRST_DAY = "selected_first_day"
        private const val STATE_LAST_DAY = "selected_last_day"
        private const val SECONDARY_TEXT_COLOR = 0xFF666666.toInt()

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}