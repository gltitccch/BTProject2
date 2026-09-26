package com.example.btproject2.models

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
    val hasTimelineConflict: Boolean = false
)
