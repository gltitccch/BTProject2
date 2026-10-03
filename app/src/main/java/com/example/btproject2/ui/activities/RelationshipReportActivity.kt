package com.example.btproject2.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.example.btproject2.R
import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.TraceResult
import com.example.btproject2.utils.PdfReportGenerator
import com.example.btproject2.utils.ThemePreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelationshipReportActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val bloodlineTracer = BloodlineTracer()

    private var personAId: String = ""
    private var personBId: String = ""
    private var treeId: String = ""
    private var currentResult: TraceResult? = null
    private var treeName: String = "Family Tree"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationship_report)

        personAId = intent.getStringExtra("personAId") ?: ""
        personBId = intent.getStringExtra("personBId") ?: ""
        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        if (personAId.isEmpty() || personBId.isEmpty()) {
            Toast.makeText(this, "Invalid report parameters", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Action Buttons
        findViewById<TextView>(R.id.btnShareText)?.setOnClickListener { shareReportText() }
        findViewById<TextView>(R.id.btnShareTextBottom)?.setOnClickListener { shareReportText() }
        findViewById<TextView>(R.id.btnExportPdf)?.setOnClickListener { generateAndOpenPdf() }
        findViewById<TextView>(R.id.btnSavePrintPdfBottom)?.setOnClickListener { generateAndOpenPdf() }

        findViewById<TextView>(R.id.btnQuickAssistReport)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.RELATIONSHIP_REPORT
            ).show(supportFragmentManager, "QuickAssistReport")
        }

        findViewById<TextView>(R.id.btnOpenVisualPath)?.setOnClickListener {
            val intent = Intent(this, RelationshipPathActivity::class.java).apply {
                putExtra("personAId", personAId)
                putExtra("personBId", personBId)
                putExtra("treeId", treeId)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btnViewTraceOnTree)?.setOnClickListener {
            val r = currentResult
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
                }
                startActivity(intent)
            } else {
                val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                    putExtra("highlightPersonAId", personAId)
                    putExtra("highlightPersonBId", personBId)
                    putExtra("viewMode", "TRACE_HIGHLIGHT")
                }
                startActivity(intent)
            }
        }

        findViewById<View>(R.id.cardReferenceSupport)?.setOnClickListener {
            startActivity(Intent(this, RelationshipTypesActivity::class.java))
        }

        val isDark = ThemePreferences.isDarkTheme(this)
        val density = resources.displayMetrics.density
        (findViewById<View>(R.id.btnViewTraceOnTree) as? MaterialButton)?.let { mb ->
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

        loadReportData()
    }

    private fun loadReportData() {
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                if (tree != null && tree.name.isNotEmpty()) {
                    treeName = tree.name
                }
            },
            onFailure = { /* fallback to default */ }
        )

        firestoreHelper.loadEntireTree(treeId,
            onSuccess = { personMap ->
                val result = bloodlineTracer.trace(personAId, personBId, personMap)
                currentResult = result
                displayReport(result)
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to generate report: ${error.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun displayReport(result: TraceResult) {
        val pA = result.personA
        val pB = result.personB

        // 1. Header & Certificate
        val timestamp = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.tvCertDetails)?.text = "Tree: $treeName • Certified on $timestamp"

        // 2. Person A
        val initA = "${pA.firstName.firstOrNull() ?: ""}${pA.lastName.firstOrNull() ?: ""}".uppercase()
        findViewById<TextView>(R.id.tvAvatarA)?.text = initA
        findViewById<TextView>(R.id.tvNameA)?.text = "${pA.firstName} ${pA.lastName}"
        val livingStrA = if (pA.isLiving) "🌱 Living" else "🕊️ Deceased" + if (pA.deathDate.isNotEmpty()) " (d. ${pA.deathDate})" else ""
        val bDateA = if (pA.birthDate.isNotEmpty()) "b. ${pA.birthDate}" else "Birthdate unrecorded"
        findViewById<TextView>(R.id.tvDetailsA)?.text = "${pA.gender} • $livingStrA\n$bDateA"

        // 3. Person B
        val initB = "${pB.firstName.firstOrNull() ?: ""}${pB.lastName.firstOrNull() ?: ""}".uppercase()
        findViewById<TextView>(R.id.tvAvatarB)?.text = initB
        findViewById<TextView>(R.id.tvNameB)?.text = "${pB.firstName} ${pB.lastName}"
        val livingStrB = if (pB.isLiving) "🌱 Living" else "🕊️ Deceased" + if (pB.deathDate.isNotEmpty()) " (d. ${pB.deathDate})" else ""
        val bDateB = if (pB.birthDate.isNotEmpty()) "b. ${pB.birthDate}" else "Birthdate unrecorded"
        findViewById<TextView>(R.id.tvDetailsB)?.text = "${pB.gender} • $livingStrB\n$bDateB"

        // Steps summary
        val totalSteps = result.distanceA + result.distanceB
        findViewById<TextView>(R.id.tvStepsSummary)?.text = if (totalSteps > 0) "$totalSteps steps" else "Direct"

        // 4. Consanguinity Degree Hero
        val tvDegreePill = findViewById<TextView>(R.id.tvDegreePill)
        val tvRelTitle = findViewById<TextView>(R.id.tvRelationshipTitle)
        val tvConsDetails = findViewById<TextView>(R.id.tvConsanguinityDetails)

        if (result.degreeOfConsanguinity > 0) {
            tvDegreePill.text = "${result.degreeOfConsanguinity}${getOrdinal(result.degreeOfConsanguinity)} Degree"
        } else {
            tvDegreePill.text = result.relationshipCategory
        }
        tvRelTitle.text = result.relationshipType
        tvConsDetails.text = "${result.relationshipCategory} Line • ${result.degreeOfConsanguinity}${getOrdinal(result.degreeOfConsanguinity)} Civil Degree (Roman Civil Method, Art. 963)"

        // 5. Philippine Family Code Legal Assessment
        val tvLegalBadge = findViewById<TextView>(R.id.tvLegalBadge)
        val tvLegalTitle = findViewById<TextView>(R.id.tvLegalTitle)
        val tvLegalDesc = findViewById<TextView>(R.id.tvLegalDescription)
        val tvLegalArticle = findViewById<TextView>(R.id.tvLegalArticle)

        if (result.isMarriageProhibited) {
            tvLegalBadge.text = "PROHIBITED"
            tvLegalBadge.setBackgroundColor(Color.parseColor("#7F1D1D")) // Deep dark red
            tvLegalBadge.setTextColor(Color.parseColor("#FCA5A5"))
        } else {
            tvLegalBadge.text = "PERMISSIBLE"
            tvLegalBadge.setBackgroundColor(Color.parseColor("#064E3B")) // Deep emerald
            tvLegalBadge.setTextColor(Color.parseColor("#6EE7B7"))
        }

        tvLegalTitle.text = result.legalStatus
        tvLegalDesc.text = result.legalDescription
        tvLegalArticle.text = "Statutory Citation: ${result.legalArticle}"

        // 6. Closest Common Ancestor (MRCA)
        val tvMrcaName = findViewById<TextView>(R.id.tvMrcaName)
        val tvMrcaGen = findViewById<TextView>(R.id.tvMrcaGenerations)
        if (result.commonAncestor != null) {
            val ca = result.commonAncestor
            tvMrcaName.text = "★ ${ca.firstName} ${ca.lastName}"
            tvMrcaGen.text = "${result.distanceA} generation(s) from ${pA.firstName} • ${result.distanceB} generation(s) from ${pB.firstName} (Sum = ${result.degreeOfConsanguinity}°)"
        } else {
            tvMrcaName.text = "No Shared Biological Ancestor"
            tvMrcaGen.text = "No biological common ancestor detected within the family tree."
        }

        // 7. Natural Language Explanation
        findViewById<TextView>(R.id.tvNarrativeExplanation)?.text = result.explanation

        // 8. Descent Path Ladder
        val tvPathLadder = findViewById<TextView>(R.id.tvPathLadderText)
        val pathAStr = result.pathFromA.joinToString(" ➔ ") { it.firstName + if (!it.isLiving) " (†)" else "" }
        val pathBStr = result.pathFromB.joinToString(" ➔ ") { it.firstName + if (!it.isLiving) " (†)" else "" }
        val mrcaLabel = result.commonAncestor?.let { "★ ${it.firstName}" + if (!it.isLiving) " (†)" else "" } ?: "Pivot"

        if (result.pathFromA.isNotEmpty() || result.pathFromB.isNotEmpty()) {
            tvPathLadder.text = "Ascending Line: $pathAStr\nCommon Ancestor Junction: $mrcaLabel\nDescending Line: $pathBStr"
        } else {
            tvPathLadder.text = "${pA.firstName} and ${pB.firstName} (Direct non-biological or unconnected evaluation)"
        }
    }

    private fun shareReportText() {
        val r = currentResult ?: return
        val pA = r.personA
        val pB = r.personB

        val shareContent = buildString {
            appendLine("═══════════════════════════════════════════════")
            appendLine("       KINTRACE GENEALOGICAL & LEGAL REPORT    ")
            appendLine("═══════════════════════════════════════════════")
            appendLine("• Subjects: ${pA.firstName} ${pA.lastName} & ${pB.firstName} ${pB.lastName}")
            appendLine("• Relationship: ${r.relationshipType}")
            appendLine("• Degree of Consanguinity: ${r.degreeOfConsanguinity}${getOrdinal(r.degreeOfConsanguinity)} Civil Degree (Roman Method)")
            appendLine("• Line: ${r.relationshipCategory} Line")
            if (r.commonAncestor != null) {
                appendLine("• Closest Common Ancestor: ★ ${r.commonAncestor.firstName} ${r.commonAncestor.lastName}")
                appendLine("• Generational Steps: ${r.distanceA} from ${pA.firstName}, ${r.distanceB} from ${pB.firstName}")
            }
            appendLine("───────────────────────────────────────────────")
            appendLine("PHILIPPINE FAMILY CODE VERDICT:")
            appendLine("• Status: ${if (r.isMarriageProhibited) "MARRIAGE PROHIBITED" else "PERMISSIBLE"}")
            appendLine("• Legal Ground: ${r.legalStatus}")
            appendLine("• Article: ${r.legalArticle}")
            appendLine("• Description: ${r.legalDescription}")
            appendLine("───────────────────────────────────────────────")
            appendLine("STEP-BY-STEP EXPLANATION:")
            appendLine(r.explanation)
            appendLine("═══════════════════════════════════════════════")
            appendLine("Generated by KinTrace — STI College Marikina Capstone")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareContent)
            putExtra(Intent.EXTRA_SUBJECT, "KinTrace Relationship Report: ${pA.firstName} & ${pB.firstName}")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Genealogical Report via"))
    }

    private fun generateAndOpenPdf() {
        val r = currentResult ?: return
        try {
            val (pdfFile, contentUri) = PdfReportGenerator.generateReport(this, r, treeName)
            Toast.makeText(this, "PDF generated: ${pdfFile.name}", Toast.LENGTH_SHORT).show()

            // Open with View or Send Intent
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
            }

            // Fallback chooser that also allows sharing
            val chooser = Intent.createChooser(viewIntent, "Open / Print PDF Report")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getOrdinal(number: Int): String {
        return when {
            number % 100 in 11..13 -> "th"
            number % 10 == 1 -> "st"
            number % 10 == 2 -> "nd"
            number % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}

