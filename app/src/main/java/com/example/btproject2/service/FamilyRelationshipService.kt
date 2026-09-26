package com.example.btproject2.service

import com.example.btproject2.engine.BloodRelationshipEngine
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.engine.MarriageValidationEngine.RelationshipValidationResult
import com.example.btproject2.models.Person
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale

/**
 * Central Family-Relationship Service for the graph-based Philippine Family Tree Application.
 *
 * Architectural Mandate:
 * ALL actions that create, modify, or remove a family relationship MUST pass through this central service.
 * No screen, form, button, import process, or external connection is permitted to save a family
 * relationship directly without passing through this service.
 *
 * Supported Core Operations:
 * 1. Check whether a proposed relationship is valid (pre-flight validation)
 * 2. Add a biological parent
 * 3. Add a biological child
 * 4. Add an adoptive parent
 * 5. Add an adoptive child
 * 6. Add a spouse or partner
 * 7. Add the spouse of an existing child (Affinity / Son-in-law / Daughter-in-law connection)
 * 8. Remove a family relationship
 * 9. Check the entire existing family tree for errors (Authoritative Graph Audit)
 *
 * Enforces all legal and genealogical impediments under:
 * - Articles 37 and 38 of the Family Code of the Philippines
 * - Articles 963–967 of the Civil Code of the Philippines
 * - Universal Genealogical Graph Integrity (cycle prevention, self-ancestry prohibition, 2-parent cap)
 */
class FamilyRelationshipService(
    val bloodEngine: BloodRelationshipEngine = BloodRelationshipEngine(),
    val marriageEngine: MarriageValidationEngine = MarriageValidationEngine(bloodEngine),
    val linkValidator: FamilyLinkValidator = FamilyLinkValidator
) {

    // =========================================================================
    // ENUMS & RESULT MODELS
    // =========================================================================

    enum class ProposedRelationshipType {
        BIOLOGICAL_PARENT,
        BIOLOGICAL_CHILD,
        ADOPTIVE_PARENT,
        ADOPTIVE_CHILD,
        SPOUSE,
        SPOUSE_OF_CHILD
    }

    enum class ParentRole {
        FATHER,
        MOTHER,
        AUTO_DETECT
    }

    enum class RelationshipToRemove {
        FATHER,
        MOTHER,
        SPOUSE,
        SPECIFIC_CHILD
    }

    enum class AuditSeverity {
        CRITICAL_ERROR, // Prohibited by law, invalid graph cycle, or corrupts lineage
        WARNING         // Questionable biological gap or anomalous metadata
    }

    data class TreeAuditIssue(
        val severity: AuditSeverity,
        val title: String,
        val description: String,
        val legalBasis: String = "",
        val primaryPerson: Person,
        val secondaryPerson: Person? = null,
        val familyPath: List<Person> = emptyList()
    )

    data class TreeAuditReport(
        val totalPersonsScanned: Int,
        val issues: List<TreeAuditIssue>
    ) {
        val isValid: Boolean get() = issues.none { it.severity == AuditSeverity.CRITICAL_ERROR }
        val criticalErrorCount: Int get() = issues.count { it.severity == AuditSeverity.CRITICAL_ERROR }
        val warningCount: Int get() = issues.count { it.severity == AuditSeverity.WARNING }
        val criticalErrors: List<TreeAuditIssue> get() = issues.filter { it.severity == AuditSeverity.CRITICAL_ERROR }
        val warnings: List<TreeAuditIssue> get() = issues.filter { it.severity == AuditSeverity.WARNING }
    }

    data class ServiceResult<T>(
        val isSuccess: Boolean,
        val data: T?,
        val message: String,
        val validationResult: RelationshipValidationResult? = null,
        val affectedPersons: List<Person> = emptyList()
    ) {
        companion object {
            fun <T> success(data: T, message: String, affectedPersons: List<Person> = emptyList()): ServiceResult<T> {
                return ServiceResult(isSuccess = true, data = data, message = message, affectedPersons = affectedPersons)
            }

            fun <T> failure(message: String, validationResult: RelationshipValidationResult? = null): ServiceResult<T> {
                return ServiceResult(isSuccess = false, data = null, message = message, validationResult = validationResult)
            }
        }
    }

    // =========================================================================
    // ACTION 1: CHECK WHETHER A PROPOSED RELATIONSHIP IS VALID
    // =========================================================================

    /**
     * Pre-flight validation: Checks whether a proposed relationship can legally and
     * genealogically be added to the family tree before making any modifications.
     */
    fun checkProposedRelationship(
        type: ProposedRelationshipType,
        primaryPersonId: String,
        secondaryPersonId: String,
        tree: Map<String, Person>,
        role: ParentRole = ParentRole.AUTO_DETECT
    ): RelationshipValidationResult {
        val p1 = tree[primaryPersonId]
            ?: return createMissingPersonResult(primaryPersonId)
        val p2 = tree[secondaryPersonId]
            ?: return createMissingPersonResult(secondaryPersonId)

        return when (type) {
            ProposedRelationshipType.SPOUSE -> {
                marriageEngine.validateSpouseConnection(primaryPersonId, secondaryPersonId, tree)
            }
            ProposedRelationshipType.SPOUSE_OF_CHILD -> {
                // primaryPersonId = child, secondaryPersonId = proposed spouse of child
                marriageEngine.validateSpouseConnection(primaryPersonId, secondaryPersonId, tree)
            }
            ProposedRelationshipType.BIOLOGICAL_PARENT -> {
                // primaryPersonId = child, secondaryPersonId = proposed parent
                val effectiveRole = resolveParentRole(p2, role)
                validateParentChildComprehensive(
                    parentId = secondaryPersonId,
                    childId = primaryPersonId,
                    role = effectiveRole,
                    isBiological = true,
                    tree = tree
                )
            }
            ProposedRelationshipType.BIOLOGICAL_CHILD -> {
                // primaryPersonId = parent, secondaryPersonId = proposed child
                val effectiveRole = resolveParentRole(p1, role)
                validateParentChildComprehensive(
                    parentId = primaryPersonId,
                    childId = secondaryPersonId,
                    role = effectiveRole,
                    isBiological = true,
                    tree = tree
                )
            }
            ProposedRelationshipType.ADOPTIVE_PARENT -> {
                // primaryPersonId = child, secondaryPersonId = proposed adopter
                val effectiveRole = resolveParentRole(p2, role)
                validateParentChildComprehensive(
                    parentId = secondaryPersonId,
                    childId = primaryPersonId,
                    role = effectiveRole,
                    isBiological = false,
                    tree = tree
                )
            }
            ProposedRelationshipType.ADOPTIVE_CHILD -> {
                // primaryPersonId = adopter, secondaryPersonId = proposed adoptee
                val effectiveRole = resolveParentRole(p1, role)
                validateParentChildComprehensive(
                    parentId = primaryPersonId,
                    childId = secondaryPersonId,
                    role = effectiveRole,
                    isBiological = false,
                    tree = tree
                )
            }
        }
    }

    // =========================================================================
    // ACTION 2: ADD A BIOLOGICAL PARENT
    // =========================================================================

    /**
     * Adds [parentId] as a biological parent of [childId].
     * Enforces two-parent cap, cycle prevention, parent-spouse conflict, and age feasibility.
     */
    fun addBiologicalParent(
        childId: String,
        parentId: String,
        tree: Map<String, Person>,
        role: ParentRole = ParentRole.AUTO_DETECT
    ): ServiceResult<Map<String, Person>> {
        val child = tree[childId] ?: return ServiceResult.failure("Child ($childId) not found in family tree.")
        val parent = tree[parentId] ?: return ServiceResult.failure("Parent ($parentId) not found in family tree.")

        val effectiveRole = resolveParentRole(parent, role)
        val validation = validateParentChildComprehensive(
            parentId = parentId,
            childId = childId,
            role = effectiveRole,
            isBiological = true,
            tree = tree
        )

        if (!validation.isAllowed) {
            return ServiceResult.failure(validation.reason, validation)
        }

        // Apply mutation
        val updatedChild = when (effectiveRole) {
            "father" -> child.copy(fatherId = parentId, fatherRelationshipType = "Biological")
            else -> child.copy(motherId = parentId, motherRelationshipType = "Biological")
        }

        val updatedMap = tree.toMutableMap()
        updatedMap[childId] = updatedChild

        val roleLabel = if (effectiveRole == "father") "father" else "mother"
        return ServiceResult.success(
            data = updatedMap,
            message = "Successfully linked ${parent.firstName} ${parent.lastName} as biological $roleLabel of ${child.firstName} ${child.lastName}.",
            affectedPersons = listOf(updatedChild, parent)
        )
    }

    // =========================================================================
    // ACTION 3: ADD A BIOLOGICAL CHILD
    // =========================================================================

    /**
     * Adds [childId] as a biological child of [parentId].
     * Strictly enforces Rule 8: Son-in-law or daughter-in-law cannot be added as biological child!
     */
    fun addBiologicalChild(
        parentId: String,
        childId: String,
        tree: Map<String, Person>,
        role: ParentRole = ParentRole.AUTO_DETECT
    ): ServiceResult<Map<String, Person>> {
        return addBiologicalParent(childId = childId, parentId = parentId, tree = tree, role = role)
    }

    // =========================================================================
    // ACTION 4: ADD AN ADOPTIVE PARENT
    // =========================================================================

    /**
     * Adds [parentId] as an adoptive parent of [childId].
     * Sets relationshipType to "Adoptive" so blood consanguinity is not affected,
     * while adoption legal ties (Family Code Art. 38(4)) are established.
     */
    fun addAdoptiveParent(
        childId: String,
        parentId: String,
        tree: Map<String, Person>,
        role: ParentRole = ParentRole.AUTO_DETECT
    ): ServiceResult<Map<String, Person>> {
        val child = tree[childId] ?: return ServiceResult.failure("Child ($childId) not found in family tree.")
        val parent = tree[parentId] ?: return ServiceResult.failure("Parent ($parentId) not found in family tree.")

        val effectiveRole = resolveParentRole(parent, role)
        val validation = validateParentChildComprehensive(
            parentId = parentId,
            childId = childId,
            role = effectiveRole,
            isBiological = false,
            tree = tree
        )

        if (!validation.isAllowed) {
            return ServiceResult.failure(validation.reason, validation)
        }

        // Apply mutation
        val updatedChild = when (effectiveRole) {
            "father" -> child.copy(fatherId = parentId, fatherRelationshipType = "Adoptive")
            else -> child.copy(motherId = parentId, motherRelationshipType = "Adoptive")
        }

        val updatedMap = tree.toMutableMap()
        updatedMap[childId] = updatedChild

        val roleLabel = if (effectiveRole == "father") "adoptive father" else "adoptive mother"
        return ServiceResult.success(
            data = updatedMap,
            message = "Successfully linked ${parent.firstName} ${parent.lastName} as $roleLabel of ${child.firstName} ${child.lastName}.",
            affectedPersons = listOf(updatedChild, parent)
        )
    }

    // =========================================================================
    // ACTION 5: ADD AN ADOPTIVE CHILD
    // =========================================================================

    /**
     * Adds [childId] as an adoptive child of [parentId].
     */
    fun addAdoptiveChild(
        parentId: String,
        childId: String,
        tree: Map<String, Person>,
        role: ParentRole = ParentRole.AUTO_DETECT
    ): ServiceResult<Map<String, Person>> {
        return addAdoptiveParent(childId = childId, parentId = parentId, tree = tree, role = role)
    }

    // =========================================================================
    // ACTION 6: ADD A SPOUSE OR PARTNER
    // =========================================================================

    /**
     * Links [personAId] and [personBId] as spouses or partners.
     * Enforces bigamy prohibition, lineal prohibitions (Art. 37(1)), sibling prohibitions (Art. 37(2)),
     * collateral prohibitions up to 4th degree (Art. 38(1)), and parent-spouse conflicts.
     */
    fun addSpouse(
        personAId: String,
        personBId: String,
        tree: Map<String, Person>
    ): ServiceResult<Map<String, Person>> {
        val personA = tree[personAId] ?: return ServiceResult.failure("Person A ($personAId) not found in family tree.")
        val personB = tree[personBId] ?: return ServiceResult.failure("Person B ($personBId) not found in family tree.")

        // Authoritative MarriageValidationEngine validation (Age, Gender, Active Spouse, Consanguinity, Affinity)
        val validation = marriageEngine.validateSpouseConnection(personAId, personBId, tree)
        if (!validation.isAllowed) {
            return ServiceResult.failure(validation.reason, validation)
        }

        // Apply mutation
        val updatedA = personA.copy(spouseId = personBId, maritalStatus = "Married")
        val updatedB = personB.copy(spouseId = personAId, maritalStatus = "Married")

        val updatedMap = tree.toMutableMap()
        updatedMap[personAId] = updatedA
        updatedMap[personBId] = updatedB

        return ServiceResult.success(
            data = updatedMap,
            message = "Successfully linked ${personA.firstName} ${personA.lastName} and ${personB.firstName} ${personB.lastName} as spouses.",
            affectedPersons = listOf(updatedA, updatedB)
        )
    }

    // =========================================================================
    // ACTION 7: ADD THE SPOUSE OF AN EXISTING CHILD (IN-LAW CONNECTION)
    // =========================================================================

    /**
     * Specifically connects [spouseId] as the spouse of an existing child [childId].
     * Directly satisfies Rule 8: "A son-in-law or daughter-in-law must be connected as the spouse
     * of an existing child. They must never be connected as a biological child of the parent-in-law."
     */
    fun addSpouseOfExistingChild(
        childId: String,
        spouseId: String,
        tree: Map<String, Person>
    ): ServiceResult<Map<String, Person>> {
        val child = tree[childId] ?: return ServiceResult.failure("Child ($childId) not found in family tree.")
        val spouse = tree[spouseId] ?: return ServiceResult.failure("Spouse ($spouseId) not found in family tree.")

        // Enforce marriage validation between child and proposed spouse
        val result = addSpouse(personAId = childId, personBId = spouseId, tree = tree)
        if (!result.isSuccess) {
            return result
        }

        val inLawTitle = if (spouse.gender.equals("female", ignoreCase = true)) "daughter-in-law" else "son-in-law"
        val parents = listOfNotNull(
            child.fatherId?.let { tree[it] },
            child.motherId?.let { tree[it] }
        )
        val parentNames = if (parents.isNotEmpty()) {
            parents.joinToString(" and ") { "${it.firstName} ${it.lastName}" }
        } else {
            "the parents"
        }

        return ServiceResult.success(
            data = result.data!!,
            message = "Successfully connected ${spouse.firstName} ${spouse.lastName} as the spouse of ${child.firstName} ${child.lastName} (recognized as $inLawTitle of $parentNames by affinity).",
            affectedPersons = result.affectedPersons
        )
    }

    // =========================================================================
    // ACTION 8: REMOVE A FAMILY RELATIONSHIP
    // =========================================================================

    /**
     * Safely unlinks a family connection between individuals.
     */
    fun removeRelationship(
        primaryPersonId: String,
        secondaryPersonId: String? = null,
        relationshipToRemove: RelationshipToRemove,
        tree: Map<String, Person>
    ): ServiceResult<Map<String, Person>> {
        val primary = tree[primaryPersonId]
            ?: return ServiceResult.failure("Person ($primaryPersonId) not found in family tree.")

        val updatedMap = tree.toMutableMap()
        val affected = mutableListOf<Person>()

        when (relationshipToRemove) {
            RelationshipToRemove.SPOUSE -> {
                val spouseId = secondaryPersonId ?: primary.spouseId
                val updatedPrimary = primary.copy(spouseId = null, maritalStatus = "Single")
                updatedMap[primaryPersonId] = updatedPrimary
                affected.add(updatedPrimary)

                if (!spouseId.isNullOrBlank()) {
                    val spouse = tree[spouseId]
                    if (spouse != null && isSameId(spouse.spouseId, primaryPersonId)) {
                        val updatedSpouse = spouse.copy(spouseId = null, maritalStatus = "Single")
                        updatedMap[spouseId] = updatedSpouse
                        affected.add(updatedSpouse)
                    }
                }
            }
            RelationshipToRemove.FATHER -> {
                val updatedChild = primary.copy(fatherId = null, fatherRelationshipType = "")
                updatedMap[primaryPersonId] = updatedChild
                affected.add(updatedChild)
            }
            RelationshipToRemove.MOTHER -> {
                val updatedChild = primary.copy(motherId = null, motherRelationshipType = "")
                updatedMap[primaryPersonId] = updatedChild
                affected.add(updatedChild)
            }
            RelationshipToRemove.SPECIFIC_CHILD -> {
                if (secondaryPersonId.isNullOrBlank()) {
                    return ServiceResult.failure("Must specify child ID to remove child relationship.")
                }
                val child = tree[secondaryPersonId]
                    ?: return ServiceResult.failure("Child ($secondaryPersonId) not found in family tree.")

                val updatedChild = when {
                    isSameId(child.fatherId, primaryPersonId) -> child.copy(fatherId = null, fatherRelationshipType = "")
                    isSameId(child.motherId, primaryPersonId) -> child.copy(motherId = null, motherRelationshipType = "")
                    else -> child
                }
                updatedMap[secondaryPersonId] = updatedChild
                affected.add(updatedChild)
            }
        }

        return ServiceResult.success(
            data = updatedMap,
            message = "Successfully removed relationship.",
            affectedPersons = affected
        )
    }

    // =========================================================================
    // ACTION 9: CHECK THE ENTIRE EXISTING FAMILY TREE FOR ERRORS (AUDIT SCANNER)
    // =========================================================================

    /**
     * Systematically audits the entire family tree graph for mistakes, invalid connections,
     * legal impediments, and questionable biological relationships.
     */
    fun auditFamilyTree(tree: Map<String, Person>): TreeAuditReport {
        val issues = mutableListOf<TreeAuditIssue>()
        val checkedSpousePairs = mutableSetOf<String>()

        for (person in tree.values) {
            // 1. Check Spouse Prohibitions & Consistency
            if (!person.spouseId.isNullOrBlank()) {
                val spouse = tree[person.spouseId]
                val pairKey = listOf(person.id, person.spouseId).sorted().joinToString("<->")

                if (spouse == null) {
                    issues.add(
                        TreeAuditIssue(
                            severity = AuditSeverity.CRITICAL_ERROR,
                            title = "Dangling Spouse Reference",
                            description = "${person.firstName} ${person.lastName} references spouse ID '${person.spouseId}' which does not exist in the tree.",
                            primaryPerson = person
                        )
                    )
                } else if (!checkedSpousePairs.contains(pairKey)) {
                    checkedSpousePairs.add(pairKey)

                    // Asymmetric spouse link check
                    if (!isSameId(spouse.spouseId, person.id)) {
                        issues.add(
                            TreeAuditIssue(
                                severity = AuditSeverity.CRITICAL_ERROR,
                                title = "Asymmetric Marriage Link",
                                description = "${person.firstName} is linked to spouse ${spouse.firstName}, but ${spouse.firstName}'s spouse link does not point back.",
                                primaryPerson = person,
                                secondaryPerson = spouse
                            )
                        )
                    }

                    // Legal consanguinity check
                    val spouseValidation = marriageEngine.validateSpouseConnection(person.id, spouse.id, tree)
                    if (!spouseValidation.isAllowed) {
                        issues.add(
                            TreeAuditIssue(
                                severity = AuditSeverity.CRITICAL_ERROR,
                                title = "Illegal Marriage: ${spouseValidation.relationshipType}",
                                description = spouseValidation.reason,
                                legalBasis = spouseValidation.legalBasis,
                                primaryPerson = person,
                                secondaryPerson = spouse,
                                familyPath = spouseValidation.familyPath
                            )
                        )
                    }
                }
            }

            // 2. Check Biological & Legal Father Link
            if (!person.fatherId.isNullOrBlank()) {
                val father = tree[person.fatherId]
                if (father == null) {
                    issues.add(
                        TreeAuditIssue(
                            severity = AuditSeverity.CRITICAL_ERROR,
                            title = "Dangling Father Reference",
                            description = "${person.firstName} references father ID '${person.fatherId}' which does not exist.",
                            primaryPerson = person
                        )
                    )
                } else {
                    auditParentChildPair(parent = father, child = person, role = "father", tree = tree, issues = issues)
                }
            }

            // 3. Check Biological & Legal Mother Link
            if (!person.motherId.isNullOrBlank()) {
                val mother = tree[person.motherId]
                if (mother == null) {
                    issues.add(
                        TreeAuditIssue(
                            severity = AuditSeverity.CRITICAL_ERROR,
                            title = "Dangling Mother Reference",
                            description = "${person.firstName} references mother ID '${person.motherId}' which does not exist.",
                            primaryPerson = person
                        )
                    )
                } else {
                    auditParentChildPair(parent = mother, child = person, role = "mother", tree = tree, issues = issues)
                }
            }

            // 4. Check Co-Parent Feasibility (if both father and mother exist)
            if (!person.fatherId.isNullOrBlank() && !person.motherId.isNullOrBlank()) {
                val father = tree[person.fatherId]
                val mother = tree[person.motherId]
                if (father != null && mother != null) {
                    if (isSameId(father.id, mother.id)) {
                        issues.add(
                            TreeAuditIssue(
                                severity = AuditSeverity.CRITICAL_ERROR,
                                title = "Invalid Parents: Same Person",
                                description = "${father.firstName} ${father.lastName} is assigned as both father and mother of ${person.firstName}.",
                                primaryPerson = person,
                                secondaryPerson = father
                            )
                        )
                    } else {
                        val coParentValidation = marriageEngine.validateSpouseConnection(father.id, mother.id, tree)
                        if (!coParentValidation.isAllowed) {
                            issues.add(
                                TreeAuditIssue(
                                    severity = AuditSeverity.CRITICAL_ERROR,
                                    title = "Prohibited Co-Parents: ${coParentValidation.relationshipType}",
                                    description = "Father (${father.firstName}) and Mother (${mother.firstName}) cannot be registered as co-parents of ${person.firstName}: ${coParentValidation.reason}",
                                    legalBasis = coParentValidation.legalBasis,
                                    primaryPerson = father,
                                    secondaryPerson = mother,
                                    familyPath = coParentValidation.familyPath
                                )
                            )
                        }
                    }
                }
            }
        }

        return TreeAuditReport(totalPersonsScanned = tree.size, issues = issues)
    }

    // =========================================================================
    // PRIVATE VALIDATION & HELPER METHODS
    // =========================================================================

    private fun auditParentChildPair(
        parent: Person,
        child: Person,
        role: String,
        tree: Map<String, Person>,
        issues: MutableList<TreeAuditIssue>
    ) {
        val relType = if (role == "father") child.fatherRelationshipType else child.motherRelationshipType
        val isBio = relType.isNullOrBlank() || relType.equals("Biological", ignoreCase = true)

        // Rule 6: Self-Parenting
        if (isSameId(parent.id, child.id)) {
            issues.add(
                TreeAuditIssue(
                    severity = AuditSeverity.CRITICAL_ERROR,
                    title = "Self-Parenting Loop",
                    description = "${parent.firstName} is registered as their own $role.",
                    primaryPerson = child,
                    secondaryPerson = parent,
                    familyPath = listOf(parent)
                )
            )
            return
        }

        // Rule 5: Spouse as Parent
        if (isSameId(parent.spouseId, child.id) || isSameId(child.spouseId, parent.id)) {
            issues.add(
                TreeAuditIssue(
                    severity = AuditSeverity.CRITICAL_ERROR,
                    title = "Spouse as Parent Conflict",
                    description = "${parent.firstName} and ${child.firstName} are registered as spouses, but ${parent.firstName} is also the $role.",
                    legalBasis = "Article 37 (1), Family Code of the Philippines",
                    primaryPerson = child,
                    secondaryPerson = parent,
                    familyPath = listOf(parent, child)
                )
            )
        }

        // Rule 7 & 8: MarriageValidationEngine parent-child check
        val pcValidation = marriageEngine.validateParentChildConnection(
            parentId = parent.id,
            childId = child.id,
            relationshipType = if (isBio) "Biological" else "Adoptive",
            personMap = tree
        )
        if (!pcValidation.isAllowed) {
            issues.add(
                TreeAuditIssue(
                    severity = AuditSeverity.CRITICAL_ERROR,
                    title = "Invalid Parent Link: ${pcValidation.relationshipType}",
                    description = pcValidation.reason,
                    legalBasis = pcValidation.legalBasis,
                    primaryPerson = child,
                    secondaryPerson = parent,
                    familyPath = pcValidation.familyPath
                )
            )
        }

        // Biological Age Feasibility
        val parentYear = linkValidator.extractYear(parent.birthDate)
        val childYear = linkValidator.extractYear(child.birthDate)
        if (parentYear != null && childYear != null) {
            val gap = childYear - parentYear
            if (gap < 0) {
                issues.add(
                    TreeAuditIssue(
                        severity = AuditSeverity.CRITICAL_ERROR,
                        title = "Parent Younger Than Child",
                        description = "$role ${parent.firstName} (born $parentYear) is ${-gap} year(s) younger than child ${child.firstName} (born $childYear).",
                        primaryPerson = child,
                        secondaryPerson = parent
                    )
                )
            } else if (gap == 0) {
                issues.add(
                    TreeAuditIssue(
                        severity = AuditSeverity.CRITICAL_ERROR,
                        title = "Parent Born Same Year",
                        description = "$role ${parent.firstName} and child ${child.firstName} were both born in $parentYear.",
                        primaryPerson = child,
                        secondaryPerson = parent
                    )
                )
            } else if (isBio && gap < 12) {
                issues.add(
                    TreeAuditIssue(
                        severity = AuditSeverity.CRITICAL_ERROR,
                        title = "Biologically Impossible Age Gap",
                        description = "$role ${parent.firstName} would be only $gap years old at childbirth. Minimum biological reproduction age is 12.",
                        primaryPerson = child,
                        secondaryPerson = parent
                    )
                )
            } else if (gap > 65) {
                issues.add(
                    TreeAuditIssue(
                        severity = AuditSeverity.WARNING,
                        title = "Unusually Large Age Gap",
                        description = "Age gap between $role ${parent.firstName} and child ${child.firstName} is $gap years.",
                        primaryPerson = child,
                        secondaryPerson = parent
                    )
                )
            }
        }
    }

    private fun validateParentChildComprehensive(
        parentId: String,
        childId: String,
        role: String,
        isBiological: Boolean,
        tree: Map<String, Person>
    ): RelationshipValidationResult {
        val parent = tree[parentId] ?: return createMissingPersonResult(parentId)
        val child = tree[childId] ?: return createMissingPersonResult(childId)

        // 1. Self Check (Rule 6)
        if (isSameId(parentId, childId)) {
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "A person cannot become their own ancestor or descendant. ${parent.firstName} ${parent.lastName} cannot be linked as their own parent.",
                legalBasis = "Universal Genealogical Graph Integrity",
                relationshipType = "Self-Parenting",
                degree = 0,
                familyPath = listOf(parent)
            )
        }

        // 2. Spouse-as-Parent Check (Rule 5)
        val areSpouses = isSameId(parent.spouseId, child.id) || isSameId(child.spouseId, parent.id)
        if (areSpouses && isBiological) {
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "A person cannot be linked as both biological parent and spouse of the same person. ${parent.firstName} and ${child.firstName} are registered as spouses.",
                legalBasis = "Article 37 (1), Family Code of the Philippines",
                relationshipType = "Spouse as Parent Conflict",
                degree = 1,
                familyPath = listOf(parent, child)
            )
        }

        // 3. MarriageValidationEngine Check (Rule 7: Cycles, Rule 8: In-law as child)
        val mveResult = marriageEngine.validateParentChildConnection(
            parentId = parentId,
            childId = childId,
            relationshipType = if (isBiological) "Biological" else "Adoptive",
            personMap = tree
        )
        if (!mveResult.isAllowed) {
            return mveResult
        }

        val existingFather = child.fatherId?.let { tree[it] }
        val existingMother = child.motherId?.let { tree[it] }
        val isFatherSlot = role == "father"

        // 4. Step-Parent Check (Must be evaluated before generic slot occupied check)
        // "The spouse of a child’s biological parent should be treated as a step-parent unless there is a separate adoption record. A step-parent must not be saved as a biological parent unless the user explicitly records an adoption."
        val isStepFatherAttempt = isFatherSlot && isBiological && existingMother != null &&
                isSameId(existingMother.spouseId, parentId) && !isSameId(child.fatherId, parentId)
        val isStepMotherAttempt = !isFatherSlot && isBiological && existingFather != null &&
                isSameId(existingFather.spouseId, parentId) && !isSameId(child.motherId, parentId)

        if (isStepFatherAttempt || isStepMotherAttempt) {
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "A step-parent cannot be saved as a biological parent. ${parent.firstName} ${parent.lastName} is the spouse of ${child.firstName}'s biological parent. Under Philippine family law, a step-parent relationship arises from marriage. To connect a step-parent as a legal parent, record an explicit Adoptive relationship.",
                legalBasis = "Article 38 (3), Family Code of the Philippines and Domestic Adoption Act",
                relationshipType = "Step-Parent as Biological Parent",
                degree = 1,
                familyPath = listOfNotNull(parent, existingFather ?: existingMother, child)
            )
        }

        // 5. Two-Parents Cap & Slot Check
        if (existingFather != null && existingMother != null &&
            !isSameId(existingFather.id, parentId) && !isSameId(existingMother.id, parentId)
        ) {
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "Cannot add parent: ${child.firstName} already has two parents defined in this family tree (${existingFather.firstName} and ${existingMother.firstName}). A person cannot have more than two parents.",
                legalBasis = "Universal Genealogical Rule & Family Code of the Philippines",
                relationshipType = "Two-Parent Cap Exceeded",
                degree = 1,
                familyPath = listOf(existingFather, existingMother, child)
            )
        }

        val targetSlotParent = if (isFatherSlot) existingFather else existingMother
        if (targetSlotParent != null && !isSameId(targetSlotParent.id, parentId)) {
            val slotName = if (isFatherSlot) "father" else "mother"
            val existingName = "${targetSlotParent.firstName} ${targetSlotParent.lastName}".trim()
            return RelationshipValidationResult(
                isAllowed = false,
                reason = "Cannot add ${parent.firstName} as $slotName: ${child.firstName} already has a $slotName defined in the family tree ($existingName).",
                legalBasis = "Article 37, Family Code of the Philippines (Two-Parent Biological Cap)",
                relationshipType = "Parent Slot Occupied",
                degree = 1,
                familyPath = listOf(targetSlotParent, child)
            )
        }

        // 6. Co-Parent Compatibility with Child's Existing Other Parent (Incestuous consanguinity check only)
        val otherParent = if (isFatherSlot) existingMother else existingFather
        if (otherParent != null && !isSameId(otherParent.id, parentId)) {
            val coParentCheck = marriageEngine.validateSpouseConnection(parentId, otherParent.id, tree)
            val incestuousFailures = coParentCheck.failedValidations.filter {
                it == com.example.btproject2.engine.MarriageValidationEngine.SpouseValidationFailureReason.SELF_MARRIAGE ||
                it == com.example.btproject2.engine.MarriageValidationEngine.SpouseValidationFailureReason.LINEAL_CONSANGUINITY ||
                it == com.example.btproject2.engine.MarriageValidationEngine.SpouseValidationFailureReason.SIBLING_CONSANGUINITY
            }
            if (incestuousFailures.isNotEmpty()) {
                return RelationshipValidationResult(
                    isAllowed = false,
                    reason = "Cannot register ${parent.firstName} as parent of ${child.firstName}: Prohibited co-parent relationship with child's other parent (${otherParent.firstName}). ${coParentCheck.reason}",
                    legalBasis = coParentCheck.legalBasis,
                    relationshipType = "Prohibited Co-Parents (${coParentCheck.relationshipType})",
                    degree = coParentCheck.degree,
                    familyPath = coParentCheck.familyPath
                )
            }
        }

        // 7. Biological Age Gap Feasibility
        val parentYear = linkValidator.extractYear(parent.birthDate)
        val childYear = linkValidator.extractYear(child.birthDate)
        if (parentYear != null && childYear != null) {
            val gap = childYear - parentYear
            if (gap < 0) {
                return RelationshipValidationResult(
                    isAllowed = false,
                    reason = "Impossible age gap: ${parent.firstName} (born $parentYear) is ${-gap} year(s) younger than ${child.firstName} (born $childYear). A parent cannot be younger than their child.",
                    legalBasis = "Biological Law of Linear Time",
                    relationshipType = "Younger Parent Error",
                    degree = 1,
                    familyPath = listOf(parent, child)
                )
            }
            if (gap == 0) {
                return RelationshipValidationResult(
                    isAllowed = false,
                    reason = "Impossible age gap: ${parent.firstName} and ${child.firstName} were both born in $parentYear. A biological parent cannot be born in the same year as their child.",
                    legalBasis = "Biological Law of Linear Time",
                    relationshipType = "Same-Year Birth Error",
                    degree = 1,
                    familyPath = listOf(parent, child)
                )
            }
            if (isBiological && gap < 12) {
                return RelationshipValidationResult(
                    isAllowed = false,
                    reason = "Biologically impossible age gap: ${parent.firstName} was only $gap year(s) old when ${child.firstName} was born. The minimum biological age for human reproduction is 12 years.",
                    legalBasis = "Human Biological Feasibility",
                    relationshipType = "Biological Reproduction Feasibility",
                    degree = 1,
                    familyPath = listOf(parent, child)
                )
            }
        }

        // 8. Chronological Vital Status Feasibility (Parent Death vs Child Birth)
        if (isBiological) {
            val deathChrono = linkValidator.validateParentDeathChronology(parent, child, role)
            if (!deathChrono.isAllowed) {
                return RelationshipValidationResult(
                    isAllowed = false,
                    reason = deathChrono.message,
                    legalBasis = "Biological Law of Linear Time & Family Code of the Philippines",
                    relationshipType = "Post-Mortem Birth Error",
                    degree = 1,
                    familyPath = listOf(parent, child)
                )
            }
        }

        return RelationshipValidationResult(
            isAllowed = true,
            reason = "Valid parent-child connection.",
            legalBasis = "Genealogical Graph Integrity (Valid)",
            relationshipType = if (isBiological) "Biological Parent and Child" else "Adoptive Parent and Child",
            degree = 1,
            familyPath = listOf(parent, child)
        )
    }

    private fun resolveParentRole(parent: Person, requestedRole: ParentRole): String {
        return when (requestedRole) {
            ParentRole.FATHER -> "father"
            ParentRole.MOTHER -> "mother"
            ParentRole.AUTO_DETECT -> {
                if (parent.gender.equals("male", ignoreCase = true)) "father"
                else if (parent.gender.equals("female", ignoreCase = true)) "mother"
                else "father"
            }
        }
    }

    private fun createMissingPersonResult(id: String): RelationshipValidationResult {
        return RelationshipValidationResult(
            isAllowed = false,
            reason = "Person ID '$id' was not found in the family tree records.",
            legalBasis = "Data Integrity Error",
            relationshipType = "Missing Record",
            degree = 0,
            familyPath = emptyList()
        )
    }

    private fun isSameId(id1: String?, id2: String?): Boolean {
        if (id1.isNullOrBlank() || id2.isNullOrBlank()) return false
        return id1.trim().equals(id2.trim(), ignoreCase = true)
    }
}
