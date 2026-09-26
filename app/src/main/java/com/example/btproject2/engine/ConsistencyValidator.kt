package com.example.btproject2.engine

import com.example.btproject2.models.Person
import java.util.Calendar

/**
 * ConsistencyValidator — Central rule engine for all genealogical data validation.
 *
 * Validates relationships before saving to prevent impossible family structures.
 * Returns ValidationResult with HARD errors (block save) and SOFT warnings (allow save but notify).
 */
class ConsistencyValidator {

    // ══════════════════════════════════════════════════════════════
    // RESULT TYPES
    // ══════════════════════════════════════════════════════════════

    enum class Severity { ERROR, WARNING }

    data class ValidationIssue(
        val severity: Severity,
        val message: String,
        val field: String = ""
    )

    data class ValidationResult(
        val issues: List<ValidationIssue>
    ) {
        val isValid: Boolean get() = issues.none { it.severity == Severity.ERROR }
        val hasWarnings: Boolean get() = issues.any { it.severity == Severity.WARNING }
        val errors: List<ValidationIssue> get() = issues.filter { it.severity == Severity.ERROR }
        val warnings: List<ValidationIssue> get() = issues.filter { it.severity == Severity.WARNING }
    }

    // ══════════════════════════════════════════════════════════════
    // MAIN VALIDATION — RUN BEFORE ADDING/EDITING A MEMBER
    // ══════════════════════════════════════════════════════════════

    fun validatePerson(
        person: Person,
        allPersons: List<Person>,
        isEditing: Boolean = false
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val personMap = allPersons.associateBy { it.id }

        // 1. Self-relationship checks
        validateSelfRelationship(person, issues)

        // 2. Same father and mother check
        validateSameParents(person, issues)

        // 3. Parent-child age validation
        validateParentChildAge(person, personMap, issues)

        // 4. Circular ancestry detection
        validateCircularAncestry(person, personMap, issues)

        // 5. Spouse validation
        validateSpouse(person, personMap, allPersons, issues)

        // 6. Marriage date validation
        validateMarriageDate(person, issues)

        // 7. Profile completeness warnings
        validateCompleteness(person, issues)

        // 8. Civil status consistency
        validateCivilStatusConsistency(person, issues)

        // 9. Consanguinity, biological age gap, and co-parent incest validation
        val linkResult = FamilyLinkValidator.validatePersonComprehensive(person, allPersons)
        for (error in linkResult.errors) {
            issues.add(ValidationIssue(Severity.ERROR, error.message, error.field))
        }
        for (warning in linkResult.warnings) {
            issues.add(ValidationIssue(Severity.WARNING, warning.message, warning.field))
        }

        return ValidationResult(issues)
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 1: SELF-RELATIONSHIP
    // ══════════════════════════════════════════════════════════════

    private fun validateSelfRelationship(person: Person, issues: MutableList<ValidationIssue>) {
        if (person.id.isNotEmpty()) {
            if (person.fatherId == person.id) {
                issues.add(ValidationIssue(Severity.ERROR, "A person cannot be their own father.", "fatherId"))
            }
            if (person.motherId == person.id) {
                issues.add(ValidationIssue(Severity.ERROR, "A person cannot be their own mother.", "motherId"))
            }
            if (person.spouseId == person.id) {
                issues.add(ValidationIssue(Severity.ERROR, "A person cannot be their own spouse.", "spouseId"))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 2: SAME FATHER AND MOTHER
    // ══════════════════════════════════════════════════════════════

    private fun validateSameParents(person: Person, issues: MutableList<ValidationIssue>) {
        if (person.fatherId != null && person.motherId != null && person.fatherId == person.motherId) {
            issues.add(ValidationIssue(Severity.ERROR, "Father and mother cannot be the same person.", "parents"))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 3: PARENT-CHILD AGE VALIDATION
    // ══════════════════════════════════════════════════════════════

    private fun validateParentChildAge(
        person: Person,
        personMap: Map<String, Person>,
        issues: MutableList<ValidationIssue>
    ) {
        if (person.birthDate.isEmpty()) return

        // Check father
        person.fatherId?.let { fatherId ->
            val father = personMap[fatherId]
            if (father != null && father.birthDate.isNotEmpty()) {
                val ageAtBirth = yearsBetween(father.birthDate, person.birthDate)

                if (ageAtBirth < 0) {
                    issues.add(ValidationIssue(Severity.ERROR,
                        "Child cannot be born before father (${father.firstName} ${father.lastName}).",
                        "fatherId"))
                } else if (ageAtBirth < 12) {
                    issues.add(ValidationIssue(Severity.ERROR,
                        "Father ${father.firstName} would be only $ageAtBirth at childbirth — biologically impossible.",
                        "fatherId"))
                } else if (ageAtBirth < 16) {
                    issues.add(ValidationIssue(Severity.WARNING,
                        "Father ${father.firstName} would be only $ageAtBirth at childbirth — unusually young.",
                        "fatherId"))
                } else if (ageAtBirth > 80) {
                    issues.add(ValidationIssue(Severity.WARNING,
                        "Father ${father.firstName} would be $ageAtBirth at childbirth — unusually old.",
                        "fatherId"))
                }
            }
        }

        // Check mother
        person.motherId?.let { motherId ->
            val mother = personMap[motherId]
            if (mother != null && mother.birthDate.isNotEmpty()) {
                val ageAtBirth = yearsBetween(mother.birthDate, person.birthDate)

                if (ageAtBirth < 0) {
                    issues.add(ValidationIssue(Severity.ERROR,
                        "Child cannot be born before mother (${mother.firstName} ${mother.lastName}).",
                        "motherId"))
                } else if (ageAtBirth < 12) {
                    issues.add(ValidationIssue(Severity.ERROR,
                        "Mother ${mother.firstName} would be only $ageAtBirth at childbirth — biologically impossible.",
                        "motherId"))
                } else if (ageAtBirth < 16) {
                    issues.add(ValidationIssue(Severity.WARNING,
                        "Mother ${mother.firstName} would be only $ageAtBirth at childbirth — unusually young.",
                        "motherId"))
                } else if (ageAtBirth > 55) {
                    issues.add(ValidationIssue(Severity.WARNING,
                        "Mother ${mother.firstName} would be $ageAtBirth at childbirth — unusually old.",
                        "motherId"))
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 4: CIRCULAR ANCESTRY DETECTION
    // ══════════════════════════════════════════════════════════════

    private fun validateCircularAncestry(
        person: Person,
        personMap: Map<String, Person>,
        issues: MutableList<ValidationIssue>
    ) {
        // Check if assigning this person's parents would create a cycle
        person.fatherId?.let { fatherId ->
            if (isAncestorOf(person.id, fatherId, personMap)) {
                issues.add(ValidationIssue(Severity.ERROR,
                    "Circular ancestry detected: this person is already an ancestor of the assigned father.",
                    "fatherId"))
            }
        }

        person.motherId?.let { motherId ->
            if (isAncestorOf(person.id, motherId, personMap)) {
                issues.add(ValidationIssue(Severity.ERROR,
                    "Circular ancestry detected: this person is already an ancestor of the assigned mother.",
                    "motherId"))
            }
        }
    }

    /**
     * Returns true if [possibleAncestorId] is an ancestor of [personId].
     * Uses BFS to walk up the tree from [personId].
     */
    private fun isAncestorOf(possibleAncestorId: String, personId: String, personMap: Map<String, Person>): Boolean {
        if (possibleAncestorId.isEmpty() || personId.isEmpty()) return false

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(personId)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (currentId == possibleAncestorId) return true
            if (visited.contains(currentId)) continue
            visited.add(currentId)

            val current = personMap[currentId] ?: continue
            current.fatherId?.let { if (!visited.contains(it)) queue.add(it) }
            current.motherId?.let { if (!visited.contains(it)) queue.add(it) }
        }

        return false
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 5: SPOUSE VALIDATION
    // ══════════════════════════════════════════════════════════════

    private fun validateSpouse(
        person: Person,
        personMap: Map<String, Person>,
        allPersons: List<Person>,
        issues: MutableList<ValidationIssue>
    ) {
        val spouseId = person.spouseId ?: return
        val spouse = personMap[spouseId] ?: return

        // Spouse cannot be parent
        if (spouseId == person.fatherId || spouseId == person.motherId) {
            issues.add(ValidationIssue(Severity.ERROR,
                "Cannot marry a parent (${spouse.firstName} ${spouse.lastName}).",
                "spouseId"))
        }

        // Spouse cannot be child
        val isChild = allPersons.any { it.fatherId == person.id && it.id == spouseId } ||
                allPersons.any { it.motherId == person.id && it.id == spouseId }
        if (isChild) {
            issues.add(ValidationIssue(Severity.ERROR,
                "Cannot marry a child (${spouse.firstName} ${spouse.lastName}).",
                "spouseId"))
        }

        // Spouse cannot be ancestor
        if (person.id.isNotEmpty() && isAncestorOf(spouseId, person.id, personMap)) {
            issues.add(ValidationIssue(Severity.ERROR,
                "Cannot marry an ancestor (${spouse.firstName} ${spouse.lastName}).",
                "spouseId"))
        }

        // Spouse cannot be descendant
        if (person.id.isNotEmpty() && isAncestorOf(person.id, spouseId, personMap)) {
            issues.add(ValidationIssue(Severity.ERROR,
                "Cannot marry a descendant (${spouse.firstName} ${spouse.lastName}).",
                "spouseId"))
        }

        // Spouse age gap warning
        if (person.birthDate.isNotEmpty() && spouse.birthDate.isNotEmpty()) {
            val gap = Math.abs(yearsBetween(person.birthDate, spouse.birthDate))
            if (gap > 40) {
                issues.add(ValidationIssue(Severity.WARNING,
                    "Large age gap of $gap years with spouse ${spouse.firstName} ${spouse.lastName}.",
                    "spouseId"))
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 6: MARRIAGE DATE VALIDATION
    // ══════════════════════════════════════════════════════════════

    private fun validateMarriageDate(person: Person, issues: MutableList<ValidationIssue>) {
        if (person.marriageDate.isEmpty() || person.birthDate.isEmpty()) return

        val ageAtMarriage = yearsBetween(person.birthDate, person.marriageDate)

        if (ageAtMarriage < 0) {
            issues.add(ValidationIssue(Severity.ERROR,
                "Marriage date cannot be before birth date.",
                "marriageDate"))
        } else if (ageAtMarriage < 18) {
            issues.add(ValidationIssue(Severity.WARNING,
                "Person would be only $ageAtMarriage at marriage — under legal age in most jurisdictions.",
                "marriageDate"))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 7: PROFILE COMPLETENESS
    // ══════════════════════════════════════════════════════════════

    private fun validateCompleteness(person: Person, issues: MutableList<ValidationIssue>) {
        if (person.birthDate.isEmpty()) {
            issues.add(ValidationIssue(Severity.WARNING, "Birth date is not set.", "birthDate"))
        }
        if (person.gender.isEmpty()) {
            issues.add(ValidationIssue(Severity.WARNING, "Gender is not set.", "gender"))
        }
        if (person.fatherId == null && person.motherId == null) {
            issues.add(ValidationIssue(Severity.WARNING, "No parents are linked.", "parents"))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RULE 8: CIVIL STATUS CONSISTENCY
    // ══════════════════════════════════════════════════════════════

    private fun validateCivilStatusConsistency(person: Person, issues: MutableList<ValidationIssue>) {
        if (person.maritalStatus == "Married" && person.spouseId == null) {
            issues.add(ValidationIssue(Severity.WARNING,
                "Status is 'Married' but no spouse is linked.",
                "maritalStatus"))
        }
        if (person.maritalStatus == "Single" && person.spouseId != null) {
            issues.add(ValidationIssue(Severity.WARNING,
                "Status is 'Single' but a spouse is linked.",
                "maritalStatus"))
        }
        if (person.marriageDate.isNotEmpty() && person.spouseId == null) {
            issues.add(ValidationIssue(Severity.WARNING,
                "Marriage date is set but no spouse is linked.",
                "marriageDate"))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SPOUSE PICKER FILTERING — excludes invalid candidates
    // ══════════════════════════════════════════════════════════════

    fun getEligibleSpouses(person: Person, allPersons: List<Person>): List<Person> {
        return FamilyLinkValidator.getEligibleSpouses(person, allPersons)
    }

    // ══════════════════════════════════════════════════════════════
    // CHILD PICKER FILTERING — excludes invalid candidates
    // ══════════════════════════════════════════════════════════════

    fun getEligibleChildren(person: Person, allPersons: List<Person>): List<Person> {
        return FamilyLinkValidator.getEligibleChildren(person, allPersons)
    }

    // ══════════════════════════════════════════════════════════════
    // PARENT PICKER FILTERING
    // ══════════════════════════════════════════════════════════════

    fun getEligibleFathers(person: Person, allPersons: List<Person>): List<Person> {
        val existingMother = FamilyLinkValidator.findPersonInList(person.motherId, allPersons)
        return FamilyLinkValidator.getEligibleFathers(person, existingMother, allPersons)
    }

    fun getEligibleMothers(person: Person, allPersons: List<Person>): List<Person> {
        val existingFather = FamilyLinkValidator.findPersonInList(person.fatherId, allPersons)
        return FamilyLinkValidator.getEligibleMothers(person, existingFather, allPersons)
    }

    // ══════════════════════════════════════════════════════════════
    // UTILITY
    // ══════════════════════════════════════════════════════════════

    private fun yearsBetween(earlier: String, later: String): Int {
        val y1 = FamilyLinkValidator.extractYear(earlier) ?: return 99
        val y2 = FamilyLinkValidator.extractYear(later) ?: return 99
        return y2 - y1
    }
}
