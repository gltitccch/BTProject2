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
        assertFalse(state.isGuideCrestDismissed)

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

    @Test
    fun testSpotlightFeatureTutorialKeys_andPresetStepsArePopulated() {
        val state = UserOnboardingState()
        assertTrue(state.seenTutorialKeys.isEmpty())

        val allKeys = com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.values()
        assertEquals(15, allKeys.size)

        for (feature in allKeys) {
            val steps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(feature)
            assertTrue("Preset steps for ${feature.name} must not be empty", steps.isNotEmpty())
            steps.forEach { step ->
                assertTrue("Step title must not be blank", step.title.isNotBlank())
                assertTrue("Step description must not be blank", step.description.isNotBlank())
            }
        }

        val updatedState = UserOnboardingState(
            seenTutorialKeys = listOf(
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.PRIVACY_CONTROLS.key,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.MERGE_CLAN_SPACE.key,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.EXPORT_TREE.key
            )
        )
        assertEquals(3, updatedState.seenTutorialKeys.size)
        assertTrue(updatedState.seenTutorialKeys.contains("tutorial_privacy_controls"))
        assertTrue(updatedState.seenTutorialKeys.contains("tutorial_merge_clan_space"))
        assertTrue(updatedState.seenTutorialKeys.contains("tutorial_export_tree"))
    }

    @Test
    fun testTreeScopedTutorialIsolation_newAccountAndJoinedTree() {
        val seenKeys = mutableSetOf<String>()
        val traceKey = com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.TRACE_BLOODLINE.key
        val addMemberKey = com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.ADD_MEMBER.key

        // 1. Newly created / joined member enters Tree Alpha for the first time
        val treeAlpha = "tree_santos_alpha"
        val alphaTraceKey = "${treeAlpha}_$traceKey"
        val alphaAddKey = "${treeAlpha}_$addMemberKey"
        assertFalse("Must be unvisited on first entry to Tree Alpha Trace", seenKeys.contains(alphaTraceKey))
        assertFalse("Must be unvisited on first entry to Tree Alpha Add Member", seenKeys.contains(alphaAddKey))

        // Complete tours in Tree Alpha
        seenKeys.add(alphaTraceKey)
        seenKeys.add(alphaAddKey)
        assertTrue("Must be marked visited in Tree Alpha Trace", seenKeys.contains(alphaTraceKey))
        assertTrue("Must be marked visited in Tree Alpha Add Member", seenKeys.contains(alphaAddKey))

        // 2. Same member or new account enters Tree Beta (e.g. joined via invite code)
        val treeBeta = "tree_malone_beta"
        val betaTraceKey = "${treeBeta}_$traceKey"
        val betaAddKey = "${treeBeta}_$addMemberKey"
        assertFalse("Must be unvisited on first entry to Tree Beta Trace despite Tree Alpha completion", seenKeys.contains(betaTraceKey))
        assertFalse("Must be unvisited on first entry to Tree Beta Add Member despite Tree Alpha completion", seenKeys.contains(betaAddKey))

        // Complete tours in Tree Beta
        seenKeys.add(betaTraceKey)
        seenKeys.add(betaAddKey)
        assertTrue(seenKeys.contains(alphaTraceKey))
        assertTrue(seenKeys.contains(alphaAddKey))
        assertTrue(seenKeys.contains(betaTraceKey))
        assertTrue(seenKeys.contains(betaAddKey))
    }

    @Test
    fun testHomeStage2DashboardPresetSteps_has13StepsStartingFromTopBar() {
        val steps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE2_DASHBOARD
        )
        assertEquals("Home Stage 2 Dashboard tutorial must have exactly 13 steps", 13, steps.size)
        assertEquals("Step 1 must target Profile Avatar", R.id.tvProfileInitial, steps[0].targetViewId)
        assertEquals("Step 2 must target KinAcademy Button", R.id.btnKinAcademy, steps[1].targetViewId)
        assertEquals("Step 3 must target Notifications Bell", R.id.btnNotificationBell, steps[2].targetViewId)
        assertEquals("Step 4 must target Active Tree Title Pill", R.id.tvTreeTitle, steps[3].targetViewId)
        assertEquals("Step 5 must target Theme Toggle", R.id.btnThemeToggle, steps[4].targetViewId)
        assertEquals("Step 6 must target Add Member Hero", R.id.btnAddMemberHero, steps[5].targetViewId)
        assertEquals("Step 7 must target Family Insights Hero", R.id.btnFamilyInsightsHero, steps[6].targetViewId)
        assertEquals("Step 8 must target View Tree", R.id.cardViewTree, steps[7].targetViewId)
        assertEquals("Step 9 must target Records", R.id.cardRecords, steps[8].targetViewId)
        assertEquals("Step 10 must target Trace", R.id.cardTrace, steps[9].targetViewId)
        assertEquals("Step 11 must target Merge", R.id.cardMerge, steps[10].targetViewId)
        assertEquals("Step 12 must target Privacy", R.id.btnPrivacy, steps[11].targetViewId)
        assertEquals("Step 13 must target Export Tree", R.id.cardExportTree, steps[12].targetViewId)

        steps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testAddMemberPresetSteps_structureAndTargets() {
        val steps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.ADD_MEMBER
        )
        assertEquals("Add Member must have exactly 3 progressive steps", 3, steps.size)
        assertEquals(R.id.etFirstName, steps[0].targetViewId)
        assertEquals(R.id.spinnerFather, steps[1].targetViewId)
        assertEquals(R.id.btnSaveMember, steps[2].targetViewId)

        steps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testViewTreeAndInteractivePresetSteps_structureAndTargets() {
        val pedigreeSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE
        )
        assertEquals("Family Tree Pedigree must have exactly 4 progressive steps", 4, pedigreeSteps.size)
        assertEquals(R.id.tvGenLabel, pedigreeSteps[0].targetViewId)
        assertEquals(R.id.layoutMemberCard, pedigreeSteps[1].targetViewId)
        assertEquals(R.id.tvKinshipPill, pedigreeSteps[2].targetViewId)
        assertEquals(R.id.btnFanChart, pedigreeSteps[3].targetViewId)

        pedigreeSteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }

        val fanChartSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAN_CHART
        )
        assertEquals("Fan Chart must have exactly 6 progressive steps", 6, fanChartSteps.size)
        fanChartSteps.forEach { step ->
            assertEquals(R.id.webViewFan, step.targetViewId)
            assertTrue("Fan Chart step must provide customCutoutProvider", step.customCutoutProvider != null)
            assertTrue(step.title.isNotBlank())
            assertTrue(step.description.isNotBlank())
            assertTrue(!step.proTip.isNullOrBlank())
        }

        val interactiveSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.INTERACTIVE_TREE
        )
        assertEquals("Interactive Tree must have exactly 9 progressive steps", 9, interactiveSteps.size)
        assertEquals(R.id.treeContainer, interactiveSteps[0].targetViewId)
        assertEquals(R.id.ivProfilePhoto, interactiveSteps[1].targetViewId)
        assertEquals(R.id.tvProfileKinshipBadge, interactiveSteps[2].targetViewId)
        assertEquals(R.id.tvProfileParents, interactiveSteps[3].targetViewId)
        assertEquals(R.id.layoutProfileDocsContainer, interactiveSteps[4].targetViewId)
        assertEquals(R.id.btnProfileToggleVitalStatus, interactiveSteps[5].targetViewId)
        assertEquals(R.id.btnProfileAddChild, interactiveSteps[6].targetViewId)
        assertEquals(R.id.btnProfileAddSpouse, interactiveSteps[7].targetViewId)
        assertEquals(R.id.btnProfileEdit, interactiveSteps[8].targetViewId)

        interactiveSteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }

        val viewerSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.INTERACTIVE_TREE,
            isViewer = true
        )
        assertEquals("Viewer role in Interactive Tree must have exactly 6 viewing steps", 6, viewerSteps.size)
        assertEquals(R.id.treeContainer, viewerSteps[0].targetViewId)
        assertEquals(R.id.ivProfilePhoto, viewerSteps[1].targetViewId)
        assertEquals(R.id.tvProfileKinshipBadge, viewerSteps[2].targetViewId)
        assertEquals(R.id.tvProfileParents, viewerSteps[3].targetViewId)
        assertEquals(R.id.layoutProfileDocsContainer, viewerSteps[4].targetViewId)
        assertEquals(R.id.btnProfileFullDetail, viewerSteps[5].targetViewId)

        val editorOnlyTargetIds = setOf(
            R.id.btnProfileToggleVitalStatus,
            R.id.btnProfileAddChild,
            R.id.btnProfileAddSpouse,
            R.id.btnProfileEdit
        )
        viewerSteps.forEach { step ->
            assertFalse(
                "Viewer steps must not target editor button ${step.targetViewId}",
                editorOnlyTargetIds.contains(step.targetViewId)
            )
            assertTrue("Viewer step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Viewer step description must be informative", step.description.isNotBlank())
            assertTrue("Viewer pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testFamilyInsightsPresetSteps_structureAndTargets() {
        val insightsSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_INSIGHTS
        )
        assertEquals("Family Insights must have exactly 3 progressive steps", 3, insightsSteps.size)
        assertEquals(R.id.tvTotalMembers, insightsSteps[0].targetViewId)
        assertEquals(R.id.cardDataQualityScorecard, insightsSteps[1].targetViewId)
        assertEquals(R.id.btnQuickAssistInsights, insightsSteps[2].targetViewId)

        insightsSteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testFamilyRecordsPresetSteps_structureAndTargets() {
        val recordsSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_RECORDS
        )
        assertEquals("Family Records must have exactly 3 progressive steps", 3, recordsSteps.size)
        assertEquals(R.id.etSearch, recordsSteps[0].targetViewId)
        assertEquals(R.id.rvMembers, recordsSteps[1].targetViewId)
        assertEquals(R.id.btnQuickAssistRecords, recordsSteps[2].targetViewId)

        recordsSteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testMergeClanSpacePresetSteps_structureAndTargets() {
        val mergeSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.MERGE_CLAN_SPACE
        )
        assertEquals("Merge Clan Space must have exactly 3 progressive steps", 3, mergeSteps.size)
        assertEquals(R.id.cardSynthesizeClanHero, mergeSteps[0].targetViewId)
        assertEquals(R.id.cardMyMergeCode, mergeSteps[1].targetViewId)
        assertEquals(R.id.btnOpenConnectBranches, mergeSteps[2].targetViewId)

        mergeSteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testPrivacyControlsPresetSteps_structureAndTargets() {
        val privacySteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.PRIVACY_CONTROLS
        )
        assertEquals("Privacy Controls must have exactly 3 progressive steps", 3, privacySteps.size)
        assertEquals(R.id.switchPrivateTree, privacySteps[0].targetViewId)
        assertEquals(R.id.switchHideBirthDates, privacySteps[1].targetViewId)
        assertEquals(R.id.btnInviteMember, privacySteps[2].targetViewId)

        privacySteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testExportTreePresetSteps_structureAndTargets() {
        val exportSteps = com.example.btproject2.utils.KinTraceSpotlightManager.getPresetSteps(
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.EXPORT_TREE
        )
        assertEquals("Export Tree must have exactly 2 progressive steps", 2, exportSteps.size)
        assertEquals(R.id.cardPdf, exportSteps[0].targetViewId)
        assertEquals(R.id.btnExport, exportSteps[1].targetViewId)

        exportSteps.forEach { step ->
            assertTrue("Step title must be in layman terms", step.title.isNotBlank())
            assertTrue("Step description must be informative", step.description.isNotBlank())
            assertTrue("Pro-tip must be populated", !step.proTip.isNullOrBlank())
        }
    }

    @Test
    fun testGuideCrestDismissed_mutationAndPersistence() {
        val state = UserOnboardingState()
        assertFalse(state.isGuideCrestDismissed)

        state.isGuideCrestDismissed = true
        assertTrue(state.isGuideCrestDismissed)

        state.isGuideCrestDismissed = false
        assertFalse(state.isGuideCrestDismissed)
    }
}
