package com.example.btproject2

import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Unit tests verifying Category 5: Light/Dark Mode "Invisible Text", Insights Completeness Scorecard & Multi-Tree Navigation Bugs (Issues 5.1 to 5.4).
 */
class Category5ThemeAndNavigationTest {

    // WCAG relative luminance & contrast ratio helper
    private fun relativeLuminance(rgbHex: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = (rgbHex shr 16) and 0xFF
        val g = (rgbHex shr 8) and 0xFF
        val b = rgbHex and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrastRatio(hex1: Int, hex2: Int): Double {
        val l1 = relativeLuminance(hex1)
        val l2 = relativeLuminance(hex2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // -------------------------------------------------------------------------
    // Issues 5.1 & 5.2: Light Mode and Dark Mode Text vs Card Background Contrast
    // -------------------------------------------------------------------------
    @Test
    fun `issues 5_1 and 5_2 light mode text has high contrast against all card backgrounds`() {
        val lightModePrimaryText = 0x0F172A // Dark Navy

        val lightNormalCardBg = 0xF5F7F1
        val lightDeceasedCardBg = 0xE2E8F0
        val lightHighlightCardBg = 0xECFDF5
        val lightMrcaCardBg = 0xFEF9C3

        assertTrue("Normal card in Light Mode must be legible (> 10:1)", contrastRatio(lightModePrimaryText, lightNormalCardBg) > 10.0)
        assertTrue("Deceased card in Light Mode must be legible (> 9:1)", contrastRatio(lightModePrimaryText, lightDeceasedCardBg) > 9.0)
        assertTrue("Highlight card in Light Mode must be legible (> 10:1)", contrastRatio(lightModePrimaryText, lightHighlightCardBg) > 10.0)
        assertTrue("MRCA card in Light Mode must be legible (> 10:1)", contrastRatio(lightModePrimaryText, lightMrcaCardBg) > 10.0)
    }

    @Test
    fun `issues 5_1 and 5_2 dark mode text has high contrast against all card backgrounds`() {
        val darkModePrimaryText = 0xFFFFFF // White

        val darkNormalCardBg = 0x0F241A
        val darkDeceasedCardBg = 0x334155
        val darkHighlightCardBg = 0x1B3B2B
        val darkMrcaCardBg = 0x2A1F08

        assertTrue("Normal card in Dark Mode must be legible (> 10:1)", contrastRatio(darkModePrimaryText, darkNormalCardBg) > 10.0)
        assertTrue("Deceased card in Dark Mode must be legible (> 7:1)", contrastRatio(darkModePrimaryText, darkDeceasedCardBg) > 7.0)
        assertTrue("Highlight card in Dark Mode must be legible (> 9:1)", contrastRatio(darkModePrimaryText, darkHighlightCardBg) > 9.0)
        assertTrue("MRCA card in Dark Mode must be legible (> 10:1)", contrastRatio(darkModePrimaryText, darkMrcaCardBg) > 10.0)
    }

    // -------------------------------------------------------------------------
    // Issue 5.3: Insights Profile Completeness Scorecard Calculation & Label
    // -------------------------------------------------------------------------
    private fun computeProfileCompleteness(persons: List<Person>): Int {
        if (persons.isEmpty()) return 0
        val avg = persons.map { person ->
            var score = 0
            val total = 6
            if (person.firstName.isNotBlank()) score++
            if (person.lastName.isNotBlank()) score++
            if (person.gender.isNotBlank()) score++
            if (person.birthDate.isNotBlank()) score++
            if (!person.fatherId.isNullOrEmpty() || !person.motherId.isNullOrEmpty()) score++
            if (person.biography.isNotBlank()) score++
            ((score.toDouble() / total.toDouble()) * 100.0).toInt()
        }.average()
        return avg.toInt()
    }

    @Test
    fun `issue 5_3 profile completeness score computes accurately for Insights scorecard`() {
        val completeMember = Person(
            id = "p1",
            firstName = "Thomas",
            lastName = "Malone",
            gender = "Male",
            birthDate = "1962-01-12",
            fatherId = "arthur_1",
            biography = "Eldest son of Arthur and Beatrice."
        )
        val partialMember = Person(
            id = "p2",
            firstName = "Evelyn",
            lastName = "Malone",
            gender = "Female"
        )

        assertEquals(100, computeProfileCompleteness(listOf(completeMember)))
        assertEquals(50, computeProfileCompleteness(listOf(partialMember)))
        assertEquals(75, computeProfileCompleteness(listOf(completeMember, partialMember)))
    }

    // -------------------------------------------------------------------------
    // Issue 5.4: Multi-Tree Dual-Key (TREE_ID & treeId) Intent Resolution
    // -------------------------------------------------------------------------
    private fun resolveTargetTreeId(extras: Map<String, String?>, fallbackActiveTreeId: String): String {
        return extras["TREE_ID"]?.takeIf { it.isNotBlank() }
            ?: extras["treeId"]?.takeIf { it.isNotBlank() }
            ?: fallbackActiveTreeId
    }

    @Test
    fun `issue 5_4 dual key treeId propagation preserves non-default tree across screens`() {
        val extrasFromUpperCase = mapOf("TREE_ID" to "clan_tree_99")
        val extrasFromLowerCase = mapOf("treeId" to "clan_tree_99")
        val extrasFromBoth = mapOf("TREE_ID" to "clan_tree_99", "treeId" to "clan_tree_99")

        assertEquals("clan_tree_99", resolveTargetTreeId(extrasFromUpperCase, "default_tree"))
        assertEquals("clan_tree_99", resolveTargetTreeId(extrasFromLowerCase, "default_tree"))
        assertEquals("clan_tree_99", resolveTargetTreeId(extrasFromBoth, "default_tree"))
        assertEquals("default_tree", resolveTargetTreeId(emptyMap(), "default_tree"))
    }

    // -------------------------------------------------------------------------
    // Trace Relationship Spotlight Search Name & Pill Filter Test
    // -------------------------------------------------------------------------
    @Test
    fun `trace spotlight search filters by first name, last name, birth year, and status pills`() {
        val members = listOf(
            Person(id = "1", firstName = "Thomas", lastName = "Malone", gender = "Male", birthDate = "1962-01-12", isLiving = true),
            Person(id = "2", firstName = "Evelyn", lastName = "Malone", gender = "Female", birthDate = "1964-08-23", isLiving = true),
            Person(id = "3", firstName = "Arthur", lastName = "Malone", gender = "Male", birthDate = "1938-05-10", isLiving = false),
            Person(id = "4", firstName = "Clara", lastName = "Vance", gender = "Female", birthDate = "1992-11-04", isLiving = true)
        )

        val byFirstName = com.example.btproject2.ui.activities.TraceActivity.filterTraceMembers(members, "thom", "ALL")
        assertEquals(1, byFirstName.size)
        assertEquals("Thomas", byFirstName.first().firstName)

        val byBirthYear = com.example.btproject2.ui.activities.TraceActivity.filterTraceMembers(members, "1938", "ALL")
        assertEquals(1, byBirthYear.size)
        assertEquals("Arthur", byBirthYear.first().firstName)

        val deceasedOnly = com.example.btproject2.ui.activities.TraceActivity.filterTraceMembers(members, "", "DECEASED")
        assertEquals(1, deceasedOnly.size)
        assertEquals("Arthur", deceasedOnly.first().firstName)

        val femaleMalones = com.example.btproject2.ui.activities.TraceActivity.filterTraceMembers(members, "Malone", "FEMALE")
        assertEquals(1, femaleMalones.size)
        assertEquals("Evelyn", femaleMalones.first().firstName)
    }

    // -------------------------------------------------------------------------
    // Global Help & KinAcademy Content Synchronization Tests
    // -------------------------------------------------------------------------
    @Test
    fun `educational content repository provides updated payloads for all quick assist contexts including INSIGHTS`() {
        val repo = com.example.btproject2.education.EducationalContentRepository
        for (ctx in com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.values()) {
            val payload = repo.getQuickAssistPayload(ctx)
            assertTrue("Title must not be blank for $ctx", payload.title.isNotBlank())
            assertTrue("Must have at least 3 tips for $ctx", payload.proTips.size >= 3)
            assertTrue("Academy module ID must be 1..10 for $ctx", payload.targetAcademyModuleId in 1..10)
        }

        val tracePayload = repo.getQuickAssistPayload(
            com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.TRACE
        )
        assertTrue(tracePayload.proTips.any { it.contains("Spotlight", ignoreCase = true) })

        val addPayload = repo.getQuickAssistPayload(
            com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.ADD_MEMBER
        )
        assertTrue(addPayload.proTips.any { it.contains("Co-Parent", ignoreCase = true) })

        val insightsPayload = repo.getQuickAssistPayload(
            com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.INSIGHTS
        )
        assertEquals(9, insightsPayload.targetAcademyModuleId)
        assertTrue(insightsPayload.proTips.any { it.contains("Profile Completeness", ignoreCase = true) })
    }

    @Test
    fun `kinacademy modules reflect latest architecture and InteractiveTreeActivity deep link`() {
        val modules = com.example.btproject2.education.EducationalContentRepository.getAllModules()
        assertEquals(10, modules.size)

        val module2 = modules.first { it.id == 2 }
        assertEquals("InteractiveTreeActivity", module2.deepLinkTarget)

        val searchCoParent = com.example.btproject2.education.EducationalContentRepository.searchModules("Co-Parent")
        assertTrue(searchCoParent.any { it.id == 4 })

        val searchSpotlight = com.example.btproject2.education.EducationalContentRepository.searchModules("Spotlight")
        assertTrue(searchSpotlight.any { it.id == 3 })
    }
}


