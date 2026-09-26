package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * ConflictScanner — Scans entire tree for inconsistencies and broken links.
 */
class ConflictScanner {

    data class Conflict(
        val type: String,      // "broken_link", "non_reciprocal", "cycle", "age_conflict", "status_mismatch"
        val personId: String,
        val personName: String,
        val description: String,
        val severity: String   // "error" or "warning"
    )

    fun scanAll(allPersons: List<Person>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        val personMap = allPersons.associateBy { it.id }

        allPersons.forEach { person ->
            val name = "${person.firstName} ${person.lastName}"

            // Broken parent links
            person.fatherId?.let { fid ->
                if (FamilyLinkValidator.findPersonInList(fid, allPersons) == null) {
                    conflicts.add(Conflict("broken_link", person.id, name,
                        "Father ID references a non-existent person.", "error"))
                }
            }
            person.motherId?.let { mid ->
                if (FamilyLinkValidator.findPersonInList(mid, allPersons) == null) {
                    conflicts.add(Conflict("broken_link", person.id, name,
                        "Mother ID references a non-existent person.", "error"))
                }
            }

            // Broken spouse link
            person.spouseId?.let { sid ->
                if (FamilyLinkValidator.findPersonInList(sid, allPersons) == null) {
                    conflicts.add(Conflict("broken_link", person.id, name,
                        "Spouse ID references a non-existent person.", "error"))
                }
            }

            // Non-reciprocal spouse
            person.spouseId?.let { sid ->
                val spouse = FamilyLinkValidator.findPersonInList(sid, allPersons)
                if (spouse != null && !FamilyLinkValidator.isSameId(spouse.spouseId, person.id)) {
                    conflicts.add(Conflict("non_reciprocal", person.id, name,
                        "Lists ${spouse.firstName} ${spouse.lastName} as spouse, but the link is not reciprocal.",
                        "warning"))
                }
            }

            // Self-reference
            if (person.fatherId == person.id) {
                conflicts.add(Conflict("cycle", person.id, name, "Is set as their own father.", "error"))
            }
            if (person.motherId == person.id) {
                conflicts.add(Conflict("cycle", person.id, name, "Is set as their own mother.", "error"))
            }
            if (person.spouseId == person.id) {
                conflicts.add(Conflict("cycle", person.id, name, "Is set as their own spouse.", "error"))
            }

            // Same father and mother
            if (person.fatherId != null && person.fatherId == person.motherId) {
                conflicts.add(Conflict("cycle", person.id, name,
                    "Has the same person set as both father and mother.", "error"))
            }

            // Parent younger than child
            person.fatherId?.let { fid ->
                val father = FamilyLinkValidator.findPersonInList(fid, allPersons)
                if (father != null && father.birthDate.isNotEmpty() && person.birthDate.isNotEmpty()) {
                    val gap = yearsBetween(father.birthDate, person.birthDate)
                    if (gap < 0) {
                        conflicts.add(Conflict("age_conflict", person.id, name,
                            "Is older than assigned father ${father.firstName}.", "error"))
                    }
                }
            }
            person.motherId?.let { mid ->
                val mother = FamilyLinkValidator.findPersonInList(mid, allPersons)
                if (mother != null && mother.birthDate.isNotEmpty() && person.birthDate.isNotEmpty()) {
                    val gap = yearsBetween(mother.birthDate, person.birthDate)
                    if (gap < 0) {
                        conflicts.add(Conflict("age_conflict", person.id, name,
                            "Is older than assigned mother ${mother.firstName}.", "error"))
                    }
                }
            }

            // Status mismatch
            if (person.maritalStatus == "Married" && person.spouseId == null) {
                conflicts.add(Conflict("status_mismatch", person.id, name,
                    "Status is Married but no spouse is linked.", "warning"))
            }
            if (person.maritalStatus == "Single" && person.spouseId != null) {
                conflicts.add(Conflict("status_mismatch", person.id, name,
                    "Status is Single but has a spouse linked.", "warning"))
            }

            // Incestuous / Prohibited Co-Parents validation
            if (!person.fatherId.isNullOrEmpty() && !person.motherId.isNullOrEmpty()) {
                val father = FamilyLinkValidator.findPersonInList(person.fatherId, allPersons)
                val mother = FamilyLinkValidator.findPersonInList(person.motherId, allPersons)
                if (father != null && mother != null) {
                    val coParentCheck = FamilyLinkValidator.validateCoParents(father, mother, allPersons)
                    if (!coParentCheck.isValid) {
                        conflicts.add(Conflict("prohibited_coparents", person.id, name,
                            "Co-parents ${father.firstName} and ${mother.firstName} violate relationship rules: ${coParentCheck.errors.firstOrNull()?.message}", "error"))
                    }
                }
            }

            // Prohibited Spouse validation
            person.spouseId?.let { sid ->
                val spouse = FamilyLinkValidator.findPersonInList(sid, allPersons)
                if (spouse != null) {
                    val sCheck = FamilyLinkValidator.validateSpouse(person, spouse, allPersons)
                    if (!sCheck.isValid) {
                        conflicts.add(Conflict("prohibited_spouse", person.id, name,
                            "Marriage with ${spouse.firstName} is prohibited: ${sCheck.errors.firstOrNull()?.message}", "error"))
                    }
                }
            }

            // Parent-Child validity check
            person.fatherId?.let { fid ->
                val father = FamilyLinkValidator.findPersonInList(fid, allPersons)
                if (father != null) {
                    val fCheck = FamilyLinkValidator.validateParentChild(parent = father, child = person, role = "father", allPersons = allPersons)
                    if (!fCheck.isValid) {
                        conflicts.add(Conflict("invalid_parent_child", person.id, name,
                            "Father link to ${father.firstName} invalid: ${fCheck.errors.firstOrNull()?.message}", "error"))
                    }
                }
            }
            person.motherId?.let { mid ->
                val mother = FamilyLinkValidator.findPersonInList(mid, allPersons)
                if (mother != null) {
                    val mCheck = FamilyLinkValidator.validateParentChild(parent = mother, child = person, role = "mother", allPersons = allPersons)
                    if (!mCheck.isValid) {
                        conflicts.add(Conflict("invalid_parent_child", person.id, name,
                            "Mother link to ${mother.firstName} invalid: ${mCheck.errors.firstOrNull()?.message}", "error"))
                    }
                }
            }
        }

        return conflicts.distinctBy { "${it.personId}-${it.type}-${it.description}" }
    }

    private fun yearsBetween(earlier: String, later: String): Int {
        return try { later.take(4).toInt() - earlier.take(4).toInt() } catch (e: Exception) { 99 }
    }
}
