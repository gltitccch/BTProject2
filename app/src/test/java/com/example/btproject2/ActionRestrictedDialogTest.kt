package com.example.btproject2

import com.example.btproject2.ui.dialogs.ActionRestrictedDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying ActionRestrictedDialog initialization and arguments bundling.
 */
class ActionRestrictedDialogTest {

    @Test
    fun testDialogInstantiationAndTag() {
        val dialog = ActionRestrictedDialog.newInstance(
            treeId = "tree_test_123",
            treeName = "Russell Clan",
            actionAttempted = "Merge Branches"
        )
        assertNotNull(dialog)
        assertEquals("ActionRestrictedDialog", ActionRestrictedDialog.TAG)
        assertEquals("tree_test_123", dialog.treeId)
        assertEquals("Russell Clan", dialog.treeName)
        assertEquals("Merge Branches", dialog.actionAttempted)
    }

    @Test
    fun testRequestAccessCallbackTrigger() {
        val dialog = ActionRestrictedDialog.newInstance()
        var callbackFired = false
        dialog.onRequestAccess = {
            callbackFired = true
        }

        assertFalse(callbackFired)
        dialog.onRequestAccess?.invoke()
        assertTrue(callbackFired)
    }

    @Test
    fun testDefaultActionAttemptedFallback() {
        val dialog = ActionRestrictedDialog.newInstance()
        assertEquals("Edit Member Records", dialog.actionAttempted)
    }

    @Test
    fun testOnDismissActionCallbackTrigger() {
        val dialog = ActionRestrictedDialog.newInstance()
        var dismissFired = false
        dialog.onDismissAction = {
            dismissFired = true
        }

        assertFalse(dismissFired)
        dialog.onDismissAction?.invoke()
        assertTrue(dismissFired)
    }
}
