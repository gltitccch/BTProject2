package com.example.btproject2.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for the locally active Family Tree selection across KinTrace.
 *
 * Persists the currently active tree ID and tree title so that all screens,
 * coordinators, and real-time synchronizers immediately resolve the user's active
 * tree without latency or hardcoded fallbacks.
 */
object TreePreferences {

    const val PREFS_NAME = "kintrace_tree_prefs"
    const val KEY_ACTIVE_TREE_ID = "active_tree_id"
    const val KEY_ACTIVE_TREE_NAME = "active_tree_name"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the active tree ID, or empty string if no tree is currently active.
     */
    fun getActiveTreeId(context: Context): String {
        return getPrefs(context).getString(KEY_ACTIVE_TREE_ID, "").orEmpty()
    }

    /**
     * Returns the cached active tree title, or empty string if not set.
     */
    fun getActiveTreeName(context: Context): String {
        return getPrefs(context).getString(KEY_ACTIVE_TREE_NAME, "").orEmpty()
    }

    /**
     * Sets the active family tree globally.
     */
    fun setActiveTree(context: Context, treeId: String, treeName: String = "") {
        getPrefs(context).edit()
            .putString(KEY_ACTIVE_TREE_ID, treeId)
            .putString(KEY_ACTIVE_TREE_NAME, treeName)
            .apply()
    }

    /**
     * Clears the active tree selection upon user logout.
     */
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}

