package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * Resolves genealogical kinship titles (e.g. FATHER, GRANDMOTHER, FULL BROTHER, HALF-SISTER, STEPBROTHER, PATRIARCH)
 * for any person in a family tree relative to an active focal person.
 *
 * Sibling and Step-Family Rules:
 * 1. Share both biological parents -> Full Siblings (FULL BROTHER / FULL SISTER).
 * 2. Share only one biological parent -> Half-Siblings (HALF-BROTHER / HALF-SISTER).
 * 3. Share no biological parent, but parents are spouses/partners -> Step-Siblings (STEPBROTHER / STEPSISTER).
 * 4. Spouse of a child's biological parent -> Step-Parent (STEPFATHER / STEPMOTHER), unless separate adoption record exists.
 * 5. Step-parents, step-siblings, and in-laws are NOT treated as blood relatives in consanguinity calculation (0 degrees).
 * 6. Half-siblings share one biological parent and remain blood relatives (2nd degree collateral consanguinity).
 */
object KinshipTitleHelper {

    enum class SiblingCategory {
        FULL_SIBLING,
        HALF_SIBLING,
        STEP_SIBLING,
        ADOPTIVE_SIBLING,
        NONE
    }

    data class SiblingGroup(
        val fullSiblings: List<Person> = emptyList(),
        val halfSiblings: List<Person> = emptyList(),
        val stepSiblings: List<Person> = emptyList(),
        val adoptiveSiblings: List<Person> = emptyList()
    ) {
        val all: List<Person> get() = (fullSiblings + halfSiblings + stepSiblings + adoptiveSiblings).distinctBy { it.id }
        val hasAny: Boolean get() = all.isNotEmpty()
        val totalCount: Int get() = all.size
    }

    data class StepFamilyGroup(
        val stepParents: List<Person> = emptyList(),
        val stepChildren: List<Person> = emptyList(),
        val stepSiblings: List<Person> = emptyList()
    ) {
        val hasAny: Boolean get() = stepParents.isNotEmpty() || stepChildren.isNotEmpty() || stepSiblings.isNotEmpty()
    }

    fun isBio(relType: String?): Boolean =
        relType.isNullOrBlank() || relType.equals("Biological", ignoreCase = true)

    fun isAdopt(relType: String?): Boolean =
        relType != null && relType.equals("Adopted", ignoreCase = true)

    /**
     * Classifies the sibling relationship between [a] and [b].
     */
    fun classifySibling(
        a: Person,
        b: Person,
        byId: Map<String, Person>
    ): SiblingCategory {
        if (a.id == b.id) return SiblingCategory.NONE

        val aFatherId = a.fatherId?.takeIf { it.isNotBlank() }
        val aMotherId = a.motherId?.takeIf { it.isNotBlank() }
        val bFatherId = b.fatherId?.takeIf { it.isNotBlank() }
        val bMotherId = b.motherId?.takeIf { it.isNotBlank() }

        // Adoptive check: either child has an adoptive parent and they share that parent ID
        val isAdoptive = (isAdopt(a.fatherRelationshipType) || isAdopt(b.fatherRelationshipType)) &&
                !aFatherId.isNullOrBlank() && aFatherId == bFatherId ||
                (isAdopt(a.motherRelationshipType) || isAdopt(b.motherRelationshipType)) &&
                !aMotherId.isNullOrBlank() && aMotherId == bMotherId
        if (isAdoptive) {
            return SiblingCategory.ADOPTIVE_SIBLING
        }

        val aBioFather = if (isBio(a.fatherRelationshipType)) aFatherId else null
        val aBioMother = if (isBio(a.motherRelationshipType)) aMotherId else null
        val bBioFather = if (isBio(b.fatherRelationshipType)) bFatherId else null
        val bBioMother = if (isBio(b.motherRelationshipType)) bMotherId else null

        val sharesBioFather = !aBioFather.isNullOrBlank() && aBioFather == bBioFather
        val sharesBioMother = !aBioMother.isNullOrBlank() && aBioMother == bBioMother

        // Rule 1: If two children share both biological parents, label them as full siblings.
        if (sharesBioFather && sharesBioMother) {
            return SiblingCategory.FULL_SIBLING
        }

        // Rule 2: If two children share only one biological parent, label them as half-siblings.
        if (sharesBioFather || sharesBioMother) {
            return SiblingCategory.HALF_SIBLING
        }

        // Rule 3: If two children share no biological parent, but their parents are spouses or partners, label them as step-siblings.
        val aParents = listOfNotNull(aFatherId?.let { byId[it] }, aMotherId?.let { byId[it] })
        val bParents = listOfNotNull(bFatherId?.let { byId[it] }, bMotherId?.let { byId[it] })

        val parentsAreSpouses = aParents.any { pA ->
            bParents.any { pB ->
                pA.id != pB.id && (pA.spouseId == pB.id || pB.spouseId == pA.id)
            }
        }

        if (parentsAreSpouses) {
            return SiblingCategory.STEP_SIBLING
        }

        return SiblingCategory.NONE
    }

    /**
     * Resolves categorized siblings (Full, Half, Step, Adoptive) for a person.
     */
    fun getSiblingsForPerson(person: Person, allMembers: List<Person>): SiblingGroup {
        val byId = allMembers.associateBy { it.id }
        val full = mutableListOf<Person>()
        val half = mutableListOf<Person>()
        val step = mutableListOf<Person>()
        val adopt = mutableListOf<Person>()

        for (other in allMembers) {
            if (other.id == person.id) continue
            when (classifySibling(other, person, byId)) {
                SiblingCategory.FULL_SIBLING -> full.add(other)
                SiblingCategory.HALF_SIBLING -> half.add(other)
                SiblingCategory.STEP_SIBLING -> step.add(other)
                SiblingCategory.ADOPTIVE_SIBLING -> adopt.add(other)
                SiblingCategory.NONE -> {}
            }
        }
        return SiblingGroup(
            fullSiblings = full,
            halfSiblings = half,
            stepSiblings = step,
            adoptiveSiblings = adopt
        )
    }

    /**
     * Resolves step-family members (step-parents, step-children, step-siblings) for a person.
     */
    fun getStepFamilyForPerson(person: Person, allMembers: List<Person>): StepFamilyGroup {
        val byId = allMembers.associateBy { it.id }
        val stepParents = mutableListOf<Person>()
        val stepChildren = mutableListOf<Person>()

        val father = person.fatherId?.let { byId[it] }
        val mother = person.motherId?.let { byId[it] }

        // Step-parents: spouse of biological mother or father who is not direct parent
        if (father != null && isBio(person.fatherRelationshipType)) {
            val fatherSpouse = father.spouseId?.let { byId[it] }
            if (fatherSpouse != null && fatherSpouse.id != person.motherId && !isAdopt(person.motherRelationshipType)) {
                stepParents.add(fatherSpouse)
            }
        }
        if (mother != null && isBio(person.motherRelationshipType)) {
            val motherSpouse = mother.spouseId?.let { byId[it] }
            if (motherSpouse != null && motherSpouse.id != person.fatherId && !isAdopt(person.fatherRelationshipType)) {
                stepParents.add(motherSpouse)
            }
        }

        // Step-children: children of person's spouse who are not person's children
        val spouse = person.spouseId?.let { byId[it] }
        if (spouse != null) {
            for (m in allMembers) {
                if (m.id == person.id) continue
                val isChildOfSpouse = m.fatherId == spouse.id || m.motherId == spouse.id
                val isChildOfPerson = m.fatherId == person.id || m.motherId == person.id
                if (isChildOfSpouse && !isChildOfPerson) {
                    stepChildren.add(m)
                }
            }
        }

        val stepSiblings = getSiblingsForPerson(person, allMembers).stepSiblings

        return StepFamilyGroup(
            stepParents = stepParents.distinctBy { it.id },
            stepChildren = stepChildren.distinctBy { it.id },
            stepSiblings = stepSiblings
        )
    }

    /**
     * Resolves a concise kinship title badge (e.g. "FATHER", "FULL BROTHER", "HALF-SISTER", "PATRIARCH")
     * for [target] relative to [focal].
     */
    fun resolveTitle(
        target: Person,
        focal: Person?,
        allMembers: List<Person>
    ): String {
        val (title, _) = resolveKinship(target, focal, allMembers)
        return title
    }

    /**
     * Batch-resolves kinship titles for ALL members in [allMembers] relative to [focal].
     * Computes the indexed graph ONCE in O(N) rather than allocating maps per member O(N^2).
     */
    fun resolveAllTitles(
        allMembers: List<Person>,
        focal: Person?
    ): Map<String, String> {
        if (allMembers.isEmpty()) return emptyMap()
        val byId = allMembers.associateBy { it.id }
        val childrenOf = mutableMapOf<String, MutableSet<String>>()
        allMembers.forEach { m ->
            listOfNotNull(m.fatherId, m.motherId).filter { it.isNotBlank() }.forEach { pid ->
                childrenOf.getOrPut(pid) { mutableSetOf() }.add(m.id)
            }
        }
        return allMembers.associate { p ->
            p.id to resolveKinshipIndexed(p, focal, allMembers, byId, childrenOf).first
        }
    }

    /**
     * Resolves a natural-language descriptive subtitle (e.g. "Father of Renzy", "Full Brother of Renzy")
     * for [target] relative to [focal].
     */
    fun resolveSubtitle(
        target: Person,
        focal: Person?,
        allMembers: List<Person>
    ): String {
        val (_, subtitle) = resolveKinship(target, focal, allMembers)
        return subtitle
    }

    private fun resolveKinship(
        target: Person,
        focal: Person?,
        allMembers: List<Person>
    ): Pair<String, String> {
        val byId = allMembers.associateBy { it.id }
        val childrenOf = mutableMapOf<String, MutableSet<String>>()
        allMembers.forEach { m ->
            listOfNotNull(m.fatherId, m.motherId).filter { it.isNotBlank() }.forEach { pid ->
                childrenOf.getOrPut(pid) { mutableSetOf() }.add(m.id)
            }
        }
        return resolveKinshipIndexed(target, focal, allMembers, byId, childrenOf)
    }

    private fun resolveKinshipIndexed(
        target: Person,
        focal: Person?,
        allMembers: List<Person>,
        byId: Map<String, Person>,
        childrenOf: Map<String, Set<String>>
    ): Pair<String, String> {
        val isMale = target.gender.trim().equals("Male", ignoreCase = true)
        val isFemale = target.gender.trim().equals("Female", ignoreCase = true)

        // 1. Same person or no focal person selected
        if (focal == null || target.id == focal.id) {
            if (focal != null && target.id == focal.id) {
                return "SELF" to "Current Member"
            }
            // Fallback when focal is null: determine structural position
            return resolveStructuralKinship(target, allMembers, isMale, isFemale, byId, childrenOf)
        }

        val focalName = focal.firstName.ifBlank { "You" }
        val focalSpouse = focal.spouseId?.let { byId[it] }

        // 2. Direct Parents (1st Degree Ascendant)
        if (focal.fatherId == target.id) {
            if (isAdopt(focal.fatherRelationshipType)) {
                return (if (isMale) "ADOPTIVE FATHER" else "ADOPTIVE PARENT") to "Adoptive Father of $focalName"
            }
            return "FATHER" to "Father of $focalName"
        }
        if (focal.motherId == target.id) {
            if (isAdopt(focal.motherRelationshipType)) {
                return (if (isFemale) "ADOPTIVE MOTHER" else "ADOPTIVE PARENT") to "Adoptive Mother of $focalName"
            }
            return "MOTHER" to "Mother of $focalName"
        }

        // 3. Spouse
        if ((!focal.spouseId.isNullOrBlank() && focal.spouseId == target.id) ||
            (!target.spouseId.isNullOrBlank() && target.spouseId == focal.id)
        ) {
            val title = if (isMale) "HUSBAND" else if (isFemale) "WIFE" else "SPOUSE"
            val label = if (isMale) "Husband" else if (isFemale) "Wife" else "Spouse"
            return title to "$label of $focalName"
        }

        // 4. Direct Children (1st Degree Descendant)
        if (target.fatherId == focal.id || target.motherId == focal.id) {
            val isAdoptiveChild = (target.fatherId == focal.id && isAdopt(target.fatherRelationshipType)) ||
                    (target.motherId == focal.id && isAdopt(target.motherRelationshipType))
            if (isAdoptiveChild) {
                val title = if (isMale) "ADOPTIVE SON" else if (isFemale) "ADOPTIVE DAUGHTER" else "ADOPTIVE CHILD"
                val label = if (isMale) "Adoptive Son" else if (isFemale) "Adoptive Daughter" else "Adoptive Child"
                return title to "$label of $focalName"
            }
            val title = if (isMale) "SON" else if (isFemale) "DAUGHTER" else "CHILD"
            val label = if (isMale) "Son" else if (isFemale) "Daughter" else "Child"
            return title to "$label of $focalName"
        }

        val focalFather = focal.fatherId?.let { byId[it] }
        val focalMother = focal.motherId?.let { byId[it] }

        // 4b. Step-Parents (Spouse of biological parent, without being direct parent or adoptive parent)
        val isStepFather = (focalMother != null && isBio(focal.motherRelationshipType) &&
                focalMother.spouseId == target.id && target.id != focal.fatherId && !isAdopt(focal.fatherRelationshipType))
        val isStepMother = (focalFather != null && isBio(focal.fatherRelationshipType) &&
                focalFather.spouseId == target.id && target.id != focal.motherId && !isAdopt(focal.motherRelationshipType))
        if (isStepFather) {
            return "STEPFATHER" to "Stepfather of $focalName"
        }
        if (isStepMother) {
            return "STEPMOTHER" to "Stepmother of $focalName"
        }

        // 4c. Step-Children (Child of spouse, without being direct child)
        if (focalSpouse != null && (target.fatherId == focalSpouse.id || target.motherId == focalSpouse.id) &&
            target.fatherId != focal.id && target.motherId != focal.id
        ) {
            val title = if (isMale) "STEPSON" else if (isFemale) "STEPDAUGHTER" else "STEPCHILD"
            val label = if (isMale) "Step-son" else if (isFemale) "Step-daughter" else "Step-child"
            return title to "$label of $focalName"
        }

        // 5. Grandparents (2nd Degree Ascendant)
        if (focalFather?.fatherId == target.id) {
            return "GRANDFATHER" to "Paternal Grandfather of $focalName"
        }
        if (focalFather?.motherId == target.id) {
            return "GRANDMOTHER" to "Paternal Grandmother of $focalName"
        }
        if (focalMother?.fatherId == target.id) {
            return "GRANDFATHER" to "Maternal Grandfather of $focalName"
        }
        if (focalMother?.motherId == target.id) {
            return "GRANDMOTHER" to "Maternal Grandmother of $focalName"
        }

        // 6. Grandchildren (2nd Degree Descendant)
        val focalChildIds = childrenOf[focal.id] ?: emptySet()
        val isGrandchild = focalChildIds.any { childId ->
            val childKids = childrenOf[childId] ?: emptySet()
            childKids.contains(target.id)
        }
        if (isGrandchild) {
            val title = if (isMale) "GRANDSON" else if (isFemale) "GRANDDAUGHTER" else "GRANDCHILD"
            val label = if (isMale) "Grandson" else if (isFemale) "Granddaughter" else "Grandchild"
            return title to "$label of $focalName"
        }

        // 7. Siblings (Rules 1, 2, 3: Full, Half, Step, Adoptive)
        when (classifySibling(target, focal, byId)) {
            SiblingCategory.FULL_SIBLING -> {
                val title = if (isMale) "FULL BROTHER" else if (isFemale) "FULL SISTER" else "FULL SIBLING"
                val label = if (isMale) "Full Brother" else if (isFemale) "Full Sister" else "Full Sibling"
                return title to "$label of $focalName"
            }
            SiblingCategory.HALF_SIBLING -> {
                val title = if (isMale) "HALF-BROTHER" else if (isFemale) "HALF-SISTER" else "HALF-SIBLING"
                val label = if (isMale) "Half-Brother" else if (isFemale) "Half-Sister" else "Half-Sibling"
                return title to "$label of $focalName"
            }
            SiblingCategory.STEP_SIBLING -> {
                val title = if (isMale) "STEPBROTHER" else if (isFemale) "STEPSISTER" else "STEP-SIBLING"
                val label = if (isMale) "Stepbrother" else if (isFemale) "Stepsister" else "Step-sibling"
                return title to "$label of $focalName"
            }
            SiblingCategory.ADOPTIVE_SIBLING -> {
                val title = if (isMale) "ADOPTIVE BROTHER" else if (isFemale) "ADOPTIVE SISTER" else "ADOPTIVE SIBLING"
                val label = if (isMale) "Adoptive Brother" else if (isFemale) "Adoptive Sister" else "Adoptive Sibling"
                return title to "$label of $focalName"
            }
            SiblingCategory.NONE -> {}
        }

        // 8. Great-Grandparents (3rd Degree Ascendant)
        val grandparents = listOfNotNull(
            focalFather?.fatherId?.let { byId[it] },
            focalFather?.motherId?.let { byId[it] },
            focalMother?.fatherId?.let { byId[it] },
            focalMother?.motherId?.let { byId[it] }
        )
        val isGreatGrandparent = grandparents.any { gp ->
            gp.fatherId == target.id || gp.motherId == target.id
        }
        if (isGreatGrandparent) {
            val title = if (isMale) "GREAT-GRANDFATHER" else if (isFemale) "GREAT-GRANDMOTHER" else "GREAT-GRANDPARENT"
            val label = if (isMale) "Great-Grandfather" else if (isFemale) "Great-Grandmother" else "Great-Grandparent"
            return title to "$label of $focalName"
        }

        // 9. Great-Grandchildren (3rd Degree Descendant)
        val isGreatGrandchild = focalChildIds.any { cId ->
            val gKids = childrenOf[cId] ?: emptySet()
            gKids.any { gId ->
                (childrenOf[gId] ?: emptySet()).contains(target.id)
            }
        }
        if (isGreatGrandchild) {
            val title = if (isMale) "GREAT-GRANDSON" else if (isFemale) "GREAT-GRANDDAUGHTER" else "GREAT-GRANDCHILD"
            val label = if (isMale) "Great-Grandson" else if (isFemale) "Great-Granddaughter" else "Great-Grandchild"
            return title to "$label of $focalName"
        }

        // 10. Great-Great-Grandparents (4th Degree Ascendant)
        val greatGrandparents = grandparents.flatMap { gp ->
            listOfNotNull(gp.fatherId?.let { byId[it] }, gp.motherId?.let { byId[it] })
        }
        val isGreatGreatGrandparent = greatGrandparents.any { ggp ->
            ggp.fatherId == target.id || ggp.motherId == target.id
        }
        if (isGreatGreatGrandparent) {
            val title = if (isMale) "GR-GREAT-GRANDFATHER" else if (isFemale) "GR-GREAT-GRANDMOTHER" else "GR-GREAT-GRANDPARENT"
            return title to "Great-Great-Grandparent of $focalName"
        }

        // 11. Aunts & Uncles (Parent's Siblings)
        val parentSiblingIds = mutableSetOf<String>()
        focalFather?.let { f ->
            f.fatherId?.let { childrenOf[it]?.let { s -> parentSiblingIds.addAll(s) } }
            f.motherId?.let { childrenOf[it]?.let { s -> parentSiblingIds.addAll(s) } }
            parentSiblingIds.remove(f.id)
        }
        focalMother?.let { m ->
            m.fatherId?.let { childrenOf[it]?.let { s -> parentSiblingIds.addAll(s) } }
            m.motherId?.let { childrenOf[it]?.let { s -> parentSiblingIds.addAll(s) } }
            parentSiblingIds.remove(m.id)
        }
        if (parentSiblingIds.contains(target.id)) {
            val title = if (isMale) "UNCLE" else if (isFemale) "AUNT" else "AUNT/UNCLE"
            val label = if (isMale) "Uncle" else if (isFemale) "Aunt" else "Aunt/Uncle"
            return title to "$label of $focalName"
        }

        // 12. Nieces & Nephews (Sibling's Children)
        val focalSiblingIds = mutableSetOf<String>()
        focal.fatherId?.let { childrenOf[it]?.let { s -> focalSiblingIds.addAll(s) } }
        focal.motherId?.let { childrenOf[it]?.let { s -> focalSiblingIds.addAll(s) } }
        focalSiblingIds.remove(focal.id)

        val isNephewOrNiece = focalSiblingIds.any { sibId ->
            (childrenOf[sibId] ?: emptySet()).contains(target.id)
        }
        if (isNephewOrNiece) {
            val title = if (isMale) "NEPHEW" else if (isFemale) "NIECE" else "NEPHEW/NIECE"
            val label = if (isMale) "Nephew" else if (isFemale) "Niece" else "Nephew/Niece"
            return title to "$label of $focalName"
        }

        // 13. First Cousins (Aunt/Uncle's Children)
        val isCousin = parentSiblingIds.any { auntUncleId ->
            (childrenOf[auntUncleId] ?: emptySet()).contains(target.id)
        }
        if (isCousin) {
            return "COUSIN" to "First Cousin of $focalName"
        }

        // 14. In-Laws (Spouse Parents / Siblings / Child's Spouse)
        if (focalSpouse != null) {
            if (focalSpouse.fatherId == target.id) {
                return "FATHER-IN-LAW" to "Father-in-law of $focalName"
            }
            if (focalSpouse.motherId == target.id) {
                return "MOTHER-IN-LAW" to "Mother-in-law of $focalName"
            }
            val spouseSiblingIds = mutableSetOf<String>()
            focalSpouse.fatherId?.let { childrenOf[it]?.let { s -> spouseSiblingIds.addAll(s) } }
            focalSpouse.motherId?.let { childrenOf[it]?.let { s -> spouseSiblingIds.addAll(s) } }
            spouseSiblingIds.remove(focalSpouse.id)
            if (spouseSiblingIds.contains(target.id)) {
                val title = if (isMale) "BROTHER-IN-LAW" else if (isFemale) "SISTER-IN-LAW" else "SIBLING-IN-LAW"
                return title to "In-law of $focalName"
            }
        }

        // Sibling's spouse
        val isSiblingSpouse = focalSiblingIds.any { sibId ->
            val sib = byId[sibId]
            sib?.spouseId == target.id || target.spouseId == sibId
        }
        if (isSiblingSpouse) {
            val title = if (isMale) "BROTHER-IN-LAW" else if (isFemale) "SISTER-IN-LAW" else "SIBLING-IN-LAW"
            return title to "In-law of $focalName"
        }

        // Child's spouse
        val isChildSpouse = focalChildIds.any { cId ->
            val c = byId[cId]
            c?.spouseId == target.id || target.spouseId == cId
        }
        if (isChildSpouse) {
            val title = if (isMale) "SON-IN-LAW" else if (isFemale) "DAUGHTER-IN-LAW" else "CHILD-IN-LAW"
            return title to "In-law of $focalName"
        }

        // 15. Structural Position Fallback
        return resolveStructuralKinship(target, allMembers, isMale, isFemale, byId, childrenOf)
    }

    private fun resolveStructuralKinship(
        target: Person,
        allMembers: List<Person>,
        isMale: Boolean,
        isFemale: Boolean,
        byId: Map<String, Person> = allMembers.associateBy { it.id },
        childrenOf: Map<String, Set<String>> = emptyMap()
    ): Pair<String, String> {
        val hasNoParents = (target.fatherId.isNullOrBlank() || !byId.containsKey(target.fatherId)) &&
                (target.motherId.isNullOrBlank() || !byId.containsKey(target.motherId))

        val hasChildren = childrenOf[target.id]?.isNotEmpty() == true ||
                allMembers.any { it.fatherId == target.id || it.motherId == target.id }

        if (hasNoParents && hasChildren) {
            val title = if (isMale) "PATRIARCH" else if (isFemale) "MATRIARCH" else "ANCESTOR"
            val sub = if (isMale) "Founding Patriarch" else if (isFemale) "Founding Matriarch" else "Family Founder"
            return title to sub
        }

        if (hasChildren) {
            val title = if (isMale) "FATHER" else if (isFemale) "MOTHER" else "PARENT"
            val sub = if (isMale) "Father / Parent" else if (isFemale) "Mother / Parent" else "Parent"
            return title to sub
        }

        val hasParents = (!target.fatherId.isNullOrBlank() && byId.containsKey(target.fatherId)) ||
                (!target.motherId.isNullOrBlank() && byId.containsKey(target.motherId))
        if (hasParents) {
            val title = if (isMale) "SON" else if (isFemale) "DAUGHTER" else "CHILD"
            val sub = if (isMale) "Son" else if (isFemale) "Daughter" else "Child"
            return title to sub
        }

        if (!target.spouseId.isNullOrBlank() && byId.containsKey(target.spouseId)) {
            val title = if (isMale) "HUSBAND" else if (isFemale) "WIFE" else "SPOUSE"
            val sub = if (isMale) "Husband" else if (isFemale) "Wife" else "Spouse"
            return title to sub
        }

        return "MEMBER" to "Family Member"
    }
}
