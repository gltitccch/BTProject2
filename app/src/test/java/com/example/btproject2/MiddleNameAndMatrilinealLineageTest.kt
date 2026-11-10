package com.example.btproject2

import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.engine.TreeHealthCalculator
import com.example.btproject2.models.Person
import com.example.btproject2.models.UserProfile
import com.example.btproject2.ui.activities.FamilyRecordsActivity
import com.example.btproject2.utils.DuplicateDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for Middle Name, Civil Code Matrilineal Auto-Inheritance,
 * RA 11642 Adoption Lineage, Cousin Disambiguation, and Kinship Tracing.
 */
class MiddleNameAndMatrilinealLineageTest {

    // ─────────────────────────────────────────────────────────────
    // 1. UserProfile Name Formats & 3-Letter Heritage Monogram
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testUserProfileMiddleInitialAndMonogram() {
        val userWithMiddle = UserProfile(
            id = "u1",
            firstName = "Juan",
            middleName = "Santos",
            lastName = "Dela Cruz",
            suffix = "Jr.",
            displayName = "Juan Dela Cruz"
        )

        assertEquals("S.", userWithMiddle.middleInitial)
        assertEquals("Juan S. Dela Cruz Jr.", userWithMiddle.standardDisplayName)
        assertEquals("Juan Santos Dela Cruz Jr.", userWithMiddle.formalFullName)
        // 3-Letter Heritage Monogram: First (J), Middle (S), Last (D)
        assertEquals("JSD", userWithMiddle.avatarMonogram)

        val userWithoutMiddle = UserProfile(
            id = "u2",
            firstName = "Maria",
            middleName = "",
            lastName = "Clara",
            suffix = "",
            displayName = "Maria Clara"
        )

        assertEquals("", userWithoutMiddle.middleInitial)
        assertEquals("Maria Clara", userWithoutMiddle.standardDisplayName)
        assertEquals("Maria Clara", userWithoutMiddle.formalFullName)
        // 2-Letter Monogram: First (M), Last (C)
        assertEquals("MC", userWithoutMiddle.avatarMonogram)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Person Model Name Formats & Suffixes
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPersonNameFormattingAndMonogram() {
        val person = Person(
            id = "p1",
            firstName = "Andres",
            middleName = "de Castro",
            lastName = "Bonifacio",
            suffix = "III"
        )

        assertEquals("d.", person.middleInitial)
        assertEquals("Andres d. Bonifacio III", person.standardDisplayName)
        assertEquals("Andres de Castro Bonifacio III", person.formalFullName)
        assertEquals("ADB", person.avatarMonogram)

        val personNoMiddle = Person(
            id = "p2",
            firstName = "Gabriela",
            lastName = "Silang"
        )

        assertEquals("", personNoMiddle.middleInitial)
        assertEquals("Gabriela Silang", personNoMiddle.standardDisplayName)
        assertEquals("GS", personNoMiddle.avatarMonogram)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Matrilineal Auto-Inheritance (Civil Code Art. 364 & RA 11642)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testMatrilinealAutoInheritance_BiologicalMother() {
        val mother = Person(
            id = "m1",
            firstName = "Corazon",
            lastName = "Sumulong",
            gender = "Female"
        )
        val father = Person(
            id = "f1",
            firstName = "Benigno",
            lastName = "Aquino",
            gender = "Male"
        )

        // Under Article 364 of the Civil Code of the Philippines:
        // Legitimate child inherits father's surname ("Aquino") and mother's maiden surname ("Sumulong") as middle name
        val childMiddleName = mother.lastName.trim()
        val childLastName = father.lastName.trim()

        val child = Person(
            id = "c1",
            firstName = "Benigno",
            middleName = childMiddleName,
            lastName = childLastName,
            suffix = "III",
            motherId = mother.id,
            fatherId = father.id
        )

        assertEquals("Sumulong", child.middleName)
        assertEquals("Aquino", child.lastName)
        assertEquals("Benigno S. Aquino III", child.standardDisplayName)
        assertEquals("Benigno Sumulong Aquino III", child.formalFullName)
        assertEquals("BSA", child.avatarMonogram)
    }

    @Test
    fun testAdoptionLineage_RA11642AndStepparentAdoption() {
        // Case A: Full Joint Adoption under RA 11642
        val adoptiveMother = Person(
            id = "am1",
            firstName = "Judy",
            lastName = "Santos",
            gender = "Female"
        )
        val adoptiveFather = Person(
            id = "af1",
            firstName = "Ryan",
            lastName = "Agoncillo",
            gender = "Male"
        )

        val adoptedChild = Person(
            id = "ac1",
            firstName = "Johanna",
            middleName = adoptiveMother.lastName.trim(),
            lastName = adoptiveFather.lastName.trim(),
            motherId = adoptiveMother.id,
            fatherId = adoptiveFather.id,
            motherRelationshipType = "Adoptive",
            fatherRelationshipType = "Adoptive"
        )

        assertEquals("Santos", adoptedChild.middleName)
        assertEquals("Agoncillo", adoptedChild.lastName)
        assertEquals("Johanna S. Agoncillo", adoptedChild.standardDisplayName)

        // Case B: Stepparent Adoption (In Re: Stephanie Garcia, G.R. No. 148311)
        // Natural mother retains middle name, adoptive stepfather confers surname
        val bioMother = Person(id = "bm1", firstName = "Honorata", lastName = "Astorga", gender = "Female")
        val stepFather = Person(id = "sf1", firstName = "Jerry", lastName = "Garcia", gender = "Male")

        val stepAdoptedChild = Person(
            id = "sc1",
            firstName = "Stephanie",
            middleName = bioMother.lastName.trim(), // Retains natural mother's maiden surname
            lastName = stepFather.lastName.trim(),   // Takes adoptive father's surname
            motherId = bioMother.id,
            fatherId = stepFather.id,
            motherRelationshipType = "Biological",
            fatherRelationshipType = "Adoptive"
        )

        assertEquals("Astorga", stepAdoptedChild.middleName)
        assertEquals("Garcia", stepAdoptedChild.lastName)
        assertEquals("Stephanie Astorga Garcia", stepAdoptedChild.formalFullName)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Cousin Duplicate Disambiguation (DuplicateDetector)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCousinDisambiguation_DuplicateDetector() {
        val cousinA = Person(
            id = "cA",
            firstName = "Juan",
            middleName = "Santos",
            lastName = "Dela Cruz",
            motherId = "mom1"
        )
        val cousinB = Person(
            id = "cB",
            firstName = "Juan",
            middleName = "Reyes",
            lastName = "Dela Cruz",
            motherId = "mom2"
        )

        // When middle names are distinct, DuplicateDetector must NOT flag them as duplicate suspects
        val duplicates = DuplicateDetector().findPossibleDuplicates(
            newPerson = cousinB,
            existingPersons = listOf(cousinA),
            threshold = 0.85
        )

        assertTrue(
            "Cousins with distinct middle names must never trigger false duplicate collision",
            duplicates.isEmpty()
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Clan Health Duplicate Calculation (TreeHealthCalculator)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTreeHealthCalculator_IgnoresDistinctMiddleNameCousins() {
        val cousinA = Person(
            id = "cA",
            firstName = "Jose",
            middleName = "Mercado",
            lastName = "Rizal",
            birthDate = "1990-06-19",
            motherId = "momA"
        )
        val cousinB = Person(
            id = "cB",
            firstName = "Jose",
            middleName = "Alonso",
            lastName = "Rizal",
            birthDate = "1991-03-09",
            motherId = "momB"
        )

        val duplicateCount = TreeHealthCalculator().countDuplicateSuspects(listOf(cousinA, cousinB))
        assertEquals(
            "Health score must not be penalized for distinct cousins sharing first and last name",
            0,
            duplicateCount
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Kinship Tracing: Maternal vs. Paternal First Cousins
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testKinshipTracing_MaternalVsPaternalFirstCousin() {
        // Grandparents
        val patGf = Person(id = "pgf", firstName = "Lolo Ama", lastName = "Dela Cruz", gender = "Male")
        val matGf = Person(id = "mgf", firstName = "Lolo Ina", lastName = "Santos", gender = "Male")

        // Parents and Siblings
        val father = Person(id = "dad", firstName = "Tatay", lastName = "Dela Cruz", gender = "Male", fatherId = "pgf")
        val mother = Person(id = "mom", firstName = "Nanay", lastName = "Santos", gender = "Female", fatherId = "mgf")

        val patUncle = Person(id = "pUncle", firstName = "Tito Paternal", lastName = "Dela Cruz", gender = "Male", fatherId = "pgf")
        val matAunt = Person(id = "mAunt", firstName = "Tita Maternal", lastName = "Santos", gender = "Female", fatherId = "mgf")

        // Focal User
        val focal = Person(id = "focal", firstName = "Juan", lastName = "Dela Cruz", fatherId = "dad", motherId = "mom")

        // Paternal Cousin (Father's brother's child)
        val patCousin = Person(id = "cPat", firstName = "Paternal Cousin", lastName = "Dela Cruz", fatherId = "pUncle")

        // Maternal Cousin (Mother's sister's child)
        val matCousin = Person(id = "cMat", firstName = "Maternal Cousin", lastName = "Ramos", motherId = "mAunt")

        val allMembers = listOf(patGf, matGf, father, mother, patUncle, matAunt, focal, patCousin, matCousin)

        // 1. Verify Badges maintain backward-compatible "COUSIN" key
        val patBadge = KinshipTitleHelper.resolveTitle(target = patCousin, focal = focal, allMembers = allMembers)
        val matBadge = KinshipTitleHelper.resolveTitle(target = matCousin, focal = focal, allMembers = allMembers)

        assertEquals("COUSIN", patBadge)
        assertEquals("COUSIN", matBadge)

        // 2. Verify Subtitles distinguish Maternal vs. Paternal
        val patSub = KinshipTitleHelper.resolveSubtitle(target = patCousin, focal = focal, allMembers = allMembers)
        val matSub = KinshipTitleHelper.resolveSubtitle(target = matCousin, focal = focal, allMembers = allMembers)

        assertTrue(
            "Paternal cousin subtitle must indicate Pinsang Buo sa Ama ($patSub)",
            patSub.contains("Paternal First Cousin (Pinsang Buo sa Ama)")
        )
        assertTrue(
            "Maternal cousin subtitle must indicate Pinsang Buo sa Ina ($matSub)",
            matSub.contains("Maternal First Cousin (Pinsang Buo sa Ina)")
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Family Records Search by Middle Name & Middle Initial
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testFamilyRecordsSearchByMiddleNameAndInitial() {
        val p1 = Person(id = "p1", firstName = "Juan", middleName = "Santos", lastName = "Dela Cruz", suffix = "Jr.")
        val p2 = Person(id = "p2", firstName = "Maria", middleName = "Reyes", lastName = "Dela Cruz")
        val p3 = Person(id = "p3", firstName = "Pedro", middleName = "Gomez", lastName = "Bautista")

        val list = listOf(p1, p2, p3)

        // Search by full middle name "Santos"
        val foundSantos = FamilyRecordsActivity.filterMembers(list, "ALL", "Santos")
        assertEquals(1, foundSantos.size)
        assertEquals("p1", foundSantos[0].id)

        // Search by middle initial "S."
        val foundInitial = FamilyRecordsActivity.filterMembers(list, "ALL", "S.")
        assertEquals(1, foundInitial.size)
        assertEquals("p1", foundInitial[0].id)

        // Search by suffix "Jr."
        val foundSuffix = FamilyRecordsActivity.filterMembers(list, "ALL", "Jr.")
        assertEquals(1, foundSuffix.size)
        assertEquals("p1", foundSuffix[0].id)

        // Sort members alphabetically using standardDisplayName
        val sorted = FamilyRecordsActivity.sortMembers(list, "AZ")
        assertEquals("Juan S. Dela Cruz Jr.", sorted[0].standardDisplayName)
        assertEquals("Maria R. Dela Cruz", sorted[1].standardDisplayName)
        assertEquals("Pedro G. Bautista", sorted[2].standardDisplayName)
    }
}
