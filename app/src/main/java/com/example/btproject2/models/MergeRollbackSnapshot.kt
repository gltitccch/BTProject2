package com.example.btproject2.models

/**
 * Immutable Pre-Merge Snapshot for 1-Tap Transactional Rollback (Pillar 2 - Item 7).
 *
 * Captures the exact pre-merge state before either:
 * 1. [TYPE_CLAN_SYNTHESIS]: Combining two family trees into a synthesized Master Clan Tree.
 * 2. [TYPE_INTRA_BRANCH]: Linking two internal branches within an existing tree.
 */
data class MergeRollbackSnapshot(
    val id: String = "",
    val ownerId: String = "",
    val snapshotType: String = TYPE_CLAN_SYNTHESIS,
    val titleSummary: String = "",
    val sourceTree1Id: String = "",
    val sourceTree1Name: String = "",
    val sourceTree2Id: String = "",
    val sourceTree2Name: String = "",
    val synthesizedMasterTreeId: String = "",
    val synthesizedMasterTreeName: String = "",
    val combinedMemberCount: Int = 0,
    val bridgeDescription: String = "",
    val intraTreeId: String = "",
    val previousPersonA: Person? = null,
    val previousPersonB: Person? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val rolledBack: Boolean = false
) {
    companion object {
        const val TYPE_CLAN_SYNTHESIS = "CLAN_SYNTHESIS"
        const val TYPE_INTRA_BRANCH = "INTRA_BRANCH"
        const val MAX_FIRESTORE_BATCH_CHUNK = 200
    }
}
