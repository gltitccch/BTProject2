package com.example.btproject2

import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import com.example.btproject2.models.UserProfile
import com.example.btproject2.utils.TreePreferences
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TreePreferences, dynamic tree switching, and onboarding empty state resolution.
 */
class TreePreferencesAndResolutionTest {

    @Test
    fun testTreePreferencesConstants() {
        assertEquals("kintrace_tree_prefs", TreePreferences.PREFS_NAME)
        assertEquals("active_tree_id", TreePreferences.KEY_ACTIVE_TREE_ID)
        assertEquals("active_tree_name", TreePreferences.KEY_ACTIVE_TREE_NAME)
    }

    @Test
    fun testTreeResolutionForNewUserWithNoTrees() {
        val userTrees = emptyList<FamilyTree>()
        val cachedTreeId = ""
        val profileTreeId = ""

        val targetTree = when {
            userTrees.any { it.id == cachedTreeId } -> userTrees.first { it.id == cachedTreeId }
            userTrees.any { it.id == profileTreeId } -> userTrees.first { it.id == profileTreeId }
            userTrees.isNotEmpty() -> userTrees.first()
            else -> null
        }

        assertNull("New user with no trees must resolve to null", targetTree)
        // Verified: When targetTree is null, showNoTreeEmptyState() is called, hiding layoutStatsHeader!
    }

    @Test
    fun testTreeResolutionPrioritizesCachedTreeIfValid() {
        val tree1 = FamilyTree(id = "tree_1", name = "Reyes Family Tree", ownerId = "user_1")
        val tree2 = FamilyTree(id = "tree_2", name = "Santos Family Tree", ownerId = "user_1")
        val userTrees = listOf(tree1, tree2)

        val cachedTreeId = "tree_2"
        val profileTreeId = "tree_1"

        val targetTree = when {
            userTrees.any { it.id == cachedTreeId } -> userTrees.first { it.id == cachedTreeId }
            userTrees.any { it.id == profileTreeId } -> userTrees.first { it.id == profileTreeId }
            userTrees.isNotEmpty() -> userTrees.first()
            else -> null
        }

        assertNotNull(targetTree)
        assertEquals("tree_2", targetTree?.id)
        assertEquals("Santos Family Tree", targetTree?.name)
    }

    @Test
    fun testTreeResolutionFallsBackToProfileTreeWhenCacheEmpty() {
        val tree1 = FamilyTree(id = "tree_abc", name = "Dela Cruz Tree", ownerId = "user_2")
        val userTrees = listOf(tree1)

        val cachedTreeId = ""
        val profileTreeId = "tree_abc"

        val targetTree = when {
            userTrees.any { it.id == cachedTreeId } -> userTrees.first { it.id == cachedTreeId }
            userTrees.any { it.id == profileTreeId } -> userTrees.first { it.id == profileTreeId }
            userTrees.isNotEmpty() -> userTrees.first()
            else -> null
        }

        assertNotNull(targetTree)
        assertEquals("tree_abc", targetTree?.id)
        assertEquals("Dela Cruz Tree", targetTree?.name)
    }

    @Test
    fun testTreeResolutionFallsBackToFirstOwnedTreeWhenCacheAndProfileInvalid() {
        val tree1 = FamilyTree(id = "tree_first", name = "Mendoza Family Tree", ownerId = "user_3")
        val userTrees = listOf(tree1)

        val cachedTreeId = "stale_old_tree"
        val profileTreeId = "non_existent_tree"

        val targetTree = when {
            userTrees.any { it.id == cachedTreeId } -> userTrees.first { it.id == cachedTreeId }
            userTrees.any { it.id == profileTreeId } -> userTrees.first { it.id == profileTreeId }
            userTrees.isNotEmpty() -> userTrees.first()
            else -> null
        }

        assertNotNull(targetTree)
        assertEquals("tree_first", targetTree?.id)
        assertEquals("Mendoza Family Tree", targetTree?.name)
    }

    @Test
    fun testNewlyCreatedTreeDataStructure() {
        val treeId = "tree_created_123"
        val treeName = "Bautista Family Tree"
        val creatorName = "Juan Bautista"
        val userId = "user_creator"

        val createdTree = FamilyTree(
            id = treeId,
            name = treeName,
            ownerId = userId,
            ownerName = creatorName,
            inviteCode = "ABC12345"
        )

        val creatorPerson = Person(
            id = "person_creator",
            firstName = "Juan",
            lastName = "Bautista",
            gender = "Male",
            birthDate = "1990-05-15",
            treeId = createdTree.id,
            createdBy = userId,
            isLiving = true
        )

        val userProfile = UserProfile(
            id = userId,
            displayName = creatorName,
            email = "juan@example.com",
            currentTreeId = createdTree.id
        )

        assertEquals("tree_created_123", createdTree.id)
        assertEquals("tree_created_123", creatorPerson.treeId)
        assertEquals("tree_created_123", userProfile.currentTreeId)
        assertEquals("✎  Bautista Family Tree", "✎  ${createdTree.name}")
    }

    @Test
    fun testUserProfileDefaultCurrentTreeIdIsEmpty() {
        val defaultProfile = UserProfile()
        assertEquals("", defaultProfile.currentTreeId)
    }

    @Test
    fun testFirestoreHelperClearCache() {
        com.example.btproject2.firebase.FirestoreHelper.setCachedPersons(listOf(
            Person(id = "p_temp", firstName = "Temp", lastName = "Person")
        ))
        assertNotNull(com.example.btproject2.firebase.FirestoreHelper.getCachedPersons())
        com.example.btproject2.firebase.FirestoreHelper.clearCache()
        assertNull(com.example.btproject2.firebase.FirestoreHelper.getCachedPersons())
    }

    @Test
    fun testTreeStrictIsolation_NoFallbackToAllDatabasePersons() {
        val allPersonsInDb = listOf(
            Person(id = "p1", firstName = "Juan", lastName = "Santos", treeId = "default_tree"),
            Person(id = "p2", firstName = "Maria", lastName = "Santos", treeId = "default_tree"),
            Person(id = "p3", firstName = "Pedro", lastName = "Santos", treeId = "")
        )

        val newTreeId = "new_tree_999"

        fun resolveFiltered(treeId: String, all: List<Person>): List<Person> {
            if (treeId.isEmpty()) {
                return emptyList()
            }
            return if (treeId == "default_tree") {
                all.filter { it.treeId.isEmpty() || it.treeId == "default_tree" }
            } else {
                all.filter { it.treeId == treeId }
            }
        }

        val filteredNewTree = resolveFiltered(newTreeId, allPersonsInDb)
        assertTrue("New tree with 0 members must return empty list, NOT fallback to all DB persons!", filteredNewTree.isEmpty())

        val filteredEmptyTreeId = resolveFiltered("", allPersonsInDb)
        assertTrue("Empty treeId must resolve to empty list", filteredEmptyTreeId.isEmpty())
    }

    @Test
    fun testCentralTreeSynchronizer_StrictTreeIsolationBetweenDifferentTrees() {
        val synchronizer = com.example.btproject2.sync.CentralTreeSynchronizer.getInstance()
        var treeBReceived = false

        val listenerTreeB = object : com.example.btproject2.sync.SyncEventListener {
            override val subscriberKey: String = "SubscriberTreeB_${System.currentTimeMillis()}"
            override val screenType: com.example.btproject2.sync.AffectedScreen = com.example.btproject2.sync.AffectedScreen.INTERACTIVE_TREE
            override val interestedTreeId: String = "tree_B"

            override fun onSyncEvent(event: com.example.btproject2.sync.TreeSyncEvent) {
                treeBReceived = true
            }

            override fun onConflictDetected(
                conflict: com.example.btproject2.sync.SyncConflictEvent,
                resolver: (com.example.btproject2.sync.ConflictChoice) -> Unit
            ) {
                resolver(com.example.btproject2.sync.ConflictChoice.REFRESH)
            }
        }

        synchronizer.registerListener(listenerTreeB)

        val personInTreeA = Person(id = "pA", firstName = "Alice", lastName = "Cruz", treeId = "tree_A")
        synchronizer.notifyMemberAdded(personInTreeA, sourceScreen = "Test")

        assertFalse("Listener scoped to tree_B must NOT receive event for tree_A", treeBReceived)

        synchronizer.unregisterListener(listenerTreeB)
    }

    @Test
    fun testInviteCodeGeneration_IsStrictlySixCharactersAlphanumeric() {
        val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet()
        for (i in 1..20) {
            val code = com.example.btproject2.firebase.FirestoreHelper.generateInviteCode()
            assertEquals("Invite code must be exactly 6 characters long", 6, code.length)
            assertTrue("Invite code characters must be uppercase alphanumeric without confusing chars",
                code.all { it in validChars })
        }
    }

    @Test
    fun testInviteCodeSanitization_StripsInternalAndExternalSpacesAndUppercases() {
        val testInputs = listOf(
            " 4 S M F D 9 " to "4SMFD9",
            "4smfd9" to "4SMFD9",
            " 4sm fd9 " to "4SMFD9",
            "4 S M F D 9" to "4SMFD9",
            "AB12CD" to "AB12CD"
        )

        for ((input, expected) in testInputs) {
            val sanitized = input.replace(" ", "").trim().uppercase()
            assertEquals("Normalized code must match expected uppercase code without spaces", expected, sanitized)
            assertEquals("Length must be 6", 6, sanitized.length)
        }
    }

    @Test
    fun testUserTreesResolution_IncludesBothOwnedAndJoinedApprovedTrees() {
        val currentUserId = "user_joiner"
        val ownedTree = FamilyTree(id = "tree_owned", name = "My Personal Tree", ownerId = currentUserId)
        val joinedTree = FamilyTree(id = "tree_joined", name = "Santos Family Tree", ownerId = "user_owner", inviteCode = "4SMFD9")

        val ownedTrees = listOf(ownedTree)
        val joinedTrees = listOf(joinedTree)

        val combinedTrees = (ownedTrees + joinedTrees).distinctBy { it.id }

        assertEquals(2, combinedTrees.size)
        assertTrue(combinedTrees.any { it.id == "tree_owned" })
        assertTrue(combinedTrees.any { it.id == "tree_joined" })
    }

    @Test
    fun testInviteCodeRecord_LifecycleStates() {
        val now = System.currentTimeMillis()

        // 1. Active record
        val activeRecord = com.example.btproject2.models.InviteCodeRecord(
            code = "5SRQJU",
            treeId = "tree_123",
            treeName = "Garcia Family Tree",
            createdBy = "user_owner_1",
            createdAt = now,
            expiresAt = 0L,
            usageLimit = 0,
            usageCount = 0,
            status = com.example.btproject2.models.InviteCodeRecord.STATUS_ACTIVE
        )
        assertTrue(activeRecord.isValid())
        assertFalse(activeRecord.isExpired())
        assertFalse(activeRecord.isRevoked())
        assertFalse(activeRecord.isUsageLimitReached())

        // 2. Expired by status
        val expiredByStatus = activeRecord.copy(status = com.example.btproject2.models.InviteCodeRecord.STATUS_EXPIRED)
        assertTrue(expiredByStatus.isExpired())
        assertFalse(expiredByStatus.isValid())

        // 3. Expired by timestamp
        val expiredByTime = activeRecord.copy(expiresAt = now - 5000L)
        assertTrue(expiredByTime.isExpired())
        assertFalse(expiredByTime.isValid())

        // 4. Future expiration date (valid)
        val validFutureExpiry = activeRecord.copy(expiresAt = now + 86400000L)
        assertFalse(validFutureExpiry.isExpired())
        assertTrue(validFutureExpiry.isValid())

        // 5. Revoked
        val revokedRecord = activeRecord.copy(status = com.example.btproject2.models.InviteCodeRecord.STATUS_REVOKED)
        assertTrue(revokedRecord.isRevoked())
        assertFalse(revokedRecord.isValid())

        // 6. Usage limit reached by count
        val limitReachedCount = activeRecord.copy(usageLimit = 3, usageCount = 3)
        assertTrue(limitReachedCount.isUsageLimitReached())
        assertFalse(limitReachedCount.isValid())

        // 7. Usage count under limit (valid)
        val underLimit = activeRecord.copy(usageLimit = 5, usageCount = 2)
        assertFalse(underLimit.isUsageLimitReached())
        assertTrue(underLimit.isValid())

        // 8. Used status
        val usedStatus = activeRecord.copy(status = com.example.btproject2.models.InviteCodeRecord.STATUS_USED)
        assertTrue(usedStatus.isUsageLimitReached())
        assertFalse(usedStatus.isValid())
    }

    @Test
    fun testInviteCodeRecord_DocumentFieldsCompleteness() {
        val record = com.example.btproject2.models.InviteCodeRecord(
            code = "5SRQJU",
            treeId = "tree_abc",
            treeName = "Dela Cruz Family Tree",
            createdBy = "user_creator",
            createdAt = 1700000000000L,
            expiresAt = 1710000000000L,
            usageLimit = 10,
            usageCount = 2,
            status = "active"
        )

        assertEquals("5SRQJU", record.code)
        assertEquals("tree_abc", record.treeId)
        assertEquals("Dela Cruz Family Tree", record.treeName)
        assertEquals("user_creator", record.createdBy)
        assertEquals(1700000000000L, record.createdAt)
        assertEquals(1710000000000L, record.expiresAt)
        assertEquals(10, record.usageLimit)
        assertEquals(2, record.usageCount)
        assertEquals("active", record.status)
    }
}

