package com.example.btproject2.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.views.BotanicalForestFxOverlayView
import com.example.btproject2.views.BotanicalRoleStageView
import java.lang.ref.WeakReference
import kotlin.math.sin

/**
 * Global Controller for the 60fps Botanical Family-Tree Role Badge Ceremony &
 * Dimmed Restricted-Action Gate Flash.
 *
 * Protected by strict lifecycle and fail-safe guards so rapid taps, back presses,
 * or activity destruction can never leak windows or crash the app.
 */
object BotanicalRoleFlashManager {

    private const val PREFS_NAME = "kintrace_botanical_role_prefs"
    private var activeDialogRef: WeakReference<Dialog>? = null

    /**
     * Checks if the user's role in [treeId] is newly assigned or changed since last seen,
     * and if so, plays the Botanical Role Badge Assignment Ceremony once per role change.
     */
    fun checkAndShowRoleCeremonyIfChanged(
        activity: Activity,
        treeId: String,
        treeName: String,
        rawRole: String?,
        isTreeOwner: Boolean = false
    ) {
        try {
            if (activity.isFinishing || activity.isDestroyed || treeId.isBlank()) return
            val tier = BotanicalRolePolicy.resolveTier(rawRole, isTreeOwner)
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "seen_role_${treeId}"
            val lastSeenRole = prefs.getString(key, null)
            if (lastSeenRole != tier.roleKey) {
                prefs.edit().putString(key, tier.roleKey).apply()
                showRoleAssignedCeremony(
                    activity = activity,
                    rawRole = tier.roleKey,
                    isTreeOwner = isTreeOwner,
                    treeName = treeName
                )
            }
        } catch (_: Throwable) {
            // Never allow ceremony check to interrupt host screen startup
        }
    }

    /**
     * Shows the 60fps Botanical Role Badge Assignment Ceremony Dialog.
     */
    fun showRoleAssignedCeremony(
        activity: Activity,
        rawRole: String?,
        isTreeOwner: Boolean = false,
        treeName: String = ""
    ) {
        showInternal(
            activity = activity,
            isCeremony = true,
            rawRole = rawRole,
            isTreeOwner = isTreeOwner,
            featureTitle = "",
            requiredTier = BotanicalRolePolicy.BotanicalTier.SPROUT_VIEWER,
            treeId = "",
            treeName = treeName,
            ownerId = "",
            onDismiss = null
        )
    }

    /**
     * Shows the 60fps Dimmed Botanical Restricted-Action Flash Dialog when a Viewer or Editor
     * taps an action outside their current role capabilities.
     */
    fun showRestrictedActionFlash(
        activity: Activity,
        rawRole: String?,
        isTreeOwner: Boolean = false,
        featureTitle: String,
        requiredTier: BotanicalRolePolicy.BotanicalTier = BotanicalRolePolicy.BotanicalTier.BRANCH_EDITOR,
        treeId: String = "",
        treeName: String = "",
        ownerId: String = "",
        onDismiss: (() -> Unit)? = null
    ) {
        showInternal(
            activity = activity,
            isCeremony = false,
            rawRole = rawRole,
            isTreeOwner = isTreeOwner,
            featureTitle = featureTitle,
            requiredTier = requiredTier,
            treeId = treeId,
            treeName = treeName,
            ownerId = ownerId,
            onDismiss = onDismiss
        )
    }

    private fun showInternal(
        activity: Activity,
        isCeremony: Boolean,
        rawRole: String?,
        isTreeOwner: Boolean,
        featureTitle: String,
        requiredTier: BotanicalRolePolicy.BotanicalTier,
        treeId: String,
        treeName: String,
        ownerId: String,
        onDismiss: (() -> Unit)?
    ) {
        try {
            if (activity.isFinishing || activity.isDestroyed) return

            // Dismiss any existing flash dialog cleanly
            dismissActiveDialogSafely()

            val userTier = BotanicalRolePolicy.resolveTier(rawRole, isTreeOwner)
            val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val rootView = LayoutInflater.from(activity).inflate(R.layout.dialog_botanical_role_flash, null, false)
            dialog.setContentView(rootView)
            dialog.window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }

            val density = activity.resources.displayMetrics.density
            val fxView = rootView.findViewById<BotanicalForestFxOverlayView?>(R.id.botanicalForestFxView)
            val stageView = rootView.findViewById<BotanicalRoleStageView?>(R.id.botanicalRoleStageView)
            val cardModal = rootView.findViewById<View?>(R.id.cardBotanicalFlashModal)
            val tvBadgePill = rootView.findViewById<TextView?>(R.id.tvFlashRoleBadgePill)
            val tvTitle = rootView.findViewById<TextView?>(R.id.tvFlashTitle)
            val tvSubtitle = rootView.findViewById<TextView?>(R.id.tvFlashSubtitle)
            val tvCapHeader = rootView.findViewById<TextView?>(R.id.tvCapabilitiesHeader)
            val tvCapStatus1 = rootView.findViewById<TextView?>(R.id.tvCapStatus1)
            val tvCapStatus2 = rootView.findViewById<TextView?>(R.id.tvCapStatus2)
            val tvCapStatus3 = rootView.findViewById<TextView?>(R.id.tvCapStatus3)
            val tvCapStatus4 = rootView.findViewById<TextView?>(R.id.tvCapStatus4)
            val tvCapIcon1 = rootView.findViewById<TextView?>(R.id.tvCapIcon1)
            val tvCapIcon2 = rootView.findViewById<TextView?>(R.id.tvCapIcon2)
            val tvCapIcon3 = rootView.findViewById<TextView?>(R.id.tvCapIcon3)
            val tvCapIcon4 = rootView.findViewById<TextView?>(R.id.tvCapIcon4)
            val rowCap1 = rootView.findViewById<View?>(R.id.rowCap1)
            val rowCap2 = rootView.findViewById<View?>(R.id.rowCap2)
            val rowCap3 = rootView.findViewById<View?>(R.id.rowCap3)
            val rowCap4 = rootView.findViewById<View?>(R.id.rowCap4)
            val tvHint = rootView.findViewById<TextView?>(R.id.tvUpgradeGuidanceHint)
            val btnPrimary = rootView.findViewById<Button?>(R.id.btnFlashPrimaryAction)
            val btnSecondary = rootView.findViewById<Button?>(R.id.btnFlashSecondaryDismiss)
            val rootOverlay = rootView.findViewById<View?>(R.id.rootBotanicalFlashOverlay)

            val isDark = ThemePreferences.isDarkTheme(activity)
            val mainTextHex = if (isDark) "#F4F7F4" else "#1C241E"
            applyThemePalette(activity, rootView)

            // Guarantee Georgia Serif Bold on the modal title (Matches Photo 2)
            tvTitle?.typeface = Typeface.create("serif", Typeface.BOLD)

            // Style role badge pill border & tint dynamically to match user's tier color
            tvBadgePill?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                val c = userTier.primaryColorHex
                setColor(Color.argb(36, Color.red(c), Color.green(c), Color.blue(c)))
                setStroke((1f * density).toInt().coerceAtLeast(1), c)
            }

            stageView?.setBotanicalTier(userTier)

            // Continuous 60fps gentle leaf rustle on the 4 capability icons (Matches Photo 2 .leaf-sway-ico)
            val capIcons = listOf(tvCapIcon1, tvCapIcon2, tvCapIcon3, tvCapIcon4)
            stageView?.onFrameTickListener = { elapsedSec ->
                capIcons.forEachIndexed { i, iconView ->
                    iconView?.rotation = sin(elapsedSec * 2.6f + i * 0.9f) * 7f
                }
            }

            // Populate capability statuses
            val checklist = BotanicalRolePolicy.buildCapabilitiesChecklist(userTier)
            val statusViews = listOf(tvCapStatus1, tvCapStatus2, tvCapStatus3, tvCapStatus4)
            checklist.forEachIndexed { idx, item ->
                val tv = statusViews.getOrNull(idx) ?: return@forEachIndexed
                tv.text = item.statusText
                tv.setTextColor(
                    if (item.isUnlocked) Color.parseColor("#34D399") else Color.parseColor("#D4AF37")
                )
            }

            tvCapHeader?.text = "🌿 WHAT YOUR ${userTier.symbol} ${userTier.displayRoleName.uppercase()} BADGE CAN DO"

            var dismissCallbackFired = false
            val invokeOnDismissOnce: () -> Unit = {
                if (!dismissCallbackFired) {
                    dismissCallbackFired = true
                    onDismiss?.invoke()
                }
            }

            if (isCeremony) {
                tvBadgePill?.text = "🍃 ${userTier.botanicalTitle.uppercase()}"
                tvBadgePill?.setTextColor(userTier.primaryColorHex)
                tvTitle?.text = when (userTier) {
                    BotanicalRolePolicy.BotanicalTier.CANOPY_OWNER -> "You Are the Tree Owner"
                    BotanicalRolePolicy.BotanicalTier.BRANCH_EDITOR -> "You Are an Editor in This Tree"
                    BotanicalRolePolicy.BotanicalTier.SPROUT_VIEWER -> "You Are a Viewer in This Tree"
                }
                tvSubtitle?.text = userTier.summary
                tvHint?.visibility = View.GONE
                btnPrimary?.text = "🌿 Enter the Family Grove"
                btnSecondary?.visibility = View.GONE

                btnPrimary?.setOnClickListener {
                    dismissDialogSafely(dialog, stageView, fxView, invokeOnDismissOnce)
                }
            } else {
                val sproutOrBranch = if (userTier == BotanicalRolePolicy.BotanicalTier.SPROUT_VIEWER) "SPROUT" else "BRANCH"
                tvBadgePill?.text = "${userTier.symbol} YOU'RE CURRENTLY A ${userTier.displayRoleName.uppercase()} ($sproutOrBranch)"
                tvBadgePill?.setTextColor(userTier.primaryColorHex)
                tvTitle?.text = featureTitle.ifBlank { "Deep Roots Privacy & Access" }

                val subtitleHtml = "This part of the grove requires the <b><font color=\"$mainTextHex\">${requiredTier.symbol} ${requiredTier.botanicalTitle}</font></b> badge."
                tvSubtitle?.text = HtmlCompat.fromHtml(subtitleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

                tvHint?.visibility = View.VISIBLE
                val hintHtml = "🌱 <b><font color=\"$mainTextHex\">Want your role to grow?</font></b> Ask the owner of this family tree to nurture &amp; upgrade your role so you can access this feature!"
                tvHint?.text = HtmlCompat.fromHtml(hintHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

                btnPrimary?.text = "🍃 Send Golden Leaf Request to Tree Owner"
                btnSecondary?.visibility = View.VISIBLE
                btnSecondary?.text = "Return to Family Tree"

                btnPrimary?.setOnClickListener { btnView ->
                    btnPrimary.isEnabled = false
                    btnPrimary.text = "🍃 Floating Golden Leaf to Tree Owner..."

                    val startX = (rootView.width / 2f).coerceAtLeast(180f)
                    val startY = (btnView.top + (cardModal?.top ?: 300)).toFloat().coerceAtLeast(450f)
                    val endX = startX
                    val endY = ((cardModal?.top ?: 180) + 90).toFloat().coerceAtLeast(160f)

                    // Send real Firestore upgrade notification to Tree Owner
                    val authHelper = AuthHelper()
                    val currentUser = authHelper.getCurrentUser()
                    val requesterId = currentUser?.uid ?: "guest_user"
                    val requesterName = currentUser?.displayName?.takeIf { it.isNotBlank() }
                        ?: currentUser?.email?.substringBefore("@")
                        ?: "A Clan Member"

                    val effectiveTreeId = treeId.ifBlank {
                        TreePreferences.getActiveTreeId(activity)
                    }
                    val effectiveTreeName = treeName.ifBlank {
                        TreePreferences.getActiveTreeName(activity)
                    }

                    FirestoreHelper().sendRoleUpgradeRequestToOwner(
                        treeId = effectiveTreeId,
                        treeName = effectiveTreeName,
                        ownerId = ownerId,
                        requesterId = requesterId,
                        requesterName = requesterName,
                        currentRole = userTier.displayRoleName,
                        requestedRole = requiredTier.displayRoleName,
                        featureName = featureTitle
                    )

                    fxView?.launchGoldenMessengerLeaf(
                        startX = startX,
                        startY = startY,
                        endX = endX,
                        endY = endY
                    ) {
                        if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing) {
                            btnPrimary.text = "🌳 Request Reached the Tree Owner!"
                            btnView.postDelayed({
                                dismissDialogSafely(dialog, stageView, fxView, invokeOnDismissOnce)
                            }, 850L)
                        }
                    }
                }

                btnSecondary?.setOnClickListener {
                    dismissDialogSafely(dialog, stageView, fxView, invokeOnDismissOnce)
                }
            }

            // Tap outside card to dismiss
            rootOverlay?.setOnClickListener {
                dismissDialogSafely(dialog, stageView, fxView, invokeOnDismissOnce)
            }
            cardModal?.setOnClickListener {
                // Consume clicks inside the card so tapping the card doesn't dismiss
            }

            dialog.setOnCancelListener {
                stageView?.onFrameTickListener = null
                stageView?.stopAnimationLoop()
                fxView?.stopFxLoop()
                invokeOnDismissOnce()
            }

            dialog.setOnDismissListener {
                stageView?.onFrameTickListener = null
                stageView?.stopAnimationLoop()
                fxView?.stopFxLoop()
                invokeOnDismissOnce()
            }

            dialog.show()
            activeDialogRef = WeakReference(dialog)

            // Play smooth organic entrance animation on card & staggered root-grow on capability rows
            cardModal?.apply {
                scaleX = 0.86f
                scaleY = 0.86f
                translationY = 36f
                alpha = 0f
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(380L)
                    .setInterpolator(OvershootInterpolator(1.08f))
                    .start()
            }

            listOf(rowCap1, rowCap2, rowCap3, rowCap4).forEachIndexed { idx, row ->
                row?.apply {
                    alpha = 0f
                    translationY = 14f
                    animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(70L + idx * 60L)
                        .setDuration(280L)
                        .start()
                }
            }

            // Trigger 60fps concentric tree-trunk ripple & drifting golden/emerald leaves
            rootView.post {
                if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing) {
                    val cx = (rootView.width / 2f).coerceAtLeast(180f)
                    val cy = (rootView.height * 0.36f).coerceAtLeast(240f)
                    val accent = if (isCeremony) userTier.primaryColorHex else Color.parseColor("#D4AF37")
                    fxView?.triggerTreeRingAndLeaves(cx, cy, accent)
                }
            }
        } catch (_: Throwable) {
            // Fail-safe guard: never crash host Activity
        }
    }

    private fun dismissDialogSafely(
        dialog: Dialog,
        stageView: BotanicalRoleStageView?,
        fxView: BotanicalForestFxOverlayView?,
        onDismiss: (() -> Unit)?
    ) {
        try {
            stageView?.onFrameTickListener = null
            stageView?.stopAnimationLoop()
            fxView?.stopFxLoop()
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        } catch (_: Throwable) {}
        onDismiss?.invoke()
    }

    fun applyThemePalette(context: Context, rootView: View) {
        val isDark = ThemePreferences.isDarkTheme(context)
        val density = context.resources.displayMetrics.density

        // Exact CSS variables from kintrace_role_badge_flash_prototype.html (:root vs body.light-theme)
        val bgCard = Color.parseColor(if (isDark) "#0C1C13" else "#FDFBF7")
        val bgSurface = Color.parseColor(if (isDark) "#132B1E" else "#EAE4D7")
        val borderEmerald = Color.parseColor(if (isDark) "#1E3F2B" else "#C9BFA8")
        val gold = Color.parseColor(if (isDark) "#D4AF37" else "#9A5B13")
        val textMain = Color.parseColor(if (isDark) "#F4F7F4" else "#1C241E")
        val textMuted = Color.parseColor(if (isDark) "#9DB2A4" else "#57635A")

        rootView.findViewById<View?>(R.id.cardBotanicalFlashModal)?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * density
            setColor(bgCard)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), gold)
        }

        rootView.findViewById<View?>(R.id.layoutCapabilitiesBox)?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(bgSurface)
            setStroke((1f * density).toInt().coerceAtLeast(1), borderEmerald)
        }

        rootView.findViewById<TextView?>(R.id.tvFlashTitle)?.setTextColor(textMain)
        rootView.findViewById<TextView?>(R.id.tvFlashSubtitle)?.setTextColor(textMuted)
        rootView.findViewById<TextView?>(R.id.tvCapabilitiesHeader)?.setTextColor(gold)

        listOf(
            R.id.tvCapLabel1,
            R.id.tvCapLabel2,
            R.id.tvCapLabel3,
            R.id.tvCapLabel4
        ).forEach { id ->
            rootView.findViewById<TextView?>(id)?.setTextColor(textMain)
        }

        rootView.findViewById<TextView?>(R.id.tvUpgradeGuidanceHint)?.apply {
            setTextColor(textMain)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(Color.parseColor("#1434D399"))
                setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#66D4AF37"))
            }
        }

        rootView.findViewById<Button?>(R.id.btnFlashSecondaryDismiss)?.apply {
            setTextColor(textMain)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(if (isDark) Color.parseColor("#08140D") else Color.TRANSPARENT)
                setStroke((1.5f * density).toInt().coerceAtLeast(1), borderEmerald)
            }
        }
    }

    fun dismissActiveDialogSafely() {
        try {
            val existing = activeDialogRef?.get()
            if (existing != null && existing.isShowing) {
                existing.dismiss()
            }
        } catch (_: Throwable) {}
        activeDialogRef = null
    }
}
