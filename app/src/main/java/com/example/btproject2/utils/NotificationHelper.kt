package com.example.btproject2.utils

import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.TreeMember

/**
 * NotificationHelper — Centralized notification logic for Module 8 (FR-07 / DFD Store D7).
 *
 * Implements:
 * - 8.a Access Request and Approval Notifications
 * - 8.b Validation and Duplicate Record Alerts
 * - 8.c Privacy and Permission Alerts
 * - 8.d Account Recovery Notifications
 * - 8.e Inactivity Reminder Notifications
 * - 8.f System Alert Display Filtering and Counter
 */
object NotificationHelper {

    const val CATEGORY_ALL = "ALL"
    const val CATEGORY_ACCESS = "ACCESS"
    const val CATEGORY_RECORDS = "RECORDS"
    const val CATEGORY_VALIDATION = "VALIDATION"
    const val CATEGORY_SECURITY = "SECURITY"

    /**
     * Filters notification records by category (8.f).
     * ALL returns all notifications sorted descending by timestamp.
     */
    fun filterNotifications(
        notifications: List<NotificationRecord>,
        category: String
    ): List<NotificationRecord> {
        val sorted = notifications.sortedByDescending { it.timestamp }
        return if (category.equals(CATEGORY_ALL, ignoreCase = true)) {
            sorted
        } else {
            sorted.filter { it.type.equals(category, ignoreCase = true) }
        }
    }

    /**
     * Computes the number of unread notifications (8.f).
     */
    fun getUnreadCount(notifications: List<NotificationRecord>): Int {
        return notifications.count { !it.isRead }
    }

    /**
     * Evaluates member inactivity for a tree (8.e).
     * Notifies tree owner when an editor/viewer has no recent activity for > thresholdDays.
     */
    fun evaluateMemberInactivity(
        treeId: String,
        ownerId: String,
        members: List<TreeMember>,
        activities: List<ActivityRecord>,
        thresholdDays: Long = 14,
        nowMs: Long = System.currentTimeMillis()
    ): List<NotificationRecord> {
        val thresholdMs = thresholdDays * 24 * 60 * 60 * 1000L
        val reminders = mutableListOf<NotificationRecord>()

        val eligibleMembers = members.filter {
            !it.role.equals("Owner", ignoreCase = true) &&
                    it.status.equals("Approved", ignoreCase = true)
        }

        for (member in eligibleMembers) {
            val lastUserActivity = activities
                .filter { it.userId == member.userId || it.userName.equals(member.userName, ignoreCase = true) }
                .maxOfOrNull { it.timestamp }

            val effectiveLastActive = lastUserActivity
                ?: member.lastActiveAt.takeIf { it > 0 }
                ?: member.joinedAt.takeIf { it > 0 }
                ?: nowMs

            val inactiveDurationMs = nowMs - effectiveLastActive
            if (inactiveDurationMs >= thresholdMs) {
                val daysInactive = inactiveDurationMs / (24 * 60 * 60 * 1000L)
                reminders.add(
                    NotificationRecord(
                        treeId = treeId,
                        userId = ownerId,
                        title = "Member Inactivity Reminder",
                        message = "${member.userName.ifBlank { "A collaborator" }} (${member.role}) has had no activity for $daysInactive days. Consider reviewing their access or inviting them to contribute.",
                        type = CATEGORY_RECORDS,
                        targetId = member.id,
                        timestamp = nowMs
                    )
                )
            }
        }

        return reminders
    }

    // ══════════════════════════════════════════════════════════════
    // 8.a Access Request and Approval Notifications
    // ══════════════════════════════════════════════════════════════

    fun createAccessRequestNotification(
        treeId: String,
        ownerId: String,
        requesterName: String,
        requesterEmail: String,
        treeName: String,
        requestedRole: String = "Viewer"
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = ownerId,
            title = "New Join Request",
            message = "$requesterName ($requesterEmail) requested to join '$treeName' as $requestedRole.",
            type = CATEGORY_ACCESS,
            targetId = treeId,
            timestamp = System.currentTimeMillis()
        )
    }

    fun createAccessDecisionNotification(
        treeId: String,
        applicantUserId: String,
        treeName: String,
        approved: Boolean,
        assignedRole: String
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = applicantUserId,
            title = if (approved) "Access Request Approved" else "Access Request Declined",
            message = if (approved) {
                "Your request to join '$treeName' has been approved with role: $assignedRole."
            } else {
                "Your request to join '$treeName' was declined by the tree owner."
            },
            type = CATEGORY_ACCESS,
            targetId = treeId,
            timestamp = System.currentTimeMillis()
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 8.b Validation and Duplicate Record Alerts
    // ══════════════════════════════════════════════════════════════

    fun createDuplicateAlertNotification(
        treeId: String,
        userId: String,
        memberName: String,
        existingId: String
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = userId,
            title = "Duplicate Record Detected",
            message = "Potential duplicate match detected for $memberName. Review and merge or confirm independent record.",
            type = CATEGORY_VALIDATION,
            targetId = existingId,
            timestamp = System.currentTimeMillis()
        )
    }

    fun createValidationConflictNotification(
        treeId: String,
        userId: String,
        memberName: String,
        conflictDetails: String,
        memberId: String = ""
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = userId,
            title = "Biological Data Warning",
            message = "Validation notice for $memberName: $conflictDetails",
            type = CATEGORY_VALIDATION,
            targetId = memberId,
            timestamp = System.currentTimeMillis()
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 8.c Privacy and Permission Alerts
    // ══════════════════════════════════════════════════════════════

    fun createPermissionRestrictedNotification(
        treeId: String,
        userId: String,
        actionAttempted: String,
        currentRole: String
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = userId,
            title = "Action Restricted ($currentRole)",
            message = "Action '$actionAttempted' was restricted. Viewers have read-only access. Contact the tree owner to request Editor permissions.",
            type = CATEGORY_SECURITY,
            targetId = treeId,
            timestamp = System.currentTimeMillis()
        )
    }

    fun createRoleUpdatedNotification(
        treeId: String,
        userId: String,
        treeName: String,
        newRole: String
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = userId,
            title = "Role Permission Updated",
            message = "Your role in '$treeName' has been updated to $newRole.",
            type = CATEGORY_SECURITY,
            targetId = treeId,
            timestamp = System.currentTimeMillis()
        )
    }

    fun createPrivacySettingsNotification(
        treeId: String,
        treeName: String,
        isPrivate: Boolean
    ): NotificationRecord {
        val mode = if (isPrivate) "Private" else "Public"
        return NotificationRecord(
            treeId = treeId,
            userId = "", // broadcast to all tree members
            title = "Tree Privacy Updated",
            message = "Tree '$treeName' privacy setting has been updated to $mode.",
            type = CATEGORY_SECURITY,
            targetId = treeId,
            timestamp = System.currentTimeMillis()
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 8.d Account Recovery Notifications
    // ══════════════════════════════════════════════════════════════

    fun createAccountRecoveryNotification(
        email: String,
        treeId: String = "system"
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = email,
            title = "Password Reset Requested",
            message = "A password recovery email was sent to $email. If you did not initiate this request, verify your account credentials immediately.",
            type = CATEGORY_SECURITY,
            targetId = email,
            timestamp = System.currentTimeMillis()
        )
    }

    fun createPasswordChangedNotification(
        userId: String,
        treeId: String = "system"
    ): NotificationRecord {
        return NotificationRecord(
            treeId = treeId,
            userId = userId,
            title = "Password Changed Successfully",
            message = "Your account password was updated directly from your profile settings.",
            type = CATEGORY_SECURITY,
            targetId = userId,
            timestamp = System.currentTimeMillis()
        )
    }
}

