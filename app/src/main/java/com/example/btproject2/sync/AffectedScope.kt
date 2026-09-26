package com.example.btproject2.sync

/**
 * Identifies the precise scope of impact resulting from a family tree mutation.
 * Enables screens to update only the specific affected parts instead of
 * reloading everything unnecessarily.
 */
data class AffectedScope(
    /**
     * The ID of the family tree where the change occurred.
     */
    val treeId: String,

    /**
     * The ID of the primary individual directly modified, added, or deleted.
     */
    val primaryPersonId: String,

    /**
     * IDs of relatives directly impacted (spouses, parents, children, co-parents).
     */
    val relatedPersonIds: Set<String> = emptySet(),

    /**
     * All individuals requiring UI or state updates (primary + related).
     */
    val allAffectedPersonIds: Set<String> = if (primaryPersonId.isNotBlank()) {
        relatedPersonIds + primaryPersonId
    } else {
        relatedPersonIds
    },

    /**
     * Specific screens that must update their views.
     */
    val affectedScreens: Set<AffectedScreen> = setOf(AffectedScreen.ALL_SCREENS),

    /**
     * Whether the graph topology changed (adding/removing members or links).
     * If false, screens can update node cards or text without recomputing expensive tree layouts.
     */
    val requiresStructuralRelayout: Boolean = false,

    /**
     * Whether degrees of consanguinity, lineal paths, or kinship titles must be recomputed.
     */
    val requiresKinshipRecomputation: Boolean = false,

    /**
     * Specific attributes that were modified (e.g. "name", "birthDate", "photo", "spouseId").
     */
    val affectedFields: Set<String> = emptySet()
) {
    /**
     * Checks whether a particular screen is impacted by this change.
     */
    fun isScreenAffected(screen: AffectedScreen): Boolean {
        if (screen == AffectedScreen.ALL_SCREENS) return true
        return affectedScreens.contains(AffectedScreen.ALL_SCREENS) || affectedScreens.contains(screen)
    }

    /**
     * Checks whether a particular person's visual representation or record must be refreshed.
     */
    fun isPersonAffected(personId: String): Boolean {
        if (personId.isBlank()) return false
        val clean = personId.trim().lowercase()
        return allAffectedPersonIds.any { it.trim().lowercase() == clean }
    }
}
