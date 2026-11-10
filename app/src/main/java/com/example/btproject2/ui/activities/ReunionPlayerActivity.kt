package com.example.btproject2.ui.activities

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.models.BranchClaimRequest
import com.example.btproject2.models.ClanTriviaSession
import com.example.btproject2.models.TriviaPlayer
import com.example.btproject2.models.TriviaQuestion
import com.example.btproject2.ui.dialogs.KinClashArcadeConfirmDialog
import com.example.btproject2.utils.ClanTriviaSoundEngine
import com.example.btproject2.utils.KinClashArcadeToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * ReunionPlayerActivity (KinClash Player Mobile Game Screen):
 * Exact Android realization of the approved Retro 8-Bit Pixel Quest theme.
 * Features 2x2 3D chiseled arcade buttons with immediate answer lock-in,
 * Mario Coin Block Jump animation with 8-bit sound synthesis on correct answers,
 * violent screen tremor and trapdoor drops into the abyss on wrong answers,
 * and live synchronization with ClanTriviaSession.
 */
class ReunionPlayerActivity : AppCompatActivity() {

    private var roomPin: String = ""
    private var nickname: String = ""
    private var userId: String = ""
    private var currentSession: ClanTriviaSession? = null
    private var sessionListener: ListenerRegistration? = null

    // Game state
    private var lastRenderedQuestionIndex: Int = -1
    private var hasAnsweredCurrentQuestion: Boolean = false
    private var currentScore: Int = 0
    private var currentStreak: Int = 1
    private var remainingSeconds: Int = 20
    private var questionTimer: CountDownTimer? = null

    // Shaking container and Red Vignette Flash
    private lateinit var layoutRootShakeContainer: LinearLayout
    private lateinit var vRedVignette: View

    // Top HUD
    private lateinit var btnPlayerBack: TextView
    private lateinit var tvPlayerRoomBadge: TextView
    private lateinit var tvPlayerCodename: TextView
    private lateinit var tvPlayerStreakBadge: TextView
    private lateinit var tvPlayerScoreBadge: TextView

    // State 1: Lobby
    private lateinit var layoutPlayerLobby: LinearLayout
    private lateinit var tvLobbySubtext: TextView

    // State 2: Game Screen (Retro 8-Bit Quest)
    private lateinit var layoutPlayerGame: ScrollView
    private lateinit var tvPlayerCountdown: TextView
    private lateinit var tvPlayerQuestionCategory: TextView
    private lateinit var tvPlayerQuestionCounter: TextView
    private lateinit var tvPlayerQuestionPrompt: TextView
    private lateinit var tvPlayerSilhouetteClues: TextView

    // 2x2 Arcade Buttons
    private lateinit var btnChoiceA: FrameLayout
    private lateinit var btnChoiceB: FrameLayout
    private lateinit var btnChoiceC: FrameLayout
    private lateinit var btnChoiceD: FrameLayout

    private lateinit var tvChoiceAName: TextView
    private lateinit var tvChoiceBName: TextView
    private lateinit var tvChoiceCName: TextView
    private lateinit var tvChoiceDName: TextView

    private lateinit var tvChoiceATag: TextView
    private lateinit var tvChoiceBTag: TextView
    private lateinit var tvChoiceCTag: TextView
    private lateinit var tvChoiceDTag: TextView

    private lateinit var tvChoiceAIcon: TextView
    private lateinit var tvChoiceBIcon: TextView
    private lateinit var tvChoiceCIcon: TextView
    private lateinit var tvChoiceDIcon: TextView

    private lateinit var badgeChoiceALock: TextView
    private lateinit var badgeChoiceBLock: TextView
    private lateinit var badgeChoiceCLock: TextView
    private lateinit var badgeChoiceDLock: TextView

    // Status Card & Feedback
    private lateinit var cardPlayerSubmittedStatus: LinearLayout
    private lateinit var tvPlayerStatusTitle: TextView
    private lateinit var tvPlayerSubmissionsStatus: TextView
    private lateinit var tvPlayerReadyCount: TextView
    private lateinit var cardPlayerResultFeedback: LinearLayout
    private lateinit var tvPlayerResultBanner: TextView
    private lateinit var tvPlayerProofText: TextView

    // State 3: Paused Overlay
    private lateinit var layoutPlayerPausedOverlay: LinearLayout

    // State 4: Completed & Podium
    private lateinit var layoutPlayerCompleted: ScrollView
    private lateinit var tvPlayerFinalScore: TextView
    private lateinit var tvPodiumRank1Name: TextView
    private lateinit var tvPodiumRank1Score: TextView
    private lateinit var tvPodiumRank2Name: TextView
    private lateinit var tvPodiumRank2Score: TextView
    private lateinit var tvPodiumRank3Name: TextView
    private lateinit var tvPodiumRank3Score: TextView
    private lateinit var btnPlayerShareCard: Button
    private lateinit var cardPlayerClaimBranch: LinearLayout
    private lateinit var tvPlayerClaimPending: TextView
    private lateinit var layoutBranchApproved: LinearLayout
    private lateinit var btnViewMyTreeNode: Button
    private lateinit var btnPlayerClaimBranch: Button
    private lateinit var btnPlayerExit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reunion_player)

        roomPin = intent.getStringExtra("PIN").orEmpty().trim().uppercase()
        nickname = intent.getStringExtra("NICKNAME").orEmpty().trim()
            .ifEmpty { "Player_${(100..999).random()}" }

        val auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid ?: "guest_${System.currentTimeMillis()}"

        initViews()
        connectToRoom()
    }

    override fun onDestroy() {
        super.onDestroy()
        questionTimer?.cancel()
        sessionListener?.remove()
        sessionListener = null
    }

    private fun initViews() {
        layoutRootShakeContainer = findViewById(R.id.layoutRootShakeContainer)
        vRedVignette = findViewById(R.id.vRedVignette)

        btnPlayerBack = findViewById(R.id.btnPlayerBack)
        tvPlayerRoomBadge = findViewById(R.id.tvPlayerRoomBadge)
        tvPlayerCodename = findViewById(R.id.tvPlayerCodename)
        tvPlayerStreakBadge = findViewById(R.id.tvPlayerStreakBadge)
        tvPlayerScoreBadge = findViewById(R.id.tvPlayerScoreBadge)

        layoutPlayerLobby = findViewById(R.id.layoutPlayerLobby)
        tvLobbySubtext = findViewById(R.id.tvLobbySubtext)

        layoutPlayerGame = findViewById(R.id.layoutPlayerGame)
        tvPlayerCountdown = findViewById(R.id.tvPlayerCountdown)
        tvPlayerQuestionCategory = findViewById(R.id.tvPlayerQuestionCategory)
        tvPlayerQuestionCounter = findViewById(R.id.tvPlayerQuestionCounter)
        tvPlayerQuestionPrompt = findViewById(R.id.tvPlayerQuestionPrompt)
        tvPlayerSilhouetteClues = findViewById(R.id.tvPlayerSilhouetteClues)

        btnChoiceA = findViewById(R.id.btnChoiceA)
        btnChoiceB = findViewById(R.id.btnChoiceB)
        btnChoiceC = findViewById(R.id.btnChoiceC)
        btnChoiceD = findViewById(R.id.btnChoiceD)

        tvChoiceAName = findViewById(R.id.tvChoiceAName)
        tvChoiceBName = findViewById(R.id.tvChoiceBName)
        tvChoiceCName = findViewById(R.id.tvChoiceCName)
        tvChoiceDName = findViewById(R.id.tvChoiceDName)

        tvChoiceATag = findViewById(R.id.tvChoiceATag)
        tvChoiceBTag = findViewById(R.id.tvChoiceBTag)
        tvChoiceCTag = findViewById(R.id.tvChoiceCTag)
        tvChoiceDTag = findViewById(R.id.tvChoiceDTag)

        tvChoiceAIcon = findViewById(R.id.tvChoiceAIcon)
        tvChoiceBIcon = findViewById(R.id.tvChoiceBIcon)
        tvChoiceCIcon = findViewById(R.id.tvChoiceCIcon)
        tvChoiceDIcon = findViewById(R.id.tvChoiceDIcon)

        badgeChoiceALock = findViewById(R.id.badgeChoiceALock)
        badgeChoiceBLock = findViewById(R.id.badgeChoiceBLock)
        badgeChoiceCLock = findViewById(R.id.badgeChoiceCLock)
        badgeChoiceDLock = findViewById(R.id.badgeChoiceDLock)

        cardPlayerSubmittedStatus = findViewById(R.id.cardPlayerSubmittedStatus)
        tvPlayerStatusTitle = findViewById(R.id.tvPlayerStatusTitle)
        tvPlayerSubmissionsStatus = findViewById(R.id.tvPlayerSubmissionsStatus)
        tvPlayerReadyCount = findViewById(R.id.tvPlayerReadyCount)

        cardPlayerResultFeedback = findViewById(R.id.cardPlayerResultFeedback)
        tvPlayerResultBanner = findViewById(R.id.tvPlayerResultBanner)
        tvPlayerProofText = findViewById(R.id.tvPlayerProofText)

        layoutPlayerPausedOverlay = findViewById(R.id.layoutPlayerPausedOverlay)

        layoutPlayerCompleted = findViewById(R.id.layoutPlayerCompleted)
        tvPlayerFinalScore = findViewById(R.id.tvPlayerFinalScore)
        tvPodiumRank1Name = findViewById(R.id.tvPodiumRank1Name)
        tvPodiumRank1Score = findViewById(R.id.tvPodiumRank1Score)
        tvPodiumRank2Name = findViewById(R.id.tvPodiumRank2Name)
        tvPodiumRank2Score = findViewById(R.id.tvPodiumRank2Score)
        tvPodiumRank3Name = findViewById(R.id.tvPodiumRank3Name)
        tvPodiumRank3Score = findViewById(R.id.tvPodiumRank3Score)
        btnPlayerShareCard = findViewById(R.id.btnPlayerShareCard)
        cardPlayerClaimBranch = findViewById(R.id.cardPlayerClaimBranch)
        tvPlayerClaimPending = findViewById(R.id.tvPlayerClaimPending)
        layoutBranchApproved = findViewById(R.id.layoutBranchApproved)
        btnViewMyTreeNode = findViewById(R.id.btnViewMyTreeNode)
        btnPlayerClaimBranch = findViewById(R.id.btnPlayerClaimBranch)
        btnPlayerExit = findViewById(R.id.btnPlayerExit)

        tvPlayerRoomBadge.text = "🕹️ ROOM #$roomPin"
        tvPlayerCodename.text = "Player: $nickname"
        updateScoreHud()

        btnPlayerBack.setOnClickListener { handleExit() }

        val clickA = View.OnClickListener { handleAnswerSelection(0) }
        btnChoiceA.setOnClickListener(clickA)
        tvChoiceAName.setOnClickListener(clickA)
        tvChoiceATag.setOnClickListener(clickA)
        tvChoiceAIcon.setOnClickListener(clickA)

        val clickB = View.OnClickListener { handleAnswerSelection(1) }
        btnChoiceB.setOnClickListener(clickB)
        tvChoiceBName.setOnClickListener(clickB)
        tvChoiceBTag.setOnClickListener(clickB)
        tvChoiceBIcon.setOnClickListener(clickB)

        val clickC = View.OnClickListener { handleAnswerSelection(2) }
        btnChoiceC.setOnClickListener(clickC)
        tvChoiceCName.setOnClickListener(clickC)
        tvChoiceCTag.setOnClickListener(clickC)
        tvChoiceCIcon.setOnClickListener(clickC)

        val clickD = View.OnClickListener { handleAnswerSelection(3) }
        btnChoiceD.setOnClickListener(clickD)
        tvChoiceDName.setOnClickListener(clickD)
        tvChoiceDTag.setOnClickListener(clickD)
        tvChoiceDIcon.setOnClickListener(clickD)

        btnPlayerShareCard.setOnClickListener { handleShareMessenger() }
        btnPlayerClaimBranch.setOnClickListener { handleClaimBranch() }
        btnViewMyTreeNode.setOnClickListener {
            val treeId = currentSession?.clanId.orEmpty()
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
            finish()
        }
        btnPlayerExit.setOnClickListener { disconnectAndReturnHome() }
    }

    private fun connectToRoom() {
        if (roomPin.isEmpty()) {
            KinClashArcadeToast.show(this, "Invalid Room PIN", "Please check the 4-digit code and try again", "⚠️")
            window.decorView.postDelayed({ finish() }, 1500L)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("trivia_sessions").document(roomPin)

        val initialPlayer = TriviaPlayer(
            userId = userId,
            nickname = nickname,
            score = 0,
            streak = 1,
            currentAnswerIndex = -1,
            hasSubmittedCurrentQuestion = false,
            isConnected = true
        )

        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                KinClashArcadeToast.show(this, "Room Not Found", "Room #$roomPin was not found or has expired", "⚠️")
                window.decorView.postDelayed({ finish() }, 1500L)
                return@addOnSuccessListener
            }

            docRef.update("players.$userId", initialPlayer.toMap())
                .addOnSuccessListener {
                    listenToSession()
                }
                .addOnFailureListener { e ->
                    KinClashArcadeToast.show(this, "Connection Failed", e.localizedMessage.orEmpty(), "⚠️")
                    window.decorView.postDelayed({ finish() }, 1500L)
                }
        }.addOnFailureListener { e ->
            KinClashArcadeToast.show(this, "Network Error", e.localizedMessage.orEmpty(), "⚠️")
            window.decorView.postDelayed({ finish() }, 1500L)
        }
    }

    private fun listenToSession() {
        val db = FirebaseFirestore.getInstance()
        sessionListener = db.collection("trivia_sessions").document(roomPin)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val session = snapshot.toObject(ClanTriviaSession::class.java)
                    if (session != null) {
                        currentSession = session
                        renderSession(session)
                    }
                } else {
                    KinClashArcadeToast.show(this, "Room Closed", "Room was closed by the host", "🚪")
                    window.decorView.postDelayed({ disconnectAndReturnHome() }, 1200L)
                }
            }
    }

    private fun renderSession(session: ClanTriviaSession) {
        if (!session.players.containsKey(userId)) {
            // Re-sync player entry into room if temporarily missing
            if (session.status != ClanTriviaSession.STATUS_COMPLETED) {
                val db = FirebaseFirestore.getInstance()
                val initialPlayer = TriviaPlayer(
                    userId = userId,
                    nickname = nickname,
                    score = currentScore,
                    streak = currentStreak,
                    currentAnswerIndex = -1,
                    hasSubmittedCurrentQuestion = false,
                    isConnected = true
                )
                db.collection("trivia_sessions").document(roomPin)
                    .update("players.$userId", initialPlayer.toMap())
            }
            return
        }

        when (session.status) {
            ClanTriviaSession.STATUS_LOBBY -> renderLobby(session)
            ClanTriviaSession.STATUS_IN_PROGRESS, ClanTriviaSession.STATUS_PAUSED -> renderGame(session)
            ClanTriviaSession.STATUS_COMPLETED -> renderCompleted(session)
        }
    }

    private fun renderLobby(session: ClanTriviaSession) {
        layoutPlayerLobby.visibility = View.VISIBLE
        layoutPlayerGame.visibility = View.GONE
        layoutPlayerCompleted.visibility = View.GONE
        layoutPlayerPausedOverlay.visibility = View.GONE

        tvLobbySubtext.text = "Connected as $nickname\nWaiting for ${session.hostName} to start the game..."
    }

    private fun renderGame(session: ClanTriviaSession) {
        layoutPlayerLobby.visibility = View.GONE
        layoutPlayerGame.visibility = View.VISIBLE
        layoutPlayerCompleted.visibility = View.GONE

        if (session.isPaused) {
            layoutPlayerPausedOverlay.visibility = View.VISIBLE
            questionTimer?.cancel()
        } else {
            layoutPlayerPausedOverlay.visibility = View.GONE
        }

        val totalQuestions = session.questions.size
        val currentIndex = session.currentQuestionIndex.coerceIn(0, (totalQuestions - 1).coerceAtLeast(0))
        val question = session.questions.getOrNull(currentIndex)

        // New question arrived!
        if (currentIndex != lastRenderedQuestionIndex) {
            lastRenderedQuestionIndex = currentIndex
            hasAnsweredCurrentQuestion = false

            resetArcadeButtons()
            cardPlayerResultFeedback.visibility = View.GONE

            if (question != null) {
                renderQuestionDetails(question, currentIndex, totalQuestions)
                startCountdownTimer()
            }
        }

        // Live player submission count update
        val totalPlayers = session.players.size
        val answeredPlayers = session.players.values.count { it.hasSubmittedCurrentQuestion }
        if (!hasAnsweredCurrentQuestion) {
            tvPlayerReadyCount.text = "$answeredPlayers of $totalPlayers Ready"
        }
    }

    private fun renderQuestionDetails(question: TriviaQuestion, index: Int, total: Int) {
        android.util.Log.d("KinClashPlayer", "renderQuestionDetails: index=$index, correct=${question.correctOptionIndex}, prompt=${question.prompt}")
        tvPlayerQuestionCategory.text = if (question.category.isNotEmpty()) {
            "👑 ${question.category.uppercase()}"
        } else {
            "👑 CLAN PATRIARCH"
        }
        tvPlayerQuestionCounter.text = "Question ${index + 1} of $total"
        tvPlayerQuestionPrompt.text = question.prompt

        val options = question.options
        tvChoiceAName.text = options.getOrElse(0) { "Option A" }
        tvChoiceBName.text = options.getOrElse(1) { "Option B" }
        tvChoiceCName.text = options.getOrElse(2) { "Option C" }
        tvChoiceDName.text = options.getOrElse(3) { "Option D" }

        tvChoiceATag.text = "ROOT A"
        tvChoiceBTag.text = "ROOT B"
        tvChoiceCTag.text = "ROOT C"
        tvChoiceDTag.text = "ROOT D"

        tvChoiceAIcon.text = "🍂"
        tvChoiceBIcon.text = "🍃"
        tvChoiceCIcon.text = "🌸"
        tvChoiceDIcon.text = "🪵"

        if (question.isSilhouetteMode && question.fallbackClues.isNotEmpty()) {
            tvPlayerSilhouetteClues.text = "🔍 Clue: ${question.fallbackClues.joinToString(" • ")}"
            tvPlayerSilhouetteClues.visibility = View.VISIBLE
        } else {
            tvPlayerSilhouetteClues.visibility = View.GONE
        }

        tvPlayerProofText.text = question.proofFact.ifEmpty { "Verified KinTrace ancestral record." }
    }

    private fun startCountdownTimer() {
        questionTimer?.cancel()
        remainingSeconds = 20
        tvPlayerCountdown.text = "20"
        tvPlayerCountdown.setTextColor(ContextCompat.getColor(this, R.color.arcade_yellow))

        questionTimer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt()
                tvPlayerCountdown.text = remainingSeconds.toString()

                if (remainingSeconds <= 5) {
                    tvPlayerCountdown.setTextColor(ContextCompat.getColor(this@ReunionPlayerActivity, R.color.arcade_red))
                }
            }

            override fun onFinish() {
                remainingSeconds = 0
                tvPlayerCountdown.text = "0"
                if (!hasAnsweredCurrentQuestion) {
                    handleTimeExpired()
                }
            }
        }.start()
    }

    /**
     * Resets all 4 arcade buttons to default 3D state, clears rotation, trapdoor drops,
     * restores opacity, and re-enables tapping for the new question.
     */
    private fun resetArcadeButtons() {
        val buttons = listOf(btnChoiceA, btnChoiceB, btnChoiceC, btnChoiceD)
        val lockBadges = listOf(badgeChoiceALock, badgeChoiceBLock, badgeChoiceCLock, badgeChoiceDLock)
        val nameLabels = listOf(tvChoiceAName, tvChoiceBName, tvChoiceCName, tvChoiceDName)
        val tagLabels = listOf(tvChoiceATag, tvChoiceBTag, tvChoiceCTag, tvChoiceDTag)
        val icons = listOf(tvChoiceAIcon, tvChoiceBIcon, tvChoiceCIcon, tvChoiceDIcon)

        for (i in 0..3) {
            val btn = buttons[i]
            btn.animate().cancel()
            btn.translationX = 0f
            btn.translationY = 0f
            btn.rotationX = 0f
            btn.scaleX = 1.0f
            btn.scaleY = 1.0f
            btn.alpha = 1.0f
            btn.isEnabled = true
            btn.setBackgroundResource(R.drawable.bg_arcade_btn_default)

            nameLabels[i].isEnabled = true
            tagLabels[i].isEnabled = true
            icons[i].isEnabled = true

            lockBadges[i].visibility = View.GONE
            nameLabels[i].setTextColor(Color.WHITE)
            tagLabels[i].setTextColor(Color.parseColor("#99FFFFFF"))
        }

        vRedVignette.animate().cancel()
        vRedVignette.alpha = 0f
        vRedVignette.visibility = View.GONE

        layoutRootShakeContainer.animate().cancel()
        layoutRootShakeContainer.translationX = 0f

        tvPlayerStatusTitle.text = "Pick your answer!"
        tvPlayerSubmissionsStatus.text = "Tap any choice to lock & trigger animation."
        tvPlayerReadyCount.text = "Ready"
    }

    /**
     * Strict Immediate Answer Lock-In & Tap Animation Orchestration:
     * - Immediate Lockout: Freezes all buttons instantly so no double-tap or switching is possible.
     * - Correct Tap: Mario 3D Coin Block Jump, 8-bit coin audio, score odometer punch, trapdoor drop on others.
     * - Wrong Tap: Violent screen tremor, red vignette flash, 8-bit thud drop audio, tapped card falls into void.
     */
    private fun handleAnswerSelection(selectedIndex: Int) {
        android.util.Log.d("KinClashPlayer", "handleAnswerSelection: tapped=$selectedIndex, hasAnswered=$hasAnsweredCurrentQuestion, sessionNull=${currentSession == null}")
        if (hasAnsweredCurrentQuestion) return
        hasAnsweredCurrentQuestion = true
        questionTimer?.cancel()

        val session = currentSession
        if (session == null) {
            android.util.Log.e("KinClashPlayer", "handleAnswerSelection: currentSession is NULL!")
            return
        }
        val question = session.questions.getOrNull(session.currentQuestionIndex)
        if (question == null) {
            android.util.Log.e("KinClashPlayer", "handleAnswerSelection: question at ${session.currentQuestionIndex} is NULL! size=${session.questions.size}")
            return
        }
        android.util.Log.d("KinClashPlayer", "handleAnswerSelection: processing answer for ${question.prompt}")

        val buttons = listOf(btnChoiceA, btnChoiceB, btnChoiceC, btnChoiceD)
        val lockBadges = listOf(badgeChoiceALock, badgeChoiceBLock, badgeChoiceCLock, badgeChoiceDLock)
        val nameLabels = listOf(tvChoiceAName, tvChoiceBName, tvChoiceCName, tvChoiceDName)
        val tagLabels = listOf(tvChoiceATag, tvChoiceBTag, tvChoiceCTag, tvChoiceDTag)
        val icons = listOf(tvChoiceAIcon, tvChoiceBIcon, tvChoiceCIcon, tvChoiceDIcon)

        // 1. Strict Immediate Lockout: Freeze all buttons & child views instantly
        for (i in 0..3) {
            buttons[i].isEnabled = false
            nameLabels[i].isEnabled = false
            tagLabels[i].isEnabled = false
            icons[i].isEnabled = false
        }

        // Show lock stamp on the tapped choice
        lockBadges[selectedIndex].visibility = View.VISIBLE

        val isCorrect = selectedIndex == question.correctOptionIndex

        if (isCorrect) {
            // === CORRECT ANSWER ANIMATION (Mario Coin Block Jump) ===
            ClanTriviaSoundEngine.playCoinSound()

            val selectedBtn = buttons[selectedIndex]
            selectedBtn.setBackgroundResource(R.drawable.bg_arcade_btn_correct)
            nameLabels[selectedIndex].setTextColor(Color.BLACK)
            tagLabels[selectedIndex].setTextColor(Color.BLACK)
            lockBadges[selectedIndex].bringToFront()

            // Mario Coin Block Jump: translate -26dp with spring bounce
            selectedBtn.animate()
                .translationY(-26f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setInterpolator(OvershootInterpolator(2.2f))
                .setDuration(450)
                .start()

            // Other 3 buttons plummet down trapdoors into the abyss
            for (i in 0..3) {
                if (i != selectedIndex) {
                    buttons[i].animate()
                        .rotationX(85f)
                        .translationY(700f)
                        .scaleX(0.6f)
                        .scaleY(0.6f)
                        .alpha(0f)
                        .setInterpolator(AccelerateInterpolator(1.8f))
                        .setDuration(650)
                        .start()
                }
            }

            // Points calculation with streak multiplier
            val basePoints = 350
            val speedBonus = remainingSeconds * 10
            currentStreak += 1

            val multiplier = when {
                currentStreak >= 3 -> 3
                currentStreak >= 2 -> 2
                else -> 1
            }
            val awardedPoints = (basePoints + speedBonus) * multiplier
            currentScore += awardedPoints

            // Score odometer punch animation
            tvPlayerScoreBadge.text = "$currentScore PTS"
            tvPlayerScoreBadge.animate()
                .scaleX(1.35f)
                .scaleY(1.35f)
                .setDuration(160)
                .withEndAction {
                    tvPlayerScoreBadge.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(160)
                        .start()
                }.start()

            updateScoreHud()

            // Update status HUD
            val chosenName = nameLabels[selectedIndex].text.toString()
            tvPlayerStatusTitle.text = "🎉 CORRECT! $chosenName"
            tvPlayerSubmissionsStatus.text = "Coin Block Jump triggered! Others dropped!"
            tvPlayerReadyCount.text = "VERIFIED"

            val streakMsg = if (multiplier > 1) " (🔥 ${multiplier}X COMBO!)" else ""
            tvPlayerResultBanner.text = "🎉 CORRECT! +$awardedPoints PTS$streakMsg"
            tvPlayerResultBanner.setTextColor(ContextCompat.getColor(this, R.color.arcade_yellow))
            cardPlayerResultFeedback.visibility = View.VISIBLE

        } else {
            // === WRONG ANSWER ANIMATION (Violent Tremor + Trapdoor Plummet) ===
            ClanTriviaSoundEngine.playWrongSound()

            val selectedBtn = buttons[selectedIndex]
            selectedBtn.setBackgroundResource(R.drawable.bg_arcade_btn_wrong)

            // 1. Violent Screen Tremor
            triggerScreenTremor()

            // 2. Red Vignette Screen Flash
            triggerRedVignetteFlash()

            // 3. Selected wrong card trembles, then plummets down trapdoor off-screen
            selectedBtn.animate().translationXBy(-16f).setDuration(60).withEndAction {
                selectedBtn.animate().translationXBy(32f).setDuration(60).withEndAction {
                    selectedBtn.animate().translationXBy(-32f).setDuration(60).withEndAction {
                        selectedBtn.animate().translationX(0f).setDuration(60).withEndAction {
                            // After brief pause, fall through trapdoor
                            selectedBtn.postDelayed({
                                selectedBtn.animate()
                                    .rotationX(85f)
                                    .translationY(700f)
                                    .scaleX(0.6f)
                                    .scaleY(0.6f)
                                    .alpha(0f)
                                    .setInterpolator(AccelerateInterpolator(1.8f))
                                    .setDuration(600)
                                    .start()
                            }, 200)
                        }.start()
                    }.start()
                }.start()
            }.start()

            // Other 3 choices dim
            for (i in 0..3) {
                if (i != selectedIndex) {
                    buttons[i].animate().alpha(0.25f).scaleX(0.94f).scaleY(0.94f).setDuration(300).start()
                }
            }

            currentStreak = 1
            updateScoreHud()

            tvPlayerStatusTitle.text = "❌ WRONG CHOICE! Trapdoor Drop!"
            tvPlayerSubmissionsStatus.text = "Your card plummeted into the void!"
            tvPlayerReadyCount.text = "MISSED"

            tvPlayerResultBanner.text = "❌ INCORRECT! Keep going!"
            tvPlayerResultBanner.setTextColor(ContextCompat.getColor(this, R.color.arcade_red))
            cardPlayerResultFeedback.visibility = View.VISIBLE
        }

        // Sync answer state to Cloud Firestore
        val db = FirebaseFirestore.getInstance()
        db.collection("trivia_sessions").document(roomPin)
            .update(
                mapOf(
                    "players.$userId.hasSubmittedCurrentQuestion" to true,
                    "players.$userId.currentAnswerIndex" to selectedIndex,
                    "players.$userId.score" to currentScore,
                    "players.$userId.streak" to currentStreak
                )
            )
    }

    private fun triggerScreenTremor() {
        layoutRootShakeContainer.animate().translationXBy(-18f).setDuration(45).withEndAction {
            layoutRootShakeContainer.animate().translationXBy(36f).setDuration(45).withEndAction {
                layoutRootShakeContainer.animate().translationXBy(-32f).setDuration(45).withEndAction {
                    layoutRootShakeContainer.animate().translationXBy(28f).setDuration(45).withEndAction {
                        layoutRootShakeContainer.animate().translationX(0f).setDuration(45).start()
                    }.start()
                }.start()
            }.start()
        }.start()
    }

    private fun triggerRedVignetteFlash() {
        vRedVignette.visibility = View.VISIBLE
        vRedVignette.alpha = 0f
        vRedVignette.animate().alpha(0.85f).setDuration(100).withEndAction {
            vRedVignette.animate().alpha(0f).setDuration(350).withEndAction {
                vRedVignette.visibility = View.GONE
            }.start()
        }.start()
    }

    private fun handleTimeExpired() {
        if (hasAnsweredCurrentQuestion) return
        hasAnsweredCurrentQuestion = true

        val buttons = listOf(btnChoiceA, btnChoiceB, btnChoiceC, btnChoiceD)
        val nameLabels = listOf(tvChoiceAName, tvChoiceBName, tvChoiceCName, tvChoiceDName)
        val tagLabels = listOf(tvChoiceATag, tvChoiceBTag, tvChoiceCTag, tvChoiceDTag)
        val icons = listOf(tvChoiceAIcon, tvChoiceBIcon, tvChoiceCIcon, tvChoiceDIcon)

        for (i in 0..3) {
            buttons[i].isEnabled = false
            nameLabels[i].isEnabled = false
            tagLabels[i].isEnabled = false
            icons[i].isEnabled = false
            buttons[i].animate().alpha(0.35f).setDuration(250).start()
        }

        currentStreak = 1
        tvPlayerStatusTitle.text = "⏱ TIME EXPIRED!"
        tvPlayerSubmissionsStatus.text = "No answer was locked in."
        tvPlayerReadyCount.text = "EXPIRED"

        tvPlayerResultBanner.text = "⏱ TIME EXPIRED!"
        tvPlayerResultBanner.setTextColor(ContextCompat.getColor(this, R.color.arcade_yellow))
        cardPlayerResultFeedback.visibility = View.VISIBLE
        updateScoreHud()

        val db = FirebaseFirestore.getInstance()
        db.collection("trivia_sessions").document(roomPin)
            .update(
                mapOf(
                    "players.$userId.hasSubmittedCurrentQuestion" to true,
                    "players.$userId.currentAnswerIndex" to -1,
                    "players.$userId.score" to currentScore,
                    "players.$userId.streak" to currentStreak
                )
            )
    }

    private fun updateScoreHud() {
        tvPlayerStreakBadge.text = "🔥 ${currentStreak}X COMBO"
        tvPlayerScoreBadge.text = "$currentScore PTS"
    }

    private fun renderCompleted(session: ClanTriviaSession) {
        layoutPlayerLobby.visibility = View.GONE
        layoutPlayerGame.visibility = View.GONE
        layoutPlayerCompleted.visibility = View.VISIBLE
        layoutPlayerPausedOverlay.visibility = View.GONE

        val sorted = session.players.values.sortedByDescending { it.score }
        val myRank = sorted.indexOfFirst { it.userId == userId } + 1
        val total = sorted.size

        val myPlayer = session.players[userId]
        val displayScore = myPlayer?.score ?: currentScore
        if (displayScore > currentScore) {
            currentScore = displayScore
            updateScoreHud()
        }

        val medal = when (myRank) {
            1 -> "🥇 1st Place • Clan Historian"
            2 -> "🥈 2nd Place"
            3 -> "🥉 3rd Place"
            else -> "#$myRank of $total"
        }
        tvPlayerFinalScore.text = "$medal • $displayScore Total Points"

        // 3-pillar podium
        val rank1 = sorted.getOrNull(0)
        val rank2 = sorted.getOrNull(1)
        val rank3 = sorted.getOrNull(2)

        tvPodiumRank1Name.text = rank1?.nickname ?: "No Record"
        tvPodiumRank1Score.text = "${rank1?.score ?: 0} pts"

        tvPodiumRank2Name.text = rank2?.nickname ?: "-"
        tvPodiumRank2Score.text = "${rank2?.score ?: 0} pts"

        tvPodiumRank3Name.text = rank3?.nickname ?: "-"
        tvPodiumRank3Score.text = "${rank3?.score ?: 0} pts"

        val myClaim = session.pendingClaims.find { it.userId == userId }
        when (myClaim?.status) {
            BranchClaimRequest.STATUS_APPROVED -> {
                btnPlayerClaimBranch.visibility = View.GONE
                tvPlayerClaimPending.visibility = View.GONE
                layoutBranchApproved.visibility = View.VISIBLE
            }
            BranchClaimRequest.STATUS_PENDING -> {
                btnPlayerClaimBranch.visibility = View.GONE
                tvPlayerClaimPending.visibility = View.VISIBLE
                layoutBranchApproved.visibility = View.GONE
            }
            else -> {
                btnPlayerClaimBranch.visibility = View.VISIBLE
                tvPlayerClaimPending.visibility = View.GONE
                layoutBranchApproved.visibility = View.GONE
            }
        }
    }

    private fun handleShareMessenger() {
        val session = currentSession ?: return
        val sorted = session.players.values.sortedByDescending { it.score }
        val winner = sorted.firstOrNull()

        val shareText = buildString {
            append("🏆 KinClash Clan Party Trivia Results!\n")
            if (winner != null) {
                append("🥇 1st Place: ${winner.nickname} (${winner.score} pts)\n")
            }
            append("\nMy Score: $currentScore pts ($nickname)\n")
            append("Think you know our family history better? Play KinClash with us on KinTrace!")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "KinClash Trivia Results")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Results via"))
    }

    private fun handleClaimBranch() {
        val session = currentSession ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_claim_branch, null)
        val etParent = dialogView.findViewById<EditText>(R.id.etClaimParentName)
        val tvError = dialogView.findViewById<TextView>(R.id.tvClaimError)
        val btnClose = dialogView.findViewById<TextView>(R.id.btnClaimClose)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnClaimCancel)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnClaimSubmit)

        val dialog = Dialog(this).apply {
            setContentView(dialogView)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val parentInput = etParent.text.toString().trim()
            if (parentInput.isEmpty()) {
                tvError.text = "Please enter your parents or clan connection."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val claim = BranchClaimRequest(
                requestId = "claim_${System.currentTimeMillis()}",
                userId = userId,
                nickname = nickname,
                targetParentId = "",
                targetParentName = parentInput,
                status = BranchClaimRequest.STATUS_PENDING,
                timestamp = System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
                .update("pendingClaims", FieldValue.arrayUnion(claim.toMap()))
                .addOnSuccessListener {
                    dialog.dismiss()
                    tvPlayerClaimPending.visibility = View.VISIBLE
                    btnPlayerClaimBranch.visibility = View.GONE
                    KinClashArcadeToast.show(
                        activity = this,
                        title = "Branch Claim Sent!",
                        subtitle = "Host will review your connection to the tree",
                        icon = "🌿"
                    )
                }
                .addOnFailureListener { e ->
                    KinClashArcadeToast.show(
                        activity = this,
                        title = "Failed to Send Request",
                        subtitle = e.localizedMessage.orEmpty(),
                        icon = "⚠️"
                    )
                }
        }

        dialog.show()
    }

    private fun handleExit() {
        if (currentSession?.status == ClanTriviaSession.STATUS_COMPLETED || currentSession?.status == ClanTriviaSession.STATUS_LOBBY) {
            disconnectAndReturnHome()
            return
        }

        KinClashArcadeConfirmDialog.show(
            fragmentManager = supportFragmentManager,
            title = "Leave KinClash Game?",
            message = "Are you sure you want to disconnect from Room #$roomPin?",
            confirmText = "Leave Game",
            icon = "🚪"
        ) {
            disconnectAndReturnHome()
        }
    }

    private fun disconnectAndReturnHome() {
        questionTimer?.cancel()
        sessionListener?.remove()

        if (roomPin.isNotEmpty() && userId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
                .update("players.$userId.isConnected", false)
        }

        val treeId = currentSession?.clanId.orEmpty()
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleExit()
    }
}
