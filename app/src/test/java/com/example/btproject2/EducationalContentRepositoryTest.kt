package com.example.btproject2

import com.example.btproject2.education.EducationalContentRepository
import com.example.btproject2.education.EducationalContentRepository.QuickAssistContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EducationalContentRepositoryTest: Verifies that the centralized educational repository
 * reliably provides complete, non-null, structured payloads for every screen context
 * and curriculum module under Proposal 25.
 */
class EducationalContentRepositoryTest {

    @Test
    fun testAllQuickAssistContextsAreHandled() {
        for (context in QuickAssistContext.values()) {
            val payload = EducationalContentRepository.getQuickAssistPayload(context)
            assertNotNull("Payload for $context must not be null", payload)
            assertEquals("Payload context must match requested context", context, payload.context)
            assertTrue("Payload title for $context must not be blank", payload.title.isNotBlank())
            assertTrue("Payload subtitle for $context must not be blank", payload.subtitle.isNotBlank())
            assertTrue("Pro tips for $context must not be empty", payload.proTips.isNotEmpty())
            assertTrue("Action CTA text for $context must not be blank", payload.actionCtaText.isNotBlank())
            assertTrue("Target academy module ID must be between 1 and 10", payload.targetAcademyModuleId in 1..10)
        }
    }

    @Test
    fun testAllTenModulesExistAndArePopulated() {
        val modules = EducationalContentRepository.getAllModules()
        assertEquals("There must be exactly 10 comprehensive educational modules", 10, modules.size)

        for (module in modules) {
            assertTrue("Module title must not be blank", module.title.isNotBlank())
            assertTrue("Module category must not be blank", module.category.isNotBlank())
            assertTrue("Module tagline must not be blank", module.tagline.isNotBlank())
            assertTrue("Module icon emoji must not be blank", module.iconEmoji.isNotBlank())
            assertTrue("Module summary must not be blank", module.summary.isNotBlank())
            assertTrue("Detailed sections must not be empty for module: ${module.title}", module.detailedSections.isNotEmpty())

            for (section in module.detailedSections) {
                assertTrue("Section heading must not be blank", section.heading.isNotBlank())
                assertTrue("Section paragraphs must not be empty", section.paragraphs.isNotEmpty())
            }
        }
    }

    @Test
    fun testKamagAnakDictionaryContainsCorePhilippineTerms() {
        val glossary = EducationalContentRepository.getKamagAnakDictionary()
        assertTrue("Glossary must contain terms", glossary.size >= 10)

        val terms = glossary.map { it.tagalogTerm }
        assertTrue("Glossary must include Pinsan Buo", terms.contains("Pinsan Buo"))
        assertTrue("Glossary must include Pinsan Makalawa", terms.contains("Pinsan Makalawa"))
        assertTrue("Glossary must include Bilas", terms.contains("Bilas"))
        assertTrue("Glossary must include Balae", terms.contains("Balae"))
        assertTrue("Glossary must include Hipag", terms.contains("Hipag"))
        assertTrue("Glossary must include Bayaw", terms.contains("Bayaw"))
        assertTrue("Glossary must include Amain", terms.contains("Amain"))
        assertTrue("Glossary must include Ale", terms.contains("Ale"))
        assertTrue("Glossary must include Ninong / Ninang", terms.contains("Ninong / Ninang"))
    }

    @Test
    fun testSearchModulesFindsRelevantContent() {
        val bloodlineResults = EducationalContentRepository.searchModules("bloodline")
        assertFalse("Search for 'bloodline' must return results", bloodlineResults.isEmpty())
        assertTrue("Search results must contain Module 3", bloodlineResults.any { it.id == 3 })

        val exportResults = EducationalContentRepository.searchModules("export")
        assertFalse("Search for 'export' must return results", exportResults.isEmpty())
        assertTrue("Search results must contain Module 8", exportResults.any { it.id == 8 })

        val nonExistentResults = EducationalContentRepository.searchModules("xyzrandomterm12345")
        assertTrue("Search for non-existent keyword must return empty list", nonExistentResults.isEmpty())
    }

    @Test
    fun testSearchGlossaryFindsTraditionalTerms() {
        val pinsanResults = EducationalContentRepository.searchGlossary("pinsan")
        assertTrue("Search for 'pinsan' must return at least 2 cousin terms", pinsanResults.size >= 2)

        val bilasResults = EducationalContentRepository.searchGlossary("bilas")
        assertEquals(1, bilasResults.size)
        assertEquals("Bilas", bilasResults[0].tagalogTerm)
        assertEquals("Co-Sibling-in-Law", bilasResults[0].englishTranslation)
    }

    @Test
    fun testSpecificScreenPayloadAccuracy() {
        // Trace Screen
        val tracePayload = EducationalContentRepository.getQuickAssistPayload(QuickAssistContext.TRACE)
        assertEquals("Bloodline Tracing Engine", tracePayload.title)
        assertEquals(3, tracePayload.targetAcademyModuleId)
        assertTrue(tracePayload.proTips.any { it.contains("MRCA") })

        // Export Screen
        val exportPayload = EducationalContentRepository.getQuickAssistPayload(QuickAssistContext.EXPORT_TREE)
        assertEquals("Tree Export & Archival Heritage", exportPayload.title)
        assertEquals(8, exportPayload.targetAcademyModuleId)
        assertTrue(exportPayload.proTips.any { it.contains("PDF") })

        // Privacy Screen
        val privacyPayload = EducationalContentRepository.getQuickAssistPayload(QuickAssistContext.PRIVACY_CONTROLS)
        assertEquals("Privacy & RA 10173 Compliance", privacyPayload.title)
        assertEquals(6, privacyPayload.targetAcademyModuleId)
        assertTrue(privacyPayload.proTips.any { it.contains("RA 10173") })

        // Audit Screen
        val auditPayload = EducationalContentRepository.getQuickAssistPayload(QuickAssistContext.TREE_AUDIT)
        assertEquals("Tree Legal & Health Audit", auditPayload.title)
        assertEquals(7, auditPayload.targetAcademyModuleId)
        assertNotNull(auditPayload.legalGuardrails)
        assertTrue(auditPayload.legalGuardrails!!.any { it.contains("Article 37") })
    }
}
