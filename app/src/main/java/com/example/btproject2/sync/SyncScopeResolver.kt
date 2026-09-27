package com.example.btproject2.sync

import com.example.btproject2.models.Person

/**
 * Intelligent analyzer that computes the exact [AffectedScope] for any family tree mutation.
 * Determines precisely which relatives and screens must be updated, and whether expensive
 * operations (such as complete tree re-layouts or kinship graph recalculations) are required.
 */
object SyncScopeResolver {

    /**
     * Resolves the affected scope for a profile edition.
     */
    fun resolveProfileEdited(
        updatedPerson: Person,
        previousPerson: Person? = null,
        treeMembers: List<Person> = emptyList()
    ): AffectedScope {
        val affectedFields = mutableSetOf<String>()
        var genderChanged = false
        var vitalDatesChanged = false

        if (previousPerson != null) {
            if (updatedPerson.firstName != previousPerson.firstName ||
                updatedPerson.middleName != previousPerson.middleName ||
                updatedPerson.lastName != previousPerson.lastName ||
                updatedPerson.suffix != previousPerson.suffix
            ) {
                affectedFields.add("name")
            }
            if (updatedPerson.birthDate != previousPerson.birthDate ||
                updatedPerson.birthPlace != previousPerson.birthPlace
            ) {
                affectedFields.add("birth")
                vitalDatesChanged = true
            }
            if (updatedPerson.deathDate != previousPerson.deathDate ||
                updatedPerson.deathPlace != previousPerson.deathPlace ||
                updatedPerson.isLiving != previousPerson.isLiving
            ) {
                affectedFields.add("death")
                vitalDatesChanged = true
            }
            if (updatedPerson.gender != previousPerson.gender) {
                affectedFields.add("gender")
                genderChanged = true
            }
            if (updatedPerson.photoUri != previousPerson.photoUri ||
                updatedPerson.photoBase64 != previousPerson.photoBase64
            ) {
                affectedFields.add("photo")
            }
            if (updatedPerson.biography != previousPerson.biography) {
                affectedFields.add("biography")
            }
        } else {
            affectedFields.addAll(listOf("name", "birth", "gender", "photo"))
        }

        // Profile edits do NOT change graph topology (no re-layout needed)
        val screens = mutableSetOf(
            AffectedScreen.MEMBER_DETAIL,
            AffectedScreen.RECORDS_LIST,
            AffectedScreen.INTERACTIVE_TREE,
            AffectedScreen.PEDIGREE_VIEW,
            AffectedScreen.FAN_CHART
        )

        if (vitalDatesChanged || genderChanged) {
            screens.add(AffectedScreen.INSIGHTS_DASHBOARD)
            screens.add(AffectedScreen.HOME_OVERVIEW)
        }

        val relatedIds = mutableSetOf<String>()
        // If gender changed, reciprocal spouses and children might have their titles affected
        if (genderChanged) {
            updatedPerson.spouseId?.let { if (it.isNotBlank()) relatedIds.add(it) }
            val children = treeMembers.filter { it.fatherId == updatedPerson.id || it.motherId == updatedPerson.id }
            relatedIds.addAll(children.map { it.id })
        }

        return AffectedScope(
            treeId = updatedPerson.treeId,
            primaryPersonId = updatedPerson.id,
            relatedPersonIds = relatedIds,
            affectedScreens = screens,
            requiresStructuralRelayout = false, // In-place node update only!
            requiresKinshipRecomputation = genderChanged,
            affectedFields = affectedFields
        )
    }

    /**
     * Resolves the affected scope when a new member is added.
     */
    fun resolveMemberAdded(
        newPerson: Person,
        treeMembers: List<Person> = emptyList()
    ): AffectedScope {
        val related = mutableSetOf<String>()
        newPerson.fatherId?.let { if (it.isNotBlank()) related.add(it) }
        newPerson.motherId?.let { if (it.isNotBlank()) related.add(it) }
        newPerson.spouseId?.let { if (it.isNotBlank()) related.add(it) }

        return AffectedScope(
            treeId = newPerson.treeId,
            primaryPersonId = newPerson.id,
            relatedPersonIds = related,
            affectedScreens = setOf(
                AffectedScreen.RECORDS_LIST,
                AffectedScreen.MEMBER_DETAIL,
                AffectedScreen.INTERACTIVE_TREE,
                AffectedScreen.PEDIGREE_VIEW,
                AffectedScreen.FAN_CHART,
                AffectedScreen.INSIGHTS_DASHBOARD,
                AffectedScreen.HOME_OVERVIEW,
                AffectedScreen.TREE_AUDIT
            ),
            requiresStructuralRelayout = true,
            requiresKinshipRecomputation = true,
            affectedFields = setOf("all")
        )
    }

    /**
     * Resolves the affected scope when a spouse relationship changes.
     */
    fun resolveSpouseChanged(
        personA: Person,
        personB: Person?,
        isRemoval: Boolean = false,
        extraAffectedRelatives: Set<String> = emptySet()
    ): AffectedScope {
        val related = mutableSetOf<String>()
        if (personB != null) related.add(personB.id)
        related.addAll(extraAffectedRelatives)

        return AffectedScope(
            treeId = personA.treeId,
            primaryPersonId = personA.id,
            relatedPersonIds = related,
            affectedScreens = setOf(
                AffectedScreen.INTERACTIVE_TREE,
                AffectedScreen.PEDIGREE_VIEW,
                AffectedScreen.FAN_CHART,
                AffectedScreen.MEMBER_DETAIL,
                AffectedScreen.RECORDS_LIST,
                AffectedScreen.RELATIONSHIP_PATH,
                AffectedScreen.TREE_AUDIT
            ),
            requiresStructuralRelayout = true,
            requiresKinshipRecomputation = true,
            affectedFields = setOf("spouseId", "maritalStatus")
        )
    }

    /**
     * Resolves the affected scope when a parent-child relationship changes.
     */
    fun resolveParentChildChanged(
        child: Person,
        parent: Person?,
        role: String = "parent",
        isAdoptive: Boolean = false,
        isRemoval: Boolean = false,
        extraAffectedRelatives: Set<String> = emptySet()
    ): AffectedScope {
        val related = mutableSetOf<String>()
        if (parent != null) related.add(parent.id)
        // Co-parents and siblings may also have kinship/bus impact
        related.addAll(extraAffectedRelatives)

        val screens = setOf(
            AffectedScreen.INTERACTIVE_TREE,
            AffectedScreen.PEDIGREE_VIEW,
            AffectedScreen.FAN_CHART,
            AffectedScreen.MEMBER_DETAIL,
            AffectedScreen.RECORDS_LIST,
            AffectedScreen.RELATIONSHIP_PATH,
            AffectedScreen.TREE_AUDIT,
            AffectedScreen.INSIGHTS_DASHBOARD
        )

        val fields = if (role.equals("mother", ignoreCase = true)) {
            setOf("motherId", "motherRelationshipType")
        } else {
            setOf("fatherId", "fatherRelationshipType")
        }

        return AffectedScope(
            treeId = child.treeId,
            primaryPersonId = child.id,
            relatedPersonIds = related,
            affectedScreens = screens,
            requiresStructuralRelayout = true,
            requiresKinshipRecomputation = true,
            affectedFields = fields
        )
    }

    /**
     * Resolves the affected scope when relationship metadata changes (status or marriage date).
     */
    fun resolveRelationshipMetadataChanged(
        personA: Person,
        personB: Person? = null,
        metadataFields: Set<String> = setOf("maritalStatus", "marriageDate")
    ): AffectedScope {
        val related = mutableSetOf<String>()
        if (personB != null) related.add(personB.id)
        personA.spouseId?.let { if (it.isNotBlank()) related.add(it) }

        return AffectedScope(
            treeId = personA.treeId,
            primaryPersonId = personA.id,
            relatedPersonIds = related,
            affectedScreens = setOf(
                AffectedScreen.MEMBER_DETAIL,
                AffectedScreen.RECORDS_LIST,
                AffectedScreen.INTERACTIVE_TREE,
                AffectedScreen.PEDIGREE_VIEW,
                AffectedScreen.FAN_CHART
            ),
            requiresStructuralRelayout = false, // Metadata only, positions don't change
            requiresKinshipRecomputation = false,
            affectedFields = metadataFields
        )
    }

    /**
     * Resolves the affected scope when a member is deleted.
     */
    fun resolveMemberDeleted(
        deletedPersonId: String,
        treeId: String,
        affectedRelatives: Set<String> = emptySet()
    ): AffectedScope {
        return AffectedScope(
            treeId = treeId,
            primaryPersonId = deletedPersonId,
            relatedPersonIds = affectedRelatives,
            affectedScreens = setOf(AffectedScreen.ALL_SCREENS),
            requiresStructuralRelayout = true,
            requiresKinshipRecomputation = true,
            affectedFields = setOf("deleted")
        )
    }

    /**
     * Resolves the affected scope when an invalid relationship is corrected.
     */
    fun resolveRelationshipCorrected(
        primaryPerson: Person,
        affectedRelatives: Set<String> = emptySet()
    ): AffectedScope {
        return AffectedScope(
            treeId = primaryPerson.treeId,
            primaryPersonId = primaryPerson.id,
            relatedPersonIds = affectedRelatives,
            affectedScreens = setOf(
                AffectedScreen.TREE_AUDIT,
                AffectedScreen.INTERACTIVE_TREE,
                AffectedScreen.PEDIGREE_VIEW,
                AffectedScreen.FAN_CHART,
                AffectedScreen.RECORDS_LIST,
                AffectedScreen.MEMBER_DETAIL
            ),
            requiresStructuralRelayout = true,
            requiresKinshipRecomputation = true,
            affectedFields = setOf("correction")
        )
    }

    /**
     * Resolves the affected scope when a tree is deleted.
     */
    fun resolveTreeDeleted(treeId: String): AffectedScope {
        return AffectedScope(
            treeId = treeId,
            primaryPersonId = "",
            relatedPersonIds = emptySet(),
            affectedScreens = setOf(AffectedScreen.ALL_SCREENS),
            requiresStructuralRelayout = true,
            requiresKinshipRecomputation = true,
            affectedFields = setOf("treeDeleted")
        )
    }
}

