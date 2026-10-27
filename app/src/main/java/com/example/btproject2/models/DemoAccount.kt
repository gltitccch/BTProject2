package com.example.btproject2.models

import androidx.annotation.ColorRes
import com.example.btproject2.R

/**
 * Defines the two distinct operating modes of the KinTrace 3D Flip-Safe Account Vault:
 * - [USER]: Personal, friend, and custom user accounts.
 * - [ADMIN_DEMO]: Super Admin and pre-seeded defense evaluation demo accounts.
 */
enum class VaultMode(val storageKey: String, val displayTitle: String, val pillSubtitle: String) {
    USER(
        storageKey = "USER",
        displayTitle = "User Account Vault",
        pillSubtitle = "MODE 1 • PERSONAL SAFE"
    ),
    ADMIN_DEMO(
        storageKey = "ADMIN_DEMO",
        displayTitle = "Admin & Demo Vault",
        pillSubtitle = "MODE 2 • CITADEL SAFE"
    );

    companion object {
        fun fromKey(key: String?): VaultMode {
            return values().firstOrNull { it.storageKey.equals(key?.trim(), ignoreCase = true) } ?: USER
        }
    }
}

/**
 * Represents a stepped archive binder tab folder inside a specific [VaultMode].
 */
data class VaultFolder(
    val id: String,
    val name: String,
    val iconEmoji: String = "📁",
    val vaultMode: VaultMode = VaultMode.USER,
    val isSystemFolder: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Encapsulates a verified or user-saved account keycard for 1-Tap authentication and instant module access.
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
    @get:ColorRes val badgeColorRes: Int = R.color.gold_accent,
    val vaultMode: VaultMode = VaultMode.ADMIN_DEMO,
    val folderId: String = "admin_preseeded_clans",
    val folderName: String = "Pre-Seeded Demo Clans",
    val isCustomAccount: Boolean = false
)
