package com.example.btproject2.sync

import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person

/**
 * Coordinates real-time synchronization between [CentralTreeSynchronizer] and
 * [com.example.btproject2.ui.activities.AddMemberActivity] (the Add Member form, candidate relative
 * pickers, and child linking).
 *
 * Responsibilities:
 * - Subscribes to [CentralTreeSynchronizer] as an [AffectedScreen.ALL_SCREENS] listener.
 * - Keeps candidate father, mother, and spouse lists in sync in real time.
 * - Evicts deleted members from selected children and candidate lists automatically.
 * - Enforces the single source of truth by resolving conflicts with [ConflictChoice.REFRESH].
 */
class AddMemberSyncCoordinator(
    val treeId: String = "",
    val listenerKey: String = "AddMemberActivity@${System.currentTimeMillis()}",
    var allMembersList: List<Person> = emptyList(),
    var selectedChildrenList: MutableList<Person> = mutableListOf(),
    var onCandidatesUpdated: ((allMembers: List<Person>, maleMembers: List<Person>, femaleMembers: List<Person>, eligibleSpouses: List<Person>) -> Unit)? = null,
    var onChildRemovedFromSelection: ((removedChildId: String) -> Unit)? = null
) : SyncEventListener {

    override val subscriberKey: String get() = listenerKey
    override val screenType: AffectedScreen get() = AffectedScreen.ALL_SCREENS
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

                // Remove deleted member from selected children if present
                if (selectedChildrenList.any { FamilyLinkValidator.isSameId(it.id, deletedId) }) {
                    selectedChildrenList.removeAll { FamilyLinkValidator.isSameId(it.id, deletedId) }
                    onChildRemovedFromSelection?.invoke(deletedId)
                }

                // Remove from allMembersList
                allMembersList = allMembersList.filterNot { FamilyLinkValidator.isSameId(it.id, deletedId) }
                notifyCandidates()
            }

            SyncChangeType.PROFILE_EDITED -> {
                val updatedPerson = event.primaryPerson
                val incoming = (listOfNotNull(updatedPerson) + event.affectedPersons).distinctBy { it.id }
                if (incoming.isNotEmpty()) {
                    mergePersonsIntoList(incoming)
                }
                notifyCandidates()
            }

            SyncChangeType.MEMBER_ADDED -> {
                val newPerson = event.primaryPerson
                val incoming = (listOfNotNull(newPerson) + event.affectedPersons).distinctBy { it.id }
                mergePersonsIntoList(incoming)
                notifyCandidates()
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
                notifyCandidates()
            }
        }
    }

    /**
     * Merges incoming updated records into allMembersList.
     */
    fun mergePersonsIntoList(incoming: List<Person>) {
        if (incoming.isEmpty()) return
        val incomingMap = incoming.associateBy { it.id }
        val result = allMembersList.map { existing ->
            incomingMap[existing.id] ?: existing
        }.toMutableList()

        for (p in incoming) {
            if (result.none { FamilyLinkValidator.isSameId(it.id, p.id) }) {
                result.add(p)
            }
        }
        allMembersList = FirestoreHelper.sanitizeTreeRecords(result)
    }

    private fun notifyCandidates() {
        val maleMembers = allMembersList.filter { it.gender.equals("Male", ignoreCase = true) }
        val femaleMembers = allMembersList.filter { it.gender.equals("Female", ignoreCase = true) }
        val eligibleSpouses = allMembersList.filter { it.spouseId.isNullOrEmpty() }
        onCandidatesUpdated?.invoke(allMembersList, maleMembers, femaleMembers, eligibleSpouses)
    }
}

