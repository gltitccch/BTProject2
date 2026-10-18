package com.example.btproject2

import com.example.btproject2.engine.AncestorFinder
import com.example.btproject2.engine.BloodRelationshipEngine
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.ui.activities.ExportTreeActivity
import org.junit.Assert.*
import org.junit.Test

/**
 * Dedicated verification and stress test suite for Category 1 Remediation (Issues #1–#10):
 * - Removal of hardcoded developer names ("Renzy", "Clara", "Pedro", "Jose", "Ghillain", "Bugarin")
 * - Cycle-safe BFS traversal in AncestorFinder, BloodRelationshipEngine, and MarriageValidationEngine
 * - Complete GEDCOM 5.5.1 family graph export (INDI + DEAT + FAMC + FAMS + FAM)
 */
class Category1RemediationTest {

    @Test
    fun testUnrelatedPeopleNamedRenzyAndClaraAreNotFalselyBlocked() {
        val unrelatedRenzy = Person(
            id = "u_renzy",
            firstName = "Renzy",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1995-04-10"
        )
        val unrelatedClara = Person(
            id = "u_clara",
            firstName = "Clara",
            lastName = "Reyes",
            gender = "Female",
            birthDate = "1996-08-22"
        )
        val tree = listOf(unrelatedRenzy, unrelatedClara)

        val coParentResult = FamilyLinkValidator.validateCoParents(unrelatedRenzy, unrelatedClara, tree)
        assertTrue(
            "Unrelated people named Renzy and Clara must be allowed as co-parents",
            coParentResult.isValid
        )

        val spouseResult = FamilyLinkValidator.validateSpouse(unrelatedRenzy, unrelatedClara, tree)
        assertTrue(
            "Unrelated people named Renzy and Clara must be allowed to marry",
            spouseResult.isValid
        )
    }

    @Test
    fun testUnrelatedParentNamedClaraOrPedroOrJoseCanParentChildNamedGhillain() {
        val claraMom = Person(
            id = "p_clara_unrelated",
            firstName = "Clara",
            lastName = "Mendoza",
            gender = "Female",
            birthDate = "1988-02-14"
        )
        val joseDad = Person(
            id = "p_jose_unrelated",
            firstName = "Jose",
            lastName = "Mendoza",
            gender = "Male",
            birthDate = "1986-07-09"
        )
        val ghillainChild = Person(
            id = "c_ghillain_unrelated",
            firstName = "Ghillain",
            lastName = "Mendoza",
            gender = "Female",
            birthDate = "2015-11-30"
        )
        val tree = listOf(claraMom, joseDad, ghillainChild)

        val motherCheck = FamilyLinkValidator.validateParentChild(claraMom, ghillainChild, "mother", tree)
        assertTrue("Legitimate mother named Clara must be allowed for child named Ghillain", motherCheck.isValid)

        val fatherCheck = FamilyLinkValidator.validateParentChild(joseDad, ghillainChild, "father", tree)
        assertTrue("Legitimate father named Jose must be allowed for child named Ghillain", fatherCheck.isValid)
    }

    @Test
    fun testSanitizeTreeRecordsDoesNotMutateUnlinkedBugarinFamilyByHardcodedNames() {
        val clara = Person(id = "b_clara", firstName = "Clara", lastName = "Bugarin", gender = "Female", treeId = "t1")
        val pedro = Person(id = "b_pedro", firstName = "Pedro", lastName = "Bugarin", gender = "Male", treeId = "t1")
        val renzy = Person(id = "b_renzy", firstName = "Renzy", lastName = "Bugarin", gender = "Male", treeId = "t1", motherId = "b_clara", fatherId = "b_pedro")
        val jose = Person(id = "b_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male", treeId = "t1")
        val ghillain = Person(id = "b_ghillain", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", treeId = "t1", fatherId = "b_renzy")

        val sanitized = FirestoreHelper.sanitizeTreeRecords(listOf(clara, pedro, renzy, jose, ghillain)).associateBy { it.id }

        // Legitimate mother/father links on Renzy must NOT be wiped out or reassigned to Jose
        assertEquals("b_clara", sanitized["b_renzy"]!!.motherId)
        assertEquals("b_pedro", sanitized["b_renzy"]!!.fatherId)
        // Legitimate father link on Ghillain must NOT be wiped out
        assertEquals("b_renzy", sanitized["b_ghillain"]!!.fatherId)
        // Unmarried Clara & Pedro must NOT be force-married by surname
        assertNull(sanitized["b_clara"]!!.spouseId)
        assertNull(sanitized["b_pedro"]!!.spouseId)
    }

    @Test
    fun testCircularAncestryCycleProtectionTerminatesCleanly() {
        // 3-node cycle: A -> B -> C -> A
        val pA = Person(id = "node_a", firstName = "Alpha", fatherId = "node_b")
        val pB = Person(id = "node_b", firstName = "Beta", fatherId = "node_c")
        val pC = Person(id = "node_c", firstName = "Gamma", fatherId = "node_a")
        val map = mapOf("node_a" to pA, "node_b" to pB, "node_c" to pC)

        val ancestorsFinder = AncestorFinder().findAncestors("node_a", map)
        assertFalse("Start person must never be added as its own ancestor in a cycle", ancestorsFinder.containsKey("node_a"))
        assertEquals(setOf("node_b", "node_c"), ancestorsFinder.keys)

        val bioAncestors = BloodRelationshipEngine().findBiologicalAncestors("node_a", map)
        assertFalse("Start person must never be added as its own biological ancestor in a cycle", bioAncestors.containsKey("node_a"))
        assertEquals(setOf("node_b", "node_c"), bioAncestors.keys)

        val path = MarriageValidationEngine().findAncestryPath("non_existent", "node_a", map)
        assertNull("Cycle traversal for non-existent ancestor must terminate and return null", path)
    }

    @Test
    fun testGedcomExportIncludesDeceasedAndFamilyLinks() {
        val father = Person(
            id = "p_father",
            firstName = "Mateo",
            lastName = "Delgado",
            gender = "Male",
            birthDate = "1940-05-01",
            birthPlace = "Vigan",
            isLiving = false,
            deathDate = "2010-12-15",
            deathPlace = "Manila",
            spouseId = "p_mother",
            maritalStatus = "Married",
            marriageDate = "1965-06-20"
        )
        val mother = Person(
            id = "p_mother",
            firstName = "Consuelo",
            lastName = "Delgado",
            gender = "Female",
            birthDate = "1943-09-10",
            spouseId = "p_father",
            maritalStatus = "Married",
            marriageDate = "1965-06-20"
        )
        val child = Person(
            id = "p_child",
            firstName = "Ricardo",
            lastName = "Delgado",
            gender = "Male",
            birthDate = "1970-03-22",
            fatherId = "p_father",
            motherId = "p_mother"
        )

        val gedcom = ExportTreeActivity.buildGedcomContent(listOf(father, mother, child))

        assertTrue("GEDCOM must include 1 DEAT Y for deceased member", gedcom.contains("1 DEAT Y"))
        assertTrue("GEDCOM must include death date", gedcom.contains("2 DATE 2010-12-15"))
        assertTrue("GEDCOM must include death place", gedcom.contains("2 PLAC Manila"))
        assertTrue("GEDCOM must include FAMS link on parents", gedcom.contains("1 FAMS @F1@"))
        assertTrue("GEDCOM must include FAMC link on child", gedcom.contains("1 FAMC @F1@"))
        assertTrue("GEDCOM must include 0 @F1@ FAM block", gedcom.contains("0 @F1@ FAM"))
        assertTrue("GEDCOM must include 1 HUSB in FAM block", gedcom.contains("1 HUSB @"))
        assertTrue("GEDCOM must include 1 WIFE in FAM block", gedcom.contains("1 WIFE @"))
        assertTrue("GEDCOM must include 1 CHIL in FAM block", gedcom.contains("1 CHIL @"))
        assertTrue("GEDCOM must include marriage date in FAM block", gedcom.contains("2 DATE 1965-06-20"))
    }

    @Test
    fun testSelectedParentFiltersOtherParentToLinkedSpouseOnly() {
        val thomas = Person(
            id = "thomas_1",
            firstName = "Thomas",
            lastName = "Malone",
            gender = "Male",
            birthDate = "1970-01-10",
            spouseId = "sarah_1",
            maritalStatus = "Married"
        )
        val sarah = Person(
            id = "sarah_1",
            firstName = "Sarah",
            lastName = "Jenkins",
            gender = "Female",
            birthDate = "1972-04-15",
            spouseId = "thomas_1",
            maritalStatus = "Married"
        )
        val dorothy = Person(id = "dorothy_1", firstName = "Dorothy", lastName = "Miller", gender = "Female", birthDate = "1975-06-20")
        val grace = Person(id = "grace_1", firstName = "Grace", lastName = "Vance", gender = "Female", birthDate = "1978-09-12")
        val patricia = Person(id = "patricia_1", firstName = "Patricia", lastName = "Scott", gender = "Female", birthDate = "1980-11-05")
        val chloe = Person(id = "chloe_1", firstName = "Chloe", lastName = "Adams", gender = "Female", birthDate = "1982-02-28")
        val unmarriedArthur = Person(id = "arthur_1", firstName = "Arthur", lastName = "Pendelton", gender = "Male", birthDate = "1974-03-11")
        val newChild = Person(id = "", firstName = "New", lastName = "Malone", gender = "Male", birthDate = "2005-05-05")

        val tree = listOf(thomas, sarah, dorothy, grace, patricia, chloe, unmarriedArthur)

        // 1. When Thomas Malone is selected as Father, Mother candidates must ONLY contain his linked wife Sarah Jenkins
        val mothersForThomas = FamilyLinkValidator.getEligibleMothers(newChild, thomas, tree)
        assertEquals("Only Thomas Malone's linked wife (Sarah Jenkins) should be returned", 1, mothersForThomas.size)
        assertEquals("sarah_1", mothersForThomas.first().id)

        // 2. Symmetrically, when Sarah Jenkins is selected as Mother, Father candidates must ONLY contain Thomas Malone
        val fathersForSarah = FamilyLinkValidator.getEligibleFathers(newChild, sarah, tree)
        assertEquals("Only Sarah Jenkins's linked husband (Thomas Malone) should be returned", 1, fathersForSarah.size)
        assertEquals("thomas_1", fathersForSarah.first().id)

        // 3. When an unmarried father (Arthur) is selected, all eligible females are returned
        val mothersForArthur = FamilyLinkValidator.getEligibleMothers(newChild, unmarriedArthur, tree)
        assertEquals(5, mothersForArthur.size)
    }
}

