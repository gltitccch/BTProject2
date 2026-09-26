package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.UserProfile
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
        val btnCreate = findViewById<Button>(R.id.btnCreateAccount)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        btnBack.setOnClickListener { finish() }

        btnCreate.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showMessage("Error", "Please fill in all fields.")
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                showMessage("Error", "Passwords do not match.")
                return@setOnClickListener
            }

            if (password.length < 6) {
                showMessage("Error", "Password must be at least 6 characters.")
                return@setOnClickListener
            }

            authHelper.register(email, password,
                onSuccess = { user ->
                    authHelper.updateDisplayName(fullName, onSuccess = {}, onFailure = {})
                    TreePreferences.clear(this)
                    FirestoreHelper.clearCache()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()
                    val profile = UserProfile(
                        id = user.uid,
                        displayName = fullName,
                        email = email,
                        role = "user",
                        currentTreeId = ""
                    )
                    firestoreHelper.saveUserProfile(profile, onSuccess = {}, onFailure = {})

                    showMessage("Success", "Account created successfully! Welcome to KinTrace.") {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                },
                onFailure = { error ->
                    showMessage("Registration Failed", error.message ?: "Unknown error occurred.")
                }
            )
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
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