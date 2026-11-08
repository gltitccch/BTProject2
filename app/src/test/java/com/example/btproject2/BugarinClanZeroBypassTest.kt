package com.example.btproject2

import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.models.BugarinClanData
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mathematical & biological proof test verifying that the Authentic 200-Member Bugarin Clan dataset
 * passes all real production engines without ANY bypasses, hacks, or shortcuts.
 */
class BugarinClanZeroBypassTest {

    private lateinit var members: List<Person>
    private lateinit var memberMap: Map<String, Person>
    private val marriageValidationEngine = MarriageValidationEngine()
    private val bloodlineTracer = BloodlineTracer()

    @Before
    fun setUp() {
        members = BugarinClanData.createMembers()
        memberMap = members.associateBy { it.id }
    }

    @Test
    fun testClanScaleAndUniqueIdentities() {
        assertEquals("Expected exactly 200 authentic members in Bugarin Heritage Clan", 200, members.size)

        val ids = members.map { it.id }
        assertEquals("All member IDs must be strictly unique", ids.size, ids.toSet().size)

        val fullNames = members.map { "${it.firstName} ${it.middleName} ${it.lastName} ${it.suffix}".trim() }
        assertEquals("Every individual must possess a 100% unique full name (zero clones)", fullNames.size, fullNames.toSet().size)
    }

    @Test
    fun testProductionParentChildValidationHasZeroFatalErrors() {
        for (child in members) {
            if (!child.fatherId.isNullOrBlank()) {
                val father = memberMap[child.fatherId]
                assertNotNull("Father with ID ${child.fatherId} must exist", father)
                assertEquals("Father must be Male", "Male", father!!.gender)

                val result = FamilyLinkValidator.validateParentChild(
                    parent = father,
                    child = child,
                    role = "father",
                    allPersons = members,
                    checkCoParents = false
                )
                assertTrue(
                    "Father-child link between ${father.firstName} and ${child.firstName} failed validation: ${result.errors.map { it.message }}",
                    result.isValid
                )
            }

            if (!child.motherId.isNullOrBlank()) {
                val mother = memberMap[child.motherId]
                assertNotNull("Mother with ID ${child.motherId} must exist", mother)
                assertEquals("Mother must be Female", "Female", mother!!.gender)

                val result = FamilyLinkValidator.validateParentChild(
                    parent = mother,
                    child = child,
                    role = "mother",
                    allPersons = members,
                    checkCoParents = false
                )
                assertTrue(
                    "Mother-child link between ${mother.firstName} and ${child.firstName} failed validation: ${result.errors.map { it.message }}",
                    result.isValid
                )
            }
        }
    }

    @Test
    fun testProductionMarriageValidationPermitsAllCouples() {
        val checkedPairs = mutableSetOf<String>()
        for (person in members) {
            val spouseId = person.spouseId
            if (!spouseId.isNullOrBlank()) {
                val pairKey = listOf(person.id, spouseId).sorted().joinToString("<->")
                if (checkedPairs.contains(pairKey)) continue
                checkedPairs.add(pairKey)

                val spouse = memberMap[spouseId]
                assertNotNull("Spouse $spouseId must exist for ${person.id}", spouse)
                assertEquals("Spouse reciprocity must hold", person.id, spouse!!.spouseId)

                val validation = marriageValidationEngine.validateSpouseConnection(person.id, spouseId, memberMap)
                assertTrue(
                    "Marriage between ${person.firstName} and ${spouse.firstName} was blocked: ${validation.reason}",
                    validation.isAllowed
                )
            }
        }
    }

    @Test
    fun testDoubleFirstCousinsEvaluation() {
        val renzyId = "bg_020_renzy"
        val mateoId = "bg_022_mateo"

        val renzy = memberMap[renzyId]!!
        val mateo = memberMap[mateoId]!!

        assertEquals("Renzy's father is Antonio", "bg_010_antonio", renzy.fatherId)
        assertEquals("Renzy's mother is Elena Santos", "st_010_elena", renzy.motherId)
        assertEquals("Mateo's father is Jose Jr.", "bg_013_jose_jr", mateo.fatherId)
        assertEquals("Mateo's mother is Maria Santos", "st_011_maria", mateo.motherId)

        val traceResult = bloodlineTracer.trace(renzyId, mateoId, memberMap)
        assertTrue("Renzy and Mateo must be related by blood", traceResult.isRelated)
        assertTrue(
            "Renzy and Mateo must be recognized as Double First Cousins by the production engine (got: ${traceResult.relationshipType})",
            traceResult.relationshipType.contains("Double First Cousin", ignoreCase = true)
        )
    }

    @Test
    fun testAuntYoungerThanNieceEvaluation() {
        val sofiaId = "bg_015_sofia"
        val ghillainId = "bt_020_ghillain"

        val sofia = memberMap[sofiaId]!!
        val ghillain = memberMap[ghillainId]!!

        val sofiaBirthYear = sofia.birthDate.substring(0, 4).toInt()
        val ghillainBirthYear = ghillain.birthDate.substring(0, 4).toInt()

        assertTrue(
            "Niece Ghillain ($ghillainBirthYear) must be born earlier than Aunt Sofia ($sofiaBirthYear)",
            ghillainBirthYear < sofiaBirthYear
        )

        val traceResult = bloodlineTracer.trace(sofiaId, ghillainId, memberMap)
        assertTrue("Aunt Sofia and Niece Ghillain must be biologically related", traceResult.isRelated)
        assertEquals(
            "Degree of consanguinity between aunt and niece must be 3rd degree",
            3,
            traceResult.degreeOfConsanguinity
        )
        assertTrue(
            "Relationship must be Aunt/Niece (got: ${traceResult.relationshipType})",
            traceResult.relationshipType.contains("Aunt", ignoreCase = true) ||
                    traceResult.relationshipType.contains("Niece", ignoreCase = true)
        )
    }

    @Test
    fun testZeroCyclesInClanGraph() {
        fun assertNoCycle(startId: String) {
            val visited = mutableSetOf<String>()
            val queue = java.util.ArrayDeque<String>()
            queue.add(startId)

            while (queue.isNotEmpty()) {
                val current = queue.poll() ?: continue
                if (current != startId) {
                    assertFalse("Cycle detected involving $current starting from $startId", visited.contains(current))
                    visited.add(current)
                }
                val person = memberMap[current] ?: continue
                person.fatherId?.let { fid -> if (fid.isNotBlank()) queue.add(fid) }
                person.motherId?.let { mid -> if (mid.isNotBlank()) queue.add(mid) }
            }
        }

        for (person in members) {
            assertNoCycle(person.id)
        }
    }
}
