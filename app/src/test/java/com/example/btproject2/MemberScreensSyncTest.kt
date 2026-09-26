package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.sync.AddMemberSyncCoordinator
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.ConflictChoice
import com.example.btproject2.sync.MemberDetailSyncCoordinator
import com.example.btproject2.sync.RecordsSyncCoordinator
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncConflictEvent
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
 * Unit and integration tests verifying the connection of [CentralTreeSynchronizer]
 * to the Records section, Person record page, Add Member form, relationship linking actions,
 * search/lookup, and family member cards.
 */
class MemberScreensSyncTest {

    private lateinit var synchronizer: CentralTreeSynchronizer

    @Before
    fun setUp() {
        synchronizer = CentralTreeSynchronizer.getInstance()
        synchronizer.resetForTesting()
    }

    // ══════════════════════════════════════════════════════════════
    // 1. RECORDS SECTION & SEARCH/LOOKUP SYNCHRONIZATION
    // ══════════════════════════════════════════════════════════════

    @Test
    fun testRecordsCoordinator_listenerProperties() {
        val coordinator = RecordsSyncCoordinator(treeId = "default_tree", listenerKey = "TestRecords@1")
        assertEquals(AffectedScreen.RECORDS_LIST, coordinator.screenType)
        assertEquals("default_tree", coordinator.interestedTreeId)
        assertEquals("TestRecords@1", coordinator.subscriberKey)
    }

    @Test
    fun testRecordsCoordinator_registrationWithCentralTreeSynchronizer() {
        val coordinator = RecordsSyncCoordinator(treeId = "default_tree")
        assertEquals(0, synchronizer.subscriberCount)
        coordinator.register(synchronizer)
        assertEquals(1, synchronizer.subscriberCount)
        coordinator.unregister(synchronizer)
        assertEquals(0, synchronizer.subscriberCount)
    }

    @Test
    fun testRecordsSection_profileEdited_immediatelyUpdatesListAndSearchResults() {
        var updatedCount = 0
        val p1 = Person(id = "p1", firstName = "Jose", lastName = "Rizal", treeId = "default_tree")
        val coordinator = RecordsSyncCoordinator(
            treeId = "default_tree",
            allMembersList = listOf(p1),
            onListUpdated = { _, _ -> updatedCount++ }
        )
        coordinator.register(synchronizer)

        // User edits Jose's name in another screen
        val editedJose = p1.copy(firstName = "Dr. Jose", lastName = "Rizal Mercado")
        synchronizer.notifyProfileEdited(updatedPerson = editedJose, previousPerson = p1)

        assertEquals(1, updatedCount)
        assertEquals("Dr. Jose", coordinator.allMembersList[0].firstName)
        assertEquals("Rizal Mercado", coordinator.allMembersList[0].lastName)

        // Instant search lookup verification
        val searchResults = coordinator.filterMembers("Mercado")
        assertEquals(1, searchResults.size)
        assertEquals("p1", searchResults[0].id)
    }

    @Test
    fun testRecordsSection_memberAdded_immediatelyIncludedInRecordsAndSearch() {
        var updatedCount = 0
        val coordinator = RecordsSyncCoordinator(
            treeId = "default_tree",
            allMembersList = emptyList(),
            onListUpdated = { _, _ -> updatedCount++ }
        )
        coordinator.register(synchronizer)

        val newMember = Person(id = "p2", firstName = "Andres", lastName = "Bonifacio", treeId = "default_tree")
        synchronizer.notifyMemberAdded(newPerson = newMember)

        assertEquals(1, updatedCount)
        assertEquals(1, coordinator.allMembersList.size)
        assertEquals("Andres", coordinator.allMembersList[0].firstName)

        // Search lookup matches new member
        val searchResults = coordinator.filterMembers("Bonifacio")
        assertEquals(1, searchResults.size)
        assertEquals("p2", searchResults[0].id)
    }

    @Test
    fun testRecordsSection_memberDeleted_immediatelyRemovesFromRecordsAndCleansRelatives() {
        var updatedCount = 0
        var deletedIdReported: String? = null
        val husband = Person(id = "h1", firstName = "Juan", lastName = "Luna", spouseId = "w1", maritalStatus = "Married", treeId = "default_tree")
        val wife = Person(id = "w1", firstName = "Paz", lastName = "Pardo", spouseId = "h1", maritalStatus = "Married", treeId = "default_tree")

        val coordinator = RecordsSyncCoordinator(
            treeId = "default_tree",
            allMembersList = listOf(husband, wife),
            onListUpdated = { _, _ -> updatedCount++ },
            onMemberDeleted = { id -> deletedIdReported = id }
        )
        coordinator.register(synchronizer)

        // Delete husband
        synchronizer.notifyMemberDeleted(deletedPersonId = "h1", treeId = "default_tree")

        assertEquals(1, updatedCount)
        assertEquals("h1", deletedIdReported)
        assertEquals(1, coordinator.allMembersList.size)
        assertFalse(coordinator.allMembersList.any { it.id == "h1" })

        // Wife's spouse link cleaned up and status reset to Single
        val remainingWife = coordinator.allMembersList.find { it.id == "w1" }
        assertNotNull(remainingWife)
        assertNull(remainingWife!!.spouseId)
        assertEquals("Single", remainingWife.maritalStatus)

        // Search for deleted member yields empty results
        val searchDeleted = coordinator.filterMembers("Juan")
        assertTrue(searchDeleted.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════
    // 2. PERSON RECORD PAGE & RELATIONSHIP ACTIONS SYNCHRONIZATION
    // ══════════════════════════════════════════════════════════════

    @Test
    fun testMemberDetailCoordinator_listenerProperties() {
        val coordinator = MemberDetailSyncCoordinator(personId = "p1", treeId = "default_tree")
        assertEquals(AffectedScreen.MEMBER_DETAIL, coordinator.screenType)
        assertEquals("default_tree", coordinator.interestedTreeId)
    }

    @Test
    fun testPersonRecord_profileEdited_immediatelyUpdatesRecord() {
        var personUpdatedCount = 0
        val p1 = Person(id = "p1", firstName = "Marcelo", lastName = "del Pilar", treeId = "default_tree")
        val coordinator = MemberDetailSyncCoordinator(
            personId = "p1",
            treeId = "default_tree",
            currentPerson = p1,
            allMembersList = listOf(p1),
            onPersonUpdated = { _, _, _ -> personUpdatedCount++ }
        )
        coordinator.register(synchronizer)

        val edited = p1.copy(biography = "Editor of La Solidaridad")
        synchronizer.notifyProfileEdited(updatedPerson = edited, previousPerson = p1)

        assertEquals(1, personUpdatedCount)
        assertEquals("Editor of La Solidaridad", coordinator.currentPerson?.biography)
    }

    @Test
    fun testPersonRecord_addSpouseAction_immediatelyReflectsInSpouseCardAndStatus() {
        var personUpdatedCount = 0
        val bachelor = Person(id = "p1", firstName = "Jose", lastName = "Rizal", maritalStatus = "Single", treeId = "default_tree")
        val bachelorette = Person(id = "p2", firstName = "Josephine", lastName = "Bracken", maritalStatus = "Single", treeId = "default_tree")

        val coordinator = MemberDetailSyncCoordinator(
            personId = "p1",
            treeId = "default_tree",
            currentPerson = bachelor,
            allMembersList = listOf(bachelor, bachelorette),
            onPersonUpdated = { _, _, _ -> personUpdatedCount++ }
        )
        coordinator.register(synchronizer)

        // Add spouse action executed
        val marriedJose = bachelor.copy(spouseId = "p2", maritalStatus = "Married")
        val marriedJosephine = bachelorette.copy(spouseId = "p1", maritalStatus = "Married")
        synchronizer.notifySpouseChanged(personA = marriedJose, personB = marriedJosephine)

        assertEquals(1, personUpdatedCount)
        assertEquals("p2", coordinator.currentPerson?.spouseId)
        assertEquals("Married", coordinator.currentPerson?.maritalStatus)

        // Resolved spouse helper returns Josephine immediately
        val resolvedSpouse = coordinator.getResolvedSpouse()
        assertNotNull(resolvedSpouse)
        assertEquals("Josephine", resolvedSpouse?.firstName)
    }

    @Test
    fun testPersonRecord_addChildAction_immediatelyReflectsInChildCards() {
        var personUpdatedCount = 0
        val father = Person(id = "f1", firstName = "Francisco", lastName = "Mercado", treeId = "default_tree")
        val coordinator = MemberDetailSyncCoordinator(
            personId = "f1",
            treeId = "default_tree",
            currentPerson = father,
            allMembersList = listOf(father),
            onPersonUpdated = { _, _, _ -> personUpdatedCount++ }
        )
        coordinator.register(synchronizer)

        // Add child action executed (child linked to father)
        val child = Person(id = "c1", firstName = "Paciano", lastName = "Rizal", fatherId = "f1", treeId = "default_tree")
        synchronizer.notifyParentChildChanged(child = child, parent = father)

        assertEquals(1, personUpdatedCount)
        val children = coordinator.getResolvedChildren()
        assertEquals(1, children.size)
        assertEquals("c1", children[0].id)
        assertEquals("Paciano", children[0].firstName)
    }

    @Test
    fun testPersonRecord_addParentAction_immediatelyReflectsInParentCards() {
        var personUpdatedCount = 0
        val child = Person(id = "c1", firstName = "Jose", lastName = "Rizal", treeId = "default_tree")
        val mother = Person(id = "m1", firstName = "Teodora", lastName = "Alonso", gender = "Female", treeId = "default_tree")

        val coordinator = MemberDetailSyncCoordinator(
            personId = "c1",
            treeId = "default_tree",
            currentPerson = child,
            allMembersList = listOf(child, mother),
            onPersonUpdated = { _, _, _ -> personUpdatedCount++ }
        )
        coordinator.register(synchronizer)

        // Add mother action executed
        val updatedChild = child.copy(motherId = "m1", motherRelationshipType = "Biological")
        synchronizer.notifyParentChildChanged(child = updatedChild, parent = mother, role = "mother")

        assertEquals(1, personUpdatedCount)
        val resolvedMother = coordinator.getResolvedMother()
        assertNotNull(resolvedMother)
        assertEquals("m1", resolvedMother?.id)
        assertEquals("Teodora", resolvedMother?.firstName)
    }

    @Test
    fun testPersonRecord_currentPersonDeleted_notifiesDismissal() {
        var dismissedId: String? = null
        val person = Person(id = "p1", firstName = "Jose", lastName = "Rizal", treeId = "default_tree")
        val coordinator = MemberDetailSyncCoordinator(
            personId = "p1",
            treeId = "default_tree",
            currentPerson = person,
            allMembersList = listOf(person),
            onCurrentPersonDeleted = { id -> dismissedId = id }
        )
        coordinator.register(synchronizer)

        // Person deleted elsewhere
        synchronizer.notifyMemberDeleted(deletedPersonId = "p1", treeId = "default_tree")

        assertEquals("p1", dismissedId)
        assertNull(coordinator.currentPerson)
    }

    @Test
    fun testPersonRecord_relativeDeleted_unlinksParentAndSpouseCardsImmediately() {
        var updateCount = 0
        val father = Person(id = "f1", firstName = "Francisco", lastName = "Mercado", treeId = "default_tree")
        val spouse = Person(id = "s1", firstName = "Josephine", lastName = "Bracken", treeId = "default_tree")
        val current = Person(id = "p1", firstName = "Jose", lastName = "Rizal", fatherId = "f1", spouseId = "s1", maritalStatus = "Married", treeId = "default_tree")

        val coordinator = MemberDetailSyncCoordinator(
            personId = "p1",
            treeId = "default_tree",
            currentPerson = current,
            allMembersList = listOf(current, father, spouse),
            onPersonUpdated = { _, _, _ -> updateCount++ }
        )
        coordinator.register(synchronizer)

        // Father deleted
        synchronizer.notifyMemberDeleted(deletedPersonId = "f1", treeId = "default_tree")

        assertEquals(1, updateCount)
        assertNull(coordinator.currentPerson?.fatherId)
        assertNull(coordinator.getResolvedFather())
        assertNotNull(coordinator.getResolvedSpouse())

        // Spouse deleted
        synchronizer.notifyMemberDeleted(deletedPersonId = "s1", treeId = "default_tree")

        assertEquals(2, updateCount)
        assertNull(coordinator.currentPerson?.spouseId)
        assertEquals("Single", coordinator.currentPerson?.maritalStatus)
        assertNull(coordinator.getResolvedSpouse())
    }

    // ══════════════════════════════════════════════════════════════
    // 3. ADD MEMBER FORM & CANDIDATE SYNCHRONIZATION
    // ══════════════════════════════════════════════════════════════

    @Test
    fun testAddMemberForm_newMemberAddedElsewhere_becomesEligibleCandidateImmediately() {
        var candidatesUpdatedCount = 0
        val coordinator = AddMemberSyncCoordinator(
            treeId = "default_tree",
            allMembersList = emptyList(),
            onCandidatesUpdated = { _, _, _, _ -> candidatesUpdatedCount++ }
        )
        coordinator.register(synchronizer)

        val newFather = Person(id = "m1", firstName = "Juan", lastName = "Luna", gender = "Male", treeId = "default_tree")
        synchronizer.notifyMemberAdded(newPerson = newFather)

        assertEquals(1, candidatesUpdatedCount)
        assertEquals(1, coordinator.allMembersList.size)
    }

    @Test
    fun testAddMemberForm_deletedMember_evictedFromSelectedChildrenAndCandidates() {
        var childEvictedId: String? = null
        val child = Person(id = "c1", firstName = "Baby", lastName = "Luna", gender = "Male", treeId = "default_tree")
        val selectedChildren = mutableListOf(child)

        val coordinator = AddMemberSyncCoordinator(
            treeId = "default_tree",
            allMembersList = listOf(child),
            selectedChildrenList = selectedChildren,
            onChildRemovedFromSelection = { id -> childEvictedId = id }
        )
        coordinator.register(synchronizer)

        synchronizer.notifyMemberDeleted(deletedPersonId = "c1", treeId = "default_tree")

        assertEquals("c1", childEvictedId)
        assertTrue(coordinator.selectedChildrenList.isEmpty())
        assertTrue(coordinator.allMembersList.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════
    // 4. CONFLICT RESOLUTION & SINGLE SOURCE OF TRUTH
    // ══════════════════════════════════════════════════════════════

    @Test
    fun testRecordsAndMemberDetail_conflictResolvesToRefresh() {
        val recordsCoordinator = RecordsSyncCoordinator(treeId = "default_tree")
        var recordsChoice: ConflictChoice? = null
        val syncEvent = TreeSyncEvent(
            changeType = SyncChangeType.PROFILE_EDITED,
            treeId = "default_tree",
            scope = com.example.btproject2.sync.AffectedScope(treeId = "default_tree", primaryPersonId = "p1"),
            isConfirmedSave = true
        )
        val session = UnsavedWorkSession(
            sessionKey = "s1",
            screenKey = recordsCoordinator.subscriberKey,
            personId = "p1",
            treeId = "default_tree",
            description = "Records"
        )
        val conflict = SyncConflictEvent(
            screenKey = recordsCoordinator.subscriberKey,
            personId = "p1",
            unsavedSession = session,
            syncEvent = syncEvent
        )

        recordsCoordinator.onConflictDetected(conflict) { recordsChoice = it }
        assertEquals(ConflictChoice.REFRESH, recordsChoice)

        val detailCoordinator = MemberDetailSyncCoordinator(personId = "p1", treeId = "default_tree")
        var detailChoice: ConflictChoice? = null
        detailCoordinator.onConflictDetected(conflict) { detailChoice = it }
        assertEquals(ConflictChoice.REFRESH, detailChoice)
    }
}

