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

class LoginActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (authHelper.getCurrentUser() != null) {
            goToHome()
            return
        }

        setContentView(R.layout.activity_login)

        val btnBack = findViewById<TextView>(R.id.btnBackToWelcome)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<TextView>(R.id.btnRegister)
        val btnForgotPassword = findViewById<TextView>(R.id.btnForgotPassword)

        btnBack.setOnClickListener { finish() }

        btnForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showMessage("Error", "Please fill in all fields.")
                return@setOnClickListener
            }

            authHelper.login(email, password,
                onSuccess = {
                    com.example.btproject2.utils.TreePreferences.clear(this)
                    com.example.btproject2.firebase.FirestoreHelper.clearCache()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()
                    goToHome()
                },
                onFailure = { error ->
                    showMessage("Login Failed", error.message ?: "Unknown error occurred.")
                }
            )
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
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