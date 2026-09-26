package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.ConflictChoice
import com.example.btproject2.sync.InteractiveTreeSyncCoordinator
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncConflictEvent
import com.example.btproject2.sync.SyncEventListener
import com.example.btproject2.sync.TreeSyncEvent
import com.example.btproject2.sync.UnsavedWorkSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit and integration tests verifying the connection of CentralTreeSynchronizer
 * to InteractiveTreeActivity via InteractiveTreeSyncCoordinator.
 *
 * Verifies that all 8 required triggers automatically synchronize the main interactive
 * tree without manual reload or reopening.
 */
class InteractiveTreeSyncTest {

    private lateinit var synchronizer: CentralTreeSynchronizer
    private lateinit var coordinator: InteractiveTreeSyncCoordinator
    private var treeUpdatedCalled = false
    private var lastUpdatedList: List<Person> = emptyList()
    private var lastEvictedAvatarId: String? = null
    private var lastDismissedProfileId: String? = null
    private var lastRefreshedProfileId: String? = null

    @Before
    fun setUp() {
        synchronizer = CentralTreeSynchronizer.getInstance()
        synchronizer.resetForTesting()

        treeUpdatedCalled = false
        lastUpdatedList = emptyList()
        lastEvictedAvatarId = null
        lastDismissedProfileId = null
        lastRefreshedProfileId = null

        coordinator = InteractiveTreeSyncCoordinator(
            treeId = "default_tree",
            onTreeUpdated = { list, _ ->
                treeUpdatedCalled = true
                lastUpdatedList = list
            },
            onMemberEvictedFromAvatarCache = { id ->
                lastEvictedAvatarId = id
            },
            onActiveProfileDismissed = { id ->
                lastDismissedProfileId = id
            },
            onActiveProfileRefreshed = { id ->
                lastRefreshedProfileId = id
            }
        )
    }

    // ─────────────────────────────────────────────────────────────
    // LISTENER REGISTRATION & METADATA
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testInteractiveTreeCoordinator_listenerProperties() {
        val listener: SyncEventListener = coordinator
        assertTrue(listener.subscriberKey.startsWith("InteractiveTreeActivity@"))
        assertEquals(AffectedScreen.INTERACTIVE_TREE, listener.screenType)
        assertEquals("default_tree", listener.interestedTreeId)
    }

    @Test
    fun testRegistrationWithCentralTreeSynchronizer() {
        assertEquals(0, synchronizer.subscriberCount)
        synchronizer.registerListener(coordinator)
        assertEquals(1, synchronizer.subscriberCount)

        synchronizer.unregisterListener(coordinator)
        assertEquals(0, synchronizer.subscriberCount)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. TRIGGER 1: PROFILE DETAILS EDITED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger1_profileEdited_automaticallyUpdatesMainTreeMember() {
        synchronizer.registerListener(coordinator)

        val p1 = Person(id = "p1", firstName = "Jose", lastName = "Rizal", birthDate = "1861-06-19", gender = "Male", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Teodora", lastName = "Alonso", birthDate = "1826-11-09", gender = "Female", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        val updatedP1 = p1.copy(
            firstName = "Dr. Jose Protacio",
            lastName = "Rizal Mercado y Alonso Realonda",
            birthPlace = "Calamba, Laguna",
            biography = "National Hero of the Philippines",
            photoBase64 = "base64_avatar_string"
        )

        synchronizer.notifyProfileEdited(
            updatedPerson = updatedP1,
            previousPerson = p1,
            sourceScreen = "FamilyRecordsActivity"
        )

        assertTrue(treeUpdatedCalled)
        val found = coordinator.allPersonsList.find { it.id == "p1" }
        assertNotNull(found)
        assertEquals("Dr. Jose Protacio", found?.firstName)
        assertEquals("Rizal Mercado y Alonso Realonda", found?.lastName)
        assertEquals("Calamba, Laguna", found?.birthPlace)
        assertEquals("National Hero of the Philippines", found?.biography)
        assertEquals("base64_avatar_string", found?.photoBase64)
        assertEquals("p1", lastRefreshedProfileId)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. TRIGGER 2: NEW FAMILY MEMBER ADDED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger2_memberAdded_automaticallyDisplaysNewMemberInMainTree() {
        synchronizer.registerListener(coordinator)

        val p1 = Person(id = "p1", firstName = "Francisco", lastName = "Mercado", gender = "Male", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1)

        val newChild = Person(
            id = "child_1",
            firstName = "Paciano",
            lastName = "Rizal",
            gender = "Male",
            fatherId = "p1",
            fatherRelationshipType = "Biological",
            treeId = "default_tree"
        )

        synchronizer.notifyMemberAdded(newChild, sourceScreen = "AddMemberActivity")

        assertTrue(treeUpdatedCalled)
        assertEquals(2, coordinator.allPersonsList.size)
        val added = coordinator.allPersonsList.find { it.id == "child_1" }
        assertNotNull(added)
        assertEquals("Paciano", added?.firstName)
        assertEquals("p1", added?.fatherId)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. TRIGGER 3: SPOUSE RELATIONSHIP ADDED OR REMOVED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger3_spouseRelationshipAdded_automaticallyLinksSpousesBesideEachOther() {
        synchronizer.registerListener(coordinator)

        val p1 = Person(id = "p1", firstName = "Juan", lastName = "Luna", gender = "Male", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Paz", lastName = "Pardo", gender = "Female", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        val p1Married = p1.copy(spouseId = "p2", maritalStatus = "Married")
        val p2Married = p2.copy(spouseId = "p1", maritalStatus = "Married")

        synchronizer.notifySpouseChanged(
            personA = p1Married,
            personB = p2Married,
            isRemoval = false,
            sourceScreen = "InteractiveTreeActivity"
        )

        assertTrue(treeUpdatedCalled)
        val treeP1 = coordinator.allPersonsList.find { it.id == "p1" }
        val treeP2 = coordinator.allPersonsList.find { it.id == "p2" }

        assertEquals("p2", treeP1?.spouseId)
        assertEquals("Married", treeP1?.maritalStatus)
        assertEquals("p1", treeP2?.spouseId)
        assertEquals("Married", treeP2?.maritalStatus)
    }

    @Test
    fun testTrigger3_spouseRelationshipRemoved_automaticallyUnlinksSpouses() {
        synchronizer.registerListener(coordinator)

        val p1 = Person(id = "p1", firstName = "Juan", lastName = "Luna", spouseId = "p2", maritalStatus = "Married", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Paz", lastName = "Pardo", spouseId = "p1", maritalStatus = "Married", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        val p1Unlinked = p1.copy(spouseId = null, maritalStatus = "Single")
        val p2Unlinked = p2.copy(spouseId = null, maritalStatus = "Single")

        synchronizer.notifySpouseChanged(
            personA = p1Unlinked,
            personB = p2Unlinked,
            isRemoval = true,
            sourceScreen = "MemberDetailActivity"
        )

        assertTrue(treeUpdatedCalled)
        val treeP1 = coordinator.allPersonsList.find { it.id == "p1" }
        val treeP2 = coordinator.allPersonsList.find { it.id == "p2" }

        assertNull(treeP1?.spouseId)
        assertEquals("Single", treeP1?.maritalStatus)
        assertNull(treeP2?.spouseId)
        assertEquals("Single", treeP2?.maritalStatus)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. TRIGGER 4: PARENT-CHILD RELATIONSHIP ADDED OR REMOVED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger4_parentChildRelationshipChanged_automaticallyConnectsChild() {
        synchronizer.registerListener(coordinator)

        val parent = Person(id = "parent_1", firstName = "Jose", lastName = "Protacio", gender = "Male", treeId = "default_tree")
        val child = Person(id = "child_1", firstName = "Maria", lastName = "Protacio", gender = "Female", treeId = "default_tree")
        coordinator.allPersonsList = listOf(parent, child)

        val childLinked = child.copy(fatherId = "parent_1", fatherRelationshipType = "Biological")

        synchronizer.notifyParentChildChanged(
            child = childLinked,
            parent = parent,
            role = "father",
            isRemoval = false,
            sourceScreen = "FamilyRelationshipService"
        )

        assertTrue(treeUpdatedCalled)
        val updatedChild = coordinator.allPersonsList.find { it.id == "child_1" }
        assertEquals("parent_1", updatedChild?.fatherId)
        assertEquals("Biological", updatedChild?.fatherRelationshipType)
    }

    // ─────────────────────────────────────────────────────────────
    // 5. TRIGGER 5: ADOPTIVE RELATIONSHIP ADDED OR REMOVED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger5_adoptiveRelationshipChanged_reflectsAdoptiveStatus() {
        synchronizer.registerListener(coordinator)

        val parent = Person(id = "adoptive_parent", firstName = "Carlos", lastName = "Romulo", gender = "Male", treeId = "default_tree")
        val child = Person(id = "adoptive_child", firstName = "Roberto", lastName = "Romulo", gender = "Male", treeId = "default_tree")
        coordinator.allPersonsList = listOf(parent, child)

        val childAdopted = child.copy(fatherId = "adoptive_parent", fatherRelationshipType = "Adoptive")

        synchronizer.notifyAdoptiveChanged(
            child = childAdopted,
            parent = parent,
            role = "father",
            isRemoval = false,
            sourceScreen = "MemberDetailActivity"
        )

        assertTrue(treeUpdatedCalled)
        val updatedChild = coordinator.allPersonsList.find { it.id == "adoptive_child" }
        assertEquals("adoptive_parent", updatedChild?.fatherId)
        assertEquals("Adoptive", updatedChild?.fatherRelationshipType)
    }

    // ─────────────────────────────────────────────────────────────
    // 6. TRIGGER 6: RELATIONSHIP STATUS OR DATE CHANGED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger6_relationshipMetadataChanged_updatesMarriageDetails() {
        synchronizer.registerListener(coordinator)

        val p1 = Person(id = "p1", firstName = "Emilio", lastName = "Aguinaldo", spouseId = "p2", maritalStatus = "Married", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Hilaria", lastName = "del Rosario", spouseId = "p1", maritalStatus = "Married", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        val p1Updated = p1.copy(marriageDate = "1896-01-01", maritalStatus = "Married")

        synchronizer.notifyRelationshipMetadataChanged(
            personA = p1Updated,
            personB = p2,
            metadataFields = setOf("marriageDate"),
            sourceScreen = "EditMemberActivity"
        )

        assertTrue(treeUpdatedCalled)
        val treeP1 = coordinator.allPersonsList.find { it.id == "p1" }
        assertEquals("1896-01-01", treeP1?.marriageDate)
    }

    // ─────────────────────────────────────────────────────────────
    // 7. TRIGGER 7: FAMILY MEMBER DELETED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger7_memberDeleted_removesMemberAndUnlinksConnections() {
        synchronizer.registerListener(coordinator)

        val father = Person(id = "f1", firstName = "Father", lastName = "Dela Cruz", treeId = "default_tree")
        val mother = Person(id = "m1", firstName = "Mother", lastName = "Dela Cruz", spouseId = "f1", maritalStatus = "Married", treeId = "default_tree")
        val child = Person(id = "c1", firstName = "Child", lastName = "Dela Cruz", fatherId = "f1", motherId = "m1", fatherRelationshipType = "Biological", treeId = "default_tree")

        coordinator.allPersonsList = listOf(father, mother, child)

        synchronizer.notifyMemberDeleted(
            deletedPersonId = "f1",
            treeId = "default_tree",
            affectedRelatives = setOf("m1", "c1"),
            sourceScreen = "FamilyRecordsActivity"
        )

        assertTrue(treeUpdatedCalled)
        // 1. Father must be completely removed from tree list
        assertNull(coordinator.allPersonsList.find { it.id == "f1" })
        assertEquals(2, coordinator.allPersonsList.size)

        // 2. Child's fatherId reference must be safely unlinked
        val updatedChild = coordinator.allPersonsList.find { it.id == "c1" }
        assertNotNull(updatedChild)
        assertNull(updatedChild?.fatherId)
        assertEquals("", updatedChild?.fatherRelationshipType)
        assertEquals("m1", updatedChild?.motherId)

        // 3. Mother's spouse reference must be unlinked and status updated
        val updatedMother = coordinator.allPersonsList.find { it.id == "m1" }
        assertNotNull(updatedMother)
        assertNull(updatedMother?.spouseId)
        assertEquals("Single", updatedMother?.maritalStatus)

        // 4. Avatar and profile cleanup
        assertEquals("f1", lastDismissedProfileId)
        assertEquals("f1", lastEvictedAvatarId)
    }

    // ─────────────────────────────────────────────────────────────
    // 8. TRIGGER 8: INVALID RELATIONSHIP CORRECTED OR REMOVED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger8_relationshipCorrected_updatesTreeState() {
        synchronizer.registerListener(coordinator)

        // Suppose person A was accidentally set with invalid self-parenting
        val corrupted = Person(id = "p1", firstName = "Juan", lastName = "Luna", fatherId = "p1", treeId = "default_tree")
        coordinator.allPersonsList = listOf(corrupted)

        // Audit or user corrects the cycle
        val corrected = corrupted.copy(fatherId = null, fatherRelationshipType = "")

        synchronizer.notifyRelationshipCorrected(
            primaryPerson = corrected,
            affectedRelatives = emptySet(),
            issueDescription = "Removed impossible cycle self-parent link",
            sourceScreen = "TreeAuditActivity"
        )

        assertTrue(treeUpdatedCalled)
        val inTree = coordinator.allPersonsList.find { it.id == "p1" }
        assertNotNull(inTree)
        assertNull(inTree?.fatherId)
        assertEquals("", inTree?.fatherRelationshipType)
    }

    // ─────────────────────────────────────────────────────────────
    // CONFLICT RESOLUTION
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOnConflictDetected_prefersRefreshToMaintainAuthoritativeTruth() {
        var chosen: ConflictChoice? = null
        val syncEvent = TreeSyncEvent(
            changeType = SyncChangeType.PROFILE_EDITED,
            treeId = "default_tree",
            scope = com.example.btproject2.sync.AffectedScope(
                treeId = "default_tree",
                primaryPersonId = "p1"
            ),
            isConfirmedSave = true
        )
        val session = UnsavedWorkSession(
            sessionKey = "session_1",
            screenKey = coordinator.subscriberKey,
            personId = "p1",
            treeId = "default_tree",
            description = "InteractiveTreeActivity"
        )
        val conflict = SyncConflictEvent(
            screenKey = coordinator.subscriberKey,
            personId = "p1",
            unsavedSession = session,
            syncEvent = syncEvent
        )

        coordinator.onConflictDetected(conflict) { choice ->
            chosen = choice
        }

        // InteractiveTreeActivity chooses REFRESH to immediately reflect authoritative backend state
        assertEquals(ConflictChoice.REFRESH, chosen)
    }
}

