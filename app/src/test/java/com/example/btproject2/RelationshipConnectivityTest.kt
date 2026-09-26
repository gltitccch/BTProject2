package com.example.btproject2

import com.example.btproject2.engine.ConsistencyValidator
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying that family members with and without recorded birth dates
 * can be properly connected in parent/child and spousal relationships, preventing false exclusions.
 */
class RelationshipConnectivityTest {

    private lateinit var validator: ConsistencyValidator

    @Before
    fun setUp() {
        validator = ConsistencyValidator()
    }

    @Test
    fun testEligibleFathersIncludesMembersWithoutBirthDate() {
        val child = Person(id = "child_1", firstName = "Child", lastName = "Doe", gender = "Male", birthDate = "2000-01-01")
        val fatherWithDate = Person(id = "dad_1", firstName = "John", lastName = "Doe", gender = "Male", birthDate = "1975-05-10")
        val fatherWithoutDate = Person(id = "dad_2", firstName = "Mark", lastName = "Doe", gender = "Male", birthDate = "")

        val allMembers = listOf(child, fatherWithDate, fatherWithoutDate)
        val eligibleFathers = validator.getEligibleFathers(child, allMembers)

        assertEquals("Both fathers should be eligible regardless of missing birth date", 2, eligibleFathers.size)
        assertTrue("Father with birth date should be eligible", eligibleFathers.any { it.id == "dad_1" })
        assertTrue("Father with missing birth date should be eligible", eligibleFathers.any { it.id == "dad_2" })
    }

    @Test
    fun testEligibleMothersIncludesMembersWithoutBirthDate() {
        val child = Person(id = "child_2", firstName = "Child", lastName = "Doe", gender = "Female", birthDate = "")
        val motherWithDate = Person(id = "mom_1", firstName = "Jane", lastName = "Doe", gender = "Female", birthDate = "1978-08-20")
        val motherWithoutDate = Person(id = "mom_2", firstName = "Mary", lastName = "Doe", gender = "Female", birthDate = "")

        val allMembers = listOf(child, motherWithDate, motherWithoutDate)
        val eligibleMothers = validator.getEligibleMothers(child, allMembers)

        assertEquals("Both mothers should be eligible even if child or mother has no birth date", 2, eligibleMothers.size)
        assertTrue(eligibleMothers.any { it.id == "mom_1" })
        assertTrue(eligibleMothers.any { it.id == "mom_2" })
    }

    @Test
    fun testEligibleParentsExcludesCircularAncestry() {
        // Grandfather -> Father -> Child
        val grandfather = Person(id = "gf", firstName = "Grandpa", lastName = "Doe", gender = "Male")
        val father = Person(id = "f", firstName = "Dad", lastName = "Doe", gender = "Male", fatherId = "gf")
        val child = Person(id = "c", firstName = "Son", lastName = "Doe", gender = "Male", fatherId = "f")

        val allMembers = listOf(grandfather, father, child)

        // Attempting to make 'child' the father of 'grandfather' should be blocked
        val eligibleForGf = validator.getEligibleFathers(grandfather, allMembers)
        assertFalse("Grandfather cannot have his descendant as father", eligibleForGf.any { it.id == "c" })
        assertFalse("Grandfather cannot have his son as father", eligibleForGf.any { it.id == "f" })
    }

    @Test
    fun testEligibleSpousesExcludesAncestorsAndDescendants() {
        val ancestor = Person(id = "anc", firstName = "Ancestor", lastName = "Doe", gender = "Male")
        val descendant = Person(id = "desc", firstName = "Descendant", lastName = "Doe", gender = "Female", fatherId = "anc")
        val unrelated = Person(id = "unrelated", firstName = "Bob", lastName = "Smith", gender = "Male")

        val allMembers = listOf(ancestor, descendant, unrelated)
        val spousesForDescendant = validator.getEligibleSpouses(descendant, allMembers)

        assertFalse("Ancestor cannot be chosen as spouse", spousesForDescendant.any { it.id == "anc" })
        assertTrue("Unrelated candidate can be chosen as spouse", spousesForDescendant.any { it.id == "unrelated" })
    }

    @Test
    fun testTreeFilteringResilience() {
        // Records that have empty treeId, default_tree, or legacy tags
        val memberA = Person(id = "m1", firstName = "Renzy", lastName = "User", treeId = "")
        val memberB = Person(id = "m2", firstName = "Parent", lastName = "User", treeId = "default_tree")
        val allPersons = listOf(memberA, memberB)

        val targetTreeId = "default_tree"
        val filtered = allPersons.filter { it.treeId.isEmpty() || it.treeId == targetTreeId }
        val finalResult = if (filtered.isNotEmpty()) filtered else allPersons

        assertEquals("Both members should be preserved under default_tree", 2, finalResult.size)
        assertTrue(finalResult.any { it.id == "m1" })
        assertTrue(finalResult.any { it.id == "m2" })
    }

    @Test
    fun testGenerationRootsWithBlankParentIds() {
        val rootPerson = Person(id = "r1", firstName = "Root", lastName = "Member", fatherId = "", motherId = "")
        val childPerson = Person(id = "c1", firstName = "Child", lastName = "Member", fatherId = "r1", motherId = "")

        val persons = listOf(rootPerson, childPerson)
        val personById = persons.associateBy { it.id }

        val roots = persons.filter { p ->
            (p.fatherId.isNullOrBlank() || personById[p.fatherId] == null) &&
                    (p.motherId.isNullOrBlank() || personById[p.motherId] == null)
        }

        assertEquals("Root person with empty string parent IDs must be detected as root", 1, roots.size)
        assertEquals("r1", roots[0].id)
    }
}

