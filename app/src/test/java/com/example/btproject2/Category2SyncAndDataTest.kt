package com.example.btproject2

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AttachedDocument
import com.example.btproject2.models.ClanMergeRequest
import com.example.btproject2.models.InviteCodeRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.Person
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.models.UserOnboardingState
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.DocumentHelper
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * Unit test suite verifying all Category 2 (Cloud Database, Memory & Real-Time Sync)
 * remediations (Issues 2.1 through 2.10).
 */
class Category2SyncAndDataTest {

    private fun findInCache(id: String): Person? {
        return FirestoreHelper.getCachedPersons().orEmpty().firstOrNull { it.id == id }
    }

    @Before
    fun setUp() {
        FirestoreHelper.clearCache()
    }

    @After
    fun tearDown() {
        FirestoreHelper.clearCache()
    }

    // =========================================================================
    // Issue 2.1 & 2.10 — Real-time listener diff before cache overwrite & 24-field divergence
    // =========================================================================

    @Test
    fun `hasListDiverged detects changes in biography, birthPlace, middleName, suffix, photoBase64, and documents`() {
        val base = Person(
            id = "p1",
            firstName = "Thomas",
            middleName = "Edward",
            lastName = "Malone",
            birthDate = "1970-01-01",
            birthPlace = "Manila",
            biography = "Founder of branch",
            treeId = "tree_1"
        )

        assertFalse(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy())))
        assertTrue(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy(biography = "Updated bio"))))
        assertTrue(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy(birthPlace = "Cebu"))))
        assertTrue(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy(middleName = "James"))))
        assertTrue(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy(suffix = "Jr."))))
        assertTrue(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy(isPrivate = true))))
        assertTrue(FirestoreHelper.hasListDiverged(listOf(base), listOf(base.copy(photoBase64 = "abc123base64"))))
        assertTrue(
            FirestoreHelper.hasListDiverged(
                listOf(base),
                listOf(base.copy(documents = listOf(AttachedDocument(id = "d1", name = "BirthCert.pdf"))))
            )
        )
    }

    @Test
    fun `CentralTreeSynchronizer processRealtimeSnapshotUpdate diffs old cache before updating and evicts zombie records`() {
        val p1 = Person(id = "p1", firstName = "Thomas", lastName = "Malone", biography = "Old Bio", treeId = "tree_1")
        val zombie = Person(id = "p_zombie", firstName = "Deleted", lastName = "Remote", treeId = "tree_1")
        val otherTreePerson = Person(id = "p_other", firstName = "Other", lastName = "Clan", treeId = "tree_2")

        val sync = CentralTreeSynchronizer.getInstance()
        sync.resetForTesting()
        FirestoreHelper.setCachedPersons(listOf(p1, zombie, otherTreePerson))

        val p1Updated = p1.copy(biography = "New Bio From Cloud")
        sync.processRealtimeSnapshotUpdate("tree_1", listOf(p1Updated))

        val events = sync.getRecentEvents()
        assertTrue(
            "processRealtimeSnapshotUpdate must emit edit/delete events by diffing BEFORE cache overwrite",
            events.isNotEmpty()
        )

        val tree1Cached = FirestoreHelper.getCachedPersons("tree_1").orEmpty()
        assertEquals("Zombie record deleted remotely must be evicted from authoritative tree snapshot", 1, tree1Cached.size)
        assertEquals("New Bio From Cloud", tree1Cached.first().biography)

        val tree2Cached = FirestoreHelper.getCachedPersons("tree_2").orEmpty()
        assertEquals("Records from other trees must remain untouched", 1, tree2Cached.size)
    }

    // =========================================================================
    // Issue 2.3 — Boolean is* Firestore serialization annotations
    // =========================================================================

    @Test
    fun `NotificationRecord and PrivacySettings have PropertyName annotations for boolean isRead and isPrivateTree`() {
        val getIsRead = NotificationRecord::class.java.getDeclaredMethod("isRead")
        val propNameRead = getIsRead.getAnnotation(PropertyName::class.java)
        assertNotNull("NotificationRecord.isRead getter must have @PropertyName(\"isRead\")", propNameRead)
        assertEquals("isRead", propNameRead?.value)

        val getIsPrivateTree = PrivacySettings::class.java.getDeclaredMethod("isPrivateTree")
        val propNamePrivate = getIsPrivateTree.getAnnotation(PropertyName::class.java)
        assertNotNull("PrivacySettings.isPrivateTree getter must have @PropertyName(\"isPrivateTree\")", propNamePrivate)
        assertEquals("isPrivateTree", propNamePrivate?.value)
    }

    @Test
    fun `computed helper methods on UserOnboardingState, InviteCodeRecord, and ClanMergeRequest have Exclude annotations`() {
        val onboardingMethod = UserOnboardingState::class.java.getDeclaredMethod("isAllCompleted")
        assertNotNull("UserOnboardingState.isAllCompleted must have @Exclude", onboardingMethod.getAnnotation(Exclude::class.java))

        val inviteExpired = InviteCodeRecord::class.java.getDeclaredMethod("isExpired")
        assertNotNull("InviteCodeRecord.isExpired must have @Exclude", inviteExpired.getAnnotation(Exclude::class.java))

        val mergeExpired = ClanMergeRequest::class.java.getDeclaredMethod("isExpired")
        assertNotNull("ClanMergeRequest.isExpired must have @Exclude", mergeExpired.getAnnotation(Exclude::class.java))
    }

    // =========================================================================
    // Issue 2.4 — Clearing optional fields in updatePersonInCache
    // =========================================================================

    @Test
    fun `updatePersonInCache clears optional fields when full profile save is performed`() {
        val initial = Person(
            id = "p1",
            firstName = "Thomas",
            middleName = "Edward",
            lastName = "Malone",
            suffix = "Sr.",
            birthPlace = "Manila",
            biography = "Initial biography",
            treeId = "tree_1"
        )
        FirestoreHelper.setCachedPersons(listOf(initial))

        // Full profile save from EditMemberActivity (has firstName, lastName, and updatedAt > 0)
        val clearedProfile = initial.copy(
            middleName = "",
            suffix = "",
            birthPlace = "",
            biography = "",
            updatedAt = System.currentTimeMillis()
        )
        FirestoreHelper.updatePersonInCache(clearedProfile)

        val afterUpdate = findInCache("p1")!!
        assertEquals("", afterUpdate.middleName)
        assertEquals("", afterUpdate.suffix)
        assertEquals("", afterUpdate.birthPlace)
        assertEquals("", afterUpdate.biography)
    }

    // =========================================================================
    // Issue 2.6 — Duplicate merge evicts deleted duplicate and relinks children/spouses in cache
    // =========================================================================

    @Test
    fun `applyMergePersonsToCache evicts deleted duplicate and updates children and spouse links`() {
        val primary = Person(id = "p_primary", firstName = "Thomas", lastName = "Malone", gender = "Male", treeId = "tree_1")
        val duplicate = Person(id = "p_dup", firstName = "Tom", lastName = "Malone", gender = "Male", spouseId = "p_wife", treeId = "tree_1")
        val wife = Person(id = "p_wife", firstName = "Maria", lastName = "Santos", gender = "Female", spouseId = "p_dup", treeId = "tree_1")
        val child = Person(id = "p_child", firstName = "Junior", lastName = "Malone", fatherId = "p_dup", motherId = "p_wife", treeId = "tree_1")

        FirestoreHelper.setCachedPersons(listOf(primary, duplicate, wife, child))

        val mergedPrimary = primary.copy(spouseId = "p_wife", maritalStatus = "Married")
        FirestoreHelper.applyMergePersonsToCache(
            mergedPerson = mergedPrimary,
            deletedPersonId = "p_dup",
            affectedChildren = listOf(child)
        )

        assertNull("Deleted duplicate must be evicted from in-memory cache", findInCache("p_dup"))
        val updatedChild = findInCache("p_child")!!
        assertEquals("Child fatherId must be relinked to primary person", "p_primary", updatedChild.fatherId)
        val updatedWife = findInCache("p_wife")!!
        assertEquals("Spouse spouseId must be relinked to primary person", "p_primary", updatedWife.spouseId)
    }

    // =========================================================================
    // Issue 2.8 — Archiving and restoring a deleted member restores child and spouse links
    // =========================================================================

    @Test
    fun `buildArchivedMemberSnapshot and applyRestoreArchivedMemberToCache preserve and restore child and spouse links`() {
        val father = Person(
            id = "p_father",
            firstName = "Thomas",
            lastName = "Malone",
            gender = "Male",
            spouseId = "p_mother",
            maritalStatus = "Married",
            treeId = "tree_1"
        )
        val mother = Person(
            id = "p_mother",
            firstName = "Maria",
            lastName = "Malone",
            gender = "Female",
            spouseId = "p_father",
            maritalStatus = "Married",
            treeId = "tree_1"
        )
        val child1 = Person(id = "p_c1", firstName = "Child", lastName = "One", fatherId = "p_father", motherId = "p_mother", treeId = "tree_1")
        val child2 = Person(id = "p_c2", firstName = "Child", lastName = "Two", fatherId = "p_father", motherId = "p_mother", treeId = "tree_1")

        val currentTree = listOf(father, mother, child1, child2)
        val archiveSnapshot = FirestoreHelper.buildArchivedMemberSnapshot(
            person = father,
            treeMembers = currentTree,
            treeName = "Malone Clan",
            deletedBy = "admin@kintrace.ph",
            deletedByName = "Admin"
        )

        assertEquals(listOf("p_c1", "p_c2"), archiveSnapshot.formerFatherChildIds)
        assertTrue(archiveSnapshot.formerMotherChildIds.isEmpty())
        assertEquals("p_mother", archiveSnapshot.originalSpouseId)

        // Simulate state after father was deleted (spouse unlinked, children fatherId cleared)
        FirestoreHelper.setCachedPersons(
            listOf(
                mother.copy(spouseId = null, maritalStatus = "Single"),
                child1.copy(fatherId = null),
                child2.copy(fatherId = null)
            )
        )

        FirestoreHelper.applyRestoreArchivedMemberToCache(archiveSnapshot)

        val restoredFather = findInCache("p_father")
        assertNotNull("Father must be restored in cache", restoredFather)
        assertEquals("p_mother", restoredFather?.spouseId)

        val restoredMother = findInCache("p_mother")!!
        assertEquals("Mother spouseId must be relinked to restored father", "p_father", restoredMother.spouseId)
        assertEquals("Married", restoredMother.maritalStatus)

        assertEquals("p_father", findInCache("p_c1")?.fatherId)
        assertEquals("p_father", findInCache("p_c2")?.fatherId)
    }

    // =========================================================================
    // Issue 2.7 — Portable Base64 document reconstruction when device-local file is missing
    // =========================================================================

    @Test
    fun `DocumentHelper reconstructFileIfMissing reconstructs missing local document from fileBase64`() {
        val tempDir = Files.createTempDirectory("kintrace_doc_test").toFile()
        try {
            val sampleBytes = "KinTrace Heritage Certificate Content 2026".toByteArray(Charsets.UTF_8)
            val encoded = Base64.getEncoder().encodeToString(sampleBytes)

            val doc = AttachedDocument(
                id = "doc_99",
                name = "Birth_Certificate.pdf",
                fileUri = File(tempDir, "non_existent_path/Birth_Certificate.pdf").absolutePath,
                fileType = "pdf",
                category = "Birth Certificate",
                fileBase64 = encoded
            )

            val reconstructed = DocumentHelper.reconstructFileIfMissing(tempDir, doc)
            assertNotNull("Missing local document should be reconstructed from fileBase64", reconstructed)
            assertTrue("Reconstructed file must exist on disk", reconstructed!!.exists())
            assertEquals("KinTrace Heritage Certificate Content 2026", reconstructed.readText(Charsets.UTF_8))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
