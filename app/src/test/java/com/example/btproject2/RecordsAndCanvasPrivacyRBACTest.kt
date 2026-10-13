package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.ui.adapters.MemberAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Capstone Scope 1 Option 1:
 * Records & Canvas RBAC Completion (Direction 1 Subtle Shield privacy masking and viewer lockouts).
 */
class RecordsAndCanvasPrivacyRBACTest {

    @Test
    fun testPrivacySettingsDefaults() {
        val settings = PrivacySettings(treeId = "tree_123")
        assertEquals("tree_123", settings.treeId)
        assertTrue(settings.isPrivateTree)
        assertFalse(settings.hideBirthDates)
        assertFalse(settings.hideNotes)
        assertFalse(settings.hideDeceasedStatus)
    }

    @Test
    fun testMemberAdapterViewerDefaultsAndState() {
        val adapter = MemberAdapter(
            members = emptyList(),
            onItemClick = {},
            onEditClick = {}
        )
        assertFalse("Default adapter isViewer must be false (Owner/Editor mode)", adapter.isViewer)
        assertFalse("Default hideBirthDates must be false", adapter.treePrivacy.hideBirthDates)
        assertFalse("Default hideDeceasedStatus must be false", adapter.treePrivacy.hideDeceasedStatus)

        // Mutate viewer state and privacy settings
        adapter.isViewer = true
        adapter.treePrivacy = PrivacySettings(
            treeId = "tree_123",
            hideBirthDates = true,
            hideDeceasedStatus = true
        )

        assertTrue(adapter.isViewer)
        assertTrue(adapter.treePrivacy.hideBirthDates)
        assertTrue(adapter.treePrivacy.hideDeceasedStatus)
    }

    @Test
    fun testCanvasNodeYearPrivacyMaskingComputation() {
        val livingPerson = Person(
            id = "p1",
            firstName = "Augustine",
            lastName = "Russell",
            birthDate = "1980-05-15",
            isLiving = true
        )
        val deceasedPerson = Person(
            id = "p2",
            firstName = "Arthur",
            lastName = "Russell",
            birthDate = "1920-03-10",
            deathDate = "1995-11-20",
            isLiving = false
        )

        // 1. Owner view (no masking)
        val ownerLivingYear = computeDisplayYear(livingPerson, isViewerMode = false, treePrivacy = PrivacySettings())
        assertEquals("b. 1980", ownerLivingYear)

        val ownerDeceasedYear = computeDisplayYear(deceasedPerson, isViewerMode = false, treePrivacy = PrivacySettings())
        assertEquals("1920 -1995", ownerDeceasedYear)

        // 2. Viewer view with hideBirthDates = true
        val privacyHideBirth = PrivacySettings(hideBirthDates = true)
        val viewerLivingMasked = computeDisplayYear(livingPerson, isViewerMode = true, treePrivacy = privacyHideBirth)
        assertEquals("🔒", viewerLivingMasked)

        val viewerDeceasedMasked = computeDisplayYear(deceasedPerson, isViewerMode = true, treePrivacy = privacyHideBirth)
        assertEquals("-1995", viewerDeceasedMasked)

        // 3. Viewer view with hideDeceasedStatus = true
        val privacyHideDeceased = PrivacySettings(hideDeceasedStatus = true)
        val viewerDeceasedStatusMasked = computeDisplayYear(deceasedPerson, isViewerMode = true, treePrivacy = privacyHideDeceased)
        assertEquals("b. 1920", viewerDeceasedStatusMasked)

        // 4. Viewer view with BOTH hideBirthDates and hideDeceasedStatus = true
        val privacyHideBoth = PrivacySettings(hideBirthDates = true, hideDeceasedStatus = true)
        val viewerBothMasked = computeDisplayYear(deceasedPerson, isViewerMode = true, treePrivacy = privacyHideBoth)
        assertEquals("🔒", viewerBothMasked)
    }

    @Test
    fun testDirection1SubtleShieldInfoTextFormatting() {
        val person = Person(
            id = "p10",
            firstName = "Clara",
            lastName = "Russell",
            gender = "Female",
            birthDate = "1955-08-20",
            deathDate = "2010-04-12",
            isLiving = false
        )

        // Owner format
        val ownerInfo = formatMemberInfoText(person, isViewer = false, treePrivacy = PrivacySettings())
        assertEquals("Female · 1955-08-20 · 🕊️ Deceased (d. 2010-04-12)", ownerInfo)

        // Viewer with hideBirthDates
        val viewerBirthMasked = formatMemberInfoText(person, isViewer = true, treePrivacy = PrivacySettings(hideBirthDates = true))
        assertEquals("Female · 🔒 Hidden by Owner · 🕊️ Deceased", viewerBirthMasked)

        // Viewer with hideDeceasedStatus
        val viewerDeceasedMasked = formatMemberInfoText(person, isViewer = true, treePrivacy = PrivacySettings(hideDeceasedStatus = true))
        assertEquals("Female · 1955-08-20", viewerDeceasedMasked)

        // Viewer with both
        val viewerBothMasked = formatMemberInfoText(person, isViewer = true, treePrivacy = PrivacySettings(hideBirthDates = true, hideDeceasedStatus = true))
        assertEquals("Female · 🔒 Hidden by Owner", viewerBothMasked)
    }

    // Helper mirroring FamilyTreeView.precomputeCardDisplayData logic
    private fun computeDisplayYear(p: Person, isViewerMode: Boolean, treePrivacy: PrivacySettings): String {
        val shouldHideBirth = isViewerMode && treePrivacy.hideBirthDates
        val shouldHideDeceased = isViewerMode && treePrivacy.hideDeceasedStatus

        val yr = if (shouldHideBirth) "" else p.birthDate.take(4)
        val dYr = if (!p.isLiving && !shouldHideDeceased) {
            val d = p.deathDate.take(4)
            if (d.isNotEmpty()) "-$d" else "🕊️"
        } else ""
        return if (shouldHideBirth) {
            if (dYr.isNotEmpty()) dYr else "🔒"
        } else if (yr.isNotEmpty()) {
            if (dYr.isNotEmpty()) "$yr $dYr" else "b. $yr"
        } else dYr
    }

    // Helper mirroring MemberAdapter info string formatting
    private fun formatMemberInfoText(person: Person, isViewer: Boolean, treePrivacy: PrivacySettings): String {
        val shouldHideDeceased = isViewer && treePrivacy.hideDeceasedStatus
        val shouldHideBirth = isViewer && treePrivacy.hideBirthDates

        return buildString {
            append(person.gender)
            if (shouldHideBirth) {
                append(" · 🔒 Hidden by Owner")
            } else if (person.birthDate.isNotEmpty()) {
                append(" · ")
                append(person.birthDate)
            }
            if (!person.isLiving && !shouldHideDeceased) {
                append(" · 🕊️ Deceased")
                if (person.deathDate.isNotEmpty() && !shouldHideBirth) {
                    append(" (d. ${person.deathDate})")
                }
            }
        }
    }
}
