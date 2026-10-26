package com.example.pocketpath

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocketpath.data.firebase.FirebaseRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var usernameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout

    private lateinit var etUsername: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText

    private lateinit var tvAuthTitle: TextView
    private lateinit var tvAuthSubtitle: TextView

    private lateinit var btnPrimaryAction: MaterialButton
    private lateinit var btnSwitchAuthMode: MaterialButton

    private var isRegisterMode = false

    companion object {
        private const val TAG = "PocketPathAuth"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseRepository.currentUserId != null) {
            Log.d(TAG, "Existing login session found")
            openDashboard()
            return
        }

        setContentView(R.layout.activity_main)

        connectViews()
        configureButtons()
        updateAuthMode()
    }

    private fun connectViews() {
        usernameLayout = findViewById(R.id.usernameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)

        etUsername = findViewById(R.id.etUsername)
        etEmail = findViewById(R.id.etEmail)
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
            usernameLayout.hint = "Username"
            emailLayout.visibility = View.VISIBLE
            confirmPasswordLayout.visibility = View.VISIBLE
            btnPrimaryAction.text = "Register"
            btnSwitchAuthMode.text = "Already have an account? Log in"
        } else {
            tvAuthTitle.text = "Welcome back"
            tvAuthSubtitle.text = "Log in to continue to your dashboard"
            usernameLayout.hint = "Username or email"
            emailLayout.visibility = View.GONE
            confirmPasswordLayout.visibility = View.GONE
            btnPrimaryAction.text = "Log in"
            btnSwitchAuthMode.text = "New to PocketPath? Create an account"
            etEmail.text?.clear()
            etConfirmPassword.text?.clear()
        }

        clearErrors()
    }

    private fun registerUser() {
        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (!validateRegistration(username, email, password, confirmPassword)) {
            return
        }

        lifecycleScope.launch {
            try {
                FirebaseRepository.registerWithUsername(username, email, password)

                Log.d(TAG, "User registered successfully")

                Toast.makeText(this@MainActivity, "Account created successfully", Toast.LENGTH_SHORT).show()

                openDashboard()
            } catch (exception: Exception) {
                Log.e(TAG, "Registration failed", exception)

                usernameLayout.error = exception.message ?: "Unable to create account"
            }
        }
    }

    private fun loginUser() {
        val rawInput = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateLogin(rawInput, password)) {
            return
        }

        lifecycleScope.launch {
            try {
                val resolvedEmail = FirebaseRepository.resolveEmailForLogin(rawInput)
                FirebaseRepository.login(resolvedEmail, password)

                Log.d(TAG, "User logged in successfully")

                openDashboard()
            } catch (exception: Exception) {
                Log.w(TAG, "Failed login attempt for: $rawInput", exception)

                passwordLayout.error = "Incorrect username/email or password"
            }
        }
    }

    private fun validateRegistration(username: String, email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (username.isBlank()) {
            usernameLayout.error = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            usernameLayout.error = "Username must contain at least 3 characters"
            isValid = false
        }

        if (email.isBlank()) {
            emailLayout.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email address"
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

    private fun validateLogin(input: String, password: String): Boolean {
        var isValid = true

        if (input.isBlank()) {
            usernameLayout.error = "Username or email is required"
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
        emailLayout.error = null
        passwordLayout.error = null
        confirmPasswordLayout.error = null
    }

    private fun openDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}