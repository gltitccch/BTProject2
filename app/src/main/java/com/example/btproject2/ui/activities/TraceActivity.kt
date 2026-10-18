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

    private val networkListener: (com.example.btproject2.utils.NetworkStateMonitor.NetworkState) -> Unit = { state ->
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.updateNetworkState(this, state) {
            val spinnerA = findViewById<android.widget.Spinner>(R.id.spinnerPersonA)
            val spinnerB = findViewById<android.widget.Spinner>(R.id.spinnerPersonB)
            if (spinnerA != null && spinnerB != null) {
                loadFamilyMembers(spinnerA, spinnerB)
            }
        }
    }

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

        // ── Floating Heritage Island Bottom Navigation Coordinator ──
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.setup(
            activity = this,
            activeTab = com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.NavTab.TRACE,
            treeIdProvider = { treeId },
            onProfileClicked = {
                // Return to home where account profile dialog is hosted
                val intent = Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intent)
            }
        )

        loadFamilyMembers(spinnerA, spinnerB)

        val openSearchA = View.OnClickListener {
            showSpotlightMemberSearchSheet(isPersonA = true, spinnerA = spinnerA, spinnerB = spinnerB)
        }
        val openSearchB = View.OnClickListener {
            showSpotlightMemberSearchSheet(isPersonA = false, spinnerA = spinnerA, spinnerB = spinnerB)
        }
        findViewById<View>(R.id.cardSelectPersonA)?.setOnClickListener(openSearchA)
        findViewById<View>(R.id.btnSpotlightPersonA)?.setOnClickListener(openSearchA)
        findViewById<View>(R.id.cardSelectPersonB)?.setOnClickListener(openSearchB)
        findViewById<View>(R.id.btnSpotlightPersonB)?.setOnClickListener(openSearchB)

        spinnerA.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                showSpotlightMemberSearchSheet(isPersonA = true, spinnerA = spinnerA, spinnerB = spinnerB)
            }
            true
        }
        spinnerB.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                showSpotlightMemberSearchSheet(isPersonA = false, spinnerA = spinnerA, spinnerB = spinnerB)
            }
            true
        }

        findViewById<View>(R.id.btnSwapTracePersons)?.setOnClickListener {
            if (familyMembers.isNotEmpty()) {
                val idxA = spinnerA.selectedItemPosition
                val idxB = spinnerB.selectedItemPosition
                if (idxA >= 0 && idxB >= 0) {
                    spinnerA.setSelection(idxB)
                    spinnerB.setSelection(idxA)
                }
            }
        }

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

    override fun onStart() {
        super.onStart()
        com.example.btproject2.utils.NetworkStateMonitor.startMonitoring(this)
        com.example.btproject2.utils.NetworkStateMonitor.addListener(networkListener)
    }

    override fun onResume() {
        super.onResume()
        loadRecentTraceReports()
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.btproject2.utils.NetworkStateMonitor.removeListener(networkListener)
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

        // Consanguinity Scope & Degree Assessment Card
        val legalCard = findViewById<LinearLayout>(R.id.legalCard)
        val tvLegalBadge = findViewById<TextView>(R.id.tvLegalBadge)
        val tvLegalTitle = findViewById<TextView>(R.id.tvLegalTitle)
        val tvLegalDescription = findViewById<TextView>(R.id.tvLegalDescription)
        val tvLegalArticle = findViewById<TextView>(R.id.tvLegalArticle)

        legalCard.visibility = View.VISIBLE
        val deg = result.degreeOfConsanguinity
        val ord = getOrdinalSuffix(deg)

        when {
            result.isRelated && deg in 1..4 -> {
                tvLegalBadge.text = "WITHIN 4TH DEGREE ($deg°)"
                tvLegalBadge.setTextColor(ContextCompat.getColor(this, R.color.status_mint_text))
                tvLegalBadge.setBackgroundResource(R.drawable.bg_legal_valid_pill)
                tvLegalTitle.text = "Within 4th Degree of Consanguinity ($deg$ord Degree)"
                tvLegalTitle.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
                tvLegalDescription.text = if (result.relationshipCategory.equals("Lineal", ignoreCase = true)) {
                    "${result.personA.firstName} and ${result.personB.firstName} are connected in the direct Lineal Line at the $deg$ord Civil Degree (${maxOf(result.distanceA, result.distanceB)} generational step(s) apart)."
                } else {
                    "${result.personA.firstName} and ${result.personB.firstName} are within the 1st–4th Degree Consanguinity scope ($deg$ord Civil Degree: ${result.distanceA} step(s) up to their shared ancestor + ${result.distanceB} step(s) down)."
                }
                tvLegalArticle.text = "Computation Basis: Roman Civil Method (Articles 963–966, Civil Code of the Philippines)"
            }
            result.isRelated && deg > 4 -> {
                tvLegalBadge.text = "BEYOND 4TH DEGREE ($deg°)"
                tvLegalBadge.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
                tvLegalBadge.setBackgroundResource(R.drawable.badge_pill_dark)
                tvLegalTitle.text = "Beyond 4th Degree of Consanguinity ($deg$ord Degree)"
                tvLegalTitle.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
                tvLegalDescription.text = "${result.personA.firstName} and ${result.personB.firstName} share a common family ancestor (${result.distanceA} step(s) up + ${result.distanceB} step(s) down = $deg$ord Civil Degree), which is beyond the 1st–4th degree core consanguinity scope."
                tvLegalArticle.text = "Extended Clan Lineage • Beyond 4th Civil Degree Scope (Articles 963–966)"
            }
            result.isRelated -> {
                tvLegalBadge.text = "DIRECT / AFFINITY"
                tvLegalBadge.setTextColor(ContextCompat.getColor(this, R.color.status_mint_text))
                tvLegalBadge.setBackgroundResource(R.drawable.bg_legal_valid_pill)
                tvLegalTitle.text = "${result.relationshipType} (${result.relationshipCategory} Connection)"
                tvLegalTitle.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
                tvLegalDescription.text = "${result.personA.firstName} and ${result.personB.firstName} are connected through ${result.relationshipCategory.lowercase()} family ties (${result.relationshipType}) rather than biological consanguinity degrees."
                tvLegalArticle.text = "Family Record Connection • Non-Consanguineous / Direct Link"
            }
            else -> {
                tvLegalBadge.text = "UNCONNECTED"
                tvLegalBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tvLegalBadge.setBackgroundResource(R.drawable.badge_pill_dark)
                tvLegalTitle.text = "No Shared Consanguinity Path Found"
                tvLegalTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvLegalDescription.text = "No biological ancestor or bloodline path connects ${result.personA.firstName} and ${result.personB.firstName} in the current family tree."
                tvLegalArticle.text = "Scope Check: 0 Shared Ancestors Detected"
            }
        }

        if (result.isRelated) {
            resultCard.setBackgroundResource(R.drawable.result_card_bg)
            tvResultLabel.text = "RELATIONSHIP FOUND"
            tvResultLabel.setTextColor(ContextCompat.getColor(this, R.color.status_mint_text))
            tvType.text = result.relationshipType
            tvType.setTextColor(ContextCompat.getColor(this, R.color.gold_accent))
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
            tvResultLabel.setTextColor(ContextCompat.getColor(this, R.color.danger))
            tvType.text = "Not Related"
            tvType.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
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
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = (1.5f * density).toInt()
                mb.cornerRadius = (24 * density).toInt()
                mb.setTextColor(Color.parseColor("#B45309"))
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
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F7F1"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#D2D9CE"))
                mb.strokeWidth = (1 * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#0F172A"))
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
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F7F1"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#D2D9CE"))
                mb.strokeWidth = (1 * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#475569"))
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
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F7F1"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#D2D9CE"))
                mb.strokeWidth = (1 * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#475569"))
            }
        }
    }

    private fun showSpotlightMemberSearchSheet(isPersonA: Boolean, spinnerA: Spinner, spinnerB: Spinner) {
        if (familyMembers.isEmpty()) {
            showMessage("No Members", "Family members are still loading or the tree is empty.")
            return
        }

        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val colorBg = ContextCompat.getColor(this, R.color.forest_bg)
        val colorCard = ContextCompat.getColor(this, R.color.forest_card)
        val colorBorder = ContextCompat.getColor(this, R.color.forest_card_border)
        val colorTextPrimary = ContextCompat.getColor(this, R.color.text_primary)
        val colorTextSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        val colorTextHint = ContextCompat.getColor(this, R.color.text_hint)
        val colorAccent = ContextCompat.getColor(this, R.color.gold_accent)
        val colorPillBg = ContextCompat.getColor(this, R.color.primary_light)
        val colorPillStroke = ContextCompat.getColor(this, R.color.primary_light_stroke)
        val colorActivePillText = ContextCompat.getColor(this, R.color.btn_gold_text)

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(22))
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    24 * density, 24 * density,
                    24 * density, 24 * density,
                    0f, 0f, 0f, 0f
                )
                setColor(colorBg)
                setStroke(dp(1), colorBorder)
            }
            background = bgDrawable
        }

        // Drag handle
        val handle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(4)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 4 * density
                setColor(colorBorder)
            }
        }
        root.addView(handle)

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val tvSheetTitle = TextView(this).apply {
            text = if (isPersonA) "Select First Person (Person A)" else "Select Second Person (Person B)"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colorAccent)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCloseSheet = TextView(this).apply {
            text = "✕"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(colorTextSecondary)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(tvSheetTitle)
        headerRow.addView(btnCloseSheet)
        root.addView(headerRow)

        // Search Box Row
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(colorCard)
                setStroke((1.5f * density).toInt(), colorAccent)
            }
        }
        val ivSearchIcon = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_search_minimal)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                rightMargin = dp(10)
            }
        }
        val etSearch = android.widget.EditText(this).apply {
            id = R.id.etSearch
            hint = "Type first name, last name, or birth year..."
            textSize = 14f
            setSingleLine(true)
            background = null
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextHint)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClearQuery = TextView(this).apply {
            text = "✕"
            textSize = 13f
            visibility = View.GONE
            setPadding(dp(8), dp(2), dp(4), dp(2))
            setTextColor(colorTextSecondary)
        }
        searchBox.addView(ivSearchIcon)
        searchBox.addView(etSearch)
        searchBox.addView(btnClearQuery)
        root.addView(searchBox)

        // Filter Pills Row
        val filterScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        filterScroll.addView(filterRow)
        root.addView(filterScroll)

        var activeFilter = "ALL"
        val filterOptions = listOf(
            "ALL" to "All (${familyMembers.size})",
            "LIVING" to "Living",
            "DECEASED" to "Deceased",
            "MALE" to "Male",
            "FEMALE" to "Female"
        )
        val pillViews = mutableMapOf<String, TextView>()

        // Result Count Subtitle
        val tvMatchCount = TextView(this).apply {
            textSize = 11f
            setTextColor(colorTextSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        root.addView(tvMatchCount)

        // Scrollable Member List
        val listScroll = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.48f).toInt()
            )
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        listScroll.addView(listContainer)
        root.addView(listScroll)

        fun styleFilterPills() {
            pillViews.forEach { (key, pill) ->
                val selected = key == activeFilter
                pill.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16 * density
                    if (selected) {
                        setColor(colorAccent)
                    } else {
                        setColor(colorCard)
                        setStroke(dp(1), colorBorder)
                    }
                }
                pill.setTextColor(if (selected) colorActivePillText else colorTextSecondary)
            }
        }

        fun renderMembers() {
            val query = etSearch.text?.toString().orEmpty()
            btnClearQuery.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            val filtered = filterTraceMembers(familyMembers, query, activeFilter)
            tvMatchCount.text = "Showing ${filtered.size} of ${familyMembers.size} family members"
            listContainer.removeAllViews()

            if (filtered.isEmpty()) {
                val emptyTv = TextView(this).apply {
                    text = "No family members match \"${query.trim()}\"."
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(16), dp(28), dp(16), dp(28))
                    setTextColor(colorTextSecondary)
                }
                listContainer.addView(emptyTv)
                return
            }

            val currentOtherIndex = if (isPersonA) spinnerB.selectedItemPosition else spinnerA.selectedItemPosition
            val currentOtherId = familyMembers.getOrNull(currentOtherIndex)?.id

            filtered.forEach { person ->
                val isOtherSelected = person.id == currentOtherId
                val itemRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 12 * density
                        setColor(colorCard)
                        setStroke(dp(1), colorBorder)
                    }
                }

                // Initials Avatar
                val initials = listOfNotNull(
                    person.firstName.firstOrNull()?.uppercaseChar(),
                    person.lastName.firstOrNull()?.uppercaseChar()
                ).joinToString("").ifEmpty { "?" }

                val tvAvatar = TextView(this).apply {
                    text = initials
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colorAccent)
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                        rightMargin = dp(12)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(colorPillBg)
                        setStroke(dp(1), colorPillStroke)
                    }
                }

                // Name + Meta Column
                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvName = TextView(this).apply {
                    text = "${person.firstName} ${person.lastName}".trim()
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(colorTextPrimary)
                }
                val birthYear = person.birthDate.takeLast(4).takeIf { it.all(Char::isDigit) && it.length == 4 }
                    ?: person.birthDate.ifBlank { "Year N/A" }
                val statusLabel = if (person.isLiving) "Living" else "Deceased"
                val otherNote = if (isOtherSelected) " • Currently Person ${if (isPersonA) "B" else "A"}" else ""
                val tvMeta = TextView(this).apply {
                    text = "${person.gender.ifBlank { "Member" }} • b. $birthYear • $statusLabel$otherNote"
                    textSize = 11.5f
                    setTextColor(colorTextSecondary)
                }
                infoCol.addView(tvName)
                infoCol.addView(tvMeta)

                // Select Pill
                val tvSelect = TextView(this).apply {
                    text = "Select ›"
                    textSize = 11.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    setTextColor(colorAccent)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 10 * density
                        setColor(colorPillBg)
                        setStroke(dp(1), colorPillStroke)
                    }
                }

                itemRow.addView(tvAvatar)
                itemRow.addView(infoCol)
                itemRow.addView(tvSelect)

                itemRow.setOnClickListener {
                    val targetIdx = familyMembers.indexOfFirst { it.id == person.id }
                    if (targetIdx >= 0) {
                        if (isPersonA) {
                            spinnerA.setSelection(targetIdx)
                        } else {
                            spinnerB.setSelection(targetIdx)
                        }
                    }
                    dialog.dismiss()
                }

                listContainer.addView(itemRow)
            }
        }

        filterOptions.forEach { (key, label) ->
            val pill = TextView(this).apply {
                text = label
                textSize = 11.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8) }
                setOnClickListener {
                    activeFilter = key
                    styleFilterPills()
                    renderMembers()
                }
            }
            pillViews[key] = pill
            filterRow.addView(pill)
        }

        btnClearQuery.setOnClickListener {
            etSearch.setText("")
        }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderMembers()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        styleFilterPills()
        renderMembers()

        dialog.setContentView(root)
        dialog.show()
        etSearch.requestFocus()
    }

    companion object {
        fun filterTraceMembers(
            members: List<Person>,
            query: String,
            filter: String = "ALL"
        ): List<Person> {
            val trimmed = query.trim().lowercase(Locale.ROOT)
            return members.filter { person ->
                val matchesFilter = when (filter.uppercase(Locale.ROOT)) {
                    "LIVING" -> person.isLiving
                    "DECEASED" -> !person.isLiving
                    "MALE" -> person.gender.equals("Male", ignoreCase = true)
                    "FEMALE" -> person.gender.equals("Female", ignoreCase = true)
                    else -> true
                }
                if (!matchesFilter) return@filter false

                if (trimmed.isEmpty()) return@filter true

                val fullName = "${person.firstName} ${person.lastName}".lowercase(Locale.ROOT)
                val reverseName = "${person.lastName} ${person.firstName}".lowercase(Locale.ROOT)
                val birth = person.birthDate.lowercase(Locale.ROOT)
                val birthPlace = person.birthPlace.lowercase(Locale.ROOT)
                fullName.contains(trimmed) ||
                    reverseName.contains(trimmed) ||
                    birth.contains(trimmed) ||
                    birthPlace.contains(trimmed)
            }
        }
    }
}