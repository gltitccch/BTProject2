package com.example.btproject2.engine

class RelationshipClassifier {

    data class Classification(
        val label: String,
        val category: String,
        val degree: Int
    )

    fun classify(d1: Int, d2: Int, isLineal: Boolean): Classification {

        if (isLineal) {
            val distance = maxOf(d1, d2)
            return when (distance) {
                1 -> Classification("Parent / Child", "Lineal", 1)
                2 -> Classification("Grandparent / Grandchild", "Lineal", 2)
                3 -> Classification("Great-Grandparent / Great-Grandchild", "Lineal", 3)
                else -> Classification("Distant Lineal Relative", "Lineal", distance)
            }
        }

        val min = minOf(d1, d2)
        val max = maxOf(d1, d2)

        return when {
            min == 1 && max == 1 -> Classification("Siblings", "Collateral", 2)
            min == 1 && max == 2 -> Classification("Uncle/Aunt and Niece/Nephew", "Collateral", 3)
            min == 2 && max == 2 -> Classification("First Cousins", "Collateral", 4)
            min == 2 && max == 3 -> Classification("First Cousin Once Removed", "Collateral", 5)
            min == 3 && max == 3 -> Classification("Second Cousins", "Collateral", 6)
            else -> Classification("Distant Blood Relative", "Collateral", min + max)
        }
    }
}