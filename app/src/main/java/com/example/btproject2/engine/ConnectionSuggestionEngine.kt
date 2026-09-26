package com.example.btproject2.engine

import com.example.btproject2.models.Person
import com.example.btproject2.utils.DuplicateDetector

/**
 * ConnectionSuggestionEngine — Suggests possible relationships and duplicates.
 */
class ConnectionSuggestionEngine {

    private val duplicateDetector = DuplicateDetector()

    data class Suggestion(
        val type: String,       // "possible_parent", "possible_sibling", "possible_duplicate", "missing_link"
        val personA: Person,
        val personB: Person,
        val reason: String,
        val confidence: Int     // 0-100
    )

    fun generateSuggestions(person: Person, allPersons: List<Person>): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        suggestPossibleParents(person, allPersons, suggestions)
        suggestPossibleSiblings(person, allPersons, suggestions)
        suggestDuplicates(person, allPersons, suggestions)
        suggestMissingLinks(person, allPersons, suggestions)

        return suggestions.sortedByDescending { it.confidence }
    }

    private fun suggestPossibleParents(
        person: Person,
        allPersons: List<Person>,
        suggestions: MutableList<Suggestion>
    ) {
        if (person.fatherId != null && person.motherId != null) return

        allPersons.filter { it.id != person.id }.forEach { candidate ->
            if (person.birthDate.isEmpty() || candidate.birthDate.isEmpty()) return@forEach

            val ageDiff = yearsBetween(candidate.birthDate, person.birthDate)
            if (ageDiff < 15 || ageDiff > 60) return@forEach

            // Same last name + right age = possible parent
            if (candidate.lastName.lowercase() == person.lastName.lowercase()) {
                val gender = candidate.gender.lowercase()
                if (gender == "male" && person.fatherId == null) {
                    suggestions.add(Suggestion(
                        "possible_parent", person, candidate,
                        "${candidate.firstName} ${candidate.lastName} shares the surname and is $ageDiff years older.",
                        70
                    ))
                } else if (gender == "female" && person.motherId == null) {
                    suggestions.add(Suggestion(
                        "possible_parent", person, candidate,
                        "${candidate.firstName} ${candidate.lastName} shares the surname and is $ageDiff years older.",
                        65
                    ))
                }
            }
        }
    }

    private fun suggestPossibleSiblings(
        person: Person,
        allPersons: List<Person>,
        suggestions: MutableList<Suggestion>
    ) {
        allPersons.filter { it.id != person.id }.forEach { candidate ->
            // Same parents = definitely siblings (already linked)
            if (person.fatherId != null && person.fatherId == candidate.fatherId) return@forEach
            if (person.motherId != null && person.motherId == candidate.motherId) return@forEach

            // Same last name + similar age = possible sibling
            if (candidate.lastName.lowercase() == person.lastName.lowercase()) {
                if (person.birthDate.isNotEmpty() && candidate.birthDate.isNotEmpty()) {
                    val ageDiff = Math.abs(yearsBetween(person.birthDate, candidate.birthDate))
                    if (ageDiff <= 15) {
                        suggestions.add(Suggestion(
                            "possible_sibling", person, candidate,
                            "${candidate.firstName} shares surname and is close in age ($ageDiff year gap).",
                            55
                        ))
                    }
                }
            }
        }
    }

    private fun suggestDuplicates(
        person: Person,
        allPersons: List<Person>,
        suggestions: MutableList<Suggestion>
    ) {
        val others = allPersons.filter { it.id != person.id }
        val duplicates = duplicateDetector.findPossibleDuplicates(person, others, 0.80)

        duplicates.forEach { dup ->
            var confidence = 60
            // Same birth date = higher confidence
            if (person.birthDate.isNotEmpty() && person.birthDate == dup.birthDate) confidence += 25
            // Same gender = slightly higher
            if (person.gender == dup.gender) confidence += 10

            suggestions.add(Suggestion(
                "possible_duplicate", person, dup,
                "${dup.firstName} ${dup.lastName} has a very similar name.",
                confidence.coerceAtMost(95)
            ))
        }
    }

    private fun suggestMissingLinks(
        person: Person,
        allPersons: List<Person>,
        suggestions: MutableList<Suggestion>
    ) {
        // Spouse linked but not reciprocal
        if (person.spouseId != null) {
            val spouse = allPersons.firstOrNull { it.id == person.spouseId }
            if (spouse != null && spouse.spouseId != person.id) {
                suggestions.add(Suggestion(
                    "missing_link", person, spouse,
                    "Spouse link is not reciprocal — ${spouse.firstName} does not list ${person.firstName} as spouse.",
                    90
                ))
            }
        }
    }

    private fun yearsBetween(earlier: String, later: String): Int {
        return try { later.take(4).toInt() - earlier.take(4).toInt() } catch (e: Exception) { 99 }
    }
}
