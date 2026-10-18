package com.example.btproject2.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.btproject2.R
import java.lang.ref.WeakReference

/**
 * KinTrace Concept 5: Adaptive Smart Notifier Engine
 *
 * Single source of truth for in-app alert notifications.
 * Displays bespoke, heraldic emerald HUD notifications directly within
 * the window decor view, completely bypassing stock Android OS toast wrappers.
 */
object KinTraceAlert {

    const val LENGTH_SHORT = 2500L
    const val LENGTH_LONG = 4200L

    enum class AlertArchetype {
        TELEMETRY,
        RESTORE,
        CONFLICT,
        SEVER,
        WARNING,
        CANVAS,
        KEY,
        LOCK,
        GENERIC_SUCCESS,
        GENERIC_INFO
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentAlertView: WeakReference<View>? = null
    private var dismissRunnable: Runnable? = null

    /**
     * Universal 1:1 drop-in replacement for Toast.makeText(...).show()
     * Automatically classifies the message into the appropriate heraldic archetype.
     */
    @JvmStatic
    fun show(context: Context?, message: CharSequence?, durationMs: Long = LENGTH_SHORT) {
        if (context == null || message.isNullOrBlank()) return

        val msgStr = message.toString()
        val archetype = inferArchetype(msgStr)

        val title = when (archetype) {
            AlertArchetype.TELEMETRY -> "Citadel Telemetry"
            AlertArchetype.RESTORE -> "Restoration Event"
            AlertArchetype.CONFLICT -> "Conflict Resolution"
            AlertArchetype.SEVER -> "Record Removed"
            AlertArchetype.WARNING -> "Kinship Rule Alert"
            AlertArchetype.CANVAS -> "Tree Navigation"
            AlertArchetype.KEY -> "Clipboard & Security"
            AlertArchetype.LOCK -> "Privacy & Access"
            AlertArchetype.GENERIC_SUCCESS -> "Heritage System"
            AlertArchetype.GENERIC_INFO -> "KinTrace Notice"
        }

        showAlert(
            context = context,
            title = title,
            message = msgStr,
            archetype = archetype,
            badgeText = getBadgeForArchetype(archetype, msgStr),
            actionText = null,
            onAction = null,
            durationMs = durationMs
        )
    }

    /**
     * Specialized: Telemetry & OTA Deployments
     */
    @JvmStatic
    fun telemetry(context: Context?, title: String, message: String, badge: String? = "LIVE") {
        showAlert(context, title, message, AlertArchetype.TELEMETRY, badgeText = badge)
    }

    /**
     * Specialized: Member & Branch Restorations
     */
    @JvmStatic
    fun restore(context: Context?, title: String, message: String, badge: String? = "RESTORED") {
        showAlert(context, title, message, AlertArchetype.RESTORE, badgeText = badge)
    }

    /**
     * Specialized: Conflict Escalation & Canonical Merges
     */
    @JvmStatic
    fun conflict(context: Context?, title: String, message: String, badge: String? = "CANONICAL") {
        showAlert(context, title, message, AlertArchetype.CONFLICT, badgeText = badge)
    }

    /**
     * Specialized: Destructive Actions with interactive UNDO button
     */
    @JvmStatic
    fun undoable(context: Context?, title: String, message: String, actionText: String = "UNDO", onUndo: () -> Unit) {
        showAlert(
            context = context,
            title = title,
            message = message,
            archetype = AlertArchetype.SEVER,
            badgeText = null,
            actionText = actionText,
            onAction = onUndo,
            durationMs = LENGTH_LONG
        )
    }

    /**
     * Specialized: Validation Warnings & Kinship Paradox Blocks
     */
    @JvmStatic
    fun warning(context: Context?, title: String, message: String, badge: String? = "RULE BLOCKED") {
        showAlert(context, title, message, AlertArchetype.WARNING, badgeText = badge)
    }

    /**
     * Specialized: Canvas Navigation, Founder Focus & Ahnentafel
     */
    @JvmStatic
    fun canvas(context: Context?, title: String, message: String, badge: String? = "CANVAS") {
        showAlert(context, title, message, AlertArchetype.CANVAS, badgeText = badge)
    }

    /**
     * Specialized: Pass Codes, Keys & Clipboard Copy
     */
    @JvmStatic
    fun clipboard(context: Context?, title: String, message: String, badge: String? = "COPIED") {
        showAlert(context, title, message, AlertArchetype.KEY, badgeText = badge)
    }

    /**
     * Specialized: Privacy Controls & Security Actions
     */
    @JvmStatic
    fun security(context: Context?, title: String, message: String, badge: String? = "SECURED") {
        showAlert(context, title, message, AlertArchetype.LOCK, badgeText = badge)
    }

    /**
     * Internal presenter that renders the custom view into the Activity decor view
     */
    private fun showAlert(
        context: Context?,
        title: String,
        message: String,
        archetype: AlertArchetype,
        badgeText: String? = null,
        actionText: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = LENGTH_SHORT
    ) {
        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            // Safe fallback if called without valid Activity window context
            mainHandler.post {
                try {
                    Toast.makeText(context ?: return@post, "$title: $message", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
            return
        }

        mainHandler.post {
            try {
                val decorView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post

                // Dismiss any currently displayed alert
                dismissCurrentAlert(immediate = true)

                val inflater = LayoutInflater.from(activity)
                val alertView = inflater.inflate(R.layout.view_kintrace_alert, decorView, false)

                val ivIcon = alertView.findViewById<ImageView>(R.id.ivAlertIcon)
                val tvTitle = alertView.findViewById<TextView>(R.id.tvAlertTitle)
                val tvMsg = alertView.findViewById<TextView>(R.id.tvAlertMessage)
                val btnAction = alertView.findViewById<TextView>(R.id.btnAlertAction)
                val tvBadge = alertView.findViewById<TextView>(R.id.tvAlertBadge)

                tvTitle.text = title
                tvMsg.text = message

                // Apply Icon based on Heraldic Archetype
                val iconRes = when (archetype) {
                    AlertArchetype.TELEMETRY -> R.drawable.ic_kintrace_alert_rocket
                    AlertArchetype.RESTORE -> R.drawable.ic_kintrace_alert_ankh
                    AlertArchetype.CONFLICT -> R.drawable.ic_kintrace_alert_justice
                    AlertArchetype.SEVER -> R.drawable.ic_kintrace_alert_sever
                    AlertArchetype.WARNING -> R.drawable.ic_kintrace_alert_shield_warn
                    AlertArchetype.CANVAS -> R.drawable.ic_kintrace_alert_compass
                    AlertArchetype.KEY -> R.drawable.ic_kintrace_alert_key
                    AlertArchetype.LOCK -> R.drawable.ic_kintrace_alert_lock
                    AlertArchetype.GENERIC_SUCCESS -> R.drawable.ic_kintrace_alert_ankh
                    AlertArchetype.GENERIC_INFO -> R.drawable.ic_kintrace_alert_compass
                }
                ivIcon.setImageResource(iconRes)

                // Configure Badge
                if (!badgeText.isNullOrBlank()) {
                    tvBadge.text = badgeText
                    tvBadge.visibility = View.VISIBLE
                } else {
                    tvBadge.visibility = View.GONE
                }

                // Configure Action Button (e.g. UNDO)
                if (!actionText.isNullOrBlank() && onAction != null) {
                    btnAction.text = actionText
                    btnAction.visibility = View.VISIBLE
                    btnAction.setOnClickListener {
                        dismissCurrentAlert(immediate = false)
                        onAction.invoke()
                    }
                } else {
                    btnAction.visibility = View.GONE
                }

                // Tap anywhere on card to dismiss
                alertView.setOnClickListener {
                    dismissCurrentAlert(immediate = false)
                }

                // Explicitly anchor layout params to bottom with 88dp clearance above nav island
                val lp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    val marginH = (16 * activity.resources.displayMetrics.density).toInt()
                    val marginB = (88 * activity.resources.displayMetrics.density).toInt()
                    setMargins(marginH, 0, marginH, marginB)
                }
                alertView.layoutParams = lp

                // Initial position for entrance slide (from below screen)
                alertView.translationY = 160f
                alertView.alpha = 0f
                decorView.addView(alertView)
                currentAlertView = WeakReference(alertView)

                // Animate Entrance (slides upward from bottom with spring physics)
                alertView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(320)
                    .setInterpolator(OvershootInterpolator(1.15f))
                    .start()

                // Auto dismiss schedule
                val runnable = Runnable {
                    dismissCurrentAlert(immediate = false)
                }
                dismissRunnable = runnable
                mainHandler.postDelayed(runnable, durationMs)

            } catch (_: Exception) {
                // Safeguard against decor view detached states
            }
        }
    }

    /**
     * Gracefully dismisses the currently active alert view
     */
    private fun dismissCurrentAlert(immediate: Boolean) {
        val view = currentAlertView?.get() ?: return
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        currentAlertView = null

        val parent = view.parent as? ViewGroup ?: return

        if (immediate) {
            parent.removeView(view)
        } else {
            view.animate()
                .translationY(160f)
                .alpha(0f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    try {
                        parent.removeView(view)
                    } catch (_: Exception) {}
                }
                .start()
        }
    }

    /**
     * Traverses ContextWrappers to locate parent Activity
     */
    private fun findActivity(context: Context?): Activity? {
        var curr = context
        while (curr != null) {
            if (curr is Activity) return curr
            if (curr is ContextWrapper) {
                curr = curr.baseContext
            } else {
                break
            }
        }
        return null
    }

    /**
     * Heuristic archetype classifier for drop-in toasts
     */
    private fun inferArchetype(msg: String): AlertArchetype {
        val lower = msg.lowercase()
        return when {
            lower.contains("deploy") || lower.contains("live across") || lower.contains("broadcast") ->
                AlertArchetype.TELEMETRY
            lower.contains("restor") ->
                AlertArchetype.RESTORE
            lower.contains("conflict") || lower.contains("escalat") || lower.contains("resolv") ->
                AlertArchetype.CONFLICT
            lower.contains("delet") || lower.contains("remov") || lower.contains("sever") || lower.contains("undo") ->
                AlertArchetype.SEVER
            lower.contains("block") || lower.contains("paradox") || lower.contains("prohibit") ||
                    lower.contains("cannot") || lower.contains("fail") || lower.contains("error") ||
                    lower.contains("invalid") || lower.contains("violat") ->
                AlertArchetype.WARNING
            lower.contains("focus") || lower.contains("founder") || lower.contains("root") ||
                    lower.contains("pedigree") || lower.contains("tree") || lower.contains("canvas") ->
                AlertArchetype.CANVAS
            lower.contains("copi") || lower.contains("clip") || lower.contains("pass") || lower.contains("key") ->
                AlertArchetype.KEY
            lower.contains("priva") || lower.contains("protect") || lower.contains("restrict") || lower.contains("lock") ->
                AlertArchetype.LOCK
            lower.contains("success") || lower.contains("saved") || lower.contains("approved") ->
                AlertArchetype.GENERIC_SUCCESS
            else ->
                AlertArchetype.GENERIC_INFO
        }
    }

    private fun getBadgeForArchetype(archetype: AlertArchetype, msg: String): String? {
        val lower = msg.lowercase()
        return when (archetype) {
            AlertArchetype.TELEMETRY -> if (lower.contains("live")) "LIVE" else "CITADEL"
            AlertArchetype.RESTORE -> "RESTORED"
            AlertArchetype.CONFLICT -> "CANONICAL"
            AlertArchetype.SEVER -> null // Sever actions use the interactive UNDO button
            AlertArchetype.WARNING -> if (lower.contains("paradox")) "PARADOX" else "RULE BLOCKED"
            AlertArchetype.CANVAS -> "CANVAS"
            AlertArchetype.KEY -> "COPIED"
            AlertArchetype.LOCK -> "SECURITY"
            AlertArchetype.GENERIC_SUCCESS -> "SUCCESS"
            AlertArchetype.GENERIC_INFO -> null
        }
    }
}
