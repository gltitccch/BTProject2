package com.example.btproject2

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.FamilyTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated Multi-Account & Heavy Post-Approval Stress Test for the Global Freemium Quota Pipeline.
 * Verifies that any existing or newly created account follows the exact same 50-member Freemium cap,
 * near-cap queue detection (>=90%), expansion request, and post-approval 200-member expansion rules
 * without any hardcoded emails or clan names.
 */
class FreemiumQuotaStressTest {

    @Test
    fun `default newly created account starts with 50 member freemium quota`() {
        val newAccountTree = FamilyTree(
            id = "tree_brand_new_user_001",
            name = "Dela Cruz Lineage",
            ownerId = "uid_new_user_001",
            ownerEmail = "new.user.2026@gmail.com",
            memberCount = 1
        )

        assertEquals(FamilyTree.DEFAULT_FREEMIUM_QUOTA, newAccountTree.effectiveQuota)
        assertEquals(50, newAccountTree.effectiveQuota)
        assertFalse(newAccountTree.isNearCap())
        assertFalse(newAccountTree.isCapped())
        assertEquals(200, newAccountTree.nextExpandedQuota())
    }

    @Test
    fun `any account reaching 45 to 49 members is flagged as Near Cap for proactive admin queue`() {
        val nearCapTree = FamilyTree(
            id = "tree_arbitrary_clan_48",
            name = "Reyes-Bautista Clan",
            ownerId = "uid_arbitrary_48",
            ownerEmail = "reyes.family@yahoo.com",
            memberCount = 48,
            memberQuota = 50
        )

        assertTrue(nearCapTree.isNearCap(48))
        assertFalse(nearCapTree.isCapped(48))

        val status = FirestoreHelper.TreeQuotaStatus(
            tree = nearCapTree,
            liveMemberCount = 48,
            ownerEmail = nearCapTree.ownerEmail
        )
        assertTrue(status.isNearCap)
        assertFalse(status.isCapped)
        assertTrue(status.badgeLabel.contains("48/50 Near Cap"))
        assertTrue(status.metaSummary.contains("reyes.family@yahoo.com"))
    }

    @Test
    fun `hard cap at 50 blocks 51st member and queues expansion request for any account`() {
        val cappedTree = FamilyTree(
            id = "kGsiZ8hb9iOM44oB6IIr",
            name = "Bugarin Family",
            ownerId = "eyvZ4rq9cZZtu6EcqP9fXI8L3812",
            ownerEmail = "gendum367@gmail.com",
            memberCount = 50,
            memberQuota = 50,
            quotaExpansionRequested = true,
            quotaRequestedAt = 1792330000000L
        )

        assertTrue("50/50 tree must be capped and block 51st member", cappedTree.isCapped(50))
        assertFalse("50/50 tree is capped, not merely near-cap", cappedTree.isNearCap(50))
        assertEquals(200, cappedTree.nextExpandedQuota())

        val status = FirestoreHelper.TreeQuotaStatus(
            tree = cappedTree,
            liveMemberCount = 50,
            ownerEmail = cappedTree.ownerEmail
        )
        assertTrue(status.isCapped)
        assertEquals("🛑 50/50 Capped", status.badgeLabel)
        assertEquals(200, status.nextQuota)
        assertTrue(status.metaSummary.contains("gendum367@gmail.com"))
        assertTrue(status.metaSummary.contains("Tier 2 (200 Members)"))
    }

    @Test
    fun `post-approval stress test allows adding 22+ new members (51 to 72) and enforces new 200 cap`() {
        // After Admin Citadel approves +150 Expansion (50 -> 200 cap):
        var expandedTree = FamilyTree(
            id = "kGsiZ8hb9iOM44oB6IIr",
            name = "Bugarin Family",
            ownerId = "eyvZ4rq9cZZtu6EcqP9fXI8L3812",
            ownerEmail = "gendum367@gmail.com",
            memberCount = 50,
            memberQuota = 200,
            quotaExpansionRequested = false
        )

        assertEquals(200, expandedTree.effectiveQuota)
        assertFalse("50/200 must no longer be capped after approval", expandedTree.isCapped(50))
        assertFalse("50/200 must not be near cap", expandedTree.isNearCap(50))

        // Stress test adding 22 additional family members (#51 through #72)
        for (memberNum in 51..72) {
            assertFalse(
                "Member #$memberNum should be allowed under the 200 cap",
                expandedTree.isCapped(memberNum - 1)
            )
            expandedTree = expandedTree.copy(memberCount = memberNum)
        }

        assertEquals(72, expandedTree.memberCount)
        assertFalse("72/200 must remain uncapped", expandedTree.isCapped(72))
        assertFalse("72/200 must not be near cap (threshold is 180)", expandedTree.isNearCap(72))

        val postStressStatus = FirestoreHelper.TreeQuotaStatus(
            tree = expandedTree,
            liveMemberCount = 72,
            ownerEmail = expandedTree.ownerEmail
        )
        assertEquals("✅ 72/200 Active", postStressStatus.badgeLabel)

        // Verify near-cap and hard-cap at Tier 2 (180/200 and 200/200)
        assertTrue("180/200 (90%) should trigger Tier 2 near-cap", expandedTree.isNearCap(180))
        assertTrue("200/200 should trigger Tier 2 hard-cap", expandedTree.isCapped(200))
        assertEquals(500, expandedTree.nextExpandedQuota())
    }
}
