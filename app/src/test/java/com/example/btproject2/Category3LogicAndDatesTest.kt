package com.example.btproject2

import com.example.btproject2.engine.ConflictScanner
import com.example.btproject2.engine.ConnectionSuggestionEngine
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.engine.MergeBridgeValidator
import com.example.btproject2.engine.MergeSimulationEngine
import com.example.btproject2.engine.TimelineGenerator
import com.example.btproject2.engine.TreeHealthCalculator
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.service.FamilyRelationshipService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Category3LogicAndDatesTest {

    @Before
    fun setUp() {
        FirestoreHelper.clearCache()
    }

    // -------------------------------------------------------------------------
    // Issue 3.1: "Adoptive" vs "Adopted" label mismatch in KinshipTitleHelper
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_1 KinshipTitleHelper recognizes Adoptive from UI spinner as adoptive parent child and sibling`() {
        val father = Person(
            id = "f1",
            firstName = "Roberto",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1965-03-12"
        )
        val adoptedSon = Person(
            id = "c1",
            firstName = "Marco",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1995-07-20",
            fatherId = "f1",
            fatherRelationshipType = "Adoptive" // Exact string saved by Add/Edit Member Spinner
        )
        val bioDaughter = Person(
            id = "c2",
            firstName = "Elena",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1997-11-05",
            fatherId = "f1",
            fatherRelationshipType = "Biological"
        )
        val allMembers = listOf(father, adoptedSon, bioDaughter)
        val byId = allMembers.associateBy { it.id }

        assertTrue(
            "isAdopt must return true for 'Adoptive' saved by UI spinners",
            KinshipTitleHelper.isAdopt("Adoptive")
        )
        assertTrue(
            "isAdopt must also return true for legacy 'Adopted'",
            KinshipTitleHelper.isAdopt("Adopted")
        )

        // Sibling classification between adoptedSon ("Adoptive") and bioDaughter ("Biological")
        val sibCat = KinshipTitleHelper.classifySibling(adoptedSon, bioDaughter, byId)
        assertEquals(KinshipTitleHelper.SiblingCategory.ADOPTIVE_SIBLING, sibCat)

        // Title of father relative to adoptedSon
        val fatherTitle = KinshipTitleHelper.resolveTitle(father, adoptedSon, allMembers)
        assertTrue("Father title should indicate adoptive relationship ($fatherTitle)", fatherTitle.contains("ADOPT", ignoreCase = true))

        // Title of adoptedSon relative to bioDaughter
        val brotherTitle = KinshipTitleHelper.resolveTitle(adoptedSon, bioDaughter, allMembers)
        assertTrue("Brother title should indicate adoptive relationship ($brotherTitle)", brotherTitle.contains("ADOPT", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Issue 3.2: Chronological date comparison & year difference across formats
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_2 compareDatesChronologically and yearsBetweenDates handle mixed human and ISO dates`() {
        // Alphabetically "Apr 10, 2020" < "May 5, 1950", but chronologically 2020 > 1950
        val cmp = FamilyLinkValidator.compareDatesChronologically("Apr 10, 2020", "May 5, 1950")
        assertNotNull(cmp)
        assertTrue("2020 should be chronologically after 1950", cmp!! > 0)

        val cmpEarlier = FamilyLinkValidator.compareDatesChronologically("May 5, 1940", "Apr 10, 1950")
        assertNotNull(cmpEarlier)
        assertTrue("1940 should be chronologically before 1950", cmpEarlier!! < 0)

        // yearsBetweenDates with human-readable dates ("May 10, 1970" to "2000-05-10")
        val diff = FamilyLinkValidator.yearsBetweenDates("May 10, 1970", "2000-05-10")
        assertEquals(30, diff)
    }

    // -------------------------------------------------------------------------
    // Issue 3.3: Separated/remarried co-parents not falsely flagged for Bigamy
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_3 auditFamilyTree does not flag separated remarried co-parents as Bigamy error`() {
        val father = Person(
            id = "father1",
            firstName = "Daniel",
            lastName = "Cruz",
            gender = "Male",
            birthDate = "1970-01-10",
            spouseId = "newWife",
            maritalStatus = "Married"
        )
        val newWife = Person(
            id = "newWife",
            firstName = "Sofia",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1975-04-15",
            spouseId = "father1",
            maritalStatus = "Married"
        )
        val exMother = Person(
            id = "mother1",
            firstName = "Carmen",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-08-22",
            maritalStatus = "Separated"
        )
        val child = Person(
            id = "child1",
            firstName = "Lucas",
            lastName = "Cruz",
            gender = "Male",
            birthDate = "1998-03-14",
            fatherId = "father1",
            motherId = "mother1"
        )

        val tree = mapOf(
            father.id to father,
            newWife.id to newWife,
            exMother.id to exMother,
            child.id to child
        )

        val service = FamilyRelationshipService()
        val auditReport = service.auditFamilyTree(tree)

        val bigamyOnChildCoParents = auditReport.issues.any {
            it.title.contains("Co-Parents", ignoreCase = true) || it.description.contains("Bigamy", ignoreCase = true)
        }
        assertFalse("Remarried co-parent of an existing child must not be flagged as Bigamy", bigamyOnChildCoParents)
    }

    // -------------------------------------------------------------------------
    // Issue 3.4: Unrelated step-siblings are not blood siblings and can marry
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_4 unrelated step-siblings are not classified as blood siblings and can marry`() {
        val stepDad = Person(
            id = "p_dad",
            firstName = "Arthur",
            lastName = "Vega",
            gender = "Male",
            birthDate = "1960-02-01",
            spouseId = "p_mom",
            maritalStatus = "Married"
        )
        val stepMom = Person(
            id = "p_mom",
            firstName = "Clara",
            lastName = "Luna",
            gender = "Female",
            birthDate = "1962-06-15",
            spouseId = "p_dad",
            maritalStatus = "Married"
        )
        val sonOfDad = Person(
            id = "step_bro",
            firstName = "Evan",
            lastName = "Vega",
            gender = "Male",
            birthDate = "1990-01-10",
            fatherId = "p_dad"
        )
        val daughterOfMom = Person(
            id = "step_sis",
            firstName = "Maya",
            lastName = "Luna",
            gender = "Female",
            birthDate = "1992-09-25",
            motherId = "p_mom"
        )
        val allPersons = listOf(stepDad, stepMom, sonOfDad, daughterOfMom)

        assertFalse(
            "Unrelated step-siblings sharing zero biological/adoptive parents must not be blood siblings",
            FamilyLinkValidator.isSiblingOf(sonOfDad, daughterOfMom, allPersons)
        )
        assertTrue(
            "isStepSiblingOf should identify step-siblings whose parents are married to each other",
            FamilyLinkValidator.isStepSiblingOf(sonOfDad, daughterOfMom, allPersons)
        )

        val spouseCheck = FamilyLinkValidator.validateSpouse(sonOfDad, daughterOfMom, allPersons)
        assertTrue(
            "Philippine Family Code Art. 37(2) does not prohibit marriage between unrelated step-siblings",
            spouseCheck.isValid
        )
    }

    // -------------------------------------------------------------------------
    // Issue 3.5: Sibling merge preserves single mother instead of overwriting
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_5 MergeSimulationEngine sibling lineage preserves single mother without overwriting`() {
        val singleMom = Person(
            id = "mom_real",
            firstName = "Teresa",
            lastName = "Magbanua",
            gender = "Female",
            birthDate = "1965-05-01",
            treeId = "treeA"
        )
        val siblingA = Person(
            id = "sib_a",
            firstName = "Carlos",
            lastName = "Magbanua",
            gender = "Male",
            birthDate = "1990-04-12",
            motherId = "mom_real", // Single mother only, no fatherId
            treeId = "treeA"
        )
        val siblingB = Person(
            id = "sib_b",
            firstName = "Ana",
            lastName = "Magbanua",
            gender = "Female",
            birthDate = "1993-08-19",
            treeId = "treeB"
        )

        val engine = MergeSimulationEngine()
        val result = engine.simulate(
            tree1Members = listOf(singleMom, siblingA),
            tree2Members = listOf(siblingB),
            config = MergeSimulationEngine.BridgeConfig(
                mode = MergeBridgeValidator.BridgeMode.SIBLING_LINEAGE,
                personAId = siblingA.id,
                personBId = siblingB.id,
                masterTreeId = "master_1"
            )
        )

        val mergedSibA = result.simulatedMembers.find { it.firstName == "Carlos" }
        val mergedSibB = result.simulatedMembers.find { it.firstName == "Ana" }
        val mergedMom = result.simulatedMembers.find { it.firstName == "Teresa" }
        assertNotNull(mergedSibA)
        assertNotNull(mergedSibB)
        assertNotNull(mergedMom)
        assertEquals("Existing single mother must be preserved on Sibling A", mergedMom!!.id, mergedSibA!!.motherId)
        assertEquals("Existing single mother must be shared with Sibling B", mergedMom.id, mergedSibB!!.motherId)
        assertNull("No synthetic father should be forced when single mother is preserved", mergedSibA.fatherId)
    }

    // -------------------------------------------------------------------------
    // Issue 3.6: Human-readable dates in ConflictScanner, ConnectionSuggestion, TimelineGenerator
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_6 ConflictScanner ConnectionSuggestionEngine and TimelineGenerator parse human readable dates`() {
        val father = Person(
            id = "p1",
            firstName = "Lorenzo",
            lastName = "Dela Cruz",
            gender = "Male",
            birthDate = "May 10, 1960",
            deathDate = "Oct 15, 2020",
            isLiving = false,
            spouseId = "p2",
            maritalStatus = "Married",
            marriageDate = "Jun 20, 1985"
        )
        val mother = Person(
            id = "p2",
            firstName = "Beatriz",
            lastName = "Dela Cruz",
            gender = "Female",
            birthDate = "Aug 14, 1963",
            spouseId = "p1",
            maritalStatus = "Married",
            marriageDate = "Jun 20, 1985"
        )
        val child = Person(
            id = "c1",
            firstName = "Mateo",
            lastName = "Dela Cruz",
            gender = "Male",
            birthDate = "Nov 3, 1990",
            fatherId = "p1",
            motherId = "p2"
        )
        val all = listOf(father, mother, child)

        // 1. ConflictScanner should calculate 1990 - 1960 = 30 years (no false "Parent Too Young" conflict)
        val ageConflicts = ConflictScanner().scanAll(all).filter { it.type == "age_conflict" }
        assertTrue("Valid 30-year parent-child gap with human-readable dates should produce 0 age conflicts", ageConflicts.isEmpty())

        // 2. TimelineGenerator should extract 4-digit years ("1960", "1985", "1990", "2020") instead of "May ", "Jun "
        val events = TimelineGenerator().generate(father, all, listOf(child))
        val years = events.map { it.year }
        assertTrue("Should contain birth year 1960", "1960" in years)
        assertTrue("Should contain marriage year 1985", "1985" in years)
        assertTrue("Should contain child birth year 1990", "1990" in years)
        assertTrue("Should contain death year 2020", "2020" in years)
        assertFalse("Must never contain truncated month prefix 'May '", years.any { it.startsWith("May") })

        // 3. ConnectionSuggestionEngine should suggest parent link for an unlinked child with same lastName & 30yr gap
        val unlinkedChild = Person(
            id = "c2",
            firstName = "Camila",
            lastName = "Dela Cruz",
            gender = "Female",
            birthDate = "Jan 12, 1992"
        )
        val suggestions = ConnectionSuggestionEngine().generateSuggestions(unlinkedChild, all + unlinkedChild)
        assertTrue(
            "ConnectionSuggestionEngine should suggest parent link across human-readable dates",
            suggestions.any { it.personA.id == "c2" && (it.personB.id == "p1" || it.personB.id == "p2") }
        )
    }

    // -------------------------------------------------------------------------
    // Issue 3.8: TreeHealthCalculator excludes married-in spouses from orphans & counts maternal depth
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_8 TreeHealthCalculator excludes married-in spouses from orphans and counts maternal generations`() {
        val greatGrandmother = Person(
            id = "ggm",
            firstName = "Lola",
            lastName = "Basyang",
            gender = "Female",
            birthDate = "1920-01-01"
        )
        val grandmother = Person(
            id = "gm",
            firstName = "Maria",
            lastName = "Clara",
            gender = "Female",
            birthDate = "1945-05-05",
            motherId = "ggm"
        )
        val mother = Person(
            id = "m",
            firstName = "Sisa",
            lastName = "Mercado",
            gender = "Female",
            birthDate = "1970-09-09",
            motherId = "gm",
            spouseId = "marriedInHusband",
            maritalStatus = "Married"
        )
        val marriedInHusband = Person(
            id = "marriedInHusband",
            firstName = "Pedro",
            lastName = "Penduko",
            gender = "Male",
            birthDate = "1968-04-04",
            spouseId = "m",
            maritalStatus = "Married"
            // No parents and no children yet -> married-in spouse, NOT an orphan!
        )
        val child = Person(
            id = "child",
            firstName = "Basilio",
            lastName = "Mercado",
            gender = "Male",
            birthDate = "1998-12-12",
            motherId = "m"
        )

        val result = TreeHealthCalculator().calculate(
            listOf(greatGrandmother, grandmother, mother, marriedInHusband, child)
        )

        assertEquals("Married-in spouse must not be counted as an orphan", 0, result.stats.orphanCount)
        assertEquals("Maternal-only lineage of 4 generations must be counted accurately", 4, result.stats.maxGenerations)
    }

    // -------------------------------------------------------------------------
    // Issue 3.9: Marriage Date preserved in cache & planPersonUpdate
    // -------------------------------------------------------------------------
    @Test
    fun `issue 3_9 marriageDate is preserved in FirestoreHelper cache and planPersonUpdate`() {
        val husband = Person(
            id = "h1",
            firstName = "Antonio",
            lastName = "Luna",
            gender = "Male",
            birthDate = "1980-01-15",
            spouseId = "w1",
            maritalStatus = "Married",
            marriageDate = "2008-06-12",
            treeId = "t1"
        )
        val wife = Person(
            id = "w1",
            firstName = "Ysidra",
            lastName = "Cojuangco",
            gender = "Female",
            birthDate = "1982-03-20",
            spouseId = "h1",
            maritalStatus = "Married",
            marriageDate = "2008-06-12",
            treeId = "t1"
        )
        FirestoreHelper.addPersonToCache(husband)
        FirestoreHelper.addPersonToCache(wife)

        val plan = FirestoreHelper.planPersonUpdate(
            husband.copy(marriageDate = "2010-10-10"),
            FirestoreHelper.getCachedPersons("t1").orEmpty()
        )
        assertEquals("2010-10-10", plan.personToSave.marriageDate)

        FirestoreHelper.updatePersonInCache(plan.personToSave, allowBlankOverwrite = true)
        val cachedHusband = FirestoreHelper.getCachedPersons("t1")?.find { it.id == "h1" }
        assertNotNull(cachedHusband)
        assertEquals("2010-10-10", cachedHusband!!.marriageDate)
    }
}
