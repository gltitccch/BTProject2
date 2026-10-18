package com.example.btproject2.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Encapsulates a global or audience-scoped maintenance notice or system advisory
 * dispatched by System Administrators (FR-11).
 */
@IgnoreExtraProperties
data class SystemAnnouncement(
    val id: String = "active",
    val title: String = "",
    val message: String = "",
    val severity: String = SEVERITY_WARNING, // INFO, WARNING, LOCKDOWN
    val audienceScope: String = SCOPE_ALL,   // ALL, OWNERS, CAPPED
    val timeWindow: String = "",
    val autoRetractHours: Int = 2,
    @get:PropertyName("isActive") @set:PropertyName("isActive") var isActive: Boolean = false,
    val createdBy: String = "admin@kintrace.ph",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SEVERITY_INFO = "INFO"
        const val SEVERITY_WARNING = "WARNING"
        const val SEVERITY_LOCKDOWN = "LOCKDOWN"

        const val SCOPE_ALL = "ALL"
        const val SCOPE_OWNERS = "OWNERS"
        const val SCOPE_CAPPED = "CAPPED"
    }

    @get:Exclude
    val isLockdown: Boolean get() = severity.equals(SEVERITY_LOCKDOWN, ignoreCase = true)

    @get:Exclude
    val isWarning: Boolean get() = severity.equals(SEVERITY_WARNING, ignoreCase = true)

    @get:Exclude
    val isInfo: Boolean get() = severity.equals(SEVERITY_INFO, ignoreCase = true)

    @get:Exclude
    val isExpired: Boolean
        get() {
            if (autoRetractHours <= 0) return false
            val expiryTime = createdAt + (autoRetractHours * 3600_000L)
            return System.currentTimeMillis() > expiryTime
        }
}
