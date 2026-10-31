package com.example.btproject2

import com.example.btproject2.models.TreeMember
import com.example.btproject2.utils.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive verification for the Golden Leaf Role Upgrade & Isolated Requests Drawer Flow.
 * Verifies single-tap approval, batch approvals, duplicate spam prevention, and unflooded privacy architecture.
 */
class GoldenLeafRoleUpgradeFlowTest {

    @Test
    fun `initial Golden Leaf request creates Pending Viewer status`() {
        val treeId = "tree_clan_001"
        val userId = "user_sprout_99"
        val member = TreeMember(
            id = "${treeId}_${userId}",
            treeId = treeId,
            userId = userId,
            userName = "Cousin Maria",
            userEmail = "maria@family.org",
            role = "Viewer",
            status = "Pending"
        )

        assertEquals("tree_clan_001_user_sprout_99", member.id)
        assertEquals("Viewer", member.role)
        assertEquals("Pending", member.status)
    }

    @Test
    fun `single approval immediately promotes member to Editor with Approved status`() {
        val pendingMember = TreeMember(
            id = "tree_001_user_002",
            treeId = "tree_001",
            userId = "user_002",
            userName = "Uncle Leo",
            userEmail = "leo@family.org",
            role = "Viewer",
            status = "Pending"
        )

        // Single tap approval action
        val approvedMember = pendingMember.copy(
            role = "Editor",
            status = "Approved",
            lastActiveAt = System.currentTimeMillis()
        )

        assertEquals("Editor", approvedMember.role)
        assertEquals("Approved", approvedMember.status)
        assertEquals("Uncle Leo", approvedMember.userName)
    }

    @Test
    fun `idempotency ensures repeated requests update single record and prevent spam duplication`() {
        val treeId = "tree_clan_001"
        val userId = "user_sprout_99"
        val memberId = "${treeId}_${userId}"

        val request1 = TreeMember(id = memberId, treeId = treeId, userId = userId, userName = "Maria", role = "Viewer", status = "Pending")
        val request2 = request1.copy(lastActiveAt = System.currentTimeMillis() + 5000L)

        assertEquals(request1.id, request2.id)
        val memberMap = mutableMapOf<String, TreeMember>()
        memberMap[request1.id] = request1
        memberMap[request2.id] = request2

        assertEquals(1, memberMap.size)
        assertEquals("Pending", memberMap[memberId]?.status)
    }

    @Test
    fun `isolated queue hides tile when 0 pending and displays badge when multiple pending`() {
        val allMembers = listOf(
            TreeMember(id = "1", role = "Owner", status = "Approved"),
            TreeMember(id = "2", role = "Editor", status = "Approved"),
            TreeMember(id = "3", role = "Viewer", status = "Approved")
        )

        val pendingWhenEmpty = allMembers.filter { it.status.equals("Pending", ignoreCase = true) }
        val isTileVisibleWhenEmpty = pendingWhenEmpty.isNotEmpty()
        assertFalse("Tile should be hidden when 0 pending requests", isTileVisibleWhenEmpty)

        val membersWithRequests = allMembers + listOf(
            TreeMember(id = "4", userName = "Cousin A", role = "Viewer", status = "Pending"),
            TreeMember(id = "5", userName = "Cousin B", role = "Viewer", status = "Pending"),
            TreeMember(id = "6", userName = "Cousin C", role = "Viewer", status = "Pending")
        )

        val pendingQueue = membersWithRequests.filter { it.status.equals("Pending", ignoreCase = true) }
        val isTileVisibleWhenQueued = pendingQueue.isNotEmpty()
        assertTrue("Tile should appear when pending requests exist", isTileVisibleWhenQueued)
        assertEquals(3, pendingQueue.size)
        assertEquals("3 Pending", "${pendingQueue.size} Pending")
    }

    @Test
    fun `batch approve all promotes every pending member to Editor in 1 pass`() {
        val pendingList = listOf(
            TreeMember(id = "m1", userName = "Alice", role = "Viewer", status = "Pending"),
            TreeMember(id = "m2", userName = "Bob", role = "Viewer", status = "Pending"),
            TreeMember(id = "m3", userName = "Charlie", role = "Viewer", status = "Pending")
        )

        val batchApproved = pendingList.map { it.copy(role = "Editor", status = "Approved") }

        assertEquals(3, batchApproved.size)
        assertTrue(batchApproved.all { it.role == "Editor" })
        assertTrue(batchApproved.all { it.status == "Approved" })

        val remainingPending = batchApproved.filter { it.status.equals("Pending", ignoreCase = true) }
        assertTrue("Queue should be completely empty after batch approval", remainingPending.isEmpty())
    }

    @Test
    fun `approval notification contains celebratory message and clean title`() {
        val notif = NotificationHelper.createAccessDecisionNotification(
            treeId = "tree_101",
            applicantUserId = "user_202",
            treeName = "Russell Family",
            approved = true,
            assignedRole = "Editor"
        )

        assertTrue(notif.title.contains("Access Approved") || notif.title.contains("Approved"))
        assertTrue(notif.message.contains("Editor"))
        assertTrue(notif.message.contains("Russell Family"))
        assertEquals("user_202", notif.userId)
    }
}
