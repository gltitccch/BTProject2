package com.example.btproject2

import com.example.btproject2.engine.ConflictScanner
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that the Universal Rule-Based Conflict & Resolution Data Engine
 * dynamically generates 3 distinct, non-hardcoded resolution options for every conflict scenario,
 * and that marking a parent as Adoptive legally resolves biological age gap errors under RA 8552.
 */
class ConflictResolutionOptionsTest {

    private val scanner = ConflictScanner()

    @Test
    fun `parent child age gap generates 3 dynamic options including legal adoption`() {
        val mariana = Person(
            id = "p_mariana",
            firstName = "Mariana",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1987-05-12"
        )
        val camila = Person(
            id = "p_camila",
            firstName = "Camila",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1998-09-20",
            motherId = "p_mariana"
        )

        val conflicts = scanner.scanAll(listOf(mariana, camila))
        assertEquals(1, conflicts.size)

        val conflict = conflicts.first()
        assertEquals("invalid_parent_child", conflict.type)
        assertEquals("motherId", conflict.field)
        assertEquals("p_mariana", conflict.relatedPersonId)

        // Verify that 3 dynamic options are generated
        assertEquals(3, conflict.options.size)

        val unlinkOpt = conflict.options.find { it.actionType == ConflictScanner.ResolutionActionType.UNLINK }
        val adoptiveOpt = conflict.options.find { it.actionType == ConflictScanner.ResolutionActionType.SET_ADOPTIVE }
        val editOpt = conflict.options.find { it.actionType == ConflictScanner.ResolutionActionType.EDIT_PROFILE }

        assertTrue("Should include safe unlink option", unlinkOpt != null)
        assertTrue("Should include legal adoption option", adoptiveOpt != null)
        assertTrue("Should include edit profile option", editOpt != null)

        assertEquals("motherRelationshipType", adoptiveOpt?.targetField)
        assertEquals("Adoptive", adoptiveOpt?.targetValue)
        assertTrue("Option tag should indicate legal adoption", adoptiveOpt?.tag?.contains("Adoption") == true)
    }

    @Test
    fun `setting adoptive relationship type bypasses biological gestation limit under RA 8552`() {
        val mariana = Person(
            id = "p_mariana",
            firstName = "Mariana",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1987-05-12"
        )
        // Biological child with 11 year age gap -> fails validation
        val bioCamila = Person(
            id = "p_camila",
            firstName = "Camila",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1998-09-20",
            motherId = "p_mariana",
            motherRelationshipType = "Biological"
        )

        val bioCheck = FamilyLinkValidator.validateParentChild(
            parent = mariana,
            child = bioCamila,
            role = "mother",
            allPersons = listOf(mariana, bioCamila)
        )
        assertFalse("Biological 11-yr gap must fail biological validation", bioCheck.isValid)
        assertTrue(bioCheck.errors.any { it.title.contains("Biologically Impossible Age Gap") })

        // Adopted daughter with 11 year gap -> passes under Philippine Adoption Law (RA 8552)
        val adoptedCamila = bioCamila.copy(motherRelationshipType = "Adoptive")
        val adoptedCheck = FamilyLinkValidator.validateParentChild(
            parent = mariana,
            child = adoptedCamila,
            role = "mother",
            allPersons = listOf(mariana, adoptedCamila)
        )
        assertTrue("Adoptive daughter must be exempt from natural gestation age limit", adoptedCheck.isValid)
    }

    @Test
    fun `broken father link generates safe clean and pick from tree options`() {
        val person = Person(
            id = "p_juan",
            firstName = "Juan",
            lastName = "Bugarin",
            gender = "Male",
            fatherId = "deleted_parent_123"
        )

        val conflicts = scanner.scanAll(listOf(person))
        val conflict = conflicts.first { it.field == "fatherId" }

        assertEquals(3, conflict.options.size)
        assertTrue(conflict.options.any { it.actionType == ConflictScanner.ResolutionActionType.UNLINK })
        assertTrue(conflict.options.any { it.actionType == ConflictScanner.ResolutionActionType.EDIT_PROFILE })
    }

    @Test
    fun `non-reciprocal marriage generates reciprocate and unlink options`() {
        val clara = Person(
            id = "p_clara",
            firstName = "Clara",
            lastName = "Bugarin",
            gender = "Female",
            spouseId = "p_mateo"
        )
        val mateo = Person(
            id = "p_mateo",
            firstName = "Mateo",
            lastName = "Bugarin",
            gender = "Male",
            spouseId = null
        )

        val conflicts = scanner.scanAll(listOf(clara, mateo))
        val conflict = conflicts.first { it.type == "non_reciprocal" }

        assertEquals(3, conflict.options.size)
        val reciprocateOpt = conflict.options.find { it.actionType == ConflictScanner.ResolutionActionType.RECIPROCATE_MARRIAGE }
        assertTrue("Should include reciprocate marriage option", reciprocateOpt != null)
        assertEquals("p_mateo", reciprocateOpt?.targetValue)
    }

    @Test
    fun `status mismatch generates civil status synchronization options`() {
        val person = Person(
            id = "p_carlos",
            firstName = "Carlos",
            lastName = "Bugarin",
            gender = "Male",
            maritalStatus = "Married",
            spouseId = null
        )

        val conflicts = scanner.scanAll(listOf(person))
        val conflict = conflicts.first { it.type == "status_mismatch" }

        assertEquals(3, conflict.options.size)
        val syncOpt = conflict.options.find { it.actionType == ConflictScanner.ResolutionActionType.SYNC_STATUS }
        assertTrue("Should include sync status option", syncOpt != null)
        assertEquals("Single", syncOpt?.targetValue)
    }
}
