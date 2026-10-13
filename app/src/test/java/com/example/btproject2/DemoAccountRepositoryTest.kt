package com.example.btproject2

import com.example.btproject2.models.DemoAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DemoAccountRepository ensuring catalog consistency,
 * verified credentials presence, and lookup methods.
 */
class DemoAccountRepositoryTest {

    @Test
    fun testRepositoryContainsExpectedVerifiedAccounts() {
        val accounts = DemoAccountRepository.getDemoAccounts()
        assertEquals(3, accounts.size)

        val augustine = accounts.firstOrNull { it.id == "augustine_russell" }
        assertNotNull("Augustine Russell must be in catalog", augustine)
        assertEquals("Augustine Russell", augustine?.name)
        assertEquals("dumgen8@gmail.com", augustine?.email)
        assertEquals("123456789", augustine?.password)
        assertEquals("PATRIARCH", augustine?.roleBadge)
        assertEquals("👑", augustine?.emojiIcon)

        val karl = accounts.firstOrNull { it.id == "karl_malone" }
        assertNotNull("Karl Malone must be in catalog", karl)
        assertEquals("Karl Malone", karl?.name)
        assertEquals("gendums5@gmail.com", karl?.email)
        assertEquals("123456789r", karl?.password)
        assertEquals("COLLABORATOR", karl?.roleBadge)
        assertEquals("🌿", karl?.emojiIcon)

        val admin = accounts.firstOrNull { it.id == "system_admin" }
        assertNotNull("System Admin must be in catalog", admin)
        assertEquals("Super Admin (KinTrace)", admin?.name)
        assertEquals("admin@kintrace.ph", admin?.email)
        assertEquals("ADMIN", admin?.roleBadge)
        assertEquals("🛡️", admin?.emojiIcon)
    }

    @Test
    fun testAccountFieldIntegrity() {
        val accounts = DemoAccountRepository.getDemoAccounts()
        for (account in accounts) {
            assertTrue("ID must not be empty", account.id.isNotBlank())
            assertTrue("Name must not be empty", account.name.isNotBlank())
            assertTrue("Role title must not be empty", account.roleTitle.isNotBlank())
            assertTrue("Role badge must not be empty", account.roleBadge.isNotBlank())
            assertTrue("Email must be valid format", account.email.contains("@") && account.email.contains("."))
            assertTrue("Password must be at least 6 characters", account.password.length >= 6)
            assertTrue("Tree summary must not be empty", account.treeSummary.isNotBlank())
            assertTrue("Emoji icon must not be empty", account.emojiIcon.isNotBlank())
        }
    }

    @Test
    fun testFindByIdLookup() {
        val found = DemoAccountRepository.findById("AUGUSTINE_RUSSELL")
        assertNotNull(found)
        assertEquals("dumgen8@gmail.com", found?.email)

        val notFound = DemoAccountRepository.findById("non_existent_user")
        assertNull(notFound)
    }

    @Test
    fun testFindByEmailLookup() {
        val found = DemoAccountRepository.findByEmail("  gendums5@gmail.com  ")
        assertNotNull(found)
        assertEquals("karl_malone", found?.id)

        val notFound = DemoAccountRepository.findByEmail("unknown@gmail.com")
        assertNull(notFound)
    }
}
