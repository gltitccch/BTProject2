package com.example.btproject2

import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.TreeMember
import com.example.btproject2.utils.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Module 8: Notification Module (FR-07 / DFD Store D7).
 * Verifies:
 * - 8.a Access Request and Approval Notifications
 * - 8.b Validation and Duplicate Record Alerts
 * - 8.c Privacy and Permission Alerts
 * - 8.d Account Recovery Notifications
 * - 8.e Inactivity Reminder Notifications
 * - 8.f System Alert Display Filtering and Counters
 */
class NotificationModuleTest {

    @Test
    fun testCategoryFiltering() {
        val notifs = listOf(
            NotificationRecord(id = "1", title = "Req", type = "ACCESS", timestamp = 1000L),
            NotificationRecord(id = "2", title = "Dup", type = "VALIDATION", timestamp = 3000L),
            NotificationRecord(id = "3", title = "Add", type = "RECORDS", timestamp = 2000L),
            NotificationRecord(id = "4", title = "Sec", type = "SECURITY", timestamp = 4000L)
        )

        // 8.f ALL filter: sorted descending by timestamp
        val all = NotificationHelper.filterNotifications(notifs, "ALL")
        assertEquals(4, all.size)
        assertEquals("4", all[0].id)
        assertEquals("2", all[1].id)
        assertEquals("3", all[2].id)
        assertEquals("1", all[3].id)

        // ACCESS filter
        val access = NotificationHelper.filterNotifications(notifs, "ACCESS")
        assertEquals(1, access.size)
        assertEquals("1", access[0].id)

        // VALIDATION filter
        val validation = NotificationHelper.filterNotifications(notifs, "VALIDATION")
        assertEquals(1, validation.size)
        assertEquals("2", validation[0].id)

        // RECORDS filter
        val records = NotificationHelper.filterNotifications(notifs, "RECORDS")
        assertEquals(1, records.size)
        assertEquals("3", records[0].id)

        // SECURITY filter
        val security = NotificationHelper.filterNotifications(notifs, "SECURITY")
        assertEquals(1, security.size)
        assertEquals("4", security[0].id)
    }

    @Test
    fun testUnreadCountComputation() {
        val list1 = listOf(
            NotificationRecord(id = "1", isRead = false),
            NotificationRecord(id = "2", isRead = true),
            NotificationRecord(id = "3", isRead = false)
        )
        assertEquals(2, NotificationHelper.getUnreadCount(list1))

        val list2 = listOf(
            NotificationRecord(id = "1", isRead = true),
            NotificationRecord(id = "2", isRead = true)
        )
        assertEquals(0, NotificationHelper.getUnreadCount(list2))

        assertEquals(0, NotificationHelper.getUnreadCount(emptyList()))
    }

    @Test
    fun testInactivityReminderEvaluation() {
        val now = System.currentTimeMillis()
        val fifteenDaysMs = 15 * 24 * 60 * 60 * 1000L
        val fiveDaysMs = 5 * 24 * 60 * 60 * 1000L

        val owner = TreeMember(id = "m1", userId = "user_owner", userName = "Owner", role = "Owner", status = "Approved")
        val activeEditor = TreeMember(id = "m2", userId = "user_editor", userName = "Active Editor", role = "Editor", status = "Approved", joinedAt = now - fifteenDaysMs, lastActiveAt = now - fifteenDaysMs)
        val dormantViewer = TreeMember(id = "m3", userId = "user_viewer", userName = "Dormant Viewer", role = "Viewer", status = "Approved", joinedAt = now - fifteenDaysMs, lastActiveAt = now - fifteenDaysMs)
        val pendingMember = TreeMember(id = "m4", userId = "user_pending", userName = "Pending", role = "Viewer", status = "Pending", joinedAt = now - fifteenDaysMs, lastActiveAt = now - fifteenDaysMs)

        val activities = listOf(
            ActivityRecord(id = "a1", userId = "user_editor", timestamp = now - fiveDaysMs, type = "MEMBER_ADDED")
        )

        val reminders = NotificationHelper.evaluateMemberInactivity(
            treeId = "tree_123",
            ownerId = "user_owner",
            members = listOf(owner, activeEditor, dormantViewer, pendingMember),
            activities = activities,
            thresholdDays = 14,
            nowMs = now
        )

        // Only dormantViewer should trigger an inactivity reminder for owner
        assertEquals(1, reminders.size)
        val reminder = reminders[0]
        assertEquals("user_owner", reminder.userId)
        assertEquals("m3", reminder.targetId)
        assertTrue(reminder.title.contains("Inactivity Reminder"))
        assertTrue(reminder.message.contains("Dormant Viewer"))
        assertTrue(reminder.message.contains("15 days"))
    }

    @Test
    fun testAccessRequestAndDecisionNotificationPayloads() {
        // 8.a Join Request to Owner
        val reqNotif = NotificationHelper.createAccessRequestNotification(
            treeId = "tree_abc",
            ownerId = "owner_xyz",
            requesterName = "Ackerley Doromal",
            requesterEmail = "ackerley@example.com",
            treeName = "Dela Cruz Tree",
            requestedRole = "Viewer"
        )
        assertEquals("owner_xyz", reqNotif.userId)
        assertEquals(NotificationHelper.CATEGORY_ACCESS, reqNotif.type)
        assertTrue(reqNotif.message.contains("Ackerley Doromal"))
        assertTrue(reqNotif.message.contains("Dela Cruz Tree"))

        // 8.a Approved Access Decision
        val approvedNotif = NotificationHelper.createAccessDecisionNotification(
            treeId = "tree_abc",
            applicantUserId = "user_123",
            treeName = "Dela Cruz Tree",
            approved = true,
            assignedRole = "Editor"
        )
        assertEquals("user_123", approvedNotif.userId)
        assertEquals(NotificationHelper.CATEGORY_ACCESS, approvedNotif.type)
        assertEquals("Access Request Approved", approvedNotif.title)
        assertTrue(approvedNotif.message.contains("approved with role: Editor"))

        // 8.a Declined Access Decision
        val declinedNotif = NotificationHelper.createAccessDecisionNotification(
            treeId = "tree_abc",
            applicantUserId = "user_123",
            treeName = "Dela Cruz Tree",
            approved = false,
            assignedRole = ""
        )
        assertEquals("Access Request Declined", declinedNotif.title)
        assertTrue(declinedNotif.message.contains("declined by the tree owner"))
    }

    @Test
    fun testValidationAndDuplicateRecordAlertPayloads() {
        // 8.b Duplicate record alert
        val dupNotif = NotificationHelper.createDuplicateAlertNotification(
            treeId = "tree_abc",
            userId = "editor_1",
            memberName = "Jose Dela Cruz",
            existingId = "person_456"
        )
        assertEquals("editor_1", dupNotif.userId)
        assertEquals(NotificationHelper.CATEGORY_VALIDATION, dupNotif.type)
        assertEquals("person_456", dupNotif.targetId)
        assertTrue(dupNotif.message.contains("Jose Dela Cruz"))

        // 8.b Biological data warning alert
        val conflictNotif = NotificationHelper.createValidationConflictNotification(
            treeId = "tree_abc",
            userId = "editor_1",
            memberName = "Maria Santos",
            conflictDetails = "Parent age at birth is 15 years old.",
            memberId = "person_789"
        )
        assertEquals(NotificationHelper.CATEGORY_VALIDATION, conflictNotif.type)
        assertEquals("person_789", conflictNotif.targetId)
        assertTrue(conflictNotif.message.contains("Parent age at birth is 15 years old."))
    }

    @Test
    fun testPrivacyPermissionAndRecoveryPayloads() {
        // 8.c Action Restricted
        val restrictedNotif = NotificationHelper.createPermissionRestrictedNotification(
            treeId = "tree_abc",
            userId = "viewer_1",
            actionAttempted = "Merge Branches",
            currentRole = "Viewer"
        )
        assertEquals(NotificationHelper.CATEGORY_SECURITY, restrictedNotif.type)
        assertTrue(restrictedNotif.title.contains("Viewer"))
        assertTrue(restrictedNotif.message.contains("Merge Branches"))

        // 8.c Role updated
        val roleNotif = NotificationHelper.createRoleUpdatedNotification(
            treeId = "tree_abc",
            userId = "user_99",
            treeName = "Dela Cruz Tree",
            newRole = "Editor"
        )
        assertEquals(NotificationHelper.CATEGORY_SECURITY, roleNotif.type)
        assertTrue(roleNotif.message.contains("Editor"))

        // 8.c Tree privacy changed
        val privacyNotif = NotificationHelper.createPrivacySettingsNotification(
            treeId = "tree_abc",
            treeName = "Dela Cruz Tree",
            isPrivate = true
        )
        assertEquals("", privacyNotif.userId) // Broadcast
        assertTrue(privacyNotif.message.contains("Private"))

        // 8.d Account Recovery
        val recoveryNotif = NotificationHelper.createAccountRecoveryNotification("renzy@example.com")
        assertEquals(NotificationHelper.CATEGORY_SECURITY, recoveryNotif.type)
        assertEquals("renzy@example.com", recoveryNotif.userId)
        assertTrue(recoveryNotif.message.contains("renzy@example.com"))
    }
}

