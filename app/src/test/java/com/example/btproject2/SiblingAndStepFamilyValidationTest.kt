package com.example.btproject2

import com.example.btproject2.engine.BloodRelationshipEngine
import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.engine.LinealConsanguinityCalculator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates Philippine Family Tree Rules for Sibling and Step-Family Relationships:
 *
 * Rule 1: If two children share both biological parents, label them as full siblings.
 * Rule 2: If two children share only one biological parent, label them as half-siblings.
 * Rule 3: If two children share no biological parent, but their parents are spouses or partners, label them as step-siblings.
 * Rule 4: The spouse of a child's biological parent should be labeled as a step-parent, unless a separate adoption record says otherwise.
 * Rule 5: Do not treat a step-parent, step-sibling, or in-law as a blood relative in the consanguinity calculation (0 degrees).
 * Rule 6: Half-siblings share one biological parent, so they are still blood relatives (2nd degree collateral consanguinity)
 *         and must follow the existing legal marriage validation rules (Art. 37(2) void ab initio).
 */
class SiblingAndStepFamilyValidationTest {

    private val kinshipTitleHelper = KinshipTitleHelper
    private val bloodEngine = BloodRelationshipEngine()
    private val bloodlineTracer = BloodlineTracer()
    private val linealCalculator = LinealConsanguinityCalculator()
    private val marriageEngine = MarriageValidationEngine()

    // Parents
    private val fatherJuan = Person(id = "f_juan", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", spouseId = "m_maria")
    private val motherMaria = Person(id = "m_maria", firstName = "Maria", lastName = "Santos", gender = "Female", spouseId = "f_juan")

    // Full siblings: share both father Juan and mother Maria (biological)
    private val fullBrother = Person(id = "c_carlos", firstName = "Carlos", lastName = "Dela Cruz", gender = "Male", fatherId = "f_juan", motherId = "m_maria")
    private val fullSister = Person(id = "c_ana", firstName = "Ana", lastName = "Dela Cruz", gender = "Female", fatherId = "f_juan", motherId = "m_maria")

    // Second partner of father Juan
    private val partnerElena = Person(id = "p_elena", firstName = "Elena", lastName = "Reyes", gender = "Female", spouseId = "f_juan")

    // Half-siblings: share father Juan, but have different mothers (Maria vs Elena)
    private val halfBrother = Person(id = "c_luis", firstName = "Luis", lastName = "Dela Cruz", gender = "Male", fatherId = "f_juan", motherId = "p_elena")
    private val halfSister = Person(id = "c_sofia", firstName = "Sofia", lastName = "Dela Cruz", gender = "Female", fatherId = "f_juan", motherId = "p_elena")

    // Step-father: married to mother Maria, not the biological father of Carlos/Ana
    private val stepFatherRoberto = Person(id = "step_roberto", firstName = "Roberto", lastName = "Gomez", gender = "Male", spouseId = "m_maria")

    // Step-mother: married to father Juan, not the biological mother of Carlos/Ana
    private val stepMotherLorna = Person(id = "step_lorna", firstName = "Lorna", lastName = "Bautista", gender = "Female", spouseId = "f_juan")

    // Step-siblings: children of Roberto from a prior relationship, sharing no biological parent with Carlos/Ana
    private val stepBrotherMark = Person(id = "step_mark", firstName = "Mark", lastName = "Gomez", gender = "Male", fatherId = "step_roberto", motherId = "prior_wife")
    private val stepSisterGrace = Person(id = "step_grace", firstName = "Grace", lastName = "Gomez", gender = "Female", fatherId = "step_roberto", motherId = "prior_wife")

    // Adoptive parent case: step-mother Lorna formally adopts Carlos
    private val adoptiveMotherLorna = Person(id = "step_lorna", firstName = "Lorna", lastName = "Bautista", gender = "Female", spouseId = "f_juan")
    private val adoptedChildCarlos = Person(id = "c_carlos_adopted", firstName = "Carlos", lastName = "Dela Cruz", gender = "Male", fatherId = "f_juan", motherId = "step_lorna", motherRelationshipType = "Adopted")

    private val allMembers = listOf(
        fatherJuan, motherMaria, fullBrother, fullSister,
        partnerElena, halfBrother, halfSister,
        stepFatherRoberto, stepMotherLorna, stepBrotherMark, stepSisterGrace
    )
    private val memberMap = allMembers.associateBy { it.id }

    @Test
    fun testRule1_FullSiblingsShareBothBiologicalParents() {
        val byId = allMembers.associateBy { it.id }

        // Carlos and Ana share both biological father (Juan) and mother (Maria)
        val classification = kinshipTitleHelper.classifySibling(fullBrother, fullSister, byId)
        assertEquals(KinshipTitleHelper.SiblingCategory.FULL_SIBLING, classification)

        // Labels
        assertEquals("FULL BROTHER", kinshipTitleHelper.resolveTitle(fullBrother, fullSister, allMembers))
        assertEquals("FULL SISTER", kinshipTitleHelper.resolveTitle(fullSister, fullBrother, allMembers))

        // Breakdown group
        val siblingGroup = kinshipTitleHelper.getSiblingsForPerson(fullBrother, allMembers)
        assertTrue(siblingGroup.fullSiblings.any { it.id == fullSister.id })
    }

    @Test
    fun testRule2_HalfSiblingsShareOnlyOneBiologicalParent() {
        val byId = allMembers.associateBy { it.id }

        // Carlos (father Juan, mother Maria) and Luis (father Juan, mother Elena)
        val classification = kinshipTitleHelper.classifySibling(halfBrother, fullBrother, byId)
        assertEquals(KinshipTitleHelper.SiblingCategory.HALF_SIBLING, classification)

        // Labels
        assertEquals("HALF-BROTHER", kinshipTitleHelper.resolveTitle(halfBrother, fullBrother, allMembers))
        assertEquals("HALF-SISTER", kinshipTitleHelper.resolveTitle(halfSister, fullBrother, allMembers))

        // Breakdown group
        val siblingGroup = kinshipTitleHelper.getSiblingsForPerson(fullBrother, allMembers)
        assertTrue(siblingGroup.halfSiblings.any { it.id == halfBrother.id })
        assertTrue(siblingGroup.halfSiblings.any { it.id == halfSister.id })
    }

    @Test
    fun testRule3_StepSiblingsShareNoBioParentsButParentsAreSpouses() {
        // Roberto is married to Maria (mother of Carlos). Roberto's child is Mark.
        // Mark and Carlos share no biological parents, but Maria and Roberto are spouses.
        val updatedMembers = allMembers.map {
            if (it.id == "m_maria") it.copy(spouseId = "step_roberto")
            else if (it.id == "step_roberto") it.copy(spouseId = "m_maria")
            else it
        }
        val byId = updatedMembers.associateBy { it.id }

        val classification = kinshipTitleHelper.classifySibling(stepBrotherMark, fullBrother, byId)
        assertEquals(KinshipTitleHelper.SiblingCategory.STEP_SIBLING, classification)

        // Labels
        assertEquals("STEPBROTHER", kinshipTitleHelper.resolveTitle(stepBrotherMark, fullBrother, updatedMembers))
        assertEquals("STEPSISTER", kinshipTitleHelper.resolveTitle(stepSisterGrace, fullBrother, updatedMembers))

        // Breakdown group
        val siblingGroup = kinshipTitleHelper.getSiblingsForPerson(fullBrother, updatedMembers)
        assertTrue(siblingGroup.stepSiblings.any { it.id == stepBrotherMark.id })
        assertTrue(siblingGroup.stepSiblings.any { it.id == stepSisterGrace.id })
    }

    @Test
    fun testRule4_SpouseOfBioParentIsStepParentUnlessAdopted() {
        // Juan is married to Lorna (step-mother of Carlos, since Maria is biological mother)
        val updatedMembers = allMembers.map {
            if (it.id == "f_juan") it.copy(spouseId = "step_lorna")
            else if (it.id == "step_lorna") it.copy(spouseId = "f_juan")
            else it
        }

        // Stepmother label
        assertEquals("STEPMOTHER", kinshipTitleHelper.resolveTitle(stepMotherLorna, fullBrother, updatedMembers))

        // Stepfather label (Roberto married to Maria)
        val updatedMembersStepDad = allMembers.map {
            if (it.id == "m_maria") it.copy(spouseId = "step_roberto")
            else if (it.id == "step_roberto") it.copy(spouseId = "m_maria")
            else it
        }
        assertEquals("STEPFATHER", kinshipTitleHelper.resolveTitle(stepFatherRoberto, fullBrother, updatedMembersStepDad))

        // When separate adoption record exists, label is ADOPTIVE MOTHER, not Stepmother
        val adoptionList = listOf(fatherJuan, adoptiveMotherLorna, adoptedChildCarlos)
        assertEquals("ADOPTIVE MOTHER", kinshipTitleHelper.resolveTitle(adoptiveMotherLorna, adoptedChildCarlos, adoptionList))
    }

    @Test
    fun testRule5_StepParentAndStepSiblingHaveZeroConsanguinity() {
        // 1. Lineal Consanguinity Calculator strictly excludes step-parents from lineal consanguinity
        val updatedMembers = allMembers.map {
            if (it.id == "f_juan") it.copy(spouseId = "step_lorna")
            else if (it.id == "step_lorna") it.copy(spouseId = "f_juan")
            else it
        }
        val linealDegree = linealCalculator.getLinealDegree(fullBrother, stepMotherLorna, updatedMembers)
        assertEquals(null, linealDegree) // Step-mother is NOT in lineal bloodline

        // 2. BloodlineTracer returns 0 degrees of consanguinity for step-parent
        val traceStepParent = bloodlineTracer.trace(stepMotherLorna.id, fullBrother.id, updatedMembers.associateBy { it.id })
        assertEquals(0, traceStepParent.degreeOfConsanguinity)
        assertEquals("Affinity / Step", traceStepParent.relationshipCategory)

        // 3. Step-siblings have 0 degrees of consanguinity and NO marriage prohibition
        val updatedWithStepSiblings = allMembers.map {
            if (it.id == "m_maria") it.copy(spouseId = "step_roberto")
            else if (it.id == "step_roberto") it.copy(spouseId = "m_maria")
            else it
        }
        val traceStepSibling = bloodlineTracer.trace(fullBrother.id, stepBrotherMark.id, updatedWithStepSiblings.associateBy { it.id })
        assertEquals(0, traceStepSibling.degreeOfConsanguinity)
        assertEquals("Step-Siblings", traceStepSibling.relationshipType)
        assertFalse(traceStepSibling.isMarriageProhibited)
    }

    @Test
    fun testRule6_HalfSiblingsAreBloodRelativesAndMarriageIsProhibited() {
        // 1. BloodRelationshipEngine confirms collateral 2nd degree consanguinity
        val bloodResult = bloodEngine.determineBloodRelationship(fullBrother.id, halfSister.id, memberMap)
        assertTrue(bloodResult.isBloodRelated)
        assertEquals(BloodRelationshipEngine.BloodCategory.COLLATERAL, bloodResult.category)
        assertEquals(2, bloodResult.degree)

        // 2. MarriageValidationEngine verifies marriage is void ab initio under Article 37 (2) of the Family Code
        val marriageResult = marriageEngine.validateSpouseConnection(fullBrother.id, halfSister.id, memberMap)
        assertFalse(marriageResult.isAllowed)
        assertTrue(marriageResult.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.SIBLING_CONSANGUINITY))
        assertTrue(marriageResult.reason.contains("Half Siblings"))
        assertTrue(marriageResult.reason.contains("Article 37 (2)"))
    }
}

