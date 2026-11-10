package com.example.btproject2.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val suffix: String = "",
    val email: String = "",
    val role: String = "user", // "user", "admin"
    val currentTreeId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tosAcceptedAt: Long = 0L,
    val onboardingState: UserOnboardingState? = null,
    val photoUri: String = "",
    val photoBase64: String = ""
) {
    @get:Exclude
    val middleInitial: String
        get() = middleName.trim().firstOrNull()?.let { "$it." } ?: ""

    @get:Exclude
    val standardDisplayName: String
        get() {
            val parts = listOf(firstName.trim(), middleInitial, lastName.trim(), suffix.trim()).filter { it.isNotEmpty() }
            return if (parts.isNotEmpty()) parts.joinToString(" ") else displayName
        }

    @get:Exclude
    val formalFullName: String
        get() {
            val parts = listOf(firstName.trim(), middleName.trim(), lastName.trim(), suffix.trim()).filter { it.isNotEmpty() }
            return if (parts.isNotEmpty()) parts.joinToString(" ") else displayName
        }

    @get:Exclude
    val avatarMonogram: String
        get() {
            val f = firstName.trim().firstOrNull()?.uppercaseChar()
            val m = middleName.trim().firstOrNull()?.uppercaseChar()
            val l = lastName.trim().firstOrNull()?.uppercaseChar()
            if (f != null && m != null && l != null) return "$f$m$l"
            if (f != null && l != null) return "$f$l"
            if (f != null) return "$f"
            val fallback = displayName.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                fallback.size >= 2 -> "${fallback.first().first().uppercaseChar()}${fallback.last().first().uppercaseChar()}"
                fallback.isNotEmpty() -> "${fallback.first().first().uppercaseChar()}"
                else -> "U"
            }
        }
}
