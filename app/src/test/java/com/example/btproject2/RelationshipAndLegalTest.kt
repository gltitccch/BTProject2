package com.example.btproject2

import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test

class RelationshipAndLegalTest {

    private val tracer = BloodlineTracer()

    @Test
    fun testParentChildConsanguinityAndArticle37() {
        val parent = Person(id = "p1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male")
        val child = Person(id = "c1", firstName = "Pedro", lastName = "Dela Cruz", gender = "Male", fatherId = "p1")
        val map = mapOf("p1" to parent, "c1" to child)

        val result = tracer.trace("p1", "c1", map)

        assertTrue(result.isRelated)
        assertEquals(1, result.degreeOfConsanguinity)
        assertEquals("Lineal", result.relationshipCategory)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void ab initio (Incestuous)", result.legalStatus)
        assertTrue(result.legalArticle.contains("37"))
    }

    @Test
    fun testSiblingsConsanguinityAndArticle37() {
        val parent = Person(id = "p1", firstName = "Juan", lastName = "Santos")
        val child1 = Person(id = "s1", firstName = "Carlos", lastName = "Santos", fatherId = "p1")
        val child2 = Person(id = "s2", firstName = "Maria", lastName = "Santos", fatherId = "p1")
        val map = mapOf("p1" to parent, "s1" to child1, "s2" to child2)

        val result = tracer.trace("s1", "s2", map)

        assertTrue(result.isRelated)
        assertEquals(2, result.degreeOfConsanguinity)
        assertEquals("Collateral", result.relationshipCategory)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void ab initio (Incestuous)", result.legalStatus)
        assertTrue(result.legalArticle.contains("37"))
    }

    @Test
    fun testFirstCousinsConsanguinityAndArticle38() {
        val grandfather = Person(id = "gf", firstName = "Antonio", lastName = "Reyes")
        val father1 = Person(id = "f1", firstName = "Jose", fatherId = "gf")
        val father2 = Person(id = "f2", firstName = "Manuel", fatherId = "gf")
        val cousin1 = Person(id = "c1", firstName = "Ana", fatherId = "f1")
        val cousin2 = Person(id = "c2", firstName = "Luis", fatherId = "f2")

        val map = mapOf(
            "gf" to grandfather,
            "f1" to father1, "f2" to father2,
            "c1" to cousin1, "c2" to cousin2
        )

        val result = tracer.trace("c1", "c2", map)

        assertTrue(result.isRelated)
        assertEquals(4, result.degreeOfConsanguinity)
        assertEquals("First Cousins", result.relationshipType)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
    }

    @Test
    fun testSecondCousinsPermissibleUnderPhilippineLaw() {
        // G1: Great-grandfather
        val ggf = Person(id = "ggf", firstName = "Emilio")
        // G2: Grandfathers
        val gf1 = Person(id = "gf1", fatherId = "ggf")
        val gf2 = Person(id = "gf2", fatherId = "ggf")
        // G3: Parents (1st cousins)
        val p1 = Person(id = "p1", fatherId = "gf1")
        val p2 = Person(id = "p2", fatherId = "gf2")
        // G4: 2nd cousins
        val sc1 = Person(id = "sc1", firstName = "Gabriel", fatherId = "p1")
        val sc2 = Person(id = "sc2", firstName = "Isabella", fatherId = "p2")

        val map = mapOf(
            "ggf" to ggf,
            "gf1" to gf1, "gf2" to gf2,
            "p1" to p1, "p2" to p2,
            "sc1" to sc1, "sc2" to sc2
        )

        val result = tracer.trace("sc1", "sc2", map)

        assertTrue(result.isRelated)
        assertEquals(6, result.degreeOfConsanguinity)
        assertEquals("Second Cousins", result.relationshipType)
        assertFalse(result.isMarriageProhibited)
        assertEquals("Permissible under Philippine Law", result.legalStatus)
        assertTrue(result.legalArticle.contains("Beyond 4th Degree"))
    }

    @Test
    fun testAdoptiveParentChildArticle38Paragraph4() {
        val adopter = Person(id = "a1", firstName = "Eduardo", lastName = "Castro")
        val adoptee = Person(
            id = "c1", firstName = "Miguel", lastName = "Castro",
            fatherId = "a1", fatherRelationshipType = "Adoptive"
        )
        val map = mapOf("a1" to adopter, "c1" to adoptee)

        val result = tracer.trace("a1", "c1", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
        assertTrue(result.legalDescription.contains("adopting parent and the adopted child"))
    }

    @Test
    fun testStepParentChildArticle38Paragraph2() {
        val stepParent = Person(id = "sp1", firstName = "Helena", lastName = "Tan")
        val stepChild = Person(
            id = "sc1", firstName = "Lorenzo", lastName = "Tan",
            motherId = "sp1", motherRelationshipType = "Step"
        )
        val map = mapOf("sp1" to stepParent, "sc1" to stepChild)

        val result = tracer.trace("sp1", "sc1", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
        assertTrue(result.legalDescription.contains("step-parents and step-children"))
    }

    @Test
    fun testAdoptiveSiblingsArticle38Paragraph8() {
        val adopter = Person(id = "ad1", firstName = "Vicente")
        val child1 = Person(id = "ac1", firstName = "Rafael", fatherId = "ad1", fatherRelationshipType = "Adoptive")
        val child2 = Person(id = "ac2", firstName = "Sofia", fatherId = "ad1", fatherRelationshipType = "Adoptive")
        val map = mapOf("ad1" to adopter, "ac1" to child1, "ac2" to child2)

        val result = tracer.trace("ac1", "ac2", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Public Policy)", result.legalStatus)
        assertTrue(result.legalArticle.contains("38"))
    }

    @Test
    fun testExistingSpousesBigamyProhibition() {
        val husband = Person(id = "h1", firstName = "Danilo", spouseId = "w1")
        val wife = Person(id = "w1", firstName = "Corazon", spouseId = "h1")
        val map = mapOf("h1" to husband, "w1" to wife)

        val result = tracer.trace("h1", "w1", map)

        assertTrue(result.isRelated)
        assertTrue(result.isMarriageProhibited)
        assertEquals("Void (Existing Marriage)", result.legalStatus)
        assertTrue(result.legalArticle.contains("35"))
    }

    @Test
    fun testFullVsHalfSiblingsAndDualCommonAncestors() {
        val father = Person(id = "f1", firstName = "Arthur", lastName = "Malone", gender = "Male", spouseId = "m1")
        val mother = Person(id = "m1", firstName = "Beatrice", lastName = "Hayes", gender = "Female", spouseId = "f1")
        val fullBro1 = Person(id = "s1", firstName = "Thomas", lastName = "Malone", fatherId = "f1", motherId = "m1")
        val fullBro2 = Person(id = "s2", firstName = "Edward", lastName = "Malone", fatherId = "f1", motherId = "m1")
        val halfSis = Person(id = "s3", firstName = "Clara", lastName = "Malone", fatherId = "f1")

        val map = mapOf(
            "f1" to father,
            "m1" to mother,
            "s1" to fullBro1,
            "s2" to fullBro2,
            "s3" to halfSis
        )

        val fullResult = tracer.trace("s1", "s2", map)
        assertTrue(fullResult.isRelated)
        assertEquals("Siblings", fullResult.relationshipType)
        assertEquals("Full Siblings", fullResult.bloodlineSubtype)
        assertEquals(2, fullResult.commonAncestors.size)
        assertEquals("Arthur Malone & Beatrice Hayes", fullResult.commonAncestorDisplayName)
        assertTrue(fullResult.explanation.contains("full siblings", ignoreCase = true))

        val halfResult = tracer.trace("s1", "s3", map)
        assertTrue(halfResult.isRelated)
        assertEquals("Siblings", halfResult.relationshipType)
        assertEquals("Half-Siblings", halfResult.bloodlineSubtype)
        assertEquals(1, halfResult.commonAncestors.size)
        assertEquals("Arthur Malone", halfResult.commonAncestorDisplayName)
        assertTrue(halfResult.explanation.contains("half-siblings", ignoreCase = true))
    }

    @Test
    fun testFullVsHalfFirstCousinsDualGrandparents() {
        val gf = Person(id = "gf", firstName = "Arthur", lastName = "Malone", gender = "Male", spouseId = "gm")
        val gm = Person(id = "gm", firstName = "Beatrice", lastName = "Hayes", gender = "Female", spouseId = "gf")
        val dad1 = Person(id = "d1", firstName = "Thomas", lastName = "Malone", fatherId = "gf", motherId = "gm")
        val dad2 = Person(id = "d2", firstName = "Edward", lastName = "Malone", fatherId = "gf", motherId = "gm")
        val cousin1 = Person(id = "c1", firstName = "Oliver", lastName = "Malone", fatherId = "d1")
        val cousin2 = Person(id = "c2", firstName = "Lily", lastName = "Malone", fatherId = "d2")

        val map = mapOf(
            "gf" to gf, "gm" to gm,
            "d1" to dad1, "d2" to dad2,
            "c1" to cousin1, "c2" to cousin2
        )

        val res = tracer.trace("c1", "c2", map)
        assertTrue(res.isRelated)
        assertEquals("First Cousins", res.relationshipType)
        assertEquals("Full First Cousins", res.bloodlineSubtype)
        assertEquals(2, res.commonAncestors.size)
        assertEquals("Arthur Malone & Beatrice Hayes", res.commonAncestorDisplayName)
    }

    @Test
    fun testParentInLawAndChildInLawAffinityImpediment() {
        val father = Person(id = "p_thomas", firstName = "Thomas", lastName = "Malone", gender = "Male")
        val son = Person(id = "p_oliver", firstName = "Oliver", lastName = "Malone", gender = "Male", fatherId = "p_thomas", spouseId = "p_chloe")
        val daughterInLaw = Person(id = "p_chloe", firstName = "Chloe", lastName = "Rivera", gender = "Female", spouseId = "p_oliver")

        val map = mapOf(
            "p_thomas" to father,
            "p_oliver" to son,
            "p_chloe" to daughterInLaw
        )

        val result = tracer.trace("p_thomas", "p_chloe", map)
        assertTrue(result.isRelated)
        assertEquals(0, result.degreeOfConsanguinity)
        assertEquals("Affinity / In-Law", result.relationshipCategory)
        assertEquals("Father-in-Law / Daughter-in-Law", result.relationshipType)
        assertTrue(result.isMarriageProhibited)
        assertTrue(result.legalArticle.contains("38"))
    }

    @Test
    fun testSiblingsInLawCollateralAffinity() {
        val father = Person(id = "f1", firstName = "Arthur", lastName = "Malone", gender = "Male")
        val bro1 = Person(id = "b1", firstName = "Thomas", lastName = "Malone", gender = "Male", fatherId = "f1", spouseId = "w1")
        val bro2 = Person(id = "b2", firstName = "Edward", lastName = "Malone", gender = "Male", fatherId = "f1")
        val wife1 = Person(id = "w1", firstName = "Evelyn", lastName = "Clark", gender = "Female", spouseId = "b1")

        val map = mapOf("f1" to father, "b1" to bro1, "b2" to bro2, "w1" to wife1)

        val result = tracer.trace("b2", "w1", map)
        assertTrue(result.isRelated)
        assertEquals(0, result.degreeOfConsanguinity)
        assertEquals("Affinity / In-Law", result.relationshipCategory)
        assertEquals("Siblings-in-Law", result.relationshipType)
        assertFalse(result.isMarriageProhibited)
    }

    @Test
    fun testSmartPedigreeFocalMemberPicksDeepestDescendantOverRootFounder() {
        val founder = Person(id = "g1", firstName = "Arthur", lastName = "Malone", gender = "Male")
        val founderWife = Person(id = "g1w", firstName = "Beatrice", lastName = "Hayes", gender = "Female", spouseId = "g1")
        val son = Person(id = "g2", firstName = "Thomas", lastName = "Malone", fatherId = "g1", motherId = "g1w")
        val grandson = Person(id = "g3", firstName = "Oliver", lastName = "Malone", fatherId = "g2")

        val list = listOf(founder, founderWife, son, grandson)
        val bestFocal = com.example.btproject2.engine.GenerationHierarchyEngine.findBestPedigreeFocalMember(list)
        assertNotNull(bestFocal)
        assertEquals("g3", bestFocal!!.id)
    }

    @Test
    fun testArthurMaloneAndSarahJenkinsGrandfatherInLawExtendedAffinity() {
        val arthur = Person(id = "arthur", firstName = "Arthur", lastName = "Malone", gender = "Male", spouseId = "beatrice")
        val beatrice = Person(id = "beatrice", firstName = "Beatrice", lastName = "Hayes", gender = "Female", spouseId = "arthur")
        val richard = Person(id = "richard", firstName = "Richard", lastName = "Malone", gender = "Male", fatherId = "arthur", motherId = "beatrice")
        val thomas = Person(id = "thomas", firstName = "Thomas", lastName = "Malone", gender = "Male", fatherId = "arthur", motherId = "beatrice")
        val oliver = Person(id = "oliver", firstName = "Oliver", lastName = "Malone", gender = "Male", fatherId = "thomas")
        val karl = Person(id = "karl", firstName = "Karl", lastName = "Malone", gender = "Male", fatherId = "richard", spouseId = "sarah")
        val sarah = Person(id = "sarah", firstName = "Sarah", lastName = "Jenkins", gender = "Female", fatherId = "david", spouseId = "karl")
        val david = Person(id = "david", firstName = "David", lastName = "Jenkins", gender = "Male")

        val map = listOf(arthur, beatrice, richard, thomas, oliver, karl, sarah, david).associateBy { it.id }

        // 1. Arthur Malone & Sarah Jenkins -> Grandfather-in-Law / Granddaughter-in-Law
        val traceArthurSarah = tracer.trace("arthur", "sarah", map)
        assertTrue(traceArthurSarah.isRelated)
        assertEquals("Grandfather-in-Law / Granddaughter-in-Law", traceArthurSarah.relationshipType)
        assertEquals("Affinity / In-Law", traceArthurSarah.relationshipCategory)
        assertEquals(0, traceArthurSarah.degreeOfConsanguinity)
        assertEquals("karl", traceArthurSarah.commonAncestor?.id)

        // Reverse direction: Sarah Jenkins & Arthur Malone -> Granddaughter-in-Law / Grandfather-in-Law
        val traceSarahArthur = tracer.trace("sarah", "arthur", map)
        assertTrue(traceSarahArthur.isRelated)
        assertEquals("Granddaughter-in-Law / Grandfather-in-Law", traceSarahArthur.relationshipType)
        assertEquals(0, traceSarahArthur.degreeOfConsanguinity)

        // 2. Thomas Malone (Uncle of Karl) & Sarah Jenkins -> Uncle-in-Law / Niece-in-Law
        val traceThomasSarah = tracer.trace("thomas", "sarah", map)
        assertTrue(traceThomasSarah.isRelated)
        assertEquals("Uncle-in-Law / Niece-in-Law", traceThomasSarah.relationshipType)
        assertEquals(0, traceThomasSarah.degreeOfConsanguinity)

        // 3. Oliver Malone (First Cousin of Karl) & Sarah Jenkins -> First Cousins-in-Law
        val traceOliverSarah = tracer.trace("oliver", "sarah", map)
        assertTrue(traceOliverSarah.isRelated)
        assertEquals("First Cousins-in-Law", traceOliverSarah.relationshipType)
        assertEquals(0, traceOliverSarah.degreeOfConsanguinity)

        // 4. Richard Malone & David Jenkins -> Co-Parents-in-Law (Balae)
        val traceBalae = tracer.trace("richard", "david", map)
        assertTrue(traceBalae.isRelated)
        assertEquals("Co-Parents-in-Law (Balae)", traceBalae.relationshipType)
        assertEquals(0, traceBalae.degreeOfConsanguinity)
    }

    @Test
    fun testKinshipTitleHelperSmartBadgesNeverFakeFatherOrSonForExtendedInLaws() {
        val arthur = Person(id = "arthur", firstName = "Arthur", lastName = "Malone", gender = "Male", spouseId = "beatrice")
        val beatrice = Person(id = "beatrice", firstName = "Beatrice", lastName = "Wright", gender = "Female", spouseId = "arthur")
        val william = Person(id = "william", firstName = "William", lastName = "Vance", gender = "Male", spouseId = "dorothy")
        val dorothy = Person(id = "dorothy", firstName = "Dorothy", lastName = "Miller", gender = "Female", spouseId = "william")
        val richard = Person(id = "richard", firstName = "Richard", lastName = "Malone", gender = "Male", fatherId = "arthur", motherId = "beatrice", spouseId = "grace")
        val grace = Person(id = "grace", firstName = "Grace", lastName = "Vance", gender = "Female", fatherId = "william", motherId = "dorothy", spouseId = "richard")
        val edward = Person(id = "edward", firstName = "Edward", lastName = "Vance", gender = "Male", fatherId = "william", motherId = "dorothy")
        val karl = Person(id = "karl", firstName = "Karl", lastName = "Malone", gender = "Male", fatherId = "richard", motherId = "grace", spouseId = "sarah")
        val sarah = Person(id = "sarah", firstName = "Sarah", lastName = "Jenkins", gender = "Female", spouseId = "karl")

        val allPersons = listOf(arthur, beatrice, william, dorothy, richard, grace, edward, karl, sarah)

        // 1. Relative to Sarah Jenkins: Arthur = GRANDFATHER-IN-LAW, Beatrice = GRANDMOTHER-IN-LAW, Edward = UNCLE-IN-LAW
        val titlesFromSarah = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(allPersons, sarah)
        assertEquals("SELF", titlesFromSarah["sarah"])
        assertEquals("HUSBAND", titlesFromSarah["karl"])
        assertEquals("FATHER-IN-LAW", titlesFromSarah["richard"])
        assertEquals("MOTHER-IN-LAW", titlesFromSarah["grace"])
        assertEquals("GRANDFATHER-IN-LAW", titlesFromSarah["arthur"])
        assertEquals("GRANDMOTHER-IN-LAW", titlesFromSarah["beatrice"])
        assertEquals("UNCLE-IN-LAW", titlesFromSarah["edward"])

        // 2. Relative to Edward Vance: Arthur & Beatrice are Richard's parents (Co-Parents-in-Law of William & Dorothy, and in-law branch to Edward), NEVER "FATHER" or "MATRIARCH"!
        val titlesFromEdward = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(allPersons, edward)
        assertEquals("SELF", titlesFromEdward["edward"])
        assertEquals("FATHER", titlesFromEdward["william"])
        assertEquals("MOTHER", titlesFromEdward["dorothy"])
        assertEquals("FULL SISTER", titlesFromEdward["grace"])
        assertEquals("BROTHER-IN-LAW", titlesFromEdward["richard"])
        assertEquals("NEPHEW", titlesFromEdward["karl"])
        assertEquals("NIECE-IN-LAW", titlesFromEdward["sarah"])
        assertNotEquals("FATHER", titlesFromEdward["arthur"])
        assertNotEquals("SON", titlesFromEdward["karl"])
    }

    @Test
    fun testDeceasedAgeCalculationAndDoubleFirstCousinsAndStatusNotes() {
        // 1. Deceased with recorded death date -> "Died at age 80"
        val ageWithDeathDate = com.example.btproject2.utils.DocumentHelper.calculateAge(
            birthDate = "1935-04-10",
            deathDate = "2015-08-19",
            isLiving = false
        )
        assertEquals("Died at age 80", ageWithDeathDate)

        // 2. Deceased with blank death date -> "Deceased (b. 1935)", NEVER "Died at age 91"
        val ageWithoutDeathDate = com.example.btproject2.utils.DocumentHelper.calculateAge(
            birthDate = "1935-04-10",
            deathDate = "",
            isLiving = false
        )
        assertEquals("Deceased (b. 1935)", ageWithoutDeathDate)
        assertFalse(ageWithoutDeathDate.contains("Died at age"))

        // 3. Double First Cousins detection when two brothers marry two sisters
        val patGf = Person(id = "pgf", firstName = "Arthur", lastName = "Malone", gender = "Male", isLiving = false, deathDate = "2015-08-19")
        val patGm = Person(id = "pgm", firstName = "Beatrice", lastName = "Malone", gender = "Female")
        val matGf = Person(id = "mgf", firstName = "William", lastName = "Vance", gender = "Male")
        val matGm = Person(id = "mgm", firstName = "Dorothy", lastName = "Vance", gender = "Female")

        val bro1 = Person(id = "bro1", firstName = "Richard", lastName = "Malone", gender = "Male", fatherId = "pgf", motherId = "pgm", spouseId = "sis1")
        val bro2 = Person(id = "bro2", firstName = "Thomas", lastName = "Malone", gender = "Male", fatherId = "pgf", motherId = "pgm", spouseId = "sis2")
        val sis1 = Person(id = "sis1", firstName = "Grace", lastName = "Vance", gender = "Female", fatherId = "mgf", motherId = "mgm", spouseId = "bro1")
        val sis2 = Person(id = "sis2", firstName = "Clara", lastName = "Vance", gender = "Female", fatherId = "mgf", motherId = "mgm", spouseId = "bro2")

        val cousin1 = Person(id = "c1", firstName = "Karl", lastName = "Malone", gender = "Male", fatherId = "bro1", motherId = "sis1")
        val cousin2 = Person(id = "c2", firstName = "Oliver", lastName = "Malone", gender = "Male", fatherId = "bro2", motherId = "sis2")

        val all = listOf(patGf, patGm, matGf, matGm, bro1, bro2, sis1, sis2, cousin1, cousin2)
        val map = all.associateBy { it.id }

        val doubleTrace = tracer.trace("c1", "c2", map)
        assertTrue(doubleTrace.isRelated)
        assertEquals("Double First Cousins", doubleTrace.relationshipType)
        assertTrue(doubleTrace.bloodlineSubtype.contains("Double First Cousins"))

        val badges = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(all, cousin1)
        assertEquals("DOUBLE COUSIN", badges["c2"])
    }
}

