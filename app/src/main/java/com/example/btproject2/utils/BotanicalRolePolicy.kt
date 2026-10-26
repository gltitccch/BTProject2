package com.example.btproject2.utils

/**
 * Pure Kotlin single source of truth for KinTrace Botanical Family-Tree Role Badges,
 * capability clearance rules, and hard safety bounds for the 60fps botanical animations.
 */
object BotanicalRolePolicy {

    const val MAX_TREE_RECURSION_DEPTH = 3
    const val MAX_TOTAL_BRANCH_SEGMENTS = 7 // 2^3 - 1 = 7 segments total
    const val MAX_PARTICLE_POOL_SIZE = 20
    const val AUTO_REST_TIMEOUT_MS = 600_000L // Continuous 10-minute loop while popup is open
    const val WATCHDOG_FRAME_LIMIT_MS = 180L // Only trips on genuine UI freeze, never on 30fps untouched idle
    const val WATCHDOG_STRIKE_LIMIT = 8

    enum class BotanicalTier(
        val tierLevel: Int,
        val roleKey: String,
        val displayRoleName: String,
        val symbol: String,
        val botanicalTitle: String,
        val shortTag: String,
        val primaryColorHex: Int,
        val summary: String,
        val canExplore: Boolean,
        val canEditMembers: Boolean,
        val canManagePrivacy: Boolean
    ) {
        SPROUT_VIEWER(
            tierLevel = 1,
            roleKey = "Viewer",
            displayRoleName = "Viewer",
            symbol = "🌱",
            botanicalTitle = "Young Sprout (Observer)",
            shortTag = "VIEWER • SPROUT",
            primaryColorHex = 0xFF4ADE80.toInt(),
            summary = "As a Young Sprout in this family tree, you can freely walk beneath the canopy, explore every branch, and read ancestor stories.",
            canExplore = true,
            canEditMembers = false,
            canManagePrivacy = false
        ),
        BRANCH_EDITOR(
            tierLevel = 2,
            roleKey = "Editor",
            displayRoleName = "Editor",
            symbol = "🌿",
            botanicalTitle = "Branch Tender (Contributor)",
            shortTag = "EDITOR • BRANCH",
            primaryColorHex = 0xFF34D399.toInt(),
            summary = "As a Branch Tender, you help nurture and grow the family tree by grafting new relatives, life dates, photos, and biographies!",
            canExplore = true,
            canEditMembers = true,
            canManagePrivacy = false
        ),
        CANOPY_OWNER(
            tierLevel = 3,
            roleKey = "Owner",
            displayRoleName = "Owner",
            symbol = "🌳",
            botanicalTitle = "Root & Canopy Keeper (Owner)",
            shortTag = "OWNER • ROOTS",
            primaryColorHex = 0xFFD4AF37.toInt(),
            summary = "As the Root & Canopy Keeper, you guard the deepest roots of this family tree, including privacy controls, invite codes, and member roles.",
            canExplore = true,
            canEditMembers = true,
            canManagePrivacy = true
        );
    }

    data class CapabilityItem(
        val icon: String,
        val label: String,
        val isUnlocked: Boolean,
        val statusText: String
    )

    /**
     * Maps any raw role string ("Owner", "Admin", "Editor", "Contributor", "Viewer", etc.)
     * to its official [BotanicalTier].
     */
    fun resolveTier(rawRole: String?, isTreeOwner: Boolean = false): BotanicalTier {
        if (isTreeOwner) return BotanicalTier.CANOPY_OWNER
        val normalized = rawRole?.trim()?.lowercase().orEmpty()
        return when {
            normalized.contains("owner") || normalized.contains("admin") || normalized.contains("creator") ->
                BotanicalTier.CANOPY_OWNER
            normalized.contains("editor") || normalized.contains("collaborator") || normalized.contains("contributor") ->
                BotanicalTier.BRANCH_EDITOR
            else ->
                BotanicalTier.SPROUT_VIEWER
        }
    }

    /**
     * Checks whether [userTier] has clearance for an action requiring [requiredTier].
     */
    fun hasClearance(userTier: BotanicalTier, requiredTier: BotanicalTier): Boolean {
        return userTier.tierLevel >= requiredTier.tierLevel
    }

    /**
     * Builds the 4-row Botanical Capabilities Checklist for the given [tier].
     */
    fun buildCapabilitiesChecklist(tier: BotanicalTier): List<CapabilityItem> {
        return listOf(
            CapabilityItem(
                icon = "🌱",
                label = "Explore Family Tree & Fan Chart",
                isUnlocked = tier.canExplore,
                statusText = "✓ Allowed"
            ),
            CapabilityItem(
                icon = "🍃",
                label = "Search Relatives & Biographies",
                isUnlocked = tier.canExplore,
                statusText = "✓ Allowed"
            ),
            CapabilityItem(
                icon = "🌿",
                label = "Graft / Edit Family Members",
                isUnlocked = tier.canEditMembers,
                statusText = if (tier.canEditMembers) "✓ Allowed" else "🔒 Branch Editor+"
            ),
            CapabilityItem(
                icon = "🌳",
                label = "Deep Roots Privacy & Roles",
                isUnlocked = tier.canManagePrivacy,
                statusText = if (tier.canManagePrivacy) "✓ Allowed" else "🔒 Tree Owner"
            )
        )
    }

    /**
     * Returns the exact number of branch segments drawn for a given recursion depth,
     * clamped strictly to [MAX_TREE_RECURSION_DEPTH].
     */
    fun computeSafeBranchSegmentCount(requestedDepth: Int): Int {
        val safeDepth = requestedDepth.coerceIn(1, MAX_TREE_RECURSION_DEPTH)
        return (1 shl safeDepth) - 1
    }
}
