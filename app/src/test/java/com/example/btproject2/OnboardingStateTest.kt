package com.example.btproject2

import com.example.btproject2.models.UserOnboardingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying the multi-stage onboarding state and progress calculation logic.
 */
class OnboardingStateTest {

    @Test
    fun testDefaultState_isZeroProgress() {
        val state = UserOnboardingState()
        assertFalse(state.hasSeenWelcomeStory)
        assertFalse(state.questRootsCompleted)
        assertFalse(state.questTwoMembersCompleted)
        assertFalse(state.questCanvasInspected)
        assertFalse(state.questCodeShared)
        assertFalse(state.isQuestDismissed)
        assertFalse(state.isQuestCollapsed)

        assertEquals(0, state.getCompletedCount())
        assertEquals(0, state.getProgressPercentage())
        assertFalse(state.isAllCompleted())
    }

    @Test
    fun testMilestoneProgress_sequentialCompletion() {
        val state = UserOnboardingState()

        // 1. Roots established (25%)
        state.questRootsCompleted = true
        assertEquals(1, state.getCompletedCount())
        assertEquals(25, state.getProgressPercentage())
        assertFalse(state.isAllCompleted())

        // 2. Two members added (50%)
        state.questTwoMembersCompleted = true
        assertEquals(2, state.getCompletedCount())
        assertEquals(50, state.getProgressPercentage())
        assertFalse(state.isAllCompleted())

        // 3. Canvas inspected (75%)
        state.questCanvasInspected = true
        assertEquals(3, state.getCompletedCount())
        assertEquals(75, state.getProgressPercentage())
        assertFalse(state.isAllCompleted())

        // 4. Code shared (100%)
        state.questCodeShared = true
        assertEquals(4, state.getCompletedCount())
        assertEquals(100, state.getProgressPercentage())
        assertTrue(state.isAllCompleted())
    }

    @Test
    fun testMilestoneProgress_outOfOrderCompletion() {
        val state = UserOnboardingState()

        // User shares code first (25%)
        state.questCodeShared = true
        assertEquals(1, state.getCompletedCount())
        assertEquals(25, state.getProgressPercentage())
        assertFalse(state.isAllCompleted())

        // User inspects canvas (50%)
        state.questCanvasInspected = true
        assertEquals(2, state.getCompletedCount())
        assertEquals(50, state.getProgressPercentage())
        assertFalse(state.isAllCompleted())
    }

    @Test
    fun testDismissAndCollapseFlags() {
        val state = UserOnboardingState(
            hasSeenWelcomeStory = true,
            isQuestCollapsed = true,
            isQuestDismissed = true
        )
        assertTrue(state.hasSeenWelcomeStory)
        assertTrue(state.isQuestCollapsed)
        assertTrue(state.isQuestDismissed)
    }

    @Test
    fun testMultiUserIsolation_independentAccountStates() {
        // User A has completed 3 quests (75%) on their account
        val userAState = UserOnboardingState(
            hasSeenWelcomeStory = true,
            questRootsCompleted = true,
            questCanvasInspected = true,
            questCodeShared = true
        )
        assertEquals(3, userAState.getCompletedCount())
        assertEquals(75, userAState.getProgressPercentage())

        // User B creates a brand new account: must start strictly isolated at 0%
        val userBState = UserOnboardingState()
        assertEquals(0, userBState.getCompletedCount())
        assertEquals(0, userBState.getProgressPercentage())
        assertFalse("New user must NOT inherit User A's roots completion", userBState.questRootsCompleted)
        assertFalse("New user must NOT inherit User A's canvas inspection", userBState.questCanvasInspected)
        assertFalse("New user must NOT inherit User A's code sharing", userBState.questCodeShared)
        assertFalse("New user must NOT inherit User A's welcome story state", userBState.hasSeenWelcomeStory)

        // Verifying that updates to User A never bleed into User B
        userAState.questTwoMembersCompleted = true
        assertEquals(100, userAState.getProgressPercentage())
        assertTrue(userAState.isAllCompleted())
        assertEquals("User B must remain unaffected at 0%", 0, userBState.getProgressPercentage())
    }

    @Test
    fun testPhaseTransition_rootsMilestoneValidation() {
        // Phase 1 (No trees yet): Milestone 1 must strictly evaluate to false
        val userTrees = emptyList<com.example.btproject2.models.FamilyTree>()
        val hasTree = userTrees.isNotEmpty()
        val phase1State = UserOnboardingState()

        if (!hasTree) {
            phase1State.questRootsCompleted = false
        }
        assertEquals("Phase 1 must have 0% progress", 0, phase1State.getProgressPercentage())
        assertFalse(phase1State.questRootsCompleted)

        // Phase 2 (User creates or joins a tree): Milestone 1 is achieved
        val createdTrees = listOf(com.example.btproject2.models.FamilyTree(id = "tree_new", name = "Santos Family"))
        val hasTreeNow = createdTrees.isNotEmpty()
        if (hasTreeNow) {
            phase1State.questRootsCompleted = true
        }
        assertEquals("Phase 2 entry must yield 25% progress", 25, phase1State.getProgressPercentage())
        assertTrue(phase1State.questRootsCompleted)
    }
}
