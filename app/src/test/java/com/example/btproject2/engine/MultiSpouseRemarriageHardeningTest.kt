package com.example.btproject2.engine

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSpouseRemarriageHardeningTest {

    private val engine = MarriageValidationEngine()

    @Test
    fun remarriage_newWifeMarriageDateEarlierThanFirstWifeMarriage_isBlockedUnderArt35_4() {
        val firstWife = Person(
            id = "wife1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-03-10",
            isLiving = false,
            deathDate = "2010-08-20",
            maritalStatus = "Married",
            marriageDate = "2000-06-15",
            spouseId = "husband"
        )
        val widower = Person(
            id = "husband",
            firstName = "Juan",
            lastName = "Dela Cruz",
            gender = "Male",
            birthDate = "1970-01-15",
            isLiving = true,
            maritalStatus = "Widowed",
            marriageDate = "2000-06-15",
            spouseId = "wife1"
        )
        // New wife added with a marriage date (1995-05-01) EARLIER than the 1st wife's marriage (2000-06-15)
        val newWifeEarlierDate = Person(
            id = "wife2",
            firstName = "Elena",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1974-04-12",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "1995-05-01",
            spouseId = "husband"
        )

        val treeMap = mapOf(
            firstWife.id to firstWife,
            widower.id to widower,
            newWifeEarlierDate.id to newWifeEarlierDate
        )

        val result = engine.validateSpouseConnection("husband", "wife2", treeMap)
        assertFalse(
            "Remarriage with a marriage date earlier than the 1st marriage must be blocked",
            result.isAllowed
        )
        assertTrue(
            "Error message must explain the chronological & legal conflict with the 1st marriage",
            result.failureMessages.any { it.contains("Chronological & Legal Conflict") && it.contains("Maria Santos") }
        )
    }

    @Test
    fun remarriage_newWifeMarriageDateBeforeFirstWifeDeath_isBlockedAsBigamousOverlap() {
        val firstWife = Person(
            id = "wife1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-03-10",
            isLiving = false,
            deathDate = "2010-08-20",
            maritalStatus = "Married",
            marriageDate = "2000-06-15",
            spouseId = "husband"
        )
        val widower = Person(
            id = "husband",
            firstName = "Juan",
            lastName = "Dela Cruz",
            gender = "Male",
            birthDate = "1970-01-15",
            isLiving = true,
            maritalStatus = "Widowed",
            marriageDate = "2000-06-15",
            spouseId = "wife1"
        )
        // New wife added with a marriage date (2005-05-01) while 1st wife was still alive (died 2010-08-20)
        val newWifeOverlapping = Person(
            id = "wife2",
            firstName = "Elena",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1978-04-12",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "2005-05-01",
            spouseId = "husband"
        )

        val treeMap = mapOf(
            firstWife.id to firstWife,
            widower.id to widower,
            newWifeOverlapping.id to newWifeOverlapping
        )

        val result = engine.validateSpouseConnection("husband", "wife2", treeMap)
        assertFalse(
            "Remarriage dated before the 1st spouse's death must be blocked as bigamous under Art. 35(4)",
            result.isAllowed
        )
        assertTrue(
            "Error message must cite that prior spouse Maria Santos was still living until 2010-08-20",
            result.failureMessages.any { it.contains("2010-08-20") && it.contains("Article 35(4)") }
        )
    }

    @Test
    fun remarriage_newWifeMarriageDateAfterFirstWifeDeath_isAllowed() {
        val firstWife = Person(
            id = "wife1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-03-10",
            isLiving = false,
            deathDate = "2010-08-20",
            maritalStatus = "Married",
            marriageDate = "2000-06-15",
            spouseId = "husband"
        )
        val widower = Person(
            id = "husband",
            firstName = "Juan",
            lastName = "Dela Cruz",
            gender = "Male",
            birthDate = "1970-01-15",
            isLiving = true,
            maritalStatus = "Widowed",
            marriageDate = "2000-06-15",
            spouseId = "wife1"
        )
        // New wife married in 2014-02-14 (strictly after 1st wife's passing on 2010-08-20)
        val newWifeValid = Person(
            id = "wife2",
            firstName = "Elena",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1978-04-12",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "2014-02-14",
            spouseId = "husband"
        )

        val treeMap = mapOf(
            firstWife.id to firstWife,
            widower.id to widower,
            newWifeValid.id to newWifeValid
        )

        val result = engine.validateSpouseConnection("husband", "wife2", treeMap)
        assertTrue(
            "Remarriage after the 1st spouse's death must be allowed: ${result.reason}",
            result.isAllowed
        )
    }

    @Test
    fun retroactiveDeceasedFirstSpouse_addedAfterSecondMarriage_isAllowedAndPreservesActiveSpouse() {
        val husband = Person(
            id = "husband",
            firstName = "Juan",
            lastName = "Dela Cruz",
            gender = "Male",
            birthDate = "1970-01-15",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "2015-06-01",
            spouseId = "wife2"
        )
        val currentSecondWife = Person(
            id = "wife2",
            firstName = "Elena",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1978-04-12",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "2015-06-01",
            spouseId = "husband"
        )
        val retroactiveDeceasedFirstWife = Person(
            id = "wife1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-03-10",
            isLiving = false,
            deathDate = "2010-08-20",
            maritalStatus = "Married",
            marriageDate = "1998-05-10",
            spouseId = "husband"
        )

        val treeMap = mapOf(
            husband.id to husband,
            currentSecondWife.id to currentSecondWife,
            retroactiveDeceasedFirstWife.id to retroactiveDeceasedFirstWife
        )

        val validation = engine.validateSpouseConnection("husband", "wife1", treeMap)
        assertTrue(
            "Retroactively linking a deceased 1st spouse whose death preceded the 2nd marriage must be allowed: ${validation.reason}",
            validation.isAllowed
        )

        val updatedHusband = husband.withAddedFormerSpouse("wife1")
        assertEquals("wife2", updatedHusband.spouseId)
        assertEquals(listOf("wife1"), updatedHusband.formerSpouseIds)
    }

    @Test
    fun planPersonUpdate_editingDeceasedFirstSpouse_doesNotResetRemarriedPartnerToWidowed() {
        val deceasedFirstWife = Person(
            id = "wife1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            isLiving = false,
            deathDate = "2010-08-20",
            maritalStatus = "Married",
            spouseId = "husband",
            treeId = "tree_1"
        )
        val remarriedHusband = Person(
            id = "husband",
            firstName = "Juan",
            lastName = "Dela Cruz",
            gender = "Male",
            isLiving = true,
            maritalStatus = "Married",
            spouseId = "wife2",
            formerSpouseIds = listOf("wife1"),
            treeId = "tree_1"
        )
        val secondWife = Person(
            id = "wife2",
            firstName = "Elena",
            lastName = "Reyes",
            gender = "Female",
            isLiving = true,
            maritalStatus = "Married",
            spouseId = "husband",
            treeId = "tree_1"
        )

        val plan = FirestoreHelper.planPersonUpdate(
            person = deceasedFirstWife.copy(biography = "Updated biography"),
            cache = listOf(deceasedFirstWife, remarriedHusband, secondWife)
        )
        assertNull(
            "Editing a deceased 1st spouse must NOT overwrite the remarried partner's maritalStatus to Widowed",
            plan.partnerStatusUpdate
        )
    }
}
