package com.example.btproject2.utils

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Universal adaptive layout engine providing responsive bounds, multi-column dashboard reflow
 * for tablets/wide displays, safe system-bar inset handling, and compact-screen scaling.
 */
object AdaptiveLayoutHelper {

    const val DEFAULT_MAX_CONTENT_WIDTH_DP = 1200
    const val DEFAULT_MAX_NAV_WIDTH_DP = 680
    const val DEFAULT_MAX_DIALOG_WIDTH_DP = 460
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
     * Dynamically adapts container horizontal padding and optional ultra-wide clamp
     * instead of forcing wide displays into a fixed narrow phone strip.
     */
    fun applyMaxContentWidth(view: View?, maxDp: Int = DEFAULT_MAX_CONTENT_WIDTH_DP) {
        if (view == null) return
        val dm = view.resources.displayMetrics
        val density = dm.density
        val screenWidthDp = dm.widthPixels / density

        // Adjust horizontal breathing room dynamically to match screen class
        val horizPadDp = when {
            screenWidthDp < COMPACT_SCREEN_THRESHOLD_DP -> 12
            screenWidthDp >= TABLET_SCREEN_THRESHOLD_DP -> 24
            else -> 16
        }
        val horizPadPx = (horizPadDp * density).toInt()
        view.setPadding(horizPadPx, view.paddingTop, horizPadPx, view.paddingBottom)

        val lp = view.layoutParams ?: return
        if (screenWidthDp > maxDp) {
            lp.width = (maxDp * density).toInt()
            when (lp) {
                is LinearLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
                is FrameLayout.LayoutParams -> lp.gravity = Gravity.CENTER_HORIZONTAL
            }
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        view.layoutParams = lp
    }

    /**
     * Preserves the normal unified single-column vertical flow across all screen sizes
     * (phones, foldables, and tablets) so the Heritage card and dashboard tools stack
     * seamlessly with full width and zero empty voids.
     */
    fun adaptDashboardGrid(
        gridContainer: LinearLayout?,
        leftColumnView: View?,
        rightColumnView: View?
    ) {
        if (gridContainer == null || leftColumnView == null || rightColumnView == null) return
        val dm = gridContainer.resources.displayMetrics
        val density = dm.density

        // Always maintain clean, unified single-column vertical flow across all screen sizes
        gridContainer.orientation = LinearLayout.VERTICAL
        val bottomGapPx = (18 * density).toInt()
        (leftColumnView.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
            lp.marginEnd = 0
            lp.bottomMargin = bottomGapPx
            leftColumnView.layoutParams = lp
        }
        (rightColumnView.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
            lp.marginStart = 0
            rightColumnView.layoutParams = lp
        }
    }

    /**
     * Applies safe top status-bar / camera-cutout and bottom gesture-bar insets dynamically
     * so content never overlaps system chrome or leaves hard-coded dead gaps.
     */
    fun applySafeSystemBarInsets(
        topContentView: View?,
        bottomNavContainer: View?,
        extraTopDp: Int = 10,
        extraBottomDp: Int = 10
    ) {
        topContentView?.let { topView ->
            val density = topView.resources.displayMetrics.density
            val baseLeft = topView.paddingLeft
            val baseRight = topView.paddingRight
            val baseBottom = topView.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(topView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.setPadding(baseLeft, bars.top + (extraTopDp * density).toInt(), baseRight, baseBottom)
                insets
            }
            ViewCompat.requestApplyInsets(topView)
        }

        bottomNavContainer?.let { navView ->
            val density = navView.resources.displayMetrics.density
            val baseLeft = navView.paddingLeft
            val baseTop = navView.paddingTop
            val baseRight = navView.paddingRight
            ViewCompat.setOnApplyWindowInsetsListener(navView) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(baseLeft, baseTop, baseRight, bars.bottom + (extraBottomDp * density).toInt())
                insets
            }
            ViewCompat.requestApplyInsets(navView)
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
