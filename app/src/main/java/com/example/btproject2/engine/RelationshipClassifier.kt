package com.example.btproject2.engine

class RelationshipClassifier {

    data class Classification(
        val label: String,
        val category: String,
        val degree: Int,
        val legalStatus: String,
        val legalArticle: String,
        val isMarriageProhibited: Boolean,
        val legalDescription: String
    )

    fun classify(d1: Int, d2: Int, isLineal: Boolean): Classification {
        if (isLineal) {
            val distance = maxOf(d1, d2)
            val label = when (distance) {
                1 -> "Parent / Child"
                2 -> "Grandparent / Grandchild"
                3 -> "Great-Grandparent / Great-Grandchild"
                4 -> "Great-Great-Grandparent / Great-Great-Grandchild"
                else -> "Distant Lineal Relative (${distance}th Degree)"
            }
            return Classification(
                label = label,
                category = "Lineal",
                degree = distance,
                legalStatus = "Void ab initio (Incestuous)",
                legalArticle = "Article 37 (1), Family Code of the Philippines",
                isMarriageProhibited = true,
                legalDescription = "Marriage between ascendants and descendants of any degree is incestuous and void from the beginning under Philippine law."
            )
        }

        val min = minOf(d1, d2)
        val max = maxOf(d1, d2)
        val degree = min + max

        return when {
            // Siblings: 1 + 1 = 2
            min == 1 && max == 1 -> Classification(
                label = "Siblings",
                category = "Collateral",
                degree = 2,
                legalStatus = "Void ab initio (Incestuous)",
                legalArticle = "Article 37 (2), Family Code of the Philippines",
                isMarriageProhibited = true,
                legalDescription = "Marriage between brothers and sisters, whether of full or half blood, is incestuous and void from the beginning."
            )

            // Uncle/Aunt & Niece/Nephew: 1 + 2 = 3
            min == 1 && max == 2 -> Classification(
                label = "Uncle/Aunt and Niece/Nephew",
                category = "Collateral",
                degree = 3,
                legalStatus = "Void (Public Policy)",
                legalArticle = "Article 38 (1), Family Code of the Philippines",
                isMarriageProhibited = true,
                legalDescription = "Marriage between collateral blood relatives within the 4th civil degree is void for reasons of public policy."
            )

            // First Cousins: 2 + 2 = 4
            min == 2 && max == 2 -> Classification(
                label = "First Cousins",
                category = "Collateral",
                degree = 4,
                legalStatus = "Void (Public Policy)",
                legalArticle = "Article 38 (1), Family Code of the Philippines",
                isMarriageProhibited = true,
                legalDescription = "First cousins are within the 4th civil degree of collateral consanguinity; marriage between them is prohibited and void under Philippine law."
            )

            // Great-Uncle/Aunt & Grandniece/Grandnephew: 1 + 3 = 4
            min == 1 && max == 3 -> Classification(
                label = "Great-Uncle/Aunt and Grandniece/Grandnephew",
                category = "Collateral",
                degree = 4,
                legalStatus = "Void (Public Policy)",
                legalArticle = "Article 38 (1), Family Code of the Philippines",
                isMarriageProhibited = true,
                legalDescription = "Great-uncles/aunts and grandnieces/nephews are within the 4th civil degree of collateral consanguinity; marriage between them is prohibited and void."
            )

            // First Cousin Once Removed: 2 + 3 = 5
            min == 2 && max == 3 -> Classification(
                label = "First Cousin Once Removed",
                category = "Collateral",
                degree = 5,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = false,
                legalDescription = "Relationship is at the 5th civil degree, beyond the 4th-degree prohibition of Article 38. There is no civil impediment to marriage."
            )

            // Great-Great-Uncle/Aunt & Great-Grandniece/Nephew: 1 + 4 = 5
            min == 1 && max == 4 -> Classification(
                label = "Great-Great-Uncle/Aunt and Great-Grandniece/Nephew",
                category = "Collateral",
                degree = 5,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = false,
                legalDescription = "Relationship is at the 5th civil degree, beyond the 4th-degree prohibition of Article 38."
            )

            // Second Cousins: 3 + 3 = 6
            min == 3 && max == 3 -> Classification(
                label = "Second Cousins",
                category = "Collateral",
                degree = 6,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = false,
                legalDescription = "Second cousins share great-grandparents at the 6th civil degree. There is no legal impediment to marriage under the Family Code."
            )

            // First Cousin Twice Removed: 2 + 4 = 6
            min == 2 && max == 4 -> Classification(
                label = "First Cousin Twice Removed",
                category = "Collateral",
                degree = 6,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = false,
                legalDescription = "Relationship is at the 6th civil degree, beyond the 4th-degree prohibition of Article 38."
            )

            // Second Cousin Once Removed: 3 + 4 = 7
            min == 3 && max == 4 -> Classification(
                label = "Second Cousin Once Removed",
                category = "Collateral",
                degree = 7,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = false,
                legalDescription = "Relationship is at the 7th civil degree, beyond the 4th-degree prohibition of Article 38."
            )

            // Third Cousins: 4 + 4 = 8
            min == 4 && max == 4 -> Classification(
                label = "Third Cousins",
                category = "Collateral",
                degree = 8,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = false,
                legalDescription = "Third cousins share great-great-grandparents at the 8th civil degree. There is no legal impediment under Philippine law."
            )

            // Distant collateral relatives
            else -> Classification(
                label = "Distant Blood Relative (${degree}th Degree)",
                category = "Collateral",
                degree = degree,
                legalStatus = if (degree <= 4) "Void (Public Policy)" else "Permissible under Philippine Law",
                legalArticle = if (degree <= 4) "Article 38 (1), Family Code of the Philippines" else "Beyond 4th Degree (Family Code Art. 38)",
                isMarriageProhibited = degree <= 4,
                legalDescription = if (degree <= 4)
                    "Collateral relations within the 4th civil degree are prohibited from marrying under Article 38."
                else
                    "At the ${degree}th civil degree, this connection is beyond the 4th civil degree prohibition of Article 38."
            )
        }
    }
}