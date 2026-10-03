package com.example.btproject2.ui.activities

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
import com.example.btproject2.engine.TreeHealthCalculator
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

class HomeActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private val healthCalculator = TreeHealthCalculator()
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

    override fun onStart() {
        super.onStart()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().registerListener(homeSyncListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        reloadDebounceHandler.removeCallbacks(reloadTreeRunnable)
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().unregisterListener(homeSyncListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        tvUserName = findViewById(R.id.tvUserName)
        tvProfileInitial = findViewById(R.id.tvProfileInitial)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val tvBranchCount = findViewById<TextView>(R.id.tvBranchCount)
        val tvRecentCount = findViewById<TextView>(R.id.tvRecentCount)
        val tvHealthScore = findViewById<TextView>(R.id.tvHealthScore)

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

        // ── Theme Toggle (Light / Dark Mode) beside Family Tree Banner ──
        val btnThemeToggle = findViewById<View?>(R.id.btnThemeToggle)
        val tvThemeToggleIcon = findViewById<TextView?>(R.id.tvThemeToggleIcon)

        fun updateThemeToggleDisplay() {
            val isDark = ThemePreferences.isDarkTheme(this)
            tvThemeToggleIcon?.text = if (isDark) "☀️" else "🌙"
            btnThemeToggle?.contentDescription = if (isDark) "Switch to Light Theme" else "Switch to Dark Theme"
        }

        updateThemeToggleDisplay()

        btnThemeToggle?.setOnClickListener {
            val isDark = ThemePreferences.isDarkTheme(this)
            val newMode = if (isDark) {
                ThemePreferences.THEME_LIGHT
            } else {
                ThemePreferences.THEME_DARK
            }
            ThemePreferences.setThemeMode(this, newMode)
            updateThemeToggleDisplay()
        }

        // ── Empty State Action Buttons (Figure 4) ──────────────────
        btnEmptyCreateTree.setOnClickListener {
            startActivity(Intent(this, CreateTreeActivity::class.java))
        }
        btnEmptyJoinTree.setOnClickListener {
            startActivity(Intent(this, JoinTreeActivity::class.java))
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
        val btnLogout = findViewById<TextView>(R.id.btnLogout)

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

        // ── Docked Bottom Navigation Bar (Figure 6) ────────────────
        findViewById<View>(R.id.navHome)?.setOnClickListener {
            resolveAndLoadTree()
        }
        findViewById<View>(R.id.navTree)?.setOnClickListener {
            if (treeId.isNotEmpty() || userOwnedTrees.isNotEmpty()) {
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
            } else {
                Toast.makeText(this, "Please create or join a family tree first.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, CreateTreeActivity::class.java))
            }
        }
        findViewById<View>(R.id.navTrace)?.setOnClickListener {
            val intent = Intent(this, TraceActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.navProfile)?.setOnClickListener {
            showAccountProfileDialog()
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes") { _, _ ->
                    TreePreferences.clear(this)
                    FirestoreHelper.clearCache()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()
                    authHelper.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        setupQuestHud()
        resolveAndLoadTree()
    }

    override fun onResume() {
        super.onResume()

        val tvThemeToggleIcon = findViewById<TextView?>(R.id.tvThemeToggleIcon)
        val isDark = ThemePreferences.isDarkTheme(this)
        tvThemeToggleIcon?.text = if (isDark) "☀️" else "🌙"

        refreshQuestHudUi()
        resolveAndLoadTree()
    }

    private fun resolveAndLoadTree() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (currentUserId.isEmpty()) {
            showNoTreeEmptyState()
            return
        }

        val cachedTreeId = TreePreferences.getActiveTreeId(this)
        val cachedTreeName = TreePreferences.getActiveTreeName(this)

        if (cachedTreeId.isNotEmpty()) {
            this.treeId = cachedTreeId
            layoutStatsHeader.visibility = View.VISIBLE
            findViewById<TextView?>(R.id.tvTreeTitle)?.text = "✎  ${cachedTreeName.ifEmpty { "Family Tree" }}"
            loadStatsForTree(cachedTreeId)
        } else {
            showNoTreeEmptyState()
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
                            showNoTreeEmptyState()
                        }
                    }
                )
            },
            onFailure = {
                if (cachedTreeId.isEmpty()) {
                    showNoTreeEmptyState()
                }
            }
        )

        firestoreHelper.getOnboardingState(currentUserId,
            onSuccess = { cloudState ->
                if (cloudState != null) {
                    val local = OnboardingPreferences.getOnboardingState(this, currentUserId)
                    var changed = false
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

            // Phase 1 Onboarding: Show 3-slide welcome story modal for new user without trees
            WelcomeStoryDialogFragment.showIfNotSeen(this@HomeActivity)

            // In Phase 1 (No trees yet), Milestone 1 (Roots) is NOT completed
            OnboardingPreferences.setMilestoneRoots(this@HomeActivity, false)
            refreshQuestHudUi(0)
        }
    }

    private fun showTreeSelectionDialog() {
        if (userOwnedTrees.isEmpty()) {
            startActivity(Intent(this, CreateTreeActivity::class.java))
            return
        }

        val treeNames = userOwnedTrees.map { it.name }.toTypedArray()
        val currentActiveId = TreePreferences.getActiveTreeId(this)
        val checkedItem = userOwnedTrees.indexOfFirst { it.id == currentActiveId }.let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this)
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
        val tvHealthScore = findViewById<TextView>(R.id.tvHealthScore)

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

                val health = healthCalculator.calculate(activePersons)
                val healthText = "${health.score}% Health"
                findViewById<TextView>(R.id.tvHealthScoreBadge)?.text = healthText
                if (tvHealthScore != null) {
                    tvHealthScore.text = healthText
                } else {
                    tvRecentCount.text = health.score.toString()
                }

                val totalRecords = activePersons.size + activePersons.count { !it.fatherId.isNullOrEmpty() } + activePersons.count { !it.motherId.isNullOrEmpty() }
                findViewById<TextView?>(R.id.tvRecordsCount)?.text = maxOf(activePersons.size, totalRecords).toString()
                findViewById<TextView?>(R.id.tvRecordsDelta)?.text = "+${minOf(activePersons.size, 6)}"
                findViewById<TextView?>(R.id.tvMemberDelta)?.text = "+${minOf(activePersons.size, 2)}"
                findViewById<TextView?>(R.id.tvBranchDelta)?.text = "+1"

                tvInsightsBannerSub.text =
                    "Tree Health: ${health.score}% (${health.label}) · Tap to view breakdown →"

                layoutStatsHeader.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE
                layoutPopulatedState.visibility = View.VISIBLE

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
                tvMemberCount.text = "0"
                findViewById<TextView>(R.id.tvLivingCount)?.text = "0"
                findViewById<TextView?>(R.id.tvGenerationCountLarge)?.text = "0"
                findViewById<TextView?>(R.id.tvGenerationsCount)?.text = "0"
                findViewById<TextView?>(R.id.tvRecordsCount)?.text = "0"
                if (tvHealthScore != null) tvHealthScore.text = "0%"
            }
        )

        // Check unread notifications count for badge
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        firestoreHelper.getUnreadNotificationCount(activeTreeId, currentUserId,
            onSuccess = { count ->
                viewNotifBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            },
            onFailure = {
                viewNotifBadge.visibility = View.GONE
            }
        )

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

            if (activity.targetId.isNotEmpty() && activity.type.uppercase() !in listOf("DELETE", "MEMBER_DELETED")) {
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

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Update Password", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newPass = etNewPassword.text.toString().trim()
                val confirmPass = etConfirmPassword.text.toString().trim()

                if (newPass.length < 6) {
                    tvPasswordError.visibility = View.VISIBLE
                    tvPasswordError.text = "Password must be at least 6 characters"
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
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Create or join a family tree first to add relatives.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Create or join a family tree first to inspect the canvas.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Clan Quest completed and archived!", Toast.LENGTH_SHORT).show()
        }

        refreshQuestHudUi()
    }

    private fun refreshQuestHudUi(memberCount: Int? = null) {
        val cardQuestHud = findViewById<View?>(R.id.cardQuestHud) ?: return
        if (OnboardingPreferences.isQuestDismissed(this)) {
            cardQuestHud.visibility = View.GONE
            return
        }
        cardQuestHud.visibility = View.VISIBLE

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

            AlertDialog.Builder(this)
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
            Toast.makeText(this, "Create or select a family tree first to share its invite code.", Toast.LENGTH_SHORT).show()
        }
    }
}