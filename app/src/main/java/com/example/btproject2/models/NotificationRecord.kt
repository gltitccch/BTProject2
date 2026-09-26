package com.example.btproject2.models

/**
 * NotificationRecord — Represents a system notification or alert (DFD Store D7).
 *
 * Types:
 * - "ACCESS": Access requests, approval or rejection updates.
 * - "RECORDS": Duplicate detection alerts, member added/updated/deleted notices.
 * - "VALIDATION": Data validation errors or relationship conflicts.
 * - "SECURITY": Role changes, privacy setting updates, password resets.
 */
data class NotificationRecord(
    val id: String = "",
    val treeId: String = "",
    val userId: String = "",          // Recipient user ID (or empty for tree-wide broadcast)
    val title: String = "",
    val message: String = "",
    val type: String = "RECORDS",     // "ACCESS", "RECORDS", "VALIDATION", "SECURITY"
    val isRead: Boolean = false,
    val targetId: String = "",        // Associated memberId, treeId, or traceId
    val timestamp: Long = System.currentTimeMillis()
)

