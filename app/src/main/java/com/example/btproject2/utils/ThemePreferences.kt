package com.example.btproject2.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * Single source of truth for the application's theme state (Light, Dark, System Default).
 * Persists user preference via SharedPreferences and immediately triggers AppCompatDelegate.
 */
object ThemePreferences {

    private const val PREFS_NAME = "kintrace_theme_prefs"
    private const val KEY_THEME_MODE = "key_theme_mode"

    const val THEME_SYSTEM = "SYSTEM_DEFAULT"
    const val THEME_LIGHT = "LIGHT"
    const val THEME_DARK = "DARK"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves the stored theme preference mode string.
     * Defaults to [THEME_DARK] ("Forest Green") to preserve the existing default experience.
     */
    fun getThemeMode(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
    }

    /**
     * Persists the selected theme mode and applies it immediately across the application.
     */
    fun setThemeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
        applyTheme(mode)
    }

    /**
     * Tells AppCompatDelegate which night mode to use globally.
     */
    fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * Returns true if the currently effective appearance is dark (either explicitly set to DARK
     * or SYSTEM_DEFAULT matching system night mode).
     */
    fun isDarkTheme(context: Context): Boolean {
        val mode = getThemeMode(context)
        return when (mode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            THEME_SYSTEM -> {
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
            else -> true
        }
    }
}

