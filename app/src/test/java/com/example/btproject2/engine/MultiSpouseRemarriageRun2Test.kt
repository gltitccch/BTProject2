package com.example.btproject2.engine

import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Run 2 of the Multi-Spouse & Legal Remarriage upgrade:
 * - BloodlineTracer recognizes Former / Late Spouse in formerSpouseIds
 * - BloodlineTracer detects Step-Parent / Step-Child (Art. 38(2)) across 1st and 2nd marriages
 * - BloodlineTracer detects Half-Siblings (Art. 37(2)) across 1st and 2nd marriages
 * - BloodlineTracer & BloodRelationshipEngine detect Parents-in-Law and Siblings-in-Law across both marriages
 * - KinshipTitleHelper resolves LATE WIFE, WIFE, STEPMOTHER, STEPSON,STEPDAUGHTER, HALF-SISTER, and FATHER-IN-LAW
 */
class MultiSpouseRemarriageRun2Test {

    private val tracer = BloodlineTracer()
    private val bloodEngine = BloodRelationshipEngine()

    private val loloPedro = Person(id = "pedro", firstName = "Pedro", lastName = "Santos", gender = "Male", isLiving = false)
    private val jose = Person(
        id = "jose",
        firstName = "Jose",
        lastName = "Malone",
        gender = "Male",
        spouseId = "elena",
        formerSpouseIds = listOf("maria"),
        maritalStatus = "Married",
        isLiving = true
    )
    private val maria1st = Person(
        id = "maria",
        firstName = "Maria",
        lastName = "Santos",
        gender = "Female",
        fatherId = "pedro",
        spouseId = "jose",
        maritalStatus = "Widowed",
        isLiving = false,
        deathDate = "1995-08-20"
    )
    private val elena2nd = Person(
        id = "elena",
        firstName = "Elena",
        lastName = "Cruz",
        gender = "Female",
        spouseId = "jose",
        maritalStatus = "Married",
        isLiving = true
    )
    private val mateo = Person(
        id = "mateo",
        firstName = "Mateo",
        lastName = "Malone",
        gender = "Male",
        fatherId = "jose",
        motherId = "maria",
        isLiving = true
    )
    private val sofia = Person(
        id = "sofia",
        firstName = "Sofia",
        lastName = "Malone",
        gender = "Female",
        fatherId = "jose",
        motherId = "elena",
        isLiving = true
    )

    private val allMembers = listOf(loloPedro, jose, maria1st, elena2nd, mateo, sofia)
    private val treeMap = allMembers.associateBy { it.id }

    @Test
    fun `bloodline tracer identifies former late spouse and active second spouse accurately`() {
        val trace1st = tracer.trace("jose", "maria", treeMap)
        assertTrue(trace1st.isRelated)
        assertEquals("Former / Late Spouse (Prior Marriage)", trace1st.relationshipType)

        val trace2nd = tracer.trace("jose", "elena", treeMap)
        assertTrue(trace2nd.isRelated)
        assertEquals("Spouse (Legally Married)", trace2nd.relationshipType)
    }

    @Test
    fun `bloodline tracer identifies step-parent and half-siblings across remarriage`() {
        // Mateo (child of 1st wife Maria) <-> Elena (2nd wife) = Step-Child / Step-Parent (Void Art. 38(2))
        val stepTrace = tracer.trace("elena", "mateo", treeMap)
        assertTrue(stepTrace.isRelated)
        assertEquals("Step-Parent", stepTrace.relationshipType)
        assertTrue(stepTrace.isMarriageProhibited)

        // Sofia (child of 2nd wife Elena) <-> Maria (1st wife) = Step-Child / Step-Parent (Void Art. 38(2))
        val reverseStepTrace = tracer.trace("maria", "sofia", treeMap)
        assertTrue(reverseStepTrace.isRelated)
        assertEquals("Step-Parent", reverseStepTrace.relationshipType)
        assertTrue(reverseStepTrace.isMarriageProhibited)

        // Mateo <-> Sofia = Half-Siblings (2nd degree collateral consanguinity sharing father Jose)
        val halfSibTrace = tracer.trace("mateo", "sofia", treeMap)
        assertTrue(halfSibTrace.isRelated)
        assertEquals(2, halfSibTrace.degreeOfConsanguinity)
        assertTrue(halfSibTrace.relationshipType.contains("Half", ignoreCase = true) || halfSibTrace.bloodlineSubtype.contains("Half", ignoreCase = true))
        assertTrue(halfSibTrace.isMarriageProhibited)
    }

    @Test
    fun `kinship titles and in-law discovery work across both first and second marriages`() {
        assertEquals("LATE WIFE", KinshipTitleHelper.resolveTitle(maria1st, jose, allMembers))
        assertEquals("WIFE", KinshipTitleHelper.resolveTitle(elena2nd, jose, allMembers))
        assertEquals("STEPMOTHER", KinshipTitleHelper.resolveTitle(elena2nd, mateo, allMembers))
        assertEquals("STEPMOTHER", KinshipTitleHelper.resolveTitle(maria1st, sofia, allMembers))
        assertEquals("STEPSON", KinshipTitleHelper.resolveTitle(mateo, elena2nd, allMembers))
        assertEquals("STEPDAUGHTER", KinshipTitleHelper.resolveTitle(sofia, maria1st, allMembers))
        assertEquals("HALF-SISTER", KinshipTitleHelper.resolveTitle(sofia, mateo, allMembers))
        assertEquals("FATHER-IN-LAW", KinshipTitleHelper.resolveTitle(loloPedro, jose, allMembers))

        // Step-family helper for Jose's 1st child Mateo includes 2nd wife Elena as step-parent
        val mateoStepFamily = KinshipTitleHelper.getStepFamilyForPerson(mateo, allMembers)
        assertTrue(mateoStepFamily.stepParents.any { it.id == "elena" })

        // BloodRelationshipEngine.findParentsInLaw for Jose still includes 1st wife's father Lolo Pedro
        val parentsInLaw = bloodEngine.findParentsInLaw("jose", treeMap)
        assertTrue(parentsInLaw.any { it.id == "pedro" })
    }
}
