package com.example.btproject2.sync

/**
 * Interface implemented by screens, view models, or background components to receive
 * targeted synchronization events and conflict warnings.
 */
interface SyncEventListener {

    /**
     * Unique identifier for the subscriber (e.g. Activity class name or instance key).
     */
    val subscriberKey: String

    /**
     * The type of screen this subscriber represents (used for targeted dispatch).
     */
    val screenType: AffectedScreen get() = AffectedScreen.ALL_SCREENS

    /**
     * Target tree ID this subscriber is interested in (or empty / null for all trees).
     */
    val interestedTreeId: String? get() = null

    /**
     * Invoked when a confirmed family tree mutation is published.
     * The subscriber can inspect [event.scope] and update only the affected UI components.
     */
    fun onSyncEvent(event: TreeSyncEvent)

    /**
     * Invoked when an incoming confirmed save conflicts with an active unsaved edit session
     * registered by this subscriber.
     *
     * The subscriber can prompt the user to choose between:
     * - [ConflictChoice.REVIEW]: View changes side-by-side
     * - [ConflictChoice.REFRESH]: Overwrite local draft with the confirmed single source of truth
     * - [ConflictChoice.KEEP_EDITING]: Retain the local unsaved work
     *
     * @param conflict The details of the conflict.
     * @param resolver Callback that the UI invokes once the user makes their selection.
     */
    fun onConflictDetected(
        conflict: SyncConflictEvent,
        resolver: (ConflictChoice) -> Unit
    ) {
        // Default behavior: prompt or default to KEEP_EDITING to protect user's work
        resolver(ConflictChoice.KEEP_EDITING)
    }
}

