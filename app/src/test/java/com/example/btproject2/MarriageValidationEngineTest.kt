package com.example.btproject2

import com.example.btproject2.engine.BloodRelationshipEngine
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Authoritative unit tests for MarriageValidationEngine.
 *
 * Verifies all 8 Philippine family rules:
 * 1. Lineal blood relatives of any degree prohibited (Art. 37(1))
 * 2. Full or half siblings prohibited (Art. 37(2))
 * 3. Collateral blood relatives up to 4th civil degree prohibited (Art. 38(1))
 * 4. Collateral blood relatives from 5th civil degree onward allowed with civil explanation (Arts. 37 & 38)
 * 5. Biological parent and spouse conflict prohibited (Rule 5)
 * 6. Self-relationship prohibited (Rule 6)
 * 7. Impossible ancestry cycles prohibited (Rule 7)
 * 8. Son-in-law or daughter-in-law cannot be added as biological child of parent-in-law (Rule 8)
 *
 * Explicitly tests:
 * - Siblings linked as spouses
 * - Parent and child linked as spouses
 * - First cousins linked as spouses
 * - Son-in-law incorrectly added as a child
 * - Two unrelated people linked as spouses
 */
class MarriageValidationEngineTest {

    private lateinit var engine: MarriageValidationEngine

    @Before
    fun setUp() {
        engine = MarriageValidationEngine(BloodRelationshipEngine())
    }

    // =========================================================================
    // TEST 1: SIBLINGS LINKED AS SPOUSES (RULE 2 - ART. 37(2))
    // =========================================================================

    @Test
    fun testSiblingsLinkedAsSpouses_prohibited() {
        // Shared biological parents: Jose and Maria
        val father = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male")
        val mother = Person(id = "p_maria", firstName = "Maria", lastName = "Santos", gender = "Female")

        val siblingA = Person(
            id = "p_renzy",
            firstName = "Renzy",
            lastName = "Bugarin",
            gender = "Male",
            fatherId = "p_jose",
            motherId = "p_maria",
            fatherRelationshipType = "Biological",
            motherRelationshipType = "Biological"
        )
        val siblingB = Person(
            id = "p_clara",
            firstName = "Clara",
            lastName = "Bugarin",
            gender = "Female",
            fatherId = "p_jose",
            motherId = "p_maria",
            fatherRelationshipType = "Biological",
            motherRelationshipType = "Biological"
        )

        val map = mapOf(
            father.id to father,
            mother.id to mother,
            siblingA.id to siblingA,
            siblingB.id to siblingB
        )

        val result = engine.validateSpouseConnection("p_renzy", "p_clara", map)

        assertFalse("Siblings must NOT be allowed to marry", result.isAllowed)
        assertEquals("Degree of consanguinity for siblings must be 2", 2, result.degree)
        assertTrue("Legal basis must cite Article 37 (2)", result.legalBasis.contains("Article 37 (2)"))
        assertTrue("Relationship type must identify Full Siblings", result.relationshipType.contains("Full Siblings"))
        assertTrue("Explanation must mention incestuous and void ab initio", result.reason.contains("void from the beginning"))
        assertEquals("Family path must connect Sibling A, Common Parent, and Sibling B", 3, result.familyPath.size)
    }

    @Test
    fun testHalfSiblingsLinkedAsSpouses_prohibited() {
        // Shared biological father only
        val father = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male")
        val mother1 = Person(id = "p_maria", firstName = "Maria", lastName = "Santos", gender = "Female")
        val mother2 = Person(id = "p_elena", firstName = "Elena", lastName = "Cruz", gender = "Female")

        val halfA = Person(
            id = "p_juan",
            firstName = "Juan",
            lastName = "Bugarin",
            gender = "Male",
            fatherId = "p_jose",
            motherId = "p_maria",
            fatherRelationshipType = "Biological",
            motherRelationshipType = "Biological"
        )
        val halfB = Person(
            id = "p_ana",
            firstName = "Ana",
            lastName = "Bugarin",
            gender = "Female",
            fatherId = "p_jose",
            motherId = "p_elena",
            fatherRelationshipType = "Biological",
            motherRelationshipType = "Biological"
        )

        val map = mapOf(
            father.id to father,
            mother1.id to mother1,
            mother2.id to mother2,
            halfA.id to halfA,
            halfB.id to halfB
        )

        val result = engine.validateSpouseConnection("p_juan", "p_ana", map)

        assertFalse("Half-siblings must NOT be allowed to marry", result.isAllowed)
        assertEquals(2, result.degree)
        assertTrue("Legal basis must cite Article 37 (2)", result.legalBasis.contains("Article 37 (2)"))
        assertTrue("Relationship type must identify Half Siblings", result.relationshipType.contains("Half Siblings"))
    }

    // =========================================================================
    // TEST 2: PARENT AND CHILD LINKED AS SPOUSES (RULE 1 & RULE 5 - ART. 37(1))
    // =========================================================================

    @Test
    fun testParentAndChildLinkedAsSpouses_prohibited() {
        val parent = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin", gender = "Male")
        val child = Person(
            id = "p_clara",
            firstName = "Clara",
            lastName = "Bugarin",
            gender = "Female",
            fatherId = "p_pedro",
            fatherRelationshipType = "Biological"
        )

        val map = mapOf(parent.id to parent, child.id to child)

        val result = engine.validateSpouseConnection("p_pedro", "p_clara", map)

        assertFalse("Parent and child must NOT be allowed to marry", result.isAllowed)
        assertEquals("Parent and child are 1st degree lineal", 1, result.degree)
        assertTrue("Legal basis must cite Article 37 (1)", result.legalBasis.contains("Article 37 (1)"))
        assertTrue("Reason must state biological parent cannot marry child", result.reason.contains("biological parent"))
        assertEquals("Family path must contain parent and child", 2, result.familyPath.size)
    }

    // =========================================================================
    // TEST 3: FIRST COUSINS LINKED AS SPOUSES (RULE 3 - ART. 38(1))
    // =========================================================================

    @Test
    fun testFirstCousinsLinkedAsSpouses_prohibited() {
        // Grandfather
        val grandfather = Person(id = "p_gf", firstName = "Antonio", lastName = "Bugarin", gender = "Male")

        // Siblings (children of grandfather)
        val parent1 = Person(id = "p_p1", firstName = "Jose", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val parent2 = Person(id = "p_p2", firstName = "Ramon", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")

        // First cousins
        val cousin1 = Person(id = "p_c1", firstName = "Renzy", lastName = "Bugarin", fatherId = "p_p1", fatherRelationshipType = "Biological")
        val cousin2 = Person(id = "p_c2", firstName = "Ghillain", lastName = "Bugarin", fatherId = "p_p2", fatherRelationshipType = "Biological")

        val map = mapOf(
            grandfather.id to grandfather,
            parent1.id to parent1,
            parent2.id to parent2,
            cousin1.id to cousin1,
            cousin2.id to cousin2
        )

        val result = engine.validateSpouseConnection("p_c1", "p_c2", map)

        assertFalse("First cousins must NOT be allowed to marry", result.isAllowed)
        assertEquals("First cousins are 4th degree collateral", 4, result.degree)
        assertTrue("Legal basis must cite Article 38 (1)", result.legalBasis.contains("Article 38 (1)"))
        assertTrue("Relationship type must identify First Cousins", result.relationshipType.contains("First Cousins"))
        assertTrue("Reason must mention public policy", result.reason.contains("public policy"))
        assertEquals("Family path must be [Cousin1, Parent1, Grandfather, Parent2, Cousin2]", 5, result.familyPath.size)
    }

    // =========================================================================
    // TEST 4: SON-IN-LAW INCORRECTLY ADDED AS A CHILD (RULE 8 - ART. 38(4))
    // =========================================================================

    @Test
    fun testSonInLawIncorrectlyAddedAsChild_prohibited() {
        val parent = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin", gender = "Male")
        val daughter = Person(
            id = "p_clara",
            firstName = "Clara",
            lastName = "Bugarin",
            gender = "Female",
            fatherId = "p_pedro",
            fatherRelationshipType = "Biological"
        )
        // Son-in-law is married to daughter
        val sonInLaw = Person(
            id = "p_jose",
            firstName = "Jose",
            lastName = "Santos",
            gender = "Male",
            spouseId = "p_clara"
        )
        // Cross link spouse
        val daughterWithSpouse = daughter.copy(spouseId = "p_jose")

        val map = mapOf(
            parent.id to parent,
            daughterWithSpouse.id to daughterWithSpouse,
            sonInLaw.id to sonInLaw
        )

        // Attempting to add son-in-law as a biological child of Pedro
        val result = engine.validateParentChildConnection(
            parentId = "p_pedro",
            childId = "p_jose",
            relationshipType = "Biological",
            personMap = map
        )

        assertFalse("Son-in-law must NOT be allowed to be added as a biological child", result.isAllowed)
        assertEquals("Son-in-law is 1st degree affinity", 1, result.degree)
        assertTrue("Relationship type must identify Son-in-Law", result.relationshipType.contains("Son-in-Law"))
        assertTrue("Legal basis must cite Article 38 (4)", result.legalBasis.contains("Article 38 (4)"))
        assertTrue("Reason must explain son-in-law must be connected as spouse of existing child",
            result.reason.contains("A son-in-law or daughter-in-law must be connected as the spouse of an existing child"))
        assertTrue("Reason must mention never connected as a biological child",
            result.reason.contains("never be connected as a biological child"))
        assertEquals("Family path must connect [Parent, Daughter, Son-in-Law]", 3, result.familyPath.size)
        assertEquals("p_pedro", result.familyPath[0].id)
        assertEquals("p_clara", result.familyPath[1].id)
        assertEquals("p_jose", result.familyPath[2].id)
    }

    @Test
    fun testDaughterInLawIncorrectlyAddedAsChild_prohibited() {
        val mother = Person(id = "p_maria", firstName = "Maria", lastName = "Bugarin", gender = "Female")
        val son = Person(
            id = "p_renzy",
            firstName = "Renzy",
            lastName = "Bugarin",
            gender = "Male",
            motherId = "p_maria",
            motherRelationshipType = "Biological",
            spouseId = "p_luz"
        )
        val daughterInLaw = Person(
            id = "p_luz",
            firstName = "Luz",
            lastName = "Santos",
            gender = "Female",
            spouseId = "p_renzy"
        )

        val map = mapOf(
            mother.id to mother,
            son.id to son,
            daughterInLaw.id to daughterInLaw
        )

        // Attempting to add daughter-in-law as a biological child of Maria
        val result = engine.validateParentChildConnection(
            parentId = "p_maria",
            childId = "p_luz",
            relationshipType = "Biological",
            personMap = map
        )

        assertFalse("Daughter-in-law must NOT be allowed to be added as a biological child", result.isAllowed)
        assertTrue("Relationship type must identify Daughter-in-Law", result.relationshipType.contains("Daughter-in-Law"))
        assertTrue("Reason must explain daughter-in-law affinity rule",
            result.reason.contains("A son-in-law or daughter-in-law must be connected as the spouse of an existing child"))
        assertEquals("Family path must connect [Mother, Son, Daughter-in-Law]", 3, result.familyPath.size)
    }

    // =========================================================================
    // TEST 5: TWO UNRELATED PEOPLE LINKED AS SPOUSES (ALLOWED)
    // =========================================================================

    @Test
    fun testTwoUnrelatedPeopleLinkedAsSpouses_allowed() {
        val personA = Person(id = "p_a", firstName = "Juan", lastName = "Dela Cruz", gender = "Male")
        val personB = Person(id = "p_b", firstName = "Maria", lastName = "Clara", gender = "Female")

        val map = mapOf(personA.id to personA, personB.id to personB)

        val result = engine.validateSpouseConnection("p_a", "p_b", map)

        assertTrue("Unrelated people must be allowed to marry", result.isAllowed)
        assertEquals(0, result.degree)
        assertEquals("Unrelated Persons", result.relationshipType)
        assertTrue("Legal basis must cite eligibility", result.legalBasis.contains("Eligible"))
        assertTrue("Reason must confirm no biological blood relationship exists",
            result.reason.contains("No biological blood relationship"))
    }

    // =========================================================================
    // TEST 6: LINEAL RELATIVES TO ANY DEGREE (RULE 1 - GRANDPARENT & GRANDCHILD)
    // =========================================================================

    @Test
    fun testGrandparentAndGrandchildLinkedAsSpouses_prohibited() {
        val grandfather = Person(id = "p_gf", firstName = "Pedro", lastName = "Bugarin", gender = "Male")
        val parent = Person(id = "p_p", firstName = "Renzy", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val grandchild = Person(id = "p_gc", firstName = "Ghillain", lastName = "Bugarin", fatherId = "p_p", fatherRelationshipType = "Biological")

        val map = mapOf(
            grandfather.id to grandfather,
            parent.id to parent,
            grandchild.id to grandchild
        )

        val result = engine.validateSpouseConnection("p_gf", "p_gc", map)

        assertFalse("Grandparent and grandchild must NOT be allowed to marry", result.isAllowed)
        assertEquals("Grandparent and grandchild are 2nd degree lineal", 2, result.degree)
        assertTrue("Legal basis must cite Article 37 (1)", result.legalBasis.contains("Article 37 (1)"))
        assertTrue("Relationship type must identify Grandparent and Grandchild",
            result.relationshipType.contains("Grandparent and Grandchild"))
        assertEquals("Family path must be [Grandfather, Parent, Grandchild]", 3, result.familyPath.size)
    }

    // =========================================================================
    // TEST 7: COLLATERAL RELATIVES UP TO 4TH DEGREE (RULE 3 - UNCLE/AUNT & GREAT-UNCLE)
    // =========================================================================

    @Test
    fun testUncleAndNieceLinkedAsSpouses_prohibited() {
        val grandfather = Person(id = "p_gf", firstName = "Antonio", lastName = "Bugarin")
        val uncle = Person(id = "p_uncle", firstName = "Renzy", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val brother = Person(id = "p_bro", firstName = "Jose", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val niece = Person(id = "p_niece", firstName = "Clara", lastName = "Bugarin", fatherId = "p_bro", fatherRelationshipType = "Biological")

        val map = mapOf(
            grandfather.id to grandfather,
            uncle.id to uncle,
            brother.id to brother,
            niece.id to niece
        )

        val result = engine.validateSpouseConnection("p_uncle", "p_niece", map)

        assertFalse("Uncle and niece must NOT be allowed to marry", result.isAllowed)
        assertEquals("Uncle and niece are 3rd degree collateral", 3, result.degree)
        assertTrue("Legal basis must cite Article 38 (1)", result.legalBasis.contains("Article 38 (1)"))
        assertTrue("Relationship type must identify Uncle/Aunt and Niece/Nephew",
            result.relationshipType.contains("Uncle/Aunt") || result.relationshipType.contains("Niece/Nephew"))
    }

    @Test
    fun testGreatUncleAndGrandnieceLinkedAsSpouses_prohibited() {
        // Great-grandfather GGF
        val ggf = Person(id = "p_ggf", firstName = "Patriarch", lastName = "Bugarin")
        // Children of GGF: Great-Uncle GU and Grandfather GF
        val greatUncle = Person(id = "p_gu", firstName = "GreatUncle", lastName = "Bugarin", fatherId = "p_ggf", fatherRelationshipType = "Biological")
        val gf = Person(id = "p_gf", firstName = "Grandfather", lastName = "Bugarin", fatherId = "p_ggf", fatherRelationshipType = "Biological")
        // Child of GF: Parent P
        val parent = Person(id = "p_p", firstName = "Parent", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        // Child of P: Grandniece GN (distance from GU = 1 + 3 = 4)
        val grandniece = Person(id = "p_gn", firstName = "Grandniece", lastName = "Bugarin", fatherId = "p_p", fatherRelationshipType = "Biological")

        val map = mapOf(
            ggf.id to ggf,
            greatUncle.id to greatUncle,
            gf.id to gf,
            parent.id to parent,
            grandniece.id to grandniece
        )

        val result = engine.validateSpouseConnection("p_gu", "p_gn", map)

        assertFalse("Great-uncle and grandniece (4th degree collateral) must NOT be allowed to marry", result.isAllowed)
        assertEquals(4, result.degree)
        assertTrue("Legal basis must cite Article 38 (1)", result.legalBasis.contains("Article 38 (1)"))
    }

    // =========================================================================
    // TEST 8: COLLATERAL RELATIVES FROM 5TH DEGREE ONWARD (RULE 4 - ALLOWED)
    // =========================================================================

    @Test
    fun testFirstCousinOnceRemovedLinkedAsSpouses_allowedWithCivilExplanation() {
        // Grandfather GF
        val gf = Person(id = "p_gf", firstName = "Antonio", lastName = "Bugarin")
        // Children of GF: P1 and P2 (siblings)
        val p1 = Person(id = "p_p1", firstName = "Jose", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val p2 = Person(id = "p_p2", firstName = "Ramon", lastName = "Bugarin", fatherId = "p_gf", fatherRelationshipType = "Biological")
        // First cousins: C1 (child of P1) and C2 (child of P2)
        val c1 = Person(id = "p_c1", firstName = "Renzy", lastName = "Bugarin", fatherId = "p_p1", fatherRelationshipType = "Biological")
        val c2 = Person(id = "p_c2", firstName = "Clara", lastName = "Bugarin", fatherId = "p_p2", fatherRelationshipType = "Biological")
        // Child of C2: C2Child (First cousin once removed from C1; distance = 2 + 3 = 5)
        val c2Child = Person(id = "p_c2child", firstName = "Ghillain", lastName = "Bugarin", motherId = "p_c2", motherRelationshipType = "Biological")

        val map = mapOf(
            gf.id to gf,
            p1.id to p1,
            p2.id to p2,
            c1.id to c1,
            c2.id to c2,
            c2Child.id to c2Child
        )

        val result = engine.validateSpouseConnection("p_c1", "p_c2child", map)

        assertTrue("First cousins once removed (5th degree) must be ALLOWED to marry", result.isAllowed)
        assertEquals("Degree must be 5", 5, result.degree)
        assertTrue("Legal basis must confirm no civil legal impediment under Articles 37 & 38",
            result.legalBasis.contains("No Civil Legal Impediment"))
        assertTrue("Reason must explain lack of impediment under Family Code Articles 37 and 38",
            result.reason.contains("no civil legal impediment under Philippine Family Code Articles 37 and 38"))
        assertEquals("Family path must connect all 6 people through common grandfather", 6, result.familyPath.size)
    }

    @Test
    fun testSecondCousinsLinkedAsSpouses_allowedWithCivilExplanation() {
        // Great-grandfather GGF
        val ggf = Person(id = "p_ggf", firstName = "Ancestor", lastName = "Bugarin")
        // Grandparents: GP1 and GP2 (siblings, children of GGF)
        val gp1 = Person(id = "p_gp1", firstName = "GP1", lastName = "Bugarin", fatherId = "p_ggf", fatherRelationshipType = "Biological")
        val gp2 = Person(id = "p_gp2", firstName = "GP2", lastName = "Bugarin", fatherId = "p_ggf", fatherRelationshipType = "Biological")
        // Parents: P1 and P2 (first cousins)
        val p1 = Person(id = "p_p1", firstName = "P1", lastName = "Bugarin", fatherId = "p_gp1", fatherRelationshipType = "Biological")
        val p2 = Person(id = "p_p2", firstName = "P2", lastName = "Bugarin", fatherId = "p_gp2", fatherRelationshipType = "Biological")
        // Second cousins: SC1 and SC2 (distance = 3 + 3 = 6)
        val sc1 = Person(id = "p_sc1", firstName = "Renzy", lastName = "Bugarin", fatherId = "p_p1", fatherRelationshipType = "Biological")
        val sc2 = Person(id = "p_sc2", firstName = "Maria", lastName = "Bugarin", fatherId = "p_p2", fatherRelationshipType = "Biological")

        val map = mapOf(
            ggf.id to ggf,
            gp1.id to gp1,
            gp2.id to gp2,
            p1.id to p1,
            p2.id to p2,
            sc1.id to sc1,
            sc2.id to sc2
        )

        val result = engine.validateSpouseConnection("p_sc1", "p_sc2", map)

        assertTrue("Second cousins (6th degree) must be ALLOWED to marry", result.isAllowed)
        assertEquals("Degree must be 6", 6, result.degree)
        assertTrue("Relationship type must identify Second Cousins", result.relationshipType.contains("Second Cousins"))
        assertTrue("Legal basis must confirm no civil legal impediment",
            result.legalBasis.contains("No Civil Legal Impediment"))
        assertTrue("Reason must cite Articles 37 and 38",
            result.reason.contains("Philippine Family Code Articles 37 and 38"))
        assertEquals("Family path must connect all 7 persons through GGF", 7, result.familyPath.size)
    }

    // =========================================================================
    // TEST 9: SELF-RELATIONSHIPS (RULE 6)
    // =========================================================================

    @Test
    fun testSelfMarriage_prohibited() {
        val person = Person(id = "p_renzy", firstName = "Renzy", lastName = "Bugarin")
        val map = mapOf(person.id to person)

        val result = engine.validateSpouseConnection("p_renzy", "p_renzy", map)

        assertFalse("Self-marriage must NOT be allowed", result.isAllowed)
        assertEquals("Self", result.relationshipType)
        assertTrue("Reason must state a person cannot marry themselves",
            result.reason.contains("cannot marry themselves"))
    }

    @Test
    fun testSelfParenting_prohibited() {
        val person = Person(id = "p_renzy", firstName = "Renzy", lastName = "Bugarin")
        val map = mapOf(person.id to person)

        val result = engine.validateParentChildConnection(
            parentId = "p_renzy",
            childId = "p_renzy",
            relationshipType = "Biological",
            personMap = map
        )

        assertFalse("A person cannot be their own parent", result.isAllowed)
        assertTrue("Relationship type must identify Self-Parenting", result.relationshipType.contains("Self-Parenting"))
        assertTrue("Reason must state person cannot become their own ancestor or descendant",
            result.reason.contains("cannot become their own ancestor or descendant"))
    }

    // =========================================================================
    // TEST 10: IMPOSSIBLE ANCESTRY CYCLES (RULE 7)
    // =========================================================================

    @Test
    fun testCircularAncestryCycle_childBecomingParentOfParent_prohibited() {
        // Parent Jose already has child Clara
        val parent = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin")
        val child = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", fatherId = "p_jose", fatherRelationshipType = "Biological")

        val map = mapOf(parent.id to parent, child.id to child)

        // Attempt to make Jose the child of Clara (i.e. Clara becoming the parent of her own parent!)
        val result = engine.validateParentChildConnection(
            parentId = "p_clara",
            childId = "p_jose",
            relationshipType = "Biological",
            personMap = map
        )

        assertFalse("Circular parent-child link must NOT be allowed", result.isAllowed)
        assertTrue("Relationship type must identify Impossible Ancestry Cycle",
            result.relationshipType.contains("Impossible Ancestry Cycle"))
        assertTrue("Reason must explain child cannot become parent of their own parent",
            result.reason.contains("child becoming the parent of their own parent"))
        // Cycle path: Clara -> Jose -> Clara
        assertEquals(3, result.familyPath.size)
        assertEquals("p_clara", result.familyPath[0].id)
        assertEquals("p_jose", result.familyPath[1].id)
        assertEquals("p_clara", result.familyPath[2].id)
    }

    @Test
    fun testCircularAncestryCycle_multiGenerationLoop_prohibited() {
        // Grandparent -> Parent -> Child
        val gp = Person(id = "p_gp", firstName = "Grandpa", lastName = "Bugarin")
        val p = Person(id = "p_p", firstName = "Father", lastName = "Bugarin", fatherId = "p_gp", fatherRelationshipType = "Biological")
        val c = Person(id = "p_c", firstName = "Grandchild", lastName = "Bugarin", fatherId = "p_p", fatherRelationshipType = "Biological")

        val map = mapOf(gp.id to gp, p.id to p, c.id to c)

        // Attempt to make grandchild the parent of grandfather
        val result = engine.validateParentChildConnection(
            parentId = "p_c",
            childId = "p_gp",
            relationshipType = "Biological",
            personMap = map
        )

        assertFalse("Grandchild cannot become parent of grandparent", result.isAllowed)
        assertTrue("Relationship type must identify Impossible Ancestry Cycle",
            result.relationshipType.contains("Impossible Ancestry Cycle"))
        // Full loop: Grandchild -> Father -> Grandpa -> Grandchild
        assertEquals(4, result.familyPath.size)
        assertEquals("p_c", result.familyPath[0].id)
        assertEquals("p_p", result.familyPath[1].id)
        assertEquals("p_gp", result.familyPath[2].id)
        assertEquals("p_c", result.familyPath[3].id)
    }

    // =========================================================================
    // TEST 11: BIOLOGICAL PARENT AND SPOUSE CONFLICT (RULE 5)
    // =========================================================================

    @Test
    fun testExistingSpouseLinkedAsBiologicalParent_prohibited() {
        // Husband and Wife are spouses
        val husband = Person(id = "p_h", firstName = "Pedro", lastName = "Bugarin", spouseId = "p_w")
        val wife = Person(id = "p_w", firstName = "Clara", lastName = "Bugarin", spouseId = "p_h")

        val map = mapOf(husband.id to husband, wife.id to wife)

        // Attempting to link husband as biological parent of wife
        val result = engine.validateParentChildConnection(
            parentId = "p_h",
            childId = "p_w",
            relationshipType = "Biological",
            personMap = map
        )

        assertFalse("A spouse cannot be linked as biological parent of their spouse", result.isAllowed)
        assertTrue("Relationship type must indicate Spouse as Biological Parent",
            result.relationshipType.contains("Spouse as Biological Parent"))
        assertTrue("Reason must state a person cannot be linked as both biological parent and spouse",
            result.reason.contains("A person cannot be linked as both biological parent and spouse of the same person"))
    }

    // =========================================================================
    // SECTION 3: NEW SPOUSE VALIDATION RULES (AGE, GENDER, ACTIVE SPOUSE)
    // =========================================================================

    @Test
    fun testUnderageSpouses_bothUnderage_prohibited() {
        // Both persons are 16 years old (born 2010)
        val youthA = Person(id = "y_a", firstName = "Jose", lastName = "Santos", gender = "Male", birthDate = "2010-05-10")
        val youthB = Person(id = "y_b", firstName = "Maria", lastName = "Reyes", gender = "Female", birthDate = "2010-08-20")

        val map = mapOf(youthA.id to youthA, youthB.id to youthB)
        val result = engine.validateSpouseConnection("y_a", "y_b", map)

        assertFalse("Both underage members must be blocked from marrying", result.isAllowed)
        assertTrue(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.MINIMUM_AGE))
        assertTrue("Message must state that both do not meet the minimum age requirement",
            result.reason.contains("Both") && result.reason.contains("do not meet the minimum age requirement"))
        assertTrue("Legal basis must cite Article 5 and RA 11596",
            result.legalBasis.contains("Article 5") || result.legalBasis.contains("11596"))
    }

    @Test
    fun testUnderageAndSameGender_userExample_prohibited() {
        // User example: 16-year-old male linked as spouse of another 16-year-old male
        val male16A = Person(id = "m1", firstName = "Carlo", lastName = "Reyes", gender = "Male", birthDate = "2010-02-15")
        val male16B = Person(id = "m2", firstName = "Daniel", lastName = "Cruz", gender = "Male", birthDate = "2010-07-20")

        val map = mapOf(male16A.id to male16A, male16B.id to male16B)
        val result = engine.validateSpouseConnection("m1", "m2", map)

        assertFalse("Two 16yo males must be blocked from marrying", result.isAllowed)
        assertTrue("Must fail minimum age validation",
            result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.MINIMUM_AGE))
        assertTrue("Must fail gender validation",
            result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.GENDER_NOT_ALLOWED))
        assertEquals("Must report exactly 2 failed validation messages", 2, result.failureMessages.size)
        assertTrue("Must explain minimum age failure", result.reason.contains("minimum age requirement"))
        assertTrue("Must explain current gender relationship rules",
            result.reason.contains("current gender relationship rules"))
    }

    @Test
    fun testUnderageSpouse_singlePersonUnderage_prohibited() {
        val adult = Person(id = "p_ad", firstName = "Pedro", lastName = "Dela Cruz", gender = "Male", birthDate = "2000-01-01")
        val minor = Person(id = "p_mn", firstName = "Ana", lastName = "Santos", gender = "Female", birthDate = "2010-01-01")

        val map = mapOf(adult.id to adult, minor.id to minor)
        val result = engine.validateSpouseConnection("p_ad", "p_mn", map)

        assertFalse("Marriage with an underage member must be blocked", result.isAllowed)
        assertTrue(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.MINIMUM_AGE))
        assertTrue(result.reason.contains("Ana Santos does not meet the minimum age requirement"))
        assertFalse(result.reason.contains("Both Pedro and Ana"))
    }

    @Test
    fun testSameGenderSpouses_notAllowedByDefault_prohibited() {
        // Two adult males (25 and 28 years old)
        val adultA = Person(id = "a_m1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1998-01-01")
        val adultB = Person(id = "a_m2", firstName = "Carlos", lastName = "Reyes", gender = "Male", birthDate = "1995-01-01")

        val map = mapOf(adultA.id to adultA, adultB.id to adultB)
        val result = engine.validateSpouseConnection("a_m1", "a_m2", map)

        assertFalse("Same-gender spouse must be blocked by default under Philippine Family Code", result.isAllowed)
        assertTrue(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.GENDER_NOT_ALLOWED))
        assertTrue(result.reason.contains("The spouse relationship is not allowed under the application’s current gender relationship rules"))
        assertTrue(result.legalBasis.contains("Articles 1 & 2"))
    }

    @Test
    fun testSameGenderSpouses_allowedWhenConfigured() {
        val progressiveEngine = MarriageValidationEngine(
            bloodEngine = BloodRelationshipEngine(),
            allowSameGenderSpouse = true
        )
        val adultA = Person(id = "a_m1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1998-01-01")
        val adultB = Person(id = "a_m2", firstName = "Carlos", lastName = "Reyes", gender = "Male", birthDate = "1995-01-01")

        val map = mapOf(adultA.id to adultA, adultB.id to adultB)
        val result = progressiveEngine.validateSpouseConnection("a_m1", "a_m2", map)

        assertTrue("Same-gender spouse must be allowed when configured and all other validations pass", result.isAllowed)
    }

    @Test
    fun testSameGenderSpouses_underageBlockedEvenWhenSameGenderAllowed() {
        val progressiveEngine = MarriageValidationEngine(
            bloodEngine = BloodRelationshipEngine(),
            allowSameGenderSpouse = true
        )
        val minorA = Person(id = "m_m1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "2010-01-01")
        val minorB = Person(id = "m_m2", firstName = "Carlos", lastName = "Reyes", gender = "Male", birthDate = "2010-01-01")

        val map = mapOf(minorA.id to minorA, minorB.id to minorB)
        val result = progressiveEngine.validateSpouseConnection("m_m1", "m_m2", map)

        assertFalse("Underage members must still be blocked even if same-gender is allowed", result.isAllowed)
        assertTrue(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.MINIMUM_AGE))
        assertFalse(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.GENDER_NOT_ALLOWED))
        assertEquals(1, result.failureMessages.size)
    }

    @Test
    fun testActiveSpouse_bigamyBlocked() {
        val husband = Person(id = "h1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1990-01-01", spouseId = "w1", maritalStatus = "Married")
        val wife1 = Person(id = "w1", firstName = "Maria", lastName = "Dela Cruz", gender = "Female", birthDate = "1992-01-01", spouseId = "h1", maritalStatus = "Married")
        val candidate = Person(id = "w2", firstName = "Elena", lastName = "Santos", gender = "Female", birthDate = "1993-01-01", maritalStatus = "Single")

        val map = mapOf(husband.id to husband, wife1.id to wife1, candidate.id to candidate)
        val result = engine.validateSpouseConnection("h1", "w2", map)

        assertFalse("Entering a second marriage while an active marriage exists must be blocked", result.isAllowed)
        assertTrue(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS))
        assertTrue(result.reason.contains("already married") || result.reason.contains("active spouse"))
        assertTrue(result.legalBasis.contains("Articles 35 (4) & 40"))
    }

    @Test
    fun testActiveSpouse_allowedWhenSpouseDeceased() {
        val husband = Person(id = "h1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1990-01-01", spouseId = "w1", maritalStatus = "Widowed")
        val deceasedWife = Person(id = "w1", firstName = "Maria", lastName = "Dela Cruz", gender = "Female", birthDate = "1992-01-01", deathDate = "2020-01-01", isLiving = false)
        val newSpouse = Person(id = "w2", firstName = "Elena", lastName = "Santos", gender = "Female", birthDate = "1993-01-01", maritalStatus = "Single")

        val map = mapOf(husband.id to husband, deceasedWife.id to deceasedWife, newSpouse.id to newSpouse)
        val result = engine.validateSpouseConnection("h1", "w2", map)

        assertTrue("Remarriage after death of previous spouse must be allowed", result.isAllowed)
    }

    @Test
    fun testActiveSpouse_allowedWhenPriorMarriageAnnulled() {
        val husband = Person(id = "h1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1990-01-01", spouseId = "w1", maritalStatus = "Annulled")
        val exWife = Person(id = "w1", firstName = "Maria", lastName = "Dela Cruz", gender = "Female", birthDate = "1992-01-01", maritalStatus = "Annulled")
        val newSpouse = Person(id = "w2", firstName = "Elena", lastName = "Santos", gender = "Female", birthDate = "1993-01-01", maritalStatus = "Single")

        val map = mapOf(husband.id to husband, exWife.id to exWife, newSpouse.id to newSpouse)
        val result = engine.validateSpouseConnection("h1", "w2", map)

        assertTrue("Remarriage after prior marriage was annulled/ended must be allowed", result.isAllowed)
    }

    @Test
    fun testSelfMarriage_prohibited_withFailureReason() {
        val person = Person(id = "p1", firstName = "Renzy", lastName = "Bugarin", gender = "Male", birthDate = "1995-01-01")
        val map = mapOf(person.id to person)
        val result = engine.validateSpouseConnection("p1", "p1", map)

        assertFalse("Self-marriage must be blocked", result.isAllowed)
        assertTrue(result.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.SELF_MARRIAGE))
        assertTrue(result.reason.contains("cannot marry themselves"))
    }
}

