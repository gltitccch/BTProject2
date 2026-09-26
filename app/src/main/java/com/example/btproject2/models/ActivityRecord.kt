package com.example.btproject2.models

/**
 * ActivityRecord — Represents an audit log or activity record (DFD Store D8).
 *
 * Types:
 * - "MEMBER_ADDED": When a new family member is enrolled.
 * - "MEMBER_UPDATED": When member details or relationships are edited.
 * - "MEMBER_DELETED": When a member is removed.
 * - "TRACE_PERFORMED": When a bloodline tracing query is executed.
 * - "BRANCH_MERGED": When two branches are unified.
 * - "TREE_CREATED": When a new tree is created.
 */
data class ActivityRecord(
    val id: String = "",
    val treeId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = "MEMBER_ADDED",
    val description: String = "",
    val targetId: String = "",
    val targetName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

