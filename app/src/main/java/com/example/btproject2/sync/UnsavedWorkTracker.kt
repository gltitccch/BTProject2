package com.example.btproject2.sync

import com.example.btproject2.models.Person
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Options presented to the user when a remote or cross-screen confirmed save
 * conflicts with local unsaved work.
 */
enum class ConflictChoice {
    /**
     * Inspect a side-by-side comparison of local draft edits versus the newly confirmed saved record.
     */
    REVIEW,

    /**
     * Discard local unsaved modifications and update the screen with the authoritative saved record.
     */
    REFRESH,

    /**
     * Retain local unsaved modifications and allow the user to continue editing without interruption.
     */
    KEEP_EDITING
}

/**
 * Represents an active, uncommitted editing session on a particular screen for a family member.
 */
data class UnsavedWorkSession(
    val sessionKey: String,
    val screenKey: String,
    val personId: String,
    val treeId: String,
    val description: String,
    val draftSnapshot: Person? = null,
    val registeredAt: Long = System.currentTimeMillis()
)

/**
 * Event generated when an authoritative saved update affects a family member who has
 * uncommitted edits in an open screen.
 */
data class SyncConflictEvent(
    val conflictId: String = UUID.randomUUID().toString(),
    val screenKey: String,
    val personId: String,
    val unsavedSession: UnsavedWorkSession,
    val syncEvent: TreeSyncEvent,
    val message: String = "Information for this family member was updated on another screen. " +
            "Would you like to review the changes, refresh to the latest saved information, " +
            "or keep your unsaved edits?"
)

/**
 * Central registry that monitors unsaved work across all screens in KinTrace.
 *
 * Mandate:
 * "Do not overwrite a user’s unsaved work without warning. If another screen has unsaved
 * changes for the same member or relationship, inform the user that the information has
 * changed and let them choose whether to review, refresh, or keep editing."
 */
class UnsavedWorkTracker {

    private val activeSessions = ConcurrentHashMap<String, UnsavedWorkSession>()

    /**
     * Builds a composite key identifying a specific screen's edit session on a person.
     */
    private fun buildKey(screenKey: String, personId: String): String {
        return "${screenKey.trim()}::${personId.trim().lowercase()}"
    }

    /**
     * Registers that a screen currently holds uncommitted edits for a person.
     */
    fun registerUnsavedWork(
        screenKey: String,
        personId: String,
        treeId: String = "",
        description: String = "Editing profile",
        draftSnapshot: Person? = null
    ) {
        if (screenKey.isBlank() || personId.isBlank()) return
        val key = buildKey(screenKey, personId)
        activeSessions[key] = UnsavedWorkSession(
            sessionKey = key,
            screenKey = screenKey,
            personId = personId.trim(),
            treeId = treeId.trim(),
            description = description,
            draftSnapshot = draftSnapshot
        )
    }

    /**
     * Removes an uncommitted edit session once the user saves or cancels edits.
     */
    fun unregisterUnsavedWork(screenKey: String, personId: String) {
        val key = buildKey(screenKey, personId)
        activeSessions.remove(key)
    }

    /**
     * Removes all uncommitted edit sessions associated with a closed screen.
     */
    fun clearScreenWork(screenKey: String) {
        val trimmed = screenKey.trim()
        activeSessions.keys.removeIf { it.startsWith("$trimmed::") }
    }

    /**
     * Checks whether any screen currently has unsaved work for [personId].
     */
    fun hasUnsavedWork(personId: String): Boolean {
        if (personId.isBlank()) return false
        val clean = personId.trim().lowercase()
        return activeSessions.values.any { it.personId.lowercase() == clean }
    }

    /**
     * Retrieves all active unsaved sessions for a specific person.
     */
    fun getUnsavedSessionsForPerson(personId: String): List<UnsavedWorkSession> {
        if (personId.isBlank()) return emptyList()
        val clean = personId.trim().lowercase()
        return activeSessions.values.filter { it.personId.lowercase() == clean }
    }

    /**
     * Detects conflicts between a confirmed incoming [syncEvent] and any active unsaved edit sessions.
     * Excludes sessions originating from the screen that initiated the save.
     */
    fun detectConflicts(syncEvent: TreeSyncEvent): List<SyncConflictEvent> {
        val conflicts = mutableListOf<SyncConflictEvent>()
        val affectedIds = syncEvent.scope.allAffectedPersonIds.map { it.trim().lowercase() }.toSet()

        for (session in activeSessions.values) {
            val sessionPersonId = session.personId.trim().lowercase()
            if (affectedIds.contains(sessionPersonId)) {
                // If the screen originating the event is the one editing, it already has the saved state
                if (session.screenKey == syncEvent.sourceScreenDescription) {
                    continue
                }

                val personName = syncEvent.primaryPerson?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?: syncEvent.affectedPersons.find { it.id.equals(session.personId, ignoreCase = true) }?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?: "this family member"

                val msg = "The information for $personName was updated on another screen (${syncEvent.sourceScreenDescription.ifBlank { "saved record" }}). " +
                        "Would you like to review the changes, refresh to the latest saved information, or keep editing your unsaved changes?"

                conflicts.add(
                    SyncConflictEvent(
                        screenKey = session.screenKey,
                        personId = session.personId,
                        unsavedSession = session,
                        syncEvent = syncEvent,
                        message = msg
                    )
                )
            }
        }

        return conflicts
    }

    /**
     * Clears all recorded sessions (useful during logout or test teardowns).
     */
    fun clearAll() {
        activeSessions.clear()
    }
}

