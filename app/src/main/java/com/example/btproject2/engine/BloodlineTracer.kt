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
                distanceA = 0, distanceB = 0,
                legalStatus = "Same Person",
                legalArticle = "N/A",
                isMarriageProhibited = true,
                legalDescription = "An individual cannot enter into a marriage contract with themselves."
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
                distanceA = 0, distanceB = distance,
                legalStatus = classification.legalStatus,
                legalArticle = classification.legalArticle,
                isMarriageProhibited = classification.isMarriageProhibited,
                legalDescription = classification.legalDescription
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
                distanceA = distance, distanceB = 0,
                legalStatus = classification.legalStatus,
                legalArticle = classification.legalArticle,
                isMarriageProhibited = classification.isMarriageProhibited,
                legalDescription = classification.legalDescription
            )
        }

        val commonAncestorIds = ancestorsA.keys.intersect(ancestorsB.keys)

        if (commonAncestorIds.isEmpty()) {
            // Check non-biological legal impediments under the Family Code of the Philippines
            val nonBioCheck = checkNonBiologicalImpediments(personA, personB, personMap)
            if (nonBioCheck != null) {
                return nonBioCheck
            }

            return TraceResult(
                personA = personA, personB = personB,
                isRelated = false,
                relationshipType = "No Blood Relationship Found",
                relationshipCategory = "None",
                degreeOfConsanguinity = 0,
                commonAncestor = null,
                pathFromA = emptyList(), pathFromB = emptyList(),
                explanation = "No biological blood connection was detected within the family tree records.",
                distanceA = 0, distanceB = 0,
                legalStatus = "No Consanguinity Impediment",
                legalArticle = "No Shared Biological Ancestor",
                isMarriageProhibited = false,
                legalDescription = "No shared biological ancestors exist in this family tree. No consanguinity impediment under Articles 37 or 38 of the Family Code of the Philippines."
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
            distanceB = bestD2,
            legalStatus = classification.legalStatus,
            legalArticle = classification.legalArticle,
            isMarriageProhibited = classification.isMarriageProhibited,
            legalDescription = classification.legalDescription
        )
    }

    /**
     * Evaluates legal impediments under the Family Code of the Philippines
     * for non-biological relationships (Spousal, Adoptive, and Step).
     */
    private fun checkNonBiologicalImpediments(
        personA: Person,
        personB: Person,
        personMap: Map<String, Person>
    ): TraceResult? {
        // 1. Spousal check (Current Marriage - Art. 35(4) / Art. 40)
        if (personA.spouseId == personB.id || personB.spouseId == personA.id) {
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = true,
                relationshipType = "Spouse (Legally Married)",
                relationshipCategory = "Affinity / Marital",
                degreeOfConsanguinity = 0,
                commonAncestor = null,
                pathFromA = listOf(personA),
                pathFromB = listOf(personB),
                explanation = "${personA.firstName} and ${personB.firstName} are recorded as spouses. Under Philippine Law, a person who is currently married cannot enter into another marriage without prior judicial declaration of nullity (Bigamy, Art. 35(4) & Art. 40 Family Code).",
                distanceA = 0, distanceB = 0,
                legalStatus = "Void (Existing Marriage)",
                legalArticle = "Art. 35(4) / Art. 40 Family Code",
                isMarriageProhibited = true,
                legalDescription = "Existing Marriage Impediment: Bigamous marriage is void ab initio under Article 35, Paragraph 4 and Article 40 of the Family Code."
            )
        }

        // 2. Adoptive Parent & Adopted Child (Art. 38(4))
        val aAdoptedByB = (personA.fatherId == personB.id && personA.fatherRelationshipType.equals("Adoptive", ignoreCase = true)) ||
                (personA.motherId == personB.id && personA.motherRelationshipType.equals("Adoptive", ignoreCase = true))
        val bAdoptedByA = (personB.fatherId == personA.id && personB.fatherRelationshipType.equals("Adoptive", ignoreCase = true)) ||
                (personB.motherId == personA.id && personB.motherRelationshipType.equals("Adoptive", ignoreCase = true))

        if (aAdoptedByB || bAdoptedByA) {
            val adopter = if (bAdoptedByA) personA else personB
            val adoptee = if (bAdoptedByA) personB else personA
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = true,
                relationshipType = if (bAdoptedByA) "Adopting Parent" else "Adopted Child",
                relationshipCategory = "Legal / Adoptive",
                degreeOfConsanguinity = 0,
                commonAncestor = adopter,
                pathFromA = listOf(personA),
                pathFromB = listOf(personB),
                explanation = "${adopter.firstName} is the adopting parent of ${adoptee.firstName}. Under Article 38, Paragraph 4 of the Family Code of the Philippines, marriage between the adopting parent and the adopted child is void ab initio for reasons of public policy.",
                distanceA = 1, distanceB = 0,
                legalStatus = "Void (Public Policy)",
                legalArticle = "Art. 38(4) Family Code",
                isMarriageProhibited = true,
                legalDescription = "Void Public Policy: Marriage between the adopting parent and the adopted child is void ab initio under Article 38(4) of the Family Code."
            )
        }

        // 3. Step-Parent & Step-Child (Art. 38(2))
        val aFather = personA.fatherId?.let { personMap[it] }
        val aMother = personA.motherId?.let { personMap[it] }
        val bFather = personB.fatherId?.let { personMap[it] }
        val bMother = personB.motherId?.let { personMap[it] }

        val aStepChildOfB = (aFather != null && aFather.spouseId == personB.id) ||
                (aMother != null && aMother.spouseId == personB.id) ||
                (personA.fatherId == personB.id && personA.fatherRelationshipType.equals("Step", ignoreCase = true)) ||
                (personA.motherId == personB.id && personA.motherRelationshipType.equals("Step", ignoreCase = true))
        val bStepChildOfA = (bFather != null && bFather.spouseId == personA.id) ||
                (bMother != null && bMother.spouseId == personA.id) ||
                (personB.fatherId == personA.id && personB.fatherRelationshipType.equals("Step", ignoreCase = true)) ||
                (personB.motherId == personA.id && personB.motherRelationshipType.equals("Step", ignoreCase = true))

        if (aStepChildOfB || bStepChildOfA) {
            val stepParent = if (bStepChildOfA) personA else personB
            val stepChild = if (bStepChildOfA) personB else personA
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = true,
                relationshipType = if (bStepChildOfA) "Step-Parent" else "Step-Child",
                relationshipCategory = "Affinity / Step",
                degreeOfConsanguinity = 0,
                commonAncestor = stepParent,
                pathFromA = listOf(personA),
                pathFromB = listOf(personB),
                explanation = "${stepParent.firstName} is the step-parent of ${stepChild.firstName}. Under Article 38, Paragraph 2 of the Family Code of the Philippines, marriage between step-parents and step-children is void ab initio for reasons of public policy.",
                distanceA = 1, distanceB = 0,
                legalStatus = "Void (Public Policy)",
                legalArticle = "Art. 38(2) Family Code",
                isMarriageProhibited = true,
                legalDescription = "Void Public Policy: Marriage between step-parents and step-children is void ab initio under Article 38(2) of the Family Code."
            )
        }

        // 3b. Step-Siblings (Rule 3 & 5: Share no biological parent, but parents are spouses)
        val aParentsList = listOfNotNull(aFather, aMother)
        val bParentsList = listOfNotNull(bFather, bMother)
        val parentsAreMarried = aParentsList.any { ap ->
            bParentsList.any { bp ->
                ap.id != bp.id && (
                    (!ap.spouseId.isNullOrBlank() && ap.spouseId == bp.id) ||
                    (!bp.spouseId.isNullOrBlank() && bp.spouseId == ap.id)
                )
            }
        }
        val isBioRel = { r: String? -> r.isNullOrBlank() || r.equals("Biological", ignoreCase = true) }
        val sharesAnyBio = (personA.fatherId != null && personA.fatherId == personB.fatherId && isBioRel(personA.fatherRelationshipType) && isBioRel(personB.fatherRelationshipType)) ||
                (personA.motherId != null && personA.motherId == personB.motherId && isBioRel(personA.motherRelationshipType) && isBioRel(personB.motherRelationshipType))

        if (parentsAreMarried && !sharesAnyBio) {
            val connectingParentA = aParentsList.first { ap -> bParentsList.any { bp -> ap.spouseId == bp.id || bp.spouseId == ap.id } }
            val connectingParentB = bParentsList.first { bp -> connectingParentA.spouseId == bp.id || bp.spouseId == connectingParentA.id }
            return TraceResult(
                personA = personA,
                personB = personB,
                isRelated = true,
                relationshipType = "Step-Siblings",
                relationshipCategory = "Affinity / Step",
                degreeOfConsanguinity = 0,
                commonAncestor = null,
                pathFromA = listOf(personA, connectingParentA),
                pathFromB = listOf(personB, connectingParentB),
                explanation = "${personA.firstName} and ${personB.firstName} are step-siblings through the marriage of their parents (${connectingParentA.firstName} and ${connectingParentB.firstName}). They share no biological parents, so there is no blood relationship (0 degrees of consanguinity) and no legal marriage impediment under Article 37 or 38 of the Family Code.",
                distanceA = 1, distanceB = 1,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Family Code (No Impediment)",
                isMarriageProhibited = false,
                legalDescription = "Step-siblings share no biological lineage. There is no blood relationship and no marital impediment under the Family Code of the Philippines."
            )
        }

        // 4. Adoptive Siblings (Art. 38(7) & Art. 38(8))
        val aParents = listOfNotNull(
            personA.fatherId?.let { it to personA.fatherRelationshipType },
            personA.motherId?.let { it to personA.motherRelationshipType }
        )
        val bParents = listOfNotNull(
            personB.fatherId?.let { it to personB.fatherRelationshipType },
            personB.motherId?.let { it to personB.motherRelationshipType }
        )

        for ((parentIdA, relTypeA) in aParents) {
            for ((parentIdB, relTypeB) in bParents) {
                if (parentIdA == parentIdB) {
                    val isOneAdoptive = relTypeA.equals("Adoptive", ignoreCase = true) || relTypeB.equals("Adoptive", ignoreCase = true)
                    if (isOneAdoptive) {
                        val parentPerson = personMap[parentIdA]
                        val article = if (relTypeA.equals("Adoptive", ignoreCase = true) && relTypeB.equals("Adoptive", ignoreCase = true)) {
                            "Art. 38(8) Family Code"
                        } else {
                            "Art. 38(7) Family Code"
                        }
                        val desc = if (article.contains("38(8)")) {
                            "Void Public Policy: Marriage between adopted children of the same adopter is void ab initio under Article 38(8) of the Family Code."
                        } else {
                            "Void Public Policy: Marriage between an adopted child and a legitimate child of the adopter is void ab initio under Article 38(7) of the Family Code."
                        }
                        return TraceResult(
                            personA = personA, personB = personB,
                            isRelated = true,
                            relationshipType = "Adoptive Siblings",
                            relationshipCategory = "Legal / Adoptive",
                            degreeOfConsanguinity = 2,
                            commonAncestor = parentPerson,
                            pathFromA = listOf(personA),
                            pathFromB = listOf(personB),
                            explanation = "${personA.firstName} and ${personB.firstName} are siblings through common parent ${parentPerson?.firstName ?: "the same adopter"}, with an adoptive legal tie. Under ${article} of the Family Code of the Philippines, marriage between them is void ab initio.",
                            distanceA = 1, distanceB = 1,
                            legalStatus = "Void (Public Policy)",
                            legalArticle = article,
                            isMarriageProhibited = true,
                            legalDescription = desc
                        )
                    }
                }
            }
        }

        return null
    }
}