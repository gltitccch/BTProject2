package com.example.btproject2.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.models.UserOnboardingState

/**
 * Fast, local-first SharedPreferences cache for KinTrace onboarding state.
 *
 * Scoped by authenticated user UID so that different accounts on the same device
 * maintain isolated quest progress, preventing new accounts from inheriting
 * completed milestones from previous users.
 */
object OnboardingPreferences {

    const val PREFS_NAME = "kintrace_onboarding_prefs"

    const val KEY_HAS_SEEN_WELCOME_STORY = "has_seen_welcome_story"
    const val KEY_QUEST_ROOTS_COMPLETED = "quest_roots_completed"
    const val KEY_QUEST_TWO_MEMBERS_COMPLETED = "quest_two_members_completed"
    const val KEY_QUEST_CANVAS_INSPECTED = "quest_canvas_inspected"
    const val KEY_QUEST_CODE_SHARED = "quest_code_shared"
    const val KEY_IS_QUEST_DISMISSED = "is_quest_dismissed"
    const val KEY_IS_QUEST_COLLAPSED = "is_quest_collapsed"
    const val KEY_LAST_UPDATED = "last_updated"

    private fun getPrefs(context: Context, userId: String? = null): SharedPreferences {
        val uid = if (!userId.isNullOrBlank()) {
            userId
        } else {
            try {
                AuthHelper().getCurrentUserId().orEmpty()
            } catch (e: Exception) {
                ""
            }
        }
        val name = if (uid.isNotEmpty()) "${PREFS_NAME}_$uid" else PREFS_NAME
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getOnboardingState(context: Context, userId: String? = null): UserOnboardingState {
        val prefs = getPrefs(context, userId)
        return UserOnboardingState(
            hasSeenWelcomeStory = prefs.getBoolean(KEY_HAS_SEEN_WELCOME_STORY, false),
            questRootsCompleted = prefs.getBoolean(KEY_QUEST_ROOTS_COMPLETED, false),
            questTwoMembersCompleted = prefs.getBoolean(KEY_QUEST_TWO_MEMBERS_COMPLETED, false),
            questCanvasInspected = prefs.getBoolean(KEY_QUEST_CANVAS_INSPECTED, false),
            questCodeShared = prefs.getBoolean(KEY_QUEST_CODE_SHARED, false),
            isQuestDismissed = prefs.getBoolean(KEY_IS_QUEST_DISMISSED, false),
            isQuestCollapsed = prefs.getBoolean(KEY_IS_QUEST_COLLAPSED, false),
            lastUpdated = prefs.getLong(KEY_LAST_UPDATED, System.currentTimeMillis())
        )
    }

    fun saveOnboardingState(context: Context, state: UserOnboardingState, userId: String? = null) {
        getPrefs(context, userId).edit()
            .putBoolean(KEY_HAS_SEEN_WELCOME_STORY, state.hasSeenWelcomeStory)
            .putBoolean(KEY_QUEST_ROOTS_COMPLETED, state.questRootsCompleted)
            .putBoolean(KEY_QUEST_TWO_MEMBERS_COMPLETED, state.questTwoMembersCompleted)
            .putBoolean(KEY_QUEST_CANVAS_INSPECTED, state.questCanvasInspected)
            .putBoolean(KEY_QUEST_CODE_SHARED, state.questCodeShared)
            .putBoolean(KEY_IS_QUEST_DISMISSED, state.isQuestDismissed)
            .putBoolean(KEY_IS_QUEST_COLLAPSED, state.isQuestCollapsed)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun hasSeenWelcomeStory(context: Context, userId: String? = null): Boolean {
        return getPrefs(context, userId).getBoolean(KEY_HAS_SEEN_WELCOME_STORY, false)
    }

    fun setHasSeenWelcomeStory(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_HAS_SEEN_WELCOME_STORY, value).apply()
    }

    fun isQuestDismissed(context: Context, userId: String? = null): Boolean {
        return getPrefs(context, userId).getBoolean(KEY_IS_QUEST_DISMISSED, false)
    }

    fun setQuestDismissed(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_IS_QUEST_DISMISSED, value).apply()
    }

    fun isQuestCollapsed(context: Context, userId: String? = null): Boolean {
        return getPrefs(context, userId).getBoolean(KEY_IS_QUEST_COLLAPSED, false)
    }

    fun setQuestCollapsed(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_IS_QUEST_COLLAPSED, value).apply()
    }

    fun setMilestoneRoots(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_QUEST_ROOTS_COMPLETED, value).apply()
    }

    fun setMilestoneTwoMembers(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_QUEST_TWO_MEMBERS_COMPLETED, value).apply()
    }

    fun setMilestoneCanvasInspected(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_QUEST_CANVAS_INSPECTED, value).apply()
    }

    fun setMilestoneCodeShared(context: Context, value: Boolean, userId: String? = null) {
        getPrefs(context, userId).edit().putBoolean(KEY_QUEST_CODE_SHARED, value).apply()
    }

    fun resetOnboardingState(context: Context, userId: String? = null) {
        getPrefs(context, userId).edit().clear().apply()
    }

    fun clear(context: Context, userId: String? = null) {
        getPrefs(context, userId).edit().clear().apply()
    }
}
