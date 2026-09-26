package com.example.btproject2.sync

import com.example.btproject2.models.Person
import java.util.UUID

/**
 * Details of a relationship mutation (spouse, parent, child, adoption, or metadata).
 */
data class RelationshipChangeDetail(
    /**
     * Kind of relationship: "Spouse", "BiologicalParent", "AdoptiveParent",
     * "Child", "MaritalStatus", "MarriageDate", or "GraphCorrection".
     */
    val relationshipKind: String,

    /**
     * Primary person involved in the relationship.
     */
    val primaryPersonId: String,

    /**
     * Secondary person involved in the relationship (e.g. spouse, child, or parent).
     */
    val secondaryPersonId: String? = null,

    /**
     * Prior state or ID before the mutation.
     */
    val previousValue: String? = null,

    /**
     * New state or ID after the mutation.
     */
    val newValue: String? = null,

    /**
     * True if the relationship connection was unlinked / dissolved.
     */
    val isRemoval: Boolean = false
)

/**
 * Authoritative synchronization event published across KinTrace whenever a family tree mutation
 * is confirmed and persisted to the backend (single source of truth).
 */
data class TreeSyncEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * One of the 8 core mutation triggers.
     */
    val changeType: SyncChangeType,

    /**
     * Target family tree ID.
     */
    val treeId: String,

    /**
     * The computed impact scope detailing which persons and screens must update.
     */
    val scope: AffectedScope,

    /**
     * Authoritative saved state of the primary individual (null if deleted).
     */
    val primaryPerson: Person? = null,

    /**
     * Authoritative saved states of all individuals affected by this change.
     */
    val affectedPersons: List<Person> = emptyList(),

    /**
     * If MEMBER_DELETED, the ID of the removed individual.
     */
    val deletedPersonId: String? = null,

    /**
     * Structural or metadata details for relationship modifications.
     */
    val relationshipChange: RelationshipChangeDetail? = null,

    /**
     * Human-readable origin of the change (e.g., "Family Records", "Interactive Tree", "Member Detail").
     */
    val sourceScreenDescription: String = "",

    /**
     * Enforces the architectural rule: "The front end should update the display
     * only after the save is confirmed."
     */
    val isConfirmedSave: Boolean = true
)

