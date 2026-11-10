package com.example.btproject2.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.btproject2.models.BranchClaimRequest
import com.example.btproject2.models.ClanTriviaSession
import com.google.firebase.firestore.FirebaseFirestore

/**
 * KinClashTestReceiver:
 * Broadcast receiver for automated device verification and multi-client orchestration
 * during KinClash live gameplay testing on Android Studio emulators.
 */
class KinClashTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pin = intent.getStringExtra("PIN").orEmpty().trim()
        if (pin.isEmpty()) return

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("trivia_sessions").document(pin)

        when (action) {
            ACTION_START_GAME -> {
                docRef.update(
                    mapOf(
                        "status" to ClanTriviaSession.STATUS_IN_PROGRESS,
                        "currentQuestionIndex" to 0,
                        "isPaused" to false,
                        "timerRemaining" to 20
                    )
                ).addOnSuccessListener {
                    Toast.makeText(context, "🎮 KinClash Live Started: Room #$pin", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_NEXT_QUESTION -> {
                val nextIdx = intent.getIntExtra("INDEX", 1)
                docRef.get().addOnSuccessListener { snap ->
                    val session = snap.toObject(ClanTriviaSession::class.java) ?: return@addOnSuccessListener
                    val resetPlayers = session.players.mapValues { (_, p) ->
                        p.copy(hasSubmittedCurrentQuestion = false, currentAnswerIndex = -1).toMap()
                    }
                    docRef.update(
                        mapOf(
                            "currentQuestionIndex" to nextIdx,
                            "timerRemaining" to 20,
                            "players" to resetPlayers,
                            "isPaused" to false
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(context, "⏭ Question ${nextIdx + 1} Live!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ACTION_FINISH_GAME -> {
                docRef.update(
                    mapOf(
                        "status" to ClanTriviaSession.STATUS_COMPLETED,
                        "isPaused" to false
                    )
                ).addOnSuccessListener {
                    Toast.makeText(context, "🏆 Game Finished! Showing Podium.", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_APPROVE_CLAIM -> {
                val requestId = intent.getStringExtra("REQUEST_ID").orEmpty()
                docRef.get().addOnSuccessListener { snap ->
                    val session = snap.toObject(ClanTriviaSession::class.java) ?: return@addOnSuccessListener
                    val updatedClaims = session.pendingClaims.map { claim ->
                        if (claim.requestId == requestId || requestId.isEmpty()) {
                            claim.copy(status = BranchClaimRequest.STATUS_APPROVED).toMap()
                        } else {
                            claim.toMap()
                        }
                    }
                    docRef.update("pendingClaims", updatedClaims).addOnSuccessListener {
                        Toast.makeText(context, "🌿 Branch Claim Approved by Host!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_START_GAME = "com.example.btproject2.START_KINCLASH"
        const val ACTION_NEXT_QUESTION = "com.example.btproject2.NEXT_QUESTION"
        const val ACTION_FINISH_GAME = "com.example.btproject2.FINISH_GAME"
        const val ACTION_APPROVE_CLAIM = "com.example.btproject2.APPROVE_CLAIM"
    }
}
