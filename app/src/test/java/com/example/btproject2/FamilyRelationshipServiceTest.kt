package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.AuditSeverity
import com.example.btproject2.service.FamilyRelationshipService.ParentRole
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.service.FamilyRelationshipService.RelationshipToRemove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Tests for the Central FamilyRelationshipService.
 *
 * Verifies all required service actions:
 * 1. Check whether a proposed relationship is valid
 * 2. Add a biological parent
 * 3. Add a biological child
 * 4. Add an adoptive parent
 * 5. Add an adoptive child
 * 6. Add a spouse or partner
 * 7. Add the spouse of an existing child
 * 8. Remove a family relationship
 * 9. Check the entire existing family tree for errors (Audit scanner)
 */
class FamilyRelationshipServiceTest {

    private lateinit var service: FamilyRelationshipService

    @Before
    fun setUp() {
        service = FamilyRelationshipService()
    }

    // =========================================================================
    // 1. CHECK PROPOSED RELATIONSHIP
    // =========================================================================

    @Test
    fun testCheckProposedRelationship_prohibitedSiblings() {
        val father = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin")
        val renzy = Person(id = "p_renzy", firstName = "Renzy", lastName = "Bugarin", fatherId = "p_jose", fatherRelationshipType = "Biological")
        val clara = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", fatherId = "p_jose", fatherRelationshipType = "Biological")

        val tree = mapOf(father.id to father, renzy.id to renzy, clara.id to clara)

        val check = service.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = "p_renzy",
            secondaryPersonId = "p_clara",
            tree = tree
        )

        assertFalse("Siblings proposed as spouses must NOT be valid", check.isAllowed)
        assertTrue(check.legalBasis.contains("Article 37 (2)"))
    }

    @Test
    fun testCheckProposedRelationship_validUnrelatedSpouses() {
        val p1 = Person(id = "p_juan", firstName = "Juan", lastName = "Dela Cruz")
        val p2 = Person(id = "p_maria", firstName = "Maria", lastName = "Clara")

        val tree = mapOf(p1.id to p1, p2.id to p2)

        val check = service.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = "p_juan",
            secondaryPersonId = "p_maria",
            tree = tree
        )

        assertTrue("Unrelated individuals must be valid to marry", check.isAllowed)
    }

    // =========================================================================
    // 2. ADD A BIOLOGICAL PARENT
    // =========================================================================

    @Test
    fun testAddBiologicalParent_success() {
        val father = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin", gender = "Male", birthDate = "1960-01-01")
        val child = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", gender = "Female", birthDate = "1990-01-01")

        val tree = mapOf(father.id to father, child.id to child)

        val result = service.addBiologicalParent(
            childId = "p_clara",
            parentId = "p_pedro",
            tree = tree,
            role = ParentRole.FATHER
        )

        assertTrue("Adding valid biological father must succeed", result.isSuccess)
        assertNotNull(result.data)
        val updatedChild = result.data!!["p_clara"]
        assertEquals("p_pedro", updatedChild?.fatherId)
        assertEquals("Biological", updatedChild?.fatherRelationshipType)
    }

    @Test
    fun testAddBiologicalParent_blockedWhenTwoParentsCapReached() {
        val f1 = Person(id = "p_f1", firstName = "Father1", lastName = "Bugarin", gender = "Male", birthDate = "1960-01-01")
        val m1 = Person(id = "p_m1", firstName = "Mother1", lastName = "Bugarin", gender = "Female", birthDate = "1962-01-01")
        val child = Person(
            id = "p_c", firstName = "Child", lastName = "Bugarin", birthDate = "1990-01-01",
            fatherId = "p_f1", fatherRelationshipType = "Biological",
            motherId = "p_m1", motherRelationshipType = "Biological"
        )
        val intruder = Person(id = "p_f2", firstName = "Intruder", lastName = "Bugarin", gender = "Male", birthDate = "1958-01-01")

        val tree = mapOf(f1.id to f1, m1.id to m1, child.id to child, intruder.id to intruder)

        val result = service.addBiologicalParent(
            childId = "p_c",
            parentId = "p_f2",
            tree = tree
        )

        assertFalse("Adding parent when child already has 2 parents must be BLOCKED", result.isSuccess)
        assertTrue(result.message.contains("already has two parents"))
    }

    @Test
    fun testAddBiologicalParent_blockedWhenParentYoungerThanChild() {
        val parent = Person(id = "p_young", firstName = "Young", lastName = "Bugarin", gender = "Male", birthDate = "2000-01-01")
        val child = Person(id = "p_old", firstName = "Old", lastName = "Bugarin", birthDate = "1990-01-01")

        val tree = mapOf(parent.id to parent, child.id to child)

        val result = service.addBiologicalParent(
            childId = "p_old",
            parentId = "p_young",
            tree = tree
        )

        assertFalse("Parent younger than child must be BLOCKED", result.isSuccess)
        assertTrue(result.message.contains("younger than"))
    }

    // =========================================================================
    // 3. ADD A BIOLOGICAL CHILD
    // =========================================================================

    @Test
    fun testAddBiologicalChild_blockedForSonInLaw_rule8() {
        // Pedro has daughter Clara
        val pedro = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin", gender = "Male")
        val clara = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", gender = "Female", fatherId = "p_pedro", fatherRelationshipType = "Biological", spouseId = "p_jose")
        // Jose is married to Clara (son-in-law of Pedro)
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Santos", gender = "Male", spouseId = "p_clara")

        val tree = mapOf(pedro.id to pedro, clara.id to clara, jose.id to jose)

        // Attempt to add Jose (son-in-law) as a biological child of Pedro
        val result = service.addBiologicalChild(
            parentId = "p_pedro",
            childId = "p_jose",
            tree = tree
        )

        assertFalse("Son-in-law must NOT be added as biological child", result.isSuccess)
        assertTrue("Message must explain son-in-law affinity rule",
            result.message.contains("A son-in-law or daughter-in-law must be connected as the spouse of an existing child"))
    }

    // =========================================================================
    // 4. ADD ADOPTIVE PARENT & CHILD
    // =========================================================================

    @Test
    fun testAddAdoptiveParent_success() {
        val adopter = Person(id = "p_adopter", firstName = "Carlos", lastName = "Bugarin", gender = "Male", birthDate = "1970-01-01")
        val adoptee = Person(id = "p_adoptee", firstName = "Maria", lastName = "Bugarin", birthDate = "2000-01-01")

        val tree = mapOf(adopter.id to adopter, adoptee.id to adoptee)

        val result = service.addAdoptiveParent(
            childId = "p_adoptee",
            parentId = "p_adopter",
            tree = tree,
            role = ParentRole.FATHER
        )

        assertTrue("Adoptive parent link must succeed", result.isSuccess)
        val updatedChild = result.data!!["p_adoptee"]
        assertEquals("p_adopter", updatedChild?.fatherId)
        assertEquals("Adoptive", updatedChild?.fatherRelationshipType)

        // Verify adoptive link does NOT count as biological consanguinity
        val bloodCheck = service.bloodEngine.determineBloodRelationship("p_adopter", "p_adoptee", result.data!!)
        assertFalse("Adoptive child must not have biological consanguinity", bloodCheck.isBloodRelated)
    }

    // =========================================================================
    // 5. ADD SPOUSE OR PARTNER
    // =========================================================================

    @Test
    fun testAddSpouse_blockedForFirstCousins() {
        val gf = Person(id = "p_gf", firstName = "Antonio", lastName = "Bugarin")
        val p1 = Person(id = "p_p1", firstName = "Jose", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val p2 = Person(id = "p_p2", firstName = "Pedro", fatherId = "p_gf", fatherRelationshipType = "Biological")
        val c1 = Person(id = "p_c1", firstName = "Renzy", fatherId = "p_p1", fatherRelationshipType = "Biological")
        val c2 = Person(id = "p_c2", firstName = "Ghillain", fatherId = "p_p2", fatherRelationshipType = "Biological")

        val tree = mapOf(gf.id to gf, p1.id to p1, p2.id to p2, c1.id to c1, c2.id to c2)

        val result = service.addSpouse("p_c1", "p_c2", tree)

        assertFalse("First cousins must NOT be allowed to marry", result.isSuccess)
        assertTrue(result.message.contains("Article 38 (1)"))
    }

    @Test
    fun testAddSpouse_blockedForBigamy() {
        val husband = Person(id = "p_h", firstName = "Juan", lastName = "Dela Cruz", spouseId = "p_w1")
        val wife1 = Person(id = "p_w1", firstName = "Maria", lastName = "Dela Cruz", spouseId = "p_h")
        val woman2 = Person(id = "p_w2", firstName = "Elena", lastName = "Santos")

        val tree = mapOf(husband.id to husband, wife1.id to wife1, woman2.id to woman2)

        val result = service.addSpouse("p_h", "p_w2", tree)

        assertFalse("Bigamy must be BLOCKED", result.isSuccess)
        assertTrue(result.message.contains("already married"))
    }

    @Test
    fun testAddSpouse_allowedForSecondCousins_rule4() {
        val ggf = Person(id = "p_ggf", firstName = "Patriarch", lastName = "Bugarin")
        val gp1 = Person(id = "p_gp1", firstName = "GP1", fatherId = "p_ggf", fatherRelationshipType = "Biological")
        val gp2 = Person(id = "p_gp2", firstName = "GP2", fatherId = "p_ggf", fatherRelationshipType = "Biological")
        val p1 = Person(id = "p_p1", firstName = "P1", fatherId = "p_gp1", fatherRelationshipType = "Biological")
        val p2 = Person(id = "p_p2", firstName = "P2", fatherId = "p_gp2", fatherRelationshipType = "Biological")
        val sc1 = Person(id = "p_sc1", firstName = "Renzy", fatherId = "p_p1", fatherRelationshipType = "Biological")
        val sc2 = Person(id = "p_sc2", firstName = "Maria", fatherId = "p_p2", fatherRelationshipType = "Biological")

        val tree = mapOf(
            ggf.id to ggf, gp1.id to gp1, gp2.id to gp2,
            p1.id to p1, p2.id to p2, sc1.id to sc1, sc2.id to sc2
        )

        val result = service.addSpouse("p_sc1", "p_sc2", tree)

        assertTrue("Second cousins (6th degree) must be ALLOWED to marry", result.isSuccess)
        assertEquals("p_sc2", result.data!!["p_sc1"]?.spouseId)
        assertEquals("p_sc1", result.data!!["p_sc2"]?.spouseId)
    }

    // =========================================================================
    // 6. ADD SPOUSE OF EXISTING CHILD (RULE 8)
    // =========================================================================

    @Test
    fun testAddSpouseOfExistingChild_success() {
        val parent = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin")
        val daughter = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", fatherId = "p_pedro", fatherRelationshipType = "Biological")
        val suitor = Person(id = "p_jose", firstName = "Jose", lastName = "Santos")

        val tree = mapOf(parent.id to parent, daughter.id to daughter, suitor.id to suitor)

        val result = service.addSpouseOfExistingChild(
            childId = "p_clara",
            spouseId = "p_jose",
            tree = tree
        )

        assertTrue(result.isSuccess)
        val updatedClara = result.data!!["p_clara"]
        val updatedJose = result.data!!["p_jose"]
        assertEquals("p_jose", updatedClara?.spouseId)
        assertEquals("p_clara", updatedJose?.spouseId)
        assertTrue(result.message.contains("son-in-law"))
    }

    // =========================================================================
    // 7. REMOVE RELATIONSHIP
    // =========================================================================

    @Test
    fun testRemoveRelationship_spouse() {
        val p1 = Person(id = "p_1", firstName = "Pedro", spouseId = "p_2", maritalStatus = "Married")
        val p2 = Person(id = "p_2", firstName = "Maria", spouseId = "p_1", maritalStatus = "Married")

        val tree = mapOf(p1.id to p1, p2.id to p2)

        val result = service.removeRelationship(
            primaryPersonId = "p_1",
            relationshipToRemove = RelationshipToRemove.SPOUSE,
            tree = tree
        )

        assertTrue(result.isSuccess)
        assertNull(result.data!!["p_1"]?.spouseId)
        assertNull(result.data!!["p_2"]?.spouseId)
        assertEquals("Single", result.data!!["p_1"]?.maritalStatus)
        assertEquals("Single", result.data!!["p_2"]?.maritalStatus)
    }

    @Test
    fun testRemoveRelationship_father() {
        val child = Person(id = "p_c", firstName = "Clara", fatherId = "p_f", fatherRelationshipType = "Biological")
        val tree = mapOf(child.id to child)

        val result = service.removeRelationship(
            primaryPersonId = "p_c",
            relationshipToRemove = RelationshipToRemove.FATHER,
            tree = tree
        )

        assertTrue(result.isSuccess)
        assertNull(result.data!!["p_c"]?.fatherId)
        assertTrue(result.data!!["p_c"]?.fatherRelationshipType.isNullOrEmpty())
    }

    // =========================================================================
    // 8. AUDIT ENTIRE FAMILY TREE
    // =========================================================================

    @Test
    fun testAuditFamilyTree_detectsIllegalSiblingMarriage() {
        val father = Person(id = "p_f", firstName = "Jose", lastName = "Bugarin")
        val s1 = Person(id = "p_s1", firstName = "Renzy", fatherId = "p_f", fatherRelationshipType = "Biological", spouseId = "p_s2")
        val s2 = Person(id = "p_s2", firstName = "Clara", fatherId = "p_f", fatherRelationshipType = "Biological", spouseId = "p_s1")

        val tree = mapOf(father.id to father, s1.id to s1, s2.id to s2)

        val report = service.auditFamilyTree(tree)

        assertFalse("Audit must fail when tree contains illegal sibling marriage", report.isValid)
        assertTrue("Must report at least 1 critical error", report.criticalErrorCount >= 1)
        val issue = report.criticalErrors.first { it.title.contains("Illegal Marriage") }
        assertTrue(issue.description.contains("Article 37 (2)"))
    }

    @Test
    fun testAuditFamilyTree_detectsAncestryCycle() {
        // Clara is registered as parent of Jose, while Jose is registered as parent of Clara
        val p1 = Person(id = "p_jose", firstName = "Jose", fatherId = "p_clara", fatherRelationshipType = "Biological")
        val p2 = Person(id = "p_clara", firstName = "Clara", fatherId = "p_jose", fatherRelationshipType = "Biological")

        val tree = mapOf(p1.id to p1, p2.id to p2)

        val report = service.auditFamilyTree(tree)

        assertFalse("Audit must fail on cyclic ancestry loop", report.isValid)
        val cycleIssue = report.criticalErrors.first { it.title.contains("Impossible Ancestry Cycle") }
        assertNotNull(cycleIssue)
    }

    @Test
    fun testAuditFamilyTree_detectsSonInLawAddedAsBiologicalChild_rule8() {
        val pedro = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin")
        val daughter = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", fatherId = "p_pedro", fatherRelationshipType = "Biological", spouseId = "p_jose")
        // Jose is married to Clara, but ALSO registered as Pedro's biological child!
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Santos", fatherId = "p_pedro", fatherRelationshipType = "Biological", spouseId = "p_clara")

        val tree = mapOf(pedro.id to pedro, daughter.id to daughter, jose.id to jose)

        val report = service.auditFamilyTree(tree)

        assertFalse("Audit must detect son-in-law incorrectly added as biological child", report.isValid)
        val inLawIssue = report.criticalErrors.first { it.title.contains("Son-in-Law") }
        assertTrue(inLawIssue.description.contains("never be connected as a biological child"))
    }

    @Test
    fun testAuditFamilyTree_cleanTree_passesWithZeroErrors() {
        val gf = Person(id = "p_gf", firstName = "Antonio", birthDate = "1930-01-01", gender = "Male")
        val father = Person(id = "p_f", firstName = "Jose", birthDate = "1960-01-01", gender = "Male", fatherId = "p_gf", fatherRelationshipType = "Biological", spouseId = "p_m")
        val mother = Person(id = "p_m", firstName = "Maria", birthDate = "1962-01-01", gender = "Female", spouseId = "p_f")
        val child = Person(id = "p_c", firstName = "Renzy", birthDate = "1990-01-01", gender = "Male", fatherId = "p_f", motherId = "p_m", fatherRelationshipType = "Biological", motherRelationshipType = "Biological")

        val tree = mapOf(gf.id to gf, father.id to father, mother.id to mother, child.id to child)

        val report = service.auditFamilyTree(tree)

        assertTrue("Clean family tree must pass audit without any critical errors", report.isValid)
        assertEquals(0, report.criticalErrorCount)
    }
}

