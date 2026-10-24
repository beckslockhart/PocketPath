package com.example.pocketpath

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.MonthlyGoal
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Allows the user to enter and update monthly minimum and maximum
 * spending goals using numerical amount fields.
 */
class MonthlyGoalsActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase

    private lateinit var minimumLayout: TextInputLayout
    private lateinit var maximumLayout: TextInputLayout

    private lateinit var etMinimum: TextInputEditText
    private lateinit var etMaximum: TextInputEditText

    private var currentUserId = -1L

    companion object {
        private const val TAG = "PocketPathMonthlyGoals"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_goals)

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
                "Monthly goals opened without a logged-in user"
            )

            finish()
            return
        }

        connectViews()
        configureButtons()
        observeExistingGoal()
    }

    /**
     * Connects the Kotlin variables to the XML controls.
     */
    private fun connectViews() {
        minimumLayout = findViewById(R.id.minimumLayout)
        maximumLayout = findViewById(R.id.maximumLayout)

        etMinimum = findViewById(R.id.etMinimum)
        etMaximum = findViewById(R.id.etMaximum)
    }

    /**
     * Configures the back and save buttons.
     */
    private fun configureButtons() {
        findViewById<MaterialButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<MaterialButton>(R.id.btnSaveGoals)
            .setOnClickListener {
                saveGoals()
            }
    }

    /**
     * Loads and displays an existing monthly goal.
     */
    private fun observeExistingGoal() {
        lifecycleScope.launch {
            try {
                database.monthlyGoalDao()
                    .getMonthlyGoal(currentUserId)
                    .collectLatest { goal ->
                        if (goal != null) {
                            etMinimum.setText(
                                formatAmount(
                                    goal.minimumAmount
                                )
                            )

                            etMaximum.setText(
                                formatAmount(
                                    goal.maximumAmount
                                )
                            )

                            Log.d(
                                TAG,
                                "Existing monthly goal loaded"
                            )
                        }
                    }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to load monthly goal",
                    exception
                )

                Toast.makeText(
                    this@MonthlyGoalsActivity,
                    "Unable to load your monthly goals",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Validates and saves the numerical monthly goal values.
     */
    private fun saveGoals() {
        clearErrors()

        val minimum = etMinimum.text
            ?.toString()
            ?.trim()
            ?.toDoubleOrNull()

        val maximum = etMaximum.text
            ?.toString()
            ?.trim()
            ?.toDoubleOrNull()

        var isValid = true

        if (
            minimum == null ||
            !minimum.isFinite() ||
            minimum <= 0.0
        ) {
            minimumLayout.error =
                "Enter a minimum amount greater than zero"

            isValid = false
        }

        if (
            maximum == null ||
            !maximum.isFinite() ||
            maximum <= 0.0
        ) {
            maximumLayout.error =
                "Enter a maximum amount greater than zero"

            isValid = false
        }

        if (
            minimum != null &&
            maximum != null &&
            minimum.isFinite() &&
            maximum.isFinite() &&
            minimum > maximum
        ) {
            maximumLayout.error =
                "Maximum cannot be lower than minimum"

            isValid = false
        }

        if (
            !isValid ||
            minimum == null ||
            maximum == null
        ) {
            return
        }

        lifecycleScope.launch {
            try {
                database.monthlyGoalDao().saveMonthlyGoal(
                    MonthlyGoal(
                        userId = currentUserId,
                        minimumAmount = minimum,
                        maximumAmount = maximum
                    )
                )

                Log.d(
                    TAG,
                    "Monthly goal saved successfully"
                )

                Toast.makeText(
                    this@MonthlyGoalsActivity,
                    "Monthly goals saved",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to save monthly goal",
                    exception
                )

                Toast.makeText(
                    this@MonthlyGoalsActivity,
                    "Unable to save your monthly goals",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Removes unnecessary decimal places when displaying saved values.
     */
    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
    }

    /**
     * Clears validation errors before checking the fields again.
     */
    private fun clearErrors() {
        minimumLayout.error = null
        maximumLayout.error = null
    }
}