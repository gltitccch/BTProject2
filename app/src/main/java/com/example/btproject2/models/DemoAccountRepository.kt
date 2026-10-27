package com.example.btproject2.models

import android.content.Context
import com.example.btproject2.R
import com.example.btproject2.utils.KinTraceCryptoEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central repository catalog providing verified demo accounts, Dual-Mode Vault folders
 * (Mode 1: User Account Vault & Mode 2: Admin & Demo Vault), encrypted local storage,
 * and 1-tap sign-in capability.
 */
object DemoAccountRepository {

    private const val PREFS_NAME = "kintrace_dual_vault_prefs"
    private const val KEY_FOLDERS_JSON = "key_vault_folders_v1"
    private const val KEY_CUSTOM_ACCOUNTS_JSON = "key_vault_custom_accounts_v1"
    private const val KEY_LAST_ACTIVE_MODE = "key_vault_last_active_mode"
    private const val VAULT_CRYPTO_SCOPE = "kintrace_dual_mode_vault_2026"

    const val FOLDER_USER_PERSONAL = "user_personal"
    const val FOLDER_USER_FRIENDS = "user_friends"
    const val FOLDER_ADMIN_CITADEL = "admin_citadel"
    const val FOLDER_ADMIN_CLANS = "admin_preseeded_clans"

    private val DEFAULT_FOLDERS = listOf(
        VaultFolder(
            id = FOLDER_USER_PERSONAL,
            name = "Personal Accounts",
            iconEmoji = "👑",
            vaultMode = VaultMode.USER,
            isSystemFolder = true,
            createdAt = 1000L
        ),
        VaultFolder(
            id = FOLDER_USER_FRIENDS,
            name = "Friends & Clan Mates",
            iconEmoji = "🌿",
            vaultMode = VaultMode.USER,
            isSystemFolder = true,
            createdAt = 2000L
        ),
        VaultFolder(
            id = FOLDER_ADMIN_CITADEL,
            name = "Citadel SuperAdmins",
            iconEmoji = "🛡️",
            vaultMode = VaultMode.ADMIN_DEMO,
            isSystemFolder = true,
            createdAt = 3000L
        ),
        VaultFolder(
            id = FOLDER_ADMIN_CLANS,
            name = "Pre-Seeded Demo Clans",
            iconEmoji = "👑",
            vaultMode = VaultMode.ADMIN_DEMO,
            isSystemFolder = true,
            createdAt = 4000L
        )
    )

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
            badgeColorRes = R.color.gold_accent,
            vaultMode = VaultMode.ADMIN_DEMO,
            folderId = FOLDER_ADMIN_CLANS,
            folderName = "Pre-Seeded Demo Clans",
            isCustomAccount = false
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
            badgeColorRes = R.color.emerald_primary,
            vaultMode = VaultMode.ADMIN_DEMO,
            folderId = FOLDER_ADMIN_CLANS,
            folderName = "Pre-Seeded Demo Clans",
            isCustomAccount = false
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
            badgeColorRes = R.color.gold_accent,
            vaultMode = VaultMode.ADMIN_DEMO,
            folderId = FOLDER_ADMIN_CITADEL,
            folderName = "Citadel SuperAdmins",
            isCustomAccount = false
        )
    )

    private val customFolders = mutableListOf<VaultFolder>()
    private val customAccounts = mutableListOf<DemoAccount>()
    private var isStorageLoaded = false

    fun getDemoAccounts(): List<DemoAccount> = DEMO_ACCOUNTS

    fun getAllVaultAccounts(): List<DemoAccount> = DEMO_ACCOUNTS + customAccounts

    fun findById(id: String): DemoAccount? =
        getAllVaultAccounts().firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun findByEmail(email: String): DemoAccount? =
        getAllVaultAccounts().firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

    fun isDemoEmail(email: String?): Boolean =
        email != null && (DEMO_ACCOUNTS.any { it.email.equals(email.trim(), ignoreCase = true) } || isNewUserPreviewEmail(email))

    fun isNewUserPreviewEmail(email: String?): Boolean =
        email != null && email.trim().equals("newuser@kintrace.ph", ignoreCase = true)

    fun isAdminEmail(email: String?): Boolean =
        email != null && (email.trim().equals("admin@kintrace.ph", ignoreCase = true) ||
            getAllVaultAccounts().any { it.email.equals(email.trim(), ignoreCase = true) && it.roleBadge == "ADMIN" })

    // =========================================================================
    // DUAL-MODE VAULT & FOLDER MANAGEMENT
    // =========================================================================

    @Synchronized
    fun ensureLoaded(context: Context?) {
        if (context == null || isStorageLoaded) return
        loadVaultFromStorage(context)
        isStorageLoaded = true
    }

    @Synchronized
    fun getFoldersByMode(mode: VaultMode, context: Context? = null): List<VaultFolder> {
        ensureLoaded(context)
        val defaults = DEFAULT_FOLDERS.filter { it.vaultMode == mode }
        val customs = customFolders.filter { it.vaultMode == mode }
        return defaults + customs
    }

    @Synchronized
    fun getAccountsInFolder(folderId: String, context: Context? = null): List<DemoAccount> {
        ensureLoaded(context)
        val builtIn = DEMO_ACCOUNTS.filter { it.folderId == folderId }
        val custom = customAccounts.filter { it.folderId == folderId }
        return builtIn + custom
    }

    @Synchronized
    fun getAccountsByMode(mode: VaultMode, context: Context? = null): List<DemoAccount> {
        ensureLoaded(context)
        val builtIn = DEMO_ACCOUNTS.filter { it.vaultMode == mode }
        val custom = customAccounts.filter { it.vaultMode == mode }
        return builtIn + custom
    }

    @Synchronized
    fun createFolder(
        name: String,
        iconEmoji: String = "📁",
        mode: VaultMode = VaultMode.USER,
        context: Context? = null
    ): VaultFolder {
        ensureLoaded(context)
        val cleanName = name.trim().ifEmpty { "New Folder" }
        val cleanEmoji = iconEmoji.trim().ifEmpty { "📁" }
        val folder = VaultFolder(
            id = "folder_${mode.storageKey.lowercase()}_${System.currentTimeMillis()}_${customFolders.size + 1}",
            name = cleanName,
            iconEmoji = cleanEmoji,
            vaultMode = mode,
            isSystemFolder = false,
            createdAt = System.currentTimeMillis()
        )
        customFolders.add(folder)
        if (context != null) {
            saveVaultToStorage(context)
        }
        return folder
    }

    @Synchronized
    fun deleteFolder(folderId: String, context: Context? = null): Boolean {
        ensureLoaded(context)
        // System folders cannot be deleted
        if (DEFAULT_FOLDERS.any { it.id == folderId }) return false
        val removed = customFolders.removeAll { it.id == folderId }
        if (removed) {
            customAccounts.removeAll { it.folderId == folderId }
            if (context != null) {
                saveVaultToStorage(context)
            }
        }
        return removed
    }

    @Synchronized
    fun addAccountToFolder(
        folderId: String,
        name: String,
        email: String,
        password: String,
        roleBadge: String = "OWNER",
        emojiIcon: String = "👑",
        mode: VaultMode? = null,
        treeSummary: String = "1-Tap Vault Keycard • Instant Sign-In",
        context: Context? = null
    ): DemoAccount {
        ensureLoaded(context)
        val allFolders = DEFAULT_FOLDERS + customFolders
        val targetFolder = allFolders.firstOrNull { it.id == folderId }
        val resolvedMode = mode ?: targetFolder?.vaultMode ?: VaultMode.USER
        val resolvedFolderName = targetFolder?.name ?: "Personal Accounts"
        val cleanRole = roleBadge.trim().uppercase().ifEmpty { "OWNER" }
        val roleTitle = when (cleanRole) {
            "ADMIN" -> "System Administrator"
            "PATRIARCH" -> "Clan Patriarch"
            "OWNER" -> "Clan Owner"
            "COLLABORATOR" -> "Branch Collaborator"
            "VIEWER" -> "Lineage Viewer"
            else -> cleanRole
        }

        // Replace existing custom account in the same folder if same email
        customAccounts.removeAll {
            it.folderId == folderId && it.email.equals(email.trim(), ignoreCase = true)
        }

        val account = DemoAccount(
            id = "cred_${System.currentTimeMillis()}_${customAccounts.size + 1}",
            name = name.trim().ifEmpty { email.substringBefore("@") },
            roleTitle = roleTitle,
            roleBadge = cleanRole,
            email = email.trim(),
            password = password,
            treeSummary = treeSummary,
            emojiIcon = emojiIcon.trim().ifEmpty { "🔑" },
            badgeColorRes = R.color.gold_accent,
            vaultMode = resolvedMode,
            folderId = folderId,
            folderName = resolvedFolderName,
            isCustomAccount = true
        )
        customAccounts.add(account)
        if (context != null) {
            saveVaultToStorage(context)
        }
        return account
    }

    @Synchronized
    fun deleteAccountFromFolder(accountId: String, context: Context? = null): Boolean {
        ensureLoaded(context)
        // Built-in accounts cannot be deleted
        if (DEMO_ACCOUNTS.any { it.id == accountId }) return false
        val removed = customAccounts.removeAll { it.id == accountId }
        if (removed && context != null) {
            saveVaultToStorage(context)
        }
        return removed
    }

    fun getLastActiveMode(context: Context?): VaultMode {
        if (context == null) return VaultMode.USER
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LAST_ACTIVE_MODE, VaultMode.USER.storageKey)
        return VaultMode.fromKey(raw)
    }

    fun setLastActiveMode(context: Context?, mode: VaultMode) {
        if (context == null) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ACTIVE_MODE, mode.storageKey)
            .apply()
    }

    @Synchronized
    fun saveVaultToStorage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val foldersArray = JSONArray()
        for (f in customFolders) {
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("iconEmoji", f.iconEmoji)
                put("vaultMode", f.vaultMode.storageKey)
                put("createdAt", f.createdAt)
            }
            foldersArray.put(obj)
        }

        val accountsArray = JSONArray()
        for (a in customAccounts) {
            val encryptedPass = KinTraceCryptoEngine.encryptField(a.password, VAULT_CRYPTO_SCOPE)
            val obj = JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("roleTitle", a.roleTitle)
                put("roleBadge", a.roleBadge)
                put("email", a.email)
                put("password", encryptedPass)
                put("treeSummary", a.treeSummary)
                put("emojiIcon", a.emojiIcon)
                put("vaultMode", a.vaultMode.storageKey)
                put("folderId", a.folderId)
                put("folderName", a.folderName)
            }
            accountsArray.put(obj)
        }

        prefs.edit()
            .putString(KEY_FOLDERS_JSON, foldersArray.toString())
            .putString(KEY_CUSTOM_ACCOUNTS_JSON, accountsArray.toString())
            .apply()
    }

    @Synchronized
    fun loadVaultFromStorage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        customFolders.clear()
        customAccounts.clear()

        val foldersRaw = prefs.getString(KEY_FOLDERS_JSON, null)
        if (!foldersRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(foldersRaw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    customFolders.add(
                        VaultFolder(
                            id = obj.optString("id"),
                            name = obj.optString("name", "Folder"),
                            iconEmoji = obj.optString("iconEmoji", "📁"),
                            vaultMode = VaultMode.fromKey(obj.optString("vaultMode")),
                            isSystemFolder = false,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        val accountsRaw = prefs.getString(KEY_CUSTOM_ACCOUNTS_JSON, null)
        if (!accountsRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(accountsRaw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val storedPass = obj.optString("password", "")
                    val decryptedPass = KinTraceCryptoEngine.decryptField(storedPass, VAULT_CRYPTO_SCOPE)
                    customAccounts.add(
                        DemoAccount(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            roleTitle = obj.optString("roleTitle", "Clan Member"),
                            roleBadge = obj.optString("roleBadge", "OWNER"),
                            email = obj.optString("email"),
                            password = decryptedPass,
                            treeSummary = obj.optString("treeSummary", "1-Tap Vault Keycard"),
                            emojiIcon = obj.optString("emojiIcon", "👑"),
                            badgeColorRes = R.color.gold_accent,
                            vaultMode = VaultMode.fromKey(obj.optString("vaultMode")),
                            folderId = obj.optString("folderId", FOLDER_USER_PERSONAL),
                            folderName = obj.optString("folderName", "Personal Accounts"),
                            isCustomAccount = true
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    fun resetCustomVaultForTesting() {
        customFolders.clear()
        customAccounts.clear()
        isStorageLoaded = false
    }
}
