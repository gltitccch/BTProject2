package com.example.btproject2.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.BranchClaimRequest
import com.example.btproject2.models.ClanTriviaSession
import com.example.btproject2.models.TriviaPlayer
import com.example.btproject2.models.TriviaQuestion
import com.example.btproject2.ui.dialogs.KinClashArcadeConfirmDialog
import com.example.btproject2.utils.ClanTriviaEngine
import com.example.btproject2.utils.KinClashArcadeToast
import com.example.btproject2.utils.TreePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * ReunionHostActivity (KinClash Host Command Center):
 * Real-time synchronous live game-master dashboard for the family host.
 * Controls room PIN generation, live player lobby roster, procedural question deck,
 * pause/debate timekeeper, live submission progress, and branch claim approvals.
 */
class ReunionHostActivity : AppCompatActivity() {

    private var roomPin: String = ""
    private var activeTreeId: String = ""
    private var currentSession: ClanTriviaSession? = null
    private var sessionListener: ListenerRegistration? = null
    private var loadedTreeMembers: List<com.example.btproject2.models.Person> = emptyList()

    // Top Bar
    private lateinit var btnHostBack: TextView
    private lateinit var tvHostRoomStatus: TextView
    private lateinit var layoutHostContentContainer: LinearLayout

    // PIN Card
    private lateinit var cardHostPin: LinearLayout
    private lateinit var tvHostPin: TextView
    private lateinit var btnHostCopyPin: Button
    private lateinit var btnHostSharePin: Button

    // Lobby Section
    private lateinit var layoutHostLobbySection: LinearLayout
    private lateinit var tvHostPlayerCount: TextView
    private lateinit var tvHostEmptyPlayers: TextView
    private lateinit var layoutHostPlayersList: LinearLayout
    private lateinit var tvHostDeckBadge: TextView
    private lateinit var etCustomPrompt: EditText
    private lateinit var etCustomChoiceA: EditText
    private lateinit var etCustomChoiceB: EditText
    private lateinit var etCustomChoiceC: EditText
    private lateinit var etCustomChoiceD: EditText
    private lateinit var layoutChoiceRowA: LinearLayout
    private lateinit var layoutChoiceRowB: LinearLayout
    private lateinit var layoutChoiceRowC: LinearLayout
    private lateinit var layoutChoiceRowD: LinearLayout
    private lateinit var btnMarkCorrectA: TextView
    private lateinit var btnMarkCorrectB: TextView
    private lateinit var btnMarkCorrectC: TextView
    private lateinit var btnMarkCorrectD: TextView
    private lateinit var tvChoiceBadgeA: TextView
    private lateinit var tvChoiceBadgeB: TextView
    private lateinit var tvChoiceBadgeC: TextView
    private lateinit var tvChoiceBadgeD: TextView
    private var selectedCorrectOptionIndex: Int = 0
    private lateinit var btnHostAddJoke: Button
    private lateinit var btnHostStartGame: Button

    // Match Question Count Selector
    private var desiredQuestionCount: Int = 10
    private lateinit var tvHostSelectedCountBadge: TextView
    private lateinit var chipCount5: TextView
    private lateinit var chipCount10: TextView
    private lateinit var chipCount15: TextView
    private lateinit var chipCount20: TextView
    private lateinit var etCustomQuestionCount: EditText
    private lateinit var tvRngEngineStatus: TextView

    // In-Progress Section
    private lateinit var layoutHostInProgressSection: LinearLayout
    private lateinit var tvHostQuestionHeader: TextView
    private lateinit var tvHostTimer: TextView
    private lateinit var tvHostQuestionPrompt: TextView
    private lateinit var tvHostOptA: TextView
    private lateinit var tvHostOptB: TextView
    private lateinit var tvHostOptC: TextView
    private lateinit var tvHostOptD: TextView
    private lateinit var tvHostAnswersStatus: TextView
    private lateinit var tvHostProofText: TextView
    private lateinit var btnHostPauseResume: Button
    private lateinit var btnHostNextQuestion: Button

    // Completed Section
    private lateinit var layoutHostCompletedSection: LinearLayout
    private lateinit var tvHostWinnerSummary: TextView
    private lateinit var tvHostClaimsHint: TextView
    private lateinit var layoutHostClaimsList: LinearLayout
    private lateinit var btnHostCloseRoom: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reunion_host)

        initViews()
        initSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionListener?.remove()
        sessionListener = null
    }

    private fun initViews() {
        btnHostBack = findViewById(R.id.btnHostBack)
        tvHostRoomStatus = findViewById(R.id.tvHostRoomStatus)
        layoutHostContentContainer = findViewById(R.id.layoutHostContentContainer)
        cardHostPin = findViewById(R.id.cardHostPin)

        tvHostPin = findViewById(R.id.tvHostPin)
        btnHostCopyPin = findViewById(R.id.btnHostCopyPin)
        btnHostSharePin = findViewById(R.id.btnHostSharePin)

        layoutHostLobbySection = findViewById(R.id.layoutHostLobbySection)
        tvHostPlayerCount = findViewById(R.id.tvHostPlayerCount)
        tvHostEmptyPlayers = findViewById(R.id.tvHostEmptyPlayers)
        layoutHostPlayersList = findViewById(R.id.layoutHostPlayersList)
        tvHostDeckBadge = findViewById(R.id.tvHostDeckBadge)
        etCustomPrompt = findViewById(R.id.etCustomPrompt)
        etCustomChoiceA = findViewById(R.id.etCustomChoiceA)
        etCustomChoiceB = findViewById(R.id.etCustomChoiceB)
        etCustomChoiceC = findViewById(R.id.etCustomChoiceC)
        etCustomChoiceD = findViewById(R.id.etCustomChoiceD)
        layoutChoiceRowA = findViewById(R.id.layoutChoiceRowA)
        layoutChoiceRowB = findViewById(R.id.layoutChoiceRowB)
        layoutChoiceRowC = findViewById(R.id.layoutChoiceRowC)
        layoutChoiceRowD = findViewById(R.id.layoutChoiceRowD)
        btnMarkCorrectA = findViewById(R.id.btnMarkCorrectA)
        btnMarkCorrectB = findViewById(R.id.btnMarkCorrectB)
        btnMarkCorrectC = findViewById(R.id.btnMarkCorrectC)
        btnMarkCorrectD = findViewById(R.id.btnMarkCorrectD)
        tvChoiceBadgeA = findViewById(R.id.tvChoiceBadgeA)
        tvChoiceBadgeB = findViewById(R.id.tvChoiceBadgeB)
        tvChoiceBadgeC = findViewById(R.id.tvChoiceBadgeC)
        tvChoiceBadgeD = findViewById(R.id.tvChoiceBadgeD)

        btnMarkCorrectA.setOnClickListener { selectCorrectChoice(0) }
        btnMarkCorrectB.setOnClickListener { selectCorrectChoice(1) }
        btnMarkCorrectC.setOnClickListener { selectCorrectChoice(2) }
        btnMarkCorrectD.setOnClickListener { selectCorrectChoice(3) }
        selectCorrectChoice(0)

        btnHostAddJoke = findViewById(R.id.btnHostAddJoke)
        btnHostStartGame = findViewById(R.id.btnHostStartGame)

        layoutHostInProgressSection = findViewById(R.id.layoutHostInProgressSection)
        tvHostQuestionHeader = findViewById(R.id.tvHostQuestionHeader)
        tvHostTimer = findViewById(R.id.tvHostTimer)
        tvHostQuestionPrompt = findViewById(R.id.tvHostQuestionPrompt)
        tvHostOptA = findViewById(R.id.tvHostOptA)
        tvHostOptB = findViewById(R.id.tvHostOptB)
        tvHostOptC = findViewById(R.id.tvHostOptC)
        tvHostOptD = findViewById(R.id.tvHostOptD)
        tvHostAnswersStatus = findViewById(R.id.tvHostAnswersStatus)
        tvHostProofText = findViewById(R.id.tvHostProofText)
        btnHostPauseResume = findViewById(R.id.btnHostPauseResume)
        btnHostNextQuestion = findViewById(R.id.btnHostNextQuestion)

        layoutHostCompletedSection = findViewById(R.id.layoutHostCompletedSection)
        tvHostWinnerSummary = findViewById(R.id.tvHostWinnerSummary)
        tvHostClaimsHint = findViewById(R.id.tvHostClaimsHint)
        layoutHostClaimsList = findViewById(R.id.layoutHostClaimsList)
        btnHostCloseRoom = findViewById(R.id.btnHostCloseRoom)

        btnHostBack.setOnClickListener { handleExitRequest() }

        btnHostCopyPin.setOnClickListener {
            if (roomPin.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("KinClash PIN", roomPin)
                clipboard.setPrimaryClip(clip)
                KinClashArcadeToast.show(
                    activity = this,
                    title = "PIN Copied to Clipboard!",
                    subtitle = "Share PIN $roomPin with clan members",
                    icon = "📋"
                )
            }
        }

        btnHostSharePin.setOnClickListener {
            if (roomPin.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Join KinClash Live Party Trivia!")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "🎮 Join our live KinClash family trivia game!\nOpen KinTrace and enter Game PIN: $roomPin"
                    )
                }
                startActivity(Intent.createChooser(shareIntent, "Share KinClash PIN via"))
            }
        }

        btnHostAddJoke.setOnClickListener { handleAddCustomJoke() }
        btnHostStartGame.setOnClickListener { handleStartGame() }
        btnHostPauseResume.setOnClickListener { handleTogglePause() }
        btnHostNextQuestion.setOnClickListener { handleNextQuestion() }
        btnHostCloseRoom.setOnClickListener { returnHome() }

        setupQuestionCountSelector()
    }

    private fun setupQuestionCountSelector() {
        tvHostSelectedCountBadge = findViewById(R.id.tvHostSelectedCountBadge)
        chipCount5 = findViewById(R.id.chipCount5)
        chipCount10 = findViewById(R.id.chipCount10)
        chipCount15 = findViewById(R.id.chipCount15)
        chipCount20 = findViewById(R.id.chipCount20)
        etCustomQuestionCount = findViewById(R.id.etCustomQuestionCount)
        tvRngEngineStatus = findViewById(R.id.tvRngEngineStatus)

        chipCount5.setOnClickListener { selectQuestionCountChip(5) }
        chipCount10.setOnClickListener { selectQuestionCountChip(10) }
        chipCount15.setOnClickListener { selectQuestionCountChip(15) }
        chipCount20.setOnClickListener { selectQuestionCountChip(20) }

        etCustomQuestionCount.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val input = s?.toString()?.trim() ?: ""
                val count = input.toIntOrNull()
                if (count != null && count > 0) {
                    desiredQuestionCount = count.coerceIn(1, 100)
                    highlightActiveChip(count)
                    updateQuestionCountDisplay()
                }
            }
        })

        selectQuestionCountChip(10)
    }

    private fun selectQuestionCountChip(count: Int) {
        desiredQuestionCount = count
        if (etCustomQuestionCount.text.isNotEmpty()) {
            etCustomQuestionCount.setText("")
        }
        highlightActiveChip(count)
        updateQuestionCountDisplay()
    }

    private fun highlightActiveChip(count: Int) {
        val allChips = listOf(
            chipCount5 to 5,
            chipCount10 to 10,
            chipCount15 to 15,
            chipCount20 to 20
        )
        for ((chip, chipVal) in allChips) {
            if (chipVal == count) {
                chip.setBackgroundResource(R.drawable.bg_arcade_btn_correct)
                chip.setTextColor(Color.parseColor("#000000"))
            } else {
                chip.setBackgroundResource(R.drawable.bg_arcade_lock_stamp)
                chip.setTextColor(Color.parseColor("#FFFFFF"))
            }
        }
    }

    private fun updateQuestionCountDisplay() {
        tvHostSelectedCountBadge.text = "$desiredQuestionCount Questions"
        btnHostStartGame.text = "🚀  START KINCLASH GAME ($desiredQuestionCount QUESTIONS)"
        val jokesCount = currentSession?.questions?.count { it.isCustomHost } ?: 0
        val rngCount = (desiredQuestionCount - jokesCount).coerceAtLeast(0)
        tvRngEngineStatus.text = "Background RNG Active: Game will automatically draw $rngCount random questions from the 240+ bank + $jokesCount inside joke(s)."
        tvHostDeckBadge.text = "$desiredQuestionCount Questions"
    }

    private fun initSession() {
        activeTreeId = intent.getStringExtra("TREE_ID")
            ?: TreePreferences.getActiveTreeId(this).ifEmpty { "" }

        val passedPin = intent.getStringExtra("PIN").orEmpty().trim()
        roomPin = if (passedPin.isNotEmpty()) {
            passedPin
        } else {
            String.format("%04d", (1000..9999).random())
        }
        val formattedPin = "${roomPin[0]} ${roomPin[1]} ${roomPin[2]} ${roomPin[3]}"
        tvHostPin.text = formattedPin

        val auth = FirebaseAuth.getInstance()
        val currentUid = auth.currentUser?.uid ?: "host_${System.currentTimeMillis()}"
        val currentName = auth.currentUser?.displayName?.ifEmpty { null }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Clan Host"

        // Generate initial procedural question deck from tree members
        val cachedMembers = FirestoreHelper.getCachedPersons(activeTreeId) ?: emptyList()
        loadedTreeMembers = cachedMembers
        val initialQuestions = ClanTriviaEngine.generateQuestionDeck(cachedMembers, maxQuestions = desiredQuestionCount)

        val newSession = ClanTriviaSession(
            pin = roomPin,
            clanId = activeTreeId,
            hostId = currentUid,
            hostName = currentName,
            status = ClanTriviaSession.STATUS_LOBBY,
            currentQuestionIndex = 0,
            timerRemaining = 20,
            isPaused = false,
            questions = initialQuestions,
            players = emptyMap(),
            pendingClaims = emptyList(),
            createdAt = System.currentTimeMillis()
        )

        // Save session document to Firestore
        val db = FirebaseFirestore.getInstance()
        db.collection("trivia_sessions").document(roomPin)
            .set(newSession)
            .addOnSuccessListener {
                listenToSession()
                // Asynchronously fetch tree members if not in cache to guarantee personalized clan trivia
                if (cachedMembers.isEmpty() && activeTreeId.isNotBlank()) {
                    FirestoreHelper().getPersonsByTree(
                        activeTreeId,
                        onSuccess = { liveMembers ->
                            if (liveMembers.isNotEmpty()) {
                                loadedTreeMembers = liveMembers
                                val enrichedQuestions = ClanTriviaEngine.generateQuestionDeck(liveMembers, maxQuestions = desiredQuestionCount)
                                db.collection("trivia_sessions").document(roomPin)
                                    .update("questions", enrichedQuestions)
                            }
                        },
                        onFailure = { /* fallback questions deck already saved */ }
                    )
                }
            }
            .addOnFailureListener { e ->
                KinClashArcadeToast.show(
                    activity = this,
                    title = "Failed to Create Room",
                    subtitle = e.localizedMessage.orEmpty(),
                    icon = "⚠️"
                )
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
                }
            }
    }

    private fun renderSession(session: ClanTriviaSession) {
        when (session.status) {
            ClanTriviaSession.STATUS_LOBBY -> renderLobby(session)
            ClanTriviaSession.STATUS_IN_PROGRESS, ClanTriviaSession.STATUS_PAUSED -> renderInProgress(session)
            ClanTriviaSession.STATUS_COMPLETED -> renderCompleted(session)
        }
    }

    private fun renderLobby(session: ClanTriviaSession) {
        cardHostPin.visibility = View.VISIBLE
        layoutHostLobbySection.visibility = View.VISIBLE
        layoutHostInProgressSection.visibility = View.GONE
        layoutHostCompletedSection.visibility = View.GONE
        layoutHostContentContainer.gravity = android.view.Gravity.NO_GRAVITY

        tvHostRoomStatus.text = "🟢 LOBBY"
        tvHostRoomStatus.setBackgroundResource(R.drawable.bg_badge_lobby)
        tvHostRoomStatus.setTextColor(Color.WHITE)
        updateQuestionCountDisplay()

        val players = session.players.values.toList()
        tvHostPlayerCount.text = "${players.size} Ready"

        if (players.isEmpty()) {
            tvHostEmptyPlayers.visibility = View.VISIBLE
            layoutHostPlayersList.removeAllViews()
        } else {
            tvHostEmptyPlayers.visibility = View.GONE
            layoutHostPlayersList.removeAllViews()

            val inflater = LayoutInflater.from(this)
            for (player in players) {
                val itemView = inflater.inflate(R.layout.item_kinclash_player, layoutHostPlayersList, false)
                val tvAvatar = itemView.findViewById<TextView>(R.id.tvPlayerAvatar)
                val tvNickname = itemView.findViewById<TextView>(R.id.tvPlayerNickname)
                val tvStatus = itemView.findViewById<TextView>(R.id.tvPlayerStatus)
                val btnKick = itemView.findViewById<TextView>(R.id.btnKickPlayer)

                val initial = player.nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "👤"
                tvAvatar.text = initial
                tvNickname.text = player.nickname
                tvStatus.text = "🟢 Ready in Lobby"

                btnKick.setOnClickListener {
                    kickPlayer(player.userId)
                }

                layoutHostPlayersList.addView(itemView)
            }
        }
    }

    private fun renderInProgress(session: ClanTriviaSession) {
        cardHostPin.visibility = View.VISIBLE
        layoutHostLobbySection.visibility = View.GONE
        layoutHostInProgressSection.visibility = View.VISIBLE
        layoutHostCompletedSection.visibility = View.GONE
        layoutHostContentContainer.gravity = android.view.Gravity.NO_GRAVITY

        if (session.isPaused) {
            tvHostRoomStatus.text = "⏸ PAUSED"
            tvHostRoomStatus.setBackgroundResource(R.drawable.bg_badge_paused)
            tvHostRoomStatus.setTextColor(Color.parseColor("#07040D"))
            btnHostPauseResume.text = "▶ Resume Timer"
        } else {
            tvHostRoomStatus.text = "🎮 LIVE"
            tvHostRoomStatus.setBackgroundResource(R.drawable.bg_badge_live)
            tvHostRoomStatus.setTextColor(Color.WHITE)
            btnHostPauseResume.text = "⏸ Pause Debate"
        }

        val totalQuestions = session.questions.size
        val currentIndex = session.currentQuestionIndex.coerceIn(0, totalQuestions - 1)
        val question = session.questions.getOrNull(currentIndex)

        if (question != null) {
            tvHostQuestionHeader.text = "QUESTION ${currentIndex + 1} OF $totalQuestions • ${question.category}"
            tvHostQuestionPrompt.text = question.prompt

            // Highlight the correct answer with a green indicator for the host
            val options = question.options
            tvHostOptA.text = "A. ${options.getOrElse(0) { "" }}"
            tvHostOptB.text = "B. ${options.getOrElse(1) { "" }}"
            tvHostOptC.text = "C. ${options.getOrElse(2) { "" }}"
            tvHostOptD.text = "D. ${options.getOrElse(3) { "" }}"

            highlightCorrectOption(question.correctOptionIndex)

            // Calculate live submissions
            val totalPlayers = session.players.size
            val submittedPlayers = session.players.values.count { it.hasSubmittedCurrentQuestion }
            tvHostAnswersStatus.text = "👥 Answers: $submittedPlayers of $totalPlayers players submitted"

            // Tree Proof fact
            tvHostProofText.text = question.proofFact.ifEmpty { "Verified KinTrace ancestral record." }
        }

        // Check if last question
        if (currentIndex >= totalQuestions - 1) {
            btnHostNextQuestion.text = "🏆 Finish Game"
        } else {
            btnHostNextQuestion.text = "Next Question ⏭"
        }
    }

    private fun highlightCorrectOption(correctIndex: Int) {
        val views = listOf(tvHostOptA, tvHostOptB, tvHostOptC, tvHostOptD)
        for ((idx, view) in views.withIndex()) {
            if (idx == correctIndex) {
                view.setBackgroundResource(R.drawable.bg_arcade_btn_correct)
                view.setTextColor(Color.BLACK)
            } else {
                view.setBackgroundResource(R.drawable.bg_arcade_btn_default)
                view.setTextColor(Color.WHITE)
            }
        }
    }

    private fun renderCompleted(session: ClanTriviaSession) {
        cardHostPin.visibility = View.GONE
        layoutHostLobbySection.visibility = View.GONE
        layoutHostInProgressSection.visibility = View.GONE
        layoutHostCompletedSection.visibility = View.VISIBLE
        layoutHostContentContainer.gravity = android.view.Gravity.CENTER_VERTICAL

        tvHostRoomStatus.text = "🏆 COMPLETED"
        tvHostRoomStatus.setBackgroundResource(R.drawable.bg_badge_completed)
        tvHostRoomStatus.setTextColor(Color.parseColor("#07040D"))

        val sortedPlayers = session.players.values.sortedByDescending { it.score }
        val topPlayer = sortedPlayers.firstOrNull()
        if (topPlayer != null) {
            tvHostWinnerSummary.text = "Top Clan Historian: 🥇 ${topPlayer.nickname} (${topPlayer.score} pts)"
        } else {
            tvHostWinnerSummary.text = "No players completed this session."
        }

        // Render Branch Claim Requests
        val claims = session.pendingClaims
        if (claims.isEmpty()) {
            tvHostClaimsHint.visibility = View.VISIBLE
            layoutHostClaimsList.removeAllViews()
        } else {
            tvHostClaimsHint.visibility = View.GONE
            layoutHostClaimsList.removeAllViews()
            val inflater = LayoutInflater.from(this)
            for (claim in claims) {
                val claimView = inflater.inflate(R.layout.item_kinclash_player, layoutHostClaimsList, false)
                val tvAvatar = claimView.findViewById<TextView>(R.id.tvPlayerAvatar)
                val tvNickname = claimView.findViewById<TextView>(R.id.tvPlayerNickname)
                val tvStatus = claimView.findViewById<TextView>(R.id.tvPlayerStatus)
                val btnAction = claimView.findViewById<TextView>(R.id.btnKickPlayer)

                tvAvatar.text = "🌿"
                val connectionNote = if (claim.targetParentName.isNotBlank()) " • ${claim.targetParentName}" else ""
                tvNickname.text = "${claim.nickname}$connectionNote"

                if (claim.status == BranchClaimRequest.STATUS_APPROVED) {
                    tvStatus.text = "🟢 Approved & Recorded in Tree"
                    tvStatus.setTextColor(Color.parseColor("#FFE600"))
                    btnAction.visibility = View.GONE
                    claimView.setOnClickListener(null)
                } else {
                    tvStatus.text = "⏳ Pending Approval (Tap to Approve)"
                    tvStatus.setTextColor(Color.parseColor("#FFE600"))
                    btnAction.visibility = View.VISIBLE
                    btnAction.text = "✓"
                    btnAction.setTextColor(Color.BLACK)

                    claimView.setOnClickListener {
                        approveBranchClaim(claim)
                    }
                    btnAction.setOnClickListener {
                        approveBranchClaim(claim)
                    }
                }

                layoutHostClaimsList.addView(claimView)
            }
        }
    }

    private fun selectCorrectChoice(index: Int) {
        selectedCorrectOptionIndex = index.coerceIn(0, 3)
        val rows = listOf(layoutChoiceRowA, layoutChoiceRowB, layoutChoiceRowC, layoutChoiceRowD)
        val buttons = listOf(btnMarkCorrectA, btnMarkCorrectB, btnMarkCorrectC, btnMarkCorrectD)
        val badges = listOf(tvChoiceBadgeA, tvChoiceBadgeB, tvChoiceBadgeC, tvChoiceBadgeD)
        val inputs = listOf(etCustomChoiceA, etCustomChoiceB, etCustomChoiceC, etCustomChoiceD)

        for (i in 0..3) {
            if (i == selectedCorrectOptionIndex) {
                rows[i].setBackgroundResource(R.drawable.bg_arcade_btn_correct)
                buttons[i].text = "✓ Correct"
                buttons[i].setBackgroundResource(R.drawable.bg_arcade_pill_black)
                buttons[i].setTextColor(Color.parseColor("#FFE600"))
                badges[i].setBackgroundResource(R.drawable.bg_arcade_pill_black)
                badges[i].setTextColor(Color.parseColor("#FFE600"))
                inputs[i].setTextColor(Color.BLACK)
                inputs[i].setHintTextColor(Color.parseColor("#4A3A00"))
            } else {
                rows[i].setBackgroundResource(R.drawable.bg_arcade_input)
                buttons[i].text = "Set Correct"
                buttons[i].setBackgroundResource(R.drawable.bg_arcade_lock_stamp)
                buttons[i].setTextColor(Color.parseColor("#D1CAE6"))
                badges[i].setBackgroundResource(R.drawable.bg_arcade_letter_pill)
                badges[i].setTextColor(Color.parseColor("#FFE600"))
                inputs[i].setTextColor(Color.WHITE)
                inputs[i].setHintTextColor(Color.parseColor("#B3A1E6"))
            }
        }
    }

    private fun handleAddCustomJoke() {
        val prompt = etCustomPrompt.text.toString().trim()
        val choiceA = etCustomChoiceA.text.toString().trim()
        val choiceB = etCustomChoiceB.text.toString().trim()
        val choiceC = etCustomChoiceC.text.toString().trim()
        val choiceD = etCustomChoiceD.text.toString().trim()

        if (prompt.isEmpty() || choiceA.isEmpty() || choiceB.isEmpty() || choiceC.isEmpty() || choiceD.isEmpty()) {
            KinClashArcadeToast.show(
                activity = this,
                title = "Incomplete Inside Joke",
                subtitle = "Please enter prompt and all 4 choices",
                icon = "💡"
            )
            return
        }

        val customQ = TriviaQuestion(
            id = "custom_${System.currentTimeMillis()}",
            prompt = prompt,
            category = TriviaQuestion.CATEGORY_HOST_CUSTOM,
            options = listOf(choiceA, choiceB, choiceC, choiceD),
            correctOptionIndex = selectedCorrectOptionIndex,
            proofMemberId = "",
            proofFact = "Verified inside memory added by the room host.",
            photoUrl = "",
            fallbackClues = emptyList(),
            isSilhouetteMode = false,
            isCustomHost = true
        )

        val session = currentSession ?: return
        val updatedQuestions = session.questions.toMutableList().apply { add(customQ) }

        FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
            .update("questions", updatedQuestions)
            .addOnSuccessListener {
                KinClashArcadeToast.show(
                    activity = this,
                    title = "Inside Joke Added!",
                    subtitle = "Added to active game deck",
                    icon = "🎉"
                )
                etCustomPrompt.text.clear()
                etCustomChoiceA.text.clear()
                etCustomChoiceB.text.clear()
                etCustomChoiceC.text.clear()
                etCustomChoiceD.text.clear()
                selectCorrectChoice(0)
                updateQuestionCountDisplay()
            }
            .addOnFailureListener { e ->
                KinClashArcadeToast.show(
                    activity = this,
                    title = "Failed to Add Question",
                    subtitle = e.localizedMessage.orEmpty(),
                    icon = "⚠️"
                )
            }
    }

    private fun handleStartGame() {
        val session = currentSession ?: return
        val members = loadedTreeMembers.ifEmpty {
            FirestoreHelper.getCachedPersons(activeTreeId) ?: emptyList()
        }
        val customJokes = session.questions.filter { it.isCustomHost }

        // Assemble the finalized RNG deck matching the exact desired question count
        val finalizedDeck = ClanTriviaEngine.generateQuestionDeck(
            members = members,
            maxQuestions = desiredQuestionCount,
            customQuestions = customJokes
        )

        if (finalizedDeck.isEmpty()) {
            KinClashArcadeToast.show(
                activity = this,
                title = "Question Deck Empty!",
                subtitle = "Unable to assemble question deck",
                icon = "⚠️"
            )
            return
        }

        FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
            .update(
                mapOf(
                    "status" to ClanTriviaSession.STATUS_IN_PROGRESS,
                    "currentQuestionIndex" to 0,
                    "questions" to finalizedDeck,
                    "isPaused" to false
                )
            )
            .addOnSuccessListener {
                KinClashArcadeToast.show(
                    activity = this,
                    title = "KinClash Started!",
                    subtitle = "Question 1 of ${finalizedDeck.size} is now live!",
                    icon = "🚀"
                )
            }
    }

    private fun handleTogglePause() {
        val session = currentSession ?: return
        val newPausedState = !session.isPaused
        FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
            .update("isPaused", newPausedState)
            .addOnSuccessListener {
                val title = if (newPausedState) "Game Paused for Debate" else "Debate Resumed!"
                val sub = if (newPausedState) "Timer paused on all screens" else "Timer running again"
                val ic = if (newPausedState) "⏸" else "▶"
                KinClashArcadeToast.show(this, title, sub, ic)
            }
    }

    private fun handleNextQuestion() {
        val session = currentSession ?: return
        val nextIndex = session.currentQuestionIndex + 1

        if (nextIndex >= session.questions.size) {
            // Optimistically update and render completed immediately
            val updated = session.copy(status = ClanTriviaSession.STATUS_COMPLETED)
            currentSession = updated
            renderSession(updated)

            FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
                .update("status", ClanTriviaSession.STATUS_COMPLETED)
                .addOnSuccessListener {
                    KinClashArcadeToast.show(
                        activity = this,
                        title = "KinClash Completed!",
                        subtitle = "Podium revealed & scores finalized",
                        icon = "🏆"
                    )
                }
        } else {
            // Reset player submission flags for the new question
            val resetPlayers = session.players.mapValues { (_, player) ->
                player.copy(hasSubmittedCurrentQuestion = false, currentAnswerIndex = -1)
            }
            // Optimistically advance question immediately
            val updated = session.copy(currentQuestionIndex = nextIndex, players = resetPlayers, isPaused = false)
            currentSession = updated
            renderSession(updated)

            FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
                .update(
                    mapOf(
                        "currentQuestionIndex" to nextIndex,
                        "players" to resetPlayers,
                        "isPaused" to false
                    )
                )
                .addOnSuccessListener {
                    KinClashArcadeToast.show(
                        activity = this,
                        title = "Advancing Question",
                        subtitle = "Question ${nextIndex + 1} of ${session.questions.size} is live",
                        icon = "⏭"
                    )
                }
        }
    }

    private fun kickPlayer(userId: String) {
        FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
            .update("players.$userId", FieldValue.delete())
            .addOnSuccessListener {
                KinClashArcadeToast.show(
                    activity = this,
                    title = "Player Removed",
                    subtitle = "Disconnected player from lobby",
                    icon = "✕"
                )
            }
    }

    private fun approveBranchClaim(claim: BranchClaimRequest) {
        val session = currentSession ?: return
        val clanTreeId = session.clanId

        if (clanTreeId.isNotBlank()) {
            val newPerson = com.example.btproject2.models.Person(
                id = java.util.UUID.randomUUID().toString(),
                firstName = claim.nickname,
                lastName = "",
                gender = "Unspecified",
                treeId = clanTreeId,
                fatherId = claim.targetParentId.ifBlank { null },
                biography = "Added from KinClash Live Party Trivia. Clan Connection: ${claim.targetParentName.ifEmpty { "Family Member" }}."
            )
            FirestoreHelper().addPerson(newPerson, onSuccess = {
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyMemberAdded(newPerson, "KinClash")
                markClaimApprovedInFirestore(claim)
            }, onFailure = {
                markClaimApprovedInFirestore(claim)
            })
        } else {
            markClaimApprovedInFirestore(claim)
        }
    }

    private fun markClaimApprovedInFirestore(claim: BranchClaimRequest) {
        val session = currentSession ?: return
        val updatedClaims = session.pendingClaims.map {
            if (it.requestId == claim.requestId) it.copy(status = BranchClaimRequest.STATUS_APPROVED).toMap() else it.toMap()
        }
        FirebaseFirestore.getInstance().collection("trivia_sessions").document(roomPin)
            .update("pendingClaims", updatedClaims)
            .addOnSuccessListener {
                KinClashArcadeToast.show(
                    activity = this,
                    title = "Branch Claim Approved!",
                    subtitle = "${claim.nickname} added to tree",
                    icon = "🌿"
                )
            }
    }

    private fun handleExitRequest() {
        if (currentSession?.status == ClanTriviaSession.STATUS_IN_PROGRESS) {
            KinClashArcadeConfirmDialog.show(
                fragmentManager = supportFragmentManager,
                title = "Exit KinClash Host Room?",
                message = "A live trivia game is currently in progress. Exiting will pause the session for all connected family members.",
                confirmText = "Exit Room",
                icon = "⚠️"
            ) {
                returnHome()
            }
        } else {
            returnHome()
        }
    }

    private fun returnHome() {
        sessionListener?.remove()
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (activeTreeId.isNotEmpty()) putExtra("TREE_ID", activeTreeId)
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleExitRequest()
    }
}
