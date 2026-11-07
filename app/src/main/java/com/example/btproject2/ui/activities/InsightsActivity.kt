package com.example.btproject2.ui.activities

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person

class InsightsActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private var treeId = ""

    private val profileChecker = ProfileCompletenessChecker()

    private val insightsSyncListener = object : com.example.btproject2.sync.SyncEventListener {
        override val subscriberKey: String = "InsightsActivity@${System.identityHashCode(this)}"
        override val screenType: com.example.btproject2.sync.AffectedScreen = com.example.btproject2.sync.AffectedScreen.INSIGHTS_DASHBOARD
        override val interestedTreeId: String? = null

        override fun onSyncEvent(event: com.example.btproject2.sync.TreeSyncEvent) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    loadInsights()
                }
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
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().registerListener(insightsSyncListener)
    }

    override fun onResume() {
        super.onResume()
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_INSIGHTS,
            treeId = treeId
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().unregisterListener(insightsSyncListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insights)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnAuditTree)?.setOnClickListener {
            val intent = android.content.Intent(this, TreeAuditActivity::class.java).apply {
                putExtra("TREE_ID", treeId)
                putExtra("treeId", treeId)
            }
            startActivity(intent)
        }

        findViewById<TextView>(R.id.btnQuickAssistInsights)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.show(
                this,
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.INSIGHTS
            )
        }

        loadInsights()
    }

    private fun loadInsights() {
        firestoreHelper.getPersonsByTree(
            treeId,
            onSuccess = { persons ->
                val avgCompleteness = profileChecker.checkAll(persons)
                displayInsights(persons, avgCompleteness)
            },
            onFailure = {}
        )
    }

    private fun displayInsights(
        persons: List<Person>,
        avgCompleteness: Int
    ) {
        val total = persons.size
        val livingCount = persons.count { it.isLiving }
        val deceasedCount = persons.count { !it.isLiving }

        // Biological relationships count: parent links + spouse links
        val totalRelationships = computeUniqueRelationshipsCount(persons)

        // BFS Generation assignment
        val genMap = calculateGenerationMap(persons)
        val maxGen = if (total == 0) 0 else (genMap.values.maxOrNull() ?: 1)

        val branchBreakdown = computeClanBranchBreakdown(persons)
        val branchCount = computeClanBranchCount(persons)

        // 1. Tree Overview Grid (Figure 6)
        val tvTotalView = findViewById<TextView>(R.id.tvTotalMembers)
        val tvLivingView = findViewById<TextView>(R.id.tvLivingMembers)
        val tvDeceasedView = findViewById<TextView>(R.id.tvDeceasedMembers)
        tvTotalView?.text = total.toString()
        tvLivingView?.text = livingCount.toString()
        tvDeceasedView?.text = deceasedCount.toString()
        findViewById<TextView>(R.id.tvMaxGen)?.text = maxGen.toString()
        findViewById<TextView>(R.id.tvRelationshipsCount)?.text = totalRelationships.toString()

        fun openRecordsWithFilter(filterMode: String) {
            val intent = android.content.Intent(this, FamilyRecordsActivity::class.java).apply {
                putExtra("TREE_ID", treeId)
                putExtra("treeId", treeId)
                putExtra("INITIAL_FILTER", filterMode)
            }
            startActivity(intent)
        }

        (tvTotalView?.parent as? android.view.View)?.setOnClickListener { openRecordsWithFilter("ALL") }
        (tvLivingView?.parent as? android.view.View)?.setOnClickListener { openRecordsWithFilter("LIVING") }
        (tvDeceasedView?.parent as? android.view.View)?.setOnClickListener { openRecordsWithFilter("DECEASED") }

        val tvBranchesCountView = findViewById<TextView>(R.id.tvBranchesCount)
        tvBranchesCountView?.text = branchCount.toString()
        (tvBranchesCountView?.parent as? android.view.View)?.setOnClickListener {
            showClanBranchesBreakdownDialog(branchBreakdown)
        }

        // Fetch tree name for subtitle
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                val treeName = tree?.name?.takeIf { it.isNotBlank() } ?: "Family"
                findViewById<TextView>(R.id.tvInsightSub)?.text = "$treeName lineage overview · $total members"
            },
            onFailure = {
                findViewById<TextView>(R.id.tvInsightSub)?.text = "Family lineage overview · $total members"
            }
        )

        // 2. Gender Distribution (Figure 6)
        val maleCount = persons.count { it.gender.equals("Male", ignoreCase = true) }
        val femaleCount = persons.count { it.gender.equals("Female", ignoreCase = true) }
        val malePct = if (total > 0) ((maleCount.toFloat() / total) * 100).toInt() else 0
        val femalePct = if (total > 0) 100 - malePct else 0

        findViewById<TextView>(R.id.tvMaleLegend)?.text = "Male: $maleCount ($malePct%)"
        findViewById<TextView>(R.id.tvFemaleLegend)?.text = "Female: $femaleCount ($femalePct%)"

        val barMale = findViewById<android.view.View>(R.id.barMale)
        val barFemale = findViewById<android.view.View>(R.id.barFemale)
        if (barMale != null && barFemale != null) {
            val weightMale = if (total > 0) (if (maleCount == 0 && femaleCount > 0) 0.01f else maleCount.toFloat()) else 50f
            val weightFemale = if (total > 0) (if (femaleCount == 0 && maleCount > 0) 0.01f else femaleCount.toFloat()) else 50f
            barMale.layoutParams = (barMale.layoutParams as LinearLayout.LayoutParams).apply { weight = weightMale }
            barFemale.layoutParams = (barFemale.layoutParams as LinearLayout.LayoutParams).apply { weight = weightFemale }
        }

        // 3. Generation Breakdown (Figure 6)
        val gen1 = persons.count { genMap[it.id] == 1 }
        val gen2 = persons.count { genMap[it.id] == 2 }
        val gen3 = persons.count { genMap[it.id] == 3 }
        val gen4 = persons.count { (genMap[it.id] ?: 0) >= 4 }

        findViewById<TextView>(R.id.tvGen1Count)?.text = gen1.toString()
        findViewById<TextView>(R.id.tvGen2Count)?.text = gen2.toString()
        findViewById<TextView>(R.id.tvGen3Count)?.text = gen3.toString()
        findViewById<TextView>(R.id.tvGen4Count)?.text = gen4.toString()

        // 4. Data Quality & Profile Completeness Scorecard
        findViewById<TextView>(R.id.tvHealthScore)?.text = "${avgCompleteness}%"
        val qualityLabel = when {
            avgCompleteness >= 85 -> "Comprehensive Member Records"
            avgCompleteness >= 60 -> "Good Record Completeness"
            avgCompleteness > 0 -> "Basic Records (Details Incomplete)"
            else -> "No Members Added Yet"
        }
        findViewById<TextView>(R.id.tvHealthLabel)?.text = qualityLabel

        val withBirthDates = persons.count { it.birthDate.isNotBlank() }
        val noParentCount = FamilyRecordsActivity.countMissingParents(persons)
        val withParents = (total - noParentCount).coerceAtLeast(0)
        val withBio = persons.count { it.biography.isNotBlank() }
        val layoutDeductions = findViewById<LinearLayout>(R.id.layoutHealthDeductions)
        if (layoutDeductions != null) {
            layoutDeductions.removeAllViews()
            val summaryRow = TextView(this).apply {
                text = "📅 Birth Dates: $withBirthDates/$total   ·   🌳 Parent Links: $withParents/$total   ·   📖 Biographies: $withBio/$total"
                textSize = 11.5f
                setTextColor(androidx.core.content.ContextCompat.getColor(this@InsightsActivity, R.color.text_secondary))
            }
            layoutDeductions.addView(summaryRow)
        }

        // Dynamic clickable Missing-Parents Tip
        val tvTip = findViewById<TextView>(R.id.tvTip)
        val cardDataQuality = findViewById<android.view.View>(R.id.cardDataQualityScorecard)
        if (tvTip != null) {
            if (noParentCount > 0) {
                tvTip.text = "💡 $noParentCount member${if (noParentCount > 1) "s" else ""} don't have parents set. Setting parent links improves bloodline tracing accuracy.  Tap to view & fix →"
                tvTip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.gold_accent))
                tvTip.setBackgroundResource(R.drawable.note_bg)
                val openMissingParents = android.view.View.OnClickListener {
                    openRecordsWithFilter("MISSING_PARENTS")
                }
                tvTip.isClickable = true
                tvTip.isFocusable = true
                tvTip.setOnClickListener(openMissingParents)
                cardDataQuality?.setOnClickListener(openMissingParents)
            } else {
                tvTip.text = "💡 Great job! All members have parents assigned, giving your family tree optimal bloodline accuracy."
                tvTip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
                tvTip.setBackgroundResource(R.drawable.note_bg)
                tvTip.setOnClickListener(null)
                tvTip.isClickable = false
                cardDataQuality?.setOnClickListener(null)
                cardDataQuality?.isClickable = false
            }
        }
    }

    private fun calculateGenerationMap(persons: List<Person>): Map<String, Int> {
        return com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations1Based(persons)
    }

    private fun showClanBranchesBreakdownDialog(breakdown: List<Pair<String, Int>>) {
        if (breakdown.isEmpty()) return
        val lines = breakdown.mapIndexed { idx, (surname, count) ->
            "${idx + 1}. $surname — $count member${if (count == 1) "" else "s"}"
        }.joinToString("\n")
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("🌿 Family Branches (${breakdown.size})")
            .setMessage("Distinct family surname lineages represented in this tree:\n\n$lines")
            .setPositiveButton("Got It") { d, _ -> d.dismiss() }
            .show()
    }

    companion object {
        fun computeClanBranchBreakdown(persons: List<Person>): List<Pair<String, Int>> {
            if (persons.isEmpty()) return emptyList()
            val grouped = persons
                .mapNotNull { p ->
                    p.lastName.trim().takeIf { it.isNotEmpty() }
                }
                .groupBy { it.lowercase(java.util.Locale.ROOT) }
                .map { (_, list) ->
                    val displaySurname = list.first().trim()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                    displaySurname to list.size
                }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

            return if (grouped.isNotEmpty()) {
                grouped
            } else {
                listOf("Primary Lineage" to persons.size)
            }
        }

        fun computeClanBranchCount(persons: List<Person>): Int {
            if (persons.isEmpty()) return 0
            return computeClanBranchBreakdown(persons).size.coerceAtLeast(1)
        }

        fun computeUniqueRelationshipsCount(persons: List<Person>): Int {
            if (persons.isEmpty()) return 0
            val parentLinks = persons.count { !it.fatherId.isNullOrBlank() } +
                persons.count { !it.motherId.isNullOrBlank() }
            val uniqueSpousePairs = mutableSetOf<Pair<String, String>>()
            for (p in persons) {
                val pid = p.id.trim()
                if (pid.isEmpty()) continue
                for (sid in p.allSpouseIds()) {
                    val cleanSid = sid.trim()
                    if (cleanSid.isNotEmpty() && cleanSid != pid) {
                        val pair = if (pid < cleanSid) pid to cleanSid else cleanSid to pid
                        uniqueSpousePairs.add(pair)
                    }
                }
            }
            return parentLinks + uniqueSpousePairs.size
        }
    }
}

/* ---------------------------------------------------
   Helper models + calculators kept in the same file
   so you only need to paste one source file.
--------------------------------------------------- */
private class ProfileCompletenessChecker {

    fun checkAll(persons: List<Person>): Int {
        if (persons.isEmpty()) return 0
        val validIds = persons.mapNotNull { it.id.trim().takeIf(String::isNotEmpty)?.lowercase() }.toSet()
        val avg = persons.map { checkOne(it, validIds) }.average()
        return avg.toInt()
    }

    private fun checkOne(person: Person, validIds: Set<String>): Int {
        var score = 0
        val total = 6

        if (person.firstName.isNotBlank()) score++
        if (person.lastName.isNotBlank()) score++
        if (person.gender.isNotBlank()) score++
        if (person.birthDate.isNotBlank()) score++
        if (!FamilyRecordsActivity.isMissingParents(person, validIds)) score++
        if (person.biography.isNotBlank()) score++

        return ((score.toDouble() / total.toDouble()) * 100.0).toInt()
    }
}
