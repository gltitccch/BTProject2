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
            "Parent / Child" -> {
                val (parent, child) = if (distanceA == 0) personA to personB else personB to personA
                val pName = "${parent.firstName} ${parent.lastName}".trim()
                val cName = "${child.firstName} ${child.lastName}".trim()
                val pRole = if (parent.gender.equals("Male", ignoreCase = true)) "father" else if (parent.gender.equals("Female", ignoreCase = true)) "mother" else "parent"
                val cRole = if (child.gender.equals("Male", ignoreCase = true)) "son" else if (child.gender.equals("Female", ignoreCase = true)) "daughter" else "child"
                "$nameA and $nameB are in a direct parent-child relationship (1st Civil Degree, Lineal): $pName is the $pRole and $cName is the $cRole."
            }

            "Grandparent / Grandchild" -> {
                val (gp, gc) = if (distanceA == 0) personA to personB else personB to personA
                val gpName = "${gp.firstName} ${gp.lastName}".trim()
                val gcName = "${gc.firstName} ${gc.lastName}".trim()
                val gpRole = if (gp.gender.equals("Male", ignoreCase = true)) "grandfather" else if (gp.gender.equals("Female", ignoreCase = true)) "grandmother" else "grandparent"
                val gcRole = if (gc.gender.equals("Male", ignoreCase = true)) "grandson" else if (gc.gender.equals("Female", ignoreCase = true)) "granddaughter" else "grandchild"
                "$nameA and $nameB are grandparent and grandchild, separated by two generational steps in a direct bloodline (2nd Civil Degree, Lineal): $gpName is the $gpRole and $gcName is the $gcRole."
            }

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

            "Uncle/Aunt and Niece/Nephew" -> {
                val aIsElderGen = distanceA < distanceB
                val elderGen = if (aIsElderGen) personA else personB
                val youngerGen = if (aIsElderGen) personB else personA
                val elderName = "${elderGen.firstName} ${elderGen.lastName}".trim()
                val youngerName = "${youngerGen.firstName} ${youngerGen.lastName}".trim()
                val elderRole = if (elderGen.gender.equals("Male", ignoreCase = true)) "uncle" else if (elderGen.gender.equals("Female", ignoreCase = true)) "aunt" else "uncle/aunt"
                val youngerRole = if (youngerGen.gender.equals("Male", ignoreCase = true)) "nephew" else if (youngerGen.gender.equals("Female", ignoreCase = true)) "niece" else "niece/nephew"

                val baseText = if (isDualAncestors) {
                    "$nameA and $nameB are full uncle/aunt and niece/nephew (3rd Civil Degree): $elderName is the full $elderRole and $youngerName is the $youngerRole through common ancestors $ancestorName."
                } else {
                    "$nameA and $nameB are uncle/aunt and niece/nephew (3rd Civil Degree): $elderName is the $elderRole and $youngerName is the $youngerRole through common ancestor $ancestorName."
                }

                val elderBirthYr = FamilyLinkValidator.extractYear(elderGen.birthDate)
                val youngerBirthYr = FamilyLinkValidator.extractYear(youngerGen.birthDate)
                val ageNote = if (elderBirthYr != null && youngerBirthYr != null && elderBirthYr > youngerBirthYr) {
                    val diff = elderBirthYr - youngerBirthYr
                    " Generational vs. Age Note: Although $youngerName (born $youngerBirthYr) is $diff year(s) older than $elderName (born $elderBirthYr), $elderName is 1 generation closer to $ancestorName (a child of $ancestorName), whereas $youngerName is a grandchild of $ancestorName (2 steps down). In genealogy, generational steps from the shared ancestor — not chronological age — determine the $elderRole and $youngerRole titles."
                } else {
                    ""
                }
                baseText + ageNote
            }

            "First Cousins" ->
                if (isDualAncestors) {
                    "$nameA and $nameB are full first cousins (4th Civil Degree, Collateral). They share both common grandparents ($ancestorName), with each being 2 generational steps away."
                } else {
                    "$nameA and $nameB are half-first cousins (4th Civil Degree, Collateral). They share common grandparent $ancestorName, with each being 2 generational steps away."
                }

            "Double First Cousins" ->
                "$nameA and $nameB are double first cousins (4th Civil Degree, Collateral). Both of their paternal and maternal parent lines are full siblings, meaning they share grandparents on both sides of the family ($ancestorName), with each being 2 generational steps away."

            "Half-Double First Cousins" ->
                "$nameA and $nameB are half-double first cousins (4th Civil Degree, Collateral). They are first cousins through both sides of their family ($ancestorName), where at least one pair of connecting parents are half-siblings rather than full siblings (2 generational steps each)."

            "Great-Uncle/Aunt and Grandniece/Grandnephew" -> {
                val aIsElderGen = distanceA < distanceB
                val elderGen = if (aIsElderGen) personA else personB
                val youngerGen = if (aIsElderGen) personB else personA
                val elderName = "${elderGen.firstName} ${elderGen.lastName}".trim()
                val youngerName = "${youngerGen.firstName} ${youngerGen.lastName}".trim()
                val elderRole = if (elderGen.gender.equals("Male", ignoreCase = true)) "great-uncle" else if (elderGen.gender.equals("Female", ignoreCase = true)) "great-aunt" else "great-uncle/aunt"
                val youngerRole = if (youngerGen.gender.equals("Male", ignoreCase = true)) "grandnephew" else if (youngerGen.gender.equals("Female", ignoreCase = true)) "grandniece" else "grandniece/nephew"
                "$nameA and $nameB are great-uncle/aunt and grandniece/nephew (4th Civil Degree, Collateral): $elderName is the $elderRole and $youngerName is the $youngerRole through common ancestor(s) $ancestorName."
            }

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
