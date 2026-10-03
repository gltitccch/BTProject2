package com.example.btproject2.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.AuditSeverity
import com.example.btproject2.service.FamilyRelationshipService.TreeAuditIssue
import com.example.btproject2.service.FamilyRelationshipService.TreeAuditReport
import com.example.btproject2.ui.adapters.TreeAuditAdapter

/**
 * TreeAuditActivity: Authoritative screen that audits the entire family tree for:
 * - Lineal blood relatives linked as spouses (Art. 37(1) Family Code)
 * - Full/half siblings linked as spouses (Art. 37(2) Family Code)
 * - Collateral blood relatives up to 4th civil degree linked as spouses (Art. 38(1) Family Code)
 * - Impossible parent-child circular ancestry loops
 * - A person becoming their own ancestor or descendant
 * - A person being linked as both parent and spouse of another person
 * - A son-in-law or daughter-in-law incorrectly saved as a biological child instead of spouse of existing child
 * - Asymmetric or dangling references
 */
class TreeAuditActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val familyService = FamilyRelationshipService()
    private var treeId: String = ""

    private lateinit var btnBack: TextView
    private lateinit var btnRescan: TextView
    private lateinit var tvTotalScannedCount: TextView
    private lateinit var tvCriticalErrorCount: TextView
    private lateinit var tvWarningCount: TextView
    private lateinit var tvSummaryHeadline: TextView

    private lateinit var chipFilterAll: TextView
    private lateinit var chipFilterCritical: TextView
    private lateinit var chipFilterWarnings: TextView

    private lateinit var rvAuditIssues: RecyclerView
    private lateinit var layoutCleanTreeState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var auditAdapter: TreeAuditAdapter
    private var currentReport: TreeAuditReport? = null
    private var currentFilter: String = "ALL" // "ALL", "CRITICAL", "WARNINGS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tree_audit)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        initViews()
        setupListeners()
        setupRecyclerView()

        runTreeAudit()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnRescan = findViewById(R.id.btnRescan)
        tvTotalScannedCount = findViewById(R.id.tvTotalScannedCount)
        tvCriticalErrorCount = findViewById(R.id.tvCriticalErrorCount)
        tvWarningCount = findViewById(R.id.tvWarningCount)
        tvSummaryHeadline = findViewById(R.id.tvSummaryHeadline)

        chipFilterAll = findViewById(R.id.chipFilterAll)
        chipFilterCritical = findViewById(R.id.chipFilterCritical)
        chipFilterWarnings = findViewById(R.id.chipFilterWarnings)

        rvAuditIssues = findViewById(R.id.rvAuditIssues)
        layoutCleanTreeState = findViewById(R.id.layoutCleanTreeState)
        progressBar = findViewById(R.id.progressBarAudit)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnRescan.setOnClickListener { runTreeAudit() }

        findViewById<TextView>(R.id.btnQuickAssistAudit)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.TREE_AUDIT
            ).show(supportFragmentManager, "QuickAssistAudit")
        }

        chipFilterAll.setOnClickListener {
            currentFilter = "ALL"
            updateFilterChipUI()
            applyFilter()
        }

        chipFilterCritical.setOnClickListener {
            currentFilter = "CRITICAL"
            updateFilterChipUI()
            applyFilter()
        }

        chipFilterWarnings.setOnClickListener {
            currentFilter = "WARNINGS"
            updateFilterChipUI()
            applyFilter()
        }
    }

    private fun setupRecyclerView() {
        auditAdapter = TreeAuditAdapter(emptyList()) { personId ->
            val intent = Intent(this, MemberDetailActivity::class.java).apply {
                putExtra("personId", personId)
                putExtra("treeId", treeId)
            }
            startActivity(intent)
        }
        rvAuditIssues.layoutManager = LinearLayoutManager(this)
        rvAuditIssues.adapter = auditAdapter
    }

    private fun runTreeAudit() {
        progressBar.visibility = View.VISIBLE
        rvAuditIssues.visibility = View.GONE
        layoutCleanTreeState.visibility = View.GONE

        firestoreHelper.getPersonsByTree(
            treeId = treeId,
            onSuccess = { persons ->
                val cached = FirestoreHelper.getCachedPersons(treeId).orEmpty()
                val mergedPersons = (persons + cached).distinctBy { it.id.trim().lowercase() }
                val treeMap = mergedPersons.associateBy { it.id }

                // Authoritative Central Service Audit
                val report = familyService.auditFamilyTree(treeMap)
                currentReport = report

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    displayAuditReport(report)
                }
            },
            onFailure = {
                val cached = FirestoreHelper.getCachedPersons(treeId).orEmpty()
                val treeMap = cached.associateBy { it.id }
                val report = familyService.auditFamilyTree(treeMap)
                currentReport = report

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    displayAuditReport(report)
                }
            }
        )
    }

    private fun displayAuditReport(report: TreeAuditReport) {
        tvTotalScannedCount.text = report.totalPersonsScanned.toString()
        tvCriticalErrorCount.text = report.criticalErrorCount.toString()
        tvWarningCount.text = report.warningCount.toString()

        if (report.criticalErrorCount > 0) {
            tvSummaryHeadline.text = "❌ Critical Legal or Structural Violations (${report.criticalErrorCount})"
            tvSummaryHeadline.setTextColor(Color.parseColor("#EF4444"))
        } else if (report.warningCount > 0) {
            tvSummaryHeadline.text = "⚠️ Lineage Warnings Detected (${report.warningCount})"
            tvSummaryHeadline.setTextColor(Color.parseColor("#F59E0B"))
        } else {
            tvSummaryHeadline.text = "✅ 100% Compliant with Philippine Family Code"
            tvSummaryHeadline.setTextColor(Color.parseColor("#34D399"))
        }

        applyFilter()
    }

    private fun updateFilterChipUI() {
        val activeBg = R.drawable.badge_pill_gold
        val inactiveBg = R.drawable.badge_pill_dark

        chipFilterAll.setBackgroundResource(if (currentFilter == "ALL") activeBg else inactiveBg)
        chipFilterAll.setTextColor(if (currentFilter == "ALL") Color.parseColor("#0A1B12") else Color.WHITE)

        chipFilterCritical.setBackgroundResource(if (currentFilter == "CRITICAL") activeBg else inactiveBg)
        chipFilterCritical.setTextColor(if (currentFilter == "CRITICAL") Color.parseColor("#0A1B12") else Color.WHITE)

        chipFilterWarnings.setBackgroundResource(if (currentFilter == "WARNINGS") activeBg else inactiveBg)
        chipFilterWarnings.setTextColor(if (currentFilter == "WARNINGS") Color.parseColor("#0A1B12") else Color.WHITE)
    }

    private fun applyFilter() {
        val report = currentReport ?: return
        val filteredList = when (currentFilter) {
            "CRITICAL" -> report.criticalErrors
            "WARNINGS" -> report.warnings
            else -> report.issues
        }

        if (filteredList.isEmpty()) {
            rvAuditIssues.visibility = View.GONE
            layoutCleanTreeState.visibility = View.VISIBLE
        } else {
            layoutCleanTreeState.visibility = View.GONE
            rvAuditIssues.visibility = View.VISIBLE
            auditAdapter.updateList(filteredList)
        }
    }
}

