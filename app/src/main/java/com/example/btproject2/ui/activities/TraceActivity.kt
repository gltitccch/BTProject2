package com.example.btproject2.ui.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.example.btproject2.R
import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.models.TraceResult
import com.example.btproject2.utils.ThemePreferences
import com.example.btproject2.utils.setDarkAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TraceActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private val bloodlineTracer = BloodlineTracer()
    private var familyMembers = listOf<Person>()
    private var treeId = ""
    private var lastPersonAId = ""
    private var lastPersonBId = ""
    private var lastTraceResult: TraceResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val spinnerA = findViewById<Spinner>(R.id.spinnerPersonA)
        val spinnerB = findViewById<Spinner>(R.id.spinnerPersonB)
        val btnTrace = findViewById<Button>(R.id.btnTrace)
        val resultContainer = findViewById<LinearLayout>(R.id.resultContainer)

        btnBack.setOnClickListener { finish() }

        val btnQuickAssistTrace = findViewById<TextView>(R.id.btnQuickAssistTrace)
        btnQuickAssistTrace?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.TRACE
            ).show(supportFragmentManager, "QuickAssistTrace")
        }

        resultContainer.visibility = View.GONE

        loadFamilyMembers(spinnerA, spinnerB)

        btnTrace.setOnClickListener {
            val indexA = spinnerA.selectedItemPosition
            val indexB = spinnerB.selectedItemPosition

            if (indexA < 0 || indexB < 0 || familyMembers.isEmpty()) {
                showMessage("Error", "Please select two people.")
                return@setOnClickListener
            }

            if (indexA == indexB) {
                showMessage("Error", "You selected the same person. Please choose two different family members.")
                return@setOnClickListener
            }

            val personAId = familyMembers[indexA].id
            val personBId = familyMembers[indexB].id

            fun executeTrace(personMap: Map<String, Person>, isNetworkFallback: Boolean = false) {
                if (!personMap.containsKey(personAId) || !personMap.containsKey(personBId)) {
                    if (isNetworkFallback) {
                        showMessage("Error", "Selected persons could not be found in the family tree.")
                    }
                    return
                }
                val result = bloodlineTracer.trace(personAId, personBId, personMap)
                displayResult(result)

                val currentUserId = authHelper.getCurrentUserId() ?: ""
                val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                val nameA = "${result.personA.firstName} ${result.personA.lastName}"
                val nameB = "${result.personB.firstName} ${result.personB.lastName}"
                firestoreHelper.logActivity(
                    treeId = treeId,
                    userId = currentUserId,
                    userName = currentUserName,
                    type = "TRACE",
                    description = "Traced consanguinity between $nameA and $nameB: ${result.relationshipType}",
                    targetId = "$personAId|$personBId",
                    targetName = "$nameA & $nameB"
                )
                loadRecentTraceReports()
            }

            val cached = FirestoreHelper.getCachedPersons(treeId)
            val cachedMap = cached?.associateBy { it.id }
            if (cachedMap != null && cachedMap.containsKey(personAId) && cachedMap.containsKey(personBId)) {
                executeTrace(cachedMap)
            } else {
                firestoreHelper.loadEntireTree(treeId,
                    onSuccess = { personMap ->
                        executeTrace(personMap, isNetworkFallback = true)
                    },
                    onFailure = { error ->
                        showMessage("Error", "Failed to load family: ${error.message}")
                    }
                )
            }
        }

        // View Full Official Report button (Module 6)
        findViewById<Button>(R.id.btnViewFullReport)?.setOnClickListener {
            if (lastPersonAId.isNotEmpty() && lastPersonBId.isNotEmpty()) {
                val intent = Intent(this, RelationshipReportActivity::class.java).apply {
                    putExtra("personAId", lastPersonAId)
                    putExtra("personBId", lastPersonBId)
                    putExtra("treeId", treeId)
                }
                startActivity(intent)
            }
        }

        // Trace Again button
        findViewById<Button>(R.id.btnTraceAgain).setOnClickListener {
            resultContainer.visibility = View.GONE
        }

        // Learn More button
        findViewById<Button>(R.id.btnViewPath).setOnClickListener {
            if (lastPersonAId.isNotEmpty() && lastPersonBId.isNotEmpty()) {
                val intent = Intent(this, RelationshipPathActivity::class.java).apply {
                    putExtra("personAId", lastPersonAId)
                    putExtra("personBId", lastPersonBId)
                    putExtra("treeId", treeId)
                }
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnHighlightOnTree)?.setOnClickListener {
            val r = lastTraceResult
            if (r != null && r.isRelated) {
                val pathIds = ArrayList<String>()
                pathIds.addAll(r.pathFromA.map { it.id })
                r.commonAncestor?.id?.let { pathIds.add(it) }
                pathIds.addAll(r.pathFromB.map { it.id })

                val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                    putExtra("highlightPersonAId", r.personA.id)
                    putExtra("highlightPersonBId", r.personB.id)
                    putExtra("mrcaId", r.commonAncestor?.id)
                    putStringArrayListExtra("highlightPathIds", pathIds)
                    putExtra("viewMode", "TRACE_HIGHLIGHT")
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            } else if (lastPersonAId.isNotEmpty() && lastPersonBId.isNotEmpty()) {
                val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                    putExtra("highlightPersonAId", lastPersonAId)
                    putExtra("highlightPersonBId", lastPersonBId)
                    putExtra("viewMode", "TRACE_HIGHLIGHT")
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnLearnMore).setOnClickListener {
            startActivity(Intent(this, RelationshipTypesActivity::class.java))
        }

        applyActionButtonsTheme()

        loadRecentTraceReports()
    }

    override fun onResume() {
        super.onResume()
        loadRecentTraceReports()
    }

    private fun displayResult(result: TraceResult) {
        lastTraceResult = result
        lastPersonAId = result.personA.id
        lastPersonBId = result.personB.id
        val resultContainer = findViewById<LinearLayout>(R.id.resultContainer)
        val resultCard = findViewById<LinearLayout>(R.id.resultCard)
        val tvResultLabel = findViewById<TextView>(R.id.tvResultLabel)
        val tvType = findViewById<TextView>(R.id.tvRelationshipType)
        val tvExplanation = findViewById<TextView>(R.id.tvExplanation)
        val ancestorContainer = findViewById<LinearLayout>(R.id.ancestorContainer)
        val tvAncestor = findViewById<TextView>(R.id.tvCommonAncestor)
        val tvCategory = findViewById<TextView>(R.id.tvCategory)
        val tvDegree = findViewById<TextView>(R.id.tvDegree)
        val tvSteps = findViewById<TextView>(R.id.tvSteps)
        val detailsCard = findViewById<LinearLayout>(R.id.detailsCard)

        resultContainer.visibility = View.VISIBLE
        applyActionButtonsTheme()

        detailsCard.visibility = if (result.isRelated) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnViewPath).visibility = if (result.isRelated) View.VISIBLE else View.GONE

        // Family Code Legal Assessment Card
        val legalCard = findViewById<LinearLayout>(R.id.legalCard)
        val tvLegalBadge = findViewById<TextView>(R.id.tvLegalBadge)
        val tvLegalTitle = findViewById<TextView>(R.id.tvLegalTitle)
        val tvLegalDescription = findViewById<TextView>(R.id.tvLegalDescription)
        val tvLegalArticle = findViewById<TextView>(R.id.tvLegalArticle)

        legalCard.visibility = View.VISIBLE
        tvLegalTitle.text = result.legalStatus
        tvLegalDescription.text = result.legalDescription
        tvLegalArticle.text = result.legalArticle

        val isDark = ThemePreferences.isDarkTheme(this)

        if (result.isMarriageProhibited) {
            tvLegalBadge.text = "PROHIBITED"
            tvLegalBadge.setTextColor(Color.parseColor("#FF5C5C"))
            tvLegalBadge.setBackgroundResource(R.drawable.badge_pill_dark)
            tvLegalTitle.setTextColor(if (isDark) Color.parseColor("#FF8A8A") else Color.parseColor("#DC2626"))
        } else if (result.isRelated) {
            tvLegalBadge.text = "PERMISSIBLE"
            tvLegalBadge.setTextColor(if (isDark) Color.parseColor("#4ECCA3") else Color.parseColor("#15803D"))
            tvLegalBadge.setBackgroundResource(R.drawable.badge_pill_dark)
            tvLegalTitle.setTextColor(if (isDark) Color.parseColor("#D4A359") else Color.parseColor("#B45309"))
        } else {
            tvLegalBadge.text = "NO IMPEDIMENT"
            tvLegalBadge.setTextColor(if (isDark) Color.parseColor("#A3B899") else Color.parseColor("#15803D"))
            tvLegalBadge.setBackgroundResource(R.drawable.badge_pill_dark)
            tvLegalTitle.setTextColor(if (isDark) Color.parseColor("#E0E0E0") else ContextCompat.getColor(this, R.color.text_primary))
        }

        if (result.isRelated) {
            resultCard.setBackgroundResource(R.drawable.result_card_bg)
            tvResultLabel.text = "RELATIONSHIP FOUND"
            tvResultLabel.setTextColor(if (isDark) Color.parseColor("#4ECCA3") else Color.parseColor("#065F46"))
            tvType.text = result.relationshipType
            tvType.setTextColor(if (isDark) Color.parseColor("#D4A359") else Color.parseColor("#B45309"))
            tvExplanation.text = result.explanation

            if (result.commonAncestor != null) {
                ancestorContainer.visibility = View.VISIBLE
                tvAncestor.text = "${result.commonAncestor.firstName} ${result.commonAncestor.lastName}"
            } else {
                ancestorContainer.visibility = View.GONE
            }

            tvCategory.text = "Classification: ${result.relationshipCategory} Line (via ${if (result.relationshipCategory == "Lineal") "direct ascent/descent" else "shared ancestor"})"
            tvDegree.text = "Consanguinity: ${result.degreeOfConsanguinity}${getOrdinalSuffix(result.degreeOfConsanguinity)} Civil Degree (Roman Method)"
            tvSteps.text = "Generational Steps: ${result.distanceA} from ${result.personA.firstName}, ${result.distanceB} from ${result.personB.firstName}"

            // Inline Bloodline Route Preview
            val cardInlineRoutePreview = findViewById<LinearLayout>(R.id.cardInlineRoutePreview)
            val tvInlineRouteText = findViewById<TextView>(R.id.tvInlineRouteText)
            val tvInlineRouteSteps = findViewById<TextView>(R.id.tvInlineRouteSteps)

            val pathA = result.pathFromA.map { it.firstName }
            val mrcaName = result.commonAncestor?.let { "★ ${it.firstName} (MRCA)" }
            val pathB = result.pathFromB.reversed().drop(1).map { it.firstName }
            val fullRoute = (pathA + listOfNotNull(mrcaName) + pathB).distinct()

            if (fullRoute.size > 1) {
                cardInlineRoutePreview.visibility = View.VISIBLE
                tvInlineRouteText.text = fullRoute.joinToString(" ➔ ")
                tvInlineRouteSteps.text = "Lineage Path: ${result.distanceA} Gen Up from ${result.personA.firstName} · ${result.distanceB} Gen Down to ${result.personB.firstName} · ${result.degreeOfConsanguinity}${getOrdinalSuffix(result.degreeOfConsanguinity)} Degree (${result.relationshipCategory} Line)"
            } else {
                cardInlineRoutePreview.visibility = View.GONE
            }
        } else {
            resultCard.setBackgroundResource(R.drawable.result_card_danger_bg)
            tvResultLabel.text = "NO BLOOD RELATION DETECTED"
            tvResultLabel.setTextColor(if (isDark) Color.parseColor("#FF5C5C") else Color.parseColor("#DC2626"))
            tvType.text = "Not Related"
            tvType.setTextColor(if (isDark) Color.parseColor("#E0E0E0") else ContextCompat.getColor(this, R.color.text_primary))
            tvExplanation.text = result.explanation
            ancestorContainer.visibility = View.GONE
            findViewById<LinearLayout>(R.id.cardInlineRoutePreview)?.visibility = View.GONE
        }
    }

    private fun getOrdinalSuffix(number: Int): String {
        return when {
            number % 100 in 11..13 -> "th"
            number % 10 == 1 -> "st"
            number % 10 == 2 -> "nd"
            number % 10 == 3 -> "rd"
            else -> "th"
        }
    }

    private fun loadFamilyMembers(spinnerA: Spinner, spinnerB: Spinner) {
        val cached = FirestoreHelper.getCachedPersons(treeId)
        if (!cached.isNullOrEmpty()) {
            familyMembers = cached
            val names = cached.map { "${it.firstName} ${it.lastName}" }
            spinnerA.setDarkAdapter(this, names)
            spinnerB.setDarkAdapter(this, names)

            val preselectedId = intent.getStringExtra("preselectedPersonA")
            if (!preselectedId.isNullOrEmpty()) {
                val idx = cached.indexOfFirst { it.id == preselectedId }
                if (idx >= 0) {
                    spinnerA.setSelection(idx)
                }
            }
        }

        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                familyMembers = persons
                val names = persons.map { "${it.firstName} ${it.lastName}" }
                spinnerA.setDarkAdapter(this, names)
                spinnerB.setDarkAdapter(this, names)

                val preselectedId = intent.getStringExtra("preselectedPersonA")
                if (!preselectedId.isNullOrEmpty()) {
                    val idx = persons.indexOfFirst { it.id == preselectedId }
                    if (idx >= 0) {
                        spinnerA.setSelection(idx)
                    }
                }
            },
            onFailure = {
                if (cached.isNullOrEmpty()) {
                    showMessage("Error", "Failed to load family members.")
                }
            }
        )
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun loadRecentTraceReports() {
        val layoutRecent = findViewById<LinearLayout>(R.id.layoutRecentTraces) ?: return
        val containerRecent = findViewById<LinearLayout>(R.id.containerRecentTraces) ?: return

        firestoreHelper.getRecentActivities(
            treeId = treeId,
            limit = 10,
            onSuccess = { activities ->
                val traceActivities = activities.filter { it.type == "TRACE" }
                if (traceActivities.isEmpty()) {
                    layoutRecent.visibility = View.GONE
                    return@getRecentActivities
                }
                layoutRecent.visibility = View.VISIBLE
                containerRecent.removeAllViews()

                val inflater = LayoutInflater.from(this)
                for (activity in traceActivities) {
                    val itemView = inflater.inflate(R.layout.item_recent_trace, containerRecent, false)
                    val tvTitle = itemView.findViewById<TextView>(R.id.tvRecentTraceTitle)
                    val tvDesc = itemView.findViewById<TextView>(R.id.tvRecentTraceDesc)
                    val tvTime = itemView.findViewById<TextView>(R.id.tvRecentTraceTime)

                    tvTitle.text = if (activity.targetName.isNotEmpty()) activity.targetName else "Genealogical Trace"
                    tvDesc.text = activity.description
                    tvTime.text = formatRelativeTime(activity.timestamp)

                    val parts = activity.targetId.split("|")
                    if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                        itemView.setOnClickListener {
                            val intent = Intent(this, RelationshipReportActivity::class.java).apply {
                                putExtra("personAId", parts[0])
                                putExtra("personBId", parts[1])
                                putExtra("treeId", treeId)
                            }
                            startActivity(intent)
                        }
                    }

                    containerRecent.addView(itemView)
                }
            },
            onFailure = {
                layoutRecent.visibility = View.GONE
            }
        )
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Recently"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (60 * 1000)
        val hours = diff / (60 * 60 * 1000)
        val days = diff / (24 * 60 * 60 * 1000)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun applyActionButtonsTheme() {
        val isDark = ThemePreferences.isDarkTheme(this)
        val density = resources.displayMetrics.density

        // 1. Trace Bloodline Button
        (findViewById<Button>(R.id.btnTrace) as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1D9E75"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }

        // 2. View Official Relationship Report Button
        (findViewById<Button>(R.id.btnViewFullReport) as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D4A359"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#07170E"))
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }

        // 3. View Highlight on Tree Canvas Button
        (findViewById<Button>(R.id.btnHighlightOnTree) as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B2E24"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#D4A359"))
                mb.strokeWidth = (1.5f * density).toInt()
                mb.cornerRadius = (24 * density).toInt()
                mb.setTextColor(Color.parseColor("#D4A359"))
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }

        // 4. Interactive Path Ladder Button
        (findViewById<Button>(R.id.btnViewPath) as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#183626"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#1E4530"))
                mb.strokeWidth = (1 * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }

        // 5. Learn About Relationship Types Button
        (findViewById<Button>(R.id.btnLearnMore) as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#183626"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#1E4530"))
                mb.strokeWidth = (1 * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#A3B899"))
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }

        // 6. Trace Again Button
        (findViewById<Button>(R.id.btnTraceAgain) as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#183626"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#1E4530"))
                mb.strokeWidth = (1 * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#A3B899"))
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }
    }
}