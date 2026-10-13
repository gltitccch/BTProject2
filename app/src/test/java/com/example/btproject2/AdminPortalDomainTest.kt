package com.example.btproject2

import com.example.btproject2.models.AdminAuditLog
import com.example.btproject2.models.DemoAccountRepository
import com.example.btproject2.models.ReunionPassRecord
import com.example.btproject2.models.SystemAnnouncement
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Phase 1: System Admin Portal & Maintenance Broadcaster domain models,
 * role-gating data, and cryptographic audit hash badges (FR-11).
 */
class AdminPortalDomainTest {

    @Test
    fun testSystemAnnouncement_DefaultValuesAndFlags() {
        val defaultAnnouncement = SystemAnnouncement()
        assertEquals("active", defaultAnnouncement.id)
        assertFalse(defaultAnnouncement.isActive)
        assertEquals(SystemAnnouncement.SEVERITY_WARNING, defaultAnnouncement.severity)
        assertEquals(SystemAnnouncement.SCOPE_ALL, defaultAnnouncement.audienceScope)
        assertEquals(2, defaultAnnouncement.autoRetractHours)
        assertTrue(defaultAnnouncement.isWarning)
        assertFalse(defaultAnnouncement.isLockdown)
        assertFalse(defaultAnnouncement.isInfo)
    }

    @Test
    fun testSystemAnnouncement_CustomSeverityAndScope() {
        val lockdownAnnouncement = SystemAnnouncement(
            id = "active_broadcast",
            title = "Database Migration in Progress",
            message = "Writes are temporarily paused for maintenance.",
            severity = SystemAnnouncement.SEVERITY_LOCKDOWN,
            audienceScope = SystemAnnouncement.SCOPE_OWNERS,
            timeWindow = "02:00 AM - 04:00 AM PHT",
            autoRetractHours = 2,
            isActive = true,
            createdBy = "admin@kintrace.ph"
        )

        assertTrue(lockdownAnnouncement.isActive)
        assertTrue(lockdownAnnouncement.isLockdown)
        assertFalse(lockdownAnnouncement.isWarning)
        assertFalse(lockdownAnnouncement.isInfo)
        assertEquals(SystemAnnouncement.SCOPE_OWNERS, lockdownAnnouncement.audienceScope)
        assertEquals("Database Migration in Progress", lockdownAnnouncement.title)
    }

    @Test
    fun testAdminAuditLog_StructureAndHashBadge() {
        val timestamp = 1700000000000L
        val log = AdminAuditLog(
            id = "AUDIT_TEST_001",
            adminEmail = "admin@kintrace.ph",
            actionType = "BROADCAST_PUBLISHED",
            targetId = "active_broadcast",
            details = "Dispatched Scheduled Maintenance banner to all users",
            checkHash = "SHA256:7a9f8c...3b1d",
            timestamp = timestamp
        )

        assertEquals("AUDIT_TEST_001", log.id)
        assertEquals("admin@kintrace.ph", log.adminEmail)
        assertEquals("BROADCAST_PUBLISHED", log.actionType)
        assertEquals("SHA256:7a9f8c...3b1d", log.checkHash)
        assertEquals(timestamp, log.timestamp)
    }

    @Test
    fun testReunionPassRecord_DefaultsAndRedemption() {
        val pass = ReunionPassRecord(
            code = "REUNION-MALONE-2026",
            treeId = "tree_malone",
            treeName = "Malone Clan",
            validDays = 30,
            generatedBy = "admin@kintrace.ph"
        )

        assertEquals("REUNION-MALONE-2026", pass.code)
        assertEquals("tree_malone", pass.treeId)
        assertEquals(30, pass.validDays)
        assertFalse(pass.isRedeemed)
        assertEquals("", pass.redeemedByEmail)

        val redeemed = pass.copy(
            isRedeemed = true,
            redeemedByEmail = "clark@kintrace.ph",
            redeemedAt = System.currentTimeMillis()
        )
        assertTrue(redeemed.isRedeemed)
        assertEquals("clark@kintrace.ph", redeemed.redeemedByEmail)
        assertTrue(redeemed.redeemedAt > 0L)
    }

    @Test
    fun testDemoAccountRepository_AdminAccountGating() {
        val accounts = DemoAccountRepository.getDemoAccounts()
        val admin = accounts.firstOrNull { it.id == "system_admin" }
        assertNotNull("Admin demo account must exist in DemoAccountRepository", admin)
        assertEquals("Super Admin (KinTrace)", admin?.name)
        assertEquals("admin@kintrace.ph", admin?.email)
        assertEquals("ADMIN", admin?.roleBadge)

        // Verify normal user accounts are NOT admin
        val normalAccounts = accounts.filter { it.id != "system_admin" }
        normalAccounts.forEach { account ->
            assertNotEquals("ADMIN", account.roleBadge)
        }
    }
}
