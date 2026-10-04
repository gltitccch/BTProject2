package com.example.btproject2

import com.example.btproject2.engine.MergeBridgeValidator
import com.example.btproject2.models.Person
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MergeBridgeValidatorTest {

    private lateinit var validator: MergeBridgeValidator

    @Before
    fun setUp() {
        validator = MergeBridgeValidator()
    }

    // =========================================================================
    // 1. 💍 SPOUSAL UNION TESTS
    // =========================================================================

    @Test
    fun testSpousalUnion_validCouple_allowed() {
        val groom = Person(
            id = "g1",
            firstName = "John",
            lastName = "Doe",
            gender = "Male",
            birthDate = "1990-01-01",
            maritalStatus = "Single"
        )
        val bride = Person(
            id = "b1",
            firstName = "Jane",
            lastName = "Smith",
            gender = "Female",
            birthDate = "1992-05-15",
            maritalStatus = "Single"
        )
        val map = mapOf(groom.id to groom, bride.id to bride)

        val result = validator.validateSpousalUnion(groom, bride, map)
        assertTrue(result.isAllowed)
    }

    @Test
    fun testSpousalUnion_selfMarriage_prohibited() {
        val person = Person(id = "p1", firstName = "John", lastName = "Doe", gender = "Male")
        val map = mapOf(person.id to person)

        val result = validator.validateSpousalUnion(person, person, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("cannot marry themselves", ignoreCase = true))
    }

    @Test
    fun testSpousalUnion_bigamy_personAAlreadyMarried_prohibited() {
        val husband = Person(
            id = "h1",
            firstName = "Arthur",
            lastName = "Russell",
            gender = "Male",
            birthDate = "1930-01-01",
            spouseId = "w1",
            maritalStatus = "Married"
        )
        val existingWife = Person(
            id = "w1",
            firstName = "Eleanor",
            lastName = "Vance",
            gender = "Female",
            birthDate = "1932-01-01",
            spouseId = "h1",
            maritalStatus = "Married"
        )
        val candidateBride = Person(
            id = "c1",
            firstName = "Beatrice",
            lastName = "Hayes",
            gender = "Female",
            birthDate = "1938-01-01",
            maritalStatus = "Single"
        )
        val map = mapOf(husband.id to husband, existingWife.id to existingWife, candidateBride.id to candidateBride)

        val result = validator.validateSpousalUnion(husband, candidateBride, map)
        assertFalse("Bigamy must be strictly blocked", result.isAllowed)
        assertTrue(result.reason.contains("already married", ignoreCase = true))
        assertTrue(result.reason.contains("Article 35(4)", ignoreCase = true))
    }

    @Test
    fun testSpousalUnion_underageCandidate_prohibited() {
        val adult = Person(id = "a1", firstName = "Adult", gender = "Male", birthDate = "1995-01-01")
        val minor = Person(id = "m1", firstName = "Minor", gender = "Female", birthDate = "2015-01-01")
        val map = mapOf(adult.id to adult, minor.id to minor)

        val result = validator.validateSpousalUnion(adult, minor, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("under the age of 18", ignoreCase = true))
    }

    // =========================================================================
    // 2. 👶 PARENT-CHILD LINK TESTS
    // =========================================================================

    @Test
    fun testParentChild_validFatherAndChild_allowed() {
        val father = Person(
            id = "f1",
            firstName = "William",
            lastName = "Vance",
            gender = "Male",
            birthDate = "1935-04-10"
        )
        val daughter = Person(
            id = "d1",
            firstName = "Grace",
            lastName = "Vance",
            gender = "Female",
            birthDate = "1963-08-20"
        )
        val map = mapOf(father.id to father, daughter.id to daughter)

        val result = validator.validateParentChildLink(proposedParent = father, proposedChild = daughter, combinedMap = map)
        assertTrue(result.isAllowed)
    }

    @Test
    fun testParentChild_selfParentage_prohibited() {
        val person = Person(id = "p1", firstName = "John", lastName = "Doe", gender = "Male")
        val result = validator.validateParentChildLink(person, person, mapOf(person.id to person))
        assertFalse(result.isAllowed)
    }

    @Test
    fun testParentChild_silentFatherOverwrite_prohibited() {
        val existingFather = Person(id = "f_orig", firstName = "Original", lastName = "Father", gender = "Male")
        val child = Person(
            id = "c1",
            firstName = "Child",
            lastName = "Doe",
            fatherId = "f_orig"
        )
        val intruderFather = Person(id = "f_intruder", firstName = "Intruder", lastName = "Father", gender = "Male")
        val map = mapOf(existingFather.id to existingFather, child.id to child, intruderFather.id to intruderFather)

        val result = validator.validateParentChildLink(proposedParent = intruderFather, proposedChild = child, combinedMap = map)
        assertFalse("Silent overwrite of existing father must be blocked", result.isAllowed)
        assertTrue(result.reason.contains("already has a biological father assigned", ignoreCase = true))
    }

    @Test
    fun testParentChild_chronologicalInversion_prohibited() {
        val youngerParent = Person(
            id = "yp",
            firstName = "Younger",
            lastName = "Parent",
            gender = "Male",
            birthDate = "2000-01-01"
        )
        val olderChild = Person(
            id = "oc",
            firstName = "Older",
            lastName = "Child",
            gender = "Female",
            birthDate = "1980-01-01"
        )
        val map = mapOf(youngerParent.id to youngerParent, olderChild.id to olderChild)

        val result = validator.validateParentChildLink(youngerParent, olderChild, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("Chronological Inversion", ignoreCase = true))
    }

    @Test
    fun testParentChild_insufficientBiologicalGap_prohibited() {
        val parent = Person(id = "p1", firstName = "Parent", gender = "Female", birthDate = "1990-01-01")
        val child = Person(id = "c1", firstName = "Child", gender = "Male", birthDate = "1998-01-01") // 8 yr gap
        val map = mapOf(parent.id to parent, child.id to child)

        val result = validator.validateParentChildLink(parent, child, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("Minimum biological gap", ignoreCase = true))
    }

    @Test
    fun testParentChild_circularAncestryLoop_prohibited() {
        // Ancestry chain: A is father of B, B is father of C. Propose C as parent of A!
        val grandfather = Person(id = "A", firstName = "Grandpa", gender = "Male")
        val father = Person(id = "B", firstName = "Dad", gender = "Male", fatherId = "A")
        val grandson = Person(id = "C", firstName = "Grandson", gender = "Male", fatherId = "B")
        val map = mapOf("a" to grandfather, "b" to father, "c" to grandson)

        val result = validator.validateParentChildLink(proposedParent = grandson, proposedChild = grandfather, combinedMap = map)
        assertFalse("Circular lineage loop must be blocked", result.isAllowed)
        assertTrue(result.reason.contains("Circular Ancestry Paradox", ignoreCase = true))
    }

    // =========================================================================
    // 3. 👥 SHARED ANCESTOR DEDUPLICATION TESTS
    // =========================================================================

    @Test
    fun testSharedAncestor_validMatchingProfiles_allowed() {
        val ancestorInTree1 = Person(
            id = "a1",
            firstName = "Harold",
            lastName = "Hayes",
            gender = "Male",
            birthDate = "1912-05-18",
            isLiving = false
        )
        val ancestorInTree2 = Person(
            id = "a2",
            firstName = "Harold",
            lastName = "Hayes",
            gender = "Male",
            birthDate = "1912-05-18",
            isLiving = false
        )
        val map = mapOf(ancestorInTree1.id to ancestorInTree1, ancestorInTree2.id to ancestorInTree2)

        val result = validator.validateSharedAncestor(ancestorInTree1, ancestorInTree2, map)
        assertTrue(result.isAllowed)
    }

    @Test
    fun testSharedAncestor_genderMismatch_prohibited() {
        val male = Person(id = "m1", firstName = "Alex", gender = "Male")
        val female = Person(id = "f1", firstName = "Alex", gender = "Female")
        val map = mapOf(male.id to male, female.id to female)

        val result = validator.validateSharedAncestor(male, female, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("Gender Mismatch", ignoreCase = true))
    }

    @Test
    fun testSharedAncestor_divergentBirthYears_prohibited() {
        val oldPerson = Person(id = "o1", firstName = "John", gender = "Male", birthDate = "1880-01-01")
        val newPerson = Person(id = "n1", firstName = "John", gender = "Male", birthDate = "1960-01-01")
        val map = mapOf(oldPerson.id to oldPerson, newPerson.id to newPerson)

        val result = validator.validateSharedAncestor(oldPerson, newPerson, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("Chronological Discrepancy", ignoreCase = true))
    }

    @Test
    fun testSharedAncestor_dualSpouseBigamyTrap_prohibited() {
        // Person A in Tree 1 is married to Wife 1
        val personA = Person(id = "pA", firstName = "Arthur", gender = "Male", spouseId = "w1", maritalStatus = "Married")
        val wife1 = Person(id = "w1", firstName = "Eleanor", lastName = "Vance", gender = "Female", spouseId = "pA", maritalStatus = "Married")

        // Person B in Tree 2 is married to Wife 2
        val personB = Person(id = "pB", firstName = "Arthur", gender = "Male", spouseId = "w2", maritalStatus = "Married")
        val wife2 = Person(id = "w2", firstName = "Beatrice", lastName = "Hayes", gender = "Female", spouseId = "pB", maritalStatus = "Married")

        val map = mapOf("pa" to personA, "w1" to wife1, "pb" to personB, "w2" to wife2)

        val result = validator.validateSharedAncestor(personA, personB, map)
        assertFalse("Dual-Spouse collision must be caught to prevent bigamy trap", result.isAllowed)
        assertTrue(result.reason.contains("Dual-Spouse Conflict", ignoreCase = true))
        assertTrue(result.reason.contains("Article 35(4)", ignoreCase = true))
    }

    // =========================================================================
    // 4. 👥 SIBLING & LINEAGE BRIDGE TESTS
    // =========================================================================

    @Test
    fun testSiblingBridge_validSisters_allowed() {
        val sister1 = Person(
            id = "s1",
            firstName = "Beatrice",
            lastName = "Hayes",
            gender = "Female",
            birthDate = "1938-03-12"
        )
        val sister2 = Person(
            id = "s2",
            firstName = "Clara",
            lastName = "Hayes",
            gender = "Female",
            birthDate = "1942-07-25"
        )
        val map = mapOf(sister1.id to sister1, sister2.id to sister2)

        val result = validator.validateSiblingBridge(sister1, sister2, map)
        assertTrue(result.isAllowed)
    }

    @Test
    fun testSiblingBridge_incestuousMarriedSpouses_prohibited() {
        val brother = Person(id = "b1", firstName = "Brother", gender = "Male", spouseId = "s1")
        val sister = Person(id = "s1", firstName = "Sister", gender = "Female", spouseId = "b1")
        val map = mapOf(brother.id to brother, sister.id to sister)

        val result = validator.validateSiblingBridge(brother, sister, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("Article 37(2)", ignoreCase = true))
    }

    @Test
    fun testSiblingBridge_linealAncestors_prohibited() {
        val grandmother = Person(id = "gm", firstName = "Grandmother", gender = "Female")
        val mother = Person(id = "mo", firstName = "Mother", gender = "Female", motherId = "gm")
        val granddaughter = Person(id = "gd", firstName = "Granddaughter", gender = "Female", motherId = "mo")
        val map = mapOf("gm" to grandmother, "mo" to mother, "gd" to granddaughter)

        val result = validator.validateSiblingBridge(grandmother, granddaughter, map)
        assertFalse(result.isAllowed)
        assertTrue(result.reason.contains("Generational Leap", ignoreCase = true))
    }
}
