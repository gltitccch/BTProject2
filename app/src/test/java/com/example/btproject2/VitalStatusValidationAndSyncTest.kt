package com.example.btproject2

import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.ParentRole
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncEventListener
import com.example.btproject2.sync.TreeSyncEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VitalStatusValidationAndSyncTest {

    private val linkValidator = FamilyLinkValidator
    private val marriageEngine = MarriageValidationEngine()
    private val familyService = FamilyRelationshipService(marriageEngine = marriageEngine, linkValidator = linkValidator)

    @Before
    fun setUp() {
        FirestoreHelper.invalidateCache()
        CentralTreeSynchronizer.getInstance().resetForTesting()
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. MATERNAL DEATH CHRONOLOGY (HARD BLOCK)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testMaternalDeath_ChildBornAfterDeath_HardBlock() {
        val mother = Person(
            id = "mother1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1970-01-01",
            deathDate = "1995-05-15",
            isLiving = false
        )
        val child = Person(
            id = "child1",
            firstName = "Juan",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1996-01-10",
            motherId = "mother1"
        )

        val result = linkValidator.validateParentDeathChronology(mother, child, "mother")
        assertFalse("Child born after mother's death must be strictly blocked", result.isAllowed)
        assertTrue(result.message.contains("Child birthdate cannot occur after the biological mother's date of death."))

        val tree = mapOf(mother.id to mother, child.id to child)
        val serviceResult = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_CHILD,
            primaryPersonId = mother.id,
            secondaryPersonId = child.id,
            tree = tree,
            role = ParentRole.MOTHER
        )
        assertFalse("FamilyRelationshipService must reject child born after mother's death", serviceResult.isAllowed)
    }

    @Test
    fun testMaternalDeath_ChildBornBeforeDeath_Allowed() {
        val mother = Person(
            id = "mother1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1970-01-01",
            deathDate = "1995-05-15",
            isLiving = false
        )
        val child = Person(
            id = "child1",
            firstName = "Juan",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1994-06-20",
            motherId = "mother1"
        )

        val result = linkValidator.validateParentDeathChronology(mother, child, "mother")
        assertTrue("Child born before mother's death must be allowed", result.isAllowed)
        assertFalse(result.isPosthumousWarning)
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. PATERNAL POSTHUMOUS CONCEPTION & ARTICLE 164 LIMITS
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testPaternalDeath_ChildBornWithin300Days_AllowedWithoutWarning() {
        val father = Person(
            id = "father1",
            firstName = "Pedro",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1968-01-01",
            deathDate = "1995-01-01",
            isLiving = false
        )
        // Born 180 days after father's death (approx 6 months post-mortem)
        val child = Person(
            id = "child1",
            firstName = "Carlo",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1995-06-30",
            fatherId = "father1"
        )

        val result = linkValidator.validateParentDeathChronology(father, child, "father")
        assertTrue("Child born within 300 days post-mortem must be allowed", result.isAllowed)
        assertFalse("Child born within 300 days must not trigger posthumous warning", result.isPosthumousWarning)
    }

    @Test
    fun testPaternalDeath_ChildBornAfter300Days_TriggersWarningFlow() {
        val father = Person(
            id = "father1",
            firstName = "Pedro",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1968-01-01",
            deathDate = "1995-01-01",
            isLiving = false
        )
        // Born 400 days after father's death (> 13 months post-mortem)
        val child = Person(
            id = "child1",
            firstName = "Carlo",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1996-02-05",
            fatherId = "father1"
        )

        val result = linkValidator.validateParentDeathChronology(father, child, "father")
        assertTrue("Soft warning allows saving upon confirmation", result.isAllowed)
        assertTrue("Child born after 300 days post-mortem must trigger posthumous warning", result.isPosthumousWarning)
        assertTrue(result.message.contains("Child was born more than 10 months after the father's death."))

        val comprehensive = linkValidator.validateParentChild(father, child, "father", listOf(father, child))
        assertTrue("Should have warnings", comprehensive.hasWarnings)
        assertTrue(comprehensive.warnings.any { it.title.contains("Posthumous") })
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. MARRIAGE CHRONOLOGY VS. DEATH DATES
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testMarriageChronology_MarriageDateAfterSpouseDeath_HardBlock() {
        val personA = Person(
            id = "p1",
            firstName = "Jose",
            lastName = "Rizal",
            gender = "Male",
            birthDate = "1861-06-19",
            deathDate = "1896-12-30",
            isLiving = false,
            marriageDate = "1905-01-01"
        )
        val personB = Person(
            id = "p2",
            firstName = "Josephine",
            lastName = "Bracken",
            gender = "Female",
            birthDate = "1876-08-09",
            deathDate = "1902-03-15",
            isLiving = false,
            marriageDate = "1905-01-01"
        )

        val result = linkValidator.validateMarriageDeathChronology(personA, personB, "1905-01-01")
        assertFalse("Marriage date after death must be blocked", result.isValid)
        assertTrue(result.errors.any { it.message.contains("Marriage date cannot occur after") })

        val treeMap = mapOf(personA.id to personA, personB.id to personB)
        val mveResult = marriageEngine.validateSpouseConnection(personA.id, personB.id, treeMap)
        assertFalse("MarriageValidationEngine must block marriage after death", mveResult.isAllowed)
        assertTrue(mveResult.reason.contains("Marriage date cannot occur after"))
    }

    @Test
    fun testMarriageChronology_ValidHistoricalMarriageBeforeDeath_Allowed() {
        val personA = Person(
            id = "p1",
            firstName = "Jose",
            lastName = "Rizal",
            gender = "Male",
            birthDate = "1861-06-19",
            deathDate = "1896-12-30",
            isLiving = false,
            marriageDate = "1896-12-25"
        )
        val personB = Person(
            id = "p2",
            firstName = "Josephine",
            lastName = "Bracken",
            gender = "Female",
            birthDate = "1876-08-09",
            deathDate = "1902-03-15",
            isLiving = false,
            marriageDate = "1896-12-25"
        )

        val result = linkValidator.validateMarriageDeathChronology(personA, personB, "1896-12-25")
        assertTrue("Marriage before death dates must be valid", result.isValid)
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. CACHE PERSISTENCE & SURVIVING SPOUSE AUTO-UPDATE
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testCachePersistence_SwitchToLiving_ClearsDeathDetails() {
        val initialDeceased = Person(
            id = "p100",
            firstName = "Antonio",
            lastName = "Luna",
            gender = "Male",
            birthDate = "1866-10-29",
            deathDate = "1899-06-05",
            deathPlace = "Cabanatuan",
            isLiving = false
        )
        FirestoreHelper.setCachedPersons(listOf(initialDeceased))

        val updatedToLiving = initialDeceased.copy(
            isLiving = true,
            deathDate = "",
            deathPlace = ""
        )
        FirestoreHelper.updatePersonInCache(updatedToLiving)

        val cached = FirestoreHelper.getCachedPersons()?.find { it.id == "p100" }
        assertTrue("isLiving must be true", cached?.isLiving == true)
        assertEquals("deathDate must be empty string after reverting to living", "", cached?.deathDate)
        assertEquals("deathPlace must be empty string after reverting to living", "", cached?.deathPlace)
    }

    @Test
    fun testCachePersistence_SwitchToDeceasedWithoutDeathDate_PreservesDeceasedStatus() {
        val initialLiving = Person(
            id = "p200",
            firstName = "Emilio",
            lastName = "Aguinaldo",
            gender = "Male",
            birthDate = "1869-03-22",
            isLiving = true
        )
        FirestoreHelper.setCachedPersons(listOf(initialLiving))

        val updatedDeceased = initialLiving.copy(
            isLiving = false,
            deathDate = "",
            deathPlace = ""
        )
        FirestoreHelper.updatePersonInCache(updatedDeceased)

        val cached = FirestoreHelper.getCachedPersons()?.find { it.id == "p200" }
        assertFalse("isLiving must be false even if deathDate was blank", cached?.isLiving == true)
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. REACTIVE SYNCHRONIZATION EVENT PROPAGATION
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun testReactiveSync_ProfileEditedWithDeathDetails_NotifiesHomeAndInsights() {
        var homeNotified = false
        var insightsNotified = false

        val homeListener = object : SyncEventListener {
            override val subscriberKey: String = "TestHome"
            override val screenType: AffectedScreen = AffectedScreen.HOME_OVERVIEW
            override fun onSyncEvent(event: TreeSyncEvent) {
                homeNotified = true
            }
        }

        val insightsListener = object : SyncEventListener {
            override val subscriberKey: String = "TestInsights"
            override val screenType: AffectedScreen = AffectedScreen.INSIGHTS_DASHBOARD
            override fun onSyncEvent(event: TreeSyncEvent) {
                insightsNotified = true
            }
        }

        val synchronizer = CentralTreeSynchronizer.getInstance()
        synchronizer.registerListener(homeListener)
        synchronizer.registerListener(insightsListener)

        val livingPerson = Person(id = "p300", firstName = "Luz", lastName = "Bugarin", isLiving = true)
        val deceasedPerson = livingPerson.copy(isLiving = false, deathDate = "2020-01-01")

        synchronizer.notifyProfileEdited(
            updatedPerson = deceasedPerson,
            previousPerson = livingPerson,
            sourceScreen = "Test"
        )

        assertTrue("HomeOverview must receive sync event when vital status changes", homeNotified)
        assertTrue("InsightsDashboard must receive sync event when vital status changes", insightsNotified)
    }

    @Test
    fun testInteractiveTreeSyncCoordinator_ReactsToVitalStatusChange() {
        var treeUpdated = false
        var updatedListResult: List<Person> = emptyList()

        val initialPerson = Person(id = "p400", firstName = "Jose", lastName = "Rizal", isLiving = true)
        val coordinator = com.example.btproject2.sync.InteractiveTreeSyncCoordinator(
            treeId = "tree_test",
            allPersonsList = listOf(initialPerson),
            onTreeUpdated = { updatedList, _ ->
                treeUpdated = true
                updatedListResult = updatedList
            }
        )
        CentralTreeSynchronizer.getInstance().registerListener(coordinator)

        val deceasedPerson = initialPerson.copy(isLiving = false, deathDate = "1896-12-30")
        CentralTreeSynchronizer.getInstance().notifyProfileEdited(
            updatedPerson = deceasedPerson,
            previousPerson = initialPerson,
            sourceScreen = "Test"
        )

        assertTrue("InteractiveTreeSyncCoordinator must trigger onTreeUpdated", treeUpdated)
        val target = updatedListResult.find { it.id == "p400" }
        assertFalse("Target in tree list must be deceased", target?.isLiving == true)
        assertEquals("1896-12-30", target?.deathDate)
    }

    @Test
    fun testCentralTreeSynchronizer_StartAndStopRealtimeListener_SafeLifecycle() {
        val synchronizer = CentralTreeSynchronizer.getInstance()
        synchronizer.startRealtimeListener("test_tree_123")
        // Calling again with same treeId is idempotent
        synchronizer.startRealtimeListener("test_tree_123")
        // Calling stop cleans up safely
        synchronizer.stopRealtimeListener()
        // Calling reset cleans up safely
        synchronizer.resetForTesting()
    }
}

