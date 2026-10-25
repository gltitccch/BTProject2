package com.example.btproject2.engine

import com.example.btproject2.models.Person

class RelationshipExplainer {

    fun explain(
        personA: Person,
        personB: Person,
        label: String,
        commonAncestor: Person?,
        distanceA: Int,
        distanceB: Int,
        commonAncestors: List<Person> = listOfNotNull(commonAncestor)
    ): String {
        val nameA = "${personA.firstName} ${personA.lastName}".trim()
        val nameB = "${personB.firstName} ${personB.lastName}".trim()
        val effectiveAncestors = if (commonAncestors.isNotEmpty()) commonAncestors else listOfNotNull(commonAncestor)
        val isDualAncestors = effectiveAncestors.size >= 2
        val ancestorName = when {
            effectiveAncestors.size >= 2 -> effectiveAncestors.joinToString(" & ") { "${it.firstName} ${it.lastName}".trim() }
            effectiveAncestors.size == 1 -> "${effectiveAncestors[0].firstName} ${effectiveAncestors[0].lastName}".trim()
            else -> "a shared ancestor"
        }

        return when (label) {
            "Parent / Child" ->
                "$nameA and $nameB are in a direct parent-child relationship (1st Civil Degree, Lineal)."

            "Grandparent / Grandchild" ->
                "$nameA and $nameB are grandparent and grandchild, separated by two generational steps in a direct bloodline (2nd Civil Degree, Lineal)."

            "Great-Grandparent / Great-Grandchild" ->
                "$nameA and $nameB are great-grandparent and great-grandchild, separated by three generations in a direct bloodline (3rd Civil Degree, Lineal)."

            "Great-Great-Grandparent / Great-Great-Grandchild" ->
                "$nameA and $nameB are great-great-grandparent and great-great-grandchild (4th Civil Degree, Lineal)."

            "Siblings" ->
                if (isDualAncestors) {
                    "$nameA and $nameB are full siblings because they share both biological parents ($ancestorName), separated by 2 civil degrees."
                } else {
                    "$nameA and $nameB are half-siblings because they share biological parent $ancestorName, separated by 2 civil degrees."
                }

            "Uncle/Aunt and Niece/Nephew" ->
                if (isDualAncestors) {
                    "$nameA and $nameB are full uncle/aunt and niece/nephew (3rd Civil Degree). One is the full sibling of the other's parent through common ancestors $ancestorName."
                } else {
                    "$nameA and $nameB are uncle/aunt and niece/nephew (3rd Civil Degree). One is the sibling of the other's parent through common ancestor $ancestorName."
                }

            "First Cousins" ->
                if (isDualAncestors) {
                    "$nameA and $nameB are full first cousins (4th Civil Degree, Collateral). They share both common grandparents ($ancestorName), with each being 2 generational steps away."
                } else {
                    "$nameA and $nameB are half-first cousins (4th Civil Degree, Collateral). They share common grandparent $ancestorName, with each being 2 generational steps away."
                }

            "Great-Uncle/Aunt and Grandniece/Grandnephew" ->
                "$nameA and $nameB are great-uncle/aunt and grandniece/nephew (4th Civil Degree, Collateral) through common ancestor(s) $ancestorName."

            "First Cousin Once Removed" ->
                "$nameA and $nameB are first cousins once removed (5th Civil Degree). They share common ancestor(s) $ancestorName at different generational levels ($distanceA and $distanceB steps)."

            "Great-Great-Uncle/Aunt and Great-Grandniece/Nephew" ->
                "$nameA and $nameB are great-great-uncle/aunt and great-grandniece/nephew (5th Civil Degree) through common ancestor(s) $ancestorName."

            "Second Cousins" ->
                if (isDualAncestors) {
                    "$nameA and $nameB are full second cousins (6th Civil Degree, Collateral). They both descend from great-grandparents $ancestorName (3 generational steps each)."
                } else {
                    "$nameA and $nameB are second cousins (6th Civil Degree, Collateral). They both descend from great-grandparent $ancestorName (3 generational steps each)."
                }

            "First Cousin Twice Removed" ->
                "$nameA and $nameB are first cousins twice removed (6th Civil Degree) through common ancestor(s) $ancestorName."

            "Second Cousin Once Removed" ->
                "$nameA and $nameB are second cousins once removed (7th Civil Degree) through common ancestor(s) $ancestorName."

            "Third Cousins" ->
                "$nameA and $nameB are third cousins (8th Civil Degree, Collateral). They descend from great-great-grandparent(s) $ancestorName (4 generational steps each)."

            else ->
                "$nameA and $nameB are blood relatives through their shared ancestor(s) $ancestorName. " +
                        "$nameA is $distanceA generation(s) away and $nameB is $distanceB generation(s) away (${distanceA + distanceB}th Civil Degree)."
        }
    }
}