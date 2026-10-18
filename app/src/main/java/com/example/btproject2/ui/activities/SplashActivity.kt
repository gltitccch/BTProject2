package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper

class SplashActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val user = authHelper.getCurrentUser()
            if (user != null) {
                if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(user.email)) {
                    val intent = Intent(this, AdminPortalActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                } else if (user.isEmailVerified || com.example.btproject2.models.DemoAccountRepository.isDemoEmail(user.email)) {
                    val intent = Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this, VerifyEmailActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
            } else {
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            finish()
        }, 2000)
    }
}