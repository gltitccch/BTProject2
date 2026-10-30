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

        // Collect all co-equal nearest common ancestors (e.g., both Father & Mother or Grandfather & Grandmother)
        val coEqualAncestors = commonAncestorIds
            .filter { id ->
                val d1 = ancestorsA[id]!!.first
                val d2 = ancestorsB[id]!!.first
                (d1 + d2) == minTotalDistance && d1 == bestD1 && d2 == bestD2
            }
            .mapNotNull { personMap[it] }
            .sortedWith(
                compareBy<Person> { p ->
                    when {
                        p.gender.equals("Male", ignoreCase = true) -> 0
                        p.gender.equals("Female", ignoreCase = true) -> 1
                        else -> 2
                    }
                }.thenBy { it.firstName }
            )
            .ifEmpty { listOfNotNull(personMap[nearestAncestorId]) }

        val commonAncestor = coEqualAncestors.firstOrNull() ?: personMap[nearestAncestorId]!!
        val primaryAncestorId = commonAncestor.id
        val pathA = ancestorsA[primaryAncestorId]?.second ?: ancestorsA[nearestAncestorId]!!.second
        val pathB = ancestorsB[primaryAncestorId]?.second ?: ancestorsB[nearestAncestorId]!!.second
        val classification = classifier.classify(bestD1, bestD2, isLineal = false)

        val isDualAncestors = coEqualAncestors.size >= 2
        val isDoubleFirstCousins = bestD1 == 2 && bestD2 == 2 && run {
            val fA = personA.fatherId?.let { personMap[it] }
            val mA = personA.motherId?.let { personMap[it] }
            val fB = personB.fatherId?.let { personMap[it] }
            val mB = personB.motherId?.let { personMap[it] }
            fun areSiblings(p1: Person?, p2: Person?): Boolean {
                if (p1 == null || p2 == null || p1.id == p2.id) return false
                val shareFather = !p1.fatherId.isNullOrBlank() && p1.fatherId == p2.fatherId
                val shareMother = !p1.motherId.isNullOrBlank() && p1.motherId == p2.motherId
                return shareFather || shareMother
            }
            (areSiblings(fA, fB) && areSiblings(mA, mB)) ||
                (areSiblings(fA, mB) && areSiblings(mA, fB))
        }
        val effectiveRelLabel = if (isDoubleFirstCousins) "Double First Cousins" else classification.label
        val bloodlineSubtype = when {
            isDoubleFirstCousins -> "Double First Cousins (Paternal & Maternal)"
            bestD1 == 1 && bestD2 == 1 -> if (isDualAncestors) "Full Siblings" else "Half-Siblings"
            bestD1 == 2 && bestD2 == 2 -> if (isDualAncestors) "Full First Cousins" else "Half-First Cousins"
            bestD1 == 3 && bestD2 == 3 -> if (isDualAncestors) "Full Second Cousins" else "Half-Second Cousins"
            else -> if (isDualAncestors) "Full Blood" else "Half Blood"
        }

        val explanation = explainer.explain(
            personA, personB, effectiveRelLabel,
            commonAncestor, bestD1, bestD2,
            commonAncestors = coEqualAncestors
        )

        return TraceResult(
            personA = personA,
            personB = personB,
            isRelated = true,
            relationshipType = effectiveRelLabel,
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
            legalDescription = decorateLegalDescriptionWithStatusNote(personA, personB, classification.legalDescription, personMap),
            commonAncestors = coEqualAncestors,
            bloodlineSubtype = bloodlineSubtype
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
        // 1. Spousal check (Current Marriage or Former/Deceased Marriage - Art. 35(4) / Art. 40)
        if (personA.hasSpouseId(personB.id) || personB.hasSpouseId(personA.id)) {
            val isFormerMarriage = personA.formerSpouseIds.any { it.equals(personB.id, ignoreCase = true) } ||
                personB.formerSpouseIds.any { it.equals(personA.id, ignoreCase = true) }
            val isEitherDeceased = !personA.isLiving || !personB.isLiving
            val relLabel = when {
                isFormerMarriage && isEitherDeceased -> "Former / Late Spouse (Prior Marriage)"
                isFormerMarriage -> "Former Spouse (Prior Marriage)"
                else -> "Spouse (Legally Married)"
            }
            return TraceResult(
                personA = personA, personB = personB,
                isRelated = true,
                relationshipType = relLabel,
                relationshipCategory = "Affinity / Marital",
                degreeOfConsanguinity = 0,
                commonAncestor = null,
                pathFromA = listOf(personA),
                pathFromB = listOf(personB),
                explanation = if (isFormerMarriage) {
                    "${personA.firstName} and ${personB.firstName} are recorded as spouses from a prior marriage (${relLabel})."
                } else {
                    "${personA.firstName} and ${personB.firstName} are recorded as spouses. Under Philippine Law, a person who is currently married cannot enter into another marriage without prior judicial declaration of nullity (Bigamy, Art. 35(4) & Art. 40 Family Code)."
                },
                distanceA = 0, distanceB = 0,
                legalStatus = if (isFormerMarriage) "Prior Marriage Record" else "Void (Existing Marriage)",
                legalArticle = "Art. 35(4) / Art. 40 Family Code",
                isMarriageProhibited = !isFormerMarriage,
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

        val aStepChildOfB = (aFather != null && (aFather.hasSpouseId(personB.id) || personB.hasSpouseId(aFather.id))) ||
                (aMother != null && (aMother.hasSpouseId(personB.id) || personB.hasSpouseId(aMother.id))) ||
                (personA.fatherId == personB.id && personA.fatherRelationshipType.equals("Step", ignoreCase = true)) ||
                (personA.motherId == personB.id && personA.motherRelationshipType.equals("Step", ignoreCase = true))
        val bStepChildOfA = (bFather != null && (bFather.hasSpouseId(personA.id) || personA.hasSpouseId(bFather.id))) ||
                (bMother != null && (bMother.hasSpouseId(personA.id) || personA.hasSpouseId(bMother.id))) ||
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
                ap.id != bp.id && (ap.hasSpouseId(bp.id) || bp.hasSpouseId(ap.id))
            }
        }
        val isBioRel = { r: String? -> r.isNullOrBlank() || r.equals("Biological", ignoreCase = true) }
        val sharesAnyBio = (personA.fatherId != null && personA.fatherId == personB.fatherId && isBioRel(personA.fatherRelationshipType) && isBioRel(personB.fatherRelationshipType)) ||
                (personA.motherId != null && personA.motherId == personB.motherId && isBioRel(personA.motherRelationshipType) && isBioRel(personB.motherRelationshipType))

        if (parentsAreMarried && !sharesAnyBio) {
            val connectingParentA = aParentsList.first { ap -> bParentsList.any { bp -> ap.hasSpouseId(bp.id) || bp.hasSpouseId(ap.id) } }
            val connectingParentB = bParentsList.first { bp -> connectingParentA.hasSpouseId(bp.id) || bp.hasSpouseId(connectingParentA.id) }
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

        // 5. Parent-in-Law & Child-in-Law (Art. 38(3) / Art. 38(4) Family Code — Direct Affinity)
        val childOfAMarriedToB = personMap.values.firstOrNull { child ->
            (child.fatherId == personA.id || child.motherId == personA.id) &&
                    (child.hasSpouseId(personB.id) || personB.hasSpouseId(child.id))
        }
        val childOfBMarriedToA = personMap.values.firstOrNull { child ->
            (child.fatherId == personB.id || child.motherId == personB.id) &&
                    (child.hasSpouseId(personA.id) || personA.hasSpouseId(child.id))
        }

        if (childOfAMarriedToB != null || childOfBMarriedToA != null) {
            val aIsParentInLaw = childOfAMarriedToB != null
            val parentInLaw = if (aIsParentInLaw) personA else personB
            val childInLaw = if (aIsParentInLaw) personB else personA
            val connectingChild = (childOfAMarriedToB ?: childOfBMarriedToA)!!

            val pInLawRole = when {
                parentInLaw.gender.equals("Male", ignoreCase = true) -> "Father-in-Law"
                parentInLaw.gender.equals("Female", ignoreCase = true) -> "Mother-in-Law"
                else -> "Parent-in-Law"
            }
            val cInLawRole = when {
                childInLaw.gender.equals("Male", ignoreCase = true) -> "Son-in-Law"
                childInLaw.gender.equals("Female", ignoreCase = true) -> "Daughter-in-Law"
                else -> "Child-in-Law"
            }
            val label = if (aIsParentInLaw) "$pInLawRole / $cInLawRole" else "$cInLawRole / $pInLawRole"

            return TraceResult(
                personA = personA,
                personB = personB,
                isRelated = true,
                relationshipType = label,
                relationshipCategory = "Affinity / In-Law",
                degreeOfConsanguinity = 0,
                commonAncestor = connectingChild,
                pathFromA = if (aIsParentInLaw) listOf(personA, connectingChild) else listOf(personA),
                pathFromB = if (aIsParentInLaw) listOf(personB) else listOf(personB, connectingChild),
                explanation = "${parentInLaw.firstName} is the ${pInLawRole.lowercase()} of ${childInLaw.firstName} through the marriage of ${connectingChild.firstName} ${connectingChild.lastName} and ${childInLaw.firstName}. While they have 0 degrees of biological consanguinity, under Article 38 of the Family Code of the Philippines, marriage between parents-in-law and children-in-law is void ab initio for reasons of public policy.",
                distanceA = 1,
                distanceB = 1,
                legalStatus = "Void (Public Policy)",
                legalArticle = "Art. 38(3) / Art. 38(4) Family Code",
                isMarriageProhibited = true,
                legalDescription = "Void Public Policy: Marriage between parents-in-law and children-in-law is void ab initio under Article 38 of the Family Code of the Philippines.",
                commonAncestors = listOf(connectingChild),
                bloodlineSubtype = "Affinity via ${connectingChild.firstName} ${connectingChild.lastName}".trim()
            )
        }

        // 6. Siblings-in-Law (Brother-in-Law / Sister-in-Law — Collateral Affinity)
        val spousesOfA = (personA.allSpouseIds().mapNotNull { personMap[it] } +
            personMap.values.filter { it.hasSpouseId(personA.id) }).distinctBy { it.id }
        val spousesOfB = (personB.allSpouseIds().mapNotNull { personMap[it] } +
            personMap.values.filter { it.hasSpouseId(personB.id) }).distinctBy { it.id }
        val spouseOfA = spousesOfA.firstOrNull()
        val spouseOfB = spousesOfB.firstOrNull()

        fun areBioOrAdoptiveSiblings(p1: Person, p2: Person): Boolean {
            if (p1.id == p2.id) return false
            val sharesFather = !p1.fatherId.isNullOrBlank() && p1.fatherId == p2.fatherId
            val sharesMother = !p1.motherId.isNullOrBlank() && p1.motherId == p2.motherId
            return sharesFather || sharesMother
        }

        val matchingSpouseOfA = spousesOfA.firstOrNull { areBioOrAdoptiveSiblings(it, personB) }
        val matchingSpouseOfB = spousesOfB.firstOrNull { areBioOrAdoptiveSiblings(it, personA) }
        val aSpouseIsSiblingOfB = matchingSpouseOfA != null
        val bSpouseIsSiblingOfA = matchingSpouseOfB != null

        if (aSpouseIsSiblingOfB || bSpouseIsSiblingOfA) {
            val connectingSpouse = if (aSpouseIsSiblingOfB) matchingSpouseOfA!! else matchingSpouseOfB!!
            return TraceResult(
                personA = personA,
                personB = personB,
                isRelated = true,
                relationshipType = "Siblings-in-Law",
                relationshipCategory = "Affinity / In-Law",
                degreeOfConsanguinity = 0,
                commonAncestor = connectingSpouse,
                pathFromA = if (aSpouseIsSiblingOfB) listOf(personA, connectingSpouse) else listOf(personA),
                pathFromB = if (bSpouseIsSiblingOfA) listOf(personB, connectingSpouse) else listOf(personB),
                explanation = "${personA.firstName} and ${personB.firstName} are siblings-in-law (collateral affinity) through the marriage of ${connectingSpouse.firstName} ${connectingSpouse.lastName}. They share 0 degrees of biological consanguinity.",
                distanceA = 1,
                distanceB = 1,
                legalStatus = "Permissible under Philippine Law",
                legalArticle = "Family Code (Collateral Affinity)",
                isMarriageProhibited = false,
                legalDescription = "Siblings-in-law share no biological bloodline (0 degrees of consanguinity). Collateral affinity is not an impediment under Articles 37 or 38 of the Family Code once any prior existing marriage is dissolved.",
                commonAncestors = listOf(connectingSpouse),
                bloodlineSubtype = "Affinity via ${connectingSpouse.firstName} ${connectingSpouse.lastName}".trim()
            )
        }

        // 7. Universal Extended Spouse-Bridge Affinity (Grandparent-in-Law, Uncle/Aunt-in-Law, Cousin-in-Law, etc.)
        val matchedSpouseBPair = spousesOfB
            .filter { it.id != personA.id }
            .mapNotNull { spB -> traceBiologicalOnly(personA.id, spB.id, personMap)?.let { spB to it } }
            .minByOrNull { it.second.distanceA + it.second.distanceB }
        val bioAToSpouseB = matchedSpouseBPair?.second

        val matchedSpouseAPair = spousesOfA
            .filter { it.id != personB.id }
            .mapNotNull { spA -> traceBiologicalOnly(spA.id, personB.id, personMap)?.let { spA to it } }
            .minByOrNull { it.second.distanceA + it.second.distanceB }
        val bioBToSpouseA = matchedSpouseAPair?.second

        if (bioAToSpouseB != null || bioBToSpouseA != null) {
            val viaSpouseB = bioAToSpouseB != null && (
                bioBToSpouseA == null ||
                    (bioAToSpouseB.distanceA + bioAToSpouseB.distanceB) <= (bioBToSpouseA.distanceA + bioBToSpouseA.distanceB)
                )
            val bioTrace = if (viaSpouseB) bioAToSpouseB!! else bioBToSpouseA!!
            val connectingSpouse = if (viaSpouseB) matchedSpouseBPair!!.first else matchedSpouseAPair!!.first
            val bloodRelative = if (viaSpouseB) personA else personB
            val marriedInPerson = if (viaSpouseB) personB else personA

            val distRelative = if (viaSpouseB) bioTrace.distanceA else bioTrace.distanceB
            val distSpouse = if (viaSpouseB) bioTrace.distanceB else bioTrace.distanceA

            val inLawLabel = formatExtendedInLawTitle(
                bloodRelative = bloodRelative,
                marriedInPerson = marriedInPerson,
                distRelative = distRelative,
                distSpouse = distSpouse,
                baseBioType = bioTrace.relationshipType,
                aIsBloodRelative = viaSpouseB
            )

            // Build complete route path across the bloodline + marriage bridge
            val fullPathFromA: List<Person>
            val fullPathFromB: List<Person>
            val bridgeHighlightNodes: List<Person>

            if (viaSpouseB) {
                // personA is blood-related to spouseOfB, and spouseOfB is married to personB
                val bioChainAtoSpouseB = buildCompleteBioChain(bioTrace)
                fullPathFromA = bioChainAtoSpouseB
                fullPathFromB = listOf(personB, connectingSpouse)
                bridgeHighlightNodes = listOf(connectingSpouse)
            } else {
                // personB is blood-related to spouseOfA, and spouseOfA is married to personA
                val bioChainBtoSpouseA = buildCompleteBioChain(bioTrace).reversed()
                fullPathFromA = listOf(personA, connectingSpouse)
                fullPathFromB = bioChainBtoSpouseA
                bridgeHighlightNodes = listOf(connectingSpouse)
            }

            val spouseFullName = "${connectingSpouse.firstName} ${connectingSpouse.lastName}".trim()
            val bioSummary = describeBioRoleToSpouse(bloodRelative, connectingSpouse, distRelative, distSpouse, bioTrace.relationshipType)

            return TraceResult(
                personA = personA,
                personB = personB,
                isRelated = true,
                relationshipType = inLawLabel,
                relationshipCategory = "Affinity / In-Law",
                degreeOfConsanguinity = 0,
                commonAncestor = connectingSpouse,
                pathFromA = fullPathFromA,
                pathFromB = fullPathFromB,
                explanation = "$bioSummary, and $spouseFullName is married to ${marriedInPerson.firstName} ${marriedInPerson.lastName}. Through this marriage bridge, ${personA.firstName} and ${personB.firstName} are connected as $inLawLabel (Affinity / By Marriage) with 0 degrees of biological blood consanguinity.",
                distanceA = if (viaSpouseB) maxOf(1, bioTrace.distanceA + bioTrace.distanceB) else 1,
                distanceB = if (viaSpouseB) 1 else maxOf(1, bioTrace.distanceA + bioTrace.distanceB),
                legalStatus = "0° Blood Consanguinity (Connected by Marriage)",
                legalArticle = "Family Code (Extended Affinity via $spouseFullName)",
                isMarriageProhibited = false,
                legalDescription = decorateLegalDescriptionWithStatusNote(
                    personA,
                    personB,
                    "${personA.firstName} and ${personB.firstName} are connected by marriage through $spouseFullName ($inLawLabel). They share no biological bloodline (0 degrees of consanguinity).",
                    personMap
                ),
                commonAncestors = bridgeHighlightNodes,
                bloodlineSubtype = "Marriage Bridge: $spouseFullName"
            )
        }

        // 8. Co-Parents-in-Law (Balae) — Child of Person A is married to Child of Person B
        val childrenOfA = personMap.values.filter { it.fatherId == personA.id || it.motherId == personA.id }
        val childrenOfB = personMap.values.filter { it.fatherId == personB.id || it.motherId == personB.id }
        val marriedChildrenPair = childrenOfA.firstNotNullOfOrNull { cA ->
            val cB = childrenOfB.firstOrNull { it.id != cA.id && (cA.hasSpouseId(it.id) || it.hasSpouseId(cA.id)) }
            if (cB != null) cA to cB else null
        }
        if (marriedChildrenPair != null) {
            val (childA, childB) = marriedChildrenPair
            val coupleNames = "${childA.firstName} ${childA.lastName} & ${childB.firstName} ${childB.lastName}"
            return TraceResult(
                personA = personA,
                personB = personB,
                isRelated = true,
                relationshipType = "Co-Parents-in-Law (Balae)",
                relationshipCategory = "Affinity / In-Law",
                degreeOfConsanguinity = 0,
                commonAncestor = childA,
                pathFromA = listOf(personA, childA),
                pathFromB = listOf(personB, childB),
                explanation = "${personA.firstName} and ${personB.firstName} are co-parents-in-law (Filipino 'Balae') through the marriage of their children, $coupleNames. They share 0 degrees of biological consanguinity.",
                distanceA = 1,
                distanceB = 1,
                legalStatus = "0° Blood Consanguinity (Connected by Marriage)",
                legalArticle = "Family Code (No Blood Impediment)",
                isMarriageProhibited = false,
                legalDescription = decorateLegalDescriptionWithStatusNote(
                    personA,
                    personB,
                    "Co-parents-in-law (Balae) share no biological bloodline (0 degrees of consanguinity) and have no marriage impediment under Articles 37 or 38 of the Family Code.",
                    personMap
                ),
                commonAncestors = listOf(childA, childB),
                bloodlineSubtype = "Marriage Bridge: $coupleNames"
            )
        }

        // 9. Co-In-Laws (Bilas / Spouses of Blood Relatives)
        if (spouseOfA != null && spouseOfB != null && spouseOfA.id != spouseOfB.id) {
            val spousesBio = traceBiologicalOnly(spouseOfA.id, spouseOfB.id, personMap)
            if (spousesBio != null && spousesBio.isRelated) {
                val isCoSiblingsInLaw = spousesBio.distanceA == 1 && spousesBio.distanceB == 1
                val relTitle = if (isCoSiblingsInLaw) {
                    "Co-Siblings-in-Law (Bilas)"
                } else {
                    "${spousesBio.relationshipType} by Marriage"
                }
                val coupleBridge = "${spouseOfA.firstName} ${spouseOfA.lastName} & ${spouseOfB.firstName} ${spouseOfB.lastName}"
                return TraceResult(
                    personA = personA,
                    personB = personB,
                    isRelated = true,
                    relationshipType = relTitle,
                    relationshipCategory = "Affinity / In-Law",
                    degreeOfConsanguinity = 0,
                    commonAncestor = spouseOfA,
                    pathFromA = listOf(personA, spouseOfA) + spousesBio.pathFromA.drop(1),
                    pathFromB = listOf(personB, spouseOfB) + spousesBio.pathFromB.drop(1),
                    explanation = "${personA.firstName} is married to ${spouseOfA.firstName} ${spouseOfA.lastName}, and ${personB.firstName} is married to ${spouseOfB.firstName} ${spouseOfB.lastName} (${spousesBio.relationshipType.lowercase()}). Through their spouses, they are connected as $relTitle with 0 degrees of biological blood consanguinity.",
                    distanceA = 1 + spousesBio.distanceA,
                    distanceB = 1 + spousesBio.distanceB,
                    legalStatus = "0° Blood Consanguinity (Connected by Marriage)",
                    legalArticle = "Family Code (Extended Affinity)",
                    isMarriageProhibited = false,
                    legalDescription = decorateLegalDescriptionWithStatusNote(
                        personA,
                        personB,
                        "Connected through two marriages ($coupleBridge). They share no biological bloodline (0 degrees of consanguinity).",
                        personMap
                    ),
                    commonAncestors = (listOf(spouseOfA, spouseOfB) + spousesBio.commonAncestors).distinctBy { it.id },
                    bloodlineSubtype = "Marriage Bridge: $coupleBridge"
                )
            }
        }

        return null
    }

    /**
     * Pure biological consanguinity trace between two members without non-biological recursion.
     */
    private fun traceBiologicalOnly(
        personAId: String,
        personBId: String,
        personMap: Map<String, Person>
    ): TraceResult? {
        if (personAId == personBId) return null
        val personA = personMap[personAId] ?: return null
        val personB = personMap[personBId] ?: return null

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
                explanation = "",
                distanceA = 0, distanceB = distance,
                commonAncestors = listOf(personA)
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
                explanation = "",
                distanceA = distance, distanceB = 0,
                commonAncestors = listOf(personB)
            )
        }

        val commonAncestorIds = ancestorsA.keys.intersect(ancestorsB.keys)
        if (commonAncestorIds.isEmpty()) return null

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

        val coEqualAncestors = commonAncestorIds
            .filter { id ->
                val d1 = ancestorsA[id]!!.first
                val d2 = ancestorsB[id]!!.first
                (d1 + d2) == minTotalDistance && d1 == bestD1 && d2 == bestD2
            }
            .mapNotNull { personMap[it] }
            .ifEmpty { listOfNotNull(personMap[nearestAncestorId]) }

        val commonAncestor = coEqualAncestors.firstOrNull() ?: personMap[nearestAncestorId]!!
        val pathA = ancestorsA[commonAncestor.id]?.second ?: ancestorsA[nearestAncestorId]!!.second
        val pathB = ancestorsB[commonAncestor.id]?.second ?: ancestorsB[nearestAncestorId]!!.second
        val classification = classifier.classify(bestD1, bestD2, isLineal = false)

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
            explanation = "",
            distanceA = bestD1,
            distanceB = bestD2,
            commonAncestors = coEqualAncestors
        )
    }

    private fun buildCompleteBioChain(bioTrace: TraceResult): List<Person> {
        val upFromA = bioTrace.pathFromA
        val downToB = bioTrace.pathFromB.reversed().drop(1)
        return (upFromA + downToB).distinctBy { it.id }
    }

    private fun formatExtendedInLawTitle(
        bloodRelative: Person,
        marriedInPerson: Person,
        distRelative: Int,
        distSpouse: Int,
        baseBioType: String,
        aIsBloodRelative: Boolean
    ): String {
        val relMale = bloodRelative.gender.equals("Male", ignoreCase = true)
        val relFemale = bloodRelative.gender.equals("Female", ignoreCase = true)
        val marMale = marriedInPerson.gender.equals("Male", ignoreCase = true)
        val marFemale = marriedInPerson.gender.equals("Female", ignoreCase = true)

        // 1. Blood relative is Lineal Ancestor of Spouse (e.g. Grandparent-in-Law / Great-Grandparent-in-Law)
        if (distRelative == 0 && distSpouse >= 2) {
            val greats = if (distSpouse > 2) "Great-".repeat(distSpouse - 2) else ""
            val ancRole = when {
                relMale -> "${greats}Grandfather-in-Law"
                relFemale -> "${greats}Grandmother-in-Law"
                else -> "${greats}Grandparent-in-Law"
            }
            val descRole = when {
                marMale -> "${greats}Grandson-in-Law"
                marFemale -> "${greats}Granddaughter-in-Law"
                else -> "${greats}Grandchild-in-Law"
            }
            return if (aIsBloodRelative) "$ancRole / $descRole" else "$descRole / $ancRole"
        }

        // 2. Blood relative is Lineal Descendant of Spouse (e.g. Grandchild of Spouse)
        if (distRelative >= 2 && distSpouse == 0) {
            val greats = if (distRelative > 2) "Great-".repeat(distRelative - 2) else ""
            val descRole = when {
                relMale -> "${greats}Grandson-in-Law"
                relFemale -> "${greats}Granddaughter-in-Law"
                else -> "${greats}Grandchild-in-Law"
            }
            val ancRole = when {
                marMale -> "${greats}Grandfather-in-Law"
                marFemale -> "${greats}Grandmother-in-Law"
                else -> "${greats}Grandparent-in-Law"
            }
            return if (aIsBloodRelative) "$descRole / $ancRole" else "$ancRole / $descRole"
        }

        // 3. Blood relative is Uncle/Aunt of Spouse (distRelative == 1 && distSpouse == 2)
        if (distRelative == 1 && distSpouse == 2) {
            val elderRole = when {
                relMale -> "Uncle-in-Law"
                relFemale -> "Aunt-in-Law"
                else -> "Uncle/Aunt-in-Law"
            }
            val youngerRole = when {
                marMale -> "Nephew-in-Law"
                marFemale -> "Niece-in-Law"
                else -> "Nibling-in-Law"
            }
            return if (aIsBloodRelative) "$elderRole / $youngerRole" else "$youngerRole / $elderRole"
        }

        // 4. Blood relative is Niece/Nephew of Spouse (distRelative == 2 && distSpouse == 1)
        if (distRelative == 2 && distSpouse == 1) {
            val youngerRole = when {
                relMale -> "Nephew-in-Law"
                relFemale -> "Niece-in-Law"
                else -> "Nibling-in-Law"
            }
            val elderRole = when {
                marMale -> "Uncle-in-Law"
                marFemale -> "Aunt-in-Law"
                else -> "Uncle/Aunt-in-Law"
            }
            return if (aIsBloodRelative) "$youngerRole / $elderRole" else "$elderRole / $youngerRole"
        }

        // 5. Great-Uncle/Aunt of Spouse (distRelative == 1 && distSpouse == 3, or 3 and 1)
        if (distRelative == 1 && distSpouse == 3) {
            val elderRole = when {
                relMale -> "Great-Uncle-in-Law"
                relFemale -> "Great-Aunt-in-Law"
                else -> "Great-Uncle/Aunt-in-Law"
            }
            val youngerRole = when {
                marMale -> "Grandnephew-in-Law"
                marFemale -> "Grandniece-in-Law"
                else -> "Grandnibling-in-Law"
            }
            return if (aIsBloodRelative) "$elderRole / $youngerRole" else "$youngerRole / $elderRole"
        }
        if (distRelative == 3 && distSpouse == 1) {
            val youngerRole = when {
                relMale -> "Grandnephew-in-Law"
                relFemale -> "Grandniece-in-Law"
                else -> "Grandnibling-in-Law"
            }
            val elderRole = when {
                marMale -> "Great-Uncle-in-Law"
                marFemale -> "Great-Aunt-in-Law"
                else -> "Great-Uncle/Aunt-in-Law"
            }
            return if (aIsBloodRelative) "$youngerRole / $elderRole" else "$elderRole / $youngerRole"
        }

        // 6. Cousins of Spouse (First Cousins-in-Law, Second Cousins-in-Law, etc.)
        if (baseBioType.contains("Cousins", ignoreCase = true)) {
            return if (baseBioType.endsWith("Cousins", ignoreCase = true)) {
                "$baseBioType-in-Law"
            } else {
                baseBioType.replace("Cousins", "Cousins-in-Law")
            }
        }

        return "$baseBioType (In-Law)"
    }

    private fun describeBioRoleToSpouse(
        bloodRelative: Person,
        connectingSpouse: Person,
        distRelative: Int,
        distSpouse: Int,
        baseBioType: String
    ): String {
        val relName = "${bloodRelative.firstName} ${bloodRelative.lastName}".trim()
        val spouseName = "${connectingSpouse.firstName} ${connectingSpouse.lastName}".trim()
        val relMale = bloodRelative.gender.equals("Male", ignoreCase = true)
        val relFemale = bloodRelative.gender.equals("Female", ignoreCase = true)

        return when {
            distRelative == 0 && distSpouse == 2 -> {
                val role = if (relMale) "biological grandfather" else if (relFemale) "biological grandmother" else "biological grandparent"
                "$relName is the $role of $spouseName"
            }
            distRelative == 0 && distSpouse >= 3 -> {
                val greats = "great-".repeat(distSpouse - 2)
                val role = if (relMale) "biological ${greats}grandfather" else if (relFemale) "biological ${greats}grandmother" else "biological ${greats}grandparent"
                "$relName is the $role of $spouseName"
            }
            distRelative == 1 && distSpouse == 2 -> {
                val role = if (relMale) "biological uncle" else if (relFemale) "biological aunt" else "biological uncle/aunt"
                "$relName is the $role of $spouseName"
            }
            distRelative == 2 && distSpouse == 2 -> {
                "$relName is the biological first cousin of $spouseName"
            }
            else -> {
                "$relName and $spouseName are biological ${baseBioType.lowercase()}"
            }
        }
    }

    private fun decorateLegalDescriptionWithStatusNote(
        personA: Person,
        personB: Person,
        baseDescription: String,
        personMap: Map<String, Person>
    ): String {
        val statusNotes = mutableListOf<String>()
        for (p in listOf(personA, personB)) {
            val fullName = "${p.firstName} ${p.lastName}".trim()
            if (!p.isLiving) {
                val dYr = p.deathDate.take(4).trim()
                statusNotes.add(if (dYr.isNotEmpty()) "$fullName is Deceased (d. $dYr)" else "$fullName is Deceased")
            } else if (!p.spouseId.isNullOrBlank()) {
                val spouse = personMap[p.spouseId]
                if (spouse != null && spouse.id != personA.id && spouse.id != personB.id) {
                    statusNotes.add("$fullName is currently married to ${spouse.firstName} ${spouse.lastName}".trim())
                }
            }
        }
        return if (statusNotes.isEmpty()) {
            baseDescription
        } else {
            "$baseDescription (Vital & Marital Status Note: This evaluates kinship degree only — ${statusNotes.joinToString("; ")}.)"
        }
    }
}