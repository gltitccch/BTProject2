package com.example.btproject2.engine

import com.example.btproject2.models.Person
import com.example.btproject2.ui.adapters.TreeMemberAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSpouseRemarriageRun5Test {

    @Test
    fun generationHierarchyEngine_alignsBothFirstAndSecondSpousesToSameGenerationAndPushesChildrenDown() {
        // Grandfather & Grandmother (Gen 1) -> Jose (Gen 2)
        // Jose married Maria (1st wife, no parents in tree) -> Child1 (Gen 3)
        // Jose remarried Elena (2nd wife, no parents in tree) -> Child2 (Gen 3)
        val lolo = Person(id = "lolo", firstName = "Lolo", lastName = "Santos", gender = "Male", spouseId = "lola")
        val lola = Person(id = "lola", firstName = "Lola", lastName = "Santos", gender = "Female", spouseId = "lolo")

        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Santos",
            gender = "Male",
            fatherId = "lolo",
            motherId = "lola",
            spouseId = "elena",
            formerSpouseIds = listOf("maria")
        )
        val maria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Reyes",
            gender = "Female",
            isLiving = false,
            deathDate = "2004-03-15",
            spouseId = "jose"
        )
        val elena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            isLiving = true,
            spouseId = "jose"
        )

        val child1 = Person(
            id = "child1",
            firstName = "Carlo",
            lastName = "Santos",
            gender = "Male",
            fatherId = "jose",
            motherId = "maria"
        )
        val child2 = Person(
            id = "child2",
            firstName = "Sofia",
            lastName = "Santos",
            gender = "Female",
            fatherId = "jose",
            motherId = "elena"
        )

        val allPersons = listOf(lolo, lola, jose, maria, elena, child1, child2)
        val gen1Based = GenerationHierarchyEngine.computeGenerations1Based(allPersons)

        // Lolo and Lola are Gen 1
        assertEquals(1, gen1Based["lolo"])
        assertEquals(1, gen1Based["lola"])

        // Jose, Maria (1st wife), and Elena (2nd wife) must ALL be aligned on Gen 2!
        assertEquals(2, gen1Based["jose"])
        assertEquals(2, gen1Based["maria"])
        assertEquals(2, gen1Based["elena"])

        // Children from both marriages must be on Gen 3
        assertEquals(3, gen1Based["child1"])
        assertEquals(3, gen1Based["child2"])

        // Hierarchy validation must pass
        assertTrue(GenerationHierarchyEngine.validateHierarchy(allPersons, gen1Based))
    }

    @Test
    fun treeMemberAdapter_resolveDefaultFocal_recognizesFormerSpousesForHubScoring() {
        val lolo = Person(id = "lolo", firstName = "Lolo", lastName = "Santos", gender = "Male")
        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Santos",
            gender = "Male",
            fatherId = "lolo",
            spouseId = null,
            formerSpouseIds = listOf("maria")
        )
        val maria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Reyes",
            gender = "Female",
            isLiving = false
        )
        val child1 = Person(
            id = "child1",
            firstName = "Carlo",
            lastName = "Santos",
            gender = "Male",
            fatherId = "jose",
            motherId = "maria"
        )

        val focal = TreeMemberAdapter.resolveDefaultFocal(listOf(lolo, jose, maria, child1))
        assertEquals("jose", focal?.id)
    }
}
