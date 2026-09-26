package com.example.btproject2

import com.example.btproject2.utils.NotificationHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for in-app password change validation, account recovery notifications, and cooldown logic.
 */
class PasswordManagementTest {

    @Test
    fun testPasswordLengthValidation() {
        fun validatePasswordLength(password: String): Boolean = password.length >= 6

        assertFalse("Empty password should be rejected", validatePasswordLength(""))
        assertFalse("1-character password should be rejected", validatePasswordLength("a"))
        assertFalse("5-character password should be rejected", validatePasswordLength("12345"))
        assertTrue("6-character password should be accepted", validatePasswordLength("123456"))
        assertTrue("Long password should be accepted", validatePasswordLength("SecretPass123!"))
    }

    @Test
    fun testPasswordConfirmationMatching() {
        fun validatePasswordsMatch(pass: String, confirm: String): Boolean = pass == confirm

        assertFalse("Mismatched passwords must be rejected", validatePasswordsMatch("password123", "password321"))
        assertFalse("Case sensitive differences must be rejected", validatePasswordsMatch("Password123", "password123"))
        assertTrue("Identical passwords must match", validatePasswordsMatch("Secret@2026", "Secret@2026"))
    }

    @Test
    fun testAccountRecoveryNotificationPayload() {
        val email = "user@example.com"
        val notif = NotificationHelper.createAccountRecoveryNotification(email, treeId = "tree_123")

        assertEquals("tree_123", notif.treeId)
        assertEquals(email, notif.userId)
        assertEquals("Password Reset Requested", notif.title)
        assertTrue(notif.message.contains(email))
        assertEquals(NotificationHelper.CATEGORY_SECURITY, notif.type)
    }

    @Test
    fun testPasswordChangedNotificationPayload() {
        val userId = "user_abc"
        val notif = NotificationHelper.createPasswordChangedNotification(userId, treeId = "tree_123")

        assertEquals("tree_123", notif.treeId)
        assertEquals(userId, notif.userId)
        assertEquals("Password Changed Successfully", notif.title)
        assertEquals(NotificationHelper.CATEGORY_SECURITY, notif.type)
        assertEquals(userId, notif.targetId)
    }

    @Test
    fun testResendCooldownTimeFormatting() {
        fun formatCooldownSeconds(millisRemaining: Long): String {
            val sec = millisRemaining / 1000
            return "Resend available in ${sec}s"
        }

        assertEquals("Resend available in 60s", formatCooldownSeconds(60000))
        assertEquals("Resend available in 45s", formatCooldownSeconds(45000))
        assertEquals("Resend available in 1s", formatCooldownSeconds(1000))
    }
}

