package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.utils.TreePreferences

class VerifyEmailActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private var resendTimer: CountDownTimer? = null

    private lateinit var tvUserEmail: TextView
    private lateinit var btnCheckVerified: Button
    private lateinit var btnResendEmail: Button
    private lateinit var btnBackOrLogout: TextView
    private lateinit var btnChangeEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        tvUserEmail = findViewById(R.id.tvUserEmail)
        btnCheckVerified = findViewById(R.id.btnCheckVerified)
        btnResendEmail = findViewById(R.id.btnResendEmail)
        btnBackOrLogout = findViewById(R.id.btnBackOrLogout)
        btnChangeEmail = findViewById(R.id.btnChangeEmail)

        val currentUser = authHelper.getCurrentUser()
        val email = currentUser?.email.orEmpty().ifEmpty { "your email" }
        tvUserEmail.text = email

        btnCheckVerified.setOnClickListener {
            checkEmailVerificationStatus()
        }

        btnResendEmail.setOnClickListener {
            sendVerificationEmailWithCooldown()
        }

        btnBackOrLogout.setOnClickListener {
            signOutAndRedirect()
        }

        btnChangeEmail.setOnClickListener {
            signOutAndRedirect()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                signOutAndRedirect()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Auto-check on resume in case user clicked link in external browser/email client
        authHelper.reloadUser { user ->
            if (user != null && user.isEmailVerified && !isFinishing && !isDestroyed) {
                grantAccessAndGoHome()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
        resendTimer = null
    }

    private fun checkEmailVerificationStatus() {
        btnCheckVerified.isEnabled = false
        btnCheckVerified.text = "Checking status..."

        authHelper.reloadUser { user ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                btnCheckVerified.isEnabled = true
                btnCheckVerified.text = "I've Verified My Email"

                if (user != null && user.isEmailVerified) {
                    grantAccessAndGoHome()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Email Not Verified Yet")
                        .setMessage("We haven't received confirmation for ${tvUserEmail.text} yet.\n\n" +
                                "1. Please check your Inbox and Spam/Junk folder.\n" +
                                "2. If the email arrived in Spam, Gmail disables links: tap \"Report not spam\" (or \"Looks safe\") to make the link clickable.\n" +
                                "3. Alternatively, open the email link on a web browser or computer.\n" +
                                "4. After clicking the link, tap \"I've Verified My Email\" again.")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        }
    }

    private fun sendVerificationEmailWithCooldown() {
        btnResendEmail.isEnabled = false
        btnResendEmail.text = "Sending..."

        authHelper.sendEmailVerification(
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Verification email sent to ${tvUserEmail.text}!", Toast.LENGTH_SHORT).show()
                    startResendCooldown(60000L)
                }
            },
            onFailure = { err ->
                runOnUiThread {
                    btnResendEmail.isEnabled = true
                    btnResendEmail.text = "Resend Verification Link"
                    Toast.makeText(this, "Failed to send email: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun startResendCooldown(millis: Long) {
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(millis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                btnResendEmail.isEnabled = false
                btnResendEmail.text = "Resend link in ${sec}s"
            }

            override fun onFinish() {
                btnResendEmail.isEnabled = true
                btnResendEmail.text = "Resend Verification Link"
            }
        }.start()
    }

    private fun grantAccessAndGoHome() {
        Toast.makeText(this, "Email verified successfully! Welcome to KinTrace.", Toast.LENGTH_SHORT).show()
        TreePreferences.clear(this)
        FirestoreHelper.clearCache()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun signOutAndRedirect() {
        resendTimer?.cancel()
        authHelper.logout()
        TreePreferences.clear(this)
        FirestoreHelper.clearCache()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
