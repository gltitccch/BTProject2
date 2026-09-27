package com.example.btproject2.sync

import android.os.Handler
import android.os.Looper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.ClanMergeRequest
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Central Synchronization Process for the Philippine family tree application (KinTrace).
 *
 * Architectural Mandates:
 * 1. Single Source of Truth:
 *    "The saved information must be the single source of truth. The back end should validate
 *    and store the final saved information. The front end should update the display only after
 *    the save is confirmed."
 *
 * 2. Targeted & Fine-Grained Propagation:
 *    "The central process should identify which member, relationship, and screens are affected
 *    by the change. It should then update only the affected parts instead of reloading everything
 *    unnecessarily."
 *
 * 3. Unsaved Work Protection:
 *    "Do not overwrite a user’s unsaved work without warning. If another screen has unsaved
 *    changes for the same member or relationship, inform the user that the information has
 *    changed and let them choose whether to review, refresh, or keep editing."
 *
 * 4. Global Scope:
 *    Applies across all current & future users, family trees, members, relationships, and screens.
 */
class CentralTreeSynchronizer private constructor() {

    companion object {
        @Volatile
        private var instance: CentralTreeSynchronizer? = null

        @JvmStatic
        fun getInstance(): CentralTreeSynchronizer {
            return instance ?: synchronized(this) {
                instance ?: CentralTreeSynchronizer().also { instance = it }
            }
        }
    }

    /**
     * Unsaved work registry preventing accidental overwrites across concurrent screens.
     */
    val unsavedWorkTracker = UnsavedWorkTracker()

    /**
     * Thread-safe list of active screen subscribers.
     */
    private val subscribers = CopyOnWriteArrayList<SyncEventListener>()

    /**
     * Recent sync event log (bounded for diagnostics and audits).
     */
    private val recentEvents = CopyOnWriteArrayList<TreeSyncEvent>()
    private val maxEventHistory = 50

    // ══════════════════════════════════════════════════════════════
    // SUBSCRIBER MANAGEMENT
    // ══════════════════════════════════════════════════════════════

    /**
     * Subscribes a screen or component to receive targeted family-tree sync events.
     */
    fun registerListener(listener: SyncEventListener) {
        // Remove existing listener with same subscriberKey to prevent duplicates
        subscribers.removeIf { it.subscriberKey == listener.subscriberKey }
        subscribers.add(listener)
    }

    /**
     * Unsubscribes a listener (typically in onStop or onDestroy).
     */
    fun unregisterListener(listener: SyncEventListener) {
        subscribers.removeIf { it.subscriberKey == listener.subscriberKey }
    }

    /**
     * Unsubscribes a listener by subscriber key.
     */
    fun unregisterListenerByKey(subscriberKey: String) {
        subscribers.removeIf { it.subscriberKey == subscriberKey }
    }

    /**
     * Clears all registered subscribers.
     */
    fun clearListeners() {
        subscribers.clear()
    }

    /**
     * Returns count of active subscribers.
     */
    val subscriberCount: Int get() = subscribers.size

    /**
     * Returns an unmodifiable snapshot of recent confirmed sync events.
     */
    fun getRecentEvents(): List<TreeSyncEvent> = recentEvents.toList()

    // ══════════════════════════════════════════════════════════════
    // UNSAVED WORK PASS-THROUGH
    // ══════════════════════════════════════════════════════════════

    fun registerUnsavedWork(
        screenKey: String,
        personId: String,
        treeId: String = "",
        description: String = "Editing profile",
        draftSnapshot: Person? = null
    ) {
        unsavedWorkTracker.registerUnsavedWork(screenKey, personId, treeId, description, draftSnapshot)
    }

    fun unregisterUnsavedWork(screenKey: String, personId: String) {
        unsavedWorkTracker.unregisterUnsavedWork(screenKey, personId)
    }

    fun clearScreenWork(screenKey: String) {
        unsavedWorkTracker.clearScreenWork(screenKey)
    }

    fun hasUnsavedWork(personId: String): Boolean = unsavedWorkTracker.hasUnsavedWork(personId)

    // ══════════════════════════════════════════════════════════════
    // CORE PUBLISHING PIPELINE (8 REQUIRED TRIGGERS)
    // ══════════════════════════════════════════════════════════════

    /**
     * 1. A user edits a family member's profile information.
     */
    fun notifyProfileEdited(
        updatedPerson: Person,
        previousPerson: Person? = null,
        sourceScreen: String = ""
    ) {
        val allPersons = FirestoreHelper.getCachedPersons().orEmpty()
        val scope = SyncScopeResolver.resolveProfileEdited(updatedPerson, previousPerson, allPersons)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.PROFILE_EDITED,
            treeId = updatedPerson.treeId,
            scope = scope,
            primaryPerson = updatedPerson,
            affectedPersons = listOf(updatedPerson),
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 2. A user adds a new family member.
     */
    fun notifyMemberAdded(
        newPerson: Person,
        sourceScreen: String = ""
    ) {
        val allPersons = FirestoreHelper.getCachedPersons().orEmpty()
        val scope = SyncScopeResolver.resolveMemberAdded(newPerson, allPersons)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.MEMBER_ADDED,
            treeId = newPerson.treeId,
            scope = scope,
            primaryPerson = newPerson,
            affectedPersons = listOf(newPerson),
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 3. A user adds, edits, or removes a spouse relationship.
     */
    fun notifySpouseChanged(
        personA: Person,
        personB: Person?,
        isRemoval: Boolean = false,
        extraAffectedRelatives: Set<String> = emptySet(),
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveSpouseChanged(personA, personB, isRemoval, extraAffectedRelatives)
        val detail = RelationshipChangeDetail(
            relationshipKind = "Spouse",
            primaryPersonId = personA.id,
            secondaryPersonId = personB?.id,
            previousValue = if (isRemoval) personB?.id else null,
            newValue = if (!isRemoval) personB?.id else null,
            isRemoval = isRemoval
        )
        val affected = listOfNotNull(personA, personB)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.SPOUSE_RELATIONSHIP_CHANGED,
            treeId = personA.treeId,
            scope = scope,
            primaryPerson = personA,
            affectedPersons = affected,
            relationshipChange = detail,
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 4. A user adds, edits, or removes a biological parent-child relationship.
     */
    fun notifyParentChildChanged(
        child: Person,
        parent: Person?,
        role: String = "parent",
        isRemoval: Boolean = false,
        extraAffectedRelatives: Set<String> = emptySet(),
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveParentChildChanged(
            child = child,
            parent = parent,
            role = role,
            isAdoptive = false,
            isRemoval = isRemoval,
            extraAffectedRelatives = extraAffectedRelatives
        )
        val detail = RelationshipChangeDetail(
            relationshipKind = "BiologicalParent",
            primaryPersonId = child.id,
            secondaryPersonId = parent?.id,
            previousValue = if (isRemoval) parent?.id else null,
            newValue = if (!isRemoval) parent?.id else null,
            isRemoval = isRemoval
        )
        val affected = listOfNotNull(child, parent)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.PARENT_CHILD_RELATIONSHIP_CHANGED,
            treeId = child.treeId,
            scope = scope,
            primaryPerson = child,
            affectedPersons = affected,
            relationshipChange = detail,
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 5. A user adds, edits, or removes an adoptive relationship.
     */
    fun notifyAdoptiveChanged(
        child: Person,
        parent: Person?,
        role: String = "parent",
        isRemoval: Boolean = false,
        extraAffectedRelatives: Set<String> = emptySet(),
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveParentChildChanged(
            child = child,
            parent = parent,
            role = role,
            isAdoptive = true,
            isRemoval = isRemoval,
            extraAffectedRelatives = extraAffectedRelatives
        )
        val detail = RelationshipChangeDetail(
            relationshipKind = "AdoptiveParent",
            primaryPersonId = child.id,
            secondaryPersonId = parent?.id,
            previousValue = if (isRemoval) parent?.id else null,
            newValue = if (!isRemoval) parent?.id else null,
            isRemoval = isRemoval
        )
        val affected = listOfNotNull(child, parent)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.ADOPTIVE_RELATIONSHIP_CHANGED,
            treeId = child.treeId,
            scope = scope,
            primaryPerson = child,
            affectedPersons = affected,
            relationshipChange = detail,
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 6. A user changes a relationship status or relationship date.
     */
    fun notifyRelationshipMetadataChanged(
        personA: Person,
        personB: Person? = null,
        metadataFields: Set<String> = setOf("maritalStatus", "marriageDate"),
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveRelationshipMetadataChanged(personA, personB, metadataFields)
        val detail = RelationshipChangeDetail(
            relationshipKind = "RelationshipMetadata",
            primaryPersonId = personA.id,
            secondaryPersonId = personB?.id,
            newValue = personA.maritalStatus,
            isRemoval = false
        )
        val affected = listOfNotNull(personA, personB)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.RELATIONSHIP_METADATA_CHANGED,
            treeId = personA.treeId,
            scope = scope,
            primaryPerson = personA,
            affectedPersons = affected,
            relationshipChange = detail,
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 7. A user deletes a family member.
     */
    fun notifyMemberDeleted(
        deletedPersonId: String,
        treeId: String,
        affectedRelatives: Set<String> = emptySet(),
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveMemberDeleted(deletedPersonId, treeId, affectedRelatives)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.MEMBER_DELETED,
            treeId = treeId,
            scope = scope,
            primaryPerson = null,
            affectedPersons = emptyList(),
            deletedPersonId = deletedPersonId,
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 8. A user corrects or removes an invalid family relationship.
     */
    fun notifyRelationshipCorrected(
        primaryPerson: Person,
        affectedRelatives: Set<String> = emptySet(),
        issueDescription: String = "",
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveRelationshipCorrected(primaryPerson, affectedRelatives)
        val detail = RelationshipChangeDetail(
            relationshipKind = "GraphCorrection",
            primaryPersonId = primaryPerson.id,
            newValue = issueDescription,
            isRemoval = true
        )
        val event = TreeSyncEvent(
            changeType = SyncChangeType.RELATIONSHIP_CORRECTED,
            treeId = primaryPerson.treeId,
            scope = scope,
            primaryPerson = primaryPerson,
            affectedPersons = listOf(primaryPerson),
            relationshipChange = detail,
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 9. A family tree is deleted (e.g. Master Clan Tree C or personal tree).
     */
    fun notifyTreeDeleted(
        deletedTreeId: String,
        sourceScreen: String = ""
    ) {
        if (currentActiveTreeId == deletedTreeId) {
            stopRealtimeListener()
        }
        val scope = SyncScopeResolver.resolveTreeDeleted(deletedTreeId)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.TREE_DELETED,
            treeId = deletedTreeId,
            scope = scope,
            primaryPerson = null,
            affectedPersons = emptyList(),
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 10. A family tree is created (e.g. Master Clan Tree C synthesized or personal tree created).
     */
    fun notifyTreeCreated(
        tree: FamilyTree,
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveTreeCreated(tree.id)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.TREE_CREATED,
            treeId = tree.id,
            scope = scope,
            primaryPerson = null,
            affectedPersons = emptyList(),
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    /**
     * 11. A clan merge request was updated (created, approved, rejected, cancelled, completed, failed).
     */
    fun notifyClanMergeRequestUpdated(
        request: ClanMergeRequest,
        sourceScreen: String = ""
    ) {
        val scope = SyncScopeResolver.resolveClanMergeRequestUpdated(request.targetTreeId)
        val event = TreeSyncEvent(
            changeType = SyncChangeType.CLAN_MERGE_REQUEST_UPDATED,
            treeId = request.targetTreeId,
            scope = scope,
            primaryPerson = null,
            affectedPersons = emptyList(),
            sourceScreenDescription = sourceScreen,
            isConfirmedSave = true
        )
        publishConfirmedEvent(event)
    }

    // ══════════════════════════════════════════════════════════════
    // EVENT BROADCAST & CONFLICT DISPATCH ENGINE
    // ══════════════════════════════════════════════════════════════

    /**
     * Authoritatively publishes a confirmed sync event to all subscribers.
     * Enforces in-memory cache coherence, detects unsaved work conflicts,
     * and dispatches updates targeted to affected screens.
     */
    fun publishConfirmedEvent(event: TreeSyncEvent) {
        // Enforce save confirmation rule
        if (!event.isConfirmedSave) {
            return
        }

        // 1. Maintain in-memory cache coherence with authoritative saved state
        syncMemoryCacheWithConfirmedEvent(event)

        // 2. Track event history
        recentEvents.add(0, event)
        while (recentEvents.size > maxEventHistory) {
            recentEvents.removeAt(recentEvents.size - 1)
        }

        // 3. Detect unsaved work conflicts
        val conflicts = unsavedWorkTracker.detectConflicts(event)
        val conflictingScreenKeys = conflicts.map { it.screenKey }.toSet()

        // 4. Dispatch to subscribers
        dispatchOnMain {
            for (subscriber in subscribers) {
                // Check tree scope filtering
                val treeMatch = subscriber.interestedTreeId.isNullOrBlank() ||
                        event.treeId.isBlank() ||
                        subscriber.interestedTreeId.equals(event.treeId, ignoreCase = true) ||
                        (subscriber.interestedTreeId == "default_tree" && (event.treeId.isBlank() || event.treeId == "default_tree"))

                // Check screen scope filtering
                val screenMatch = event.scope.isScreenAffected(subscriber.screenType)

                if (!treeMatch) continue
                if (!screenMatch) continue

                // Check unsaved work conflict for this specific subscriber
                if (conflictingScreenKeys.contains(subscriber.subscriberKey)) {
                    val matchingConflict = conflicts.find { it.screenKey == subscriber.subscriberKey }
                    if (matchingConflict != null) {
                        subscriber.onConflictDetected(matchingConflict) { choice ->
                            when (choice) {
                                ConflictChoice.REVIEW -> {
                                    // Subscriber displays diff review dialog
                                }
                                ConflictChoice.REFRESH -> {
                                    // Subscriber discards local draft and receives authoritative event
                                    unsavedWorkTracker.unregisterUnsavedWork(matchingConflict.screenKey, matchingConflict.personId)
                                    subscriber.onSyncEvent(event)
                                }
                                ConflictChoice.KEEP_EDITING -> {
                                    // Subscriber retains unsaved draft; authoritative event is not forced
                                }
                            }
                        }
                        continue
                    }
                }

                // If no conflict on this screen, directly update affected components
                try {
                    subscriber.onSyncEvent(event)
                } catch (e: Exception) {
                    android.util.Log.e("CentralTreeSynchronizer", "Error dispatching sync event to ${subscriber.subscriberKey}", e)
                }
            }
        }
    }

    /**
     * Synchronizes FirestoreHelper in-memory cache with the authoritative event
     * so that any immediate read sees the saved data immediately.
     */
    private fun syncMemoryCacheWithConfirmedEvent(event: TreeSyncEvent) {
        try {
            when (event.changeType) {
                SyncChangeType.MEMBER_DELETED -> {
                    event.deletedPersonId?.let { FirestoreHelper.removePersonFromCache(it) }
                }
                SyncChangeType.MEMBER_ADDED -> {
                    event.primaryPerson?.let { FirestoreHelper.addPersonToCache(it) }
                }
                else -> {
                    for (person in event.affectedPersons) {
                        FirestoreHelper.updatePersonInCache(person)
                    }
                    if (event.primaryPerson != null && event.affectedPersons.none { it.id == event.primaryPerson.id }) {
                        FirestoreHelper.updatePersonInCache(event.primaryPerson)
                    }
                }
            }
        } catch (_: Exception) {
            // Memory sync failsafe
        }
    }

    /**
     * Safely executes an action on the UI/Main Thread, falling back to direct execution
     * when Looper is unavailable (such as in local JVM unit tests).
     */
    private fun dispatchOnMain(action: () -> Unit) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null && Looper.myLooper() == looper) {
                action()
            } else if (looper != null) {
                Handler(looper).post(action)
            } else {
                action()
            }
        } catch (_: Exception) {
            action()
        }
    }

    private var realtimeListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentActiveTreeId: String? = null

    /**
     * Starts the global active real-time Firestore snapshot listener for [treeId].
     * Continuously observes the active family tree collection in Cloud Firestore
     * and broadcasts confirmed TreeSyncEvents to all registered screen subscribers in real time.
     */
    fun startRealtimeListener(treeId: String = "") {
        if (treeId.isBlank()) {
            stopRealtimeListener()
            return
        }
        if (realtimeListenerRegistration != null && currentActiveTreeId == treeId) {
            return
        }
        stopRealtimeListener()
        currentActiveTreeId = treeId

        try {
            realtimeListenerRegistration = FirestoreHelper().listenToTreePersons(
                treeId = treeId,
                onUpdate = { updatedPersons ->
                    val oldList = FirestoreHelper.getCachedPersons().orEmpty().filter { it.treeId == treeId }
                    FirestoreHelper.setCachedPersons(updatedPersons)

                    val oldMap = oldList.associateBy { it.id }
                    val newMap = updatedPersons.associateBy { it.id }

                    // Avoid event storm when an entire tree is loaded for the first time
                    if (oldList.isEmpty() && updatedPersons.size > 1) {
                        val focal = updatedPersons.firstOrNull()
                        if (focal != null) {
                            notifyMemberAdded(focal, sourceScreen = "RealtimeListenerInitialLoad")
                        }
                        return@listenToTreePersons
                    }

                    // Broadcast additions and modifications
                    for (person in updatedPersons) {
                        val prev = oldMap[person.id]
                        if (prev == null) {
                            notifyMemberAdded(person, sourceScreen = "RealtimeListener")
                        } else if (prev != person) {
                            notifyProfileEdited(person, previousPerson = prev, sourceScreen = "RealtimeListener")
                        }
                    }

                    // Broadcast deletions
                    for (oldPerson in oldList) {
                        if (!newMap.containsKey(oldPerson.id)) {
                            notifyMemberDeleted(deletedPersonId = oldPerson.id, treeId = oldPerson.treeId, sourceScreen = "RealtimeListener")
                        }
                    }
                },
                onError = {
                    // Real-time snapshot listener error
                }
            )
        } catch (_: Throwable) {
            // Environment failsafe (e.g. unit tests without Firebase initialized)
        }
    }

    /**
     * Safely detaches the active real-time Firestore listener.
     */
    fun stopRealtimeListener() {
        try {
            realtimeListenerRegistration?.remove()
        } catch (_: Throwable) {}
        realtimeListenerRegistration = null
        currentActiveTreeId = null
    }

    /**
     * Resets the synchronizer state (used for automated tests).
     */
    fun resetForTesting() {
        stopRealtimeListener()
        subscribers.clear()
        recentEvents.clear()
        unsavedWorkTracker.clearAll()
    }
}
