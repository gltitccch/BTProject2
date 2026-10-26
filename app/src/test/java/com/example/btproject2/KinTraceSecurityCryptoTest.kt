package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.utils.JwtSessionVerifier
import com.example.btproject2.utils.KinTraceCryptoEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class KinTraceSecurityCryptoTest {

    @Test
    fun `encryptField and decryptField round trip preserves exact plaintext`() {
        val treeId = "tree_delacruz_2026"
        val original = "Cebu City, Philippines — Ancestral Home near Basilica"

        val encrypted = KinTraceCryptoEngine.encryptField(original, treeId)
        assertTrue("Encrypted field must have ENCv1:: prefix", KinTraceCryptoEngine.isEncrypted(encrypted))
        assertNotEquals("Encrypted field must not equal plaintext", original, encrypted)

        val decrypted = KinTraceCryptoEngine.decryptField(encrypted, treeId)
        assertEquals("Decrypted field must match original plaintext", original, decrypted)
    }

    @Test
    fun `encryptField uses random 12-byte IV so identical plaintexts produce distinct ciphertexts`() {
        val treeId = "tree_delacruz_2026"
        val text = "Manila, Philippines"

        val cipher1 = KinTraceCryptoEngine.encryptField(text, treeId)
        val cipher2 = KinTraceCryptoEngine.encryptField(text, treeId)

        assertNotEquals("Fresh random IV must produce distinct ciphertexts", cipher1, cipher2)
        assertEquals(text, KinTraceCryptoEngine.decryptField(cipher1, treeId))
        assertEquals(text, KinTraceCryptoEngine.decryptField(cipher2, treeId))
    }

    @Test
    fun `legacy unencrypted plaintext passes through decryptField unchanged`() {
        val legacyValue = "Quezon City, Metro Manila"
        assertFalse(KinTraceCryptoEngine.isEncrypted(legacyValue))
        assertEquals(legacyValue, KinTraceCryptoEngine.decryptField(legacyValue, "any_tree"))
    }

    @Test
    fun `encryptPersonForCloud encrypts sensitive fields while keeping graph fields searchable`() {
        val rawPerson = Person(
            id = "person_01",
            treeId = "tree_malone_01",
            firstName = "Mateo",
            lastName = "Dela Cruz",
            birthDate = "14/03/1952",
            birthPlace = "Vigan, Ilocos Sur",
            deathDate = "20/11/2020",
            deathPlace = "Makati Medical Center",
            biography = "Founded the family printing press in 1978.",
            fatherId = "father_01",
            motherId = "mother_01"
        )

        val cloudPerson = KinTraceCryptoEngine.encryptPersonForCloud(rawPerson)

        // Graph & search fields remain readable for tree algorithms
        assertEquals("Mateo", cloudPerson.firstName)
        assertEquals("Dela Cruz", cloudPerson.lastName)
        assertEquals("14/03/1952", cloudPerson.birthDate)
        assertEquals("father_01", cloudPerson.fatherId)

        // Sensitive personal details are encrypted with AES-256-GCM
        assertTrue(KinTraceCryptoEngine.isEncrypted(cloudPerson.birthPlace))
        assertTrue(KinTraceCryptoEngine.isEncrypted(cloudPerson.deathPlace))
        assertTrue(KinTraceCryptoEngine.isEncrypted(cloudPerson.biography))

        // Decrypting from cloud restores 100% of the original Person
        val restoredPerson = KinTraceCryptoEngine.decryptPersonFromCloud(cloudPerson)
        assertEquals(rawPerson, restoredPerson)
    }

    @Test
    fun `JwtSessionVerifier decodes valid RS256 OAuth2 JWT and computes remaining lifetime`() {
        val nowSeconds = 1_800_000_000L
        val iatSeconds = nowSeconds - 600L       // issued 10 mins ago
        val expSeconds = nowSeconds + 3000L      // expires in 50 mins

        val headerJson = """{"alg":"RS256","typ":"JWT"}"""
        val payloadJson = """{"iss":"https://securetoken.google.com/kintrace-app","sub":"uid_renzy_123","iat":$iatSeconds,"exp":$expSeconds}"""

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val fakeJwt = "${encoder.encodeToString(headerJson.toByteArray())}.${encoder.encodeToString(payloadJson.toByteArray())}.signature_bytes"

        val summary = JwtSessionVerifier.verifyAndDecodeJwt(fakeJwt, currentEpochSec = nowSeconds)

        assertTrue(summary.isValidStructure)
        assertEquals("RS256", summary.algorithm)
        assertEquals("JWT", summary.tokenType)
        assertEquals("uid_renzy_123", summary.subjectUid)
        assertFalse(summary.isExpired)
        assertFalse(summary.shouldRotateSoon)
        assertEquals(50L, summary.remainingMinutes)
    }

    @Test
    fun `JwtSessionVerifier flags expired and near-expiry tokens for rotation`() {
        val nowSeconds = 1_800_000_000L
        val expSoon = nowSeconds + 120L // 2 minutes left (< 5 min threshold)

        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"sub":"user_1","iat":${nowSeconds - 3480},"exp":$expSoon}""".toByteArray())
        val token = "$header.$payload.sig"

        val summarySoon = JwtSessionVerifier.verifyAndDecodeJwt(token, currentEpochSec = nowSeconds)
        assertTrue(summarySoon.isValidStructure)
        assertFalse(summarySoon.isExpired)
        assertTrue("Token with 2 mins left should flag shouldRotateSoon", summarySoon.shouldRotateSoon)

        val summaryExpired = JwtSessionVerifier.verifyAndDecodeJwt(token, currentEpochSec = nowSeconds + 500L)
        assertTrue("Token past exp timestamp must be marked expired", summaryExpired.isExpired)
    }
}
