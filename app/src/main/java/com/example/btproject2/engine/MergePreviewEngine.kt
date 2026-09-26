package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * MergePreviewEngine — Shows what would happen if two members are merged.
 * Detects conflicts and lets user choose correct values.
 */
class MergePreviewEngine {

    data class FieldConflict(
        val fieldName: String,
        val valueA: String,
        val valueB: String
    )

    data class MergePreview(
        val personA: Person,
        val personB: Person,
        val matchingFields: List<String>,
        val conflicts: List<FieldConflict>,
        val affectedChildren: List<Person>,    // children that reference personB
        val mergedPerson: Person               // suggested merged result (using A as base)
    )

    fun preview(personA: Person, personB: Person, allPersons: List<Person>): MergePreview {
        val matching = mutableListOf<String>()
        val conflicts = mutableListOf<FieldConflict>()

        // Compare fields
        compareField("First Name", personA.firstName, personB.firstName, matching, conflicts)
        compareField("Middle Name", personA.middleName, personB.middleName, matching, conflicts)
        compareField("Last Name", personA.lastName, personB.lastName, matching, conflicts)
        compareField("Suffix", personA.suffix, personB.suffix, matching, conflicts)
        compareField("Gender", personA.gender, personB.gender, matching, conflicts)
        compareField("Birth Date", personA.birthDate, personB.birthDate, matching, conflicts)
        compareField("Birth Place", personA.birthPlace, personB.birthPlace, matching, conflicts)
        compareField("Living Status", if (personA.isLiving) "Living" else "Deceased", if (personB.isLiving) "Living" else "Deceased", matching, conflicts)
        compareField("Marital Status", personA.maritalStatus, personB.maritalStatus, matching, conflicts)
        compareField("Father", personA.fatherId ?: "", personB.fatherId ?: "", matching, conflicts)
        compareField("Mother", personA.motherId ?: "", personB.motherId ?: "", matching, conflicts)
        compareField("Spouse", personA.spouseId ?: "", personB.spouseId ?: "", matching, conflicts)

        // Find children that reference personB
        val affectedChildren = allPersons.filter {
            it.fatherId == personB.id || it.motherId == personB.id
        }

        // Build merged person (prefer A's data, fill gaps from B)
        val merged = Person(
            id = personA.id,
            firstName = personA.firstName.ifEmpty { personB.firstName },
            middleName = personA.middleName.ifEmpty { personB.middleName },
            lastName = personA.lastName.ifEmpty { personB.lastName },
            suffix = personA.suffix.ifEmpty { personB.suffix },
            gender = personA.gender.ifEmpty { personB.gender },
            birthDate = personA.birthDate.ifEmpty { personB.birthDate },
            birthPlace = personA.birthPlace.ifEmpty { personB.birthPlace },
            motherId = personA.motherId ?: personB.motherId,
            motherRelationshipType = personA.motherRelationshipType.ifEmpty { personB.motherRelationshipType },
            fatherId = personA.fatherId ?: personB.fatherId,
            fatherRelationshipType = personA.fatherRelationshipType.ifEmpty { personB.fatherRelationshipType },
            spouseId = personA.spouseId ?: personB.spouseId,
            treeId = personA.treeId.ifEmpty { personB.treeId },
            createdBy = personA.createdBy.ifEmpty { personB.createdBy },
            createdAt = minOf(personA.createdAt, personB.createdAt),
            maritalStatus = personA.maritalStatus.ifEmpty { personB.maritalStatus },
            isLiving = personA.isLiving && personB.isLiving,
            deathDate = personA.deathDate.ifEmpty { personB.deathDate },
            deathPlace = personA.deathPlace.ifEmpty { personB.deathPlace },
            biography = personA.biography.ifEmpty { personB.biography },
            marriageDate = personA.marriageDate.ifEmpty { personB.marriageDate }
        )

        return MergePreview(personA, personB, matching, conflicts, affectedChildren, merged)
    }

    /**
     * Constructs a merged Person applying user-selected overrides for conflicts.
     */
    fun buildCustomMergedPerson(
        personA: Person,
        personB: Person,
        overrides: Map<String, String> // e.g. "Birth Date" -> "1985-05-12"
    ): Person {
        return Person(
            id = personA.id,
            firstName = overrides["First Name"] ?: personA.firstName.ifEmpty { personB.firstName },
            middleName = overrides["Middle Name"] ?: personA.middleName.ifEmpty { personB.middleName },
            lastName = overrides["Last Name"] ?: personA.lastName.ifEmpty { personB.lastName },
            suffix = overrides["Suffix"] ?: personA.suffix.ifEmpty { personB.suffix },
            gender = overrides["Gender"] ?: personA.gender.ifEmpty { personB.gender },
            birthDate = overrides["Birth Date"] ?: personA.birthDate.ifEmpty { personB.birthDate },
            birthPlace = overrides["Birth Place"] ?: personA.birthPlace.ifEmpty { personB.birthPlace },
            motherId = overrides["Mother"] ?: (personA.motherId ?: personB.motherId),
            fatherId = overrides["Father"] ?: (personA.fatherId ?: personB.fatherId),
            spouseId = overrides["Spouse"] ?: (personA.spouseId ?: personB.spouseId),
            treeId = personA.treeId.ifEmpty { personB.treeId },
            createdBy = personA.createdBy.ifEmpty { personB.createdBy },
            createdAt = minOf(personA.createdAt, personB.createdAt),
            maritalStatus = overrides["Marital Status"] ?: personA.maritalStatus.ifEmpty { personB.maritalStatus },
            isLiving = when (overrides["Living Status"]) {
                "Living" -> true
                "Deceased" -> false
                else -> personA.isLiving && personB.isLiving
            },
            deathDate = personA.deathDate.ifEmpty { personB.deathDate },
            deathPlace = personA.deathPlace.ifEmpty { personB.deathPlace },
            biography = personA.biography.ifEmpty { personB.biography },
            marriageDate = personA.marriageDate.ifEmpty { personB.marriageDate }
        )
    }

    /**
     * Biological cycle / loop detection (FR-10 / FR-08).
     * Returns true if making proposedParentId a parent of proposedChildId creates a circular loop.
     * (i.e. proposedChildId is already an ancestor of proposedParentId).
     */
    fun detectCycle(
        proposedParentId: String,
        proposedChildId: String,
        allPersonsMap: Map<String, Person>
    ): Boolean {
        if (proposedParentId.isBlank() || proposedChildId.isBlank()) return false
        if (proposedParentId == proposedChildId) return true

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(proposedParentId)

        while (queue.isNotEmpty()) {
            val currId = queue.removeFirst()
            if (currId == proposedChildId) return true
            if (visited.add(currId)) {
                val p = allPersonsMap[currId] ?: continue
                p.fatherId?.let { if (it.isNotEmpty() && !visited.contains(it)) queue.add(it) }
                p.motherId?.let { if (it.isNotEmpty() && !visited.contains(it)) queue.add(it) }
            }
        }
        return false
    }

    private fun compareField(
        name: String,
        valueA: String,
        valueB: String,
        matching: MutableList<String>,
        conflicts: MutableList<FieldConflict>
    ) {
        when {
            valueA == valueB -> if (valueA.isNotEmpty()) matching.add(name)
            valueA.isEmpty() || valueB.isEmpty() -> {} // one is empty, not a conflict
            else -> conflicts.add(FieldConflict(name, valueA, valueB))
        }
    }
}
