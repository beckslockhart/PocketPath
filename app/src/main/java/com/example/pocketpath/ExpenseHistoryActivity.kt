package com.example.pocketpath

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExpenseHistoryActivity : AppCompatActivity() {
    private lateinit var database: PocketPathDatabase
    private lateinit var expenseAdapter: ExpenseAdapter

    private lateinit var fromDateLayout: TextInputLayout
    private lateinit var toDateLayout: TextInputLayout
    private lateinit var etFromDate: TextInputEditText
    private lateinit var etToDate: TextInputEditText
    private lateinit var tvPeriodTotal: TextView
    private lateinit var categoryTotalsContainer: LinearLayout
    private lateinit var recyclerExpenses: RecyclerView
    private lateinit var tvEmptyState: TextView

    private var currentUserId = -1L

    private var firstDayMillis = 0L
    private var lastDayMillis = 0L

    private var observeJob: Job? = null

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

    private fun configureList() {
        expenseAdapter = ExpenseAdapter(
            imageScope = lifecycleScope,
            onViewPhoto = ::openPhoto
        )

        recyclerExpenses.layoutManager = LinearLayoutManager(this)
        recyclerExpenses.adapter = expenseAdapter
    }

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

    private fun applyChosenDate(chosenMillis: Long, isFromDate: Boolean) {
        val proposedFirstDay = if (isFromDate) chosenMillis else firstDayMillis
        val proposedLastDay = if (isFromDate) lastDayMillis else chosenMillis

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

    private fun updatePeriodText() {
        etFromDate.setText(DATE_FORMAT.format(Date(firstDayMillis)))
        etToDate.setText(DATE_FORMAT.format(Date(lastDayMillis)))
    }

    private fun observeSelectedPeriod() {
        val period: Period = PeriodFilter.periodBetween(firstDayMillis, lastDayMillis)

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

    private fun renderCategoryTotals(totals: List<CategorySpendingTotal>) {
        val summary = CategoryTotals.summarise(totals)

        tvPeriodTotal.text = Money.format(summary.periodTotal)
        categoryTotalsContainer.removeAllViews()

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

    private fun returnToLogin() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_FIRST_DAY, firstDayMillis)
        outState.putLong(STATE_LAST_DAY, lastDayMillis)
    }

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
