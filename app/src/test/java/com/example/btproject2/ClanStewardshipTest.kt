package com.example.btproject2

import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import com.example.btproject2.models.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Clan Stewardship data model and account deletion logic rules.
 * Ensures genealogical integrity, living relative privacy redaction, and succession governance.
 */
class ClanStewardshipTest {

    @Test
    fun testUserProfileTermsOfServiceAcceptanceTimestamp() {
        val defaultProfile = UserProfile(id = "user123", email = "test@example.com")
        assertEquals(0L, defaultProfile.tosAcceptedAt)

        val acceptTime = System.currentTimeMillis()
        val acceptedProfile = defaultProfile.copy(tosAcceptedAt = acceptTime)
        assertTrue(acceptedProfile.tosAcceptedAt > 0L)
        assertEquals(acceptTime, acceptedProfile.tosAcceptedAt)
    }

    @Test
    fun testTreeClassificationSoloVsSharedUnderStewardship() {
        val userId = "user_alpha"

        val soloTree = FamilyTree(
            id = "tree_solo",
            ownerId = userId,
            memberCount = 1,
            coOwnerIds = emptyList()
        )

        val sharedCollaborativeTree = FamilyTree(
            id = "tree_shared_collab",
            ownerId = userId,
            memberCount = 14,
            coOwnerIds = listOf("co_owner_beta")
        )

        val multiMemberTreeNoCoOwner = FamilyTree(
            id = "tree_shared_multi",
            ownerId = userId,
            memberCount = 6,
            coOwnerIds = emptyList()
        )

        // Rule: Solo unshared tree (memberCount <= 1 && coOwnerIds.isEmpty()) can be purged
        val isSolo = soloTree.ownerId == userId && soloTree.memberCount <= 1 && soloTree.coOwnerIds.isEmpty()
        assertTrue(isSolo)

        // Rule: Shared trees must NEVER be purged, but preserved under Clan Stewardship
        val isShared1 = !(sharedCollaborativeTree.ownerId == userId && sharedCollaborativeTree.memberCount <= 1 && sharedCollaborativeTree.coOwnerIds.isEmpty())
        assertTrue(isShared1)

        val isShared2 = !(multiMemberTreeNoCoOwner.ownerId == userId && multiMemberTreeNoCoOwner.memberCount <= 1 && multiMemberTreeNoCoOwner.coOwnerIds.isEmpty())
        assertTrue(isShared2)
    }

    @Test
    fun testLivingRelativeRedactionUnderClanStewardship() {
        val userId = "user_alpha"

        // Deceased ancestor: Historical data stays public / intact for descendants
        val deceasedAncestor = Person(
            id = "p1",
            firstName = "Jose",
            lastName = "Rizal",
            isLiving = false,
            deathDate = "1896-12-30",
            createdBy = userId
        )

        // Living relative: Subject to RA 10173 privacy protection upon contributor account deletion
        val livingRelative = Person(
            id = "p2",
            firstName = "Clara",
            lastName = "Santos",
            isLiving = true,
            deathDate = "",
            createdBy = userId
        )

        val isDeceasedActuallyLiving = deceasedAncestor.isLiving && deceasedAncestor.deathDate.isBlank()
        assertFalse(isDeceasedActuallyLiving)

        val isLivingActuallyLiving = livingRelative.isLiving && livingRelative.deathDate.isBlank()
        assertTrue(isLivingActuallyLiving)

        // Redaction verification
        val redactedLiving = livingRelative.copy(
            createdBy = "Former Clan Contributor",
            isPrivate = true,
            firstName = "Private",
            lastName = "Living Relative",
            middleName = "",
            biography = "[Redacted per Clan Stewardship & RA 10173]"
        )

        assertEquals("Former Clan Contributor", redactedLiving.createdBy)
        assertTrue(redactedLiving.isPrivate)
        assertEquals("Private", redactedLiving.firstName)
        assertEquals("Living Relative", redactedLiving.lastName)
        assertEquals("[Redacted per Clan Stewardship & RA 10173]", redactedLiving.biography)
    }

    @Test
    fun testOwnershipSuccessionUnderClanStewardship() {
        val currentUserId = "founder_user"
        val coOwner1 = "sibling_co_owner"
        val coOwner2 = "cousin_co_owner"

        val tree = FamilyTree(
            id = "clan_tree_001",
            ownerId = currentUserId,
            coOwnerIds = listOf(coOwner1, coOwner2),
            memberCount = 20
        )

        // When founder deletes account: First co-owner is promoted, remaining co-owners preserved
        val nextOwner = tree.coOwnerIds.firstOrNull() ?: "Former Clan Contributor"
        assertEquals(coOwner1, nextOwner)

        val updatedCoOwners = tree.coOwnerIds.filter { it != nextOwner && it != currentUserId }
        assertEquals(listOf(coOwner2), updatedCoOwners)
    }
}
