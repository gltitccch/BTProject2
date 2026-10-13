package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Immutable audit log record capturing administrative actions for compliance
 * with the Philippine Data Privacy Act (RA 10173) and Capstone Governance.
 */
@IgnoreExtraProperties
data class AdminAuditLog(
    val id: String = "",
    val adminEmail: String = "admin@kintrace.ph",
    val actionType: String = "",
    val targetId: String = "",
    val details: String = "",
    val checkHash: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
