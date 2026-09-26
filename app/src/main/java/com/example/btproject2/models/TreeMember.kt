package com.example.btproject2.models

data class TreeMember(
    val id: String = "",
    val treeId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val role: String = "Viewer", // "Owner", "Editor", "Viewer"
    val status: String = "Approved", // "Approved", "Pending"
    val joinedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

