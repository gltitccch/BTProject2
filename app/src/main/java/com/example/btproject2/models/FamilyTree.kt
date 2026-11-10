package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class FamilyTree(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val inviteCode: String = "",
    @get:PropertyName("isPrivate") @set:PropertyName("isPrivate") var isPrivate: Boolean = false,
    val memberCount: Int = 1,
    val memberQuota: Int = DEFAULT_FREEMIUM_QUOTA,
    val previousMemberQuota: Int = DEFAULT_FREEMIUM_QUOTA,
    @get:PropertyName("quotaExpansionRequested") @set:PropertyName("quotaExpansionRequested") var quotaExpansionRequested: Boolean = false,
    val quotaRequestedAt: Long = 0L,
    val quotaApprovedAt: Long = 0L,
    val quotaApprovedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val treeType: String = TREE_TYPE_PERSONAL,
    val bridgeDescription: String = "",
    val mergeInviteCode: String = "",
    val sourceTree1Id: String = "",
    val sourceTree2Id: String = "",
    val coOwnerIds: List<String> = emptyList(),
    val founderRole: String = "",
    val founderPersonId: String = "",
    @get:PropertyName("isPremium") @set:PropertyName("isPremium") var isPremium: Boolean = false
) {
    val isFreeTier: Boolean
        get() = !isPremium

    fun isAudioCapped(storiesCount: Int): Boolean {
        return !isPremium && storiesCount >= FREE_AUDIO_CAP
    }

    val effectiveQuota: Int
        get() = if (memberQuota > 0) memberQuota else DEFAULT_FREEMIUM_QUOTA

    fun isCapped(liveMemberCount: Int = memberCount): Boolean {
        return liveMemberCount >= effectiveQuota
    }

    fun isNearCap(liveMemberCount: Int = memberCount): Boolean {
        val threshold = (effectiveQuota * NEAR_CAP_RATIO).toInt().coerceAtLeast(1)
        return liveMemberCount in threshold until effectiveQuota
    }

    fun nextExpandedQuota(increment: Int = EXPANSION_STEP): Int {
        return when {
            effectiveQuota < 200 -> 200
            effectiveQuota < 500 -> 500
            else -> effectiveQuota + increment
        }
    }

    @com.google.firebase.firestore.Exclude
    fun isOwnedBy(userId: String): Boolean {
        return ownerId.isEmpty() || (userId.isNotBlank() && (ownerId == userId || coOwnerIds.contains(userId)))
    }

    @com.google.firebase.firestore.Exclude
    fun isMergedClan(): Boolean {
        return treeType.equals(TREE_TYPE_MERGED_CLAN, ignoreCase = true)
    }

    @com.google.firebase.firestore.Exclude
    fun switcherDisplayLabel(): String {
        return if (isMergedClan()) "🏛️ $name (Merged Clan)" else name
    }

    companion object {
        const val TREE_TYPE_PERSONAL = "PERSONAL"
        const val TREE_TYPE_MERGED_CLAN = "MERGED_CLAN"
        const val DEFAULT_FREEMIUM_QUOTA = 50
        const val EXPANSION_STEP = 150
        const val NEAR_CAP_RATIO = 0.85
        const val FREE_AUDIO_CAP = 5
        const val HERITAGE_PASS_PRICE_PHP = 99.0
        const val EXPANSION_BOOSTER_PRICE_PHP = 50.0
    }
}
