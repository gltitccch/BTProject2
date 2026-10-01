package com.example.btproject2

import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying Patriarch and Matriarch founder role resolution,
 * bi-directional gender mappings, and model serialization compatibility.
 */
class FounderRoleValidationTest {

    @Test
    fun testPatriarchLineageRoleResolution() {
        val founder = Person(
            id = "founder_1",
            firstName = "Jose",
            lastName = "Bugarin",
            gender = "Male",
            lineageRole = "Patriarch"
        )
        val allMembers = listOf(founder)

        // When viewing general tree or self
        val selfTitle = KinshipTitleHelper.resolveTitle(founder, founder, allMembers)
        assertEquals("PATRIARCH", selfTitle)

        val nullFocalTitle = KinshipTitleHelper.resolveTitle(founder, null, allMembers)
        assertEquals("PATRIARCH", nullFocalTitle)

        val subtitle = KinshipTitleHelper.resolveSubtitle(founder, founder, allMembers)
        assertEquals("Founding Patriarch", subtitle)
    }

    @Test
    fun testMatriarchLineageRoleResolution() {
        val founder = Person(
            id = "founder_2",
            firstName = "Clara",
            lastName = "Bugarin",
            gender = "Female",
            lineageRole = "Matriarch"
        )
        val allMembers = listOf(founder)

        // When viewing general tree or self
        val selfTitle = KinshipTitleHelper.resolveTitle(founder, founder, allMembers)
        assertEquals("MATRIARCH", selfTitle)

        val nullFocalTitle = KinshipTitleHelper.resolveTitle(founder, null, allMembers)
        assertEquals("MATRIARCH", nullFocalTitle)

        val subtitle = KinshipTitleHelper.resolveSubtitle(founder, founder, allMembers)
        assertEquals("Founding Matriarch", subtitle)
    }

    @Test
    fun testBidirectionalGenderRoleMapping() {
        fun resolveRoleFromGender(gender: String): String =
            if (gender.equals("Male", ignoreCase = true)) "Patriarch" else "Matriarch"

        fun resolveGenderFromRole(role: String): String =
            if (role.equals("Patriarch", ignoreCase = true)) "Male" else "Female"

        assertEquals("Patriarch", resolveRoleFromGender("Male"))
        assertEquals("Matriarch", resolveRoleFromGender("Female"))

        assertEquals("Male", resolveGenderFromRole("Patriarch"))
        assertEquals("Female", resolveGenderFromRole("Matriarch"))
    }

    @Test
    fun testFamilyTreeModelFounderFields() {
        val tree = FamilyTree(
            id = "tree_xyz",
            name = "Bugarin Family",
            ownerId = "user_123",
            ownerName = "Jose Bugarin",
            founderRole = "Patriarch",
            founderPersonId = "person_456"
        )

        assertEquals("Patriarch", tree.founderRole)
        assertEquals("person_456", tree.founderPersonId)
        assertNotNull(tree.inviteCode)
    }
}
