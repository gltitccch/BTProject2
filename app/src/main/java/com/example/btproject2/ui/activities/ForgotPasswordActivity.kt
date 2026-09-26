package com.example.btproject2.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.utils.NotificationHelper

class ForgotPasswordActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()

    private lateinit var etEmail: EditText
    private lateinit var btnSendReset: Button
    private lateinit var cardSuccess: LinearLayout
    private lateinit var tvSuccessMessage: TextView
    private lateinit var btnOpenEmailApp: Button
    private lateinit var btnResend: TextView
    private lateinit var tvResendCooldown: TextView

    private var resendTimer: CountDownTimer? = null
    private var isResendCooldownActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        etEmail = findViewById(R.id.etEmail)
        btnSendReset = findViewById(R.id.btnSendReset)
        cardSuccess = findViewById(R.id.cardSuccess)
        tvSuccessMessage = findViewById(R.id.tvSuccessMessage)
        btnOpenEmailApp = findViewById(R.id.btnOpenEmailApp)
        btnResend = findViewById(R.id.btnResend)
        tvResendCooldown = findViewById(R.id.tvResendCooldown)

        btnBack.setOnClickListener { finish() }

        btnSendReset.setOnClickListener {
            attemptPasswordReset()
        }

        btnResend.setOnClickListener {
            if (!isResendCooldownActive) {
                attemptPasswordReset()
            }
        }

        btnOpenEmailApp.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val mailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
                    startActivity(mailIntent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "Please open your email application manually", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }

    private fun attemptPasswordReset() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Please enter your registered email address.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (isResendCooldownActive) {
            Toast.makeText(this, "Please wait before requesting another reset email", Toast.LENGTH_SHORT).show()
            return
        }

        btnSendReset.isEnabled = false
        btnSendReset.text = "Sending..."

        authHelper.sendPasswordResetEmail(
            email,
            onSuccess = {
                btnSendReset.isEnabled = true
                btnSendReset.text = "Send reset link"
                cardSuccess.visibility = View.VISIBLE
                tvSuccessMessage.text = "A password recovery link has been dispatched to $email."

                startResendCountdown(60)

                // 8.d Account Recovery Notification (DFD D7)
                val notif = NotificationHelper.createAccountRecoveryNotification(email)
                firestoreHelper.addNotification(notif)

                Toast.makeText(this, "Password reset email sent! Check your Inbox and Spam/Junk.", Toast.LENGTH_LONG).show()
            },
            onFailure = { error ->
                btnSendReset.isEnabled = true
                btnSendReset.text = "Send reset link"
                AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(error.message ?: "Failed to send reset email. Please verify your address.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    private fun startResendCountdown(seconds: Long) {
        isResendCooldownActive = true
        btnResend.isEnabled = false
        btnResend.setTextColor(resources.getColor(R.color.text_hint, null))
        tvResendCooldown.visibility = View.VISIBLE

        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvResendCooldown.text = "Resend available in ${sec}s"
            }

            override fun onFinish() {
                isResendCooldownActive = false
                btnResend.isEnabled = true
                btnResend.setTextColor(resources.getColor(R.color.primary, null))
                tvResendCooldown.visibility = View.GONE
            }
        }.start()
    }
}

