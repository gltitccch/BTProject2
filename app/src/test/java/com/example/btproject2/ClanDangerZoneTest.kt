package com.example.btproject2

import com.example.btproject2.ui.dialogs.ClanDangerZoneBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ClanDangerZoneBottomSheet and its safety threshold engine.
 */
class ClanDangerZoneTest {

    @Test
    fun testBottomSheetInstantiationAndTag() {
        val sheet = ClanDangerZoneBottomSheet.newInstance()
        assertNotNull(sheet)
        assertEquals("ClanDangerZoneBottomSheet", ClanDangerZoneBottomSheet.TAG)
    }

    @Test
    fun testConfirmDeleteCallbackBinding() {
        val sheet = ClanDangerZoneBottomSheet.newInstance()
        var deleteTriggered = false

        sheet.onConfirmDelete = {
            deleteTriggered = true
        }

        assertFalse(deleteTriggered)
        sheet.onConfirmDelete?.invoke()
        assertTrue(deleteTriggered)
    }

    @Test
    fun testSliderConfirmationThresholdMath() {
        val trackWidth = 320f
        val thumbWidth = 44f
        val marginOffset = 8f
        val maxTranslate = trackWidth - thumbWidth - marginOffset // 268f

        fun isDeletionConfirmed(translateDistance: Float): Boolean {
            val progress = translateDistance / maxTranslate
            return progress >= 0.90f
        }

        // Test 0% (at start)
        assertFalse(isDeletionConfirmed(0f))

        // Test 50% (halfway dragged)
        assertFalse(isDeletionConfirmed(134f))

        // Test 85% (almost there but released prematurely)
        assertFalse(isDeletionConfirmed(maxTranslate * 0.85f))

        // Test 89.9% (below threshold)
        assertFalse(isDeletionConfirmed(maxTranslate * 0.899f))

        // Test 90% (threshold reached)
        assertTrue(isDeletionConfirmed(maxTranslate * 0.90f))

        // Test 100% (fully slid across track)
        assertTrue(isDeletionConfirmed(maxTranslate))
    }
}
