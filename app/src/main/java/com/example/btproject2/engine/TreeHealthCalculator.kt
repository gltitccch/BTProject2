package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * TreeHealthCalculator — Scores the overall quality of the family tree.
 * Starts at 100 and subtracts for issues.
 */
class TreeHealthCalculator {

    data class HealthResult(
        val score: Int,               // 0-100
        val label: String,            // "Excellent", "Good", "Fair", "Poor"
        val deductions: List<String>, // what caused point loss
        val stats: TreeStats
    )

    data class TreeStats(
        val totalMembers: Int,
        val withBirthDate: Int,
        val withParents: Int,
        val withSpouse: Int,
        val orphanCount: Int,        // no parents AND no children
        val duplicateSuspects: Int,
        val incompleteProfiles: Int,
        val maxGenerations: Int
    )

    private val profileChecker = ProfileCompletenessChecker()
    private val validator = ConsistencyValidator()

    fun calculate(allPersons: List<Person>): HealthResult {
        if (allPersons.isEmpty()) {
            return HealthResult(0, "Empty", listOf("No family members added"), TreeStats(0,0,0,0,0,0,0,0))
        }

        var score = 100
        val deductions = mutableListOf<String>()

        val total = allPersons.size
        val withBirth = allPersons.count { it.birthDate.isNotEmpty() }
        val withParents = allPersons.count { it.fatherId != null || it.motherId != null }
        val withSpouse = allPersons.count { it.spouseId != null }
        val childIds = allPersons.flatMap { listOfNotNull(it.fatherId, it.motherId) }.toSet()
        val parentIds = allPersons.filter { it.fatherId != null || it.motherId != null }.map { it.id }.toSet()
        val orphans = allPersons.count { it.fatherId == null && it.motherId == null && it.id !in childIds }

        // Missing birth dates: -1 per member, max -15
        val missingBirth = total - withBirth
        if (missingBirth > 0) {
            val penalty = (missingBirth * 1).coerceAtMost(15)
            score -= penalty
            deductions.add("$missingBirth member(s) missing birth dates (-$penalty)")
        }

        // Missing parents: -1 per member, max -15
        val missingParents = total - withParents
        if (missingParents > 1) { // allow 1 root
            val penalty = ((missingParents - 1) * 1).coerceAtMost(15)
            score -= penalty
            deductions.add("${missingParents - 1} member(s) missing parent links (-$penalty)")
        }

        // Orphans (no parents, no children pointing to them): -2 each, max -10
        if (orphans > 1) {
            val penalty = ((orphans - 1) * 2).coerceAtMost(10)
            score -= penalty
            deductions.add("${orphans - 1} disconnected member(s) (-$penalty)")
        }

        // Incomplete profiles: -1 per member below 60% completeness, max -10
        val incomplete = allPersons.count { profileChecker.check(it).score < 60 }
        if (incomplete > 0) {
            val penalty = (incomplete * 1).coerceAtMost(10)
            score -= penalty
            deductions.add("$incomplete member(s) with incomplete profiles (-$penalty)")
        }

        // Validation errors: -5 per member with errors, max -20
        val personMap = allPersons.associateBy { it.id }
        var errorMembers = 0
        allPersons.forEach { person ->
            val result = validator.validatePerson(person, allPersons)
            if (!result.isValid) errorMembers++
        }
        if (errorMembers > 0) {
            val penalty = (errorMembers * 5).coerceAtMost(20)
            score -= penalty
            deductions.add("$errorMembers member(s) with data errors (-$penalty)")
        }

        // Duplicate suspects: -3 each, max -10
        val duplicateSuspects = countDuplicateSuspects(allPersons)
        if (duplicateSuspects > 0) {
            val penalty = (duplicateSuspects * 3).coerceAtMost(10)
            score -= penalty
            deductions.add("$duplicateSuspects possible duplicate(s) detected (-$penalty)")
        }

        // Max generations
        val maxGen = calculateMaxGenerations(allPersons)

        score = score.coerceIn(0, 100)

        val label = when {
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            else -> "Poor"
        }

        return HealthResult(
            score, label, deductions,
            TreeStats(total, withBirth, withParents, withSpouse, orphans, duplicateSuspects, incomplete, maxGen)
        )
    }

    private fun countDuplicateSuspects(persons: List<Person>): Int {
        var count = 0
        val checked = mutableSetOf<String>()
        for (i in persons.indices) {
            for (j in i + 1 until persons.size) {
                val key = "${persons[i].id}-${persons[j].id}"
                if (checked.contains(key)) continue
                checked.add(key)
                val nameA = "${persons[i].firstName} ${persons[i].lastName}".lowercase().trim()
                val nameB = "${persons[j].firstName} ${persons[j].lastName}".lowercase().trim()
                if (nameA == nameB || levenshteinSimilarity(nameA, nameB) >= 0.85) {
                    count++
                }
            }
        }
        return count
    }

    private fun levenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val maxLen = maxOf(s1.length, s2.length)
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i-1] == s2[j-1]) 0 else 1
                dp[i][j] = minOf(dp[i-1][j]+1, dp[i][j-1]+1, dp[i-1][j-1]+cost)
            }
        }
        return 1.0 - (dp[s1.length][s2.length].toDouble() / maxLen)
    }

    private fun calculateMaxGenerations(persons: List<Person>): Int {
        val personMap = persons.associateBy { it.id }
        var maxDepth = 0
        persons.forEach { person ->
            var depth = 0
            var current = person
            val visited = mutableSetOf<String>()
            while ((current.fatherId != null || current.motherId != null) && !visited.contains(current.id)) {
                visited.add(current.id)
                depth++
                val parentId = current.fatherId ?: current.motherId ?: break
                current = personMap[parentId] ?: break
            }
            if (depth > maxDepth) maxDepth = depth
        }
        return maxDepth + 1
    }
}
