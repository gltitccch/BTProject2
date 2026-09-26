package com.example.btproject2

import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test

class RelationshipAndLegalTest {

    private val tracer = BloodlineTracer()

    @Test
    fun testParentChildConsanguinityAndArticle37() {
        val parent = Person(id = "p1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male")
        val child = Person(id = "c1", firstName = "Pedro", lastName = "Dela Cruz", gender = "Male", fatherId = "p1")
        val map = mapOf("p1" to parent, "c1" to child)

        val result = tracer.trace("p1", "c1", map)

        assertTrue(result.isRelated)
        assertEquals(1, result.degreeOfConsanguinity)
        assertEquals("Lineal", result.relationshipCategory)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void ab initio (Incestuous)", result.legalStatus)
        assertTrue(result.legalArticle.contains("37"))
    }

    @Test
    fun testSiblingsConsanguinityAndArticle37() {
        val parent = Person(id = "p1", firstName = "Juan", lastName = "Santos")
        val child1 = Person(id = "s1", firstName = "Carlos", lastName = "Santos", fatherId = "p1")
        val child2 = Person(id = "s2", firstName = "Maria", lastName = "Santos", fatherId = "p1")
        val map = mapOf("p1" to parent, "s1" to child1, "s2" to child2)

        val result = tracer.trace("s1", "s2", map)

        assertTrue(result.isRelated)
        assertEquals(2, result.degreeOfConsanguinity)
        assertEquals("Collateral", result.relationshipCategory)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void ab initio (Incestuous)", result.legalStatus)
        assertTrue(result.legalArticle.contains("37"))
    }

    @Test
    fun testFirstCousinsConsanguinityAndArticle38() {
        val grandfather = Person(id = "gf", firstName = "Antonio", lastName = "Reyes")
        val father1 = Person(id = "f1", firstName = "Jose", fatherId = "gf")
        val father2 = Person(id = "f2", firstName = "Manuel", fatherId = "gf")
        val cousin1 = Person(id = "c1", firstName = "Ana", fatherId = "f1")
        val cousin2 = Person(id = "c2", firstName = "Luis", fatherId = "f2")

        val map = mapOf(
            "gf" to grandfather,
            "f1" to father1, "f2" to father2,
            "c1" to cousin1, "c2" to cousin2
        )

        val result = tracer.trace("c1", "c2", map)

        assertTrue(result.isRelated)
        assertEquals(4, result.degreeOfConsanguinity)
        assertEquals("First Cousins", result.relationshipType)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
    }

    @Test
    fun testSecondCousinsPermissibleUnderPhilippineLaw() {
        // G1: Great-grandfather
        val ggf = Person(id = "ggf", firstName = "Emilio")
        // G2: Grandfathers
        val gf1 = Person(id = "gf1", fatherId = "ggf")
        val gf2 = Person(id = "gf2", fatherId = "ggf")
        // G3: Parents (1st cousins)
        val p1 = Person(id = "p1", fatherId = "gf1")
        val p2 = Person(id = "p2", fatherId = "gf2")
        // G4: 2nd cousins
        val sc1 = Person(id = "sc1", firstName = "Gabriel", fatherId = "p1")
        val sc2 = Person(id = "sc2", firstName = "Isabella", fatherId = "p2")

        val map = mapOf(
            "ggf" to ggf,
            "gf1" to gf1, "gf2" to gf2,
            "p1" to p1, "p2" to p2,
            "sc1" to sc1, "sc2" to sc2
        )

        val result = tracer.trace("sc1", "sc2", map)

        assertTrue(result.isRelated)
        assertEquals(6, result.degreeOfConsanguinity)
        assertEquals("Second Cousins", result.relationshipType)
        assertFalse(result.isMarriageProhibited)
        assertEquals("Permissible under Philippine Law", result.legalStatus)
        assertTrue(result.legalArticle.contains("Beyond 4th Degree"))
    }

    @Test
    fun testAdoptiveParentChildArticle38Paragraph4() {
        val adopter = Person(id = "a1", firstName = "Eduardo", lastName = "Castro")
        val adoptee = Person(
            id = "c1", firstName = "Miguel", lastName = "Castro",
            fatherId = "a1", fatherRelationshipType = "Adoptive"
        )
        val map = mapOf("a1" to adopter, "c1" to adoptee)

        val result = tracer.trace("a1", "c1", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
        assertTrue(result.legalDescription.contains("adopting parent and the adopted child"))
    }

    @Test
    fun testStepParentChildArticle38Paragraph2() {
        val stepParent = Person(id = "sp1", firstName = "Helena", lastName = "Tan")
        val stepChild = Person(
            id = "sc1", firstName = "Lorenzo", lastName = "Tan",
            motherId = "sp1", motherRelationshipType = "Step"
        )
        val map = mapOf("sp1" to stepParent, "sc1" to stepChild)

        val result = tracer.trace("sp1", "sc1", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
        assertTrue(result.legalDescription.contains("step-parents and step-children"))
    }

    @Test
    fun testAdoptiveSiblingsArticle38Paragraph8() {
        val adopter = Person(id = "ad1", firstName = "Vicente")
        val child1 = Person(id = "ac1", firstName = "Rafael", fatherId = "ad1", fatherRelationshipType = "Adoptive")
        val child2 = Person(id = "ac2", firstName = "Sofia", fatherId = "ad1", fatherRelationshipType = "Adoptive")
        val map = mapOf("ad1" to adopter, "ac1" to child1, "ac2" to child2)

        val result = tracer.trace("ac1", "ac2", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
    }

    @Test
    fun testExistingSpousesBigamyProhibition() {
        val husband = Person(id = "h1", firstName = "Danilo", spouseId = "w1")
        val wife = Person(id = "w1", firstName = "Corazon", spouseId = "h1")
        val map = mapOf("h1" to husband, "w1" to wife)

        val result = tracer.trace("h1", "w1", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Existing Marriage)", result.legalStatus)
        assertTrue(result.legalArticle.contains("35"))
    }
}

