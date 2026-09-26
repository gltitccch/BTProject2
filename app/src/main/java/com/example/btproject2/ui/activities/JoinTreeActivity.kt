package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.InviteCodeRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.TreeMember
import com.example.btproject2.models.UserProfile
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.TreePreferences

class JoinTreeActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()

    private lateinit var layoutJoinForm: View
    private lateinit var layoutPendingApproval: View
    private lateinit var etInviteCode: EditText
    private lateinit var btnRequestAccess: Button
    private lateinit var tvPendingTreeMessage: TextView
    private lateinit var btnPendingBackToHome: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_tree)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        layoutJoinForm = findViewById(R.id.layoutJoinForm)
        layoutPendingApproval = findViewById(R.id.layoutPendingApproval)
        etInviteCode = findViewById(R.id.etInviteCode)
        btnRequestAccess = findViewById(R.id.btnRequestAccess)
        tvPendingTreeMessage = findViewById(R.id.tvPendingTreeMessage)
        btnPendingBackToHome = findViewById(R.id.btnPendingBackToHome)

        etInviteCode.filters = arrayOf(android.text.InputFilter.AllCaps())

        btnBack.setOnClickListener { finish() }
        btnPendingBackToHome.setOnClickListener { finish() }

        btnRequestAccess.setOnClickListener {
            handleJoinRequest()
        }
    }

    private fun handleJoinRequest() {
        val rawInput = etInviteCode.text.toString()
        val code = rawInput.replace(" ", "").trim().uppercase()
        if (code.length != 6) {
            Toast.makeText(this, "Please enter a valid 6-character code", Toast.LENGTH_SHORT).show()
            return
        }

        btnRequestAccess.isEnabled = false
        btnRequestAccess.text = "Checking code..."

        // Requirement 5 & 6: Search dedicated inviteCodes collection case-insensitively with leading/trailing spaces removed
        firestoreHelper.getInviteCodeRecord(code,
            onSuccess = { record ->
                if (record == null) {
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Invalid Code")
                        .setMessage("No family tree found with invite code \"$code\". Please check with the tree owner.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@getInviteCodeRecord
                }

                // Requirement 12: Specific error if revoked
                if (record.isRevoked()) {
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Code Revoked")
                        .setMessage("This invite code has been revoked by the tree owner.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@getInviteCodeRecord
                }

                // Requirement 12: Specific error if expired
                if (record.isExpired()) {
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Code Expired")
                        .setMessage("This invite code has expired. Please request a new invite code from the tree owner.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@getInviteCodeRecord
                }

                // Requirement 12: Specific error if usage limit reached
                if (record.isUsageLimitReached()) {
                    resetButton()
                    AlertDialog.Builder(this)
                        .setTitle("Usage Limit Reached")
                        .setMessage("This invite code has reached its maximum usage limit.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@getInviteCodeRecord
                }

                val user = authHelper.getCurrentUser()
                val userId = user?.uid.orEmpty()
                if (userId.isEmpty()) {
                    resetButton()
                    Toast.makeText(this, "Please log in to join a family tree", Toast.LENGTH_SHORT).show()
                    return@getInviteCodeRecord
                }

                // Retrieve linked family tree ID & verify tree exists
                firestoreHelper.getTree(record.treeId,
                    onSuccess = { tree ->
                        val targetTreeId = tree?.id?.takeIf { it.isNotBlank() } ?: record.treeId
                        val treeName = tree?.name?.takeIf { it.isNotBlank() } ?: record.treeName.ifBlank { "Family Tree" }
                        val ownerId = tree?.ownerId?.takeIf { it.isNotBlank() } ?: record.createdBy

                        // Check if user is tree owner
                        if (ownerId == userId) {
                            resetButton()
                            AlertDialog.Builder(this)
                                .setTitle("Notice")
                                .setMessage("You are already the owner of this family tree (\"$treeName\").")
                                .setPositiveButton("OK", null)
                                .show()
                            return@getTree
                        }

                        // Requirement 8: Check whether the new user is already a member of that family tree
                        firestoreHelper.getUserMembership(userId, targetTreeId,
                            onSuccess = { existing ->
                                if (existing != null && existing.status.equals("Approved", ignoreCase = true)) {
                                    resetButton()
                                    AlertDialog.Builder(this)
                                        .setTitle("Already a Member")
                                        .setMessage("You are already a member of this family tree (\"$treeName\").")
                                        .setPositiveButton("OK") { _, _ -> finish() }
                                        .show()
                                } else {
                                    // Requirements 9, 10, 11: Create membership with role Viewer, status Approved, and immediately display tree
                                    joinTreeImmediately(
                                        treeId = targetTreeId,
                                        treeName = treeName,
                                        ownerId = ownerId,
                                        userId = userId,
                                        code = code,
                                        record = record,
                                        existingMemberId = existing?.id
                                    )
                                }
                            },
                            onFailure = {
                                // If membership lookup fails, proceed to join
                                joinTreeImmediately(
                                    treeId = targetTreeId,
                                    treeName = treeName,
                                    ownerId = ownerId,
                                    userId = userId,
                                    code = code,
                                    record = record,
                                    existingMemberId = null
                                )
                            }
                        )
                    },
                    onFailure = { err ->
                        resetButton()
                        Toast.makeText(this, "Failed to load tree details: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onFailure = { err ->
                resetButton()
                android.util.Log.e("JoinTreeActivity", "Invite code lookup failed", err)
                val msg = err.message.orEmpty()
                val friendlyMessage = when {
                    msg.contains("offline", ignoreCase = true) ->
                        "Unable to connect to Firebase. If your internet connection is active, please check whether Cloud Firestore is enabled in your Firebase Console (project-x-61285)."
                    msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("not been used", ignoreCase = true) ->
                        "Cloud Firestore API has not been enabled in the Firebase Console for project-x-61285. Please create your Firestore Database in the Firebase Console."
                    else ->
                        "Network error: $msg"
                }
                AlertDialog.Builder(this)
                    .setTitle("Connection Error")
                    .setMessage(friendlyMessage)
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    private fun joinTreeImmediately(
        treeId: String,
        treeName: String,
        ownerId: String,
        userId: String,
        code: String,
        record: InviteCodeRecord,
        existingMemberId: String?
    ) {
        val user = authHelper.getCurrentUser()
        val userName = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Family Member"
        val userEmail = user?.email.orEmpty()

        val member = TreeMember(
            id = existingMemberId.orEmpty(),
            treeId = treeId,
            userId = userId,
            userName = userName,
            userEmail = userEmail,
            role = "Viewer",
            status = "Approved"
        )

        val onMemberAdded = {
            // Increment usage count in inviteCodes collection
            firestoreHelper.incrementInviteCodeUsage(code, record.usageCount, record.usageLimit)

            // Requirement 11: Set active tree in preferences and clear stale cache
            TreePreferences.setActiveTree(this, treeId, treeName)
            FirestoreHelper.setCachedPersons(emptyList())

            // Update user profile currentTreeId
            val profile = UserProfile(
                id = userId,
                displayName = userName,
                email = userEmail,
                currentTreeId = treeId
            )
            firestoreHelper.saveUserProfile(profile, onSuccess = {}, onFailure = {})

            // Start CentralTreeSynchronizer real-time listener for the joined tree
            CentralTreeSynchronizer.getInstance().startRealtimeListener(treeId)

            // Notify tree owner
            if (ownerId.isNotEmpty()) {
                val ownerNotif = NotificationRecord(
                    treeId = treeId,
                    userId = ownerId,
                    title = "New Member Joined",
                    message = "$userName joined '$treeName' using invite code $code.",
                    type = NotificationHelper.CATEGORY_ACCESS,
                    targetId = treeId
                )
                firestoreHelper.addNotification(ownerNotif)
            }

            // Welcome notification for the new member
            val memberNotif = NotificationRecord(
                treeId = treeId,
                userId = userId,
                title = "Welcome to $treeName",
                message = "You have successfully joined '$treeName' as a Viewer.",
                type = NotificationHelper.CATEGORY_ACCESS,
                targetId = treeId
            )
            firestoreHelper.addNotification(memberNotif)

            Toast.makeText(this, "Successfully joined $treeName!", Toast.LENGTH_SHORT).show()

            // Requirement 11: Immediately display the joined family tree in the new user's account
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        if (existingMemberId.isNullOrBlank()) {
            firestoreHelper.addTreeMember(member,
                onSuccess = onMemberAdded,
                onFailure = { err ->
                    resetButton()
                    Toast.makeText(this, "Failed to join tree: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            firestoreHelper.updateTreeMemberRole(existingMemberId, "Viewer", "Approved",
                onSuccess = onMemberAdded,
                onFailure = { err ->
                    resetButton()
                    Toast.makeText(this, "Failed to update membership: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun resetButton() {
        btnRequestAccess.isEnabled = true
        btnRequestAccess.text = "Join Tree"
    }
}
