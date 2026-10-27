package com.example.btproject2

import com.example.btproject2.models.DemoAccountRepository
import com.example.btproject2.models.VaultMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Concept 1 Dual-Mode Account Vault (3D Flip-Safe, Binder Folders & 1-Tap Keycards).
 */
class DualModeAccountVaultTest {

    @Before
    fun setUp() {
        DemoAccountRepository.resetCustomVaultForTesting()
    }

    @After
    fun tearDown() {
        DemoAccountRepository.resetCustomVaultForTesting()
    }

    @Test
    fun testDefaultFoldersExistForUserAndAdminModes() {
        val userFolders = DemoAccountRepository.getFoldersByMode(VaultMode.USER)
        assertEquals(2, userFolders.size)
        assertTrue(userFolders.any { it.id == DemoAccountRepository.FOLDER_USER_PERSONAL && it.name == "Personal Accounts" })
        assertTrue(userFolders.any { it.id == DemoAccountRepository.FOLDER_USER_FRIENDS && it.name == "Friends & Clan Mates" })

        val adminFolders = DemoAccountRepository.getFoldersByMode(VaultMode.ADMIN_DEMO)
        assertEquals(2, adminFolders.size)
        assertTrue(adminFolders.any { it.id == DemoAccountRepository.FOLDER_ADMIN_CITADEL && it.name == "Citadel SuperAdmins" })
        assertTrue(adminFolders.any { it.id == DemoAccountRepository.FOLDER_ADMIN_CLANS && it.name == "Pre-Seeded Demo Clans" })
    }

    @Test
    fun testPreSeededDemoAccountsAreAssignedToAdminFolders() {
        val citadelAccounts = DemoAccountRepository.getAccountsInFolder(DemoAccountRepository.FOLDER_ADMIN_CITADEL)
        assertEquals(1, citadelAccounts.size)
        assertEquals("admin@kintrace.ph", citadelAccounts.first().email)

        val clanAccounts = DemoAccountRepository.getAccountsInFolder(DemoAccountRepository.FOLDER_ADMIN_CLANS)
        assertEquals(2, clanAccounts.size)
        assertTrue(clanAccounts.any { it.email == "dumgen8@gmail.com" })
        assertTrue(clanAccounts.any { it.email == "gendums5@gmail.com" })
    }

    @Test
    fun testCreateAndDeleteFolderInBothModes() {
        val customUserFolder = DemoAccountRepository.createFolder(
            name = "Capstone Defense Viewers",
            iconEmoji = "👁️",
            mode = VaultMode.USER
        )
        assertEquals(VaultMode.USER, customUserFolder.vaultMode)
        assertEquals(3, DemoAccountRepository.getFoldersByMode(VaultMode.USER).size)

        val customAdminFolder = DemoAccountRepository.createFolder(
            name = "Panelist Test Accounts",
            iconEmoji = "🛡️",
            mode = VaultMode.ADMIN_DEMO
        )
        assertEquals(VaultMode.ADMIN_DEMO, customAdminFolder.vaultMode)
        assertEquals(3, DemoAccountRepository.getFoldersByMode(VaultMode.ADMIN_DEMO).size)

        // System folders cannot be deleted
        assertFalse(DemoAccountRepository.deleteFolder(DemoAccountRepository.FOLDER_USER_PERSONAL))

        // Custom folders can be deleted
        assertTrue(DemoAccountRepository.deleteFolder(customUserFolder.id))
        assertEquals(2, DemoAccountRepository.getFoldersByMode(VaultMode.USER).size)
    }

    @Test
    fun testAddAndDeleteKeycardCredentialInsideFolder() {
        val folder = DemoAccountRepository.createFolder(
            name = "My Personal Accounts",
            iconEmoji = "👑",
            mode = VaultMode.USER
        )

        val keycard = DemoAccountRepository.addAccountToFolder(
            folderId = folder.id,
            name = "Renzy Owner",
            email = "renzy.owner@gmail.com",
            password = "SecretPassword123",
            roleBadge = "OWNER",
            emojiIcon = "👑",
            mode = VaultMode.USER
        )

        val folderAccounts = DemoAccountRepository.getAccountsInFolder(folder.id)
        assertEquals(1, folderAccounts.size)
        assertEquals("Renzy Owner", folderAccounts.first().name)
        assertEquals("renzy.owner@gmail.com", folderAccounts.first().email)
        assertEquals("SecretPassword123", folderAccounts.first().password)
        assertTrue(folderAccounts.first().isCustomAccount)

        // Lookup by email and ID must work across custom vault keycards
        assertNotNull(DemoAccountRepository.findByEmail("renzy.owner@gmail.com"))
        assertNotNull(DemoAccountRepository.findById(keycard.id))

        // Built-in accounts cannot be deleted
        assertFalse(DemoAccountRepository.deleteAccountFromFolder("augustine_russell"))

        // Custom keycards can be deleted
        assertTrue(DemoAccountRepository.deleteAccountFromFolder(keycard.id))
        assertTrue(DemoAccountRepository.getAccountsInFolder(folder.id).isEmpty())
    }

    @Test
    fun testKeycardPreFlightRestrictionsAndBurnerDomainBlocking() {
        val folderAccounts = DemoAccountRepository.getAccountsInFolder(DemoAccountRepository.FOLDER_ADMIN_CLANS)

        // 1. Blank / too-short holder name rejected
        val shortNameErr = com.example.btproject2.utils.KeycardAccountVerifier.validatePreFlightInputs(
            holderName = "A",
            email = "valid.user@gmail.com",
            password = "SecretPassword123",
            existingFolderAccounts = folderAccounts
        )
        assertNotNull(shortNameErr)
        assertEquals("name", shortNameErr?.field)

        // 2. Disposable burner domain rejected (same rule as RegisterActivity)
        val burnerErr = com.example.btproject2.utils.KeycardAccountVerifier.validatePreFlightInputs(
            holderName = "Gabriel Cremen",
            email = "gabriel@tempmail.com",
            password = "SecretPassword123",
            existingFolderAccounts = folderAccounts
        )
        assertNotNull(burnerErr)
        assertEquals("email", burnerErr?.field)

        // 3. Duplicate email inside the same folder rejected
        val duplicateErr = com.example.btproject2.utils.KeycardAccountVerifier.validatePreFlightInputs(
            holderName = "Augustine Russell",
            email = "dumgen8@gmail.com",
            password = "123456789",
            existingFolderAccounts = folderAccounts
        )
        assertNotNull(duplicateErr)
        assertEquals("email", duplicateErr?.field)

        // 4. Short / empty password rejected
        val passErr = com.example.btproject2.utils.KeycardAccountVerifier.validatePreFlightInputs(
            holderName = "Gabriel Cremen",
            email = "gabriel@gmail.com",
            password = "123",
            existingFolderAccounts = folderAccounts
        )
        assertNotNull(passErr)
        assertEquals("password", passErr?.field)

        // 5. Valid pre-flight inputs pass cleanly
        val validCheck = com.example.btproject2.utils.KeycardAccountVerifier.validatePreFlightInputs(
            holderName = "Gabriel Cremen",
            email = "gabriel.cremen@gmail.com",
            password = "SecretPassword123",
            existingFolderAccounts = folderAccounts
        )
        org.junit.Assert.assertNull(validCheck)
    }

    @Test
    fun testKeycardVerificationOutcomesProvideClearAdvisoryMessages() {
        val notReg = com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered("unregistered@gmail.com")
        assertTrue(notReg.message.contains("Create Account"))
        assertTrue(notReg.message.contains("verify"))

        val unverified = com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.EmailNotVerified("pending@gmail.com")
        assertTrue(unverified.message.contains("not been verified"))

        val wrongPass = com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.WrongPassword("user@gmail.com")
        assertTrue(wrongPass.message.contains("password"))
    }
}

