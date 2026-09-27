package com.example.btproject2.sync

import com.example.btproject2.engine.GenerationHierarchyEngine
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.ui.adapters.TreeMemberAdapter

/**
 * Coordinates synchronization between [CentralTreeSynchronizer] and [com.example.btproject2.ui.activities.FamilyTreeActivity]
 * which hosts both the Pedigree View and the Semicircular Fan Chart.
 *
 * Mandates:
 * 1. Single Source of Truth: Saved information is authoritative. Discards stale view drafts on conflict.
 * 2. Automatic Updates: Updates both Pedigree View and Fan Chart across all 8 standard mutation triggers.
 * 3. State Preservation: Rebuilds data in-memory without requiring full activity reload, preserving zoom/scroll.
 * 4. Dual Channel Support: Listens to both [AffectedScreen.PEDIGREE_VIEW] and [AffectedScreen.FAN_CHART]
 *    without duplicate event processing.
 */
class PedigreeFanChartSyncCoordinator(
    val treeId: String = "",
    val listenerKey: String = "FamilyTreeActivity@${System.currentTimeMillis()}",
    override val screenType: AffectedScreen = AffectedScreen.PEDIGREE_VIEW,
    var allPersonsList: List<Person> = emptyList(),
    var onTreeUpdated: ((updatedList: List<Person>, event: TreeSyncEvent) -> Unit)? = null,
    var onMemberDeleted: ((deletedId: String) -> Unit)? = null
) : SyncEventListener {

    override val subscriberKey: String get() = listenerKey
    override val interestedTreeId: String? get() = treeId.ifBlank { null }

    /**
     * Secondary listener registered for [AffectedScreen.FAN_CHART] to ensure events
     * explicitly targeting the fan chart are captured even if [screenType] is [AffectedScreen.PEDIGREE_VIEW].
     * Avoids duplicate execution when an event targets both screens.
     */
    val fanChartListener: SyncEventListener = object : SyncEventListener {
        override val subscriberKey: String get() = "${listenerKey}_fanchart"
        override val screenType: AffectedScreen get() = AffectedScreen.FAN_CHART
        override val interestedTreeId: String? get() = treeId.ifBlank { null }

        override fun onSyncEvent(event: TreeSyncEvent) {
            if (!event.scope.isScreenAffected(this@PedigreeFanChartSyncCoordinator.screenType)) {
                applySyncEvent(event)
            }
        }

        override fun onConflictDetected(conflict: SyncConflictEvent, resolver: (ConflictChoice) -> Unit) {
            resolver(ConflictChoice.REFRESH)
        }
    }

    /**
     * Registers both the primary and fan chart listeners with the central synchronizer.
     */
    fun register(synchronizer: CentralTreeSynchronizer = CentralTreeSynchronizer.getInstance()) {
        synchronizer.registerListener(this)
        synchronizer.registerListener(fanChartListener)
    }

    /**
     * Unregisters both listeners from the central synchronizer.
     */
    fun unregister(synchronizer: CentralTreeSynchronizer = CentralTreeSynchronizer.getInstance()) {
        synchronizer.unregisterListener(this)
        synchronizer.unregisterListener(fanChartListener)
    }

    override fun onConflictDetected(
        conflict: SyncConflictEvent,
        resolver: (ConflictChoice) -> Unit
    ) {
        // Pedigree and Fan Chart display authoritative saved state; discard stale view drafts
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
                onTreeUpdated?.invoke(allPersonsList, event)
            }

            SyncChangeType.PROFILE_EDITED -> {
                val updatedPerson = event.primaryPerson
                val incoming = (listOfNotNull(updatedPerson) + event.affectedPersons).distinctBy { it.id }
                if (incoming.isNotEmpty()) {
                    mergePersonsIntoList(incoming)
                }
                onTreeUpdated?.invoke(allPersonsList, event)
            }

            SyncChangeType.MEMBER_ADDED -> {
                val newPerson = event.primaryPerson
                val incoming = (listOfNotNull(newPerson) + event.affectedPersons).distinctBy { it.id }
                mergePersonsIntoList(incoming)
                onTreeUpdated?.invoke(allPersonsList, event)
            }

            SyncChangeType.PARENT_CHILD_RELATIONSHIP_CHANGED,
            SyncChangeType.ADOPTIVE_RELATIONSHIP_CHANGED,
            SyncChangeType.SPOUSE_RELATIONSHIP_CHANGED,
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
                onTreeUpdated?.invoke(allPersonsList, event)
            }

            SyncChangeType.TREE_DELETED -> {
                if (event.treeId == treeId) {
                    allPersonsList = emptyList()
                    onTreeUpdated?.invoke(emptyList(), event)
                }
            }
        }
    }

    /**
     * Merges incoming updated records into the in-memory member list.
     */
    fun mergePersonsIntoList(incoming: List<Person>) {
        if (incoming.isEmpty()) return
        val incomingMap = incoming.associateBy { it.id }
        val result = allPersonsList.map { existing ->
            incomingMap[existing.id] ?: existing
        }.toMutableList()

        for (p in incoming) {
            if (result.none { it.id == p.id }) {
                result.add(p)
            }
        }
        allPersonsList = FirestoreHelper.sanitizeTreeRecords(result)
    }

    /**
     * Computes the authoritative generation hierarchy map using [GenerationHierarchyEngine].
     */
    fun computeGenerationMap(persons: List<Person> = allPersonsList): Map<String, Int> {
        return GenerationHierarchyEngine.computeGenerations1Based(persons)
    }

    /**
     * Builds flat generation item list for the Pedigree RecyclerView.
     */
    fun buildGenerationItems(
        persons: List<Person> = allPersonsList,
        generationMap: Map<String, Int> = computeGenerationMap(persons)
    ): List<TreeMemberAdapter.TreeItem> {
        if (persons.isEmpty()) return emptyList()

        val grouped = persons.groupBy { generationMap[it.id] ?: 1 }
        val items = mutableListOf<TreeMemberAdapter.TreeItem>()
        grouped.keys.sorted().forEach { gen ->
            items.add(TreeMemberAdapter.TreeItem.Header(gen))
            grouped[gen]
                ?.sortedWith(compareBy({ it.birthDate.ifEmpty { "9999" } }, { it.firstName }))
                ?.forEach { items.add(TreeMemberAdapter.TreeItem.Member(it)) }
        }
        return items
    }

    /**
     * Serializes tree members into the JSON array expected by the Fan Chart HTML5 canvas.
     */
    fun buildFanChartNodesJson(
        persons: List<Person> = allPersonsList,
        genMap: Map<String, Int> = computeGenerationMap(persons),
        focalPerson: Person? = null
    ): String {
        val sortedPersons = persons.sortedWith(
            compareBy(
                { genMap[it.id] ?: 1 },
                { it.fatherId ?: it.motherId ?: "" },
                { it.birthDate.ifEmpty { "9999" } },
                { it.firstName }
            )
        )
        val focal = focalPerson ?: persons.firstOrNull()
        return buildString {
            append("[")
            sortedPersons.forEachIndexed { i, p ->
                if (i > 0) append(",")
                val gen = genMap[p.id] ?: 1
                val parentId = p.fatherId ?: p.motherId
                val kinship = if (focal != null && focal.id != p.id && persons.isNotEmpty()) {
                    com.example.btproject2.engine.KinshipTitleHelper.resolveTitle(p, focal, persons)
                } else ""
                val memorialSuffix = if (!p.isLiving) " 🕊️" else ""
                append("{")
                append("\"id\":\"${p.id}\",")
                append("\"name\":\"${p.firstName.replace("\"", "\\\"")} ${p.lastName.replace("\"", "\\\"")}$memorialSuffix\",")
                append("\"gender\":\"${p.gender}\",")
                append("\"isLiving\":${p.isLiving},")
                append("\"kinship\":\"${kinship.replace("\"", "\\\"")}\",")
                append("\"generation\":$gen,")
                append("\"parentId\":${if (parentId != null) "\"$parentId\"" else "null"}")
                append("}")
            }
            append("]")
        }
    }
}

