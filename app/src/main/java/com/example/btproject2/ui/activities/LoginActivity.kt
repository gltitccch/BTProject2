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

class LoginActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = authHelper.getCurrentUser()
        if (currentUser != null) {
            if (currentUser.isEmailVerified) {
                goToHome()
            } else {
                startActivity(Intent(this, VerifyEmailActivity::class.java))
                finish()
            }
            return
        }

        setContentView(R.layout.activity_login)

        val btnBack = findViewById<TextView>(R.id.btnBackToWelcome)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnTogglePassword = findViewById<ImageView>(R.id.btnTogglePassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<TextView>(R.id.btnRegister)
        val btnForgotPassword = findViewById<TextView>(R.id.btnForgotPassword)

        btnBack.setOnClickListener { finish() }

        // Setup password show/hide visibility toggle
        setupPasswordToggle(etPassword, btnTogglePassword)

        // Clear errors in real-time
        etEmail.doOnTextChanged { _, _, _, _ -> etEmail.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> etPassword.error = null }

        btnForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email address is required."
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Password is required."
                etPassword.requestFocus()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            authHelper.login(email, password,
                onSuccess = { user ->
                    com.example.btproject2.utils.TreePreferences.clear(this)
                    com.example.btproject2.firebase.FirestoreHelper.clearCache()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

                    if (user.isEmailVerified) {
                        goToHome()
                    } else {
                        val intent = Intent(this, VerifyEmailActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                },
                onFailure = { error ->
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"
                    showMessage("Login Failed", error.message ?: "Unknown error occurred.")
                }
            )
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
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

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}