package com.example.btproject2.sync

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person

/**
 * Coordinates synchronization between [CentralTreeSynchronizer] and [com.example.btproject2.ui.activities.InteractiveTreeActivity].
 *
 * Responsibilities:
 * - Subscribes to [CentralTreeSynchronizer] as an [AffectedScreen.INTERACTIVE_TREE] listener.
 * - Handles all 8 mandatory tree mutation triggers without requiring manual reload or reopening.
 * - Updates the authoritative in-memory member list.
 * - Unlinks broken parent/spouse references when members are deleted.
 * - Triggers avatar bitmap eviction when profile photos are updated.
 * - Refreshes or dismisses the active member profile modal dialog.
 * - Enforces single source of truth by resolving conflicts with [ConflictChoice.REFRESH].
 */
class InteractiveTreeSyncCoordinator(
    val treeId: String = "",
    val listenerKey: String = "InteractiveTreeActivity@${System.currentTimeMillis()}",
    var allPersonsList: List<Person> = emptyList(),
    var focalPedigreePersonId: String? = null,
    var onTreeUpdated: ((List<Person>, Boolean) -> Unit)? = null,
    var onMemberEvictedFromAvatarCache: ((String) -> Unit)? = null,
    var onActiveProfileRefreshed: ((String) -> Unit)? = null,
    var onActiveProfileDismissed: ((String) -> Unit)? = null
) : SyncEventListener {

    override val subscriberKey: String get() = listenerKey
    override val screenType: AffectedScreen get() = AffectedScreen.INTERACTIVE_TREE
    override val interestedTreeId: String? get() = treeId.ifBlank { null }

    override fun onConflictDetected(
        conflict: SyncConflictEvent,
        resolver: (ConflictChoice) -> Unit
    ) {
        // Main interactive tree renders authoritative saved truth; discard stale local view state
        resolver(ConflictChoice.REFRESH)
    }

    override fun onSyncEvent(event: TreeSyncEvent) {
        applySyncEvent(event)
    }

    /**
     * Authoritatively processes a confirmed family tree mutation event across all 8 triggers.
     */
    fun applySyncEvent(event: TreeSyncEvent) {
        when (event.changeType) {
            SyncChangeType.MEMBER_DELETED -> {
                val deletedId = event.deletedPersonId ?: event.scope.primaryPersonId
                if (deletedId.isNullOrEmpty()) return

                onActiveProfileDismissed?.invoke(deletedId)
                onMemberEvictedFromAvatarCache?.invoke(deletedId)

                val remaining = allPersonsList.filterNot { it.id == deletedId }.map { p ->
                    var modified = p
                    if (p.fatherId == deletedId) {
                        modified = modified.copy(fatherId = null, fatherRelationshipType = "")
                    }
                    if (p.motherId == deletedId) {
                        modified = modified.copy(motherId = null, motherRelationshipType = "")
                    }
                    if (p.spouseId == deletedId) {
                        modified = modified.copy(
                            spouseId = null,
                            maritalStatus = if (modified.maritalStatus.equals("Married", ignoreCase = true)) "Single" else modified.maritalStatus
                        )
                    }
                    modified
                }
                allPersonsList = FirestoreHelper.sanitizeTreeRecords(remaining)

                if (focalPedigreePersonId == deletedId) {
                    focalPedigreePersonId = allPersonsList.firstOrNull()?.id
                }

                onTreeUpdated?.invoke(allPersonsList, true)
            }

            SyncChangeType.PROFILE_EDITED -> {
                val updatedPerson = event.primaryPerson
                val incoming = (listOfNotNull(updatedPerson) + event.affectedPersons).distinctBy { it.id }
                if (incoming.isNotEmpty()) {
                    mergePersonsIntoList(incoming, forceEvictPhotos = event.scope.affectedFields.contains("photo"))
                }

                if (updatedPerson != null) {
                    onActiveProfileRefreshed?.invoke(updatedPerson.id)
                }

                onTreeUpdated?.invoke(allPersonsList, true)
            }

            SyncChangeType.MEMBER_ADDED -> {
                val newPerson = event.primaryPerson
                val incoming = (listOfNotNull(newPerson) + event.affectedPersons).distinctBy { it.id }
                mergePersonsIntoList(incoming)

                if (focalPedigreePersonId.isNullOrEmpty() && newPerson != null) {
                    focalPedigreePersonId = newPerson.id
                }

                onTreeUpdated?.invoke(allPersonsList, true)
            }

            SyncChangeType.SPOUSE_RELATIONSHIP_CHANGED,
            SyncChangeType.PARENT_CHILD_RELATIONSHIP_CHANGED,
            SyncChangeType.ADOPTIVE_RELATIONSHIP_CHANGED,
            SyncChangeType.RELATIONSHIP_METADATA_CHANGED,
            SyncChangeType.RELATIONSHIP_CORRECTED -> {
                val incoming = (listOfNotNull(event.primaryPerson) + event.affectedPersons).distinctBy { it.id }
                if (incoming.isNotEmpty()) {
                    mergePersonsIntoList(incoming)
                } else {
                    val cached = FirestoreHelper.getCachedPersons()
                    if (!cached.isNullOrEmpty()) {
                        allPersonsList = FirestoreHelper.sanitizeTreeRecords(cached)
                    }
                }

                event.primaryPerson?.let { onActiveProfileRefreshed?.invoke(it.id) }

                onTreeUpdated?.invoke(allPersonsList, true)
            }

            SyncChangeType.TREE_DELETED -> {
                if (event.treeId == treeId) {
                    allPersonsList = emptyList()
                    focalPedigreePersonId = null
                    onTreeUpdated?.invoke(emptyList(), true)
                }
            }

            SyncChangeType.TREE_CREATED -> {}
            SyncChangeType.CLAN_MERGE_REQUEST_UPDATED -> {}
        }
    }

    private fun mergePersonsIntoList(incoming: List<Person>, forceEvictPhotos: Boolean = false) {
        if (incoming.isEmpty()) return
        val incomingMap = incoming.associateBy { it.id }
        val result = allPersonsList.map { existing ->
            val updated = incomingMap[existing.id]
            if (updated != null) {
                if (forceEvictPhotos || (updated.photoBase64.isNotEmpty() && updated.photoBase64 != existing.photoBase64)) {
                    onMemberEvictedFromAvatarCache?.invoke(existing.id)
                }
                updated
            } else {
                existing
            }
        }.toMutableList()

        for (p in incoming) {
            if (result.none { it.id == p.id }) {
                result.add(p)
            }
        }
        allPersonsList = FirestoreHelper.sanitizeTreeRecords(result)
    }
}

