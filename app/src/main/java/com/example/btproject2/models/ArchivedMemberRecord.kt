package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * ArchivedMemberRecord — Soft-delete archive for family members deleted from any tree.
 * Preserves the full Person object and topology so the System Admin can restore them
 * from the Ops & Recovery Console with 1 tap.
 */
@IgnoreExtraProperties
data class ArchivedMemberRecord(
    val id: String = "",
    val treeId: String = "",
    val treeName: String = "",
    val person: Person = Person(),
    val deletedBy: String = "",
    val deletedByName: String = "",
    val deletedAt: Long = System.currentTimeMillis(),
    val originalFatherId: String? = null,
    val originalMotherId: String? = null,
    val originalSpouseId: String? = null,
    val originalFormerSpouseIds: List<String> = emptyList(),
    val formerFatherChildIds: List<String> = emptyList(),
    val formerMotherChildIds: List<String> = emptyList(),
    @get:PropertyName("isRestored") @set:PropertyName("isRestored") var isRestored: Boolean = false
)
