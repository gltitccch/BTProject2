package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authHelper.login(email, password,
                onSuccess = {
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    goToHome()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Login failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            authHelper.register(email, password,
                onSuccess = {
                    Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show()
                    goToHome()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Registration failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}