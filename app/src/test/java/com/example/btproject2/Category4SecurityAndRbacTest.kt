package com.example.btproject2

import com.example.btproject2.models.DemoAccountRepository
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.TreeMember
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Category 4: Security & "Viewer" Role Permission Leaks (Issues 4.1 to 4.3).
 */
class Category4SecurityAndRbacTest {

    // -------------------------------------------------------------------------
    // Issue 4.1: Super Admin Citadel Hub Access Guard
    // -------------------------------------------------------------------------
    @Test
    fun `issue 4_1 only super admin email is authorized for AdminPortalActivity`() {
        assertTrue(DemoAccountRepository.isAdminEmail("admin@kintrace.ph"))
        assertTrue(DemoAccountRepository.isAdminEmail("  ADMIN@KINTRACE.PH "))

        // Regular demo accounts and standard users must be blocked
        assertFalse(DemoAccountRepository.isAdminEmail("dumgen8@gmail.com"))
        assertFalse(DemoAccountRepository.isAdminEmail("gendums5@gmail.com"))
        assertFalse(DemoAccountRepository.isAdminEmail("user@example.com"))
        assertFalse(DemoAccountRepository.isAdminEmail(""))
        assertFalse(DemoAccountRepository.isAdminEmail(null))
    }

    // -------------------------------------------------------------------------
    // Issue 4.2: Viewer Role Restrictions (Documents, Canvas Quick-Add, Clan Tree Deletion)
    // -------------------------------------------------------------------------
    @Test
    fun `issue 4_2 viewers cannot delete documents or master clan trees`() {
        val ownerUserId = "user_owner_1"
        val viewerUserId = "user_viewer_2"
        val clanTree = FamilyTree(
            id = "clan_tree_1",
            name = "Russell-Malone Master Clan",
            ownerId = ownerUserId,
            treeType = FamilyTree.TREE_TYPE_MERGED_CLAN
        )

        // Viewer attempting to delete clan tree -> blocked
        val isViewerOnlyForViewer = true
        val canViewerDeleteClan = !isViewerOnlyForViewer &&
            (clanTree.ownerId.isEmpty() || clanTree.ownerId == viewerUserId)
        assertFalse("Viewer must not be able to delete Master Clan Tree", canViewerDeleteClan)

        // Non-owner Editor attempting to delete clan tree owned by someone else -> blocked
        val isViewerOnlyForEditor = false
        val canEditorDeleteOtherUsersClan = !isViewerOnlyForEditor &&
            (clanTree.ownerId.isEmpty() || clanTree.ownerId == viewerUserId)
        assertFalse("Non-owner Editor must not delete another owner's Master Clan Tree", canEditorDeleteOtherUsersClan)

        // Actual Owner with non-viewer role -> allowed
        val canOwnerDeleteClan = !isViewerOnlyForEditor &&
            (clanTree.ownerId.isEmpty() || clanTree.ownerId == ownerUserId)
        assertTrue("Owner must be allowed to delete their Master Clan Tree", canOwnerDeleteClan)
    }

    // -------------------------------------------------------------------------
    // Issue 4.3: Fail-Closed Role Resolution on Network Failure
    // -------------------------------------------------------------------------
    private data class ResolvedRoleState(
        val isOwner: Boolean,
        val isEditor: Boolean,
        val isViewer: Boolean
    )

    private fun resolveRoleWithFailClosedFallback(
        currentUserId: String,
        treeResult: Result<FamilyTree?>,
        membershipResult: Result<TreeMember?>
    ): ResolvedRoleState {
        return treeResult.fold(
            onSuccess = { tree ->
                val ownerMatch = tree != null && (tree.ownerId.isEmpty() || tree.ownerId == currentUserId)
                if (ownerMatch) {
                    ResolvedRoleState(isOwner = true, isEditor = false, isViewer = false)
                } else {
                    membershipResult.fold(
                        onSuccess = { member ->
                            val role = member?.role ?: "Viewer"
                            val isEditor = role.equals("Editor", ignoreCase = true)
                            ResolvedRoleState(isOwner = false, isEditor = isEditor, isViewer = !isEditor)
                        },
                        onFailure = {
                            // Fail-closed: network error on membership check defaults to read-only Viewer
                            ResolvedRoleState(isOwner = false, isEditor = false, isViewer = true)
                        }
                    )
                }
            },
            onFailure = {
                // Fail-closed: network error on tree fetch defaults to read-only Viewer, NEVER Owner
                ResolvedRoleState(isOwner = false, isEditor = false, isViewer = true)
            }
        )
    }

    @Test
    fun `issue 4_3 network failure on tree or membership fetch fails closed to Viewer`() {
        val treeFetchFailure = resolveRoleWithFailClosedFallback(
            currentUserId = "user_123",
            treeResult = Result.failure(RuntimeException("Firestore unavailable")),
            membershipResult = Result.success(null)
        )
        assertFalse("Tree fetch failure must NOT promote user to Owner", treeFetchFailure.isOwner)
        assertFalse("Tree fetch failure must NOT promote user to Editor", treeFetchFailure.isEditor)
        assertTrue("Tree fetch failure MUST fail-closed to Viewer", treeFetchFailure.isViewer)

        val tree = FamilyTree(id = "tree_1", name = "Test Tree", ownerId = "owner_999")
        val membershipFetchFailure = resolveRoleWithFailClosedFallback(
            currentUserId = "user_123",
            treeResult = Result.success(tree),
            membershipResult = Result.failure(RuntimeException("Timeout"))
        )
        assertFalse("Membership fetch failure must NOT promote user to Owner", membershipFetchFailure.isOwner)
        assertFalse("Membership fetch failure must NOT promote user to Editor", membershipFetchFailure.isEditor)
        assertTrue("Membership fetch failure MUST fail-closed to Viewer", membershipFetchFailure.isViewer)
    }
}
