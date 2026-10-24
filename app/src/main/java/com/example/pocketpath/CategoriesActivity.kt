package com.example.pocketpath

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.Category
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Allows users to create, edit and delete budget categories
 * and monitor their spending against monthly category limits.
 */
class CategoriesActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase
    private lateinit var categoriesContainer: LinearLayout

    private var currentUserId = -1L

    companion object {
        private const val TAG = "PocketPathCategories"
        private const val PREFERENCES_NAME =
            "pocketpath_preferences"
        private const val KEY_USER_ID =
            "logged_in_user_id"

        private const val COLOR_TEAL = "#176B66"
        private const val COLOR_GOLD = "#C28B2C"
        private const val COLOR_RED = "#C43D3D"
        private const val COLOR_SECONDARY_TEXT = "#607372"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        database = PocketPathDatabase.getDatabase(this)

        currentUserId = getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        ).getLong(KEY_USER_ID, -1L)

        if (currentUserId == -1L) {
            Log.w(
                TAG,
                "Categories opened without a logged-in user"
            )

            finish()
            return
        }

        categoriesContainer =
            findViewById(R.id.categoriesContainer)

        configureButtons()
        observeCategories()
    }

    /**
     * Configures navigation and category creation controls.
     */
    private fun configureButtons() {
        findViewById<MaterialButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<MaterialButton>(R.id.btnAddCategory)
            .setOnClickListener {
                showCategoryDialog(null)
            }
    }

    /**
     * Combines the user's categories with the amount spent in
     * each category during the current month.
     */
    private fun observeCategories() {
        val (startOfMonth, endOfMonth) = monthRange()

        lifecycleScope.launch {
            try {
                combine(
                    database.categoryDao()
                        .getCategoriesForUser(currentUserId),

                    database.expenseDao()
                        .getCategoryTotalsForPeriod(
                            currentUserId,
                            startOfMonth,
                            endOfMonth
                        )
                ) { categories, totals ->
                    val totalsByName =
                        totals.associateBy {
                            it.categoryName
                        }

                    categories.map { category ->
                        category to (
                                totalsByName[category.name]
                                    ?.totalAmount ?: 0.0
                                )
                    }
                }.collectLatest { categoryPairs ->
                    renderCategories(categoryPairs)
                }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to load categories",
                    exception
                )

                Toast.makeText(
                    this@CategoriesActivity,
                    "Unable to load categories",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Rebuilds the category list whenever category or spending
     * information changes.
     */
    private fun renderCategories(
        categoryPairs: List<Pair<Category, Double>>
    ) {
        categoriesContainer.removeAllViews()

        if (categoryPairs.isEmpty()) {
            val emptyText = TextView(this).apply {
                text =
                    "No categories yet. Add your first one below."

                setTextColor(
                    COLOR_SECONDARY_TEXT.toColorInt()
                )

                textSize = 14f

                setPadding(
                    0,
                    dpToPx(4),
                    0,
                    dpToPx(4)
                )
            }

            categoriesContainer.addView(emptyText)
            return
        }

        val inflater = LayoutInflater.from(this)

        categoryPairs.forEach { (category, spent) ->
            val cardView = inflater.inflate(
                R.layout.item_category,
                categoriesContainer,
                false
            )

            bindCategoryCard(
                cardView,
                category,
                spent
            )

            categoriesContainer.addView(cardView)
        }
    }

    /**
     * Displays one category and chooses its progress colour:
     * teal when safe, gold near the limit and red over budget.
     */
    private fun bindCategoryCard(
        cardView: View,
        category: Category,
        spent: Double
    ) {
        val tvName =
            cardView.findViewById<TextView>(
                R.id.tvCategoryName
            )

        val tvLimit =
            cardView.findViewById<TextView>(
                R.id.tvLimit
            )

        val tvSpentRemaining =
            cardView.findViewById<TextView>(
                R.id.tvSpentRemaining
            )

        val progress =
            cardView.findViewById<LinearProgressIndicator>(
                R.id.progressCategory
            )

        val btnDelete =
            cardView.findViewById<MaterialButton>(
                R.id.btnDeleteCategory
            )

        val remaining =
            category.monthlyLimit - spent

        val ratio =
            if (category.monthlyLimit > 0.0) {
                spent / category.monthlyLimit
            } else {
                0.0
            }

        val progressPercentage =
            (ratio * 100)
                .toInt()
                .coerceIn(0, 100)

        tvName.text = category.name

        tvLimit.text =
            "Monthly limit: R%.2f".format(
                category.monthlyLimit
            )

        tvSpentRemaining.text =
            if (remaining >= 0.0) {
                "R%.2f spent · R%.2f remaining".format(
                    spent,
                    remaining
                )
            } else {
                "R%.2f spent · R%.2f over limit".format(
                    spent,
                    -remaining
                )
            }

        progress.progress = progressPercentage

        val progressColour =
            when {
                ratio >= 1.0 ->
                    COLOR_RED

                ratio >= 0.8 ->
                    COLOR_GOLD

                else ->
                    COLOR_TEAL
            }

        progress.setIndicatorColor(
            progressColour.toColorInt()
        )

        cardView.setOnClickListener {
            showCategoryDialog(category)
        }

        btnDelete.setOnClickListener {
            confirmDelete(category)
        }
    }

    /**
     * Opens the category form. A null category creates a new
     * record, while an existing category is edited.
     */
    private fun showCategoryDialog(
        existingCategory: Category?
    ) {
        val dialogView =
            LayoutInflater.from(this).inflate(
                R.layout.dialog_add_category,
                null
            )

        val nameLayout =
            dialogView.findViewById<TextInputLayout>(
                R.id.nameLayout
            )

        val limitLayout =
            dialogView.findViewById<TextInputLayout>(
                R.id.limitLayout
            )

        val etName =
            dialogView.findViewById<TextInputEditText>(
                R.id.etCategoryName
            )

        val etLimit =
            dialogView.findViewById<TextInputEditText>(
                R.id.etCategoryLimit
            )

        if (existingCategory != null) {
            etName.setText(existingCategory.name)

            etLimit.setText(
                formatAmount(
                    existingCategory.monthlyLimit
                )
            )
        }

        val isEditing =
            existingCategory != null

        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    if (isEditing) {
                        "Edit category"
                    } else {
                        "Add category"
                    }
                )
                .setView(dialogView)
                .setPositiveButton(
                    if (isEditing) {
                        "Save"
                    } else {
                        "Add"
                    },
                    null
                )
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setTextColor(
                COLOR_TEAL.toColorInt()
            )

            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            ).setTextColor(
                COLOR_SECONDARY_TEXT.toColorInt()
            )

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                validateAndSaveCategory(
                    dialog,
                    existingCategory,
                    nameLayout,
                    limitLayout,
                    etName,
                    etLimit
                )
            }
        }

        dialog.show()
    }

    /**
     * Validates the category form and writes valid information
     * to the database.
     */
    private fun validateAndSaveCategory(
        dialog: AlertDialog,
        existingCategory: Category?,
        nameLayout: TextInputLayout,
        limitLayout: TextInputLayout,
        etName: TextInputEditText,
        etLimit: TextInputEditText
    ) {
        nameLayout.error = null
        limitLayout.error = null

        val name =
            etName.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val limit =
            etLimit.text
                ?.toString()
                ?.trim()
                ?.toDoubleOrNull()

        var isValid = true

        if (name.isBlank()) {
            nameLayout.error =
                "Category name is required"

            isValid = false
        }

        if (
            limit == null ||
            !limit.isFinite() ||
            limit <= 0.0
        ) {
            limitLayout.error =
                "Enter a limit greater than zero"

            isValid = false
        }

        if (!isValid || limit == null) {
            return
        }

        lifecycleScope.launch {
            try {
                val duplicateCount =
                    database.categoryDao()
                        .countCategoriesWithName(
                            currentUserId,
                            name
                        )

                val isDuplicate =
                    if (existingCategory != null) {
                        duplicateCount > 0 &&
                                !existingCategory.name.equals(
                                    name,
                                    ignoreCase = true
                                )
                    } else {
                        duplicateCount > 0
                    }

                if (isDuplicate) {
                    nameLayout.error =
                        "You already have a category with this name"

                    return@launch
                }

                if (existingCategory == null) {
                    database.categoryDao()
                        .insertCategory(
                            Category(
                                userId = currentUserId,
                                name = name,
                                monthlyLimit = limit
                            )
                        )

                    Log.d(
                        TAG,
                        "Category created successfully"
                    )
                } else {
                    database.categoryDao()
                        .updateCategory(
                            Category(
                                categoryId =
                                    existingCategory.categoryId,
                                userId = currentUserId,
                                name = name,
                                monthlyLimit = limit
                            )
                        )

                    Log.d(
                        TAG,
                        "Category updated successfully"
                    )
                }

                dialog.dismiss()
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to save category",
                    exception
                )

                Toast.makeText(
                    this@CategoriesActivity,
                    "Unable to save category. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Requests confirmation before permanently deleting a category.
     */
    private fun confirmDelete(category: Category) {
        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete category?")
                .setMessage(
                    "Delete \"${category.name}\"? " +
                            "This cannot be undone."
                )
                .setPositiveButton(
                    "Delete"
                ) { _, _ ->
                    deleteCategory(category)
                }
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setTextColor(
                COLOR_RED.toColorInt()
            )

            dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
            ).setTextColor(
                COLOR_TEAL.toColorInt()
            )
        }

        dialog.show()
    }

    /**
     * Deletes the selected category from the database.
     */
    private fun deleteCategory(category: Category) {
        lifecycleScope.launch {
            try {
                database.categoryDao()
                    .deleteCategory(category)

                Log.d(
                    TAG,
                    "Category deleted successfully"
                )

                Toast.makeText(
                    this@CategoriesActivity,
                    "Category deleted",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to delete category",
                    exception
                )

                Toast.makeText(
                    this@CategoriesActivity,
                    "Unable to delete category",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Calculates the first and last millisecond of the current month.
     */
    private fun monthRange(): Pair<Long, Long> {
        val start =
            Calendar.getInstance().apply {
                set(
                    Calendar.DAY_OF_MONTH,
                    1
                )

                set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                set(
                    Calendar.MINUTE,
                    0
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )
            }.timeInMillis

        val end =
            Calendar.getInstance().apply {
                set(
                    Calendar.DAY_OF_MONTH,
                    getActualMaximum(
                        Calendar.DAY_OF_MONTH
                    )
                )

                set(
                    Calendar.HOUR_OF_DAY,
                    23
                )

                set(
                    Calendar.MINUTE,
                    59
                )

                set(
                    Calendar.SECOND,
                    59
                )

                set(
                    Calendar.MILLISECOND,
                    999
                )
            }.timeInMillis

        return start to end
    }

    /**
     * Displays whole-number limits without unnecessary decimal places.
     */
    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
    }

    /**
     * Converts density-independent pixels into device pixels.
     */
    private fun dpToPx(dp: Int): Int {
        return (
                dp * resources.displayMetrics.density
                ).toInt()
    }
}