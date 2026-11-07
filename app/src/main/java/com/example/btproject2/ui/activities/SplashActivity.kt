package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.ui.views.SplashBotanicalView

class SplashActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        findViewById<SplashBotanicalView>(R.id.splashBotanicalView)?.refreshThemePalette()

        handler.postDelayed(navigateRunnable, 2400L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }
}
