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
    @get:PropertyName("quotaExpansionRequested") @set:PropertyName("quotaExpansionRequested") var quotaExpansionRequested: Boolean = false,
    val quotaRequestedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val treeType: String = TREE_TYPE_PERSONAL,
    val bridgeDescription: String = "",
    val mergeInviteCode: String = "",
    val sourceTree1Id: String = "",
    val sourceTree2Id: String = "",
    val coOwnerIds: List<String> = emptyList(),
    val founderRole: String = "",
    val founderPersonId: String = ""
) {
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

    companion object {
        const val TREE_TYPE_PERSONAL = "PERSONAL"
        const val TREE_TYPE_MERGED_CLAN = "MERGED_CLAN"
        const val DEFAULT_FREEMIUM_QUOTA = 50
        const val EXPANSION_STEP = 150
        const val NEAR_CAP_RATIO = 0.85
    }
}
