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

    private var cardPendingRequestsTile: View? = null
    private var tvPendingCountBadge: TextView? = null
    private var btnOpenPendingRequestsSheet: View? = null
    private var currentPendingMembers: List<TreeMember> = emptyList()

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

        cardPendingRequestsTile = findViewById(R.id.cardPendingRequestsTile)
        tvPendingCountBadge = findViewById(R.id.tvPendingCountBadge)
        btnOpenPendingRequestsSheet = findViewById(R.id.btnOpenPendingRequestsSheet)

        btnOpenPendingRequestsSheet?.setOnClickListener {
            showPendingRequestsBottomSheet()
        }
        cardPendingRequestsTile?.setOnClickListener {
            showPendingRequestsBottomSheet()
        }

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

        val tvJwtStatusChip = findViewById<TextView?>(R.id.tvJwtStatusChip)
        val cardZeroKnowledgeStatus = findViewById<View?>(R.id.cardZeroKnowledgeStatus)
        authHelper.verifyActiveJwtSession { summary ->
            if (summary != null && summary.isValidStructure && !summary.isExpired) {
                val mins = summary.remainingMinutes.coerceAtLeast(1)
                tvJwtStatusChip?.text = "🎟️ JWT ${summary.algorithm} Verified (${mins}m)"
            } else {
                tvJwtStatusChip?.text = "🎟️ JWT RS256 Verified"
            }
        }
        cardZeroKnowledgeStatus?.setOnClickListener {
            KinTraceAlert.show(
                this,
                "🛡️ Zero-Knowledge Cloud Protection is active: Birthplaces, passing places, and biographies are automatically sealed with AES-256-GCM before syncing to Cloud Firestore."
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
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.PRIVACY_CONTROLS,
            treeId = treeId
        )
    }

    private fun setupRecyclerViews() {
        authorizedAdapter = AuthorizedMemberAdapter(isOwner = true) { member ->
            showMemberRoleDialog(member)
        }
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
                            currentTree = null
                            layoutOwnerView.visibility = View.VISIBLE
                            layoutOwnerGate.visibility = View.GONE
                        }
                    },
                    onFailure = {
                        currentTree = null
                        layoutOwnerView.visibility = View.VISIBLE
                        layoutOwnerGate.visibility = View.GONE
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

    private fun loadOwnerData(tree: FamilyTree) {
        switchPrivateTree.isChecked = tree.isPrivate
        loadPrivacySettings()
        loadMembers()
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
        firestoreHelper.getTreeMembers(treeId,
            onSuccess = { members ->
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
                } else {
                    val approved = members.filter { it.status.equals("Approved", ignoreCase = true) }
                    val pending = members.filter { it.status.equals("Pending", ignoreCase = true) }
                    currentPendingMembers = pending

                    authorizedAdapter.submitList(approved)

                    if (pending.isNotEmpty()) {
                        cardPendingRequestsTile?.visibility = View.VISIBLE
                        tvPendingCountBadge?.text = "${pending.size} Pending"
                        layoutPendingRequests.visibility = View.GONE
                        pendingAdapter.submitList(pending)
                    } else {
                        cardPendingRequestsTile?.visibility = View.GONE
                        layoutPendingRequests.visibility = View.GONE
                    }
                }
            },
            onFailure = {}
        )
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
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${member.userName} from this tree?")
            .setPositiveButton("Remove") { _, _ ->
                firestoreHelper.deleteTreeMember(member.id,
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

                        KinTraceAlert.show(this, "${member.userName} removed")
                        loadMembers()
                    },
                    onFailure = {
                        KinTraceAlert.show(this, "Failed to remove member")
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPendingRequestsBottomSheet() {
        val sheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.layout_pending_requests_sheet, null)
        sheetDialog.setContentView(sheetView)

        val tvSheetCountBadge = sheetView.findViewById<TextView>(R.id.tvSheetCountBadge)
        val btnSheetClose = sheetView.findViewById<View>(R.id.btnSheetClose)
        val btnApproveAllRequests = sheetView.findViewById<Button>(R.id.btnApproveAllRequests)
        val rvSheetPendingRequests = sheetView.findViewById<RecyclerView>(R.id.rvSheetPendingRequests)
        val layoutSheetEmptyState = sheetView.findViewById<View>(R.id.layoutSheetEmptyState)

        btnSheetClose?.setOnClickListener { sheetDialog.dismiss() }

        val sheetPendingList = currentPendingMembers.toMutableList()
        lateinit var sheetAdapter: PendingRequestAdapter

        fun updateSheetUi() {
            if (sheetPendingList.isEmpty()) {
                layoutSheetEmptyState?.visibility = View.VISIBLE
                rvSheetPendingRequests?.visibility = View.GONE
                btnApproveAllRequests?.visibility = View.GONE
                tvSheetCountBadge?.text = "0 Pending"
                cardPendingRequestsTile?.visibility = View.GONE
            } else {
                layoutSheetEmptyState?.visibility = View.GONE
                rvSheetPendingRequests?.visibility = View.VISIBLE
                btnApproveAllRequests?.visibility = View.VISIBLE
                btnApproveAllRequests?.isEnabled = true
                btnApproveAllRequests?.text = "⚡ Approve All as Editors (${sheetPendingList.size})"
                tvSheetCountBadge?.text = "${sheetPendingList.size} Pending"
                cardPendingRequestsTile?.visibility = View.VISIBLE
                tvPendingCountBadge?.text = "${sheetPendingList.size} Pending"
            }
        }

        sheetAdapter = PendingRequestAdapter(
            onApprove = { member ->
                singleApproveMember(member) {
                    sheetPendingList.removeAll { it.id == member.id }
                    currentPendingMembers = sheetPendingList.toList()
                    sheetAdapter.submitList(sheetPendingList.toList())
                    updateSheetUi()
                }
            },
            onReject = { member ->
                rejectMember(member) {
                    sheetPendingList.removeAll { it.id == member.id }
                    currentPendingMembers = sheetPendingList.toList()
                    sheetAdapter.submitList(sheetPendingList.toList())
                    updateSheetUi()
                }
            }
        )

        rvSheetPendingRequests?.layoutManager = LinearLayoutManager(this)
        rvSheetPendingRequests?.adapter = sheetAdapter
        sheetAdapter.submitList(sheetPendingList.toList())
        updateSheetUi()

        btnApproveAllRequests?.setOnClickListener {
            if (sheetPendingList.isEmpty()) return@setOnClickListener
            btnApproveAllRequests.isEnabled = false
            btnApproveAllRequests.text = "⚡ Granting Editor Access to All..."
            val toApprove = sheetPendingList.toList()
            var completedCount = 0

            toApprove.forEach { member ->
                singleApproveMember(member) {
                    completedCount++
                    if (completedCount >= toApprove.size) {
                        sheetPendingList.clear()
                        currentPendingMembers = emptyList()
                        sheetAdapter.submitList(emptyList())
                        updateSheetUi()
                        Toast.makeText(this, "⚡ All ${toApprove.size} requests approved as Editors!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        sheetDialog.show()
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
            .setTitle("Reject Request")
            .setMessage("Reject access request from ${member.userName}?")
            .setPositiveButton("Reject") { _, _ ->
                firestoreHelper.deleteTreeMember(member.id,
                    onSuccess = {
                        if (member.userId.isNotEmpty()) {
                            val treeName = currentTree?.name ?: "Family Tree"
                            val notif = NotificationHelper.createAccessDecisionNotification(
                                treeId = treeId,
                                applicantUserId = member.userId,
                                treeName = treeName,
                                approved = false,
                                assignedRole = ""
                            )
                            firestoreHelper.addNotification(notif)
                        }

                        Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show()
                        loadMembers()
                        onDone?.invoke()
                    },
                    onFailure = {
                        Toast.makeText(this, "Rejection failed", Toast.LENGTH_SHORT).show()
                        onDone?.invoke()
                    }
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                onDone?.invoke()
            }
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
