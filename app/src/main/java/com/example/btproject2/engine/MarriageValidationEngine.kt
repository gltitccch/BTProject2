package com.example.btproject2.engine

import com.example.btproject2.models.Person
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale

/**
 * Marriage and Relationship Validation Engine for Philippine Family Trees.
 *
 * Implements the marriage prohibitions and impediments under Philippine Law:
 * - Article 1 & 2 of the Family Code of the Philippines (Essential Requisites & Gender Union)
 * - Article 5 of the Family Code of the Philippines & RA 11596 (Minimum Marriage Age of 18 / Child Marriage Prohibition)
 * - Articles 35(4) & 40 of the Family Code of the Philippines (Bigamy & Prior Existing Marriage)
 * - Article 37 of the Family Code of the Philippines (Incestuous Marriages: Lineal & Siblings)
 * - Article 38 of the Family Code of the Philippines (Void for Reasons of Public Policy: Collateral to 4th Degree, Affinity & In-Laws)
 * - Articles 963–967 of the Civil Code of the Philippines (Consanguinity Computation)
 * - Universal Genealogical Graph Integrity (Self-ancestry & Cycle Prevention)
 */
class MarriageValidationEngine(
    private val bloodEngine: BloodRelationshipEngine = BloodRelationshipEngine(),
    val minimumSpouseAge: Int = DEFAULT_MINIMUM_SPOUSE_AGE,
    val allowSameGenderSpouse: Boolean = DEFAULT_ALLOW_SAME_GENDER_SPOUSE
) {

    companion object {
        const val DEFAULT_MINIMUM_SPOUSE_AGE = 18
        const val DEFAULT_ALLOW_SAME_GENDER_SPOUSE = false

        /**
         * Calculates the chronological age of a person in years using their date of birth whenever available.
         * Uses [referenceDate] (defaults to current system time) or death date if deceased.
         * Returns null if birth date is unrecorded.
         */
        fun calculateAge(person: Person, referenceDate: Calendar = Calendar.getInstance()): Int? {
            if (person.birthDate.isBlank()) return null
            val trimmed = person.birthDate.trim()

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
                        val birthCal = Calendar.getInstance().apply { time = parsed }
                        val targetCal = if (!person.isLiving && person.deathDate.isNotBlank()) {
                            parseDateToCalendar(person.deathDate) ?: referenceDate
                        } else {
                            referenceDate
                        }
                        var age = targetCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
                        if (targetCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
                            age--
                        }
                        return age.coerceAtLeast(0)
                    }
                } catch (_: Exception) {
                }
            }

            // Fallback for 4-digit year format (e.g., "2010")
            val yearMatch = Regex("""\b(18|19|20)\d{2}\b""").find(trimmed)
            if (yearMatch != null) {
                val birthYear = yearMatch.value.toIntOrNull()
                if (birthYear != null) {
                    val targetYear = if (!person.isLiving && person.deathDate.isNotBlank()) {
                        extractYear(person.deathDate) ?: referenceDate.get(Calendar.YEAR)
                    } else {
                        referenceDate.get(Calendar.YEAR)
                    }
                    return (targetYear - birthYear).coerceAtLeast(0)
                }
            }

            return null
        }

        private fun parseDateToCalendar(dateStr: String): Calendar? {
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
                "dd/MM/yyyy"
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
                    return Calendar.getInstance().apply { set(yr, Calendar.JANUARY, 1) }
                }
            }
            return null
        }

        private fun extractYear(dateStr: String): Int? {
            val match = Regex("""\b(18|19|20)\d{2}\b""").find(dateStr)
            return match?.value?.toIntOrNull()
        }
    }

    enum class SpouseValidationFailureReason {
        SELF_MARRIAGE,
        MINIMUM_AGE,
        GENDER_NOT_ALLOWED,
        ACTIVE_SPOUSE_EXISTS,
        LINEAL_CONSANGUINITY,
        SIBLING_CONSANGUINITY,
        COLLATERAL_CONSANGUINITY_4TH_DEGREE,
        PARENT_SPOUSE_CONFLICT,
        STEP_RELATIONSHIP,
        AFFINITY_IN_LAW,
        IMPOSSIBLE_CYCLE,
        MARRIAGE_AFTER_DEATH
    }

    /**
     * Clear, structured result for an attempted marriage or relationship link.
     */
    data class RelationshipValidationResult(
        val isAllowed: Boolean,
        val reason: String,
        val legalBasis: String,
        val relationshipType: String,
        val degree: Int,
        val familyPath: List<Person>,
        val explanation: String = reason,
        val failedValidations: List<SpouseValidationFailureReason> = emptyList(),
        val failureMessages: List<String> = emptyList()
    )

    // =========================================================================
    // SECTION 1: SPOUSE / PARTNER VALIDATION
    // =========================================================================

    /**
     * Validates whether [personAId] and [personBId] can legally and genealogically be linked as spouses.
     * Evaluates Age, Gender, Existing Spouse, and Family Relationships.
     *
     * @param personAId The ID of the first individual.
     * @param personBId The ID of the proposed spouse.
     * @param personMap Complete lookup map of individuals in the family tree.
     * @return [RelationshipValidationResult] containing permission status, reason, legal citation, degree, and path.
     */
    fun validateSpouseConnection(
        personAId: String,
        personBId: String,
        personMap: Map<String, Person>
    ): RelationshipValidationResult {
        val personA = personMap[personAId]
            ?: throw IllegalArgumentException("Person A ($personAId) not found in family tree.")
        val personB = personMap[personBId]
            ?: throw IllegalArgumentException("Person B ($personBId) not found in family tree.")

        // ---------------------------------------------------------------------
        // RULE 6: Self-Relationship Check (Individual Identity)
        // ---------------------------------------------------------------------
        if (personAId.trim().equals(personBId.trim(), ignoreCase = true)) {
            val selfMsg = "A person cannot marry themselves. An individual cannot be linked as their own spouse."
            return RelationshipValidationResult(
                isAllowed = false,
                reason = selfMsg,
                legalBasis = "Civil Law Principle of Individual Identity",
                relationshipType = "Self",
                degree = 0,
                familyPath = listOf(personA),
                failedValidations = listOf(SpouseValidationFailureReason.SELF_MARRIAGE),
                failureMessages = listOf(selfMsg)
            )
        }

        val failures = mutableListOf<SpouseValidationFailureReason>()
        val failureMessages = mutableListOf<String>()
        val legalBases = mutableListOf<String>()

        // ---------------------------------------------------------------------
        // 1. AGE VALIDATION
        // ---------------------------------------------------------------------
        val ageA = calculateAge(personA)
        val ageB = calculateAge(personB)
        val isAUnderage = ageA != null && ageA < minimumSpouseAge
        val isBUnderage = ageB != null && ageB < minimumSpouseAge

        if (isAUnderage || isBUnderage) {
            failures.add(SpouseValidationFailureReason.MINIMUM_AGE)
            val ageMsg = when {
                isAUnderage && isBUnderage -> {
                    "Both ${personA.firstName} and ${personB.firstName} do not meet the minimum age requirement for a spouse relationship (minimum age is $minimumSpouseAge years; ${personA.firstName} is $ageA years old and ${personB.firstName} is $ageB years old)."
                }
                isAUnderage -> {
                    "${personA.firstName} ${personA.lastName} does not meet the minimum age requirement for a spouse relationship (minimum age is $minimumSpouseAge years; currently $ageA years old)."
                }
                else -> {
                    "${personB.firstName} ${personB.lastName} does not meet the minimum age requirement for a spouse relationship (minimum age is $minimumSpouseAge years; currently $ageB years old)."
                }
            }
            failureMessages.add("$ageMsg Under Article 5 of the Family Code of the Philippines and Republic Act No. 11596, marriage of any person under the age of 18 is void ab initio.")
            legalBases.add("Article 5, Family Code of the Philippines & Republic Act No. 11596")
        }

        // ---------------------------------------------------------------------
        // 2. GENDER VALIDATION
        // ---------------------------------------------------------------------
        val genderA = personA.gender.trim().lowercase()
        val genderB = personB.gender.trim().lowercase()
        if (!allowSameGenderSpouse && genderA.isNotBlank() && genderB.isNotBlank() && genderA == genderB) {
            failures.add(SpouseValidationFailureReason.GENDER_NOT_ALLOWED)
            val displayGender = personA.gender.trim().replaceFirstChar { it.uppercase() }
            val genderMsg = "The spouse relationship is not allowed under the application’s current gender relationship rules. Philippine Family Code Articles 1 and 2 define marriage as a permanent union between a male and a female, but both ${personA.firstName} and ${personB.firstName} are recorded as $displayGender."
            failureMessages.add(genderMsg)
            legalBases.add("Articles 1 & 2, Family Code of the Philippines")
        }

        // ---------------------------------------------------------------------
        // 2b. MARRIAGE DATE VS. DEATH DATE VALIDATION
        // ---------------------------------------------------------------------
        val mDateStr = personA.marriageDate.ifBlank { personB.marriageDate }
        if (mDateStr.isNotBlank()) {
            val mCal = parseDateToCalendar(mDateStr)
            if (!personA.isLiving && personA.deathDate.isNotBlank()) {
                val dCal = parseDateToCalendar(personA.deathDate)
                val isAfter = if (mCal != null && dCal != null) mCal.after(dCal)
                else {
                    val mYr = extractYear(mDateStr)
                    val dYr = extractYear(personA.deathDate)
                    mYr != null && dYr != null && mYr > dYr
                }
                if (isAfter) {
                    failures.add(SpouseValidationFailureReason.MARRIAGE_AFTER_DEATH)
                    failureMessages.add("Marriage date cannot occur after ${personA.firstName} ${personA.lastName}'s date of death.")
                    legalBases.add("Chronological Feasibility & Philippine Civil Law")
                }
            }
            if (!personB.isLiving && personB.deathDate.isNotBlank()) {
                val dCal = parseDateToCalendar(personB.deathDate)
                val isAfter = if (mCal != null && dCal != null) mCal.after(dCal)
                else {
                    val mYr = extractYear(mDateStr)
                    val dYr = extractYear(personB.deathDate)
                    mYr != null && dYr != null && mYr > dYr
                }
                if (isAfter) {
                    failures.add(SpouseValidationFailureReason.MARRIAGE_AFTER_DEATH)
                    failureMessages.add("Marriage date cannot occur after ${personB.firstName} ${personB.lastName}'s date of death.")
                    legalBases.add("Chronological Feasibility & Philippine Civil Law")
                }
            }
        }

        // ---------------------------------------------------------------------
        // 3. EXISTING ACTIVE SPOUSE VALIDATION (BIGAMY PROHIBITION)
        // ---------------------------------------------------------------------
        val (hasActiveA, existingSpouseA) = hasActiveSpouse(personA, personB.id, personMap)
        val (hasActiveB, existingSpouseB) = hasActiveSpouse(personB, personA.id, personMap)

        if (hasActiveA || hasActiveB) {
            failures.add(SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS)
            val spouseAName = existingSpouseA?.let { "${it.firstName} ${it.lastName}".trim() } ?: "an existing spouse"
            val spouseBName = existingSpouseB?.let { "${it.firstName} ${it.lastName}".trim() } ?: "an existing spouse"
            val activeSpouseMsg = when {
                hasActiveA && hasActiveB -> {
                    "Both ${personA.firstName} and ${personB.firstName} are already married (already have active spouse relationships: ${personA.firstName} is married to $spouseAName and ${personB.firstName} is married to $spouseBName). Under Philippine law (Articles 35(4) and 40, Family Code of the Philippines), a person cannot enter into a new spouse relationship while an existing marriage is active unless the previous relationship has been properly ended through death, annulment, divorce, or legal separation."
                }
                hasActiveA -> {
                    "${personA.firstName} ${personA.lastName} is already married to $spouseAName (already has an active spouse relationship). Under Philippine law (Articles 35(4) and 40, Family Code of the Philippines), a person cannot enter into a new spouse relationship while an existing marriage is active unless the previous relationship has been properly ended through death, annulment, divorce, or legal separation."
                }
                else -> {
                    "${personB.firstName} ${personB.lastName} is already married to $spouseBName (already has an active spouse relationship). Under Philippine law (Articles 35(4) and 40, Family Code of the Philippines), a person cannot enter into a new spouse relationship while an existing marriage is active unless the previous relationship has been properly ended through death, annulment, divorce, or legal separation."
                }
            }
            failureMessages.add(activeSpouseMsg)
            legalBases.add("Articles 35 (4) & 40, Family Code of the Philippines")
        }

        // ---------------------------------------------------------------------
        // 4. BIOLOGICAL PARENT AND SPOUSE CONFLICT (RULE 5)
        // ---------------------------------------------------------------------
        val isABioParentOfB = (isSameId(personB.fatherId, personA.id) && isBiological(personB.fatherRelationshipType)) ||
                (isSameId(personB.motherId, personA.id) && isBiological(personB.motherRelationshipType))
        val isBBioParentOfA = (isSameId(personA.fatherId, personB.id) && isBiological(personA.fatherRelationshipType)) ||
                (isSameId(personA.motherId, personB.id) && isBiological(personA.motherRelationshipType))

        if (isABioParentOfB || isBBioParentOfA) {
            failures.add(SpouseValidationFailureReason.PARENT_SPOUSE_CONFLICT)
            val parent = if (isABioParentOfB) personA else personB
            val child = if (isABioParentOfB) personB else personA
            failureMessages.add("A person cannot be linked as both biological parent and spouse of the same person. ${parent.firstName} ${parent.lastName} is already registered as the biological parent of ${child.firstName} ${child.lastName}.")
            legalBases.add("Article 37 (1), Family Code of the Philippines & Universal Genealogical Integrity")
        }

        // ---------------------------------------------------------------------
        // 5. BLOOD RELATIONSHIP VALIDATION (CONSANGUINITY)
        // ---------------------------------------------------------------------
        val bloodResult = bloodEngine.determineBloodRelationship(personAId, personBId, personMap)

        // Lineal blood relatives of ANY degree
        if (bloodResult.category == BloodRelationshipEngine.BloodCategory.LINEAL) {
            failures.add(SpouseValidationFailureReason.LINEAL_CONSANGUINITY)
            val degreeStr = "${bloodResult.degree}${getOrdinalSuffix(bloodResult.degree)}"
            val lineDesc = when (bloodResult.degree) {
                1 -> "Parent and Child"
                2 -> "Grandparent and Grandchild"
                3 -> "Great-Grandparent and Great-Grandchild"
                else -> "${bloodResult.degree}-generation Direct Lineal Descendant"
            }
            failureMessages.add("Marriage between lineal blood relatives of any degree is not allowed. ${personA.firstName} and ${personB.firstName} have a $lineDesc relationship ($degreeStr degree lineal consanguinity). Under Article 37 (1) of the Family Code of the Philippines, marriages between ascendants and descendants of any degree (whether legitimate or illegitimate) are incestuous and void from the beginning (void ab initio).")
            legalBases.add("Article 37 (1), Family Code of the Philippines")
        }

        // Full or half siblings
        if (bloodResult.category == BloodRelationshipEngine.BloodCategory.COLLATERAL && bloodResult.degree == 2) {
            failures.add(SpouseValidationFailureReason.SIBLING_CONSANGUINITY)
            val isFull = isFullSibling(personA, personB)
            val sibType = if (isFull) "Full Siblings" else "Half Siblings"
            failureMessages.add("Marriage between full siblings or half siblings is not allowed. ${personA.firstName} and ${personB.firstName} are $sibType. Under Article 37 (2) of the Family Code of the Philippines, marriages between brothers and sisters, whether of the full or half blood, are incestuous and void from the beginning (void ab initio).")
            legalBases.add("Article 37 (2), Family Code of the Philippines")
        }

        // Collateral up to 4th civil degree
        if (bloodResult.category == BloodRelationshipEngine.BloodCategory.COLLATERAL && bloodResult.degree in 3..4) {
            failures.add(SpouseValidationFailureReason.COLLATERAL_CONSANGUINITY_4TH_DEGREE)
            val degreeStr = "${bloodResult.degree}${getOrdinalSuffix(bloodResult.degree)}"
            val mrcaName = bloodResult.commonAncestor?.let { "${it.firstName} ${it.lastName}" } ?: "a common biological ancestor"
            val displayLabel = when (bloodResult.degree) {
                3 -> "Uncle/Aunt & Niece/Nephew"
                4 -> if (bloodResult.generationsA == 2 && bloodResult.generationsB == 2) "First Cousins" else "Great-Uncle/Aunt & Grandniece/Nephew"
                else -> bloodResult.relationshipLabel
            }
            failureMessages.add("Marriage between collateral blood relatives up to the fourth civil degree is not allowed. ${personA.firstName} and ${personB.firstName} are $displayLabel ($degreeStr degree collateral consanguinity sharing $mrcaName). Under Article 38 (1) of the Family Code of the Philippines, marriages between collateral blood relatives, whether legitimate or illegitimate, up to the fourth civil degree are void from the beginning for reasons of public policy (void ab initio).")
            legalBases.add("Article 38 (1), Family Code of the Philippines")
        }

        // ---------------------------------------------------------------------
        // 6. AFFINITY AND STEP-RELATIONSHIP VALIDATION (ART. 38(3) & 38(4))
        // ---------------------------------------------------------------------
        val aFather = personA.fatherId?.let { personMap[it] }
        val aMother = personA.motherId?.let { personMap[it] }
        val bFather = personB.fatherId?.let { personMap[it] }
        val bMother = personB.motherId?.let { personMap[it] }

        val isBStepParentOfA = (aFather != null && isSameId(aFather.spouseId, personB.id)) ||
                (aMother != null && isSameId(aMother.spouseId, personB.id))
        val isAStepParentOfB = (bFather != null && isSameId(bFather.spouseId, personA.id)) ||
                (bMother != null && isSameId(bMother.spouseId, personA.id))

        if (isBStepParentOfA || isAStepParentOfB) {
            failures.add(SpouseValidationFailureReason.STEP_RELATIONSHIP)
            failureMessages.add("Marriage between step-parents and step-children is not allowed. ${personA.firstName} and ${personB.firstName} have a step-parent and step-child relationship. Under Article 38 (3) of the Family Code of the Philippines, marriage between step-parents and step-children is void from the beginning for reasons of public policy (void ab initio).")
            legalBases.add("Article 38 (3), Family Code of the Philippines")
        }

        val isBChildInLawOfA = personMap.values.any {
            (isSameId(it.fatherId, personA.id) || isSameId(it.motherId, personA.id)) && isSameId(it.spouseId, personB.id)
        }
        val isAChildInLawOfB = personMap.values.any {
            (isSameId(it.fatherId, personB.id) || isSameId(it.motherId, personB.id)) && isSameId(it.spouseId, personA.id)
        }
        if (isBChildInLawOfA || isAChildInLawOfB) {
            failures.add(SpouseValidationFailureReason.AFFINITY_IN_LAW)
            failureMessages.add("Marriage between parents-in-law and children-in-law is not allowed. ${personA.firstName} and ${personB.firstName} have a parent-in-law and child-in-law relationship. Under Article 38 (4) of the Family Code of the Philippines, marriage between parents-in-law and children-in-law is void from the beginning for reasons of public policy (void ab initio).")
            legalBases.add("Article 38 (4), Family Code of the Philippines")
        }

        // ---------------------------------------------------------------------
        // 7. COMPILE FINAL RESULT
        // ---------------------------------------------------------------------
        if (failures.isNotEmpty()) {
            val primaryReason = failureMessages.joinToString("\n\n")
            val primaryLegal = legalBases.distinct().joinToString("; ")
            val relType = buildRelationshipTypeLabel(failures, bloodResult, personA, personB)
            val path = if (bloodResult.familyPath.isNotEmpty()) bloodResult.familyPath else listOf(personA, personB)

            return RelationshipValidationResult(
                isAllowed = false,
                reason = primaryReason,
                legalBasis = primaryLegal,
                relationshipType = relType,
                degree = bloodResult.degree,
                familyPath = path,
                failedValidations = failures,
                failureMessages = failureMessages
            )
        }

        // Allowed: Collateral from 5th civil degree onward
        if (bloodResult.category == BloodRelationshipEngine.BloodCategory.COLLATERAL && bloodResult.degree >= 5) {
            val degreeStr = "${bloodResult.degree}${getOrdinalSuffix(bloodResult.degree)}"
            val mrcaName = bloodResult.commonAncestor?.let { "${it.firstName} ${it.lastName}" } ?: "a shared biological ancestor"
            val displayLabel = when {
                (bloodResult.generationsA == 2 && bloodResult.generationsB == 3) || (bloodResult.generationsA == 3 && bloodResult.generationsB == 2) -> "First Cousins Once Removed"
                bloodResult.generationsA == 3 && bloodResult.generationsB == 3 -> "Second Cousins"
                else -> bloodResult.relationshipLabel
            }
            return RelationshipValidationResult(
                isAllowed = true,
                reason = "Allowed to marry. Although ${personA.firstName} and ${personB.firstName} share $mrcaName ($displayLabel, $degreeStr degree collateral consanguinity), collateral blood relatives from the fifth civil degree onward have no civil legal impediment under Philippine Family Code Articles 37 and 38.",
                legalBasis = "Articles 37 & 38, Family Code of the Philippines (No Civil Legal Impediment)",
                relationshipType = "$displayLabel ($degreeStr Degree Collateral Consanguinity)",
                degree = bloodResult.degree,
                familyPath = bloodResult.familyPath
            )
        }

        // Allowed: Unrelated individuals
        return RelationshipValidationResult(
            isAllowed = true,
            reason = "Allowed to marry. No biological blood relationship or consanguinity impediment exists between ${personA.firstName} and ${personB.firstName}.",
            legalBasis = "Family Code of the Philippines (Eligible)",
            relationshipType = "Unrelated Persons",
            degree = 0,
            familyPath = emptyList()
        )
    }

    /**
     * Calculates the chronological age of a person in years using their date of birth whenever available.
     * Uses [referenceDate] (defaults to current system time) or death date if deceased.
     * Returns null if birth date is unrecorded.
     */
    fun calculateAge(person: Person, referenceDate: Calendar = Calendar.getInstance()): Int? =
        Companion.calculateAge(person, referenceDate)

    /**
     * Checks whether [person] already has an active spouse relationship in [personMap].
     * An existing marriage is not considered active if properly ended through:
     * - Death of the spouse (isLiving == false or deathDate set)
     * - Dissolution (maritalStatus is Divorced, Annulled, Separated, or Widowed)
     */
    fun hasActiveSpouse(
        person: Person,
        candidateSpouseId: String,
        personMap: Map<String, Person>
    ): Pair<Boolean, Person?> {
        val existingSpouseId = person.spouseId?.trim()
        val existingSpouse = if (!existingSpouseId.isNullOrBlank() && !isSameId(existingSpouseId, candidateSpouseId)) {
            personMap[existingSpouseId]
        } else {
            // Check reverse link: someone else in tree pointing to person as spouse
            personMap.values.find {
                isSameId(it.spouseId, person.id) && !isSameId(it.id, candidateSpouseId) && !isSameId(it.id, person.id)
            }
        }

        if (existingSpouse == null) {
            // Check if person has an un-ended marriage status with an unknown/unloaded spouse ID
            if (!existingSpouseId.isNullOrBlank() && !isSameId(existingSpouseId, candidateSpouseId)) {
                val terminatedStatuses = setOf("divorced", "annulled", "separated", "widowed")
                if (person.maritalStatus.trim().lowercase() !in terminatedStatuses) {
                    return Pair(true, Person(id = existingSpouseId, firstName = "existing", lastName = "spouse"))
                }
            }
            return Pair(false, null)
        }

        // 1. Check death of spouse
        val isSpouseDeceased = !existingSpouse.isLiving || existingSpouse.deathDate.isNotBlank()
        if (isSpouseDeceased) {
            return Pair(false, null)
        }

        // 2. Check marital status indicating termination
        val terminatedStatuses = setOf("divorced", "annulled", "separated", "widowed")
        val personStatus = person.maritalStatus.trim().lowercase()
        val spouseStatus = existingSpouse.maritalStatus.trim().lowercase()

        if (personStatus in terminatedStatuses || spouseStatus in terminatedStatuses) {
            return Pair(false, null)
        }

        return Pair(true, existingSpouse)
    }

    private fun parseDateToCalendar(dateStr: String): Calendar? = Companion.parseDateToCalendar(dateStr)

    private fun extractYear(dateStr: String): Int? = Companion.extractYear(dateStr)

    private fun buildRelationshipTypeLabel(
        failures: List<SpouseValidationFailureReason>,
        bloodResult: BloodRelationshipEngine.BloodRelationshipResult,
        personA: Person,
        personB: Person
    ): String {
        if (failures.size == 1) {
            return when (failures.first()) {
                SpouseValidationFailureReason.SELF_MARRIAGE -> "Self"
                SpouseValidationFailureReason.MINIMUM_AGE -> "Underage Member"
                SpouseValidationFailureReason.GENDER_NOT_ALLOWED -> "Same-Gender Union"
                SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS -> "Active Existing Marriage"
                SpouseValidationFailureReason.PARENT_SPOUSE_CONFLICT -> "Biological Parent and Child (Lineal)"
                SpouseValidationFailureReason.LINEAL_CONSANGUINITY -> {
                    val lineDesc = when (bloodResult.degree) {
                        1 -> "Parent and Child"
                        2 -> "Grandparent and Grandchild"
                        3 -> "Great-Grandparent and Great-Grandchild"
                        else -> "${bloodResult.degree}-generation Direct Lineal Descendant"
                    }
                    "$lineDesc (Lineal Consanguinity)"
                }
                SpouseValidationFailureReason.SIBLING_CONSANGUINITY -> {
                    val isFull = isFullSibling(personA, personB)
                    val sibType = if (isFull) "Full Siblings" else "Half Siblings"
                    "$sibType (2nd Degree Collateral Consanguinity)"
                }
                SpouseValidationFailureReason.COLLATERAL_CONSANGUINITY_4TH_DEGREE -> {
                    val degreeStr = "${bloodResult.degree}${getOrdinalSuffix(bloodResult.degree)}"
                    val displayLabel = when (bloodResult.degree) {
                        3 -> "Uncle/Aunt & Niece/Nephew"
                        4 -> if (bloodResult.generationsA == 2 && bloodResult.generationsB == 2) "First Cousins" else "Great-Uncle/Aunt & Grandniece/Nephew"
                        else -> bloodResult.relationshipLabel
                    }
                    "$displayLabel ($degreeStr Degree Collateral Consanguinity)"
                }
                SpouseValidationFailureReason.STEP_RELATIONSHIP -> "Step-Parent and Step-Child"
                SpouseValidationFailureReason.AFFINITY_IN_LAW -> "Parent-in-Law and Child-in-Law"
                SpouseValidationFailureReason.IMPOSSIBLE_CYCLE -> "Impossible Ancestry Cycle"
                SpouseValidationFailureReason.MARRIAGE_AFTER_DEATH -> "Post-Mortem Marriage Error"
            }
        }

        val labels = mutableListOf<String>()
        if (failures.contains(SpouseValidationFailureReason.MINIMUM_AGE)) labels.add("Underage Member")
        if (failures.contains(SpouseValidationFailureReason.GENDER_NOT_ALLOWED)) labels.add("Same-Gender Union")
        if (failures.contains(SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS)) labels.add("Active Existing Marriage")
        if (failures.contains(SpouseValidationFailureReason.PARENT_SPOUSE_CONFLICT)) labels.add("Parent-Spouse Conflict")
        if (failures.contains(SpouseValidationFailureReason.LINEAL_CONSANGUINITY)) labels.add("Lineal Relatives")
        if (failures.contains(SpouseValidationFailureReason.SIBLING_CONSANGUINITY)) {
            val isFull = isFullSibling(personA, personB)
            labels.add(if (isFull) "Full Siblings" else "Half Siblings")
        }
        if (failures.contains(SpouseValidationFailureReason.COLLATERAL_CONSANGUINITY_4TH_DEGREE)) labels.add("Collateral Relatives (4th Degree or Below)")
        if (failures.contains(SpouseValidationFailureReason.STEP_RELATIONSHIP)) labels.add("Step-Relation")
        if (failures.contains(SpouseValidationFailureReason.AFFINITY_IN_LAW)) labels.add("In-Law Relation")
        if (failures.contains(SpouseValidationFailureReason.MARRIAGE_AFTER_DEATH)) labels.add("Marriage After Death")
        return labels.joinToString(", ").ifEmpty { "Prohibited Spouse Relationship" }
    }

    // =========================================================================
    // SECTION 2: PARENT-CHILD & ANCESTRY VALIDATION
    // =========================================================================

    /**
     * Validates whether [parentId] can be linked as a parent of [childId].
     * Evaluates Rules 5, 6, 7, and 8.
     *
     * @param parentId The ID of the proposed parent.
     * @param childId The ID of the proposed child.
     * @param relationshipType "Biological" or "Adoptive".
     * @param personMap Complete lookup map of individuals in the family tree.
     * @return [RelationshipValidationResult] containing permission status, reason, legal citation, degree, and path.
     */
    fun validateParentChildConnection(
        parentId: String,
        childId: String,
        relationshipType: String = "Biological",
        personMap: Map<String, Person>
    ): RelationshipValidationResult {
        val parent = personMap[parentId]
            ?: throw IllegalArgumentException("Parent ($parentId) not found in family tree.")
        val child = personMap[childId]
            ?: throw IllegalArgumentException("Child ($childId) not found in family tree.")

        // ---------------------------------------------------------------------
        // RULE 6: A person cannot become their own ancestor or descendant
        // ---------------------------------------------------------------------
        if (parentId.trim().equals(childId.trim(), ignoreCase = true)) {
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "A person cannot become their own ancestor or descendant. ${parent.firstName} ${parent.lastName} cannot be linked as their own parent.",
                legalBasis = "Universal Genealogical Graph Integrity",
                relationshipType = "Self-Parenting (Self-Cycle)",
                degree = 0,
                familyPath = listOf(parent)
            )
        }

        // ---------------------------------------------------------------------
        // RULE 5: A person cannot be linked as both biological parent and spouse
        // ---------------------------------------------------------------------
        val areSpouses = isSameId(parent.spouseId, child.id) || isSameId(child.spouseId, parent.id)
        if (areSpouses && isBiological(relationshipType)) {
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "A person cannot be linked as both biological parent and spouse of the same person. ${parent.firstName} ${parent.lastName} and ${child.firstName} ${child.lastName} are currently registered as spouses.",
                legalBasis = "Article 37 (1), Family Code of the Philippines & Universal Genealogical Integrity",
                relationshipType = "Spouse as Biological Parent",
                degree = 1,
                familyPath = listOf(parent, child)
            )
        }

        // ---------------------------------------------------------------------
        // RULE 7: Family connections must not create impossible cycles
        // (e.g., child becoming parent of their own parent, or loop ancestry)
        // ---------------------------------------------------------------------
        val cyclePath = findAncestryPath(ancestorId = child.id, descendantId = parent.id, personMap = personMap)
        if (cyclePath != null && cyclePath.isNotEmpty()) {
            // cyclePath is [parent (descendant), ..., child (ancestor)]. Appending parent gives the closed cycle loop.
            val fullLoop = cyclePath + parent
            val cycleLength = cyclePath.size - 1
            val cycleDesc = if (cycleLength == 1) {
                "${child.firstName} is already registered as the parent of ${parent.firstName}"
            } else {
                "${child.firstName} is already an ancestor (${cycleLength} generations up) of ${parent.firstName}"
            }
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "Family connections must not create impossible cycles, such as a child becoming the parent of their own parent. $cycleDesc. Linking ${parent.firstName} as parent of ${child.firstName} creates a circular ancestry loop.",
                legalBasis = "Universal Genealogical Graph Integrity (Cycle Prevention)",
                relationshipType = "Impossible Ancestry Cycle",
                degree = cycleLength,
                familyPath = fullLoop
            )
        }

        // ---------------------------------------------------------------------
        // RULE 8: Son-in-law or daughter-in-law must be connected as spouse of
        // an existing child; NEVER as a biological child of the parent-in-law.
        // ---------------------------------------------------------------------
        if (isBiological(relationshipType)) {
            // Case 8A: Candidate child is married to an existing child of parent
            val parentChildren = personMap.values.filter {
                isSameId(it.fatherId, parent.id) || isSameId(it.motherId, parent.id)
            }
            for (pChild in parentChildren) {
                if (isSameId(pChild.spouseId, child.id) || isSameId(child.spouseId, pChild.id)) {
                    val inLawTitle = if (child.gender.equals("female", ignoreCase = true)) "daughter-in-law" else "son-in-law"
                    val inLawLabel = if (child.gender.equals("female", ignoreCase = true)) "Daughter-in-Law" else "Son-in-Law"
                    val childName = "${child.firstName} ${child.lastName}".trim()
                    val parentName = "${parent.firstName} ${parent.lastName}".trim()
                    val spouseChildName = "${pChild.firstName} ${pChild.lastName}".trim()

                    return RelationshipValidationResult(
                        isAllowed = false,
                        reason = "A son-in-law or daughter-in-law must be connected as the spouse of an existing child. They must never be connected as a biological child of the parent-in-law. $childName is the $inLawTitle of $parentName (married to $parentName's child, $spouseChildName).",
                        legalBasis = "Article 38 (4), Family Code of the Philippines & Philippine Family Law on Affinity",
                        relationshipType = "$inLawLabel (Affinity)",
                        degree = 1,
                        familyPath = listOf(parent, pChild, child)
                    )
                }
            }

            // Case 8B: Parent is married to an existing child of child (inversion of in-law)
            val childChildren = personMap.values.filter {
                isSameId(it.fatherId, child.id) || isSameId(it.motherId, child.id)
            }
            for (cChild in childChildren) {
                if (isSameId(cChild.spouseId, parent.id) || isSameId(parent.spouseId, cChild.id)) {
                    val inLawTitle = if (parent.gender.equals("female", ignoreCase = true)) "daughter-in-law" else "son-in-law"
                    val inLawLabel = if (parent.gender.equals("female", ignoreCase = true)) "Daughter-in-Law" else "Son-in-Law"
                    val parentName = "${parent.firstName} ${parent.lastName}".trim()
                    val childName = "${child.firstName} ${child.lastName}".trim()
                    val spouseChildName = "${cChild.firstName} ${cChild.lastName}".trim()

                    return RelationshipValidationResult(
                        isAllowed = false,
                        reason = "A son-in-law or daughter-in-law must be connected as the spouse of an existing child. They must never be connected as a biological parent of their parent-in-law. $parentName is the $inLawTitle of $childName (married to $childName's child, $spouseChildName).",
                        legalBasis = "Article 38 (4), Family Code of the Philippines & Philippine Family Law on Affinity",
                        relationshipType = "$inLawLabel (Affinity)",
                        degree = 1,
                        familyPath = listOf(child, cChild, parent)
                    )
                }
            }
        }

        // ---------------------------------------------------------------------
        // ALLOWED PARENT-CHILD CONNECTION
        // ---------------------------------------------------------------------
        return RelationshipValidationResult(
            isAllowed = true,
            reason = "Valid parent-child connection. No cycles, in-law conflicts, or biological incest impediments detected.",
            legalBasis = "Genealogical Graph Integrity (Valid)",
            relationshipType = "$relationshipType Parent and Child",
            degree = 1,
            familyPath = listOf(parent, child)
        )
    }

    // =========================================================================
    // GRAPH HELPER METHODS
    // =========================================================================

    /**
     * Discovers a directed ancestry path from [ancestorId] down to [descendantId].
     * Returns the ordered list of persons: [ancestor, ..., descendant], or null if no path exists.
     */
    fun findAncestryPath(
        ancestorId: String,
        descendantId: String,
        personMap: Map<String, Person>
    ): List<Person>? {
        if (ancestorId.isBlank() || descendantId.isBlank()) return null
        val targetAncestorId = ancestorId.trim().lowercase()
        val startDescendant = personMap[descendantId] ?: return null

        data class SearchNode(val person: Person, val path: List<Person>)
        val queue = ArrayDeque<SearchNode>()
        val visited = mutableSetOf<String>()

        queue.add(SearchNode(startDescendant, listOf(startDescendant)))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentPerson = current.person

            // Ascend to biological or legal parents
            val parents = listOfNotNull(
                currentPerson.fatherId?.let { personMap[it] },
                currentPerson.motherId?.let { personMap[it] }
            )

            for (p in parents) {
                if (p.id.trim().lowercase() == targetAncestorId) {
                    return current.path + p // Returns [descendant, ..., ancestor]
                }

                if (visited.add(p.id.trim().lowercase())) {
                    queue.add(SearchNode(p, current.path + p))
                }
            }
        }

        return null
    }

    private fun isFullSibling(a: Person, b: Person): Boolean {
        val sameFather = !a.fatherId.isNullOrBlank() && !b.fatherId.isNullOrBlank() &&
                isSameId(a.fatherId, b.fatherId) && isBiological(a.fatherRelationshipType) && isBiological(b.fatherRelationshipType)
        val sameMother = !a.motherId.isNullOrBlank() && !b.motherId.isNullOrBlank() &&
                isSameId(a.motherId, b.motherId) && isBiological(a.motherRelationshipType) && isBiological(b.motherRelationshipType)
        return sameFather && sameMother
    }

    private fun isSameId(id1: String?, id2: String?): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        return id1.trim().equals(id2.trim(), ignoreCase = true)
    }

    private fun isBiological(relType: String?): Boolean {
        return relType.isNullOrBlank() || relType.equals("Biological", ignoreCase = true)
    }

    private fun getOrdinalSuffix(n: Int): String {
        return when {
            n in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}

