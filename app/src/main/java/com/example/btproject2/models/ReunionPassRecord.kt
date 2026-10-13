package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Encapsulates a promotional or temporary 30-day unlimited expansion pass for
 * large family reunions, allowing clans to add & merge without permanent free-tier cap breach.
 */
@IgnoreExtraProperties
data class ReunionPassRecord(
    val code: String = "",
    val treeId: String = "",
    val treeName: String = "",
    val validDays: Int = 30,
    val generatedBy: String = "admin@kintrace.ph",
    @get:PropertyName("isRedeemed") @set:PropertyName("isRedeemed") var isRedeemed: Boolean = false,
    val redeemedByEmail: String = "",
    val redeemedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
