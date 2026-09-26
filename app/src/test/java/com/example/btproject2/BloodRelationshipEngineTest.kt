package com.example.btproject2

import com.example.btproject2.engine.BloodRelationshipEngine
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test

class BloodRelationshipEngineTest {

    private val engine = BloodRelationshipEngine()

    // 1. Parent and child are first degree, lineal.
    @Test
    fun testParentAndChildFirstDegreeLineal() {
        val parent = Person(id = "p1", firstName = "Jose", lastName = "Bugarin", gender = "Male")
        val child = Person(id = "c1", firstName = "Clara", lastName = "Bugarin", gender = "Female", fatherId = "p1", fatherRelationshipType = "Biological")
        val map = mapOf("p1" to parent, "c1" to child)

        val resultForward = engine.determineBloodRelationship("p1", "c1", map)
        assertTrue(resultForward.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.LINEAL, resultForward.category)
        assertEquals(1, resultForward.degree)
        assertEquals("Parent and child", resultForward.relationshipLabel)
        assertEquals(listOf("p1", "c1"), resultForward.familyPath.map { it.id })

        val resultReverse = engine.determineBloodRelationship("c1", "p1", map)
        assertTrue(resultReverse.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.LINEAL, resultReverse.category)
        assertEquals(1, resultReverse.degree)
        assertEquals("Parent and child", resultReverse.relationshipLabel)
        assertEquals(listOf("c1", "p1"), resultReverse.familyPath.map { it.id })
    }

    // 2. Grandparent and grandchild are second degree, lineal.
    @Test
    fun testGrandparentAndGrandchildSecondDegreeLineal() {
        val grandfather = Person(id = "gf", firstName = "Lope", lastName = "Reyes")
        val father = Person(id = "f", firstName = "Manuel", lastName = "Reyes", fatherId = "gf", fatherRelationshipType = "Biological")
        val grandchild = Person(id = "gc", firstName = "Ghillain", lastName = "Reyes", fatherId = "f", fatherRelationshipType = "Biological")
        val map = mapOf("gf" to grandfather, "f" to father, "gc" to grandchild)

        val result = engine.determineBloodRelationship("gf", "gc", map)
        assertTrue(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.LINEAL, result.category)
        assertEquals(2, result.degree)
        assertEquals("Grandparent and grandchild", result.relationshipLabel)
        assertEquals(listOf("gf", "f", "gc"), result.familyPath.map { it.id })

        val resultRev = engine.determineBloodRelationship("gc", "gf", map)
        assertEquals(2, resultRev.degree)
        assertEquals(BloodRelationshipEngine.BloodCategory.LINEAL, resultRev.category)
        assertEquals(listOf("gc", "f", "gf"), resultRev.familyPath.map { it.id })
    }

    // 3. Siblings are second degree, collateral.
    @Test
    fun testSiblingsSecondDegreeCollateral() {
        val father = Person(id = "f", firstName = "Jose", lastName = "Bugarin")
        val child1 = Person(id = "s1", firstName = "Clara", lastName = "Bugarin", fatherId = "f", fatherRelationshipType = "Biological")
        val child2 = Person(id = "s2", firstName = "Renzy", lastName = "Bugarin", fatherId = "f", fatherRelationshipType = "Biological")
        val map = mapOf("f" to father, "s1" to child1, "s2" to child2)

        val result = engine.determineBloodRelationship("s1", "s2", map)
        assertTrue(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.COLLATERAL, result.category)
        assertEquals(2, result.degree)
        assertEquals("Siblings", result.relationshipLabel)
        assertEquals("f", result.commonAncestor?.id)
        assertEquals(listOf("s1", "f", "s2"), result.familyPath.map { it.id })
    }

    // 4. Uncle or aunt and niece or nephew are third degree, collateral.
    @Test
    fun testUncleAuntAndNieceNephewThirdDegreeCollateral() {
        val grandfather = Person(id = "gf", firstName = "Jose", lastName = "Bugarin")
        val uncle = Person(id = "u", firstName = "Renzy", lastName = "Bugarin", fatherId = "gf", fatherRelationshipType = "Biological")
        val mother = Person(id = "m", firstName = "Clara", lastName = "Bugarin", fatherId = "gf", fatherRelationshipType = "Biological")
        val niece = Person(id = "n", firstName = "Ghillain", lastName = "Bugarin", motherId = "m", motherRelationshipType = "Biological")

        val map = mapOf("gf" to grandfather, "u" to uncle, "m" to mother, "n" to niece)

        val result = engine.determineBloodRelationship("u", "n", map)
        assertTrue(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.COLLATERAL, result.category)
        assertEquals(3, result.degree)
        assertEquals("Uncle or aunt and niece or nephew", result.relationshipLabel)
        assertEquals("gf", result.commonAncestor?.id)
        assertEquals(1, result.generationsA) // Uncle to grandfather = 1
        assertEquals(2, result.generationsB) // Niece to grandfather = 2
        assertEquals(listOf("u", "gf", "m", "n"), result.familyPath.map { it.id })
    }

    // 5. First cousins are fourth degree, collateral.
    @Test
    fun testFirstCousinsFourthDegreeCollateral() {
        val grandfather = Person(id = "gf", firstName = "Antonio", lastName = "Bugarin")
        val father1 = Person(id = "f1", firstName = "Jose", fatherId = "gf", fatherRelationshipType = "Biological")
        val father2 = Person(id = "f2", firstName = "Pedro", fatherId = "gf", fatherRelationshipType = "Biological")
        val cousin1 = Person(id = "c1", firstName = "Clara", fatherId = "f1", fatherRelationshipType = "Biological")
        val cousin2 = Person(id = "c2", firstName = "Mateo", fatherId = "f2", fatherRelationshipType = "Biological")

        val map = mapOf("gf" to grandfather, "f1" to father1, "f2" to father2, "c1" to cousin1, "c2" to cousin2)

        val result = engine.determineBloodRelationship("c1", "c2", map)
        assertTrue(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.COLLATERAL, result.category)
        assertEquals(4, result.degree)
        assertEquals("First cousins", result.relationshipLabel)
        assertEquals("gf", result.commonAncestor?.id)
        assertEquals(2, result.generationsA)
        assertEquals(2, result.generationsB)
        assertEquals(listOf("c1", "f1", "gf", "f2", "c2"), result.familyPath.map { it.id })
    }

    // 6. First cousin once removed is fifth degree, collateral.
    @Test
    fun testFirstCousinOnceRemovedFifthDegreeCollateral() {
        val grandfather = Person(id = "gf", firstName = "Antonio", lastName = "Bugarin")
        val father1 = Person(id = "f1", firstName = "Jose", fatherId = "gf", fatherRelationshipType = "Biological")
        val father2 = Person(id = "f2", firstName = "Pedro", fatherId = "gf", fatherRelationshipType = "Biological")
        val cousin1 = Person(id = "c1", firstName = "Clara", fatherId = "f1", fatherRelationshipType = "Biological")
        val cousin2 = Person(id = "c2", firstName = "Mateo", fatherId = "f2", fatherRelationshipType = "Biological")
        val childOfCousin2 = Person(id = "c2Child", firstName = "Ghillain", fatherId = "c2", fatherRelationshipType = "Biological")

        val map = mapOf(
            "gf" to grandfather,
            "f1" to father1, "f2" to father2,
            "c1" to cousin1, "c2" to cousin2,
            "c2Child" to childOfCousin2
        )

        val result = engine.determineBloodRelationship("c1", "c2Child", map)
        assertTrue(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.COLLATERAL, result.category)
        assertEquals(5, result.degree)
        assertEquals("First cousin once removed", result.relationshipLabel)
        assertEquals("gf", result.commonAncestor?.id)
        assertEquals(2, result.generationsA)
        assertEquals(3, result.generationsB)
        assertEquals(listOf("c1", "f1", "gf", "f2", "c2", "c2Child"), result.familyPath.map { it.id })
    }

    // 7. Second cousins are sixth degree, collateral.
    @Test
    fun testSecondCousinsSixthDegreeCollateral() {
        val greatGrandfather = Person(id = "ggf", firstName = "Emilio", lastName = "Bugarin")
        val grandfather1 = Person(id = "gf1", fatherId = "ggf", fatherRelationshipType = "Biological")
        val grandfather2 = Person(id = "gf2", fatherId = "ggf", fatherRelationshipType = "Biological")
        val parent1 = Person(id = "p1", fatherId = "gf1", fatherRelationshipType = "Biological")
        val parent2 = Person(id = "p2", fatherId = "gf2", fatherRelationshipType = "Biological")
        val secondCousin1 = Person(id = "sc1", firstName = "Gabriel", fatherId = "p1", fatherRelationshipType = "Biological")
        val secondCousin2 = Person(id = "sc2", firstName = "Isabella", fatherId = "p2", fatherRelationshipType = "Biological")

        val map = mapOf(
            "ggf" to greatGrandfather,
            "gf1" to grandfather1, "gf2" to grandfather2,
            "p1" to parent1, "p2" to parent2,
            "sc1" to secondCousin1, "sc2" to secondCousin2
        )

        val result = engine.determineBloodRelationship("sc1", "sc2", map)
        assertTrue(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.COLLATERAL, result.category)
        assertEquals(6, result.degree)
        assertEquals("Second cousins", result.relationshipLabel)
        assertEquals("ggf", result.commonAncestor?.id)
        assertEquals(3, result.generationsA)
        assertEquals(3, result.generationsB)
        assertEquals(listOf("sc1", "p1", "gf1", "ggf", "gf2", "p2", "sc2"), result.familyPath.map { it.id })
    }

    // 8. Spouses have NO blood relationship.
    @Test
    fun testSpousesNoBloodRelationship() {
        val husband = Person(id = "h1", firstName = "Danilo", spouseId = "w1")
        val wife = Person(id = "w1", firstName = "Corazon", spouseId = "h1")
        val map = mapOf("h1" to husband, "w1" to wife)

        val result = engine.determineBloodRelationship("h1", "w1", map)
        assertFalse(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.NO_BLOOD_RELATIONSHIP, result.category)
        assertEquals(0, result.degree)
        assertEquals("No blood relationship", result.relationshipLabel)
        assertTrue(result.familyPath.isEmpty())
        assertNull(result.commonAncestor)
    }

    // 9. Adoptive parent and child have NO blood relationship.
    @Test
    fun testAdoptiveParentAndChildNoBloodRelationship() {
        val adopter = Person(id = "a1", firstName = "Eduardo", lastName = "Castro")
        val adoptee = Person(
            id = "c1", firstName = "Miguel", lastName = "Castro",
            fatherId = "a1", fatherRelationshipType = "Adoptive"
        )
        val map = mapOf("a1" to adopter, "c1" to adoptee)

        val result = engine.determineBloodRelationship("a1", "c1", map)
        assertFalse(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.NO_BLOOD_RELATIONSHIP, result.category)
        assertEquals(0, result.degree)
        assertEquals("No blood relationship", result.relationshipLabel)
        assertTrue(result.familyPath.isEmpty())
    }

    // 10. Adoptive siblings have NO blood relationship.
    @Test
    fun testAdoptiveSiblingsNoBloodRelationship() {
        val adopter = Person(id = "ad1", firstName = "Vicente")
        val child1 = Person(id = "ac1", firstName = "Rafael", fatherId = "ad1", fatherRelationshipType = "Adoptive")
        val child2 = Person(id = "ac2", firstName = "Sofia", fatherId = "ad1", fatherRelationshipType = "Adoptive")
        val map = mapOf("ad1" to adopter, "ac1" to child1, "ac2" to child2)

        val result = engine.determineBloodRelationship("ac1", "ac2", map)
        assertFalse(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.NO_BLOOD_RELATIONSHIP, result.category)
        assertEquals(0, result.degree)
        assertEquals("No blood relationship", result.relationshipLabel)
        assertTrue(result.familyPath.isEmpty())
    }

    // 11. Stepfamily and In-Laws have NO blood relationship.
    @Test
    fun testStepfamilyAndInLawsNoBloodRelationship() {
        val stepParent = Person(id = "sp1", firstName = "Helena", lastName = "Tan")
        val stepChild = Person(
            id = "sc1", firstName = "Lorenzo", lastName = "Tan",
            motherId = "sp1", motherRelationshipType = "Step"
        )
        val map = mapOf("sp1" to stepParent, "sc1" to stepChild)

        val result = engine.determineBloodRelationship("sp1", "sc1", map)
        assertFalse(result.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.NO_BLOOD_RELATIONSHIP, result.category)
        assertEquals(0, result.degree)
        assertEquals("No blood relationship", result.relationshipLabel)
        assertTrue(result.familyPath.isEmpty())
    }

    // 12. Automatic Discovery of Derived Relationships (without storing them directly)
    @Test
    fun testAutomaticDiscoveryOfDerivedRelationships() {
        // Setup family network:
        // Parents: Jose (father) & Maria (mother)
        // Children: Clara, Renzy
        // Clara marries Pedro
        // Clara & Pedro have child Ghillain
        // Pedro's parents: Fernando & Lucia
        val jose = Person(id = "jose", firstName = "Jose")
        val maria = Person(id = "maria", firstName = "Maria")
        val clara = Person(id = "clara", firstName = "Clara", fatherId = "jose", motherId = "maria", spouseId = "pedro")
        val renzy = Person(id = "renzy", firstName = "Renzy", fatherId = "jose", motherId = "maria")
        val fernando = Person(id = "fernando", firstName = "Fernando")
        val lucia = Person(id = "lucia", firstName = "Lucia")
        val pedro = Person(id = "pedro", firstName = "Pedro", fatherId = "fernando", motherId = "lucia", spouseId = "clara")
        val ghillain = Person(id = "ghillain", firstName = "Ghillain", fatherId = "pedro", motherId = "clara")

        val map = mapOf(
            "jose" to jose, "maria" to maria,
            "clara" to clara, "renzy" to renzy,
            "fernando" to fernando, "lucia" to lucia,
            "pedro" to pedro, "ghillain" to ghillain
        )

        // Sibling discovery (Clara & Renzy)
        val claraSiblings = engine.findSiblings("clara", map)
        assertEquals(1, claraSiblings.size)
        assertEquals("renzy", claraSiblings[0].id)

        // Grandparent discovery (Ghillain's maternal grandparents are Jose & Maria)
        val ghillainGrandparents = engine.findGrandparents("ghillain", map)
        val gpIds = ghillainGrandparents.map { it.id }.toSet()
        assertEquals(setOf("jose", "maria", "fernando", "lucia"), gpIds)

        // Grandchildren discovery (Jose's grandchild is Ghillain)
        val joseGrandchildren = engine.findGrandchildren("jose", map)
        assertEquals(1, joseGrandchildren.size)
        assertEquals("ghillain", joseGrandchildren[0].id)

        // Uncle/Aunt discovery (Renzy is Ghillain's uncle)
        val ghillainUnclesAunts = engine.findUnclesAndAunts("ghillain", map)
        assertEquals(1, ghillainUnclesAunts.size)
        assertEquals("renzy", ghillainUnclesAunts[0].id)

        // Niece/Nephew discovery (Ghillain is Renzy's niece)
        val renzyNieces = engine.findNiecesAndNephews("renzy", map)
        assertEquals(1, renzyNieces.size)
        assertEquals("ghillain", renzyNieces[0].id)

        // Parent-in-law discovery (Jose & Maria are Pedro's parents-in-law)
        val pedroParentsInLaw = engine.findParentsInLaw("pedro", map)
        assertEquals(setOf("jose", "maria"), pedroParentsInLaw.map { it.id }.toSet())

        // Child-in-law discovery (Pedro is Jose's child-in-law)
        val joseChildrenInLaw = engine.findChildrenInLaw("jose", map)
        assertEquals(1, joseChildrenInLaw.size)
        assertEquals("pedro", joseChildrenInLaw[0].id)

        // Sibling-in-law discovery (Renzy is Pedro's sibling-in-law)
        val pedroSiblingsInLaw = engine.findSiblingsInLaw("pedro", map)
        assertEquals(1, pedroSiblingsInLaw.size)
        assertEquals("renzy", pedroSiblingsInLaw[0].id)
    }
}

