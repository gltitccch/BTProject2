package com.example.btproject2

import com.example.btproject2.engine.ConflictScanner
import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated Stress Test for the Global Friendly Question Engine & Step-by-Step Guided Family Helper.
 * Verifies that all relationship inconsistencies generate Option 2 ("Friendly Question") headlines,
 * simple everyday explanations (zero technical jargon like 'ID references a non-existent person'),
 * and actionable step-by-step wizard metadata across any account or tree size.
 */
class ConflictWizardStressTest {

    private val scanner = ConflictScanner()
    private val familyService = FamilyRelationshipService()

    @Test
    fun `broken father mother and spouse links generate friendly questions and auto-fix metadata`() {
        val renzy = Person(
            id = "p_renzy",
            firstName = "Renzy",
            lastName = "Bugarin",
            gender = "Male",
            fatherId = "deleted_father_999"
        )
        val maya = Person(
            id = "p_maya",
            firstName = "Maya Isabelle",
            lastName = "Bugarin",
            gender = "Female",
            motherId = "deleted_mother_888"
        )
        val carlos = Person(
            id = "p_carlos",
            firstName = "Carlos",
            lastName = "Bugarin",
            gender = "Male",
            spouseId = "deleted_spouse_777",
            maritalStatus = "Married"
        )

        val allPersons = listOf(renzy, maya, carlos)
        val conflicts = scanner.scanAll(allPersons)

        val fatherConflict = conflicts.first { it.personId == "p_renzy" && it.field == "fatherId" }
        assertEquals("“Who is Renzy’s father?”", fatherConflict.questionHeadline)
        assertTrue(fatherConflict.simpleExplanation.contains("deleted from the tree"))
        assertFalse(fatherConflict.description.contains("non-existent"))
        assertTrue(fatherConflict.canAutoFix)
        assertTrue(fatherConflict.primaryActionLabel.contains("Leave father blank"))

        val motherConflict = conflicts.first { it.personId == "p_maya" && it.field == "motherId" }
        assertEquals("“Who is Maya Isabelle’s mother?”", motherConflict.questionHeadline)
        assertTrue(motherConflict.simpleExplanation.contains("deleted from the tree"))
        assertTrue(motherConflict.canAutoFix)

        val spouseConflict = conflicts.first { it.personId == "p_carlos" && it.field == "spouseId" }
        assertEquals("“Who is Carlos married to?”", spouseConflict.questionHeadline)
        assertTrue(spouseConflict.simpleExplanation.contains("deleted from the tree"))
        assertTrue(spouseConflict.canAutoFix)
    }

    @Test
    fun `status mismatch and non-reciprocal marriage generate friendly questions`() {
        val marco = Person(
            id = "p_marco",
            firstName = "Marco",
            lastName = "Bugarin",
            gender = "Male",
            maritalStatus = "Married",
            spouseId = null
        )
        val clara = Person(
            id = "p_clara",
            firstName = "Clara",
            lastName = "Bugarin",
            gender = "Female",
            maritalStatus = "Married",
            spouseId = "p_mateo"
        )
        val mateo = Person(
            id = "p_mateo",
            firstName = "Mateo",
            lastName = "Bugarin",
            gender = "Male",
            maritalStatus = "Single",
            spouseId = null
        )

        val conflicts = scanner.scanAll(listOf(marco, clara, mateo))

        val marcoConflict = conflicts.first { it.personId == "p_marco" }
        assertEquals("“Who is Marco married to?”", marcoConflict.questionHeadline)
        assertTrue(marcoConflict.simpleExplanation.contains("hasn’t been picked yet"))
        assertTrue(marcoConflict.canAutoFix)

        val claraConflict = conflicts.first { it.personId == "p_clara" && it.type == "non_reciprocal" }
        assertEquals("“Are Clara and Mateo married to each other?”", claraConflict.questionHeadline)
        assertTrue(claraConflict.canAutoFix)
    }

    @Test
    fun `stress test 72-member multi-generational tree step-by-step repair transitions from 17 questions to 0`() {
        // Build 55 connected members + 16 Gen-5 children with broken parent links + 1 founder with dangling fatherId = 72 members
        val members = mutableListOf<Person>()
        members.add(Person(id = "founder_renzy", firstName = "Renzy", lastName = "Bugarin", gender = "Male", birthDate = "1995-05-10", fatherId = "missing_old_dad"))

        for (i in 1..55) {
            members.add(
                Person(
                    id = "gen_member_$i",
                    firstName = "Relative$i",
                    lastName = "Bugarin",
                    gender = if (i % 2 == 0) "Female" else "Male",
                    birthDate = "1980-01-01"
                )
            )
        }

        for (c in 1..16) {
            members.add(
                Person(
                    id = "gen5_child_$c",
                    firstName = "Child$c",
                    lastName = "Bugarin",
                    gender = if (c % 2 == 0) "Female" else "Male",
                    birthDate = "2022-03-15",
                    fatherId = if (c % 2 == 1) "wrong_parent_id_$c" else null,
                    motherId = if (c % 2 == 0) "wrong_parent_id_$c" else null
                )
            )
        }

        assertEquals(72, members.size)

        // 1. Initial scan detects all 17 broken links as Friendly Questions
        val initialConflicts = scanner.scanAll(members)
        assertEquals(17, initialConflicts.size)
        assertTrue(initialConflicts.all { it.questionHeadline.startsWith("“Who is ") })
        assertTrue(initialConflicts.all { it.canAutoFix })

        // 2. Central Tree Audit also reports Friendly Questions
        val auditReport = familyService.auditFamilyTree(members.associateBy { it.id })
        assertEquals(17, auditReport.criticalErrorCount)
        assertTrue(auditReport.criticalErrors.all { it.title.startsWith("“Who is ") })

        // 3. Simulate "Fix All at Once" / Step-by-Step completion across all 17 conflicts
        val repairedMembers = members.map { p ->
            if (p.id == "founder_renzy") {
                p.copy(fatherId = null)
            } else if (p.id.startsWith("gen5_child_")) {
                p.copy(fatherId = "gen_member_1", motherId = "gen_member_2")
            } else {
                p
            }
        }

        val postRepairConflicts = scanner.scanAll(repairedMembers)
        assertEquals("After repairing all 17 questions, 0 conflicts should remain", 0, postRepairConflicts.size)
    }
}
