package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * TimelineGenerator — Builds a chronological event list for a person.
 */
class TimelineGenerator {

    data class TimelineEvent(
        val year: String,
        val icon: String,
        val description: String,
        val sortKey: Int  // for ordering
    )

    fun generate(
        person: Person,
        allPersons: List<Person>,
        children: List<Person> = emptyList()
    ): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()

        // Born
        if (person.birthDate.isNotEmpty()) {
            val year = person.birthDate.take(4)
            val desc = buildString {
                append("Born · ${person.gender}")
                if (person.birthPlace.isNotBlank()) append(" in ${person.birthPlace}")
            }
            events.add(TimelineEvent(year, "🎂", desc, yearToInt(year)))
        }

        // Father
        val father = allPersons.firstOrNull { it.id == person.fatherId }
        if (father != null) {
            val year = person.birthDate.take(4).ifEmpty { "?" }
            events.add(TimelineEvent(year, "👨", "Father: ${father.firstName} ${father.lastName}", yearToInt(year)))
        }

        // Mother
        val mother = allPersons.firstOrNull { it.id == person.motherId }
        if (mother != null) {
            val year = person.birthDate.take(4).ifEmpty { "?" }
            events.add(TimelineEvent(year, "👩", "Mother: ${mother.firstName} ${mother.lastName}", yearToInt(year)))
        }

        // Marriage
        if (person.spouseId != null && person.marriageDate.isNotEmpty()) {
            val spouse = allPersons.firstOrNull { it.id == person.spouseId }
            val spouseName = spouse?.let { "${it.firstName} ${it.lastName}" } ?: "Spouse"
            val year = person.marriageDate.take(4)
            events.add(TimelineEvent(year, "💍", "Married $spouseName", yearToInt(year)))
        }

        // Children born
        children.filter { it.birthDate.isNotEmpty() }.forEach { child ->
            val year = child.birthDate.take(4)
            events.add(TimelineEvent(year, "👶", "Child born: ${child.firstName} ${child.lastName}", yearToInt(year)))
        }

        // Life status
        if (person.maritalStatus == "Widowed") {
            events.add(TimelineEvent("—", "🕊", "Widowed", 9998))
        }
        if (person.maritalStatus == "Separated") {
            events.add(TimelineEvent("—", "💔", "Separated", 9998))
        }
        if (!person.isLiving) {
            val dYear = person.deathDate.take(4).ifEmpty { "—" }
            val desc = buildString {
                append("Passed away")
                if (person.deathPlace.isNotBlank()) append(" in ${person.deathPlace}")
            }
            val key = if (dYear != "—") yearToInt(dYear) else 9999
            events.add(TimelineEvent(dYear, "✝", desc, key))
        }

        return events.sortedBy { it.sortKey }
    }

    private fun yearToInt(year: String): Int {
        return try { year.toInt() } catch (e: Exception) { 9997 }
    }
}
