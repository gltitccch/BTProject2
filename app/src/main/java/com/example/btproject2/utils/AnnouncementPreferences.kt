package com.example.btproject2.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user dismissals of system announcements to SharedPreferences.
 * Ensures that once a user taps '✕' on a maintenance advisory banner,
 * it will not appear again across app restarts for that specific advisory version.
 */
object AnnouncementPreferences {
    private const val PREFS_NAME = "kintrace_announcement_prefs"
    private const val KEY_PREFIX_DISMISSED = "dismissed_announcement_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if the announcement with the given ID and createdAt timestamp has been dismissed by the user.
     * If the announcement has been updated with a newer createdAt timestamp than the recorded dismissal,
     * it will return false so the updated advisory properly appears.
     */
    fun isDismissed(context: Context, announcementId: String, createdAt: Long): Boolean {
        if (announcementId.isBlank()) return false
        val prefs = getPrefs(context)
        val key = "$KEY_PREFIX_DISMISSED$announcementId"
        val dismissedTimestamp = prefs.getLong(key, -1L)
        return dismissedTimestamp >= createdAt
    }

    /**
     * Marks an announcement as permanently dismissed by saving its createdAt timestamp.
     */
    fun setDismissed(context: Context, announcementId: String, createdAt: Long) {
        if (announcementId.isBlank()) return
        val prefs = getPrefs(context)
        val key = "$KEY_PREFIX_DISMISSED$announcementId"
        prefs.edit().putLong(key, createdAt).apply()
    }

    private const val KEY_PREFIX_READ = "read_advisory_notif_"

    /**
     * Checks if the announcement has been marked as read inside the Notifications Hub.
     */
    fun isAdvisoryReadInNotifications(context: Context, announcementId: String, createdAt: Long): Boolean {
        if (announcementId.isBlank()) return true
        val prefs = getPrefs(context)
        val key = "$KEY_PREFIX_READ$announcementId"
        val readTimestamp = prefs.getLong(key, -1L)
        return readTimestamp >= createdAt
    }

    /**
     * Marks an announcement as read inside the Notifications Hub.
     */
    fun setAdvisoryReadInNotifications(context: Context, announcementId: String, createdAt: Long) {
        if (announcementId.isBlank()) return
        val prefs = getPrefs(context)
        val key = "$KEY_PREFIX_READ$announcementId"
        prefs.edit().putLong(key, createdAt).apply()
    }

    /**
     * Clears all dismissed announcement records (useful for testing or admin resets).
     */
    fun clearDismissed(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
