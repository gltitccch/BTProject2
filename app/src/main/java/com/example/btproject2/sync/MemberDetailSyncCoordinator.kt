package com.example.btproject2.sync

import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person

/**
 * Coordinates real-time synchronization between [CentralTreeSynchronizer] and
 * [com.example.btproject2.ui.activities.MemberDetailActivity] (the Person Record Page,
 * Relationship Linking Feature, and Relative Member Cards).
 *
 * Responsibilities:
 * - Subscribes to [CentralTreeSynchronizer] as an [AffectedScreen.MEMBER_DETAIL] listener.
 * - Tracks the active individual being viewed ([personId]) and their direct relatives
 *   (parents, spouse, children, in-laws).
 * - Reactively updates profile details, vital statistics, parental cards, spouse cards,
 *   and children lists when changes are confirmed anywhere in the app.
 * - Notifies when the viewed individual is deleted from the tree, allowing safe dismissal.
 * - Cleans dangling parent and spouse references if a relative is removed.
 * - Protects unsaved edits via [onConflictDetected].
 */
class MemberDetailSyncCoordinator(
    val personId: String,
    val treeId: String = "",
    val listenerKey: String = "MemberDetailActivity@${personId}_${System.currentTimeMillis()}",
    var currentPerson: Person? = null,
    var allMembersList: List<Person> = emptyList(),
    var onPersonUpdated: ((updatedPerson: Person?, allMembers: List<Person>, event: TreeSyncEvent) -> Unit)? = null,
    var onCurrentPersonDeleted: ((deletedPersonId: String) -> Unit)? = null,
    var onConflict: ((conflict: SyncConflictEvent, resolver: (ConflictChoice) -> Unit) -> Unit)? = null
) : SyncEventListener {

    override val subscriberKey: String get() = listenerKey
    override val screenType: AffectedScreen get() = AffectedScreen.MEMBER_DETAIL
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
        if (onConflict != null) {
            onConflict?.invoke(conflict, resolver)
        } else {
            resolver(ConflictChoice.REFRESH)
        }
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

                // 1. If the currently open member was deleted, notify screen to finish/dismiss
                if (FamilyLinkValidator.isSameId(deletedId, personId)) {
                    currentPerson = null
                    allMembersList = allMembersList.filterNot { FamilyLinkValidator.isSameId(it.id, deletedId) }
                    onCurrentPersonDeleted?.invoke(deletedId)
                    return
                }

                // 2. If a relative was deleted, clean dangling pointers on currentPerson
                var personModified = false
                var p = currentPerson
                if (p != null) {
                    if (FamilyLinkValidator.isSameId(p.fatherId, deletedId)) {
                        p = p.copy(fatherId = null, fatherRelationshipType = "")
                        personModified = true
                    }
                    if (FamilyLinkValidator.isSameId(p.motherId, deletedId)) {
                        p = p.copy(motherId = null, motherRelationshipType = "")
                        personModified = true
                    }
                    if (FamilyLinkValidator.isSameId(p.spouseId, deletedId)) {
                        p = p.copy(
                            spouseId = null,
                            maritalStatus = if (p.maritalStatus.equals("Married", ignoreCase = true)) "Single" else p.maritalStatus
                        )
                        personModified = true
                    }
                }
                currentPerson = p

                // 3. Remove deleted person from allMembersList and clean dangling references across tree
                val remaining = allMembersList.filterNot { FamilyLinkValidator.isSameId(it.id, deletedId) }.map { member ->
                    var mod = member
                    if (FamilyLinkValidator.isSameId(mod.fatherId, deletedId)) {
                        mod = mod.copy(fatherId = null, fatherRelationshipType = "")
                    }
                    if (FamilyLinkValidator.isSameId(mod.motherId, deletedId)) {
                        mod = mod.copy(motherId = null, motherRelationshipType = "")
                    }
                    if (FamilyLinkValidator.isSameId(mod.spouseId, deletedId)) {
                        mod = mod.copy(
                            spouseId = null,
                            maritalStatus = if (mod.maritalStatus.equals("Married", ignoreCase = true)) "Single" else mod.maritalStatus
                        )
                    }
                    mod
                }
                allMembersList = FirestoreHelper.sanitizeTreeRecords(remaining)

                onPersonUpdated?.invoke(currentPerson, allMembersList, event)
            }

            SyncChangeType.PROFILE_EDITED -> {
                val updatedPerson = event.primaryPerson
                val incoming = (listOfNotNull(updatedPerson) + event.affectedPersons).distinctBy { it.id }
                if (incoming.isNotEmpty()) {
                    mergePersonsIntoList(incoming)
                }

                // Update currentPerson if directly edited
                if (updatedPerson != null && FamilyLinkValidator.isSameId(updatedPerson.id, personId)) {
                    currentPerson = updatedPerson
                } else {
                    allMembersList.find { FamilyLinkValidator.isSameId(it.id, personId) }?.let {
                        currentPerson = it
                    }
                }

                onPersonUpdated?.invoke(currentPerson, allMembersList, event)
            }

            SyncChangeType.MEMBER_ADDED -> {
                val newPerson = event.primaryPerson
                val incoming = (listOfNotNull(newPerson) + event.affectedPersons).distinctBy { it.id }
                mergePersonsIntoList(incoming)

                // If new member is a child, spouse, or parent of currentPerson
                allMembersList.find { FamilyLinkValidator.isSameId(it.id, personId) }?.let {
                    currentPerson = it
                }

                onPersonUpdated?.invoke(currentPerson, allMembersList, event)
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
                    val cached = FirestoreHelper.getCachedPersons(treeId)
                    if (!cached.isNullOrEmpty()) {
                        allMembersList = FirestoreHelper.sanitizeTreeRecords(cached)
                    }
                }

                // Refresh currentPerson from latest synchronized list
                allMembersList.find { FamilyLinkValidator.isSameId(it.id, personId) }?.let {
                    currentPerson = it
                }

                onPersonUpdated?.invoke(currentPerson, allMembersList, event)
            }

            SyncChangeType.TREE_DELETED -> {
                if (event.treeId == treeId) {
                    currentPerson = null
                    allMembersList = emptyList()
                    onCurrentPersonDeleted?.invoke(personId)
                }
            }

            SyncChangeType.TREE_CREATED -> {}
            SyncChangeType.CLAN_MERGE_REQUEST_UPDATED -> {}
        }
    }

    /**
     * Merges incoming updated records into allMembersList.
     */
    fun mergePersonsIntoList(incoming: List<Person>) {
        if (incoming.isEmpty()) return
        val existingIds = allMembersList.map { it.id }.toSet()
        val relevantIncoming = incoming.filter { p ->
            if (existingIds.contains(p.id)) {
                true
            } else if (treeId.isBlank()) {
                true
            } else if (treeId == "default_tree") {
                p.treeId.isEmpty() || p.treeId == "default_tree"
            } else {
                p.treeId == treeId
            }
        }
        if (relevantIncoming.isEmpty()) return
        val incomingMap = relevantIncoming.associateBy { it.id }
        val result = allMembersList.map { existing ->
            incomingMap[existing.id] ?: existing
        }.toMutableList()

        for (p in relevantIncoming) {
            if (result.none { FamilyLinkValidator.isSameId(it.id, p.id) }) {
                result.add(p)
            }
        }
        allMembersList = FirestoreHelper.sanitizeTreeRecords(result)
    }

    /**
     * Resolves the current person's spouse from the synchronized list.
     */
    fun getResolvedSpouse(): Person? {
        val spouseId = currentPerson?.spouseId ?: return null
        return allMembersList.find { FamilyLinkValidator.isSameId(it.id, spouseId) }
    }

    /**
     * Resolves the current person's father from the synchronized list.
     */
    fun getResolvedFather(): Person? {
        val fatherId = currentPerson?.fatherId ?: return null
        return allMembersList.find { FamilyLinkValidator.isSameId(it.id, fatherId) }
    }

    /**
     * Resolves the current person's mother from the synchronized list.
     */
    fun getResolvedMother(): Person? {
        val motherId = currentPerson?.motherId ?: return null
        return allMembersList.find { FamilyLinkValidator.isSameId(it.id, motherId) }
    }

    /**
     * Resolves all children belonging to the current person.
     */
    fun getResolvedChildren(): List<Person> {
        val pid = currentPerson?.id ?: return emptyList()
        return allMembersList.filter {
            FamilyLinkValidator.isSameId(it.fatherId, pid) || FamilyLinkValidator.isSameId(it.motherId, pid)
        }
    }
}

