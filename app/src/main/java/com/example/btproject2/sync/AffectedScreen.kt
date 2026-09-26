package com.example.btproject2.sync

/**
 * Enumerates all screens and components in KinTrace that present or manipulate
 * family tree data, allowing the central synchronizer to notify only the screens
 * that are actually impacted by a confirmed change.
 */
enum class AffectedScreen {
    /**
     * The main interactive family tree canvas (InteractiveTreeActivity).
     */
    INTERACTIVE_TREE,

    /**
     * Direct lineal ancestral pedigree view (mode PEDIGREE in InteractiveTreeActivity).
     */
    PEDIGREE_VIEW,

    /**
     * Semicircular generation fan chart (FamilyTreeActivity).
     */
    FAN_CHART,

    /**
     * Tabular member directory and record listing (FamilyRecordsActivity).
     */
    RECORDS_LIST,

    /**
     * Detailed individual profile and biological lineage record page (MemberDetailActivity).
     */
    MEMBER_DETAIL,

    /**
     * Blood-relationship path and consanguinity calculator (RelationshipPathActivity / TraceActivity).
     */
    RELATIONSHIP_PATH,

    /**
     * Central tree audit and consistency diagnostic screen (TreeAuditActivity).
     */
    TREE_AUDIT,

    /**
     * Statistical demographics, generation count, and health gauge (InsightsActivity).
     */
    INSIGHTS_DASHBOARD,

    /**
     * High-level dashboard summary cards and counts (HomeActivity).
     */
    HOME_OVERVIEW,

    /**
     * Branch comparison and duplicate resolution screen (MergeBranchesActivity).
     */
    MERGE_BRANCHES,

    /**
     * Wildcard representing all connected tree displays (used for deletions or whole-tree audits).
     */
    ALL_SCREENS
}

