package com.example.btproject2.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.btproject2.models.FamilyTree
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for the locally active Family Tree selection across KinTrace.
 *
 * Persists both the active session tree ID/title and per-user remembered tree mappings
 * keyed by Firebase UID so that switching accounts or launching on spotty network
 * immediately resolves the user's trees without latency or premature empty states.
 */
object TreePreferences {

    const val PREFS_NAME = "kintrace_tree_prefs"
    const val KEY_ACTIVE_TREE_ID = "active_tree_id"
    const val KEY_ACTIVE_TREE_NAME = "active_tree_name"
    private const val PREFIX_USER_TREE_ID = "user_tree_id_"
    private const val PREFIX_USER_TREE_NAME = "user_tree_name_"
    private const val PREFIX_USER_TREES_JSON = "user_trees_json_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun resolveCurrentAuthUidSafely(): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        } catch (_: Throwable) {
            ""
        }
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
     * Sets the active family tree globally and remembers it for the given user ID.
     */
    fun setActiveTree(
        context: Context,
        treeId: String,
        treeName: String = "",
        userId: String = resolveCurrentAuthUidSafely()
    ) {
        val editor = getPrefs(context).edit()
            .putString(KEY_ACTIVE_TREE_ID, treeId)
            .putString(KEY_ACTIVE_TREE_NAME, treeName)
        if (userId.isNotBlank() && treeId.isNotBlank()) {
            editor.putString(PREFIX_USER_TREE_ID + userId, treeId)
            editor.putString(PREFIX_USER_TREE_NAME + userId, treeName)
        }
        editor.apply()
    }

    /**
     * Returns the last active tree ID remembered for a specific user ID on this device.
     */
    fun getRememberedTreeIdForUser(context: Context, userId: String): String {
        if (userId.isBlank()) return ""
        return getPrefs(context).getString(PREFIX_USER_TREE_ID + userId, "").orEmpty()
    }

    /**
     * Returns the last active tree name remembered for a specific user ID on this device.
     */
    fun getRememberedTreeNameForUser(context: Context, userId: String): String {
        if (userId.isBlank()) return ""
        return getPrefs(context).getString(PREFIX_USER_TREE_NAME + userId, "").orEmpty()
    }

    /**
     * Caches the list of trees (owned and joined) for a specific user ID on this device.
     */
    fun saveUserTreesList(context: Context, userId: String, trees: List<FamilyTree>) {
        if (userId.isBlank()) return
        val arr = JSONArray()
        trees.forEach { tree ->
            if (tree.id.isNotBlank()) {
                val obj = JSONObject().apply {
                    put("id", tree.id)
                    put("name", tree.name)
                    put("ownerId", tree.ownerId)
                    put("ownerName", tree.ownerName)
                    put("inviteCode", tree.inviteCode)
                    put("isPrivate", tree.isPrivate)
                    put("treeType", tree.treeType)
                }
                arr.put(obj)
            }
        }
        getPrefs(context).edit()
            .putString(PREFIX_USER_TREES_JSON + userId, arr.toString())
            .apply()
    }

    /**
     * Returns the cached list of trees (owned and joined) for a specific user ID on this device.
     */
    fun getRememberedTreesForUser(context: Context, userId: String): List<FamilyTree> {
        if (userId.isBlank()) return emptyList()
        val raw = getPrefs(context).getString(PREFIX_USER_TREES_JSON + userId, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<FamilyTree>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isNotBlank()) {
                    list.add(
                        FamilyTree(
                            id = id,
                            name = obj.optString("name", "Family Tree"),
                            ownerId = obj.optString("ownerId", ""),
                            ownerName = obj.optString("ownerName", ""),
                            inviteCode = obj.optString("inviteCode", ""),
                            isPrivate = obj.optBoolean("isPrivate", false),
                            treeType = obj.optString("treeType", FamilyTree.TREE_TYPE_PERSONAL)
                        )
                    )
                }
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Clears the remembered tree mappings for a specific user when Firestore confirms they have no trees.
     */
    fun clearUserRememberedTree(context: Context, userId: String) {
        if (userId.isBlank()) return
        getPrefs(context).edit()
            .remove(PREFIX_USER_TREE_ID + userId)
            .remove(PREFIX_USER_TREE_NAME + userId)
            .remove(PREFIX_USER_TREES_JSON + userId)
            .apply()
    }

    /**
     * Clears the current session's active tree selection upon user logout or account switch,
     * while preserving per-user remembered tree mappings keyed by userId.
     */
    fun clear(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ACTIVE_TREE_ID)
            .remove(KEY_ACTIVE_TREE_NAME)
            .apply()
    }
}

