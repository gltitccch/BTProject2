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
    val distanceB: Int,
    val legalStatus: String = "No Consanguinity Impediment",
    val legalArticle: String = "N/A",
    val isMarriageProhibited: Boolean = false,
    val legalDescription: String = "No biological relationship found.",
    val commonAncestors: List<Person> = listOfNotNull(commonAncestor),
    val bloodlineSubtype: String = ""
) {
    /**
     * Formatted display label for all co-equal nearest common ancestors (MRCAs).
     * When two people share both ancestral parents/grandparents at the same minimum distance,
     * both names are shown together (e.g., "Arthur Malone & Beatrice Hayes").
     */
    val commonAncestorDisplayName: String
        get() = when {
            commonAncestors.size >= 2 -> commonAncestors.joinToString(" & ") { "${it.firstName} ${it.lastName}".trim() }
            commonAncestors.size == 1 -> "${commonAncestors[0].firstName} ${commonAncestors[0].lastName}".trim()
            commonAncestor != null -> "${commonAncestor.firstName} ${commonAncestor.lastName}".trim()
            else -> ""
        }
}