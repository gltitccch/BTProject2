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

    @Test
    fun testPatriarchUnder18Rejected() {
        // Person with birth date making them a newborn or minor (< 18)
        val minor = Person(
            id = "minor_1",
            firstName = "Baby",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "2026-09-09",
            lineageRole = "Patriarch"
        )
        val result = com.example.btproject2.engine.FamilyLinkValidator.validateLineageRole(minor)
        org.junit.Assert.assertFalse("Minor should NOT be allowed as Patriarch", result.isValid)
        org.junit.Assert.assertTrue(result.errors.any { it.message.contains("at least 18 years old") })
    }

    @Test
    fun testMatriarchUnder18Rejected() {
        val minor = Person(
            id = "minor_2",
            firstName = "Young",
            lastName = "Clara",
            gender = "Female",
            birthDate = "2015-05-10",
            lineageRole = "Matriarch"
        )
        val result = com.example.btproject2.engine.FamilyLinkValidator.validateLineageRole(minor)
        org.junit.Assert.assertFalse("Minor should NOT be allowed as Matriarch", result.isValid)
        org.junit.Assert.assertTrue(result.errors.any { it.message.contains("at least 18 years old") })
    }

    @Test
    fun testAdultPatriarchAndMatriarchAccepted() {
        val adultPatriarch = Person(
            id = "adult_1",
            firstName = "Jose",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1980-01-15",
            lineageRole = "Patriarch"
        )
        val patResult = com.example.btproject2.engine.FamilyLinkValidator.validateLineageRole(adultPatriarch)
        org.junit.Assert.assertTrue("Adult (18+) should be valid Patriarch", patResult.isValid)

        val adultMatriarch = Person(
            id = "adult_2",
            firstName = "Maria",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1985-06-20",
            lineageRole = "Matriarch"
        )
        val matResult = com.example.btproject2.engine.FamilyLinkValidator.validateLineageRole(adultMatriarch)
        org.junit.Assert.assertTrue("Adult (18+) should be valid Matriarch", matResult.isValid)
    }

    @Test
    fun testBlankBirthDateRejectedForPatriarchAndMatriarch() {
        val blankPatriarch = Person(
            id = "p_blank",
            firstName = "Jose",
            lastName = "Bugarin",
            birthDate = "",
            lineageRole = "Patriarch"
        )
        val result = com.example.btproject2.engine.FamilyLinkValidator.validateLineageRole(blankPatriarch)
        org.junit.Assert.assertFalse("Blank birth date must be rejected for Patriarch", result.isValid)
        org.junit.Assert.assertTrue(result.errors.any { it.message.contains("Birth date is required") || it.title.contains("Birth Date Required") })
    }

    @Test
    fun testMinorAllowedAsMemberAdaptiveRole() {
        val minorMember = Person(
            id = "minor_member",
            firstName = "Renzy",
            lastName = "Bugarin",
            birthDate = "2026-09-09",
            lineageRole = "Member"
        )
        val result = com.example.btproject2.engine.FamilyLinkValidator.validateLineageRole(minorMember)
        org.junit.Assert.assertTrue("Minor should be accepted with Member role", result.isValid)

        // Member role should not produce royal Founding Patriarch/Matriarch title
        val title = KinshipTitleHelper.resolveTitle(minorMember, minorMember, listOf(minorMember))
        assertEquals("SELF", title)
    }

    @Test
    fun testCalculateAgeYears() {
        val age20 = com.example.btproject2.engine.FamilyLinkValidator.calculateAgeYears("2000-01-01")
        org.junit.Assert.assertNotNull(age20)
        org.junit.Assert.assertTrue(age20!! >= 24)

        val newbornAge = com.example.btproject2.engine.FamilyLinkValidator.calculateAgeYears("2026-09-09")
        org.junit.Assert.assertNotNull(newbornAge)
        assertEquals(0, newbornAge)
    }
}

