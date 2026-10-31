package com.example.btproject2.ui.dialogs

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import kotlin.math.sin
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.utils.BotanicalRoleFlashManager
import com.example.btproject2.utils.BotanicalRolePolicy
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.ThemePreferences
import com.example.btproject2.utils.TreePreferences
import com.example.btproject2.views.BotanicalForestFxOverlayView
import com.example.btproject2.views.BotanicalRoleStageView

/**
 * ActionRestrictedDialog:
 * Renders the 60fps Botanical Family-Tree Role-Gate Flash when a Viewer (🌱 Young Sprout)
 * or Editor (🌿 Branch Tender) attempts an action outside their role clearance.
 *
 * Features:
 * - 60fps Growing Family Tree Stage (🌱 Sprout -> 🌿 Branch -> 🌳 Canopy)
 * - Concentric Tree-Trunk Ring Ripple & Drifting Botanical Leaves
 * - 4-Row Botanical Capabilities Checklist with staggered root-grow entrance
 * - Wind-Borne Golden Messenger Leaf animation on "🍃 Send Golden Leaf Request to Tree Owner"
 */
class ActionRestrictedDialog : DialogFragment() {

    var onRequestAccess: (() -> Unit)? = null
    var onDismissAction: (() -> Unit)? = null
    var treeId: String = ""
    var treeName: String = ""
    var actionAttempted: String = "Edit Member Records"

    private val authHelper by lazy { AuthHelper() }
    private val firestoreHelper by lazy { FirestoreHelper() }

    private var stageViewRef: BotanicalRoleStageView? = null
    private var fxViewRef: BotanicalForestFxOverlayView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar)
        arguments?.let {
            val argTreeId = it.getString(ARG_TREE_ID, "")
            if (argTreeId.isNotBlank()) treeId = argTreeId
            val argTreeName = it.getString(ARG_TREE_NAME, "Family Tree")
            if (argTreeName.isNotBlank()) treeName = argTreeName
            val argAction = it.getString(ARG_ACTION_ATTEMPTED, "Edit Member Records")
            if (argAction.isNotBlank()) actionAttempted = argAction
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return inflater.inflate(R.layout.dialog_botanical_role_flash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val fxView = view.findViewById<BotanicalForestFxOverlayView?>(R.id.botanicalForestFxView)
            val stageView = view.findViewById<BotanicalRoleStageView?>(R.id.botanicalRoleStageView)
            val cardModal = view.findViewById<View?>(R.id.cardBotanicalFlashModal)
            val tvBadgePill = view.findViewById<TextView?>(R.id.tvFlashRoleBadgePill)
            val tvTitle = view.findViewById<TextView?>(R.id.tvFlashTitle)
            val tvSubtitle = view.findViewById<TextView?>(R.id.tvFlashSubtitle)
            val tvCapHeader = view.findViewById<TextView?>(R.id.tvCapabilitiesHeader)
            val tvCapStatus1 = view.findViewById<TextView?>(R.id.tvCapStatus1)
            val tvCapStatus2 = view.findViewById<TextView?>(R.id.tvCapStatus2)
            val tvCapStatus3 = view.findViewById<TextView?>(R.id.tvCapStatus3)
            val tvCapStatus4 = view.findViewById<TextView?>(R.id.tvCapStatus4)
            val tvCapIcon1 = view.findViewById<TextView?>(R.id.tvCapIcon1)
            val tvCapIcon2 = view.findViewById<TextView?>(R.id.tvCapIcon2)
            val tvCapIcon3 = view.findViewById<TextView?>(R.id.tvCapIcon3)
            val tvCapIcon4 = view.findViewById<TextView?>(R.id.tvCapIcon4)
            val rowCap1 = view.findViewById<View?>(R.id.rowCap1)
            val rowCap2 = view.findViewById<View?>(R.id.rowCap2)
            val rowCap3 = view.findViewById<View?>(R.id.rowCap3)
            val rowCap4 = view.findViewById<View?>(R.id.rowCap4)
            val tvHint = view.findViewById<TextView?>(R.id.tvUpgradeGuidanceHint)
            val btnPrimary = view.findViewById<Button?>(R.id.btnFlashPrimaryAction)
            val btnSecondary = view.findViewById<Button?>(R.id.btnFlashSecondaryDismiss)
            val rootOverlay = view.findViewById<View?>(R.id.rootBotanicalFlashOverlay)

            stageViewRef = stageView
            fxViewRef = fxView

            val ctxForTheme = context
            val isDark = if (ctxForTheme != null) ThemePreferences.isDarkTheme(ctxForTheme) else true
            val mainTextHex = if (isDark) "#F4F7F4" else "#1C241E"
            if (ctxForTheme != null) {
                BotanicalRoleFlashManager.applyThemePalette(ctxForTheme, view)
            }

            tvTitle?.typeface = Typeface.create("serif", Typeface.BOLD)

            val userTier = BotanicalRolePolicy.BotanicalTier.SPROUT_VIEWER
            val requiredTier = if (actionAttempted.contains("Privacy", ignoreCase = true) ||
                actionAttempted.contains("Owner", ignoreCase = true)
            ) {
                BotanicalRolePolicy.BotanicalTier.CANOPY_OWNER
            } else {
                BotanicalRolePolicy.BotanicalTier.BRANCH_EDITOR
            }

            stageView?.setBotanicalTier(userTier)
            val capIcons = listOf(tvCapIcon1, tvCapIcon2, tvCapIcon3, tvCapIcon4)
            stageView?.onFrameTickListener = { elapsedSec ->
                capIcons.forEachIndexed { i, iconView ->
                    iconView?.rotation = sin(elapsedSec * 2.6f + i * 0.9f) * 7f
                }
            }

            val checklist = BotanicalRolePolicy.buildCapabilitiesChecklist(userTier)
            val statusViews = listOf(tvCapStatus1, tvCapStatus2, tvCapStatus3, tvCapStatus4)
            checklist.forEachIndexed { idx, item ->
                val tv = statusViews.getOrNull(idx) ?: return@forEachIndexed
                tv.text = item.statusText
                tv.setTextColor(
                    if (item.isUnlocked) Color.parseColor("#34D399") else Color.parseColor("#D4AF37")
                )
            }

            val treeDisplay = treeName.ifBlank { "this family tree" }
            tvBadgePill?.text = "${userTier.symbol} YOU'RE CURRENTLY A VIEWER (SPROUT)"
            tvBadgePill?.setTextColor(userTier.primaryColorHex)
            tvCapHeader?.text = "🌿 WHAT YOUR 🌱 VIEWER BADGE CAN DO"
            tvTitle?.text = actionAttempted.ifBlank { "Graft / Edit Family Members" }

            val subtitleHtml = "This part of the grove requires the <b><font color=\"$mainTextHex\">${requiredTier.symbol} ${requiredTier.botanicalTitle}</font></b> badge."
            tvSubtitle?.text = HtmlCompat.fromHtml(subtitleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

            val hintHtml = "🌱 <b><font color=\"$mainTextHex\">Want your role to grow?</font></b> Ask the owner of this family tree to nurture &amp; upgrade your role so you can access this feature!"
            tvHint?.text = HtmlCompat.fromHtml(hintHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

            btnPrimary?.text = "🍃 Send Golden Leaf Request to Tree Owner"
            btnSecondary?.text = "Return to Family Tree"

            btnSecondary?.setOnClickListener {
                dismissSafely()
            }
            rootOverlay?.setOnClickListener {
                dismissSafely()
            }
            cardModal?.setOnClickListener {
                // Consume inside card clicks
            }

            btnPrimary?.setOnClickListener { btnView ->
                btnPrimary.isEnabled = false
                btnPrimary.text = "🍃 Floating Golden Leaf to Tree Owner..."

                val currentUser = authHelper.getCurrentUser()
                val currentUserId = currentUser?.uid.orEmpty()
                val requesterName = currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser?.email?.substringBefore("@")
                    ?: "A Family Member"
                val ctx = context
                val effectiveTreeId = treeId.ifBlank {
                    if (ctx != null) TreePreferences.getActiveTreeId(ctx) else ""
                }

                if (effectiveTreeId.isNotBlank() && currentUserId.isNotBlank()) {
                    val notif = NotificationHelper.createPermissionRestrictedNotification(
                        treeId = effectiveTreeId,
                        userId = currentUserId,
                        actionAttempted = actionAttempted,
                        currentRole = "Viewer"
                    )
                    firestoreHelper.addNotification(notif)
                    firestoreHelper.sendRoleUpgradeRequestToOwner(
                        treeId = effectiveTreeId,
                        treeName = treeDisplay,
                        requesterId = currentUserId,
                        requesterName = requesterName,
                        currentRole = userTier.displayRoleName,
                        requestedRole = requiredTier.displayRoleName,
                        featureName = actionAttempted,
                        requesterEmail = currentUser?.email.orEmpty()
                    )
                }

                val startX = (view.width / 2f).coerceAtLeast(180f)
                val startY = (btnView.top + (cardModal?.top ?: 300)).toFloat().coerceAtLeast(450f)
                val endX = startX
                val endY = ((cardModal?.top ?: 180) + 90).toFloat().coerceAtLeast(160f)

                fxView?.launchGoldenMessengerLeaf(startX, startY, endX, endY) {
                    if (isAdded) {
                        btnPrimary.text = "🌳 Request Reached the Tree Owner!"
                        onRequestAccess?.invoke()
                        btnView.postDelayed({
                            dismissSafely()
                        }, 800L)
                    }
                }
            }

            cardModal?.apply {
                scaleX = 0.82f
                scaleY = 0.82f
                translationY = 48f
                alpha = 0f
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(420L)
                    .setInterpolator(OvershootInterpolator(1.15f))
                    .start()
            }

            listOf(rowCap1, rowCap2, rowCap3, rowCap4).forEachIndexed { idx, row ->
                row?.apply {
                    alpha = 0f
                    translationY = 18f
                    animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(90L + idx * 70L)
                        .setDuration(320L)
                        .start()
                }
            }

            view.post {
                if (isAdded) {
                    val cx = (view.width / 2f).coerceAtLeast(180f)
                    val cy = (view.height * 0.36f).coerceAtLeast(240f)
                    fxView?.triggerTreeRingAndLeaves(cx, cy, Color.parseColor("#D4AF37"))
                }
            }
        } catch (_: Throwable) {}
    }

    private fun dismissSafely() {
        try {
            stageViewRef?.stopAnimationLoop()
            fxViewRef?.stopFxLoop()
            if (isAdded) {
                dismissAllowingStateLoss()
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroyView() {
        stageViewRef?.stopAnimationLoop()
        fxViewRef?.stopFxLoop()
        stageViewRef = null
        fxViewRef = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        stageViewRef?.stopAnimationLoop()
        fxViewRef?.stopFxLoop()
        super.onDismiss(dialog)
        onDismissAction?.invoke()
    }

    companion object {
        const val TAG = "ActionRestrictedDialog"
        const val ARG_TREE_ID = "arg_tree_id"
        const val ARG_TREE_NAME = "arg_tree_name"
        const val ARG_ACTION_ATTEMPTED = "arg_action_attempted"

        fun newInstance(
            treeId: String = "",
            treeName: String = "",
            actionAttempted: String = "Edit Member Records"
        ): ActionRestrictedDialog {
            return ActionRestrictedDialog().apply {
                this.treeId = treeId
                this.treeName = treeName
                this.actionAttempted = actionAttempted
                try {
                    arguments = Bundle().apply {
                        putString(ARG_TREE_ID, treeId)
                        putString(ARG_TREE_NAME, treeName)
                        putString(ARG_ACTION_ATTEMPTED, actionAttempted)
                    }
                } catch (_: Throwable) {
                    // Safe fallback in pure JVM unit test environments where Bundle is unmocked
                }
            }
        }
    }
}
