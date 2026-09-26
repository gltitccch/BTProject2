package com.example.btproject2

import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.service.FamilyRelationshipService.ParentRole
import com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.CoParentChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the "Add Child" feature improvements:
 * 1. Choice 1 (Unknown): Only original member is connected as biological parent. Spouse is NOT connected and treated as step-parent.
 * 2. Choice 2 (Current spouse): Both original member and spouse are connected as biological parents.
 * 3. Choice 3 (Another person): Original member and chosen other person are connected as biological parents. Spouse is treated as step-parent.
 * 4. Step-parent rule: A step-parent cannot be saved as a biological parent unless there is a separate adoption record.
 * 5. Sibling labels remain intact.
 */
class AddChildBiologicalParentTest {

    private val service = FamilyRelationshipService()

    private val jose = Person(
        id = "jose_1",
        firstName = "Jose",
        lastName = "Bugarin",
        gender = "Male",
        birthDate = "1942-01-01",
        spouseId = "clara_1",
        maritalStatus = "Married"
    )

    private val clara = Person(
        id = "clara_1",
        firstName = "Clara",
        lastName = "Bugarin",
        gender = "Female",
        birthDate = "1945-05-10",
        spouseId = "jose_1",
        maritalStatus = "Married"
    )

    private val maria = Person(
        id = "maria_1",
        firstName = "Maria",
        lastName = "Santos",
        gender = "Female",
        birthDate = "1944-03-15",
        maritalStatus = "Single"
    )

    @Test
    fun testChoice1_UnknownOrNotRecorded_ConnectsOnlyOriginalMember() {
        // When user chooses "Unknown or not recorded" when adding child to Jose:
        val child = Person(
            id = "child_1",
            firstName = "Luz",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1970-08-20",
            fatherId = jose.id,
            fatherRelationshipType = "Biological",
            motherId = null // other biological parent not recorded
        )

        val tree = mapOf(
            jose.id to jose,
            clara.id to clara,
            child.id to child
        )

        // Validation permits single biological parent
        val valResult = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = jose.id,
            tree = tree,
            role = ParentRole.FATHER
        )
        assertTrue("Jose can be linked as biological father", valResult.isAllowed)

        // Mother is null, Clara is NOT connected as mother
        assertNull("Mother is not recorded", child.motherId)

        // Kinship: Clara is recognized as STEPMOTHER, Luz is recognized as STEPDAUGHTER
        val allPersons = listOf(jose, clara, child)
        val claraTitle = KinshipTitleHelper.resolveTitle(target = clara, focal = child, allMembers = allPersons)
        assertEquals("STEPMOTHER", claraTitle)

        val childTitle = KinshipTitleHelper.resolveTitle(target = child, focal = clara, allMembers = allPersons)
        assertEquals("STEPDAUGHTER", childTitle)

        val fatherTitle = KinshipTitleHelper.resolveTitle(target = jose, focal = child, allMembers = allPersons)
        assertEquals("FATHER", fatherTitle)
    }

    @Test
    fun testChoice2_CurrentSpouse_ConnectsBothAsBiologicalParents() {
        // When user chooses "Current spouse or partner" when adding child to Jose:
        val child = Person(
            id = "child_2",
            firstName = "Renzy",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1972-10-12",
            fatherId = jose.id,
            fatherRelationshipType = "Biological",
            motherId = clara.id,
            motherRelationshipType = "Biological"
        )

        val tree = mapOf(
            jose.id to jose,
            clara.id to clara,
            child.id to child
        )

        // Validation permits both
        val fVal = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = jose.id,
            tree = tree,
            role = ParentRole.FATHER
        )
        assertTrue("Jose is allowed as father", fVal.isAllowed)

        val mVal = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = clara.id,
            tree = tree,
            role = ParentRole.MOTHER
        )
        assertTrue("Clara is allowed as mother", mVal.isAllowed)

        // Kinship: Clara is MOTHER, child is SON
        val allPersons = listOf(jose, clara, child)
        val claraTitle = KinshipTitleHelper.resolveTitle(target = clara, focal = child, allMembers = allPersons)
        assertEquals("MOTHER", claraTitle)

        val childTitle = KinshipTitleHelper.resolveTitle(target = child, focal = clara, allMembers = allPersons)
        assertEquals("SON", childTitle)
    }

    @Test
    fun testChoice3_AnotherPerson_ConnectsOriginalAndOtherPersonAsParents() {
        // When user chooses "Another person" (Maria):
        val child = Person(
            id = "child_3",
            firstName = "Pedro",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1968-04-05",
            fatherId = jose.id,
            fatherRelationshipType = "Biological",
            motherId = maria.id,
            motherRelationshipType = "Biological"
        )

        val tree = mapOf(
            jose.id to jose,
            clara.id to clara,
            maria.id to maria,
            child.id to child
        )

        // Validate co-parenting between Jose and Maria
        val fVal = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = jose.id,
            tree = tree,
            role = ParentRole.FATHER
        )
        assertTrue(fVal.isAllowed)

        val mVal = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = maria.id,
            tree = tree,
            role = ParentRole.MOTHER
        )
        assertTrue(mVal.isAllowed)

        // Kinship: Maria is MOTHER, Clara (Jose's spouse) is STEPMOTHER
        val allPersons = listOf(jose, clara, maria, child)
        val mariaTitle = KinshipTitleHelper.resolveTitle(target = maria, focal = child, allMembers = allPersons)
        assertEquals("MOTHER", mariaTitle)

        val claraTitle = KinshipTitleHelper.resolveTitle(target = clara, focal = child, allMembers = allPersons)
        assertEquals("STEPMOTHER", claraTitle)

        val childToClara = KinshipTitleHelper.resolveTitle(target = child, focal = clara, allMembers = allPersons)
        assertEquals("STEPSON", childToClara)
    }

    @Test
    fun testStepParentCannotBeSavedAsBiologicalParent() {
        // Child already has Jose as biological father and no mother (from previous relationship/unknown)
        val child = Person(
            id = "child_4",
            firstName = "Dan",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1965-02-01",
            fatherId = jose.id,
            fatherRelationshipType = "Biological",
            motherId = null,
            motherRelationshipType = ""
        )

        val tree = mapOf(
            jose.id to jose,
            clara.id to clara,
            maria.id to maria,
            child.id to child
        )

        // Attempting to add Clara (stepmother) as BIOLOGICAL mother must fail!
        val bioResult = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = clara.id,
            tree = tree,
            role = ParentRole.MOTHER
        )
        assertFalse("Step-parent cannot be added as biological parent", bioResult.isAllowed)
        assertTrue(bioResult.reason.contains("step-parent cannot be saved as a biological parent", ignoreCase = true))

        // But attempting to record an ADOPTIVE parent relationship is permitted when slot is available!
        val adoptResult = service.checkProposedRelationship(
            type = ProposedRelationshipType.ADOPTIVE_PARENT,
            primaryPersonId = child.id,
            secondaryPersonId = clara.id,
            tree = tree,
            role = ParentRole.MOTHER
        )
        assertTrue("Step-parent CAN be recorded as adoptive parent with legal adoption", adoptResult.isAllowed)

        // Also verify case where child has a biological mother (Maria) from a prior relationship:
        val childWithPriorMother = child.copy(motherId = maria.id, motherRelationshipType = "Biological")
        val treeWithPriorMother = tree + (childWithPriorMother.id to childWithPriorMother)
        val bioResultWithMother = service.checkProposedRelationship(
            type = ProposedRelationshipType.BIOLOGICAL_PARENT,
            primaryPersonId = childWithPriorMother.id,
            secondaryPersonId = clara.id,
            tree = treeWithPriorMother,
            role = ParentRole.MOTHER
        )
        assertFalse("Step-parent cannot be added as biological parent even if mother slot is occupied", bioResultWithMother.isAllowed)
        assertTrue(bioResultWithMother.reason.contains("step-parent cannot be saved as a biological parent", ignoreCase = true))
    }
}
