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
        val spouseTargetIds = allPersons.mapNotNull { it.spouseId?.takeIf { s -> s.isNotBlank() } }.toSet()
        val orphans = allPersons.count {
            it.fatherId.isNullOrBlank() &&
                it.motherId.isNullOrBlank() &&
                it.id !in childIds &&
                it.spouseId.isNullOrBlank() &&
                it.id !in spouseTargetIds
        }

        // Missing birth dates: -1 per member, max -15
        val missingBirth = total - withBirth
        if (missingBirth > 0) {
            val penalty = (missingBirth * 1).coerceAtMost(15)
            score -= penalty
            deductions.add("$missingBirth member(s) missing birth dates (-$penalty)")
        }

        // Missing parents: only count members without parents who are neither founding ancestors nor married-in spouses
        val effectiveMissingParents = allPersons.count { p ->
            val hasNoParents = p.fatherId.isNullOrBlank() && p.motherId.isNullOrBlank()
            if (!hasNoParents) return@count false
            val isFoundingAncestor = p.id in childIds
            val isMarriedInSpouse = (!p.spouseId.isNullOrBlank() || p.id in spouseTargetIds) &&
                allPersons.any { sp ->
                    (sp.id == p.spouseId || sp.spouseId == p.id) &&
                        (!sp.fatherId.isNullOrBlank() || !sp.motherId.isNullOrBlank() || sp.id in childIds)
                }
            !isFoundingAncestor && !isMarriedInSpouse
        }
        if (effectiveMissingParents > 0) {
            val penalty = (effectiveMissingParents * 1).coerceAtMost(15)
            score -= penalty
            deductions.add("$effectiveMissingParents member(s) missing parent links (-$penalty)")
        }

        // Orphans (no parents, no children pointing to them, and no spouse): -2 each, max -10
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
                val pA = persons[i]
                val pB = persons[j]
                val key = "${pA.id}-${pB.id}"
                if (checked.contains(key)) continue
                checked.add(key)

                val firstA = pA.firstName.lowercase().trim()
                val firstB = pB.firstName.lowercase().trim()
                val lastA = pA.lastName.lowercase().trim()
                val lastB = pB.lastName.lowercase().trim()
                if (firstA.isEmpty() || firstB.isEmpty()) continue

                // If genders are both known and different (e.g. "Maria" vs "Mario"), they are not duplicates
                if (pA.gender.isNotBlank() && pB.gender.isNotBlank() && !pA.gender.equals(pB.gender, ignoreCase = true)) {
                    continue
                }

                // If names have distinct numeric or Roman numeral suffixes (e.g. "Juan II" vs "Juan III" or "Member 1" vs "Member 2"), not duplicates
                val numA = Regex("""(\d+|\b(?:ii|iii|iv|jr|sr)\b)$""").find(firstA)?.value
                val numB = Regex("""(\d+|\b(?:ii|iii|iv|jr|sr)\b)$""").find(firstB)?.value
                if (numA != null && numB != null && numA != numB) {
                    continue
                }

                // Compare first names and last names separately so a long shared surname doesn't inflate similarity
                val firstSim = levenshteinSimilarity(firstA, firstB)
                val lastMatch = lastA == lastB || levenshteinSimilarity(lastA, lastB) >= 0.85
                val yearA = FamilyLinkValidator.extractYear(pA.birthDate)
                val yearB = FamilyLinkValidator.extractYear(pB.birthDate)
                val closeBirthYear = yearA == null || yearB == null || kotlin.math.abs(yearA - yearB) <= 3

                if (lastMatch && firstSim >= 0.88 && closeBirthYear) {
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
        if (persons.isEmpty()) return 0
        val personMap = persons.associateBy { it.id }
        val memo = mutableMapOf<String, Int>()

        fun depthOf(personId: String, visiting: MutableSet<String>): Int {
            memo[personId]?.let { return it }
            if (!visiting.add(personId)) return 0 // cycle guard
            val p = personMap[personId] ?: run {
                visiting.remove(personId)
                return 0
            }
            val fatherDepth = p.fatherId?.takeIf { it.isNotBlank() && personMap.containsKey(it) }?.let {
                1 + depthOf(it, visiting)
            } ?: 0
            val motherDepth = p.motherId?.takeIf { it.isNotBlank() && personMap.containsKey(it) }?.let {
                1 + depthOf(it, visiting)
            } ?: 0
            visiting.remove(personId)
            val best = maxOf(fatherDepth, motherDepth)
            memo[personId] = best
            return best
        }

        var maxDepth = 0
        persons.forEach { person ->
            val d = depthOf(person.id, mutableSetOf())
            if (d > maxDepth) maxDepth = d
        }
        return maxDepth + 1
    }
}
