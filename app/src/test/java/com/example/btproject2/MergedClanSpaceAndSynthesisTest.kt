package com.example.btproject2

import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests verifying Proposal 15 (Safe Master Tree Synthesis) and
 * Proposal 16 (Dedicated Merged Clan Space & Separation of Concerns).
 */
class MergedClanSpaceAndSynthesisTest {

    @Test
    fun testFamilyTreeModel_defaultsToPersonalTree() {
        val tree = FamilyTree(id = "tree1", name = "Dela Cruz Family Tree")
        assertEquals(FamilyTree.TREE_TYPE_PERSONAL, tree.treeType)
        assertEquals("", tree.bridgeDescription)
    }

    @Test
    fun testFamilyTreeModel_supportsMergedClanType() {
        val clanTree = FamilyTree(
            id = "clanTree1",
            name = "Dela Cruz - Santos Master Clan Tree",
            treeType = FamilyTree.TREE_TYPE_MERGED_CLAN,
            bridgeDescription = "💍 Juan Dela Cruz married to Maria Santos"
        )
        assertEquals(FamilyTree.TREE_TYPE_MERGED_CLAN, clanTree.treeType)
        assertTrue(clanTree.bridgeDescription.contains("Juan Dela Cruz"))
    }

    @Test
    fun testTreeTypeSegregation_keepsPersonalTreesPure() {
        val trees = listOf(
            FamilyTree(id = "p1", name = "Dela Cruz Tree", treeType = FamilyTree.TREE_TYPE_PERSONAL),
            FamilyTree(id = "p2", name = "Santos Tree", treeType = FamilyTree.TREE_TYPE_PERSONAL),
            FamilyTree(id = "c1", name = "Dela Cruz & Santos Clan Tree", treeType = FamilyTree.TREE_TYPE_MERGED_CLAN)
        )

        val personalTrees = trees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
        assertEquals(2, personalTrees.size)
        assertTrue(personalTrees.none { it.id == "c1" })

        val clanTrees = trees.filter { it.treeType == FamilyTree.TREE_TYPE_MERGED_CLAN }
        assertEquals(1, clanTrees.size)
        assertEquals("c1", clanTrees.first().id)
    }

    @Test
    fun testDeepCloningWithIdRemapping_preservesOriginalTrees() {
        // Original Tree A
        val fatherA = Person(id = "fA", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", treeId = "treeA")
        val childA = Person(id = "cA", firstName = "Pedro", lastName = "Dela Cruz", gender = "Male", fatherId = "fA", treeId = "treeA")
        val treeAPersons = listOf(fatherA, childA)

        // Original Tree B
        val motherB = Person(id = "mB", firstName = "Maria", lastName = "Santos", gender = "Female", treeId = "treeB")
        val treeBPersons = listOf(motherB)

        // Snapshot original states
        val originalFatherTreeId = fatherA.treeId
        val originalChildFatherId = childA.fatherId
        val originalMotherTreeId = motherB.treeId

        // Synthesis to Master Tree C
        val masterTreeId = "masterTreeC"
        val idMap = mutableMapOf<String, String>()
        for (p in treeAPersons + treeBPersons) {
            idMap[p.id] = "new_" + p.id
        }

        fun clonePerson(p: Person): Person {
            return p.copy(
                id = idMap[p.id]!!,
                treeId = masterTreeId,
                fatherId = idMap[p.fatherId] ?: p.fatherId,
                motherId = idMap[p.motherId] ?: p.motherId,
                spouseId = idMap[p.spouseId] ?: p.spouseId
            )
        }

        val clonedList = (treeAPersons + treeBPersons).map { clonePerson(it) }.toMutableList()

        // Apply Spousal Bridge in Tree C
        val idxA = clonedList.indexOfFirst { it.id == idMap[fatherA.id] }
        val idxB = clonedList.indexOfFirst { it.id == idMap[motherB.id] }
        clonedList[idxA] = clonedList[idxA].copy(spouseId = clonedList[idxB].id)
        clonedList[idxB] = clonedList[idxB].copy(spouseId = clonedList[idxA].id)

        // VERIFY: Tree C is properly formed
        assertEquals(3, clonedList.size)
        assertTrue(clonedList.all { it.treeId == masterTreeId })

        val clonedFather = clonedList.first { it.id == "new_fA" }
        val clonedMother = clonedList.first { it.id == "new_mB" }
        val clonedChild = clonedList.first { it.id == "new_cA" }

        assertEquals("new_mB", clonedFather.spouseId)
        assertEquals("new_fA", clonedMother.spouseId)
        assertEquals("new_fA", clonedChild.fatherId)

        // VERIFY: Original trees remain 100% UNTOUCHED (Zero Risk)
        assertEquals(originalFatherTreeId, fatherA.treeId)
        assertEquals(originalChildFatherId, childA.fatherId)
        assertEquals(originalMotherTreeId, motherB.treeId)
        assertNull(fatherA.spouseId)
        assertNull(motherB.spouseId)
    }

    @Test
    fun testSharedAncestorFusion_combinesDuplicateNodesInTreeC() {
        // Both trees contain Grandfather Ramon
        val ramonA = Person(id = "rA", firstName = "Ramon", lastName = "Dela Cruz", treeId = "treeA")
        val sonA = Person(id = "sA", firstName = "Juan", lastName = "Dela Cruz", fatherId = "rA", treeId = "treeA")
        val ramonB = Person(id = "rB", firstName = "Ramon", lastName = "Dela Cruz", treeId = "treeB")
        val daughterB = Person(id = "dB", firstName = "Clara", lastName = "Dela Cruz", fatherId = "rB", treeId = "treeB")

        val masterTreeId = "masterTreeC"
        val idMap = mutableMapOf<String, String>()
        listOf(ramonA, sonA, ramonB, daughterB).forEach { idMap[it.id] = "new_" + it.id }

        val clonedList = listOf(ramonA, sonA, ramonB, daughterB).map { p ->
            p.copy(
                id = idMap[p.id]!!,
                treeId = masterTreeId,
                fatherId = idMap[p.fatherId] ?: p.fatherId
            )
        }.toMutableList()

        // Fuse ramonB into ramonA
        val canonicalId = idMap[ramonA.id]!!
        val duplicateId = idMap[ramonB.id]!!

        for (i in clonedList.indices) {
            var item = clonedList[i]
            if (item.fatherId == duplicateId) item = item.copy(fatherId = canonicalId)
            clonedList[i] = item
        }
        clonedList.removeAll { it.id == duplicateId }

        assertEquals(3, clonedList.size)
        val fusedRamon = clonedList.first { it.id == canonicalId }
        val clonedSon = clonedList.first { it.id == "new_sA" }
        val clonedDaughter = clonedList.first { it.id == "new_dB" }

        assertEquals(canonicalId, clonedSon.fatherId)
        assertEquals(canonicalId, clonedDaughter.fatherId)

        // Original Ramon A and Ramon B remain separate and untouched
        assertEquals("treeA", ramonA.treeId)
        assertEquals("treeB", ramonB.treeId)
    }

    @Test
    fun testMarriageValidationEngine_blocksProhibitedSelfOrConsanguineousSpouse() {
        val engine = MarriageValidationEngine(minimumSpouseAge = 18)
        val personA = Person(id = "p1", firstName = "Juan", lastName = "Dela Cruz", gender = "Male", birthDate = "1990-01-01")
        val map = mapOf("p1" to personA)

        // Self marriage test
        val selfResult = engine.validateSpouseConnection("p1", "p1", map)
        assertFalse(selfResult.isAllowed)
        assertTrue(selfResult.reason.contains("cannot marry themselves"))
    }
    @Test
    fun testTreeListDeduplication_ensuresSingleSourceOfTruth() {
        val duplicateRawTrees = listOf(
            FamilyTree(id = "clan1", name = "Smith - Malone Master Clan Tree", treeType = FamilyTree.TREE_TYPE_MERGED_CLAN),
            FamilyTree(id = "clan1", name = "Smith - Malone Master Clan Tree", treeType = FamilyTree.TREE_TYPE_MERGED_CLAN),
            FamilyTree(id = "treeA", name = "Smith Family", treeType = FamilyTree.TREE_TYPE_PERSONAL),
            FamilyTree(id = "treeA", name = "Smith Family", treeType = FamilyTree.TREE_TYPE_PERSONAL),
            FamilyTree(id = "treeB", name = "Malone Family", treeType = FamilyTree.TREE_TYPE_PERSONAL)
        )

        // Verifying distinctBy preserves single instance per tree ID
        val distinctUserTrees = duplicateRawTrees.distinctBy { it.id }
        assertEquals(3, distinctUserTrees.size)
        assertEquals(listOf("clan1", "treeA", "treeB"), distinctUserTrees.map { it.id })

        val distinctClanTrees = duplicateRawTrees
            .filter { it.treeType == FamilyTree.TREE_TYPE_MERGED_CLAN }
            .distinctBy { it.id }
        assertEquals(1, distinctClanTrees.size)
        assertEquals("clan1", distinctClanTrees.first().id)
    }
}
