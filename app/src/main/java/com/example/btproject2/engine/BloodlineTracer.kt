package com.example.btproject2.engine

import com.example.btproject2.models.Person
import com.example.btproject2.models.TraceResult

class BloodlineTracer {

    private val ancestorFinder = AncestorFinder()
    private val classifier = RelationshipClassifier()
    private val explainer = RelationshipExplainer()

    fun trace(
        personAId: String,
        personBId: String,
        personMap: Map<String, Person>
    ): TraceResult {
        val personA = personMap[personAId]!!
        val personB = personMap[personBId]!!

        if (personAId == personBId) {
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = false,
                relationshipType = "Same Person",
                relationshipCategory = "None",
                degreeOfConsanguinity = 0,
                commonAncestor = null,
                pathFromA = emptyList(), pathFromB = emptyList(),
                explanation = "You selected the same individual.",
                distanceA = 0, distanceB = 0
            )
        }

        val ancestorsA = ancestorFinder.findAncestors(personAId, personMap)
        val ancestorsB = ancestorFinder.findAncestors(personBId, personMap)

        if (ancestorsB.containsKey(personAId)) {
            val (distance, path) = ancestorsB[personAId]!!
            val classification = classifier.classify(0, distance, isLineal = true)
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = true,
                relationshipType = classification.label,
                relationshipCategory = classification.category,
                degreeOfConsanguinity = classification.degree,
                commonAncestor = personA,
                pathFromA = listOf(personA),
                pathFromB = path,
                explanation = explainer.explain(personA, personB, classification.label, personA, 0, distance),
                distanceA = 0, distanceB = distance
            )
        }

        if (ancestorsA.containsKey(personBId)) {
            val (distance, path) = ancestorsA[personBId]!!
            val classification = classifier.classify(distance, 0, isLineal = true)
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = true,
                relationshipType = classification.label,
                relationshipCategory = classification.category,
                degreeOfConsanguinity = classification.degree,
                commonAncestor = personB,
                pathFromA = path,
                pathFromB = listOf(personB),
                explanation = explainer.explain(personA, personB, classification.label, personB, distance, 0),
                distanceA = distance, distanceB = 0
            )
        }

        val commonAncestorIds = ancestorsA.keys.intersect(ancestorsB.keys)

        if (commonAncestorIds.isEmpty()) {
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = false,
                relationshipType = "No Blood Relationship Found",
                relationshipCategory = "None",
                degreeOfConsanguinity = 0,
                commonAncestor = null,
                pathFromA = emptyList(), pathFromB = emptyList(),
                explanation = "No blood relationship was detected within the supported tracing range.",
                distanceA = 0, distanceB = 0
            )
        }

        var nearestAncestorId = ""
        var minTotalDistance = Int.MAX_VALUE
        var bestD1 = 0
        var bestD2 = 0

        for (ancestorId in commonAncestorIds) {
            val d1 = ancestorsA[ancestorId]!!.first
            val d2 = ancestorsB[ancestorId]!!.first
            val total = d1 + d2
            if (total < minTotalDistance) {
                minTotalDistance = total
                nearestAncestorId = ancestorId
                bestD1 = d1
                bestD2 = d2
            }
        }

        val commonAncestor = personMap[nearestAncestorId]!!
        val pathA = ancestorsA[nearestAncestorId]!!.second
        val pathB = ancestorsB[nearestAncestorId]!!.second
        val classification = classifier.classify(bestD1, bestD2, isLineal = false)
        val explanation = explainer.explain(
            personA, personB, classification.label,
            commonAncestor, bestD1, bestD2
        )

        return TraceResult(
            personA = personA,
            personB = personB,
            isRelated = true,
            relationshipType = classification.label,
            relationshipCategory = classification.category,
            degreeOfConsanguinity = classification.degree,
            commonAncestor = commonAncestor,
            pathFromA = pathA,
            pathFromB = pathB,
            explanation = explanation,
            distanceA = bestD1,
            distanceB = bestD2
        )
    }
}