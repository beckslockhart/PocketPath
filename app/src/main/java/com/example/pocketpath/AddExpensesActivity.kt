package com.example.pocketpath

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.Category
import com.example.pocketpath.data.entity.Expense
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Captures and validates all information required for one expense entry. */
class AddExpensesActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase
    private lateinit var amountLayout: TextInputLayout
    private lateinit var categoryLayout: TextInputLayout
    private lateinit var descriptionLayout: TextInputLayout
    private lateinit var dateLayout: TextInputLayout
    private lateinit var startTimeLayout: TextInputLayout
    private lateinit var endTimeLayout: TextInputLayout
    private lateinit var etAmount: TextInputEditText
    private lateinit var categoryDropdown: AutoCompleteTextView
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    private lateinit var photoPreview: ImageView
    private lateinit var photoStatus: TextView
    private lateinit var btnRemovePhoto: MaterialButton
    private lateinit var btnSaveExpense: MaterialButton

    private var currentUserId = -1L
    private var categories: List<Category> = emptyList()
    private var selectedCategoryId: Long? = null
    private var selectedDateMillis = startOfDay(System.currentTimeMillis())
    private var photoUri: Uri? = null
    private var pendingPhotoUri: Uri? = null
    private var pendingPhotoFile: File? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { wasSaved ->
            if (wasSaved && pendingPhotoUri != null) {
                photoUri = pendingPhotoUri
                displayPhoto(photoUri)
                Log.d(TAG, "Expense photograph captured")
            } else {
                pendingPhotoFile?.delete()
                Toast.makeText(this, "Photograph was not saved", Toast.LENGTH_SHORT).show()
            }
            pendingPhotoUri = null
            pendingPhotoFile = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expenses)

        database = PocketPathDatabase.getDatabase(this)
        currentUserId = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getLong(KEY_USER_ID, -1L)

        if (currentUserId == -1L) {
            returnToLogin()
            return
        }

        connectViews()
        restoreState(savedInstanceState)
        configureInputs()
        loadCategories()
    }

    private fun connectViews() {
        amountLayout = findViewById(R.id.amountLayout)
        categoryLayout = findViewById(R.id.categoryLayout)
        descriptionLayout = findViewById(R.id.descriptionLayout)
        dateLayout = findViewById(R.id.dateLayout)
        startTimeLayout = findViewById(R.id.startTimeLayout)
        endTimeLayout = findViewById(R.id.endTimeLayout)
        etAmount = findViewById(R.id.etAmount)
        categoryDropdown = findViewById(R.id.categoryDropdown)
        etDescription = findViewById(R.id.etDescription)
        etDate = findViewById(R.id.etDate)
        etStartTime = findViewById(R.id.etStartTime)
        etEndTime = findViewById(R.id.etEndTime)
        photoPreview = findViewById(R.id.photoPreview)
        photoStatus = findViewById(R.id.photoStatus)
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto)
        btnSaveExpense = findViewById(R.id.btnSaveExpense)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            setDefaultTimes()
            updateDateText()
            return
        }

        selectedCategoryId = savedInstanceState.getLong(STATE_CATEGORY_ID, -1L)
            .takeIf { it != -1L }
        selectedDateMillis = savedInstanceState.getLong(STATE_DATE, selectedDateMillis)
        savedInstanceState.getString(STATE_START_TIME)?.let(etStartTime::setText)
        savedInstanceState.getString(STATE_END_TIME)?.let(etEndTime::setText)
        savedInstanceState.getString(STATE_PHOTO_URI)?.let {
            photoUri = it.toUri()
            displayPhoto(photoUri)
        }
        updateDateText()
    }

    private fun configureInputs() {
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        etDate.setOnClickListener { openDatePicker() }
        dateLayout.setEndIconOnClickListener { openDatePicker() }
        etStartTime.setOnClickListener { openTimePicker(etStartTime, startTimeLayout) }
        startTimeLayout.setEndIconOnClickListener {
            openTimePicker(etStartTime, startTimeLayout)
        }
        etEndTime.setOnClickListener { openTimePicker(etEndTime, endTimeLayout) }
        endTimeLayout.setEndIconOnClickListener { openTimePicker(etEndTime, endTimeLayout) }
        categoryDropdown.setOnClickListener { categoryDropdown.showDropDown() }
        categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categories.getOrNull(position)?.categoryId
            categoryLayout.error = null
        }
        findViewById<MaterialButton>(R.id.btnTakePhoto).setOnClickListener { launchCamera() }
        btnRemovePhoto.setOnClickListener { removePhoto() }
        btnSaveExpense.setOnClickListener { saveExpense() }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            try {
                database.categoryDao().getCategoriesForUser(currentUserId)
                    .collectLatest { latestCategories ->
                        categories = latestCategories
                        categoryDropdown.setAdapter(
                            ArrayAdapter(
                                this@AddExpensesActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                latestCategories.map { it.name }
                            )
                        )
                        val selected = latestCategories
                            .firstOrNull { it.categoryId == selectedCategoryId }
                        if (selected != null) {
                            categoryDropdown.setText(selected.name, false)
                        } else if (selectedCategoryId != null) {
                            selectedCategoryId = null
                            categoryDropdown.setText("", false)
                        }
                        if (latestCategories.isEmpty()) {
                            categoryLayout.helperText =
                                "Add a budget category from the dashboard before saving."
                        } else {
                            categoryLayout.helperText = null
                        }
                    }
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to load categories", exception)
                Toast.makeText(
                    this@AddExpensesActivity,
                    "Unable to load categories",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDateMillis = Calendar.getInstance().apply {
                    clear()
                    set(year, month, day)
                }.timeInMillis
                updateDateText()
                dateLayout.error = null
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun openTimePicker(field: TextInputEditText, layout: TextInputLayout) {
        val currentTime = parseTime(field.text?.toString())
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                field.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
                layout.error = null
            },
            currentTime?.first ?: now.get(Calendar.HOUR_OF_DAY),
            currentTime?.second ?: now.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun launchCamera() {
        try {
            val photosDirectory = File(filesDir, PHOTO_DIRECTORY).apply { mkdirs() }
            val photoFile = File.createTempFile("expense_", ".jpg", photosDirectory)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            pendingPhotoFile = photoFile
            pendingPhotoUri = uri
            takePictureLauncher.launch(uri)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to open camera", exception)
            Toast.makeText(this, "Unable to open the camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayPhoto(uri: Uri?) {
        if (uri == null) return
        photoPreview.setImageURI(null)
        photoPreview.setImageURI(uri)
        photoPreview.visibility = View.VISIBLE
        btnRemovePhoto.visibility = View.VISIBLE
        photoStatus.setText(R.string.photograph_attached)
    }

    private fun removePhoto() {
        photoUri?.lastPathSegment?.substringAfterLast('/')?.let { fileName ->
            File(filesDir, "$PHOTO_DIRECTORY/$fileName").takeIf { it.exists() }?.delete()
        }
        photoUri = null
        photoPreview.setImageDrawable(null)
        photoPreview.visibility = View.GONE
        btnRemovePhoto.visibility = View.GONE
        photoStatus.setText(R.string.no_photograph_attached)
    }

    private fun saveExpense() {
        clearErrors()
        val amount = etAmount.text?.toString()?.trim()?.toDoubleOrNull()
        val description = etDescription.text?.toString()?.trim().orEmpty()
        val startTime = etStartTime.text?.toString()?.trim().orEmpty()
        val endTime = etEndTime.text?.toString()?.trim().orEmpty()
        var isValid = true

        if (amount == null || !amount.isFinite() || amount <= 0.0) {
            amountLayout.error = "Enter an amount greater than zero"
            isValid = false
        }
        if (selectedCategoryId == null) {
            categoryLayout.error = if (categories.isEmpty()) {
                "Create a category before adding an expense"
            } else {
                "Select a category"
            }
            isValid = false
        }
        if (description.isBlank()) {
            descriptionLayout.error = "Description is required"
            isValid = false
        }
        val startMinutes = timeInMinutes(startTime)
        val endMinutes = timeInMinutes(endTime)
        if (startMinutes == null) {
            startTimeLayout.error = "Select a valid start time"
            isValid = false
        }
        if (endMinutes == null) {
            endTimeLayout.error = "Select a valid end time"
            isValid = false
        } else if (startMinutes != null && endMinutes < startMinutes) {
            endTimeLayout.error = "End time cannot be before start time"
            isValid = false
        }
        if (!isValid || amount == null) return

        btnSaveExpense.isEnabled = false
        btnSaveExpense.setText(R.string.saving_expense)
        lifecycleScope.launch {
            try {
                val expenseId = database.expenseDao().insertExpense(
                    Expense(
                        userId = currentUserId,
                        categoryId = selectedCategoryId,
                        amount = amount,
                        description = description,
                        expenseDate = selectedDateMillis,
                        startTime = startTime,
                        endTime = endTime,
                        photoUri = photoUri?.toString()
                    )
                )
                Log.d(TAG, "Expense saved with ID: $expenseId")
                Toast.makeText(
                    this@AddExpensesActivity,
                    "Expense saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to save expense", exception)
                btnSaveExpense.isEnabled = true
                btnSaveExpense.setText(R.string.save_expense)
                Toast.makeText(
                    this@AddExpensesActivity,
                    "Unable to save expense. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setDefaultTimes() {
        val now = Calendar.getInstance()
        etStartTime.setText(formatTime(now))
        now.add(Calendar.HOUR_OF_DAY, 1)
        etEndTime.setText(formatTime(now))
    }

    private fun updateDateText() {
        etDate.setText(
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(selectedDateMillis))
        )
    }

    private fun clearErrors() {
        amountLayout.error = null
        categoryLayout.error = null
        descriptionLayout.error = null
        dateLayout.error = null
        startTimeLayout.error = null
        endTimeLayout.error = null
    }

    private fun returnToLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_CATEGORY_ID, selectedCategoryId ?: -1L)
        outState.putLong(STATE_DATE, selectedDateMillis)
        outState.putString(STATE_START_TIME, etStartTime.text?.toString())
        outState.putString(STATE_END_TIME, etEndTime.text?.toString())
        outState.putString(STATE_PHOTO_URI, photoUri?.toString())
    }

    companion object {
        private const val TAG = "PocketPathAddExpense"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
        private const val PHOTO_DIRECTORY = "expense_photos"
        private const val STATE_CATEGORY_ID = "selected_category_id"
        private const val STATE_DATE = "selected_date"
        private const val STATE_START_TIME = "selected_start_time"
        private const val STATE_END_TIME = "selected_end_time"
        private const val STATE_PHOTO_URI = "selected_photo_uri"

        private fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun formatTime(calendar: Calendar): String = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )

        private fun parseTime(value: String?): Pair<Int, Int>? {
            val parts = value?.split(":") ?: return null
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }

        private fun timeInMinutes(value: String): Int? {
            val (hour, minute) = parseTime(value) ?: return null
            return hour * 60 + minute
        }
    }
}
