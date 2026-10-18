package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * MergeCollisionRecord — Represents a deadlocked branch merge conflict that was blocked
 * by the validation engine and escalated to the Admin Ops Desk.
 */
@IgnoreExtraProperties
data class MergeCollisionRecord(
    val id: String = "",
    val sourceTree1Id: String = "",
    val sourceTree1Name: String = "",
    val sourceTree2Id: String = "",
    val sourceTree2Name: String = "",
    val personAId: String = "",
    val personAName: String = "",
    val personBId: String = "",
    val personBName: String = "",
    val conflictType: String = "MERGE_BLOCKED",
    val conflictSummary: String = "",
    val conflictDetails: String = "",
    val suggestedResolution: String = "",
    val escalatedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_OPEN,
    val resolvedAt: Long = 0L,
    val resolvedBy: String = ""
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_RESOLVED = "RESOLVED"
    }
}
