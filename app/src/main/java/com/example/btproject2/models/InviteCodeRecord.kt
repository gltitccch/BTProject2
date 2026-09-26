package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class InviteCodeRecord(
    val code: String = "",
    val treeId: String = "",
    val treeName: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L,         // 0L = never expires
    val usageLimit: Int = 0,          // 0 = unlimited
    val usageCount: Int = 0,
    val status: String = STATUS_ACTIVE // "active", "used", "expired", "revoked"
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_USED = "used"
        const val STATUS_EXPIRED = "expired"
        const val STATUS_REVOKED = "revoked"
    }

    fun isExpired(): Boolean =
        status.equals(STATUS_EXPIRED, ignoreCase = true) ||
        (expiresAt > 0L && expiresAt < System.currentTimeMillis())

    fun isRevoked(): Boolean =
        status.equals(STATUS_REVOKED, ignoreCase = true)

    fun isUsageLimitReached(): Boolean =
        status.equals(STATUS_USED, ignoreCase = true) ||
        (usageLimit > 0 && usageCount >= usageLimit)

    fun isValid(): Boolean =
        !isExpired() && !isRevoked() && !isUsageLimitReached()
}

