package com.example.btproject2.engine

import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test

class MultiSpouseRemarriageRun3Test {

    @Test
    fun `ConflictScanner and TreeHealthCalculator give 0 false conflicts on remarriage triad`() {
        val jose = Person(
            id = "jose",
            firstName = "Jose",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1970-01-01",
            birthPlace = "Manila",
            spouseId = "elena",
            formerSpouseIds = listOf("maria"),
            maritalStatus = "Married",
            marriageDate = "2006-06-01",
            biography = "Patriarch"
        )
        val maria = Person(
            id = "maria",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-05-10",
            birthPlace = "Cebu",
            isLiving = false,
            deathDate = "2004-03-15",
            deathPlace = "Manila",
            spouseId = "jose",
            maritalStatus = "Married",
            marriageDate = "1995-05-01",
            biography = "First wife"
        )
        val elena = Person(
            id = "elena",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1975-08-20",
            birthPlace = "Davao",
            spouseId = "jose",
            maritalStatus = "Married",
            marriageDate = "2006-06-01",
            biography = "Second wife"
        )
        val carlo = Person(
            id = "carlo",
            firstName = "Carlo",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1998-02-14",
            birthPlace = "Manila",
            fatherId = "jose",
            motherId = "maria",
            maritalStatus = "Single",
            biography = "Son of first marriage"
        )
        val sofia = Person(
            id = "sofia",
            firstName = "Sofia",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "2008-11-05",
            birthPlace = "Manila",
            fatherId = "jose",
            motherId = "elena",
            maritalStatus = "Single",
            biography = "Daughter of second marriage"
        )

        val family = listOf(jose, maria, elena, carlo, sofia)

        // 1. ConflictScanner should find 0 conflicts (Maria's spouseId="jose" is reciprocal via jose.formerSpouseIds)
        val conflicts = ConflictScanner().scanAll(family)
        assertTrue("Expected 0 conflicts on healthy remarriage triad, got: $conflicts", conflicts.isEmpty())

        // 2. ConnectionSuggestionEngine should find 0 missing_link suggestions for Maria
        val mariaSuggestions = ConnectionSuggestionEngine().generateSuggestions(maria, family)
        assertTrue(
            "Maria should not have a false missing_link suggestion",
            mariaSuggestions.none { it.type == "missing_link" }
        )

        // 3. TreeHealthCalculator should score 100 ("Excellent") with 0 orphans
        val health = TreeHealthCalculator().calculate(family)
        assertEquals(0, health.stats.orphanCount)
        assertEquals(3, health.stats.withSpouse)
        assertTrue("Expected Excellent health score (>= 90), got ${health.score}: ${health.deductions}", health.score >= 90)
    }

    @Test
    fun `MergeSimulationEngine preserves formerSpouseIds and allows widowed remarriage merge`() {
        // Tree 1: Jose was married to late Maria, had son Carlo
        val joseT1 = Person(
            id = "jose_t1",
            firstName = "Jose",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1970-01-01",
            spouseId = "maria_t1",
            maritalStatus = "Widowed"
        )
        val mariaT1 = Person(
            id = "maria_t1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-05-10",
            isLiving = false,
            deathDate = "2004-03-15",
            spouseId = "jose_t1",
            maritalStatus = "Married"
        )
        val carloT1 = Person(
            id = "carlo_t1",
            firstName = "Carlo",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1998-02-14",
            fatherId = "jose_t1",
            motherId = "maria_t1"
        )

        // Tree 2: Elena (unmarried)
        val elenaT2 = Person(
            id = "elena_t2",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1975-08-20",
            maritalStatus = "Single"
        )

        val simEngine = MergeSimulationEngine()
        val result = simEngine.simulate(
            tree1Members = listOf(joseT1, mariaT1, carloT1),
            tree2Members = listOf(elenaT2),
            config = MergeSimulationEngine.BridgeConfig(
                mode = MergeBridgeValidator.BridgeMode.SPOUSAL_UNION,
                personAId = "jose_t1",
                personBId = "elena_t2"
            )
        )

        assertTrue("Expected spousal union simulation to be safe: ${result.blockReason}", result.isSafe)
        val mergedJose = result.simulatedMembers.first { it.firstName == "Jose" }
        val mergedMaria = result.simulatedMembers.first { it.firstName == "Maria" }
        val mergedElena = result.simulatedMembers.first { it.firstName == "Elena" }

        assertEquals(mergedElena.id, mergedJose.spouseId)
        assertTrue("Jose should retain Maria in formerSpouseIds", mergedJose.formerSpouseIds.contains(mergedMaria.id))
        assertTrue("Zero critical conflicts after merge", result.criticalConflicts.isEmpty())
    }
}
