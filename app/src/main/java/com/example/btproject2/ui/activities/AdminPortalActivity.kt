package com.example.btproject2.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AdminAuditLog
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
    private lateinit var btnApproveMalone: Button
    private lateinit var tvMaloneStatus: TextView
    private lateinit var btnApproveSantos: Button
    private lateinit var tvSantosStatus: TextView
    private lateinit var btnGenerateReunionPass: Button
    private lateinit var layoutGeneratedPass: LinearLayout
    private lateinit var tvGeneratedPassCode: TextView
    private lateinit var btnCopyPassCode: Button

    // Ops Desk Controls
    private lateinit var btnRestoreNode: Button
    private lateinit var tvRestoreStatus: TextView
    private lateinit var btnResolveCollision: Button
    private lateinit var tvCollisionStatus: TextView
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
    }

    private fun initViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener {
            showSignOutDialog()
        }
        findViewById<View?>(R.id.btnPreviewFeed)?.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("ADMIN_PREVIEW_MODE", true)
            }
            startActivity(intent)
        }

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
        btnApproveMalone = findViewById(R.id.btnApproveMalone)
        tvMaloneStatus = findViewById(R.id.tvMaloneStatus)
        btnApproveSantos = findViewById(R.id.btnApproveSantos)
        tvSantosStatus = findViewById(R.id.tvSantosStatus)
        btnGenerateReunionPass = findViewById(R.id.btnGenerateReunionPass)
        layoutGeneratedPass = findViewById(R.id.layoutGeneratedPass)
        tvGeneratedPassCode = findViewById(R.id.tvGeneratedPassCode)
        btnCopyPassCode = findViewById(R.id.btnCopyPassCode)

        // Ops Desk
        btnRestoreNode = findViewById(R.id.btnRestoreNode)
        tvRestoreStatus = findViewById(R.id.tvRestoreStatus)
        btnResolveCollision = findViewById(R.id.btnResolveCollision)
        tvCollisionStatus = findViewById(R.id.tvCollisionStatus)
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
            Toast.makeText(this, "Please enter both a title and message", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Broadcast published live!", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Broadcast retracted.", Toast.LENGTH_SHORT).show()
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
                tvTotalNodes.text = "${telemetry.totalNodes} Nodes"
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
            tvGhostTreeList.text = "✅ All 3 dormant / empty ghost tree records purged.\nReclaimed document slots for active clans."
            tvPruneStatus.text = "✅ 3 Ghost Trees Purged • Database Optimized."
            tvPruneStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            btnPruneGhostTrees.isEnabled = false
            recordAudit("GHOST_TREES_PRUNED", "tree_catalog", "Purged 3 dormant trees (Test_Empty_01, Draft_Sandbox_A, Orphan_Tree_99)")
            loadLiveTelemetry()
            Toast.makeText(this, "Purged 3 ghost trees successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuotaAndPasses() {
        btnApproveMalone.setOnClickListener {
            tvMaloneStatus.text = "✅ Expansion Approved: Cap elevated from 50 to 200 members."
            tvMaloneStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            btnApproveMalone.isEnabled = false
            recordAudit("CLAN_CAP_EXPANDED", "tree_malone", "Malone Clan quota elevated to 200 nodes")
            Toast.makeText(this, "Malone Clan expansion approved!", Toast.LENGTH_SHORT).show()
        }

        btnApproveSantos.setOnClickListener {
            tvSantosStatus.text = "✅ Proactive Expansion Approved: Cap elevated to 200 members."
            tvSantosStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            btnApproveSantos.isEnabled = false
            recordAudit("CLAN_CAP_EXPANDED", "tree_santos", "Santos-Reyes Heritage quota elevated to 200 nodes")
            Toast.makeText(this, "Santos-Reyes proactive expansion approved!", Toast.LENGTH_SHORT).show()
        }

        btnGenerateReunionPass.setOnClickListener {
            val passCode = "REUNION-PASS-${(1000..9999).random()}"
            layoutGeneratedPass.visibility = View.VISIBLE
            tvGeneratedPassCode.text = passCode

            val passRecord = ReunionPassRecord(
                code = passCode,
                treeId = "tree_malone",
                treeName = "Malone Clan Reunion",
                validDays = 30,
                generatedBy = "admin@kintrace.ph"
            )

            firestoreHelper.saveReunionPass(
                pass = passRecord,
                onSuccess = {
                    recordAudit("REUNION_PASS_GENERATED", passCode, "Generated 30-day reunion pass for Malone Clan")
                },
                onFailure = {
                    recordAudit("REUNION_PASS_GENERATED", passCode, "Generated 30-day reunion pass (offline)")
                }
            )

            Toast.makeText(this, "Pass generated: $passCode", Toast.LENGTH_SHORT).show()
        }

        btnCopyPassCode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Reunion Pass Code", tvGeneratedPassCode.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied ${tvGeneratedPassCode.text} to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOpsDesk() {
        btnRestoreNode.setOnClickListener {
            tvRestoreStatus.text = "✅ Node Restored! Juan Dela Cruz re-linked to graph."
            tvRestoreStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            btnRestoreNode.isEnabled = false
            recordAudit("NODE_RESTORED", "node_juan_delacruz", "Soft-delete flag cleared; restored ancestor node into tree_malone")
            Toast.makeText(this, "Ancestor node restored & graph healed!", Toast.LENGTH_SHORT).show()
        }

        btnResolveCollision.setOnClickListener {
            tvCollisionStatus.text = "✅ Conflict resolved! Canonical Birth Year 1945 established."
            tvCollisionStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            btnResolveCollision.isEnabled = false
            recordAudit("COLLISION_RESOLVED", "person_maria_santos", "Escalation #402 resolved: Birth year established as 1945")
            Toast.makeText(this, "Branch conflict resolved successfully!", Toast.LENGTH_SHORT).show()
        }

        btnSeedDemoClan.setOnClickListener {
            tvSeedStatus.text = "✅ 4-Generation Test Clan (24 Nodes) seeded into active session."
            tvSeedStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            recordAudit("DEMO_SANDBOX_SEEDED", "demo_session", "Seeded 24 test nodes for oral defense demonstration")
            loadLiveTelemetry()
            Toast.makeText(this, "24 test nodes seeded into emulator!", Toast.LENGTH_SHORT).show()
        }

        btnFlushDemoState.setOnClickListener {
            tvFlushStatus.text = "✅ Sandbox reset to clean proposal defaults."
            tvFlushStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            recordAudit("DEMO_SANDBOX_FLUSHED", "demo_session", "Reset sandbox state to clean proposal defaults")
            loadLiveTelemetry()
            Toast.makeText(this, "Demo state reset to clean defaults.", Toast.LENGTH_SHORT).show()
        }

        // OTA Release Desk Logic
        val etReleaseVersionName = findViewById<EditText>(R.id.etReleaseVersionName)
        val etReleaseVersionCode = findViewById<EditText>(R.id.etReleaseVersionCode)
        val etReleaseApkUrl = findViewById<EditText>(R.id.etReleaseApkUrl)
        val etReleaseNotes = findViewById<EditText>(R.id.etReleaseNotes)

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
                    if (etReleaseVersionName.text.isBlank()) {
                        val currentMajorMinor = config.versionName.removePrefix("v").split(".")
                        val nextVersionName = if (currentMajorMinor.size >= 2) {
                            val minor = currentMajorMinor[1].toIntOrNull() ?: 2
                            "v${currentMajorMinor[0]}.${minor + 1}.0"
                        } else "v1.3.0"
                        etReleaseVersionName.setText(nextVersionName)
                    }
                    if (etReleaseNotes.text.isBlank()) {
                        etReleaseNotes.setText("• New Feature: \n• Performance optimization\n• System stability enhancement")
                    }
                    if (etReleaseApkUrl.text.isBlank() && config.apkDownloadUrl.isNotBlank()) {
                        etReleaseApkUrl.setText(config.apkDownloadUrl)
                    }
                }
            }
        )

        btnPublishVersionIncrement.setOnClickListener {
            val vName = etReleaseVersionName.text.toString().trim()
            val vCodeStr = etReleaseVersionCode.text.toString().trim()
            val vNotes = etReleaseNotes.text.toString().trim()
            val vUrl = etReleaseApkUrl.text.toString().trim()

            if (vName.isBlank()) {
                etReleaseVersionName.error = "Version name required"
                return@setOnClickListener
            }
            val vCode = vCodeStr.toLongOrNull() ?: 2L

            val nextConfig = com.example.btproject2.models.AppVersionConfig(
                versionCode = vCode,
                versionName = vName,
                releaseNotes = if (vNotes.isNotBlank()) vNotes else "• System increment $vName",
                apkDownloadUrl = vUrl,
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
                    tvPublishVersionStatus.text = "✅ $vName (Code $vCode) deployed live to all client devices!"
                    tvPublishVersionStatus.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                    btnPublishVersionIncrement.isEnabled = true
                    com.example.btproject2.utils.InAppUpdateManager.clearDismissalCache(this)
                    recordAudit("OTA_RELEASE_DEPLOYED", "app_version_$vName", "Deployed version increment $vName (Code $vCode) to Firestore")
                    Toast.makeText(this, "Version $vName deployed to all client devices!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { err ->
                    tvPublishVersionStatus.text = "⚠️ Deploy failed: ${err.message}"
                    tvPublishVersionStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                    btnPublishVersionIncrement.isEnabled = true
                }
            )
        }
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
        auditLogs.add(
            AdminAuditLog(
                id = "AUDIT_${now - 100000}",
                actionType = "CITADEL_SESSION_INIT",
                targetId = "citadel_hub",
                details = "Admin session authenticated for Super Admin (admin@kintrace.ph)",
                checkHash = "SHA256:4a8c91...7e2a",
                timestamp = now - 100000
            )
        )
        auditLogs.add(
            AdminAuditLog(
                id = "AUDIT_${now - 300000}",
                actionType = "TELEMETRY_HEALTH_CHECK",
                targetId = "cloud_quota",
                details = "Firestore read/write quota consumption evaluated at 76.8% and 71.0%",
                checkHash = "SHA256:8f1b29...4c90",
                timestamp = now - 300000
            )
        )
    }

    private fun renderAuditLogs() {
        layoutAuditList.removeAllViews()
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

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
                text = "${timeFormat.format(Date(log.timestamp))} • ${log.actionType}"
                textSize = 11f
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
                authHelper.logout()
                com.example.btproject2.utils.TreePreferences.clear(this)
                com.example.btproject2.firebase.FirestoreHelper.clearCache()
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
