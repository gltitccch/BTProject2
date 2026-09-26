package com.example.btproject2.models

data class PrivacySettings(
    val treeId: String = "",
    val isPrivateTree: Boolean = true,
    val hideBirthDates: Boolean = false,
    val hideNotes: Boolean = false,
    val hideDeceasedStatus: Boolean = false,
    // Backward compatibility fields
    val publicTree: Boolean = false,
    val hideLivingMembers: Boolean = false,
    val requireLogin: Boolean = true,
    val viewerApproval: Boolean = false
)