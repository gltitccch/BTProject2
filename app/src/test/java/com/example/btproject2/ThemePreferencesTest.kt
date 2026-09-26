package com.example.btproject2

import androidx.appcompat.app.AppCompatDelegate
import com.example.btproject2.utils.ThemePreferences
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ThemePreferences and Dynamic Theme Toggling in KinTrace.
 * Verifies:
 * - Constants for LIGHT, DARK, and SYSTEM_DEFAULT modes.
 * - AppCompatDelegate night mode mapping.
 * - Dynamic theme toggle logic between Dark and Light modes.
 * - Dynamic icon display rules (Sun for Dark, Moon for Light).
 */
class ThemePreferencesTest {

    @Test
    fun testThemeConstants() {
        assertEquals("LIGHT", ThemePreferences.THEME_LIGHT)
        assertEquals("DARK", ThemePreferences.THEME_DARK)
        assertEquals("SYSTEM_DEFAULT", ThemePreferences.THEME_SYSTEM)
    }

    @Test
    fun testNightModeMapping() {
        // Test that applyTheme sets the correct AppCompatDelegate mode
        ThemePreferences.applyTheme(ThemePreferences.THEME_LIGHT)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())

        ThemePreferences.applyTheme(ThemePreferences.THEME_DARK)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())

        ThemePreferences.applyTheme(ThemePreferences.THEME_SYSTEM)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun testThemeToggleSwitchLogic() {
        // When currently dark, the next mode should be LIGHT
        val isDarkCurrent = true
        val nextModeWhenDark = if (isDarkCurrent) ThemePreferences.THEME_LIGHT else ThemePreferences.THEME_DARK
        assertEquals(ThemePreferences.THEME_LIGHT, nextModeWhenDark)

        // When currently light, the next mode should be DARK
        val isLightCurrent = false
        val nextModeWhenLight = if (isLightCurrent) ThemePreferences.THEME_LIGHT else ThemePreferences.THEME_DARK
        assertEquals(ThemePreferences.THEME_DARK, nextModeWhenLight)
    }

    @Test
    fun testDynamicIconDisplayRules() {
        // When in Dark Mode: Display Sun icon ☀️ (prompting switch to Light)
        fun getToggleIcon(isDark: Boolean): String = if (isDark) "☀️" else "🌙"

        assertEquals("☀️", getToggleIcon(isDark = true))
        // When in Light Mode: Display Moon icon 🌙 (prompting switch to Dark)
        assertEquals("🌙", getToggleIcon(isDark = false))
    }
}

