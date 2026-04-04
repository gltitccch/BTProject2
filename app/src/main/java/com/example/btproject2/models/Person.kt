package com.example.btproject2.models

data class Person(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val motherId: String? = null,
    val fatherId: String? = null,
    val spouseId: String? = null,
    val treeId: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)