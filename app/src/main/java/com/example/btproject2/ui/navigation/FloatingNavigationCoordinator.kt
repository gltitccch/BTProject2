package com.example.btproject2.ui.navigation

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.ui.activities.CreateTreeActivity
import com.example.btproject2.ui.activities.FamilyTreeActivity
import com.example.btproject2.ui.activities.HomeActivity
import com.example.btproject2.ui.activities.TraceActivity
import com.example.btproject2.utils.OnboardingPreferences
import com.example.btproject2.utils.TreePreferences

/**
 * Single source of truth coordinator for the Floating Heritage Island bottom navigation.
 * Binds active tab styling, circular highlight, and cross-activity transitions.
 */
object FloatingNavigationCoordinator {

    enum class NavTab {
        HOME, TREE, TRACE, PROFILE
    }

    fun setup(
        activity: Activity,
        activeTab: NavTab,
        treeIdProvider: () -> String = { TreePreferences.getActiveTreeId(activity) },
        onProfileClicked: (() -> Unit)? = null
    ) {
        val navHome = activity.findViewById<LinearLayout?>(R.id.navHome) ?: return
        val navTree = activity.findViewById<LinearLayout?>(R.id.navTree) ?: return
        val navTrace = activity.findViewById<LinearLayout?>(R.id.navTrace) ?: return
        val navProfile = activity.findViewById<LinearLayout?>(R.id.navProfile) ?: return

        val islandNavBar = activity.findViewById<View?>(R.id.islandNavBar)
        com.example.btproject2.utils.AdaptiveLayoutHelper.applyAdaptiveIslandWidth(islandNavBar)

        val ivNavHome = activity.findViewById<ImageView?>(R.id.ivNavHome)
        val tvNavHome = activity.findViewById<TextView?>(R.id.tvNavHome)

        val ivNavTree = activity.findViewById<ImageView?>(R.id.ivNavTree)
        val tvNavTree = activity.findViewById<TextView?>(R.id.tvNavTree)

        val ivNavTrace = activity.findViewById<ImageView?>(R.id.ivNavTrace)
        val tvNavTrace = activity.findViewById<TextView?>(R.id.tvNavTrace)

        val ivNavProfile = activity.findViewById<ImageView?>(R.id.ivNavProfile)
        val tvNavProfile = activity.findViewById<TextView?>(R.id.tvNavProfile)

        val activeColor = ContextCompat.getColor(activity, R.color.dark_nav_active)
        val inactiveColor = ContextCompat.getColor(activity, R.color.dark_nav_inactive)

        // Helper to reset and apply tab styling
        fun applyTabState(
            tabLayout: LinearLayout,
            iconView: ImageView?,
            textView: TextView?,
            isActive: Boolean
        ) {
            if (isActive) {
                tabLayout.setBackgroundResource(R.drawable.bg_nav_tab_active_circle)
                iconView?.imageTintList = ColorStateList.valueOf(activeColor)
                textView?.setTextColor(activeColor)
                textView?.paint?.isFakeBoldText = true
            } else {
                tabLayout.background = null
                iconView?.imageTintList = ColorStateList.valueOf(inactiveColor)
                textView?.setTextColor(inactiveColor)
                textView?.paint?.isFakeBoldText = false
            }
        }

        applyTabState(navHome, ivNavHome, tvNavHome, activeTab == NavTab.HOME)
        applyTabState(navTree, ivNavTree, tvNavTree, activeTab == NavTab.TREE)
        applyTabState(navTrace, ivNavTrace, tvNavTrace, activeTab == NavTab.TRACE)
        applyTabState(navProfile, ivNavProfile, tvNavProfile, activeTab == NavTab.PROFILE)

        // ── Home Tab Action ──────────────────────────────────────────
        navHome.setOnClickListener {
            if (activeTab != NavTab.HOME) {
                val intent = Intent(activity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                activity.startActivity(intent)
            }
        }

        // ── Tree Tab Action ──────────────────────────────────────────
        navTree.setOnClickListener {
            if (activeTab != NavTab.TREE) {
                val treeId = treeIdProvider()
                if (treeId.isNotEmpty()) {
                    OnboardingPreferences.setMilestoneCanvasInspected(activity, true)
                    val authHelper = AuthHelper()
                    val firestoreHelper = FirestoreHelper()
                    val currentUserId = authHelper.getCurrentUserId().orEmpty()
                    if (currentUserId.isNotEmpty()) {
                        firestoreHelper.saveOnboardingState(
                            currentUserId,
                            OnboardingPreferences.getOnboardingState(activity)
                        )
                    }
                    val intent = Intent(activity, FamilyTreeActivity::class.java).apply {
                        putExtra("TREE_ID", treeId)
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    activity.startActivity(intent)
                } else {
                    activity.startActivity(Intent(activity, CreateTreeActivity::class.java))
                }
            }
        }

        // ── Trace Tab Action ─────────────────────────────────────────
        navTrace.setOnClickListener {
            if (activeTab != NavTab.TRACE) {
                val treeId = treeIdProvider()
                val intent = Intent(activity, TraceActivity::class.java).apply {
                    if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                activity.startActivity(intent)
            }
        }

        // ── Profile Tab Action ───────────────────────────────────────
        navProfile.setOnClickListener {
            onProfileClicked?.invoke()
        }

        // ── Center Heritage Guide Crest Action (Variation A: Zenith Sparkle) ─
        val btnNavGuideCrest = activity.findViewById<View?>(R.id.btnNavGuideCrest)
        updateGuideCrestVisibility(activity)

        btnNavGuideCrest?.setOnClickListener {
            val mappedTutorialKey = when (activeTab) {
                NavTab.HOME -> {
                    val treeId = treeIdProvider()
                    if (treeId.isEmpty()) {
                        com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE1_EMPTY
                    } else {
                        com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE2_DASHBOARD
                    }
                }
                NavTab.TREE -> {
                    if (activity is com.example.btproject2.ui.activities.FamilyTreeActivity) {
                        com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE
                    } else {
                        com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.INTERACTIVE_TREE
                    }
                }
                NavTab.TRACE -> com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.TRACE_BLOODLINE
                NavTab.PROFILE -> com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE2_DASHBOARD
            }
            com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialForced(activity, mappedTutorialKey)
        }
    }

    /**
     * Updates visibility of the Center Guide Crest in real time based on user preference.
     * When dismissed, the navigation island automatically re-balances to the standard 4-tab bar.
     */
    fun updateGuideCrestVisibility(activity: Activity) {
        val layoutNavGuideCrest = activity.findViewById<View?>(R.id.layoutNavGuideCrest) ?: return
        val tvGuideCrestBeacon = activity.findViewById<TextView?>(R.id.tvGuideCrestBeacon)
        val isDismissed = OnboardingPreferences.isGuideCrestDismissed(activity)
        activity.runOnUiThread {
            layoutNavGuideCrest.visibility = if (isDismissed) View.GONE else View.VISIBLE
            if (!isDismissed && tvGuideCrestBeacon != null) {
                val activeTree = TreePreferences.getActiveTreeId(activity)
                val isSeen = when (activity) {
                    is HomeActivity -> {
                        if (activeTree.isEmpty()) {
                            OnboardingPreferences.hasSeenTutorial(activity, com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE1_EMPTY.key, treeId = activeTree)
                        } else {
                            OnboardingPreferences.hasSeenTutorial(activity, com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE2_DASHBOARD.key, treeId = activeTree)
                        }
                    }
                    is FamilyTreeActivity -> OnboardingPreferences.hasSeenTutorial(activity, com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE.key, treeId = activeTree)
                    is TraceActivity -> OnboardingPreferences.hasSeenTutorial(activity, com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.TRACE_BLOODLINE.key, treeId = activeTree)
                    else -> true
                }
                tvGuideCrestBeacon.visibility = if (!isSeen) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Binds and renders network connectivity status in the floating dock pill (Placement 2)
     * positioned directly above the floating navigation island.
     */
    fun updateNetworkState(
        activity: Activity,
        state: com.example.btproject2.utils.NetworkStateMonitor.NetworkState,
        onRetryClicked: (() -> Unit)? = null
    ) {
        val dock = activity.findViewById<View?>(R.id.dockNetworkStatus) ?: return
        val tvIcon = activity.findViewById<TextView?>(R.id.tvNetworkDockIcon)
        val tvMessage = activity.findViewById<TextView?>(R.id.tvNetworkDockMessage)
        val btnRetry = activity.findViewById<TextView?>(R.id.btnNetworkDockRetry)

        activity.runOnUiThread {
            when (state) {
                com.example.btproject2.utils.NetworkStateMonitor.NetworkState.OFFLINE -> {
                    dock.visibility = View.VISIBLE
                    dock.setBackgroundResource(R.drawable.bg_network_dock_offline)
                    tvIcon?.text = "📡"
                    tvMessage?.text = "Offline Mode · Local Archive"
                    tvMessage?.setTextColor(android.graphics.Color.parseColor("#FED7AA"))
                    btnRetry?.visibility = View.VISIBLE
                    btnRetry?.setOnClickListener { onRetryClicked?.invoke() }
                }
                com.example.btproject2.utils.NetworkStateMonitor.NetworkState.WEAK -> {
                    dock.visibility = View.VISIBLE
                    dock.setBackgroundResource(R.drawable.bg_network_dock_weak)
                    tvIcon?.text = "🟡"
                    tvMessage?.text = "Weak Connection · Syncing..."
                    tvMessage?.setTextColor(android.graphics.Color.parseColor("#FEF08A"))
                    btnRetry?.visibility = View.GONE
                }
                com.example.btproject2.utils.NetworkStateMonitor.NetworkState.ONLINE -> {
                    if (dock.visibility == View.VISIBLE) {
                        dock.setBackgroundResource(R.drawable.bg_network_dock_restored)
                        tvIcon?.text = "🟢"
                        tvMessage?.text = "Back Online · Tree Synced"
                        tvMessage?.setTextColor(android.graphics.Color.parseColor("#86EFAC"))
                        btnRetry?.visibility = View.GONE
                        dock.postDelayed({
                            dock.visibility = View.GONE
                        }, 2500L)
                    } else {
                        dock.visibility = View.GONE
                    }
                }
            }
        }
    }
}

