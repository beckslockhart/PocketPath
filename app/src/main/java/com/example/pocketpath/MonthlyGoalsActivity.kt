package com.example.pocketpath

import android.os.Bundle
import android.util.Log
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

/** Lets the user set and update their monthly minimum and maximum spending goals. */
class MonthlyGoalsActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase
    private lateinit var minimumLayout: TextInputLayout
    private lateinit var maximumLayout: TextInputLayout
    private lateinit var etMinimum: TextInputEditText
    private lateinit var etMaximum: TextInputEditText
    private var currentUserId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_goals)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = PocketPathDatabase.getDatabase(this)
        currentUserId = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1L)

        minimumLayout = findViewById(R.id.minimumLayout)
        maximumLayout = findViewById(R.id.maximumLayout)
        etMinimum = findViewById(R.id.etMinimum)
        etMaximum = findViewById(R.id.etMaximum)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSaveGoals).setOnClickListener { saveGoals() }

        observeExistingGoal()
    }

    private fun observeExistingGoal() {
        lifecycleScope.launch {
            try {
                database.monthlyGoalDao().getMonthlyGoal(currentUserId).collectLatest { goal ->
                    if (goal != null) {
                        etMinimum.setText(goal.minimumAmount.toString())
                        etMaximum.setText(goal.maximumAmount.toString())
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load monthly goal", exception)
            }
        }
    }

    private fun saveGoals() {
        minimumLayout.error = null
        maximumLayout.error = null

        val minimum = etMinimum.text?.toString()?.trim()?.toDoubleOrNull()
        val maximum = etMaximum.text?.toString()?.trim()?.toDoubleOrNull()
        var isValid = true

        if (minimum == null || !minimum.isFinite() || minimum < 0.0) {
            minimumLayout.error = "Enter a valid minimum amount"
            isValid = false
        }
        if (maximum == null || !maximum.isFinite() || maximum <= 0.0) {
            maximumLayout.error = "Enter a valid maximum amount"
            isValid = false
        }
        if (isValid && minimum != null && maximum != null && minimum > maximum) {
            minimumLayout.error = "Minimum cannot be greater than maximum"
            isValid = false
        }
        if (!isValid || minimum == null || maximum == null) return

        lifecycleScope.launch {
            try {
                database.monthlyGoalDao().saveMonthlyGoal(
                    MonthlyGoal(
                        userId = currentUserId,
                        minimumAmount = minimum,
                        maximumAmount = maximum
                    )
                )
                finish()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to save monthly goal", exception)
            }
        }
    }

    companion object {
        private const val TAG = "PocketPathMonthlyGoals"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
    }
}