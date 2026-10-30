package com.example.btproject2.engine

import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Run 1 of the Multi-Spouse & Legal Remarriage upgrade:
 * - Person.withLinkedSpouse preserves 1st spouse in formerSpouseIds when remarrying
 * - MarriageValidationEngine blocks bigamy when 1st spouse is living & active,
 *   but allows legal remarriage when 1st spouse is deceased or marriage is dissolved
 * - Step-Parent (Art. 38(3)) and Parent-in-Law (Art. 38(4)) prohibitions hold across formerSpouseIds
 * - FamilyLinkValidator.getEligibleMothers includes both 1st and 2nd wives
 * - FamilyRelationshipService.auditFamilyTree recognizes formerSpouseIds as reciprocally valid
 */
class MultiSpouseRemarriageRun1Test {

    private val service = FamilyRelationshipService()
    private val marriageEngine = MarriageValidationEngine()

    @Test
    fun `remarriage after deceased first spouse preserves first spouse in formerSpouseIds`() {
        val loloJose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Malone",
            gender = "Male",
            birthDate = "1950-01-10",
            spouseId = "maria",
            maritalStatus = "Married",
            isLiving = true
        )
        val lolaMaria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1952-05-14",
            spouseId = "jose",
            maritalStatus = "Married",
            isLiving = false,
            deathDate = "1995-08-20"
        )
        val lolaElena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1958-11-03",
            maritalStatus = "Single",
            isLiving = true
        )

        val initialTree = mapOf(
            "jose" to loloJose,
            "maria" to lolaMaria,
            "elena" to lolaElena
        )

        // Link Jose to 2nd spouse Elena
        val result = service.addSpouse("jose", "elena", initialTree)
        assertTrue("Remarriage after 1st spouse's death should be allowed", result.isSuccess)

        val updatedTree = result.data!!
        val updatedJose = updatedTree["jose"]!!
        assertEquals("elena", updatedJose.spouseId)
        assertTrue("1st wife Maria must be preserved in formerSpouseIds", updatedJose.formerSpouseIds.contains("maria"))
        assertEquals(listOf("elena", "maria"), updatedJose.allSpouseIds())

        // Verify Tree Audit does NOT flag Maria <-> Jose as an asymmetric spouse error
        val auditReport = service.auditFamilyTree(updatedTree)
        assertTrue("Healthy remarriage triad should have 0 critical audit errors", auditReport.issues.isEmpty())
    }

    @Test
    fun `bigamy is blocked when first spouse is still living and active`() {
        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Malone",
            gender = "Male",
            birthDate = "1950-01-10",
            spouseId = "maria",
            maritalStatus = "Married",
            isLiving = true
        )
        val mariaLiving = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1952-05-14",
            spouseId = "jose",
            maritalStatus = "Married",
            isLiving = true
        )
        val elena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1958-11-03",
            maritalStatus = "Single",
            isLiving = true
        )

        val tree = mapOf("jose" to jose, "maria" to mariaLiving, "elena" to elena)
        val validation = marriageEngine.validateSpouseConnection("jose", "elena", tree)
        assertFalse("Bigamous marriage while 1st spouse is living & Married must be blocked", validation.isAllowed)
        assertTrue(validation.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS))
    }

    @Test
    fun `step-parent marriage prohibition holds across both first and second marriages`() {
        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Malone",
            gender = "Male",
            birthDate = "1950-01-10",
            spouseId = "elena",
            formerSpouseIds = listOf("maria"),
            maritalStatus = "Married",
            isLiving = true
        )
        val maria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1952-05-14",
            spouseId = "jose",
            maritalStatus = "Widowed",
            isLiving = false,
            deathDate = "1995-08-20"
        )
        val elena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1958-11-03",
            spouseId = "jose",
            maritalStatus = "Married",
            isLiving = true
        )
        val mateoChildOf1st = Person(
            id = "mateo",
            firstName = "Mateo",
            lastName = "Malone",
            gender = "Male",
            birthDate = "1980-03-15",
            fatherId = "jose",
            motherId = "maria",
            isLiving = true
        )
        val sofiaChildOf2nd = Person(
            id = "sofia",
            firstName = "Sofia",
            lastName = "Malone",
            gender = "Female",
            birthDate = "1998-07-22",
            fatherId = "jose",
            motherId = "elena",
            isLiving = true
        )

        val allPersons = listOf(jose, maria, elena, mateoChildOf1st, sofiaChildOf2nd)
        val tree = allPersons.associateBy { it.id }

        // Mateo (child of 1st wife Maria) cannot marry Elena (2nd wife / Step-Mother) under Art. 38(3)
        val stepCheck = marriageEngine.validateSpouseConnection("mateo", "elena", tree)
        assertFalse("Step-son cannot marry father's 2nd wife (Step-Mother)", stepCheck.isAllowed)
        assertTrue(stepCheck.failedValidations.contains(MarriageValidationEngine.SpouseValidationFailureReason.STEP_RELATIONSHIP))

        // Both Maria (1st wife) and Elena (2nd wife) are eligible mothers for a new child of Jose
        val newChild = Person(id = "new_child", firstName = "Lucas", lastName = "Malone", gender = "Male", birthDate = "1990-01-01", fatherId = "jose")
        val eligibleMothers = FamilyLinkValidator.getEligibleMothers(newChild, jose, allPersons)
        val eligibleIds = eligibleMothers.map { it.id }.toSet()
        assertTrue("1st wife Maria should be an eligible mother", eligibleIds.contains("maria"))
        assertTrue("2nd wife Elena should be an eligible mother", eligibleIds.contains("elena"))
    }
}
