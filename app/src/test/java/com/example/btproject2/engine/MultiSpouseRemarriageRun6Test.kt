package com.example.btproject2.engine

import com.example.btproject2.models.Person
import com.example.btproject2.ui.activities.ExportTreeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSpouseRemarriageRun6Test {

    @Test
    fun timelineGenerator_includesPriorMarriageWidowhoodAndSecondMarriageInChronologicalOrder() {
        val maria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1972-04-10",
            isLiving = false,
            deathDate = "2004-03-15",
            maritalStatus = "Married",
            marriageDate = "1995-05-01",
            spouseId = "jose"
        )
        val elena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1975-08-20",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "2006-06-01",
            spouseId = "jose"
        )
        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1970-01-15",
            isLiving = true,
            maritalStatus = "Married",
            marriageDate = "2006-06-01",
            spouseId = "elena",
            formerSpouseIds = listOf("maria")
        )
        val child1 = Person(
            id = "child1",
            firstName = "Carlo",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1998-09-12",
            fatherId = "jose",
            motherId = "maria"
        )
        val child2 = Person(
            id = "child2",
            firstName = "Sofia",
            lastName = "Santos",
            gender = "Female",
            birthDate = "2008-11-03",
            fatherId = "jose",
            motherId = "elena"
        )

        val generator = TimelineGenerator()
        val events = generator.generate(
            person = jose,
            allPersons = listOf(jose, maria, elena, child1, child2),
            children = listOf(child1, child2)
        )

        val descriptions = events.map { "${it.year}: ${it.description}" }
        assertTrue(descriptions.any { it.contains("1995") && it.contains("Married Maria Reyes (Prior marriage)") })
        assertTrue(descriptions.any { it.contains("1998") && it.contains("Child born: Carlo Santos") })
        assertTrue(descriptions.any { it.contains("2004") && it.contains("Widowed (Passing of Maria Reyes)") })
        assertTrue(descriptions.any { it.contains("2006") && it.contains("Married Elena Cruz") })
        assertTrue(descriptions.any { it.contains("2008") && it.contains("Child born: Sofia Santos") })
    }

    @Test
    fun exportTreeActivity_buildGedcomContent_exportsBothFirstAndSecondMarriagesAsSeparateFamBlocks() {
        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1970-01-15",
            spouseId = "elena",
            formerSpouseIds = listOf("maria"),
            marriageDate = "2006-06-01"
        )
        val maria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1972-04-10",
            isLiving = false,
            deathDate = "2004-03-15",
            spouseId = "jose",
            marriageDate = "1995-05-01"
        )
        val elena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1975-08-20",
            isLiving = true,
            spouseId = "jose",
            marriageDate = "2006-06-01"
        )
        val child1 = Person(
            id = "child1",
            firstName = "Carlo",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1998-09-12",
            fatherId = "jose",
            motherId = "maria"
        )
        val child2 = Person(
            id = "child2",
            firstName = "Sofia",
            lastName = "Santos",
            gender = "Female",
            birthDate = "2008-11-03",
            fatherId = "jose",
            motherId = "elena"
        )

        val gedcom = ExportTreeActivity.buildGedcomContent(listOf(jose, maria, elena, child1, child2))

        // Must contain two distinct FAM records: @F1@ and @F2@
        assertTrue(gedcom.contains("0 @F1@ FAM"))
        assertTrue(gedcom.contains("0 @F2@ FAM"))
        // Jose must reference both FAMS @F1@ and FAMS @F2@
        assertTrue(gedcom.contains("1 FAMS @F1@"))
        assertTrue(gedcom.contains("1 FAMS @F2@"))
        // Both marriage dates must be preserved in the GEDCOM output
        assertTrue(gedcom.contains("2 DATE 1995-05-01"))
        assertTrue(gedcom.contains("2 DATE 2006-06-01"))
    }

    @Test
    fun profileCompletenessChecker_givesFullMaritalScoreWhenFormerSpouseRecorded() {
        val widowedWithFormerSpouse = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1970-01-15",
            fatherId = "f1",
            motherId = "m1",
            maritalStatus = "Single", // even if status string wasn't updated, formerSpouseIds proves marital info is present
            spouseId = null,
            formerSpouseIds = listOf("maria"),
            biography = "Family patriarch."
        )
        val checker = ProfileCompletenessChecker()
        val res = checker.check(widowedWithFormerSpouse)
        assertEquals(100, res.score)
        assertTrue(res.missingFields.isEmpty())
    }
}
