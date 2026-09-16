package com.example.pocketpath

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.Category
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/** Lets the user create, edit and remove budget categories and monthly limits. */
class CategoriesActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase
    private lateinit var categoriesContainer: LinearLayout
    private var currentUserId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = PocketPathDatabase.getDatabase(this)
        currentUserId = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1L)

        categoriesContainer = findViewById(R.id.categoriesContainer)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnAddCategory).setOnClickListener {
            showCategoryDialog(null)
        }

        observeCategories()
    }

    /** Combines the user's categories with this month's spending per category, live. */
    private fun observeCategories() {
        val (startOfMonth, endOfMonth) = monthRange()
        lifecycleScope.launch {
            try {
                combine(
                    database.categoryDao().getCategoriesForUser(currentUserId),
                    database.expenseDao().getCategoryTotalsForPeriod(
                        currentUserId, startOfMonth, endOfMonth
                    )
                ) { categories, totals ->
                    val totalsByName = totals.associateBy { it.categoryName }
                    categories.map { category ->
                        category to (totalsByName[category.name]?.totalAmount ?: 0.0)
                    }
                }.collectLatest { pairs ->
                    renderCategories(pairs)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load categories", exception)
                Toast.makeText(
                    this@CategoriesActivity,
                    "Unable to load categories",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Rebuilds the on-screen list of category cards to match the latest data. */
    private fun renderCategories(pairs: List<Pair<Category, Double>>) {
        categoriesContainer.removeAllViews()
        if (pairs.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No categories yet. Add your first one below."
                setTextColor("#666666".toColorInt())
                textSize = 14f
            }
            categoriesContainer.addView(emptyText)
            return
        }

        val inflater = LayoutInflater.from(this)
        pairs.forEach { (category, spent) ->
            val cardView = inflater.inflate(
                R.layout.item_category, categoriesContainer, false
            )
            bindCategoryCard(cardView, category, spent)
            categoriesContainer.addView(cardView)
        }
    }

    /** Fills in one category card's values and sets its progress bar colour based on spend ratio. */
    private fun bindCategoryCard(cardView: View, category: Category, spent: Double) {
        val tvName = cardView.findViewById<TextView>(R.id.tvCategoryName)
        val tvLimit = cardView.findViewById<TextView>(R.id.tvLimit)
        val tvSpentRemaining = cardView.findViewById<TextView>(R.id.tvSpentRemaining)
        val progress = cardView.findViewById<LinearProgressIndicator>(R.id.progressCategory)
        val btnDelete = cardView.findViewById<MaterialButton>(R.id.btnDeleteCategory)

        val remaining = category.monthlyLimit - spent
        val percentSpent = if (category.monthlyLimit > 0) {
            ((spent / category.monthlyLimit) * 100).toInt().coerceAtMost(100)
        } else {
            0
        }

        tvName.text = category.name
        tvLimit.text = "Limit: R%.2f".format(category.monthlyLimit)
        tvSpentRemaining.text = "R%.2f spent · R%.2f remaining".format(spent, remaining)
        progress.progress = percentSpent

        val ratio = if (category.monthlyLimit > 0) spent / category.monthlyLimit else 0.0
        val colorHex = when {
            ratio >= 1.0 -> "#D32F2F"
            ratio >= 0.8 -> "#1976D2"
            else -> "#388E3C"
        }
        progress.setIndicatorColor(colorHex.toColorInt())

        cardView.setOnClickListener { showCategoryDialog(category) }
        btnDelete.setOnClickListener { confirmDelete(category) }
    }

    /** Opens the add/edit popup. Pass null to add a new category, or an existing one to edit it. */
    private fun showCategoryDialog(existing: Category?) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_category, null)
        val nameLayout = dialogView.findViewById<TextInputLayout>(R.id.nameLayout)
        val limitLayout = dialogView.findViewById<TextInputLayout>(R.id.limitLayout)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etCategoryName)
        val etLimit = dialogView.findViewById<TextInputEditText>(R.id.etCategoryLimit)

        if (existing != null) {
            etName.setText(existing.name)
            etLimit.setText(existing.monthlyLimit.toString())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add category" else "Edit category")
            .setView(dialogView)
            .setPositiveButton(if (existing == null) "Add" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                nameLayout.error = null
                limitLayout.error = null

                val name = etName.text?.toString()?.trim().orEmpty()
                val limit = etLimit.text?.toString()?.trim()?.toDoubleOrNull()
                var isValid = true

                if (name.isBlank()) {
                    nameLayout.error = "Category name is required"
                    isValid = false
                }
                if (limit == null || !limit.isFinite() || limit <= 0.0) {
                    limitLayout.error = "Enter a limit greater than zero"
                    isValid = false
                }
                if (!isValid || limit == null) return@setOnClickListener

                lifecycleScope.launch {
                    val duplicateCount = database.categoryDao()
                        .countCategoriesWithName(currentUserId, name)
                    val isDuplicate = if (existing != null) {
                        duplicateCount > 0 && !existing.name.equals(name, ignoreCase = true)
                    } else {
                        duplicateCount > 0
                    }
                    if (isDuplicate) {
                        nameLayout.error = "You already have a category with this name"
                        return@launch
                    }

                    try {
                        if (existing == null) {
                            database.categoryDao().insertCategory(
                                Category(
                                    userId = currentUserId,
                                    name = name,
                                    monthlyLimit = limit
                                )
                            )
                        } else {
                            database.categoryDao().updateCategory(
                                Category(
                                    categoryId = existing.categoryId,
                                    userId = currentUserId,
                                    name = name,
                                    monthlyLimit = limit
                                )
                            )
                        }
                        dialog.dismiss()
                    } catch (exception: Exception) {
                        Log.e(TAG, "Unable to save category", exception)
                        Toast.makeText(
                            this@CategoriesActivity,
                            "Unable to save category. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Delete category?")
            .setMessage("Delete \"${category.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        database.categoryDao().deleteCategory(category)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Unable to delete category", exception)
                        Toast.makeText(
                            this@CategoriesActivity,
                            "Unable to delete category",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    companion object {
        private const val TAG = "PocketPathCategories"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
    }
}