package com.example.btproject2.models

data class TraceResult(
    val personA: Person,
    val personB: Person,
    val isRelated: Boolean,
    val relationshipType: String,
    val relationshipCategory: String,
    val degreeOfConsanguinity: Int,
    val commonAncestor: Person?,
    val pathFromA: List<Person>,
    val pathFromB: List<Person>,
    val explanation: String,
    val distanceA: Int,
    val distanceB: Int
)