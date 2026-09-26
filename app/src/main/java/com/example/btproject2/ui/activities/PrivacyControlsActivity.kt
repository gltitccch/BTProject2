package com.example.btproject2.ui.activities

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

        btnBack.setOnClickListener { finish() }
        btnGateBackToHome.setOnClickListener { finish() }

        setupRecyclerViews()
        checkOwnerAccessAndLoad()

        btnInviteMember.setOnClickListener {
            showInviteDialog()
        }

        btnSavePrivacy.setOnClickListener {
            savePrivacySettings()
        }
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
                // If tree cannot be fetched, default to owner view for safety
                layoutOwnerView.visibility = View.VISIBLE
                layoutOwnerGate.visibility = View.GONE
                loadPrivacySettings()
                loadMembers()
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
            layoutOwnerView.visibility = View.GONE
            layoutOwnerGate.visibility = View.VISIBLE
        }
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
                    // Seed initial owner member if none exist
                    val currentUserId = authHelper.getCurrentUserId() ?: ""
                    val owner = TreeMember(
                        id = "owner_default",
                        treeId = treeId,
                        userId = currentUserId,
                        userName = authHelper.getCurrentUser()?.displayName ?: "Tree Owner",
                        userEmail = authHelper.getCurrentUser()?.email ?: "",
                        role = "Owner",
                        status = "Approved"
                    )
                    authorizedAdapter.submitList(listOf(owner))
                } else {
                    val approved = members.filter { it.status.equals("Approved", ignoreCase = true) }
                    val pending = members.filter { it.status.equals("Pending", ignoreCase = true) }

                    authorizedAdapter.submitList(approved)

                    if (pending.isNotEmpty()) {
                        layoutPendingRequests.visibility = View.VISIBLE
                        pendingAdapter.submitList(pending)
                    } else {
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
            Toast.makeText(this, "Please select or create a family tree first.", Toast.LENGTH_SHORT).show()
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

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Done", null)
            .create()

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("KinTrace Tree Invite Code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Invite code $code copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showMemberRoleDialog(member: TreeMember) {
        val options = arrayOf("Editor (Can add/edit)", "Viewer (View only)", "Remove from tree")
        AlertDialog.Builder(this)
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

                Toast.makeText(this, "Updated ${member.userName}'s role to $newRole", Toast.LENGTH_SHORT).show()
                loadMembers()
            },
            onFailure = {
                Toast.makeText(this, "Failed to update role", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun removeMember(member: TreeMember) {
        AlertDialog.Builder(this)
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

                        Toast.makeText(this, "${member.userName} removed", Toast.LENGTH_SHORT).show()
                        loadMembers()
                    },
                    onFailure = {
                        Toast.makeText(this, "Failed to remove member", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun approveMember(member: TreeMember) {
        val roles = arrayOf("Editor (Can add/edit)", "Viewer (View only)")
        AlertDialog.Builder(this)
            .setTitle("Approve ${member.userName}")
            .setItems(roles) { _, which ->
                val assignedRole = if (which == 0) "Editor" else "Viewer"
                firestoreHelper.updateTreeMemberRole(member.id, assignedRole, "Approved",
                    onSuccess = {
                        // 8.a Access approval notification to applicant
                        if (member.userId.isNotEmpty()) {
                            val treeName = currentTree?.name ?: "Family Tree"
                            val notif = NotificationHelper.createAccessDecisionNotification(
                                treeId = treeId,
                                applicantUserId = member.userId,
                                treeName = treeName,
                                approved = true,
                                assignedRole = assignedRole
                            )
                            firestoreHelper.addNotification(notif)
                        }

                        Toast.makeText(this, "${member.userName} approved as $assignedRole", Toast.LENGTH_SHORT).show()
                        loadMembers()
                    },
                    onFailure = {
                        Toast.makeText(this, "Approval failed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rejectMember(member: TreeMember) {
        AlertDialog.Builder(this)
            .setTitle("Reject Request")
            .setMessage("Reject access request from ${member.userName}?")
            .setPositiveButton("Reject") { _, _ ->
                firestoreHelper.deleteTreeMember(member.id,
                    onSuccess = {
                        // 8.a Access rejection notification to applicant
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
                    },
                    onFailure = {
                        Toast.makeText(this, "Rejection failed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePrivacySettings() {
        val updated = currentSettings.copy(
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
                Toast.makeText(this, "Privacy settings saved", Toast.LENGTH_SHORT).show()
                finish()
            },
            onFailure = {
                btnSavePrivacy.isEnabled = true
                btnSavePrivacy.text = "Save Privacy Settings"
                Toast.makeText(this, "Failed to save privacy settings: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
