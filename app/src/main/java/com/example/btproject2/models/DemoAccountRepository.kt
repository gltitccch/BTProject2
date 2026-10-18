package com.example.btproject2.models

import com.example.btproject2.R

/**
 * Central repository catalog providing verified demo accounts with pre-seeded
 * family lineages, full module access, and 1-tap sign-in capability.
 */
object DemoAccountRepository {

    private val DEMO_ACCOUNTS = listOf(
        DemoAccount(
            id = "augustine_russell",
            name = "Augustine Russell",
            roleTitle = "Clan Patriarch",
            roleBadge = "PATRIARCH",
            email = "dumgen8@gmail.com",
            password = "123456789",
            treeSummary = "20 Members • 4 Generations • 44 Records",
            emojiIcon = "👑",
            badgeColorRes = R.color.gold_accent
        ),
        DemoAccount(
            id = "karl_malone",
            name = "Karl Malone",
            roleTitle = "Branch Collaborator",
            roleBadge = "COLLABORATOR",
            email = "gendums5@gmail.com",
            password = "123456789r",
            treeSummary = "20 Members • Malone Clan • 100% Quests",
            emojiIcon = "🌿",
            badgeColorRes = R.color.emerald_primary
        ),
        DemoAccount(
            id = "system_admin",
            name = "Super Admin (KinTrace)",
            roleTitle = "System Administrator",
            roleBadge = "ADMIN",
            email = "admin@kintrace.ph",
            password = "123456789",
            treeSummary = "Global Overseer • 42 Trees • Citadel Hub",
            emojiIcon = "🛡️",
            badgeColorRes = R.color.gold_accent
        )
    )

    fun getDemoAccounts(): List<DemoAccount> = DEMO_ACCOUNTS

    fun findById(id: String): DemoAccount? =
        DEMO_ACCOUNTS.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun findByEmail(email: String): DemoAccount? =
        DEMO_ACCOUNTS.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

    fun isDemoEmail(email: String?): Boolean =
        email != null && DEMO_ACCOUNTS.any { it.email.equals(email.trim(), ignoreCase = true) }

    fun isAdminEmail(email: String?): Boolean =
        email != null && (email.trim().equals("admin@kintrace.ph", ignoreCase = true) ||
            DEMO_ACCOUNTS.any { it.email.equals(email.trim(), ignoreCase = true) && it.roleBadge == "ADMIN" })
}
