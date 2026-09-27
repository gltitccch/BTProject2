package com.example.btproject2.sync

/**
 * Categorizes the 8 distinct family-tree mutation triggers supported by the
 * central synchronization process in KinTrace.
 */
enum class SyncChangeType {
    /**
     * 1. A user edits a family member's profile information, such as name,
     * date of birth, gender, photo, biography, living status, birth place, or death details.
     */
    PROFILE_EDITED,

    /**
     * 2. A user adds a new family member to the tree.
     */
    MEMBER_ADDED,

    /**
     * 3. A user adds, edits, or removes a spouse/partner relationship.
     */
    SPOUSE_RELATIONSHIP_CHANGED,

    /**
     * 4. A user adds, edits, or removes a biological parent-child relationship.
     */
    PARENT_CHILD_RELATIONSHIP_CHANGED,

    /**
     * 5. A user adds, edits, or removes an adoptive relationship (adoptive parent or child).
     */
    ADOPTIVE_RELATIONSHIP_CHANGED,

    /**
     * 6. A user changes a relationship status (e.g. Married, Single, Divorced, Annulled, Separated)
     * or relationship date (e.g. marriage date).
     */
    RELATIONSHIP_METADATA_CHANGED,

    /**
     * 7. A user deletes a family member from the tree.
     */
    MEMBER_DELETED,

    /**
     * 8. A user corrects or removes an invalid family relationship (e.g., resolving a graph cycle,
     * removing an illegal consanguinity connection, or fixing parent-child misalignments).
     */
    RELATIONSHIP_CORRECTED,

    /**
     * 9. A family tree (e.g., Master Clan Tree C or personal tree) is deleted.
     */
    TREE_DELETED,

    /**
     * 10. A family tree (e.g., Master Clan Tree C or personal tree) is created or synthesized.
     */
    TREE_CREATED,

    /**
     * 11. A clan merge request was created, approved, rejected, cancelled, completed, or failed.
     */
    CLAN_MERGE_REQUEST_UPDATED
}
