package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.ConflictChoice
import com.example.btproject2.sync.PedigreeFanChartSyncCoordinator
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncConflictEvent
import com.example.btproject2.sync.SyncScopeResolver
import com.example.btproject2.sync.TreeSyncEvent
import com.example.btproject2.sync.UnsavedWorkSession
import com.example.btproject2.ui.adapters.TreeMemberAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit and integration tests verifying the connection of [CentralTreeSynchronizer]
 * to the Pedigree View and Fan Chart via [PedigreeFanChartSyncCoordinator].
 *
 * Verifies that all 8 required mutation triggers automatically update both views
 * without manual refresh or reopening.
 */
class PedigreeFanChartSyncTest {

    private lateinit var synchronizer: CentralTreeSynchronizer
    private lateinit var coordinator: PedigreeFanChartSyncCoordinator
    private var treeUpdatedCount = 0
    private var lastUpdatedList: List<Person> = emptyList()
    private var lastDeletedPersonId: String? = null

    @Before
    fun setUp() {
        synchronizer = CentralTreeSynchronizer.getInstance()
        synchronizer.resetForTesting()

        treeUpdatedCount = 0
        lastUpdatedList = emptyList()
        lastDeletedPersonId = null

        coordinator = PedigreeFanChartSyncCoordinator(
            treeId = "default_tree",
            listenerKey = "TestFamilyTreeActivity@1",
            screenType = AffectedScreen.PEDIGREE_VIEW,
            onTreeUpdated = { list, _ ->
                treeUpdatedCount++
                lastUpdatedList = list
            },
            onMemberDeleted = { deletedId ->
                lastDeletedPersonId = deletedId
            }
        )
    }

    // ─────────────────────────────────────────────────────────────
    // LISTENER REGISTRATION & METADATA
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCoordinator_listenerProperties() {
        assertEquals("TestFamilyTreeActivity@1", coordinator.subscriberKey)
        assertEquals(AffectedScreen.PEDIGREE_VIEW, coordinator.screenType)
        assertEquals("default_tree", coordinator.interestedTreeId)

        assertEquals("TestFamilyTreeActivity@1_fanchart", coordinator.fanChartListener.subscriberKey)
        assertEquals(AffectedScreen.FAN_CHART, coordinator.fanChartListener.screenType)
        assertEquals("default_tree", coordinator.fanChartListener.interestedTreeId)
    }

    @Test
    fun testRegistrationAndUnregistrationWithCentralTreeSynchronizer() {
        assertEquals(0, synchronizer.subscriberCount)
        coordinator.register(synchronizer)
        assertEquals(2, synchronizer.subscriberCount) // primary + fanChartListener

        coordinator.unregister(synchronizer)
        assertEquals(0, synchronizer.subscriberCount)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. TRIGGER 1: PROFILE DETAILS EDITED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger1_profileEdited_automaticallyUpdatesPedigreeAndFanChart() {
        coordinator.register(synchronizer)

        val p1 = Person(id = "p1", firstName = "Jose", lastName = "Rizal", birthDate = "1861-06-19", gender = "Male", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Francisco", lastName = "Mercado", birthDate = "1818-05-11", gender = "Male", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        // Edit Jose's profile (name change in Records)
        val editedJose = p1.copy(firstName = "Dr. Jose Protacio", lastName = "Rizal Mercado")
        synchronizer.notifyProfileEdited(
            updatedPerson = editedJose,
            previousPerson = p1,
            sourceScreen = "RecordsList"
        )

        assertEquals(1, treeUpdatedCount)
        val foundJose = lastUpdatedList.find { it.id == "p1" }
        assertNotNull(foundJose)
        assertEquals("Dr. Jose Protacio", foundJose!!.firstName)
        assertEquals("Rizal Mercado", foundJose.lastName)

        // Verify Pedigree items reflect the new name
        val pedigreeItems = coordinator.buildGenerationItems()
        val memberItem = pedigreeItems.filterIsInstance<TreeMemberAdapter.TreeItem.Member>().find { it.person.id == "p1" }
        assertNotNull(memberItem)
        assertEquals("Dr. Jose Protacio", memberItem!!.person.firstName)

        // Verify Fan Chart JSON reflects the new name
        val fanJson = coordinator.buildFanChartNodesJson()
        assertTrue(fanJson.contains("Dr. Jose Protacio Rizal Mercado"))
    }

    // ─────────────────────────────────────────────────────────────
    // 2. TRIGGER 2: NEW ANCESTOR OR DESCENDANT ADDED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger2_ancestorOrDescendantAdded_updatesBothViews() {
        coordinator.register(synchronizer)

        val grandfather = Person(id = "p1", firstName = "Domingo", lastName = "Lam-co", birthDate = "1697-01-01", gender = "Male", treeId = "default_tree")
        coordinator.allPersonsList = listOf(grandfather)

        // Add child Francisco
        val father = Person(id = "p2", firstName = "Francisco", lastName = "Mercado", fatherId = "p1", birthDate = "1731-01-01", gender = "Male", treeId = "default_tree")
        synchronizer.notifyMemberAdded(newPerson = father, sourceScreen = "AddMemberForm")

        assertEquals(1, treeUpdatedCount)
        assertEquals(2, lastUpdatedList.size)
        assertTrue(lastUpdatedList.any { it.id == "p2" })

        // Check generations: grandfather is Gen 1, father is Gen 2
        val genMap = coordinator.computeGenerationMap()
        assertEquals(1, genMap["p1"])
        assertEquals(2, genMap["p2"])

        // Pedigree view items contain both generations
        val pedigreeItems = coordinator.buildGenerationItems(generationMap = genMap)
        val headers = pedigreeItems.filterIsInstance<TreeMemberAdapter.TreeItem.Header>().map { it.generation }
        assertTrue(headers.contains(1))
        assertTrue(headers.contains(2))

        // Fan Chart JSON contains both members with correct generation indices
        val fanJson = coordinator.buildFanChartNodesJson(genMap = genMap)
        assertTrue(fanJson.contains("\"id\":\"p1\""))
        assertTrue(fanJson.contains("\"id\":\"p2\""))
        assertTrue(fanJson.contains("\"generation\":1"))
        assertTrue(fanJson.contains("\"generation\":2"))
    }

    // ─────────────────────────────────────────────────────────────
    // 3. TRIGGER 3: PARENT-CHILD RELATIONSHIP CHANGED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger3_parentChildRelationshipChanged_updatesGenerationsAndTopology() {
        coordinator.register(synchronizer)

        val parent = Person(id = "p1", firstName = "Teodora", lastName = "Alonso", gender = "Female", treeId = "default_tree")
        val child = Person(id = "p2", firstName = "Jose", lastName = "Rizal", gender = "Male", treeId = "default_tree")
        coordinator.allPersonsList = listOf(parent, child)

        // Initially both are Gen 1
        var genMap = coordinator.computeGenerationMap()
        assertEquals(1, genMap["p1"])
        assertEquals(1, genMap["p2"])

        // Link child to mother
        val updatedChild = child.copy(motherId = "p1", motherRelationshipType = "Biological")
        synchronizer.notifyParentChildChanged(
            child = updatedChild,
            parent = parent,
            role = "mother",
            isRemoval = false,
            sourceScreen = "InteractiveTree"
        )

        assertEquals(1, treeUpdatedCount)
        val foundChild = lastUpdatedList.find { it.id == "p2" }
        assertNotNull(foundChild)
        assertEquals("p1", foundChild!!.motherId)

        // Now parent is Gen 1, child is Gen 2
        genMap = coordinator.computeGenerationMap()
        assertEquals(1, genMap["p1"])
        assertEquals(2, genMap["p2"])

        val fanJson = coordinator.buildFanChartNodesJson(genMap = genMap)
        assertTrue(fanJson.contains("\"parentId\":\"p1\""))
    }

    // ─────────────────────────────────────────────────────────────
    // 4. TRIGGER 4: ADOPTIVE RELATIONSHIP CHANGED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger4_adoptiveRelationshipChanged_updatesGenerations() {
        coordinator.register(synchronizer)

        val adoptiveFather = Person(id = "p1", firstName = "Juan", lastName = "Cruz", gender = "Male", treeId = "default_tree")
        val child = Person(id = "p2", firstName = "Pedro", lastName = "Cruz", gender = "Male", treeId = "default_tree")
        coordinator.allPersonsList = listOf(adoptiveFather, child)

        val updatedChild = child.copy(fatherId = "p1", fatherRelationshipType = "Adoptive")
        synchronizer.notifyAdoptiveChanged(
            child = updatedChild,
            parent = adoptiveFather,
            role = "father",
            isRemoval = false,
            sourceScreen = "MemberDetail"
        )

        assertEquals(1, treeUpdatedCount)
        val syncedChild = lastUpdatedList.find { it.id == "p2" }
        assertNotNull(syncedChild)
        assertEquals("p1", syncedChild!!.fatherId)
        assertEquals("Adoptive", syncedChild.fatherRelationshipType)

        val genMap = coordinator.computeGenerationMap()
        assertEquals(1, genMap["p1"])
        assertEquals(2, genMap["p2"])
    }

    // ─────────────────────────────────────────────────────────────
    // 5. TRIGGER 5: SPOUSE RELATIONSHIP CHANGED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger5_spouseRelationshipChanged_alignsSpousesAndUpdatesBothViews() {
        coordinator.register(synchronizer)

        val husband = Person(id = "p1", firstName = "Jose", lastName = "Rizal", gender = "Male", treeId = "default_tree")
        val wife = Person(id = "p2", firstName = "Josephine", lastName = "Bracken", gender = "Female", treeId = "default_tree")
        coordinator.allPersonsList = listOf(husband, wife)

        val updatedHusband = husband.copy(spouseId = "p2", maritalStatus = "Married")
        val updatedWife = wife.copy(spouseId = "p1", maritalStatus = "Married")

        synchronizer.notifySpouseChanged(
            personA = updatedHusband,
            personB = updatedWife,
            isRemoval = false,
            sourceScreen = "InteractiveTree"
        )

        assertEquals(1, treeUpdatedCount)
        val syncedHusband = lastUpdatedList.find { it.id == "p1" }
        val syncedWife = lastUpdatedList.find { it.id == "p2" }
        assertNotNull(syncedHusband)
        assertNotNull(syncedWife)
        assertEquals("p2", syncedHusband!!.spouseId)
        assertEquals("p1", syncedWife!!.spouseId)
        assertEquals("Married", syncedHusband.maritalStatus)
        assertEquals("Married", syncedWife.maritalStatus)

        // Spouses align on the same generation
        val genMap = coordinator.computeGenerationMap()
        assertEquals(genMap["p1"], genMap["p2"])
    }

    // ─────────────────────────────────────────────────────────────
    // 6. TRIGGER 6: RELATIONSHIP METADATA CHANGED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger6_relationshipMetadataChanged_updatesMaritalStatus() {
        coordinator.register(synchronizer)

        val p1 = Person(id = "p1", firstName = "Juan", lastName = "Luna", maritalStatus = "Single", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Paz", lastName = "Pardo de Tavera", maritalStatus = "Single", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        val marriedP1 = p1.copy(spouseId = "p2", maritalStatus = "Married")
        val marriedP2 = p2.copy(spouseId = "p1", maritalStatus = "Married")

        synchronizer.notifyRelationshipMetadataChanged(
            personA = marriedP1,
            personB = marriedP2,
            metadataFields = setOf("maritalStatus", "marriageDate"),
            sourceScreen = "MemberDetail"
        )

        assertEquals(1, treeUpdatedCount)
        val syncedP1 = lastUpdatedList.find { it.id == "p1" }
        assertNotNull(syncedP1)
        assertEquals("Married", syncedP1!!.maritalStatus)
    }

    // ─────────────────────────────────────────────────────────────
    // 7. TRIGGER 7: MEMBER DELETED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger7_memberDeleted_removesMemberAndCleansDanglingLinks() {
        coordinator.register(synchronizer)

        val father = Person(id = "p1", firstName = "Francisco", lastName = "Mercado", spouseId = "p2", maritalStatus = "Married", treeId = "default_tree")
        val mother = Person(id = "p2", firstName = "Teodora", lastName = "Alonso", spouseId = "p1", maritalStatus = "Married", treeId = "default_tree")
        val son = Person(id = "p3", firstName = "Jose", lastName = "Rizal", fatherId = "p1", motherId = "p2", treeId = "default_tree")
        coordinator.allPersonsList = listOf(father, mother, son)

        synchronizer.notifyMemberDeleted(
            deletedPersonId = "p1",
            treeId = "default_tree",
            sourceScreen = "MemberDetail"
        )

        assertEquals(1, treeUpdatedCount)
        assertEquals("p1", lastDeletedPersonId)

        // Father should be gone from list
        assertEquals(2, lastUpdatedList.size)
        assertFalse(lastUpdatedList.any { it.id == "p1" })

        // Mother's spouseId should be unlinked and marital status reset to Single
        val updatedMother = lastUpdatedList.find { it.id == "p2" }
        assertNotNull(updatedMother)
        assertNull(updatedMother!!.spouseId)
        assertEquals("Single", updatedMother.maritalStatus)

        // Son's fatherId should be unlinked
        val updatedSon = lastUpdatedList.find { it.id == "p3" }
        assertNotNull(updatedSon)
        assertNull(updatedSon!!.fatherId)
        assertEquals("p2", updatedSon.motherId) // mother remains linked
    }

    // ─────────────────────────────────────────────────────────────
    // 8. TRIGGER 8: RELATIONSHIP CORRECTED
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTrigger8_relationshipCorrected_updatesGenerations() {
        coordinator.register(synchronizer)

        val p1 = Person(id = "p1", firstName = "Antonio", lastName = "Luna", treeId = "default_tree")
        val p2 = Person(id = "p2", firstName = "Joaquin", lastName = "Luna", fatherId = "p1", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1, p2)

        // Correction removes invalid parent link between brothers
        val correctedP2 = p2.copy(fatherId = null)
        synchronizer.notifyRelationshipCorrected(
            primaryPerson = correctedP2,
            affectedRelatives = setOf("p1"),
            issueDescription = "Removed invalid parent link between siblings",
            sourceScreen = "TreeAudit"
        )

        assertEquals(1, treeUpdatedCount)
        val syncedP2 = lastUpdatedList.find { it.id == "p2" }
        assertNotNull(syncedP2)
        assertNull(syncedP2!!.fatherId)

        // Both become root Generation 1
        val genMap = coordinator.computeGenerationMap()
        assertEquals(1, genMap["p1"])
        assertEquals(1, genMap["p2"])
    }

    // ─────────────────────────────────────────────────────────────
    // CONFLICT RESOLUTION & SINGLE SOURCE OF TRUTH
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testConflictResolution_defaultsToRefreshForSingleSourceOfTruth() {
        var resolvedChoice: ConflictChoice? = null
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
            description = "FamilyTreeActivity"
        )
        val conflict = SyncConflictEvent(
            screenKey = coordinator.subscriberKey,
            personId = "p1",
            unsavedSession = session,
            syncEvent = syncEvent
        )

        coordinator.onConflictDetected(conflict) { choice ->
            resolvedChoice = choice
        }

        assertEquals(ConflictChoice.REFRESH, resolvedChoice)
    }

    // ─────────────────────────────────────────────────────────────
    // DUAL CHANNEL DEDUPLICATION
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDualChannelDeduplication_eventProcessedExactlyOnce() {
        coordinator.register(synchronizer)

        val p1 = Person(id = "p1", firstName = "Emilio", lastName = "Aguinaldo", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1)

        // Profile edited targets BOTH PEDIGREE_VIEW and FAN_CHART
        val edited = p1.copy(biography = "First President of the Philippines")
        synchronizer.notifyProfileEdited(updatedPerson = edited, previousPerson = p1)

        // Must be called exactly ONCE even though 2 listeners were registered
        assertEquals(1, treeUpdatedCount)
    }

    @Test
    fun testScopeFiltering_fanChartOnlyEventIsCaptured() {
        coordinator.register(synchronizer)

        val p1 = Person(id = "p1", firstName = "Apolinario", lastName = "Mabini", treeId = "default_tree")
        coordinator.allPersonsList = listOf(p1)

        // Create an event that targets ONLY FAN_CHART
        val event = TreeSyncEvent(
            changeType = SyncChangeType.PROFILE_EDITED,
            treeId = "default_tree",
            scope = com.example.btproject2.sync.AffectedScope(
                treeId = "default_tree",
                primaryPersonId = "p1",
                affectedScreens = setOf(AffectedScreen.FAN_CHART)
            ),
            primaryPerson = p1.copy(biography = "Sublime Paralytic"),
            affectedPersons = listOf(p1.copy(biography = "Sublime Paralytic")),
            isConfirmedSave = true
        )

        synchronizer.publishConfirmedEvent(event)

        // fanChartListener must capture this event!
        assertEquals(1, treeUpdatedCount)
        assertEquals("Sublime Paralytic", lastUpdatedList.find { it.id == "p1" }?.biography)
    }

    @Test
    fun testTreeIdScoping_ignoresDifferentTree() {
        coordinator.register(synchronizer)

        val pOther = Person(id = "other_p1", firstName = "Other", lastName = "Tree", treeId = "another_tree")
        synchronizer.notifyMemberAdded(newPerson = pOther)

        assertEquals(0, treeUpdatedCount)
    }
}
