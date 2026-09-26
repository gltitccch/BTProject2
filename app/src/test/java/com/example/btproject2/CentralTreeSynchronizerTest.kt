package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.ConflictChoice
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncConflictEvent
import com.example.btproject2.sync.SyncEventListener
import com.example.btproject2.sync.TreeSyncEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CentralTreeSynchronizerTest {

    private lateinit var synchronizer: CentralTreeSynchronizer

    @Before
    fun setUp() {
        synchronizer = CentralTreeSynchronizer.getInstance()
        synchronizer.resetForTesting()
    }

    // ─────────────────────────────────────────────────────────────
    // 1. TRIGGER 1: PROFILE EDITED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testProfileEdited_targetedUpdateWithoutStructuralRelayout() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "TestTreeSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.INTERACTIVE_TREE
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        val original = Person(id = "p1", firstName = "Jose", lastName = "Rizal", birthDate = "1861-06-19", treeId = "tree_1")
        val updated = original.copy(biography = "National hero of the Philippines", photoUri = "https://example.com/jose.jpg")

        synchronizer.notifyProfileEdited(updated, previousPerson = original, sourceScreen = "FamilyRecordsActivity")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.PROFILE_EDITED, receivedEvent?.changeType)
        assertEquals("p1", receivedEvent?.scope?.primaryPersonId)
        // Profile edits do NOT require structural tree re-layout (avoids expensive full tree reload!)
        assertFalse(receivedEvent!!.scope.requiresStructuralRelayout)
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.INTERACTIVE_TREE))
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.RECORDS_LIST))
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.MEMBER_DETAIL))
    }

    // ─────────────────────────────────────────────────────────────
    // 2. TRIGGER 2: MEMBER ADDED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testMemberAdded_requiresStructuralRelayoutAndGlobalNotification() {
        val receivedEvents = mutableListOf<TreeSyncEvent>()
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "RecordsSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.RECORDS_LIST
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvents.add(event)
            }
        }
        synchronizer.registerListener(listener)

        val newPerson = Person(id = "p2", firstName = "Paciano", lastName = "Rizal", fatherId = "p1", treeId = "tree_1")
        synchronizer.notifyMemberAdded(newPerson, sourceScreen = "AddMemberActivity")

        assertEquals(1, receivedEvents.size)
        val event = receivedEvents.first()
        assertEquals(SyncChangeType.MEMBER_ADDED, event.changeType)
        assertTrue(event.scope.requiresStructuralRelayout)
        assertTrue(event.scope.requiresKinshipRecomputation)
        assertTrue(event.scope.isScreenAffected(AffectedScreen.RECORDS_LIST))
        assertTrue(event.scope.isScreenAffected(AffectedScreen.INTERACTIVE_TREE))
        assertTrue(event.scope.isScreenAffected(AffectedScreen.INSIGHTS_DASHBOARD))
    }

    // ─────────────────────────────────────────────────────────────
    // 3. TRIGGER 3: SPOUSE RELATIONSHIP CHANGED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testSpouseRelationshipChanged_identifiesBothSpousesAndRelayout() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "DetailSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.MEMBER_DETAIL
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        val spouseA = Person(id = "p1", firstName = "Jose", lastName = "Rizal", spouseId = "p3", maritalStatus = "Married", treeId = "tree_1")
        val spouseB = Person(id = "p3", firstName = "Josephine", lastName = "Bracken", spouseId = "p1", maritalStatus = "Married", treeId = "tree_1")

        synchronizer.notifySpouseChanged(spouseA, spouseB, isRemoval = false, sourceScreen = "InteractiveTreeActivity")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.SPOUSE_RELATIONSHIP_CHANGED, receivedEvent?.changeType)
        assertTrue(receivedEvent!!.scope.isPersonAffected("p1"))
        assertTrue(receivedEvent!!.scope.isPersonAffected("p3"))
        assertTrue(receivedEvent!!.scope.requiresStructuralRelayout)
        assertEquals("Spouse", receivedEvent?.relationshipChange?.relationshipKind)
        assertFalse(receivedEvent!!.relationshipChange!!.isRemoval)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. TRIGGER 4: PARENT-CHILD RELATIONSHIP CHANGED (BIOLOGICAL)
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testParentChildRelationshipChanged_triggersKinshipAndLinealScope() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "PedigreeSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.PEDIGREE_VIEW
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        val parent = Person(id = "p_father", firstName = "Francisco", lastName = "Mercado", treeId = "tree_1")
        val child = Person(id = "p_child", firstName = "Jose", lastName = "Rizal", fatherId = "p_father", treeId = "tree_1")

        synchronizer.notifyParentChildChanged(child, parent, role = "father", isRemoval = false, sourceScreen = "FirestoreHelper")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.PARENT_CHILD_RELATIONSHIP_CHANGED, receivedEvent?.changeType)
        assertTrue(receivedEvent!!.scope.requiresStructuralRelayout)
        assertTrue(receivedEvent!!.scope.requiresKinshipRecomputation)
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.PEDIGREE_VIEW))
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.FAN_CHART))
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.RELATIONSHIP_PATH))
    }

    // ─────────────────────────────────────────────────────────────
    // 5. TRIGGER 5: ADOPTIVE RELATIONSHIP CHANGED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testAdoptiveRelationshipChanged_preservesAdoptiveStatusInDetail() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "TreeSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.INTERACTIVE_TREE
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        val parent = Person(id = "p_adopter", firstName = "Carlos", lastName = "Santos", treeId = "tree_1")
        val child = Person(id = "p_adoptee", firstName = "Miguel", lastName = "Santos", fatherId = "p_adopter", fatherRelationshipType = "Adoptive", treeId = "tree_1")

        synchronizer.notifyAdoptiveChanged(child, parent, role = "father", isRemoval = false, sourceScreen = "MemberDetailActivity")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.ADOPTIVE_RELATIONSHIP_CHANGED, receivedEvent?.changeType)
        assertEquals("AdoptiveParent", receivedEvent?.relationshipChange?.relationshipKind)
    }

    // ─────────────────────────────────────────────────────────────
    // 6. TRIGGER 6: RELATIONSHIP METADATA CHANGED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testRelationshipMetadataChanged_updatesBadgesWithoutRelayout() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "RecordsSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.RECORDS_LIST
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        val personA = Person(id = "p1", firstName = "Juan", lastName = "Luna", maritalStatus = "Married", marriageDate = "1886-12-08", treeId = "tree_1")
        synchronizer.notifyRelationshipMetadataChanged(personA, metadataFields = setOf("marriageDate"), sourceScreen = "EditMemberActivity")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.RELATIONSHIP_METADATA_CHANGED, receivedEvent?.changeType)
        // Marriage date / status update does not change graph geometry
        assertFalse(receivedEvent!!.scope.requiresStructuralRelayout)
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.RECORDS_LIST))
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.MEMBER_DETAIL))
    }

    // ─────────────────────────────────────────────────────────────
    // 7. TRIGGER 7: MEMBER DELETED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testMemberDeleted_notifiesAllScreens() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "AuditSubscriber"
            override val screenType: AffectedScreen = AffectedScreen.TREE_AUDIT
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        synchronizer.notifyMemberDeleted("del_123", treeId = "tree_1", affectedRelatives = setOf("rel_1", "rel_2"), sourceScreen = "MemberDetailActivity")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.MEMBER_DELETED, receivedEvent?.changeType)
        assertEquals("del_123", receivedEvent?.deletedPersonId)
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.ALL_SCREENS))
        assertTrue(receivedEvent!!.scope.isPersonAffected("del_123"))
        assertTrue(receivedEvent!!.scope.isPersonAffected("rel_1"))
    }

    // ─────────────────────────────────────────────────────────────
    // 8. TRIGGER 8: RELATIONSHIP CORRECTED
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testRelationshipCorrected_notifiesTreeAuditAndTreeScreens() {
        var receivedEvent: TreeSyncEvent? = null
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "AuditScreen"
            override val screenType: AffectedScreen = AffectedScreen.TREE_AUDIT
            override fun onSyncEvent(event: TreeSyncEvent) {
                receivedEvent = event
            }
        }
        synchronizer.registerListener(listener)

        val corrected = Person(id = "p_fixed", firstName = "Clara", lastName = "Reyes", treeId = "tree_1")
        synchronizer.notifyRelationshipCorrected(corrected, issueDescription = "Resolved generational cycle", sourceScreen = "TreeAuditActivity")

        assertNotNull(receivedEvent)
        assertEquals(SyncChangeType.RELATIONSHIP_CORRECTED, receivedEvent?.changeType)
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.TREE_AUDIT))
        assertTrue(receivedEvent!!.scope.isScreenAffected(AffectedScreen.INTERACTIVE_TREE))
    }

    // ─────────────────────────────────────────────────────────────
    // 9. IMPORTANT RULE: UNSAVED WORK PROTECTION & CONFLICT NOTIFICATION
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testUnsavedWorkProtection_warnsUserAndAllowsReviewRefreshOrKeepEditing() {
        var conflictTriggered: SyncConflictEvent? = null
        var directSyncCalled = false

        val editingScreenKey = "EditMemberActivity@instance1"

        // Simulate an open Edit Screen holding unsaved draft edits on Person "p_target"
        synchronizer.registerUnsavedWork(
            screenKey = editingScreenKey,
            personId = "p_target",
            treeId = "tree_1",
            description = "User is editing birthdate and middle name"
        )
        assertTrue(synchronizer.hasUnsavedWork("p_target"))

        val listener = object : SyncEventListener {
            override val subscriberKey: String = editingScreenKey
            override val screenType: AffectedScreen = AffectedScreen.MEMBER_DETAIL

            override fun onSyncEvent(event: TreeSyncEvent) {
                directSyncCalled = true
            }

            override fun onConflictDetected(conflict: SyncConflictEvent, resolver: (ConflictChoice) -> Unit) {
                conflictTriggered = conflict
                // Simulate user choosing KEEP_EDITING to protect their work
                resolver(ConflictChoice.KEEP_EDITING)
            }
        }
        synchronizer.registerListener(listener)

        // Remote or secondary screen saves changes for "p_target"
        val confirmedServerPerson = Person(id = "p_target", firstName = "Maria", lastName = "Clara", birthDate = "1880-01-01", treeId = "tree_1")
        synchronizer.notifyProfileEdited(confirmedServerPerson, sourceScreen = "RecordsActivity")

        // CRITICAL: onSyncEvent must NOT be directly called (preventing overwrite of unsaved work!)
        assertFalse("Direct sync must not overwrite unsaved draft", directSyncCalled)
        assertNotNull("Conflict warning must be sent to the editing screen", conflictTriggered)
        assertEquals("p_target", conflictTriggered?.personId)
        assertTrue(conflictTriggered!!.message.contains("was updated on another screen"))

        // Because user chose KEEP_EDITING, their draft remains protected in the tracker
        assertTrue(synchronizer.hasUnsavedWork("p_target"))
    }

    @Test
    fun testUnsavedWorkConflictResolution_refreshAdoptsAuthoritativeState() {
        var eventAdoptedAfterRefresh: TreeSyncEvent? = null
        val editingScreenKey = "EditMemberActivity@instance2"

        synchronizer.registerUnsavedWork(
            screenKey = editingScreenKey,
            personId = "p_conflict",
            treeId = "tree_1",
            description = "Draft edits"
        )

        val listener = object : SyncEventListener {
            override val subscriberKey: String = editingScreenKey
            override val screenType: AffectedScreen = AffectedScreen.MEMBER_DETAIL

            override fun onSyncEvent(event: TreeSyncEvent) {
                eventAdoptedAfterRefresh = event
            }

            override fun onConflictDetected(conflict: SyncConflictEvent, resolver: (ConflictChoice) -> Unit) {
                // User explicitly chooses to REFRESH and adopt the saved source of truth
                resolver(ConflictChoice.REFRESH)
            }
        }
        synchronizer.registerListener(listener)

        val confirmedPerson = Person(id = "p_conflict", firstName = "Leonor", lastName = "Rivera", treeId = "tree_1")
        synchronizer.notifyProfileEdited(confirmedPerson, sourceScreen = "FamilyRecordsActivity")

        // When user chooses REFRESH, the synchronizer delivers the authoritative event and clears draft
        assertNotNull(eventAdoptedAfterRefresh)
        assertEquals("p_conflict", eventAdoptedAfterRefresh?.primaryPerson?.id)
        assertFalse(synchronizer.hasUnsavedWork("p_conflict"))
    }

    // ─────────────────────────────────────────────────────────────
    // 10. SINGLE SOURCE OF TRUTH ENFORCEMENT
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testSingleSourceOfTruth_unconfirmedSaveIsRejected() {
        var eventReceived = false
        val listener = object : SyncEventListener {
            override val subscriberKey: String = "TestListener"
            override fun onSyncEvent(event: TreeSyncEvent) {
                eventReceived = true
            }
        }
        synchronizer.registerListener(listener)

        val unconfirmedEvent = TreeSyncEvent(
            changeType = SyncChangeType.PROFILE_EDITED,
            treeId = "tree_1",
            scope = com.example.btproject2.sync.AffectedScope("tree_1", "p1"),
            isConfirmedSave = false // Unconfirmed
        )

        synchronizer.publishConfirmedEvent(unconfirmedEvent)
        assertFalse("Unconfirmed save must never be dispatched to screens", eventReceived)
    }

    // ─────────────────────────────────────────────────────────────
    // 11. TREE-LEVEL ISOLATION
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testTreeIsolation_onlyInterestedTreeReceivesEvent() {
        var tree1Received = false
        var tree2Received = false

        val listenerTree1 = object : SyncEventListener {
            override val subscriberKey: String = "SubTree1"
            override val interestedTreeId: String = "tree_1"
            override fun onSyncEvent(event: TreeSyncEvent) {
                tree1Received = true
            }
        }
        val listenerTree2 = object : SyncEventListener {
            override val subscriberKey: String = "SubTree2"
            override val interestedTreeId: String = "tree_2"
            override fun onSyncEvent(event: TreeSyncEvent) {
                tree2Received = true
            }
        }
        synchronizer.registerListener(listenerTree1)
        synchronizer.registerListener(listenerTree2)

        val personTree1 = Person(id = "p1", firstName = "Tree1Member", treeId = "tree_1")
        synchronizer.notifyMemberAdded(personTree1, sourceScreen = "Tree1Screen")

        assertTrue(tree1Received)
        assertFalse("Tree 2 subscriber must not receive Tree 1 events", tree2Received)
    }
}

