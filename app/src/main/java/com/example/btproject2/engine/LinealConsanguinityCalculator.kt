package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * Computes Lineal Consanguinity (Direct Line Ascendants and Descendants)
 * under the Roman Civil Law Method (Art. 963-964, Philippine Civil Code)
 * and verifies marital prohibitions under Article 37 (1) of the Family Code of the Philippines.
 *
 * 1st Degree: Parent / Child
 * 2nd Degree: Grandparent / Grandchild
 * 3rd Degree: Great-Grandparent / Great-Grandchild
 * 4th Degree: Great-Great-Grandparent / Great-Great-Grandchild
 */
class LinealConsanguinityCalculator {

    data class LinealDegreeGroup(
        val degree: Int,
        val title: String,
        val degreeLabel: String,
        val ascendantTitle: String,
        val descendantTitle: String,
        val ascendants: List<Person>,
        val descendants: List<Person>
    ) {
        val allRelatives: List<Person> get() = (ascendants + descendants).distinctBy { it.id }
        val isEmpty: Boolean get() = ascendants.isEmpty() && descendants.isEmpty()
        val count: Int get() = allRelatives.size
    }

    data class LinealConsanguinityReport(
        val subject: Person,
        val degree1: LinealDegreeGroup,
        val degree2: LinealDegreeGroup,
        val degree3: LinealDegreeGroup,
        val degree4: LinealDegreeGroup,
        val legalStatus: String = "Void ab initio (Incestuous)",
        val legalArticle: String = "Article 37 (1), Family Code of the Philippines",
        val legalDescription: String = "Marriage between ascendants and descendants of any degree is incestuous and void from the beginning under Philippine law."
    ) {
        val degrees: List<LinealDegreeGroup> get() = listOf(degree1, degree2, degree3, degree4)
        val totalDirectRelatives: Int get() = degrees.sumOf { it.count }
        val hasAnyRelatives: Boolean get() = totalDirectRelatives > 0
    }

    /**
     * Calculates all 4 lineal degrees (ascendants and descendants) for the given person.
     */
    fun calculate(person: Person, allMembers: Collection<Person>): LinealConsanguinityReport {
        val personMap = allMembers.associateBy { it.id }
        val childrenMap = mutableMapOf<String, MutableList<Person>>()
        for (m in allMembers) {
            if (!m.fatherId.isNullOrEmpty()) {
                childrenMap.getOrPut(m.fatherId!!) { mutableListOf() }.add(m)
            }
            if (!m.motherId.isNullOrEmpty()) {
                childrenMap.getOrPut(m.motherId!!) { mutableListOf() }.add(m)
            }
        }

        fun isBio(relType: String?) = relType.isNullOrBlank() || relType.equals("Biological", ignoreCase = true)

        // --- Ascendants (Biological Only - Consanguinity) ---
        fun getParents(p: Person): List<Person> =
            listOfNotNull(
                p.fatherId?.let { if (isBio(p.fatherRelationshipType)) personMap[it] else null },
                p.motherId?.let { if (isBio(p.motherRelationshipType)) personMap[it] else null }
            ).filter { it.id != p.id }

        val parents = getParents(person).distinctBy { it.id }
        val grandparents = parents.flatMap { getParents(it) }.distinctBy { it.id }.filter { it.id != person.id }
        val greatGrandparents = grandparents.flatMap { getParents(it) }.distinctBy { it.id }.filter { it.id != person.id }
        val greatGreatGrandparents = greatGrandparents.flatMap { getParents(it) }.distinctBy { it.id }.filter { it.id != person.id }

        // --- Descendants (Biological Only - Consanguinity) ---
        fun getChildren(p: Person): List<Person> =
            childrenMap[p.id].orEmpty().filter { child ->
                child.id != p.id && (
                    (child.fatherId == p.id && isBio(child.fatherRelationshipType)) ||
                    (child.motherId == p.id && isBio(child.motherRelationshipType))
                )
            }.distinctBy { it.id }

        val children = getChildren(person).distinctBy { it.id }
        val grandchildren = children.flatMap { getChildren(it) }.distinctBy { it.id }.filter { it.id != person.id }
        val greatGrandchildren = grandchildren.flatMap { getChildren(it) }.distinctBy { it.id }.filter { it.id != person.id }
        val greatGreatGrandchildren = greatGrandchildren.flatMap { getChildren(it) }.distinctBy { it.id }.filter { it.id != person.id }

        return LinealConsanguinityReport(
            subject = person,
            degree1 = LinealDegreeGroup(
                degree = 1,
                title = "1st Degree",
                degreeLabel = "1°",
                ascendantTitle = "Parents",
                descendantTitle = "Children",
                ascendants = parents,
                descendants = children
            ),
            degree2 = LinealDegreeGroup(
                degree = 2,
                title = "2nd Degree",
                degreeLabel = "2°",
                ascendantTitle = "Grandparents",
                descendantTitle = "Grandchildren",
                ascendants = grandparents,
                descendants = grandchildren
            ),
            degree3 = LinealDegreeGroup(
                degree = 3,
                title = "3rd Degree",
                degreeLabel = "3°",
                ascendantTitle = "Great-Grandparents",
                descendantTitle = "Great-Grandchildren",
                ascendants = greatGrandparents,
                descendants = greatGrandchildren
            ),
            degree4 = LinealDegreeGroup(
                degree = 4,
                title = "4th Degree",
                degreeLabel = "4°",
                ascendantTitle = "Great-Great-Grandparents",
                descendantTitle = "Great-Great-Grandchildren",
                ascendants = greatGreatGrandparents,
                descendants = greatGreatGrandchildren
            )
        )
    }

    /**
     * Determines whether personB is in the direct lineal line of personA within 1st-4th degree,
     * and returns the degree (1, 2, 3, 4) or null if not in direct line.
     */
    fun getLinealDegree(personA: Person, personB: Person, allMembers: Collection<Person>): Int? {
        if (personA.id == personB.id) return null
        val report = calculate(personA, allMembers)
        return when {
            report.degree1.allRelatives.any { it.id == personB.id } -> 1
            report.degree2.allRelatives.any { it.id == personB.id } -> 2
            report.degree3.allRelatives.any { it.id == personB.id } -> 3
            report.degree4.allRelatives.any { it.id == personB.id } -> 4
            else -> null
        }
    }
}

