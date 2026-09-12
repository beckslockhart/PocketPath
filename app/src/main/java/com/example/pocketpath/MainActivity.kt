package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.database.PocketPathDatabase
import com.example.pocketpath.data.entity.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var database: PocketPathDatabase

    private lateinit var usernameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout

    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText

    private lateinit var tvAuthTitle: TextView
    private lateinit var tvAuthSubtitle: TextView

    private lateinit var btnPrimaryAction: MaterialButton
    private lateinit var btnSwitchAuthMode: MaterialButton

    private var isRegisterMode = false

    companion object {
        private const val TAG = "PocketPathAuth"
        private const val PREFERENCES_NAME = "pocketpath_preferences"
        private const val KEY_USER_ID = "logged_in_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = PocketPathDatabase.getDatabase(this)

        connectViews()
        configureButtons()
        updateAuthMode()
    }

    /**
     * Connects the Kotlin variables to the controls in activity_main.xml.
     */
    private fun connectViews() {
        usernameLayout = findViewById(R.id.usernameLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        tvAuthTitle = findViewById(R.id.tvAuthTitle)
        tvAuthSubtitle = findViewById(R.id.tvAuthSubtitle)

        btnPrimaryAction = findViewById(R.id.btnPrimaryAction)
        btnSwitchAuthMode = findViewById(R.id.btnSwitchAuthMode)
    }


    private fun configureButtons() {
        btnPrimaryAction.setOnClickListener {
            clearErrors()

            if (isRegisterMode) {
                registerUser()
            } else {
                loginUser()
            }
        }

        btnSwitchAuthMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateAuthMode()
        }
    }


    private fun updateAuthMode() {
        if (isRegisterMode) {
            tvAuthTitle.text = "Create account"
            tvAuthSubtitle.text = "Register to begin your financial journey"
            confirmPasswordLayout.visibility = View.VISIBLE
            btnPrimaryAction.text = "Register"
            btnSwitchAuthMode.text = "Already have an account? Log in"
        } else {
            tvAuthTitle.text = "Welcome back"
            tvAuthSubtitle.text = "Log in to continue to your dashboard"
            confirmPasswordLayout.visibility = View.GONE
            btnPrimaryAction.text = "Log in"
            btnSwitchAuthMode.text = "New to PocketPath? Create an account"
            etConfirmPassword.text?.clear()
        }

        clearErrors()
    }


    private fun registerUser() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (!validateRegistration(username, password, confirmPassword)) {
            return
        }

        lifecycleScope.launch {
            try {
                val existingUser = database.userDao().getUserByUsername(username)

                if (existingUser != null) {
                    usernameLayout.error = "This username is already registered"
                    return@launch
                }

                val userId = database.userDao().insertUser(
                    User(
                        username = username,
                        password = password
                    )
                )

                Log.d(TAG, "User registered successfully with ID: $userId")

                Toast.makeText(
                    this@MainActivity,
                    "Account created successfully",
                    Toast.LENGTH_SHORT
                ).show()

                saveLoggedInUser(userId)
                openDashboard()
            } catch (exception: Exception) {
                Log.e(TAG, "Registration failed", exception)

                Toast.makeText(
                    this@MainActivity,
                    "Unable to create account",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun loginUser() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateLogin(username, password)) {
            return
        }

        lifecycleScope.launch {
            try {
                val user = database.userDao().login(username, password)

                if (user == null) {
                    passwordLayout.error = "Incorrect username or password"
                    Log.w(TAG, "Failed login attempt for username: $username")
                    return@launch
                }

                Log.d(TAG, "User logged in successfully with ID: ${user.userId}")

                saveLoggedInUser(user.userId)
                openDashboard()
            } catch (exception: Exception) {
                Log.e(TAG, "Login failed", exception)

                Toast.makeText(
                    this@MainActivity,
                    "Unable to log in",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun validateRegistration(
        username: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        if (username.isBlank()) {
            usernameLayout.error = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            usernameLayout.error = "Username must contain at least 3 characters"
            isValid = false
        }

        if (password.isBlank()) {
            passwordLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordLayout.error = "Password must contain at least 6 characters"
            isValid = false
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordLayout.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordLayout.error = "Passwords do not match"
            isValid = false
        }

        return isValid
    }

    private fun validateLogin(
        username: String,
        password: String
    ): Boolean {
        var isValid = true

        if (username.isBlank()) {
            usernameLayout.error = "Username is required"
            isValid = false
        }

        if (password.isBlank()) {
            passwordLayout.error = "Password is required"
            isValid = false
        }

        return isValid
    }

    private fun clearErrors() {
        usernameLayout.error = null
        passwordLayout.error = null
        confirmPasswordLayout.error = null
    }


    private fun saveLoggedInUser(userId: Long) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putLong(KEY_USER_ID, userId)
            .apply()
    }


    private fun openDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}