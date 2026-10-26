package com.example.btproject2.ui.activities

import com.example.btproject2.utils.KinTraceAlert

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.Person
import com.example.btproject2.models.UserProfile
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.utils.TreePreferences
import com.example.btproject2.sync.CentralTreeSynchronizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.btproject2.utils.ThemePreferences
import com.example.btproject2.ui.dialogs.WelcomeStoryDialogFragment
import com.example.btproject2.ui.dialogs.ClanProfileBottomSheet
import com.example.btproject2.utils.OnboardingPreferences
import com.example.btproject2.models.UserOnboardingState
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged

class HomeActivity : AppCompatActivity() {

    companion object {
        private var isRecreatingFromEmptyStateThemeSwitch: Boolean = false
        private var pendingCornerDockMorph: Boolean = false
        private var pendingCornerButtonPop: Boolean = false
        private var pendingThemeWaveMode: String = ThemePreferences.THEME_DARK
        private var justCompletedThemeMorphInSession: Boolean = false
        private var isPlayingThemeMorphAnimation: Boolean = false
    }

    private val activeThemeOnboardingAnimators = mutableListOf<android.animation.ValueAnimator>()

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private var treeId: String = ""
    private var userOwnedTrees: List<FamilyTree> = emptyList()
    private val recentlyDeletedIds = mutableSetOf<String>()

    private lateinit var tvUserName: TextView
    private lateinit var tvProfileInitial: TextView
    private lateinit var layoutStatsHeader: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutPopulatedState: LinearLayout

    private val memberDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deletedId = result.data?.getStringExtra("deletedPersonId")
            if (!deletedId.isNullOrEmpty()) {
                recentlyDeletedIds.add(deletedId)
                resolveAndLoadTree()
            }
        }
    }
    private lateinit var btnEmptyCreateTree: Button
    private lateinit var btnEmptyJoinTree: Button
    private lateinit var btnEmptyAddMember: Button
    private lateinit var cardInsightsBanner: LinearLayout
    private lateinit var tvInsightsBannerSub: TextView
    private lateinit var containerRecentItems: LinearLayout
    private lateinit var tvNoRecentActivity: TextView
    private lateinit var viewNotifBadge: View

    private val reloadDebounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val reloadTreeRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            resolveAndLoadTree()
        }
    }

    private fun debounceReloadTree(delayMs: Long = 300L) {
        reloadDebounceHandler.removeCallbacks(reloadTreeRunnable)
        reloadDebounceHandler.postDelayed(reloadTreeRunnable, delayMs)
    }

    private val homeSyncListener = object : com.example.btproject2.sync.SyncEventListener {
        override val subscriberKey: String = "HomeActivity@${System.identityHashCode(this)}"
        override val screenType: com.example.btproject2.sync.AffectedScreen = com.example.btproject2.sync.AffectedScreen.HOME_OVERVIEW
        override val interestedTreeId: String? = null

        override fun onSyncEvent(event: com.example.btproject2.sync.TreeSyncEvent) {
            if (event.changeType == com.example.btproject2.sync.SyncChangeType.TREE_DELETED) {
                if (event.treeId == treeId) {
                    runOnUiThread {
                        treeId = ""
                        resolveAndLoadTree()
                    }
                    return
                }
            }
            // Only reload if the event belongs to this activity's active tree or is global
            if (event.treeId.isNotEmpty() && treeId.isNotEmpty() && event.treeId != treeId) {
                return
            }
            runOnUiThread {
                debounceReloadTree()
                refreshQuestHudUi()
            }
        }

        override fun onConflictDetected(
            conflict: com.example.btproject2.sync.SyncConflictEvent,
            resolver: (com.example.btproject2.sync.ConflictChoice) -> Unit
        ) {
            resolver(com.example.btproject2.sync.ConflictChoice.REFRESH)
        }
    }

    private var announcementListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentAnnouncement: com.example.btproject2.models.SystemAnnouncement? = null
    private var isBannerDismissedSession = false

    private val networkListener: (com.example.btproject2.utils.NetworkStateMonitor.NetworkState) -> Unit = { state ->
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.updateNetworkState(
            activity = this,
            state = state,
            onRetryClicked = { resolveAndLoadTree() }
        )
    }

    override fun onStart() {
        super.onStart()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().registerListener(homeSyncListener)
        announcementListener = firestoreHelper.listenToActiveSystemAnnouncement { announcement ->
            currentAnnouncement = announcement
            runOnUiThread {
                updateMaintenanceBannerUi(announcement)
                refreshNotificationBadge()
            }
        }
        com.example.btproject2.utils.NetworkStateMonitor.startMonitoring(this)
        com.example.btproject2.utils.NetworkStateMonitor.addListener(networkListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        reloadDebounceHandler.removeCallbacks(reloadTreeRunnable)
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().unregisterListener(homeSyncListener)
        announcementListener?.remove()
        announcementListener = null
        com.example.btproject2.utils.NetworkStateMonitor.removeListener(networkListener)
        stopThemeOnboardingLoopAnimators()
        findViewById<View?>(R.id.layoutHomeShimmer)?.let { com.example.btproject2.utils.KinTraceShimmerHelper.stopShimmer(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeUser = authHelper.getCurrentUser()
        val isAdmin = com.example.btproject2.models.DemoAccountRepository.isAdminEmail(activeUser?.email)
        if (isAdmin) {
            startActivity(Intent(this, AdminPortalActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_home)

        com.example.btproject2.utils.InAppUpdateManager.syncCurrentReleaseToFirestore {
            com.example.btproject2.utils.InAppUpdateManager.checkForUpdates(this, onNoUpdate = {
                com.example.btproject2.utils.InAppUpdateManager.checkAndShowPostUpdateWhatsNew(this)
            })
        }

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        if (com.example.btproject2.models.DemoAccountRepository.isNewUserPreviewEmail(authHelper.getCurrentUser()?.email) &&
            !isRecreatingFromEmptyStateThemeSwitch && !pendingCornerDockMorph && !pendingCornerButtonPop && !justCompletedThemeMorphInSession && !isPlayingThemeMorphAnimation
        ) {
            OnboardingPreferences.setHasChosenInitialTheme(this, false)
        }
        tvUserName = findViewById(R.id.tvUserName)
        tvProfileInitial = findViewById(R.id.tvProfileInitial)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val tvBranchCount = findViewById<TextView>(R.id.tvBranchCount)
        val tvRecentCount = findViewById<TextView>(R.id.tvRecentCount)

        layoutStatsHeader = findViewById(R.id.layoutStatsHeader)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        layoutPopulatedState = findViewById(R.id.layoutPopulatedState)
        btnEmptyCreateTree = findViewById(R.id.btnEmptyCreateTree)
        btnEmptyJoinTree = findViewById(R.id.btnEmptyJoinTree)
        btnEmptyAddMember = findViewById(R.id.btnEmptyAddMember)
        cardInsightsBanner = findViewById(R.id.cardInsightsBanner)
        tvInsightsBannerSub = findViewById(R.id.tvInsightsBannerSub)
        containerRecentItems = findViewById(R.id.containerRecentItems)
        tvNoRecentActivity = findViewById(R.id.tvNoRecentActivity)
        viewNotifBadge = findViewById(R.id.viewNotifBadge)

        val tvHeaderDate = findViewById<TextView?>(R.id.tvHeaderDate)
        tvHeaderDate?.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()).uppercase()

        // Responsive Adaptive Screen Layout
        com.example.btproject2.utils.AdaptiveLayoutHelper.applyMaxContentWidth(findViewById(R.id.layoutHomeScrollContent))
        com.example.btproject2.utils.AdaptiveLayoutHelper.adaptHeroButtons(
            findViewById(R.id.btnAddMemberHero),
            findViewById(R.id.btnViewTreeHero),
            findViewById(R.id.btnFamilyInsightsHero)
        )
        com.example.btproject2.utils.AdaptiveLayoutHelper.adaptToolsTiles(
            findViewById(R.id.cardTrace),
            findViewById(R.id.cardViewTree),
            findViewById(R.id.cardRecords)
        )

        // ── Family Insights Navigation ────────────────────────────
        val openInsights = View.OnClickListener {
            val intent = Intent(this, InsightsActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnFamilyInsightsHero)?.setOnClickListener(openInsights)
        cardInsightsBanner.setOnClickListener(openInsights)
        findViewById<View>(R.id.btnSeeAllInsights)?.setOnClickListener(openInsights)

        // ── Hero Action Buttons ────────────────────────────────────
        findViewById<View>(R.id.btnAddMemberHero)?.setOnClickListener {
            val intent = Intent(this, AddMemberActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnViewTreeHero)?.setOnClickListener {
            OnboardingPreferences.setMilestoneCanvasInspected(this, true)
            refreshQuestHudUi()
            val intent = Intent(this, FamilyTreeActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.cardTraceSpotlight)?.setOnClickListener {
            val intent = Intent(this, TraceActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnNotificationBell)?.setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnKinAcademy)?.setOnClickListener {
            val intent = Intent(this, KinAcademyActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        // ── Tree Switcher on Tree Title ──────────────────────────
        val tvTreeTitle = findViewById<TextView?>(R.id.tvTreeTitle)
        tvTreeTitle?.setOnClickListener {
            showTreeSelectionDialog()
        }

        // ── Theme Toggle (Light / Dark Mode) beside Family Tree Banner & Empty State Top-Left Corner ──
        val btnThemeToggle = findViewById<View?>(R.id.btnThemeToggle)
        val btnEmptyThemeToggle = findViewById<View?>(R.id.btnEmptyThemeToggle)
        val cardChooseLightMode = findViewById<View?>(R.id.cardChooseLightMode)
        val cardChooseDarkMode = findViewById<View?>(R.id.cardChooseDarkMode)

        updateAllThemeToggles()

        btnThemeToggle?.setOnClickListener {
            requestThemeToggleWithSpinAndWave()
        }

        btnEmptyThemeToggle?.setOnClickListener {
            requestThemeToggleWithSpinAndWave()
        }

        btnEmptyThemeToggle?.setOnLongClickListener {
            showThemeOnboardingOverlay(forceShow = true)
            true
        }

        cardChooseLightMode?.setOnClickListener {
            handleNewUserThemeChoice(ThemePreferences.THEME_LIGHT)
        }

        cardChooseDarkMode?.setOnClickListener {
            handleNewUserThemeChoice(ThemePreferences.THEME_DARK)
        }

        // ── Empty State Action Buttons (Figure 4) ──────────────────
        btnEmptyCreateTree.setOnClickListener {
            startActivity(Intent(this, CreateTreeActivity::class.java))
        }
        btnEmptyJoinTree.setOnClickListener {
            startActivity(Intent(this, JoinTreeActivity::class.java))
        }

        val btnEmptyReplayTour = findViewById<View?>(R.id.btnEmptyReplayTour)
        btnEmptyReplayTour?.setOnClickListener {
            com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialForced(
                this,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.HOME_STAGE1_EMPTY
            )
        }
        btnEmptyAddMember.setOnClickListener {
            val intent = Intent(this, AddMemberActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        // ── Populated State Action Buttons (Figure 6) ──────────────
        val cardExportTree = findViewById<LinearLayout?>(R.id.cardExportTree) ?: findViewById<LinearLayout?>(R.id.cardAddMember)
        val cardViewTree = findViewById<LinearLayout>(R.id.cardViewTree)
        val cardTrace = findViewById<LinearLayout>(R.id.cardTrace)
        val cardRecords = findViewById<LinearLayout>(R.id.cardRecords)
        val cardMerge = findViewById<LinearLayout>(R.id.cardMerge)
        val btnCreateTree = findViewById<LinearLayout?>(R.id.btnCreateTree)
        val btnJoinTree = findViewById<LinearLayout?>(R.id.btnJoinTree)
        val btnSeeAllRecentActivities = findViewById<View?>(R.id.btnSeeAllRecentActivities) ?: findViewById<View?>(R.id.btnExportTree)
        val btnPrivacy = findViewById<LinearLayout>(R.id.btnPrivacy)

        btnCreateTree?.setOnClickListener {
            startActivity(Intent(this, CreateTreeActivity::class.java))
        }

        btnJoinTree?.setOnClickListener {
            startActivity(Intent(this, JoinTreeActivity::class.java))
        }

        tvGreeting.text = "Hi, "

        val currentUser = authHelper.getCurrentUser()
        val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "User"
        tvUserName.text = displayName
        tvProfileInitial.text = displayName.first().uppercase()

        tvProfileInitial.setOnClickListener { showAccountProfileDialog() }
        tvUserName.setOnClickListener { showAccountProfileDialog() }

        cardExportTree?.setOnClickListener {
            val intent = Intent(this, ExportTreeActivity::class.java).apply {
                val activeId = TreePreferences.getActiveTreeId(this@HomeActivity).ifEmpty { treeId }
                if (activeId.isNotEmpty()) putExtra("TREE_ID", activeId)
            }
            startActivity(intent)
        }

        cardViewTree.setOnClickListener {
            OnboardingPreferences.setMilestoneCanvasInspected(this, true)
            refreshQuestHudUi()
            val intent = Intent(this, FamilyTreeActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        cardTrace.setOnClickListener {
            val intent = Intent(this, TraceActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        cardRecords.setOnClickListener {
            val intent = Intent(this, FamilyRecordsActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        cardMerge.setOnClickListener {
            val intent = Intent(this, MergeBranchesActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        btnSeeAllRecentActivities?.setOnClickListener {
            val intent = Intent(this, RecentActivitiesActivity::class.java).apply {
                val activeId = TreePreferences.getActiveTreeId(this@HomeActivity).ifEmpty { treeId }
                if (activeId.isNotEmpty()) putExtra("TREE_ID", activeId)
            }
            startActivity(intent)
        }

        btnPrivacy.setOnClickListener {
            val intent = Intent(this, PrivacyControlsActivity::class.java).apply {
                val activeId = TreePreferences.getActiveTreeId(this@HomeActivity).ifEmpty { treeId }
                if (activeId.isNotEmpty()) putExtra("TREE_ID", activeId)
            }
            startActivity(intent)
        }

        // ── Floating Heritage Island Bottom Navigation Coordinator ──
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.setup(
            activity = this,
            activeTab = com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.NavTab.HOME,
            treeIdProvider = { treeId },
            onProfileClicked = { showAccountProfileDialog() }
        )



        setupQuestHud()
        resolveAndLoadTree()
    }

    override fun onResume() {
        super.onResume()

        updateAllThemeToggles()

        refreshQuestHudUi()
        refreshNotificationBadge()
        resolveAndLoadTree()
    }

    private fun updateAllThemeToggles() {
        val isDark = ThemePreferences.isDarkTheme(this)
        val btnThemeToggle = findViewById<View?>(R.id.btnThemeToggle)
        val tvThemeToggleIcon = findViewById<TextView?>(R.id.tvThemeToggleIcon)
        tvThemeToggleIcon?.text = if (isDark) "☀️" else "🌙"
        btnThemeToggle?.contentDescription = if (isDark) "Switch to Light Theme" else "Switch to Dark Theme"

        val btnEmptyThemeToggle = findViewById<View?>(R.id.btnEmptyThemeToggle)
        val tvEmptyIcon = findViewById<TextView?>(R.id.tvEmptyThemeToggleIcon)
        val tvEmptyLabel = findViewById<TextView?>(R.id.tvEmptyThemeToggleLabel)
        tvEmptyIcon?.text = if (isDark) "🌙" else "☀️"
        tvEmptyLabel?.text = if (isDark) "Dark" else "Light"
        btnEmptyThemeToggle?.contentDescription = if (isDark) "Current: Dark Mode. Tap to switch to Light Mode" else "Current: Light Mode. Tap to switch to Dark Mode"
    }

    private fun resolveAndLoadTree() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (currentUserId.isEmpty()) {
            showNoTreeEmptyState()
            return
        }

        val cachedTreeId = TreePreferences.getActiveTreeId(this)
        val cachedTreeName = TreePreferences.getActiveTreeName(this)

        val layoutHomeShimmer = findViewById<View?>(R.id.layoutHomeShimmer)

        if (cachedTreeId.isNotEmpty()) {
            this.treeId = cachedTreeId
            layoutStatsHeader.visibility = View.VISIBLE
            findViewById<TextView?>(R.id.tvTreeTitle)?.text = "✎  ${cachedTreeName.ifEmpty { "Family Tree" }}"
            loadStatsForTree(cachedTreeId)
            if (pendingCornerButtonPop) {
                pendingCornerButtonPop = false
                isRecreatingFromEmptyStateThemeSwitch = false
                justCompletedThemeMorphInSession = true
                playPostRecreateSpinAndWave(pendingThemeWaveMode, isEmptyCornerPill = false)
            }
        } else if (isRecreatingFromEmptyStateThemeSwitch || isPlayingThemeMorphAnimation || pendingCornerDockMorph || pendingCornerButtonPop || justCompletedThemeMorphInSession) {
            // Instant 0ms Empty State restoration during theme switch animation (never show shimmer skeleton)
            layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
            showNoTreeEmptyState()
        } else {
            // Fresh login / switch account: preserve shimmer skeleton while Firestore resolves user's trees (prevents 0.1s blink)
            layoutStatsHeader.visibility = View.GONE
            layoutPopulatedState.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
            layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.startShimmer(it) }
        }

        firestoreHelper.getUserProfile(currentUserId,
            onSuccess = { profile ->
                val profileTreeId = profile?.currentTreeId.orEmpty()
                firestoreHelper.getUserTrees(currentUserId,
                    onSuccess = { trees ->
                        userOwnedTrees = trees
                        if (trees.isEmpty()) {
                            this.treeId = ""
                            TreePreferences.clear(this)
                            layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
                            showNoTreeEmptyState()
                            return@getUserTrees
                        }

                        val targetTree = trees.firstOrNull { it.id == cachedTreeId }
                            ?: trees.firstOrNull { it.id == profileTreeId }
                            ?: trees.first()

                        val treeChanged = this.treeId != targetTree.id
                        this.treeId = targetTree.id
                        TreePreferences.setActiveTree(this, targetTree.id, targetTree.name)
                        CentralTreeSynchronizer.getInstance().startRealtimeListener(targetTree.id)

                        layoutStatsHeader.visibility = View.VISIBLE
                        findViewById<TextView?>(R.id.tvTreeTitle)?.text = "✎  ${targetTree.name}"
                        if (treeChanged || cachedTreeId.isEmpty()) {
                            loadStatsForTree(targetTree.id)
                        }
                    },
                    onFailure = {
                        if (cachedTreeId.isEmpty()) {
                            layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
                            showNoTreeEmptyState()
                        }
                    }
                )
            },
            onFailure = {
                if (cachedTreeId.isEmpty()) {
                    layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
                    showNoTreeEmptyState()
                }
            }
        )

        firestoreHelper.getOnboardingState(currentUserId,
            onSuccess = { cloudState ->
                if (cloudState != null) {
                    val local = OnboardingPreferences.getOnboardingState(this, currentUserId)
                    var changed = false
                    if (cloudState.hasChosenInitialTheme && !local.hasChosenInitialTheme &&
                        !com.example.btproject2.models.DemoAccountRepository.isNewUserPreviewEmail(authHelper.getCurrentUser()?.email)) {
                        OnboardingPreferences.setHasChosenInitialTheme(this, true, currentUserId)
                        changed = true
                    }
                    if (cloudState.isQuestDismissed && !local.isQuestDismissed) {
                        OnboardingPreferences.setQuestDismissed(this, true, currentUserId)
                        changed = true
                    }
                    if (cloudState.questCanvasInspected && !local.questCanvasInspected && (userOwnedTrees.isNotEmpty() || treeId.isNotEmpty())) {
                        OnboardingPreferences.setMilestoneCanvasInspected(this, true, currentUserId)
                        changed = true
                    }
                    if (cloudState.questCodeShared && !local.questCodeShared && (userOwnedTrees.isNotEmpty() || treeId.isNotEmpty())) {
                        OnboardingPreferences.setMilestoneCodeShared(this, true, currentUserId)
                        changed = true
                    }
                    if (changed) {
                        runOnUiThread { refreshQuestHudUi() }
                    }
                }
            },
            onFailure = {}
        )
    }

    private fun showNoTreeEmptyState() {
        runOnUiThread {
            this.treeId = ""
            layoutStatsHeader.visibility = View.GONE
            layoutPopulatedState.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
            findViewById<TextView?>(R.id.tvTreeTitle)?.text = "✎  Family Tree"
            updateAllThemeToggles()

            when {
                isPlayingThemeMorphAnimation -> {
                    // Do not interrupt an in-flight Celestial Corner Dock Morph animation
                }
                pendingCornerDockMorph -> {
                    pendingCornerDockMorph = false
                    isRecreatingFromEmptyStateThemeSwitch = false
                    justCompletedThemeMorphInSession = true
                    playCornerDockMorphAnimation(pendingThemeWaveMode)
                }
                pendingCornerButtonPop -> {
                    pendingCornerButtonPop = false
                    isRecreatingFromEmptyStateThemeSwitch = false
                    justCompletedThemeMorphInSession = true
                    stopThemeOnboardingLoopAnimators()
                    findViewById<View?>(R.id.layoutEmptyThemeOnboardingOverlay)?.visibility = View.GONE
                    findViewById<View?>(R.id.viewEmptyCornerPulseRing)?.visibility = View.GONE
                    findViewById<View?>(R.id.layoutEmptyStateNormalBody)?.apply {
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        translationY = 0f
                    }
                    playPostRecreateSpinAndWave(pendingThemeWaveMode, isEmptyCornerPill = true)
                }
                !OnboardingPreferences.hasChosenInitialTheme(this@HomeActivity) -> {
                    showThemeOnboardingOverlay(forceShow = false)
                }
                else -> {
                    stopThemeOnboardingLoopAnimators()
                    findViewById<View?>(R.id.layoutEmptyThemeOnboardingOverlay)?.visibility = View.GONE
                    findViewById<View?>(R.id.viewEmptyCornerPulseRing)?.visibility = View.GONE
                    findViewById<View?>(R.id.layoutEmptyStateNormalBody)?.apply {
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        translationY = 0f
                    }
                    if (!justCompletedThemeMorphInSession) {
                        // Phase 1 Onboarding: Show 3-slide welcome story modal for new user without trees
                        WelcomeStoryDialogFragment.showIfNotSeen(this@HomeActivity)
                    }
                }
            }

            // In Phase 1 (No trees yet), Milestone 1 (Roots) is NOT completed
            OnboardingPreferences.setMilestoneRoots(this@HomeActivity, false)
            refreshQuestHudUi(0)
        }
    }

    private fun showThemeOnboardingOverlay(forceShow: Boolean = false) {
        val overlay = findViewById<View?>(R.id.layoutEmptyThemeOnboardingOverlay) ?: return
        val spotlightContent = findViewById<View?>(R.id.layoutThemeSpotlightContent)
        val normalBody = findViewById<View?>(R.id.layoutEmptyStateNormalBody)
        val pulseRing = findViewById<View?>(R.id.viewEmptyCornerPulseRing)
        val tvBadge = findViewById<View?>(R.id.tvThemeOnboardingBadge)
        val tvSun = findViewById<View?>(R.id.tvOrbSunIcon)
        val tvMoon = findViewById<View?>(R.id.tvOrbMoonIcon)

        stopThemeOnboardingLoopAnimators()

        val density = resources.displayMetrics.density
        normalBody?.apply {
            alpha = 0.06f
            scaleX = 0.96f
            scaleY = 0.96f
            translationY = 10f * density
        }

        overlay.apply {
            visibility = View.VISIBLE
            alpha = 1f
            setBackgroundColor(android.graphics.Color.argb(236, 3, 8, 5))
        }

        spotlightContent?.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }

        pulseRing?.apply {
            visibility = View.VISIBLE
            alpha = 0.75f
            scaleX = 1f
            scaleY = 1f
        }

        // 1. Floating Orb Animation on Sun & Moon (matches @keyframes floatOrb)
        listOfNotNull(tvSun, tvMoon).forEachIndexed { idx, orbView ->
            val floatAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400L
                startDelay = if (idx == 1) 300L else 0L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    orbView.translationY = -5f * density * fraction
                    orbView.rotation = 6f * fraction
                }
                start()
            }
            activeThemeOnboardingAnimators.add(floatAnim)
        }

        // 2. Attention Flash Pulse on "✨ WELCOME TO KINTRACE" Badge
        tvBadge?.let { badgeView ->
            val badgeAnim = android.animation.ValueAnimator.ofFloat(1f, 1.05f).apply {
                duration = 1500L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                addUpdateListener { anim ->
                    val s = anim.animatedValue as Float
                    badgeView.scaleX = s
                    badgeView.scaleY = s
                }
                start()
            }
            activeThemeOnboardingAnimators.add(badgeAnim)
        }

        // 3. Target Ring Pulse around Top-Left Corner Button
        pulseRing?.let { ringView ->
            val ringAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                addUpdateListener { anim ->
                    val f = anim.animatedValue as Float
                    val s = 1f + (0.08f * f)
                    ringView.scaleX = s
                    ringView.scaleY = s
                    ringView.alpha = 0.75f - (0.45f * f)
                }
                start()
            }
            activeThemeOnboardingAnimators.add(ringAnim)
        }
    }

    private fun stopThemeOnboardingLoopAnimators() {
        activeThemeOnboardingAnimators.forEach { it.cancel() }
        activeThemeOnboardingAnimators.clear()
    }

    private fun handleNewUserThemeChoice(chosenMode: String) {
        if (isPlayingThemeMorphAnimation) return
        OnboardingPreferences.setHasChosenInitialTheme(this, true)
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (currentUserId.isNotEmpty()) {
            firestoreHelper.saveOnboardingState(
                currentUserId,
                OnboardingPreferences.getOnboardingState(this, currentUserId)
            )
        }

        val currentlyDark = ThemePreferences.isDarkTheme(this)
        val wantsDark = (chosenMode == ThemePreferences.THEME_DARK)
        pendingThemeWaveMode = chosenMode

        if (currentlyDark != wantsDark) {
            // Switch the entire Activity theme at 0ms so 100% of views (cards, header, bottom nav, background)
            // render in the chosen theme simultaneously with 0.0s hybrid delay, then play the 60fps morph animation!
            isRecreatingFromEmptyStateThemeSwitch = true
            pendingCornerDockMorph = true
            ThemePreferences.setThemeMode(this, chosenMode)
            overridePendingTransition(0, 0)
        } else {
            ThemePreferences.setThemeMode(this, chosenMode)
            playCornerDockMorphAnimation(chosenMode)
        }
    }

    private fun requestThemeToggleWithSpinAndWave() {
        if (isPlayingThemeMorphAnimation) return
        OnboardingPreferences.setHasChosenInitialTheme(this, true)
        val currentlyDark = ThemePreferences.isDarkTheme(this)
        val newMode = if (currentlyDark) ThemePreferences.THEME_LIGHT else ThemePreferences.THEME_DARK

        // Commit the global theme switch at 0ms so the entire screen changes on the exact same frame
        // (zero 0.1s hybrid state), then immediately burst the 360° Celestial Spin + Radial Color Wave on Frame 0!
        isRecreatingFromEmptyStateThemeSwitch = true
        pendingCornerButtonPop = true
        pendingThemeWaveMode = newMode
        justCompletedThemeMorphInSession = true
        ThemePreferences.setThemeMode(this, newMode)
        overridePendingTransition(0, 0)
    }

    private fun runWallClockAnimation(
        durationMs: Long,
        interpolator: android.animation.TimeInterpolator = android.view.animation.DecelerateInterpolator(),
        onUpdate: (Float) -> Unit,
        onEnd: (() -> Unit)? = null
    ) {
        val startTime = android.os.SystemClock.uptimeMillis()
        val choreographer = android.view.Choreographer.getInstance()
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (isFinishing || isDestroyed) return
                val elapsed = android.os.SystemClock.uptimeMillis() - startTime
                val rawFraction = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val eased = interpolator.getInterpolation(rawFraction)
                onUpdate(eased)
                if (rawFraction < 1f) {
                    choreographer.postFrameCallback(this)
                } else {
                    onEnd?.invoke()
                }
            }
        }
        choreographer.postFrameCallback(callback)
    }

    private fun playCornerDockMorphAnimation(chosenMode: String) {
        isPlayingThemeMorphAnimation = true
        justCompletedThemeMorphInSession = true
        val overlay = findViewById<View?>(R.id.layoutEmptyThemeOnboardingOverlay)
        val spotlightContent = findViewById<View?>(R.id.layoutThemeSpotlightContent)
        val cornerBtn = findViewById<View?>(R.id.btnEmptyThemeToggle)
        val normalBody = findViewById<View?>(R.id.layoutEmptyStateNormalBody)
        val pulseRing = findViewById<View?>(R.id.viewEmptyCornerPulseRing)
        val tappedCard = findViewById<View?>(
            if (chosenMode == ThemePreferences.THEME_DARK) R.id.cardChooseDarkMode else R.id.cardChooseLightMode
        )

        stopThemeOnboardingLoopAnimators()

        val density = resources.displayMetrics.density

        // Tactile press pop on the chosen card (60fps wall-clock)
        if (tappedCard != null) {
            runWallClockAnimation(
                durationMs = 150L,
                onUpdate = { f ->
                    val scale = 1f + 0.045f * kotlin.math.sin(f * Math.PI).toFloat()
                    tappedCard.scaleX = scale
                    tappedCard.scaleY = scale
                }
            )
        }

        // Start in initial full-screen spotlight position so user sees the smooth morph into top-left corner
        overlay?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            setBackgroundColor(android.graphics.Color.argb(236, 3, 8, 5))
        }
        spotlightContent?.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
        normalBody?.apply {
            alpha = 0.25f
            scaleX = 0.96f
            scaleY = 0.96f
            translationY = 10f * density
        }
        pulseRing?.apply {
            visibility = View.VISIBLE
            alpha = 0.9f
        }

        // Wait for layout pass so spotlightContent and cornerBtn have exact screen coordinates
        overlay?.post {
            if (isFinishing || isDestroyed) {
                isPlayingThemeMorphAnimation = false
                isRecreatingFromEmptyStateThemeSwitch = false
                return@post
            }

            triggerRadialThemeWave(chosenMode)

            overlay.postDelayed({
                if (isFinishing || isDestroyed) {
                    isPlayingThemeMorphAnimation = false
                    isRecreatingFromEmptyStateThemeSwitch = false
                    return@postDelayed
                }

                // Compute exact screen delta from spotlight center to top-left corner pill
                var targetDeltaX = -118f * density
                var targetDeltaY = -260f * density
                if (spotlightContent != null && cornerBtn != null && spotlightContent.width > 0 && cornerBtn.width > 0) {
                    val spotLoc = IntArray(2)
                    val cornerLoc = IntArray(2)
                    spotlightContent.getLocationOnScreen(spotLoc)
                    cornerBtn.getLocationOnScreen(cornerLoc)
                    val spotCenterX = spotLoc[0] + spotlightContent.width / 2f
                    val spotCenterY = spotLoc[1] + spotlightContent.height / 2f
                    val cornerCenterX = cornerLoc[0] + cornerBtn.width / 2f
                    val cornerCenterY = cornerLoc[1] + cornerBtn.height / 2f
                    targetDeltaX = cornerCenterX - spotCenterX
                    targetDeltaY = cornerCenterY - spotCenterY
                }

                val morphInterpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
                var hasPoppedCornerBtn = false

                // 60fps Choreographer wall-clock animation (immune to animator_duration_scale = 0 / Battery Saver)
                runWallClockAnimation(
                    durationMs = 920L,
                    interpolator = morphInterpolator,
                    onUpdate = { f ->
                        // 1. Shrink and glide spotlight cards directly into the top-left corner pill
                        spotlightContent?.apply {
                            translationX = targetDeltaX * f
                            translationY = targetDeltaY * f
                            val s = 1f - (0.88f * f)
                            scaleX = s
                            scaleY = s
                            // Stay bright during travel, fade out only in the final 35%
                            alpha = if (f < 0.65f) 1f else ((1f - f) / 0.35f).coerceIn(0f, 1f)
                        }

                        // 2. Smoothly dissolve ONLY the dimmed backdrop color alpha (236 -> 0)
                        val scrimAlpha = (236f * (1f - f)).toInt().coerceIn(0, 236)
                        overlay.setBackgroundColor(android.graphics.Color.argb(scrimAlpha, 3, 8, 5))

                        // 3. Smoothly un-blur and rise Empty State interface into full focus
                        normalBody?.apply {
                            alpha = 0.25f + (0.75f * f)
                            val bodyScale = 0.96f + (0.04f * f)
                            scaleX = bodyScale
                            scaleY = bodyScale
                            translationY = 10f * density * (1f - f)
                        }

                        // 4. Pop the top-left corner button right as the shrinking card docks into it
                        if (!hasPoppedCornerBtn && f >= 0.72f) {
                            hasPoppedCornerBtn = true
                            popEmptyCornerThemeButton()
                        }

                        // 5. Fade out the dashed target ring in the final stretch
                        if (f > 0.75f) {
                            pulseRing?.alpha = (0.9f * ((1f - f) / 0.25f)).coerceIn(0f, 0.9f)
                        }
                    },
                    onEnd = {
                        isPlayingThemeMorphAnimation = false
                        isRecreatingFromEmptyStateThemeSwitch = false
                        overlay.visibility = View.GONE
                        overlay.setBackgroundColor(android.graphics.Color.argb(236, 3, 8, 5))
                        pulseRing?.visibility = View.GONE
                        spotlightContent?.apply {
                            translationX = 0f
                            translationY = 0f
                            scaleX = 1f
                            scaleY = 1f
                            alpha = 1f
                        }
                        updateAllThemeToggles()
                    }
                )
            }, 140L)
        }
    }

    private fun playPostRecreateSpinAndWave(
        mode: String,
        isEmptyCornerPill: Boolean
    ) {
        val anchorButton = findViewById<View?>(
            if (isEmptyCornerPill) R.id.btnEmptyThemeToggle else R.id.btnThemeToggle
        ) ?: return
        val iconView = findViewById<TextView?>(
            if (isEmptyCornerPill) R.id.tvEmptyThemeToggleIcon else R.id.tvThemeToggleIcon
        )
        val wave = findViewById<View?>(R.id.viewThemeRadialWave)
        val wantsDark = (mode == ThemePreferences.THEME_DARK)

        anchorButton.post {
            if (isFinishing || isDestroyed) return@post

            val rootView = wave?.parent as? View
            if (wave != null && rootView != null && anchorButton.width > 0 && rootView.width > 0) {
                val btnLoc = IntArray(2)
                val rootLoc = IntArray(2)
                anchorButton.getLocationOnScreen(btnLoc)
                rootView.getLocationOnScreen(rootLoc)
                val btnCenterX = btnLoc[0] + anchorButton.width / 2f
                val btnCenterY = btnLoc[1] + anchorButton.height / 2f
                val rootCenterX = rootLoc[0] + rootView.width / 2f
                val rootCenterY = rootLoc[1] + rootView.height / 2f
                wave.translationX = btnCenterX - rootCenterX
                wave.translationY = btnCenterY - rootCenterY
            }

            wave?.apply {
                setBackgroundResource(
                    if (wantsDark) R.drawable.bg_theme_wave_dark else R.drawable.bg_theme_wave_light
                )
                visibility = View.VISIBLE
                scaleX = 0f
                scaleY = 0f
                alpha = 0.90f
            }

            val spinInterpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)

            runWallClockAnimation(
                durationMs = 640L,
                interpolator = spinInterpolator,
                onUpdate = { f ->
                    // 1. 360° Celestial Spin on the Sun/Moon glyph + Spring Pop on the Pill
                    iconView?.rotation = 360f * f
                    val springScale = 1f + 0.34f * kotlin.math.sin(f * Math.PI).toFloat()
                    anchorButton.scaleX = springScale
                    anchorButton.scaleY = springScale

                    // 2. Radial Color Wave Burst expanding from the icon across the newly-themed screen
                    wave?.apply {
                        val s = 0.5f + (56f * f)
                        scaleX = s
                        scaleY = s
                        alpha = (0.90f * (1f - f)).coerceIn(0f, 0.90f)
                    }
                },
                onEnd = {
                    iconView?.rotation = 0f
                    anchorButton.scaleX = 1f
                    anchorButton.scaleY = 1f
                    wave?.apply {
                        visibility = View.GONE
                        scaleX = 0f
                        scaleY = 0f
                        translationX = 0f
                        translationY = 0f
                    }
                }
            )
        }
    }

    private fun triggerRadialThemeWave(mode: String) {
        val wave = findViewById<View?>(R.id.viewThemeRadialWave) ?: return
        val isDarkWave = (mode == ThemePreferences.THEME_DARK)
        wave.setBackgroundResource(
            if (isDarkWave) R.drawable.bg_theme_wave_dark else R.drawable.bg_theme_wave_light
        )
        wave.visibility = View.VISIBLE
        wave.scaleX = 0f
        wave.scaleY = 0f
        wave.alpha = 0.88f

        runWallClockAnimation(
            durationMs = 820L,
            interpolator = android.view.animation.DecelerateInterpolator(),
            onUpdate = { f ->
                val s = 54f * f
                wave.scaleX = s
                wave.scaleY = s
                wave.alpha = 0.88f * (1f - f)
            },
            onEnd = {
                wave.visibility = View.GONE
                wave.scaleX = 0f
                wave.scaleY = 0f
            }
        )
    }

    private fun popEmptyCornerThemeButton() {
        val cornerBtn = findViewById<View?>(R.id.btnEmptyThemeToggle) ?: return
        runWallClockAnimation(
            durationMs = 420L,
            interpolator = android.view.animation.LinearInterpolator(),
            onUpdate = { f ->
                val s = if (f < 0.4f) {
                    1f + 0.28f * (f / 0.4f)
                } else {
                    1.28f - 0.28f * ((f - 0.4f) / 0.6f)
                }
                cornerBtn.scaleX = s
                cornerBtn.scaleY = s
            },
            onEnd = {
                cornerBtn.scaleX = 1f
                cornerBtn.scaleY = 1f
            }
        )
    }

    private fun showTreeSelectionDialog() {
        if (userOwnedTrees.isEmpty()) {
            startActivity(Intent(this, CreateTreeActivity::class.java))
            return
        }

        val treeNames = userOwnedTrees.map { it.name }.toTypedArray()
        val currentActiveId = TreePreferences.getActiveTreeId(this)
        val checkedItem = userOwnedTrees.indexOfFirst { it.id == currentActiveId }.let { if (it >= 0) it else 0 }

        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Select Family Tree")
            .setSingleChoiceItems(treeNames, checkedItem) { dialog, which ->
                val selectedTree = userOwnedTrees[which]
                TreePreferences.setActiveTree(this, selectedTree.id, selectedTree.name)
                this.treeId = selectedTree.id
                CentralTreeSynchronizer.getInstance().startRealtimeListener(selectedTree.id)

                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                if (currentUserId.isNotEmpty()) {
                    firestoreHelper.getUserProfile(currentUserId, onSuccess = { profile ->
                        val updatedProfile = (profile ?: UserProfile(id = currentUserId)).copy(currentTreeId = selectedTree.id)
                        firestoreHelper.saveUserProfile(updatedProfile, onSuccess = {}, onFailure = {})
                    }, onFailure = {})
                }
                findViewById<TextView>(R.id.tvTreeTitle)?.text = "✎  ${selectedTree.name}"
                loadStatsForTree(selectedTree.id)
                dialog.dismiss()
            }
            .setPositiveButton("Create New Tree") { _, _ ->
                startActivity(Intent(this, CreateTreeActivity::class.java))
            }
            .setNeutralButton("Join Tree") { _, _ ->
                startActivity(Intent(this, JoinTreeActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadStatsForTree(activeTreeId: String) {
        if (activeTreeId.isEmpty()) {
            showNoTreeEmptyState()
            return
        }

        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val tvBranchCount = findViewById<TextView>(R.id.tvBranchCount)
        val tvRecentCount = findViewById<TextView>(R.id.tvRecentCount)
        val layoutHomeShimmer = findViewById<View?>(R.id.layoutHomeShimmer)

        // Instant 0ms cache rendering before network query
        val cached = FirestoreHelper.getCachedPersons(activeTreeId)?.filter { it.id !in recentlyDeletedIds }
        if (!cached.isNullOrEmpty()) {
            layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
            tvMemberCount.text = cached.size.toString()
            val genCount = calculateGenerations(cached)
            findViewById<TextView?>(R.id.tvGenerationCountLarge)?.text = genCount.toString()
            findViewById<TextView?>(R.id.tvGenerationsCount)?.text = genCount.toString()
            findViewById<TextView>(R.id.tvLivingCount)?.text = cached.count { it.isLiving }.toString()
            val totalRecords = cached.size + cached.count { !it.fatherId.isNullOrEmpty() } + cached.count { !it.motherId.isNullOrEmpty() }
            findViewById<TextView?>(R.id.tvRecordsCount)?.text = maxOf(cached.size, totalRecords).toString()
            layoutStatsHeader.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
            layoutPopulatedState.visibility = View.VISIBLE
            renderRecentActivity(cached)
        } else {
            // No cache available: display heritage shimmer skeleton placeholder (0ms)
            layoutStatsHeader.visibility = View.GONE
            layoutPopulatedState.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
            layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.startShimmer(it) }
        }

        firestoreHelper.getPersonsByTree(
            activeTreeId,
            onSuccess = { persons ->
                val activePersons = persons.filter { it.id !in recentlyDeletedIds }
                FirestoreHelper.setCachedPersons(activePersons)
                tvMemberCount.text = activePersons.size.toString()
                
                // In Phase 2, Milestone 1 (Roots) is confirmed established
                OnboardingPreferences.setMilestoneRoots(this@HomeActivity, true)

                if (activePersons.size >= 2) {
                    OnboardingPreferences.setMilestoneTwoMembers(this@HomeActivity, true)
                } else {
                    OnboardingPreferences.setMilestoneTwoMembers(this@HomeActivity, false)
                }
                refreshQuestHudUi(activePersons.size)

                val genCount = calculateGenerations(activePersons)
                findViewById<TextView?>(R.id.tvGenerationCountLarge)?.text = genCount.toString()
                findViewById<TextView?>(R.id.tvGenerationsCount)?.text = genCount.toString()

                val livingCount = activePersons.count { it.isLiving }
                findViewById<TextView>(R.id.tvLivingCount)?.text = livingCount.toString()

                val totalRecords = activePersons.size + activePersons.count { !it.fatherId.isNullOrEmpty() } + activePersons.count { !it.motherId.isNullOrEmpty() }
                findViewById<TextView?>(R.id.tvRecordsCount)?.text = maxOf(activePersons.size, totalRecords).toString()
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)
                val recentMembers7d = activePersons.count { it.createdAt >= sevenDaysAgo || it.updatedAt >= sevenDaysAgo }
                val recentLinks7d = activePersons.count {
                    (it.createdAt >= sevenDaysAgo || it.updatedAt >= sevenDaysAgo) &&
                        (!it.fatherId.isNullOrEmpty() || !it.motherId.isNullOrEmpty() || !it.spouseId.isNullOrEmpty())
                }
                val recentRecords7d = recentMembers7d + recentLinks7d
                val uniqueClanSurnames = activePersons.mapNotNull { it.lastName.trim().takeIf { s -> s.isNotEmpty() }?.lowercase() }.distinct().size.coerceAtLeast(1)

                findViewById<TextView?>(R.id.tvRecordsDelta)?.text = "+$recentRecords7d"
                findViewById<TextView?>(R.id.tvMemberDelta)?.text = "+$recentMembers7d"
                findViewById<TextView?>(R.id.tvBranchDelta)?.text = "+$uniqueClanSurnames"

                tvInsightsBannerSub.text = "Lineage Overview · Tap to view breakdown →"

                layoutEmptyState.visibility = View.GONE
                if (layoutHomeShimmer != null && layoutHomeShimmer.visibility == View.VISIBLE) {
                    com.example.btproject2.utils.KinTraceShimmerHelper.crossfade(layoutHomeShimmer, layoutPopulatedState, durationMs = 150)
                    layoutStatsHeader.alpha = 0f
                    layoutStatsHeader.visibility = View.VISIBLE
                    layoutStatsHeader.animate().alpha(1f).setDuration(150).start()
                } else {
                    layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
                    layoutStatsHeader.visibility = View.VISIBLE
                    layoutPopulatedState.visibility = View.VISIBLE
                }

                firestoreHelper.getRecentActivities(activeTreeId, limit = 5,
                    onSuccess = { activities ->
                        if (activities.isNotEmpty()) {
                            renderActivityRecords(activities)
                        } else {
                            renderRecentActivity(activePersons)
                        }
                    },
                    onFailure = {
                        renderRecentActivity(activePersons)
                    }
                )
            },
            onFailure = {
                val fallbackCache = FirestoreHelper.getCachedPersons(activeTreeId)?.filter { it.id !in recentlyDeletedIds }
                if (!fallbackCache.isNullOrEmpty()) {
                    tvMemberCount.text = fallbackCache.size.toString()
                    val genCount = calculateGenerations(fallbackCache)
                    findViewById<TextView?>(R.id.tvGenerationCountLarge)?.text = genCount.toString()
                    findViewById<TextView?>(R.id.tvGenerationsCount)?.text = genCount.toString()
                    findViewById<TextView>(R.id.tvLivingCount)?.text = fallbackCache.count { it.isLiving }.toString()
                    val totalRecords = fallbackCache.size + fallbackCache.count { !it.fatherId.isNullOrEmpty() } + fallbackCache.count { !it.motherId.isNullOrEmpty() }
                    findViewById<TextView?>(R.id.tvRecordsCount)?.text = maxOf(fallbackCache.size, totalRecords).toString()
                    layoutEmptyState.visibility = View.GONE
                    if (layoutHomeShimmer != null && layoutHomeShimmer.visibility == View.VISIBLE) {
                        com.example.btproject2.utils.KinTraceShimmerHelper.crossfade(layoutHomeShimmer, layoutPopulatedState, durationMs = 150)
                        layoutStatsHeader.alpha = 0f
                        layoutStatsHeader.visibility = View.VISIBLE
                        layoutStatsHeader.animate().alpha(1f).setDuration(150).start()
                    } else {
                        layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
                        layoutStatsHeader.visibility = View.VISIBLE
                        layoutPopulatedState.visibility = View.VISIBLE
                    }
                    renderRecentActivity(fallbackCache)
                } else {
                    layoutHomeShimmer?.let { com.example.btproject2.utils.KinTraceShimmerHelper.hideInstantly(it) }
                    tvMemberCount.text = "0"
                    findViewById<TextView>(R.id.tvLivingCount)?.text = "0"
                    findViewById<TextView?>(R.id.tvGenerationCountLarge)?.text = "0"
                    findViewById<TextView?>(R.id.tvGenerationsCount)?.text = "0"
                    findViewById<TextView?>(R.id.tvRecordsCount)?.text = "0"
                }
            }
        )

        // Check unread notifications count (including system advisories) for badge
        refreshNotificationBadge()

        firestoreHelper.getTree(activeTreeId,
            onSuccess = { tree ->
                val treeName = tree?.name?.takeIf { it.isNotBlank() } ?: "Your Family Tree"
                findViewById<TextView>(R.id.tvTreeTitle)?.text = "✎  $treeName"
            },
            onFailure = {}
        )

        val userId = authHelper.getCurrentUser()?.uid ?: ""
        firestoreHelper.getBranchCount(
            userId,
            onSuccess = { count -> tvBranchCount.text = count.toString() },
            onFailure = { tvBranchCount.text = "1" }
        )
    }

    private fun calculateGenerations(persons: List<Person>): Int {
        if (persons.isEmpty()) return 0
        val personMap = persons.associateBy { it.id }
        val memo = mutableMapOf<String, Int>()

        fun getDepth(personId: String?, visited: MutableSet<String>): Int {
            if (personId.isNullOrEmpty() || personId !in personMap || personId in visited) return 0
            memo[personId]?.let { return it }
            visited.add(personId)
            val p = personMap[personId] ?: return 0
            val fatherDepth = getDepth(p.fatherId, visited)
            val motherDepth = getDepth(p.motherId, visited)
            visited.remove(personId)
            val d = 1 + maxOf(fatherDepth, motherDepth)
            memo[personId] = d
            return d
        }

        val maxDepth = persons.maxOfOrNull { getDepth(it.id, mutableSetOf()) } ?: 1
        return maxOf(1, maxDepth)
    }

    private fun renderActivityRecords(activities: List<ActivityRecord>) {
        containerRecentItems.removeAllViews()
        if (activities.isEmpty()) {
            tvNoRecentActivity.visibility = View.VISIBLE
            return
        }

        tvNoRecentActivity.visibility = View.GONE
        val now = System.currentTimeMillis()

        for (activity in activities) {
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, containerRecentItems, false)
            val tvIcon = itemView.findViewById<TextView>(R.id.tvRecentIcon)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvRecentTitle)
            val tvSubtitle = itemView.findViewById<TextView>(R.id.tvRecentSubtitle)
            val tvRecentGenBadge = itemView.findViewById<TextView?>(R.id.tvRecentGenBadge)
            val tvRecentTime = itemView.findViewById<TextView?>(R.id.tvRecentTime)

            when (activity.type.uppercase()) {
                "MEMBER_ADDED", "CREATE" -> {
                    tvIcon.text = "➕"
                    tvRecentGenBadge?.text = "Added"
                }
                "MEMBER_UPDATED", "UPDATE" -> {
                    tvIcon.text = "✏️"
                    tvRecentGenBadge?.text = "Edited"
                }
                "MEMBER_DELETED", "DELETE" -> {
                    tvIcon.text = "🗑️"
                    tvRecentGenBadge?.text = "Deleted"
                }
                "RELATIONSHIP_LINKED", "LINK" -> {
                    tvIcon.text = "🔗"
                    tvRecentGenBadge?.text = "Linked"
                }
                "TRACE_PERFORMED", "TRACE" -> {
                    tvIcon.text = "🧬"
                    tvRecentGenBadge?.text = "Traced"
                }
                "BRANCH_MERGED", "MERGE" -> {
                    tvIcon.text = "🌿"
                    tvRecentGenBadge?.text = "Merged"
                }
                "DOCUMENT_ATTACHED", "DOCUMENT" -> {
                    tvIcon.text = "📎"
                    tvRecentGenBadge?.text = "Document"
                }
                "PHOTO_UPDATED", "PHOTO" -> {
                    tvIcon.text = "📷"
                    tvRecentGenBadge?.text = "Photo"
                }
                else -> {
                    tvIcon.text = "📜"
                    tvRecentGenBadge?.text = "Activity"
                }
            }

            tvTitle.text = activity.description
            tvSubtitle.text = activity.userName.ifEmpty { "Family Tree" }

            val diffMs = now - activity.timestamp
            val diffMinutes = diffMs / (1000 * 60)
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            val timeText = when {
                diffMinutes < 1 -> "Just now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> "${diffDays}d ago"
            }
            tvRecentTime?.text = timeText

            val actType = activity.type.uppercase()
            if (actType in listOf("DELETE", "MEMBER_DELETED")) {
                itemView.setOnClickListener {
                    if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(authHelper.getCurrentUser()?.email)) {
                        val intent = Intent(this, AdminPortalActivity::class.java).apply {
                            putExtra("OPEN_TAB", 3)
                            putExtra("HIGHLIGHT_MEMBER_ID", activity.targetId)
                            putExtra("TREE_ID", activity.treeId)
                        }
                        startActivity(intent)
                    } else {
                        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                            .setTitle("Archived Record Notice")
                            .setMessage("${activity.description}\n\nDeleted member recovery is managed in the System Administrator Citadel.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } else if (actType in listOf("MERGE_COLLISION_BLOCKED", "COLLISION", "MERGE_COLLISION")) {
                itemView.setOnClickListener {
                    if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(authHelper.getCurrentUser()?.email)) {
                        val intent = Intent(this, AdminPortalActivity::class.java).apply {
                            putExtra("OPEN_TAB", 3)
                            putExtra("HIGHLIGHT_COLLISION_ID", activity.targetId)
                        }
                        startActivity(intent)
                    } else {
                        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                            .setTitle("Merge Collision Notice")
                            .setMessage("${activity.description}\n\nMerge collision dockets are reviewed by the System Administrator.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } else if (activity.targetId.isNotEmpty()) {
                itemView.setOnClickListener {
                    val intent = Intent(this, MemberDetailActivity::class.java).apply {
                        putExtra("personId", activity.targetId)
                        putExtra("TREE_ID", treeId)
                    }
                    memberDetailLauncher.launch(intent)
                }
            }

            containerRecentItems.addView(itemView)
        }
    }

    private fun renderRecentActivity(persons: List<Person>) {
        containerRecentItems.removeAllViews()
        val recentPersons = persons.sortedByDescending { it.createdAt }.take(5)

        if (recentPersons.isEmpty()) {
            tvNoRecentActivity.visibility = View.VISIBLE
            return
        }

        tvNoRecentActivity.visibility = View.GONE
        val now = System.currentTimeMillis()

        val personMap = persons.associateBy { it.id }
        val memo = mutableMapOf<String, Int>()

        fun getPersonGen(personId: String?, visited: MutableSet<String>): Int {
            if (personId.isNullOrEmpty() || personId !in personMap || personId in visited) return 1
            memo[personId]?.let { return it }
            visited.add(personId)
            val p = personMap[personId] ?: return 1
            val fGen = getPersonGen(p.fatherId, visited)
            val mGen = getPersonGen(p.motherId, visited)
            visited.remove(personId)
            val g = if (p.fatherId.isNullOrEmpty() && p.motherId.isNullOrEmpty()) 1 else 1 + maxOf(fGen, mGen)
            memo[personId] = g
            return g
        }

        for (person in recentPersons) {
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, containerRecentItems, false)
            val tvIcon = itemView.findViewById<TextView>(R.id.tvRecentIcon)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvRecentTitle)
            val tvSubtitle = itemView.findViewById<TextView>(R.id.tvRecentSubtitle)
            val tvRecentGenBadge = itemView.findViewById<TextView?>(R.id.tvRecentGenBadge)
            val tvRecentTime = itemView.findViewById<TextView?>(R.id.tvRecentTime)

            val fnInitial = person.firstName.firstOrNull()?.uppercase() ?: ""
            val lnInitial = person.lastName.firstOrNull()?.uppercase() ?: ""
            tvIcon.text = (fnInitial + lnInitial).ifEmpty { "FM" }

            val fullName = "${person.firstName} ${person.lastName}".trim().ifEmpty { "Family Member" }
            tvTitle.text = fullName

            val isFemale = person.gender.equals("Female", ignoreCase = true)
            val branchSide = if (isFemale) "Maternal" else "Paternal"
            val statusDesc = if (person.isLiving) "Living" else "Deceased"
            val gen = getPersonGen(person.id, mutableSetOf())
            tvSubtitle.text = "$statusDesc · $branchSide lineage"

            tvRecentGenBadge?.text = "G$gen"

            val diffMs = now - person.createdAt
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            val timeText = when {
                diffHours < 1 -> "Just now"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> "${diffDays}d ago"
            }
            tvRecentTime?.text = timeText

            itemView.setOnClickListener {
                val intent = Intent(this, MemberDetailActivity::class.java).apply {
                    putExtra("personId", person.id)
                    if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
                }
                memberDetailLauncher.launch(intent)
            }

            containerRecentItems.addView(itemView)
        }
    }

    private fun showAccountProfileDialog() {
        val currentUser = authHelper.getCurrentUser()
        if (currentUser?.email.equals("admin@kintrace.ph", ignoreCase = true)) {
            val adminSheet = com.example.btproject2.ui.dialogs.SuperAdminProfileBottomSheet.newInstance()
            adminSheet.onProfileUpdated = { newName ->
                tvUserName.text = newName
                tvProfileInitial.text = newName.firstOrNull()?.uppercase() ?: "A"
            }
            adminSheet.show(supportFragmentManager, com.example.btproject2.ui.dialogs.SuperAdminProfileBottomSheet.TAG)
            return
        }

        val sheet = ClanProfileBottomSheet.newInstance(treeId)
        sheet.onProfileUpdated = { newName ->
            tvUserName.text = newName
            tvProfileInitial.text = newName.first().uppercase()
        }
        sheet.onQuestResetRequested = {
            refreshQuestHudUi()
        }
        sheet.onRequestChangePassword = {
            showChangePasswordDialog()
        }
        sheet.show(supportFragmentManager, ClanProfileBottomSheet.TAG)
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etNewPassword = view.findViewById<EditText>(R.id.etDialogNewPassword)
        val etConfirmPassword = view.findViewById<EditText>(R.id.etDialogConfirmPassword)
        val tvPasswordError = view.findViewById<TextView>(R.id.tvDialogPasswordError)
        val layoutStrength = view.findViewById<View?>(R.id.layoutDialogPasswordStrength)
        val tvMatchPill = view.findViewById<TextView?>(R.id.tvDialogPasswordMatchPill)

        if (layoutStrength != null) {
            com.example.btproject2.utils.PasswordStrengthMeterBinder(
                container = layoutStrength,
                etPassword = etNewPassword,
                etConfirmPassword = etConfirmPassword,
                tvMatchPill = tvMatchPill
            )
        }

        etNewPassword.doOnTextChanged { _, _, _, _ -> tvPasswordError.visibility = View.GONE }
        etConfirmPassword.doOnTextChanged { _, _, _, _ -> tvPasswordError.visibility = View.GONE }

        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Update Password", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            (dialog.findViewById<View>(R.id.btnModalPositive) ?: dialog.getButton(AlertDialog.BUTTON_POSITIVE)).setOnClickListener {
                val newPass = etNewPassword.text.toString().trim()
                val confirmPass = etConfirmPassword.text.toString().trim()

                val passValidation = com.example.btproject2.utils.PasswordSecurityPolicy.validate(newPass)
                if (passValidation is com.example.btproject2.utils.RegistrationValidator.FieldResult.Invalid) {
                    tvPasswordError.visibility = View.VISIBLE
                    tvPasswordError.text = passValidation.errorMessage
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
                        KinTraceAlert.show(this, "Password updated successfully!")
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        if (currentUserId.isNotEmpty()) {
                            val notif = com.example.btproject2.utils.NotificationHelper.createPasswordChangedNotification(
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

    // ══════════════════════════════════════════════════════════════
    // CLAN BUILDER QUEST HUD (PHASE 2 ONBOARDING)
    // ══════════════════════════════════════════════════════════════

    private fun setupQuestHud() {
        val cardQuestHud = findViewById<View?>(R.id.cardQuestHud) ?: return
        val btnToggleCollapse = findViewById<TextView?>(R.id.btnToggleQuestCollapse)
        val btnRootsAction = findViewById<Button?>(R.id.btnQuestRootsAction)
        val btnAddMember = findViewById<Button?>(R.id.btnQuestAddMember)
        val btnOpenCanvas = findViewById<Button?>(R.id.btnQuestOpenCanvas)
        val btnShareCode = findViewById<Button?>(R.id.btnQuestShareCode)
        val btnDismiss = findViewById<Button?>(R.id.btnDismissQuest)

        btnToggleCollapse?.setOnClickListener {
            val currentState = OnboardingPreferences.getOnboardingState(this)
            val newCollapsed = !currentState.isQuestCollapsed
            OnboardingPreferences.setQuestCollapsed(this, newCollapsed)
            refreshQuestHudUi()
        }

        btnRootsAction?.setOnClickListener {
            if (userOwnedTrees.isEmpty() && treeId.isEmpty()) {
                startActivity(Intent(this, CreateTreeActivity::class.java))
            } else {
                OnboardingPreferences.setMilestoneRoots(this, true)
                refreshQuestHudUi()
            }
        }

        btnAddMember?.setOnClickListener {
            if (treeId.isEmpty()) {
                KinTraceAlert.show(this, "Create or join a family tree first to add relatives.")
                startActivity(Intent(this, CreateTreeActivity::class.java))
            } else {
                val intent = Intent(this, AddMemberActivity::class.java).apply {
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }
        }

        btnOpenCanvas?.setOnClickListener {
            if (treeId.isEmpty() && userOwnedTrees.isEmpty()) {
                KinTraceAlert.show(this, "Create or join a family tree first to inspect the canvas.")
                startActivity(Intent(this, CreateTreeActivity::class.java))
            } else {
                OnboardingPreferences.setMilestoneCanvasInspected(this, true)
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                if (currentUserId.isNotEmpty()) {
                    firestoreHelper.saveOnboardingState(
                        currentUserId,
                        OnboardingPreferences.getOnboardingState(this)
                    )
                }
                refreshQuestHudUi()
                val intent = Intent(this, FamilyTreeActivity::class.java).apply {
                    if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }
        }

        btnShareCode?.setOnClickListener {
            shareOrCopyTreeInviteCode()
        }

        btnDismiss?.setOnClickListener {
            OnboardingPreferences.setQuestDismissed(this, true)
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            if (currentUserId.isNotEmpty()) {
                firestoreHelper.saveOnboardingState(
                    currentUserId,
                    OnboardingPreferences.getOnboardingState(this)
                )
            }
            cardQuestHud.visibility = View.GONE
            KinTraceAlert.show(this, "Clan Quest completed and archived!")
        }

        refreshQuestHudUi()
    }

    private fun refreshQuestHudUi(memberCount: Int? = null) {
        val cardQuestHud = findViewById<View?>(R.id.cardQuestHud) ?: return
        // Legacy Clan Builder Quest HUD is retired in favor of KinAcademy & Quick Start Tour
        cardQuestHud.visibility = View.GONE
        return

        val state = OnboardingPreferences.getOnboardingState(this)

        val hasTree = userOwnedTrees.isNotEmpty() || treeId.isNotEmpty()
        if (hasTree && !state.questRootsCompleted) {
            state.questRootsCompleted = true
            OnboardingPreferences.setMilestoneRoots(this, true)
        } else if (!hasTree && state.questRootsCompleted) {
            state.questRootsCompleted = false
            OnboardingPreferences.setMilestoneRoots(this, false)
        }

        if (memberCount != null) {
            if (memberCount >= 2 && !state.questTwoMembersCompleted) {
                state.questTwoMembersCompleted = true
                OnboardingPreferences.setMilestoneTwoMembers(this, true)
            } else if (memberCount < 2 && state.questTwoMembersCompleted) {
                state.questTwoMembersCompleted = false
                OnboardingPreferences.setMilestoneTwoMembers(this, false)
            }
        }

        val percent = state.getProgressPercentage()

        val tvPercent = findViewById<TextView?>(R.id.tvQuestPercent)
        val progressBar = findViewById<ProgressBar?>(R.id.progressQuestBar)
        val layoutBody = findViewById<View?>(R.id.layoutQuestBody)
        val btnToggleCollapse = findViewById<TextView?>(R.id.btnToggleQuestCollapse)
        val layoutCelebration = findViewById<View?>(R.id.layoutQuestCelebration)

        tvPercent?.text = "$percent%"
        progressBar?.progress = percent

        if (state.isQuestCollapsed) {
            layoutBody?.visibility = View.GONE
            btnToggleCollapse?.text = "▲ Show"
        } else {
            layoutBody?.visibility = View.VISIBLE
            btnToggleCollapse?.text = "▼ Hide"
        }

        // Milestone 1: Roots
        val tvM1Check = findViewById<TextView?>(R.id.tvMilestone1Check)
        val btnM1Action = findViewById<Button?>(R.id.btnQuestRootsAction)
        if (state.questRootsCompleted) {
            tvM1Check?.text = "✓"
            tvM1Check?.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnM1Action?.visibility = View.GONE
        } else {
            tvM1Check?.text = "○"
            tvM1Check?.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            btnM1Action?.visibility = View.VISIBLE
        }

        // Milestone 2: 2 Relatives
        val tvM2Check = findViewById<TextView?>(R.id.tvMilestone2Check)
        val tvM2Title = findViewById<TextView?>(R.id.tvMilestone2Title)
        val btnM2Action = findViewById<Button?>(R.id.btnQuestAddMember)
        val count = memberCount ?: if (state.questTwoMembersCompleted) 2 else 0
        if (state.questTwoMembersCompleted) {
            tvM2Check?.text = "✓"
            tvM2Check?.setTextColor(ContextCompat.getColor(this, R.color.primary))
            tvM2Title?.text = "2. Add 2 Immediate Relatives (2/2)"
            btnM2Action?.visibility = View.GONE
        } else {
            tvM2Check?.text = "○"
            tvM2Check?.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            tvM2Title?.text = "2. Add 2 Immediate Relatives ($count/2)"
            btnM2Action?.visibility = View.VISIBLE
        }

        // Milestone 3: Canvas
        val tvM3Check = findViewById<TextView?>(R.id.tvMilestone3Check)
        val btnM3Action = findViewById<Button?>(R.id.btnQuestOpenCanvas)
        if (state.questCanvasInspected) {
            tvM3Check?.text = "✓"
            tvM3Check?.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnM3Action?.visibility = View.GONE
        } else {
            tvM3Check?.text = "○"
            tvM3Check?.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            btnM3Action?.visibility = View.VISIBLE
        }

        // Milestone 4: Share Code
        val tvM4Check = findViewById<TextView?>(R.id.tvMilestone4Check)
        val btnM4Action = findViewById<Button?>(R.id.btnQuestShareCode)
        if (state.questCodeShared) {
            tvM4Check?.text = "✓"
            tvM4Check?.setTextColor(ContextCompat.getColor(this, R.color.primary))
            btnM4Action?.visibility = View.GONE
        } else {
            tvM4Check?.text = "○"
            tvM4Check?.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            btnM4Action?.visibility = View.VISIBLE
        }

        // 100% Celebration Banner
        if (state.isAllCompleted()) {
            layoutCelebration?.visibility = View.VISIBLE
        } else {
            layoutCelebration?.visibility = View.GONE
        }
    }

    private fun shareOrCopyTreeInviteCode() {
        val currentTree = userOwnedTrees.firstOrNull { it.id == treeId }
            ?: userOwnedTrees.firstOrNull()
        val code = currentTree?.inviteCode.orEmpty()
        if (code.isNotEmpty()) {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Clan Invite Code", code)
            clipboard.setPrimaryClip(clip)

            OnboardingPreferences.setMilestoneCodeShared(this, true)
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            if (currentUserId.isNotEmpty()) {
                firestoreHelper.saveOnboardingState(
                    currentUserId,
                    OnboardingPreferences.getOnboardingState(this)
                )
            }
            refreshQuestHudUi()

            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                .setTitle("Clan Invite Code: $code")
                .setMessage("Your 6-character clan code has been copied to your clipboard!\n\nShare this code with family members so they can join your tree.")
                .setPositiveButton("Share via App...") { _, _ ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Join our KinTrace family tree")
                        putExtra(Intent.EXTRA_TEXT, "Join our family tree on KinTrace using our clan invite code: $code")
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Clan Code"))
                }
                .setNegativeButton("Done", null)
                .show()
        } else {
            KinTraceAlert.show(this, "Create or select a family tree first to share its invite code.")
        }
    }

    private fun updateMaintenanceBannerUi(announcement: com.example.btproject2.models.SystemAnnouncement?) {
        val banner = findViewById<View?>(R.id.cardMaintenanceBanner) ?: return
        val badge = findViewById<TextView?>(R.id.tvNoticeSeverityBadge)
        val headline = findViewById<TextView?>(R.id.tvNoticeHeadline)
        val body = findViewById<TextView?>(R.id.tvNoticeBody)
        val btnDismiss = findViewById<View?>(R.id.btnDismissNoticeBanner)

        val isDismissed = announcement != null && com.example.btproject2.utils.AnnouncementPreferences.isDismissed(
            this,
            announcement.id,
            announcement.createdAt
        )
        val isExpired = announcement?.isExpired ?: false

        if (announcement == null || !announcement.isActive || isExpired || isDismissed || isBannerDismissedSession) {
            banner.visibility = View.GONE
            return
        }

        headline?.text = announcement.title
        body?.text = announcement.message

        when {
            announcement.isLockdown -> {
                badge?.text = "⛔ CRITICAL LOCKDOWN"
                badge?.setTextColor(ContextCompat.getColor(this, R.color.danger))
            }
            announcement.isWarning -> {
                badge?.text = "⚠️ SYSTEM ADVISORY"
                badge?.setTextColor(ContextCompat.getColor(this, R.color.warning))
            }
            else -> {
                badge?.text = "ℹ️ SYSTEM NOTICE"
                badge?.setTextColor(ContextCompat.getColor(this, R.color.info))
            }
        }

        btnDismiss?.setOnClickListener {
            isBannerDismissedSession = true
            com.example.btproject2.utils.AnnouncementPreferences.setDismissed(
                this,
                announcement.id,
                announcement.createdAt
            )
            banner.visibility = View.GONE
            KinTraceAlert.show(this, "Advisory dismissed. View anytime in Notifications (🔔)")
        }

        banner.visibility = View.VISIBLE
    }

    private fun refreshNotificationBadge() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        val ann = currentAnnouncement
        val hasUnreadAdvisory = ann != null && ann.isActive && !ann.isExpired &&
            !com.example.btproject2.utils.AnnouncementPreferences.isAdvisoryReadInNotifications(this, ann.id, ann.createdAt)

        val targetTreeId = treeId.ifEmpty { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this) }
        if (targetTreeId.isEmpty()) {
            viewNotifBadge.visibility = if (hasUnreadAdvisory) View.VISIBLE else View.GONE
            return
        }

        firestoreHelper.getUnreadNotificationCount(targetTreeId, currentUserId,
            onSuccess = { count ->
                viewNotifBadge.visibility = if (count > 0 || hasUnreadAdvisory) View.VISIBLE else View.GONE
            },
            onFailure = {
                viewNotifBadge.visibility = if (hasUnreadAdvisory) View.VISIBLE else View.GONE
            }
        )
    }
}