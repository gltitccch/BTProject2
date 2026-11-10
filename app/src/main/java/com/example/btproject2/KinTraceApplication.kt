package com.example.btproject2

import android.app.Application
import com.example.btproject2.services.MilestoneNotificationManager
import com.example.btproject2.utils.ThemePreferences

/**
 * Custom Application class for KinTrace.
 * Initializes the global theme engine from ThemePreferences on process launch
 * and configures the native Android Lock Screen milestone notification channel.
 */
class KinTraceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val savedTheme = ThemePreferences.getThemeMode(this)
        ThemePreferences.applyTheme(savedTheme)

        // Initialize Android notification channel and daily 8:00 AM alarm
        MilestoneNotificationManager.createNotificationChannel(this)
        MilestoneNotificationManager.scheduleDailyMorningAlarm(this)
    }
}

