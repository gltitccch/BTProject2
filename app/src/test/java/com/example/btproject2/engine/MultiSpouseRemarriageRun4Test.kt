package com.example.btproject2.engine

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.sync.SyncScopeResolver
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MultiSpouseRemarriageRun4Test {

    @Before
    fun setUp() {
        FirestoreHelper.clearCache()
    }

    @After
    fun tearDown() {
        FirestoreHelper.clearCache()
    }

    @Test
    fun `setCachedPersons preserves remarriage triad without overwriting surviving spouse back to 1st spouse`() {
        val jose = Person(
            id = "jose",
            treeId = "tree_1",
            firstName = "Jose",
            lastName = "Reyes",
            gender = "Male",
            birthDate = "1970-01-01",
            spouseId = "elena",
            formerSpouseIds = listOf("maria"),
            maritalStatus = "Married",
            updatedAt = 2000L
        )
        val maria = Person(
            id = "maria",
            treeId = "tree_1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            birthDate = "1972-05-10",
            isLiving = false,
            deathDate = "2004-03-15",
            spouseId = "jose",
            maritalStatus = "Married",
            updatedAt = 1000L
        )
        val elena = Person(
            id = "elena",
            treeId = "tree_1",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            birthDate = "1975-08-20",
            spouseId = "jose",
            maritalStatus = "Married",
            updatedAt = 2000L
        )

        FirestoreHelper.setCachedPersons(
            listOf(jose, maria, elena),
            authoritativeTreeId = "tree_1",
            isAuthoritativeServerSnapshot = true
        )

        val cached = FirestoreHelper.getCachedPersons("tree_1").orEmpty()
        val cachedJose = cached.first { it.id == "jose" }
        val cachedMaria = cached.first { it.id == "maria" }
        val cachedElena = cached.first { it.id == "elena" }

        // Jose must still have Elena as active spouseId and Maria in formerSpouseIds!
        assertEquals("elena", cachedJose.spouseId)
        assertTrue(cachedJose.formerSpouseIds.contains("maria"))
        // Maria must still point to Jose
        assertEquals("jose", cachedMaria.spouseId)
        // Elena must still point to Jose
        assertEquals("jose", cachedElena.spouseId)
    }

    @Test
    fun `Soft-delete archive and restore preserves originalFormerSpouseIds and remarriage triad`() {
        val jose = Person(
            id = "jose",
            treeId = "tree_1",
            firstName = "Jose",
            lastName = "Reyes",
            gender = "Male",
            spouseId = "elena",
            formerSpouseIds = listOf("maria"),
            maritalStatus = "Married"
        )
        val maria = Person(
            id = "maria",
            treeId = "tree_1",
            firstName = "Maria",
            lastName = "Santos",
            gender = "Female",
            isLiving = false,
            deathDate = "2004-03-15",
            spouseId = "jose",
            maritalStatus = "Married"
        )
        val elena = Person(
            id = "elena",
            treeId = "tree_1",
            firstName = "Elena",
            lastName = "Cruz",
            gender = "Female",
            spouseId = "jose",
            maritalStatus = "Married"
        )
        val carlo = Person(
            id = "carlo",
            treeId = "tree_1",
            firstName = "Carlo",
            lastName = "Reyes",
            gender = "Male",
            fatherId = "jose",
            motherId = "maria"
        )

        val all = listOf(jose, maria, elena, carlo)
        FirestoreHelper.setCachedPersons(all, "tree_1", true)

        // 1. Archive Jose (who has both active spouse Elena and former spouse Maria)
        val archiveJose = FirestoreHelper.buildArchivedMemberSnapshot(
            person = jose,
            treeMembers = all,
            treeName = "Reyes Family"
        )
        assertEquals("elena", archiveJose.originalSpouseId)
        assertEquals(listOf("maria"), archiveJose.originalFormerSpouseIds)
        assertEquals(listOf("carlo"), archiveJose.formerFatherChildIds)

        // Simulate Jose deleted from cache
        FirestoreHelper.removePersonFromCache("jose")

        // 2. Restore Jose via applyRestoreArchivedMemberToCache
        val restoredJose = FirestoreHelper.applyRestoreArchivedMemberToCache(archiveJose)
        assertEquals("elena", restoredJose.spouseId)
        assertEquals(listOf("maria"), restoredJose.formerSpouseIds)

        // 3. Also test archiving & restoring 1st spouse Maria while Jose is already remarried to Elena
        val archiveMaria = FirestoreHelper.buildArchivedMemberSnapshot(
            person = maria,
            treeMembers = all,
            treeName = "Reyes Family"
        )
        val joseWithoutMaria = jose.copy(formerSpouseIds = emptyList())
        FirestoreHelper.setCachedPersons(listOf(joseWithoutMaria, elena, carlo.copy(motherId = null)), "tree_1", true)

        FirestoreHelper.applyRestoreArchivedMemberToCache(archiveMaria)
        val cachedAfterMariaRestore = FirestoreHelper.getCachedPersons("tree_1").orEmpty()
        val updatedJose = cachedAfterMariaRestore.first { it.id == "jose" }
        assertEquals("Jose's active marriage to Elena must not be overwritten when restoring late 1st wife Maria", "elena", updatedJose.spouseId)
        assertTrue("Restored late 1st wife Maria must be added back to Jose's formerSpouseIds", updatedJose.formerSpouseIds.contains("maria"))

        // 4. Verify SyncScopeResolver includes formerSpouseIds in relatedPersonIds
        val spouseScope = SyncScopeResolver.resolveSpouseChanged(updatedJose, elena)
        assertTrue(spouseScope.relatedPersonIds.contains("elena"))
        assertTrue(spouseScope.relatedPersonIds.contains("maria"))
    }
}
