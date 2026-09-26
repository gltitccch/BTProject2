package com.example.btproject2

import com.example.btproject2.engine.BloodRelationshipEngine
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.utils.SpouseValidationMessageHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * End-to-end integration and connection test suite for Spouse-Relationship Validation Rules.
 * Verifies that the rules are rigorously connected and return user-friendly messages across
 * all required categories:
 * - Age validation (both underage, single underage)
 * - Gender validation (opposite-sex requirement under Philippine Family Code Arts. 1 & 2)
 * - Existing spouse validation (bigamy prohibition under Arts. 35(4) & 40)
 * - Consanguinity validation (lineal, sibling, collateral <= 4th degree)
 * - Self-marriage prevention
 * - Multi-error message aggregation
 */
class SpouseRelationshipConnectionTest {

    private lateinit var bloodEngine: BloodRelationshipEngine
    private lateinit var marriageEngine: MarriageValidationEngine
    private lateinit var familyService: FamilyRelationshipService

    @Before
    fun setUp() {
        bloodEngine = BloodRelationshipEngine()
        marriageEngine = MarriageValidationEngine(bloodEngine)
        familyService = FamilyRelationshipService(bloodEngine, marriageEngine)
    }

    // =========================================================================
    // 1. AGE VALIDATION TESTS
    // =========================================================================

    @Test
    fun testBothUnderage_returnsExactUserFriendlyMessage() {
        // User's example: 16-year-old male and another 16-year-old
        val teenA = Person(id = "t1", firstName = "Mark", lastName = "Cruz", gender = "Male", birthDate = "2010-01-01")
        val teenB = Person(id = "t2", firstName = "Ana", lastName = "Santos", gender = "Female", birthDate = "2010-06-01")

        val tree = mapOf(teenA.id to teenA, teenB.id to teenB)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = teenA.id,
            secondaryPersonId = teenB.id,
            tree = tree
        )

        assertFalse("Both underage must be blocked", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(teenA, teenB, result)
        assertTrue(
            "Must contain exact requirement: Unable to add this person as a spouse because both members do not meet the minimum age requirement.",
            errorMsg.contains("Unable to add this person as a spouse because both members do not meet the minimum age requirement.")
        )
    }

    @Test
    fun testSingleUnderage_returnsSpecificUnderageMessage() {
        val adult = Person(id = "a1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1995-01-01")
        val minor = Person(id = "m1", firstName = "Clara", lastName = "Reyes", gender = "Female", birthDate = "2010-05-15")

        val tree = mapOf(adult.id to adult, minor.id to minor)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = adult.id,
            secondaryPersonId = minor.id,
            tree = tree
        )

        assertFalse("Underage minor marriage must be blocked", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(adult, minor, result)
        assertTrue(
            "Must state that minor does not meet the minimum age requirement",
            errorMsg.contains("Unable to add this person as a spouse because Clara Reyes does not meet the minimum age requirement.")
        )
    }

    // =========================================================================
    // 2. GENDER VALIDATION TESTS
    // =========================================================================

    @Test
    fun testSameGender_whenUnsupported_returnsStandardizedMessage() {
        val manA = Person(id = "m1", firstName = "Renzy", lastName = "Bugarin", gender = "Male", birthDate = "1995-01-01")
        val manB = Person(id = "m2", firstName = "Carlo", lastName = "Mendoza", gender = "Male", birthDate = "1994-05-20")

        val tree = mapOf(manA.id to manA, manB.id to manB)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = manA.id,
            secondaryPersonId = manB.id,
            tree = tree
        )

        assertFalse("Same-gender must be blocked by default under Philippine Family Code", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(manA, manB, result)
        assertTrue(
            "Must contain: Unable to add this person as a spouse because this relationship is not allowed under the current gender relationship rules.",
            errorMsg.contains("Unable to add this person as a spouse because this relationship is not allowed under the current gender relationship rules.")
        )
    }

    // =========================================================================
    // 3. EXISTING SPOUSE / BIGAMY VALIDATION TESTS
    // =========================================================================

    @Test
    fun testActiveSpouse_returnsStandardizedMessage() {
        val husband = Person(id = "h1", firstName = "Eduardo", lastName = "Santos", gender = "Male", birthDate = "1990-01-01", spouseId = "w1", maritalStatus = "Married")
        val wife = Person(id = "w1", firstName = "Elena", lastName = "Santos", gender = "Female", birthDate = "1992-01-01", spouseId = "h1", maritalStatus = "Married")
        val candidate = Person(id = "c1", firstName = "Grace", lastName = "Perez", gender = "Female", birthDate = "1993-01-01")

        val tree = mapOf(husband.id to husband, wife.id to wife, candidate.id to candidate)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = husband.id,
            secondaryPersonId = candidate.id,
            tree = tree
        )

        assertFalse("Active spouse bigamy must be blocked", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(husband, candidate, result)
        assertTrue(
            "Must contain: Unable to add this person as a spouse because one member already has an active spouse relationship.",
            errorMsg.contains("Unable to add this person as a spouse because one member already has an active spouse relationship.")
        )
    }

    // =========================================================================
    // 4. BLOOD RELATIONSHIP VALIDATION TESTS
    // =========================================================================

    @Test
    fun testBloodRelatives_lineal_returnsDirectlyRelatedByBloodMessage() {
        val parent = Person(id = "p1", firstName = "Fernando", lastName = "Perez", gender = "Male", birthDate = "1970-01-01")
        val child = Person(id = "c1", firstName = "Luz", lastName = "Perez", gender = "Female", birthDate = "1995-01-01", fatherId = "p1")

        val tree = mapOf(parent.id to parent, child.id to child)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = parent.id,
            secondaryPersonId = child.id,
            tree = tree
        )

        assertFalse("Lineal blood relatives cannot marry", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(parent, child, result)
        assertTrue(
            "Must state: Unable to add this person as a spouse because they are directly related by blood.",
            errorMsg.contains("Unable to add this person as a spouse because they are directly related by blood.")
        )
    }

    @Test
    fun testBloodRelatives_siblings_returnsDirectlyRelatedByBloodMessage() {
        val brother = Person(id = "b1", firstName = "Renzy", lastName = "Bugarin", gender = "Male", birthDate = "1995-01-01", fatherId = "f1")
        val sister = Person(id = "s1", firstName = "Clara", lastName = "Bugarin", gender = "Female", birthDate = "1998-01-01", fatherId = "f1")
        val father = Person(id = "f1", firstName = "Antonio", lastName = "Bugarin", gender = "Male", birthDate = "1970-01-01")

        val tree = mapOf(brother.id to brother, sister.id to sister, father.id to father)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = brother.id,
            secondaryPersonId = sister.id,
            tree = tree
        )

        assertFalse("Siblings cannot marry", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(brother, sister, result)
        assertTrue(
            "Must state: Unable to add this person as a spouse because they are directly related by blood.",
            errorMsg.contains("Unable to add this person as a spouse because they are directly related by blood.")
        )
    }

    @Test
    fun testBloodRelatives_firstCousins_returnsDirectlyRelatedByBloodMessage() {
        val grandFather = Person(id = "gf", firstName = "Lolo", lastName = "Tan", gender = "Male", birthDate = "1940-01-01")
        val aunt = Person(id = "aunt", firstName = "Tita", lastName = "Tan", gender = "Female", birthDate = "1965-01-01", fatherId = "gf")
        val uncle = Person(id = "uncle", firstName = "Tito", lastName = "Tan", gender = "Male", birthDate = "1968-01-01", fatherId = "gf")
        val cousinA = Person(id = "cA", firstName = "CousinA", lastName = "Reyes", gender = "Male", birthDate = "1992-01-01", motherId = "aunt")
        val cousinB = Person(id = "cB", firstName = "CousinB", lastName = "Tan", gender = "Female", birthDate = "1994-01-01", fatherId = "uncle")

        val tree = mapOf(
            grandFather.id to grandFather,
            aunt.id to aunt,
            uncle.id to uncle,
            cousinA.id to cousinA,
            cousinB.id to cousinB
        )

        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = cousinA.id,
            secondaryPersonId = cousinB.id,
            tree = tree
        )

        assertFalse("First cousins cannot marry under Philippine Family Code Article 38(1)", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(cousinA, cousinB, result)
        assertTrue(
            "Must state: Unable to add this person as a spouse because they are directly related by blood.",
            errorMsg.contains("Unable to add this person as a spouse because they are directly related by blood.")
        )
    }

    // =========================================================================
    // 5. SELF-MARRIAGE TEST
    // =========================================================================

    @Test
    fun testSelfMarriage_returnsSelfMarriageMessage() {
        val person = Person(id = "p1", firstName = "Renzy", lastName = "Bugarin", gender = "Male", birthDate = "1995-01-01")
        val tree = mapOf(person.id to person)

        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = person.id,
            secondaryPersonId = person.id,
            tree = tree
        )

        assertFalse("Self-marriage must be blocked", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(person, person, result)
        assertTrue(
            "Must explain person cannot marry themselves",
            errorMsg.contains("Unable to add this person as a spouse because a person cannot marry themselves.")
        )
    }

    // =========================================================================
    // 6. MULTI-ERROR AGGREGATION TEST (USER'S 16YO MALE + 16YO MALE EXAMPLE)
    // =========================================================================

    @Test
    fun testMultiErrorAggregation_two16YearOldMales() {
        // Two 16-year-old males: violates BOTH minimum age AND same-gender marriage rules
        val boy1 = Person(id = "b1", firstName = "Juan", lastName = "Santos", gender = "Male", birthDate = "2010-01-01")
        val boy2 = Person(id = "b2", firstName = "Pedro", lastName = "Cruz", gender = "Male", birthDate = "2010-02-01")

        val tree = mapOf(boy1.id to boy1, boy2.id to boy2)
        val result = familyService.checkProposedRelationship(
            type = ProposedRelationshipType.SPOUSE,
            primaryPersonId = boy1.id,
            secondaryPersonId = boy2.id,
            tree = tree
        )

        assertFalse("Must be blocked", result.isAllowed)
        val errorMsg = SpouseValidationMessageHelper.formatErrorMessage(boy1, boy2, result)

        // Both reasons must be returned so the user understands everything that needs to be resolved
        assertTrue(
            "Must explain both members do not meet minimum age",
            errorMsg.contains("Unable to add this person as a spouse because both members do not meet the minimum age requirement.")
        )
        assertTrue(
            "Must explain same-gender relationship rule",
            errorMsg.contains("Unable to add this person as a spouse because this relationship is not allowed under the current gender relationship rules.")
        )
    }
}

