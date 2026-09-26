package com.example.btproject2

import android.app.Application
import com.example.btproject2.utils.ThemePreferences

/**
 * Custom Application class for KinTrace.
 * Initializes the global theme engine from ThemePreferences on process launch.
 */
class KinTraceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val savedTheme = ThemePreferences.getThemeMode(this)
        ThemePreferences.applyTheme(savedTheme)
    }
}

