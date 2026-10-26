package com.example.btproject2.utils

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Pure Kotlin OAuth 2.0 / OpenID Connect JWT (JSON Web Token) Session Verifier.
 *
 * Inspects cryptographically signed RS256 JWT tokens issued by Firebase Authentication,
 * validates structural integrity and expiration windows, and determines when silent background
 * token rotation is needed.
 */
data class JwtTokenSummary(
    val isValidStructure: Boolean,
    val algorithm: String,
    val tokenType: String,
    val issuer: String,
    val subjectUid: String,
    val issuedAtEpochSec: Long,
    val expirationEpochSec: Long,
    val remainingMinutes: Long,
    val isExpired: Boolean,
    val shouldRotateSoon: Boolean
)

object JwtSessionVerifier {

    private const val SILENT_ROTATION_THRESHOLD_SEC = 300L // Rotate silently when < 5 minutes left

    /**
     * Parses and verifies the claims of a raw `header.payload.signature` JWT string.
     */
    fun verifyAndDecodeJwt(
        rawJwt: String,
        currentEpochSec: Long = System.currentTimeMillis() / 1000L
    ): JwtTokenSummary {
        val parts = rawJwt.trim().split(".")
        if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return invalidSummary()
        }

        return try {
            val headerJson = String(Base64.getUrlDecoder().decode(padBase64(parts[0])), StandardCharsets.UTF_8)
            val payloadJson = String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8)

            val alg = extractStringClaim(headerJson, "alg").ifEmpty { "RS256" }
            val typ = extractStringClaim(headerJson, "typ").ifEmpty { "JWT" }
            val iss = extractStringClaim(payloadJson, "iss")
            val sub = extractStringClaim(payloadJson, "sub").ifEmpty {
                extractStringClaim(payloadJson, "user_id")
            }
            val iat = extractLongClaim(payloadJson, "iat")
            val exp = extractLongClaim(payloadJson, "exp")

            val remainingSec = if (exp > 0L) (exp - currentEpochSec).coerceAtLeast(0L) else 0L
            val remainingMin = remainingSec / 60L
            val expired = exp in 1L..currentEpochSec
            val rotateSoon = exp > 0L && remainingSec <= SILENT_ROTATION_THRESHOLD_SEC

            JwtTokenSummary(
                isValidStructure = true,
                algorithm = alg,
                tokenType = typ,
                issuer = iss,
                subjectUid = sub,
                issuedAtEpochSec = iat,
                expirationEpochSec = exp,
                remainingMinutes = remainingMin,
                isExpired = expired,
                shouldRotateSoon = rotateSoon
            )
        } catch (e: Exception) {
            invalidSummary()
        }
    }

    private fun padBase64(input: String): String {
        val rem = input.length % 4
        return if (rem == 0) input else input + "=".repeat(4 - rem)
    }

    internal fun extractStringClaim(json: String, key: String): String {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.getOrNull(1).orEmpty()
    }

    internal fun extractLongClaim(json: String, key: String): Long {
        val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    private fun invalidSummary(): JwtTokenSummary {
        return JwtTokenSummary(
            isValidStructure = false,
            algorithm = "RS256",
            tokenType = "JWT",
            issuer = "",
            subjectUid = "",
            issuedAtEpochSec = 0L,
            expirationEpochSec = 0L,
            remainingMinutes = 0L,
            isExpired = true,
            shouldRotateSoon = true
        )
    }
}
