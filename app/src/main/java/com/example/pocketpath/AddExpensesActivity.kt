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

/**
 * Handles the Add Expense feature.
 *
 * This screen allows a logged-in user to enter expense information,
 * choose a category, select a date and times, optionally capture a
 * receipt photograph, validate the information and save it to Room.
 */
class AddExpensesActivity : AppCompatActivity() {

    // Local Room database used to read categories and save expenses.
    private lateinit var database: PocketPathDatabase

    // Input layouts are used to display validation errors to the user.
    private lateinit var amountLayout: TextInputLayout
    private lateinit var categoryLayout: TextInputLayout
    private lateinit var descriptionLayout: TextInputLayout
    private lateinit var dateLayout: TextInputLayout
    private lateinit var startTimeLayout: TextInputLayout
    private lateinit var endTimeLayout: TextInputLayout

    // Main expense input fields.
    private lateinit var etAmount: TextInputEditText
    private lateinit var categoryDropdown: AutoCompleteTextView
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText

    // Photograph preview and action controls.
    private lateinit var photoPreview: ImageView
    private lateinit var photoStatus: TextView
    private lateinit var btnRemovePhoto: MaterialButton
    private lateinit var btnSaveExpense: MaterialButton

    // Stores the currently logged-in user and the data selected on the form.
    private var currentUserId = -1L
    private var categories: List<Category> = emptyList()
    private var selectedCategoryId: Long? = null
    private var selectedDateMillis = startOfDay(System.currentTimeMillis())

    // URI information used when capturing and storing a receipt photograph.
    private var photoUri: Uri? = null
    private var pendingPhotoUri: Uri? = null
    private var pendingPhotoFile: File? = null

    /**
     * Launches the device camera and handles the result of the photograph.
     * If the photograph is successfully saved, it is displayed on the form.
     */
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { wasSaved ->
            if (wasSaved && pendingPhotoUri != null) {
                photoUri = pendingPhotoUri
                displayPhoto(photoUri)

                Log.d(TAG, "Expense photograph captured")
            } else {
                // Delete the unused file if the photograph was cancelled or failed.
                pendingPhotoFile?.delete()

                Toast.makeText(
                    this,
                    "Photograph was not saved",
                    Toast.LENGTH_SHORT
                ).show()
            }

            pendingPhotoUri = null
            pendingPhotoFile = null
        }

    /**
     * Sets up the Add Expense screen when the activity is opened.
     *
     * The logged-in user is retrieved first before the form controls
     * and categories are loaded.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expenses)

        database = PocketPathDatabase.getDatabase(this)

        // Retrieve the user ID that was saved when the user logged in.
        currentUserId = getSharedPreferences(
            PREFERENCES_NAME,
            MODE_PRIVATE
        ).getLong(KEY_USER_ID, -1L)

        // A user must be logged in before an expense can be created.
        if (currentUserId == -1L) {
            returnToLogin()
            return
        }

        connectViews()
        restoreState(savedInstanceState)
        configureInputs()
        loadCategories()
    }

    /**
     * Connects the Kotlin activity to the views defined in
     * activity_add_expenses.xml.
     */
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

    /**
     * Restores temporary form values if Android recreates the activity,
     * for example after a configuration change.
     */
    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            // A new form starts with today's date and sensible default times.
            setDefaultTimes()
            updateDateText()
            return
        }

        selectedCategoryId =
            savedInstanceState.getLong(STATE_CATEGORY_ID, -1L)
                .takeIf { it != -1L }

        selectedDateMillis =
            savedInstanceState.getLong(
                STATE_DATE,
                selectedDateMillis
            )

        savedInstanceState
            .getString(STATE_START_TIME)
            ?.let(etStartTime::setText)

        savedInstanceState
            .getString(STATE_END_TIME)
            ?.let(etEndTime::setText)

        // Restore the attached receipt photograph if one was selected.
        savedInstanceState
            .getString(STATE_PHOTO_URI)
            ?.let {
                photoUri = it.toUri()
                displayPhoto(photoUri)
            }

        updateDateText()
    }

    /**
     * Configures click listeners for the form controls and buttons.
     */
    private fun configureInputs() {

        // Return to the previous screen.
        findViewById<MaterialButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        // Date can be selected by pressing either the field or its icon.
        etDate.setOnClickListener {
            openDatePicker()
        }

        dateLayout.setEndIconOnClickListener {
            openDatePicker()
        }

        // Start-time selection.
        etStartTime.setOnClickListener {
            openTimePicker(
                etStartTime,
                startTimeLayout
            )
        }

        startTimeLayout.setEndIconOnClickListener {
            openTimePicker(
                etStartTime,
                startTimeLayout
            )
        }

        // End-time selection.
        etEndTime.setOnClickListener {
            openTimePicker(
                etEndTime,
                endTimeLayout
            )
        }

        endTimeLayout.setEndIconOnClickListener {
            openTimePicker(
                etEndTime,
                endTimeLayout
            )
        }

        // Display the user's available categories.
        categoryDropdown.setOnClickListener {
            categoryDropdown.showDropDown()
        }

        categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId =
                categories.getOrNull(position)?.categoryId

            // Remove a previous validation message once a category is chosen.
            categoryLayout.error = null
        }

        // Photograph controls.
        findViewById<MaterialButton>(R.id.btnTakePhoto)
            .setOnClickListener {
                launchCamera()
            }

        btnRemovePhoto.setOnClickListener {
            removePhoto()
        }

        // Validate and save the completed expense.
        btnSaveExpense.setOnClickListener {
            saveExpense()
        }
    }

    /**
     * Loads the logged-in user's categories from Room.
     *
     * The Flow is observed so the dropdown automatically reflects
     * category changes while this screen is open.
     */
    private fun loadCategories() {
        lifecycleScope.launch {
            try {
                database.categoryDao()
                    .getCategoriesForUser(currentUserId)
                    .collectLatest { latestCategories ->

                        categories = latestCategories

                        // Display the category names in the dropdown.
                        categoryDropdown.setAdapter(
                            ArrayAdapter(
                                this@AddExpensesActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                latestCategories.map { it.name }
                            )
                        )

                        // Restore the selected category if the activity was recreated.
                        val selected =
                            latestCategories.firstOrNull {
                                it.categoryId == selectedCategoryId
                            }

                        if (selected != null) {
                            categoryDropdown.setText(
                                selected.name,
                                false
                            )
                        } else if (selectedCategoryId != null) {
                            selectedCategoryId = null
                            categoryDropdown.setText(
                                "",
                                false
                            )
                        }

                        // Expenses require a category, so guide the user if none exist.
                        if (latestCategories.isEmpty()) {
                            categoryLayout.helperText =
                                "Add a budget category from the dashboard before saving."
                        } else {
                            categoryLayout.helperText = null
                        }
                    }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to load categories",
                    exception
                )

                Toast.makeText(
                    this@AddExpensesActivity,
                    "Unable to load categories",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Opens a calendar dialog and stores the date selected by the user.
     */
    private fun openDatePicker() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->

                // Store the selected date at the beginning of that day.
                selectedDateMillis =
                    Calendar.getInstance().apply {
                        clear()
                        set(
                            year,
                            month,
                            day
                        )
                    }.timeInMillis

                updateDateText()
                dateLayout.error = null
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Opens a 24-hour time picker for either the start-time
     * or end-time field.
     */
    private fun openTimePicker(
        field: TextInputEditText,
        layout: TextInputLayout
    ) {
        val currentTime =
            parseTime(field.text?.toString())

        val now = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->

                field.setText(
                    String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        hour,
                        minute
                    )
                )

                // Clear validation errors once a valid time is selected.
                layout.error = null
            },
            currentTime?.first
                ?: now.get(Calendar.HOUR_OF_DAY),
            currentTime?.second
                ?: now.get(Calendar.MINUTE),
            true
        ).show()
    }

    /**
     * Creates a temporary photograph file and securely launches
     * the camera using Android's FileProvider.
     */
    private fun launchCamera() {
        try {
            // Receipt photographs are stored in the app's private files directory.
            val photosDirectory =
                File(
                    filesDir,
                    PHOTO_DIRECTORY
                ).apply {
                    mkdirs()
                }

            val photoFile =
                File.createTempFile(
                    "expense_",
                    ".jpg",
                    photosDirectory
                )

            // FileProvider creates a secure content URI for the camera app.
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    photoFile
                )

            pendingPhotoFile = photoFile
            pendingPhotoUri = uri

            takePictureLauncher.launch(uri)
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to open camera",
                exception
            )

            Toast.makeText(
                this,
                "Unable to open the camera",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Displays the captured receipt photograph and enables
     * the option to remove it.
     */
    private fun displayPhoto(uri: Uri?) {
        if (uri == null) return

        // Clear the old image before loading the new URI.
        photoPreview.setImageURI(null)
        photoPreview.setImageURI(uri)

        photoPreview.visibility = View.VISIBLE
        btnRemovePhoto.visibility = View.VISIBLE

        photoStatus.setText(
            R.string.photograph_attached
        )
    }

    /**
     * Deletes the attached photograph and resets the photograph section.
     */
    private fun removePhoto() {

        // Delete the saved image from the app's internal photo directory.
        photoUri
            ?.lastPathSegment
            ?.substringAfterLast('/')
            ?.let { fileName ->

                File(
                    filesDir,
                    "$PHOTO_DIRECTORY/$fileName"
                )
                    .takeIf { it.exists() }
                    ?.delete()
            }

        photoUri = null

        photoPreview.setImageDrawable(null)
        photoPreview.visibility = View.GONE
        btnRemovePhoto.visibility = View.GONE

        photoStatus.setText(
            R.string.no_photograph_attached
        )
    }

    /**
     * Validates the expense information and saves a valid entry
     * to the Room database for the currently logged-in user.
     */
    private fun saveExpense() {

        // Clear old error messages before performing a new validation attempt.
        clearErrors()

        val amount =
            etAmount.text
                ?.toString()
                ?.trim()
                ?.toDoubleOrNull()

        val description =
            etDescription.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val startTime =
            etStartTime.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val endTime =
            etEndTime.text
                ?.toString()
                ?.trim()
                .orEmpty()

        var isValid = true

        // Amount must contain a valid value greater than zero.
        if (
            amount == null ||
            !amount.isFinite() ||
            amount <= 0.0
        ) {
            amountLayout.error =
                "Enter an amount greater than zero"

            isValid = false
        }

        // Every expense must belong to one of the user's categories.
        if (selectedCategoryId == null) {
            categoryLayout.error =
                if (categories.isEmpty()) {
                    "Create a category before adding an expense"
                } else {
                    "Select a category"
                }

            isValid = false
        }

        // A description is required so the expense can be identified later.
        if (description.isBlank()) {
            descriptionLayout.error =
                "Description is required"

            isValid = false
        }

        // Convert both times to minutes so they can be compared easily.
        val startMinutes =
            timeInMinutes(startTime)

        val endMinutes =
            timeInMinutes(endTime)

        if (startMinutes == null) {
            startTimeLayout.error =
                "Select a valid start time"

            isValid = false
        }

        if (endMinutes == null) {
            endTimeLayout.error =
                "Select a valid end time"

            isValid = false
        } else if (
            startMinutes != null &&
            endMinutes < startMinutes
        ) {
            endTimeLayout.error =
                "End time cannot be before start time"

            isValid = false
        }

        // Stop here if any required information failed validation.
        if (!isValid || amount == null) return

        // Prevent the user from pressing Save more than once while Room is working.
        btnSaveExpense.isEnabled = false
        btnSaveExpense.setText(
            R.string.saving_expense
        )

        lifecycleScope.launch {
            try {
                /*
                 * Store the completed expense in Room.
                 * Other features such as the dashboard and expense history
                 * can then access the same saved entry.
                 */
                val expenseId =
                    database.expenseDao()
                        .insertExpense(
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

                Log.d(
                    TAG,
                    "Expense saved with ID: $expenseId"
                )

                Toast.makeText(
                    this@AddExpensesActivity,
                    "Expense saved successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Return to the previous screen after a successful save.
                finish()
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Unable to save expense",
                    exception
                )

                // Re-enable the button so the user can try again.
                btnSaveExpense.isEnabled = true
                btnSaveExpense.setText(
                    R.string.save_expense
                )

                Toast.makeText(
                    this@AddExpensesActivity,
                    "Unable to save expense. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Gives a new expense a default start time of the current
     * time and an end time one hour later.
     */
    private fun setDefaultTimes() {
        val now = Calendar.getInstance()

        etStartTime.setText(
            formatTime(now)
        )

        now.add(
            Calendar.HOUR_OF_DAY,
            1
        )

        etEndTime.setText(
            formatTime(now)
        )
    }

    /**
     * Formats the selected expense date into a readable value
     * for the date field.
     */
    private fun updateDateText() {
        etDate.setText(
            SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()
            ).format(
                Date(selectedDateMillis)
            )
        )
    }

    /**
     * Removes previous validation messages before the form
     * is checked again.
     */
    private fun clearErrors() {
        amountLayout.error = null
        categoryLayout.error = null
        descriptionLayout.error = null
        dateLayout.error = null
        startTimeLayout.error = null
        endTimeLayout.error = null
    }

    /**
     * Returns the user to the login screen if a valid logged-in
     * user cannot be found.
     */
    private fun returnToLogin() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )

        finish()
    }

    /**
     * Saves temporary form selections so that they can be restored
     * if Android recreates the activity.
     */
    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        super.onSaveInstanceState(outState)

        outState.putLong(
            STATE_CATEGORY_ID,
            selectedCategoryId ?: -1L
        )

        outState.putLong(
            STATE_DATE,
            selectedDateMillis
        )

        outState.putString(
            STATE_START_TIME,
            etStartTime.text?.toString()
        )

        outState.putString(
            STATE_END_TIME,
            etEndTime.text?.toString()
        )

        outState.putString(
            STATE_PHOTO_URI,
            photoUri?.toString()
        )
    }

    companion object {

        // Logcat tag used for Add Expense debugging information.
        private const val TAG =
            "PocketPathAddExpense"

        // SharedPreferences information used to identify the logged-in user.
        private const val PREFERENCES_NAME =
            "pocketpath_preferences"

        private const val KEY_USER_ID =
            "logged_in_user_id"

        // Internal folder used to store receipt photographs.
        private const val PHOTO_DIRECTORY =
            "expense_photos"

        // Keys used when preserving temporary form state.
        private const val STATE_CATEGORY_ID =
            "selected_category_id"

        private const val STATE_DATE =
            "selected_date"

        private const val STATE_START_TIME =
            "selected_start_time"

        private const val STATE_END_TIME =
            "selected_end_time"

        private const val STATE_PHOTO_URI =
            "selected_photo_uri"

        /**
         * Converts a timestamp to the beginning of that calendar day.
         */
        private fun startOfDay(
            timeMillis: Long
        ): Long =
            Calendar.getInstance().apply {
                this.timeInMillis = timeMillis

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

        /**
         * Formats a Calendar value as a 24-hour HH:mm time string.
         */
        private fun formatTime(
            calendar: Calendar
        ): String =
            String.format(
                Locale.getDefault(),
                "%02d:%02d",
                calendar.get(
                    Calendar.HOUR_OF_DAY
                ),
                calendar.get(
                    Calendar.MINUTE
                )
            )

        /**
         * Parses and validates a 24-hour time value in HH:mm format.
         */
        private fun parseTime(
            value: String?
        ): Pair<Int, Int>? {

            val parts =
                value?.split(":")
                    ?: return null

            if (parts.size != 2) {
                return null
            }

            val hour =
                parts[0].toIntOrNull()
                    ?: return null

            val minute =
                parts[1].toIntOrNull()
                    ?: return null

            // Reject values outside a valid 24-hour clock.
            if (
                hour !in 0..23 ||
                minute !in 0..59
            ) {
                return null
            }

            return hour to minute
        }

        /**
         * Converts an HH:mm value into the number of minutes
         * after midnight, making start/end time comparison easier.
         */
        private fun timeInMinutes(
            value: String
        ): Int? {

            val (hour, minute) =
                parseTime(value)
                    ?: return null

            return hour * 60 + minute
        }
    }
}