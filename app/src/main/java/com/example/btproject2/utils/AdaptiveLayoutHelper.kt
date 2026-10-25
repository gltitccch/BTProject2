package com.example.btproject2.utils

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Universal adaptive layout engine providing responsive bounds, max-width clamping
 * for tablets and foldables, and dynamic typography adaptation for compact devices.
 */
object AdaptiveLayoutHelper {

    const val DEFAULT_MAX_CONTENT_WIDTH_DP = 580
    const val DEFAULT_MAX_NAV_WIDTH_DP = 520
    const val DEFAULT_MAX_DIALOG_WIDTH_DP = 440
    const val COMPACT_SCREEN_THRESHOLD_DP = 360f
    const val TABLET_SCREEN_THRESHOLD_DP = 600f

    fun getScreenWidthDp(context: Context): Float {
        val dm = context.resources.displayMetrics
        return dm.widthPixels / dm.density
    }

    fun isCompactScreen(context: Context): Boolean {
        return getScreenWidthDp(context) < COMPACT_SCREEN_THRESHOLD_DP
    }

    fun isTabletScreen(context: Context): Boolean {
        return getScreenWidthDp(context) >= TABLET_SCREEN_THRESHOLD_DP
    }

    /**
     * Clamps a content container to a maximum width on large displays (tablets, foldables unfolded)
     * and centers it horizontally, preventing wide 1000dp+ unconstrained stretching.
     */
    fun applyMaxContentWidth(view: View?, maxDp: Int = DEFAULT_MAX_CONTENT_WIDTH_DP) {
        if (view == null) return
        val dm = view.resources.displayMetrics
        val screenWidthDp = dm.widthPixels / dm.density
        if (screenWidthDp > maxDp) {
            val maxPx = (maxDp * dm.density).toInt()
            val lp = view.layoutParams ?: return
            lp.width = maxPx
            when (lp) {
                is LinearLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
                is FrameLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
            }
            view.layoutParams = lp
        }
    }

    /**
     * Constrains the Floating Navigation Island to a comfortable centered touch zone
     * on tablets rather than stretching across 10-12 inches of glass.
     */
    fun applyAdaptiveIslandWidth(navIsland: View?, maxDp: Int = DEFAULT_MAX_NAV_WIDTH_DP) {
        if (navIsland == null) return
        val dm = navIsland.resources.displayMetrics
        val screenWidthDp = dm.widthPixels / dm.density
        if (screenWidthDp > maxDp) {
            val maxPx = (maxDp * dm.density).toInt()
            val lp = navIsland.layoutParams ?: return
            lp.width = maxPx
            if (lp is LinearLayout.LayoutParams) {
                lp.gravity = Gravity.CENTER_HORIZONTAL
            } else if (lp is FrameLayout.LayoutParams) {
                lp.gravity = Gravity.CENTER_HORIZONTAL
            }
            navIsland.layoutParams = lp
        }
    }

    /**
     * Adapts modal dialog window dimensions so alert boxes and sheets remain centered
     * and proportional on wide tablets, while maximizing usable space on compact phones.
     */
    fun clampDialogWidth(dialog: Dialog?, maxDp: Int = DEFAULT_MAX_DIALOG_WIDTH_DP) {
        if (dialog == null) return
        val window = dialog.window ?: return
        val context = dialog.context
        val dm = context.resources.displayMetrics
        val screenWidthPx = dm.widthPixels
        val maxPx = (maxDp * dm.density).toInt()
        val targetWidth = minOf((screenWidthPx * 0.92f).toInt(), maxPx)
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * Adapts Hero action buttons on compact screens so labels like "Family Insights"
     * fit without vertical clipping or awkward multiline breakage.
     */
    fun adaptHeroButtons(btnAdd: View?, btnView: View?, btnInsights: View?) {
        if (btnAdd == null && btnView == null && btnInsights == null) return
        val context = btnAdd?.context ?: btnView?.context ?: btnInsights?.context ?: return
        if (isCompactScreen(context)) {
            // Find text views inside buttons and adapt text for compact footprint
            findTextView(btnAdd)?.let { tv ->
                tv.text = "＋ Add"
                tv.textSize = 10f
            }
            findTextView(btnView)?.let { tv ->
                tv.text = "👁 View Tree"
                tv.textSize = 10f
            }
            findTextView(btnInsights)?.let { tv ->
                tv.text = "📊 Insights"
                tv.textSize = 10f
            }
        }
    }

    /**
     * Adapts 3-tile exploration tools on compact screens so labels don't get truncated
     * with ellipses (e.g. "Trace Bloodline" -> "Trace", "Family Records" -> "Records").
     */
    fun adaptToolsTiles(cardTrace: View?, cardViewTree: View?, cardRecords: View?) {
        if (cardTrace == null && cardViewTree == null && cardRecords == null) return
        val context = cardTrace?.context ?: cardViewTree?.context ?: cardRecords?.context ?: return
        if (isCompactScreen(context)) {
            findFirstTextView(cardTrace)?.let { it.text = "Trace" }
            findFirstTextView(cardViewTree)?.let { it.text = "Tree View" }
            findFirstTextView(cardRecords)?.let { it.text = "Records" }
        }
    }

    private fun findTextView(parent: View?): TextView? {
        if (parent is TextView) return parent
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is TextView) return child
            }
        }
        return null
    }

    private fun findFirstTextView(parent: View?): TextView? {
        if (parent is TextView) return parent
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val found = findTextView(child)
                if (found != null) return found
            }
        }
        return null
    }
}
