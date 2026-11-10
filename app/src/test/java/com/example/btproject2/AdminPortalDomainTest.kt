package com.example.btproject2

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AdminAuditLog
import com.example.btproject2.models.DemoAccountRepository
import com.example.btproject2.models.FamilyTree
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
    fun testTreeQuotaStatus_CapacityExpansionRules() {
        val baseTree = FamilyTree(
            id = "tree_test",
            name = "Test Clan",
            memberQuota = FamilyTree.DEFAULT_FREEMIUM_QUOTA // 50
        )
        val statusNormal = FirestoreHelper.TreeQuotaStatus(
            tree = baseTree,
            liveMemberCount = 10,
            resolvedOwnerDisplay = "owner@test.ph"
        )
        assertEquals(50, statusNormal.effectiveQuota)
        assertFalse(statusNormal.isCapped)
        assertFalse(statusNormal.isNearCap)
        assertFalse(statusNormal.isExpandedTier)

        val statusCapped = FirestoreHelper.TreeQuotaStatus(
            tree = baseTree,
            liveMemberCount = 50,
            resolvedOwnerDisplay = "owner@test.ph"
        )
        assertTrue(statusCapped.isCapped)

        val expandedTree = baseTree.copy(memberQuota = 200)
        val statusExpanded = FirestoreHelper.TreeQuotaStatus(
            tree = expandedTree,
            liveMemberCount = 55,
            resolvedOwnerDisplay = "owner@test.ph"
        )
        assertEquals(200, statusExpanded.effectiveQuota)
        assertFalse(statusExpanded.isCapped)
        assertTrue(statusExpanded.isExpandedTier)
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
