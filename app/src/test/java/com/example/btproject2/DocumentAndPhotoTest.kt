package com.example.btproject2

import com.example.btproject2.models.AttachedDocument
import com.example.btproject2.models.Person
import com.example.btproject2.utils.DocumentHelper
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DocumentAndPhotoTest {

    @Test
    fun testAttachedDocumentProperties() {
        val doc = AttachedDocument(
            id = "doc-123",
            name = "PSA_Birth_Certificate.pdf",
            fileUri = "/data/user/0/com.example.btproject2/files/documents/PSA_Birth_Certificate.pdf",
            fileType = "pdf",
            category = "Birth Certificate",
            uploadedAt = 1700000000000L,
            fileSize = "245 KB"
        )

        assertEquals("doc-123", doc.id)
        assertEquals("PSA_Birth_Certificate.pdf", doc.name)
        assertEquals("pdf", doc.fileType)
        assertEquals("Birth Certificate", doc.category)
        assertEquals("245 KB", doc.fileSize)
    }

    @Test
    fun testPersonModelWithPhotoAndDocuments() {
        val doc1 = AttachedDocument(
            id = "d1",
            name = "Birth_Cert.pdf",
            fileType = "pdf",
            category = "Birth Certificate"
        )
        val doc2 = AttachedDocument(
            id = "d2",
            name = "National_ID.jpg",
            fileType = "image",
            category = "Government ID"
        )

        val person = Person(
            id = "p1",
            firstName = "Renzy",
            lastName = "Bugarin",
            gender = "Male",
            birthDate = "2000-05-14",
            photoUri = "/path/to/photo.jpg",
            photoBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            documents = listOf(doc1, doc2)
        )

        assertEquals("Renzy", person.firstName)
        assertEquals("/path/to/photo.jpg", person.photoUri)
        assertTrue(person.photoBase64.isNotEmpty())
        assertEquals(2, person.documents.size)
        assertEquals("Birth_Cert.pdf", person.documents[0].name)
        assertEquals("National_ID.jpg", person.documents[1].name)
    }

    @Test
    fun testCalculateAgeLiving() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val birthYear = currentYear - 25
        val birthDate = "$birthYear-01-01"

        val ageStr = DocumentHelper.calculateAge(birthDate, isLiving = true)
        assertTrue(ageStr.contains("25 yrs old") || ageStr.contains("24 yrs old"))
    }

    @Test
    fun testCalculateAgeDeceased() {
        val birthDate = "1950-06-15"
        val deathDate = "2020-06-15"

        val ageStr = DocumentHelper.calculateAge(birthDate, deathDate = deathDate, isLiving = false)
        assertEquals("Died at age 70", ageStr)
    }

    @Test
    fun testCalculateAgeEmptyDate() {
        val ageStrLiving = DocumentHelper.calculateAge("", isLiving = true)
        assertEquals("", ageStrLiving)

        val ageStrDeceased = DocumentHelper.calculateAge("", isLiving = false)
        assertEquals("Deceased", ageStrDeceased)
    }

    @Test
    fun testAhnentafelPedigreeFormulas() {
        // Root is index 1
        val selfIdx = 1
        val fatherIdx = 2 * selfIdx
        val motherIdx = 2 * selfIdx + 1

        assertEquals(2, fatherIdx)
        assertEquals(3, motherIdx)

        // Paternal grandparents: father's parents
        val paternalGfIdx = 2 * fatherIdx
        val paternalGmIdx = 2 * fatherIdx + 1
        assertEquals(4, paternalGfIdx)
        assertEquals(5, paternalGmIdx)

        // Maternal grandparents: mother's parents
        val maternalGfIdx = 2 * motherIdx
        val maternalGmIdx = 2 * motherIdx + 1
        assertEquals(6, maternalGfIdx)
        assertEquals(7, maternalGmIdx)

        // Great-grandparents: 8 to 15
        val greatGrandparents = (4..7).flatMap { listOf(2 * it, 2 * it + 1) }
        assertEquals(8, greatGrandparents.size)
        assertEquals((8..15).toList(), greatGrandparents)

        // Great-great-grandparents: 16 to 31
        val greatGreatGrandparents = (8..15).flatMap { listOf(2 * it, 2 * it + 1) }
        assertEquals(16, greatGreatGrandparents.size)
        assertEquals((16..31).toList(), greatGreatGrandparents)
    }

    @Test
    fun testPdfAttachmentSnapshotDoesNotTrigger72MemberEventStorm() {
        val sync = com.example.btproject2.sync.CentralTreeSynchronizer.getInstance()
        sync.resetForTesting()
        com.example.btproject2.firebase.FirestoreHelper.clearCache()

        val treeId = "bugarin_tree_72"
        val initialMembers = (1..72).map { idx ->
            Person(
                id = "m_$idx",
                firstName = if (idx == 4) "Victoria" else "Member$idx",
                lastName = "Bugarin",
                gender = if (idx % 2 == 0) "Female" else "Male",
                treeId = treeId,
                createdAt = 1000L + idx
            )
        }
        com.example.btproject2.firebase.FirestoreHelper.setCachedPersons(
            initialMembers,
            authoritativeTreeId = treeId,
            isAuthoritativeServerSnapshot = true
        )

        val receivedEvents = mutableListOf<com.example.btproject2.sync.TreeSyncEvent>()
        var lastRequiresRelayout: Boolean? = null
        val coordinator = com.example.btproject2.sync.InteractiveTreeSyncCoordinator(
            treeId = treeId,
            allPersonsList = com.example.btproject2.firebase.FirestoreHelper.getCachedPersons(treeId).orEmpty(),
            onTreeUpdated = { _, requiresRelayout ->
                lastRequiresRelayout = requiresRelayout
            }
        )
        val countingListener = object : com.example.btproject2.sync.SyncEventListener {
            override val subscriberKey: String = "TestCountingListener"
            override val screenType = com.example.btproject2.sync.AffectedScreen.INTERACTIVE_TREE
            override val interestedTreeId: String = treeId
            override fun onSyncEvent(event: com.example.btproject2.sync.TreeSyncEvent) {
                receivedEvents.add(event)
                coordinator.onSyncEvent(event)
            }
        }
        sync.registerListener(countingListener)

        // Simulate Firestore snapshot where all 72 members get a fresh createdAt timestamp
        // (as happens when createdAt is missing in Firestore) and ONLY Victoria (m_4) has a new PDF attached
        val attachedPdf = AttachedDocument(
            id = "pdf_1",
            name = "Bugarin_Family_Report.pdf",
            fileUri = "/data/user/0/com.example.btproject2/files/documents/Bugarin_Family_Report.pdf",
            fileType = "pdf",
            category = "Birth Certificate",
            fileSize = "495 KB"
        )
        val snapshotMembers = initialMembers.map { m ->
            if (m.id == "m_4") {
                m.copy(createdAt = 9999999L, documents = listOf(attachedPdf))
            } else {
                m.copy(createdAt = 9999999L)
            }
        }

        sync.processRealtimeSnapshotUpdate(treeId, snapshotMembers)

        // Must emit EXACTLY 1 event (for m_4), NOT 72 events!
        assertEquals(1, receivedEvents.size)
        val event = receivedEvents.first()
        assertEquals("m_4", event.primaryPerson?.id)
        assertTrue(event.scope.affectedFields.contains("documents"))
        assertFalse(event.scope.requiresStructuralRelayout)
        assertEquals(false, lastRequiresRelayout)
        assertEquals(1, coordinator.allPersonsList.find { it.id == "m_4" }?.documents?.size)

        sync.resetForTesting()
        com.example.btproject2.firebase.FirestoreHelper.clearCache()
    }

    @Test
    fun testLarge200MemberKinshipPrecomputationAndSpouseFilteringPerformance() {
        val treeId = "clan_200_stress"
        val members = mutableListOf<Person>()

        // Founding Patriarch & Matriarch
        members.add(
            Person(
                id = "p_1",
                firstName = "Founder",
                lastName = "Bugarin",
                gender = "Male",
                birthDate = "1940-01-01",
                spouseId = "p_2",
                maritalStatus = "Married",
                treeId = treeId
            )
        )
        members.add(
            Person(
                id = "p_2",
                firstName = "Matriarch",
                lastName = "Bugarin",
                gender = "Female",
                birthDate = "1942-05-10",
                spouseId = "p_1",
                maritalStatus = "Married",
                treeId = treeId
            )
        )

        // Children of founders (p_3 .. p_12)
        for (i in 3..12) {
            members.add(
                Person(
                    id = "p_$i",
                    firstName = "Child$i",
                    lastName = "Bugarin",
                    gender = if (i % 2 == 0) "Female" else "Male",
                    birthDate = "1968-03-15",
                    fatherId = "p_1",
                    motherId = "p_2",
                    treeId = treeId
                )
            )
        }

        // Fill up to 200 members with extended clan and unrelated eligible adults
        for (i in 13..200) {
            val isUnrelatedAdult = i > 150
            members.add(
                Person(
                    id = "p_$i",
                    firstName = "Member$i",
                    lastName = if (isUnrelatedAdult) "Santos" else "Bugarin",
                    gender = if (i % 2 == 0) "Female" else "Male",
                    birthDate = "1995-07-20",
                    fatherId = if (isUnrelatedAdult) null else "p_3",
                    treeId = treeId
                )
            )
        }

        val focal = members.first { it.id == "p_3" }

        // 1. Validate KinshipTitleHelper.resolveAllTitles precomputation on 200 members
        val startTitles = System.currentTimeMillis()
        val allTitles = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(members, focal)
        val elapsedTitlesMs = System.currentTimeMillis() - startTitles

        assertEquals(200, allTitles.size)
        assertEquals("FATHER", allTitles["p_1"])
        assertEquals("MOTHER", allTitles["p_2"])
        assertEquals("FULL SISTER", allTitles["p_4"])
        assertEquals("FULL BROTHER", allTitles["p_5"])
        assertTrue("Precomputing 200 kinship titles took too long: ${elapsedTitlesMs}ms", elapsedTitlesMs < 1500L)

        // 2. Validate FamilyLinkValidator.getEligibleSpouses on 200 members
        val startSpouses = System.currentTimeMillis()
        val eligibleSpouses = com.example.btproject2.engine.FamilyLinkValidator.getEligibleSpouses(focal, members)
        val elapsedSpousesMs = System.currentTimeMillis() - startSpouses

        // Focal p_3 is Male -> all eligible spouses must be Female, not p_2 (Mother), not p_4 (Sister), not daughters
        assertTrue(eligibleSpouses.isNotEmpty())
        assertTrue(eligibleSpouses.all { it.gender.equals("Female", ignoreCase = true) })
        assertFalse(eligibleSpouses.any { it.id == "p_2" }) // Mother excluded
        assertFalse(eligibleSpouses.any { it.id == "p_4" }) // Sister excluded
        assertFalse(eligibleSpouses.any { it.fatherId == "p_3" }) // Daughters excluded
        assertTrue("Filtering 200 spouse candidates took too long: ${elapsedSpousesMs}ms", elapsedSpousesMs < 2000L)
    }
}


