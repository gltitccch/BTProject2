package com.example.btproject2.utils

import com.example.btproject2.models.Person

class DuplicateDetector {

    fun findPossibleDuplicates(
        newPerson: Person,
        existingPersons: List<Person>,
        threshold: Double = 0.85
    ): List<Person> {
        return existingPersons.filter { existing ->
            val similarity = calculateSimilarity(
                "${newPerson.firstName} ${newPerson.lastName}",
                "${existing.firstName} ${existing.lastName}"
            )
            similarity >= threshold
        }
    }

    private fun calculateSimilarity(str1: String, str2: String): Double {
        val s1 = str1.lowercase().trim()
        val s2 = str2.lowercase().trim()

        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)

        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}