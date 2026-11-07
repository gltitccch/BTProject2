package com.example.btproject2.ui.activities

import com.example.btproject2.utils.KinTraceAlert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.InviteCodeRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.models.TreeMember
import com.example.btproject2.ui.adapters.AuthorizedMemberAdapter
import com.example.btproject2.ui.adapters.PendingRequestAdapter
import com.example.btproject2.utils.NotificationHelper

class PrivacyControlsActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private var treeId = ""

    private lateinit var layoutOwnerView: View
    private lateinit var layoutOwnerGate: View
    private lateinit var btnGateBackToHome: Button

    private lateinit var switchPrivateTree: Switch
    private lateinit var switchHideBirthDates: Switch
    private lateinit var switchHideNotes: Switch
    private lateinit var switchHideDeceasedStatus: Switch
    private lateinit var btnSavePrivacy: Button
    private lateinit var btnInviteMember: TextView

    private lateinit var rvAuthorizedMembers: RecyclerView
    private lateinit var layoutPendingRequests: LinearLayout
    private lateinit var rvPendingRequests: RecyclerView

    private lateinit var authorizedAdapter: AuthorizedMemberAdapter
    private lateinit var pendingAdapter: PendingRequestAdapter

    private var cardPendingJoinRequestsTile: View? = null
    private var tvPendingJoinCountBadge: TextView? = null
    private var tvPendingJoinRequestsSubtitle: TextView? = null
    private var btnOpenPendingJoinRequestsSheet: View? = null
    private var currentPendingJoinMembers: List<TreeMember> = emptyList()

    private var cardOutgoingPendingJoinTile: View? = null
    private var tvOutgoingPendingTreeTitle: TextView? = null
    private var tvOutgoingPendingTreeSubtitle: TextView? = null
    private var viewOutgoingMiniHourglass: com.example.btproject2.views.PendingSandHourglassView? = null

    private var cardPendingRequestsTile: View? = null
    private var tvPendingCountBadge: TextView? = null
    private var btnOpenPendingRequestsSheet: View? = null
    private var currentPendingMembers: List<TreeMember> = emptyList()

    private var layoutApplicantPendingJoin: View? = null
    private var viewPrivacyPendingHourglass: com.example.btproject2.views.PendingSandHourglassView? = null
    private var tvPrivacyPendingTreeTitle: TextView? = null
    private var tvPrivacyPendingMessage: TextView? = null
    private var btnPrivacyWithdrawJoinRequest: Button? = null
    private var btnPrivacyPendingBackHome: Button? = null

    private var treeMembersListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var hasAutoOpenedJoinSheet: Boolean = false

    private var currentTree: FamilyTree? = null
    private var currentSettings = PrivacySettings(treeId = treeId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_controls)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        layoutOwnerView = findViewById(R.id.layoutOwnerView)
        layoutOwnerGate = findViewById(R.id.layoutOwnerGate)
        btnGateBackToHome = findViewById(R.id.btnGateBackToHome)

        switchPrivateTree = findViewById(R.id.switchPrivateTree)
        switchHideBirthDates = findViewById(R.id.switchHideBirthDates)
        switchHideNotes = findViewById(R.id.switchHideNotes)
        switchHideDeceasedStatus = findViewById(R.id.switchHideDeceasedStatus)
        btnSavePrivacy = findViewById(R.id.btnSavePrivacy)
        btnInviteMember = findViewById(R.id.btnInviteMember)

        rvAuthorizedMembers = findViewById(R.id.rvAuthorizedMembers)
        layoutPendingRequests = findViewById(R.id.layoutPendingRequests)
        rvPendingRequests = findViewById(R.id.rvPendingRequests)

        cardPendingJoinRequestsTile = findViewById(R.id.cardPendingJoinRequestsTile)
        tvPendingJoinCountBadge = findViewById(R.id.tvPendingJoinCountBadge)
        tvPendingJoinRequestsSubtitle = findViewById(R.id.tvPendingJoinRequestsSubtitle)
        btnOpenPendingJoinRequestsSheet = findViewById(R.id.btnOpenPendingJoinRequestsSheet)

        btnOpenPendingJoinRequestsSheet?.setOnClickListener {
            showPendingJoinRequestsBottomSheet()
        }
        cardPendingJoinRequestsTile?.setOnClickListener {
            showPendingJoinRequestsBottomSheet()
        }

        cardOutgoingPendingJoinTile = findViewById(R.id.cardOutgoingPendingJoinTile)
        tvOutgoingPendingTreeTitle = findViewById(R.id.tvOutgoingPendingTreeTitle)
        tvOutgoingPendingTreeSubtitle = findViewById(R.id.tvOutgoingPendingTreeSubtitle)
        viewOutgoingMiniHourglass = findViewById(R.id.viewOutgoingMiniHourglass)

        cardPendingRequestsTile = findViewById(R.id.cardPendingRequestsTile)
        tvPendingCountBadge = findViewById(R.id.tvPendingCountBadge)
        btnOpenPendingRequestsSheet = findViewById(R.id.btnOpenPendingRequestsSheet)

        btnOpenPendingRequestsSheet?.setOnClickListener {
            showPendingRequestsBottomSheet()
        }
        cardPendingRequestsTile?.setOnClickListener {
            showPendingRequestsBottomSheet()
        }

        layoutApplicantPendingJoin = findViewById(R.id.layoutApplicantPendingJoin)
        viewPrivacyPendingHourglass = findViewById(R.id.viewPrivacyPendingHourglass)
        tvPrivacyPendingTreeTitle = findViewById(R.id.tvPrivacyPendingTreeTitle)
        tvPrivacyPendingMessage = findViewById(R.id.tvPrivacyPendingMessage)
        btnPrivacyWithdrawJoinRequest = findViewById(R.id.btnPrivacyWithdrawJoinRequest)
        btnPrivacyPendingBackHome = findViewById(R.id.btnPrivacyPendingBackHome)

        btnPrivacyPendingBackHome?.setOnClickListener { finish() }

        btnBack.setOnClickListener { finish() }

        val btnQuickAssistPrivacy = findViewById<TextView>(R.id.btnQuickAssistPrivacy)
        btnQuickAssistPrivacy?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.PRIVACY_CONTROLS
            ).show(supportFragmentManager, "QuickAssistPrivacy")
        }

        btnGateBackToHome.setOnClickListener { finish() }

        val btnGateSendLeafRequest = findViewById<Button?>(R.id.btnGateSendLeafRequest)
        btnGateSendLeafRequest?.setOnClickListener {
            val user = authHelper.getCurrentUser()
            val userId = user?.uid.orEmpty()
            val userName = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")
                ?: "A Family Member"
            val userEmail = user?.email.orEmpty()

            btnGateSendLeafRequest.isEnabled = false
            btnGateSendLeafRequest.text = "🍃 Floating Golden Leaf to Tree Owner..."

            firestoreHelper.sendRoleUpgradeRequestToOwner(
                treeId = treeId,
                treeName = currentTree?.name.orEmpty(),
                ownerId = currentTree?.ownerId.orEmpty(),
                requesterId = userId,
                requesterName = userName,
                currentRole = "Viewer",
                requestedRole = "Editor",
                featureName = "Privacy & Access Control",
                requesterEmail = userEmail,
                onComplete = { success ->
                    if (success) {
                        btnGateSendLeafRequest.text = "🌳 Golden Leaf Reached the Tree Owner!"
                        Toast.makeText(
                            this,
                            "🍃 Your Golden Leaf request was dispatched to the Tree Owner!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        btnGateSendLeafRequest.isEnabled = true
                        btnGateSendLeafRequest.text = "🍃 Send Golden Leaf Request to Tree Owner"
                        Toast.makeText(
                            this,
                            "Could not reach Tree Owner. Please check your connection.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        setupRecyclerViews()
        checkOwnerAccessAndLoad()

        btnInviteMember.setOnClickListener {
            showInviteDialog()
        }

        btnSavePrivacy.setOnClickListener {
            savePrivacySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        checkOutgoingPendingJoinForOwner()
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.PRIVACY_CONTROLS,
            treeId = treeId
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        treeMembersListener?.remove()
        treeMembersListener = null
    }

    private fun setupRecyclerViews() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        authorizedAdapter = AuthorizedMemberAdapter(
            isOwner = true,
            currentUserId = currentUserId,
            onSetRole = { member, newRole ->
                updateMemberRole(member, newRole)
            },
            onRemoveMember = { member ->
                removeMember(member)
            },
            onRoleClick = { member ->
                showMemberRoleDialog(member)
            }
        )
        rvAuthorizedMembers.layoutManager = LinearLayoutManager(this)
        rvAuthorizedMembers.adapter = authorizedAdapter

        pendingAdapter = PendingRequestAdapter(
            onApprove = { member -> approveMember(member) },
            onReject = { member -> rejectMember(member) }
        )
        rvPendingRequests.layoutManager = LinearLayoutManager(this)
        rvPendingRequests.adapter = pendingAdapter
    }

    private fun checkOwnerAccessAndLoad() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        val effectiveTreeId = treeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        if (effectiveTreeId.isNotBlank()) {
            this.treeId = effectiveTreeId
        }

        if (effectiveTreeId.isBlank()) {
            if (currentUserId.isNotBlank()) {
                firestoreHelper.getUserTrees(currentUserId,
                    onSuccess = { trees ->
                        if (trees.isNotEmpty()) {
                            val resolved = trees.first()
                            this.treeId = resolved.id
                            com.example.btproject2.utils.TreePreferences.setActiveTree(this, resolved.id, resolved.name)
                            loadTreeData(resolved, currentUserId)
                        } else {
                            // Check if this user has an active Pending Join Request waiting for acceptance
                            checkApplicantPendingJoinStatus(currentUserId)
                        }
                    },
                    onFailure = {
                        checkApplicantPendingJoinStatus(currentUserId)
                    }
                )
            } else {
                currentTree = null
                layoutOwnerView.visibility = View.VISIBLE
                layoutOwnerGate.visibility = View.GONE
            }
            return
        }

        firestoreHelper.getTree(effectiveTreeId,
            onSuccess = { tree ->
                val activeTree = if (tree != null) {
                    if (tree.inviteCode.isBlank() && tree.id.isNotBlank()) {
                        val newCode = generateInviteCode()
                        firestoreHelper.updateTreeInviteCode(tree.id, newCode, tree.name, tree.ownerId)
                        val record = InviteCodeRecord(
                            code = newCode,
                            treeId = tree.id,
                            treeName = tree.name,
                            createdBy = tree.ownerId.ifBlank { currentUserId },
                            createdAt = System.currentTimeMillis(),
                            expiresAt = 0L,
                            usageLimit = 0,
                            usageCount = 0,
                            status = InviteCodeRecord.STATUS_ACTIVE
                        )
                        firestoreHelper.saveInviteCodeRecord(record)
                        tree.copy(inviteCode = newCode)
                    } else {
                        if (tree.inviteCode.isNotBlank()) {
                            val record = InviteCodeRecord(
                                code = tree.inviteCode.replace(" ", "").trim().uppercase(),
                                treeId = tree.id,
                                treeName = tree.name,
                                createdBy = tree.ownerId.ifBlank { currentUserId },
                                createdAt = System.currentTimeMillis(),
                                expiresAt = 0L,
                                usageLimit = 0,
                                usageCount = 0,
                                status = InviteCodeRecord.STATUS_ACTIVE
                            )
                            firestoreHelper.saveInviteCodeRecord(record)
                        }
                        tree
                    }
                } else {
                    val newCode = generateInviteCode()
                    val fallbackName = com.example.btproject2.utils.TreePreferences.getActiveTreeName(this).ifBlank { "Family Tree" }
                    FamilyTree(
                        id = effectiveTreeId,
                        name = fallbackName,
                        ownerId = currentUserId,
                        ownerName = authHelper.getCurrentUser()?.displayName ?: "Owner",
                        inviteCode = newCode
                    ).also {
                        firestoreHelper.createTree(it, onSuccess = {}, onFailure = {})
                    }
                }
                loadTreeData(activeTree, currentUserId)
            },
            onFailure = {
                // Fail-closed if tree ownership cannot be verified
                layoutOwnerView.visibility = View.GONE
                layoutOwnerGate.visibility = View.GONE
                com.example.btproject2.utils.BotanicalRoleFlashManager.showRestrictedActionFlash(
                    activity = this,
                    rawRole = "Viewer",
                    isTreeOwner = false,
                    featureTitle = "Deep Roots Privacy & Access",
                    requiredTier = com.example.btproject2.utils.BotanicalRolePolicy.BotanicalTier.CANOPY_OWNER,
                    treeId = effectiveTreeId,
                    onDismiss = {
                        if (!isFinishing && !isDestroyed) {
                            finish()
                        }
                    }
                )
            }
        )
    }

    private fun loadTreeData(activeTree: FamilyTree, currentUserId: String) {
        currentTree = activeTree

        // Check if current user is owner
        val isOwner = activeTree.ownerId.isEmpty() || activeTree.ownerId == currentUserId

        if (isOwner) {
            layoutOwnerView.visibility = View.VISIBLE
            layoutOwnerGate.visibility = View.GONE
            loadOwnerData(activeTree)
        } else {
            // Keep both inline layouts hidden so ONLY the single Botanical Role Popup appears
            layoutOwnerView.visibility = View.GONE
            layoutOwnerGate.visibility = View.GONE
            bindNonOwnerBotanicalGate(activeTree, currentUserId)
        }
    }

    private fun bindNonOwnerBotanicalGate(activeTree: FamilyTree, currentUserId: String) {
        firestoreHelper.getUserRole(
            treeId = activeTree.id,
            userId = currentUserId,
            onResult = { rawRole ->
                val tier = com.example.btproject2.utils.BotanicalRolePolicy.resolveTier(rawRole, isTreeOwner = false)

                // Show ONLY ONE Botanical Role-Gate Popup; clicking "Return to Family Tree" immediately finishes & returns to main interface
                com.example.btproject2.utils.BotanicalRoleFlashManager.showRestrictedActionFlash(
                    activity = this,
                    rawRole = tier.roleKey,
                    isTreeOwner = false,
                    featureTitle = "Deep Roots Privacy & Access",
                    requiredTier = com.example.btproject2.utils.BotanicalRolePolicy.BotanicalTier.CANOPY_OWNER,
                    treeId = activeTree.id,
                    treeName = activeTree.name,
                    ownerId = activeTree.ownerId,
                    onDismiss = {
                        if (!isFinishing && !isDestroyed) {
                            finish()
                        }
                    }
                )
            }
        )
    }

    private fun checkApplicantPendingJoinStatus(currentUserId: String) {
        firestoreHelper.getUserPendingJoinRequests(
            userId = currentUserId,
            onSuccess = { pendingList ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val pending = pendingList.firstOrNull()
                    if (pending != null) {
                        layoutOwnerView.visibility = View.GONE
                        layoutOwnerGate.visibility = View.GONE
                        layoutApplicantPendingJoin?.visibility = View.VISIBLE
                        viewPrivacyPendingHourglass?.setApprovedMode(false)
                        tvPrivacyPendingTreeTitle?.text = "Pending Tree Approval"
                        tvPrivacyPendingMessage?.text = "Your request to join this family tree is waiting for acceptance from the tree owner."

                        if (pending.treeId.isNotBlank()) {
                            firestoreHelper.getTree(pending.treeId,
                                onSuccess = { tree ->
                                    val tName = tree?.name?.takeIf { it.isNotBlank() } ?: "Family Tree"
                                    runOnUiThread {
                                        tvPrivacyPendingTreeTitle?.text = "⏳ $tName"
                                        tvPrivacyPendingMessage?.text = "Your invite code request to join \"$tName\" is waiting for acceptance from the tree owner."
                                    }
                                },
                                onFailure = {}
                            )
                        }

                        btnPrivacyWithdrawJoinRequest?.setOnClickListener {
                            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                                .setTitle("Withdraw Join Request")
                                .setMessage("Cancel your pending join request?")
                                .setPositiveButton("Withdraw") { _, _ ->
                                    firestoreHelper.deleteTreeMember(pending.id,
                                        onSuccess = {
                                            KinTraceAlert.show(this, "Join request withdrawn")
                                            finish()
                                        },
                                        onFailure = {
                                            KinTraceAlert.show(this, "Could not withdraw request")
                                        }
                                    )
                                }
                                .setNegativeButton("Keep Waiting", null)
                                .show()
                        }
                    } else {
                        currentTree = null
                        layoutApplicantPendingJoin?.visibility = View.GONE
                        layoutOwnerView.visibility = View.VISIBLE
                        layoutOwnerGate.visibility = View.GONE
                    }
                }
            },
            onFailure = {
                currentTree = null
                layoutOwnerView.visibility = View.VISIBLE
                layoutOwnerGate.visibility = View.GONE
            }
        )
    }

    private fun loadOwnerData(tree: FamilyTree) {
        layoutApplicantPendingJoin?.visibility = View.GONE
        switchPrivateTree.isChecked = tree.isPrivate
        loadPrivacySettings()
        loadMembers()
        checkOutgoingPendingJoinForOwner()
    }

    private fun checkOutgoingPendingJoinForOwner() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (currentUserId.isBlank()) {
            cardOutgoingPendingJoinTile?.visibility = View.GONE
            return
        }
        firestoreHelper.getUserPendingJoinRequests(
            userId = currentUserId,
            onSuccess = { pendingList ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val outgoing = pendingList.firstOrNull { it.treeId.isNotBlank() && it.treeId != treeId }
                    if (outgoing != null) {
                        cardOutgoingPendingJoinTile?.visibility = View.VISIBLE
                        viewOutgoingMiniHourglass?.setApprovedMode(false)
                        tvOutgoingPendingTreeTitle?.text = "Pending Acceptance"
                        tvOutgoingPendingTreeSubtitle?.text = "Waiting for tree owner approval • Tap to view or cancel"
                        firestoreHelper.getTree(outgoing.treeId,
                            onSuccess = { targetTree ->
                                val tName = targetTree?.name?.takeIf { it.isNotBlank() } ?: "Family Tree"
                                val oName = targetTree?.ownerName?.takeIf { it.isNotBlank() } ?: "Tree Owner"
                                runOnUiThread {
                                    tvOutgoingPendingTreeTitle?.text = "🌳 $tName — Pending Acceptance"
                                    tvOutgoingPendingTreeSubtitle?.text = "Waiting for $oName to approve • Tap to view or cancel"
                                }
                            },
                            onFailure = {}
                        )
                        val openPendingClick = View.OnClickListener {
                            startActivity(android.content.Intent(this, JoinTreeActivity::class.java))
                        }
                        cardOutgoingPendingJoinTile?.setOnClickListener(openPendingClick)
                        findViewById<View?>(R.id.btnOpenOutgoingPendingJoin)?.setOnClickListener(openPendingClick)
                    } else {
                        cardOutgoingPendingJoinTile?.visibility = View.GONE
                    }
                }
            },
            onFailure = {
                runOnUiThread { cardOutgoingPendingJoinTile?.visibility = View.GONE }
            }
        )
    }

    private fun loadPrivacySettings() {
        firestoreHelper.getPrivacySettings(treeId,
            onSuccess = { settings ->
                currentSettings = settings
                switchPrivateTree.isChecked = settings.isPrivateTree
                switchHideBirthDates.isChecked = settings.hideBirthDates
                switchHideNotes.isChecked = settings.hideNotes
                switchHideDeceasedStatus.isChecked = settings.hideDeceasedStatus
            },
            onFailure = {}
        )
    }

    private fun loadMembers() {
        treeMembersListener?.remove()
        treeMembersListener = firestoreHelper.listenToTreeMembers(treeId) { members ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                bindTreeMembersList(members)
            }
        }
        firestoreHelper.getTreeMembers(treeId,
            onSuccess = { members ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    bindTreeMembersList(members)
                }
            },
            onFailure = {}
        )
    }

    private fun updateUnifiedPendingTileSummary() {
        val joinCount = currentPendingJoinMembers.size
        val roleCount = currentPendingMembers.size
        val total = joinCount + roleCount
        cardPendingJoinRequestsTile?.visibility = View.VISIBLE
        cardPendingRequestsTile?.visibility = View.GONE
        layoutPendingRequests.visibility = View.GONE

        tvPendingJoinCountBadge?.text = "$total Pending"
        tvPendingJoinRequestsSubtitle?.text = when {
            joinCount > 0 && roleCount > 0 ->
                "$joinCount Join Request${if (joinCount > 1) "s" else ""} • $roleCount Role Upgrade${if (roleCount > 1) "s" else ""} — tap to review"
            joinCount > 0 ->
                "$joinCount Join Request${if (joinCount > 1) "s" else ""} • 0 Role Upgrades — tap to review"
            roleCount > 0 ->
                "0 Join Requests • $roleCount Role Upgrade${if (roleCount > 1) "s" else ""} — tap to review"
            else ->
                "0 Join Requests • 0 Role Upgrades — tap to review"
        }
    }

    private fun bindTreeMembersList(members: List<TreeMember>) {
        if (members.isEmpty()) {
            // Seed initial owner member in Firestore if none exist
            val currentUserId = authHelper.getCurrentUserId() ?: ""
            val owner = TreeMember(
                id = "${treeId}_${currentUserId}".trim('_').ifBlank { "owner_default" },
                treeId = treeId,
                userId = currentUserId,
                userName = authHelper.getCurrentUser()?.displayName ?: "Tree Owner",
                userEmail = authHelper.getCurrentUser()?.email ?: "",
                role = "Owner",
                status = "Approved"
            )
            if (treeId.isNotBlank() && currentUserId.isNotBlank()) {
                firestoreHelper.addTreeMember(owner, onSuccess = {}, onFailure = {})
            }
            authorizedAdapter.submitList(listOf(owner))
            currentPendingJoinMembers = emptyList()
            currentPendingMembers = emptyList()
            updateUnifiedPendingTileSummary()
            return
        }

        val pendingJoin = members.filter {
            it.status.equals("Pending", ignoreCase = true) ||
                it.status.equals("Pending_Join", ignoreCase = true) ||
                it.role.equals("Pending_Join", ignoreCase = true)
        }
        val pendingRoleUpgrade = members.filter {
            it.status.equals("Role_Upgrade_Pending", ignoreCase = true)
        }
        val approved = members.filter {
            it.status.equals("Approved", ignoreCase = true) ||
                it.status.equals("Role_Upgrade_Pending", ignoreCase = true)
        }

        currentPendingJoinMembers = pendingJoin
        currentPendingMembers = pendingRoleUpgrade

        authorizedAdapter.submitList(approved)
        pendingAdapter.submitList(pendingRoleUpgrade)
        updateUnifiedPendingTileSummary()

        // Auto-open Unified Pending Requests Sheet if launched from a Join Request or Role Upgrade Notification click
        val shouldAutoOpenJoin = intent.getBooleanExtra("OPEN_JOIN_REQUESTS", false)
        val shouldOpenRoleTab = intent.getBooleanExtra("OPEN_ROLE_TAB", false)
        if (shouldAutoOpenJoin && !hasAutoOpenedJoinSheet) {
            hasAutoOpenedJoinSheet = true
            showUnifiedPendingRequestsHubSheet(initialTabIsRoleUpgrade = shouldOpenRoleTab)
        }
    }

    private fun showInviteDialog() {
        val targetTreeId = currentTree?.id?.takeIf { it.isNotBlank() }
            ?: treeId.takeIf { it.isNotBlank() }
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this).takeIf { it.isNotBlank() }

        if (targetTreeId.isNullOrBlank()) {
            KinTraceAlert.show(this, "Please select or create a family tree first.")
            return
        }

        val treeName = currentTree?.name ?: "Family Tree"
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        val ownerId = currentTree?.ownerId?.takeIf { it.isNotBlank() } ?: currentUserId
        val existingCode = currentTree?.inviteCode?.takeIf { it.isNotBlank() }
        val code = if (existingCode != null) {
            val normalized = existingCode.replace(" ", "").trim().uppercase()
            firestoreHelper.updateTreeInviteCode(targetTreeId, normalized, treeName, ownerId)
            val record = InviteCodeRecord(
                code = normalized,
                treeId = targetTreeId,
                treeName = treeName,
                createdBy = ownerId,
                createdAt = System.currentTimeMillis(),
                expiresAt = 0L,
                usageLimit = 0,
                usageCount = 0,
                status = InviteCodeRecord.STATUS_ACTIVE
            )
            firestoreHelper.saveInviteCodeRecord(record)
            normalized
        } else {
            val generated = generateInviteCode()
            firestoreHelper.updateTreeInviteCode(targetTreeId, generated, treeName, ownerId)
            val record = InviteCodeRecord(
                code = generated,
                treeId = targetTreeId,
                treeName = treeName,
                createdBy = ownerId,
                createdAt = System.currentTimeMillis(),
                expiresAt = 0L,
                usageLimit = 0,
                usageCount = 0,
                status = InviteCodeRecord.STATUS_ACTIVE
            )
            firestoreHelper.saveInviteCodeRecord(record)
            currentTree = currentTree?.copy(id = targetTreeId, inviteCode = generated)
                ?: FamilyTree(id = targetTreeId, inviteCode = generated)
            generated
        }

        val view = layoutInflater.inflate(R.layout.dialog_invite_code, null)
        val tvCode = view.findViewById<TextView>(R.id.tvInviteCode)
        val btnCopy = view.findViewById<Button>(R.id.btnCopyCode)

        tvCode.text = code

        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Done", null)
            .create()

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("KinTrace Tree Invite Code", code)
            clipboard.setPrimaryClip(clip)
            KinTraceAlert.show(this, "Invite code $code copied to clipboard!")
        }

        dialog.show()
    }

    private fun showMemberRoleDialog(member: TreeMember) {
        val options = arrayOf("Editor (Can add/edit)", "Viewer (View only)", "Remove from tree")
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("${member.userName.ifBlank { member.userEmail }}'s Access")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> updateMemberRole(member, "Editor")
                    1 -> updateMemberRole(member, "Viewer")
                    2 -> removeMember(member)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMemberRole(member: TreeMember, newRole: String) {
        firestoreHelper.updateTreeMemberRole(member.id, newRole, "Approved",
            onSuccess = {
                // 8.c Role change alert
                if (member.userId.isNotEmpty()) {
                    val treeName = currentTree?.name ?: "Family Tree"
                    val notif = NotificationHelper.createRoleUpdatedNotification(
                        treeId = treeId,
                        userId = member.userId,
                        treeName = treeName,
                        newRole = newRole
                    )
                    firestoreHelper.addNotification(notif)
                }

                KinTraceAlert.show(this, "Updated ${member.userName}'s role to $newRole")
                loadMembers()
            },
            onFailure = {
                KinTraceAlert.show(this, "Failed to update role")
            }
        )
    }

    private fun removeMember(member: TreeMember) {
        val displayName = member.userName.ifBlank { member.userEmail.ifBlank { "this member" } }
        val roleLabel = member.role.ifBlank { "Member" }
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Remove $displayName?")
            .setMessage("Are you sure you want to remove $displayName ($roleLabel) from this family tree?\n\n• Their access to this tree will be immediately revoked.\n• If they currently have this tree open, they will be switched out automatically.")
            .setPositiveButton("Remove from Tree") { _, _ ->
                firestoreHelper.revokeTreeMemberAccess(
                    member = member,
                    treeId = treeId,
                    onSuccess = {
                        // 8.c Access revocation alert
                        if (member.userId.isNotEmpty()) {
                            val treeName = currentTree?.name ?: "Family Tree"
                            val notif = NotificationRecord(
                                treeId = treeId,
                                userId = member.userId,
                                title = "Tree Access Revoked",
                                message = "You have been removed from '$treeName' by the tree owner.",
                                type = NotificationHelper.CATEGORY_SECURITY,
                                targetId = treeId,
                                timestamp = System.currentTimeMillis()
                            )
                            firestoreHelper.addNotification(notif)
                        }

                        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyTreeLayoutChanged(
                            treeId = treeId,
                            sourceScreen = "PrivacyControlsActivity"
                        )

                        KinTraceAlert.show(this, "🚫 $displayName removed from tree")
                        loadMembers()
                    },
                    onFailure = {
                        KinTraceAlert.show(this, "Failed to remove member: ${it.message}")
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPendingJoinRequestsBottomSheet() {
        showUnifiedPendingRequestsHubSheet(initialTabIsRoleUpgrade = false)
    }

    private fun showPendingRequestsBottomSheet() {
        showUnifiedPendingRequestsHubSheet(initialTabIsRoleUpgrade = true)
    }

    private fun showUnifiedPendingRequestsHubSheet(initialTabIsRoleUpgrade: Boolean = false) {
        val sheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.layout_pending_requests_sheet, null)
        sheetDialog.setContentView(sheetView)

        val tvSheetHeaderIcon = sheetView.findViewById<TextView?>(R.id.tvSheetHeaderIcon)
        val tvSheetTitle = sheetView.findViewById<TextView?>(R.id.tvSheetTitle)
        val tvSheetSubtitle = sheetView.findViewById<TextView?>(R.id.tvSheetSubtitle)
        val tvSheetCountBadge = sheetView.findViewById<TextView?>(R.id.tvSheetCountBadge)
        val btnSheetClose = sheetView.findViewById<View?>(R.id.btnSheetClose)
        val btnTabJoinRequests = sheetView.findViewById<TextView?>(R.id.btnTabJoinRequests)
        val btnTabRoleUpgrades = sheetView.findViewById<TextView?>(R.id.btnTabRoleUpgrades)
        val btnApproveAllRequests = sheetView.findViewById<Button?>(R.id.btnApproveAllRequests)
        val rvSheetPendingRequests = sheetView.findViewById<RecyclerView?>(R.id.rvSheetPendingRequests)
        val layoutSheetEmptyState = sheetView.findViewById<View?>(R.id.layoutSheetEmptyState)
        val tvSheetEmptyTitle = sheetView.findViewById<TextView?>(R.id.tvSheetEmptyTitle)
        val tvSheetEmptySub = sheetView.findViewById<TextView?>(R.id.tvSheetEmptySub)

        btnSheetClose?.setOnClickListener { sheetDialog.dismiss() }

        val joinList = currentPendingJoinMembers.toMutableList()
        val roleList = currentPendingMembers.toMutableList()

        // Default to whichever tab was requested or whichever tab has active items
        var isRoleTabActive = if (initialTabIsRoleUpgrade) {
            true
        } else if (joinList.isEmpty() && roleList.isNotEmpty()) {
            true
        } else {
            false
        }

        lateinit var sheetAdapter: PendingRequestAdapter

        fun updateSheetUi() {
            val totalCount = joinList.size + roleList.size
            tvSheetCountBadge?.text = "$totalCount Pending"
            btnTabJoinRequests?.text = "🔑 Join Requests (${joinList.size})"
            btnTabRoleUpgrades?.text = "🍃 Role Upgrades (${roleList.size})"

            val activeTabTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text)
            val inactiveTabTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)

            if (!isRoleTabActive) {
                // TAB 1: JOIN REQUESTS
                tvSheetHeaderIcon?.text = "🔑"
                tvSheetTitle?.text = "Pending Access & Role Requests"
                tvSheetSubtitle?.text = "Review family members who entered your tree's invite code and are waiting for your acceptance."
                btnTabJoinRequests?.setBackgroundResource(R.drawable.badge_pill_gold)
                btnTabJoinRequests?.setTextColor(activeTabTextColor)
                btnTabRoleUpgrades?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnTabRoleUpgrades?.setTextColor(inactiveTabTextColor)

                sheetAdapter.submitList(joinList.toList())
                if (joinList.isEmpty()) {
                    layoutSheetEmptyState?.visibility = View.VISIBLE
                    rvSheetPendingRequests?.visibility = View.GONE
                    btnApproveAllRequests?.visibility = View.GONE
                    tvSheetEmptyTitle?.text = "No Pending Join Requests"
                    tvSheetEmptySub?.text = "Anyone entering your 6-character invite code will appear here for approval."
                } else {
                    layoutSheetEmptyState?.visibility = View.GONE
                    rvSheetPendingRequests?.visibility = View.VISIBLE
                    btnApproveAllRequests?.visibility = View.VISIBLE
                    btnApproveAllRequests?.isEnabled = true
                    btnApproveAllRequests?.text = "✓ Accept All to Tree (${joinList.size})"
                }
            } else {
                // TAB 2: ROLE UPGRADES
                tvSheetHeaderIcon?.text = "🍃"
                tvSheetTitle?.text = "Pending Access & Role Requests"
                tvSheetSubtitle?.text = "Review existing Viewers asking for Editor access to add and edit family tree members."
                btnTabRoleUpgrades?.setBackgroundResource(R.drawable.badge_pill_gold)
                btnTabRoleUpgrades?.setTextColor(activeTabTextColor)
                btnTabJoinRequests?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnTabJoinRequests?.setTextColor(inactiveTabTextColor)

                sheetAdapter.submitList(roleList.toList())
                if (roleList.isEmpty()) {
                    layoutSheetEmptyState?.visibility = View.VISIBLE
                    rvSheetPendingRequests?.visibility = View.GONE
                    btnApproveAllRequests?.visibility = View.GONE
                    tvSheetEmptyTitle?.text = "No Pending Role Upgrades"
                    tvSheetEmptySub?.text = "Viewers who send a Golden Leaf Editor request will appear here."
                } else {
                    layoutSheetEmptyState?.visibility = View.GONE
                    rvSheetPendingRequests?.visibility = View.VISIBLE
                    btnApproveAllRequests?.visibility = View.VISIBLE
                    btnApproveAllRequests?.isEnabled = true
                    btnApproveAllRequests?.text = "⚡ Approve All as Editors (${roleList.size})"
                }
            }

            currentPendingJoinMembers = joinList.toList()
            currentPendingMembers = roleList.toList()
            updateUnifiedPendingTileSummary()
        }

        sheetAdapter = PendingRequestAdapter(
            onApprove = { member ->
                val isJoin = member.role.equals("Pending_Join", ignoreCase = true) ||
                    member.status.equals("Pending", ignoreCase = true)
                if (isJoin) {
                    approveJoinMember(member) {
                        joinList.removeAll { it.id == member.id }
                        updateSheetUi()
                    }
                } else {
                    singleApproveMember(member) {
                        roleList.removeAll { it.id == member.id }
                        updateSheetUi()
                    }
                }
            },
            onReject = { member ->
                val isJoin = member.role.equals("Pending_Join", ignoreCase = true) ||
                    member.status.equals("Pending", ignoreCase = true)
                if (isJoin) {
                    rejectJoinMember(member) {
                        joinList.removeAll { it.id == member.id }
                        updateSheetUi()
                    }
                } else {
                    rejectMember(member) {
                        roleList.removeAll { it.id == member.id }
                        updateSheetUi()
                    }
                }
            }
        )

        rvSheetPendingRequests?.layoutManager = LinearLayoutManager(this)
        rvSheetPendingRequests?.adapter = sheetAdapter

        btnTabJoinRequests?.setOnClickListener {
            if (isRoleTabActive) {
                isRoleTabActive = false
                updateSheetUi()
            }
        }

        btnTabRoleUpgrades?.setOnClickListener {
            if (!isRoleTabActive) {
                isRoleTabActive = true
                updateSheetUi()
            }
        }

        btnApproveAllRequests?.setOnClickListener {
            if (!isRoleTabActive) {
                if (joinList.isEmpty()) return@setOnClickListener
                btnApproveAllRequests.isEnabled = false
                btnApproveAllRequests.text = "✓ Accepting All..."
                val toApprove = joinList.toList()
                var completedCount = 0
                toApprove.forEach { member ->
                    approveJoinMember(member) {
                        completedCount++
                        if (completedCount >= toApprove.size) {
                            joinList.clear()
                            updateSheetUi()
                            Toast.makeText(this, "✓ All ${toApprove.size} join requests accepted!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                if (roleList.isEmpty()) return@setOnClickListener
                btnApproveAllRequests.isEnabled = false
                btnApproveAllRequests.text = "⚡ Granting Editor Access to All..."
                val toApprove = roleList.toList()
                var completedCount = 0
                toApprove.forEach { member ->
                    singleApproveMember(member) {
                        completedCount++
                        if (completedCount >= toApprove.size) {
                            roleList.clear()
                            updateSheetUi()
                            Toast.makeText(this, "⚡ All ${toApprove.size} requests approved as Editors!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        updateSheetUi()
        sheetDialog.show()
    }

    private fun approveJoinMember(member: TreeMember, onDone: (() -> Unit)? = null) {
        val assignedRole = "Viewer"
        firestoreHelper.updateTreeMemberRole(
            memberId = member.id,
            role = assignedRole,
            status = "Approved",
            onSuccess = {
                val treeName = currentTree?.name ?: "Family Tree"
                if (member.userId.isNotEmpty()) {
                    firestoreHelper.getUserProfile(member.userId,
                        onSuccess = { existingProfile ->
                            if (existingProfile != null && existingProfile.currentTreeId.isBlank()) {
                                firestoreHelper.saveUserProfile(
                                    existingProfile.copy(currentTreeId = treeId),
                                    onSuccess = {},
                                    onFailure = {}
                                )
                            }
                        },
                        onFailure = {}
                    )

                    val notif = NotificationHelper.createAccessDecisionNotification(
                        treeId = treeId,
                        applicantUserId = member.userId,
                        treeName = treeName,
                        approved = true,
                        assignedRole = assignedRole
                    )
                    firestoreHelper.addNotification(notif)
                }

                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyTreeLayoutChanged(
                    treeId = treeId,
                    sourceScreen = "PrivacyControlsActivity"
                )

                Toast.makeText(this, "✓ ${member.userName} accepted to $treeName!", Toast.LENGTH_SHORT).show()
                loadMembers()
                onDone?.invoke()
            },
            onFailure = {
                Toast.makeText(this, "Approval failed: ${it.message}", Toast.LENGTH_SHORT).show()
                onDone?.invoke()
            }
        )
    }

    private fun rejectJoinMember(member: TreeMember, onDone: (() -> Unit)? = null) {
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Decline Join Request")
            .setMessage("Decline ${member.userName}'s request to join this family tree?")
            .setPositiveButton("Decline") { _, _ ->
                firestoreHelper.deleteTreeMember(
                    memberId = member.id,
                    onSuccess = {
                        if (member.userId.isNotEmpty()) {
                            val treeName = currentTree?.name ?: "Family Tree"
                            val notif = NotificationHelper.createAccessDecisionNotification(
                                treeId = treeId,
                                applicantUserId = member.userId,
                                treeName = treeName,
                                approved = false,
                                assignedRole = "Viewer"
                            )
                            firestoreHelper.addNotification(notif)
                        }
                        Toast.makeText(this, "Join request from ${member.userName} declined.", Toast.LENGTH_SHORT).show()
                        loadMembers()
                        onDone?.invoke()
                    },
                    onFailure = {
                        Toast.makeText(this, "Decline failed", Toast.LENGTH_SHORT).show()
                        onDone?.invoke()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun singleApproveMember(member: TreeMember, onDone: (() -> Unit)? = null) {
        val assignedRole = "Editor"
        firestoreHelper.updateTreeMemberRole(
            memberId = member.id,
            role = assignedRole,
            status = "Approved",
            onSuccess = {
                val treeName = currentTree?.name ?: "Family Tree"
                if (member.userId.isNotEmpty()) {
                    val notif = NotificationHelper.createAccessDecisionNotification(
                        treeId = treeId,
                        applicantUserId = member.userId,
                        treeName = treeName,
                        approved = true,
                        assignedRole = assignedRole
                    )
                    firestoreHelper.addNotification(notif)
                }

                // Notify Central Tree Synchronizer for immediate live sync
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyTreeLayoutChanged(
                    treeId = treeId,
                    sourceScreen = "PrivacyControlsActivity"
                )

                Toast.makeText(this, "✓ ${member.userName} approved as Editor!", Toast.LENGTH_SHORT).show()
                loadMembers()
                onDone?.invoke()
            },
            onFailure = {
                Toast.makeText(this, "Approval failed: ${it.message}", Toast.LENGTH_SHORT).show()
                onDone?.invoke()
            }
        )
    }

    private fun approveMember(member: TreeMember) {
        singleApproveMember(member)
    }

    private fun rejectMember(member: TreeMember, onDone: (() -> Unit)? = null) {
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Decline Role Request")
            .setMessage("Decline Editor role request from ${member.userName}? They will remain an active Viewer in this family tree.")
            .setPositiveButton("Decline Upgrade") { _, _ ->
                firestoreHelper.updateTreeMemberRole(
                    memberId = member.id,
                    role = "Viewer",
                    status = "Approved",
                    onSuccess = {
                        if (member.userId.isNotEmpty()) {
                            val treeName = currentTree?.name ?: "Family Tree"
                            val notif = NotificationHelper.createAccessDecisionNotification(
                                treeId = treeId,
                                applicantUserId = member.userId,
                                treeName = treeName,
                                approved = false,
                                assignedRole = "Viewer"
                            )
                            firestoreHelper.addNotification(notif)
                        }

                        Toast.makeText(this, "Upgrade declined. ${member.userName} remains a Viewer.", Toast.LENGTH_SHORT).show()
                        loadMembers()
                        onDone?.invoke()
                    },
                    onFailure = {
                        Toast.makeText(this, "Decline failed", Toast.LENGTH_SHORT).show()
                        onDone?.invoke()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePrivacySettings() {
        val updated = currentSettings.copy(
            treeId = treeId,
            isPrivateTree = switchPrivateTree.isChecked,
            hideBirthDates = switchHideBirthDates.isChecked,
            hideNotes = switchHideNotes.isChecked,
            hideDeceasedStatus = switchHideDeceasedStatus.isChecked,
            publicTree = !switchPrivateTree.isChecked
        )

        btnSavePrivacy.isEnabled = false
        btnSavePrivacy.text = "Saving..."

        firestoreHelper.savePrivacySettings(updated,
            onSuccess = {
                // 8.c Tree privacy update broadcast
                val treeName = currentTree?.name ?: "Family Tree"
                val notif = NotificationHelper.createPrivacySettingsNotification(
                    treeId = treeId,
                    treeName = treeName,
                    isPrivate = updated.isPrivateTree
                )
                firestoreHelper.addNotification(notif)

                btnSavePrivacy.isEnabled = true
                btnSavePrivacy.text = "Save Privacy Settings"
                KinTraceAlert.show(this, "Privacy settings saved")
                finish()
            },
            onFailure = {
                btnSavePrivacy.isEnabled = true
                btnSavePrivacy.text = "Save Privacy Settings"
                KinTraceAlert.show(this, "Failed to save privacy settings: ${it.message}")
            }
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
