package com.example.pocketpath

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.SeekBar
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
    private lateinit var seekMinimum: SeekBar
    private lateinit var seekMaximum: SeekBar
    private var currentUserId = -1L

    /** Prevents a text field and its SeekBar from triggering each other in a loop. */
    private var isSyncingMinimum = false
    private var isSyncingMaximum = false

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
        seekMinimum = findViewById(R.id.seekMinimum)
        seekMaximum = findViewById(R.id.seekMaximum)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSaveGoals).setOnClickListener { saveGoals() }

        configureMinimumSync()
        configureMaximumSync()
        observeExistingGoal()
    }

    /** Keeps the minimum text field and its SeekBar showing the same value. */
    private fun configureMinimumSync() {
        etMinimum.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isSyncingMinimum) return
                val amount = s?.toString()?.toDoubleOrNull() ?: return
                isSyncingMinimum = true
                seekMinimum.progress = amountToProgress(amount)
                isSyncingMinimum = false
            }
        })

        seekMinimum.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isSyncingMinimum) return
                isSyncingMinimum = true
                etMinimum.setText(progressToAmount(progress).toString())
                etMinimum.setSelection(etMinimum.text?.length ?: 0)
                isSyncingMinimum = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /** Keeps the maximum text field and its SeekBar showing the same value. */
    private fun configureMaximumSync() {
        etMaximum.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isSyncingMaximum) return
                val amount = s?.toString()?.toDoubleOrNull() ?: return
                isSyncingMaximum = true
                seekMaximum.progress = amountToProgress(amount)
                isSyncingMaximum = false
            }
        })

        seekMaximum.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isSyncingMaximum) return
                isSyncingMaximum = true
                etMaximum.setText(progressToAmount(progress).toString())
                etMaximum.setSelection(etMaximum.text?.length ?: 0)
                isSyncingMaximum = false
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun amountToProgress(amount: Double): Int =
        (amount / SEEKBAR_STEP).toInt().coerceIn(0, SEEKBAR_MAX)

    private fun progressToAmount(progress: Int): Double =
        (progress * SEEKBAR_STEP)

    private fun observeExistingGoal() {
        lifecycleScope.launch {
            try {
                database.monthlyGoalDao().getMonthlyGoal(currentUserId).collectLatest { goal ->
                    if (goal != null) {
                        isSyncingMinimum = true
                        etMinimum.setText(goal.minimumAmount.toString())
                        seekMinimum.progress = amountToProgress(goal.minimumAmount)
                        isSyncingMinimum = false

                        isSyncingMaximum = true
                        etMaximum.setText(goal.maximumAmount.toString())
                        seekMaximum.progress = amountToProgress(goal.maximumAmount)
                        isSyncingMaximum = false
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
        private const val SEEKBAR_MAX = 100
        private const val SEEKBAR_STEP = 100.0 // each SeekBar unit = R100, so max = R10 000
    }
}