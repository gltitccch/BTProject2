package com.example.btproject2.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.PedigreeFanChartSyncCoordinator
import com.example.btproject2.ui.adapters.TreeMemberAdapter

class FamilyTreeActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private var treeId = ""
    private val recentlyDeletedIds = mutableSetOf<String>()

    private lateinit var rvTree: RecyclerView
    private lateinit var webViewFan: WebView
    private lateinit var btnPedigree: TextView
    private lateinit var btnFanChart: TextView
    private lateinit var btnInteractiveTree: TextView
    private lateinit var tvMemberCount: TextView
    private lateinit var treeAdapter: TreeMemberAdapter

    private var allPersons: List<Person> = emptyList()
    private var isFanChartLoaded = false
    private var isViewer = false
    fun isViewerUser(): Boolean = isViewer

    internal val syncCoordinator by lazy {
        PedigreeFanChartSyncCoordinator(
            treeId = treeId,
            listenerKey = "FamilyTreeActivity@${System.identityHashCode(this)}",
            screenType = AffectedScreen.PEDIGREE_VIEW,
            allPersonsList = allPersons,
            onTreeUpdated = { updatedList, _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    allPersons = updatedList
                    tvMemberCount.text = "${allPersons.size} members"

                    val layoutManager = rvTree.layoutManager as? LinearLayoutManager
                    val scrollState = layoutManager?.onSaveInstanceState()

                    Thread {
                        val genMap = computeGenerationMap(allPersons)
                        val items = buildGenerationItems(allPersons, genMap)
                        val focal = findDefaultFocalPerson(allPersons)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            treeAdapter.submitItems(items, allPersons, focal)
                            scrollState?.let { layoutManager?.onRestoreInstanceState(it) }

                            if (webViewFan.visibility == android.view.View.VISIBLE) {
                                renderFanChart(allPersons, genMap)
                            }
                        }
                    }.start()
                }
            },
            onMemberDeleted = { deletedId ->
                recentlyDeletedIds.add(deletedId)
            }
        )
    }

    private val memberDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deletedId = result.data?.getStringExtra("deletedPersonId")
            if (!deletedId.isNullOrEmpty()) {
                recentlyDeletedIds.add(deletedId)
                allPersons = allPersons.filter { it.id != deletedId }
                syncCoordinator.allPersonsList = allPersons
                tvMemberCount.text = "${allPersons.size} members"
                val items = buildGenerationItems(allPersons)
                val focal = findDefaultFocalPerson(allPersons)
                treeAdapter.submitItems(items, allPersons, focal)
                if (webViewFan.visibility == android.view.View.VISIBLE) {
                    renderFanChart(allPersons)
                }
            }
        }
    }

    private val networkListener: (com.example.btproject2.utils.NetworkStateMonitor.NetworkState) -> Unit = { state ->
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.updateNetworkState(this, state) {
            loadTree()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_tree)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        tvMemberCount = findViewById(R.id.tvMemberCount)
        btnPedigree   = findViewById(R.id.btnPedigree)
        btnFanChart         = findViewById(R.id.btnFanChart)
        btnInteractiveTree  = findViewById(R.id.btnInteractiveTree)
        rvTree        = findViewById(R.id.rvTree)
        webViewFan    = findViewById(R.id.webViewFan)

        btnBack.setOnClickListener { finish() }

        // ── Floating Heritage Island Bottom Navigation Coordinator ──
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.setup(
            activity = this,
            activeTab = com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.NavTab.TREE,
            treeIdProvider = { treeId },
            onProfileClicked = {
                // Return to home where account profile dialog is hosted
                val intent = Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intent)
            }
        )

        val tvTreeRoleBadge = findViewById<TextView>(R.id.tvTreeRoleBadge)
        val layoutViewerBanner = findViewById<android.view.View>(R.id.layoutViewerBanner)
        val authHelper = com.example.btproject2.firebase.AuthHelper()
        val currentUserId = authHelper.getCurrentUserId() ?: ""

        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (isOwner) {
                    isViewer = false
                    tvTreeRoleBadge.text = "★ Owner • Full access"
                    layoutViewerBanner.visibility = android.view.View.GONE
                } else {
                    firestoreHelper.getUserMembership(currentUserId, treeId,
                        onSuccess = { member ->
                            val role = member?.role ?: "Viewer"
                            when (role.lowercase()) {
                                "editor" -> {
                                    isViewer = false
                                    tvTreeRoleBadge.text = "✎ Editor - Edit access"
                                    layoutViewerBanner.visibility = android.view.View.GONE
                                }
                                else -> {
                                    isViewer = true
                                    tvTreeRoleBadge.text = "Viewer • Read-only access"
                                    layoutViewerBanner.visibility = android.view.View.VISIBLE
                                }
                            }
                        },
                        onFailure = {
                            isViewer = true
                            tvTreeRoleBadge.text = "Viewer • Read-only access"
                            layoutViewerBanner.visibility = android.view.View.VISIBLE
                        }
                    )
                }
            },
            onFailure = {
                isViewer = false
                tvTreeRoleBadge.text = "★ Owner • Full access"
                layoutViewerBanner.visibility = android.view.View.GONE
            }
        )

        // RecyclerView setup
        treeAdapter = TreeMemberAdapter { person ->
            val intent = Intent(this, MemberDetailActivity::class.java).apply {
                putExtra("personId", person.id)
                putExtra("TREE_ID", treeId)
                putExtra("isViewer", isViewer)
            }
            memberDetailLauncher.launch(intent)
        }
        rvTree.layoutManager = LinearLayoutManager(this)
        rvTree.adapter = treeAdapter

        // Fan chart WebView setup
        webViewFan.settings.javaScriptEnabled = true
        webViewFan.settings.builtInZoomControls = true
        webViewFan.settings.displayZoomControls = false
        webViewFan.settings.useWideViewPort = true
        webViewFan.settings.loadWithOverviewMode = true
        webViewFan.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isFanChartLoaded = true
            }
        }

        val btnTreeLegend = findViewById<TextView>(R.id.btnTreeLegend)
        btnTreeLegend?.setOnClickListener { showTreeLegendDialog() }

        val btnQuickAssistTree = findViewById<TextView>(R.id.btnQuickAssistTree)
        btnQuickAssistTree?.setOnClickListener {
            val ctx = if (webViewFan.visibility == android.view.View.VISIBLE) {
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.FAN_CHART
            } else {
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.PEDIGREE_FAN_CHART
            }
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(ctx)
                .show(supportFragmentManager, "QuickAssistFamilyTree")
        }

        val btnExportTree = findViewById<TextView>(R.id.btnExportTree)
        btnExportTree?.setOnClickListener {
            val intent = Intent(this, ExportTreeActivity::class.java).apply {
                if (treeId.isNotEmpty()) putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        val btnTreeToolsMenu = findViewById<TextView>(R.id.btnTreeToolsMenu)
        btnTreeToolsMenu?.setOnClickListener { anchorView ->
            com.example.btproject2.ui.dialogs.TreeToolsPopoverHelper.show(
                context = this,
                anchor = anchorView,
                showConnect = !isViewer,
                showAudit = true,
                showOrganize = false,
                onConnect = {
                    val activeTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this@FamilyTreeActivity) }
                    val intent = Intent(this, MergeBranchesActivity::class.java).apply {
                        putExtra("TREE_ID", activeTreeId)
                        putExtra("treeId", activeTreeId)
                    }
                    startActivity(intent)
                },
                onAudit = {
                    val activeTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this@FamilyTreeActivity) }
                    val intent = Intent(this, TreeAuditActivity::class.java).apply {
                        putExtra("TREE_ID", activeTreeId)
                        putExtra("treeId", activeTreeId)
                    }
                    startActivity(intent)
                },
                onExport = { btnExportTree?.performClick() },
                onLegend = { btnTreeLegend?.performClick() }
            )
        }

        // Tab buttons
        btnPedigree.setOnClickListener { switchTab(pedigree = true) }
        btnFanChart.setOnClickListener { switchTab(pedigree = false) }
        btnInteractiveTree.setOnClickListener {
            com.example.btproject2.utils.KinTraceSpotlightManager.dismissActiveOverlay()
            com.example.btproject2.utils.KinTraceSpotlightManager.cancelPendingTutorials()
            val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }
        switchTab(pedigree = true) // default

        loadTree()
    }

    override fun onStart() {
        super.onStart()
        syncCoordinator.register()
        com.example.btproject2.utils.NetworkStateMonitor.startMonitoring(this)
        com.example.btproject2.utils.NetworkStateMonitor.addListener(networkListener)
    }

    override fun onResume() {
        super.onResume()
        loadTree()

        if (webViewFan.visibility == android.view.View.VISIBLE) {
            com.example.btproject2.utils.KinTraceSpotlightManager.cancelPendingTutorials(
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE
            )
            com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
                this,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAN_CHART,
                treeId = treeId
            )
        } else {
            com.example.btproject2.utils.KinTraceSpotlightManager.cancelPendingTutorials(
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAN_CHART
            )
            com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
                this,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE,
                treeId = treeId
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncCoordinator.unregister()
        com.example.btproject2.utils.NetworkStateMonitor.removeListener(networkListener)
    }

    private fun switchTab(pedigree: Boolean) {
        val activeColor = androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)

        fun styleSegment(tv: TextView, isActive: Boolean) {
            tv.setBackgroundResource(if (isActive) R.drawable.bg_segmented_item_active else android.R.color.transparent)
            val color = if (isActive) activeColor else inactiveColor
            tv.setTextColor(color)
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                tv,
                android.content.res.ColorStateList.valueOf(color)
            )
        }

        com.example.btproject2.utils.KinTraceSpotlightManager.dismissActiveOverlay()
        com.example.btproject2.utils.KinTraceSpotlightManager.cancelPendingTutorials()

        if (pedigree) {
            com.example.btproject2.utils.KinTraceSpotlightManager.cancelPendingTutorials(
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAN_CHART
            )
            styleSegment(btnPedigree, true)
            styleSegment(btnFanChart, false)
            styleSegment(btnInteractiveTree, false)
            rvTree.visibility = android.view.View.VISIBLE
            webViewFan.visibility = android.view.View.GONE
            com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
                this,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE,
                treeId = treeId
            )
        } else {
            com.example.btproject2.utils.KinTraceSpotlightManager.cancelPendingTutorials(
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_TREE_PEDIGREE
            )
            styleSegment(btnPedigree, false)
            styleSegment(btnFanChart, true)
            styleSegment(btnInteractiveTree, false)
            rvTree.visibility = android.view.View.GONE
            webViewFan.visibility = android.view.View.VISIBLE
            com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
                this,
                com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAN_CHART,
                treeId = treeId
            )
            if (allPersons.isNotEmpty()) {
                Thread {
                    val genMap = computeGenerationMap(allPersons)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            renderFanChart(allPersons, genMap)
                        }
                    }
                }.start()
            }
        }
    }

    private fun showTreeLegendDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_legend, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.view.View>(R.id.btnCloseLegend)?.setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<android.view.View>(R.id.btnGotItLegend)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun findDefaultFocalPerson(persons: List<Person>): Person? {
        if (persons.isEmpty()) return null
        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Throwable) { null }
        val uName = (currentUser?.displayName ?: "").lowercase().trim()

        if (uName.isNotEmpty()) {
            val userMatch = persons.find {
                val fName = "${it.firstName} ${it.lastName}".lowercase()
                fName.contains(uName) || uName.contains(it.firstName.lowercase())
            }
            if (userMatch != null) return userMatch
        }

        return TreeMemberAdapter.resolveDefaultFocal(persons)
    }

    private fun loadTree() {
        val cached = FirestoreHelper.getCachedPersons(treeId)
        if (!cached.isNullOrEmpty() && allPersons.isEmpty()) {
            val activePersons = FirestoreHelper.sanitizeTreeRecords(cached).filter { it.id !in recentlyDeletedIds }
            allPersons = activePersons
            syncCoordinator.allPersonsList = activePersons
            tvMemberCount.text = "${activePersons.size} members"
            Thread {
                val genMap = computeGenerationMap(activePersons)
                val items = buildGenerationItems(activePersons, genMap)
                val focal = findDefaultFocalPerson(activePersons)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    treeAdapter.submitItems(items, activePersons, focal)
                    if (webViewFan.visibility == android.view.View.VISIBLE) {
                        renderFanChart(activePersons, genMap)
                    }
                }
            }.start()
        }

        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                val activePersons = FirestoreHelper.sanitizeTreeRecords(persons).filter { it.id !in recentlyDeletedIds }
                allPersons = activePersons
                syncCoordinator.allPersonsList = activePersons
                tvMemberCount.text = "${activePersons.size} members"
                Thread {
                    val genMap = computeGenerationMap(activePersons)
                    val items = buildGenerationItems(activePersons, genMap)
                    val focal = findDefaultFocalPerson(activePersons)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        treeAdapter.submitItems(items, activePersons, focal)
                        if (webViewFan.visibility == android.view.View.VISIBLE) {
                            renderFanChart(activePersons, genMap)
                        }
                    }
                }.start()
            },
            onFailure = {
                val fallbackCache = FirestoreHelper.getCachedPersons(treeId)
                if (!fallbackCache.isNullOrEmpty()) {
                    val activePersons = FirestoreHelper.sanitizeTreeRecords(fallbackCache).filter { it.id !in recentlyDeletedIds }
                    allPersons = activePersons
                    syncCoordinator.allPersonsList = activePersons
                    tvMemberCount.text = "${activePersons.size} members"
                    Thread {
                        val genMap = computeGenerationMap(activePersons)
                        val items = buildGenerationItems(activePersons, genMap)
                        val focal = findDefaultFocalPerson(activePersons)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            treeAdapter.submitItems(items, activePersons, focal)
                            if (webViewFan.visibility == android.view.View.VISIBLE) {
                                renderFanChart(activePersons, genMap)
                            }
                        }
                    }.start()
                } else {
                    tvMemberCount.text = "Failed to load"
                }
            }
        )
    }

    companion object {
        /**
         * Authoritative generation calculation:
         * 1. Roots (no known parents in tree) = generation 1.
         * 2. Iterative topological pass: gen(child) = max(gen(father), gen(mother)) + 1
         * 3. Spouse alignment: Spouses who married descendants without parents in tree align with their spouse's generation.
         * 4. Fallback for unresolved persons: maxGen + 1.
         */
        fun computeGenerationMap(persons: List<Person>): Map<String, Int> {
            return com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations1Based(persons)
        }
    }

    /**
     * BFS from roots to assign generation numbers, then build flat item list
     * interleaved with HEADER items for generation labels.
     */
    fun buildGenerationItems(
        persons: List<Person>,
        generationMap: Map<String, Int> = computeGenerationMap(persons)
    ): List<TreeMemberAdapter.TreeItem> {
        if (persons.isEmpty()) return emptyList()

        // Within each generation, sort by birthdate (oldest first), then by name
        val grouped = persons.groupBy { generationMap[it.id] ?: 1 }
        val items = mutableListOf<TreeMemberAdapter.TreeItem>()
        grouped.keys.sorted().forEach { gen ->
            val list = grouped[gen].orEmpty()
            items.add(TreeMemberAdapter.TreeItem.Header(gen, list.size))
            list
                .sortedWith(compareBy({ it.birthDate.ifEmpty { "9999" } }, { it.firstName }))
                .forEach { items.add(TreeMemberAdapter.TreeItem.Member(it)) }
        }
        return items
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun renderFanChart(
        persons: List<Person>,
        genMap: Map<String, Int> = computeGenerationMap(persons)
    ) {
        val bestFocal = com.example.btproject2.engine.GenerationHierarchyEngine.findBestPedigreeFocalMember(persons)
            ?: persons.firstOrNull()
        val nodesJson = syncCoordinator.buildFanChartNodesJson(persons, genMap, bestFocal)
        if (isFanChartLoaded) {
            webViewFan.evaluateJavascript("if (typeof window.updateFanChart === 'function') { window.updateFanChart($nodesJson); 'true'; } else { 'false'; }") { res ->
                if (res != "\"true\"" && res != "true") {
                    val html = buildFanChartHtml(nodesJson)
                    webViewFan.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                }
            }
        } else {
            val html = buildFanChartHtml(nodesJson)
            webViewFan.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        }
    }

    private fun colorToHex(colorRes: Int): String {
        val c = androidx.core.content.ContextCompat.getColor(this, colorRes)
        return String.format("#%06X", 0xFFFFFF and c)
    }

    private fun buildFanChartHtml(nodesJson: String): String {
        val bgHex = colorToHex(R.color.forest_bg)
        val cardHex = colorToHex(R.color.forest_card)
        val borderHex = colorToHex(R.color.forest_card_border)
        val textPrimaryHex = colorToHex(R.color.text_primary)
        val textSecHex = colorToHex(R.color.text_secondary)
        val goldHex = colorToHex(R.color.gold_accent)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
* { margin:0; padding:0; box-sizing:border-box; -webkit-user-select:none; user-select:none; }
html, body { width:100%; height:100%; background:$bgHex; color:$textPrimaryHex; font-family:-apple-system,sans-serif; overflow:hidden; }
body { padding:4px 18px 14px; display:flex; flex-direction:column; }
.fan-card { width:100%; flex:1; min-height:0; background:$cardHex; border:1px solid $borderHex; border-radius:20px; padding:14px 14px 12px; display:flex; flex-direction:column; align-items:center; gap:8px; position:relative; overflow:hidden; }
.fan-header { width:100%; display:flex; justify-content:space-between; align-items:flex-start; gap:8px; z-index:10; flex-shrink:0; }
.fan-title { font-size:14.5px; font-weight:800; color:$textPrimaryHex; }
.fan-sub { font-size:10.5px; color:$textSecHex; margin-top:2px; }
.header-right { display:flex; align-items:center; gap:6px; flex-shrink:0; }
.custom-trigger-pill {
  display:inline-flex;
  align-items:center;
  gap:5px;
  font-size:10.5px;
  font-weight:800;
  color:$goldHex;
  background:$bgHex;
  border:1px solid $borderHex;
  border-radius:999px;
  padding:5px 10px;
  cursor:pointer;
  white-space:nowrap;
}
.custom-trigger-pill:active { opacity:0.75; transform:scale(0.97); }
.custom-trigger-pill .dot {
  width:6px;
  height:6px;
  border-radius:50%;
  background:$goldHex;
}
.fan-badge { font-size:10px; font-weight:800; color:$goldHex; border:1px solid $borderHex; background:$bgHex; padding:5px 9px; border-radius:999px; white-space:nowrap; }
.viewport { width:100%; flex:1; min-height:0; position:relative; overflow:hidden; display:flex; align-items:center; justify-content:center; border-radius:14px; touch-action:none; cursor:grab; }
.viewport:active { cursor:grabbing; }
canvas { width:348px; height:348px; transform-origin:center center; will-change:transform; }

/* UI 2: Slide-Up Compact Bottom Drawer with Generation Filter Chips & 2-Column Grid */
.ui2-backdrop {
  position:absolute;
  inset:0;
  background:rgba(0,0,0,0.32);
  z-index:35;
  display:none;
  align-items:flex-end;
}
.ui2-backdrop.open { display:flex; }
.ui2-sheet {
  width:100%;
  background:$cardHex;
  border-top:1px solid $borderHex;
  border-radius:20px 20px 0 0;
  padding:10px 14px 14px;
  box-shadow:0 -10px 30px rgba(0,0,0,0.22);
  display:flex;
  flex-direction:column;
  gap:9px;
}
.sheet-handle {
  width:34px;
  height:4px;
  border-radius:99px;
  background:$borderHex;
  margin:0 auto 2px;
}
.sheet-top {
  display:flex;
  justify-content:space-between;
  align-items:center;
}
.sheet-title {
  font-size:12.5px;
  font-weight:800;
  color:$textPrimaryHex;
}
.sheet-close {
  font-size:11px;
  font-weight:700;
  color:$textSecHex;
  cursor:pointer;
  padding:2px 6px;
}
.gen-chips {
  display:flex;
  gap:6px;
  overflow-x:auto;
  padding-bottom:2px;
  touch-action:pan-x;
}
.gen-chips::-webkit-scrollbar { display:none; }
.gen-chip {
  font-size:10px;
  font-weight:700;
  padding:5px 11px;
  border-radius:999px;
  background:$bgHex;
  border:1px solid $borderHex;
  color:$textSecHex;
  cursor:pointer;
  white-space:nowrap;
}
.gen-chip.active {
  background:$goldHex;
  color:#FFFFFF;
  border-color:$goldHex;
}
.grid-pills {
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:6px;
  max-height:190px;
  overflow-y:auto;
  touch-action:pan-y;
}
.member-pill-card {
  display:flex;
  align-items:center;
  gap:7px;
  padding:7px 9px;
  border-radius:11px;
  background:$bgHex;
  border:1px solid $borderHex;
  cursor:pointer;
  min-width:0;
}
.member-pill-card.active {
  border-color:$goldHex;
  box-shadow:inset 0 0 0 1px $goldHex;
}
.pop-avatar {
  width:24px;
  height:24px;
  border-radius:50%;
  background:$cardHex;
  border:1px solid $borderHex;
  color:$goldHex;
  font-size:9.5px;
  font-weight:800;
  display:flex;
  align-items:center;
  justify-content:center;
  flex-shrink:0;
}
.pop-name {
  font-size:11px;
  font-weight:700;
  color:$textPrimaryHex;
  white-space:nowrap;
  overflow:hidden;
  text-overflow:ellipsis;
}
.pop-sub {
  font-size:9.5px;
  color:$textSecHex;
  white-space:nowrap;
  overflow:hidden;
  text-overflow:ellipsis;
}

/* Educational Tap Explainer Pill */
.edu-toast {
  position:absolute;
  top:8px;
  left:50%;
  transform:translateX(-50%);
  background:$bgHex;
  border:1px solid $goldHex;
  border-radius:999px;
  padding:5px 12px;
  font-size:10.5px;
  font-weight:700;
  color:$textPrimaryHex;
  box-shadow:0 4px 14px rgba(0,0,0,0.16);
  z-index:22;
  white-space:nowrap;
  max-width:94%;
  overflow:hidden;
  text-overflow:ellipsis;
  pointer-events:none;
  opacity:0;
  transition:opacity 0.2s ease;
}
.edu-toast.show { opacity:1; }

/* UI 2: Centered Bottom Floating HUD Pill ("−  100%  +  |  ⟲") */
.hud-pill {
  position:absolute;
  bottom:10px;
  left:50%;
  transform:translateX(-50%);
  background:$bgHex;
  border:1px solid $borderHex;
  box-shadow:0 6px 18px rgba(0,0,0,0.18);
  border-radius:999px;
  padding:3px 8px;
  display:inline-flex;
  align-items:center;
  gap:4px;
  z-index:20;
}
.hud-btn {
  width:27px;
  height:27px;
  border-radius:50%;
  border:none;
  background:transparent;
  color:$goldHex;
  font-size:15.5px;
  font-weight:800;
  display:flex;
  align-items:center;
  justify-content:center;
  cursor:pointer;
}
.hud-btn:active { opacity:0.65; transform:scale(0.92); }
.hud-pct {
  font-size:11px;
  font-weight:800;
  color:$textPrimaryHex;
  min-width:42px;
  text-align:center;
  cursor:pointer;
}
.hud-div { width:1px; height:14px; background:$borderHex; margin:0 2px; }
.legend-row { width:100%; display:flex; flex-wrap:wrap; gap:10px; justify-content:center; padding-top:8px; border-top:1px solid $borderHex; flex-shrink:0; }
.legend-item { display:inline-flex; align-items:center; gap:5px; font-size:10.5px; font-weight:600; color:$textSecHex; }
.swatch { width:9px; height:9px; border-radius:3px; border:1px solid rgba(0,0,0,0.18); }
</style></head><body>
<div class="fan-card">
  <div class="fan-header">
    <div>
      <div class="fan-title">Horseshoe Fan Chart</div>
      <div class="fan-sub">Tap wedge to inspect · Drag to pan · Pinch to zoom</div>
    </div>
    <div class="header-right">
      <div class="custom-trigger-pill" id="triggerPill" onclick="openRootDrawer()">
        <span class="dot"></span>
        <span id="triggerText">All Clan</span>
        <span style="font-size:9px; opacity:0.8;">▾</span>
      </div>
      <span class="fan-badge" id="genBadge">0 Gens</span>
    </div>
  </div>

  <div class="viewport" id="viewport">
    <div class="edu-toast" id="eduToast"></div>
    <canvas id="fan" width="840" height="840"></canvas>

    <div class="hud-pill" id="hudPill">
      <button class="hud-btn" onclick="stepZoom(-0.25)" aria-label="Zoom Out">−</button>
      <span class="hud-pct" id="zoomPct" onclick="resetTransform()">100%</span>
      <button class="hud-btn" onclick="stepZoom(0.25)" aria-label="Zoom In">+</button>
      <div class="hud-div"></div>
      <button class="hud-btn" onclick="resetTransform()" aria-label="Reset View" style="font-size:13.5px;">⟲</button>
    </div>
  </div>

  <!-- UI 2: Compact Slide-Up Bottom Drawer inside Fan Card -->
  <div class="ui2-backdrop" id="ui2Backdrop" onclick="if(event.target===this) closeRootDrawer()">
    <div class="ui2-sheet">
      <div class="sheet-handle"></div>
      <div class="sheet-top">
        <span class="sheet-title">Center Fan Chart On</span>
        <span class="sheet-close" onclick="closeRootDrawer()">Done ✕</span>
      </div>
      <div class="gen-chips" id="ui2GenChips"></div>
      <div class="grid-pills" id="ui2Grid"></div>
    </div>
  </div>

  <div class="legend-row" id="legendRow"></div>
</div>
<script>
const canvas = document.getElementById('fan');
const ctx = canvas.getContext('2d');
const viewport = document.getElementById('viewport');
const zoomPctEl = document.getElementById('zoomPct');
const triggerTextEl = document.getElementById('triggerText');
const ui2Backdrop = document.getElementById('ui2Backdrop');
const ui2GenChips = document.getElementById('ui2GenChips');
const ui2Grid = document.getElementById('ui2Grid');
const eduToast = document.getElementById('eduToast');
const isDark = $isNightMode;

let allPersons = [];
let selectableRoots = [];
let currentMode = 'clan'; // 'clan' or personId for Ancestor Ahnentafel fan
let hasAutoSelectedRoot = false;
let activeGenFilter = 0;
let selectedWedgeId = null;
let hitWedges = [];

let scale = 1;
let panX = 0;
let panY = 0;
const MIN_SCALE = 0.6;
const MAX_SCALE = 3.5;

function applyTransform(animate) {
  canvas.style.transition = animate ? 'transform 0.14s ease-out' : 'none';
  canvas.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) scale(' + scale + ')';
  zoomPctEl.textContent = Math.round(scale * 100) + '%';
}

function clampScale(s) {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));
}

function stepZoom(delta) {
  scale = clampScale(+(scale + delta).toFixed(2));
  applyTransform(true);
}

function resetTransform() {
  scale = 1;
  panX = 0;
  panY = 0;
  applyTransform(true);
}

// ── 1-Finger Drag Pan + 2-Finger Pinch-to-Zoom + Double-Tap ──
let isDragging = false;
let dragMoved = false;
let dragStartX = 0;
let dragStartY = 0;
let initialPinchDist = null;
let initialPinchScale = 1;
let lastTapTime = 0;

function getTouchDist(t1, t2) {
  const dx = t1.clientX - t2.clientX;
  const dy = t1.clientY - t2.clientY;
  return Math.hypot(dx, dy);
}

viewport.addEventListener('touchstart', function(e) {
  if (e.target.closest('#hudPill')) return;
  if (e.touches.length === 2) {
    isDragging = false;
    initialPinchDist = getTouchDist(e.touches[0], e.touches[1]);
    initialPinchScale = scale;
  } else if (e.touches.length === 1) {
    const now = Date.now();
    if (now - lastTapTime < 260) {
      if (scale > 1.2) {
        resetTransform();
      } else {
        scale = 1.75;
        applyTransform(true);
      }
      lastTapTime = 0;
      return;
    }
    lastTapTime = now;
    isDragging = true;
    dragMoved = false;
    dragStartX = e.touches[0].clientX - panX;
    dragStartY = e.touches[0].clientY - panY;
  }
}, { passive: true });

viewport.addEventListener('touchmove', function(e) {
  if (e.target.closest('#hudPill')) return;
  e.preventDefault();
  if (e.touches.length === 2 && initialPinchDist) {
    dragMoved = true;
    const dist = getTouchDist(e.touches[0], e.touches[1]);
    const ratio = dist / initialPinchDist;
    scale = clampScale(initialPinchScale * ratio);
    applyTransform(false);
  } else if (e.touches.length === 1 && isDragging) {
    const nx = e.touches[0].clientX - dragStartX;
    const ny = e.touches[0].clientY - dragStartY;
    if (Math.hypot(nx - panX, ny - panY) > 5) dragMoved = true;
    panX = nx;
    panY = ny;
    applyTransform(false);
  }
}, { passive: false });

viewport.addEventListener('touchend', function(e) {
  if (e.touches.length < 2) initialPinchDist = null;
  if (e.touches.length === 0) isDragging = false;
});

// Mouse drag & wheel support
let mouseDown = false;
viewport.addEventListener('mousedown', function(e) {
  if (e.target.closest('#hudPill')) return;
  mouseDown = true;
  dragMoved = false;
  dragStartX = e.clientX - panX;
  dragStartY = e.clientY - panY;
});
window.addEventListener('mousemove', function(e) {
  if (!mouseDown) return;
  const nx = e.clientX - dragStartX;
  const ny = e.clientY - dragStartY;
  if (Math.hypot(nx - panX, ny - panY) > 5) dragMoved = true;
  panX = nx;
  panY = ny;
  applyTransform(false);
});
window.addEventListener('mouseup', function() { mouseDown = false; });
viewport.addEventListener('wheel', function(e) {
  e.preventDefault();
  stepZoom(e.deltaY < 0 ? 0.15 : -0.15);
}, { passive: false });

// Educational tap handler on wedges
canvas.addEventListener('click', function(e) {
  if (dragMoved) return;
  const rect = canvas.getBoundingClientRect();
  const x = (e.clientX - rect.left) * (canvas.width / rect.width);
  const y = (e.clientY - rect.top) * (canvas.height / rect.height);
  const cx = canvas.width / 2;
  const cy = canvas.height * 0.555;
  const dx = x - cx;
  const dy = y - cy;
  const r = Math.hypot(dx, dy);
  let ang = Math.atan2(dy, dx);
  if (ang > Math.PI * 0.45) ang -= 2 * Math.PI;

  for (let i = 0; i < hitWedges.length; i++) {
    const w = hitWedges[i];
    if (r >= w.r1 && r <= w.r2 && ang >= w.a1 && ang <= w.a2) {
      selectedWedgeId = w.person.id;
      const roleInfo = w.roleLabel || w.person.kinship || ('Gen ' + (w.ringIndex + 1));
      const yrs = w.person.years ? ' ' + w.person.years : '';
      showEduToast(w.person.firstName + ' ' + w.person.lastName + yrs + ' · ' + roleInfo);
      renderCurrentChart();
      return;
    }
  }
});

function showEduToast(msg) {
  eduToast.textContent = msg;
  eduToast.classList.add('show');
  clearTimeout(window._eduTimer);
  window._eduTimer = setTimeout(function() {
    eduToast.classList.remove('show');
  }, 3000);
}

// UI 1: Classic Archival Pastel Palette (Reference-Faithful + High Contrast)
function getArchivalPalette() {
  if (isDark) {
    return {
      center: ['#2C2618', '#3F341D', '#D4AF37', '#F8F5EE'],
      rings: [
        ['#223824', '#2F4D32', '#6CA870', '#ECF6ED'],
        ['#1B3A36', '#25524C', '#58B0A3', '#E8F7F5'],
        ['#1A3542', '#244B5E', '#5BA8CF', '#E8F4FA'],
        ['#232E42', '#31405C', '#7C98CC', '#ECF0F9']
      ]
    };
  }
  return {
    center: ['#FFFDF7', '#F9ECCB', '#C9B58A', '#111613'],
    rings: [
      ['#F2F8EA', '#D7ECC4', '#A5C48B', '#111A13'],
      ['#EBF7F2', '#BCE4D5', '#8BBDAE', '#101C18'],
      ['#EAF6FB', '#B9E3F4', '#89BCD2', '#0F1B22'],
      ['#EDF2FA', '#C2D4EC', '#93A9C9', '#121A26']
    ]
  };
}

function openRootDrawer() {
  renderDrawerChipsAndGrid();
  ui2Backdrop.classList.add('open');
}

function closeRootDrawer() {
  ui2Backdrop.classList.remove('open');
}

function filterDrawerGen(gen) {
  activeGenFilter = gen;
  renderDrawerChipsAndGrid();
}

function getInitials(p) {
  const f = (p.firstName || '').trim();
  const l = (p.lastName || '').trim();
  return ((f ? f[0] : '') + (l ? l[0] : '')).toUpperCase() || 'M';
}

function getShortName(p) {
  const f = (p.firstName || '').trim();
  const l = (p.lastName || '').trim();
  return f + (l ? ' ' + l[0] + '.' : '');
}

function populateModeDropdown(persons) {
  const byId = {};
  persons.forEach(p => { byId[p.id] = p; });

  selectableRoots = persons.filter(p => (p.fatherId && byId[p.fatherId]) || (p.motherId && byId[p.motherId]));
  selectableRoots.sort((a, b) => (b.generation || 1) - (a.generation || 1));

  if (currentMode !== 'clan' && !byId[currentMode]) {
    currentMode = 'clan';
  }
  updateTriggerLabel(byId);
  renderDrawerChipsAndGrid();
}

function updateTriggerLabel(byId) {
  if (currentMode === 'clan' || !byId[currentMode]) {
    triggerTextEl.textContent = 'All Clan';
  } else {
    triggerTextEl.textContent = getShortName(byId[currentMode]);
  }
}

function renderDrawerChipsAndGrid() {
  const gens = [];
  selectableRoots.forEach(p => {
    const g = p.generation || 1;
    if (gens.indexOf(g) === -1) gens.push(g);
  });
  gens.sort((a, b) => b - a);

  let chipsHtml = '<span class="gen-chip ' + (activeGenFilter === 0 ? 'active' : '') + '" onclick="filterDrawerGen(0)">All (' + selectableRoots.length + ')</span>';
  gens.forEach((g, idx) => {
    const suffix = idx === 0 ? ' (Youngest)' : '';
    chipsHtml += '<span class="gen-chip ' + (activeGenFilter === g ? 'active' : '') + '" onclick="filterDrawerGen(' + g + ')">Gen ' + g + suffix + '</span>';
  });
  ui2GenChips.innerHTML = chipsHtml;

  const filtered = activeGenFilter === 0
    ? selectableRoots
    : selectableRoots.filter(p => (p.generation || 1) === activeGenFilter);

  let gridHtml = '<div class="member-pill-card ' + (currentMode === 'clan' ? 'active' : '') + '" onclick="selectRootMode(\'clan\')">' +
    '<div class="pop-avatar">🌐</div>' +
    '<div style="min-width:0;">' +
      '<div class="pop-name">All Clan</div>' +
      '<div class="pop-sub">Full Overview</div>' +
    '</div>' +
  '</div>';

  filtered.forEach(p => {
    const isAct = currentMode === p.id;
    const sub = 'Gen ' + (p.generation || 1) + (p.years ? ' · ' + p.years : '');
    gridHtml += '<div class="member-pill-card ' + (isAct ? 'active' : '') + '" onclick="selectRootMode(\'' + p.id + '\')">' +
      '<div class="pop-avatar">' + getInitials(p) + '</div>' +
      '<div style="min-width:0;">' +
        '<div class="pop-name">' + getShortName(p) + '</div>' +
        '<div class="pop-sub">' + sub + '</div>' +
      '</div>' +
    '</div>';
  });
  ui2Grid.innerHTML = gridHtml;
}

function selectRootMode(val) {
  currentMode = val;
  selectedWedgeId = null;
  const byId = {};
  allPersons.forEach(p => { byId[p.id] = p; });
  updateTriggerLabel(byId);
  closeRootDrawer();
  resetTransform();
  renderCurrentChart();
}

// Build binary Ahnentafel ancestor rings for a chosen root person
function buildAncestorStructure(rootPerson, byId) {
  const rings = [];
  let currentLevel = [rootPerson];
  for (let depth = 1; depth <= 4; depth++) {
    const nextLevel = [];
    let anyFound = false;
    currentLevel.forEach(p => {
      const father = (p && p.fatherId && byId[p.fatherId]) ? byId[p.fatherId] : null;
      const mother = (p && p.motherId && byId[p.motherId]) ? byId[p.motherId] : null;
      if (father || mother) anyFound = true;
      nextLevel.push(father);
      nextLevel.push(mother);
    });
    if (!anyFound) break;
    rings.push(nextLevel);
    currentLevel = nextLevel;
  }
  return {
    isAncestorMode: true,
    centerTitle: rootPerson.firstName || 'Root',
    centerSurname: rootPerson.lastName || '',
    centerSub: rootPerson.years || ('Gen ' + (rootPerson.generation || 1)),
    rings: rings
  };
}

// Build Clan Generational Horseshoe structure (Gen 1 in Center/Ring 1 -> Outer Rings)
function buildClanStructure(persons) {
  const genMap = {};
  persons.forEach(p => {
    const g = p.generation || 1;
    if (!genMap[g]) genMap[g] = [];
    genMap[g].push(p);
  });
  const sortedGens = Object.keys(genMap).map(Number).sort((a, b) => a - b);
  const firstGen = genMap[sortedGens[0]] || [];

  if (sortedGens.length === 1) {
    return {
      isAncestorMode: false,
      centerTitle: firstGen[0] ? firstGen[0].firstName : 'Clan',
      centerSurname: firstGen[0] ? firstGen[0].lastName : 'Root',
      centerSub: firstGen[0] ? (firstGen[0].years || 'Gen 1') : '',
      rings: [firstGen]
    };
  }

  // If Gen 1 has 1 person, put them in Center Medallion and Gen 2..N as rings.
  // If Gen 1 has multiple founders, show Founders summary in Center Medallion and Gen 1..N as separated rings!
  if (firstGen.length === 1) {
    const rootP = firstGen[0];
    return {
      isAncestorMode: false,
      centerTitle: rootP.firstName,
      centerSurname: rootP.lastName,
      centerSub: rootP.years || 'Gen 1 Founder',
      rings: sortedGens.slice(1).map(g => genMap[g]),
      ringGenNumbers: sortedGens.slice(1)
    };
  } else {
    const surnames = [];
    firstGen.forEach(p => {
      if (p.lastName && surnames.indexOf(p.lastName) === -1) surnames.push(p.lastName);
    });
    const clanTitle = surnames.slice(0, 2).join(' – ') || 'Family Clan';
    return {
      isAncestorMode: false,
      centerTitle: clanTitle,
      centerSurname: 'CLAN ROOTS',
      centerSub: '(' + persons.length + ' Members · ' + sortedGens.length + ' Gens)',
      rings: sortedGens.map(g => genMap[g]),
      ringGenNumbers: sortedGens
    };
  }
}

function renderCurrentChart() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  hitWedges = [];

  const cx = canvas.width / 2;
  const cy = canvas.height * 0.555;

  if (!allPersons || allPersons.length === 0) {
    ctx.fillStyle = '$textSecHex';
    ctx.font = '18px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('No family members to display', cx, cy);
    return;
  }

  const byId = {};
  allPersons.forEach(p => { byId[p.id] = p; });

  const struct = (currentMode !== 'clan' && byId[currentMode])
    ? buildAncestorStructure(byId[currentMode], byId)
    : buildClanStructure(allPersons);

  const pal = getArchivalPalette();

  // 1. Subtle background quadrant crosshair lines (like the reference image)
  ctx.save();
  ctx.strokeStyle = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(18,32,26,0.08)';
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(0, cy);
  ctx.lineTo(canvas.width, cy);
  ctx.moveTo(cx, 0);
  ctx.lineTo(cx, canvas.height);
  ctx.stroke();
  ctx.restore();

  // 2. 250-degree Open Horseshoe Geometry (-215 deg to +35 deg)
  const startRad = -215 * Math.PI / 180;
  const endRad = 35 * Math.PI / 180;
  const totalSpan = endRad - startRad;

  const numRings = Math.max(1, struct.rings.length);
  const rCenter = 92;
  const maxRadius = 392;
  const ringGap = 6;
  const ringThickness = (maxRadius - rCenter - numRings * ringGap) / numRings;

  document.getElementById('genBadge').textContent = (struct.isAncestorMode ? (numRings + 1) : struct.rings.length) + ' Gens';

  // 3. Draw Each Ring's Separated Wedge Cards
  struct.rings.forEach(function(ringMembers, ri) {
    const r1 = rCenter + ri * (ringThickness + ringGap) + ringGap;
    const r2 = r1 + ringThickness;
    const count = Math.max(1, ringMembers.length);
    const wedgeSpan = totalSpan / count;
    const midR = (r1 + r2) / 2;
    const angGap = count === 1 ? 0 : Math.min(wedgeSpan * 0.10, 5.0 / midR);
    const colors = pal.rings[ri % pal.rings.length];

    ringMembers.forEach(function(p, wi) {
      const a1 = startRad + wi * wedgeSpan + angGap / 2;
      const a2 = startRad + (wi + 1) * wedgeSpan - angGap / 2;
      const midA = (a1 + a2) / 2;

      if (!p) {
        // Ghost slot for unknown ancestor in binary Ahnentafel mode
        ctx.save();
        ctx.beginPath();
        ctx.arc(cx, cy, r2, a1, a2, false);
        ctx.arc(cx, cy, r1, a2, a1, true);
        ctx.closePath();
        ctx.setLineDash([5, 5]);
        ctx.strokeStyle = isDark ? 'rgba(255,255,255,0.12)' : 'rgba(18,32,26,0.14)';
        ctx.lineWidth = 1.2;
        ctx.stroke();
        ctx.restore();
        return;
      }

      const roleLabel = struct.isAncestorMode
        ? (ri === 0 ? (wi === 0 ? 'Father' : 'Mother') : (wi < count / 2 ? 'Paternal Gen ' + (ri + 2) : 'Maternal Gen ' + (ri + 2)))
        : ('Gen ' + (p.generation || (ri + 1)) + (p.kinship ? ' · ' + p.kinship : ''));

      hitWedges.push({ r1: r1, r2: r2, a1: a1, a2: a2, person: p, ringIndex: ri, roleLabel: roleLabel });

      const isSelected = (selectedWedgeId === p.id);

      // Separated wedge card with radial gradient + subtle shadow
      ctx.save();
      ctx.shadowColor = isDark ? 'rgba(0,0,0,0.45)' : 'rgba(0,0,0,0.12)';
      ctx.shadowBlur = isSelected ? 10 : 6;
      ctx.shadowOffsetY = 2;

      const grad = ctx.createRadialGradient(cx, cy, r1, cx, cy, r2);
      grad.addColorStop(0, colors[0]);
      grad.addColorStop(1, colors[1]);

      ctx.beginPath();
      ctx.arc(cx, cy, r2, a1, a2, false);
      ctx.arc(cx, cy, r1, a2, a1, true);
      ctx.closePath();

      ctx.fillStyle = grad;
      ctx.fill();

      ctx.shadowColor = 'transparent';
      ctx.strokeStyle = isSelected ? '$goldHex' : colors[2];
      ctx.lineWidth = isSelected ? 3.2 : 1.7;
      ctx.stroke();
      ctx.restore();

      // Typography inside wedge:
      // Wide arc -> Tangential; Narrower arc -> Upright Radial
      const arcLen = midR * (a2 - a1);
      const isTangential = arcLen > ringThickness * 1.18;

      const firstName = (p.firstName || p.name || '').trim();
      const lastName = (p.lastName || '').trim();
      const subLine = p.years || p.kinship || ('Gen ' + (p.generation || (ri + 1)));

      ctx.save();
      const tx = cx + midR * Math.cos(midA);
      const ty = cy + midR * Math.sin(midA);
      ctx.translate(tx, ty);
      ctx.fillStyle = colors[3];
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';

      if (isTangential) {
        let rot = midA + Math.PI / 2;
        if (rot > Math.PI / 2 && rot < 3 * Math.PI / 2) {
          rot += Math.PI;
        }
        ctx.rotate(rot);

        const fontSize = ri === 0 ? 15 : (ri === 1 ? 13 : 11.5);
        ctx.font = '700 ' + fontSize + 'px -apple-system, sans-serif';
        ctx.fillText((firstName + ' ' + lastName).trim(), 0, -8);

        ctx.font = '600 ' + (fontSize - 1.5) + 'px -apple-system, sans-serif';
        ctx.globalAlpha = 0.88;
        ctx.fillText(subLine, 0, 9);
      } else {
        // Auto-upright radial orientation (left side vs right side)
        const isLeftSide = midA < -Math.PI / 2;
        const rot = isLeftSide ? (midA + Math.PI) : midA;
        ctx.rotate(rot);

        const fontSize = count >= 12 ? 10.5 : (count >= 8 ? 11.5 : 12.5);
        ctx.font = '600 ' + fontSize + 'px -apple-system, sans-serif';
        ctx.fillText(firstName, 0, -11);

        ctx.font = '800 ' + fontSize + 'px -apple-system, sans-serif';
        ctx.fillText(lastName.toUpperCase(), 0, 0);

        ctx.font = '500 ' + (fontSize - 1) + 'px -apple-system, sans-serif';
        ctx.globalAlpha = 0.86;
        ctx.fillText(subLine, 0, 11);
      }
      ctx.restore();
    });
  });

  // 4. Flat-Bottom Center Medallion (matches reference image)
  ctx.save();
  ctx.shadowColor = isDark ? 'rgba(0,0,0,0.5)' : 'rgba(0,0,0,0.14)';
  ctx.shadowBlur = 8;
  ctx.shadowOffsetY = 2;

  const cGrad = ctx.createRadialGradient(cx, cy - 10, 8, cx, cy, rCenter);
  cGrad.addColorStop(0, pal.center[0]);
  cGrad.addColorStop(1, pal.center[1]);

  ctx.beginPath();
  ctx.arc(cx, cy, rCenter, startRad, endRad, false);
  ctx.closePath();

  ctx.fillStyle = cGrad;
  ctx.fill();
  ctx.shadowColor = 'transparent';
  ctx.strokeStyle = pal.center[2];
  ctx.lineWidth = 2.2;
  ctx.stroke();

  ctx.fillStyle = pal.center[3];
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';

  ctx.font = '800 15.5px -apple-system, sans-serif';
  ctx.fillText(struct.centerTitle, cx, cy - 19);
  ctx.font = '800 15px -apple-system, sans-serif';
  ctx.fillText(struct.centerSurname, cx, cy - 1);
  ctx.font = '600 12.5px -apple-system, sans-serif';
  ctx.globalAlpha = 0.88;
  ctx.fillText(struct.centerSub, cx, cy + 17);
  ctx.restore();

  // 5. Educational Hemisphere Micro-Guides in the open horseshoe base
  ctx.save();
  ctx.fillStyle = '$textSecHex';
  ctx.globalAlpha = 0.78;
  ctx.font = '700 11px -apple-system, sans-serif';
  ctx.textAlign = 'center';
  if (struct.isAncestorMode) {
    ctx.fillText('← Father’s Line (Paternal)   ·   Mother’s Line (Maternal) →', cx, cy + 82);
  } else {
    ctx.fillText('Inner Ring = Older Gen  ·  Outer Ring = Newer Gen', cx, cy + 82);
  }
  ctx.restore();

  // 6. Update Legend Row
  const legendRow = document.getElementById('legendRow');
  const items = [];
  items.push('<span class="legend-item"><span class="swatch" style="background:' + pal.center[1] + ';"></span>Center Root</span>');
  struct.rings.forEach(function(ring, idx) {
    const c = pal.rings[idx % pal.rings.length][1];
    const popCount = ring.filter(Boolean).length;
    const lbl = struct.isAncestorMode
      ? (idx === 0 ? 'Parents' : (idx === 1 ? 'Grandparents' : 'Gen ' + (idx + 2)))
      : ('Gen ' + (struct.ringGenNumbers ? struct.ringGenNumbers[idx] : (idx + 1)));
    items.push('<span class="legend-item"><span class="swatch" style="background:' + c + ';"></span>' + lbl + ' (' + popCount + ')</span>');
  });
  legendRow.innerHTML = items.join('');
}

function initFanChart(persons) {
  allPersons = persons || [];
  populateModeDropdown(allPersons);
  renderCurrentChart();
}

window.updateFanChart = function(newPersons) {
  initFanChart(newPersons);
};

initFanChart($nodesJson);
</script></body></html>""".trimIndent()
    }
}
