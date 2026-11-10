package com.example.btproject2.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.example.btproject2.R

/**
 * KinClashArcadeToast:
 * In-game 8-bit retro arcade HUD notification banner.
 * Replaces generic Android OS Toasts with a sleek 3D cosmic purple and gold banner
 * that slides down from the top with custom arcade icons, heavy bold typography,
 * and automatic clean dismissal.
 */
object KinClashArcadeToast {

    private var currentToastView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun show(
        activity: Activity,
        title: String,
        subtitle: String = "",
        icon: String = "🕹️",
        durationMs: Long = 2500L
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        mainHandler.post {
            // Dismiss any existing toast first
            dismissCurrent(animate = false)

            val decorView = activity.window?.decorView as? ViewGroup ?: return@post
            val inflater = LayoutInflater.from(activity)
            val toastView = inflater.inflate(R.layout.layout_arcade_hud_toast, decorView, false)

            val tvIcon = toastView.findViewById<TextView>(R.id.tvToastIcon)
            val tvTitle = toastView.findViewById<TextView>(R.id.tvToastTitle)
            val tvSubtitle = toastView.findViewById<TextView>(R.id.tvToastSubtitle)

            tvIcon.text = icon
            tvTitle.text = title

            if (subtitle.isNotBlank()) {
                tvSubtitle.text = subtitle
                tvSubtitle.visibility = View.VISIBLE
            } else {
                tvSubtitle.visibility = View.GONE
            }

            // Layout Params: Top-pinned HUD banner with safe top margin
            val statusBarHeight = getStatusBarHeight(activity)
            val topMargin = statusBarHeight + (12 * activity.resources.displayMetrics.density).toInt()
            val sideMargin = (16 * activity.resources.displayMetrics.density).toInt()

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(sideMargin, topMargin, sideMargin, 0)
            }

            toastView.layoutParams = params
            toastView.translationY = -300f
            toastView.alpha = 0f

            decorView.addView(toastView)
            currentToastView = toastView

            // Animate slide-down with bouncy overshoot
            toastView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(350L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()

            // Optional 8-bit audio chirp
            try {
                ClanTriviaSoundEngine.playCoinSound()
            } catch (_: Exception) {}

            // Dismiss when tapped
            toastView.setOnClickListener {
                dismissCurrent(animate = true)
            }

            // Auto-dismiss after duration
            dismissRunnable = Runnable {
                dismissCurrent(animate = true)
            }
            mainHandler.postDelayed(dismissRunnable!!, durationMs)
        }
    }

    private fun dismissCurrent(animate: Boolean) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        val view = currentToastView ?: return
        currentToastView = null

        val parent = view.parent as? ViewGroup ?: return

        if (animate) {
            view.animate()
                .translationY(-300f)
                .alpha(0f)
                .setDuration(250L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    try {
                        parent.removeView(view)
                    } catch (_: Exception) {}
                }
                .start()
        } else {
            try {
                parent.removeView(view)
            } catch (_: Exception) {}
        }
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else 0
    }
}
