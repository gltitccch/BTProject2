package com.example.btproject2

import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.engine.ConflictScanner
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.engine.TreeHealthCalculator
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FourthDegreeConsanguinityStressTest {

    private fun buildFiveGenClan(): List<Person> {
        val members = mutableListOf<Person>()

        // Gen 1: Founders (2)
        members.add(
            Person(
                id = "g1_father",
                firstName = "Mateo",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1920-01-10",
                deathDate = "1998-05-12",
                isLiving = false,
                maritalStatus = "Married",
                spouseId = "g1_mother"
            )
        )
        members.add(
            Person(
                id = "g1_mother",
                firstName = "Clara",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "1922-03-15",
                deathDate = "2001-08-20",
                isLiving = false,
                maritalStatus = "Married",
                spouseId = "g1_father"
            )
        )

        // Gen 2: 3 Siblings (Roberto, Carlos, Rosa) + married-in spouses
        members.add(
            Person(
                id = "g2_roberto",
                firstName = "Roberto",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1945-04-12",
                fatherId = "g1_father",
                motherId = "g1_mother",
                maritalStatus = "Married",
                spouseId = "g2_lucia"
            )
        )
        members.add(
            Person(
                id = "g2_lucia",
                firstName = "Lucia",
                lastName = "Santos",
                gender = "Female",
                birthDate = "1947-06-18",
                maritalStatus = "Married",
                spouseId = "g2_roberto"
            )
        )
        members.add(
            Person(
                id = "g2_carlos",
                firstName = "Carlos",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1948-09-22",
                fatherId = "g1_father",
                motherId = "g1_mother",
                maritalStatus = "Married",
                spouseId = "g2_teresa"
            )
        )
        members.add(
            Person(
                id = "g2_teresa",
                firstName = "Teresa",
                lastName = "Reyes",
                gender = "Female",
                birthDate = "1950-11-05",
                maritalStatus = "Married",
                spouseId = "g2_carlos"
            )
        )
        members.add(
            Person(
                id = "g2_rosa",
                firstName = "Rosa",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "1951-02-14",
                fatherId = "g1_father",
                motherId = "g1_mother",
                maritalStatus = "Single"
            )
        )

        // Gen 3: Children of Roberto (Elena & Mario) and Children of Carlos (Paolo & Maria)
        members.add(
            Person(
                id = "g3_elena",
                firstName = "Elena",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "1970-02-14",
                fatherId = "g2_roberto",
                motherId = "g2_lucia",
                maritalStatus = "Married",
                spouseId = "g3_miguel"
            )
        )
        members.add(
            Person(
                id = "g3_miguel",
                firstName = "Miguel",
                lastName = "Cruz",
                gender = "Male",
                birthDate = "1969-07-21",
                maritalStatus = "Married",
                spouseId = "g3_elena"
            )
        )
        members.add(
            Person(
                id = "g3_mario",
                firstName = "Mario",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1972-05-19",
                fatherId = "g2_roberto",
                motherId = "g2_lucia",
                maritalStatus = "Single"
            )
        )
        members.add(
            Person(
                id = "g3_paolo",
                firstName = "Paolo",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1973-10-08",
                fatherId = "g2_carlos",
                motherId = "g2_teresa",
                maritalStatus = "Married",
                spouseId = "g3_ana"
            )
        )
        members.add(
            Person(
                id = "g3_ana",
                firstName = "Ana",
                lastName = "Mendoza",
                gender = "Female",
                birthDate = "1974-12-01",
                maritalStatus = "Married",
                spouseId = "g3_paolo"
            )
        )
        members.add(
            Person(
                id = "g3_maria",
                firstName = "Maria",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "1975-08-30",
                fatherId = "g2_carlos",
                motherId = "g2_teresa",
                maritalStatus = "Single"
            )
        )

        // Gen 4: Children of Elena (Marco) & Children of Paolo (Nina) -> Second Cousins (6th Degree)
        members.add(
            Person(
                id = "g4_marco",
                firstName = "Marco",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1995-03-11",
                fatherId = "g3_miguel",
                motherId = "g3_elena",
                maritalStatus = "Married",
                spouseId = "g4_sofia"
            )
        )
        members.add(
            Person(
                id = "g4_sofia",
                firstName = "Sofia",
                lastName = "Lim",
                gender = "Female",
                birthDate = "1996-09-14",
                maritalStatus = "Married",
                spouseId = "g4_marco"
            )
        )
        members.add(
            Person(
                id = "g4_nina",
                firstName = "Nina",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "1997-04-25",
                fatherId = "g3_paolo",
                motherId = "g3_ana",
                maritalStatus = "Single"
            )
        )

        // Gen 5: Children of Marco & Sofia (Lucas & Chloe) -> 4th-degree lineal descendants of Gen 1 Mateo & Clara
        members.add(
            Person(
                id = "g5_lucas",
                firstName = "Lucas",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "2020-01-15",
                fatherId = "g4_marco",
                motherId = "g4_sofia",
                maritalStatus = "Single"
            )
        )
        members.add(
            Person(
                id = "g5_chloe",
                firstName = "Chloe",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "2022-06-20",
                fatherId = "g4_marco",
                motherId = "g4_sofia",
                maritalStatus = "Single"
            )
        )

        return members
    }

    @Test
    fun testAll1stTo4thDegreeAnd5thPlusBloodlineTraces() {
        val clan = buildFiveGenClan()
        val personMap = clan.associateBy { it.id }
        val tracer = BloodlineTracer()

        // 1st Degree Lineal: Roberto (Gen 2) & Elena (Gen 3)
        val deg1 = tracer.trace("g2_roberto", "g3_elena", personMap)
        assertEquals(1, deg1.degreeOfConsanguinity)
        assertTrue(deg1.relationshipType.contains("Parent", ignoreCase = true))

        // 2nd Degree Collateral: Elena (Gen 3) & Mario (Gen 3) -> Siblings
        val deg2Sib = tracer.trace("g3_elena", "g3_mario", personMap)
        assertEquals(2, deg2Sib.degreeOfConsanguinity)
        assertTrue(deg2Sib.relationshipType.contains("Sibling", ignoreCase = true))

        // 3rd Degree Collateral: Carlos (Gen 2) & Elena (Gen 3) -> Uncle / Niece
        val deg3Uncle = tracer.trace("g2_carlos", "g3_elena", personMap)
        assertEquals(3, deg3Uncle.degreeOfConsanguinity)
        assertTrue(deg3Uncle.relationshipType.contains("Uncle", ignoreCase = true))

        // 4th Degree Collateral (First Cousins): Elena (Gen 3) & Paolo (Gen 3)
        val deg4Cousins = tracer.trace("g3_elena", "g3_paolo", personMap)
        assertEquals(4, deg4Cousins.degreeOfConsanguinity)
        assertEquals("First Cousins", deg4Cousins.relationshipType)

        // 4th Degree Collateral (Great-Uncle / Grandnephew): Carlos (Gen 2) & Marco (Gen 4)
        val deg4GreatUncle = tracer.trace("g2_carlos", "g4_marco", personMap)
        assertEquals(4, deg4GreatUncle.degreeOfConsanguinity)
        assertTrue(deg4GreatUncle.relationshipType.contains("Great-Uncle", ignoreCase = true))

        // 4th Degree Lineal (Great-Great-Grandparent / Great-Great-Grandchild): Mateo (Gen 1) & Lucas (Gen 5)
        val deg4Lineal = tracer.trace("g1_father", "g5_lucas", personMap)
        assertEquals(4, deg4Lineal.degreeOfConsanguinity)
        assertTrue(deg4Lineal.relationshipType.contains("Great-Great-Grandparent", ignoreCase = true))

        // 5th Degree Collateral (First Cousin Once Removed): Elena (Gen 3) & Nina (Gen 4)
        val deg5 = tracer.trace("g3_elena", "g4_nina", personMap)
        assertEquals(5, deg5.degreeOfConsanguinity)
        assertEquals("First Cousin Once Removed", deg5.relationshipType)

        // 6th Degree Collateral (Second Cousins): Marco (Gen 4) & Nina (Gen 4)
        val deg6 = tracer.trace("g4_marco", "g4_nina", personMap)
        assertEquals(6, deg6.degreeOfConsanguinity)
        assertEquals("Second Cousins", deg6.relationshipType)
    }

    @Test
    fun testComplete4thDegreeKinshipBadges() {
        val clan = buildFiveGenClan()
        val founderMateo = clan.first { it.id == "g1_father" }
        val titlesFromFounder = KinshipTitleHelper.resolveAllTitles(clan, founderMateo)

        // Gen 5 Lucas & Chloe should be GR-GREAT-GRANDSON and GR-GREAT-GRANDDAUGHTER relative to Gen 1 Founder Mateo
        assertEquals("GR-GREAT-GRANDSON", titlesFromFounder["g5_lucas"])
        assertEquals("GR-GREAT-GRANDDAUGHTER", titlesFromFounder["g5_chloe"])

        // Relative to Gen 4 Marco: Carlos & Rosa (grandparent Roberto's siblings) are GREAT-UNCLE and GREAT-AUNT
        val marco = clan.first { it.id == "g4_marco" }
        val titlesFromMarco = KinshipTitleHelper.resolveAllTitles(clan, marco)
        assertEquals("GREAT-UNCLE", titlesFromMarco["g2_carlos"])
        assertEquals("GREAT-AUNT", titlesFromMarco["g2_rosa"])

        // Relative to Gen 2 Carlos: Marco (sibling Roberto's grandchild) is GRANDNEPHEW, and Nina's cousin Elena is NIECE
        val carlos = clan.first { it.id == "g2_carlos" }
        val titlesFromCarlos = KinshipTitleHelper.resolveAllTitles(clan, carlos)
        assertEquals("NIECE", titlesFromCarlos["g3_elena"])
        assertEquals("GRANDNEPHEW", titlesFromCarlos["g4_marco"])

        // Relative to Gen 3 Elena: Paolo & Maria are COUSIN
        val elena = clan.first { it.id == "g3_elena" }
        val titlesFromElena = KinshipTitleHelper.resolveAllTitles(clan, elena)
        assertEquals("COUSIN", titlesFromElena["g3_paolo"])
        assertEquals("COUSIN", titlesFromElena["g3_maria"])
    }

    @Test
    fun testCoParentValidationAllows5thAnd6thDegreeButBlocksUpTo4thDegree() {
        val clan = buildFiveGenClan()

        // 4th Degree First Cousins (Paolo & Elena) -> blocked as co-parents (degree <= 4)
        val paolo = clan.first { it.id == "g3_paolo" }
        val elena = clan.first { it.id == "g3_elena" }
        val deg4Res = FamilyLinkValidator.validateCoParents(paolo, elena, clan)
        assertFalse("4th degree First Cousins must be flagged as invalid co-parents", deg4Res.isValid)

        // 6th Degree Second Cousins (Marco & Nina) -> allowed (degree == 6 > 4)
        val marco = clan.first { it.id == "g4_marco" }
        val nina = clan.first { it.id == "g4_nina" }
        val deg6Res = FamilyLinkValidator.validateCoParents(marco, nina, clan)
        assertTrue(
            "6th degree Second Cousins should be allowed as co-parents (not blocked by loose substring checks): ${deg6Res.issues}",
            deg6Res.isValid
        )
    }

    @Test
    fun test100Member5GenStressTreeHealthAndConflictScanner() {
        val baseClan = buildFiveGenClan().toMutableList()

        // Expand to 100 members across Gen 3, Gen 4, and Gen 5 with married-in spouses and same-surname cousins
        var idx = 1
        while (baseClan.size < 100) {
            val childId = "stress_child_$idx"
            val spouseId = "stress_spouse_$idx"
            val day = String.format("%02d", (idx % 28) + 1)
            baseClan.add(
                Person(
                    id = childId,
                    firstName = "Descendant$idx",
                    lastName = "Bugarin",
                    gender = if (idx % 2 == 0) "Male" else "Female",
                    birthDate = "1998-05-$day",
                    birthPlace = "Manila",
                    fatherId = "g3_paolo",
                    motherId = "g3_ana",
                    maritalStatus = "Married",
                    spouseId = spouseId
                )
            )
            if (baseClan.size < 100) {
                baseClan.add(
                    Person(
                        id = spouseId,
                        firstName = "InLaw$idx",
                        lastName = "Rivera",
                        gender = if (idx % 2 == 0) "Female" else "Male",
                        birthDate = "1998-07-$day",
                        birthPlace = "Quezon City",
                        maritalStatus = "Married",
                        spouseId = childId
                    )
                )
            }
            idx++
        }

        assertEquals(100, baseClan.size)

        val health = TreeHealthCalculator().calculate(baseClan)
        assertEquals(
            "0 duplicate suspects expected when first names are distinct despite shared family surname",
            0,
            health.stats.duplicateSuspects
        )
        assertFalse(
            "No missing-parent deduction expected when all bloodline descendants have parents and married-in spouses are excluded",
            health.deductions.any { it.contains("missing parent links", ignoreCase = true) }
        )
        assertEquals("Excellent", health.label)
        assertTrue(
            "Health score of clean 100-member 5-gen clan should be >= 90 (Excellent), was ${health.score} (${health.deductions})",
            health.score >= 90
        )

        val conflicts = ConflictScanner().scanAll(baseClan)
        assertTrue(
            "Clean 100-member 5-gen clan should have 0 conflicts, found: $conflicts",
            conflicts.isEmpty()
        )
    }
}
