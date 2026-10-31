package com.example.btproject2

import com.example.btproject2.utils.KinTraceSpotlightManager
import com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification tests for KinTrace Spotlight Interactive Visual Tours.
 * Verifies that the new Organize Mode & Free Move tour step and 2-step Organize Mode walkthrough
 * are correctly integrated into the central spotlight tutorial engine for tree editors,
 * while maintaining strict read-only isolation for viewers.
 */
class KinTraceSpotlightTourTest {

    @Test
    fun testInteractiveTreeTourStepSequenceForEditors() {
        val steps = KinTraceSpotlightManager.getPresetSteps(
            feature = TutorialFeatureKey.INTERACTIVE_TREE,
            isViewer = false
        )

        // Must have exactly 10 comprehensive steps for tree editors
        assertEquals("Editor tour must contain exactly 10 steps", 10, steps.size)

        // Step 1: Canvas tap intro
        assertEquals(R.id.treeContainer, steps[0].targetViewId)

        // Steps 2 to 9: Member Profile Card walk-through
        assertEquals(R.id.ivProfilePhoto, steps[1].targetViewId)
        assertEquals(R.id.tvProfileKinshipBadge, steps[2].targetViewId)
        assertEquals(R.id.tvProfileParents, steps[3].targetViewId)
        assertEquals(R.id.layoutProfileDocsContainer, steps[4].targetViewId)
        assertEquals(R.id.btnProfileToggleVitalStatus, steps[5].targetViewId)
        assertEquals(R.id.btnProfileAddChild, steps[6].targetViewId)
        assertEquals(R.id.btnProfileAddSpouse, steps[7].targetViewId)
        assertEquals(R.id.btnProfileEdit, steps[8].targetViewId)

        // Step 10: Organize Mode & Free Move layout customizer
        val organizeStep = steps[9]
        assertEquals("Step 10 must target the organize floating button", R.id.btnToggleOrganizeMode, organizeStep.targetViewId)
        assertEquals("Free Move & Custom Layout", organizeStep.title)
        assertEquals("✎", organizeStep.icon)
        assertTrue("Description must explain free dragging", organizeStep.description.contains("drag"))
        assertTrue("Description must explain ↩ Undo", organizeStep.description.contains("Undo"))
        assertNotNull("Step 10 must have an onStepEnter callback to dismiss modal", organizeStep.onStepEnter)
    }

    @Test
    fun testInteractiveTreeTourSequenceForViewers() {
        val viewerSteps = KinTraceSpotlightManager.getPresetSteps(
            feature = TutorialFeatureKey.INTERACTIVE_TREE,
            isViewer = true
        )

        // Viewers should have exactly 6 read-only profile steps
        assertEquals("Viewer tour must have 6 read-only steps", 6, viewerSteps.size)

        // Must NOT contain the organize floating button for read-only guests
        val hasOrganizeStep = viewerSteps.any { it.targetViewId == R.id.btnToggleOrganizeMode }
        assertFalse("Viewer tour must NOT contain Organize Mode step", hasOrganizeStep)
    }

    @Test
    fun testOrganizeModeTourStepSequenceAndStrictViewerExclusion() {
        // Editors get exactly 2 steps upon entering Organize Mode
        val editorSteps = KinTraceSpotlightManager.getPresetSteps(
            feature = TutorialFeatureKey.ORGANIZE_MODE,
            isViewer = false
        )
        assertEquals("Organize Mode walkthrough must have exactly 2 steps for editors", 2, editorSteps.size)

        // Step 1: Top action bar (Undo, Reset, banner)
        val step1 = editorSteps[0]
        assertEquals(R.id.layoutOrganizeModeBar, step1.targetViewId)
        assertEquals("Organize Controls & Single-Step Undo", step1.title)
        assertTrue("Step 1 must explain ↩ Undo", step1.description.contains("Undo"))
        assertTrue("Step 1 must explain ↺ Reset", step1.description.contains("Reset"))

        // Step 2: Line handles and Done button
        val step2 = editorSteps[1]
        assertEquals(R.id.btnDoneOrganize, step2.targetViewId)
        assertEquals("Bend Connector Lines & Save", step2.title)
        assertTrue("Step 2 must explain line handles and saving", step2.description.contains("Done"))

        // Viewers are 100% strictly excluded from Organize Mode tutorial
        val viewerSteps = KinTraceSpotlightManager.getPresetSteps(
            feature = TutorialFeatureKey.ORGANIZE_MODE,
            isViewer = true
        )
        assertTrue("Organize Mode tutorial must be completely empty (hidden) for viewers", viewerSteps.isEmpty())
    }
}
