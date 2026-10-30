package com.example.btproject2.engine

import com.example.btproject2.models.Person
import kotlin.math.abs

/**
 * MergeBridgeValidator
 *
 * Dedicated domain validation engine for cross-tree clan synthesis (Proposals 15 & 16 in KinTrace).
 * Enforces legal, biological, and genealogical integrity across all 4 clan bridge types:
 * 1. Spousal Union (💍)
 * 2. Parent-Child Link (👶)
 * 3. Shared Ancestor Deduplication (👥)
 * 4. Sibling & Lineage Bridge (👥)
 *
 * Implements strict compliance with:
 * - Articles 1 & 2, Family Code of the Philippines (Essential Requisites & Gender Union)
 * - Article 5, Family Code & RA 11596 (Minimum Marriage Age of 18)
 * - Articles 35(4) & 40, Family Code (Bigamy & Prior Existing Active Marriages)
 * - Articles 37 & 38, Family Code (Incestuous Marriages & Public Policy Impediments)
 * - Universal Genealogical Integrity (Anti-Loop DAG, Chronological Feasibility, Non-Reciprocal Prevention)
 */
class MergeBridgeValidator(
    private val marriageEngine: MarriageValidationEngine = MarriageValidationEngine()
) {

    enum class BridgeMode {
        SPOUSAL_UNION,
        PARENT_CHILD,
        SHARED_ANCESTOR,
        SIBLING_LINEAGE
    }

    data class ValidationResult(
        val isAllowed: Boolean,
        val reason: String,
        val details: List<String> = emptyList(),
        val isWarningOnly: Boolean = false
    ) {
        companion object {
            fun allowed(msg: String = "Valid bridge connection"): ValidationResult =
                ValidationResult(isAllowed = true, reason = msg)

            fun prohibited(reason: String, details: List<String> = emptyList()): ValidationResult =
                ValidationResult(isAllowed = false, reason = reason, details = details)

            fun warning(reason: String, details: List<String> = emptyList()): ValidationResult =
                ValidationResult(isAllowed = true, reason = reason, details = details, isWarningOnly = true)
        }
    }

    // =========================================================================
    // 1. 💍 SPOUSAL UNION VALIDATION
    // =========================================================================

    fun validateSpousalUnion(
        personA: Person,
        personB: Person,
        combinedMap: Map<String, Person>
    ): ValidationResult {
        // Self-marriage prohibition
        if (FamilyLinkValidator.isSameId(personA.id, personB.id)) {
            return ValidationResult.prohibited(
                "A person cannot marry themselves."
            )
        }

        // Active Prior Existing Marriage (Bigamy - Arts. 35(4) & 40)
        val aHasSpouse = isCurrentlyMarried(personA, combinedMap)
        val bHasSpouse = isCurrentlyMarried(personB, combinedMap)

        if (aHasSpouse || bHasSpouse) {
            val spouseAName = resolveSpouseName(personA, combinedMap)
            val spouseBName = resolveSpouseName(personB, combinedMap)

            val msg = when {
                aHasSpouse && bHasSpouse ->
                    "Both ${personA.firstName} ${personA.lastName} (married to $spouseAName) and ${personB.firstName} ${personB.lastName} (married to $spouseBName) already have active spouses. Under Article 35(4) of the Family Code of the Philippines, entering into a new marriage is bigamous and void ab initio."
                aHasSpouse ->
                    "${personA.firstName} ${personA.lastName} is already married to $spouseAName. Under Article 35(4) of the Family Code of the Philippines, entering into a new marriage is bigamous and void ab initio."
                else ->
                    "${personB.firstName} ${personB.lastName} is already married to $spouseBName. Under Article 35(4) of the Family Code of the Philippines, entering into a new marriage is bigamous and void ab initio."
            }
            return ValidationResult.prohibited(msg)
        }

        // Age Verification (Minimum age 18 - Art. 5 & RA 11596)
        val ageA = FamilyLinkValidator.calculateAgeYears(personA.birthDate, personA.deathDate.takeIf { !personA.isLiving })
        val ageB = FamilyLinkValidator.calculateAgeYears(personB.birthDate, personB.deathDate.takeIf { !personB.isLiving })

        if (ageA != null && ageA < 18) {
            return ValidationResult.prohibited(
                "${personA.firstName} ${personA.lastName} is $ageA years old. Under Article 5 of the Family Code and RA 11596, marriage of any person under the age of 18 is void ab initio."
            )
        }
        if (ageB != null && ageB < 18) {
            return ValidationResult.prohibited(
                "${personB.firstName} ${personB.lastName} is $ageB years old. Under Article 5 of the Family Code and RA 11596, marriage of any person under the age of 18 is void ab initio."
            )
        }

        // Consanguinity, Affinity & Family Code Prohibitions via MarriageValidationEngine
        val mveResult = marriageEngine.validateSpouseConnection(personA.id, personB.id, combinedMap)
        if (!mveResult.isAllowed) {
            return ValidationResult.prohibited(
                reason = mveResult.reason,
                details = mveResult.failureMessages
            )
        }

        return ValidationResult.allowed("💍 Lawful spousal union: Both members are free to marry and legally compliant.")
    }

    // =========================================================================
    // 2. 👶 PARENT-CHILD LINK VALIDATION
    // =========================================================================

    /**
     * Validates linking [proposedParent] as the parent of [proposedChild].
     */
    fun validateParentChildLink(
        proposedParent: Person,
        proposedChild: Person,
        combinedMap: Map<String, Person>
    ): ValidationResult {
        // Self-parentage prohibition
        if (FamilyLinkValidator.isSameId(proposedParent.id, proposedChild.id)) {
            return ValidationResult.prohibited("A person cannot be their own parent.")
        }

        val isFemale = proposedParent.gender.equals("Female", ignoreCase = true)
        val roleStr = if (isFemale) "mother" else "father"

        // Slot Availability Check: Prevent silent parent overwrite
        val existingParentId = if (isFemale) proposedChild.motherId else proposedChild.fatherId
        if (!existingParentId.isNullOrBlank() && !FamilyLinkValidator.isSameId(existingParentId, proposedParent.id)) {
            val existingParent = combinedMap[existingParentId.trim().lowercase()]
                ?: combinedMap.values.find { FamilyLinkValidator.isSameId(it.id, existingParentId) }
            val existingName = existingParent?.let { "${it.firstName} ${it.lastName}".trim() } ?: "an existing $roleStr"

            return ValidationResult.prohibited(
                "Child ${proposedChild.firstName} ${proposedChild.lastName} already has a biological $roleStr assigned ($existingName). A child cannot have multiple biological ${roleStr}s. Connecting ${proposedParent.firstName} would overwrite and detach their biological lineage."
            )
        }

        // Chronological Feasibility
        val parentBirthYear = FamilyLinkValidator.extractYear(proposedParent.birthDate)
        val childBirthYear = FamilyLinkValidator.extractYear(proposedChild.birthDate)

        if (parentBirthYear != null && childBirthYear != null) {
            if (parentBirthYear >= childBirthYear) {
                return ValidationResult.prohibited(
                    "Chronological Inversion: Proposed parent ${proposedParent.firstName} (born $parentBirthYear) cannot be born after or in the same year as child ${proposedChild.firstName} (born $childBirthYear)."
                )
            }
            val gap = childBirthYear - parentBirthYear
            if (gap < 12) {
                return ValidationResult.prohibited(
                    "Biologically impossible parent-child age gap: ${proposedParent.firstName} (born $parentBirthYear) is only $gap year(s) older than ${proposedChild.firstName} (born $childBirthYear). Minimum biological gap is 12 years."
                )
            }
        }

        // Mortality & Conception Limit: Parent deceased before child's birth
        if (!proposedParent.isLiving && proposedParent.deathDate.isNotBlank() && childBirthYear != null) {
            val parentDeathYear = FamilyLinkValidator.extractYear(proposedParent.deathDate)
            if (parentDeathYear != null) {
                if (isFemale && parentDeathYear < childBirthYear) {
                    return ValidationResult.prohibited(
                        "Biological impossibility: Mother ${proposedParent.firstName} passed away in $parentDeathYear, before child ${proposedChild.firstName} was born in $childBirthYear."
                    )
                } else if (!isFemale && parentDeathYear < childBirthYear - 1) {
                    return ValidationResult.prohibited(
                        "Biological impossibility: Father ${proposedParent.firstName} passed away in $parentDeathYear, more than 1 year prior to child ${proposedChild.firstName}'s birth in $childBirthYear."
                    )
                }
            }
        }

        // Cycle Prevention: Proposed child cannot already be an ancestor of proposed parent
        if (FamilyLinkValidator.isAncestorOf(proposedChild.id, proposedParent.id, combinedMap)) {
            return ValidationResult.prohibited(
                "Circular Ancestry Paradox: ${proposedChild.firstName} ${proposedChild.lastName} is already an ancestor of ${proposedParent.firstName} ${proposedParent.lastName}. Establishing this link would create an infinite genealogical loop."
            )
        }

        // Existing Spousal Conflict: Parent cannot be married to their child (Art. 37(1))
        val isSpouse = proposedParent.hasSpouseId(proposedChild.id) ||
                proposedChild.hasSpouseId(proposedParent.id)
        if (isSpouse) {
            return ValidationResult.prohibited(
                "A person cannot be both a biological parent and a spouse of the same individual. Under Article 37(1) of the Family Code, direct lineal parent-child marriages are incestuous and void ab initio."
            )
        }

        return ValidationResult.allowed("👶 Valid parent-child link: ${proposedParent.firstName} is biologically and genealogically compatible as $roleStr of ${proposedChild.firstName}.")
    }

    // =========================================================================
    // 3. 👥 SHARED ANCESTOR DEDUPLICATION VALIDATION
    // =========================================================================

    /**
     * Validates deduplicating [personA] (from Tree 1) and [personB] (from Tree 2) into a single individual.
     */
    fun validateSharedAncestor(
        personA: Person,
        personB: Person,
        combinedMap: Map<String, Person>
    ): ValidationResult {
        // Identity check
        if (FamilyLinkValidator.isSameId(personA.id, personB.id)) {
            return ValidationResult.prohibited("Cannot deduplicate a person with themselves.")
        }

        // Gender Compatibility
        val genderA = personA.gender.trim().lowercase()
        val genderB = personB.gender.trim().lowercase()
        if (genderA.isNotBlank() && genderB.isNotBlank() && genderA != genderB) {
            return ValidationResult.prohibited(
                "Gender Mismatch: Cannot merge ${personA.firstName} (${personA.gender}) and ${personB.firstName} (${personB.gender}) as the same person. Shared ancestors must share the same biological gender."
            )
        }

        // Chronological Consistency (Birth Year tolerance)
        val birthYearA = FamilyLinkValidator.extractYear(personA.birthDate)
        val birthYearB = FamilyLinkValidator.extractYear(personB.birthDate)
        if (birthYearA != null && birthYearB != null) {
            val diff = abs(birthYearA - birthYearB)
            if (diff > 5) {
                return ValidationResult.prohibited(
                    "Chronological Discrepancy: ${personA.firstName} was born in $birthYearA, while ${personB.firstName} was born in $birthYearB ($diff years difference). Individuals with divergent birth years cannot be merged as a shared ancestor."
                )
            }
        }

        // Dual-Spouse Bigamy Trap Prevention (allows merging if one marriage dissolved/widowed into a remarriage)
        val aHasSpouse = isCurrentlyMarried(personA, combinedMap)
        val bHasSpouse = isCurrentlyMarried(personB, combinedMap)
        if (aHasSpouse && bHasSpouse) {
            val spouseA = resolveSpouse(personA, combinedMap)
            val spouseB = resolveSpouse(personB, combinedMap)

            val areSpousesSame = spouseA != null && spouseB != null && (
                    FamilyLinkValidator.isSameId(spouseA.id, spouseB.id) ||
                            (spouseA.firstName.equals(spouseB.firstName, ignoreCase = true) &&
                                    spouseA.lastName.equals(spouseB.lastName, ignoreCase = true))
                    )

            val isOneSpouseDeceasedOrDissolved = (spouseA != null && (!spouseA.isLiving || spouseA.deathDate.isNotBlank())) ||
                    (spouseB != null && (!spouseB.isLiving || spouseB.deathDate.isNotBlank()))

            if (!areSpousesSame && !isOneSpouseDeceasedOrDissolved) {
                val spouseAName = spouseA?.let { "${it.firstName} ${it.lastName}".trim() } ?: "an existing spouse"
                val spouseBName = spouseB?.let { "${it.firstName} ${it.lastName}".trim() } ?: "an existing spouse"

                return ValidationResult.prohibited(
                    "Dual-Spouse Conflict (Bigamy Trap): ${personA.firstName} is married to $spouseAName in Tree 1, while ${personB.firstName} is married to $spouseBName in Tree 2. Merging them would create an illegal bigamous marriage with two conflicting living spouses under Article 35(4) of the Family Code."
                )
            }
        }

        // Conflicting Parents Warning
        val faId = personA.fatherId?.trim()
        val fbId = personB.fatherId?.trim()
        val maId = personA.motherId?.trim()
        val mbId = personB.motherId?.trim()

        if (!faId.isNullOrEmpty() && !fbId.isNullOrEmpty() && !FamilyLinkValidator.isSameId(faId, fbId)) {
            val fa = combinedMap[faId] ?: combinedMap[faId.lowercase()]
            val fb = combinedMap[fbId] ?: combinedMap[fbId.lowercase()]
            if (fa != null && fb != null && !fa.firstName.equals(fb.firstName, ignoreCase = true)) {
                return ValidationResult.prohibited(
                    "Conflicting Parental Lineage: ${personA.firstName} has father ${fa.firstName} ${fa.lastName}, but ${personB.firstName} has father ${fb.firstName} ${fb.lastName}. Deduplicating these records would corrupt ancestral lineage."
                )
            }
        }

        if (!maId.isNullOrEmpty() && !mbId.isNullOrEmpty() && !FamilyLinkValidator.isSameId(maId, mbId)) {
            val ma = combinedMap[maId] ?: combinedMap[maId.lowercase()]
            val mb = combinedMap[mbId] ?: combinedMap[mbId.lowercase()]
            if (ma != null && mb != null && !ma.firstName.equals(mb.firstName, ignoreCase = true)) {
                return ValidationResult.prohibited(
                    "Conflicting Parental Lineage: ${personA.firstName} has mother ${ma.firstName} ${ma.lastName}, but ${personB.firstName} has mother ${mb.firstName} ${mb.lastName}. Deduplicating these records would corrupt ancestral lineage."
                )
            }
        }

        return ValidationResult.allowed("👥 Valid shared ancestor: Both records represent the same individual and can be safely deduplicated.")
    }

    // =========================================================================
    // 4. 👥 SIBLING & IN-LAW LINEAGE BRIDGE VALIDATION
    // =========================================================================

    /**
     * Validates linking [personA] and [personB] as siblings via shared parental lineage.
     */
    fun validateSiblingBridge(
        personA: Person,
        personB: Person,
        combinedMap: Map<String, Person>
    ): ValidationResult {
        // Self-link prohibition
        if (FamilyLinkValidator.isSameId(personA.id, personB.id)) {
            return ValidationResult.prohibited("A person cannot be a sibling of themselves.")
        }

        // Existing Spousal Conflict: Prohibits brother-sister incestuous marriages (Art. 37(2))
        val isSpouse = personA.hasSpouseId(personB.id) ||
                personB.hasSpouseId(personA.id)
        if (isSpouse) {
            return ValidationResult.prohibited(
                "Under Article 37(2) of the Family Code of the Philippines, marriages between brothers and sisters (whether full or half blood) are incestuous and void ab initio. These individuals are currently recorded as spouses."
            )
        }

        // Lineal Ancestor Conflict: Cannot be parent/child or lineal ancestor
        if (FamilyLinkValidator.isAncestorOf(personA.id, personB.id, combinedMap)) {
            return ValidationResult.prohibited(
                "Generational Leap: ${personA.firstName} is already a lineal ancestor of ${personB.firstName}. They cannot be linked as siblings."
            )
        }
        if (FamilyLinkValidator.isAncestorOf(personB.id, personA.id, combinedMap)) {
            return ValidationResult.prohibited(
                "Generational Leap: ${personB.firstName} is already a lineal ancestor of ${personA.firstName}. They cannot be linked as siblings."
            )
        }

        // Chronological Gap Check (Warning/Prohibition on extreme age gap)
        val birthA = FamilyLinkValidator.extractYear(personA.birthDate)
        val birthB = FamilyLinkValidator.extractYear(personB.birthDate)
        if (birthA != null && birthB != null) {
            val gap = abs(birthA - birthB)
            if (gap > 35) {
                return ValidationResult.prohibited(
                    "Generational age gap: ${personA.firstName} (born $birthA) and ${personB.firstName} (born $birthB) have a $gap-year age difference, exceeding biologically viable maternal sibling spans."
                )
            }
        }

        return ValidationResult.allowed("👥 Valid sibling bridge: Clean shared lineage link between ${personA.firstName} and ${personB.firstName}.")
    }

    // =========================================================================
    // HELPER UTILITIES
    // =========================================================================

    private fun isCurrentlyMarried(person: Person, personMap: Map<String, Person>): Boolean {
        if (person.maritalStatus.equals("Single", ignoreCase = true) ||
            person.maritalStatus.equals("Divorced", ignoreCase = true) ||
            person.maritalStatus.equals("Annulled", ignoreCase = true) ||
            person.maritalStatus.equals("Widowed", ignoreCase = true)
        ) {
            return false
        }

        val sId = person.spouseId?.trim()
        if (!sId.isNullOrEmpty()) {
            val spouse = personMap[sId.lowercase()]
                ?: personMap.values.find { FamilyLinkValidator.isSameId(it.id, sId) }
            // If spouse passed away or dissolved, the person is legally free to remarry
            if (spouse != null) {
                if (!spouse.isLiving || spouse.deathDate.isNotBlank()) return false
                if (spouse.maritalStatus.equals("Divorced", ignoreCase = true) ||
                    spouse.maritalStatus.equals("Annulled", ignoreCase = true) ||
                    spouse.maritalStatus.equals("Separated", ignoreCase = true)
                ) {
                    return false
                }
                return true
            }
        }

        return person.maritalStatus.equals("Married", ignoreCase = true) && !sId.isNullOrEmpty()
    }

    private fun resolveSpouse(person: Person, personMap: Map<String, Person>): Person? {
        val sId = person.spouseId?.trim() ?: return null
        return personMap[sId.lowercase()]
            ?: personMap.values.find { FamilyLinkValidator.isSameId(it.id, sId) }
    }

    private fun resolveSpouseName(person: Person, personMap: Map<String, Person>): String {
        val spouse = resolveSpouse(person, personMap)
        return spouse?.let { "${it.firstName} ${it.lastName}".trim() } ?: "an existing spouse"
    }
}
