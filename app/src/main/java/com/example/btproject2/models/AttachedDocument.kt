package com.example.btproject2.models

data class AttachedDocument(
    val id: String = "",
    val name: String = "",
    val fileUri: String = "",
    val fileType: String = "pdf", // "pdf", "image", "doc"
    val category: String = "Birth Certificate", // "Birth Certificate", "Government ID", "Marriage Certificate", "Other"
    val uploadedAt: Long = System.currentTimeMillis(),
    val fileSize: String = ""
)

