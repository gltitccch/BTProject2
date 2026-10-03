package com.example.btproject2.models

import com.google.firebase.firestore.PropertyName

/**
 * Encapsulates the multi-stage onboarding state for a KinTrace user.
 *
 * Coordinates both Phase 1 (Pamana at Lahi Welcome Storybook Modal)
 * and Phase 2 (Clan Builder Quest HUD on Home Dashboard).
 */
data class UserOnboardingState(
    @get:PropertyName("hasSeenWelcomeStory") @set:PropertyName("hasSeenWelcomeStory")
    var hasSeenWelcomeStory: Boolean = false,

    @get:PropertyName("questRootsCompleted") @set:PropertyName("questRootsCompleted")
    var questRootsCompleted: Boolean = false,

    @get:PropertyName("questTwoMembersCompleted") @set:PropertyName("questTwoMembersCompleted")
    var questTwoMembersCompleted: Boolean = false,

    @get:PropertyName("questCanvasInspected") @set:PropertyName("questCanvasInspected")
    var questCanvasInspected: Boolean = false,

    @get:PropertyName("questCodeShared") @set:PropertyName("questCodeShared")
    var questCodeShared: Boolean = false,

    @get:PropertyName("isQuestDismissed") @set:PropertyName("isQuestDismissed")
    var isQuestDismissed: Boolean = false,

    @get:PropertyName("isQuestCollapsed") @set:PropertyName("isQuestCollapsed")
    var isQuestCollapsed: Boolean = false,

    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated")
    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Counts how many of the 4 core clan milestones are achieved.
     */
    fun getCompletedCount(): Int {
        var count = 0
        if (questRootsCompleted) count++
        if (questTwoMembersCompleted) count++
        if (questCanvasInspected) count++
        if (questCodeShared) count++
        return count
    }

    /**
     * Calculates completion percentage (0%, 25%, 50%, 75%, or 100%).
     */
    fun getProgressPercentage(): Int {
        return (getCompletedCount() / 4.0 * 100).toInt()
    }

    /**
     * Returns true if all 4 milestones are complete.
     */
    fun isAllCompleted(): Boolean = getCompletedCount() >= 4
}
