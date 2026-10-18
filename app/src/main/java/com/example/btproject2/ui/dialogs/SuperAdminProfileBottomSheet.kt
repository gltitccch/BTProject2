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
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.utils.KinTraceAlert
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.UserProfile
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.ui.activities.AdminPortalActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * SuperAdminProfileBottomSheet: The minimalist Obsidian Sleek Super Admin Profile (Variation 1A).
 * Specialized authoritative executive drawer designed for the Super Administrator.
 *
 * Features:
 * - Gold-rimmed avatar crest with real-time green status dot
 * - Inline display name editor with instant Firestore & Auth persistence
 * - 3-Point Micro-Metric Ribbon (Clans Governed, Total Nodes, Live Sync Latency)
 * - 1-Tap Hero Gateway to The Citadel Command Hub
 * - Sleek Hairline Actions Card (Master Password Change, Ops & Recovery Desk, Audit Trail)
 * - Secure Super Admin session sign-out
 */
class SuperAdminProfileBottomSheet : BottomSheetDialogFragment() {

    private lateinit var authHelper: AuthHelper
    private lateinit var firestoreHelper: FirestoreHelper

    var onProfileUpdated: ((newName: String) -> Unit)? = null

    companion object {
        const val TAG = "SuperAdminProfileBottomSheet"

        fun newInstance(): SuperAdminProfileBottomSheet {
            return SuperAdminProfileBottomSheet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authHelper = AuthHelper()
        firestoreHelper = FirestoreHelper()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_super_admin_profile_sheet, container, false)
    }

    override fun onStart() {
        super.onStart()
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

        // View References
        val tvAvatar = view.findViewById<TextView>(R.id.tvSuperAdminAvatar)
        val tvHeaderName = view.findViewById<TextView>(R.id.tvSuperAdminHeaderName)
        val btnEditName = view.findViewById<View>(R.id.btnEditSuperAdminName)
        val tvEmail = view.findViewById<TextView>(R.id.tvSuperAdminEmail)
        val tvMetricClans = view.findViewById<TextView>(R.id.tvMetricClans)
        val tvMetricNodes = view.findViewById<TextView>(R.id.tvMetricNodes)
        val tvMetricStatus = view.findViewById<TextView>(R.id.tvMetricStatus)
        val tvMetricLatency = view.findViewById<TextView>(R.id.tvMetricLatency)
        val btnOpenCitadelHub = view.findViewById<View>(R.id.btnOpenCitadelHub)
        val rowChangeMasterPassword = view.findViewById<View>(R.id.rowChangeMasterPassword)
        val rowOpsRecoveryDesk = view.findViewById<View>(R.id.rowOpsRecoveryDesk)
        val rowSystemAuditTrail = view.findViewById<View>(R.id.rowSystemAuditTrail)
        val btnSignOut = view.findViewById<View>(R.id.btnSuperAdminSignOut)

        // Populate Initial State
        val currentName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Super Administrator"

        tvAvatar.text = currentName.firstOrNull()?.uppercase() ?: "SA"
        tvHeaderName.text = currentName
        tvEmail.text = user.email ?: "admin@kintrace.ph"

        // Load Real-Time Telemetry into 3-Point Micro-Metric Ribbon
        firestoreHelper.fetchLivePlatformTelemetry { telemetry ->
            activity?.runOnUiThread {
                tvMetricClans.text = telemetry.totalClans.toString()
                tvMetricNodes.text = telemetry.totalNodes.toString()
                if (telemetry.isOnline) {
                    tvMetricStatus.text = "Online"
                    context?.let { ctx ->
                        tvMetricStatus.setTextColor(ContextCompat.getColor(ctx, R.color.mint_text))
                    }
                    tvMetricLatency.text = "PINGING..."
                    firestoreHelper.measureFirestoreLatencyMs { latencyMs, _ ->
                        activity?.runOnUiThread {
                            tvMetricLatency.text = "LATENCY ${latencyMs}MS"
                        }
                    }
                } else {
                    tvMetricStatus.text = "Offline"
                    context?.let { ctx ->
                        tvMetricStatus.setTextColor(ContextCompat.getColor(ctx, R.color.warning))
                    }
                    tvMetricLatency.text = "SYNC PENDING"
                }
            }
        }

        // Display Name Quick Editor
        val openNameEditor = {
            showEditNameDialog(tvHeaderName, tvAvatar)
        }
        btnEditName.setOnClickListener { openNameEditor() }
        tvHeaderName.setOnClickListener { openNameEditor() }

        // Primary Hero Gateway: The Citadel Command Hub
        btnOpenCitadelHub.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            dismiss()
            val intent = Intent(ctx, AdminPortalActivity::class.java).apply {
                putExtra("OPEN_TAB", 0)
            }
            startActivity(intent)
        }

        // Action Row 1: Master Admin Password Change
        rowChangeMasterPassword.setOnClickListener {
            showChangeMasterPasswordDialog()
        }

        // Action Row 2: Ops & Recovery Desk Deep Link
        rowOpsRecoveryDesk.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            dismiss()
            val intent = Intent(ctx, AdminPortalActivity::class.java).apply {
                putExtra("OPEN_TAB", 3)
            }
            startActivity(intent)
        }

        // Action Row 3: System Admin Audit Trail Deep Link
        rowSystemAuditTrail.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            dismiss()
            val intent = Intent(ctx, AdminPortalActivity::class.java).apply {
                putExtra("OPEN_TAB", 1)
            }
            startActivity(intent)
        }

        // Session Termination
        btnSignOut.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            com.example.btproject2.utils.KinTraceModalDialog.Builder(ctx)
                .setTitle("Sign Out Super Admin Session")
                .setMessage("Are you sure you want to terminate your Root Sovereign administrative session?")
                .setPositiveButton("Sign Out") { _, _ ->
                    dismiss()
                    activity?.let { authHelper.performGlobalLogout(it) }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showEditNameDialog(tvHeaderName: TextView, tvAvatar: TextView) {
        val ctx = context ?: return
        val user = authHelper.getCurrentUser() ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_edit_super_admin_name, null)
        val etNewName = dialogView.findViewById<EditText>(R.id.etSuperAdminNewName)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelEditName)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnSaveSuperAdminName)

        val currentName = tvHeaderName.text.toString()
        etNewName.setText(currentName)
        etNewName.setSelection(etNewName.text.length)

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etNewName.text.toString().trim()
            if (newName.isBlank()) {
                etNewName.error = "Name cannot be empty"
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            authHelper.updateDisplayName(
                displayName = newName,
                onSuccess = {
                    val profile = UserProfile(
                        id = user.uid,
                        displayName = newName,
                        email = user.email.orEmpty(),
                        role = "SUPER_ADMIN"
                    )
                    firestoreHelper.saveUserProfile(profile, onSuccess = {}, onFailure = {})

                    activity?.runOnUiThread {
                        tvHeaderName.text = newName
                        tvAvatar.text = newName.firstOrNull()?.uppercase() ?: "SA"
                        onProfileUpdated?.invoke(newName)
                        context?.let { c ->
                            KinTraceAlert.security(activity ?: c, "Super Admin Updated", "Super Admin profile updated!", "SAVED")
                        }
                        dialog.dismiss()
                    }
                },
                onFailure = { err ->
                    activity?.runOnUiThread {
                        btnSave.isEnabled = true
                        btnSave.text = "Save ✓"
                        context?.let { c ->
                            KinTraceAlert.warning(activity ?: c, "Update Failed", "Update failed: ${err.message}")
                        }
                    }
                }
            )
        }

        dialog.show()
    }

    private fun showChangeMasterPasswordDialog() {
        val ctx = context ?: return
        val user = authHelper.getCurrentUser() ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_change_super_admin_password, null)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.etNewMasterPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmMasterPassword)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelChangePassword)
        val btnSubmit = dialogView.findViewById<TextView>(R.id.btnSubmitChangePassword)

        val dialog = AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (newPassword.length < 6) {
                etNewPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                etConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Updating..."

            user.updatePassword(newPassword)
                .addOnSuccessListener {
                    context?.let { c ->
                        KinTraceAlert.security(activity ?: c, "Master Password Updated", "Master Admin password updated successfully!", "SECURED")
                    }
                    dialog.dismiss()
                }
                .addOnFailureListener { err ->
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Update Password ✓"
                    context?.let { c ->
                        KinTraceAlert.warning(activity ?: c, "Password Update Failed", "Failed to update password: ${err.message}")
                    }
                }
        }

        dialog.show()
    }
}
