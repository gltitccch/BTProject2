package com.example.btproject2.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.DemoAccount
import com.example.btproject2.models.DemoAccountRepository
import com.example.btproject2.models.VaultFolder
import com.example.btproject2.models.VaultMode
import com.example.btproject2.ui.activities.RegisterActivity
import com.example.btproject2.utils.KeycardAccountVerifier
import com.example.btproject2.utils.KinTraceModalDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * DemoVaultBottomSheet (Full-Screen Segmented Studio + Minimalist Concept 1 Single-Line Folder Dropdown Bar):
 * - Mode 1: User Account Vault (Personal accounts, friends/clan mates, custom folders)
 * - Mode 2: Admin & Demo Account Vault (Super Admin + pre-seeded defense clans)
 * - Minimalist Single-Line Folder Dropdown Selector (`[ FOLDER: 👑 Pre-Seeded Demo Clans (2) ▾ ]` + `[📁+]` + `[+ Keycard]`)
 * - Enforces Account Registration & Email Verification Pop-Up Guard before saving any new keycard.
 */
class DemoVaultBottomSheet : BottomSheetDialogFragment() {

    var onAccountSelected: ((DemoAccount) -> Unit)? = null

    private var currentMode: VaultMode = VaultMode.ADMIN_DEMO
    private var activeFolderId: String? = null
    private val firestoreHelper by lazy { FirestoreHelper() }
    private val authHelper by lazy { AuthHelper() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_demo_vault, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
            bottomSheet?.let { sheet ->
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.peekHeight = resources.displayMetrics.heightPixels
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = false
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        DemoAccountRepository.ensureLoaded(ctx)

        val userAccounts = DemoAccountRepository.getAccountsByMode(VaultMode.USER, ctx)
        currentMode = if (userAccounts.isNotEmpty()) {
            DemoAccountRepository.getLastActiveMode(ctx)
        } else {
            VaultMode.ADMIN_DEMO
        }

        val initialFolders = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
        activeFolderId = initialFolders.firstOrNull()?.id

        val btnDismiss = view.findViewById<TextView>(R.id.btnDismissSheet)
        btnDismiss?.setOnClickListener { dismiss() }

        // Segmented Studio Mode Switcher: Mode 1 (User Vault) vs Mode 2 (Admin & Demo Vault)
        val btnSegmentUserVault = view.findViewById<TextView>(R.id.btnSegmentUserVault)
        val btnSegmentAdminVault = view.findViewById<TextView>(R.id.btnSegmentAdminVault)

        btnSegmentUserVault?.setOnClickListener {
            if (currentMode != VaultMode.USER) {
                switchToMode(VaultMode.USER, view)
            }
        }

        btnSegmentAdminVault?.setOnClickListener {
            if (currentMode != VaultMode.ADMIN_DEMO) {
                switchToMode(VaultMode.ADMIN_DEMO, view)
            }
        }

        // Compatibility fallback if btnFlipSafeMode is triggered programmatically
        val btnFlipSafeMode = view.findViewById<View>(R.id.btnFlipSafeMode)
        btnFlipSafeMode?.setOnClickListener {
            val nextMode = if (currentMode == VaultMode.USER) VaultMode.ADMIN_DEMO else VaultMode.USER
            switchToMode(nextMode, view)
        }

        // Minimalist Concept 1: Single-Line Folder Dropdown Trigger
        val btnFolderDropdownSelector = view.findViewById<View>(R.id.btnFolderDropdownSelector)
        btnFolderDropdownSelector?.setOnClickListener { anchor ->
            showMinimalistFolderDropdownMenu(anchor, view)
        }

        // Quick Create New Folder Button (📁+)
        val btnCreateFolderQuick = view.findViewById<View>(R.id.btnCreateFolderQuick)
        btnCreateFolderQuick?.setOnClickListener {
            showCreateFolderDialog(view)
        }

        // Add New Keycard Button (+ Keycard)
        val btnAddKeycard = view.findViewById<TextView>(R.id.btnAddKeycard)
        btnAddKeycard?.setOnClickListener {
            showIssueKeycardDialog(view)
        }

        // Delete Custom Folder Button (🗑️)
        val btnDeleteActiveFolder = view.findViewById<TextView>(R.id.btnDeleteActiveFolder)
        btnDeleteActiveFolder?.setOnClickListener {
            val folderId = activeFolderId ?: return@setOnClickListener
            val folders = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
            val folder = folders.firstOrNull { it.id == folderId } ?: return@setOnClickListener
            if (folder.isSystemFolder) return@setOnClickListener

            KinTraceModalDialog.Builder(ctx)
                .setTitle("Delete Folder?")
                .setMessage("Remove folder \"${folder.name}\" and all keycards stored inside it?")
                .setPositiveButton("Delete") { dlg, _ ->
                    dlg.dismiss()
                    DemoAccountRepository.deleteFolder(folder.id, ctx)
                    firestoreHelper.deleteVaultFolderFromCloud(folder.id)
                    val remaining = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
                    activeFolderId = remaining.firstOrNull()?.id
                    renderVaultState(view)
                }
                .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
                .show()
        }

        renderVaultState(view)
    }

    private fun switchToMode(targetMode: VaultMode, rootView: View) {
        val ctx = context ?: return
        currentMode = targetMode
        DemoAccountRepository.setLastActiveMode(ctx, currentMode)
        val folders = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
        activeFolderId = folders.firstOrNull()?.id
        renderVaultState(rootView)
    }

    /**
     * Opens the Minimalist Concept 1 Folder Dropdown Menu right below the Single-Line Folder Selector.
     * Also includes a direct "+ Create New Folder..." option at the bottom of the dropdown.
     */
    private fun showMinimalistFolderDropdownMenu(anchor: View, rootView: View) {
        val ctx = context ?: return
        val folders = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
        val popup = PopupMenu(ctx, anchor)

        folders.forEachIndexed { index, folder ->
            val count = DemoAccountRepository.getAccountsInFolder(folder.id, ctx).size
            val checkMark = if (folder.id == activeFolderId) "✓ " else "   "
            popup.menu.add(0, index, index, "$checkMark${folder.iconEmoji} ${folder.name} ($count)")
        }

        val createFolderMenuId = 999
        popup.menu.add(1, createFolderMenuId, folders.size + 1, "📁+ Create New Folder...")

        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == createFolderMenuId) {
                showCreateFolderDialog(rootView)
                true
            } else {
                val chosen = folders.getOrNull(item.itemId)
                if (chosen != null) {
                    activeFolderId = chosen.id
                    renderVaultState(rootView)
                }
                true
            }
        }
        popup.show()
    }

    private fun renderVaultState(rootView: View) {
        val ctx = context ?: return
        val btnSegmentUserVault = rootView.findViewById<TextView>(R.id.btnSegmentUserVault)
        val btnSegmentAdminVault = rootView.findViewById<TextView>(R.id.btnSegmentAdminVault)
        val tvSafeModePill = rootView.findViewById<TextView>(R.id.tvSafeModePill)
        val tvSafeTitle = rootView.findViewById<TextView>(R.id.tvSafeTitle)
        val containerBinderTabs = rootView.findViewById<LinearLayout>(R.id.containerBinderTabs)
        val tvActiveFolderHeader = rootView.findViewById<TextView>(R.id.tvActiveFolderHeader)
        val btnDeleteActiveFolder = rootView.findViewById<TextView>(R.id.btnDeleteActiveFolder)
        val tvEmptyFolderHint = rootView.findViewById<TextView>(R.id.tvEmptyFolderHint)
        val containerDemoCards = rootView.findViewById<LinearLayout>(R.id.containerDemoCards)

        val activeTextColor = ContextCompat.getColor(ctx, R.color.vault_segment_active_text)
        val inactiveTextColor = ContextCompat.getColor(ctx, R.color.vault_segment_inactive_text)

        if (currentMode == VaultMode.USER) {
            btnSegmentUserVault?.setBackgroundResource(R.drawable.bg_vault_segment_active)
            btnSegmentUserVault?.setTextColor(activeTextColor)
            btnSegmentAdminVault?.background = null
            btnSegmentAdminVault?.setTextColor(inactiveTextColor)
            tvSafeTitle?.text = "KinTrace Account Studio"
            tvSafeModePill?.text = "Mode 1 • Personal User Account Vault"
        } else {
            btnSegmentAdminVault?.setBackgroundResource(R.drawable.bg_vault_segment_active)
            btnSegmentAdminVault?.setTextColor(activeTextColor)
            btnSegmentUserVault?.background = null
            btnSegmentUserVault?.setTextColor(inactiveTextColor)
            tvSafeTitle?.text = "KinTrace Account Studio"
            tvSafeModePill?.text = "Mode 2 • Admin & Pre-Seeded Defense Vault"
        }

        val folders = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
        val activeFolder = folders.firstOrNull { it.id == activeFolderId } ?: folders.firstOrNull()
        activeFolderId = activeFolder?.id

        // Keep hidden compatibility tab container populated in case any test queries it
        containerBinderTabs?.removeAllViews()
        for (folder in folders) {
            val count = DemoAccountRepository.getAccountsInFolder(folder.id, ctx).size
            val isSelected = folder.id == activeFolder?.id
            val tabView = buildBinderTabView(folder, count, isSelected) {
                activeFolderId = folder.id
                renderVaultState(rootView)
            }
            containerBinderTabs?.addView(tabView)
        }

        containerDemoCards?.removeAllViews()
        if (activeFolder == null) {
            tvActiveFolderHeader?.text = "No Folder Selected"
            btnDeleteActiveFolder?.visibility = View.GONE
            tvEmptyFolderHint?.visibility = View.VISIBLE
            return
        }

        val accountsInFolder = DemoAccountRepository.getAccountsInFolder(activeFolder.id, ctx)
        tvActiveFolderHeader?.text = "${activeFolder.iconEmoji} ${activeFolder.name} (${accountsInFolder.size})"
        btnDeleteActiveFolder?.visibility = if (activeFolder.isSystemFolder) View.GONE else View.VISIBLE

        if (accountsInFolder.isEmpty()) {
            tvEmptyFolderHint?.visibility = View.VISIBLE
        } else {
            tvEmptyFolderHint?.visibility = View.GONE
            val inflater = LayoutInflater.from(ctx)
            for (account in accountsInFolder) {
                val cardView = inflater.inflate(R.layout.item_demo_account, containerDemoCards, false)

                val tvEmoji = cardView.findViewById<TextView>(R.id.tvDemoEmoji)
                val tvName = cardView.findViewById<TextView>(R.id.tvDemoName)
                val tvBadge = cardView.findViewById<TextView>(R.id.tvDemoBadge)
                val tvKeycardEmail = cardView.findViewById<TextView>(R.id.tvKeycardEmail)
                val tvSummary = cardView.findViewById<TextView>(R.id.tvDemoSummary)
                val btnDeleteKeycard = cardView.findViewById<TextView>(R.id.btnDeleteKeycard)

                tvEmoji?.text = account.emojiIcon
                tvName?.text = account.name
                tvBadge?.text = account.roleBadge
                tvKeycardEmail?.text = account.email
                tvSummary?.text = account.treeSummary

                if (account.isCustomAccount) {
                    btnDeleteKeycard?.visibility = View.VISIBLE
                    btnDeleteKeycard?.setOnClickListener {
                        DemoAccountRepository.deleteAccountFromFolder(account.id, ctx)
                        firestoreHelper.deleteVaultAccountFromCloud(account.id)
                        renderVaultState(rootView)
                    }
                } else {
                    btnDeleteKeycard?.visibility = View.GONE
                }

                cardView.setOnClickListener {
                    dismiss()
                    onAccountSelected?.invoke(account)
                }

                containerDemoCards?.addView(cardView)
            }
        }
    }

    private fun buildBinderTabView(
        folder: VaultFolder,
        count: Int,
        isSelected: Boolean,
        onClick: () -> Unit
    ): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            text = "${folder.iconEmoji} ${folder.name} ($count)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create(Typeface.DEFAULT, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            setOnClickListener { onClick() }
        }
    }

    /**
     * Opens a dialog allowing the user to create a new folder inside the active VaultMode.
     */
    private fun showCreateFolderDialog(rootView: View) {
        val ctx = context ?: return
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(18)
            setPadding(pad, dpToPx(10), pad, dpToPx(4))
        }

        val tvModeHint = TextView(ctx).apply {
            text = "Creating inside: ${currentMode.displayTitle}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(ContextCompat.getColor(ctx, R.color.gold_accent))
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(tvModeHint)

        val etFolderName = EditText(ctx).apply {
            hint = "Folder Name (e.g., My Clan Accounts, Test Viewers)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(etFolderName, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(8) })

        val emblemOptions = listOf(
            "📁 Archive Folder" to "📁",
            "👑 Patriarch / Owner" to "👑",
            "🌿 Botanical Branch" to "🌿",
            "🛡️ Citadel Guard" to "🛡️",
            "👁️ Viewer Group" to "👁️"
        )
        val spinnerEmblem = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                emblemOptions.map { it.first }
            )
        }
        container.addView(spinnerEmblem, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(10) })

        KinTraceModalDialog.Builder(ctx)
            .setTitle("📁 Create New Vault Folder")
            .setView(container)
            .setPositiveButton("Create Folder") { dlg, _ ->
                val rawName = etFolderName.text?.toString()?.trim().orEmpty()
                if (rawName.isEmpty()) {
                    Toast.makeText(ctx, "Please enter a folder name.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedIcon = emblemOptions.getOrNull(spinnerEmblem.selectedItemPosition)?.second ?: "📁"
                val created = DemoAccountRepository.createFolder(
                    name = rawName,
                    iconEmoji = selectedIcon,
                    mode = currentMode,
                    context = ctx
                )
                firestoreHelper.syncVaultFolderToCloud(created)
                activeFolderId = created.id
                dlg.dismiss()
                renderVaultState(rootView)
            }
            .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    /**
     * Opens a dialog allowing the user to add an account credential keycard inside the active folder.
     * When "Save Keycard" is clicked, verifies whether the account was already created in "Create Account"
     * and email-verified. If not, pops up the Heritage Shield Verification Guard advising the user to
     * create and verify the account first.
     */
    private fun showIssueKeycardDialog(rootView: View) {
        val ctx = context ?: return
        val folderId = activeFolderId ?: return
        val folders = DemoAccountRepository.getFoldersByMode(currentMode, ctx)
        val folder = folders.firstOrNull { it.id == folderId } ?: return

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(18)
            setPadding(pad, dpToPx(8), pad, dpToPx(4))
        }

        val tvTargetFolder = TextView(ctx).apply {
            text = "Saving inside folder: ${folder.iconEmoji} ${folder.name}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(ContextCompat.getColor(ctx, R.color.gold_accent))
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(tvTargetFolder)

        val etHolderName = EditText(ctx).apply {
            hint = "Account Label / Holder Name (e.g., Renzy Owner)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(etHolderName, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(6) })

        val etEmail = EditText(ctx).apply {
            hint = "Email Address (e.g., user@gmail.com)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        container.addView(etEmail, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(6) })

        // Password Row with Show/Hide Eye Toggle
        val passRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val etPassword = EditText(ctx).apply {
            hint = "Account Password"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val btnTogglePass = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_visibility_off)
            contentDescription = "Show password"
            val pad = dpToPx(8)
            setPadding(pad, pad, pad, pad)
            var isPassVisible = false
            setOnClickListener {
                isPassVisible = !isPassVisible
                if (isPassVisible) {
                    etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                    setImageResource(R.drawable.ic_visibility)
                } else {
                    etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                    setImageResource(R.drawable.ic_visibility_off)
                }
                etPassword.setSelection(etPassword.text?.length ?: 0)
            }
        }
        passRow.addView(etPassword, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        passRow.addView(btnTogglePass, LinearLayout.LayoutParams(dpToPx(38), dpToPx(38)))
        container.addView(passRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(6) })

        val roleOptions = listOf(
            "👑 OWNER (Clan Owner)" to ("OWNER" to "👑"),
            "🌿 COLLABORATOR (Branch Editor)" to ("COLLABORATOR" to "🌿"),
            "👁️ VIEWER (Read-Only Member)" to ("VIEWER" to "👁️"),
            "🛡️ ADMIN (Citadel Administrator)" to ("ADMIN" to "🛡️")
        )
        val spinnerRole = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                roleOptions.map { it.first }
            )
        }
        container.addView(spinnerRole, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dpToPx(8) })

        var isVerifying = false

        KinTraceModalDialog.Builder(ctx)
            .setTitle("💳 Issue New 1-Tap Keycard")
            .setView(container)
            .setPositiveButton("Save Keycard") { dlg, _ ->
                if (isVerifying) return@setPositiveButton

                val name = etHolderName.text?.toString()?.trim().orEmpty()
                val email = etEmail.text?.toString()?.trim().orEmpty()
                val password = etPassword.text?.toString().orEmpty()

                val existingInFolder = DemoAccountRepository.getAccountsInFolder(folder.id, ctx)
                val preFlightError = KeycardAccountVerifier.validatePreFlightInputs(
                    holderName = name.ifEmpty { email.substringBefore("@") },
                    email = email,
                    password = password,
                    existingFolderAccounts = existingInFolder
                )

                if (preFlightError != null) {
                    when (preFlightError.field) {
                        "name" -> {
                            etHolderName.error = preFlightError.errorMessage
                            etHolderName.requestFocus()
                        }
                        "email" -> {
                            etEmail.error = preFlightError.errorMessage
                            etEmail.requestFocus()
                        }
                        "password" -> {
                            etPassword.error = preFlightError.errorMessage
                            etPassword.requestFocus()
                        }
                    }
                    Toast.makeText(ctx, preFlightError.errorMessage, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                isVerifying = true
                Toast.makeText(ctx, "Verifying account in KinTrace...", Toast.LENGTH_SHORT).show()

                authHelper.verifyAccountForKeycard(
                    holderName = name,
                    email = email,
                    password = password
                ) { outcome ->
                    isVerifying = false
                    if (!isAdded) return@verifyAccountForKeycard

                    when (outcome) {
                        is KeycardAccountVerifier.VerificationOutcome.Verified -> {
                            val selectedRolePair = roleOptions.getOrNull(spinnerRole.selectedItemPosition)?.second ?: ("OWNER" to "👑")
                            val savedAccount = DemoAccountRepository.addAccountToFolder(
                                folderId = folder.id,
                                name = outcome.resolvedName,
                                email = outcome.email,
                                password = password,
                                roleBadge = selectedRolePair.first,
                                emojiIcon = selectedRolePair.second,
                                mode = currentMode,
                                treeSummary = "✓ Verified Account • Folder: ${folder.name}",
                                context = ctx
                            )
                            firestoreHelper.syncVaultAccountToCloud(savedAccount)
                            dlg.dismiss()
                            renderVaultState(rootView)
                            Toast.makeText(ctx, "✓ Verified Keycard saved to ${folder.name}!", Toast.LENGTH_SHORT).show()
                        }

                        is KeycardAccountVerifier.VerificationOutcome.NotRegistered -> {
                            showAccountNotRegisteredPopup(outcome.email, name)
                        }

                        is KeycardAccountVerifier.VerificationOutcome.EmailNotVerified -> {
                            showEmailNotVerifiedPopup(outcome.email)
                        }

                        is KeycardAccountVerifier.VerificationOutcome.WrongPassword -> {
                            showWrongPasswordPopup(outcome.email)
                        }

                        is KeycardAccountVerifier.VerificationOutcome.InvalidInput -> {
                            Toast.makeText(ctx, outcome.errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    /**
     * Heritage Shield Pop-Up Guard when the entered email has NOT been created
     * in "Create Account" (RegisterActivity) yet.
     */
    private fun showAccountNotRegisteredPopup(email: String, holderName: String) {
        val ctx = context ?: return
        val message = "The account \"$email\" is not registered in KinTrace yet.\n\n" +
            "💡 What to do first:\n" +
            "1. Go to \"Create Account\" and register this account first.\n" +
            "2. Open your email inbox and click the Verification Link.\n" +
            "3. Come back here to add it as a 1-Tap Keycard!"

        KinTraceModalDialog.Builder(ctx)
            .setTitle("🛡️ Account Not Registered")
            .setMessage(message)
            .setPositiveButton("Create Account ➔") { popupDlg, _ ->
                popupDlg.dismiss()
                dismissAllowingStateLoss()
                val intent = Intent(ctx, RegisterActivity::class.java).apply {
                    putExtra("EXTRA_PREFILL_EMAIL", email)
                    putExtra("EXTRA_PREFILL_NAME", holderName)
                }
                startActivity(intent)
            }
            .setNegativeButton("Got It") { popupDlg, _ ->
                popupDlg.dismiss()
            }
            .show()
    }

    /**
     * Heritage Shield Pop-Up Guard when the account was created in "Create Account",
     * but its email address has not been verified yet.
     */
    private fun showEmailNotVerifiedPopup(email: String) {
        val ctx = context ?: return
        val message = "We found \"$email\" in KinTrace, but its email address has not been verified yet.\n\n" +
            "💡 Verify before adding Keycard:\n" +
            "Please check the email inbox for \"$email\" and tap the verification link first before putting it on a 1-Tap Keycard."

        KinTraceModalDialog.Builder(ctx)
            .setTitle("✉️ Account Not Verified Yet")
            .setMessage(message)
            .setPositiveButton("OK, I'll Verify First") { popupDlg, _ ->
                popupDlg.dismiss()
            }
            .show()
    }

    /**
     * Pop-Up Guard when the account exists in KinTrace, but the password entered is incorrect.
     */
    private fun showWrongPasswordPopup(email: String) {
        val ctx = context ?: return
        val message = "The account \"$email\" is registered in KinTrace, but the password you entered does not match.\n\n" +
            "Please enter the correct password for this account so 1-Tap Auto-Login can sign in cleanly."

        KinTraceModalDialog.Builder(ctx)
            .setTitle("🔒 Incorrect Account Password")
            .setMessage(message)
            .setPositiveButton("Try Again") { popupDlg, _ ->
                popupDlg.dismiss()
            }
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        const val TAG = "DemoVaultBottomSheet"

        fun newInstance(onSelected: (DemoAccount) -> Unit): DemoVaultBottomSheet {
            return DemoVaultBottomSheet().apply {
                this.onAccountSelected = onSelected
            }
        }
    }
}
