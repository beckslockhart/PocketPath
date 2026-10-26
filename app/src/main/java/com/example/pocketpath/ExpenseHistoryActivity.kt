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
import com.example.pocketpath.data.firebase.FirebaseRepository
import com.example.pocketpath.data.firebase.FirebaseRepository.CategoryData
import com.example.pocketpath.data.firebase.FirebaseRepository.ExpenseData
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

class ExpenseHistoryActivity : AppCompatActivity() {

    private lateinit var expenseAdapter: ExpenseAdapter

    private lateinit var fromDateLayout: TextInputLayout
    private lateinit var toDateLayout: TextInputLayout
    private lateinit var etFromDate: TextInputEditText
    private lateinit var etToDate: TextInputEditText
    private lateinit var tvPeriodTotal: TextView
    private lateinit var categoryTotalsContainer: LinearLayout
    private lateinit var recyclerExpenses: RecyclerView
    private lateinit var tvEmptyState: TextView

    private var currentUserId: String? = null

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

        currentUserId = FirebaseRepository.currentUserId

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
        val (monthStart, monthEnd) = currentMonthRange()

        firstDayMillis = savedInstanceState?.getLong(STATE_FIRST_DAY)
            ?.takeIf { it > 0L }
            ?: monthStart

        lastDayMillis = savedInstanceState?.getLong(STATE_LAST_DAY)
            ?.takeIf { it > 0L }
            ?: monthEnd

        updatePeriodText()
    }

    private fun configureList() {
        expenseAdapter = ExpenseAdapter(
            imageScope = lifecycleScope,
            onViewPhoto = ::openPhoto,
            onDelete = ::confirmDeleteExpense
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
            val (monthStart, monthEnd) = currentMonthRange()
            firstDayMillis = monthStart
            lastDayMillis = monthEnd
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

        if (proposedLastDay < proposedFirstDay) {
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
        val uid = currentUserId ?: return

        val periodStart = startOfDay(firstDayMillis)
        val periodEnd = endOfDay(lastDayMillis)

        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            try {
                combine(
                    FirebaseRepository.observeExpenses(uid),
                    FirebaseRepository.observeCategories(uid),
                    FirebaseRepository.observeCategoryTotalsForPeriod(uid, periodStart, periodEnd)
                ) { expenses, categories, totals ->
                    Triple(expenses, categories, totals)
                }.collectLatest { (expenses, categories, totals) ->
                    val expensesInPeriod = expenses
                        .filter { it.expenseDate in periodStart..periodEnd }
                        .sortedByDescending { it.expenseDate }

                    renderExpenses(expensesInPeriod, categories)
                    renderCategoryTotals(totals, categories)
                }
            } catch (cancellation: CancellationException) {
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

    private fun renderCategoryTotals(totals: Map<String, Double>, categories: List<CategoryData>) {
        val categoryNamesById = categories.associate { it.categoryId to it.name }
        val periodTotal = totals.values.sum()

        tvPeriodTotal.text = "R%.2f".format(periodTotal)
        categoryTotalsContainer.removeAllViews()

        if (totals.isEmpty()) {
            val emptyText = TextView(this).apply {
                setText(R.string.no_category_totals)
                setTextColor(SECONDARY_TEXT_COLOR)
                textSize = 13f
            }
            categoryTotalsContainer.addView(emptyText)
            return
        }

        val inflater = LayoutInflater.from(this)

        totals.entries
            .sortedByDescending { it.value }
            .forEach { (categoryId, amount) ->
                val rowView = inflater.inflate(
                    R.layout.item_category_total, categoryTotalsContainer, false
                )

                val share = if (periodTotal > 0.0) (amount / periodTotal) * 100 else 0.0

                rowView.findViewById<TextView>(R.id.tvTotalCategoryName).text =
                    categoryNamesById[categoryId] ?: getString(R.string.uncategorised)

                rowView.findViewById<TextView>(R.id.tvTotalCategoryAmount).text =
                    "R%.2f (%.0f%%)".format(amount, share)

                categoryTotalsContainer.addView(rowView)
            }
    }

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

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun currentMonthRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return start to end
    }

    companion object {
        private const val TAG = "PocketPathHistory"
        private const val STATE_FIRST_DAY = "selected_first_day"
        private const val STATE_LAST_DAY = "selected_last_day"
        private const val SECONDARY_TEXT_COLOR = 0xFF666666.toInt()

        private val DATE_FORMAT = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
}