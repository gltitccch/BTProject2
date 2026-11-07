package com.example.btproject2.engine

import com.example.btproject2.models.Person
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asynchronous relationship coordination engine for the KinTrace family tree.
 * 
 * Responsibilities:
 * 1. Indexes tree relations (spouses, parents, children, living state) once in memory.
 * 2. Delivers fast-path candidate resolutions (< 5ms) for UI spinners, eliminating main-thread freezes.
 * 3. Prevents recursive re-entrant spinner update loops.
 * 4. Coordinates authoritative deep validation before writes.
 */
class FamilyRelationshipCoordinator(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private var indexedMembers: List<Person> = emptyList()
    private var memberMapById: Map<String, Person> = emptyMap()
    private var maleMembersList: List<Person> = emptyList()
    private var femaleMembersList: List<Person> = emptyList()

    /**
     * Indexes tree members in memory. Safe to call on background thread.
     */
    suspend fun indexTreeMembers(members: List<Person>) = withContext(defaultDispatcher) {
        val distinct = members.distinctBy { it.id.trim().lowercase() }
        indexedMembers = distinct
        memberMapById = distinct.associateBy { it.id.trim().lowercase() }
        maleMembersList = distinct.filter { it.gender.equals("Male", ignoreCase = true) }
        femaleMembersList = distinct.filter { it.gender.equals("Female", ignoreCase = true) }
    }

    fun getIndexedMembers(): List<Person> = indexedMembers

    /**
     * Resolves eligible fathers for [child] synchronously using fast indexed lookups (< 1ms).
     */
    fun resolveEligibleFathersFast(
        child: Person,
        currentMother: Person?,
        preferredOtherParentId: String? = null
    ): List<Person> {
        val motherId = currentMother?.id?.trim()?.lowercase().orEmpty()
        val childId = child.id.trim().lowercase()

        // 1. Direct mother linked spouses
        val motherSpouseId = currentMother?.spouseId?.trim()?.lowercase().orEmpty()
        val motherFormerSpouseIds = currentMother?.formerSpouseIds?.map { it.trim().lowercase() }?.toSet().orEmpty()

        val eligible = maleMembersList.filter { candidate ->
            val candId = candidate.id.trim().lowercase()
            if (candId.isBlank()) return@filter false
            if (candId == childId) return@filter false
            if (candId == motherId) return@filter false

            // Cannot be parent to own parent
            if (child.fatherId?.trim()?.lowercase() == candId) return@filter true
            if (candidate.fatherId?.trim()?.lowercase() == childId || candidate.motherId?.trim()?.lowercase() == childId) return@filter false

            // Fast biological age sanity: candidate cannot be born after or within < 12 years of child
            val candYear = FamilyLinkValidator.extractYear(candidate.birthDate)
            val childYear = FamilyLinkValidator.extractYear(child.birthDate)
            if (candYear != null && childYear != null) {
                val gap = childYear - candYear
                val isAdoptive = child.fatherRelationshipType.equals("Adoptive", ignoreCase = true)
                if (gap <= 0 || (!isAdoptive && gap < 12)) return@filter false
            }

            // Quick incest sanity: Candidate and mother cannot share the exact same parents (full/half siblings)
            if (currentMother != null) {
                val candF = candidate.fatherId?.trim()?.lowercase().orEmpty()
                val candM = candidate.motherId?.trim()?.lowercase().orEmpty()
                val motF = currentMother.fatherId?.trim()?.lowercase().orEmpty()
                val motM = currentMother.motherId?.trim()?.lowercase().orEmpty()

                val sharesFather = candF.isNotBlank() && candF == motF
                val sharesMother = candM.isNotBlank() && candM == motM
                if (sharesFather || sharesMother) return@filter false
            }

            true
        }

        return eligible.sortedWith(
            compareBy<Person> { candidate ->
                val candId = candidate.id.trim().lowercase()
                when {
                    !preferredOtherParentId.isNullOrBlank() && candId == preferredOtherParentId.trim().lowercase() -> 0
                    motherSpouseId.isNotBlank() && candId == motherSpouseId -> 1
                    motherFormerSpouseIds.contains(candId) -> 2
                    candidate.spouseId?.trim()?.lowercase() == motherId -> 3
                    else -> 4
                }
            }.thenBy { "${it.firstName} ${it.lastName}".trim().lowercase() }
        )
    }

    /**
     * Resolves eligible mothers for [child] synchronously using fast indexed lookups (< 1ms).
     */
    fun resolveEligibleMothersFast(
        child: Person,
        currentFather: Person?,
        preferredOtherParentId: String? = null
    ): List<Person> {
        val fatherId = currentFather?.id?.trim()?.lowercase().orEmpty()
        val childId = child.id.trim().lowercase()

        // 1. Direct father linked spouses
        val fatherSpouseId = currentFather?.spouseId?.trim()?.lowercase().orEmpty()
        val fatherFormerSpouseIds = currentFather?.formerSpouseIds?.map { it.trim().lowercase() }?.toSet().orEmpty()

        val eligible = femaleMembersList.filter { candidate ->
            val candId = candidate.id.trim().lowercase()
            if (candId.isBlank()) return@filter false
            if (candId == childId) return@filter false
            if (candId == fatherId) return@filter false

            // Cannot be parent to own parent
            if (child.motherId?.trim()?.lowercase() == candId) return@filter true
            if (candidate.fatherId?.trim()?.lowercase() == childId || candidate.motherId?.trim()?.lowercase() == childId) return@filter false

            // Fast biological age sanity: candidate cannot be born after or within < 12 years of child
            val candYear = FamilyLinkValidator.extractYear(candidate.birthDate)
            val childYear = FamilyLinkValidator.extractYear(child.birthDate)
            if (candYear != null && childYear != null) {
                val gap = childYear - candYear
                val isAdoptive = child.motherRelationshipType.equals("Adoptive", ignoreCase = true)
                if (gap <= 0 || (!isAdoptive && gap < 12)) return@filter false
            }

            // Quick incest sanity: Candidate and father cannot share the exact same parents (full/half siblings)
            if (currentFather != null) {
                val candF = candidate.fatherId?.trim()?.lowercase().orEmpty()
                val candM = candidate.motherId?.trim()?.lowercase().orEmpty()
                val fatF = currentFather.fatherId?.trim()?.lowercase().orEmpty()
                val fatM = currentFather.motherId?.trim()?.lowercase().orEmpty()

                val sharesFather = candF.isNotBlank() && candF == fatF
                val sharesMother = candM.isNotBlank() && candM == fatM
                if (sharesFather || sharesMother) return@filter false
            }

            true
        }

        return eligible.sortedWith(
            compareBy<Person> { candidate ->
                val candId = candidate.id.trim().lowercase()
                when {
                    !preferredOtherParentId.isNullOrBlank() && candId == preferredOtherParentId.trim().lowercase() -> 0
                    fatherSpouseId.isNotBlank() && candId == fatherSpouseId -> 1
                    fatherFormerSpouseIds.contains(candId) -> 2
                    candidate.spouseId?.trim()?.lowercase() == fatherId -> 3
                    else -> 4
                }
            }.thenBy { "${it.firstName} ${it.lastName}".trim().lowercase() }
        )
    }

    /**
     * Resolves eligible spouses for [person] synchronously using fast indexed lookups (< 1ms).
     */
    fun resolveEligibleSpousesFast(person: Person): List<Person> {
        val pId = person.id.trim().lowercase()
        val pGender = person.gender.trim().lowercase()
        val targetGender = if (pGender == "male") "female" else if (pGender == "female") "male" else ""

        return indexedMembers.filter { candidate ->
            val cId = candidate.id.trim().lowercase()
            if (cId.isBlank()) return@filter false
            if (pId.isNotBlank() && cId == pId) return@filter false

            // Gender compatibility under Philippine Family Law (Arts 1 & 2)
            if (targetGender.isNotBlank() && !candidate.gender.equals(targetGender, ignoreCase = true)) {
                return@filter false
            }

            // Cannot marry own direct parent or child
            if (candidate.fatherId?.trim()?.lowercase() == pId || candidate.motherId?.trim()?.lowercase() == pId) return@filter false
            if (person.fatherId?.trim()?.lowercase() == cId || person.motherId?.trim()?.lowercase() == cId) return@filter false

            // Cannot marry full or half sibling
            val pFat = person.fatherId?.trim()?.lowercase().orEmpty()
            val pMot = person.motherId?.trim()?.lowercase().orEmpty()
            val cFat = candidate.fatherId?.trim()?.lowercase().orEmpty()
            val cMot = candidate.motherId?.trim()?.lowercase().orEmpty()
            val sharesFather = pFat.isNotBlank() && pFat == cFat
            val sharesMother = pMot.isNotBlank() && pMot == cMot
            if (sharesFather || sharesMother) return@filter false

            true
        }.sortedBy { "${it.firstName} ${it.lastName}".trim().lowercase() }
    }
}
