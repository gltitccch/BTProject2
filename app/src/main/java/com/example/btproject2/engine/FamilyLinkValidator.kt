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

    private val YEAR_REGEX = Regex("""\b(18|19|20)\d{2}\b""")

    fun extractYear(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()

        if (trimmed.length == 4) {
            trimmed.toIntOrNull()?.let { return it }
        }

        YEAR_REGEX.find(trimmed)?.value?.toIntOrNull()?.let { return it }

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

        return null
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
     * Compares two date strings chronologically regardless of formatting ("MMM d, yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "yyyy").
     * Returns negative if [dateA] is earlier than [dateB], 0 if equal, positive if [dateA] is later than [dateB],
     * or null if either date cannot be parsed.
     */
    fun compareDatesChronologically(dateA: String?, dateB: String?): Int? {
        val calA = parseDateToCalendar(dateA)
        val calB = parseDateToCalendar(dateB)
        if (calA != null && calB != null) {
            return calA.timeInMillis.compareTo(calB.timeInMillis)
        }
        val yrA = extractYear(dateA)
        val yrB = extractYear(dateB)
        if (yrA != null && yrB != null) {
            return yrA.compareTo(yrB)
        }
        return null
    }

    /**
     * Returns the signed difference in years (`laterYear - earlierYear`) between two date strings,
     * accounting for month/day when full calendar dates are available.
     */
    fun yearsBetweenDates(earlierDateStr: String?, laterDateStr: String?): Int? {
        val cal1 = parseDateToCalendar(earlierDateStr)
        val cal2 = parseDateToCalendar(laterDateStr)
        if (cal1 != null && cal2 != null) {
            var years = cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR)
            if (cal2.get(Calendar.MONTH) < cal1.get(Calendar.MONTH) ||
                (cal2.get(Calendar.MONTH) == cal1.get(Calendar.MONTH) &&
                    cal2.get(Calendar.DAY_OF_MONTH) < cal1.get(Calendar.DAY_OF_MONTH))
            ) {
                years--
            }
            return years
        }
        val yr1 = extractYear(earlierDateStr)
        val yr2 = extractYear(laterDateStr)
        if (yr1 != null && yr2 != null) {
            return yr2 - yr1
        }
        return null
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
     *
     * @param skipDeepTraces When true (used during fast dropdown candidate filtering), skips the
     *                       multi-generation BloodlineTracer while keeping direct incest/lineal checks active.
     */
    fun validateCoParents(
        father: Person?,
        mother: Person?,
        allPersons: List<Person>,
        skipDeepTraces: Boolean = false
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
        if (!skipDeepTraces && father.id.isNotBlank() && mother.id.isNotBlank()) {
            try {
                val map = allPersons.associateBy { it.id.trim() }.toMutableMap()
                map[father.id.trim()] = father
                map[mother.id.trim()] = mother
                val trace = bloodlineTracer.trace(father.id.trim(), mother.id.trim(), map)
                val isMutualSpouses = areSpouses(father.id, mother.id, allPersons) || trace.relationshipType.contains("Spouse", ignoreCase = true)
                val isLinealRelative = trace.relationshipCategory.equals("Lineal", ignoreCase = true) ||
                        trace.relationshipType.contains("Parent", ignoreCase = true) ||
                        trace.relationshipType.contains("Child", ignoreCase = true)
                val isWithinFourthDegree = trace.isRelated && trace.degreeOfConsanguinity in 1..4
                if (!isMutualSpouses && (trace.isMarriageProhibited ||
                    trace.relationshipType.contains("Sibling", ignoreCase = true) ||
                    isLinealRelative ||
                    isWithinFourthDegree)
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

        // 8. Historical Lifespan Overlap & 80-Year Birth-Gap Guard for Co-Parents
        val fatherBirthYr = extractYear(father.birthDate)
        val motherBirthYr = extractYear(mother.birthDate)
        val fatherDeathYr = if (!father.isLiving && father.deathDate.isNotBlank()) extractYear(father.deathDate) else null
        val motherDeathYr = if (!mother.isLiving && mother.deathDate.isNotBlank()) extractYear(mother.deathDate) else null

        if (fatherDeathYr != null && motherBirthYr != null && fatherDeathYr - motherBirthYr < 12) {
            val detail = if (fatherDeathYr < motherBirthYr) {
                "${father.firstName} ${father.lastName} passed away in $fatherDeathYr, which is ${motherBirthYr - fatherDeathYr} year(s) before ${mother.firstName} ${mother.lastName} was born in $motherBirthYr."
            } else {
                "${mother.firstName} ${mother.lastName} (born $motherBirthYr) was only ${fatherDeathYr - motherBirthYr} year(s) old when ${father.firstName} ${father.lastName} passed away in $fatherDeathYr."
            }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Chronological Lifespan Conflict (Co-Parents)",
                    message = "Cannot register ${father.firstName} and ${mother.firstName} as biological co-parents: $detail",
                    legalArticle = "Biological & Chronological Lifespan Feasibility",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        if (motherDeathYr != null && fatherBirthYr != null && motherDeathYr - fatherBirthYr < 12) {
            val detail = if (motherDeathYr < fatherBirthYr) {
                "${mother.firstName} ${mother.lastName} passed away in $motherDeathYr, which is ${fatherBirthYr - motherDeathYr} year(s) before ${father.firstName} ${father.lastName} was born in $fatherBirthYr."
            } else {
                "${father.firstName} ${father.lastName} (born $fatherBirthYr) was only ${motherDeathYr - fatherBirthYr} year(s) old when ${mother.firstName} ${mother.lastName} passed away in $motherDeathYr."
            }
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Chronological Lifespan Conflict (Co-Parents)",
                    message = "Cannot register ${father.firstName} and ${mother.firstName} as biological co-parents: $detail",
                    legalArticle = "Biological & Chronological Lifespan Feasibility",
                    field = "parents"
                )
            )
            return ValidationResult(issues)
        }

        if (fatherBirthYr != null && motherBirthYr != null) {
            val birthYearGap = kotlin.math.abs(fatherBirthYr - motherBirthYr)
            if (birthYearGap > MarriageValidationEngine.MAX_SPOUSE_BIRTH_YEAR_GAP) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Historical Birth-Year Conflict (Co-Parents)",
                        message = "Cannot register ${father.firstName} ${father.lastName} (born $fatherBirthYr) and ${mother.firstName} ${mother.lastName} (born $motherBirthYr) as co-parents: their birth years are $birthYearGap years apart (exceeding the ${MarriageValidationEngine.MAX_SPOUSE_BIRTH_YEAR_GAP}-year human generation span).",
                        legalArticle = "Genealogical Lifespan Feasibility (Maximum 80-Year Birth Span)",
                        field = "parents"
                    )
                )
                return ValidationResult(issues)
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
        allPersons: List<Person> = emptyList(),
        checkCoParents: Boolean = true
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
        if (checkCoParents && otherParent != null && !isSameId(otherParent.id, parent.id)) {
            val f = if (isFatherRole) parent else otherParent
            val m = if (isFatherRole) otherParent else parent
            val coParentCheck = validateCoParents(f, m, allPersons)
            if (!coParentCheck.isValid) {
                issues.addAll(coParentCheck.errors)
            }
        }

        // 3. Spouse check (cannot be parent to own spouse)
        if (parent.hasSpouseId(child.id) || child.hasSpouseId(parent.id)) {
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

        val effectiveRelType = if (isFatherRole) {
            child.fatherRelationshipType.ifBlank { "Biological" }
        } else {
            child.motherRelationshipType.ifBlank { "Biological" }
        }

        val mveParentChildResult = marriageValidationEngine.validateParentChildConnection(
            parentId = pId,
            childId = cId,
            relationshipType = effectiveRelType,
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

        if (isParentOfExistingFather || isParentOfExistingMother || isGrandparentThroughAnyChild || isExistingAncestor2ndDegree) {
            val gpTerm = if (parent.gender.equals("Female", ignoreCase = true)) "biological grandmother" else if (parent.gender.equals("Male", ignoreCase = true)) "biological grandfather" else "grandparent"
            val relation = when {
                isParentOfExistingFather -> "the $gpTerm (parent of father ${existingFather?.firstName ?: "their father"})"
                isParentOfExistingMother -> "the $gpTerm (parent of mother ${existingMother?.firstName ?: "their mother"})"
                else -> "a $gpTerm / lineal ancestor"
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

        // 5c. Collateral / Uncle-Aunt / Nephew-Niece / Cousin Parent Prohibition Check (Article 38(1))
        // A collateral blood relative (uncle/aunt, nephew/niece, or first cousin within the 4th civil degree)
        // cannot be registered as the direct biological parent of their close collateral relative.
        val isFatherBrotherOfParent = existingFather != null && isSiblingOf(parent, existingFather, allPersons)
        val isMotherSisterOfParent = existingMother != null && isSiblingOf(parent, existingMother, allPersons)

        val isUncleThroughAnySibling = allPersons.any { sibling ->
            !isSameId(sibling.id, child.id) &&
            (
                (existingFather != null && isSameId(sibling.fatherId, existingFather.id)) ||
                (existingMother != null && isSameId(sibling.motherId, existingMother.id))
            ) &&
            (
                (sibling.fatherId != null && isSiblingOf(parent, personMap[sibling.fatherId.trim().lowercase()] ?: Person(), allPersons)) ||
                (sibling.motherId != null && isSiblingOf(parent, personMap[sibling.motherId.trim().lowercase()] ?: Person(), allPersons))
            )
        }

        // Check if child is actually the sibling of parent's father or mother (i.e. child is the Aunt/Uncle and parent is the Nephew/Niece!)
        val parentFatherObj = findPersonInList(parent.fatherId, allPersons)
        val parentMotherObj = findPersonInList(parent.motherId, allPersons)
        val isChildSiblingOfParentsParent = (parentFatherObj != null && isSiblingOf(child, parentFatherObj, allPersons)) ||
            (parentMotherObj != null && isSiblingOf(child, parentMotherObj, allPersons))

        var traceResultForCollateral: com.example.btproject2.models.TraceResult? = null
        if (!isAlreadyChildOfProposedParent && parent.id.isNotBlank() && child.id.isNotBlank()) {
            try {
                val trace = bloodlineTracer.trace(pId, cId, validationMap)
                if (trace.isRelated) {
                    traceResultForCollateral = trace
                }
            } catch (_: Exception) {}
        }

        val isCollateralUncleAuntViaTrace = traceResultForCollateral != null &&
            (traceResultForCollateral.relationshipType.contains("Uncle", ignoreCase = true) ||
                traceResultForCollateral.relationshipType.contains("Aunt", ignoreCase = true)) &&
            traceResultForCollateral.distanceA <= traceResultForCollateral.distanceB

        val isReverseNephewNieceParentage = isChildSiblingOfParentsParent || (
            traceResultForCollateral != null &&
                (traceResultForCollateral.relationshipType.contains("Uncle", ignoreCase = true) ||
                    traceResultForCollateral.relationshipType.contains("Aunt", ignoreCase = true)) &&
                traceResultForCollateral.distanceA > traceResultForCollateral.distanceB
            )

        val isCollateralCousinParentage = traceResultForCollateral != null &&
            traceResultForCollateral.relationshipCategory.equals("Collateral", ignoreCase = true) &&
            traceResultForCollateral.degreeOfConsanguinity in 2..4 &&
            !isCollateralUncleAuntViaTrace && !isReverseNephewNieceParentage &&
            !isSiblingOf(parent, child, allPersons)

        if (isReverseNephewNieceParentage) {
            val parentRoleWord = if (parent.gender.equals("Female", ignoreCase = true)) "niece" else "nephew"
            val childRoleWord = if (child.gender.equals("Female", ignoreCase = true)) "aunt" else "uncle"
            val sharedParentObj = when {
                parentFatherObj != null && isSiblingOf(child, parentFatherObj, allPersons) -> parentFatherObj
                parentMotherObj != null && isSiblingOf(child, parentMotherObj, allPersons) -> parentMotherObj
                else -> null
            }
            val siblingContext = sharedParentObj?.let { " (sibling of ${parent.firstName}'s parent ${it.firstName} ${it.lastName})".trimEnd() } ?: ""

            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent Link (Nephew/Niece to Aunt/Uncle)",
                    message = "It’s not possible to add $childDisplayName as a child of $parentDisplayName.\n\n" +
                            "$parentDisplayName is the biological $parentRoleWord of $childDisplayName, meaning $childDisplayName is already ${parent.firstName}'s biological $childRoleWord$siblingContext.\n\n" +
                            "Even when a nephew or niece is older in years than a young aunt or uncle, generational steps in the family tree cannot be reversed. Under Philippine Family Law (Article 38), a $parentRoleWord cannot be registered as the parent of their own $childRoleWord.",
                    legalArticle = "Article 38 (1), Family Code of the Philippines",
                    field = role
                )
            )
        } else if (isFatherBrotherOfParent || isMotherSisterOfParent || isUncleThroughAnySibling || isCollateralUncleAuntViaTrace) {
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
        } else if (isCollateralCousinParentage && effectiveRelType.equals("Biological", ignoreCase = true)) {
            val relLabel = traceResultForCollateral?.relationshipType ?: "Collateral Relative"
            issues.add(
                LinkIssue(
                    type = IssueType.FATAL_ERROR,
                    title = "Prohibited Parent Link ($relLabel)",
                    message = "It’s not possible to add $childDisplayName as a biological child of $parentDisplayName.\n\n" +
                            "$parentDisplayName and $childDisplayName are already connected as $relLabel (${traceResultForCollateral?.degreeOfConsanguinity ?: 4}th Civil Degree).\n\n" +
                            "Under universal genealogical rules and Article 38(1) of the Family Code of the Philippines, close collateral blood relatives within the 4th civil degree cannot be linked as direct biological parent and child.",
                    legalArticle = "Article 38 (1), Family Code of the Philippines",
                    field = role
                )
            )
        }

        // 6. Direct & Multi-Generation Age Gap Checks
        val parentYear = extractYear(parent.birthDate)
        val childYear = extractYear(child.birthDate)
        val isAdoptiveLink = (isFatherRole && child.fatherRelationshipType.equals("Adoptive", ignoreCase = true)) ||
                (!isFatherRole && child.motherRelationshipType.equals("Adoptive", ignoreCase = true))

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
                if (!isAdoptiveLink) {
                    issues.add(
                        LinkIssue(
                            type = IssueType.FATAL_ERROR,
                            title = "Biologically Impossible Age Gap",
                            message = "Cannot assign ${parent.firstName} (born $parentYear) as $role of ${child.firstName} (born $childYear).\n\n" +
                                    "The parent would be only $gap year(s) old at childbirth. The minimum biological age for human reproduction is 12 years.",
                            field = role
                        )
                    )
                }
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

        // 6b. Multi-Generation Ancestor-Descendant Age Feasibility Guard
        // Even if a middle parent has no birth date recorded yet, any biological ancestor N steps above
        // a biological descendant must be at least (12 * N) years older (e.g. Grandparent >= 24 yrs, Great-Grandparent >= 36 yrs).
        issues.addAll(
            validateMultiGenerationAgeFeasibility(
                parent = parent,
                child = child,
                role = role,
                isAdoptiveLink = isAdoptiveLink,
                allPersons = allPersons
            )
        )

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

        val isHistoricalFormerSpouse = (personA.formerSpouseIds.any { isSameId(it, spouse.id) } && !isSameId(personA.spouseId, spouse.id)) ||
            (spouse.formerSpouseIds.any { isSameId(it, personA.id) } && !isSameId(spouse.spouseId, personA.id))
        val effectiveMDate = marriageDate ?: if (isHistoricalFormerSpouse) {
            if (!spouse.isLiving || spouse.deathDate.isNotBlank()) spouse.marriageDate
            else if (!personA.isLiving || personA.deathDate.isNotBlank()) personA.marriageDate
            else personA.marriageDate.ifBlank { spouse.marriageDate }
        } else {
            personA.marriageDate.ifBlank { spouse.marriageDate }
        }
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

        // Soft warning for large birth-year gap (41..80 years) if marriage is otherwise valid
        val birthYearA = extractYear(personA.birthDate)
        val birthYearB = extractYear(spouse.birthDate)
        if (birthYearA != null && birthYearB != null) {
            val gap = kotlin.math.abs(birthYearA - birthYearB)
            if (gap > 40) {
                issues.add(
                    LinkIssue(
                        type = IssueType.WARNING,
                        title = "Large Age Gap Between Spouses",
                        message = "Large birth-year gap of $gap years between spouses ${personA.firstName} (born $birthYearA) and ${spouse.firstName} (born $birthYearB).",
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

        // 8. Multi-generation ancestor/descendant age guard when editing a person's birth date
        // Ensures that even if a middle parent has no birth date recorded, editing a grandparent's or grandchild's
        // birth date cannot create a 3-year-old grandmother above a 27-year-old grandchild.
        issues.addAll(validatePersonMultiGenerationAgeConsistency(person, allPersons))

        return ValidationResult(issues.distinctBy { "${it.type}|${it.title}|${it.message}" })
    }

    /**
     * Multi-Generation Biological Age Feasibility Check (`12 * N` years minimum gap across `N >= 2` steps).
     * Walks upward through biological ancestors of [parent] and downward through biological descendants of [child]
     * so that even when an intermediate parent has no birth date recorded, an ancestor `N` steps above a descendant
     * must be born at least `12 * N` years earlier (or strictly earlier if an adoptive link is present).
     */
    fun validateMultiGenerationAgeFeasibility(
        parent: Person,
        child: Person,
        role: String,
        isAdoptiveLink: Boolean,
        allPersons: List<Person>
    ): List<LinkIssue> {
        val issues = mutableListOf<LinkIssue>()
        val personMap = normalizePersonMap(allPersons)
        if (parent.id.isNotBlank()) personMap[parent.id.trim().lowercase()] = parent
        if (child.id.isNotBlank()) personMap[child.id.trim().lowercase()] = child

        // Collect ancestors of parent:Pair(Person, stepsAboveParent, hasAdoptiveStep)
        val ancestors = collectAncestorsWithDepth(parent, personMap, maxDepth = 5)
        // Collect descendants of child:Pair(Person, stepsBelowChild, hasAdoptiveStep)
        val descendants = collectDescendantsWithDepth(child, personMap, maxDepth = 5)

        for ((ancestor, upSteps, upHasAdoptive) in ancestors) {
            val ancYear = extractYear(ancestor.birthDate) ?: continue
            for ((descendant, downSteps, downHasAdoptive) in descendants) {
                val totalSteps = upSteps + 1 + downSteps
                if (totalSteps < 2) continue // 1-step direct parent-child is already checked in step 6
                if (isSameId(ancestor.id, descendant.id)) continue

                val descYear = extractYear(descendant.birthDate) ?: continue
                val gap = descYear - ancYear
                val anyAdoptiveInChain = isAdoptiveLink || upHasAdoptive || downHasAdoptive
                val minRequiredBioGap = 12 * totalSteps

                val ancestorLabel = when (totalSteps) {
                    2 -> if (ancestor.gender.equals("Female", ignoreCase = true)) "grandmother" else if (ancestor.gender.equals("Male", ignoreCase = true)) "grandfather" else "grandparent"
                    3 -> if (ancestor.gender.equals("Female", ignoreCase = true)) "great-grandmother" else if (ancestor.gender.equals("Male", ignoreCase = true)) "great-grandfather" else "great-grandparent"
                    else -> "${"great-".repeat((totalSteps - 2).coerceAtLeast(1))}grandparent"
                }
                val descendantLabel = when (totalSteps) {
                    2 -> if (descendant.gender.equals("Female", ignoreCase = true)) "granddaughter" else if (descendant.gender.equals("Male", ignoreCase = true)) "grandson" else "grandchild"
                    3 -> if (descendant.gender.equals("Female", ignoreCase = true)) "great-granddaughter" else if (descendant.gender.equals("Male", ignoreCase = true)) "great-grandson" else "great-grandchild"
                    else -> "${"great-".repeat((totalSteps - 2).coerceAtLeast(1))}grandchild"
                }

                if (gap <= 0) {
                    val youngerMsg = if (gap < 0) "${-gap} year(s) younger than" else "born in the exact same year as"
                    issues.add(
                        LinkIssue(
                            type = IssueType.FATAL_ERROR,
                            title = "Impossible Multi-Generation Age Gap (${ancestorLabel.replaceFirstChar { it.uppercase() }})",
                            message = "Cannot link ${parent.firstName} as $role of ${child.firstName}: " +
                                    "${ancestor.firstName} ${ancestor.lastName} (born $ancYear) would become the $ancestorLabel of " +
                                    "${descendant.firstName} ${descendant.lastName} (born $descYear), making the $ancestorLabel $youngerMsg their own $descendantLabel.",
                            field = role
                        )
                    )
                } else if (!anyAdoptiveInChain && gap < minRequiredBioGap) {
                    issues.add(
                        LinkIssue(
                            type = IssueType.FATAL_ERROR,
                            title = "Biologically Impossible Multi-Generation Age Gap",
                            message = "Cannot link ${parent.firstName} as $role of ${child.firstName}: " +
                                    "${ancestor.firstName} ${ancestor.lastName} (born $ancYear) is $totalSteps biological generations above " +
                                    "${descendant.firstName} ${descendant.lastName} (born $descYear) — a gap of only $gap year(s).\n\n" +
                                    "Across $totalSteps biological generations, a $ancestorLabel must be at least $minRequiredBioGap years older than their $descendantLabel (12 years minimum per generation).",
                            field = role
                        )
                    )
                }
            }
        }

        return issues
    }

    /**
     * Checks multi-generation age feasibility when editing an existing [person]'s birthDate directly,
     * even if their immediate parents or children have blank birth dates.
     */
    private fun validatePersonMultiGenerationAgeConsistency(
        person: Person,
        allPersons: List<Person>
    ): List<LinkIssue> {
        val issues = mutableListOf<LinkIssue>()
        val personYear = extractYear(person.birthDate) ?: return issues
        val personMap = normalizePersonMap(allPersons)
        if (person.id.isNotBlank()) personMap[person.id.trim().lowercase()] = person

        // Check ancestors >= 2 steps above person
        val ancestors = collectAncestorsWithDepth(person, personMap, maxDepth = 5)
        for ((ancestor, steps, hasAdoptive) in ancestors) {
            if (steps < 2) continue
            val ancYear = extractYear(ancestor.birthDate) ?: continue
            val gap = personYear - ancYear
            val minBioGap = 12 * steps
            val ancRole = if (steps == 2) "grandparent" else "${"great-".repeat(steps - 2)}grandparent"
            if (gap <= 0 || (!hasAdoptive && gap < minBioGap)) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Biologically Impossible Multi-Generation Age Gap",
                        message = "${ancestor.firstName} ${ancestor.lastName} (born $ancYear) is $steps generations above ${person.firstName} (born $personYear). " +
                                "A biological $ancRole must be at least $minBioGap years older than their descendant (found $gap year(s) difference).",
                        field = "birthDate"
                    )
                )
            }
        }

        // Check descendants >= 2 steps below person
        val descendants = collectDescendantsWithDepth(person, personMap, maxDepth = 5)
        for ((descendant, steps, hasAdoptive) in descendants) {
            if (steps < 2) continue
            val descYear = extractYear(descendant.birthDate) ?: continue
            val gap = descYear - personYear
            val minBioGap = 12 * steps
            val ancRole = if (steps == 2) "grandparent" else "${"great-".repeat(steps - 2)}grandparent"
            if (gap <= 0 || (!hasAdoptive && gap < minBioGap)) {
                issues.add(
                    LinkIssue(
                        type = IssueType.FATAL_ERROR,
                        title = "Biologically Impossible Multi-Generation Age Gap",
                        message = "${person.firstName} ${person.lastName} (born $personYear) is $steps generations above ${descendant.firstName} ${descendant.lastName} (born $descYear). " +
                                "A biological $ancRole must be at least $minBioGap years older than their descendant (found $gap year(s) difference).",
                        field = "birthDate"
                    )
                )
            }
        }

        return issues
    }

    private fun collectAncestorsWithDepth(
        start: Person,
        personMap: Map<String, Person>,
        maxDepth: Int
    ): List<Triple<Person, Int, Boolean>> {
        val result = mutableListOf<Triple<Person, Int, Boolean>>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Triple<Person, Int, Boolean>>()
        queue.add(Triple(start, 0, false))
        if (start.id.isNotBlank()) visited.add(start.id.trim().lowercase())

        while (queue.isNotEmpty()) {
            val (curr, depth, hasAdoptive) = queue.removeFirst()
            result.add(Triple(curr, depth, hasAdoptive))
            if (depth >= maxDepth) continue

            val fId = curr.fatherId?.trim()?.lowercase()
            if (!fId.isNullOrBlank() && visited.add(fId)) {
                val fObj = personMap[fId]
                val isStep = curr.fatherRelationshipType.equals("Step", ignoreCase = true)
                val isAdopt = curr.fatherRelationshipType.equals("Adoptive", ignoreCase = true)
                if (fObj != null && !isStep) {
                    queue.add(Triple(fObj, depth + 1, hasAdoptive || isAdopt))
                }
            }

            val mId = curr.motherId?.trim()?.lowercase()
            if (!mId.isNullOrBlank() && visited.add(mId)) {
                val mObj = personMap[mId]
                val isStep = curr.motherRelationshipType.equals("Step", ignoreCase = true)
                val isAdopt = curr.motherRelationshipType.equals("Adoptive", ignoreCase = true)
                if (mObj != null && !isStep) {
                    queue.add(Triple(mObj, depth + 1, hasAdoptive || isAdopt))
                }
            }
        }
        return result
    }

    private fun collectDescendantsWithDepth(
        start: Person,
        personMap: Map<String, Person>,
        maxDepth: Int
    ): List<Triple<Person, Int, Boolean>> {
        val result = mutableListOf<Triple<Person, Int, Boolean>>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Triple<Person, Int, Boolean>>()
        queue.add(Triple(start, 0, false))
        if (start.id.isNotBlank()) visited.add(start.id.trim().lowercase())

        while (queue.isNotEmpty()) {
            val (curr, depth, hasAdoptive) = queue.removeFirst()
            result.add(Triple(curr, depth, hasAdoptive))
            if (depth >= maxDepth || curr.id.isBlank()) continue

            val children = personMap.values.filter {
                isSameId(it.fatherId, curr.id) || isSameId(it.motherId, curr.id)
            }
            for (c in children) {
                val cId = c.id.trim().lowercase()
                if (cId.isNotBlank() && visited.add(cId)) {
                    val isFather = isSameId(c.fatherId, curr.id)
                    val relType = if (isFather) c.fatherRelationshipType else c.motherRelationshipType
                    val isStep = relType.equals("Step", ignoreCase = true)
                    val isAdopt = relType.equals("Adoptive", ignoreCase = true)
                    if (!isStep) {
                        queue.add(Triple(c, depth + 1, hasAdoptive || isAdopt))
                    }
                }
            }
        }
        return result
    }

    // ══════════════════════════════════════════════════════════════
    // CANDIDATE FILTERING HELPERS FOR DROPDOWNS & PICKERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Resolves all linked spouses (current [Person.spouseId], [Person.formerSpouseIds], reciprocal links,
     * and existing co-parent links) of [person] from [allPersons].
     */
    fun findLinkedSpouses(person: Person?, allPersons: List<Person>): List<Person> {
        if (person == null || person.id.isBlank()) return emptyList()
        val result = mutableListOf<Person>()
        fun addUnique(p: Person?) {
            if (p != null && !isSameId(p.id, person.id) && result.none { isSameId(it.id, p.id) }) {
                result.add(p)
            }
        }
        for (sId in person.allSpouseIds()) {
            addUnique(findPersonInList(sId, allPersons))
        }
        allPersons.forEach { other ->
            if (!isSameId(other.id, person.id) && other.hasSpouseId(person.id)) {
                addUnique(other)
            }
        }
        allPersons.forEach { other ->
            if (!isSameId(other.id, person.id) && areCoParents(person.id, other.id, allPersons)) {
                addUnique(other)
            }
        }
        return result
    }

    /**
     * Resolves the primary linked spouse or existing co-parent partner of [person] from [allPersons].
     * Checks direct `spouseId`, `formerSpouseIds`, reciprocal links, and existing co-parent links.
     */
    fun findLinkedSpouse(person: Person?, allPersons: List<Person>): Person? {
        return findLinkedSpouses(person, allPersons).firstOrNull()
    }

    fun getEligibleFathers(
        child: Person,
        existingMother: Person?,
        allPersons: List<Person>,
        preferredOtherParentId: String? = null
    ): List<Person> {
        val existingParents = getExistingParentsInfo(child, allPersons)
        if (existingParents.hasTwoParents) return emptyList()
        if (existingParents.father != null && child.fatherId != null) {
            // Already has a father
            return emptyList()
        }

        val mother = existingMother ?: findPersonInList(child.motherId, allPersons)
        val childEffective = if (mother != null && child.motherId.isNullOrBlank()) child.copy(motherId = mother.id) else child

        val eligible = allPersons.filter { candidate ->
            if (candidate.gender.lowercase() != "male") return@filter false
            if (child.id.isNotBlank() && isSameId(candidate.id, child.id)) return@filter false

            val parentCheck = validateParentChild(candidate, childEffective, "father", allPersons)
            if (!parentCheck.isValid) return@filter false

            if (mother != null && mother.id.isNotBlank()) {
                val coParentCheck = validateCoParents(candidate, mother, allPersons, skipDeepTraces = true)
                if (!coParentCheck.isValid) return@filter false
            }

            true
        }

        val linkedHusbands = findLinkedSpouses(mother, allPersons)
        return eligible.sortedWith(
            compareBy<Person> { candidate ->
                when {
                    !preferredOtherParentId.isNullOrBlank() && isSameId(candidate.id, preferredOtherParentId) -> 0
                    mother != null && isSameId(candidate.id, mother.spouseId) -> 1
                    linkedHusbands.any { isSameId(candidate.id, it.id) } -> 2
                    else -> 3
                }
            }.thenBy { "${it.firstName} ${it.lastName}".trim().lowercase() }
        )
    }

    fun getEligibleMothers(
        child: Person,
        existingFather: Person?,
        allPersons: List<Person>,
        preferredOtherParentId: String? = null
    ): List<Person> {
        val existingParents = getExistingParentsInfo(child, allPersons)
        if (existingParents.hasTwoParents) return emptyList()
        if (existingParents.mother != null && child.motherId != null) {
            // Already has a mother
            return emptyList()
        }

        val father = existingFather ?: findPersonInList(child.fatherId, allPersons)
        val childEffective = if (father != null && child.fatherId.isNullOrBlank()) child.copy(fatherId = father.id) else child

        val eligible = allPersons.filter { candidate ->
            if (candidate.gender.lowercase() != "female") return@filter false
            if (child.id.isNotBlank() && isSameId(candidate.id, child.id)) return@filter false

            val parentCheck = validateParentChild(candidate, childEffective, "mother", allPersons)
            if (!parentCheck.isValid) return@filter false

            if (father != null && father.id.isNotBlank()) {
                val coParentCheck = validateCoParents(father, candidate, allPersons, skipDeepTraces = true)
                if (!coParentCheck.isValid) return@filter false
            }

            true
        }

        val linkedWives = findLinkedSpouses(father, allPersons)
        return eligible.sortedWith(
            compareBy<Person> { candidate ->
                when {
                    !preferredOtherParentId.isNullOrBlank() && isSameId(candidate.id, preferredOtherParentId) -> 0
                    father != null && isSameId(candidate.id, father.spouseId) -> 1
                    linkedWives.any { isSameId(candidate.id, it.id) } -> 2
                    else -> 3
                }
            }.thenBy { "${it.firstName} ${it.lastName}".trim().lowercase() }
        )
    }

    fun getEligibleSpouses(
        person: Person,
        allPersons: List<Person>
    ): List<Person> {
        val baseMap = normalizePersonMap(allPersons)
        val pGender = person.gender.trim().lowercase()
        val aId = if (person.id.isNotBlank()) person.id.trim().lowercase() else "temp_a"

        return allPersons.filter { candidate ->
            if (person.id.isNotBlank() && isSameId(candidate.id, person.id)) return@filter false

            // Fast gender pre-filter before running full bloodline consanguinity traversal
            if (!marriageValidationEngine.allowSameGenderSpouse && pGender.isNotEmpty()) {
                val cGender = candidate.gender.trim().lowercase()
                if (cGender.isEmpty() || cGender == pGender) return@filter false
            }

            val isAlreadyLinkedToCandidate = person.hasSpouseId(candidate.id) || candidate.hasSpouseId(person.id)
            val personHasOtherPriorSpouse = person.allSpouseIds().any { !isSameId(it, candidate.id) }
            val effectiveMDate = when {
                isAlreadyLinkedToCandidate -> {
                    val isHistorical = (person.formerSpouseIds.any { isSameId(it, candidate.id) } && !isSameId(person.spouseId, candidate.id)) ||
                        (candidate.formerSpouseIds.any { isSameId(it, person.id) } && !isSameId(candidate.spouseId, person.id))
                    if (isHistorical) {
                        if (!candidate.isLiving || candidate.deathDate.isNotBlank()) candidate.marriageDate
                        else if (!person.isLiving || person.deathDate.isNotBlank()) person.marriageDate
                        else person.marriageDate.ifBlank { candidate.marriageDate }
                    } else {
                        person.marriageDate.ifBlank { candidate.marriageDate }
                    }
                }
                personHasOtherPriorSpouse -> candidate.marriageDate
                else -> person.marriageDate.ifBlank { candidate.marriageDate }
            }
            if (!validateMarriageDeathChronology(person, candidate, effectiveMDate).isValid) {
                return@filter false
            }

            val sId = if (candidate.id.isNotBlank()) candidate.id.trim().lowercase() else "temp_s"
            val prevA = baseMap[aId]
            val prevS = baseMap[sId]
            baseMap[aId] = person.copy(id = aId, marriageDate = effectiveMDate)
            baseMap[sId] = candidate.copy(id = sId, marriageDate = effectiveMDate)

            val allowed = marriageValidationEngine.validateSpouseConnection(aId, sId, baseMap).isAllowed

            if (prevA != null) baseMap[aId] = prevA else baseMap.remove(aId)
            if (prevS != null) baseMap[sId] = prevS else baseMap.remove(sId)

            allowed
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

        return false
    }

    /**
     * Checks whether [a] and [b] are unrelated step-siblings (share zero biological/adoptive parents,
     * but a parent of [a] is married to a parent of [b]). Step-siblings have 0 degrees of consanguinity.
     */
    fun isStepSiblingOf(a: Person, b: Person, allPersons: List<Person>): Boolean {
        if (isSameId(a.id, b.id)) return false
        if (isSiblingOf(a, b, allPersons)) return false
        if (allPersons.isEmpty()) return false

        val aFatherObj = findPersonInList(a.fatherId, allPersons)
        val aMotherObj = findPersonInList(a.motherId, allPersons)
        val bFatherObj = findPersonInList(b.fatherId, allPersons)
        val bMotherObj = findPersonInList(b.motherId, allPersons)

        val step1 = aFatherObj != null && bMotherObj != null && areSpouses(aFatherObj.id, bMotherObj.id, allPersons)
        val step2 = aMotherObj != null && bFatherObj != null && areSpouses(aMotherObj.id, bFatherObj.id, allPersons)
        val step3 = aFatherObj != null && bFatherObj != null && areSpouses(aFatherObj.id, bFatherObj.id, allPersons)
        val step4 = aMotherObj != null && bMotherObj != null && areSpouses(aMotherObj.id, bMotherObj.id, allPersons)
        return step1 || step2 || step3 || step4
    }

    fun areSpouses(id1: String?, id2: String?, allPersons: List<Person>): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        if (isSameId(id1, id2)) return false
        val p1 = findPersonInList(id1, allPersons)
        val p2 = findPersonInList(id2, allPersons)
        if (p1 != null && p1.hasSpouseId(id2)) return true
        if (p2 != null && p2.hasSpouseId(id1)) return true
        return allPersons.any {
            (isSameId(it.id, id1) && it.hasSpouseId(id2)) ||
            (isSameId(it.id, id2) && it.hasSpouseId(id1))
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
