package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * ClanTriviaSession:
 * Central real-time Firestore room document for KinClash live party trivia sessions.
 * Stored at `trivia_sessions/{pin}`.
 */
@IgnoreExtraProperties
data class ClanTriviaSession(
    val pin: String = "",
    val clanId: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val status: String = STATUS_LOBBY,
    val currentQuestionIndex: Int = 0,
    val timerRemaining: Int = 20,
    val isPaused: Boolean = false,
    val questions: List<TriviaQuestion> = emptyList(),
    val players: Map<String, TriviaPlayer> = emptyMap(),
    val pendingClaims: List<BranchClaimRequest> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_LOBBY = "LOBBY"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}

/**
 * TriviaQuestion:
 * An individual multiple-choice question in the KinClash deck.
 * Supports procedural kinship questions, shared birthdays, birthplaces, and custom inside jokes.
 * When photos are missing, [isSilhouetteMode] is set to true with 3 [fallbackClues].
 */
@IgnoreExtraProperties
data class TriviaQuestion(
    val id: String = "",
    val prompt: String = "",
    val category: String = CATEGORY_KINSHIP,
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = 0,
    val proofMemberId: String = "",
    val proofFact: String = "",
    val photoUrl: String = "",
    val fallbackClues: List<String> = emptyList(),
    val isSilhouetteMode: Boolean = false,
    val isCustomHost: Boolean = false
) {
    companion object {
        const val CATEGORY_KINSHIP = "KINSHIP"
        const val CATEGORY_SHARED_BIRTHDAY = "SHARED_BIRTHDAY"
        const val CATEGORY_ANCESTOR_ROOTS = "ANCESTOR_ROOTS"
        const val CATEGORY_VINTAGE_PHOTO = "VINTAGE_PHOTO"
        const val CATEGORY_HOST_CUSTOM = "HOST_CUSTOM"
        const val CATEGORY_HERITAGE_TRADITION = "HERITAGE_TRADITION"
    }
}

/**
 * TriviaPlayer:
 * Represents a connected relative or guest participant in the live room.
 */
@IgnoreExtraProperties
data class TriviaPlayer(
    val userId: String = "",
    val nickname: String = "",
    val score: Int = 0,
    val streak: Int = 0,
    val currentAnswerIndex: Int = -1,
    val hasSubmittedCurrentQuestion: Boolean = false,
    val isConnected: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "nickname" to nickname,
        "score" to score,
        "streak" to streak,
        "currentAnswerIndex" to currentAnswerIndex,
        "hasSubmittedCurrentQuestion" to hasSubmittedCurrentQuestion,
        "isConnected" to isConnected
    )
}

/**
 * BranchClaimRequest:
 * Created when a visiting guest relative taps "Claim My Branch" at the end of the game.
 * Allows the Clan Admin (Host) to review and approve them into the central Firestore tree.
 */
@IgnoreExtraProperties
data class BranchClaimRequest(
    val requestId: String = "",
    val userId: String = "",
    val nickname: String = "",
    val targetParentId: String = "",
    val targetParentName: String = "",
    val status: String = STATUS_PENDING,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "requestId" to requestId,
        "userId" to userId,
        "nickname" to nickname,
        "targetParentId" to targetParentId,
        "targetParentName" to targetParentName,
        "status" to status,
        "timestamp" to timestamp
    )

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"
    }
}
