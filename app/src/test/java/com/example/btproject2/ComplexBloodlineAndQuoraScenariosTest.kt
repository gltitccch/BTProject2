package com.example.btproject2

import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.engine.ConflictScanner
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying complex multi-generational bloodline tracing, connection linking guards,
 * and the real-world Quora family scenarios (Sharon's 38yo Mother-in-Law vs. 40yo Son-in-Law,
 * William's 25yo Step-Grandmother married to 80yo Grandfather, 27yo Nephew with 8yo Aunt,
 * and 27yo Grandchild vs. 3yo Grandmother with missing middle parent birth date).
 */
class ComplexBloodlineAndQuoraScenariosTest {

    private val tracer = BloodlineTracer()

    @Test
    fun `test 27yo grandchild cannot have 3yo grandmother even when middle parent has blank birth date`() {
        // Grandchild Person A is 27 yrs old (born 1999)
        // Middle Parent has NO birth date recorded ("")
        // Grandmother is 3 yrs old (born 2023)
        val grandmother = Person(
            id = "gm_3yo",
            firstName = "Baby",
            lastName = "Lola",
            gender = "Female",
            birthDate = "2023-05-10"
        )
        val fatherNoBirthDate = Person(
            id = "father_blank_dob",
            firstName = "Carlos",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "", // Blank birth date!
            motherId = "gm_3yo"
        )
        val grandchild27yo = Person(
            id = "person_a_27yo",
            firstName = "Marco",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1999-01-15"
        )
        val allPersons = listOf(grandmother, fatherNoBirthDate, grandchild27yo)

        // 1. Trying to link fatherNoBirthDate (whose mother is 3yo) as father of 27yo Marco must FAIL!
        val linkResult = FamilyLinkValidator.validateParentChild(
            parent = fatherNoBirthDate,
            child = grandchild27yo,
            role = "father",
            allPersons = allPersons
        )
        assertFalse(
            "Linking a middle parent whose mother is 3yo to a 27yo child must be blocked by multi-generation age guard",
            linkResult.isValid
        )
        assertTrue(
            "Error message should mention grandmother/multi-generation age impossibility",
            linkResult.errors.any {
                it.title.contains("Multi-Generation Age Gap", ignoreCase = true) ||
                    it.message.contains("grandmother", ignoreCase = true)
            }
        )

        // 2. Also verify ConflictScanner flags an existing tree where Marco already has fatherId = father_blank_dob
        val linkedGrandchild = grandchild27yo.copy(fatherId = "father_blank_dob")
        val conflicts = ConflictScanner().scanAll(listOf(grandmother, fatherNoBirthDate, linkedGrandchild))
        assertTrue(
            "ConflictScanner must flag multi-generation age conflict when grandmother is younger than grandchild",
            conflicts.any { it.type == "age_conflict" && it.personId == "person_a_27yo" }
        )
    }

    @Test
    fun `test younger aunt 8yo and older nephew 27yo traced directionally with generational insight and blocked from reverse parentage`() {
        val lolo = Person(
            id = "lolo_1",
            firstName = "Roberto",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1950-03-01"
        )
        val eldestSon = Person(
            id = "eldest_son",
            firstName = "Eduardo",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1972-06-12",
            fatherId = "lolo_1"
        )
        // Marco is 27 years old (born 1999), son of Eduardo
        val nephew27yo = Person(
            id = "nephew_marco",
            firstName = "Marco",
            lastName = "Santos",
            gender = "Male",
            birthDate = "1999-04-20",
            fatherId = "eldest_son"
        )
        // Clarita is 8 years old (born 2018), late-life daughter of Roberto (Marco's grandfather)
        val aunt8yo = Person(
            id = "aunt_clarita",
            firstName = "Clarita",
            lastName = "Santos",
            gender = "Female",
            birthDate = "2018-09-10",
            fatherId = "lolo_1"
        )

        val allPersons = listOf(lolo, eldestSon, nephew27yo, aunt8yo)
        val personMap = allPersons.associateBy { it.id }

        // 1. Trace from 27yo Nephew (Marco) to 8yo Aunt (Clarita)
        val trace = tracer.trace("nephew_marco", "aunt_clarita", personMap)
        assertTrue(trace.isRelated)
        assertEquals("Uncle/Aunt and Niece/Nephew", trace.relationshipType)
        assertEquals(3, trace.degreeOfConsanguinity)
        assertTrue(
            "Explanation must explicitly identify Clarita as the aunt and Marco as the nephew",
            trace.explanation.contains("Clarita Santos is the aunt and Marco Santos is the nephew")
        )
        assertTrue(
            "Explanation must include the Generational vs. Age Note explaining why 8yo Clarita is the aunt of 27yo Marco",
            trace.explanation.contains("Generational vs. Age Note")
        )

        // 2. Verify KinshipTitleHelper badges in both directions
        assertEquals("AUNT", KinshipTitleHelper.resolveTitle(aunt8yo, nephew27yo, allPersons))
        assertEquals("NEPHEW", KinshipTitleHelper.resolveTitle(nephew27yo, aunt8yo, allPersons))

        // 3. Verify 27yo Nephew is strictly blocked from adding his 8yo Aunt as his biological child!
        val reverseParentageCheck = FamilyLinkValidator.validateParentChild(
            parent = nephew27yo,
            child = aunt8yo,
            role = "father",
            allPersons = allPersons
        )
        assertFalse(
            "27yo Nephew must be blocked from registering his 8yo Aunt as his child",
            reverseParentageCheck.isValid
        )
        assertTrue(
            "Error message must accurately state that Marco is the nephew and Clarita is his aunt",
            reverseParentageCheck.errors.any {
                it.message.contains("nephew", ignoreCase = true) && it.message.contains("aunt", ignoreCase = true)
            }
        )
    }

    @Test
    fun `test Quora Sharon scenario - 38yo maternal grandmother with 22yo daughter married to 40yo son-in-law`() {
        // Sharon's story:
        // Elena (born 1988, age 38) had daughter Sofia (born 2004, age 22) at age 16.
        // Sofia (age 22) married Richard (born 1986, age 40).
        // Sofia and Richard have child Leo (born 2025, age 1).
        // Result: Father Richard (40) is 2 years older than Maternal Grandmother Elena (38)!
        val grandmaElena38 = Person(
            id = "elena_38",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1988-02-14"
        )
        val momSofia22 = Person(
            id = "sofia_22",
            firstName = "Sofia",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "2004-06-01",
            motherId = "elena_38",
            spouseId = "richard_40",
            maritalStatus = "Married"
        )
        val dadRichard40 = Person(
            id = "richard_40",
            firstName = "Richard",
            lastName = "Gomez",
            gender = "Male",
            birthDate = "1986-01-20",
            spouseId = "sofia_22",
            maritalStatus = "Married"
        )
        val childLeo = Person(
            id = "leo_1",
            firstName = "Leo",
            lastName = "Gomez",
            gender = "Male",
            birthDate = "2025-03-10",
            fatherId = "richard_40",
            motherId = "sofia_22"
        )

        val allPersons = listOf(grandmaElena38, momSofia22, dadRichard40, childLeo)
        val personMap = allPersons.associateBy { it.id }

        // 1. Validate that Elena -> Sofia (16 yr gap), Sofia + Richard marriage, and Leo's parentage are all VALID!
        assertTrue(FamilyLinkValidator.validateParentChild(grandmaElena38, momSofia22, "mother", allPersons).isValid)
        assertTrue(FamilyLinkValidator.validateSpouse(momSofia22, dadRichard40, allPersons).isValid)
        assertTrue(FamilyLinkValidator.validateParentChild(dadRichard40, childLeo, "father", allPersons).isValid)
        assertTrue(FamilyLinkValidator.validateParentChild(momSofia22, childLeo, "mother", allPersons).isValid)

        // 2. Trace between 38yo Mother-in-Law Elena and 40yo Son-in-Law Richard
        val inLawTrace = tracer.trace("elena_38", "richard_40", personMap)
        assertTrue(inLawTrace.isRelated)
        assertEquals("Mother-in-Law / Son-in-Law", inLawTrace.relationshipType)
        assertTrue(
            "Explanation must include the Generational & Affinity Age Note explaining why 40yo Richard is the son-in-law of 38yo Elena",
            inLawTrace.explanation.contains("Generational & Affinity Age Note")
        )
    }

    @Test
    fun `test Quora William scenario - 80yo widowed grandfather remarries 25yo step-grandmother`() {
        // William's story:
        // 80yo Grandfather Don (born 1946) remarries 25yo Trixie (born 2001).
        // Grandson Liam is 27yo (born 1999), son of Don's child Arthur (born 1972).
        val grandpa80 = Person(
            id = "don_80",
            firstName = "Don",
            lastName = "Valdez",
            gender = "Male",
            birthDate = "1946-01-10",
            spouseId = "trixie_25",
            maritalStatus = "Married"
        )
        val stepGrandma25 = Person(
            id = "trixie_25",
            firstName = "Trixie",
            lastName = "Valdez",
            gender = "Female",
            birthDate = "2001-08-15",
            spouseId = "don_80",
            maritalStatus = "Married"
        )
        val dadArthur54 = Person(
            id = "arthur_54",
            firstName = "Arthur",
            lastName = "Valdez",
            gender = "Male",
            birthDate = "1972-04-05",
            fatherId = "don_80"
        )
        val grandsonLiam27 = Person(
            id = "liam_27",
            firstName = "Liam",
            lastName = "Valdez",
            gender = "Male",
            birthDate = "1999-11-20",
            fatherId = "arthur_54"
        )

        val allPersons = listOf(grandpa80, stepGrandma25, dadArthur54, grandsonLiam27)
        val personMap = allPersons.associateBy { it.id }

        // 1. KinshipTitleHelper must classify Trixie as STEP-GRANDMOTHER of Liam (and Liam as STEP-GRANDSON of Trixie)
        assertEquals("STEP-GRANDMOTHER", KinshipTitleHelper.resolveTitle(stepGrandma25, grandsonLiam27, allPersons))
        assertEquals("STEP-GRANDSON", KinshipTitleHelper.resolveTitle(grandsonLiam27, stepGrandma25, allPersons))

        // 2. BloodlineTracer must classify Trixie and Liam as Step-Grandmother / Step-Grandson with Step-Family Age Note
        val stepTrace = tracer.trace("trixie_25", "liam_27", personMap)
        assertTrue(stepTrace.isRelated)
        assertEquals("Step-Grandmother / Step-Grandson", stepTrace.relationshipType)
        assertEquals("Affinity / Step", stepTrace.relationshipCategory)
        assertTrue(
            "Explanation must include the Generational & Step-Family Age Note when 25yo step-grandmother is younger than 27yo step-grandson",
            stepTrace.explanation.contains("Generational & Step-Family Age Note")
        )
    }

    @Test
    fun `test planTreeDeduplication removes synthetic _bal_ padding and merges duplicate copies while rewiring links`() {
        val primaryDad = Person(
            id = "dad_primary",
            firstName = "Mateo",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1951-04-12",
            fatherId = "grandpa_1",
            motherId = "grandma_1",
            spouseId = "mom_primary",
            maritalStatus = "Married"
        )
        val duplicateDad = Person(
            id = "dad_dup_copy",
            firstName = "Mateo",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1951-04-12",
            biography = "Patriarch of the Bugarin clan"
        )
        val mom = Person(
            id = "mom_primary",
            firstName = "Alba",
            lastName = "Bugarin",
            gender = "Female",
            birthDate = "1954-08-19",
            spouseId = "dad_primary",
            maritalStatus = "Married"
        )
        // Child accidentally pointing to duplicateDad instead of primaryDad
        val child = Person(
            id = "child_1",
            firstName = "Carlos",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1978-03-11",
            fatherId = "dad_dup_copy",
            motherId = "mom_primary"
        )
        // Synthetic balancing padding clone
        val balPadding = Person(
            id = "tree_bal_001",
            firstName = "Rafael",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "1990-01-01"
        )

        val plan = com.example.btproject2.firebase.FirestoreHelper.planTreeDeduplication(
            listOf(primaryDad, duplicateDad, mom, child, balPadding)
        )

        assertTrue(plan.hasDuplicates)
        assertEquals(3, plan.uniqueCount)
        assertEquals(2, plan.duplicatesRemovedCount)
        assertTrue(plan.duplicateIdsToDelete.contains("dad_dup_copy"))
        assertTrue(plan.duplicateIdsToDelete.contains("tree_bal_001"))

        val cleanedChild = plan.cleanedMembers.first { it.id == "child_1" }
        assertEquals("dad_primary", cleanedChild.fatherId)

        val cleanedDad = plan.cleanedMembers.first { it.id == "dad_primary" }
        assertEquals("Patriarch of the Bugarin clan", cleanedDad.biography)
    }

    @Test
    fun `test Merged Clan Tree switcherDisplayLabel coOwner recognition and merge completion notification`() {
        val personalTree = com.example.btproject2.models.FamilyTree(
            id = "tree_personal_1",
            name = "Bugarin Family",
            ownerId = "user_alpha",
            treeType = com.example.btproject2.models.FamilyTree.TREE_TYPE_PERSONAL
        )
        val mergedClanTree = com.example.btproject2.models.FamilyTree(
            id = "tree_clan_c",
            name = "Cruz-Bugarin Master Clan",
            ownerId = "user_alpha",
            coOwnerIds = listOf("user_alpha", "user_beta"),
            treeType = com.example.btproject2.models.FamilyTree.TREE_TYPE_MERGED_CLAN,
            memberCount = 122
        )

        // 1. Verify switcher display labels for "Select Family Tree" dialog
        assertFalse(personalTree.isMergedClan())
        assertEquals("Bugarin Family", personalTree.switcherDisplayLabel())
        assertTrue(mergedClanTree.isMergedClan())
        assertEquals("🏛️ Cruz-Bugarin Master Clan (Merged Clan)", mergedClanTree.switcherDisplayLabel())

        // 2. Verify co-owner recognition across both merged family tree owners
        assertTrue(mergedClanTree.isOwnedBy("user_alpha"))
        assertTrue(mergedClanTree.isOwnedBy("user_beta"))
        assertFalse(mergedClanTree.isOwnedBy("user_stranger"))

        // 3. Verify merge completion notification explains optional tree switcher and is categorized properly
        val notif = com.example.btproject2.utils.NotificationHelper.createClanMergeCompletedNotification(
            masterTreeId = mergedClanTree.id,
            masterTreeName = mergedClanTree.name,
            recipientUserId = "user_beta",
            combinedMemberCount = 122,
            sourceTreeId = personalTree.id
        )
        assertEquals("CLAN_MERGE_COMPLETED", notif.type)
        assertTrue(notif.title.contains("Master Clan Tree Merged Successfully"))
        assertTrue(notif.message.contains("Select Family Tree"))

        val accessFiltered = com.example.btproject2.utils.NotificationHelper.filterNotifications(
            listOf(notif),
            com.example.btproject2.utils.NotificationHelper.CATEGORY_ACCESS
        )
        assertEquals(1, accessFiltered.size)
    }

    @Test
    fun `test InsightsActivity computeClanBranchBreakdown and computeUniqueRelationshipsCount match HomeActivity`() {
        val members = listOf(
            Person(id = "p1", firstName = "Mateo", lastName = "Bugarin", spouseId = "p2"),
            Person(id = "p2", firstName = "Alba", lastName = "Reyes", spouseId = "p1"),
            Person(id = "p3", firstName = "Carlos", lastName = "Bugarin", fatherId = "p1", motherId = "p2", spouseId = "p4"),
            Person(id = "p4", firstName = "Elena", lastName = "Santos", spouseId = "p3"),
            Person(id = "p5", firstName = "Lucia", lastName = "bugarin", fatherId = "p3", motherId = "p4")
        )

        val breakdown = com.example.btproject2.ui.activities.InsightsActivity.computeClanBranchBreakdown(members)
        val branchCount = com.example.btproject2.ui.activities.InsightsActivity.computeClanBranchCount(members)
        val uniqueRelCount = com.example.btproject2.ui.activities.InsightsActivity.computeUniqueRelationshipsCount(members)

        // 3 distinct case-insensitive surname branches: Bugarin (3), Reyes (1), Santos (1)
        assertEquals(3, branchCount)
        assertEquals(3, breakdown.size)
        assertEquals("Bugarin", breakdown[0].first)
        assertEquals(3, breakdown[0].second)

        // 4 parent links (p3 has 2, p5 has 2) + 2 unique married couples (p1-p2, p3-p4) = 6
        assertEquals(6, uniqueRelCount)
    }

    @Test
    fun `test BloodlineTracer distinguishes Full Double First Cousins from Half-Double First Cousins and ExportTreeActivity unique marriage unions`() {
        val tracer = BloodlineTracer()

        // Paternal grandparents & Maternal grandparents (where maternal brothers are only HALF-brothers sharing only m_gma)
        val pGpa = Person(id = "p_gpa", firstName = "Tomas", lastName = "Cruz", gender = "Male")
        val pGma = Person(id = "p_gma", firstName = "Elena", lastName = "Cruz", gender = "Female")
        val mGpa1 = Person(id = "m_gpa1", firstName = "Ramon", lastName = "Reyes", gender = "Male")
        val mGpa2 = Person(id = "m_gpa2", firstName = "julio", lastName = "Santos", gender = "Male")
        val mGma = Person(id = "m_gma", firstName = "Clara", lastName = "Reyes", gender = "Female")

        // Fathers are FULL brothers (share p_gpa & p_gma)
        val dad1 = Person(id = "dad1", firstName = "Marco", lastName = "Cruz", gender = "Male", fatherId = "p_gpa", motherId = "p_gma", spouseId = "mom1")
        val dad2 = Person(id = "dad2", firstName = "Dante", lastName = "Cruz", gender = "Male", fatherId = "p_gpa", motherId = "p_gma", spouseId = "mom2")

        // Mothers are HALF sisters (share m_gma, different fathers m_gpa1 & m_gpa2)
        val mom1 = Person(id = "mom1", firstName = "Rosa", lastName = "Reyes", gender = "Female", fatherId = "m_gpa1", motherId = "m_gma", spouseId = "dad1")
        val mom2 = Person(id = "mom2", firstName = "Lina", lastName = "Santos", gender = "Female", fatherId = "m_gpa2", motherId = "m_gma", spouseId = "dad2")

        val cousinA = Person(id = "cousinA", firstName = "Leo", lastName = "Cruz", gender = "Male", fatherId = "dad1", motherId = "mom1")
        val cousinB = Person(id = "cousinB", firstName = "Maya", lastName = "Cruz", gender = "Female", fatherId = "dad2", motherId = "mom2")

        val halfDoubleMap = listOf(pGpa, pGma, mGpa1, mGpa2, mGma, dad1, dad2, mom1, mom2, cousinA, cousinB).associateBy { it.id }
        val halfDoubleRes = tracer.trace("cousinA", "cousinB", halfDoubleMap)
        assertEquals("Half-Double First Cousins", halfDoubleRes.relationshipType)
        assertEquals("Half-Double First Cousins (Paternal & Maternal Half-Lineage)", halfDoubleRes.bloodlineSubtype)
        assertTrue(halfDoubleRes.explanation.contains("half-double first cousins"))
        val halfDoubleBadges = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(halfDoubleMap.values.toList(), cousinA)
        assertEquals("HALF-DOUBLE COUSIN", halfDoubleBadges["cousinB"])

        // When mom2 also shares m_gpa1 (making mothers FULL sisters too), they become Full Double First Cousins
        val fullMom2 = mom2.copy(fatherId = "m_gpa1")
        val fullDoubleMap = halfDoubleMap.toMutableMap().apply { put("mom2", fullMom2) }
        val fullDoubleRes = tracer.trace("cousinA", "cousinB", fullDoubleMap)
        assertEquals("Double First Cousins", fullDoubleRes.relationshipType)
        assertEquals("Double First Cousins (Paternal & Maternal)", fullDoubleRes.bloodlineSubtype)
        assertTrue(fullDoubleRes.explanation.contains("double first cousins"))
        val fullDoubleBadges = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(fullDoubleMap.values.toList(), cousinA)
        assertEquals("DOUBLE COUSIN", fullDoubleBadges["cousinB"])

        // Regular Full First Cousins (only fathers are brothers; mothers are unrelated)
        val unrelatedMom2 = mom2.copy(fatherId = null, motherId = null)
        val regularFullCousinMap = halfDoubleMap.toMutableMap().apply { put("mom2", unrelatedMom2) }
        val regularFullRes = tracer.trace("cousinA", "cousinB", regularFullCousinMap)
        assertEquals("First Cousins", regularFullRes.relationshipType)
        assertEquals("Full First Cousins", regularFullRes.bloodlineSubtype)

        // Regular Half-First Cousins (only mothers are half-sisters; fathers are unrelated)
        val unrelatedDad2 = dad2.copy(fatherId = null, motherId = null)
        val regularHalfCousinMap = halfDoubleMap.toMutableMap().apply { put("dad2", unrelatedDad2) }
        val regularHalfRes = tracer.trace("cousinA", "cousinB", regularHalfCousinMap)
        assertEquals("First Cousins", regularHalfRes.relationshipType)
        assertEquals("Half-First Cousins", regularHalfRes.bloodlineSubtype)

        // Verify ExportTreeActivity.computeUniqueMarriageUnionsCount handles remarriages and asymmetric spouse pointers accurately
        val remarriedHusband = Person(id = "h1", firstName = "Carlos", spouseId = "w2", formerSpouseIds = listOf("w1"))
        val firstWife = Person(id = "w1", firstName = "Ana", formerSpouseIds = listOf("h1"))
        val secondWife = Person(id = "w2", firstName = "Bea", spouseId = "h1")
        val marriageUnions = com.example.btproject2.ui.activities.ExportTreeActivity.computeUniqueMarriageUnionsCount(
            listOf(remarriedHusband, firstWife, secondWife)
        )
        assertEquals(2, marriageUnions)
    }

    @Test
    fun `test FamilyRecordsActivity filterMembers accurately filters Living Deceased and Missing Parents`() {
        val rootFounder = Person(id = "p1", firstName = "Arturo", lastName = "Bugarin", isLiving = false, fatherId = null, motherId = null)
        val spouseNoParents = Person(id = "p2", firstName = "Maria", lastName = "Santos", isLiving = true, fatherId = "", motherId = "")
        val childWithFather = Person(id = "p3", firstName = "Carlos", lastName = "Bugarin", isLiving = true, fatherId = "p1", motherId = null)
        val childWithBoth = Person(id = "p4", firstName = "Elena", lastName = "Bugarin", isLiving = false, fatherId = "p1", motherId = "p2")

        val all = listOf(rootFounder, spouseNoParents, childWithFather, childWithBoth)

        val missingParents = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterMembers(all, "MISSING_PARENTS", "")
        assertEquals(listOf("p1", "p2"), missingParents.map { it.id })

        val livingOnly = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterMembers(all, "LIVING", "")
        assertEquals(listOf("p2", "p3"), livingOnly.map { it.id })

        val deceasedOnly = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterMembers(all, "DECEASED", "")
        assertEquals(listOf("p1", "p4"), deceasedOnly.map { it.id })

        val missingParentsSearch = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterMembers(all, "MISSING_PARENTS", "santos")
        assertEquals(listOf("p2"), missingParentsSearch.map { it.id })
    }

    @Test
    fun `test MarriageValidationEngine and FamilyLinkValidator block cross-century and non-overlapping spouse lifespans`() {
        val mve = com.example.btproject2.engine.MarriageValidationEngine()

        // Case 1: Deceased ancestor (born 1850, died 1880 at age 30) vs modern descendant (born 1995, age ~31)
        val ancestor1850 = Person(id = "anc1850", firstName = "Juan", lastName = "Bugarin", gender = "Male", birthDate = "1850-05-10", deathDate = "1880-08-12", isLiving = false)
        val modern1995 = Person(id = "mod1995", firstName = "Maria", lastName = "Santos", gender = "Female", birthDate = "1995-03-20", isLiving = true)
        val map1 = listOf(ancestor1850, modern1995).associateBy { it.id }

        val res1 = mve.validateSpouseConnection("anc1850", "mod1995", map1)
        assertFalse(res1.isAllowed)
        assertTrue(res1.failedValidations.contains(com.example.btproject2.engine.MarriageValidationEngine.SpouseValidationFailureReason.CHRONOLOGICAL_LIFESPAN_CONFLICT))

        val flvSpouse1 = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(ancestor1850, modern1995, listOf(ancestor1850, modern1995))
        assertFalse(flvSpouse1.isValid)

        val flvCoParents1 = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(ancestor1850, modern1995, listOf(ancestor1850, modern1995))
        assertFalse(flvCoParents1.isValid)

        // Case 2: Person A died when Person B was only 10 years old (born 1930, died 1980 vs born 1970)
        val elder1930 = Person(id = "eld1930", firstName = "Roberto", lastName = "Cruz", gender = "Male", birthDate = "1930-01-01", deathDate = "1980-06-01", isLiving = false)
        val childAtDeath1970 = Person(id = "ch1970", firstName = "Lorna", lastName = "Reyes", gender = "Female", birthDate = "1970-04-15", isLiving = true)
        val map2 = listOf(elder1930, childAtDeath1970).associateBy { it.id }

        val res2 = mve.validateSpouseConnection("eld1930", "ch1970", map2)
        assertFalse(res2.isAllowed)
        assertTrue(res2.reason.contains("only 10 year(s) old"))

        // Case 3: Blank death dates, but birth years > 80 years apart (1890 vs 1985 = 95 years apart)
        val born1890 = Person(id = "b1890", firstName = "Emilio", lastName = "Diaz", gender = "Male", birthDate = "1890-02-14", isLiving = false)
        val born1985 = Person(id = "b1985", firstName = "Carla", lastName = "Gomez", gender = "Female", birthDate = "1985-11-05", isLiving = true)
        val map3 = listOf(born1890, born1985).associateBy { it.id }

        val res3 = mve.validateSpouseConnection("b1890", "b1985", map3)
        assertFalse(res3.isAllowed)
        assertTrue(res3.reason.contains("95 years apart"))

        val flvCoParents3 = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(born1890, born1985, listOf(born1890, born1985))
        assertFalse(flvCoParents3.isValid)

        // Case 4: Valid 45-year birth gap (1950 vs 1995, both living adults) -> Allowed, but triggers soft warning in FamilyLinkValidator
        val born1950 = Person(id = "b1950", firstName = "Arturo", lastName = "Vega", gender = "Male", birthDate = "1950-01-10", isLiving = true)
        val flvSpouse4 = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(born1950, modern1995, listOf(born1950, modern1995))
        assertTrue(flvSpouse4.isValid)
        assertTrue(flvSpouse4.hasWarnings)
        assertTrue(flvSpouse4.warnings.any { it.message.contains("45 years") })
    }

    @Test
    fun `test FamilyRecordsActivity filterAndSortMembers handles AZ Oldest Youngest and blank birth dates`() {
        val m1 = Person(id = "m1", firstName = "Carlos", lastName = "Bugarin", birthDate = "1985-04-12", birthPlace = "Manila", isLiving = true, fatherId = "f1")
        val m2 = Person(id = "m2", firstName = "Ana", lastName = "Bugarin", birthDate = "1950-01-01", birthPlace = "Cebu", isLiving = false)
        val m3 = Person(id = "m3", firstName = "Bea", lastName = "Santos", birthDate = "2005-09-20", birthPlace = "Davao", isLiving = true)
        val m4 = Person(id = "m4", firstName = "Dante", lastName = "Reyes", birthDate = "", birthPlace = "Iloilo", isLiving = true)
        val all = listOf(m1, m2, m3, m4)

        // 1. Sort A-Z
        val az = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterAndSortMembers(all, "ALL", "", "AZ")
        assertEquals(listOf("m2", "m3", "m1", "m4"), az.map { it.id }) // Ana, Bea, Carlos, Dante

        // 2. Sort Oldest First (1950 -> 1985 -> 2005 -> blank date at end)
        val oldest = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterAndSortMembers(all, "ALL", "", "OLDEST")
        assertEquals(listOf("m2", "m1", "m3", "m4"), oldest.map { it.id })

        // 3. Sort Youngest First (2005 -> 1985 -> 1950 -> blank date at end)
        val youngest = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterAndSortMembers(all, "ALL", "", "YOUNGEST")
        assertEquals(listOf("m3", "m1", "m2", "m4"), youngest.map { it.id })

        // 4. Filter Living + Sort Oldest
        val livingOldest = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterAndSortMembers(all, "LIVING", "", "OLDEST")
        assertEquals(listOf("m1", "m3", "m4"), livingOldest.map { it.id })

        // 5. Search by birth year or birth place
        val byYear = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterAndSortMembers(all, "ALL", "2005", "AZ")
        assertEquals(listOf("m3"), byYear.map { it.id })

        val byPlace = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterAndSortMembers(all, "ALL", "cebu", "AZ")
        assertEquals(listOf("m2"), byPlace.map { it.id })
    }

    @Test
    fun `test TraceActivity resolveInitialSpinnerIndices guarantees distinct Person A and Person B when launched from Member Profile`() {
        val p0 = Person(id = "p0", firstName = "Juan", lastName = "Bugarin")
        val p1 = Person(id = "p1", firstName = "Maria", lastName = "Bugarin")
        val p2 = Person(id = "p2", firstName = "Pedro", lastName = "Bugarin")
        val list = listOf(p0, p1, p2)

        // Case 1: Preselected Person A is the very first item (index 0) -> Person B must NOT be index 0!
        val (idxA1, idxB1) = com.example.btproject2.ui.activities.TraceActivity.resolveInitialSpinnerIndices(list, "p0", null)
        assertEquals(0, idxA1)
        assertEquals(1, idxB1)

        // Case 2: Preselected Person A is index 1 -> Person B defaults to index 0
        val (idxA2, idxB2) = com.example.btproject2.ui.activities.TraceActivity.resolveInitialSpinnerIndices(list, "p1", null)
        assertEquals(1, idxA2)
        assertEquals(0, idxB2)

        // Case 3: Both Person A and Person B explicitly provided
        val (idxA3, idxB3) = com.example.btproject2.ui.activities.TraceActivity.resolveInitialSpinnerIndices(list, "p2", "p1")
        assertEquals(2, idxA3)
        assertEquals(1, idxB3)

        // Case 4: No preselection provided -> defaults to (0, 1), never (0, 0)
        val (idxA4, idxB4) = com.example.btproject2.ui.activities.TraceActivity.resolveInitialSpinnerIndices(list, null, null)
        assertEquals(0, idxA4)
        assertEquals(1, idxB4)
    }

    @Test
    fun `test InsightsActivity and FamilyRecordsActivity missing parents count and filter match 100 percent including whitespace and deleted parent IDs`() {
        val father = Person(id = "f1", firstName = "Roberto", lastName = "Bugarin", gender = "Male")
        val childWithValidFather = Person(id = "c1", firstName = "Marco", lastName = "Bugarin", fatherId = "f1")
        val memberWithBlankParents = Person(id = "m1", firstName = "Ana", lastName = "Santos", fatherId = "", motherId = "")
        val memberWithWhitespaceParents = Person(id = "m2", firstName = "Bea", lastName = "Santos", fatherId = "   ", motherId = "  ")
        val memberWithDeletedGhostParent = Person(id = "m3", firstName = "Carlos", lastName = "Reyes", fatherId = "deleted_ghost_id_999")

        val all = listOf(father, childWithValidFather, memberWithBlankParents, memberWithWhitespaceParents, memberWithDeletedGhostParent)

        // 1. InsightsActivity countMissingParents must count: father, m1, m2, m3 (4 members)
        val missingCount = com.example.btproject2.ui.activities.FamilyRecordsActivity.countMissingParents(all)
        assertEquals(4, missingCount)

        // 2. FamilyRecordsActivity filterMembers(..., "MISSING_PARENTS", "") must return the exact same 4 members!
        val filteredMissing = com.example.btproject2.ui.activities.FamilyRecordsActivity.filterMembers(all, "MISSING_PARENTS", "")
        assertEquals(4, filteredMissing.size)
        assertEquals(listOf("f1", "m1", "m2", "m3"), filteredMissing.map { it.id })
    }
}
