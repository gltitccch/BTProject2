package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class PrivacySettings(
    val treeId: String = "",
    @get:PropertyName("isPrivateTree") @set:PropertyName("isPrivateTree") var isPrivateTree: Boolean = true,
    val hideBirthDates: Boolean = false,
    val hideNotes: Boolean = false,
    val hideDeceasedStatus: Boolean = false,
    // Backward compatibility fields
    val publicTree: Boolean = false,
    val hideLivingMembers: Boolean = false,
    val requireLogin: Boolean = true,
    val viewerApproval: Boolean = false
)