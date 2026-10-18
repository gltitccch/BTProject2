package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "user", // "user", "admin"
    val currentTreeId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tosAcceptedAt: Long = 0L,
    val onboardingState: UserOnboardingState? = null
)

