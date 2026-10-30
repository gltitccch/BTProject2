package com.example.btproject2.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.SyncEventListener
import com.example.btproject2.sync.TreeSyncEvent
import com.example.btproject2.ui.adapters.TreeAuditAdapter
import com.example.btproject2.utils.KinTraceModalDialog

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
class TreeAuditActivity : AppCompatActivity(), SyncEventListener {

    override val subscriberKey: String get() = "TreeAuditActivity_${treeId}"
    override val screenType: AffectedScreen get() = AffectedScreen.ALL_SCREENS
    override val interestedTreeId: String? get() = treeId.ifBlank { null }

    override fun onSyncEvent(event: TreeSyncEvent) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                runTreeAudit()
            }
        }
    }

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
    private var currentPersons: List<com.example.btproject2.models.Person> = emptyList()
    private var currentWizardConflicts: List<com.example.btproject2.engine.ConflictScanner.Conflict> = emptyList()
    private var btnLaunchConflictWizard: android.widget.Button? = null

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

    override fun onStart() {
        super.onStart()
        CentralTreeSynchronizer.getInstance().registerListener(this)
    }

    override fun onStop() {
        super.onStop()
        CentralTreeSynchronizer.getInstance().unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (currentReport != null) {
            runTreeAudit()
        }
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.TREE_HEALTH
        )
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
        btnLaunchConflictWizard = findViewById(R.id.btnLaunchConflictWizard)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnRescan.setOnClickListener { runTreeAudit() }

        btnLaunchConflictWizard?.setOnClickListener {
            showTreeConflictsDialog(currentWizardConflicts)
        }

        findViewById<TextView>(R.id.btnQuickAssistAudit)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.TREE_AUDIT
            ).show(supportFragmentManager, "QuickAssistAudit")
        }

        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.TREE_HEALTH
        )

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
                Thread {
                    val cached = FirestoreHelper.getCachedPersons(treeId).orEmpty()
                    val mergedPersons = (persons + cached).distinctBy { it.id.trim().lowercase() }
                    val treeMap = mergedPersons.associateBy { it.id }

                    val report = familyService.auditFamilyTree(treeMap)
                    val wizardConflicts = com.example.btproject2.engine.ConflictScanner().scanAll(mergedPersons)

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        currentPersons = mergedPersons
                        currentReport = report
                        currentWizardConflicts = wizardConflicts
                        progressBar.visibility = View.GONE
                        displayAuditReport(report)
                    }
                }.start()
            },
            onFailure = {
                Thread {
                    val cached = FirestoreHelper.getCachedPersons(treeId).orEmpty()
                    val treeMap = cached.associateBy { it.id }
                    val report = familyService.auditFamilyTree(treeMap)
                    val wizardConflicts = com.example.btproject2.engine.ConflictScanner().scanAll(cached)

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        currentPersons = cached
                        currentReport = report
                        currentWizardConflicts = wizardConflicts
                        progressBar.visibility = View.GONE
                        displayAuditReport(report)
                    }
                }.start()
            }
        )
    }

    private fun displayAuditReport(report: TreeAuditReport) {
        val totalMembers = if (currentPersons.isNotEmpty()) currentPersons.size else report.totalPersonsScanned
        tvTotalScannedCount.text = totalMembers.toString()

        val conflictCritical = currentWizardConflicts.count { it.severity.equals("error", ignoreCase = true) }
        val conflictWarning = currentWizardConflicts.count { it.severity.equals("warning", ignoreCase = true) }

        val effectiveCritical = maxOf(report.criticalErrorCount, conflictCritical)
        val effectiveWarning = maxOf(report.warningCount, conflictWarning)

        tvCriticalErrorCount.text = effectiveCritical.toString()
        tvWarningCount.text = effectiveWarning.toString()

        if (currentWizardConflicts.isNotEmpty()) {
            btnLaunchConflictWizard?.visibility = View.VISIBLE
            val count = currentWizardConflicts.size
            btnLaunchConflictWizard?.text = "✨ Start Guided Resolution ($count Issue${if (count > 1) "s" else ""})"
        } else {
            btnLaunchConflictWizard?.visibility = View.GONE
        }

        if (effectiveCritical > 0) {
            tvSummaryHeadline.text = "🧭 Family Connections Need Review ($effectiveCritical)"
            tvSummaryHeadline.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.audit_critical_text))
        } else if (effectiveWarning > 0) {
            tvSummaryHeadline.text = "⚠️ Lineage Suggestions Detected ($effectiveWarning)"
            tvSummaryHeadline.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.audit_warning_text))
        } else {
            tvSummaryHeadline.text = "✅ All Family Tree Connections Look Healthy"
            tvSummaryHeadline.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.status_mint_text))
        }

        applyFilter()
    }

    private fun showTreeConflictsDialog(conflicts: List<com.example.btproject2.engine.ConflictScanner.Conflict>) {
        if (conflicts.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_conflict_wizard, null, false)
        val dialog = KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvHeaderBadge = dialogView.findViewById<TextView>(R.id.tvWizardHeaderBadge)
        val btnFixAll = dialogView.findViewById<TextView>(R.id.btnWizardFixAll)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvWizardModalTitle)
        val tvModalSub = dialogView.findViewById<TextView>(R.id.tvWizardModalSub)
        val tvStepCounter = dialogView.findViewById<TextView>(R.id.tvWizardStepCounter)
        val tvStepPercent = dialogView.findViewById<TextView>(R.id.tvWizardStepPercent)
        val progressSteps = dialogView.findViewById<ProgressBar>(R.id.progressWizardSteps)

        val tvPersonName = dialogView.findViewById<TextView>(R.id.tvWizardPersonName)
        val tvStepTag = dialogView.findViewById<TextView>(R.id.tvWizardStepTag)
        val tvHeadline = dialogView.findViewById<TextView>(R.id.tvWizardQuestionHeadline)
        val tvExplain = dialogView.findViewById<TextView>(R.id.tvWizardQuestionExplain)
        val tvHelper = dialogView.findViewById<TextView>(R.id.tvWizardHelperPrompt)
        val layoutOptionsGroup = dialogView.findViewById<LinearLayout>(R.id.layoutWizardOptionsGroup)
        val btnPrimary = dialogView.findViewById<Button>(R.id.btnWizardPrimaryChoice)
        val btnSecondary = dialogView.findViewById<Button>(R.id.btnWizardSecondaryChoice)
        val btnPrev = dialogView.findViewById<Button>(R.id.btnWizardPrev)
        val btnSkip = dialogView.findViewById<Button>(R.id.btnWizardSkip)

        // Hide reckless blind bulk fix; always guide user through verified steps
        btnFixAll.visibility = View.GONE

        tvHeaderBadge.text = "🛡️ GUIDED RESOLUTION STEPPER"
        tvHeaderBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.warning))
        tvModalTitle.text = "Guided Family Tree Resolution"
        tvModalSub.text = "Step through relationship details with guided instructions."

        val activeConflicts = conflicts.toMutableList()
        var currentIndex = 0

        fun renderCompleteState() {
            tvHeaderBadge.text = "🏆 ALL ISSUES RESOLVED"
            tvHeaderBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
            btnFixAll.visibility = View.GONE
            tvModalTitle.text = "Tree Integrity Verified!"
            tvModalSub.text = "Your family tree connections are healthy, verified, and complete."
            tvStepCounter.text = "All Steps Complete"
            tvStepPercent.text = "100% Verified"
            progressSteps.progress = 100

            tvPersonName.text = "🎉 Family Tree Healthy"
            tvStepTag.text = "✓ Flawless"
            tvStepTag.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
            tvHeadline.text = "“Tree Integrity Verified!”"
            tvExplain.text = "All relationship connections, spouses, and parent links have been validated."
            tvHelper.text = "✨ 0 issues remaining."
            layoutOptionsGroup?.removeAllViews()

            btnPrimary.isEnabled = true
            btnPrimary.text = "✓ Return to Audit Report"
            btnPrimary.setOnClickListener {
                dialog.dismiss()
                runTreeAudit()
            }
            btnSecondary.visibility = View.GONE
            btnPrev.visibility = View.GONE
            btnSkip.text = "Close"
            btnSkip.setOnClickListener {
                dialog.dismiss()
                runTreeAudit()
            }
        }

        fun renderCurrentStep() {
            if (activeConflicts.isEmpty()) {
                renderCompleteState()
                return
            }
            if (currentIndex >= activeConflicts.size) currentIndex = activeConflicts.size - 1
            if (currentIndex < 0) currentIndex = 0

            val total = activeConflicts.size
            val item = activeConflicts[currentIndex]
            val pct = (((currentIndex + 1) * 100) / total).coerceIn(1, 100)

            tvStepCounter.text = "Issue ${currentIndex + 1} of $total"
            tvStepPercent.text = "$pct% Done"
            progressSteps.progress = pct

            tvPersonName.text = "👤 ${item.personName}"
            tvStepTag.text = "Issue ${currentIndex + 1}"
            tvHeadline.text = item.questionHeadline
            tvExplain.text = item.simpleExplanation
            tvHelper.text = item.helperPrompt

            btnSecondary.visibility = View.GONE
            layoutOptionsGroup?.removeAllViews()

            val currentOptions = if (item.options.isNotEmpty()) {
                item.options
            } else {
                listOf(
                    com.example.btproject2.engine.ConflictScanner.ResolutionOption(
                        id = "default_safe_fix",
                        title = item.primaryActionLabel.replace("✨", "").replace("✅", "").trim(),
                        tag = "Safe Fix",
                        tagType = "safe",
                        description = item.simpleExplanation,
                        actionType = if (item.canAutoFix) com.example.btproject2.engine.ConflictScanner.ResolutionActionType.UNLINK else com.example.btproject2.engine.ConflictScanner.ResolutionActionType.EDIT_PROFILE,
                        targetField = item.field
                    )
                )
            }

            var selectedOptionIndex = currentOptions.indexOfFirst { it.actionType == com.example.btproject2.engine.ConflictScanner.ResolutionActionType.SET_ADOPTIVE }
            if (selectedOptionIndex < 0) selectedOptionIndex = 0

            val cardViews = mutableListOf<View>()

            fun updateSelectionUI() {
                val chosen = currentOptions.getOrNull(selectedOptionIndex) ?: currentOptions[0]
                btnPrimary.isEnabled = true
                btnPrimary.text = "✨ Apply: ${chosen.title}"

                cardViews.forEachIndexed { idx, cardView ->
                    val isSelected = idx == selectedOptionIndex
                    cardView.setBackgroundResource(
                        if (isSelected) R.drawable.bg_wizard_option_card_selected else R.drawable.bg_wizard_option_card_default
                    )
                    val ivRadio = cardView.findViewById<ImageView>(R.id.ivOptionRadio)
                    ivRadio?.setImageResource(
                        if (isSelected) R.drawable.ic_wizard_radio_checked else R.drawable.ic_wizard_radio_unchecked
                    )
                }
            }

            currentOptions.forEachIndexed { index, option ->
                val cardView = layoutInflater.inflate(R.layout.item_wizard_resolution_option, layoutOptionsGroup, false)
                val tvTitle = cardView.findViewById<TextView>(R.id.tvOptionTitle)
                val tvBadge = cardView.findViewById<TextView>(R.id.tvOptionBadge)
                val tvDesc = cardView.findViewById<TextView>(R.id.tvOptionDescription)

                tvTitle.text = option.title
                tvBadge.text = option.tag
                when (option.tagType) {
                    "legal" -> {
                        tvBadge.setBackgroundResource(R.drawable.badge_pill_mint)
                        tvBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
                    }
                    "edit" -> {
                        tvBadge.setBackgroundResource(R.drawable.badge_pill_slate)
                        tvBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary))
                    }
                    else -> {
                        tvBadge.setBackgroundResource(R.drawable.badge_pill_gold)
                        tvBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text))
                    }
                }
                tvDesc.text = option.description

                cardView.setOnClickListener {
                    selectedOptionIndex = index
                    updateSelectionUI()
                }

                cardViews.add(cardView)
                layoutOptionsGroup?.addView(cardView)
            }

            updateSelectionUI()

            btnPrev.visibility = if (total > 1) View.VISIBLE else View.GONE
            btnPrev.isEnabled = currentIndex > 0
            btnPrev.alpha = if (currentIndex > 0) 1.0f else 0.45f
            btnSkip.text = if (currentIndex < total - 1) "Skip for Now →" else "Close"

            btnPrimary.setOnClickListener {
                val chosen = currentOptions.getOrNull(selectedOptionIndex) ?: currentOptions[0]
                if (chosen.actionType == com.example.btproject2.engine.ConflictScanner.ResolutionActionType.EDIT_PROFILE) {
                    dialog.dismiss()
                    val targetPersonId = if (item.field == "motherId" && chosen.id.contains("mother")) {
                        item.relatedPersonId.ifBlank { item.personId }
                    } else {
                        item.personId
                    }
                    val intent = Intent(this, EditMemberActivity::class.java).apply {
                        putExtra("personId", targetPersonId)
                        putExtra("TREE_ID", treeId)
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }

                btnPrimary.isEnabled = false
                btnPrimary.text = "⏳ Applying Resolution..."
                firestoreHelper.executeResolutionAction(
                    conflict = item,
                    option = chosen,
                    allPersons = currentPersons,
                    onSuccess = { updatedPerson ->
                        runOnUiThread {
                            if (updatedPerson != null) {
                                currentPersons = currentPersons.map { if (it.id == updatedPerson.id) updatedPerson else it }
                            }
                            activeConflicts.removeAt(currentIndex)
                            com.example.btproject2.utils.KinTraceAlert.conflict(this, "Resolution Applied", "${chosen.title} saved successfully.")
                            runTreeAudit()
                            renderCurrentStep()
                        }
                    },
                    onFailure = { err ->
                        runOnUiThread {
                            btnPrimary.isEnabled = true
                            btnPrimary.text = "✨ Apply: ${chosen.title}"
                            com.example.btproject2.utils.KinTraceAlert.warning(this, "Could Not Save", err.message ?: "Please try again.")
                        }
                    }
                )
            }

            btnSecondary.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this, EditMemberActivity::class.java).apply {
                    putExtra("personId", item.personId)
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                renderCurrentStep()
            }
        }

        btnSkip.setOnClickListener {
            if (currentIndex < activeConflicts.size - 1) {
                currentIndex++
                renderCurrentStep()
            } else {
                dialog.dismiss()
            }
        }

        renderCurrentStep()
        dialog.show()
    }

    private fun updateFilterChipUI() {
        val activeBg = R.drawable.badge_pill_gold
        val inactiveBg = R.drawable.badge_pill_dark

        val activeTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text)
        val inactiveTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)

        chipFilterAll.setBackgroundResource(if (currentFilter == "ALL") activeBg else inactiveBg)
        chipFilterAll.setTextColor(if (currentFilter == "ALL") activeTextColor else inactiveTextColor)

        chipFilterCritical.setBackgroundResource(if (currentFilter == "CRITICAL") activeBg else inactiveBg)
        chipFilterCritical.setTextColor(if (currentFilter == "CRITICAL") activeTextColor else inactiveTextColor)

        chipFilterWarnings.setBackgroundResource(if (currentFilter == "WARNINGS") activeBg else inactiveBg)
        chipFilterWarnings.setTextColor(if (currentFilter == "WARNINGS") activeTextColor else inactiveTextColor)
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

