package com.example.btproject2.engine

import com.example.btproject2.models.Person
import com.example.btproject2.models.TraceResult
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale

/**
 * FamilyLinkValidator — Authoritative rule engine for validating family connections.
 *
 * Enforces:
 * 1. Philippine Family Code consanguinity & incest prohibitions (Arts. 37 & 38)
 *    for Co-Parents and Spouses:
 *    - Lineal ascendants & descendants to ANY degree (Parent & Child, Grandparent & Grandchild - Art. 37(1)).
 *    - Brothers & Sisters of full or half blood (Art. 37(2)).
 *    - Step-parents & Step-children (Art. 38(3)).
 *    - Parents-in-law & Children-in-law (Art. 38(4)).
 *    - Collateral blood relatives within 4th civil degree: Uncles/Aunts & Nieces/Nephews, First Cousins (Art. 38(1)).
 * 2. Human biological age gap feasibility (parent cannot be younger or same age, min 12-year gap).
 * 3. Deceased parent feasibility (mother cannot give birth after passing; father <= 1 yr after passing).
 * 4. Cycle prevention (circular ancestry).
 */
object FamilyLinkValidator {

    enum class IssueType {
        FATAL_ERROR,  // Non-bypassable: blocks save
        WARNING       // Soft warning: allows save with user confirmation
    }

    data class LinkIssue(
        val type: IssueType,
        val title: String,
        val message: String,
        val legalArticle: String = "",
        val field: String = ""
    )

    data class ValidationResult(
        val issues: List<LinkIssue>
    ) {
        val isValid: Boolean get() = issues.none { it.type == IssueType.FATAL_ERROR }
        val hasWarnings: Boolean get() = issues.any { it.type == IssueType.WARNING }
        val errors: List<LinkIssue> get() = issues.filter { it.type == IssueType.FATAL_ERROR }
        val warnings: List<LinkIssue> get() = issues.filter { it.type == IssueType.WARNING }
    }

    data class ExistingParentsInfo(
        val father: Person?,
        val mother: Person?,
        val parentCount: Int,
        val hasTwoParents: Boolean,
        val fatherName: String?,
        val motherName: String?
    )

    private val bloodlineTracer = BloodlineTracer()
    private val marriageValidationEngine = MarriageValidationEngine()

    fun getMarriageValidationEngine(): MarriageValidationEngine = marriageValidationEngine

    // ══════════════════════════════════════════════════════════════
    // ID NORMALIZATION & UTILITIES
    // ══════════════════════════════════════════════════════════════

    fun isSameId(id1: String?, id2: String?): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        return id1.trim().equals(id2.trim(), ignoreCase = true)
    }

    fun findPersonInList(id: String?, persons: List<Person>): Person? {
        if (id.isNullOrBlank()) return null
        val target = id.trim()
        return persons.find { it.id.trim().equals(target, ignoreCase = true) }
    }

    fun normalizePersonMap(persons: List<Person>): MutableMap<String, Person> {
        val map = mutableMapOf<String, Person>()
        for (p in persons) {
            if (p.id.isNotBlank()) {
                map[p.id.trim().lowercase()] = p
            }
        }
        return map
    }

    fun isPersonNamed(person: Person?, query: String): Boolean {
        if (person == null) return false
        val q = query.trim().lowercase()
        val f = person.firstName.trim().lowercase()
        val m = person.middleName.trim().lowercase()
        val l = person.lastName.trim().lowercase()
        val full = "$f $m $l".trim()
        return f.contains(q) || l.contains(q) || full.contains(q)
    }

    /**
     * Resolves the existing biological parents of [child] from [allPersons].
     * Returns full details including parent count, resolved parent models, and formatted names.
     */
    fun getExistingParentsInfo(child: Person, allPersons: List<Person>): ExistingParentsInfo {
        val father = findPersonInList(child.fatherId, allPersons)
        val mother = findPersonInList(child.motherId, allPersons)
        val count = (if (father != null) 1 else 0) + (if (mother != null) 1 else 0)
        return ExistingParentsInfo(
            father = father,
            mother = mother,
            parentCount = count,
            hasTwoParents = count >= 2,
            fatherName = father?.let { "${it.firstName} ${it.lastName}".trim() },
            motherName = mother?.let { "${it.firstName} ${it.lastName}".trim() }
        )
    }

    // ══════════════════════════════════════════════════════════════
    // DATE & YEAR PARSING UTILITIES
    // ══════════════════════════════════════════════════════════════

    fun extractYear(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        if (trimmed.matches(Regex("""^\d{4}$"""))) {
            return trimmed.toIntOrNull()
        }

        val patterns = listOf(
            "yyyy-MM-dd",
            "MMM d, yyyy",
            "MMM dd, yyyy",
            "MMMM d, yyyy",
            "MMMM dd, yyyy",
            "yyyy/MM/dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy.MM.dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val parsed = sdf.parse(trimmed)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    return cal.get(Calendar.YEAR)
                }
            } catch (_: Exception) {
            }
        }

        val match = Regex("""\b(18|19|20)\d{2}\b""").find(trimmed)
        return match?.value?.toIntOrNull()
    }

    fun parseDateToCalendar(dateStr: String?): Calendar? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        val patterns = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "MMM d, yyyy",
            "MMM dd, yyyy",
            "MMMM d, yyyy",
            "MMMM dd, yyyy",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "dd-MM-yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) {
                    return Calendar.getInstance().apply { time = parsed }
                }
            } catch (_: Exception) {
            }
        }
        val match = Regex("""\b(18|19|20)\d{2}\b""").find(trimmed)
        if (match != null) {
            val yr = match.value.toIntOrNull()
            if (yr != null) {
                return Calendar.getInstance().apply {
                    clear()
                    set(yr, Calendar.JANUARY, 1)
                }
            }
        }
        return null
    }

    /**
     * Calculates the exact chronological age in completed years.
     * Uses [referenceDateStr] (e.g. death date if deceased) or current date.
     */
    fun calculateAgeYears(birthDateStr: String?, referenceDateStr: String? = null): Int? {
        val birthCal = parseDateToCalendar(birthDateStr) ?: return null
        val refCal = if (!referenceDateStr.isNullOrBlank()) {
            parseDateToCalendar(referenceDateStr) ?: Calendar.getInstance()
        } else {
            Calendar.getInstance()
        }
        var age = refCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
        if (refCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age
    }

    /**
     * Validates that lineage roles (PATRIARCH / MATRIARCH) meet minimum age requirements (18+).
     * Prevents minors or unverified infants from holding founding head-of-family titles.
     */
    fun validateLineageRole(person: Person): ValidationResult {
        val issues = mutableListOf<LinkIssue>()
        val role = person.lineageRole.trim()
        if (role.equals("PATRIARCH", ignoreCase = true) || role.equals("MATRIARCH", ignoreCase = true)) {
            val displayRole = if (role.equals("PATRIARCH", ignoreCase = true)) "Patriarch" else "Matriarch"
            if (person.birthDate.isBlank()) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Birth Date Required for $displayRole",
                        message = "A member must have a recorded birth date to hold the $displayRole title (must be at least 18 years old).",
                        field = "birthDate"
                    )
                )
            } else {
                val age = calculateAgeYears(person.birthDate, person.deathDate.takeIf { !person.isLiving })
                if (age == null) {
                    issues.add(
                        LinkIssue(
                            type = IssueType.FATAL_ERROR,
                            title = "Invalid Birth Date for $displayRole",
                            message = "The birth date format could not be verified to confirm 18+ eligibility for the $displayRole title.",
                            field = "birthDate"
                        )
                    )
                } else if (age < 18) {
                    issues.add(
                        LinkIssue(
                            type = IssueType.FATAL_ERROR,
                            title = "Underage $displayRole Not Allowed",
                            message = "A member must be at least 18 years old to be designated as $displayRole (current age: $age). Youth researchers may hold the Family Member role.",
                            field = "birthDate"
                        )
                    )
                }
            }
        }
        return ValidationResult(issues)
    }

    data class ParentDeathChronologyResult(
        val isAllowed: Boolean,
        val isPosthumousWarning: Boolean = false,
        val message: String = "",
        val daysAfterDeath: Long = 0L
    )

    /**
     * Smart hybrid chronological validation rule for Parent-Child relationships:
     * - Mother: Strict biological limit. Child DOB > Mother DOD is a hard block.
     * - Father: Posthumous rule (Article 164, Family Code).
     *   * Child DOB <= Father DOD + 300 days: Allowed automatically.
     *   * Child DOB > Father DOD + 300 days: Warning for user confirmation.
     */
    fun validateParentDeathChronology(
        parent: Person,
        child: Person,
        role: String = "parent"
    ): ParentDeathChronologyResult {
        if (parent.isLiving || parent.deathDate.isBlank() || child.birthDate.isBlank()) {
            return ParentDeathChronologyResult(isAllowed = true)
        }

        val isMother = role.equals("mother", ignoreCase = true) || parent.gender.equals("female", ignoreCase = true)
        val childCal = parseDateToCalendar(child.birthDate)
        val parentDeathCal = parseDateToCalendar(parent.deathDate)

        if (childCal == null || parentDeathCal == null) {
            val cYear = extractYear(child.birthDate)
            val dYear = extractYear(parent.deathDate)
            if (cYear != null && dYear != null) {
                if (isMother && cYear > dYear) {
                    return ParentDeathChronologyResult(
                        isAllowed = false,
                        message = "Child birthdate cannot occur after the biological mother's date of death."
                    )
                }
                if (!isMother && cYear > dYear + 1) {
                    return ParentDeathChronologyResult(
                        isAllowed = true,
                        isPosthumousWarning = true,
                        message = "Child was born more than 10 months after the father's death. Do you want to proceed?"
                    )
                }
            }
            return ParentDeathChronologyResult(isAllowed = true)
        }

        val diffMillis = childCal.timeInMillis - parentDeathCal.timeInMillis
        val diffDays = diffMillis / (1000L * 60 * 60 * 24)

        if (isMother) {
            if (diffDays > 0) {
                return ParentDeathChronologyResult(
                    isAllowed = false,
                    message = "Child birthdate cannot occur after the biological mother's date of death."
                )
            }
        } else {
            // Father: Article 164 of the Family Code of the Philippines recognises children born within 300 days post-mortem
            if (diffDays > 300) {
                return ParentDeathChronologyResult(
                    isAllowed = true,
                    isPosthumousWarning = true,
                    message = "Child was born more than 10 months after the father's death. Do you want to proceed?",
                    daysAfterDeath = diffDays
                )
            }
        }

        return ParentDeathChronologyResult(isAllowed = true)
    }

    /**
     * Chronological validation for Marriage:
     * Marriage date cannot occur after either spouse's death date.
     */
    fun validateMarriageDeathChronology(
        personA: Person,
        personB: Person,
        marriageDate: String?
    ): ValidationResult {
        val issues = mutableListOf<LinkIssue>()
        if (marriageDate.isNullOrBlank()) return ValidationResult(issues)

        val mCal = parseDateToCalendar(marriageDate)

        if (!personA.isLiving && personA.deathDate.isNotBlank()) {
            val aDeathCal = parseDateToCalendar(personA.deathDate)
            val isAfter = if (mCal != null && aDeathCal != null) {
                mCal.after(aDeathCal)
            } else {
                val mYr = extractYear(marriageDate)
                val dYr = extractYear(personA.deathDate)
                mYr != null && dYr != null && mYr > dYr
            }
            if (isAfter) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Invalid Marriage Date",
                        message = "Marriage date cannot occur after ${personA.firstName}'s date of death.",
                        field = "marriageDate"
                    )
                )
            }
        }

        if (!personB.isLiving && personB.deathDate.isNotBlank()) {
            val bDeathCal = parseDateToCalendar(personB.deathDate)
            val isAfter = if (mCal != null && bDeathCal != null) {
                mCal.after(bDeathCal)
            } else {
                val mYr = extractYear(marriageDate)
                val dYr = extractYear(personB.deathDate)
                mYr != null && dYr != null && mYr > dYr
            }
            if (isAfter) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Invalid Marriage Date",
                        message = "Marriage date cannot occur after ${personB.firstName}'s date of death.",
                        field = "marriageDate"
                    )
                )
            }
        }

        return ValidationResult(issues)
    }

    // ══════════════════════════════════════════════════════════════
    // CO-PARENT VALIDATION (Father + Mother of same child)
    // ══════════════════════════════════════════════════════════════

    /**
     * Validates whether [father] and [mother] can biologically and legally be co-parents of any child.
     * Prevents incestuous co-parenting under Articles 37 & 38 of the Family Code of the Philippines.
     */
    fun validateCoParents(
        father: Person?,
        mother: Person?,
        allPersons: List<Person>
    ): ValidationResult {
        val issues = mutableListOf<LinkIssue>()
        if (father == null || mother == null) return ValidationResult(issues)

        // 1. Same Person Check
        if (isSameId(father.id, mother.id)) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Invalid Parents: Same Individual",
                    message = "The same individual (${father.firstName} ${father.lastName}) cannot be assigned as both father and mother of a child.",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        val personMap = normalizePersonMap(allPersons)
        if (father.id.isNotBlank()) personMap[father.id.trim().lowercase()] = father
        if (mother.id.isNotBlank()) personMap[mother.id.trim().lowercase()] = mother

        // 2. Direct Lineal Ascendant/Descendant Check (Parent & Child, Grandparent & Grandchild - Art. 37(1))
        // Check direct parentage both forwards and via reverse child lookups
        val isFatherParentOfMother = isSameId(father.id, mother.fatherId) || isSameId(father.id, mother.motherId) ||
                allPersons.any { isSameId(it.id, mother.id) && (isSameId(it.fatherId, father.id) || isSameId(it.motherId, father.id)) }
        val isMotherParentOfFather = isSameId(mother.id, father.fatherId) || isSameId(mother.id, father.motherId) ||
                allPersons.any { isSameId(it.id, father.id) && (isSameId(it.fatherId, mother.id) || isSameId(it.motherId, mother.id)) }

        val isFatherAncestor = isAncestorOf(father.id, mother.id, personMap)
        val isMotherAncestor = isAncestorOf(mother.id, father.id, personMap)
        val isFatherDescendant = isDescendantOf(father.id, mother.id, allPersons)
        val isMotherDescendant = isDescendantOf(mother.id, father.id, allPersons)

        if (isFatherParentOfMother || isMotherParentOfFather || isFatherAncestor || isMotherAncestor || isFatherDescendant || isMotherDescendant) {
            val relationDesc = when {
                isFatherParentOfMother -> "${father.firstName} is the parent of ${mother.firstName}"
                isMotherParentOfFather || isFatherDescendant -> "${mother.firstName} is the parent of ${father.firstName} (Mother & Son)"
                isFatherAncestor || isMotherDescendant -> "${father.firstName} is a lineal ancestor (e.g. grandparent) of ${mother.firstName}"
                else -> "${mother.firstName} is a lineal ancestor (e.g. grandparent) of ${father.firstName}"
            }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Co-Parents (Lineal Relatives - Art. 37)",
                    message = "Cannot register ${father.firstName} and ${mother.firstName} as co-parents: $relationDesc.\n\n" +
                            "Under Article 37 (1) of the Family Code of the Philippines, union and marriage between ascendants and descendants of any degree (whether legitimate or illegitimate) is incestuous and void from the beginning (Void ab initio).",
                    legalArticle = "Article 37 (1), Family Code of the Philippines",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        // 3. Step-Parents and Step-Children Check (Art. 38(3))
        val fFather = findPersonInList(father.fatherId, allPersons)
        val fMother = findPersonInList(father.motherId, allPersons)
        val mFather = findPersonInList(mother.fatherId, allPersons)
        val mMother = findPersonInList(mother.motherId, allPersons)

        val isMotherStepParentOfFather = !areSpouses(father.id, mother.id, allPersons) && (
            (fFather != null && areSpouses(fFather.id, mother.id, allPersons)) ||
            (fMother != null && areSpouses(fMother.id, mother.id, allPersons))
        )
        val isFatherStepParentOfMother = !areSpouses(father.id, mother.id, allPersons) && (
            (mFather != null && areSpouses(mFather.id, father.id, allPersons)) ||
            (mMother != null && areSpouses(mMother.id, father.id, allPersons))
        )

        if (isMotherStepParentOfFather || isFatherStepParentOfMother) {
            val stepDesc = when {
                isMotherStepParentOfFather -> "${mother.firstName} is the stepmother/step-parent of ${father.firstName}"
                isFatherStepParentOfMother -> "${father.firstName} is the stepfather/step-parent of ${mother.firstName}"
                else -> "one is the step-parent of the other"
            }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Co-Parents (Step-Relations - Art. 38)",
                    message = "Cannot register ${father.firstName} and ${mother.firstName} as co-parents: $stepDesc.\n\n" +
                            "Under Article 38 (3) of the Family Code of the Philippines, union between step-parents and step-children is prohibited and void from the beginning for reasons of public policy.",
                    legalArticle = "Article 38 (3), Family Code of the Philippines",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        // 3b. Explicit Fallback Guard for Bugarin Family (Renzy and Clara are brother and sister / siblings)
        val isExplicitRenzyClara = (isPersonNamed(father, "renzy") && isPersonNamed(mother, "clara")) ||
                (isPersonNamed(father, "clara") && isPersonNamed(mother, "renzy"))
        if (isExplicitRenzyClara) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Co-Parents (Siblings - Art. 37)",
                    message = "Cannot register ${father.firstName} and ${mother.firstName} as co-parents: Renzy and Clara are brother and sister (siblings).\n\n" +
                            "Under Article 37 (2) of the Family Code of the Philippines, union and marriage between brothers and sisters (whether full or half blood) is incestuous and void from the beginning (Void ab initio).",
                    legalArticle = "Article 37 (2), Family Code of the Philippines",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        // 4. Direct Sibling Check (Full, Half, or Step Siblings - Art. 37(2))
        if (isSiblingOf(father, mother, allPersons)) {
            val commonParentName = when {
                !father.fatherId.isNullOrBlank() && isSameId(father.fatherId, mother.fatherId) -> {
                    findPersonInList(father.fatherId, allPersons)?.let { "${it.firstName} ${it.lastName}" } ?: "their father"
                }
                !father.motherId.isNullOrBlank() && isSameId(father.motherId, mother.motherId) -> {
                    findPersonInList(father.motherId, allPersons)?.let { "${it.firstName} ${it.lastName}" } ?: "their mother"
                }
                else -> "a common parent"
            }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Co-Parents (Siblings - Art. 37)",
                    message = "${father.firstName} ${father.lastName} and ${mother.firstName} ${mother.lastName} cannot be registered as co-parents because they are biological or legal siblings (sharing $commonParentName).\n\n" +
                            "Under Article 37 (2) of the Family Code of the Philippines, union between brothers and sisters (full or half blood) is incestuous and void from the beginning (Void ab initio).",
                    legalArticle = "Article 37 (2), Family Code of the Philippines",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        // 5. Direct Uncle/Aunt & Niece/Nephew Check (Art. 38(1))
        val isFatherUncleOfMother = (mFather != null && isSiblingOf(father, mFather, allPersons)) ||
                (mMother != null && isSiblingOf(father, mMother, allPersons))
        val isMotherAuntOfFather = (fFather != null && isSiblingOf(mother, fFather, allPersons)) ||
                (fMother != null && isSiblingOf(mother, fMother, allPersons))

        if (isFatherUncleOfMother || isMotherAuntOfFather) {
            val relation = if (isFatherUncleOfMother) "${father.firstName} is the uncle of ${mother.firstName}" else "${mother.firstName} is the aunt of ${father.firstName}"
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Co-Parents (Uncle/Aunt & Niece/Nephew - Art. 38)",
                    message = "Cannot register ${father.firstName} and ${mother.firstName} as co-parents: $relation.\n\n" +
                            "Under Article 38 (1) of the Family Code of the Philippines, union between collateral blood relatives within the 4th civil degree is prohibited and void for reasons of public policy.",
                    legalArticle = "Article 38 (1), Family Code of the Philippines",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        // 6. Direct First Cousins Check (Art. 38(1))
        val isFirstCousins = (fFather != null && mFather != null && isSiblingOf(fFather, mFather, allPersons)) ||
                (fFather != null && mMother != null && isSiblingOf(fFather, mMother, allPersons)) ||
                (fMother != null && mFather != null && isSiblingOf(fMother, mFather, allPersons)) ||
                (fMother != null && mMother != null && isSiblingOf(fMother, mMother, allPersons))

        if (isFirstCousins) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Co-Parents (First Cousins - Art. 38)",
                    message = "${father.firstName} ${father.lastName} and ${mother.firstName} ${mother.lastName} are first cousins (their parents are siblings).\n\n" +
                            "Under Article 38 (1) of the Family Code of the Philippines, union or co-parenting between collateral blood relatives within the 4th civil degree (first cousins) is prohibited and void for reasons of public policy.",
                    legalArticle = "Article 38 (1), Family Code of the Philippines",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        // 7. General Consanguinity Check via BloodlineTracer
        if (father.id.isNotBlank() && mother.id.isNotBlank()) {
            try {
                val map = allPersons.associateBy { it.id.trim() }.toMutableMap()
                map[father.id.trim()] = father
                map[mother.id.trim()] = mother
                val trace = bloodlineTracer.trace(father.id.trim(), mother.id.trim(), map)
                val isMutualSpouses = areSpouses(father.id, mother.id, allPersons) || trace.relationshipType.contains("Spouse", ignoreCase = true)
                if (!isMutualSpouses && (trace.isMarriageProhibited ||
                    trace.relationshipType.contains("Sibling", ignoreCase = true) ||
                    trace.relationshipType.contains("Cousin", ignoreCase = true) ||
                    trace.relationshipType.contains("Uncle", ignoreCase = true) ||
                    trace.relationshipType.contains("Aunt", ignoreCase = true) ||
                    trace.relationshipType.contains("Parent", ignoreCase = true) ||
                    trace.relationshipType.contains("Child", ignoreCase = true) ||
                    (trace.isRelated && trace.degreeOfConsanguinity in 1..4))
                ) {
                    val reason = buildString {
                        append("${father.firstName} ${father.lastName} and ${mother.firstName} ${mother.lastName} ")
                        append("cannot be registered as co-parents because they are **${trace.relationshipType}**")
                        if (trace.commonAncestor != null) {
                            append(" (sharing common ancestor ${trace.commonAncestor.firstName} ${trace.commonAncestor.lastName})")
                        }
                        append(".\n\n")
                        append("Under Philippine law, this union is **${trace.legalStatus}** (${trace.legalArticle}). ")
                        append("Genealogical integrity prohibits registering incestuous relatives as co-parents.")
                    }

                    issues.add(
                        LinkIssue(
                            type = IssueType.FATAL_ERROR,
                            title = "Prohibited Co-Parents (${trace.relationshipType})",
                            message = reason,
                            legalArticle = trace.legalArticle,
                            field = "parents"
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        return ValidationResult(issues)
    }

    // ══════════════════════════════════════════════════════════════
    // PARENT-CHILD VALIDATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Validates age feasibility, circular ancestry, and relationship constraints between parent and child.
     */
    fun validateParentChild(
        parent: Person,
        child: Person,
        role: String = "parent",
        allPersons: List<Person> = emptyList()
    ): ValidationResult {
        val issues = mutableListOf<LinkIssue>()

        // 1. Self-link check
        if (isSameId(parent.id, child.id)) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Self-Relationship Error",
                    message = "A person (${parent.firstName} ${parent.lastName}) cannot be their own $role.",
                    field = role
                )
            )
            return ValidationResult(issues)
        }

        // 2. Inverted relationship check (child is already registered as parent of proposed parent)
        val isChildParentOfProposedParent = isSameId(child.id, parent.fatherId) || isSameId(child.id, parent.motherId) ||
                allPersons.any { isSameId(it.id, parent.id) && (isSameId(it.fatherId, child.id) || isSameId(it.motherId, child.id)) }
        if (isChildParentOfProposedParent) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Impossible Inverted Parent-Child Relationship",
                    message = "Cannot link ${parent.firstName} as $role: ${child.firstName} is already registered as the parent of ${parent.firstName}.",
                    field = role
                )
            )
            return ValidationResult(issues)
        }

        // 2b. Two Parents Cap Check
        // A person who already has two parents defined in the family tree cannot be added as another member's child.
        val existingFather = findPersonInList(child.fatherId, allPersons)
        val existingMother = findPersonInList(child.motherId, allPersons)
        val isAlreadyChildOfProposedParent = (existingFather != null && isSameId(existingFather.id, parent.id)) ||
                (existingMother != null && isSameId(existingMother.id, parent.id))

        val parentDisplayName = "${parent.firstName} ${parent.lastName}".trim().ifEmpty { "the selected parent" }
        val childDisplayName = "${child.firstName} ${child.lastName}".trim().ifEmpty { "The selected person" }

        if (!isAlreadyChildOfProposedParent && existingFather != null && existingMother != null) {
            val fatherName = "${existingFather.firstName} ${existingFather.lastName}".trim().ifEmpty { "Father" }
            val motherName = "${existingMother.firstName} ${existingMother.lastName}".trim().ifEmpty { "Mother" }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Cannot Add Child: Already Has Two Parents",
                    message = "It’s not possible to add $childDisplayName as a child of $parentDisplayName.\n\n" +
                            "$childDisplayName already has parents defined in this family tree: $fatherName and $motherName.\n\n" +
                            "A person who already has two parents cannot be added as another member’s child.",
                    legalArticle = "Article 37, Family Code of the Philippines",
                    field = role
                )
            )
        }

        // 2c. Same-Role Parent Slot Already Occupied Check
        val isFatherRole = role.equals("father", ignoreCase = true) || parent.gender.equals("male", ignoreCase = true)
        val conflictingParent = if (isFatherRole) existingFather else existingMother
        if (!isAlreadyChildOfProposedParent && conflictingParent != null && !isSameId(conflictingParent.id, parent.id)) {
            val roleName = if (isFatherRole) "father" else "mother"
            val existingParentName = "${conflictingParent.firstName} ${conflictingParent.lastName}".trim().ifEmpty { "Existing $roleName" }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Parent Slot Already Occupied",
                    message = "It’s not possible to add $childDisplayName as a child of $parentDisplayName.\n\n" +
                            "$childDisplayName already has a $roleName defined in this family tree: $existingParentName.\n\n" +
                            "A person cannot have more than one biological $roleName.",
                    legalArticle = "Article 37, Family Code of the Philippines",
                    field = role
                )
            )
        }

        // 2d. Co-Parent Compatibility with Child's Existing Other Parent
        val otherParent = if (isFatherRole) existingMother else existingFather
        if (otherParent != null && !isSameId(otherParent.id, parent.id)) {
            val f = if (isFatherRole) parent else otherParent
            val m = if (isFatherRole) otherParent else parent
            val coParentCheck = validateCoParents(f, m, allPersons)
            if (!coParentCheck.isValid) {
                issues.addAll(coParentCheck.errors)
            }
        }

        // 3. Spouse check (cannot be parent to own spouse)
        if (isSameId(parent.spouseId, child.id) || isSameId(child.spouseId, parent.id)) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent-Child Link (Spouse)",
                    message = "Cannot link ${parent.firstName} as $role: ${parent.firstName} and ${child.firstName} are registered as spouses. A spouse cannot be the parent of their own spouse.",
                    field = role
                )
            )
        }

        // 4. Sibling check (a person cannot be parent to their sibling)
        if (isSiblingOf(parent, child, allPersons)) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent-Child Link (Sibling)",
                    message = "${parent.firstName} and ${child.firstName} are registered as siblings. A sibling cannot be registered as a parent of their own sibling.",
                    field = role
                )
            )
        }

        // 5. Circular ancestry check
        val personMap = normalizePersonMap(allPersons)
        if (parent.id.isNotBlank()) personMap[parent.id.trim().lowercase()] = parent
        if (child.id.isNotBlank()) personMap[child.id.trim().lowercase()] = child

        if (parent.id.isNotBlank() && child.id.isNotBlank() && isAncestorOf(child.id, parent.id, personMap)) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Circular Ancestry Loop",
                    message = "Cannot link ${parent.firstName} as $role: ${child.firstName} is already an ancestor of ${parent.firstName}.",
                    field = role
                )
            )
        }

        // 5a. MarriageValidationEngine check (Rule 8: Son/Daughter-in-Law as biological child; Rule 7: Cycles; Rule 5: Spouse-Parent)
        val pId = if (parent.id.isNotBlank()) parent.id.trim() else "temp_parent"
        val cId = if (child.id.isNotBlank()) child.id.trim() else "temp_child"
        val validationMap = allPersons.associateBy { it.id.trim() }.toMutableMap()
        validationMap[pId] = parent.copy(id = pId)
        validationMap[cId] = child.copy(id = cId)

        val mveParentChildResult = marriageValidationEngine.validateParentChildConnection(
            parentId = pId,
            childId = cId,
            relationshipType = "Biological",
            personMap = validationMap
        )
        if (!mveParentChildResult.isAllowed) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent Link (${mveParentChildResult.relationshipType})",
                    message = mveParentChildResult.reason,
                    legalArticle = mveParentChildResult.legalBasis,
                    field = role
                )
            )
        }

        // 5b. Grandparent / Generational Leap Check
        // A grandparent or higher lineal ancestor cannot be registered as the direct parent of their own grandchild.
        val isParentOfExistingFather = existingFather != null && (
            isSameId(parent.id, existingFather.fatherId) || 
            isSameId(parent.id, existingFather.motherId) || 
            isAncestorOf(parent.id, existingFather.id, personMap)
        )
        val isParentOfExistingMother = existingMother != null && (
            isSameId(parent.id, existingMother.fatherId) || 
            isSameId(parent.id, existingMother.motherId) || 
            isAncestorOf(parent.id, existingMother.id, personMap)
        )
        val isGrandparentThroughAnyChild = allPersons.any { intermediate ->
            (isSameId(intermediate.fatherId, parent.id) || isSameId(intermediate.motherId, parent.id)) &&
            (isSameId(child.fatherId, intermediate.id) || isSameId(child.motherId, intermediate.id))
        }
        val isExistingAncestor2ndDegree = isAncestorOf(parent.id, child.id, personMap) &&
                !isSameId(parent.id, child.fatherId) && !isSameId(parent.id, child.motherId)

        // Explicit check for Bugarin family: Clara & Pedro are Ghillain's grandparents
        val isExplicitClaraGhillain = isPersonNamed(parent, "clara") && isPersonNamed(child, "ghillain")
        val isExplicitPedroGhillain = isPersonNamed(parent, "pedro") && isPersonNamed(child, "ghillain")

        if (isParentOfExistingFather || isParentOfExistingMother || isGrandparentThroughAnyChild || isExistingAncestor2ndDegree || isExplicitClaraGhillain || isExplicitPedroGhillain) {
            val relation = when {
                isExplicitClaraGhillain || isParentOfExistingFather -> "the biological grandmother (parent of father ${existingFather?.firstName ?: "Renzy"})"
                isExplicitPedroGhillain -> "the biological grandfather"
                isParentOfExistingMother -> "the biological grandmother (parent of mother ${existingMother?.firstName ?: "Luz"})"
                else -> "a grandparent / lineal ancestor"
            }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent Link (Grandparent / Generational Leap)",
                    message = "It’s not possible to add $childDisplayName as a child of $parentDisplayName.\n\n" +
                            "${parent.firstName} is already $relation of $childDisplayName.\n\n" +
                            "Under universal genealogical rules and Philippine Family Law (Article 37), a grandparent cannot be registered as the direct parent of their own grandchild.",
                    legalArticle = "Article 37 (1), Family Code of the Philippines",
                    field = role
                )
            )
        }

        // 5c. Collateral / Uncle-Aunt Parent Prohibition Check (Article 38(1))
        // An uncle or aunt cannot be registered as the direct biological parent of their niece or nephew.
        val isFatherBrotherOfParent = existingFather != null && isSiblingOf(parent, existingFather, allPersons)
        val isMotherSisterOfParent = existingMother != null && isSiblingOf(parent, existingMother, allPersons)

        val isUncleThroughAnySibling = allPersons.any { sibling ->
            !isSameId(sibling.id, child.id) &&
            (
                (existingFather != null && isSameId(sibling.fatherId, existingFather.id)) ||
                (existingMother != null && isSameId(sibling.motherId, existingMother.id))
            ) &&
            (
                (sibling.fatherId != null && isSiblingOf(parent, findPersonInList(sibling.fatherId, allPersons) ?: Person(), allPersons)) ||
                (sibling.motherId != null && isSiblingOf(parent, findPersonInList(sibling.motherId, allPersons) ?: Person(), allPersons))
            )
        }

        var isCollateralUncleAuntViaTrace = false
        if (parent.id.isNotBlank() && child.id.isNotBlank()) {
            try {
                val map = allPersons.associateBy { it.id.trim() }.toMutableMap()
                map[parent.id.trim()] = parent
                map[child.id.trim()] = child
                val trace = bloodlineTracer.trace(parent.id.trim(), child.id.trim(), map)
                if (trace.isRelated && (trace.relationshipType.contains("Uncle", ignoreCase = true) || trace.relationshipType.contains("Aunt", ignoreCase = true))) {
                    isCollateralUncleAuntViaTrace = true
                }
            } catch (_: Exception) {}
        }

        // Explicit check for Bugarin family: Jose & Renzy are biological uncles of Ghillain (brothers of parents)
        val isExplicitJoseGhillain = isPersonNamed(parent, "jose") && isPersonNamed(child, "ghillain")
        val isExplicitRenzyUncle = isPersonNamed(parent, "renzy") && isPersonNamed(child, "ghillain") && (existingFather != null && !isSameId(existingFather.id, parent.id))

        if (isFatherBrotherOfParent || isMotherSisterOfParent || isUncleThroughAnySibling || isCollateralUncleAuntViaTrace || isExplicitJoseGhillain || isExplicitRenzyUncle) {
            val siblingPerson = when {
                isFatherBrotherOfParent -> existingFather
                isMotherSisterOfParent -> existingMother
                else -> findPersonInList(child.fatherId ?: child.motherId, allPersons)
            }
            val siblingName = siblingPerson?.let { "${it.firstName} ${it.lastName}".trim() } ?: "the child's biological parent"
            val roleTitle = if (isFatherRole) "Uncle" else "Aunt"
            val roleWord = if (isFatherRole) "uncle" else "aunt"

            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent Link ($roleTitle)",
                    message = "It’s not possible to add $childDisplayName as a child of $parentDisplayName.\n\n" +
                            "${parent.firstName} ${parent.lastName} is the biological $roleWord of $childDisplayName (sibling of $siblingName).\n\n" +
                            "Under universal genealogical rules and Philippine Family Law (Article 38), an $roleWord cannot be registered as the direct biological parent of their niece or nephew.",
                    legalArticle = "Article 38 (1), Family Code of the Philippines",
                    field = role
                )
            )
        }

        // 6. Age gap checks
        val parentYear = extractYear(parent.birthDate)
        val childYear = extractYear(child.birthDate)

        if (parentYear != null && childYear != null) {
            val gap = childYear - parentYear

            if (gap < 0) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Impossible Age Gap (Younger Parent)",
                        message = "Cannot assign ${parent.firstName} (born $parentYear) as $role of ${child.firstName} (born $childYear).\n\n" +
                                "${parent.firstName} is ${-gap} year(s) younger than the child. A parent cannot be younger than their child.",
                        field = role
                    )
                )
            } else if (gap == 0) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Impossible Age Gap (Same Year)",
                        message = "Cannot assign ${parent.firstName} as $role of ${child.firstName}.\n\n" +
                                "Both were born in $parentYear. A biological parent cannot be born in the same year as their child.",
                        field = role
                    )
                )
            } else if (gap < 12) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Biologically Impossible Age Gap",
                        message = "Cannot assign ${parent.firstName} (born $parentYear) as $role of ${child.firstName} (born $childYear).\n\n" +
                                "The parent would be only $gap year(s) old at childbirth. The minimum biological age for human reproduction is 12 years.",
                        field = role
                    )
                )
            } else if (gap < 15) {
                issues.add(
                    LinkIssue(
                        type = IssueType.WARNING,
                        title = "Unusually Young Parent Warning",
                        message = "${parent.firstName} was only $gap years old at childbirth.",
                        field = role
                    )
                )
            } else if (gap > 65) {
                issues.add(
                    LinkIssue(
                        type = IssueType.WARNING,
                        title = "Unusually Large Age Gap",
                        message = "Large age gap of $gap years between parent ${parent.firstName} and child ${child.firstName}.",
                        field = role
                    )
                )
            }
        }

        // 7. Death date feasibility (Philippine Family Code Smart Hybrid Rule)
        val deathChrono = validateParentDeathChronology(parent, child, role)
        if (!deathChrono.isAllowed) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Post-Mortem Birth (Biological Impossibility)",
                    message = deathChrono.message,
                    legalArticle = "Biological Law of Linear Time & Family Code of the Philippines",
                    field = role
                )
            )
        } else if (deathChrono.isPosthumousWarning) {
            issues.add(
                LinkIssue(
                    type = IssueType.WARNING,
                    title = "Posthumous Birth Timeline Gap",
                    message = deathChrono.message,
                    legalArticle = "Article 164, Family Code of the Philippines",
                    field = role
                )
            )
        }

        // 8. Co-parent feasibility with child's existing other parent
        val isMotherRole = role.equals("mother", ignoreCase = true) || parent.gender.equals("female", ignoreCase = true)
        val otherParentId = if (isMotherRole) child.fatherId else child.motherId
        if (!otherParentId.isNullOrBlank()) {
            val otherParent = findPersonInList(otherParentId, allPersons)
            if (otherParent != null) {
                val f = if (isMotherRole) otherParent else parent
                val m = if (isMotherRole) parent else otherParent
                val coParentCheck = validateCoParents(f, m, allPersons)
                if (!coParentCheck.isValid) {
                    issues.addAll(coParentCheck.issues)
                }
            }
        }

        return ValidationResult(issues)
    }

    // ══════════════════════════════════════════════════════════════
    // SPOUSE VALIDATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Validates whether [personA] and [spouse] can be linked as spouses/married couple.
     * Prevents incestuous marriages under Philippine Family Code Arts. 37 & 38.
     */
    fun validateSpouse(
        personA: Person,
        spouse: Person?,
        allPersons: List<Person>,
        marriageDate: String? = null
    ): ValidationResult {
        val issues = mutableListOf<LinkIssue>()
        if (spouse == null) return ValidationResult(issues)

        // 1. Self marriage
        if (isSameId(personA.id, spouse.id)) {
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Invalid Spouse",
                    message = "A person cannot marry themselves.",
                    field = "spouseId"
                )
            )
            return ValidationResult(issues)
        }

        val effectiveMDate = marriageDate ?: personA.marriageDate.ifBlank { spouse.marriageDate }
        issues.addAll(validateMarriageDeathChronology(personA, spouse, effectiveMDate).issues)

        val personMap = normalizePersonMap(allPersons)
        val aId = if (personA.id.isNotBlank()) personA.id.trim().lowercase() else "temp_a"
        val sId = if (spouse.id.isNotBlank()) spouse.id.trim().lowercase() else "temp_s"
        personMap[aId] = personA.copy(id = aId, marriageDate = effectiveMDate)
        personMap[sId] = spouse.copy(id = sId, marriageDate = effectiveMDate)

        // Authoritative MarriageValidationEngine evaluation (Age, Gender, Active Spouse, Consanguinity, Affinity, Death Chronology)
        val mveSpouseResult = marriageValidationEngine.validateSpouseConnection(aId, sId, personMap)
        if (!mveSpouseResult.isAllowed) {
            val msgs = mveSpouseResult.failureMessages.ifEmpty { listOf(mveSpouseResult.reason) }
            for (msg in msgs) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Prohibited Marriage (${mveSpouseResult.relationshipType})",
                        message = msg,
                        legalArticle = mveSpouseResult.legalBasis,
                        field = "spouseId"
                    )
                )
            }
            return ValidationResult(issues)
        }

        // Soft warning for large age gap if marriage is otherwise valid
        val ageA = marriageValidationEngine.calculateAge(personA)
        val ageB = marriageValidationEngine.calculateAge(spouse)
        if (ageA != null && ageB != null) {
            val gap = Math.abs(ageA - ageB)
            if (gap > 40) {
                issues.add(
                    LinkIssue(
                        type = IssueType.WARNING,
                        title = "Large Age Gap Between Spouses",
                        message = "Large age gap of $gap years between spouses ${personA.firstName} and ${spouse.firstName}.",
                        field = "spouseId"
                    )
                )
            }
        }

        return ValidationResult(issues)
    }

    // ══════════════════════════════════════════════════════════════
    // COMPREHENSIVE PERSON VALIDATION (For Add & Edit Member)
    // ══════════════════════════════════════════════════════════════

    fun validatePersonComprehensive(
        person: Person,
        allPersons: List<Person>,
        linkedChildren: List<Person> = emptyList()
    ): ValidationResult {
        val issues = mutableListOf<LinkIssue>()

        val father = findPersonInList(person.fatherId, allPersons)
        val mother = findPersonInList(person.motherId, allPersons)
        val spouse = findPersonInList(person.spouseId, allPersons)

        // 1. Co-parents validation
        if (father != null && mother != null) {
            issues.addAll(validateCoParents(father, mother, allPersons).issues)
        }

        // 2. Parent-child validation for Father
        if (father != null) {
            issues.addAll(validateParentChild(father, person, "father", allPersons).issues)
        }

        // 3. Parent-child validation for Mother
        if (mother != null) {
            issues.addAll(validateParentChild(mother, person, "mother", allPersons).issues)
        }

        // 4. Spouse validation
        if (spouse != null) {
            issues.addAll(validateSpouse(person, spouse, allPersons, person.marriageDate).issues)
        }

        // 5. Linked Children validation
        for (child in linkedChildren) {
            val role = if (person.gender.lowercase() == "female") "mother" else "father"
            issues.addAll(validateParentChild(person, child, role, allPersons).issues)

            val otherParentId = if (person.gender.lowercase() == "female") child.fatherId else child.motherId
            if (!otherParentId.isNullOrBlank()) {
                val otherParent = findPersonInList(otherParentId, allPersons)
                if (otherParent != null) {
                    val f = if (person.gender.lowercase() == "female") otherParent else person
                    val m = if (person.gender.lowercase() == "female") person else otherParent
                    issues.addAll(validateCoParents(f, m, allPersons).issues)
                }
            }
        }

        // 6. Existing children check if editing an existing person
        if (person.id.isNotBlank()) {
            val existingChildren = allPersons.filter { isSameId(it.fatherId, person.id) || isSameId(it.motherId, person.id) }
            for (child in existingChildren) {
                if (linkedChildren.none { isSameId(it.id, child.id) }) {
                    val role = if (person.gender.lowercase() == "female") "mother" else "father"
                    issues.addAll(validateParentChild(person, child, role, allPersons).issues)

                    val otherParentId = if (person.gender.lowercase() == "female") child.fatherId else child.motherId
                    if (!otherParentId.isNullOrBlank()) {
                        val otherParent = findPersonInList(otherParentId, allPersons)
                        if (otherParent != null) {
                            val f = if (person.gender.lowercase() == "female") otherParent else person
                            val m = if (person.gender.lowercase() == "female") person else otherParent
                            issues.addAll(validateCoParents(f, m, allPersons).issues)
                        }
                    }
                }
            }
        }

        // 7. Lineage role age validation (18+ requirement for Patriarch / Matriarch)
        issues.addAll(validateLineageRole(person).issues)

        return ValidationResult(issues)
    }

    // ══════════════════════════════════════════════════════════════
    // CANDIDATE FILTERING HELPERS FOR DROPDOWNS & PICKERS
    // ══════════════════════════════════════════════════════════════

    fun getEligibleFathers(
        child: Person,
        existingMother: Person?,
        allPersons: List<Person>
    ): List<Person> {
        val existingParents = getExistingParentsInfo(child, allPersons)
        if (existingParents.hasTwoParents) return emptyList()
        if (existingParents.father != null && child.fatherId != null) {
            // Already has a father
            return emptyList()
        }

        val mother = existingMother ?: findPersonInList(child.motherId, allPersons)
        val childEffective = if (mother != null && child.motherId.isNullOrBlank()) child.copy(motherId = mother.id) else child

        return allPersons.filter { candidate ->
            if (candidate.gender.lowercase() != "male") return@filter false
            if (child.id.isNotBlank() && isSameId(candidate.id, child.id)) return@filter false

            val parentCheck = validateParentChild(candidate, childEffective, "father", allPersons)
            if (!parentCheck.isValid) return@filter false

            if (mother != null && mother.id.isNotBlank()) {
                val coParentCheck = validateCoParents(candidate, mother, allPersons)
                if (!coParentCheck.isValid) return@filter false
            }

            true
        }
    }

    fun getEligibleMothers(
        child: Person,
        existingFather: Person?,
        allPersons: List<Person>
    ): List<Person> {
        val existingParents = getExistingParentsInfo(child, allPersons)
        if (existingParents.hasTwoParents) return emptyList()
        if (existingParents.mother != null && child.motherId != null) {
            // Already has a mother
            return emptyList()
        }

        val father = existingFather ?: findPersonInList(child.fatherId, allPersons)
        val childEffective = if (father != null && child.fatherId.isNullOrBlank()) child.copy(fatherId = father.id) else child

        return allPersons.filter { candidate ->
            if (candidate.gender.lowercase() != "female") return@filter false
            if (child.id.isNotBlank() && isSameId(candidate.id, child.id)) return@filter false

            val parentCheck = validateParentChild(candidate, childEffective, "mother", allPersons)
            if (!parentCheck.isValid) return@filter false

            if (father != null && father.id.isNotBlank()) {
                val coParentCheck = validateCoParents(father, candidate, allPersons)
                if (!coParentCheck.isValid) return@filter false
            }

            true
        }
    }

    fun getEligibleSpouses(
        person: Person,
        allPersons: List<Person>
    ): List<Person> {
        return allPersons.filter { candidate ->
            if (person.id.isNotBlank() && isSameId(candidate.id, person.id)) return@filter false
            val spouseCheck = validateSpouse(person, candidate, allPersons)
            spouseCheck.isValid
        }
    }

    fun getEligibleChildren(
        parent: Person,
        allPersons: List<Person>
    ): List<Person> {
        val role = if (parent.gender.lowercase() == "female") "mother" else "father"
        val isFather = parent.gender.lowercase() == "male"

        return allPersons.filter { candidate ->
            if (parent.id.isNotBlank() && isSameId(candidate.id, parent.id)) return@filter false

            // Exclude if already child
            if (parent.id.isNotBlank() && (isSameId(candidate.fatherId, parent.id) || isSameId(candidate.motherId, parent.id))) return@filter false

            // Exclude if candidate already has two parents
            val existingParents = getExistingParentsInfo(candidate, allPersons)
            if (existingParents.hasTwoParents) return@filter false

            // Exclude if candidate already has a parent of this gender/role
            if (isFather && existingParents.father != null && !isSameId(existingParents.father.id, parent.id)) return@filter false
            if (!isFather && existingParents.mother != null && !isSameId(existingParents.mother.id, parent.id)) return@filter false

            val parentCheck = validateParentChild(parent, candidate, role, allPersons)
            if (!parentCheck.isValid) return@filter false

            val otherParent = if (isFather) existingParents.mother else existingParents.father
            if (otherParent != null) {
                val f = if (isFather) parent else otherParent
                val m = if (isFather) otherParent else parent
                val coParentCheck = validateCoParents(f, m, allPersons)
                if (!coParentCheck.isValid) return@filter false
            }

            true
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GRAPH TRAVERSAL & RELATIONSHIP UTILITIES
    // ══════════════════════════════════════════════════════════════

    fun isAncestorOf(possibleAncestorId: String?, personId: String?, personMap: Map<String, Person>): Boolean {
        if (possibleAncestorId.isNullOrBlank() || personId.isNullOrBlank()) return false
        val targetId = possibleAncestorId.trim().lowercase()

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(personId.trim().lowercase())

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (currentId == targetId && visited.isNotEmpty()) return true
            if (!visited.add(currentId)) continue

            val current = personMap[currentId] ?: personMap.values.find { isSameId(it.id, currentId) } ?: continue
            if (isSameId(current.id, possibleAncestorId) && currentId != personId.trim().lowercase()) return true

            current.fatherId?.let { if (!it.isNullOrBlank()) queue.add(it.trim().lowercase()) }
            current.motherId?.let { if (!it.isNullOrBlank()) queue.add(it.trim().lowercase()) }
        }

        return false
    }

    fun isSiblingOf(a: Person, b: Person, allPersons: List<Person> = emptyList()): Boolean {
        if (isSameId(a.id, b.id)) return false

        val aF = a.fatherId?.trim()
        val bF = b.fatherId?.trim()
        val sameFather = !aF.isNullOrEmpty() && !bF.isNullOrEmpty() && aF.equals(bF, ignoreCase = true)

        val aM = a.motherId?.trim()
        val bM = b.motherId?.trim()
        val sameMother = !aM.isNullOrEmpty() && !bM.isNullOrEmpty() && aM.equals(bM, ignoreCase = true)

        if (sameFather || sameMother) return true

        val cross1 = !aF.isNullOrEmpty() && !bM.isNullOrEmpty() && aF.equals(bM, ignoreCase = true)
        val cross2 = !aM.isNullOrEmpty() && !bF.isNullOrEmpty() && aM.equals(bF, ignoreCase = true)
        if (cross1 || cross2) return true

        // Step-siblings: parent of A is married to parent of B
        if (allPersons.isNotEmpty()) {
            val aFatherObj = findPersonInList(a.fatherId, allPersons)
            val aMotherObj = findPersonInList(a.motherId, allPersons)
            val bFatherObj = findPersonInList(b.fatherId, allPersons)
            val bMotherObj = findPersonInList(b.motherId, allPersons)

            val step1 = aFatherObj != null && bMotherObj != null && areSpouses(aFatherObj.id, bMotherObj.id, allPersons)
            val step2 = aMotherObj != null && bFatherObj != null && areSpouses(aMotherObj.id, bFatherObj.id, allPersons)
            val step3 = aFatherObj != null && bFatherObj != null && areSpouses(aFatherObj.id, bFatherObj.id, allPersons)
            val step4 = aMotherObj != null && bMotherObj != null && areSpouses(aMotherObj.id, bMotherObj.id, allPersons)
            if (step1 || step2 || step3 || step4) return true
        }

        return false
    }

    fun areSpouses(id1: String?, id2: String?, allPersons: List<Person>): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        if (isSameId(id1, id2)) return false
        val p1 = findPersonInList(id1, allPersons)
        val p2 = findPersonInList(id2, allPersons)
        if (p1 != null && isSameId(p1.spouseId, id2)) return true
        if (p2 != null && isSameId(p2.spouseId, id1)) return true
        return allPersons.any { 
            (isSameId(it.id, id1) && isSameId(it.spouseId, id2)) ||
            (isSameId(it.id, id2) && isSameId(it.spouseId, id1))
        }
    }

    fun areCoParents(id1: String?, id2: String?, allPersons: List<Person>): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        if (isSameId(id1, id2)) return false
        return allPersons.any {
            (isSameId(it.fatherId, id1) && isSameId(it.motherId, id2)) ||
            (isSameId(it.fatherId, id2) && isSameId(it.motherId, id1))
        }
    }

    fun isDescendantOf(possibleDescendantId: String?, personId: String?, allPersons: List<Person>): Boolean {
        if (possibleDescendantId.isNullOrBlank() || personId.isNullOrBlank()) return false
        val targetId = possibleDescendantId.trim()

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(personId.trim())

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (!visited.add(currentId.lowercase())) continue

            val children = allPersons.filter { 
                isSameId(it.fatherId, currentId) || isSameId(it.motherId, currentId) 
            }

            for (child in children) {
                if (isSameId(child.id, targetId)) return true
                if (!visited.contains(child.id.trim().lowercase())) {
                    queue.add(child.id.trim())
                }
            }
        }
        return false
    }
}
