package com.example.btproject2.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class Person(
    val id: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val suffix: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val birthPlace: String = "",
    val motherId: String? = null,
    val fatherId: String? = null,
    val spouseId: String? = null,
    val formerSpouseIds: List<String> = emptyList(),
    val treeId: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val maritalStatus: String = "Single",
    @get:PropertyName("isLiving") @set:PropertyName("isLiving") var isLiving: Boolean = true,
    val deathDate: String = "",
    val deathPlace: String = "",
    val biography: String = "",
    @get:PropertyName("isPrivate") @set:PropertyName("isPrivate") var isPrivate: Boolean = false,
    val marriageDate: String = "",
    val fatherRelationshipType: String = "Biological",
    val motherRelationshipType: String = "Biological",
    val photoUri: String = "",
    val photoBase64: String = "",
    val documents: List<AttachedDocument> = emptyList(),
    val updatedAt: Long = 0L,
    val hasTimelineConflict: Boolean = false,
    val lineageRole: String = ""
) {
    /**
     * Returns all recorded spouse IDs (both the current/latest [spouseId] and any [formerSpouseIds]),
     * preserving order and removing blanks or duplicates.
     */
    @Exclude
    fun allSpouseIds(): List<String> {
        val result = mutableListOf<String>()
        if (!spouseId.isNullOrBlank()) {
            result.add(spouseId.trim())
        }
        for (fId in formerSpouseIds) {
            val trimmed = fId.trim()
            if (trimmed.isNotEmpty() && result.none { it.equals(trimmed, ignoreCase = true) }) {
                result.add(trimmed)
            }
        }
        return result
    }

    /**
     * Returns true if [targetId] matches either the current [spouseId] or any ID in [formerSpouseIds].
     */
    @Exclude
    fun hasSpouseId(targetId: String?): Boolean {
        if (targetId.isNullOrBlank()) return false
        val target = targetId.trim()
        return allSpouseIds().any { it.equals(target, ignoreCase = true) }
    }

    /**
     * Returns a new [Person] with [newSpouseId] set as the active [spouseId], while automatically
     * preserving any previous [spouseId] inside [formerSpouseIds] so remarriage never erases a 1st spouse.
     */
    @Exclude
    fun withLinkedSpouse(newSpouseId: String, newMaritalStatus: String = "Married"): Person {
        val cleanNew = newSpouseId.trim()
        val updatedFormer = formerSpouseIds
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cleanNew, ignoreCase = true) }
            .toMutableList()
        val prevSpouse = spouseId?.trim()
        if (!prevSpouse.isNullOrEmpty() &&
            !prevSpouse.equals(cleanNew, ignoreCase = true) &&
            updatedFormer.none { it.equals(prevSpouse, ignoreCase = true) }
        ) {
            updatedFormer.add(prevSpouse)
        }
        return copy(
            spouseId = cleanNew,
            formerSpouseIds = updatedFormer,
            maritalStatus = newMaritalStatus
        )
    }

    /**
     * Appends [formerId] into [formerSpouseIds] without overwriting an active [spouseId].
     * Used when a historical/deceased prior spouse is linked retroactively.
     */
    @Exclude
    fun withAddedFormerSpouse(formerId: String): Person {
        val cleanFormer = formerId.trim()
        if (cleanFormer.isEmpty() || spouseId?.trim()?.equals(cleanFormer, ignoreCase = true) == true) {
            return this
        }
        val updatedFormer = formerSpouseIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        if (updatedFormer.none { it.equals(cleanFormer, ignoreCase = true) }) {
            updatedFormer.add(cleanFormer)
        }
        return copy(formerSpouseIds = updatedFormer)
    }
}
