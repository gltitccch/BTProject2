package com.example.btproject2

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PHASE 0 — CHARACTERIZATION (snapshot) TESTS.
 *
 * These tests lock in what the app does TODAY (pre-refactor tag), including quirks.
 * They are NOT a statement of ideal behaviour. If a refactor makes one fail, stop and
 * get explicit user approval before changing the expectation.
 */
class CharacterizationCacheAndSanitizeTest {

    private fun p(
        id: String, first: String = id, last: String = "Test", tree: String = "T1",
        father: String? = null, mother: String? = null, spouse: String? = null,
        marital: String = "Single", living: Boolean = true, updatedAt: Long = 0L,
        deathDate: String = "", marriageDate: String = ""
    ) = Person(
        id = id, firstName = first, lastName = last, treeId = tree,
        fatherId = father, motherId = mother, spouseId = spouse,
        maritalStatus = marital, isLiving = living, updatedAt = updatedAt,
        deathDate = deathDate, marriageDate = marriageDate
    )

    private fun cached(id: String) = FirestoreHelper.getCachedPersons()!!.first { it.id == id }

    @Before fun setUp() = FirestoreHelper.clearCache()
    @After fun tearDown() = FirestoreHelper.clearCache()

    // ---------- getCachedPersons scoping ----------

    @Test fun getCached_nullWhenEmpty() = assertNull(FirestoreHelper.getCachedPersons("T1"))

    @Test fun getCached_scopesByTree_andDefaultTreeIncludesBlankTreeIds() {
        FirestoreHelper.setCachedPersons(listOf(p("a", tree = "T1"), p("b", tree = "T2"), p("c", tree = ""), p("d", tree = "default_tree")))
        assertEquals(setOf("a"), FirestoreHelper.getCachedPersons("T1")!!.map { it.id }.toSet())
        assertEquals(setOf("c", "d"), FirestoreHelper.getCachedPersons("default_tree")!!.map { it.id }.toSet())
        assertTrue(FirestoreHelper.getCachedPersons("")!!.isEmpty())
        // QUIRK: null treeId returns ALL trees (single global cache)
        assertEquals(4, FirestoreHelper.getCachedPersons(null)!!.size)
    }

    // ---------- setCachedPersons merge precedence ----------

    @Test fun merge_newerCachedVitalStatusWinsOverOlderServer() {
        FirestoreHelper.setCachedPersons(listOf(p("a", living = false, updatedAt = 200, deathDate = "2000-01-01")))
        FirestoreHelper.setCachedPersons(listOf(p("a", living = true, updatedAt = 100)))
        assertEquals(false, cached("a").isLiving)
        assertEquals("2000-01-01", cached("a").deathDate)
    }

    @Test fun merge_newerServerVitalStatusWins_andClearsDeathDate() {
        FirestoreHelper.setCachedPersons(listOf(p("a", living = false, updatedAt = 100, deathDate = "2000-01-01")))
        FirestoreHelper.setCachedPersons(listOf(p("a", living = true, updatedAt = 200)))
        assertEquals(true, cached("a").isLiving)
        assertEquals("", cached("a").deathDate)
    }

    @Test fun merge_QUIRK_serverCannotClearParentOrSpouseThatCacheStillHas() {
        FirestoreHelper.setCachedPersons(listOf(p("f"), p("c", father = "f")))
        FirestoreHelper.setCachedPersons(listOf(p("f"), p("c", father = null)))
        assertEquals("f", cached("c").fatherId)
    }

    @Test fun merge_QUIRK_localOnlyRecordsSurviveServerSnapshot() {
        FirestoreHelper.setCachedPersons(listOf(p("a"), p("local")))
        FirestoreHelper.setCachedPersons(listOf(p("a")))
        assertTrue(FirestoreHelper.getCachedPersons()!!.any { it.id == "local" })
    }

    @Test fun merge_idsMatchCaseInsensitively() {
        FirestoreHelper.setCachedPersons(listOf(p("ABC", first = "Old")))
        FirestoreHelper.setCachedPersons(listOf(p("abc", first = "New")))
        assertEquals(1, FirestoreHelper.getCachedPersons()!!.size)
    }

    // ---------- reciprocal spouse normalization ----------

    @Test fun spouse_oneSidedLinkBecomesMutualMarried_withMarriageDateCopied() {
        FirestoreHelper.setCachedPersons(listOf(p("h", spouse = "w", marital = "Married", marriageDate = "1990"), p("w")))
        val w = cached("w")
        assertEquals("h", w.spouseId); assertEquals("Married", w.maritalStatus); assertEquals("1990", w.marriageDate)
    }

    @Test fun spouse_notMirroredAcrossDifferentTrees() {
        FirestoreHelper.setCachedPersons(listOf(p("h", tree = "T1", spouse = "w"), p("w", tree = "T2")))
        assertNull(cached("w").spouseId)
    }

    // ---------- sanitizeTreeRecords (read-path repairs) ----------

    @Test fun sanitize_emptyListUnchanged() = assertTrue(FirestoreHelper.sanitizeTreeRecords(emptyList()).isEmpty())

    @Test fun sanitize_validTreeUnchanged() {
        val input = listOf(p("gf"), p("gm"), p("dad", father = "gf", mother = "gm"), p("kid", father = "dad"))
        assertEquals(input.toSet(), FirestoreHelper.sanitizeTreeRecords(input).toSet())
    }

    @Test fun sanitize_selfParentRemoved() {
        val out = FirestoreHelper.sanitizeTreeRecords(listOf(p("a", father = "a")))
        assertNull(out.first().fatherId)
    }

    @Test fun sanitize_twoNodeCycleBroken() {
        val out = FirestoreHelper.sanitizeTreeRecords(listOf(p("a", father = "b"), p("b", father = "a"))).associateBy { it.id }
        assertTrue(out["a"]!!.fatherId == null || out["b"]!!.fatherId == null)
    }

    @Test fun sanitize_sameFatherAndMotherId_dropsFather() {
        val out = FirestoreHelper.sanitizeTreeRecords(listOf(p("x"), p("k", father = "x", mother = "x"))).first { it.id == "k" }
        assertNull(out.fatherId); assertEquals("x", out.motherId)
    }

    @Test fun sanitize_motherIsAncestorOfFather_dropsMother() {
        val out = FirestoreHelper.sanitizeTreeRecords(
            listOf(p("gm"), p("dad", mother = "gm"), p("k", father = "dad", mother = "gm"))
        ).first { it.id == "k" }
        assertEquals("dad", out.fatherId); assertNull(out.motherId)
    }

    @Test fun sanitize_parentInOtherTreeIgnoredByRules() {
        val input = listOf(p("a", tree = "T1", father = "b"), p("b", tree = "T2", father = "a"))
        val out = FirestoreHelper.sanitizeTreeRecords(input).associateBy { it.id }
        assertEquals("b", out["a"]!!.fatherId); assertEquals("a", out["b"]!!.fatherId)
    }

    @Test fun sanitize_doesNotForceMarryUnlinkedBugarinMembersByHardcodedName() {
        val out = FirestoreHelper.sanitizeTreeRecords(
            listOf(p("c", first = "Clara", last = "Bugarin"), p("pd", first = "Pedro", last = "Bugarin"))
        ).associateBy { it.id }
        assertNull(out["c"]!!.spouseId); assertNull(out["pd"]!!.spouseId)
    }

    @Test fun sanitize_bugarinRulesDoNotTouchOtherSurnames() {
        val input = listOf(p("c", first = "Clara", last = "Malone"), p("pd", first = "Pedro", last = "Malone"))
        assertEquals(input.toSet(), FirestoreHelper.sanitizeTreeRecords(input).toSet())
    }

    // ---------- updatePersonInCache ----------

    @Test fun updateInCache_QUIRK_blankFieldsKeepOldValues() {
        FirestoreHelper.setCachedPersons(listOf(p("a", first = "Ann").copy(birthPlace = "Manila")))
        FirestoreHelper.updatePersonInCache(Person(id = "a", firstName = "Anna"))
        assertEquals("Anna", cached("a").firstName); assertEquals("Manila", cached("a").birthPlace)
    }

    @Test fun updateInCache_unknownIdIsAppended() {
        FirestoreHelper.updatePersonInCache(p("new"))
        assertEquals(1, FirestoreHelper.getCachedPersons()!!.size)
    }
}
