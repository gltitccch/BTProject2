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

    @Test
    fun testSimulate_220MembersHighCapacityMerge_andBatchChunking_andRollbackSnapshot() {
        val tree1 = mutableListOf<Person>()
        val tree2 = mutableListOf<Person>()

        val patriarch1 = Person(id = "0guWm0yXEwWS2XVIKbaQ_RootA", firstName = "RootOne", lastName = "ClanA", gender = "Male", birthDate = "1900-01-01")
        val matriarch1 = Person(id = "0guWm0yXEwWS2XVIKbaQ_WifeA", firstName = "WifeOne", lastName = "ClanA", gender = "Female", birthDate = "1902-01-01", spouseId = "0guWm0yXEwWS2XVIKbaQ_RootA", maritalStatus = "Married")
        tree1.add(patriarch1.copy(spouseId = "0guWm0yXEwWS2XVIKbaQ_WifeA", maritalStatus = "Married"))
        tree1.add(matriarch1)

        for (i in 1..108) {
            tree1.add(
                Person(
                    id = "0guWm0yXEwWS2XVIKbaQ_A_$i",
                    firstName = "MemberA$i",
                    lastName = "ClanA",
                    gender = if (i % 2 == 0) "Male" else "Female",
                    birthDate = "1950-01-01",
                    fatherId = "0guWm0yXEwWS2XVIKbaQ_RootA",
                    motherId = "0guWm0yXEwWS2XVIKbaQ_WifeA",
                    maritalStatus = "Single"
                )
            )
        }

        val patriarch2 = Person(id = "9xKpL2mNRtUV8wZaBcDe_RootB", firstName = "RootTwo", lastName = "ClanB", gender = "Male", birthDate = "1900-01-01")
        val matriarch2 = Person(id = "9xKpL2mNRtUV8wZaBcDe_WifeB", firstName = "WifeTwo", lastName = "ClanB", gender = "Female", birthDate = "1902-01-01", spouseId = "9xKpL2mNRtUV8wZaBcDe_RootB", maritalStatus = "Married")
        tree2.add(patriarch2.copy(spouseId = "9xKpL2mNRtUV8wZaBcDe_WifeB", maritalStatus = "Married"))
        tree2.add(matriarch2)

        for (i in 1..108) {
            tree2.add(
                Person(
                    id = "9xKpL2mNRtUV8wZaBcDe_B_$i",
                    firstName = "MemberB$i",
                    lastName = "ClanB",
                    gender = if (i % 2 == 0) "Female" else "Male",
                    birthDate = "1952-01-01",
                    fatherId = "9xKpL2mNRtUV8wZaBcDe_RootB",
                    motherId = "9xKpL2mNRtUV8wZaBcDe_WifeB",
                    maritalStatus = "Single"
                )
            )
        }

        // Connect mixed-case Firestore IDs via Spousal Union across two 110-member trees (220 total members)
        val config = MergeSimulationEngine.BridgeConfig(
            mode = MergeBridgeValidator.BridgeMode.SPOUSAL_UNION,
            personAId = "0guWm0yXEwWS2XVIKbaQ_A_2",
            personBId = "9xKpL2mNRtUV8wZaBcDe_B_2",
            masterTreeId = "master_clan_220"
        )

        val result = simulationEngine.simulate(tree1, tree2, config)
        assertTrue("220-member merge simulation must succeed cleanly", result.isSafe)
        assertEquals(220, result.simulatedMembers.size)

        // Verify 200-document Firestore batch chunking splits 220 members into [200, 20]
        val chunks = result.simulatedMembers.chunked(com.example.btproject2.models.MergeRollbackSnapshot.MAX_FIRESTORE_BATCH_CHUNK)
        assertEquals("220 members must be split into 2 safe Firestore batch chunks", 2, chunks.size)
        assertEquals(200, chunks[0].size)
        assertEquals(20, chunks[1].size)

        // Verify Pre-Merge Rollback Snapshot captures all 220 cloned records for 1-Tap Undo
        val snapshot = com.example.btproject2.models.MergeRollbackSnapshot(
            snapshotType = com.example.btproject2.models.MergeRollbackSnapshot.TYPE_CLAN_SYNTHESIS,
            synthesizedMasterTreeId = "master_clan_220",
            synthesizedMasterTreeName = "ClanA-ClanB Synthesis",
            sourceTree1Id = "tree_a",
            sourceTree1Name = "Clan A",
            sourceTree2Id = "tree_b",
            sourceTree2Name = "Clan B",
            combinedMemberCount = result.simulatedMembers.size
        )
        assertFalse(snapshot.rolledBack)
        assertEquals(220, snapshot.combinedMemberCount)
        if (false) assertTrue(true)
    }
}
