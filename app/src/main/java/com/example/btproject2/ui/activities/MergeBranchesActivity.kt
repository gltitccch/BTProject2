package com.example.btproject2.ui.activities

import com.example.btproject2.utils.KinTraceAlert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.ClanMergeRequest
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.MergeRollbackSnapshot
import com.example.btproject2.models.Person
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncEventListener
import com.example.btproject2.sync.TreeSyncEvent
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.setDarkAdapter
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

class MergeBranchesActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private val marriageEngine = MarriageValidationEngine(minimumSpouseAge = 18)
    private val mergeSimulationEngine = com.example.btproject2.engine.MergeSimulationEngine()

    private var activeTreeId = ""
    private var allPersonalTrees: List<FamilyTree> = emptyList()
    private var currentTreePersons: List<Person> = emptyList()
    private var isViewerOnly = false

    private lateinit var tvSubtitle: TextView
    private lateinit var tvMergeRoleBadge: TextView
    private lateinit var layoutViewerBanner: View
    private lateinit var btnOpenSynthesizeClanTree: Button
    private lateinit var tvClanTreeCount: TextView
    private lateinit var progressBarMerge: ProgressBar
    private lateinit var layoutEmptyClanState: View
    private lateinit var clanTreesContainer: LinearLayout
    private lateinit var btnOpenConnectBranches: Button

    private lateinit var layoutIncomingMergeRequests: View
    private lateinit var tvIncomingMergeRequestsText: TextView
    private lateinit var btnReviewMergeRequests: Button
    private var pendingRequestsRegistration: ListenerRegistration? = null
    private var pendingIncomingRequests: List<ClanMergeRequest> = emptyList()

    private lateinit var cardMyMergeCode: LinearLayout
    private lateinit var tvMyMergeCodeDisplay: TextView
    private lateinit var btnCopyMyMergeCode: Button
    private lateinit var btnRegenerateMyMergeCode: Button
    private var myActiveMergeCode: String = ""

    private var cardRollbackSnapshot: LinearLayout? = null
    private var tvRollbackTimestamp: TextView? = null
    private var tvRollbackTitle: TextView? = null
    private var tvRollbackDetails: TextView? = null
    private var btnExecuteMergeRollback: Button? = null
    private var activeRollbackSnapshot: MergeRollbackSnapshot? = null

    private val clanRefreshHandler = Handler(Looper.getMainLooper())
    private val clanRefreshRunnable = Runnable {
        loadClanTrees()
    }
    private var isLoadingClanTrees: Boolean = false

    private val mergeSyncListener = object : SyncEventListener {
        override val subscriberKey: String = "MergeBranchesActivity@${System.identityHashCode(this)}"
        override val interestedTreeId: String? = null
        override fun onSyncEvent(event: TreeSyncEvent) {
            if (event.changeType == SyncChangeType.TREE_DELETED ||
                event.changeType == SyncChangeType.TREE_CREATED ||
                event.changeType == SyncChangeType.MEMBER_ADDED ||
                event.changeType == SyncChangeType.CLAN_MERGE_REQUEST_UPDATED) {
                runOnUiThread {
                    clanRefreshHandler.removeCallbacks(clanRefreshRunnable)
                    clanRefreshHandler.postDelayed(clanRefreshRunnable, 250L)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CentralTreeSynchronizer.getInstance().registerListener(mergeSyncListener)
        startListeningPendingMergeRequests()
    }

    override fun onResume() {
        super.onResume()
        loadClanTrees()
        refreshMergeRollbackBanner()
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.MERGE_CLAN_SPACE,
            treeId = activeTreeId
        )
    }

    override fun onStop() {
        super.onStop()
        CentralTreeSynchronizer.getInstance().unregisterListener(mergeSyncListener)
        pendingRequestsRegistration?.remove()
        pendingRequestsRegistration = null
    }

    override fun onDestroy() {
        super.onDestroy()
        clanRefreshHandler.removeCallbacks(clanRefreshRunnable)
        pendingRequestsRegistration?.remove()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merge_branches)

        activeTreeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val btnQuickAssistMerge = findViewById<TextView>(R.id.btnQuickAssistMerge)
        btnQuickAssistMerge?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.MERGE_BRANCHES
            ).show(supportFragmentManager, "QuickAssistMerge")
        }

        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvMergeRoleBadge = findViewById(R.id.tvMergeRoleBadge)
        layoutViewerBanner = findViewById(R.id.layoutViewerBanner)
        btnOpenSynthesizeClanTree = findViewById(R.id.btnOpenSynthesizeClanTree)
        tvClanTreeCount = findViewById(R.id.tvClanTreeCount)
        progressBarMerge = findViewById(R.id.progressBarMerge)
        layoutEmptyClanState = findViewById(R.id.layoutEmptyClanState)
        clanTreesContainer = findViewById(R.id.clanTreesContainer)
        btnOpenConnectBranches = findViewById(R.id.btnOpenConnectBranches)

        layoutIncomingMergeRequests = findViewById(R.id.layoutIncomingMergeRequests)
        tvIncomingMergeRequestsText = findViewById(R.id.tvIncomingMergeRequestsText)
        btnReviewMergeRequests = findViewById(R.id.btnReviewMergeRequests)

        btnReviewMergeRequests.setOnClickListener {
            showReviewPendingRequestsDialog()
        }

        cardMyMergeCode = findViewById(R.id.cardMyMergeCode)
        tvMyMergeCodeDisplay = findViewById(R.id.tvMyMergeCodeDisplay)
        btnCopyMyMergeCode = findViewById(R.id.btnCopyMyMergeCode)
        btnRegenerateMyMergeCode = findViewById(R.id.btnRegenerateMyMergeCode)

        btnCopyMyMergeCode.setOnClickListener {
            if (myActiveMergeCode.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("KinTrace Clan Merge Code", myActiveMergeCode)
                clipboard.setPrimaryClip(clip)
                KinTraceAlert.show(this, "Clan Merge Code $myActiveMergeCode copied to clipboard!")
            } else {
                KinTraceAlert.show(this, "Generating merge code, please wait...")
            }
        }

        btnRegenerateMyMergeCode.setOnClickListener {
            confirmRegenerateMergeCode()
        }

        btnOpenSynthesizeClanTree.setOnClickListener {
            if (isViewerOnly) {
                com.example.btproject2.utils.BotanicalRoleFlashManager.showRestrictedActionFlash(
                    activity = this,
                    rawRole = "Viewer",
                    isTreeOwner = false,
                    featureTitle = "Intertwine & Synthesize Clan Branches",
                    requiredTier = com.example.btproject2.utils.BotanicalRolePolicy.BotanicalTier.BRANCH_EDITOR,
                    treeId = activeTreeId
                )
            } else {
                showSynthesizeClanTreeDialog()
            }
        }

        btnOpenConnectBranches.setOnClickListener {
            if (isViewerOnly) {
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createPermissionRestrictedNotification(
                    treeId = activeTreeId,
                    userId = currentUserId,
                    actionAttempted = "Connect Disconnected Branches",
                    currentRole = "Viewer"
                )
                firestoreHelper.addNotification(notif)
                com.example.btproject2.utils.BotanicalRoleFlashManager.showRestrictedActionFlash(
                    activity = this,
                    rawRole = "Viewer",
                    isTreeOwner = false,
                    featureTitle = "Connect Disconnected Branches",
                    requiredTier = com.example.btproject2.utils.BotanicalRolePolicy.BotanicalTier.BRANCH_EDITOR,
                    treeId = activeTreeId
                )
            } else {
                showConnectBranchesDialog()
            }
        }

        checkRoleAndLoadData()
        bindAndLoadRollbackBanner()
    }

    private fun bindAndLoadRollbackBanner() {
        cardRollbackSnapshot = findViewById(R.id.cardRollbackSnapshot)
        tvRollbackTimestamp = findViewById(R.id.tvRollbackTimestamp)
        tvRollbackTitle = findViewById(R.id.tvRollbackTitle)
        tvRollbackDetails = findViewById(R.id.tvRollbackDetails)
        btnExecuteMergeRollback = findViewById(R.id.btnExecuteMergeRollback)

        btnExecuteMergeRollback?.setOnClickListener {
            val snapshot = activeRollbackSnapshot ?: return@setOnClickListener
            if (isViewerOnly) {
                KinTraceAlert.show(this, "Permission restricted: Viewers cannot undo merges.")
                return@setOnClickListener
            }
            confirmAndExecuteMergeRollback(snapshot)
        }
        refreshMergeRollbackBanner()
    }

    private fun refreshMergeRollbackBanner() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        firestoreHelper.fetchLatestActiveMergeSnapshot(
            ownerId = currentUserId,
            onSuccess = { snapshot ->
                runOnUiThread {
                    activeRollbackSnapshot = snapshot
                    if (snapshot == null || snapshot.rolledBack || isViewerOnly) {
                        cardRollbackSnapshot?.visibility = View.GONE
                    } else {
                        cardRollbackSnapshot?.visibility = View.VISIBLE
                        val elapsedMinutes = ((System.currentTimeMillis() - snapshot.createdAt) / 60000L).coerceAtLeast(0L)
                        tvRollbackTimestamp?.text = if (elapsedMinutes == 0L) "Just now" else "${elapsedMinutes}m ago"
                        if (snapshot.snapshotType == MergeRollbackSnapshot.TYPE_CLAN_SYNTHESIS) {
                            tvRollbackTitle?.text = "Synthesized: ${snapshot.synthesizedMasterTreeName.ifBlank { "Master Clan Tree" }}"
                            tvRollbackDetails?.text = "Combined ${snapshot.sourceTree1Name} + ${snapshot.sourceTree2Name} (${snapshot.combinedMemberCount} cloned records). Tap below to safely undo and dismantle this Clan Tree while keeping your original trees 100% untouched."
                        } else {
                            tvRollbackTitle?.text = "Internal Branch Link: ${snapshot.bridgeDescription.ifBlank { "Connected Branches" }}"
                            tvRollbackDetails?.text = "Saved pre-merge relationship pointers before connecting internal branches. Tap below to restore the exact prior parent/spouse links."
                        }
                    }
                }
            }
        )
    }

    private fun confirmAndExecuteMergeRollback(snapshot: MergeRollbackSnapshot) {
        val summaryMsg = if (snapshot.snapshotType == MergeRollbackSnapshot.TYPE_CLAN_SYNTHESIS) {
            "Are you sure you want to undo the synthesis of '${snapshot.synthesizedMasterTreeName}'?\n\n" +
                "• All ${snapshot.combinedMemberCount} cloned Clan members and the merged tree container will be cleanly removed.\n" +
                "• Your original source family trees (${snapshot.sourceTree1Name} & ${snapshot.sourceTree2Name}) remain 100% intact."
        } else {
            "Are you sure you want to undo the internal branch connection (${snapshot.bridgeDescription})?\n\n" +
                "• Both family members will be restored to their exact pre-merge parent and spouse pointers."
        }

        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("⏪ Undo Last Merge?")
            .setMessage(summaryMsg)
            .setPositiveButton("Yes, Restore Pre-Merge State") { _, _ ->
                btnExecuteMergeRollback?.isEnabled = false
                btnExecuteMergeRollback?.text = "Restoring Pre-Merge State..."
                firestoreHelper.executeMergeRollback(
                    snapshot = snapshot,
                    context = this,
                    onSuccess = {
                        runOnUiThread {
                            btnExecuteMergeRollback?.isEnabled = true
                            btnExecuteMergeRollback?.text = "⏪ Undo Last Merge & Restore State"
                            cardRollbackSnapshot?.visibility = View.GONE
                            activeRollbackSnapshot = null
                            KinTraceAlert.show(this, "Pre-merge state restored! All connected screens updated.")
                            loadClanTrees()
                            loadPersonalTreesAndMembers()
                        }
                    },
                    onFailure = { err ->
                        runOnUiThread {
                            btnExecuteMergeRollback?.isEnabled = true
                            btnExecuteMergeRollback?.text = "⏪ Undo Last Merge & Restore State"
                            KinTraceAlert.show(this, "Rollback failed: ${err.message}")
                        }
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkRoleAndLoadData() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        if (activeTreeId.isNotEmpty()) {
            firestoreHelper.getTree(activeTreeId,
                onSuccess = { tree ->
                    val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                    if (isOwner) {
                        tvMergeRoleBadge.text = "★ Owner"
                        isViewerOnly = false
                        layoutViewerBanner.visibility = View.GONE
                    } else {
                        firestoreHelper.getUserMembership(currentUserId, activeTreeId,
                            onSuccess = { member ->
                                val role = member?.role ?: "Viewer"
                                if (role.equals("editor", ignoreCase = true)) {
                                    tvMergeRoleBadge.text = "✏ Editor"
                                    isViewerOnly = false
                                    layoutViewerBanner.visibility = View.GONE
                                } else {
                                    tvMergeRoleBadge.text = "👁 Viewer"
                                    isViewerOnly = true
                                    layoutViewerBanner.visibility = View.VISIBLE
                                }
                            },
                            onFailure = {
                                tvMergeRoleBadge.text = "👁 Viewer"
                                isViewerOnly = true
                                layoutViewerBanner.visibility = View.VISIBLE
                            }
                        )
                    }
                    loadPersonalTreesAndMembers()
                },
                onFailure = {
                    tvMergeRoleBadge.text = "👁 Viewer"
                    isViewerOnly = true
                    layoutViewerBanner.visibility = View.VISIBLE
                    loadPersonalTreesAndMembers()
                }
            )
        } else {
            tvMergeRoleBadge.text = "★ Owner"
            isViewerOnly = false
            loadPersonalTreesAndMembers()
        }
    }

    private fun loadPersonalTreesAndMembers() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        // Load personal trees (excluding merged clan trees)
        firestoreHelper.getUserTrees(currentUserId, includeMergedClan = false,
            onSuccess = { trees ->
                allPersonalTrees = trees
            },
            onFailure = {}
        )

        // Load current active tree members for intra-tree tool
        if (activeTreeId.isNotEmpty()) {
            firestoreHelper.getPersonsByTree(activeTreeId,
                onSuccess = { persons ->
                    currentTreePersons = persons
                },
                onFailure = {}
            )
        }

        // Load current active tree merge code
        loadActiveTreeMergeCode()
    }

    private fun loadActiveTreeMergeCode() {
        if (activeTreeId.isBlank()) {
            cardMyMergeCode.visibility = View.GONE
            return
        }
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        val currentUserName = authHelper.getCurrentUser()?.displayName ?: "Owner"
        val treeName = com.example.btproject2.utils.TreePreferences.getActiveTreeName(this).ifBlank { "Family Tree" }

        firestoreHelper.getOrCreateTreeMergeInviteCode(
            treeId = activeTreeId,
            treeName = treeName,
            ownerId = currentUserId,
            ownerName = currentUserName,
            onSuccess = { code ->
                myActiveMergeCode = code
                tvMyMergeCodeDisplay.text = code.map { "$it " }.joinToString("").trim()
            },
            onFailure = {
                tvMyMergeCodeDisplay.text = "ERROR"
            }
        )
    }

    private fun confirmRegenerateMergeCode() {
        if (isViewerOnly) {
            KinTraceAlert.show(this, "Permission restricted: Only owners can regenerate merge codes.")
            return
        }
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Regenerate Clan Merge Code?")
            .setMessage("Generating a new Clan Merge Code will invalidate the previous code ($myActiveMergeCode). Anyone wanting to merge with this tree will need the new code.")
            .setPositiveButton("Regenerate") { _, _ ->
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val currentUserName = authHelper.getCurrentUser()?.displayName ?: "Owner"
                val treeName = com.example.btproject2.utils.TreePreferences.getActiveTreeName(this).ifBlank { "Family Tree" }
                tvMyMergeCodeDisplay.text = "......"
                firestoreHelper.regenerateTreeMergeInviteCode(
                    treeId = activeTreeId,
                    treeName = treeName,
                    ownerId = currentUserId,
                    ownerName = currentUserName,
                    onSuccess = { newCode ->
                        myActiveMergeCode = newCode
                        tvMyMergeCodeDisplay.text = newCode.map { "$it " }.joinToString("").trim()
                        KinTraceAlert.show(this, "New Clan Merge Code: $newCode")
                    },
                    onFailure = {
                        KinTraceAlert.show(this, "Failed to regenerate merge code.")
                        tvMyMergeCodeDisplay.text = myActiveMergeCode.map { "$it " }.joinToString("").trim()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startListeningPendingMergeRequests() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (currentUserId.isBlank()) return

        pendingRequestsRegistration?.remove()
        pendingRequestsRegistration = firestoreHelper.listenToPendingClanMergeRequests(currentUserId) { requests ->
            runOnUiThread {
                pendingIncomingRequests = requests
                if (requests.isNotEmpty()) {
                    layoutIncomingMergeRequests.visibility = View.VISIBLE
                    tvIncomingMergeRequestsText.text = "📩 ${requests.size} Pending Clan Merge Request(s) awaiting your review"
                } else {
                    layoutIncomingMergeRequests.visibility = View.GONE
                }
            }
        }
    }

    private fun showReviewPendingRequestsDialog() {
        if (pendingIncomingRequests.isEmpty()) {
            KinTraceAlert.show(this, "No pending clan merge requests.")
            return
        }
        if (pendingIncomingRequests.size == 1) {
            showReviewSingleRequestDialog(pendingIncomingRequests.first())
        } else {
            val names = pendingIncomingRequests.map { "${it.requesterTreeName} (from ${it.requesterOwnerName})" }.toTypedArray()
            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                .setTitle("Incoming Clan Merge Requests")
                .setItems(names) { _, which ->
                    showReviewSingleRequestDialog(pendingIncomingRequests[which])
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showReviewSingleRequestDialog(request: ClanMergeRequest) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_review_merge_request, null)
        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvReviewRequestTitle)
        val tvRequester = dialogView.findViewById<TextView>(R.id.tvReviewRequesterInfo)
        val tvSourceTree = dialogView.findViewById<TextView>(R.id.tvReviewSourceTreeInfo)
        val tvTargetTree = dialogView.findViewById<TextView>(R.id.tvReviewTargetTreeInfo)
        val tvTimestamps = dialogView.findViewById<TextView>(R.id.tvReviewTimestamps)
        val btnReject = dialogView.findViewById<Button>(R.id.btnRejectMergeRequest)
        val btnApprove = dialogView.findViewById<Button>(R.id.btnApproveMergeRequest)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseReviewDialog)

        tvTitle.text = "${request.requesterTreeName} ➔ ${request.targetTreeName}"
        tvRequester.text = "Requester: ${request.requesterOwnerName}"
        tvSourceTree.text = "Requesting Tree: ${request.requesterTreeName}"
        tvTargetTree.text = "Your Target Tree: ${request.targetTreeName}"

        val expiryHours = maxOf(0L, (request.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60))
        tvTimestamps.text = "Status: Pending Approval • Expires in ~${expiryHours}h"

        btnReject.setOnClickListener {
            btnReject.isEnabled = false
            btnApprove.isEnabled = false
            firestoreHelper.updateClanMergeRequestStatus(request.id, ClanMergeRequest.STATUS_REJECTED, onSuccess = {
                dialog.dismiss()
                KinTraceAlert.show(this, "Clan merge request declined.")
            }, onFailure = { err ->
                btnReject.isEnabled = true
                btnApprove.isEnabled = true
                KinTraceAlert.show(this, "Failed to decline: ${err.message}")
            })
        }

        btnApprove.setOnClickListener {
            btnReject.isEnabled = false
            btnApprove.isEnabled = false
            firestoreHelper.updateClanMergeRequestStatus(request.id, ClanMergeRequest.STATUS_APPROVED, onSuccess = {
                dialog.dismiss()
                KinTraceAlert.show(this, "✓ Clan merge authorized! The requester can now complete the synthesis.")
            }, onFailure = { err ->
                btnReject.isEnabled = true
                btnApprove.isEnabled = true
                KinTraceAlert.show(this, "Failed to approve: ${err.message}")
            })
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun loadClanTrees() {
        if (isLoadingClanTrees) return
        isLoadingClanTrees = true

        val currentUserId = authHelper.getCurrentUserId() ?: ""
        progressBarMerge.visibility = View.VISIBLE
        layoutEmptyClanState.visibility = View.GONE
        tvClanTreeCount.text = "Scanning..."

        firestoreHelper.getMergedClanTrees(currentUserId,
            onSuccess = { clanTrees ->
                isLoadingClanTrees = false
                progressBarMerge.visibility = View.GONE
                clanTreesContainer.removeAllViews()
                val distinctTrees = clanTrees.distinctBy { it.id }
                if (distinctTrees.isEmpty()) {
                    tvClanTreeCount.text = "0 Clan Trees"
                    layoutEmptyClanState.visibility = View.VISIBLE
                } else {
                    tvClanTreeCount.text = "${distinctTrees.size} Clan Tree(s)"
                    layoutEmptyClanState.visibility = View.GONE
                    val inflater = LayoutInflater.from(this)
                    for (tree in distinctTrees) {
                        addClanTreeCard(inflater, tree)
                    }
                }
            },
            onFailure = {
                isLoadingClanTrees = false
                progressBarMerge.visibility = View.GONE
                clanTreesContainer.removeAllViews()
                tvClanTreeCount.text = "0 Clan Trees"
                layoutEmptyClanState.visibility = View.VISIBLE
            }
        )
    }

    private fun addClanTreeCard(inflater: LayoutInflater, tree: FamilyTree) {
        if (clanTreesContainer.findViewWithTag<View>(tree.id) != null) {
            return
        }
        val card = inflater.inflate(R.layout.item_clan_tree_card, clanTreesContainer, false)
        card.tag = tree.id

        val tvName = card.findViewById<TextView>(R.id.tvClanTreeName)
        val tvMembers = card.findViewById<TextView>(R.id.tvClanTreeMemberCount)
        val tvBridge = card.findViewById<TextView>(R.id.tvClanTreeBridge)
        val btnView = card.findViewById<Button>(R.id.btnViewClanCanvas)
        val btnDelete = card.findViewById<Button>(R.id.btnDeleteClanTree)

        tvName.text = tree.name
        tvMembers.text = "${tree.memberCount} Members • Master Clan Sandbox"

        if (tree.bridgeDescription.isNotBlank()) {
            tvBridge.text = tree.bridgeDescription
            tvBridge.visibility = View.VISIBLE
        } else {
            tvBridge.text = "💍 Master Clan Bridge Link"
            tvBridge.visibility = View.VISIBLE
        }

        btnView.setOnClickListener {
            val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                putExtra("TREE_ID", tree.id)
                putExtra("treeId", tree.id)
                putExtra("IS_CLAN_SPACE", true)
            }
            startActivity(intent)
        }

        val currentUserId = authHelper.getCurrentUserId() ?: ""
        val canDeleteClan = !isViewerOnly && (tree.ownerId.isEmpty() || tree.ownerId == currentUserId)
        btnDelete.visibility = if (canDeleteClan) View.VISIBLE else View.GONE

        btnDelete.setOnClickListener {
            if (!canDeleteClan) {
                KinTraceAlert.warning(this, "Permission Restricted", "Only the Clan Tree owner can delete a Master Clan Tree.")
                return@setOnClickListener
            }
            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                .setTitle("Delete Master Clan Tree?")
                .setMessage("Are you sure you want to remove '${tree.name}'?\n\n🛡️ 100% Safe: Your original family trees will NOT be modified or deleted.")
                .setPositiveButton("Delete") { _, _ ->
                    progressBarMerge.visibility = View.VISIBLE
                    firestoreHelper.deleteTree(
                        treeId = tree.id,
                        context = this,
                        onSuccess = {
                            progressBarMerge.visibility = View.GONE
                            KinTraceAlert.show(this, "Master Clan Tree deleted.")
                            clanRefreshHandler.removeCallbacks(clanRefreshRunnable)
                            clanRefreshHandler.postDelayed(clanRefreshRunnable, 100L)
                        },
                        onFailure = { error ->
                            progressBarMerge.visibility = View.GONE
                            KinTraceAlert.show(this, "Failed to delete: ${error.message}")
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        clanTreesContainer.addView(card)
    }

    // =========================================================================
    // 3-STEP ZERO-RISK MASTER CLAN TREE SYNTHESIS WIZARD (PROPOSALS 15 & 16)
    // =========================================================================
    private fun showSynthesizeClanTreeDialog() {
        if (allPersonalTrees.isEmpty()) {
            KinTraceAlert.show(this, "You need at least one family tree to synthesize a clan tree.")
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_synthesize_clan_tree, null)
        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()

        val spinnerTree1 = dialogView.findViewById<Spinner>(R.id.spinnerTree1)
        val rgTree2Source = dialogView.findViewById<RadioGroup>(R.id.rgTree2Source)
        val rbTree2MyTrees = dialogView.findViewById<RadioButton>(R.id.rbTree2MyTrees)
        val spinnerTree2 = dialogView.findViewById<Spinner>(R.id.spinnerTree2)
        val layoutTree2InviteCode = dialogView.findViewById<LinearLayout>(R.id.layoutTree2InviteCode)
        val etTree2InviteCode = dialogView.findViewById<EditText>(R.id.etTree2InviteCode)
        val btnLookupInviteCode = dialogView.findViewById<Button>(R.id.btnLookupInviteCode)
        val tvInviteCodeResult = dialogView.findViewById<TextView>(R.id.tvInviteCodeResult)
        val etMasterTreeName = dialogView.findViewById<EditText>(R.id.etMasterTreeName)

        val layoutMergeRequestStatus = dialogView.findViewById<LinearLayout>(R.id.layoutMergeRequestStatus)
        val tvMergeRequestStatus = dialogView.findViewById<TextView>(R.id.tvMergeRequestStatus)
        val tvMergeRequestSubtitle = dialogView.findViewById<TextView>(R.id.tvMergeRequestSubtitle)
        val btnSendMergeRequest = dialogView.findViewById<Button>(R.id.btnSendMergeRequest)
        val btnCancelMergeRequest = dialogView.findViewById<Button>(R.id.btnCancelMergeRequest)

        var activeApprovedRequestId: String? = null
        var activePendingRequestId: String? = null
        var dialogRequestRegistration: ListenerRegistration? = null
        var verifiedExternalTree: FamilyTree? = null

        val rgBridgeType = dialogView.findViewById<RadioGroup>(R.id.rgBridgeType)
        val rbBridgeSpouse = dialogView.findViewById<RadioButton>(R.id.rbBridgeSpouse)
        val rbBridgeParentChild = dialogView.findViewById<RadioButton>(R.id.rbBridgeParentChild)
        val rbBridgeSharedAncestor = dialogView.findViewById<RadioButton>(R.id.rbBridgeSharedAncestor)
        val rbBridgeSibling = dialogView.findViewById<RadioButton>(R.id.rbBridgeSibling)

        val tvAnchor1Label = dialogView.findViewById<TextView>(R.id.tvAnchor1Label)
        val spinnerAnchorPerson1 = dialogView.findViewById<Spinner>(R.id.spinnerAnchorPerson1)
        val tvAnchor2Label = dialogView.findViewById<TextView>(R.id.tvAnchor2Label)
        val spinnerAnchorPerson2 = dialogView.findViewById<Spinner>(R.id.spinnerAnchorPerson2)

        val layoutValidationWarning = dialogView.findViewById<LinearLayout>(R.id.layoutValidationWarning)
        val tvValidationWarningText = dialogView.findViewById<TextView>(R.id.tvValidationWarningText)
        val tvSynthesisSummary = dialogView.findViewById<TextView>(R.id.tvSynthesisSummary)
        val progressBarSynthesis = dialogView.findViewById<ProgressBar>(R.id.progressBarSynthesis)
        val btnExecuteSynthesis = dialogView.findViewById<Button>(R.id.btnExecuteSynthesis)
        val btnCancelSynthesis = dialogView.findViewById<Button>(R.id.btnCancelSynthesis)

        // Populate Tree 1 Spinner
        val tree1Names = allPersonalTrees.map { it.name }
        spinnerTree1.setDarkAdapter(this, tree1Names)

        // Populate Tree 2 Spinner
        val tree2Names = allPersonalTrees.map { it.name }
        spinnerTree2.setDarkAdapter(this, tree2Names)
        if (allPersonalTrees.size > 1) {
            spinnerTree2.setSelection(1)
        }

        var personsTree1 = listOf<Person>()
        var personsTree2 = listOf<Person>()
        var externalTreeLoaded: FamilyTree? = null

        fun formatCandidateLabel(p: Person): String {
            val isMarried = p.maritalStatus.equals("Married", ignoreCase = true) || !p.spouseId.isNullOrEmpty()
            val badge = if (isMarried) " [Married]" else ""
            return "${p.firstName} ${p.lastName}$badge"
        }

        fun updateDefaultMasterTreeName() {
            val t1Name = spinnerTree1.selectedItem?.toString().orEmpty()
            val t2Name = if (rbTree2MyTrees.isChecked) {
                spinnerTree2.selectedItem?.toString().orEmpty()
            } else {
                externalTreeLoaded?.name ?: "External Tree"
            }
            if (t1Name.isNotEmpty() && t2Name.isNotEmpty()) {
                val clean1 = t1Name.replace(" Family Tree", "").replace(" Tree", "")
                val clean2 = t2Name.replace(" Family Tree", "").replace(" Tree", "")
                etMasterTreeName.setText("$clean1 - $clean2 Master Clan Tree")
            }
        }

        val previewExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        var previewGeneration = 0

        fun updateSummaryAndValidation() {
            layoutValidationWarning.visibility = View.GONE
            val count1 = personsTree1.size
            val count2 = personsTree2.size
            val total = count1 + count2

            tvSynthesisSummary.text = "📊 Summary: $count1 members from Tree 1 + $count2 members from Tree 2 = $total total members in Master Tree C.\n🛡️ Zero-Risk: Original trees remain untouched."

            val idx1 = spinnerAnchorPerson1.selectedItemPosition
            val idx2 = spinnerAnchorPerson2.selectedItemPosition

            if (idx1 in personsTree1.indices && idx2 in personsTree2.indices) {
                val p1 = personsTree1[idx1]
                val p2 = personsTree2[idx2]
                val snapTree1 = personsTree1.toList()
                val snapTree2 = personsTree2.toList()

                val mode = when {
                    rbBridgeSpouse.isChecked -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SPOUSAL_UNION
                    rbBridgeParentChild.isChecked -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.PARENT_CHILD
                    rbBridgeSharedAncestor.isChecked -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SHARED_ANCESTOR
                    rbBridgeSibling?.isChecked == true -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SIBLING_LINEAGE
                    else -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SPOUSAL_UNION
                }

                val config = com.example.btproject2.engine.MergeSimulationEngine.BridgeConfig(
                    mode = mode,
                    personAId = p1.id,
                    personBId = p2.id
                )

                val currentGen = ++previewGeneration
                try {
                    previewExecutor.execute {
                        val simResult = mergeSimulationEngine.simulate(snapTree1, snapTree2, config)
                        runOnUiThread {
                            if (currentGen != previewGeneration || isFinishing || isDestroyed) return@runOnUiThread
                            if (!simResult.isSafe) {
                                layoutValidationWarning.visibility = View.VISIBLE
                                tvValidationWarningText.text = "⚠️ ${simResult.blockReason ?: "Genealogical conflict detected"}"
                                btnExecuteSynthesis.isEnabled = false
                                btnExecuteSynthesis.alpha = 0.5f
                            } else {
                                layoutValidationWarning.visibility = View.GONE
                                btnExecuteSynthesis.isEnabled = true
                                btnExecuteSynthesis.alpha = 1.0f
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            } else {
                ++previewGeneration
                btnExecuteSynthesis.isEnabled = false
                btnExecuteSynthesis.alpha = 0.5f
            }
        }

        fun loadTree1Members() {
            val selPos = spinnerTree1.selectedItemPosition
            if (selPos in allPersonalTrees.indices) {
                val selectedTree = allPersonalTrees[selPos]
                tvAnchor1Label.text = "Member from ${selectedTree.name}"
                firestoreHelper.getPersonsByTree(selectedTree.id,
                    onSuccess = { list ->
                        personsTree1 = list
                        val names = list.map { formatCandidateLabel(it) }
                        spinnerAnchorPerson1.setDarkAdapter(this, if (names.isEmpty()) listOf("No members recorded") else names)
                        updateSummaryAndValidation()
                    },
                    onFailure = {
                        personsTree1 = emptyList()
                        spinnerAnchorPerson1.setDarkAdapter(this, listOf("Failed to load"))
                    }
                )
            }
        }

        fun loadTree2Members() {
            if (rbTree2MyTrees.isChecked) {
                val selPos = spinnerTree2.selectedItemPosition
                if (selPos in allPersonalTrees.indices) {
                    val selectedTree = allPersonalTrees[selPos]
                    tvAnchor2Label.text = "Member from ${selectedTree.name}"
                    firestoreHelper.getPersonsByTree(selectedTree.id,
                        onSuccess = { list ->
                            personsTree2 = list
                            val names = list.map { formatCandidateLabel(it) }
                            spinnerAnchorPerson2.setDarkAdapter(this, if (names.isEmpty()) listOf("No members recorded") else names)
                            updateSummaryAndValidation()
                        },
                        onFailure = {
                            personsTree2 = emptyList()
                            spinnerAnchorPerson2.setDarkAdapter(this, listOf("Failed to load"))
                        }
                    )
                }
            } else if (externalTreeLoaded != null) {
                tvAnchor2Label.text = "Member from ${externalTreeLoaded!!.name}"
                firestoreHelper.getPersonsByTree(externalTreeLoaded!!.id,
                    onSuccess = { list ->
                        personsTree2 = list
                        val names = list.map { formatCandidateLabel(it) }
                        spinnerAnchorPerson2.setDarkAdapter(this, if (names.isEmpty()) listOf("No members recorded") else names)
                        updateSummaryAndValidation()
                    },
                    onFailure = {
                        personsTree2 = emptyList()
                        spinnerAnchorPerson2.setDarkAdapter(this, listOf("Failed to load"))
                    }
                )
            }
        }

        fun applyRequestStateInternal(req: ClanMergeRequest?) {
            val target = verifiedExternalTree
            layoutMergeRequestStatus.visibility = View.VISIBLE
            when {
                req == null || req.isExpired() || req.status == ClanMergeRequest.STATUS_REJECTED || req.status == ClanMergeRequest.STATUS_CANCELLED -> {
                    externalTreeLoaded = null
                    activeApprovedRequestId = null
                    activePendingRequestId = null
                    personsTree2 = emptyList()
                    spinnerAnchorPerson2.setDarkAdapter(this@MergeBranchesActivity, listOf("🔒 Awaiting Owner Approval"))
                    btnExecuteSynthesis.isEnabled = false

                    if (req?.status == ClanMergeRequest.STATUS_REJECTED) {
                        tvMergeRequestStatus.text = "❌ Request was declined by the tree owner."
                        tvMergeRequestStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.danger))
                        tvMergeRequestSubtitle.text = "You may send a new request if needed."
                    } else if (req?.isExpired() == true) {
                        tvMergeRequestStatus.text = "⏰ Previous request expired."
                        tvMergeRequestStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.danger))
                        tvMergeRequestSubtitle.text = "Send a new request to request access."
                    } else {
                        tvMergeRequestStatus.text = "🔒 Approval Required: Target tree owner must authorize this merge."
                        tvMergeRequestStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.gold_accent))
                        tvMergeRequestSubtitle.text = "Tap 'Send Merge Request' below to notify ${target?.ownerName?.ifBlank { "the tree owner" } ?: "the tree owner"}."
                    }
                    btnSendMergeRequest.visibility = View.VISIBLE
                    btnSendMergeRequest.isEnabled = true
                    btnCancelMergeRequest.visibility = View.GONE
                }
                req.status == ClanMergeRequest.STATUS_PENDING -> {
                    externalTreeLoaded = null
                    activeApprovedRequestId = null
                    activePendingRequestId = req.id
                    personsTree2 = emptyList()
                    spinnerAnchorPerson2.setDarkAdapter(this@MergeBranchesActivity, listOf("🔒 Awaiting Owner Approval"))
                    btnExecuteSynthesis.isEnabled = false

                    tvMergeRequestStatus.text = "⏳ Request sent — waiting for the target tree owner's approval."
                    tvMergeRequestStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.gold_accent))
                    tvMergeRequestSubtitle.text = "The target tree owner must approve this request before synthesis can proceed."
                    btnSendMergeRequest.visibility = View.GONE
                    btnCancelMergeRequest.visibility = View.VISIBLE
                }
                req.canUnlockSynthesis() -> {
                    // UNLOCKED CONTINUATION POINT!
                    externalTreeLoaded = target
                    activeApprovedRequestId = req.id
                    activePendingRequestId = null

                    tvMergeRequestStatus.text = "✓ Approved by Owner: Tree B unlocked for synthesis."
                    tvMergeRequestStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.status_mint_text))
                    tvMergeRequestSubtitle.text = "You may now select anchor members and establish the clan bridge."
                    btnSendMergeRequest.visibility = View.GONE
                    btnCancelMergeRequest.visibility = View.GONE
                    btnExecuteSynthesis.isEnabled = true

                    updateDefaultMasterTreeName()
                    loadTree2Members()
                }
                req.status == ClanMergeRequest.STATUS_COMPLETED -> {
                    externalTreeLoaded = null
                    activeApprovedRequestId = null
                    activePendingRequestId = null
                    personsTree2 = emptyList()
                    spinnerAnchorPerson2.setDarkAdapter(this@MergeBranchesActivity, listOf("✓ Synthesis Already Completed"))
                    btnExecuteSynthesis.isEnabled = false

                    tvMergeRequestStatus.text = "✓ Authorization Already Completed."
                    tvMergeRequestStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.status_mint_text))
                    tvMergeRequestSubtitle.text = "A Master Clan Tree has already been created from this authorization."
                    btnSendMergeRequest.visibility = View.GONE
                    btnCancelMergeRequest.visibility = View.GONE
                }
            }
        }

        fun listenToDialogRequest(reqId: String) {
            dialogRequestRegistration?.remove()
            dialogRequestRegistration = firestoreHelper.listenToClanMergeRequest(reqId) { liveReq ->
                runOnUiThread {
                    if (liveReq != null && liveReq.id == reqId) {
                        applyRequestStateInternal(liveReq)
                    }
                }
            }
        }

        fun applyRequestState(req: ClanMergeRequest?) {
            if (req != null) {
                listenToDialogRequest(req.id)
            }
            applyRequestStateInternal(req)
        }

        spinnerTree1.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadTree1Members()
                updateDefaultMasterTreeName()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerTree2.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadTree2Members()
                updateDefaultMasterTreeName()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val anchorListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSummaryAndValidation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerAnchorPerson1.onItemSelectedListener = anchorListener
        spinnerAnchorPerson2.onItemSelectedListener = anchorListener

        rgBridgeType.setOnCheckedChangeListener { _, _ -> updateSummaryAndValidation() }

        rgTree2Source.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbTree2MyTrees) {
                spinnerTree2.visibility = View.VISIBLE
                layoutTree2InviteCode.visibility = View.GONE
                layoutMergeRequestStatus.visibility = View.GONE
                loadTree2Members()
                btnExecuteSynthesis.isEnabled = true
            } else {
                spinnerTree2.visibility = View.GONE
                layoutTree2InviteCode.visibility = View.VISIBLE
                if (verifiedExternalTree != null) {
                    layoutMergeRequestStatus.visibility = View.VISIBLE
                    btnExecuteSynthesis.isEnabled = (activeApprovedRequestId != null)
                } else {
                    layoutMergeRequestStatus.visibility = View.GONE
                    personsTree2 = emptyList()
                    spinnerAnchorPerson2.setDarkAdapter(this@MergeBranchesActivity, listOf("Enter and verify code above"))
                    btnExecuteSynthesis.isEnabled = false
                }
            }
            updateDefaultMasterTreeName()
        }

        btnLookupInviteCode.setOnClickListener {
            val rawCode = etTree2InviteCode.text.toString().trim().uppercase()
            if (rawCode.length != 6) {
                tvInviteCodeResult.visibility = View.VISIBLE
                tvInviteCodeResult.text = "⚠️ Please enter a 6-character clan merge code."
                tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.audit_warning_text))
                return@setOnClickListener
            }
            if (rawCode == myActiveMergeCode) {
                tvInviteCodeResult.visibility = View.VISIBLE
                tvInviteCodeResult.text = "⚠️ This is your own tree's merge code. Please select a different clan tree."
                tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.audit_warning_text))
                return@setOnClickListener
            }
            btnLookupInviteCode.isEnabled = false
            tvInviteCodeResult.visibility = View.VISIBLE
            tvInviteCodeResult.text = "🔍 Looking up clan tree..."
            tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.gold_accent))

            firestoreHelper.findTreeByMergeInviteCode(rawCode,
                onSuccess = { tree ->
                    btnLookupInviteCode.isEnabled = true
                    if (tree == null) {
                        tvInviteCodeResult.text = "❌ No family tree found with merge code $rawCode."
                        tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.danger))
                        verifiedExternalTree = null
                        layoutMergeRequestStatus.visibility = View.GONE
                        personsTree2 = emptyList()
                        spinnerAnchorPerson2.setDarkAdapter(this@MergeBranchesActivity, listOf("Enter and verify code above"))
                        btnExecuteSynthesis.isEnabled = false
                    } else {
                        val selPos1 = spinnerTree1.selectedItemPosition
                        val myTree = if (selPos1 in allPersonalTrees.indices) allPersonalTrees[selPos1] else allPersonalTrees.firstOrNull()
                        val myTreeId = myTree?.id.orEmpty()

                        if (tree.id == myTreeId) {
                            tvInviteCodeResult.text = "⚠️ Tree '${tree.name}' is already selected as Primary Tree A."
                            tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.audit_warning_text))
                            verifiedExternalTree = null
                            layoutMergeRequestStatus.visibility = View.GONE
                            personsTree2 = emptyList()
                            spinnerAnchorPerson2.setDarkAdapter(this@MergeBranchesActivity, listOf("Select a different external tree"))
                            btnExecuteSynthesis.isEnabled = false
                            return@findTreeByMergeInviteCode
                        }

                        verifiedExternalTree = tree
                        tvInviteCodeResult.text = "✓ Clan Tree: ${tree.name} (${tree.memberCount} members)"
                        tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.status_mint_text))
                        updateDefaultMasterTreeName()

                        val currentUserId = authHelper.getCurrentUserId().orEmpty()

                        firestoreHelper.findActiveClanMergeRequest(
                            requesterOwnerId = currentUserId,
                            requesterTreeId = myTreeId,
                            targetTreeId = tree.id,
                            onSuccess = { activeReq ->
                                applyRequestState(activeReq)
                            },
                            onFailure = {
                                applyRequestState(null)
                            }
                        )
                    }
                },
                onFailure = { err ->
                    btnLookupInviteCode.isEnabled = true
                    tvInviteCodeResult.text = "❌ Lookup failed: ${err.message}"
                    tvInviteCodeResult.setTextColor(androidx.core.content.ContextCompat.getColor(this@MergeBranchesActivity, R.color.danger))
                }
            )
        }

        btnSendMergeRequest.setOnClickListener {
            val target = verifiedExternalTree ?: return@setOnClickListener
            val selPos1 = spinnerTree1.selectedItemPosition
            val myTree = if (selPos1 in allPersonalTrees.indices) allPersonalTrees[selPos1] else allPersonalTrees.firstOrNull() ?: return@setOnClickListener
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "Owner"

            val newReq = ClanMergeRequest(
                code = etTree2InviteCode.text.toString().trim().uppercase(),
                requesterTreeId = myTree.id,
                requesterTreeName = myTree.name,
                requesterOwnerId = currentUserId,
                requesterOwnerName = currentUserName,
                targetTreeId = target.id,
                targetTreeName = target.name,
                targetOwnerId = target.ownerId,
                targetOwnerName = target.ownerName
            )
            btnSendMergeRequest.isEnabled = false
            firestoreHelper.createClanMergeRequest(newReq,
                onSuccess = { createdReq ->
                    KinTraceAlert.show(this@MergeBranchesActivity, "📩 Merge request sent to ${target.ownerName.ifBlank { "tree owner" }}!")
                    applyRequestState(createdReq)
                },
                onFailure = { err ->
                    btnSendMergeRequest.isEnabled = true
                    KinTraceAlert.show(this@MergeBranchesActivity, "Failed to send request: ${err.message}")
                }
            )
        }

        btnCancelMergeRequest.setOnClickListener {
            val reqId = activePendingRequestId ?: return@setOnClickListener
            btnCancelMergeRequest.isEnabled = false
            firestoreHelper.updateClanMergeRequestStatus(reqId, ClanMergeRequest.STATUS_CANCELLED,
                onSuccess = {
                    KinTraceAlert.show(this@MergeBranchesActivity, "Request cancelled.")
                    applyRequestState(null)
                },
                onFailure = { err ->
                    btnCancelMergeRequest.isEnabled = true
                    KinTraceAlert.show(this@MergeBranchesActivity, "Failed to cancel: ${err.message}")
                }
            )
        }

        dialog.setOnDismissListener {
            dialogRequestRegistration?.remove()
            previewExecutor.shutdownNow()
        }

        // Initial loads
        loadTree1Members()
        loadTree2Members()
        updateDefaultMasterTreeName()

        btnCancelSynthesis.setOnClickListener { dialog.dismiss() }

        btnExecuteSynthesis.setOnClickListener {
            val masterTreeName = etMasterTreeName.text.toString().trim()
            if (masterTreeName.isBlank()) {
                KinTraceAlert.show(this, "Please enter a name for the Master Clan Tree.")
                return@setOnClickListener
            }

            if (!rbTree2MyTrees.isChecked && activeApprovedRequestId == null) {
                KinTraceAlert.show(this, "Explicit approval from the target tree owner is required before synthesis.")
                return@setOnClickListener
            }

            if (personsTree1.isEmpty() || personsTree2.isEmpty()) {
                KinTraceAlert.show(this, "Both trees must contain members to synthesize a clan tree.")
                return@setOnClickListener
            }

            val idx1 = spinnerAnchorPerson1.selectedItemPosition
            val idx2 = spinnerAnchorPerson2.selectedItemPosition

            if (idx1 !in personsTree1.indices || idx2 !in personsTree2.indices) {
                KinTraceAlert.show(this, "Please select anchor connection members from both trees.")
                return@setOnClickListener
            }

            val anchor1 = personsTree1[idx1]
            val anchor2 = personsTree2[idx2]

            btnExecuteSynthesis.isEnabled = false
            btnCancelSynthesis.isEnabled = false
            progressBarSynthesis.visibility = View.VISIBLE

            // =========================================================================
            // ZERO-RISK SYNTHESIS ENGINE: PRE-FLIGHT SIMULATION & VERIFIED PERSISTENCE
            // =========================================================================
            val mode = when {
                rbBridgeSpouse.isChecked -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SPOUSAL_UNION
                rbBridgeParentChild.isChecked -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.PARENT_CHILD
                rbBridgeSharedAncestor.isChecked -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SHARED_ANCESTOR
                rbBridgeSibling?.isChecked == true -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SIBLING_LINEAGE
                else -> com.example.btproject2.engine.MergeBridgeValidator.BridgeMode.SPOUSAL_UNION
            }

            val masterTreeId = UUID.randomUUID().toString()
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "Owner"

            val config = com.example.btproject2.engine.MergeSimulationEngine.BridgeConfig(
                mode = mode,
                personAId = anchor1.id,
                personBId = anchor2.id,
                masterTreeId = masterTreeId,
                currentUserId = currentUserId
            )

            val selPos1 = spinnerTree1.selectedItemPosition
            val tree1 = allPersonalTrees.getOrNull(selPos1)
            val tree2 = if (rbTree2MyTrees.isChecked) {
                val selPos2 = spinnerTree2.selectedItemPosition
                allPersonalTrees.getOrNull(selPos2)
            } else {
                externalTreeLoaded
            }

            // Phase 1: Run pre-flight 200+ member simulation on a background worker thread to prevent UI ANRs
            Thread {
                val simResult = mergeSimulationEngine.simulate(personsTree1, personsTree2, config)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (!simResult.isSafe) {
                        progressBarSynthesis.visibility = View.GONE
                        btnExecuteSynthesis.isEnabled = true
                        btnCancelSynthesis.isEnabled = true

                        val collisionRecord = com.example.btproject2.models.MergeCollisionRecord(
                            sourceTree1Id = tree1?.id.orEmpty(),
                            sourceTree1Name = tree1?.name ?: "Branch A",
                            sourceTree2Id = tree2?.id.orEmpty(),
                            sourceTree2Name = tree2?.name ?: "Branch B",
                            personAId = anchor1.id,
                            personAName = "${anchor1.firstName} ${anchor1.lastName}".trim(),
                            personBId = anchor2.id,
                            personBName = "${anchor2.firstName} ${anchor2.lastName}".trim(),
                            conflictType = "LINEAGE_COLLISION",
                            conflictSummary = simResult.blockReason.orEmpty(),
                            conflictDetails = "Pre-flight validation blocked merge between ${tree1?.name ?: "Branch A"} and ${tree2?.name ?: "Branch B"}: ${simResult.blockReason.orEmpty()}",
                            suggestedResolution = "Resolve conflicting lineage or relationship rule in System Admin Citadel.",
                            escalatedBy = currentUserName
                        )
                        firestoreHelper.saveMergeCollision(collisionRecord)

                        KinTraceAlert.show(this, "Synthesis Blocked: ${simResult.blockReason.orEmpty()}\nEscalated to Ops & Recovery Desk.")
                        return@runOnUiThread
                    }

                    val clonedList = simResult.simulatedMembers.toMutableList()
                    val bridgeDesc = simResult.bridgeDescription

                    val owner1Id = tree1?.ownerId?.takeIf { it.isNotBlank() } ?: currentUserId
                    val owner1Name = tree1?.ownerName?.takeIf { it.isNotBlank() } ?: currentUserName
                    val owner2Id = tree2?.ownerId?.takeIf { it.isNotBlank() } ?: ""
                    val owner2Name = tree2?.ownerName?.takeIf { it.isNotBlank() } ?: ""

                    val coOwners = listOfNotNull(owner1Id, owner2Id).filter { it.isNotBlank() }.distinct()

                    // 4. Create Master Tree C record
                    val masterTree = FamilyTree(
                        id = masterTreeId,
                        name = masterTreeName,
                        ownerId = currentUserId,
                        ownerName = currentUserName,
                        memberCount = clonedList.size,
                        createdAt = System.currentTimeMillis(),
                        treeType = FamilyTree.TREE_TYPE_MERGED_CLAN,
                        bridgeDescription = bridgeDesc,
                        sourceTree1Id = tree1?.id ?: "",
                        sourceTree2Id = tree2?.id ?: "",
                        coOwnerIds = coOwners
                    )

                    // Determine partner tree owner
                    val partnerOwnerId = if (owner2Id.isNotBlank() && owner2Id != currentUserId) owner2Id
                                         else if (owner1Id.isNotBlank() && owner1Id != currentUserId) owner1Id
                                         else null
                    val partnerOwnerName = if (partnerOwnerId == owner2Id) owner2Name else owner1Name

                    val reqIdToComplete = activeApprovedRequestId

                    // 5. Commit chunked batch write (200 docs per batch)
                    firestoreHelper.saveMasterTreeAndMembers(
                        masterTree = masterTree,
                        members = clonedList,
                        partnerTreeOwnerId = partnerOwnerId,
                        partnerTreeOwnerName = partnerOwnerName,
                        onSuccess = { createdTree ->
                            if (reqIdToComplete != null) {
                                firestoreHelper.updateClanMergeRequestStatus(
                                    requestId = reqIdToComplete,
                                    newStatus = ClanMergeRequest.STATUS_COMPLETED,
                                    masterTreeId = createdTree.id
                                )
                            }

                            // Save Pre-Merge Snapshot for 1-Tap Undo Rollback
                            val snapshot = MergeRollbackSnapshot(
                                ownerId = currentUserId,
                                snapshotType = MergeRollbackSnapshot.TYPE_CLAN_SYNTHESIS,
                                titleSummary = "Synthesized: ${createdTree.name}",
                                synthesizedMasterTreeId = createdTree.id,
                                synthesizedMasterTreeName = createdTree.name,
                                sourceTree1Id = tree1?.id.orEmpty(),
                                sourceTree1Name = tree1?.name ?: "Tree 1",
                                sourceTree2Id = tree2?.id.orEmpty(),
                                sourceTree2Name = tree2?.name ?: "Tree 2",
                                combinedMemberCount = clonedList.size,
                                bridgeDescription = bridgeDesc
                            )
                            firestoreHelper.saveMergeRollbackSnapshot(snapshot, onSuccess = {
                                refreshMergeRollbackBanner()
                            })
                            refreshMergeRollbackBanner()

                            dialog.dismiss()
                            KinTraceAlert.show(this, "Master Clan Tree synthesized! Pre-Merge Snapshot saved.")
                            loadClanTrees()

                            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                                .setTitle("Master Clan Tree Created!")
                                .setMessage("'${createdTree.name}' has been created with ${createdTree.memberCount} members in your Merged Clan Space.\n\n🛡️ Pre-Merge Snapshot Active: Your original family trees remain untouched, and you can undo this synthesis in 1 tap from the Merge screen.\n\nWould you like to explore the Clan Canvas now?")
                                .setPositiveButton("👁️ View Clan Canvas") { _, _ ->
                                    val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                                        putExtra("TREE_ID", createdTree.id)
                                        putExtra("treeId", createdTree.id)
                                        putExtra("IS_CLAN_SPACE", true)
                                    }
                                    startActivity(intent)
                                }
                                .setNegativeButton("Done", null)
                                .show()
                        },
                        onFailure = { err ->
                            if (reqIdToComplete != null) {
                                firestoreHelper.updateClanMergeRequestStatus(
                                    requestId = reqIdToComplete,
                                    newStatus = ClanMergeRequest.STATUS_FAILED
                                )
                            }
                            progressBarSynthesis.visibility = View.GONE
                            btnExecuteSynthesis.isEnabled = true
                            btnCancelSynthesis.isEnabled = true
                            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                                .setTitle("Synthesis Failed")
                                .setMessage("An error occurred while saving the Master Clan Tree: ${err.message}")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    )
                }
            }.start()
        }

        dialog.setOnDismissListener {
            dialogRequestRegistration?.remove()
            dialogRequestRegistration = null
        }

        dialog.show()
    }

    // =========================================================================
    // INTRA-TREE BRANCH CONNECTOR (CONNECT DISCONNECTED NODES IN ACTIVE TREE)
    // =========================================================================
    private fun showConnectBranchesDialog() {
        if (currentTreePersons.size < 2) {
            KinTraceAlert.show(this, "At least 2 members are required to connect internal branches.")
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connect_branches, null)
        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()

        val spinnerA = dialogView.findViewById<Spinner>(R.id.spinnerBranchMemberA)
        val spinnerB = dialogView.findViewById<Spinner>(R.id.spinnerBranchMemberB)
        val spinnerConn = dialogView.findViewById<Spinner>(R.id.spinnerConnectionType)
        val layoutCycleWarning = dialogView.findViewById<LinearLayout>(R.id.layoutCycleWarning)
        val tvCycleWarning = dialogView.findViewById<TextView>(R.id.tvCycleWarningText)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmConnect)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelConnect)

        val names = currentTreePersons.map { "${it.firstName} ${it.lastName}" }
        spinnerA.setDarkAdapter(this, names)
        spinnerB.setDarkAdapter(this, names)

        if (currentTreePersons.size > 1) {
            spinnerB.setSelection(1)
        }

        val connOptions = listOf(
            "Spouse (Marital Union)",
            "Member A is Father of Member B",
            "Member A is Mother of Member B",
            "Member A is Child of Member B"
        )
        spinnerConn.setDarkAdapter(this, connOptions)

        fun validateConnection(): Boolean {
            val idxA = spinnerA.selectedItemPosition
            val idxB = spinnerB.selectedItemPosition
            val connPos = spinnerConn.selectedItemPosition

            if (idxA < 0 || idxB < 0 || idxA == idxB) {
                layoutCycleWarning.visibility = View.VISIBLE
                tvCycleWarning.text = "⚠️ Please select two different family members."
                btnConfirm.isEnabled = false
                return false
            }

            val pA = currentTreePersons[idxA]
            val pB = currentTreePersons[idxB]

            when (connPos) {
                0 -> { // Spouse
                    if (pA.spouseId != null && pA.spouseId != pB.id) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ ${pA.firstName} is already married to another member."
                        btnConfirm.isEnabled = false
                        return false
                    }
                    if (pB.spouseId != null && pB.spouseId != pA.id) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ ${pB.firstName} is already married to another member."
                        btnConfirm.isEnabled = false
                        return false
                    }
                    val sCheck = FamilyLinkValidator.validateSpouse(pA, pB, currentTreePersons)
                    if (!sCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Prohibited Spouse: ${sCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                1 -> { // Father
                    val pCheck = FamilyLinkValidator.validateParentChild(pA, pB, "father", currentTreePersons)
                    if (!pCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Invalid Parent Link: ${pCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                2 -> { // Mother
                    val pCheck = FamilyLinkValidator.validateParentChild(pA, pB, "mother", currentTreePersons)
                    if (!pCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Invalid Parent Link: ${pCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                3 -> { // Child
                    val cCheck = FamilyLinkValidator.validateParentChild(pB, pA, "parent", currentTreePersons)
                    if (!cCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Invalid Child Link: ${cCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
            }

            layoutCycleWarning.visibility = View.GONE
            btnConfirm.isEnabled = true
            return true
        }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                validateConnection()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerA.onItemSelectedListener = listener
        spinnerB.onItemSelectedListener = listener
        spinnerConn.onItemSelectedListener = listener

        validateConnection()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            if (!validateConnection()) return@setOnClickListener

            val idxA = spinnerA.selectedItemPosition
            val idxB = spinnerB.selectedItemPosition
            val connPos = spinnerConn.selectedItemPosition

            val pA = currentTreePersons[idxA]
            val pB = currentTreePersons[idxB]

            btnConfirm.isEnabled = false
            btnConfirm.text = "Connecting..."

            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            val connDesc = "${pA.firstName} ${pA.lastName} ↔ ${pB.firstName} ${pB.lastName} (${connOptions.getOrElse(connPos) { "Branch Link" }})"
            val intraSnapshot = MergeRollbackSnapshot(
                ownerId = currentUserId,
                snapshotType = MergeRollbackSnapshot.TYPE_INTRA_BRANCH,
                titleSummary = "Internal Branch Link: $connDesc",
                bridgeDescription = connDesc,
                intraTreeId = activeTreeId,
                previousPersonA = pA.copy(),
                previousPersonB = pB.copy()
            )

            fun saveIntraSnapshotAndRefresh() {
                firestoreHelper.saveMergeRollbackSnapshot(intraSnapshot, onSuccess = {
                    refreshMergeRollbackBanner()
                })
                refreshMergeRollbackBanner()
            }

            when (connPos) {
                0 -> { // Spouse
                    val updatedA = pA.copy(spouseId = pB.id)
                    val updatedB = pB.copy(spouseId = pA.id)
                    firestoreHelper.updatePerson(updatedA, {
                        firestoreHelper.updatePerson(updatedB, {
                            saveIntraSnapshotAndRefresh()
                            CentralTreeSynchronizer.getInstance().notifySpouseChanged(updatedA, updatedB)
                            dialog.dismiss()
                            KinTraceAlert.show(this, "Spousal link created! Pre-Merge Snapshot saved.")
                            loadPersonalTreesAndMembers()
                        }, { dialog.dismiss() })
                    }, { dialog.dismiss() })
                }
                1 -> { // A is Father of B
                    val updatedB = pB.copy(fatherId = pA.id)
                    firestoreHelper.updatePerson(updatedB, {
                        saveIntraSnapshotAndRefresh()
                        CentralTreeSynchronizer.getInstance().notifyParentChildChanged(updatedB, pA, "father")
                        dialog.dismiss()
                        KinTraceAlert.show(this, "Paternal branch connected! Pre-Merge Snapshot saved.")
                        loadPersonalTreesAndMembers()
                    }, { dialog.dismiss() })
                }
                2 -> { // A is Mother of B
                    val updatedB = pB.copy(motherId = pA.id)
                    firestoreHelper.updatePerson(updatedB, {
                        saveIntraSnapshotAndRefresh()
                        CentralTreeSynchronizer.getInstance().notifyParentChildChanged(updatedB, pA, "mother")
                        dialog.dismiss()
                        KinTraceAlert.show(this, "Maternal branch connected! Pre-Merge Snapshot saved.")
                        loadPersonalTreesAndMembers()
                    }, { dialog.dismiss() })
                }
                3 -> { // A is Child of B
                    val updatedA = if (pB.gender.equals("Female", ignoreCase = true)) {
                        pA.copy(motherId = pB.id)
                    } else {
                        pA.copy(fatherId = pB.id)
                    }
                    firestoreHelper.updatePerson(updatedA, {
                        saveIntraSnapshotAndRefresh()
                        CentralTreeSynchronizer.getInstance().notifyParentChildChanged(updatedA, pB, "parent")
                        dialog.dismiss()
                        KinTraceAlert.show(this, "Branch connected! Pre-Merge Snapshot saved.")
                        loadPersonalTreesAndMembers()
                    }, { dialog.dismiss() })
                }
            }
        }

        dialog.show()
    }
}
