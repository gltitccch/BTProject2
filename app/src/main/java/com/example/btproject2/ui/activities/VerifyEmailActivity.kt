package com.example.btproject2.ui.activities

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

        val startTime = System.currentTimeMillis()
        authHelper.reloadUser { user ->
            val elapsed = System.currentTimeMillis() - startTime
            val remainingDelay = (1200L - elapsed).coerceAtLeast(0L)

            Handler(Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                btnCheckVerified.isEnabled = true
                btnCheckVerified.text = "I've Verified My Email"

                if (user != null && user.isEmailVerified) {
                    grantAccessAndGoHome()
                } else {
                    showEmailNotVerifiedDialog(tvUserEmail.text.toString())
                }
            }, remainingDelay)
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

        // Dispatch official Academic & Educational Welcome Notification (DFD Store D7)
        val currentUser = authHelper.getCurrentUser()
        if (currentUser != null) {
            val eduNotif = com.example.btproject2.utils.NotificationHelper.createEducationalWelcomeNotification(
                userId = currentUser.uid,
                userEmail = currentUser.email.orEmpty()
            )
            FirestoreHelper().addNotification(eduNotif)
        }

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

    private fun showEmailNotVerifiedDialog(email: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_email_not_verified)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvDialogUserEmail = dialog.findViewById<TextView>(R.id.tvDialogUserEmail)
        val layoutDialogStatusBanner = dialog.findViewById<LinearLayout>(R.id.layoutDialogStatusBanner)
        val tvDialogStatusIcon = dialog.findViewById<TextView>(R.id.tvDialogStatusIcon)
        val tvDialogStatusBanner = dialog.findViewById<TextView>(R.id.tvDialogStatusBanner)
        val btnDialogCheckStatus = dialog.findViewById<TextView>(R.id.btnDialogCheckStatus)
        val btnDialogOpenEmailApp = dialog.findViewById<TextView>(R.id.btnDialogOpenEmailApp)
        val btnDialogDismiss = dialog.findViewById<TextView>(R.id.btnDialogDismiss)

        tvDialogUserEmail?.text = email

        btnDialogCheckStatus?.setOnClickListener {
            // Option A: In-dialog loading state without dismissing
            layoutDialogStatusBanner?.visibility = View.GONE
            btnDialogCheckStatus.isEnabled = false
            btnDialogCheckStatus.text = "⏳ Checking Firebase..."

            val startTime = System.currentTimeMillis()
            authHelper.reloadUser { user ->
                val elapsed = System.currentTimeMillis() - startTime
                val remainingDelay = (1200L - elapsed).coerceAtLeast(0L)

                Handler(Looper.getMainLooper()).postDelayed({
                    if (isFinishing || isDestroyed || !dialog.isShowing) return@postDelayed

                    if (user != null && user.isEmailVerified) {
                        btnDialogCheckStatus.text = "✅ Verified!"
                        layoutDialogStatusBanner?.visibility = View.VISIBLE
                        tvDialogStatusIcon?.text = "✅"
                        tvDialogStatusBanner?.text = "Email confirmed! Redirecting to KinTrace..."
                        tvDialogStatusBanner?.setTextColor(ContextCompat.getColor(this@VerifyEmailActivity, R.color.mint_text))

                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isFinishing && !isDestroyed && dialog.isShowing) {
                                dialog.dismiss()
                            }
                            grantAccessAndGoHome()
                        }, 600L)
                    } else {
                        btnDialogCheckStatus.isEnabled = true
                        btnDialogCheckStatus.text = "🔄 Check Status Again"
                        layoutDialogStatusBanner?.visibility = View.VISIBLE
                        tvDialogStatusIcon?.text = "⚠️"
                        tvDialogStatusBanner?.text = "Still unverified. Please confirm the link in your email and try again."
                        tvDialogStatusBanner?.setTextColor(ContextCompat.getColor(this@VerifyEmailActivity, R.color.warning))
                    }
                }, remainingDelay)
            }
        }

        btnDialogOpenEmailApp?.setOnClickListener {
            dialog.dismiss()
            openDefaultEmailApp()
        }

        btnDialogDismiss?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openDefaultEmailApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val mailtoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
                startActivity(mailtoIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Please open your email application manually.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

