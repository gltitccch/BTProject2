package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class FamilyTree(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val inviteCode: String = "",
    @get:PropertyName("isPrivate") @set:PropertyName("isPrivate") var isPrivate: Boolean = false,
    val memberCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val treeType: String = TREE_TYPE_PERSONAL,
    val bridgeDescription: String = "",
    val mergeInviteCode: String = "",
    val sourceTree1Id: String = "",
    val sourceTree2Id: String = "",
    val coOwnerIds: List<String> = emptyList()
) {
    companion object {
        const val TREE_TYPE_PERSONAL = "PERSONAL"
        const val TREE_TYPE_MERGED_CLAN = "MERGED_CLAN"
    }
}

