package com.example.btproject2

import com.example.btproject2.utils.BotanicalRolePolicy
import com.example.btproject2.utils.BotanicalRolePolicy.BotanicalTier
import org.junit.Assert.*
import org.junit.Test

class BotanicalRolePermissionTest {

    @Test
    fun `resolveTier maps Viewer Editor and Owner strings to BotanicalTiers`() {
        assertEquals(BotanicalTier.SPROUT_VIEWER, BotanicalRolePolicy.resolveTier("Viewer", false))
        assertEquals(BotanicalTier.SPROUT_VIEWER, BotanicalRolePolicy.resolveTier(null, false))
        assertEquals(BotanicalTier.BRANCH_EDITOR, BotanicalRolePolicy.resolveTier("Editor", false))
        assertEquals(BotanicalTier.BRANCH_EDITOR, BotanicalRolePolicy.resolveTier("Collaborator", false))
        assertEquals(BotanicalTier.CANOPY_OWNER, BotanicalRolePolicy.resolveTier("Owner", false))
        assertEquals(BotanicalTier.CANOPY_OWNER, BotanicalRolePolicy.resolveTier("Viewer", isTreeOwner = true))
    }

    @Test
    fun `hasClearance enforces strict Sprout Branch Canopy hierarchy`() {
        // Sprout Viewer (Tier 1)
        assertTrue(BotanicalRolePolicy.hasClearance(BotanicalTier.SPROUT_VIEWER, BotanicalTier.SPROUT_VIEWER))
        assertFalse(BotanicalRolePolicy.hasClearance(BotanicalTier.SPROUT_VIEWER, BotanicalTier.BRANCH_EDITOR))
        assertFalse(BotanicalRolePolicy.hasClearance(BotanicalTier.SPROUT_VIEWER, BotanicalTier.CANOPY_OWNER))

        // Branch Editor (Tier 2)
        assertTrue(BotanicalRolePolicy.hasClearance(BotanicalTier.BRANCH_EDITOR, BotanicalTier.SPROUT_VIEWER))
        assertTrue(BotanicalRolePolicy.hasClearance(BotanicalTier.BRANCH_EDITOR, BotanicalTier.BRANCH_EDITOR))
        assertFalse(BotanicalRolePolicy.hasClearance(BotanicalTier.BRANCH_EDITOR, BotanicalTier.CANOPY_OWNER))

        // Canopy Owner (Tier 3)
        assertTrue(BotanicalRolePolicy.hasClearance(BotanicalTier.CANOPY_OWNER, BotanicalTier.SPROUT_VIEWER))
        assertTrue(BotanicalRolePolicy.hasClearance(BotanicalTier.CANOPY_OWNER, BotanicalTier.BRANCH_EDITOR))
        assertTrue(BotanicalRolePolicy.hasClearance(BotanicalTier.CANOPY_OWNER, BotanicalTier.CANOPY_OWNER))
    }

    @Test
    fun `buildCapabilitiesChecklist reflects exact permissions for each BotanicalTier`() {
        val viewerCaps = BotanicalRolePolicy.buildCapabilitiesChecklist(BotanicalTier.SPROUT_VIEWER)
        assertEquals(4, viewerCaps.size)
        assertTrue(viewerCaps[0].isUnlocked)
        assertTrue(viewerCaps[1].isUnlocked)
        assertFalse(viewerCaps[2].isUnlocked)
        assertFalse(viewerCaps[3].isUnlocked)

        val editorCaps = BotanicalRolePolicy.buildCapabilitiesChecklist(BotanicalTier.BRANCH_EDITOR)
        assertTrue(editorCaps[2].isUnlocked)
        assertFalse(editorCaps[3].isUnlocked)

        val ownerCaps = BotanicalRolePolicy.buildCapabilitiesChecklist(BotanicalTier.CANOPY_OWNER)
        assertTrue(ownerCaps.all { it.isUnlocked })
    }

    @Test
    fun `safety guardrails clamp branch count to 7 and particle pool to 20`() {
        assertEquals(3, BotanicalRolePolicy.MAX_TREE_RECURSION_DEPTH)
        assertEquals(7, BotanicalRolePolicy.MAX_TOTAL_BRANCH_SEGMENTS)
        assertEquals(20, BotanicalRolePolicy.MAX_PARTICLE_POOL_SIZE)

        // Even if an excessive recursion depth is requested, it is clamped to 7 segments max
        assertEquals(7, BotanicalRolePolicy.computeSafeBranchSegmentCount(3))
        assertEquals(7, BotanicalRolePolicy.computeSafeBranchSegmentCount(50))
        assertEquals(1, BotanicalRolePolicy.computeSafeBranchSegmentCount(-5))
    }
}
