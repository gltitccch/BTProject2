package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * ProfileCompletenessChecker — Calculates how complete a person's profile data is.
 * Returns a percentage score and list of missing fields.
 */
class ProfileCompletenessChecker {

    data class CompletenessResult(
        val score: Int,           // 0-100
        val missingFields: List<String>,
        val label: String         // "Complete", "Good", "Incomplete", "Minimal"
    )

    fun check(person: Person): CompletenessResult {
        val missing = mutableListOf<String>()
        var points = 0
        val maxPoints = 100

        // First name (15 pts)
        if (person.firstName.isNotEmpty()) points += 15
        else missing.add("First name")

        // Last name (15 pts)
        if (person.lastName.isNotEmpty()) points += 15
        else missing.add("Last name")

        // Gender (10 pts)
        if (person.gender.isNotEmpty()) points += 10
        else missing.add("Gender")

        // Birth date (15 pts)
        if (person.birthDate.isNotEmpty()) points += 15
        else missing.add("Birth date")

        // Father linked (10 pts)
        if (person.fatherId != null) points += 10
        else missing.add("Father")

        // Mother linked (10 pts)
        if (person.motherId != null) points += 10
        else missing.add("Mother")

        // Spouse or marital status (10 pts)
        if (person.spouseId != null || person.maritalStatus != "Single") points += 10
        else missing.add("Marital info")

        // Biography (5 pts)
        if (person.biography.isNotEmpty()) points += 5
        else missing.add("Biography")

        // Marriage date if married (5 pts)
        if (person.maritalStatus == "Married") {
            if (person.marriageDate.isNotEmpty()) points += 5
            else missing.add("Marriage date")
        } else {
            points += 5 // not applicable = full score
        }

        // Living status set (5 pts — always set by default, so free points)
        points += 5

        val score = (points * 100) / maxPoints
        val label = when {
            score >= 90 -> "Complete"
            score >= 70 -> "Good"
            score >= 40 -> "Incomplete"
            else -> "Minimal"
        }

        return CompletenessResult(score, missing, label)
    }

    fun checkAll(persons: List<Person>): Int {
        if (persons.isEmpty()) return 0
        return persons.map { check(it).score }.average().toInt()
    }
}
