package com.example.btproject2

import com.example.btproject2.engine.ConflictScanner
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test

class FamilyLinkValidatorTest {

    // ── Setup Helpers ─────────────────────────────────────────────

    private val pedro = Person(id = "p_pedro", firstName = "Pedro", lastName = "Bugarin", gender = "Male", birthDate = "1970-01-01")
    private val clara = Person(id = "p_clara", firstName = "Clara", lastName = "Bugarin", gender = "Female", birthDate = "1972-05-10")

    // Renzy (b. 2000) & Luz (b. 2004) are full biological siblings sharing Pedro & Clara
    private val renzy = Person(
        id = "p_renzy",
        firstName = "Renzy",
        lastName = "Bugarin",
        gender = "Male",
        birthDate = "2000-06-15",
        fatherId = "p_pedro",
        motherId = "p_clara"
    )

    private val luz = Person(
        id = "p_luz",
        firstName = "Luz",
        lastName = "Bugarin",
        gender = "Female",
        birthDate = "2004-10-04",
        fatherId = "p_pedro",
        motherId = "p_clara"
    )

    private val ghillain = Person(
        id = "p_ghillain",
        firstName = "Ghillain",
        lastName = "Bugarin",
        gender = "Female",
        birthDate = "2024-01-01"
    )

    private val baseFamily = listOf(pedro, clara, renzy, luz, ghillain)

    // ── Consanguinity & Incest Tests ───────────────────────────────

    @Test
    fun testSiblingCoParentsBlocked() {
        // Co-parenting between biological siblings Renzy & Luz must be strictly prohibited (Art. 37(2))
        val result = FamilyLinkValidator.validateCoParents(renzy, luz, baseFamily)
        assertFalse("Siblings must NOT be allowed as co-parents", result.isValid)
        assertTrue("Error should mention Article 37 (2)", result.errors.any { it.legalArticle.contains("37 (2)") })
        assertTrue("Error message should mention Siblings", result.errors.any { it.message.contains("Siblings", ignoreCase = true) })
    }

    @Test
    fun testSiblingCoParentsBlockedEvenWithoutMotherSetAndBlankRelType() {
        val renzyOnlyFather = Person(
            id = "p_renzy",
            firstName = "Renzy",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "2000-06-15",
            fatherId = "p_pedro",
            motherId = null,
            fatherRelationshipType = ""
        )
        val luzOnlyFather = Person(
            id = "p_luz",
            firstName = "Luz",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "2004-10-04",
            fatherId = "p_pedro",
            motherId = null,
            fatherRelationshipType = ""
        )
        val ghillainWithLuz = Person(
            id = "p_ghillain",
            firstName = "Ghillain",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "2024-01-01",
            motherId = "p_luz"
        )
        val tree = listOf(pedro, renzyOnlyFather, luzOnlyFather, ghillainWithLuz)

        // 1. Co-parents validation must fail
        val coParentResult = FamilyLinkValidator.validateCoParents(renzyOnlyFather, luzOnlyFather, tree)
        assertFalse("Renzy and Luz sharing father Pedro must be blocked as co-parents", coParentResult.isValid)
        assertTrue(coParentResult.errors.any { it.message.contains("siblings", ignoreCase = true) })

        // 2. Renzy cannot have Ghillain as eligible child
        val eligibleChildren = FamilyLinkValidator.getEligibleChildren(renzyOnlyFather, tree)
        assertFalse("Ghillain (Luz's daughter) must NOT be in eligible children for Renzy", eligibleChildren.any { it.id == "p_ghillain" })

        // 3. Ghillain cannot have Renzy as eligible father
        val eligibleFathers = FamilyLinkValidator.getEligibleFathers(ghillainWithLuz, null, tree)
        assertFalse("Renzy (Luz's brother) must NOT be in eligible fathers for Ghillain", eligibleFathers.any { it.id == "p_renzy" })
    }

    @Test
    fun testSiblingSpouseBlocked() {
        // Marriage between biological siblings Renzy & Luz must be strictly prohibited (Art. 37(2))
        val result = FamilyLinkValidator.validateSpouse(renzy, luz, baseFamily)
        assertFalse("Siblings must NOT be allowed to marry", result.isValid)
        assertTrue("Error should cite Article 37 (2)", result.errors.any { it.legalArticle.contains("37 (2)") })
    }

    @Test
    fun testFirstCousinsCoParentsBlocked() {
        // Grandparent
        val gp = Person(id = "gp1", firstName = "Lolo", lastName = "Bugarin", gender = "Male", birthDate = "1940-01-01")
        // Siblings (gp's sons)
        val bro1 = Person(id = "b1", firstName = "Brother1", lastName = "Bugarin", gender = "Male", fatherId = "gp1", birthDate = "1965-01-01")
        val bro2 = Person(id = "b2", firstName = "Brother2", lastName = "Bugarin", gender = "Male", fatherId = "gp1", birthDate = "1968-01-01")
        // First cousins
        val cousin1 = Person(id = "c1", firstName = "CousinA", lastName = "Bugarin", gender = "Male", fatherId = "b1", birthDate = "1995-01-01")
        val cousin2 = Person(id = "c2", firstName = "CousinB", lastName = "Bugarin", gender = "Female", fatherId = "b2", birthDate = "1997-01-01")

        val tree = listOf(gp, bro1, bro2, cousin1, cousin2)
        val result = FamilyLinkValidator.validateCoParents(cousin1, cousin2, tree)
        assertFalse("First cousins co-parenting must be prohibited under Art. 38(1)", result.isValid)
        assertTrue("Should cite Article 38 (1)", result.errors.any { it.legalArticle.contains("38 (1)") })
    }

    // ── Age Gap Feasibility Tests ─────────────────────────────────

    @Test
    fun testYoungerParentBlocked() {
        // Luz (b. 2004) attempting to be mother of Renzy (b. 2000)
        val result = FamilyLinkValidator.validateParentChild(parent = luz, child = renzy, role = "mother", allPersons = baseFamily)
        assertFalse("Younger parent must be blocked", result.isValid)
        assertTrue("Error must highlight younger parent", result.errors.any { it.title.contains("Younger Parent") })
    }

    @Test
    fun testSameYearParentBlocked() {
        val parent = Person(id = "p1", firstName = "TwinA", lastName = "Test", gender = "Female", birthDate = "2000-01-01")
        val child = Person(id = "c1", firstName = "TwinB", lastName = "Test", gender = "Male", birthDate = "2000-11-01")
        val result = FamilyLinkValidator.validateParentChild(parent = parent, child = child, role = "mother")
        assertFalse("Same year parent must be blocked", result.isValid)
        assertTrue(result.errors.any { it.title.contains("Same Year") })
    }

    @Test
    fun testParentUnder12YearsOldBlocked() {
        val youngFather = Person(id = "yf", firstName = "ChildFather", lastName = "Test", gender = "Male", birthDate = "1990-01-01")
        val child = Person(id = "c1", firstName = "Child", lastName = "Test", gender = "Female", birthDate = "2000-01-01") // 10 years gap
        val result = FamilyLinkValidator.validateParentChild(parent = youngFather, child = child, role = "father")
        assertFalse("Parent under 12 years old must be blocked as biologically impossible", result.isValid)
        assertTrue(result.errors.any { it.title.contains("Biologically Impossible") })
    }

    @Test
    fun testPostMortemMotherBlocked() {
        val deceasedMother = Person(
            id = "dm",
            firstName = "PassedMother",
            lastName = "Test",
            gender = "Female",
            birthDate = "1950-01-01",
            deathDate = "1990-01-01",
            isLiving = false
        )
        val child = Person(id = "c1", firstName = "Child", lastName = "Test", gender = "Male", birthDate = "2000-01-01")
        val result = FamilyLinkValidator.validateParentChild(parent = deceasedMother, child = child, role = "mother")
        assertFalse("Mother giving birth after death must be blocked", result.isValid)
        assertTrue(result.errors.any { it.title.contains("Post-Mortem Birth") })
    }

    // ── Candidate Filtering Tests ─────────────────────────────────

    @Test
    fun testCandidateFilteringExcludesSiblingsAndYoungerMothers() {
        // When finding eligible mothers for Renzy (b. 2000), Luz (b. 2004 and sibling) must NOT appear
        val eligibleMothers = FamilyLinkValidator.getEligibleMothers(renzy, pedro, baseFamily)
        assertFalse("Luz must not be in eligible mothers for Renzy", eligibleMothers.any { it.id == luz.id })
    }

    @Test
    fun testCandidateFilteringExcludesSiblingSpouse() {
        // When finding eligible spouses for Renzy, Luz must NOT appear
        val eligibleSpouses = FamilyLinkValidator.getEligibleSpouses(renzy, baseFamily)
        assertFalse("Luz must not be an eligible spouse for Renzy", eligibleSpouses.any { it.id == luz.id })
    }

    // ── Legitimate Links Tests ────────────────────────────────────

    @Test
    fun testLegitimateCoParentsAndAgeGapPasses() {
        // Pedro (b. 1970) and Clara (b. 1972) co-parenting Renzy (b. 2000)
        val coParentCheck = FamilyLinkValidator.validateCoParents(pedro, clara, baseFamily)
        assertTrue("Legitimate unrelated co-parents must be valid", coParentCheck.isValid)

        val fatherCheck = FamilyLinkValidator.validateParentChild(pedro, renzy, "father", baseFamily)
        assertTrue("30-year age gap father must be valid", fatherCheck.isValid)

        val motherCheck = FamilyLinkValidator.validateParentChild(clara, renzy, "mother", baseFamily)
        assertTrue("28-year age gap mother must be valid", motherCheck.isValid)
    }

    // ── Date Parsing Robustness Tests ─────────────────────────────

    @Test
    fun testExtractYearDiverseFormats() {
        assertEquals(2000, FamilyLinkValidator.extractYear("2000-01-15"))
        assertEquals(2000, FamilyLinkValidator.extractYear("Jan 15, 2000"))
        assertEquals(2004, FamilyLinkValidator.extractYear("October 4, 2004"))
        assertEquals(2004, FamilyLinkValidator.extractYear("2004"))
        assertEquals(1995, FamilyLinkValidator.extractYear("12/25/1995"))
        assertEquals(1995, FamilyLinkValidator.extractYear("25/12/1995"))
        assertNull(FamilyLinkValidator.extractYear(""))
        assertNull(FamilyLinkValidator.extractYear(null))
        assertNull(FamilyLinkValidator.extractYear("Unknown Date"))
    }

    // ── Direct Lineal & Step-Relation Tests (Arts. 37(1) & 38(3)) ──

    @Test
    fun testMotherAndSonCoParentsBlocked() {
        // Clara (Mother) and Renzy (Son) must NEVER be allowed as co-parents (Art. 37(1))
        val result = FamilyLinkValidator.validateCoParents(renzy, clara, baseFamily)
        assertFalse("Mother and son co-parenting must be strictly blocked", result.isValid)
        assertTrue("Error should mention Article 37 (1)", result.errors.any { it.legalArticle.contains("37 (1)") })
        assertTrue("Error should identify Mother & Son / Lineal", result.errors.any { it.message.contains("Mother & Son", ignoreCase = true) || it.title.contains("Lineal") })

        // Symmetrically, Ghillain's eligible fathers with Clara as existing mother must NOT contain Renzy
        val eligibleFathers = FamilyLinkValidator.getEligibleFathers(ghillain, clara, baseFamily)
        assertFalse("Son Renzy must NOT be in eligible fathers when Clara is mother", eligibleFathers.any { it.id == renzy.id })

        // Eligible mothers with Renzy as existing father must NOT contain Clara
        val eligibleMothers = FamilyLinkValidator.getEligibleMothers(ghillain, renzy, baseFamily)
        assertFalse("Mother Clara must NOT be in eligible mothers when Renzy is father", eligibleMothers.any { it.id == clara.id })
    }

    @Test
    fun testMotherAndSonSpouseBlocked() {
        val result = FamilyLinkValidator.validateSpouse(renzy, clara, baseFamily)
        assertFalse("Marriage between mother and son must be strictly prohibited", result.isValid)
        assertTrue(result.errors.any { it.legalArticle.contains("37 (1)") })
    }

    @Test
    fun testStepMotherAndStepSonCoParentsBlocked() {
        // Pedro is Renzy's father and married to Clara. Renzy's motherId is null in this record.
        val pedroWithSpouse = pedro.copy(spouseId = clara.id)
        val claraWithSpouse = clara.copy(spouseId = pedro.id)
        val renzyOnlyFather = renzy.copy(motherId = null)
        val tree = listOf(pedroWithSpouse, claraWithSpouse, renzyOnlyFather, ghillain)

        val result = FamilyLinkValidator.validateCoParents(renzyOnlyFather, claraWithSpouse, tree)
        assertFalse("Stepmother and stepson co-parenting must be blocked under Art. 38(3)", result.isValid)
        assertTrue("Error must cite Article 38 (3)", result.errors.any { it.legalArticle.contains("38 (3)") })
    }

    @Test
    fun testStepMotherAndStepSonSpouseBlocked() {
        val pedroWithSpouse = pedro.copy(spouseId = clara.id)
        val claraWithSpouse = clara.copy(spouseId = pedro.id)
        val renzyOnlyFather = renzy.copy(motherId = null)
        val tree = listOf(pedroWithSpouse, claraWithSpouse, renzyOnlyFather)

        val result = FamilyLinkValidator.validateSpouse(renzyOnlyFather, claraWithSpouse, tree)
        assertFalse("Marriage between stepmother and stepson must be blocked under Art. 38(3)", result.isValid)
        assertTrue(result.errors.any { it.legalArticle.contains("38 (3)") })
    }

    @Test
    fun testDynamicArbitraryNewMembersBlocked() {
        // Test with arbitrary IDs and names to guarantee rules are 100% universal and not hardcoded
        val personX = Person(id = "id_x_999", firstName = "Alice", gender = "Female", birthDate = "1980-01-01")
        val personY = Person(id = "id_y_888", firstName = "Bob", gender = "Male", birthDate = "2005-01-01", motherId = "id_x_999")
        val personZ = Person(id = "id_z_777", firstName = "Charlie", gender = "Male", birthDate = "2025-01-01")
        val customTree = listOf(personX, personY, personZ)

        // 1. Bob (Son) and Alice (Mother) cannot co-parent Charlie
        val coParentCheck = FamilyLinkValidator.validateCoParents(personY, personX, customTree)
        assertFalse("Arbitrary mother and son cannot co-parent", coParentCheck.isValid)

        // 2. Bob cannot marry Alice
        val spouseCheck = FamilyLinkValidator.validateSpouse(personY, personX, customTree)
        assertFalse("Arbitrary mother and son cannot marry", spouseCheck.isValid)

        // 3. Candidate filtering works universally
        val eligibleMothers = FamilyLinkValidator.getEligibleMothers(personZ, personY, customTree)
        assertFalse("Mother Alice must be excluded from eligible mothers for Bob's child", eligibleMothers.any { it.id == "id_x_999" })
    }

    // ── Bidirectional Linkage & Reciprocal Status Tests ───────────

    @Test
    fun testMutualSpouseResolutionFromEitherDirection() {
        // Scenario: Clara has spouseId pointing to Pedro, but Pedro's spouseId is null in database
        val pedroUnlinked = pedro.copy(spouseId = null, maritalStatus = "Single")
        val claraLinked = clara.copy(spouseId = pedro.id, maritalStatus = "Married")
        val tree = listOf(pedroUnlinked, claraLinked)

        // Resolving spouse for Pedro (the one with null spouseId) using mutual resolution
        val pedroResolvedSpouse = tree.find { FamilyLinkValidator.isSameId(it.id, pedroUnlinked.spouseId) }
            ?: tree.find { FamilyLinkValidator.isSameId(it.spouseId, pedroUnlinked.id) }

        assertNotNull("Pedro must resolve Clara as spouse through mutual link", pedroResolvedSpouse)
        assertEquals("Clara's ID must match", clara.id, pedroResolvedSpouse?.id)

        // Marital status should effectively be Married
        val effectiveStatus = if (pedroResolvedSpouse != null) "Married" else pedroUnlinked.maritalStatus
        assertEquals("Pedro's status must resolve to Married", "Married", effectiveStatus)
    }

    @Test
    fun testParentChildBidirectionalLookup() {
        val childRenzy = renzy.copy(fatherId = pedro.id, motherId = clara.id)
        val tree = listOf(pedro, clara, childRenzy)

        // Look up children for Pedro (Father)
        val pedroChildren = tree.filter { 
            FamilyLinkValidator.isSameId(it.fatherId, pedro.id) || 
            FamilyLinkValidator.isSameId(it.motherId, pedro.id) 
        }
        assertEquals("Pedro must have 1 child (Renzy)", 1, pedroChildren.size)
        assertEquals("Renzy must be Pedro's child", childRenzy.id, pedroChildren.first().id)

        // Look up children for Clara (Mother)
        val claraChildren = tree.filter { 
            FamilyLinkValidator.isSameId(it.fatherId, clara.id) || 
            FamilyLinkValidator.isSameId(it.motherId, clara.id) 
        }
        assertEquals("Clara must have 1 child (Renzy)", 1, claraChildren.size)
        assertEquals("Renzy must be Clara's child", childRenzy.id, claraChildren.first().id)
    }

    // ── ConflictScanner Integrated Relationship Tests ─────────────

    @Test
    fun testConflictScannerDetectsIncestuousCoParents() {
        // User's specific scenario: Renzy and Clara registered as Ghilain's parents, but Renzy is Clara's son
        val renzySon = renzy.copy(motherId = clara.id)
        val ghilainWithInvalidParents = ghillain.copy(fatherId = renzySon.id, motherId = clara.id)
        val tree = listOf(pedro, clara, renzySon, ghilainWithInvalidParents)

        val scanner = ConflictScanner()
        val conflicts = scanner.scanAll(tree)

        assertTrue("ConflictScanner must detect prohibited co-parents conflict", conflicts.any { it.type == "prohibited_coparents" })
        val conflict = conflicts.first { it.type == "prohibited_coparents" }
        assertTrue("Conflict must identify Renzy & Clara violation", conflict.description.contains("Renzy", ignoreCase = true) && conflict.description.contains("Clara", ignoreCase = true))
    }

    @Test
    fun testConflictScannerDetectsProhibitedSpouse() {
        // Attempting to marry mother and son
        val claraWithSonSpouse = clara.copy(spouseId = renzy.id, maritalStatus = "Married")
        val renzyWithMotherSpouse = renzy.copy(motherId = clara.id, spouseId = clara.id, maritalStatus = "Married")
        val tree = listOf(pedro, claraWithSonSpouse, renzyWithMotherSpouse)

        val scanner = ConflictScanner()
        val conflicts = scanner.scanAll(tree)

        assertTrue("ConflictScanner must detect prohibited spouse conflict", conflicts.any { it.type == "prohibited_spouse" })
    }

    @Test
    fun testUniversalArbitraryNewMembersCoParentingBlockedInScanner() {
        // Universal validation test with completely arbitrary random IDs
        val mom = Person(id = "rand_mom_1", firstName = "Eve", gender = "Female", birthDate = "1975-01-01")
        val son = Person(id = "rand_son_2", firstName = "Adam", gender = "Male", birthDate = "2000-01-01", motherId = "rand_mom_1")
        val baby = Person(id = "rand_baby_3", firstName = "Cain", gender = "Male", birthDate = "2024-01-01", fatherId = "rand_son_2", motherId = "rand_mom_1")
        val tree = listOf(mom, son, baby)

        val scanner = ConflictScanner()
        val conflicts = scanner.scanAll(tree)

        assertTrue("Universal mother-son co-parenting must be flagged as conflict", conflicts.any { it.type == "prohibited_coparents" })
    }

    // ── Universal Reciprocal Normalization & Cache Lag Tests ───────

    @Test
    fun testReciprocalSpouseNormalizationInFirestoreHelperCache() {
        // Clara has spouseId pointing to Pedro, Pedro has null spouseId and Single status
        val claraPerson = Person(id = "id_clara", firstName = "Clara", gender = "Female", spouseId = "id_pedro", maritalStatus = "Married")
        val pedroPerson = Person(id = "id_pedro", firstName = "Pedro", gender = "Male", spouseId = null, maritalStatus = "Single")

        com.example.btproject2.firebase.FirestoreHelper.invalidateCache()
        com.example.btproject2.firebase.FirestoreHelper.setCachedPersons(listOf(claraPerson, pedroPerson))

        val cached = com.example.btproject2.firebase.FirestoreHelper.getCachedPersons()
        assertNotNull(cached)

        val pedroInCache = cached?.find { it.id == "id_pedro" }
        assertNotNull("Pedro must exist in cache", pedroInCache)
        assertEquals("Pedro's spouseId must be reciprocally normalized to Clara", "id_clara", pedroInCache?.spouseId)
        assertEquals("Pedro's maritalStatus must be normalized to Married", "Married", pedroInCache?.maritalStatus)
    }

    @Test
    fun testServerLagCachePreservationRetainsMotherId() {
        // Simulate local cache already having child with motherId = Luz
        val motherPerson = Person(id = "m_1", firstName = "Luz", lastName = "Bugarin", gender = "Female")
        val childWithMother = Person(id = "c_1", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", motherId = "m_1")

        com.example.btproject2.firebase.FirestoreHelper.invalidateCache()
        com.example.btproject2.firebase.FirestoreHelper.setCachedPersons(listOf(motherPerson, childWithMother))

        // Simulate server returning stale snapshot where child's motherId is still empty due to write lag
        val staleServerChild = Person(id = "c_1", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", motherId = null)
        com.example.btproject2.firebase.FirestoreHelper.setCachedPersons(listOf(motherPerson, staleServerChild))

        val cached = com.example.btproject2.firebase.FirestoreHelper.getCachedPersons()
        val childCached = cached?.find { it.id == "c_1" }

        assertEquals("Server lag must not clobber cached motherId", "m_1", childCached?.motherId)

        // Effective members must catch co-parent violation even if activity had stale list
        val activityStaleMembers = listOf(motherPerson, staleServerChild)
        val effectiveMembers = (activityStaleMembers + com.example.btproject2.firebase.FirestoreHelper.getCachedPersons().orEmpty()).distinctBy { it.id.trim().lowercase() }

        val coParentCheck = FamilyLinkValidator.validateCoParents(childCached, motherPerson, effectiveMembers)
        assertFalse("Effective members check must prevent co-parenting despite server read lag", coParentCheck.isValid)
    }

    @Test
    fun testGrandmotherCannotBeMotherToGrandchildUniversalRule() {
        // Clara is mother of Luz, Luz is mother of Ghillain
        val ghillainWithLuz = ghillain.copy(motherId = luz.id)
        val tree = listOf(pedro, clara, renzy, luz, ghillainWithLuz)

        // 1. Explicit check: Clara cannot be mother of Ghillain
        val checkClaraGhillain = FamilyLinkValidator.validateParentChild(clara, ghillainWithLuz, "mother", tree)
        assertFalse("Grandmother Clara must NOT be allowed as mother of Ghillain", checkClaraGhillain.isValid)
        assertTrue("Error must identify grandparent leap", checkClaraGhillain.errors.any { it.title.contains("Grandparent") || it.message.contains("grandmother", ignoreCase = true) })

        // 2. Candidate filtering check: Clara must not appear in eligible mothers for Ghillain
        val eligibleMothers = FamilyLinkValidator.getEligibleMothers(ghillainWithLuz, null, tree)
        assertFalse("Grandmother Clara must not appear in eligible mothers for Ghillain", eligibleMothers.any { it.id == clara.id })

        // 3. Universal arbitrary check with random IDs
        val gma = Person(id = "rand_gma_10", firstName = "Martha", gender = "Female", birthDate = "1960-01-01")
        val mom = Person(id = "rand_mom_11", firstName = "Sarah", gender = "Female", birthDate = "1985-01-01", motherId = "rand_gma_10")
        val baby = Person(id = "rand_baby_12", firstName = "Emma", gender = "Female", birthDate = "2020-01-01", motherId = "rand_mom_11")
        val customTree = listOf(gma, mom, baby)

        val checkUniversalGrandma = FamilyLinkValidator.validateParentChild(gma, baby, "mother", customTree)
        assertFalse("Universal rule: Grandmother cannot be mother of grandchild", checkUniversalGrandma.isValid)
        val candidateMothers = FamilyLinkValidator.getEligibleMothers(baby, null, customTree)
        assertFalse("Universal rule: Grandmother cannot be in candidate mothers", candidateMothers.any { it.id == "rand_gma_10" })
    }

    @Test
    fun testMaternalUncleCannotBeFatherToNieceUniversalRule() {
        // Renzy and Luz are siblings (both children of Pedro & Clara). Luz is mother of Ghillain.
        val ghillainWithLuz = ghillain.copy(motherId = luz.id)
        val tree = listOf(pedro, clara, renzy, luz, ghillainWithLuz)

        // 1. Explicit check: Renzy cannot be father of Ghillain
        val checkRenzyGhillain = FamilyLinkValidator.validateParentChild(renzy, ghillainWithLuz, "father", tree)
        assertFalse("Maternal uncle Renzy must NOT be allowed as father of Ghillain", checkRenzyGhillain.isValid)

        // 2. Candidate filtering check: Renzy must not appear in eligible fathers for Ghillain
        val eligibleFathers = FamilyLinkValidator.getEligibleFathers(ghillainWithLuz, luz, tree)
        assertFalse("Maternal uncle Renzy must not appear in eligible fathers for Ghillain", eligibleFathers.any { it.id == renzy.id })

        // 3. Co-parenting check: Renzy and Luz cannot co-parent Ghillain
        val coParentCheck = FamilyLinkValidator.validateCoParents(renzy, luz, tree)
        assertFalse("Uncle and mother (siblings) cannot co-parent", coParentCheck.isValid)
    }

    @Test
    fun testClaraAndRenzyCannotCoParentAnyNewChildUniversalRule() {
        // Even if owner creates a completely new child member with arbitrary ID
        val newChild = Person(id = "new_child_99", firstName = "Newborn", lastName = "Bugarin", gender = "Female", birthDate = "2026-01-01")
        val tree = listOf(pedro, clara, renzy, luz, newChild)

        // Co-parents check must block Renzy and Clara
        val coParentResult = FamilyLinkValidator.validateCoParents(renzy, clara, tree)
        assertFalse("Mother Clara and Son Renzy must NEVER co-parent any new child", coParentResult.isValid)
        assertTrue(coParentResult.errors.any { it.legalArticle.contains("37 (1)") })

        // If mother is Clara, Renzy cannot be eligible father
        val fathers = FamilyLinkValidator.getEligibleFathers(newChild, clara, tree)
        assertFalse("Renzy cannot be eligible father when mother is Clara", fathers.any { it.id == renzy.id })

        // If father is Renzy, Clara cannot be eligible mother
        val mothers = FamilyLinkValidator.getEligibleMothers(newChild, renzy, tree)
        assertFalse("Clara cannot be eligible mother when father is Renzy", mothers.any { it.id == clara.id })
    }

    @Test
    fun testSanitizeTreeRecordsAutoRepairsGhillainClaraRenzyAndEnforcesUniversalRules() {
        val josePatriarch = Person(id = "id_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male")
        val unlinkedRenzy = renzy.copy(motherId = null, fatherId = null)
        val unlinkedLuz = luz.copy(motherId = null, fatherId = null)
        val unlinkedPedro = pedro.copy(spouseId = null, maritalStatus = "Single")
        val unlinkedClara = clara.copy(spouseId = null, maritalStatus = "Single")
        val corruptedGhillain = ghillain.copy(motherId = clara.id, fatherId = renzy.id)

        val rawTree = listOf(josePatriarch, unlinkedPedro, unlinkedClara, unlinkedRenzy, unlinkedLuz, corruptedGhillain)
        val repairedTree = com.example.btproject2.firebase.FirestoreHelper.sanitizeTreeRecords(rawTree)

        val repJose = repairedTree.first { it.id == josePatriarch.id }
        val repPedro = repairedTree.first { it.id == pedro.id }
        val repClara = repairedTree.first { it.id == clara.id }
        val repRenzy = repairedTree.first { it.id == renzy.id }
        val repLuz = repairedTree.first { it.id == luz.id }
        val repGhillain = repairedTree.first { it.id == ghillain.id }

        // 1. Pedro and Clara must be mutually married
        assertEquals(repClara.id, repPedro.spouseId)
        assertEquals(repPedro.id, repClara.spouseId)
        assertEquals("Married", repPedro.maritalStatus)
        assertEquals("Married", repClara.maritalStatus)

        // 2. Renzy & Clara are siblings sharing father Jose (Renzy is NOT child of Clara)
        assertEquals(repJose.id, repRenzy.fatherId)
        assertNull("Renzy must NOT have Clara as mother", repRenzy.motherId)
        assertEquals(repJose.id, repClara.fatherId)
        assertNotEquals("Renzy and Clara must NOT be parent and child", repClara.id, repRenzy.motherId)

        // 3. Ghillain must NOT have Clara as mother and must NOT have Renzy as father
        assertNotEquals("Ghillain mother must NOT be Clara", repClara.id, repGhillain.motherId)
        assertEquals("Ghillain mother must be auto-assigned to Luz", repLuz.id, repGhillain.motherId)
        assertNull("Ghillain father Renzy must be disconnected", repGhillain.fatherId)

        // 4. Clara & Renzy must NOT have Ghillain as a child in lookups
        val claraChildren = repairedTree.filter { it.fatherId == repClara.id || it.motherId == repClara.id }
        assertFalse("Clara must NOT have Ghillain in her children", claraChildren.any { it.id == repGhillain.id })

        val renzyChildren = repairedTree.filter { it.fatherId == repRenzy.id || it.motherId == repRenzy.id }
        assertFalse("Renzy must NOT have Ghillain in his children", renzyChildren.any { it.id == repGhillain.id })
    }

    // ── Two Parents Cap & Error Message Verification Tests ────────

    @Test
    fun testChildWithTwoParentsCannotBeAddedToNewMember() {
        // Renzy has Pedro as father and Clara as mother (2 parents)
        val newMember = Person(id = "p_new_member", firstName = "Antonio", lastName = "Santos", gender = "Male", birthDate = "1965-01-01")
        val tree = listOf(pedro, clara, renzy, newMember)

        // Attempting to link Renzy as a child of newMember must be blocked
        val result = FamilyLinkValidator.validateParentChild(newMember, renzy, "father", tree)
        assertFalse("Person with two parents cannot be linked as child of a new member", result.isValid)
        assertTrue("Error title must mention Already Has Two Parents", result.errors.any { it.title.contains("Already Has Two Parents", ignoreCase = true) })
    }

    @Test
    fun testErrorMessageIncludesRealParentNames() {
        // Child with two parents: Pedro Bugarin & Clara Bugarin
        val child = Person(
            id = "p_test_child",
            firstName = "Ghillain",
            lastName = "Bugarin",
            fatherId = pedro.id,
            motherId = clara.id
        )
        val newMember = Person(id = "p_new_woman", firstName = "Elena", lastName = "Cruz", gender = "Female", birthDate = "1970-01-01")
        val tree = listOf(pedro, clara, child, newMember)

        val result = FamilyLinkValidator.validateParentChild(newMember, child, "mother", tree)
        assertFalse(result.isValid)

        val error = result.errors.first()
        // Must contain child's name
        assertTrue("Error message must mention Ghillain Bugarin", error.message.contains("Ghillain Bugarin"))
        // Must contain both real parents' names
        assertTrue("Error message must mention Father Pedro Bugarin", error.message.contains("Pedro Bugarin"))
        assertTrue("Error message must mention Mother Clara Bugarin", error.message.contains("Clara Bugarin"))
        // Must contain the specific explanatory sentence
        assertTrue(
            "Error message must explain two parents rule",
            error.message.contains("A person who already has two parents cannot be added as another member’s child")
        )
    }

    @Test
    fun testChildWithOneParentCanOnlyAcceptOtherGenderParent() {
        // Child has only father Pedro
        val childOnlyFather = Person(id = "p_single_parent_child", firstName = "Leo", lastName = "Bugarin", fatherId = pedro.id)
        val newMaleMember = Person(id = "p_male_2", firstName = "Carlos", lastName = "Bugarin", gender = "Male", birthDate = "1968-01-01")
        val tree = listOf(pedro, childOnlyFather, newMaleMember)

        // Attempting to add a 2nd father must be blocked
        val maleResult = FamilyLinkValidator.validateParentChild(newMaleMember, childOnlyFather, "father", tree)
        assertFalse("Cannot have two biological fathers", maleResult.isValid)
        assertTrue(maleResult.errors.first().message.contains("Pedro Bugarin"))
        assertTrue(maleResult.errors.first().message.contains("cannot have more than one biological father"))
    }

    // ── Uncle / Aunt Collateral Parent Prohibitions ───────────────

    @Test
    fun testUncleCannotBeLinkedAsFatherOfNiece() {
        // Pedro & Clara are parents of Jose (b. 1987) and Renzy (b. 2000)
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male", birthDate = "1987-01-01", fatherId = pedro.id, motherId = clara.id)
        // Ghillain is daughter of Renzy
        val ghillainDaughter = Person(id = "p_ghillain_kid", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", birthDate = "2024-01-01", fatherId = renzy.id)
        val tree = listOf(pedro, clara, jose, renzy, ghillainDaughter)

        // Jose is Ghillain's biological uncle. Attempting to link Jose as father must be blocked!
        val result = FamilyLinkValidator.validateParentChild(jose, ghillainDaughter, "father", tree)
        assertFalse("Uncle Jose cannot be linked as father of niece Ghillain", result.isValid)
        val error = result.errors.first()
        assertTrue("Error title must mention Uncle or Already Has Two Parents or Slot Occupied", 
            error.title.contains("Uncle", ignoreCase = true) || error.title.contains("Slot Occupied", ignoreCase = true) || error.title.contains("Parent Slot Already Occupied", ignoreCase = true))
    }

    @Test
    fun testUncleCannotBeSelectedAsEligibleFather() {
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male", birthDate = "1987-01-01", fatherId = pedro.id, motherId = clara.id)
        val ghillainDaughter = Person(id = "p_ghillain_kid", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", birthDate = "2024-01-01", fatherId = renzy.id)
        val tree = listOf(pedro, clara, jose, renzy, ghillainDaughter)

        val eligibleFathers = FamilyLinkValidator.getEligibleFathers(ghillainDaughter, null, tree)
        assertFalse("Uncle Jose must NOT be in eligible fathers for Ghillain", eligibleFathers.any { it.id == jose.id })
    }

    @Test
    fun testNieceCannotBeSelectedAsEligibleChildForUncle() {
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male", birthDate = "1987-01-01", fatherId = pedro.id, motherId = clara.id)
        val ghillainDaughter = Person(id = "p_ghillain_kid", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", birthDate = "2024-01-01", fatherId = renzy.id)
        val tree = listOf(pedro, clara, jose, renzy, ghillainDaughter)

        val eligibleChildren = FamilyLinkValidator.getEligibleChildren(jose, tree)
        assertFalse("Niece Ghillain must NOT be in eligible children for Uncle Jose", eligibleChildren.any { it.id == ghillainDaughter.id })
    }

    @Test
    fun testUncleCannotBeFatherWhenMotherIsSisterLuz() {
        // Clara & Pedro -> Jose (brother) & Luz (sister)
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male", birthDate = "1987-01-01", fatherId = pedro.id, motherId = clara.id)
        val ghillainWithLuz = Person(id = "p_ghillain_luz", firstName = "Ghillain", lastName = "Bugarin", gender = "Female", birthDate = "2024-01-01", motherId = luz.id)
        val tree = listOf(pedro, clara, jose, luz, ghillainWithLuz)

        val result = FamilyLinkValidator.validateParentChild(jose, ghillainWithLuz, "father", tree)
        assertFalse("Jose cannot be father of Ghillain when Luz is mother (Jose is Uncle and sibling of Luz)", result.isValid)
        assertTrue("Error must identify Uncle or Sibling co-parenting", result.errors.any { it.title.contains("Uncle", ignoreCase = true) || it.title.contains("Siblings", ignoreCase = true) })

        val eligibleFathers = FamilyLinkValidator.getEligibleFathers(ghillainWithLuz, luz, tree)
        assertFalse("Uncle Jose must not be eligible father when mother is Luz", eligibleFathers.any { it.id == jose.id })
    }

    @Test
    fun testSiblingMarriageBetweenJoseAndLuzBlocked() {
        val jose = Person(id = "p_jose", firstName = "Jose", lastName = "Bugarin", gender = "Male", birthDate = "1987-01-01", fatherId = pedro.id, motherId = clara.id)
        val tree = listOf(pedro, clara, jose, luz)

        val spouseCheck = FamilyLinkValidator.validateSpouse(jose, luz, tree)
        assertFalse("Siblings Jose and Luz cannot marry under Art. 37(2)", spouseCheck.isValid)
        assertTrue(spouseCheck.errors.any { it.title.contains("Sibling", ignoreCase = true) || it.legalArticle.contains("37") })

        val eligibleSpouses = FamilyLinkValidator.getEligibleSpouses(jose, tree)
        assertFalse("Sister Luz must NOT be in eligible spouses for Jose", eligibleSpouses.any { it.id == luz.id })
    }
}

