package com.example.btproject2

import com.example.btproject2.engine.LinealConsanguinityCalculator
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test

class LinealConsanguinityTest {

    private val calculator = LinealConsanguinityCalculator()

    @Test
    fun test1stDegreeParentsAndChildren() {
        val father = Person(id = "p_father", firstName = "Antonio", lastName = "Reyes", gender = "Male")
        val mother = Person(id = "p_mother", firstName = "Elena", lastName = "Reyes", gender = "Female")
        val child1 = Person(id = "c_1", firstName = "Jose", lastName = "Reyes", gender = "Male", fatherId = "p_father", motherId = "p_mother")
        val child2 = Person(id = "c_2", firstName = "Maria", lastName = "Reyes", gender = "Female", fatherId = "p_father", motherId = "p_mother")

        val tree = listOf(father, mother, child1, child2)

        // Test from father's perspective
        val fatherReport = calculator.calculate(father, tree)
        assertEquals(0, fatherReport.degree1.ascendants.size)
        assertEquals(2, fatherReport.degree1.descendants.size)
        assertTrue(fatherReport.degree1.descendants.any { it.id == "c_1" })
        assertTrue(fatherReport.degree1.descendants.any { it.id == "c_2" })

        // Test from child's perspective
        val childReport = calculator.calculate(child1, tree)
        assertEquals(2, childReport.degree1.ascendants.size)
        assertEquals(0, childReport.degree1.descendants.size)
        assertTrue(childReport.degree1.ascendants.any { it.id == "p_father" })
        assertTrue(childReport.degree1.ascendants.any { it.id == "p_mother" })

        // Test degree helper
        assertEquals(1, calculator.getLinealDegree(father, child1, tree))
        assertEquals(1, calculator.getLinealDegree(child1, mother, tree))
    }

    @Test
    fun test2ndDegreeGrandparentsAndGrandchildren() {
        val grandfather = Person(id = "ggp0", firstName = "Vicente", lastName = "Santos", gender = "Male")
        val grandmother = Person(id = "ggm0", firstName = "Clara", lastName = "Santos", gender = "Female")
        val father = Person(id = "f0", firstName = "Manuel", lastName = "Santos", gender = "Male", fatherId = "ggp0", motherId = "ggm0")
        val grandchild1 = Person(id = "gc1", firstName = "Danilo", lastName = "Santos", gender = "Male", fatherId = "f0")
        val grandchild2 = Person(id = "gc2", firstName = "Luz", lastName = "Santos", gender = "Female", fatherId = "f0")

        val tree = listOf(grandfather, grandmother, father, grandchild1, grandchild2)

        val gfReport = calculator.calculate(grandfather, tree)
        assertEquals(1, gfReport.degree1.descendants.size) // father
        assertEquals(2, gfReport.degree2.descendants.size) // 2 grandchildren
        assertTrue(gfReport.degree2.descendants.any { it.id == "gc1" })
        assertTrue(gfReport.degree2.descendants.any { it.id == "gc2" })

        val gcReport = calculator.calculate(grandchild1, tree)
        assertEquals(1, gcReport.degree1.ascendants.size) // father
        assertEquals(2, gcReport.degree2.ascendants.size) // grandfather & grandmother
        assertTrue(gcReport.degree2.ascendants.any { it.id == "ggp0" })
        assertTrue(gcReport.degree2.ascendants.any { it.id == "ggm0" })

        assertEquals(2, calculator.getLinealDegree(grandfather, grandchild1, tree))
        assertEquals(2, calculator.getLinealDegree(grandchild2, grandmother, tree))
    }

    @Test
    fun test3rdDegreeGreatGrandparentsAndGreatGrandchildren() {
        val greatGrandpa = Person(id = "gen1", firstName = "G1_Pat", lastName = "Cruz", gender = "Male")
        val grandpa = Person(id = "gen2", firstName = "G2_Pat", lastName = "Cruz", gender = "Male", fatherId = "gen1")
        val parent = Person(id = "gen3", firstName = "G3_Pat", lastName = "Cruz", gender = "Male", fatherId = "gen2")
        val child = Person(id = "gen4", firstName = "G4_Pat", lastName = "Cruz", gender = "Male", fatherId = "gen3")

        val tree = listOf(greatGrandpa, grandpa, parent, child)

        val reportFromTop = calculator.calculate(greatGrandpa, tree)
        assertEquals(1, reportFromTop.degree1.descendants.size) // child (grandpa)
        assertEquals(1, reportFromTop.degree2.descendants.size) // grandchild (parent)
        assertEquals(1, reportFromTop.degree3.descendants.size) // great-grandchild (child)
        assertEquals("gen4", reportFromTop.degree3.descendants.first().id)

        val reportFromBottom = calculator.calculate(child, tree)
        assertEquals(1, reportFromBottom.degree1.ascendants.size) // parent
        assertEquals(1, reportFromBottom.degree2.ascendants.size) // grandpa
        assertEquals(1, reportFromBottom.degree3.ascendants.size) // great-grandpa
        assertEquals("gen1", reportFromBottom.degree3.ascendants.first().id)

        assertEquals(3, calculator.getLinealDegree(greatGrandpa, child, tree))
        assertEquals(3, calculator.getLinealDegree(child, greatGrandpa, tree))
    }

    @Test
    fun test4thDegreeGreatGreatGrandparentsAndGreatGreatGrandchildren() {
        val ggGrandpa = Person(id = "gen1", firstName = "Ancestor", lastName = "Dela Cruz", gender = "Male")
        val gGrandpa = Person(id = "gen2", firstName = "GreatGrand", lastName = "Dela Cruz", gender = "Male", fatherId = "gen1")
        val grandpa = Person(id = "gen3", firstName = "Grandpa", lastName = "Dela Cruz", gender = "Male", fatherId = "gen2")
        val parent = Person(id = "gen4", firstName = "Parent", lastName = "Dela Cruz", gender = "Male", fatherId = "gen3")
        val child = Person(id = "gen5", firstName = "Descendant", lastName = "Dela Cruz", gender = "Female", fatherId = "gen4")

        val tree = listOf(ggGrandpa, gGrandpa, grandpa, parent, child)

        val topReport = calculator.calculate(ggGrandpa, tree)
        assertEquals(1, topReport.degree4.descendants.size)
        assertEquals("gen5", topReport.degree4.descendants.first().id)
        assertEquals("4°", topReport.degree4.degreeLabel)
        assertEquals("Great-Great-Grandparents", topReport.degree4.ascendantTitle)
        assertEquals("Great-Great-Grandchildren", topReport.degree4.descendantTitle)

        val bottomReport = calculator.calculate(child, tree)
        assertEquals(1, bottomReport.degree4.ascendants.size)
        assertEquals("gen1", bottomReport.degree4.ascendants.first().id)

        assertEquals(4, calculator.getLinealDegree(ggGrandpa, child, tree))
        assertEquals(4, calculator.getLinealDegree(child, ggGrandpa, tree))
    }

    @Test
    fun testArticle37ProhibitionsApplyToAllLinealDegrees() {
        val ggGrandpa = Person(id = "gen1", firstName = "Ancestor", lastName = "Dela Cruz", gender = "Male")
        val gGrandpa = Person(id = "gen2", firstName = "GreatGrand", lastName = "Dela Cruz", gender = "Male", fatherId = "gen1")
        val grandpa = Person(id = "gen3", firstName = "Grandpa", lastName = "Dela Cruz", gender = "Male", fatherId = "gen2")
        val parent = Person(id = "gen4", firstName = "Parent", lastName = "Dela Cruz", gender = "Male", fatherId = "gen3")
        val child = Person(id = "gen5", firstName = "Descendant", lastName = "Dela Cruz", gender = "Female", fatherId = "gen4")

        val tree = listOf(ggGrandpa, gGrandpa, grandpa, parent, child)
        val report = calculator.calculate(parent, tree)

        assertTrue(report.legalArticle.contains("Article 37"))
        assertTrue(report.legalStatus.contains("Void ab initio"))
        assertTrue(report.legalDescription.contains("Marriage between ascendants and descendants"))
        assertEquals(4, report.degrees.size)
    }

    @Test
    fun testDisconnectedPersonHasEmptyLinealReport() {
        val soloPerson = Person(id = "solo", firstName = "Solo", lastName = "Person", gender = "Male")
        val otherPerson = Person(id = "other", firstName = "Other", lastName = "Person", gender = "Female")

        val report = calculator.calculate(soloPerson, listOf(soloPerson, otherPerson))
        assertTrue(report.degree1.isEmpty)
        assertTrue(report.degree2.isEmpty)
        assertTrue(report.degree3.isEmpty)
        assertTrue(report.degree4.isEmpty)
        assertFalse(report.hasAnyRelatives)
        assertEquals(0, report.totalDirectRelatives)
        assertNull(calculator.getLinealDegree(soloPerson, otherPerson, listOf(soloPerson, otherPerson)))
    }
}

