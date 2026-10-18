package com.example.btproject2.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.utils.KinTraceAlert
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AdminAuditLog
import com.example.btproject2.models.ArchivedMemberRecord
import com.example.btproject2.models.MergeCollisionRecord
import com.example.btproject2.models.Person
import com.example.btproject2.models.ReunionPassRecord
import com.example.btproject2.models.SystemAnnouncement
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * System Admin Portal ("The Central Command Citadel") for KinTrace (FR-11).
 * Provides centralized administrative management, live telemetry, scoped broadcast announcements,
 * 50-member quota approvals, soft-delete node healing, and audit trails.
 */
class AdminPortalActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()

    // Option 3 Minimalist Chronometer Header Views
    private lateinit var barCitadelHeader: View
    private lateinit var tvCitadelAvatar: TextView
    private lateinit var tvCitadelDate: TextView
    private lateinit var tvCitadelGreeting: TextView
    private lateinit var tvCitadelClock: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            val dateFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
            tvCitadelDate.text = dateFmt.format(now).uppercase()
            tvCitadelClock.text = timeFmt.format(now)
            clockHandler.postDelayed(this, 1000L)
        }
    }

    // Navigation Tabs
    private lateinit var tabTelemetryBtn: TextView
    private lateinit var tabBroadcasterBtn: TextView
    private lateinit var tabQuotaBtn: TextView
    private lateinit var tabOpsBtn: TextView

    // Tab Containers
    private lateinit var layoutTelemetryContent: LinearLayout
    private lateinit var layoutBroadcasterContent: LinearLayout
    private lateinit var layoutQuotaContent: LinearLayout
    private lateinit var layoutOpsContent: LinearLayout

    // Broadcaster State & Controls
    private lateinit var etBroadcastTitle: EditText
    private lateinit var etBroadcastMessage: EditText
    private lateinit var btnSeverityInfo: TextView
    private lateinit var btnSeverityWarning: TextView
    private lateinit var btnSeverityLockdown: TextView
    private lateinit var btnScopeAll: TextView
    private lateinit var btnScopeOwners: TextView
    private lateinit var btnScopeCapped: TextView
    private lateinit var btnRetract2h: TextView
    private lateinit var btnRetract6h: TextView
    private lateinit var btnRetract24h: TextView

    // Broadcast Preview
    private lateinit var tvPreviewBadge: TextView
    private lateinit var tvPreviewTitle: TextView
    private lateinit var tvPreviewMessage: TextView
    private lateinit var tvPreviewMeta: TextView
    private lateinit var btnPublishBroadcast: Button
    private lateinit var btnRetractBroadcast: Button
    private lateinit var tvBroadcastStatus: TextView

    // Telemetry & Pruner Controls
    private lateinit var tvTotalAccounts: TextView
    private lateinit var tvTotalClans: TextView
    private lateinit var tvTotalNodes: TextView
    private lateinit var tvSystemStatus: TextView
    private lateinit var tvTelemetrySyncTime: TextView
    private lateinit var btnRefreshTelemetry: TextView
    private lateinit var btnPruneGhostTrees: Button
    private lateinit var tvPruneStatus: TextView
    private lateinit var tvGhostTreeList: TextView

    // Quota & Passes
    private lateinit var layoutQuotaQueueList: LinearLayout
    private lateinit var tvEmptyQuotaQueue: TextView
    private lateinit var btnRefreshQuotaQueue: TextView
    private lateinit var btnApproveMalone: Button
    private lateinit var tvMaloneStatus: TextView
    private lateinit var btnApproveSantos: Button
    private lateinit var tvSantosStatus: TextView
    private lateinit var btnGenerateReunionPass: Button
    private lateinit var layoutGeneratedPass: LinearLayout
    private lateinit var tvGeneratedPassCode: TextView
    private lateinit var btnCopyPassCode: Button

    // Ops Desk Dynamic Containers
    private lateinit var cardDeletedRecovery: View
    private lateinit var layoutDeletedMembersList: LinearLayout
    private lateinit var tvNoDeletedMembers: TextView
    private lateinit var cardMergeCollision: View
    private lateinit var layoutCollisionDocketsList: LinearLayout
    private lateinit var tvNoCollisions: TextView
    private lateinit var btnSeedDemoClan: Button
    private lateinit var tvSeedStatus: TextView
    private lateinit var btnFlushDemoState: Button
    private lateinit var tvFlushStatus: TextView

    // OTA Release Desk Controls
    private lateinit var tvCurrentPublishedVersion: TextView
    private lateinit var tvVersionNotesPreview: TextView
    private lateinit var btnPublishVersionIncrement: Button
    private lateinit var tvPublishVersionStatus: TextView

    // Audit Trail Container
    private lateinit var layoutAuditList: LinearLayout

    // Internal In-Memory State
    private var selectedSeverity = SystemAnnouncement.SEVERITY_WARNING
    private var selectedScope = SystemAnnouncement.SCOPE_ALL
    private var selectedRetractHours = 2
    private val auditLogs = mutableListOf<AdminAuditLog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentEmail = authHelper.getCurrentUser()?.email
        if (!com.example.btproject2.models.DemoAccountRepository.isAdminEmail(currentEmail)) {
            KinTraceAlert.warning(this, "Access Denied", "Only System Administrators can access the Central Command Citadel.")
            finish()
            return
        }

        setContentView(R.layout.activity_admin_portal)

        initViews()
        setupNavigationTabs()
        setupBroadcasterInputs()
        setupCapacityPruner()
        setupQuotaAndPasses()
        setupOpsDesk()
        seedInitialAuditLogs()
        renderAuditLogs()
        loadLiveTelemetry()

        val openTab = intent.getIntExtra("OPEN_TAB", -1)
        if (openTab >= 0) {
            switchTab(openTab)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openTab = intent.getIntExtra("OPEN_TAB", -1)
        if (openTab >= 0) {
            switchTab(openTab)
            val highlightMemberId = intent.getStringExtra("HIGHLIGHT_MEMBER_ID")
            val highlightCollisionId = intent.getStringExtra("HIGHLIGHT_COLLISION_ID")
            loadArchivedDeletedMembers(highlightMemberId)
            loadMergeCollisions(highlightCollisionId)
        }
    }

    override fun onResume() {
        super.onResume()
        populateHeaderAdminProfile()
        clockHandler.removeCallbacks(clockRunnable)
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun populateHeaderAdminProfile() {
        val user = authHelper.getCurrentUser()
        val displayName = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Super Admin"
        tvCitadelAvatar.text = displayName.firstOrNull()?.uppercase() ?: "A"
        tvCitadelGreeting.text = "Hi, $displayName"
    }

    private fun initViews() {
        barCitadelHeader = findViewById(R.id.barCitadelHeader)
        tvCitadelAvatar = findViewById(R.id.tvCitadelAvatar)
        tvCitadelDate = findViewById(R.id.tvCitadelDate)
        tvCitadelGreeting = findViewById(R.id.tvCitadelGreeting)
        tvCitadelClock = findViewById(R.id.tvCitadelClock)

        barCitadelHeader.setOnClickListener {
            val sheet = com.example.btproject2.ui.dialogs.SuperAdminProfileBottomSheet.newInstance()
            sheet.onProfileUpdated = { newName ->
                tvCitadelAvatar.text = newName.firstOrNull()?.uppercase() ?: "A"
                tvCitadelGreeting.text = "Hi, $newName"
            }
            sheet.show(supportFragmentManager, com.example.btproject2.ui.dialogs.SuperAdminProfileBottomSheet.TAG)
        }

        populateHeaderAdminProfile()


        // Tabs
        tabTelemetryBtn = findViewById(R.id.tabTelemetryBtn)
        tabBroadcasterBtn = findViewById(R.id.tabBroadcasterBtn)
        tabQuotaBtn = findViewById(R.id.tabQuotaBtn)
        tabOpsBtn = findViewById(R.id.tabOpsBtn)

        // Containers
        layoutTelemetryContent = findViewById(R.id.layoutTelemetryContent)
        layoutBroadcasterContent = findViewById(R.id.layoutBroadcasterContent)
        layoutQuotaContent = findViewById(R.id.layoutQuotaContent)
        layoutOpsContent = findViewById(R.id.layoutOpsContent)

        // Broadcaster
        etBroadcastTitle = findViewById(R.id.etBroadcastTitle)
        etBroadcastMessage = findViewById(R.id.etBroadcastMessage)
        btnSeverityInfo = findViewById(R.id.btnSeverityInfo)
        btnSeverityWarning = findViewById(R.id.btnSeverityWarning)
        btnSeverityLockdown = findViewById(R.id.btnSeverityLockdown)
        btnScopeAll = findViewById(R.id.btnScopeAll)
        btnScopeOwners = findViewById(R.id.btnScopeOwners)
        btnScopeCapped = findViewById(R.id.btnScopeCapped)
        btnRetract2h = findViewById(R.id.btnRetract2h)
        btnRetract6h = findViewById(R.id.btnRetract6h)
        btnRetract24h = findViewById(R.id.btnRetract24h)
        tvPreviewBadge = findViewById(R.id.tvPreviewBadge)
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle)
        tvPreviewMessage = findViewById(R.id.tvPreviewMessage)
        tvPreviewMeta = findViewById(R.id.tvPreviewMeta)
        btnPublishBroadcast = findViewById(R.id.btnPublishBroadcast)
        btnRetractBroadcast = findViewById(R.id.btnRetractBroadcast)
        tvBroadcastStatus = findViewById(R.id.tvBroadcastStatus)

        // Telemetry & Pruner
        tvTotalAccounts = findViewById(R.id.tvTotalAccounts)
        tvTotalClans = findViewById(R.id.tvTotalClans)
        tvTotalNodes = findViewById(R.id.tvTotalNodes)
        tvSystemStatus = findViewById(R.id.tvSystemStatus)
        tvTelemetrySyncTime = findViewById(R.id.tvTelemetrySyncTime)
        btnRefreshTelemetry = findViewById(R.id.btnRefreshTelemetry)
        btnRefreshTelemetry.setOnClickListener { loadLiveTelemetry() }
        btnPruneGhostTrees = findViewById(R.id.btnPruneGhostTrees)
        tvPruneStatus = findViewById(R.id.tvPruneStatus)
        tvGhostTreeList = findViewById(R.id.tvGhostTreeList)

        // Quota
        layoutQuotaQueueList = findViewById(R.id.layoutQuotaQueueList)
        tvEmptyQuotaQueue = findViewById(R.id.tvEmptyQuotaQueue)
        btnRefreshQuotaQueue = findViewById(R.id.btnRefreshQuotaQueue)
        btnApproveMalone = findViewById(R.id.btnApproveMalone)
        tvMaloneStatus = findViewById(R.id.tvMaloneStatus)
        btnApproveSantos = findViewById(R.id.btnApproveSantos)
        tvSantosStatus = findViewById(R.id.tvSantosStatus)
        btnGenerateReunionPass = findViewById(R.id.btnGenerateReunionPass)
        layoutGeneratedPass = findViewById(R.id.layoutGeneratedPass)
        tvGeneratedPassCode = findViewById(R.id.tvGeneratedPassCode)
        btnCopyPassCode = findViewById(R.id.btnCopyPassCode)

        // Ops Desk
        cardDeletedRecovery = findViewById(R.id.cardDeletedRecovery)
        layoutDeletedMembersList = findViewById(R.id.layoutDeletedMembersList)
        tvNoDeletedMembers = findViewById(R.id.tvNoDeletedMembers)
        cardMergeCollision = findViewById(R.id.cardMergeCollision)
        layoutCollisionDocketsList = findViewById(R.id.layoutCollisionDocketsList)
        tvNoCollisions = findViewById(R.id.tvNoCollisions)
        btnSeedDemoClan = findViewById(R.id.btnSeedDemoClan)
        tvSeedStatus = findViewById(R.id.tvSeedStatus)
        btnFlushDemoState = findViewById(R.id.btnFlushDemoState)
        tvFlushStatus = findViewById(R.id.tvFlushStatus)

        // OTA Release Desk
        tvCurrentPublishedVersion = findViewById(R.id.tvCurrentPublishedVersion)
        tvVersionNotesPreview = findViewById(R.id.tvVersionNotesPreview)
        btnPublishVersionIncrement = findViewById(R.id.btnPublishVersionIncrement)
        tvPublishVersionStatus = findViewById(R.id.tvPublishVersionStatus)

        // Audit Trail
        layoutAuditList = findViewById(R.id.layoutAuditList)
    }

    private fun setupNavigationTabs() {
        tabTelemetryBtn.setOnClickListener { switchTab(0) }
        tabBroadcasterBtn.setOnClickListener { switchTab(1) }
        tabQuotaBtn.setOnClickListener { switchTab(2) }
        tabOpsBtn.setOnClickListener { switchTab(3) }
    }

    private fun switchTab(tabIndex: Int) {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_inactive)
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_primary)

        tabTelemetryBtn.background = if (tabIndex == 0) activeBg else inactiveBg
        tabTelemetryBtn.setTextColor(if (tabIndex == 0) activeColor else inactiveColor)
        layoutTelemetryContent.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE

        tabBroadcasterBtn.background = if (tabIndex == 1) activeBg else inactiveBg
        tabBroadcasterBtn.setTextColor(if (tabIndex == 1) activeColor else inactiveColor)
        layoutBroadcasterContent.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE

        tabQuotaBtn.background = if (tabIndex == 2) activeBg else inactiveBg
        tabQuotaBtn.setTextColor(if (tabIndex == 2) activeColor else inactiveColor)
        layoutQuotaContent.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        if (tabIndex == 2) {
            loadDynamicQuotaQueue()
        }

        tabOpsBtn.background = if (tabIndex == 3) activeBg else inactiveBg
        tabOpsBtn.setTextColor(if (tabIndex == 3) activeColor else inactiveColor)
        layoutOpsContent.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
    }

    private fun setupBroadcasterInputs() {
        // Real-time title & message sync with preview
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvPreviewTitle.text = etBroadcastTitle.text.toString().ifBlank { "System Notice Headline" }
                tvPreviewMessage.text = etBroadcastMessage.text.toString().ifBlank { "Advisory description..." }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etBroadcastTitle.addTextChangedListener(watcher)
        etBroadcastMessage.addTextChangedListener(watcher)

        // Severity toggles
        btnSeverityInfo.setOnClickListener { setSeverity(SystemAnnouncement.SEVERITY_INFO) }
        btnSeverityWarning.setOnClickListener { setSeverity(SystemAnnouncement.SEVERITY_WARNING) }
        btnSeverityLockdown.setOnClickListener { setSeverity(SystemAnnouncement.SEVERITY_LOCKDOWN) }

        // Audience Scope toggles
        btnScopeAll.setOnClickListener { setScope(SystemAnnouncement.SCOPE_ALL) }
        btnScopeOwners.setOnClickListener { setScope(SystemAnnouncement.SCOPE_OWNERS) }
        btnScopeCapped.setOnClickListener { setScope(SystemAnnouncement.SCOPE_CAPPED) }

        // Retract Hour toggles
        btnRetract2h.setOnClickListener { setRetractHours(2) }
        btnRetract6h.setOnClickListener { setRetractHours(6) }
        btnRetract24h.setOnClickListener { setRetractHours(24) }

        // Action Buttons
        btnPublishBroadcast.setOnClickListener { dispatchBroadcast() }
        btnRetractBroadcast.setOnClickListener { retractBroadcast() }
    }

    private fun setSeverity(severity: String) {
        selectedSeverity = severity
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_inactive)
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_primary)

        btnSeverityInfo.background = if (severity == SystemAnnouncement.SEVERITY_INFO) activeBg else inactiveBg
        btnSeverityInfo.setTextColor(if (severity == SystemAnnouncement.SEVERITY_INFO) activeColor else inactiveColor)

        btnSeverityWarning.background = if (severity == SystemAnnouncement.SEVERITY_WARNING) activeBg else inactiveBg
        btnSeverityWarning.setTextColor(if (severity == SystemAnnouncement.SEVERITY_WARNING) activeColor else inactiveColor)

        btnSeverityLockdown.background = if (severity == SystemAnnouncement.SEVERITY_LOCKDOWN) activeBg else inactiveBg
        btnSeverityLockdown.setTextColor(if (severity == SystemAnnouncement.SEVERITY_LOCKDOWN) activeColor else inactiveColor)

        when (severity) {
            SystemAnnouncement.SEVERITY_INFO -> {
                tvPreviewBadge.text = "ℹ️ SYSTEM NOTICE"
                tvPreviewBadge.setTextColor(ContextCompat.getColor(this, R.color.info))
            }
            SystemAnnouncement.SEVERITY_WARNING -> {
                tvPreviewBadge.text = "⚠️ SYSTEM ADVISORY"
                tvPreviewBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
            }
            SystemAnnouncement.SEVERITY_LOCKDOWN -> {
                tvPreviewBadge.text = "⛔ CRITICAL LOCKDOWN"
                tvPreviewBadge.setTextColor(ContextCompat.getColor(this, R.color.danger))
            }
        }
    }

    private fun setScope(scope: String) {
        selectedScope = scope
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_inactive)
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_primary)

        btnScopeAll.background = if (scope == SystemAnnouncement.SCOPE_ALL) activeBg else inactiveBg
        btnScopeAll.setTextColor(if (scope == SystemAnnouncement.SCOPE_ALL) activeColor else inactiveColor)

        btnScopeOwners.background = if (scope == SystemAnnouncement.SCOPE_OWNERS) activeBg else inactiveBg
        btnScopeOwners.setTextColor(if (scope == SystemAnnouncement.SCOPE_OWNERS) activeColor else inactiveColor)

        btnScopeCapped.background = if (scope == SystemAnnouncement.SCOPE_CAPPED) activeBg else inactiveBg
        btnScopeCapped.setTextColor(if (scope == SystemAnnouncement.SCOPE_CAPPED) activeColor else inactiveColor)

        updatePreviewMeta()
    }

    private fun setRetractHours(hours: Int) {
        selectedRetractHours = hours
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_admin_tab_inactive)
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_primary)

        btnRetract2h.background = if (hours == 2) activeBg else inactiveBg
        btnRetract2h.setTextColor(if (hours == 2) activeColor else inactiveColor)

        btnRetract6h.background = if (hours == 6) activeBg else inactiveBg
        btnRetract6h.setTextColor(if (hours == 6) activeColor else inactiveColor)

        btnRetract24h.background = if (hours == 24) activeBg else inactiveBg
        btnRetract24h.setTextColor(if (hours == 24) activeColor else inactiveColor)

        updatePreviewMeta()
    }

    private fun updatePreviewMeta() {
        val scopeLabel = when (selectedScope) {
            SystemAnnouncement.SCOPE_OWNERS -> "Clan Owners Only"
            SystemAnnouncement.SCOPE_CAPPED -> "Capped Clans (50/50)"
            else -> "All Clans"
        }
        tvPreviewMeta.text = "Retracts in ${selectedRetractHours}h • $scopeLabel"
    }

    private fun dispatchBroadcast() {
        val title = etBroadcastTitle.text.toString().trim()
        val message = etBroadcastMessage.text.toString().trim()

        if (title.isBlank() || message.isBlank()) {
            KinTraceAlert.warning(this, "Validation Notice", "Please enter both a title and message")
            return
        }

        val announcement = SystemAnnouncement(
            id = "active_broadcast",
            title = title,
            message = message,
            severity = selectedSeverity,
            audienceScope = selectedScope,
            timeWindow = "Expires in ${selectedRetractHours}h",
            autoRetractHours = selectedRetractHours,
            isActive = true,
            createdBy = "admin@kintrace.ph"
        )

        firestoreHelper.publishSystemAnnouncement(
            announcement = announcement,
            onSuccess = {
                tvBroadcastStatus.text = "✅ System Broadcast published live to Firestore."
                tvBroadcastStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                recordAudit("BROADCAST_PUBLISHED", "active_broadcast", "Dispatched: '$title' ($selectedSeverity to $selectedScope)")
                KinTraceAlert.telemetry(this, "Broadcast Published", "Broadcast published live to all devices!", "BROADCAST")
            },
            onFailure = { err ->
                tvBroadcastStatus.text = "⚠️ Offline: Local preview saved (${err.message})"
                tvBroadcastStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                recordAudit("BROADCAST_LOCAL_SIM", "active_broadcast", "Local simulation: '$title'")
            }
        )
    }

    private fun retractBroadcast() {
        firestoreHelper.retractSystemAnnouncement(
            onSuccess = {
                tvBroadcastStatus.text = "🛑 Active broadcast retracted from all devices."
                tvBroadcastStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                recordAudit("BROADCAST_RETRACTED", "active_broadcast", "Notice retracted by admin")
                KinTraceAlert.telemetry(this, "Broadcast Retracted", "Live alert removed from client feeds", "RETRACTED")
            },
            onFailure = {
                tvBroadcastStatus.text = "🛑 Broadcast retracted locally."
                recordAudit("BROADCAST_RETRACTED", "active_broadcast", "Local retraction simulated")
            }
        )
    }

    private fun loadLiveTelemetry() {
        tvTelemetrySyncTime.text = "🔄 Querying Firestore..."
        tvTelemetrySyncTime.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        firestoreHelper.fetchLivePlatformTelemetry { telemetry ->
            runOnUiThread {
                tvTotalAccounts.text = "${telemetry.totalAccounts} Users"
                tvTotalClans.text = "${telemetry.totalClans} Clans"
                tvTotalNodes.text = "${telemetry.totalNodes} Members"
                if (telemetry.isOnline) {
                    tvSystemStatus.text = "Online & Synced"
                    tvSystemStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    tvTelemetrySyncTime.text = "🟢 Live sync at $timeStr"
                    tvTelemetrySyncTime.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                } else {
                    tvSystemStatus.text = "Offline Mode"
                    tvSystemStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                    tvTelemetrySyncTime.text = "⚠️ Firestore connection offline"
                    tvTelemetrySyncTime.setTextColor(ContextCompat.getColor(this, R.color.warning))
                }
            }
        }
    }

    private fun setupCapacityPruner() {
        btnPruneGhostTrees.setOnClickListener {
            btnPruneGhostTrees.isEnabled = false
            tvPruneStatus.text = "⏳ Scanning Firestore for unpopulated ghost trees..."
            firestoreHelper.pruneDormantTrees(
                onSuccess = { count, names ->
                    runOnUiThread {
                        if (count > 0) {
                            val listSummary = names.joinToString(", ")
                            tvGhostTreeList.text = "✅ Purged $count dormant / unpopulated tree record(s): $listSummary.\nReclaimed capacity for active clans."
                            tvPruneStatus.text = "✅ $count Inactive Tree(s) Purged • Database Optimized."
                        } else {
                            tvGhostTreeList.text = "✅ Audit complete: 0 unpopulated ghost trees found. All registered clan trees contain active members."
                            tvPruneStatus.text = "✅ 0 Dormant Trees • All Active Trees Verified."
                        }
                        tvPruneStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        recordAudit("INACTIVE_TREES_PURGED", "tree_catalog", "Pruned $count dormant tree(s): ${names.ifEmpty { listOf("None") }.joinToString()}")
                        loadLiveTelemetry()
                        KinTraceAlert.security(this, "Audit Purge Complete", "Pruned $count unpopulated tree(s) from Firestore.", "PURGED")
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        btnPruneGhostTrees.isEnabled = true
                        tvPruneStatus.text = "⚠️ Purge failed: ${err.message}"
                        tvPruneStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                    }
                }
            )
        }
    }

    private fun setupQuotaAndPasses() {
        btnRefreshQuotaQueue.setOnClickListener {
            loadDynamicQuotaQueue()
        }
        loadDynamicQuotaQueue()

        btnApproveMalone.setOnClickListener {
            btnApproveMalone.isEnabled = false
            val activeTreeId = com.example.btproject2.utils.TreePreferences.getActiveTreeId(this).ifBlank { "tree_malone" }
            val activeTreeName = com.example.btproject2.utils.TreePreferences.getActiveTreeName(this).ifBlank { "Malone Clan" }
            firestoreHelper.expandTreeMemberQuota(
                treeId = activeTreeId,
                treeName = activeTreeName,
                newQuota = 200,
                onSuccess = {
                    runOnUiThread {
                        tvMaloneStatus.text = "✅ Expansion Approved: Cap elevated from 50 to 200 members in Firestore."
                        tvMaloneStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        recordAudit("CLAN_CAP_EXPANDED", activeTreeId, "$activeTreeName quota elevated to 200 members")
                        KinTraceAlert.security(this, "Expansion Approved", "$activeTreeName expansion persisted (200 cap)!", "APPROVED")
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        btnApproveMalone.isEnabled = true
                        tvMaloneStatus.text = "⚠️ Failed to update quota: ${err.message}"
                    }
                }
            )
        }

        btnApproveSantos.setOnClickListener {
            btnApproveSantos.isEnabled = false
            firestoreHelper.expandTreeMemberQuota(
                treeId = "tree_santos",
                treeName = "Santos-Reyes Heritage",
                newQuota = 200,
                onSuccess = {
                    runOnUiThread {
                        tvSantosStatus.text = "✅ Proactive Expansion Approved: Cap elevated to 200 members in Firestore."
                        tvSantosStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        recordAudit("CLAN_CAP_EXPANDED", "tree_santos", "Santos-Reyes Heritage quota elevated to 200 members")
                        KinTraceAlert.security(this, "Expansion Approved", "Santos-Reyes proactive expansion persisted!", "APPROVED")
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        btnApproveSantos.isEnabled = true
                        tvSantosStatus.text = "⚠️ Failed to update quota: ${err.message}"
                    }
                }
            )
        }

        btnGenerateReunionPass.setOnClickListener {
            val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val passCode = (1..6).map { charset.random() }.joinToString("")
            layoutGeneratedPass.visibility = View.VISIBLE
            tvGeneratedPassCode.text = passCode

            val activeTreeId = com.example.btproject2.utils.TreePreferences.getActiveTreeId(this).ifBlank { "tree_malone" }
            val activeTreeName = com.example.btproject2.utils.TreePreferences.getActiveTreeName(this).ifBlank { "Malone Clan Reunion" }
            val currentUserId = authHelper.getCurrentUserId().orEmpty().ifBlank { "system_admin" }

            // 1. Register as a valid 6-character InviteCodeRecord so JoinTreeActivity accepts it
            val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
            val inviteRecord = com.example.btproject2.models.InviteCodeRecord(
                code = passCode,
                treeId = activeTreeId,
                treeName = activeTreeName,
                createdBy = currentUserId,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + thirtyDaysMs,
                usageLimit = 0,
                usageCount = 0,
                status = com.example.btproject2.models.InviteCodeRecord.STATUS_ACTIVE
            )
            firestoreHelper.saveInviteCodeRecord(inviteRecord)
            firestoreHelper.updateTreeInviteCode(activeTreeId, passCode, activeTreeName, currentUserId)

            // 2. Also persist the ReunionPassRecord for Admin Citadel tracking
            val passRecord = ReunionPassRecord(
                code = passCode,
                treeId = activeTreeId,
                treeName = activeTreeName,
                validDays = 30,
                generatedBy = authHelper.getCurrentUser()?.email ?: "admin@kintrace.ph"
            )

            firestoreHelper.saveReunionPass(
                pass = passRecord,
                onSuccess = {
                    recordAudit("REUNION_PASS_GENERATED", passCode, "Generated 6-char 30-day reunion invite code ($passCode) for $activeTreeName")
                },
                onFailure = {
                    recordAudit("REUNION_PASS_GENERATED", passCode, "Generated 6-char 30-day reunion invite code (offline)")
                }
            )

            KinTraceAlert.clipboard(this, "Pass Generated", "6-Char Join Code generated: $passCode", "GENERATED")
        }

        btnCopyPassCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Reunion Pass Code", tvGeneratedPassCode.text.toString())
            clipboard.setPrimaryClip(clip)
            KinTraceAlert.clipboard(this, "Code Copied", "Copied ${tvGeneratedPassCode.text} to clipboard!")
        }
    }

    private fun loadDynamicQuotaQueue() {
        tvEmptyQuotaQueue.visibility = View.VISIBLE
        tvEmptyQuotaQueue.text = "🔄 Scanning Firestore for clans at or near their member cap..."
        tvEmptyQuotaQueue.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        layoutQuotaQueueList.removeAllViews()

        firestoreHelper.fetchQuotaQueueTrees(
            includeAllForInspection = false,
            onSuccess = { statuses ->
                runOnUiThread {
                layoutQuotaQueueList.removeAllViews()
                if (statuses.isEmpty()) {
                    tvEmptyQuotaQueue.visibility = View.VISIBLE
                    tvEmptyQuotaQueue.text = "✅ All active clans are currently within their member quota limits. Any clan reaching ≥90% capacity or requesting a tier upgrade will appear here automatically."
                    tvEmptyQuotaQueue.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                    return@runOnUiThread
                }

                tvEmptyQuotaQueue.visibility = View.GONE
                val inflater = android.view.LayoutInflater.from(this)

                for (status in statuses) {
                    val cardView = inflater.inflate(R.layout.item_quota_queue_card, layoutQuotaQueueList, false)
                    val tvTreeName = cardView.findViewById<TextView>(R.id.tvQuotaTreeName)
                    val tvBadge = cardView.findViewById<TextView>(R.id.tvQuotaStatusBadge)
                    val tvMeta = cardView.findViewById<TextView>(R.id.tvQuotaOwnerMeta)
                    val btnApprove = cardView.findViewById<Button>(R.id.btnApproveQuotaExpansion)
                    val tvActionStatus = cardView.findViewById<TextView>(R.id.tvQuotaActionStatus)

                    val tree = status.tree
                    val nextCap = status.nextQuota
                    val delta = (nextCap - status.quota).coerceAtLeast(150)

                    tvTreeName.text = tree.name.ifBlank { "Unnamed Clan" }
                    tvBadge.text = status.badgeLabel
                    tvMeta.text = status.metaSummary

                    if (status.isCapped || tree.quotaExpansionRequested) {
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
                        btnApprove.text = "⚡ Approve Clan Expansion (+$delta Cap → $nextCap)"
                        btnApprove.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.primary)
                        )
                        btnApprove.setTextColor(ContextCompat.getColor(this, R.color.white))
                        tvActionStatus.text = if (tree.quotaExpansionRequested) {
                            "🔔 Explicit expansion request submitted by owner (${status.liveMemberCount}/${status.quota})."
                        } else {
                            "Pending admin review (${status.liveMemberCount}/${status.quota} hard cap reached)."
                        }
                    } else {
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        btnApprove.text = "⚡ Approve Proactive Expansion (→ $nextCap Cap)"
                        btnApprove.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.forest_card)
                        )
                        btnApprove.setTextColor(ContextCompat.getColor(this, R.color.primary))
                        tvActionStatus.text = "Proactive upgrade available (${status.liveMemberCount}/${status.quota})."
                    }

                    btnApprove.setOnClickListener {
                        btnApprove.isEnabled = false
                        tvActionStatus.text = "⏳ Persisting $nextCap-member quota to Firestore..."
                        firestoreHelper.expandTreeMemberQuota(
                            treeId = tree.id,
                            treeName = tree.name,
                            newQuota = nextCap,
                            onSuccess = {
                                runOnUiThread {
                                    tvBadge.text = "✅ ${status.liveMemberCount}/$nextCap Active"
                                    tvBadge.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                    tvActionStatus.text = "✅ Expansion Approved: Cap elevated from ${status.quota} to $nextCap members in Firestore."
                                    tvActionStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                    btnApprove.text = "✓ Quota Expanded ($nextCap Cap)"
                                    recordAudit(
                                        "CLAN_CAP_EXPANDED",
                                        tree.id,
                                        "${tree.name} (${status.ownerEmail.ifBlank { tree.ownerId }}) quota elevated from ${status.quota} to $nextCap members"
                                    )
                                    KinTraceAlert.security(
                                        this,
                                        "Expansion Approved",
                                        "${tree.name} expansion persisted ($nextCap member cap)!",
                                        "APPROVED"
                                    )
                                }
                            },
                            onFailure = { err ->
                                runOnUiThread {
                                    btnApprove.isEnabled = true
                                    tvActionStatus.text = "⚠️ Failed to update quota: ${err.message}"
                                    tvActionStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                                }
                            }
                        )
                    }

                    layoutQuotaQueueList.addView(cardView)
                }
            }
            },
            onFailure = { err ->
                runOnUiThread {
                    tvEmptyQuotaQueue.visibility = View.VISIBLE
                    tvEmptyQuotaQueue.text = "⚠️ Failed to load quota queue: ${err.message}"
                    tvEmptyQuotaQueue.setTextColor(ContextCompat.getColor(this, R.color.warning))
                }
            }
        )
    }

    private fun setupOpsDesk() {
        val highlightMemberId = intent?.getStringExtra("HIGHLIGHT_MEMBER_ID")
        val highlightCollisionId = intent?.getStringExtra("HIGHLIGHT_COLLISION_ID")

        loadArchivedDeletedMembers(highlightMemberId)
        loadMergeCollisions(highlightCollisionId)

        btnSeedDemoClan.setOnClickListener {
            btnSeedDemoClan.isEnabled = false
            tvSeedStatus.text = "⏳ Seeding isolated [DEMO] Heritage Sandbox Clan to Firestore..."
            firestoreHelper.seedDemoSandboxClan(
                onSuccess = { memberCount ->
                    runOnUiThread {
                        btnSeedDemoClan.isEnabled = true
                        tvSeedStatus.text = "✅ 4-Generation Demo Sandbox Clan ($memberCount Members) seeded in Firestore."
                        tvSeedStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        recordAudit("DEMO_SANDBOX_SEEDED", "tree_demo_sandbox", "Seeded $memberCount sample members into isolated [DEMO] Heritage Sandbox Clan")
                        loadLiveTelemetry()
                        KinTraceAlert.telemetry(this, "Demo Clan Seeded", "$memberCount sample members seeded into isolated Demo Tree!", "SEEDED")
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        btnSeedDemoClan.isEnabled = true
                        tvSeedStatus.text = "⚠️ Failed to seed demo clan: ${err.message}"
                    }
                }
            )
        }

        btnFlushDemoState.setOnClickListener {
            btnFlushDemoState.isEnabled = false
            tvFlushStatus.text = "⏳ Flushing demo sandbox & legacy test records from Firestore..."
            firestoreHelper.flushDemoSandboxClan(
                onSuccess = { removedCount ->
                    runOnUiThread {
                        btnFlushDemoState.isEnabled = true
                        tvFlushStatus.text = "✅ Sandbox flushed ($removedCount demo member records & legacy seeds removed)."
                        tvFlushStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        recordAudit("DEMO_SANDBOX_FLUSHED", "tree_demo_sandbox", "Flushed demo sandbox ($removedCount nodes) and legacy test records")
                        loadArchivedDeletedMembers(null)
                        loadMergeCollisions(null)
                        loadLiveTelemetry()
                        KinTraceAlert.telemetry(this, "Sandbox Flushed", "Demo sandbox and legacy test records purged from Firestore.", "RESET")
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        btnFlushDemoState.isEnabled = true
                        tvFlushStatus.text = "⚠️ Failed to flush demo state: ${err.message}"
                    }
                }
            )
        }

        // OTA Release Desk Logic (Zero-Cost GitHub Releases Integration)
        val etReleaseVersionName = findViewById<EditText>(R.id.etReleaseVersionName)
        val etReleaseVersionCode = findViewById<EditText>(R.id.etReleaseVersionCode)
        val etReleaseApkUrl = findViewById<EditText>(R.id.etReleaseApkUrl)
        val etReleaseNotes = findViewById<EditText>(R.id.etReleaseNotes)

        com.example.btproject2.utils.InAppUpdateManager.syncCurrentReleaseToFirestore {
            firestoreHelper.fetchLatestAppVersion(
                onSuccess = { config ->
                    if (config != null) {
                        tvCurrentPublishedVersion.text = "Active Version: ${config.versionName} (Code ${config.versionCode})"
                        if (config.releaseNotes.isNotBlank()) {
                            tvVersionNotesPreview.text = config.releaseNotes.lines().firstOrNull() ?: "Active release"
                        }
                        if (etReleaseVersionCode.text.isBlank()) {
                            etReleaseVersionCode.setText((config.versionCode + 1).toString())
                        }
                        val nextVersionName = if (etReleaseVersionName.text.isBlank()) {
                            val currentMajorMinor = config.versionName.removePrefix("v").split(".")
                            val computed = if (currentMajorMinor.size >= 2) {
                                val minor = currentMajorMinor[1].toIntOrNull() ?: 2
                                "v${currentMajorMinor[0]}.${minor + 1}.0"
                            } else "v1.3.0"
                            etReleaseVersionName.setText(computed)
                            computed
                        } else {
                            etReleaseVersionName.text.toString().trim()
                        }
                        if (etReleaseNotes.text.isBlank()) {
                            etReleaseNotes.setText("• Added new lineage features & UI enhancements\n• Zero-Cost GitHub Releases OTA auto-installer\n• Adaptive Dark & Light Mode semantic palette")
                        }
                        if (etReleaseApkUrl.text.isBlank()) {
                            val defaultUrl = config.apkDownloadUrl.ifBlank {
                                com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl(nextVersionName)
                            }
                            etReleaseApkUrl.setText(defaultUrl)
                        }
                    }
                }
            )
        }

        btnPublishVersionIncrement.setOnClickListener {
            val vName = etReleaseVersionName.text.toString().trim()
            val vCodeStr = etReleaseVersionCode.text.toString().trim()
            val vNotes = etReleaseNotes.text.toString().trim()
            val rawUrl = etReleaseApkUrl.text.toString().trim()

            if (vName.isBlank()) {
                etReleaseVersionName.error = "Version name required"
                return@setOnClickListener
            }
            val vCode = vCodeStr.toLongOrNull() ?: 3L
            val resolvedUrl = if (vCode >= 3L && rawUrl.isBlank()) {
                com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl(vName)
            } else {
                rawUrl
            }

            val nextConfig = com.example.btproject2.models.AppVersionConfig(
                versionCode = vCode,
                versionName = vName,
                releaseNotes = if (vNotes.isNotBlank()) vNotes else "• System increment $vName",
                apkDownloadUrl = resolvedUrl,
                isMandatory = false,
                publishedBy = authHelper.getCurrentUser()?.email ?: "admin@kintrace.ph"
            )

            tvPublishVersionStatus.text = "⏳ Publishing $vName (Code $vCode) to Firestore..."
            btnPublishVersionIncrement.isEnabled = false

            firestoreHelper.publishAppVersion(
                config = nextConfig,
                onSuccess = {
                    tvCurrentPublishedVersion.text = "Active Version: $vName (Code $vCode)"
                    tvVersionNotesPreview.text = nextConfig.releaseNotes.lines().firstOrNull() ?: "Active release"
                    tvPublishVersionStatus.text = "✅ $vName (Code $vCode) deployed via GitHub Releases!"
                    tvPublishVersionStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                    btnPublishVersionIncrement.isEnabled = true
                    com.example.btproject2.utils.InAppUpdateManager.clearDismissalCache(this)
                    recordAudit("OTA_RELEASE_DEPLOYED", "app_version_$vName", "Deployed version increment $vName (Code $vCode) pointing to $resolvedUrl")
                    KinTraceAlert.telemetry(this, "Version $vName Deployed", "GitHub Release $vName published to client feeds!", "LIVE")
                },
                onFailure = { err ->
                    tvPublishVersionStatus.text = "⚠️ Deploy failed: ${err.message}"
                    tvPublishVersionStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                    btnPublishVersionIncrement.isEnabled = true
                }
            )
        }
    }

    private fun loadArchivedDeletedMembers(highlightMemberId: String? = null) {
        layoutDeletedMembersList.removeAllViews()
        firestoreHelper.getArchivedDeletedMembers(
            onSuccess = { list ->
                runOnUiThread {
                    if (list.isEmpty()) {
                        tvNoDeletedMembers.visibility = View.VISIBLE
                        tvNoDeletedMembers.text = "🛡️ No archived deleted members. All lineages intact."
                        tvNoDeletedMembers.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        layoutDeletedMembersList.visibility = View.GONE
                    } else {
                        tvNoDeletedMembers.visibility = View.GONE
                        layoutDeletedMembersList.visibility = View.VISIBLE

                        for (rec in list) {
                            val itemView = layoutInflater.inflate(R.layout.item_deleted_member_recovery, layoutDeletedMembersList, false)
                            val tvName = itemView.findViewById<TextView>(R.id.tvDeletedMemberName)
                            val tvTree = itemView.findViewById<TextView>(R.id.tvDeletedMemberTree)
                            val tvTimeAgo = itemView.findViewById<TextView>(R.id.tvDeletedTimeAgo)
                            val tvDetails = itemView.findViewById<TextView>(R.id.tvDeletedMemberDetails)
                            val btnRestore = itemView.findViewById<Button>(R.id.btnRestoreMemberItem)
                            val tvStatus = itemView.findViewById<TextView>(R.id.tvDeletedMemberStatus)
                            val rootCard = itemView.findViewById<View>(R.id.cardDeletedMemberRoot)

                            val fullName = "${rec.person.firstName} ${rec.person.lastName}".trim().ifBlank { "Family Member" }
                            val role = if (rec.person.lineageRole.isNotBlank()) rec.person.lineageRole else if (rec.person.gender.equals("Female", true)) "Daughter" else "Son"
                            tvName.text = "$fullName ($role)"
                            tvTree.text = "🌳 ${rec.treeName.ifBlank { "Family Tree" }}"

                            val diffMs = System.currentTimeMillis() - rec.deletedAt
                            val diffMins = diffMs / 60000
                            val diffHours = diffMins / 60
                            val timeStr = when {
                                diffMins < 1 -> "Just now"
                                diffMins < 60 -> "${diffMins}m ago"
                                diffHours < 24 -> "${diffHours}h ago"
                                else -> "${diffHours / 24}d ago"
                            }
                            tvTimeAgo.text = timeStr
                            tvDetails.text = "ID: ${rec.id} • Deleted by ${rec.deletedByName.ifBlank { rec.deletedBy }}"

                            // Pulse highlight if navigated from Recent Activities
                            if (rec.id.equals(highlightMemberId, ignoreCase = true) ||
                                (highlightMemberId != null && rec.person.firstName.contains(highlightMemberId, ignoreCase = true))) {
                                rootCard.setBackgroundResource(R.drawable.bg_card_highlight_gold)
                                rootCard.postDelayed({
                                    rootCard.setBackgroundResource(R.drawable.bg_admin_code_box)
                                }, 3500)
                            }

                            btnRestore.setOnClickListener {
                                btnRestore.isEnabled = false
                                tvStatus.text = "⏳ Restoring member to tree..."
                                firestoreHelper.restoreArchivedMember(
                                    recordId = rec.id,
                                    onSuccess = { restoredPerson ->
                                        runOnUiThread {
                                            tvStatus.text = "✅ Restored! ${restoredPerson.firstName} re-linked to ${rec.treeName}."
                                            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                            btnRestore.visibility = View.GONE
                                            recordAudit("NODE_RESTORED", restoredPerson.id, "Restored ${restoredPerson.firstName} ${restoredPerson.lastName} to ${rec.treeName} via Ops Desk")
                                            loadLiveTelemetry()
                                            KinTraceAlert.restore(this, "Member Restored", "${restoredPerson.firstName} restored to ${rec.treeName} successfully!")

                                            // Check if all items in the UI are now restored
                                            val remainingUnrestored = (0 until layoutDeletedMembersList.childCount)
                                                .map { layoutDeletedMembersList.getChildAt(it) }
                                                .count { it.findViewById<Button>(R.id.btnRestoreMemberItem)?.visibility == View.VISIBLE }
                                            if (remainingUnrestored == 0) {
                                                tvNoDeletedMembers.visibility = View.VISIBLE
                                                tvNoDeletedMembers.text = "🛡️ No archived deleted members. All lineages intact."
                                                tvNoDeletedMembers.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                            }
                                        }
                                    },
                                    onFailure = { err ->
                                        runOnUiThread {
                                            tvStatus.text = "⚠️ Restore failed: ${err.message}"
                                            btnRestore.isEnabled = true
                                        }
                                    }
                                )
                            }

                            layoutDeletedMembersList.addView(itemView)
                        }
                    }
                }
            },
            onFailure = {
                runOnUiThread {
                    tvNoDeletedMembers.visibility = View.VISIBLE
                    tvNoDeletedMembers.text = "⚠️ Unable to load archived members: ${it.message}"
                    tvNoDeletedMembers.setTextColor(ContextCompat.getColor(this, R.color.warning))
                }
            }
        )
    }

    private fun loadMergeCollisions(highlightCollisionId: String? = null) {
        layoutCollisionDocketsList.removeAllViews()
        firestoreHelper.getOpenMergeCollisions(
            onSuccess = { list ->
                runOnUiThread {
                    if (list.isEmpty()) {
                        tvNoCollisions.visibility = View.VISIBLE
                        tvNoCollisions.text = "⚖️ No unresolved merge collisions. All clan branches synthesized."
                        tvNoCollisions.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                        layoutCollisionDocketsList.visibility = View.GONE
                    } else {
                        tvNoCollisions.visibility = View.GONE
                        layoutCollisionDocketsList.visibility = View.VISIBLE

                        for (docket in list) {
                            val itemView = layoutInflater.inflate(R.layout.item_collision_docket, layoutCollisionDocketsList, false)
                            val tvTitle = itemView.findViewById<TextView>(R.id.tvDocketTitle)
                            val tvSubtitle = itemView.findViewById<TextView>(R.id.tvDocketSubtitle)
                            val tvDocketBadge = itemView.findViewById<TextView>(R.id.tvDocketBadge)
                            val btnReport = itemView.findViewById<Button>(R.id.btnViewDocketReport)
                            val btnResolve = itemView.findViewById<Button>(R.id.btnResolveDocket)
                            val tvStatus = itemView.findViewById<TextView>(R.id.tvDocketStatus)
                            val rootCard = itemView.findViewById<View>(R.id.cardCollisionDocketRoot)

                            val docketNumber = docket.id.takeLast(4).uppercase()
                            tvTitle.text = "Escalation #$docketNumber: ${docket.personAName} Conflict"
                            tvSubtitle.text = docket.conflictSummary.ifBlank { "${docket.sourceTree1Name} ⇄ ${docket.sourceTree2Name}" }

                            if (docket.status == MergeCollisionRecord.STATUS_RESOLVED) {
                                tvDocketBadge.text = "RESOLVED"
                                tvDocketBadge.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                tvStatus.text = "✅ Conflict resolved. Canonical record established."
                                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                btnResolve.visibility = View.GONE
                                btnReport.text = "📄 View Resolved Dossier"
                            } else {
                                tvDocketBadge.text = "BLOCKED"
                                tvDocketBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
                                tvStatus.text = "Branch conflict unresolved."
                                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                                btnResolve.visibility = View.VISIBLE
                                btnReport.text = "📄 View Full Report"
                            }

                            if (docket.id.equals(highlightCollisionId, ignoreCase = true)) {
                                rootCard.setBackgroundResource(R.drawable.bg_card_highlight_gold)
                                rootCard.postDelayed({
                                    rootCard.setBackgroundResource(R.drawable.bg_admin_code_box)
                                }, 3500)
                            }

                            val effectiveResolution = docket.suggestedResolution.ifBlank { "Canonical lineage record established" }
                            btnReport.setOnClickListener {
                                showCollisionReportDialog(docket) {
                                    tvStatus.text = "✅ Conflict resolved! $effectiveResolution"
                                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                    tvDocketBadge.text = "RESOLVED"
                                    tvDocketBadge.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                    btnResolve.visibility = View.GONE
                                    btnReport.text = "📄 View Resolved Dossier"
                                    loadLiveTelemetry()

                                    val remainingUnresolved = (0 until layoutCollisionDocketsList.childCount)
                                        .map { layoutCollisionDocketsList.getChildAt(it) }
                                        .count { it.findViewById<Button>(R.id.btnResolveDocket)?.visibility == View.VISIBLE }
                                    if (remainingUnresolved == 0) {
                                        tvNoCollisions.visibility = View.VISIBLE
                                        tvNoCollisions.text = "⚖️ No unresolved merge collisions. All clan branches synthesized."
                                        tvNoCollisions.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                    }
                                }
                            }

                            btnResolve.setOnClickListener {
                                btnResolve.isEnabled = false
                                tvStatus.text = "⏳ Resolving conflict in Firestore..."
                                firestoreHelper.resolveMergeCollision(
                                    collisionId = docket.id,
                                    resolutionNote = effectiveResolution,
                                    resolvedBy = authHelper.getCurrentUser()?.email ?: "admin@kintrace.ph",
                                    onSuccess = {
                                        runOnUiThread {
                                            tvStatus.text = "✅ Conflict resolved! $effectiveResolution"
                                            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                            tvDocketBadge.text = "RESOLVED"
                                            tvDocketBadge.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                            btnResolve.visibility = View.GONE
                                            btnReport.text = "📄 View Resolved Dossier"
                                            recordAudit("COLLISION_RESOLVED", docket.id, "Escalation #${docketNumber} resolved: $effectiveResolution")
                                            loadLiveTelemetry()
                                            KinTraceAlert.conflict(this, "Escalation Resolved", "Branch conflict resolved successfully!", "CANONICAL")

                                            val remainingUnresolved = (0 until layoutCollisionDocketsList.childCount)
                                                .map { layoutCollisionDocketsList.getChildAt(it) }
                                                .count { it.findViewById<Button>(R.id.btnResolveDocket)?.visibility == View.VISIBLE }
                                            if (remainingUnresolved == 0) {
                                                tvNoCollisions.visibility = View.VISIBLE
                                                tvNoCollisions.text = "⚖️ No unresolved merge collisions. All clan branches synthesized."
                                                tvNoCollisions.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                                            }
                                        }
                                    },
                                    onFailure = { err ->
                                        runOnUiThread {
                                            tvStatus.text = "⚠️ Resolution failed: ${err.message}"
                                            btnResolve.isEnabled = true
                                        }
                                    }
                                )
                            }

                            layoutCollisionDocketsList.addView(itemView)
                        }
                    }
                }
            },
            onFailure = {
                runOnUiThread {
                    tvNoCollisions.visibility = View.VISIBLE
                    tvNoCollisions.text = "⚠️ Unable to load merge collisions: ${it.message}"
                    tvNoCollisions.setTextColor(ContextCompat.getColor(this, R.color.warning))
                }
            }
        )
    }

    private fun showCollisionReportDialog(docket: MergeCollisionRecord? = null, onResolved: (() -> Unit)? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_merge_collision_report, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val docketNum = docket?.id?.takeLast(4)?.uppercase()?.ifBlank { "0001" } ?: "0001"
        dialogView.findViewById<TextView>(R.id.tvCollisionDialogTitle)?.text =
            "Escalation #$docketNum: Collision Dossier"
        dialogView.findViewById<TextView>(R.id.tvCollisionDialogSubtitle)?.text =
            "${docket?.personAName?.ifBlank { "Branch A" } ?: "Branch A"} vs ${docket?.personBName?.ifBlank { "Branch B" } ?: "Branch B"} • RA 10173 Audit"
        dialogView.findViewById<TextView>(R.id.tvCollisionDialogSummary)?.text =
            docket?.conflictDetails?.takeIf { it.isNotBlank() }
                ?: "Automated merge between ${docket?.sourceTree1Name ?: "Branch A"} and ${docket?.sourceTree2Name ?: "Branch B"} was blocked to prevent lineage paradox."

        dialogView.findViewById<TextView>(R.id.tvCollisionBranchAHeader)?.text =
            docket?.sourceTree1Name?.takeIf { it.isNotBlank() } ?: "Branch A"
        dialogView.findViewById<TextView>(R.id.tvCollisionBranchBHeader)?.text =
            docket?.sourceTree2Name?.takeIf { it.isNotBlank() } ?: "Branch B"
        dialogView.findViewById<TextView>(R.id.tvCollisionNameA)?.text =
            docket?.personAName?.takeIf { it.isNotBlank() } ?: "—"
        dialogView.findViewById<TextView>(R.id.tvCollisionNameB)?.text =
            docket?.personBName?.takeIf { it.isNotBlank() } ?: "—"

        val summaryParts = docket?.conflictSummary?.split("|")?.map { it.trim() }.orEmpty()
        dialogView.findViewById<TextView>(R.id.tvCollisionBirthA)?.text =
            summaryParts.getOrNull(0)?.ifBlank { docket?.conflictSummary ?: "—" } ?: "—"
        dialogView.findViewById<TextView>(R.id.tvCollisionBirthB)?.text =
            summaryParts.getOrNull(1)?.ifBlank { "—" } ?: "—"

        dialogView.findViewById<TextView>(R.id.tvCollisionTypeA)?.text =
            docket?.conflictType?.takeIf { it.isNotBlank() } ?: "LINEAGE_COLLISION"
        dialogView.findViewById<TextView>(R.id.tvCollisionEscalatedBy)?.text =
            docket?.escalatedBy?.takeIf { it.isNotBlank() } ?: "Automated Validation Engine"

        val resolutionText = docket?.suggestedResolution?.takeIf { it.isNotBlank() }
            ?: "Apply Canonical Lineage Resolution"
        dialogView.findViewById<TextView>(R.id.tvCollisionRecommendation)?.text = resolutionText

        val btnDialogResolve = dialogView.findViewById<TextView>(R.id.btnDialogResolveCollision)
        val btnDialogDismiss = dialogView.findViewById<TextView>(R.id.btnDialogDismissReport)

        if (docket?.status == MergeCollisionRecord.STATUS_RESOLVED) {
            btnDialogResolve.text = "✅ Already Resolved"
            btnDialogResolve.isEnabled = false
            btnDialogResolve.alpha = 0.6f
        } else {
            btnDialogResolve.text = "⚖️ $resolutionText"
        }

        btnDialogResolve.setOnClickListener {
            btnDialogResolve.isEnabled = false
            dialog.dismiss()
            val docketId = docket?.id?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
            firestoreHelper.resolveMergeCollision(
                collisionId = docketId,
                resolutionNote = resolutionText,
                resolvedBy = authHelper.getCurrentUser()?.email ?: "admin@kintrace.ph",
                onSuccess = {
                    runOnUiThread {
                        onResolved?.invoke()
                        recordAudit("COLLISION_RESOLVED", docketId, "Escalation #$docketNum resolved via forensic dossier: $resolutionText")
                        KinTraceAlert.conflict(this, "Canonical Resolution", "Branch conflict resolved via Canonical Dossier!", "CANONICAL")
                    }
                },
                onFailure = {
                    runOnUiThread {
                        onResolved?.invoke()
                        KinTraceAlert.conflict(this, "Local Resolution", "Resolved locally.", "LOCAL")
                    }
                }
            )
        }

        btnDialogDismiss.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun generateSha256Badge(actionType: String, targetId: String, timestamp: Long): String {
        return try {
            val raw = "$actionType:$targetId:$timestamp:kintrace_gov"
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
            "SHA256:${hash.take(8)}...${hash.takeLast(4)}"
        } catch (_: Exception) {
            "SHA256:7a9f8c...3b1d"
        }
    }

    private fun recordAudit(actionType: String, targetId: String, details: String) {
        val timestamp = System.currentTimeMillis()
        val hash = generateSha256Badge(actionType, targetId, timestamp)
        val log = AdminAuditLog(
            id = "AUDIT_${timestamp}",
            adminEmail = "admin@kintrace.ph",
            actionType = actionType,
            targetId = targetId,
            details = details,
            checkHash = hash,
            timestamp = timestamp
        )
        auditLogs.add(0, log)
        firestoreHelper.logAdminAuditAction(log)
        renderAuditLogs()
    }

    private fun seedInitialAuditLogs() {
        val now = System.currentTimeMillis()
        val fallbackLogs = listOf(
            AdminAuditLog(
                id = "AUDIT_${now - 100000}",
                actionType = "CITADEL_SESSION_INIT",
                targetId = "citadel_hub",
                details = "Admin session authenticated for Super Admin (${authHelper.getCurrentUser()?.email ?: "admin@kintrace.ph"})",
                checkHash = generateSha256Badge("CITADEL_SESSION_INIT", "citadel_hub", now - 100000),
                timestamp = now - 100000
            )
        )
        if (auditLogs.isEmpty()) {
            auditLogs.addAll(fallbackLogs)
        }
        firestoreHelper.fetchAdminAuditLogs(limit = 15, onSuccess = { cloudLogs ->
            runOnUiThread {
                if (cloudLogs.isNotEmpty()) {
                    val existingIds = cloudLogs.map { it.id }.toSet()
                    val localUnsynced = auditLogs.filter { it.id !in existingIds && !it.id.startsWith("AUDIT_${now - 100000}") }
                    auditLogs.clear()
                    auditLogs.addAll((localUnsynced + cloudLogs).sortedByDescending { it.timestamp })
                    renderAuditLogs()
                }
            }
        })
    }

    private fun renderAuditLogs() {
        layoutAuditList.removeAllViews()
        val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm:ss a", Locale.getDefault())

        for (log in auditLogs.take(5)) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@AdminPortalActivity, R.drawable.bg_admin_code_box)
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            val headerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(this).apply {
                text = "${dateTimeFormat.format(Date(log.timestamp))} • ${log.actionType}"
                textSize = 10.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@AdminPortalActivity, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvHash = TextView(this).apply {
                text = log.checkHash
                textSize = 9f
                setTypeface(android.graphics.Typeface.MONOSPACE)
                setTextColor(ContextCompat.getColor(this@AdminPortalActivity, R.color.primary))
            }

            headerLayout.addView(tvTitle)
            headerLayout.addView(tvHash)

            val tvDetails = TextView(this).apply {
                text = log.details
                textSize = 10f
                setTextColor(ContextCompat.getColor(this@AdminPortalActivity, R.color.text_secondary))
                setPadding(0, 8, 0, 0)
            }

            itemLayout.addView(headerLayout)
            itemLayout.addView(tvDetails)
            layoutAuditList.addView(itemLayout)
        }
    }

    private fun showSignOutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out of Admin Session")
            .setMessage("Are you sure you want to sign out of the System Admin Citadel?")
            .setPositiveButton("Sign Out") { _, _ ->
                authHelper.performGlobalLogout(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
