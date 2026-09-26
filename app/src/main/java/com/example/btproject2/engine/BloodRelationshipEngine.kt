package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * Family-Connection and Blood-Relationship (Consanguinity) Engine for Philippine Family Trees.
 *
 * Implements the Roman Civil Law method of counting degrees of consanguinity
 * (Articles 963–967 of the Civil Code of the Philippines; Articles 37–38 of the Family Code of the Philippines).
 *
 * 1. Storage Architecture:
 *    - Only three primitive connections are directly stored:
 *      a) Biological parent and child (fatherId, motherId with relationshipType = "Biological")
 *      b) Adoptive parent and child (fatherId, motherId with relationshipType = "Adoptive")
 *      c) Spouse or partner (spouseId)
 *    - Derived relationships (siblings, grandparents, grandchildren, uncles/aunts, nieces/nephews,
 *      cousins, in-laws) are NEVER stored directly in the database. They are automatically discovered
 *      dynamically through graph traversal over the primitive connections.
 *
 * 2. Blood-Relationship (Consanguinity) Rules:
 *    - Only biological connections count towards blood relationships.
 *    - Spouses, adopted family members, stepfamily, and in-laws do not affect the blood-relationship calculation.
 *    - Lineal: counted by direct generation gap between ascendant and descendant.
 *    - Collateral: counted by ascending from Person A to the closest shared biological ancestor (MRCA),
 *      then descending to Person B. Total = (gen from A to MRCA) + (gen from MRCA to B).
 */
class BloodRelationshipEngine {

    enum class BloodCategory(val displayLabel: String) {
        LINEAL("Lineal"),
        COLLATERAL("Collateral"),
        NO_BLOOD_RELATIONSHIP("No blood relationship")
    }

    /**
     * Detailed result of a blood-relationship determination.
     */
    data class BloodRelationshipResult(
        val personA: Person,
        val personB: Person,
        val category: BloodCategory,
        val degree: Int,
        val relationshipLabel: String,
        val familyPath: List<Person>,
        val commonAncestor: Person?,
        val generationsA: Int,
        val generationsB: Int,
        val explanation: String
    ) {
        val isBloodRelated: Boolean get() = category != BloodCategory.NO_BLOOD_RELATIONSHIP
    }

    // =========================================================================
    // SECTION 1: BLOOD-RELATIONSHIP DETERMINATION (CONSANGUINITY)
    // =========================================================================

    /**
     * Determines whether two individuals have a biological blood relationship under Philippine Civil Law.
     *
     * @param personAId The ID of the first person.
     * @param personBId The ID of the second person.
     * @param personMap The complete family tree lookup map (ID -> Person).
     * @return BloodRelationshipResult containing the category (Lineal, Collateral, No blood relationship),
     *         degree, human-readable label, and the exact connecting family path.
     */
    fun determineBloodRelationship(
        personAId: String,
        personBId: String,
        personMap: Map<String, Person>
    ): BloodRelationshipResult {
        val personA = personMap[personAId] ?: throw IllegalArgumentException("Person A ($personAId) not found in family tree.")
        val personB = personMap[personBId] ?: throw IllegalArgumentException("Person B ($personBId) not found in family tree.")

        // Edge case: Same individual
        if (personAId == personBId) {
            return BloodRelationshipResult(
                personA = personA,
                personB = personB,
                category = BloodCategory.NO_BLOOD_RELATIONSHIP,
                degree = 0,
                relationshipLabel = "Same Individual",
                familyPath = listOf(personA),
                commonAncestor = null,
                generationsA = 0,
                generationsB = 0,
                explanation = "Selected the same person. Blood relationship is evaluated between two distinct individuals."
            )
        }

        // Step 1: Discover strictly biological ancestors for both individuals
        val ancestorsA = findBiologicalAncestors(personAId, personMap)
        val ancestorsB = findBiologicalAncestors(personBId, personMap)

        // Step 2: Lineal Consanguinity Check (Direct biological descent)
        // Case 2A: Person A is a direct biological ascendant of Person B
        if (ancestorsB.containsKey(personAId)) {
            val (distance, pathFromB) = ancestorsB[personAId]!!
            // pathFromB is [personB, parent, ..., personA]; reverse to get [personA, ..., personB]
            val connectingPath = pathFromB.reversed()
            val label = getLinealLabel(distance)
            return BloodRelationshipResult(
                personA = personA,
                personB = personB,
                category = BloodCategory.LINEAL,
                degree = distance,
                relationshipLabel = label,
                familyPath = connectingPath,
                commonAncestor = personA,
                generationsA = 0,
                generationsB = distance,
                explanation = "${personA.firstName} is a direct biological ascendant of ${personB.firstName} (${distance} generation${if (distance > 1) "s" else ""} apart). This is a ${distance}${getOrdinalSuffix(distance)} degree lineal blood relationship under Philippine Civil Code Art. 966."
            )
        }

        // Case 2B: Person B is a direct biological ascendant of Person A
        if (ancestorsA.containsKey(personBId)) {
            val (distance, pathFromA) = ancestorsA[personBId]!!
            // pathFromA is [personA, parent, ..., personB]
            val connectingPath = pathFromA
            val label = getLinealLabel(distance)
            return BloodRelationshipResult(
                personA = personA,
                personB = personB,
                category = BloodCategory.LINEAL,
                degree = distance,
                relationshipLabel = label,
                familyPath = connectingPath,
                commonAncestor = personB,
                generationsA = distance,
                generationsB = 0,
                explanation = "${personB.firstName} is a direct biological ascendant of ${personA.firstName} (${distance} generation${if (distance > 1) "s" else ""} apart). This is a ${distance}${getOrdinalSuffix(distance)} degree lineal blood relationship under Philippine Civil Code Art. 966."
            )
        }

        // Step 3: Collateral Consanguinity Check (Shared common biological stock)
        val sharedAncestorIds = ancestorsA.keys.intersect(ancestorsB.keys)

        if (sharedAncestorIds.isEmpty()) {
            return BloodRelationshipResult(
                personA = personA,
                personB = personB,
                category = BloodCategory.NO_BLOOD_RELATIONSHIP,
                degree = 0,
                relationshipLabel = "No blood relationship",
                familyPath = emptyList(),
                commonAncestor = null,
                generationsA = 0,
                generationsB = 0,
                explanation = "No shared biological ancestors exist between ${personA.firstName} and ${personB.firstName}. Non-biological ties (spouses, adoptive connections, stepfamily, and in-laws) do not constitute consanguinity."
            )
        }

        // Find the Most Recent Common Biological Ancestor (MRCA) that minimizes (dA + dB)
        var bestMrcaId = ""
        var minTotalDistance = Int.MAX_VALUE
        var bestDa = 0
        var bestDb = 0

        for (ancId in sharedAncestorIds) {
            val da = ancestorsA[ancId]!!.first
            val db = ancestorsB[ancId]!!.first
            val total = da + db
            if (total < minTotalDistance) {
                minTotalDistance = total
                bestMrcaId = ancId
                bestDa = da
                bestDb = db
            }
        }

        val mrca = personMap[bestMrcaId]!!
        val pathA = ancestorsA[bestMrcaId]!!.second // [personA, ..., MRCA]
        val pathB = ancestorsB[bestMrcaId]!!.second // [personB, ..., MRCA]

        // Full connecting path: A up to MRCA, then down to B
        // pathA is [A, ..., MRCA]
        // pathB.reversed() is [MRCA, ..., B]
        val connectingPath = pathA + pathB.reversed().drop(1)

        val degree = bestDa + bestDb
        val label = getCollateralLabel(bestDa, bestDb)

        val explanation = "${personA.firstName} and ${personB.firstName} share ${mrca.firstName} ${mrca.lastName} as their closest biological ancestor. " +
                "Ascending ${bestDa} generation${if (bestDa > 1) "s" else ""} from ${personA.firstName} to ${mrca.firstName}, " +
                "then descending ${bestDb} generation${if (bestDb > 1) "s" else ""} to ${personB.firstName} yields a total collateral degree of $degree " +
                "(${label}) under Philippine Civil Code Art. 967."

        return BloodRelationshipResult(
            personA = personA,
            personB = personB,
            category = BloodCategory.COLLATERAL,
            degree = degree,
            relationshipLabel = label,
            familyPath = connectingPath,
            commonAncestor = mrca,
            generationsA = bestDa,
            generationsB = bestDb,
            explanation = explanation
        )
    }

    /**
     * Traverses upward using strictly biological parent connections to discover all biological ancestors.
     */
    fun findBiologicalAncestors(
        personId: String,
        personMap: Map<String, Person>,
        maxDepth: Int = 15
    ): Map<String, Pair<Int, List<Person>>> {
        val ancestors = mutableMapOf<String, Pair<Int, List<Person>>>()
        val startPerson = personMap[personId] ?: return ancestors

        data class QueueItem(val person: Person, val depth: Int, val path: List<Person>)
        val queue = ArrayDeque<QueueItem>()
        queue.add(QueueItem(startPerson, 0, listOf(startPerson)))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.depth >= maxDepth) continue

            // 1. Biological Mother
            val isMotherBio = isBiological(current.person.motherRelationshipType)
            if (isMotherBio && !current.person.motherId.isNullOrBlank()) {
                val mother = personMap[current.person.motherId]
                if (mother != null && !ancestors.containsKey(mother.id)) {
                    val nextDepth = current.depth + 1
                    val nextPath = current.path + mother
                    ancestors[mother.id] = Pair(nextDepth, nextPath)
                    queue.add(QueueItem(mother, nextDepth, nextPath))
                }
            }

            // 2. Biological Father
            val isFatherBio = isBiological(current.person.fatherRelationshipType)
            if (isFatherBio && !current.person.fatherId.isNullOrBlank()) {
                val father = personMap[current.person.fatherId]
                if (father != null && !ancestors.containsKey(father.id)) {
                    val nextDepth = current.depth + 1
                    val nextPath = current.path + father
                    ancestors[father.id] = Pair(nextDepth, nextPath)
                    queue.add(QueueItem(father, nextDepth, nextPath))
                }
            }
        }

        return ancestors
    }

    private fun isBiological(relType: String?): Boolean {
        return relType.isNullOrBlank() || relType.equals("Biological", ignoreCase = true)
    }

    private fun getLinealLabel(distance: Int): String {
        return when (distance) {
            1 -> "Parent and child"
            2 -> "Grandparent and grandchild"
            3 -> "Great-grandparent and great-grandchild"
            4 -> "Great-great-grandparent and great-great-grandchild"
            else -> "Lineal relative (${distance}${getOrdinalSuffix(distance)} degree)"
        }
    }

    private fun getCollateralLabel(d1: Int, d2: Int): String {
        val min = minOf(d1, d2)
        val max = maxOf(d1, d2)
        val degree = min + max
        return when {
            // Siblings: 1 + 1 = 2
            min == 1 && max == 1 -> "Siblings"
            // Uncle/Aunt & Niece/Nephew: 1 + 2 = 3
            min == 1 && max == 2 -> "Uncle or aunt and niece or nephew"
            // First Cousins: 2 + 2 = 4
            min == 2 && max == 2 -> "First cousins"
            // Great-Uncle/Aunt & Grandniece/Nephew: 1 + 3 = 4
            min == 1 && max == 3 -> "Great-uncle or aunt and grandniece or nephew"
            // First Cousin Once Removed: 2 + 3 = 5
            min == 2 && max == 3 -> "First cousin once removed"
            // Great-Great-Uncle/Aunt & Great-Grandniece/Nephew: 1 + 4 = 5
            min == 1 && max == 4 -> "Great-great-uncle or aunt and great-grandniece or nephew"
            // Second Cousins: 3 + 3 = 6
            min == 3 && max == 3 -> "Second cousins"
            // First Cousin Twice Removed: 2 + 4 = 6
            min == 2 && max == 4 -> "First cousin twice removed"
            // Second Cousin Once Removed: 3 + 4 = 7
            min == 3 && max == 4 -> "Second cousin once removed"
            // Third Cousins: 4 + 4 = 8
            min == 4 && max == 4 -> "Third cousins"
            else -> "Collateral relative (${degree}${getOrdinalSuffix(degree)} degree)"
        }
    }

    private fun getOrdinalSuffix(number: Int): String {
        return when {
            number % 100 in 11..13 -> "th"
            number % 10 == 1 -> "st"
            number % 10 == 2 -> "nd"
            number % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    // =========================================================================
    // SECTION 2: AUTOMATIC DISCOVERY OF DERIVED RELATIONSHIPS
    // (Never stored directly; dynamically discovered from saved connections)
    // =========================================================================

    /**
     * Discovers all siblings of a person (sharing at least one parent).
     */
    fun findSiblings(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        val person = personMap[personId] ?: return emptyList()
        val parentIds = mutableSetOf<String>()

        if (!person.fatherId.isNullOrBlank() && (!biologicalOnly || isBiological(person.fatherRelationshipType))) {
            parentIds.add(person.fatherId)
        }
        if (!person.motherId.isNullOrBlank() && (!biologicalOnly || isBiological(person.motherRelationshipType))) {
            parentIds.add(person.motherId)
        }

        if (parentIds.isEmpty()) return emptyList()

        return personMap.values.filter { candidate ->
            candidate.id != person.id && (
                (!candidate.fatherId.isNullOrBlank() && parentIds.contains(candidate.fatherId) && (!biologicalOnly || isBiological(candidate.fatherRelationshipType))) ||
                (!candidate.motherId.isNullOrBlank() && parentIds.contains(candidate.motherId) && (!biologicalOnly || isBiological(candidate.motherRelationshipType)))
            )
        }.distinctBy { it.id }
    }

    /**
     * Discovers all grandparents of a person (parents of parents).
     */
    fun findGrandparents(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        val person = personMap[personId] ?: return emptyList()
        val parents = listOfNotNull(
            person.fatherId?.let { if (!biologicalOnly || isBiological(person.fatherRelationshipType)) personMap[it] else null },
            person.motherId?.let { if (!biologicalOnly || isBiological(person.motherRelationshipType)) personMap[it] else null }
        )

        return parents.flatMap { parent ->
            listOfNotNull(
                parent.fatherId?.let { if (!biologicalOnly || isBiological(parent.fatherRelationshipType)) personMap[it] else null },
                parent.motherId?.let { if (!biologicalOnly || isBiological(parent.motherRelationshipType)) personMap[it] else null }
            )
        }.distinctBy { it.id }
    }

    /**
     * Discovers all grandchildren of a person (children of children).
     */
    fun findGrandchildren(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        val children = findChildren(personId, personMap, biologicalOnly)
        return children.flatMap { child ->
            findChildren(child.id, personMap, biologicalOnly)
        }.distinctBy { it.id }
    }

    /**
     * Discovers direct children of a person.
     */
    fun findChildren(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        return personMap.values.filter { candidate ->
            val isFather = candidate.fatherId == personId && (!biologicalOnly || isBiological(candidate.fatherRelationshipType))
            val isMother = candidate.motherId == personId && (!biologicalOnly || isBiological(candidate.motherRelationshipType))
            isFather || isMother
        }
    }

    /**
     * Discovers uncles and aunts of a person (siblings of parents).
     */
    fun findUnclesAndAunts(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        val person = personMap[personId] ?: return emptyList()
        val parents = listOfNotNull(
            person.fatherId?.let { if (!biologicalOnly || isBiological(person.fatherRelationshipType)) personMap[it] else null },
            person.motherId?.let { if (!biologicalOnly || isBiological(person.motherRelationshipType)) personMap[it] else null }
        )

        return parents.flatMap { parent ->
            findSiblings(parent.id, personMap, biologicalOnly)
        }.distinctBy { it.id }
    }

    /**
     * Discovers nieces and nephews of a person (children of siblings).
     */
    fun findNiecesAndNephews(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        val siblings = findSiblings(personId, personMap, biologicalOnly)
        return siblings.flatMap { sibling ->
            findChildren(sibling.id, personMap, biologicalOnly)
        }.distinctBy { it.id }
    }

    /**
     * Discovers first cousins of a person (children of parents' siblings).
     */
    fun findFirstCousins(
        personId: String,
        personMap: Map<String, Person>,
        biologicalOnly: Boolean = false
    ): List<Person> {
        val unclesAndAunts = findUnclesAndAunts(personId, personMap, biologicalOnly)
        return unclesAndAunts.flatMap { uncleOrAunt ->
            findChildren(uncleOrAunt.id, personMap, biologicalOnly)
        }.filter { it.id != personId }.distinctBy { it.id }
    }

    /**
     * Discovers parents-in-law of a person (parents of spouse).
     */
    fun findParentsInLaw(
        personId: String,
        personMap: Map<String, Person>
    ): List<Person> {
        val person = personMap[personId] ?: return emptyList()
        val spouseId = person.spouseId ?: return emptyList()
        val spouse = personMap[spouseId] ?: return emptyList()

        return listOfNotNull(
            spouse.fatherId?.let { personMap[it] },
            spouse.motherId?.let { personMap[it] }
        ).distinctBy { it.id }
    }

    /**
     * Discovers children-in-law of a person (spouses of children).
     */
    fun findChildrenInLaw(
        personId: String,
        personMap: Map<String, Person>
    ): List<Person> {
        val children = findChildren(personId, personMap, biologicalOnly = false)
        return children.mapNotNull { child ->
            child.spouseId?.let { personMap[it] }
        }.distinctBy { it.id }
    }

    /**
     * Discovers siblings-in-law of a person (siblings of spouse, and spouses of siblings).
     */
    fun findSiblingsInLaw(
        personId: String,
        personMap: Map<String, Person>
    ): List<Person> {
        val person = personMap[personId] ?: return emptyList()
        val result = mutableListOf<Person>()

        // 1. Siblings of spouse
        val spouseId = person.spouseId
        if (!spouseId.isNullOrBlank()) {
            val spouseSiblings = findSiblings(spouseId, personMap, biologicalOnly = false)
            result.addAll(spouseSiblings)
        }

        // 2. Spouses of siblings
        val siblings = findSiblings(person.id, personMap, biologicalOnly = false)
        for (sibling in siblings) {
            val sSpouseId = sibling.spouseId
            if (!sSpouseId.isNullOrBlank() && sSpouseId != person.id) {
                personMap[sSpouseId]?.let { result.add(it) }
            }
        }

        return result.distinctBy { it.id }
    }
}

