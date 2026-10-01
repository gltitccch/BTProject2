package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.UserProfile
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.RegistrationValidator
import com.example.btproject2.utils.TreePreferences

class RegisterActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnBack = findViewById<TextView>(R.id.btnBackToWelcome)
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnTogglePassword = findViewById<ImageView>(R.id.btnTogglePassword)
        val btnToggleConfirmPassword = findViewById<ImageView>(R.id.btnToggleConfirmPassword)
        val btnCreate = findViewById<Button>(R.id.btnCreateAccount)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        btnBack.setOnClickListener { finish() }

        // Setup password show/hide visibility toggles
        setupPasswordToggle(etPassword, btnTogglePassword)
        setupPasswordToggle(etConfirmPassword, btnToggleConfirmPassword)

        // Clear errors in real-time as user types
        etFullName.doOnTextChanged { _, _, _, _ -> etFullName.error = null }
        etEmail.doOnTextChanged { _, _, _, _ -> etEmail.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> etPassword.error = null }
        etConfirmPassword.doOnTextChanged { _, _, _, _ -> etConfirmPassword.error = null }

        btnCreate.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            val validation = RegistrationValidator.validateForm(
                fullName = fullName,
                email = email,
                password = password,
                confirmPassword = confirmPassword
            )

            if (!validation.isValid) {
                var firstErrorField: EditText? = null

                if (validation.confirmPasswordResult is RegistrationValidator.FieldResult.Invalid) {
                    etConfirmPassword.error = validation.confirmPasswordResult.errorMessage
                    firstErrorField = etConfirmPassword
                }
                if (validation.passwordResult is RegistrationValidator.FieldResult.Invalid) {
                    etPassword.error = validation.passwordResult.errorMessage
                    firstErrorField = etPassword
                }
                if (validation.emailResult is RegistrationValidator.FieldResult.Invalid) {
                    etEmail.error = validation.emailResult.errorMessage
                    firstErrorField = etEmail
                }
                if (validation.fullNameResult is RegistrationValidator.FieldResult.Invalid) {
                    etFullName.error = validation.fullNameResult.errorMessage
                    firstErrorField = etFullName
                }

                firstErrorField?.requestFocus()
                return@setOnClickListener
            }

            // Disable button and show progress indicator to prevent duplicate submission
            btnCreate.isEnabled = false
            btnCreate.text = "Creating Account..."

            authHelper.register(email, password,
                onSuccess = { user ->
                    // 1. Update Firebase Auth display name
                    authHelper.updateDisplayName(fullName, onSuccess = {}, onFailure = {})

                    // 2. Dispatch zero-cost email verification link
                    authHelper.sendEmailVerification(onSuccess = {}, onFailure = {})

                    // 3. Clear existing tree preferences & runtime cache
                    TreePreferences.clear(this)
                    FirestoreHelper.clearCache()
                    CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    CentralTreeSynchronizer.getInstance().resetForTesting()

                    // 4. Save UserProfile to Firestore as single source of truth
                    val profile = UserProfile(
                        id = user.uid,
                        displayName = fullName,
                        email = email,
                        role = "user",
                        currentTreeId = ""
                    )
                    firestoreHelper.saveUserProfile(profile,
                        onSuccess = {
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                btnCreate.isEnabled = true
                                btnCreate.text = "Create Account"
                                val intent = Intent(this, VerifyEmailActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                        },
                        onFailure = {
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                btnCreate.isEnabled = true
                                btnCreate.text = "Create Account"
                                // Still proceed to verification since auth account was created
                                val intent = Intent(this, VerifyEmailActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                        }
                    )
                },
                onFailure = { error ->
                    btnCreate.isEnabled = true
                    btnCreate.text = "Create Account"
                    showMessage("Registration Failed", error.message ?: "Unknown error occurred.")
                }
            )
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupPasswordToggle(editText: EditText, toggleButton: ImageView) {
        var isVisible = false
        toggleButton.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleButton.setImageResource(R.drawable.ic_visibility)
                toggleButton.contentDescription = "Hide password"
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleButton.setImageResource(R.drawable.ic_visibility_off)
                toggleButton.contentDescription = "Show password"
            }
            editText.setSelection(editText.text.length)
        }
    }

    private fun showMessage(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .show()
    }
}