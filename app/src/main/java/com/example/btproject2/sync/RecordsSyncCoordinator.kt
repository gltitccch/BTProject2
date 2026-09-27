package com.example.btproject2.sync

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person

/**
 * Coordinates real-time synchronization between [CentralTreeSynchronizer] and
 * [com.example.btproject2.ui.activities.FamilyRecordsActivity] (the Records Section & Search/Lookup).
 *
 * Responsibilities:
 * - Subscribes to [CentralTreeSynchronizer] as an [AffectedScreen.RECORDS_LIST] listener.
 * - Handles all 8 mandatory mutation triggers.
 * - Updates the authoritative in-memory member list and cleans broken relationships upon deletions.
 * - Powers instant filtering for search queries and member lookups.
 * - Enforces the single source of truth by resolving conflicts with [ConflictChoice.REFRESH].
 */
class RecordsSyncCoordinator(
    val treeId: String = "",
    val listenerKey: String = "FamilyRecordsActivity@${System.currentTimeMillis()}",
    var allMembersList: List<Person> = emptyList(),
    var onListUpdated: ((updatedList: List<Person>, event: TreeSyncEvent) -> Unit)? = null,
    var onMemberDeleted: ((deletedId: String) -> Unit)? = null
) : SyncEventListener {

    override val subscriberKey: String get() = listenerKey
    override val screenType: AffectedScreen get() = AffectedScreen.RECORDS_LIST
    override val interestedTreeId: String? get() = treeId.ifBlank { null }

    fun register(synchronizer: CentralTreeSynchronizer = CentralTreeSynchronizer.getInstance()) {
        synchronizer.registerListener(this)
    }

    fun unregister(synchronizer: CentralTreeSynchronizer = CentralTreeSynchronizer.getInstance()) {
        synchronizer.unregisterListener(this)
    }

    override fun onConflictDetected(
        conflict: SyncConflictEvent,
        resolver: (ConflictChoice) -> Unit
    ) {
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

                onMemberDeleted?.invoke(deletedId)

                val remaining = allMembersList.filterNot { it.id == deletedId }.map { p ->
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
                allMembersList = FirestoreHelper.sanitizeTreeRecords(remaining).sortedBy { "${it.firstName} ${it.lastName}" }
                onListUpdated?.invoke(allMembersList, event)
            }

            SyncChangeType.PROFILE_EDITED -> {
                val updatedPerson = event.primaryPerson
                val incoming = (listOfNotNull(updatedPerson) + event.affectedPersons).distinctBy { it.id }
                if (incoming.isNotEmpty()) {
                    mergePersonsIntoList(incoming)
                }
                onListUpdated?.invoke(allMembersList, event)
            }

            SyncChangeType.MEMBER_ADDED -> {
                val newPerson = event.primaryPerson
                val incoming = (listOfNotNull(newPerson) + event.affectedPersons).distinctBy { it.id }
                mergePersonsIntoList(incoming)
                onListUpdated?.invoke(allMembersList, event)
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
                        allMembersList = FirestoreHelper.sanitizeTreeRecords(cached)
                    }
                }
                onListUpdated?.invoke(allMembersList, event)
            }

            SyncChangeType.TREE_DELETED -> {
                if (event.treeId == treeId) {
                    allMembersList = emptyList()
                    onListUpdated?.invoke(emptyList(), event)
                }
            }

            SyncChangeType.TREE_CREATED -> {}
        }
    }

    /**
     * Merges incoming updated records into the in-memory member list and maintains alphabetical sorting.
     */
    fun mergePersonsIntoList(incoming: List<Person>) {
        if (incoming.isEmpty()) return
        val incomingMap = incoming.associateBy { it.id }
        val result = allMembersList.map { existing ->
            incomingMap[existing.id] ?: existing
        }.toMutableList()

        for (p in incoming) {
            if (result.none { it.id == p.id }) {
                result.add(p)
            }
        }
        allMembersList = FirestoreHelper.sanitizeTreeRecords(result).sortedBy { "${it.firstName} ${it.lastName}" }
    }

    /**
     * Filters members matching a search query string across first and last names.
     */
    fun filterMembers(rawQuery: String, excludedIds: Set<String> = emptySet()): List<Person> {
        val query = rawQuery.lowercase().trim()
        val visible = allMembersList.filter { it.id !in excludedIds }
        if (query.isEmpty()) return visible
        return visible.filter {
            "${it.firstName} ${it.lastName}".lowercase().contains(query)
        }
    }
}

