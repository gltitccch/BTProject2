package com.example.btproject2.engine

import com.example.btproject2.models.Person

class RelationshipExplainer {

    fun explain(
        personA: Person,
        personB: Person,
        label: String,
        commonAncestor: Person?,
        distanceA: Int,
        distanceB: Int
    ): String {
        val nameA = "${personA.firstName} ${personA.lastName}"
        val nameB = "${personB.firstName} ${personB.lastName}"
        val ancestorName = if (commonAncestor != null) {
            "${commonAncestor.firstName} ${commonAncestor.lastName}"
        } else {
            "an unknown ancestor"
        }

        return when (label) {
            "Parent / Child" ->
                "$nameA and $nameB are in a direct parent-child relationship."

            "Grandparent / Grandchild" ->
                "$nameA and $nameB are grandparent and grandchild, " +
                        "separated by two generations in a direct bloodline."

            "Great-Grandparent / Great-Grandchild" ->
                "$nameA and $nameB are great-grandparent and great-grandchild, " +
                        "separated by three generations."

            "Siblings" ->
                "$nameA and $nameB are siblings because they share the same parent(s)."

            "Uncle/Aunt and Niece/Nephew" ->
                "$nameA and $nameB are uncle/aunt and niece/nephew. " +
                        "One is the sibling of the other's parent."

            "First Cousins" ->
                "$nameA and $nameB are first cousins because they share " +
                        "the same grandparent: $ancestorName."

            "First Cousin Once Removed" ->
                "$nameA and $nameB are first cousins once removed. " +
                        "They share a common ancestor ($ancestorName) but are " +
                        "at different generational levels."

            "Second Cousins" ->
                "$nameA and $nameB are second cousins because they share " +
                        "the same great-grandparent: $ancestorName."

            else ->
                "$nameA and $nameB are blood relatives through " +
                        "their shared ancestor $ancestorName. " +
                        "$nameA is $distanceA generation(s) away and " +
                        "$nameB is $distanceB generation(s) away from this ancestor."
        }
    }
}