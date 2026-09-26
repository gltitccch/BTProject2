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

    private val healthCalculator = TreeHealthCalculator()
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

        loadInsights()
    }

    private fun loadInsights() {
        firestoreHelper.getPersonsByTree(
            treeId,
            onSuccess = { persons ->
                val health = healthCalculator.calculate(persons)
                val avgCompleteness = profileChecker.checkAll(persons)
                displayInsights(persons, health, avgCompleteness)
            },
            onFailure = {}
        )
    }

    private fun displayInsights(
        persons: List<Person>,
        health: TreeHealthResult,
        avgCompleteness: Int
    ) {
        val total = persons.size
        val livingCount = persons.count { it.isLiving }
        val deceasedCount = persons.count { !it.isLiving }

        // Biological relationships count: parent links + spouse links
        val totalRelationships = persons.count { !it.fatherId.isNullOrEmpty() } +
                persons.count { !it.motherId.isNullOrEmpty() } +
                persons.count { !it.spouseId.isNullOrEmpty() }

        // BFS Generation assignment
        val genMap = calculateGenerationMap(persons)
        val maxGen = if (total == 0) 0 else (genMap.values.maxOrNull() ?: 1)

        // 1. Tree Overview Grid (Figure 6)
        findViewById<TextView>(R.id.tvTotalMembers)?.text = total.toString()
        findViewById<TextView>(R.id.tvLivingMembers)?.text = livingCount.toString()
        findViewById<TextView>(R.id.tvDeceasedMembers)?.text = deceasedCount.toString()
        findViewById<TextView>(R.id.tvMaxGen)?.text = maxGen.toString()
        findViewById<TextView>(R.id.tvRelationshipsCount)?.text = totalRelationships.toString()

        // Fetch branch count
        val currentUserId = com.example.btproject2.firebase.AuthHelper().getCurrentUserId() ?: ""
        firestoreHelper.getBranchCount(currentUserId,
            onSuccess = { count -> findViewById<TextView>(R.id.tvBranchesCount)?.text = count.toString() },
            onFailure = { findViewById<TextView>(R.id.tvBranchesCount)?.text = "1" }
        )

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

        // 4. Tree Health & Data Quality Scorecard
        findViewById<TextView>(R.id.tvHealthScore)?.text = "${health.score}%"
        findViewById<TextView>(R.id.tvHealthLabel)?.text = health.label

        val layoutDeductions = findViewById<LinearLayout>(R.id.layoutHealthDeductions)
        layoutDeductions?.removeAllViews()
        if (layoutDeductions != null) {
            if (health.deductions.isEmpty()) {
                val okRow = TextView(this).apply {
                    text = "✓ All recorded relationships are biologically consistent"
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#0F6E56"))
                    setPadding(0, 4, 0, 4)
                }
                layoutDeductions.addView(okRow)
            } else {
                health.deductions.take(4).forEach { deduction ->
                    val row = TextView(this).apply {
                        text = "• ${deduction.reason} (-${deduction.points})"
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#BA7517"))
                        setPadding(0, 2, 0, 2)
                    }
                    layoutDeductions.addView(row)
                }
            }
        }

        // Dynamic tip
        val withParents = persons.count { !it.motherId.isNullOrEmpty() || !it.fatherId.isNullOrEmpty() }
        val noParentCount = total - withParents
        findViewById<TextView>(R.id.tvTip)?.text = if (noParentCount > 0) {
            "💡 $noParentCount member${if (noParentCount > 1) "s" else ""} don't have parents set. Setting parent links improves bloodline tracing accuracy."
        } else {
            "💡 Great job! All members have parents assigned, giving your family tree optimal bloodline accuracy."
        }
    }

    private fun calculateGenerationMap(persons: List<Person>): Map<String, Int> {
        return com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations1Based(persons)
    }
}

/* ---------------------------------------------------
   Helper models + calculators kept in the same file
   so you only need to paste one source file.
--------------------------------------------------- */

private data class TreeHealthDeduction(
    val reason: String,
    val points: Int
)

private data class TreeHealthResult(
    val score: Int,
    val label: String,
    val deductions: List<TreeHealthDeduction>
)

private class TreeHealthCalculator {

    fun calculate(persons: List<Person>): TreeHealthResult {
        val deductions = mutableListOf<TreeHealthDeduction>()

        persons.forEach { person ->
            if (person.birthDate.isBlank()) {
                deductions.add(TreeHealthDeduction("Missing birth date for ${fullName(person)}", 5))
            }

            if (person.fatherId.isNullOrEmpty() && person.motherId.isNullOrEmpty()) {
                deductions.add(TreeHealthDeduction("No parents set for ${fullName(person)}", 3))
            }

            if (!person.spouseId.isNullOrEmpty() && person.maritalStatus.equals("Single", ignoreCase = true)) {
                deductions.add(TreeHealthDeduction("Single but has spouse: ${fullName(person)}", 4))
            }

            if (person.maritalStatus.equals("Married", ignoreCase = true) && person.spouseId.isNullOrEmpty()) {
                deductions.add(TreeHealthDeduction("Married but no spouse set: ${fullName(person)}", 4))
            }

            if (
                !person.fatherId.isNullOrEmpty() &&
                !person.motherId.isNullOrEmpty() &&
                person.fatherId == person.motherId
            ) {
                deductions.add(TreeHealthDeduction("Same father and mother set for ${fullName(person)}", 10))
            }

            if (!person.birthDate.isNullOrBlank()) {
                val birthYear = person.birthDate.take(4).toIntOrNull()

                if (!person.marriageDate.isNullOrBlank()) {
                    val marriageYear = person.marriageDate.take(4).toIntOrNull()
                    if (birthYear != null && marriageYear != null && marriageYear < birthYear) {
                        deductions.add(
                            TreeHealthDeduction(
                                "Marriage before birth for ${fullName(person)}",
                                10
                            )
                        )
                    }
                }
            }
        }

        val rawScore = 100 - deductions.sumOf { it.points }
        val finalScore = rawScore.coerceIn(0, 100)

        val label = when {
            finalScore >= 90 -> "Excellent"
            finalScore >= 75 -> "Good"
            finalScore >= 50 -> "Needs Improvement"
            else -> "Critical"
        }

        return TreeHealthResult(
            score = finalScore,
            label = label,
            deductions = deductions
        )
    }

    private fun fullName(person: Person): String {
        return "${person.firstName} ${person.lastName}".trim()
    }
}
private class ProfileCompletenessChecker {

    fun checkAll(persons: List<Person>): Int {
        if (persons.isEmpty()) return 0
        val avg = persons.map { checkOne(it) }.average()
        return avg.toInt()
    }

    private fun checkOne(person: Person): Int {
        var score = 0
        val total = 6

        if (person.firstName.isNotBlank()) score++
        if (person.lastName.isNotBlank()) score++
        if (person.gender.isNotBlank()) score++
        if (person.birthDate.isNotBlank()) score++
        if (!person.fatherId.isNullOrEmpty() || !person.motherId.isNullOrEmpty()) score++
        if (person.biography.isNotBlank()) score++

        return ((score.toDouble() / total.toDouble()) * 100.0).toInt()
    }
}