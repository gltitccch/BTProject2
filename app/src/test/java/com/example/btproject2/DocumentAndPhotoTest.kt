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
}

