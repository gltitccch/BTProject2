package com.example.btproject2.ui.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.example.btproject2.R
import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.models.TraceResult
import com.example.btproject2.utils.DocumentHelper
import com.example.btproject2.utils.ThemePreferences

class RelationshipPathActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val bloodlineTracer = BloodlineTracer()
    private var treeId = ""
    private var lastTraceResult: TraceResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationship_path)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val tvSubtitle = findViewById<TextView>(R.id.tvPathSubtitle)
        val pathContainer = findViewById<LinearLayout>(R.id.pathContainer)

        btnBack.setOnClickListener { finish() }

        val personAId = intent.getStringExtra("personAId") ?: return
        val personBId = intent.getStringExtra("personBId") ?: return
        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        // Instant cache dispatch with network fallback
        val cached = FirestoreHelper.getCachedPersons()
        val cachedMap = cached?.associateBy { it.id }
        if (cachedMap != null && cachedMap.containsKey(personAId) && cachedMap.containsKey(personBId)) {
            val result = bloodlineTracer.trace(personAId, personBId, cachedMap)
            renderTraceResult(result, tvSubtitle, pathContainer)
        } else {
            firestoreHelper.loadEntireTree(treeId,
                onSuccess = { personMap ->
                    if (personMap.containsKey(personAId) && personMap.containsKey(personBId)) {
                        val result = bloodlineTracer.trace(personAId, personBId, personMap)
                        renderTraceResult(result, tvSubtitle, pathContainer)
                    }
                },
                onFailure = {}
            )
        }
    }

    private fun renderTraceResult(
        result: TraceResult,
        tvSubtitle: TextView,
        pathContainer: LinearLayout
    ) {
        lastTraceResult = result
        pathContainer.removeAllViews()

        val layoutHeroSummary = findViewById<LinearLayout>(R.id.layoutHeroSummary)
        val tvConsanguinityBadge = findViewById<TextView>(R.id.tvConsanguinityBadge)
        val tvRelationshipHeading = findViewById<TextView>(R.id.tvRelationshipHeading)
        val tvSharedAncestorSummary = findViewById<TextView>(R.id.tvSharedAncestorSummary)
        val btnHighlightOnTreeHeader = findViewById<Button>(R.id.btnHighlightOnTreeHeader)
        val layoutBottomActions = findViewById<LinearLayout>(R.id.layoutBottomActions)
        val btnHighlightOnTreeBottom = findViewById<Button>(R.id.btnHighlightOnTreeBottom)
        val btnViewReport = findViewById<Button>(R.id.btnViewReport)

        if (result.isRelated) {
            tvSubtitle.text = "Step-by-step bloodline connection between ${result.personA.firstName} and ${result.personB.firstName}"
            layoutHeroSummary.visibility = View.VISIBLE
            layoutBottomActions.visibility = View.VISIBLE

            val ordSuffix = getOrdinalSuffix(result.degreeOfConsanguinity)
            tvConsanguinityBadge.text = "${result.degreeOfConsanguinity}$ordSuffix CIVIL DEGREE · ${result.relationshipCategory.uppercase()} LINE"
            tvRelationshipHeading.text = result.relationshipType

            val ancestorName = result.commonAncestor?.let { "${it.firstName} ${it.lastName}" }
            tvSharedAncestorSummary.text = if (ancestorName != null) {
                "Nearest Shared Ancestor: $ancestorName (${result.distanceA} Gen Up from ${result.personA.firstName}, ${result.distanceB} Gen Down to ${result.personB.firstName})"
            } else {
                result.explanation
            }

            val openTreeAction = View.OnClickListener {
                val pathIds = ArrayList<String>()
                pathIds.addAll(result.pathFromA.map { it.id })
                result.commonAncestor?.id?.let { pathIds.add(it) }
                pathIds.addAll(result.pathFromB.map { it.id })

                val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                    putExtra("highlightPersonAId", result.personA.id)
                    putExtra("highlightPersonBId", result.personB.id)
                    putExtra("mrcaId", result.commonAncestor?.id)
                    putStringArrayListExtra("highlightPathIds", pathIds)
                    putExtra("viewMode", "TRACE_HIGHLIGHT")
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }

            btnHighlightOnTreeHeader.setOnClickListener(openTreeAction)
            btnHighlightOnTreeBottom.setOnClickListener(openTreeAction)

            btnViewReport.setOnClickListener {
                val intent = Intent(this, RelationshipReportActivity::class.java).apply {
                    putExtra("personAId", result.personA.id)
                    putExtra("personBId", result.personB.id)
                    putExtra("treeId", treeId)
                }
                startActivity(intent)
            }

            val isDark = ThemePreferences.isDarkTheme(this)
            val density = resources.displayMetrics.density

            listOf(btnHighlightOnTreeHeader, btnHighlightOnTreeBottom).forEach { btn ->
                (btn as? MaterialButton)?.let { mb ->
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
            }

            (btnViewReport as? MaterialButton)?.let { mb ->
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

            var stepCounter = 1

            // Build path from Person A up to ancestor
            for (i in result.pathFromA.indices) {
                val person = result.pathFromA[i]
                val isAncestor = result.commonAncestor != null && person.id == result.commonAncestor.id
                val isStarter = i == 0
                val roleTag = when {
                    isStarter -> "Starting Individual (Person A)"
                    isAncestor -> "Most Recent Common Ancestor"
                    else -> "${i} Generation${if (i > 1) "s" else ""} Up (Ascendant)"
                }

                addPathNode(
                    container = pathContainer,
                    stepNum = stepCounter++,
                    person = person,
                    isAncestor = isAncestor,
                    isStarter = isStarter,
                    isTarget = false,
                    subtitle = roleTag
                )

                if (i < result.pathFromA.size - 1) {
                    addConnectorPipe(pathContainer, isAscending = true, label = "Biological Child of")
                }
            }

            // Path from ancestor down to Person B
            if (result.pathFromB.size > 1) {
                addConnectorPipe(pathContainer, isAscending = false, label = "Biological Parent of")

                val pathDown = result.pathFromB.reversed()
                for (i in 1 until pathDown.size) {
                    val person = pathDown[i]
                    val isLast = i == pathDown.size - 1
                    val stepsRemaining = pathDown.size - 1 - i
                    val roleTag = when {
                        isLast -> "Target Individual (Person B)"
                        else -> "${stepsRemaining} Generation${if (stepsRemaining > 1) "s" else ""} Up from ${result.personB.firstName}"
                    }

                    addPathNode(
                        container = pathContainer,
                        stepNum = stepCounter++,
                        person = person,
                        isAncestor = false,
                        isStarter = false,
                        isTarget = isLast,
                        subtitle = roleTag
                    )

                    if (i < pathDown.size - 1) {
                        addConnectorPipe(pathContainer, isAscending = false, label = "Biological Parent of")
                    }
                }
            }
        } else {
            tvSubtitle.text = "No biological blood connection found"
            layoutHeroSummary.visibility = View.GONE
            layoutBottomActions.visibility = View.GONE

            val noResultCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.result_card_danger_bg)
                setPadding(32, 32, 32, 32)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = 16
                layoutParams = params
            }

            val tvNoIcon = TextView(this).apply {
                text = "⚠️"
                textSize = 28f
                gravity = Gravity.CENTER
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = 8
                layoutParams = p
            }
            noResultCard.addView(tvNoIcon)

            val tvNoTitle = TextView(this).apply {
                text = "No Common Ancestor Detected"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#FF8A8A"))
                gravity = Gravity.CENTER
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = 6
                layoutParams = p
            }
            noResultCard.addView(tvNoTitle)

            val tvNoDesc = TextView(this).apply {
                text = result.explanation.ifBlank { "These two individuals do not share a common ancestor within the registered family tree records." }
                textSize = 13f
                setTextColor(Color.parseColor("#A3B899"))
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.3f)
            }
            noResultCard.addView(tvNoDesc)

            pathContainer.addView(noResultCard)
        }
    }

    private fun addPathNode(
        container: LinearLayout,
        stepNum: Int,
        person: Person,
        isAncestor: Boolean,
        isStarter: Boolean,
        isTarget: Boolean,
        subtitle: String
    ) {
        val nodeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 0
            layoutParams = params

            if (isAncestor) {
                setBackgroundResource(R.drawable.path_node_ancestor_bg)
            } else {
                setBackgroundResource(R.drawable.bg_forest_card)
            }
        }

        // Top Step Badge Pill
        val badgeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.bottomMargin = 10
            layoutParams = p
        }

        val stepBadge = TextView(this).apply {
            text = "STEP $stepNum"
            textSize = 9f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#A3B899"))
            setBackgroundResource(R.drawable.badge_pill_dark)
            setPadding(16, 4, 16, 4)
            gravity = Gravity.CENTER
        }
        badgeContainer.addView(stepBadge)

        val rolePill = TextView(this).apply {
            val pillText = when {
                isAncestor -> "★ MOST RECENT COMMON ANCESTOR"
                isStarter -> "🚩 PERSON A"
                isTarget -> "🎯 PERSON B"
                else -> "⚡ BLOODLINE LINK"
            }
            text = pillText
            textSize = 9f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(16, 4, 16, 4)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginStart = 8
            layoutParams = lp

            if (isAncestor) {
                setTextColor(Color.parseColor("#0A1B12"))
                setBackgroundResource(R.drawable.badge_pill_gold)
            } else if (isStarter || isTarget) {
                setTextColor(Color.parseColor("#D4A359"))
                setBackgroundResource(R.drawable.badge_pill_dark)
            } else {
                setTextColor(Color.parseColor("#4ECCA3"))
                setBackgroundResource(R.drawable.badge_pill_dark)
            }
        }
        badgeContainer.addView(rolePill)

        nodeLayout.addView(badgeContainer)

        // Avatar Initials Circle
        val avatarView = TextView(this).apply {
            val f = person.firstName.firstOrNull()?.uppercaseChar() ?: '?'
            val l = person.lastName.firstOrNull()?.uppercaseChar() ?: '?'
            text = "$f$l"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            if (isAncestor) {
                setTextColor(Color.parseColor("#0A1B12"))
                setBackgroundResource(R.drawable.circle_gold_btn)
            } else if (person.gender.equals("Female", ignoreCase = true)) {
                setTextColor(Color.parseColor("#F472B6"))
                setBackgroundResource(R.drawable.avatar_circle_dark)
            } else {
                setTextColor(Color.parseColor("#4ECCA3"))
                setBackgroundResource(R.drawable.avatar_circle_dark)
            }
            val sizePx = (44 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                bottomMargin = 8
            }
        }
        nodeLayout.addView(avatarView)

        // Full Name
        val nameView = TextView(this).apply {
            text = "${person.firstName} ${person.lastName}".trim()
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            if (isAncestor) {
                setTextColor(Color.parseColor("#F5D77F"))
            } else {
                setTextColor(Color.parseColor("#FFFFFF"))
            }
        }
        nodeLayout.addView(nameView)

        // Subtitle / Generational Role
        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (isAncestor) Color.parseColor("#D4A359") else Color.parseColor("#4ECCA3"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 4
            layoutParams = p
        }
        nodeLayout.addView(subtitleView)

        // Vital Life Details & Document Badge
        val ageStr = DocumentHelper.calculateAge(person.birthDate, person.deathDate, person.isLiving)
        val detailsRow = TextView(this).apply {
            val details = mutableListOf<String>()
            if (person.gender.isNotBlank()) details.add(person.gender)
            if (ageStr.isNotBlank()) details.add(ageStr)
            if (person.documents.isNotEmpty()) details.add("✓ Verified (${person.documents.size} Docs)")
            text = details.joinToString(" · ")
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#A3B899"))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = 4
            layoutParams = p
        }
        nodeLayout.addView(detailsRow)

        container.addView(nodeLayout)
    }

    private fun addConnectorPipe(container: LinearLayout, isAscending: Boolean, label: String) {
        val connectorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 6
            params.bottomMargin = 6
            layoutParams = params
        }

        // Connector Arrow & Label Pill
        val pill = TextView(this).apply {
            text = if (isAscending) "▲  $label" else "▼  $label"
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#D4A359"))
            setBackgroundResource(R.drawable.badge_pill_dark)
            setPadding(20, 6, 20, 6)
            gravity = Gravity.CENTER
        }
        connectorLayout.addView(pill)

        container.addView(connectorLayout)
    }

    private fun getOrdinalSuffix(number: Int): String {
        return when {
            number % 100 in 11..13 -> "TH"
            number % 10 == 1 -> "ST"
            number % 10 == 2 -> "ND"
            number % 10 == 3 -> "RD"
            else -> "TH"
        }
    }
}