package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Represents a recorded oral history audio capsule attached to a family tree member.
 * Designed with a strict 60-second limit and lightweight AAC storage.
 */
@IgnoreExtraProperties
data class VoiceCapsule(
    val id: String = "",
    val title: String = "Family Memory",
    val promptQuestion: String = "",
    val audioUri: String = "",
    val audioBase64: String = "",
    val durationSeconds: Int = 0,
    val durationFormatted: String = "0:00",
    val recordedAt: Long = System.currentTimeMillis(),
    val recordedBy: String = "",
    val fileSizeFormatted: String = ""
)
