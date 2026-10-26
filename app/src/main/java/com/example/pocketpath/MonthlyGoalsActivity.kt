package com.example.pocketpath

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.firebase.FirebaseRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MonthlyGoalsActivity : AppCompatActivity() {

    private lateinit var minimumLayout: TextInputLayout
    private lateinit var maximumLayout: TextInputLayout

    private lateinit var etMinimum: TextInputEditText
    private lateinit var etMaximum: TextInputEditText

    private var currentUserId: String? = null

    companion object {
        private const val TAG = "PocketPathMonthlyGoals"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_goals)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentUserId = FirebaseRepository.currentUserId

        if (currentUserId == null) {
            Log.w(TAG, "Monthly goals opened without a logged-in user")
            finish()
            return
        }

        connectViews()
        configureButtons()
        observeExistingGoal()
    }

    private fun connectViews() {
        minimumLayout = findViewById(R.id.minimumLayout)
        maximumLayout = findViewById(R.id.maximumLayout)

        etMinimum = findViewById(R.id.etMinimum)
        etMaximum = findViewById(R.id.etMaximum)
    }

    private fun configureButtons() {
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSaveGoals).setOnClickListener { saveGoals() }
    }

    private fun observeExistingGoal() {
        val uid = currentUserId ?: return

        lifecycleScope.launch {
            try {
                FirebaseRepository.observeMonthlyGoal(uid).collectLatest { goal ->
                    if (goal != null) {
                        etMinimum.setText(formatAmount(goal.minimumAmount))
                        etMaximum.setText(formatAmount(goal.maximumAmount))

                        Log.d(TAG, "Existing monthly goal loaded")
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load monthly goal", exception)

                Toast.makeText(this@MonthlyGoalsActivity, "Unable to load your monthly goals", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveGoals() {
        clearErrors()

        val minimum = etMinimum.text?.toString()?.trim()?.toDoubleOrNull()
        val maximum = etMaximum.text?.toString()?.trim()?.toDoubleOrNull()

        var isValid = true

        if (minimum == null || !minimum.isFinite() || minimum <= 0.0) {
            minimumLayout.error = "Enter a minimum amount greater than zero"
            isValid = false
        }

        if (maximum == null || !maximum.isFinite() || maximum <= 0.0) {
            maximumLayout.error = "Enter a maximum amount greater than zero"
            isValid = false
        }

        if (
            minimum != null &&
            maximum != null &&
            minimum.isFinite() &&
            maximum.isFinite() &&
            minimum > maximum
        ) {
            maximumLayout.error = "Maximum cannot be lower than minimum"
            isValid = false
        }

        if (!isValid || minimum == null || maximum == null) {
            return
        }

        val uid = currentUserId ?: return

        lifecycleScope.launch {
            try {
                FirebaseRepository.saveMonthlyGoal(uid, minimum, maximum)

                Log.d(TAG, "Monthly goal saved successfully")

                Toast.makeText(this@MonthlyGoalsActivity, "Monthly goals saved", Toast.LENGTH_SHORT).show()

                finish()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to save monthly goal", exception)

                Toast.makeText(this@MonthlyGoalsActivity, "Unable to save your monthly goals", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
    }

    private fun clearErrors() {
        minimumLayout.error = null
        maximumLayout.error = null
    }
}