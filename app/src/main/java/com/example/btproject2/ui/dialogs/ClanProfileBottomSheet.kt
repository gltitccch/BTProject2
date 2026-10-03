package com.example.btproject2.ui.dialogs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.UserProfile
import com.example.btproject2.ui.activities.LoginActivity
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.OnboardingPreferences
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * ClanProfileBottomSheet: The modern Heritage Identity bottom sheet (Proposal 26 - Concept 1).
 * Replaces the legacy Android AlertDialog with a polished, high-craft genealogical identity drawer.
 *
 * Features:
 * - 72dp Gold Crest Avatar with Verified Member badge
 * - Inline Display Name editing with instant cloud persistence
 * - Dual Security Credentials matrix (Direct Password Change & Email Reset Link)
 * - App Experience & Guides list (Welcome Storybook replay & Clan Quest HUD reset)
 * - Full Dark & Light theme adaptability
 */
class ClanProfileBottomSheet : BottomSheetDialogFragment() {

    private lateinit var authHelper: AuthHelper
    private lateinit var firestoreHelper: FirestoreHelper
    private var treeId: String = ""

    var onProfileUpdated: ((newName: String) -> Unit)? = null
    var onQuestResetRequested: (() -> Unit)? = null
    var onRequestChangePassword: (() -> Unit)? = null

    companion object {
        const val TAG = "ClanProfileBottomSheet"
        private const val ARG_TREE_ID = "arg_tree_id"

        fun newInstance(treeId: String = ""): ClanProfileBottomSheet {
            val fragment = ClanProfileBottomSheet()
            val args = Bundle().apply {
                putString(ARG_TREE_ID, treeId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        treeId = arguments?.getString(ARG_TREE_ID).orEmpty()
        authHelper = AuthHelper()
        firestoreHelper = FirestoreHelper()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_clan_profile_sheet, container, false)
    }

    override fun onStart() {
        super.onStart()
        // Ensure the bottom sheet container is transparent so custom curved corners render cleanly
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = authHelper.getCurrentUser()
        if (user == null) {
            dismiss()
            return
        }

        val currentName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "User"

        // View References
        val btnClose = view.findViewById<TextView>(R.id.btnProfileSheetClose)
        val tvAvatar = view.findViewById<TextView>(R.id.tvProfileAvatar)
        val tvHeaderName = view.findViewById<TextView>(R.id.tvProfileHeaderName)
        val tvEmail = view.findViewById<TextView>(R.id.tvProfileEmail)
        val tvRoleTag = view.findViewById<TextView>(R.id.tvProfileRoleTag)
        val etDisplayName = view.findViewById<EditText>(R.id.etProfileDisplayName)
        val btnSaveName = view.findViewById<TextView>(R.id.btnSaveProfileName)
        val cardChangePassword = view.findViewById<View>(R.id.cardChangePassword)
        val cardResetPassword = view.findViewById<View>(R.id.cardResetPassword)
        val btnReplayStorybook = view.findViewById<View>(R.id.btnReplayStorybook)
        val btnResetQuest = view.findViewById<View>(R.id.btnResetQuest)
        val btnViewTerms = view.findViewById<View>(R.id.btnViewTermsOfService)
        val btnDeleteAccount = view.findViewById<View>(R.id.btnDeleteAccountProfile)
        val btnDone = view.findViewById<TextView>(R.id.btnProfileDone)

        // Populate Initial User State
        tvAvatar.text = currentName.first().uppercase()
        tvHeaderName.text = currentName
        tvEmail.text = user.email ?: ""
        etDisplayName.setText(currentName)

        // Dismissal Handlers
        btnClose.setOnClickListener { dismiss() }
        btnDone.setOnClickListener { dismiss() }

        // Save Profile Name Action
        btnSaveName.setOnClickListener {
            val newName = etDisplayName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSaveName.isEnabled = false
            btnSaveName.text = "..."

            authHelper.updateDisplayName(newName,
                onSuccess = {
                    val profile = UserProfile(
                        id = user.uid,
                        displayName = newName,
                        email = user.email ?: "",
                        currentTreeId = treeId
                    )
                    firestoreHelper.saveUserProfile(profile, onSuccess = {}, onFailure = {})
                    
                    tvAvatar.text = newName.first().uppercase()
                    tvHeaderName.text = newName
                    btnSaveName.isEnabled = true
                    btnSaveName.text = "Saved ✓"
                    
                    onProfileUpdated?.invoke(newName)
                    Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { err ->
                    btnSaveName.isEnabled = true
                    btnSaveName.text = "Save"
                    Toast.makeText(requireContext(), "Failed to update profile: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Direct Password Change
        cardChangePassword.setOnClickListener {
            if (onRequestChangePassword != null) {
                onRequestChangePassword?.invoke()
            } else {
                showDirectChangePasswordDialog()
            }
        }

        // Send Password Reset Email
        cardResetPassword.setOnClickListener {
            val email = user.email
            if (email.isNullOrBlank()) {
                Toast.makeText(requireContext(), "No email address linked to this account", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Send Password Reset Link")
                .setMessage("A reset link will be sent to $email.\n\n⚠️ Note: Automated emails may arrive in your Spam / Junk folder. If found in Junk, please tap 'Report not spam'.")
                .setPositiveButton("Send Email") { _, _ ->
                    authHelper.sendPasswordResetEmail(email,
                        onSuccess = {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Email Sent")
                                .setMessage("Password reset email sent to $email.\n\nPlease check your Inbox and Spam / Junk folder.")
                                .setPositiveButton("OK", null)
                                .show()
                        },
                        onFailure = { err ->
                            Toast.makeText(requireContext(), "Failed to send reset email: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Replay Welcome Storybook
        btnReplayStorybook.setOnClickListener {
            dismiss()
            WelcomeStoryDialogFragment.newInstance().show(parentFragmentManager, WelcomeStoryDialogFragment.TAG)
        }

        // Reset Clan Quest HUD
        btnResetQuest.setOnClickListener {
            val context = requireContext()
            OnboardingPreferences.setQuestDismissed(context, false)
            OnboardingPreferences.setQuestCollapsed(context, false)
            
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            if (currentUserId.isNotEmpty()) {
                firestoreHelper.saveOnboardingState(
                    currentUserId,
                    OnboardingPreferences.getOnboardingState(context)
                )
            }
            
            onQuestResetRequested?.invoke()
            Toast.makeText(context, "Clan Quest checklist restored to dashboard!", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        // View Terms of Service & Clan Stewardship
        btnViewTerms?.setOnClickListener {
            TermsOfServiceBottomSheet.newInstance().show(parentFragmentManager, TermsOfServiceBottomSheet.TAG)
        }

        // Delete Account & Clan Stewardship
        btnDeleteAccount?.setOnClickListener {
            showDeleteAccountConfirmationDialog(user.uid)
        }
    }

    private fun showDeleteAccountConfirmationDialog(userId: String) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete Clan Account")
            .setMessage(
                "Are you sure you want to permanently delete your KinTrace account?\n\n" +
                "• Your login credentials, email, and personal profile will be completely wiped.\n" +
                "• Any unshared solo trees you created will be deleted.\n" +
                "• Shared family trees will remain preserved under Clan Stewardship to safeguard genealogical records for surviving relatives, with authorship attributed to 'Former Clan Contributor'.\n" +
                "• Any living relatives you added without death records will have their personal details masked as 'Private Living Relative' for privacy protection.\n\n" +
                "This action is permanent and cannot be undone."
            )
            .setPositiveButton("Delete Permanently") { _, _ ->
                executeAccountDeletion(userId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeAccountDeletion(userId: String) {
        val context = context ?: return
        val progressDialog = AlertDialog.Builder(context)
            .setTitle("Processing Account Deletion")
            .setMessage("Invoking Clan Stewardship and safely removing personal credentials...")
            .setCancelable(false)
            .show()

        firestoreHelper.deleteUserAccount(
            userId = userId,
            context = context,
            onSuccess = {
                authHelper.deleteCurrentUser(
                    onSuccess = {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Account deleted under Clan Stewardship.", Toast.LENGTH_LONG).show()
                        dismiss()
                        val intent = Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        activity?.finish()
                    },
                    onFailure = { err ->
                        progressDialog.dismiss()
                        AlertDialog.Builder(context)
                            .setTitle("Security Re-Authentication Required")
                            .setMessage("Your profile and personal records have been removed under Clan Stewardship. However, Firebase requires recent authentication to permanently delete your login session.\n\nPlease sign out and sign in again to complete account removal:\n${err.message}")
                            .setPositiveButton("Sign Out") { _, _ ->
                                authHelper.logout()
                                dismiss()
                                val intent = Intent(context, LoginActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                activity?.finish()
                            }
                            .setNegativeButton("Dismiss", null)
                            .show()
                    }
                )
            },
            onFailure = { err ->
                progressDialog.dismiss()
                Toast.makeText(context, "Failed to delete account: ${err.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showDirectChangePasswordDialog() {
        val context = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etNewPassword = view.findViewById<EditText>(R.id.etDialogNewPassword)
        val etConfirmPassword = view.findViewById<EditText>(R.id.etDialogConfirmPassword)
        val tvPasswordError = view.findViewById<TextView>(R.id.tvDialogPasswordError)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Update Password", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newPass = etNewPassword.text.toString().trim()
                val confirmPass = etConfirmPassword.text.toString().trim()

                if (newPass.length < 6) {
                    tvPasswordError.visibility = View.VISIBLE
                    tvPasswordError.text = "Password must be at least 6 characters"
                    return@setOnClickListener
                }
                if (newPass != confirmPass) {
                    tvPasswordError.visibility = View.VISIBLE
                    tvPasswordError.text = "Passwords do not match"
                    return@setOnClickListener
                }

                tvPasswordError.visibility = View.GONE
                authHelper.updatePassword(newPass,
                    onSuccess = {
                        Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        if (currentUserId.isNotEmpty()) {
                            val notif = NotificationHelper.createPasswordChangedNotification(
                                userId = currentUserId,
                                treeId = treeId.ifEmpty { "system" }
                            )
                            firestoreHelper.addNotification(notif)
                        }
                        dialog.dismiss()
                    },
                    onFailure = { err ->
                        tvPasswordError.visibility = View.VISIBLE
                        tvPasswordError.text = err.message ?: "Failed to update password. You may need to sign in again."
                    }
                )
            }
        }

        dialog.show()
    }
}
