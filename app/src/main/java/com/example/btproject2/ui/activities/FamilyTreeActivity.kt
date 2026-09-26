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
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            treeAdapter.submitItems(items, allPersons, allPersons.firstOrNull())
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
                treeAdapter.submitItems(items, allPersons, allPersons.firstOrNull())
                if (webViewFan.visibility == android.view.View.VISIBLE) {
                    renderFanChart(allPersons)
                }
            }
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

        val tvTreeRoleBadge = findViewById<TextView>(R.id.tvTreeRoleBadge)
        val layoutViewerBanner = findViewById<android.view.View>(R.id.layoutViewerBanner)
        val authHelper = com.example.btproject2.firebase.AuthHelper()
        val currentUserId = authHelper.getCurrentUserId() ?: ""

        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (isOwner) {
                    tvTreeRoleBadge.text = "★ Owner"
                    layoutViewerBanner.visibility = android.view.View.GONE
                } else {
                    firestoreHelper.getUserMembership(currentUserId, treeId,
                        onSuccess = { member ->
                            val role = member?.role ?: "Viewer"
                            when (role.lowercase()) {
                                "editor" -> {
                                    tvTreeRoleBadge.text = "✏ Editor"
                                    layoutViewerBanner.visibility = android.view.View.GONE
                                }
                                else -> {
                                    tvTreeRoleBadge.text = "👁 Viewer"
                                    layoutViewerBanner.visibility = android.view.View.VISIBLE
                                }
                            }
                        },
                        onFailure = {
                            tvTreeRoleBadge.text = "👁 Viewer"
                            layoutViewerBanner.visibility = android.view.View.VISIBLE
                        }
                    )
                }
            },
            onFailure = {
                tvTreeRoleBadge.text = "★ Owner"
                layoutViewerBanner.visibility = android.view.View.GONE
            }
        )

        // RecyclerView setup
        treeAdapter = TreeMemberAdapter { person ->
            val intent = Intent(this, MemberDetailActivity::class.java).apply {
                putExtra("personId", person.id)
                putExtra("TREE_ID", treeId)
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

        // Tab buttons
        btnPedigree.setOnClickListener { switchTab(pedigree = true) }
        btnFanChart.setOnClickListener { switchTab(pedigree = false) }
        btnInteractiveTree.setOnClickListener {
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
    }

    override fun onResume() {
        super.onResume()
        loadTree()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncCoordinator.unregister()
    }

    private fun switchTab(pedigree: Boolean) {
        if (pedigree) {
            btnPedigree.setBackgroundResource(R.drawable.btn_gold_primary)
            btnPedigree.setTextColor(android.graphics.Color.parseColor("#0A1B12"))
            btnFanChart.setBackgroundResource(R.drawable.btn_dark_secondary)
            btnFanChart.setTextColor(resources.getColor(R.color.text_secondary, null))
            btnInteractiveTree.setBackgroundResource(R.drawable.btn_dark_secondary)
            btnInteractiveTree.setTextColor(resources.getColor(R.color.text_secondary, null))
            rvTree.visibility = android.view.View.VISIBLE
            webViewFan.visibility = android.view.View.GONE
        } else {
            btnFanChart.setBackgroundResource(R.drawable.btn_gold_primary)
            btnFanChart.setTextColor(android.graphics.Color.parseColor("#0A1B12"))
            btnPedigree.setBackgroundResource(R.drawable.btn_dark_secondary)
            btnPedigree.setTextColor(resources.getColor(R.color.text_secondary, null))
            btnInteractiveTree.setBackgroundResource(R.drawable.btn_dark_secondary)
            btnInteractiveTree.setTextColor(resources.getColor(R.color.text_secondary, null))
            rvTree.visibility = android.view.View.GONE
            webViewFan.visibility = android.view.View.VISIBLE
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

    private fun loadTree() {
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                val activePersons = FirestoreHelper.sanitizeTreeRecords(persons).filter { it.id !in recentlyDeletedIds }
                allPersons = activePersons
                syncCoordinator.allPersonsList = activePersons
                tvMemberCount.text = "${activePersons.size} members"
                Thread {
                    val genMap = computeGenerationMap(activePersons)
                    val items = buildGenerationItems(activePersons, genMap)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        treeAdapter.submitItems(items, activePersons, activePersons.firstOrNull())
                        if (webViewFan.visibility == android.view.View.VISIBLE) {
                            renderFanChart(activePersons, genMap)
                        }
                    }
                }.start()
            },
            onFailure = {
                tvMemberCount.text = "Failed to load"
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
            items.add(TreeMemberAdapter.TreeItem.Header(gen))
            grouped[gen]
                ?.sortedWith(compareBy({ it.birthDate.ifEmpty { "9999" } }, { it.firstName }))
                ?.forEach { items.add(TreeMemberAdapter.TreeItem.Member(it)) }
        }
        return items
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun renderFanChart(
        persons: List<Person>,
        genMap: Map<String, Int> = computeGenerationMap(persons)
    ) {
        val nodesJson = syncCoordinator.buildFanChartNodesJson(persons, genMap, persons.firstOrNull())
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

    private fun buildFanChartHtml(nodesJson: String): String = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=yes">
<style>
* { margin:0; padding:0; box-sizing:border-box; }
body { background:#FAFAF7; font-family:-apple-system,sans-serif; display:flex; justify-content:center; align-items:center; min-height:100vh; }
canvas { max-width:100%; }
.label { position:absolute; font-size:9px; color:#2C2A24; text-align:center; pointer-events:none; }
</style></head><body>
<canvas id="fan" width="700" height="700"></canvas>
<script>
const canvas = document.getElementById('fan');
const ctx = canvas.getContext('2d');
const cx = canvas.width/2, cy = canvas.height/2;

function drawFanChart(persons) {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (!persons || persons.length === 0) {
    ctx.fillStyle = '#666';
    ctx.font = '16px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('No family members to display', cx, cy);
    return;
  }

  // Group members strictly by their computed generation
  const genMap = {};
  persons.forEach(p => {
    const g = p.generation || 1;
    if (!genMap[g]) genMap[g] = [];
    genMap[g].push(p);
  });

  const sortedGens = Object.keys(genMap).map(Number).sort((a,b) => a - b);
  const generations = sortedGens.map(g => genMap[g]);

  const colors = ['#1D9E75','#185FA5','#993556','#FAC775','#7F77DD','#D85A30'];
  const maxGen = generations.length;
  const ringWidth = Math.min(cx, cy) / (maxGen + 1) * 0.85;

  generations.forEach((gen, gi) => {
    const r1 = gi === 0 ? 0 : gi * ringWidth;
    const r2 = (gi + 1) * ringWidth;
    const total = gen.length;
    const color = colors[gi % colors.length];

    gen.forEach((p, pi) => {
      const a1 = (2 * Math.PI * pi / total) - Math.PI/2;
      const a2 = (2 * Math.PI * (pi + 1) / total) - Math.PI/2;
      const mid = (a1 + a2) / 2;

      if (gi === 0) {
        if (total === 1) {
          // Single root member in generation 1 occupies the full center circle
          ctx.beginPath();
          ctx.arc(cx, cy, r2, 0, 2 * Math.PI);
          ctx.fillStyle = color;
          ctx.fill();
          ctx.strokeStyle = '#FAFAF7';
          ctx.lineWidth = 2;
          ctx.stroke();
        } else {
          // Multiple root members (e.g. Pedro and Clara) form distinct pie sectors
          ctx.beginPath();
          ctx.moveTo(cx, cy);
          ctx.arc(cx, cy, r2, a1, a2);
          ctx.closePath();
          ctx.fillStyle = color;
          ctx.fill();
          ctx.strokeStyle = '#FAFAF7';
          ctx.lineWidth = 2;
          ctx.stroke();
        }
      } else {
        // Annular ring sectors for outer generations
        ctx.beginPath();
        ctx.moveTo(cx + r1 * Math.cos(a1), cy + r1 * Math.sin(a1));
        ctx.arc(cx, cy, r2, a1, a2);
        ctx.arc(cx, cy, r1, a2, a1, true);
        ctx.closePath();
        ctx.fillStyle = color + 'CC';
        ctx.fill();
        ctx.strokeStyle = '#FAFAF7';
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      // Name label
      const lr = gi === 0 ? (total === 1 ? 0 : r2 * 0.52) : (r1 + r2) / 2;
      const lx = (gi === 0 && total === 1) ? cx : cx + lr * Math.cos(mid);
      const ly = (gi === 0 && total === 1) ? cy : cy + lr * Math.sin(mid);

      ctx.save();
      ctx.translate(lx, ly);
      if (!(gi === 0 && total === 1)) {
        ctx.rotate(mid + Math.PI / 2);
      }
      const parts = (p.name || '').trim().split(/\s+/);
      const name = parts[0] + (parts[1] ? ' ' + parts[1][0] + '.' : '');
      if (p.kinship && gi > 0) {
        ctx.font = 'bold ' + Math.max(8, 11 - gi * 1.5) + 'px sans-serif';
        ctx.fillText(name, 0, -5);
        ctx.font = 'normal ' + Math.max(7, 9 - gi * 1.5) + 'px sans-serif';
        ctx.fillStyle = 'rgba(255,255,255,0.85)';
        ctx.fillText(p.kinship, 0, 7);
      } else {
        ctx.font = 'bold ' + Math.max(9, 13 - gi * 1.5) + 'px sans-serif';
        ctx.fillText(name, 0, 0);
      }
      ctx.restore();
    });
  });

  // Center dot only if multiple roots in Gen 1
  if (generations.length > 0 && generations[0].length > 1) {
    ctx.beginPath();
    ctx.arc(cx, cy, 6, 0, 2 * Math.PI);
    ctx.fillStyle = '#FAFAF7';
    ctx.fill();
  }
}

window.updateFanChart = function(newPersons) {
  drawFanChart(newPersons);
};

drawFanChart($nodesJson);
</script></body></html>""".trimIndent()
}
