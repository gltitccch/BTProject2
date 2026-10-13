package com.example.btproject2.models

import androidx.annotation.ColorRes
import com.example.btproject2.R

/**
 * Encapsulates a verified demo account for 1-Tap authentication and instant module access.
 */
data class DemoAccount(
    val id: String,
    val name: String,
    val roleTitle: String,
    val roleBadge: String,
    val email: String,
    val password: String,
    val treeSummary: String,
    val emojiIcon: String,
    @get:ColorRes val badgeColorRes: Int = R.color.gold_accent
)
