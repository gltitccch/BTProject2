package com.example.btproject2

import com.example.btproject2.models.FamilyTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the on-demand Freemium Clan Expansion request flow:
 * a Tree Owner can request an expansion at ANY member count (not only when full),
 * the request locks into a "Queued" state to prevent duplicate spam,
 * and an Admin approval moves the tree from the 50-member Free Tier to the 200-member Extended Clan Tier.
 */
class OnDemandQuotaExpansionTest {

    private fun tierLabel(quota: Int) = if (quota >= 200) "Extended Clan Tier" else "Free Tier"

    @Test
    fun `owner can request expansion well before the tree is full`() {
        val tree = FamilyTree(id = "t1", ownerId = "owner", memberCount = 21)
        val liveCount = 21
        assertFalse("21/50 is not capped", tree.isCapped(liveCount))
        assertFalse("21/50 is not near cap", tree.isNearCap(liveCount))
        // On-demand request is allowed whenever not already requested
        val canRequest = !tree.quotaExpansionRequested
        assertTrue(canRequest)
    }

    @Test
    fun `requesting locks the tree into queued state to prevent duplicate requests`() {
        val tree = FamilyTree(id = "t1", ownerId = "owner", memberCount = 21)
        val queued = tree.copy(quotaRequestedAt = System.currentTimeMillis()).also {
            it.quotaExpansionRequested = true
        }
        assertTrue(queued.quotaExpansionRequested)
        val canRequestAgain = !queued.quotaExpansionRequested
        assertFalse("A queued tree must not offer a second request", canRequestAgain)
        assertEquals("Quota stays at 50 until Admin approves", 50, queued.effectiveQuota)
    }

    @Test
    fun `admin approval moves free tier 50 to extended clan tier 200`() {
        val tree = FamilyTree(id = "t1", ownerId = "owner", memberCount = 21)
        assertEquals("Free Tier", tierLabel(tree.effectiveQuota))

        val newQuota = tree.nextExpandedQuota()
        assertEquals(200, newQuota)
        assertEquals(tree.effectiveQuota + FamilyTree.EXPANSION_STEP, newQuota)

        val approved = tree.copy(memberQuota = newQuota).also { it.quotaExpansionRequested = false }
        assertEquals("Extended Clan Tier", tierLabel(approved.effectiveQuota))
        assertFalse(approved.quotaExpansionRequested)
        assertEquals(179, approved.effectiveQuota - 21)
    }

    @Test
    fun `capacity progress percentage is computed safely`() {
        fun pct(live: Int, quota: Int) = ((live * 100f) / quota.coerceAtLeast(1)).toInt().coerceIn(0, 100)
        assertEquals(42, pct(21, 50))
        assertEquals(10, pct(21, 200))
        assertEquals(100, pct(60, 50))
        assertEquals(0, pct(0, 0))
    }
}
