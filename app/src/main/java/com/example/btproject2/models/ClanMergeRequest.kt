package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Represents a pending, approved, or completed authorization request
 * to merge with an external family tree via a Clan Merge Code.
 */
@IgnoreExtraProperties
data class ClanMergeRequest(
    val id: String = "",
    val code: String = "",
    val requesterTreeId: String = "",
    val requesterTreeName: String = "",
    val requesterOwnerId: String = "",
    val requesterOwnerName: String = "",
    val targetTreeId: String = "",
    val targetTreeName: String = "",
    val targetOwnerId: String = "",
    val targetOwnerName: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + DEFAULT_EXPIRY_MILLIS,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdMasterTreeId: String = ""
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"

        // 48 hours validity by default
        const val DEFAULT_EXPIRY_MILLIS = 48L * 60L * 60L * 1000L
    }

    fun isExpired(): Boolean {
        return status == STATUS_EXPIRED || (expiresAt > 0 && System.currentTimeMillis() > expiresAt)
    }

    fun canUnlockSynthesis(): Boolean {
        return status == STATUS_APPROVED && createdMasterTreeId.isBlank() && !isExpired()
    }
}
