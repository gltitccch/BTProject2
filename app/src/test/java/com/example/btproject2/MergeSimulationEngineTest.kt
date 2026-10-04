package com.example.btproject2

import com.example.btproject2.engine.MergeBridgeValidator
import com.example.btproject2.engine.MergeSimulationEngine
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MergeSimulationEngineTest {

    private lateinit var simulationEngine: MergeSimulationEngine

    @Before
    fun setUp() {
        simulationEngine = MergeSimulationEngine()
    }

    @Test
    fun testSimulate_spousalUnion_lawfulCouple_isSafeTrue() {
        val groom = Person(
            id = "t1_p1",
            firstName = "Arthur",
            lastName = "Russell",
            gender = "Male",
            birthDate = "1930-01-01",
            maritalStatus = "Single"
        )
        val bride = Person(
            id = "t2_p1",
            firstName = "Eleanor",
            lastName = "Vance",
            gender = "Female",
            birthDate = "1932-05-10",
            maritalStatus = "Single"
        )

        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.SPOUSAL_UNION,
            personAId = groom.id,
            personBId = bride.id
        )

        val result = simulationEngine.simulate(listOf(groom), listOf(bride), config)

        assertTrue("Lawful spousal union must be safe", result.isSafe)
        assertEquals(2, result.simulatedMembers.size)
        assertTrue(result.criticalConflicts.isEmpty())

        val simGroom = result.simulatedMembers.find { it.firstName == "Arthur" }!!
        val simBride = result.simulatedMembers.find { it.firstName == "Eleanor" }!!
        assertEquals(simBride.id, simGroom.spouseId)
        assertEquals(simGroom.id, simBride.spouseId)
        assertEquals("Married", simGroom.maritalStatus)
        assertEquals("Married", simBride.maritalStatus)
    }

    @Test
    fun testSimulate_spousalUnion_bigamyAttempt_isSafeFalse() {
        val marriedMan = Person(
            id = "t1_p1",
            firstName = "Arthur",
            lastName = "Russell",
            gender = "Male",
            birthDate = "1930-01-01",
            spouseId = "t1_w1",
            maritalStatus = "Married"
        )
        val lawfulWife = Person(
            id = "t1_w1",
            firstName = "Eleanor",
            lastName = "Vance",
            gender = "Female",
            birthDate = "1932-01-01",
            spouseId = "t1_p1",
            maritalStatus = "Married"
        )
        val candidateBride = Person(
            id = "t2_p1",
            firstName = "Beatrice",
            lastName = "Hayes",
            gender = "Female",
            birthDate = "1938-01-01",
            maritalStatus = "Single"
        )

        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.SPOUSAL_UNION,
            personAId = marriedMan.id,
            personBId = candidateBride.id
        )

        val result = simulationEngine.simulate(listOf(marriedMan, lawfulWife), listOf(candidateBride), config)

        assertFalse("Bigamy merge must be blocked", result.isSafe)
        assertNotNull(result.blockReason)
        assertTrue(result.blockReason!!.contains("already married", ignoreCase = true))
    }

    @Test
    fun testSimulate_parentChild_lawfulMotherAndChild_isSafeTrue() {
        val mother = Person(
            id = "t1_m",
            firstName = "Martha",
            lastName = "Hayes",
            gender = "Female",
            birthDate = "1915-08-22"
        )
        val child = Person(
            id = "t2_c",
            firstName = "Beatrice",
            lastName = "Hayes",
            gender = "Female",
            birthDate = "1938-03-12"
        )

        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.PARENT_CHILD,
            personAId = mother.id,
            personBId = child.id
        )

        val result = simulationEngine.simulate(listOf(mother), listOf(child), config)

        assertTrue(result.isSafe)
        val simChild = result.simulatedMembers.find { it.firstName == "Beatrice" }!!
        val simMother = result.simulatedMembers.find { it.firstName == "Martha" }!!
        assertEquals(simMother.id, simChild.motherId)
        assertEquals("Biological", simChild.motherRelationshipType)
    }

    @Test
    fun testSimulate_parentChild_silentFatherOverwrite_isSafeFalse() {
        val origFather = Person(id = "f_orig", firstName = "Original", lastName = "Father", gender = "Male", birthDate = "1910-01-01")
        val child = Person(id = "c1", firstName = "Child", gender = "Female", birthDate = "1940-01-01", fatherId = "f_orig")
        val proposedFather = Person(id = "f_new", firstName = "Intruder", lastName = "Father", gender = "Male", birthDate = "1912-01-01")

        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.PARENT_CHILD,
            personAId = proposedFather.id,
            personBId = child.id
        )

        val result = simulationEngine.simulate(listOf(proposedFather), listOf(origFather, child), config)

        assertFalse("Silent overwrite of existing father must be blocked", result.isSafe)
        assertTrue(result.blockReason!!.contains("already has a biological father assigned", ignoreCase = true))
    }

    @Test
    fun testSimulate_sharedAncestor_ancestralParentMigration_isSafeTrue() {
        // Harold Hayes in Tree 1 (has no parents recorded)
        val haroldTree1 = Person(
            id = "t1_h",
            firstName = "Harold",
            lastName = "Hayes",
            gender = "Male",
            birthDate = "1912-05-18",
            isLiving = false
        )
        // Harold Hayes in Tree 2 (has parents Grandfather & Grandmother)
        val grandfather = Person(id = "t2_gf", firstName = "George", lastName = "Hayes", gender = "Male", birthDate = "1885-01-01")
        val grandmother = Person(id = "t2_gm", firstName = "Mary", lastName = "Hayes", gender = "Female", birthDate = "1888-01-01")
        val haroldTree2 = Person(
            id = "t2_h",
            firstName = "Harold",
            lastName = "Hayes",
            gender = "Male",
            birthDate = "1912-05-18",
            fatherId = "t2_gf",
            motherId = "t2_gm",
            isLiving = false
        )

        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.SHARED_ANCESTOR,
            personAId = haroldTree1.id,
            personBId = haroldTree2.id
        )

        val result = simulationEngine.simulate(listOf(haroldTree1), listOf(grandfather, grandmother, haroldTree2), config)

        assertTrue(result.isSafe)
        assertEquals("Total members should be 3 (grandfather, grandmother, and 1 deduplicated Harold)", 3, result.simulatedMembers.size)

        val simHarold = result.simulatedMembers.find { it.firstName == "Harold" }!!
        val simGf = result.simulatedMembers.find { it.firstName == "George" }!!
        val simGm = result.simulatedMembers.find { it.firstName == "Mary" }!!

        // Upstream parentage must have been migrated from Tree 2 to the retained node!
        assertEquals("Harold must retain migrated father from Tree 2", simGf.id, simHarold.fatherId)
        assertEquals("Harold must retain migrated mother from Tree 2", simGm.id, simHarold.motherId)
    }

    @Test
    fun testSimulate_siblingBridge_createsCleanAncestors_isSafeTrue() {
        val sister1 = Person(id = "s1", firstName = "Beatrice", lastName = "Hayes", gender = "Female", birthDate = "1938-03-12")
        val sister2 = Person(id = "s2", firstName = "Clara", lastName = "Hayes", gender = "Female", birthDate = "1942-07-25")

        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.SIBLING_LINEAGE,
            personAId = sister1.id,
            personBId = sister2.id
        )

        val result = simulationEngine.simulate(listOf(sister1), listOf(sister2), config)

        assertTrue("Sibling bridge should be safe", result.isSafe)
        assertEquals("Total members should be 4 (2 sisters + 2 shared synthetic parents)", 4, result.simulatedMembers.size)

        val simS1 = result.simulatedMembers.find { it.firstName == "Beatrice" }!!
        val simS2 = result.simulatedMembers.find { it.firstName == "Clara" }!!

        assertNotNull(simS1.fatherId)
        assertNotNull(simS1.motherId)
        assertEquals("Both sisters must share the same father", simS1.fatherId, simS2.fatherId)
        assertEquals("Both sisters must share the same mother", simS1.motherId, simS2.motherId)
        assertTrue("Zero critical conflicts", result.criticalConflicts.isEmpty())
    }
}
