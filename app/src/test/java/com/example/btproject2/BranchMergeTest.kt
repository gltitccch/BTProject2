package com.example.btproject2

import com.example.btproject2.engine.MergePreviewEngine
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BranchMergeTest {

    private lateinit var mergeEngine: MergePreviewEngine

    @Before
    fun setUp() {
        mergeEngine = MergePreviewEngine()
    }

    @Test
    fun testConflictDetectionAndMatchingFields() {
        val personA = Person(
            id = "p1",
            firstName = "Jose",
            lastName = "Rizal",
            gender = "Male",
            birthDate = "1861-06-19",
            birthPlace = "Calamba, Laguna"
        )
        val personB = Person(
            id = "p2",
            firstName = "Jose",
            lastName = "Rizal",
            gender = "Male",
            birthDate = "1861-06-20", // Conflicting birthdate
            birthPlace = "Calamba"     // Conflicting birthplace
        )

        val preview = mergeEngine.preview(personA, personB, listOf(personA, personB))

        assertTrue(preview.matchingFields.contains("First Name"))
        assertTrue(preview.matchingFields.contains("Last Name"))
        assertTrue(preview.matchingFields.contains("Gender"))

        val conflictFields = preview.conflicts.map { it.fieldName }
        assertTrue(conflictFields.contains("Birth Date"))
        assertTrue(conflictFields.contains("Birth Place"))
    }

    @Test
    fun testAffectedChildrenIdentified() {
        val fatherA = Person(id = "fa1", firstName = "Antonio", lastName = "Luna")
        val fatherB = Person(id = "fa2", firstName = "Antonio", lastName = "Luna")
        val child1 = Person(id = "ch1", firstName = "Juan", fatherId = "fa2")
        val child2 = Person(id = "ch2", firstName = "Maria", fatherId = "fa2")
        val otherChild = Person(id = "ch3", firstName = "Pedro", fatherId = "fa1")

        val all = listOf(fatherA, fatherB, child1, child2, otherChild)
        val preview = mergeEngine.preview(fatherA, fatherB, all)

        assertEquals(2, preview.affectedChildren.size)
        val affectedIds = preview.affectedChildren.map { it.id }
        assertTrue(affectedIds.contains("ch1"))
        assertTrue(affectedIds.contains("ch2"))
        assertFalse(affectedIds.contains("ch3"))
    }

    @Test
    fun testCustomOverrideResolution() {
        val personA = Person(id = "a", firstName = "Carlos", birthDate = "1970-01-01")
        val personB = Person(id = "b", firstName = "Carlos", birthDate = "1972-05-10")

        val overrides = mapOf("Birth Date" to "1972-05-10")
        val merged = mergeEngine.buildCustomMergedPerson(personA, personB, overrides)

        assertEquals("a", merged.id)
        assertEquals("Carlos", merged.firstName)
        assertEquals("1972-05-10", merged.birthDate)
    }

    @Test
    fun testDirectBiologicalCycleDetection() {
        val parent = Person(id = "p", firstName = "Parent")
        val child = Person(id = "c", firstName = "Child", fatherId = "p")
        val map = mapOf("p" to parent, "c" to child)

        // Attempting to make Child the parent of Parent should detect a cycle
        val hasCycle = mergeEngine.detectCycle(proposedParentId = "c", proposedChildId = "p", allPersonsMap = map)
        assertTrue(hasCycle)

        // Valid assignment: Parent as parent of Child should not detect cycle
        val validCycle = mergeEngine.detectCycle(proposedParentId = "p", proposedChildId = "c", allPersonsMap = map)
        assertFalse(validCycle)
    }

    @Test
    fun testMultiGenerationalCycleDetection() {
        val grandParent = Person(id = "gp", firstName = "Grandparent")
        val parent = Person(id = "p", firstName = "Parent", fatherId = "gp")
        val grandChild = Person(id = "gc", firstName = "Grandchild", fatherId = "p")
        val map = mapOf("gp" to grandParent, "p" to parent, "gc" to grandChild)

        // Attempting to make Grandchild the parent of Grandparent must trigger cycle detection
        val hasCycle = mergeEngine.detectCycle(proposedParentId = "gc", proposedChildId = "gp", allPersonsMap = map)
        assertTrue(hasCycle)
    }

    @Test
    fun testNoCycleForUnrelatedLineages() {
        val personA = Person(id = "branchA1", firstName = "Alice")
        val personB = Person(id = "branchB1", firstName = "Bob")
        val map = mapOf("branchA1" to personA, "branchB1" to personB)

        val hasCycle = mergeEngine.detectCycle(proposedParentId = "branchA1", proposedChildId = "branchB1", allPersonsMap = map)
        assertFalse(hasCycle)
    }
}

