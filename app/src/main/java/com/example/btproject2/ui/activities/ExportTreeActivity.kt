package com.example.btproject2.ui.activities

import com.example.btproject2.utils.KinTraceAlert

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import java.io.IOException
import java.io.OutputStream

class ExportTreeActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private var treeId = ""
    private var treeName = "Family Tree"
    private var persons: List<Person> = emptyList()

    private lateinit var tvTotalMembers: TextView
    private lateinit var tvParentLinks: TextView
    private lateinit var tvLivingMembers: TextView
    private lateinit var tvExportFormat: TextView
    private lateinit var btnExport: TextView
    private var selectedFormat: String? = null
    private var isExporting = false

    // Format card views
    private lateinit var cardPdf: LinearLayout
    private lateinit var cardPng: LinearLayout
    private lateinit var cardGedcom: LinearLayout
    private lateinit var cardCsv: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export_tree)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        treeName = intent.getStringExtra("TREE_NAME")
            ?: intent.getStringExtra("treeName")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeName(this).ifBlank { "Family Tree" }

        val btnBack = findViewById<TextView>(R.id.btnBack)
        tvTotalMembers  = findViewById(R.id.tvTotalMembers)
        tvParentLinks   = findViewById(R.id.tvParentLinks)
        tvLivingMembers = findViewById(R.id.tvLivingMembers)
        tvExportFormat  = findViewById(R.id.tvExportFormat)
        btnExport       = findViewById(R.id.btnExport)
        cardPdf         = findViewById(R.id.cardPdf)
        cardPng         = findViewById(R.id.cardPng)
        cardGedcom      = findViewById(R.id.cardGedcom)
        cardCsv         = findViewById(R.id.cardCsv)

        btnBack.setOnClickListener { finish() }

        val btnQuickAssistExport = findViewById<TextView>(R.id.btnQuickAssistExport)
        btnQuickAssistExport?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.newInstance(
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.EXPORT_TREE
            ).show(supportFragmentManager, "QuickAssistExport")
        }

        // Format selection
        cardPdf.setOnClickListener    { selectFormat("PDF",    cardPdf) }
        cardPng.setOnClickListener    { selectFormat("PNG",    cardPng) }
        cardGedcom.setOnClickListener { selectFormat("GEDCOM", cardGedcom) }
        cardCsv.setOnClickListener    { selectFormat("CSV",    cardCsv) }

        btnExport.setOnClickListener {
            if (isExporting) return@setOnClickListener
            when (selectedFormat) {
                "PDF"    -> exportPdf()
                "PNG"    -> exportPng()
                "GEDCOM" -> exportGedcom()
                "CSV"    -> exportCsv()
                else     -> KinTraceAlert.show(this, "Please select a format first")
            }
        }

        loadData()
        updateTreePillBadge()
    }

    override fun onResume() {
        super.onResume()
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.EXPORT_TREE,
            treeId = treeId
        )
    }

    private fun loadData() {
        if (treeId.isNotBlank()) {
            firestoreHelper.getTree(treeId,
                onSuccess = { tree ->
                    if (tree != null && tree.name.isNotBlank()) {
                        treeName = tree.name
                        updateTreePillBadge()
                    }
                },
                onFailure = {}
            )
        }
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { list ->
                persons = list
                val parentLinks = list.count { it.fatherId != null || it.motherId != null }
                val living = list.count { it.isLiving }
                tvTotalMembers.text  = list.size.toString()
                tvParentLinks.text   = parentLinks.toString()
                tvLivingMembers.text = living.toString()
                updateTreePillBadge()
            },
            onFailure = {
                toast("Failed to load family members: ${it.message}")
            }
        )
    }

    private fun updateTreePillBadge() {
        val tvTreePillBadge = findViewById<TextView?>(R.id.tvTreePillBadge) ?: return
        val count = persons.size
        val countText = if (count == 1) "1 Member" else "$count Members"
        val displayName = if (treeName.isNotBlank() && treeName != "Family") treeName else "Family Tree"
        tvTreePillBadge.text = "🌳 $displayName • $countText"
    }

    private fun selectFormat(format: String, card: LinearLayout) {
        selectedFormat = format
        tvExportFormat.text = format

        // Reset all cards
        listOf(cardPdf, cardPng, cardGedcom, cardCsv).forEach {
            it.setBackgroundResource(R.drawable.card_bg)
        }
        // Highlight selected with gold accent border
        card.setBackgroundResource(R.drawable.bg_export_card_selected)

        // Enable export button with radiant gold styling
        btnExport.setBackgroundResource(R.drawable.btn_gold_primary)
        btnExport.setTextColor(resources.getColor(R.color.forest_bg, null))
        btnExport.text = "Export as $format"
    }

    private fun setExporting(isExp: Boolean, label: String = "") {
        isExporting = isExp
        btnExport.isEnabled = !isExp
        if (isExp) {
            btnExport.text = label
            btnExport.alpha = 0.65f
        } else {
            btnExport.alpha = 1.0f
            btnExport.text = if (selectedFormat != null) "Export as $selectedFormat" else "Select a format first"
        }
    }

    private fun getSafeTreeFileName(suffix: String): String {
        val safe = treeName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').take(30).ifBlank { "FamilyTree" }
        return "${safe}_$suffix"
    }

    // ── Export implementations ────────────────────────────────────────

    private fun exportPng() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        if (isExporting) return
        setExporting(true, "Generating visual tree PNG...")

        val personsSnapshot = persons.toList()
        val treeNameSnapshot = treeName
        val fileName = getSafeTreeFileName("Chart.png")
        val treeView = FamilyTreeView(this)

        Thread {
            try {
                val bmp = treeView.exportToBitmap(personsSnapshot, treeNameSnapshot)
                saveFile(fileName, "image/png") { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setExporting(false)
                    toast("✅ Visual Tree saved to Downloads: $fileName")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setExporting(false)
                    toast("Export failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun exportPdf() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        if (isExporting) return
        setExporting(true, "Generating comprehensive PDF report...")

        val personsSnapshot = persons.toList()
        val treeNameSnapshot = treeName
        val treeView = FamilyTreeView(this)

        Thread {
            try {
                val treeBmp = treeView.exportToBitmap(personsSnapshot, treeNameSnapshot)
                val pdfDoc = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842

                // Calculate metrics
                val totalMembers = personsSnapshot.size
                val livingCount = personsSnapshot.count { it.isLiving }
                    val deceasedCount = totalMembers - livingCount
                    val parentLinks = persons.count { !it.fatherId.isNullOrBlank() || !it.motherId.isNullOrBlank() }
                    val marriageCount = persons.count { !it.spouseId.isNullOrBlank() } / 2
                    val dateStr = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())

                    val personMap = persons.associateBy { it.id }

                    // Determine pagination for member directory table
                    val rowsPerPage = 24
                    val memberPages = ((persons.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)
                    val totalPages = 1 + memberPages

                    // ─────────────────────────────────────────────────────────
                    // PAGE 1: Executive Heritage Summary & Visual Tree Diagram
                    // ─────────────────────────────────────────────────────────
                    val page1Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page1 = pdfDoc.startPage(page1Info)
                    val canvas1 = page1.canvas

                    // 1. Header Banner
                    val pDarkBg = Paint().apply { color = Color.parseColor("#0C2017"); style = Paint.Style.FILL }
                    val pEmeraldBar = Paint().apply { color = Color.parseColor("#1D9E75"); style = Paint.Style.FILL }
                    canvas1.drawRect(0f, 0f, pageWidth.toFloat(), 85f, pDarkBg)
                    canvas1.drawRect(0f, 85f, pageWidth.toFloat(), 88f, pEmeraldBar)

                    val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
                    }
                    val pSubtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#A3B899"); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                    }
                    val pDate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#94A3B8"); textSize = 9f; textAlign = Paint.Align.RIGHT
                    }

                    canvas1.drawText("KinTrace Genealogical Heritage Report", 36f, 40f, pTitle)
                    canvas1.drawText("Family Tree: $treeName", 36f, 62f, pSubtitle)
                    canvas1.drawText("Exported: $dateStr", 559f, 62f, pDate)

                    // 2. Executive Metric Cards (4 cards)
                    val cardY = 104f
                    val cardH = 50f
                    val cardGap = 8f
                    val cardW = (523f - 3f * cardGap) / 4f

                    val metrics = listOf(
                        "TOTAL MEMBERS" to totalMembers.toString(),
                        "LIVING KIN" to livingCount.toString(),
                        "DECEASED" to deceasedCount.toString(),
                        "PARENT LINKS" to parentLinks.toString()
                    )

                    val pCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
                    val pCardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 1f }
                    val pMetricLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#64748B"); textSize = 8f; typeface = Typeface.DEFAULT_BOLD }
                    val pMetricVal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F6E56"); textSize = 18f; typeface = Typeface.DEFAULT_BOLD }

                    metrics.forEachIndexed { i, (label, value) ->
                        val cx = 36f + i * (cardW + cardGap)
                        val r = RectF(cx, cardY, cx + cardW, cardY + cardH)
                        canvas1.drawRoundRect(r, 6f, 6f, pCardBg)
                        canvas1.drawRoundRect(r, 6f, 6f, pCardStroke)
                        canvas1.drawText(label, cx + 10f, cardY + 18f, pMetricLabel)
                        canvas1.drawText(value, cx + 10f, cardY + 40f, pMetricVal)
                    }

                    // 3. Demographics & Overview Info Box
                    val infoBoxY = 166f
                    val infoBoxH = 52f
                    val infoRect = RectF(36f, infoBoxY, 559f, infoBoxY + infoBoxH)
                    val pInfoBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL }
                    val pInfoStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 1f }
                    canvas1.drawRoundRect(infoRect, 6f, 6f, pInfoBg)
                    canvas1.drawRoundRect(infoRect, 6f, 6f, pInfoStroke)

                    val pInfoText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); textSize = 9.5f }
                    val pInfoBold = Paint(pInfoText).apply { typeface = Typeface.DEFAULT_BOLD }

                    val oldest = persons.filter { it.birthDate.isNotEmpty() }.minByOrNull { it.birthDate }
                    val oldestStr = if (oldest != null) "${oldest.firstName} ${oldest.lastName} (${oldest.birthDate})" else "None recorded"
                    val verifiedDocs = persons.sumOf { it.documents.size }

                    canvas1.drawText("Oldest Recorded Ancestor: ", 48f, infoBoxY + 22f, pInfoBold)
                    canvas1.drawText(oldestStr, 190f, infoBoxY + 22f, pInfoText)

                    canvas1.drawText("Recorded Marriages: ", 48f, infoBoxY + 40f, pInfoBold)
                    canvas1.drawText("$marriageCount unions", 160f, infoBoxY + 40f, pInfoText)

                    canvas1.drawText("Attached Archive Documents: ", 320f, infoBoxY + 22f, pInfoBold)
                    canvas1.drawText("$verifiedDocs documents", 475f, infoBoxY + 22f, pInfoText)

                    canvas1.drawText("Family Tree Identifier: ", 320f, infoBoxY + 40f, pInfoBold)
                    canvas1.drawText(treeId.take(16).ifBlank { "N/A" }, 430f, infoBoxY + 40f, pInfoText)

                    // 4. Section Header: Visual Tree Chart
                    val chartHeaderY = 236f
                    val pSectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F6E56"); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                    }
                    val pSectionSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#64748B"); textSize = 8.5f
                    }
                    canvas1.drawText("GENEALOGICAL TREE DIAGRAM", 36f, chartHeaderY, pSectionTitle)
                    canvas1.drawText("Visual relational tree and generational connections", 36f, chartHeaderY + 12f, pSectionSub)

                    // 5. Embedded Scaled Visual Tree Diagram
                    val treeFrameTop = 256f
                    val treeFrameBottom = 780f
                    val treeFrameW = 523f
                    val treeFrameH = treeFrameBottom - treeFrameTop

                    val pFrameBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#06120D"); style = Paint.Style.FILL }
                    val pFrameBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1D9E75"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
                    val frameRect = RectF(36f, treeFrameTop, 559f, treeFrameBottom)
                    canvas1.drawRoundRect(frameRect, 8f, 8f, pFrameBg)
                    canvas1.drawRoundRect(frameRect, 8f, 8f, pFrameBorder)

                    // Compute scaling preserving aspect ratio
                    val scaleW = (treeFrameW - 8f) / treeBmp.width.toFloat()
                    val scaleH = (treeFrameH - 8f) / treeBmp.height.toFloat()
                    val fitScale = minOf(scaleW, scaleH)
                    val targetW = treeBmp.width * fitScale
                    val targetH = treeBmp.height * fitScale
                    val drawLeft = 36f + 4f + (treeFrameW - 8f - targetW) / 2f
                    val drawTop = treeFrameTop + 4f + (treeFrameH - 8f - targetH) / 2f
                    val destRect = RectF(drawLeft, drawTop, drawLeft + targetW, drawTop + targetH)

                    val pBmpFilter = Paint(Paint.FILTER_BITMAP_FLAG)
                    canvas1.drawBitmap(treeBmp, null, destRect, pBmpFilter)
                    if (!treeBmp.isRecycled) {
                        treeBmp.recycle()
                    }

                    // 6. Page 1 Footer
                    val pDivider = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }
                    canvas1.drawLine(36f, 796f, 559f, 796f, pDivider)
                    val pFooterL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#94A3B8"); textSize = 8f }
                    val pFooterR = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#64748B"); textSize = 8f; textAlign = Paint.Align.RIGHT }
                    canvas1.drawText("KinTrace Certified Family Records  •  Heritage Preservation", 36f, 810f, pFooterL)
                    canvas1.drawText("Page 1 of $totalPages", 559f, 810f, pFooterR)

                    pdfDoc.finishPage(page1)

                    // ─────────────────────────────────────────────────────────
                    // PAGES 2+: Detailed Paginated Member Registry Table
                    // ─────────────────────────────────────────────────────────
                    var personIdx = 0
                    for (pageNum in 2..totalPages) {
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        val page = pdfDoc.startPage(pageInfo)
                        val canvas = page.canvas

                        // Page Header
                        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 55f, pDarkBg)
                        canvas.drawRect(0f, 55f, pageWidth.toFloat(), 57f, pEmeraldBar)
                        val pDirTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
                        val pDirSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#A3B899"); textSize = 9.5f }
                        canvas.drawText("KinTrace Member Directory & Lineage Register", 36f, 30f, pDirTitle)
                        canvas.drawText(treeName, 36f, 46f, pDirSub)
                        canvas.drawText(dateStr, 559f, 46f, pDate)

                        // Table Columns Setup (Total width = 523 pt)
                        val colX = floatArrayOf(36f, 58f, 173f, 215f, 261f, 336f, 428f)
                        val colW = floatArrayOf(22f, 115f, 42f, 46f, 75f, 92f, 131f)
                        val colTitles = arrayOf("#", "Full Name", "Gender", "Status", "Birth / Death", "Spouse", "Parents")

                        // Table Header Row
                        val tableHeaderY = 72f
                        val tableHeaderH = 22f
                        val pThBg = Paint().apply { color = Color.parseColor("#0F6E56"); style = Paint.Style.FILL }
                        val pThText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD }
                        canvas.drawRect(36f, tableHeaderY, 559f, tableHeaderY + tableHeaderH, pThBg)
                        colTitles.forEachIndexed { i, title ->
                            canvas.drawText(title, colX[i] + 4f, tableHeaderY + 15f, pThText)
                        }

                        var rowY = tableHeaderY + tableHeaderH
                        val rowH = 25f

                        val pRowEven = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
                        val pRowOdd = Paint().apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
                        val pRowBorder = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 0.5f }
                        val pCellText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); textSize = 8.5f }
                        val pCellBold = Paint(pCellText).apply { typeface = Typeface.DEFAULT_BOLD }
                        val pLiving = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F766E"); textSize = 8f; typeface = Typeface.DEFAULT_BOLD }
                        val pDeceased = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#64748B"); textSize = 8f }

                        for (r in 0 until rowsPerPage) {
                            if (personIdx >= persons.size) break
                            val p = persons[personIdx]
                            val isEven = (personIdx % 2 == 0)
                            canvas.drawRect(36f, rowY, 559f, rowY + rowH, if (isEven) pRowEven else pRowOdd)
                            canvas.drawLine(36f, rowY + rowH, 559f, rowY + rowH, pRowBorder)

                            // 1. #
                            canvas.drawText((personIdx + 1).toString(), colX[0] + 4f, rowY + 16f, pCellText)

                            // 2. Full Name
                            val fullName = "${p.firstName} ${p.lastName}".trim()
                            val shortName = android.text.TextUtils.ellipsize(fullName, android.text.TextPaint(pCellBold), colW[1] - 8f, android.text.TextUtils.TruncateAt.END).toString()
                            canvas.drawText(shortName, colX[1] + 4f, rowY + 16f, pCellBold)

                            // 3. Gender
                            canvas.drawText(p.gender.take(6), colX[2] + 4f, rowY + 16f, pCellText)

                            // 4. Status
                            val statusText = if (p.isLiving) "Living" else "Deceased"
                            canvas.drawText(statusText, colX[3] + 4f, rowY + 16f, if (p.isLiving) pLiving else pDeceased)

                            // 5. Birth / Death
                            val bStr = p.birthDate.ifEmpty { "—" }
                            val dStr = if (!p.isLiving) "† ${p.deathDate.ifEmpty { "Deceased" }}" else ""
                            val dateLine = if (dStr.isNotEmpty()) "$bStr / $dStr" else bStr
                            val shortDate = android.text.TextUtils.ellipsize(dateLine, android.text.TextPaint(pCellText), colW[4] - 8f, android.text.TextUtils.TruncateAt.END).toString()
                            canvas.drawText(shortDate, colX[4] + 4f, rowY + 16f, pCellText)

                            // 6. Spouse
                            val spouseList = p.allSpouseIds().mapNotNull { personMap[it] }
                            val spouseStr = if (spouseList.isNotEmpty()) {
                                spouseList.joinToString(" · ") { "${it.firstName} ${it.lastName}".trim() }
                            } else "None"
                            val shortSpouse = android.text.TextUtils.ellipsize(spouseStr, android.text.TextPaint(pCellText), colW[5] - 8f, android.text.TextUtils.TruncateAt.END).toString()
                            canvas.drawText(shortSpouse, colX[5] + 4f, rowY + 16f, pCellText)

                            // 7. Parents
                            val father = personMap[p.fatherId]
                            val mother = personMap[p.motherId]
                            val pStr = when {
                                father != null && mother != null -> "${father.firstName} & ${mother.firstName}"
                                father != null -> "F: ${father.firstName} ${father.lastName}"
                                mother != null -> "M: ${mother.firstName} ${mother.lastName}"
                                else -> "—"
                            }
                            val shortParents = android.text.TextUtils.ellipsize(pStr, android.text.TextPaint(pCellText), colW[6] - 8f, android.text.TextUtils.TruncateAt.END).toString()
                            canvas.drawText(shortParents, colX[6] + 4f, rowY + 16f, pCellText)

                            rowY += rowH
                            personIdx++
                        }

                        // Directory Page Footer
                        canvas.drawLine(36f, 796f, 559f, 796f, pDivider)
                        canvas.drawText("KinTrace Certified Family Records  •  Member Registry", 36f, 810f, pFooterL)
                        canvas.drawText("Page $pageNum of $totalPages", 559f, 810f, pFooterR)

                        pdfDoc.finishPage(page)
                    }

                    val fileName = getSafeTreeFileName("Report.pdf")
                    saveFile(fileName, "application/pdf") { out ->
                        pdfDoc.writeTo(out)
                        pdfDoc.close()
                    }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        setExporting(false)
                        toast("✅ Comprehensive PDF Report saved: $fileName")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        setExporting(false)
                        toast("PDF export failed: ${e.message}")
                    }
                }
            }.start()
    }

    private fun exportGedcom() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        val gedcom = buildGedcomContent(persons)
        saveTextFile("FamilyTree.ged", gedcom)
    }

    private fun exportCsv() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        if (isExporting) return
        setExporting(true, "Exporting CSV spreadsheet...")

        Thread {
            try {
                val personMap = persons.associateBy { it.id }
                val sb = StringBuilder()

                // Prepend UTF-8 Byte Order Mark (\uFEFF) for automatic UTF-8 detection in Microsoft Excel & Windows
                sb.append("\uFEFF")

                val headers = listOf(
                    "Record No",
                    "Person ID",
                    "First Name",
                    "Middle Name",
                    "Last Name",
                    "Full Name",
                    "Gender",
                    "Vital Status",
                    "Birth Date",
                    "Birth Place",
                    "Death Date",
                    "Death Place",
                    "Civil Status",
                    "Marriage Date",
                    "Spouse ID",
                    "Spouse Name",
                    "Father ID",
                    "Father Name",
                    "Mother ID",
                    "Mother Name",
                    "Children Count",
                    "Children Names",
                    "Documents Count",
                    "Tree ID"
                )
                sb.appendLine(headers.joinToString(","))

                persons.forEachIndexed { index, p ->
                    val father = personMap[p.fatherId]
                    val mother = personMap[p.motherId]
                    val spouse = personMap[p.spouseId]
                    val children = persons.filter { it.fatherId == p.id || it.motherId == p.id }
                    val childrenNames = children.joinToString("; ") { "${it.firstName} ${it.lastName}".trim() }

                    val row = listOf(
                        (index + 1).toString(),
                        p.id,
                        p.firstName,
                        p.middleName,
                        p.lastName,
                        "${p.firstName} ${if (p.middleName.isNotBlank()) p.middleName + " " else ""}${p.lastName}".trim(),
                        p.gender,
                        if (p.isLiving) "Living" else "Deceased",
                        p.birthDate,
                        p.birthPlace,
                        p.deathDate,
                        p.deathPlace,
                        p.maritalStatus,
                        p.marriageDate,
                        p.allSpouseIds().joinToString("; "),
                        p.allSpouseIds().mapNotNull { personMap[it] }.joinToString("; ") { "${it.firstName} ${it.lastName}".trim() },
                        p.fatherId ?: "",
                        father?.let { "${it.firstName} ${it.lastName}".trim() } ?: "",
                        p.motherId ?: "",
                        mother?.let { "${it.firstName} ${it.lastName}".trim() } ?: "",
                        children.size.toString(),
                        childrenNames,
                        p.documents.size.toString(),
                        treeId
                    ).map { escapeCsv(it) }

                    sb.appendLine(row.joinToString(","))
                }

                val fileName = getSafeTreeFileName("Directory.csv")
                saveFile(fileName, "text/csv") { out ->
                    out.write(sb.toString().toByteArray(Charsets.UTF_8))
                }

                runOnUiThread {
                    setExporting(false)
                    toast("✅ CSV Spreadsheet saved: $fileName")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setExporting(false)
                    toast("CSV export failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun escapeCsv(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    // ── File saving helpers ──────────────────────────────────────────

    private fun saveFile(fileName: String, mimeType: String, write: (OutputStream) -> Unit) {
        try {
            var saved = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { write(it) }
                    saved = true
                }
            }
            if (!saved) {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                val file = java.io.File(dir, fileName)
                java.io.FileOutputStream(file).use { write(it) }
            }
            android.util.Log.i("ExportTreeActivity", "Successfully saved: $fileName ($mimeType)")
        } catch (e: Exception) {
            android.util.Log.e("ExportTreeActivity", "Failed to save $fileName", e)
            runOnUiThread {
                toast("Export failed: ${e.message}")
            }
        }
    }

    private fun saveTextFile(fileName: String, content: String, mimeType: String = "text/plain") {
        saveFile(fileName, mimeType) { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    private fun toast(msg: String) = KinTraceAlert.show(this, msg)

    companion object {
        private data class GedcomFamilyUnit(
            val famId: String,
            val husbId: String?,
            val wifeId: String?,
            val marriageDate: String = "",
            val childIds: MutableList<String> = mutableListOf()
        )

        /**
         * Builds a standard-compliant GEDCOM 5.5.1 document containing individual (`INDI`) records,
         * vital events (`BIRT`, `DEAT`), parent/spouse links (`FAMC`, `FAMS`), and family (`FAM`) blocks.
         */
        fun buildGedcomContent(persons: List<Person>): String {
            val sb = StringBuilder()
            sb.appendLine("0 HEAD")
            sb.appendLine("1 SOUR KinTrace")
            sb.appendLine("2 NAME KinTrace Heritage Lineage System")
            sb.appendLine("1 GEDC")
            sb.appendLine("2 VERS 5.5.1")
            sb.appendLine("2 FORM LINEAGE-LINKED")
            sb.appendLine("1 CHAR UTF-8")

            val personById = persons.associateBy { it.id }
            val indiTagById = persons.mapIndexed { idx, p ->
                val clean = p.id.replace(Regex("[^A-Za-z0-9]"), "").take(10).ifEmpty { (idx + 1).toString() }
                p.id to "I_${idx + 1}_$clean"
            }.toMap()

            // Discover all unique family units (couples via spouseId and parent pairs via children's fatherId/motherId)
            val familyUnits = linkedMapOf<String, GedcomFamilyUnit>()
            var famCounter = 1

            fun getOrCreateFamily(fatherId: String?, motherId: String?, marriageDate: String = ""): GedcomFamilyUnit? {
                val fValid = fatherId?.takeIf { personById.containsKey(it) }
                val mValid = motherId?.takeIf { personById.containsKey(it) }
                if (fValid == null && mValid == null) return null
                val key = "${fValid.orEmpty()}|${mValid.orEmpty()}"
                return familyUnits.getOrPut(key) {
                    GedcomFamilyUnit(
                        famId = "F${famCounter++}",
                        husbId = fValid,
                        wifeId = mValid,
                        marriageDate = marriageDate
                    )
                }
            }

            // 1. Register spousal pairs first (both active spouseId and formerSpouseIds)
            persons.forEach { p ->
                p.allSpouseIds().mapNotNull { personById[it] }.forEach { spouse ->
                    val isP1Female = p.gender.equals("Female", ignoreCase = true)
                    val isP2Female = spouse.gender.equals("Female", ignoreCase = true)
                    val (husb, wife) = when {
                        !isP1Female && isP2Female -> p.id to spouse.id
                        isP1Female && !isP2Female -> spouse.id to p.id
                        p.id < spouse.id -> p.id to spouse.id
                        else -> spouse.id to p.id
                    }
                    val isFormerLink = p.formerSpouseIds.contains(spouse.id) || spouse.formerSpouseIds.contains(p.id)
                    val mDate = if (isFormerLink) {
                        if (!spouse.isLiving) spouse.marriageDate.ifBlank { p.marriageDate } else p.marriageDate.ifBlank { spouse.marriageDate }
                    } else {
                        p.marriageDate.ifBlank { spouse.marriageDate }
                    }
                    getOrCreateFamily(husb, wife, mDate)
                }
            }

            // 2. Register parent-child links into family units
            val childToFamc = mutableMapOf<String, String>()
            persons.forEach { child ->
                val fId = child.fatherId?.takeIf { personById.containsKey(it) }
                val mId = child.motherId?.takeIf { personById.containsKey(it) }
                if (fId != null || mId != null) {
                    val fam = getOrCreateFamily(fId, mId)
                    if (fam != null) {
                        if (!fam.childIds.contains(child.id)) {
                            fam.childIds.add(child.id)
                        }
                        childToFamc[child.id] = fam.famId
                    }
                }
            }

            // Map each person to all FAMS where they are husb or wife
            val personToFams = mutableMapOf<String, MutableList<String>>()
            familyUnits.values.forEach { fam ->
                fam.husbId?.let { personToFams.getOrPut(it) { mutableListOf() }.add(fam.famId) }
                fam.wifeId?.let { personToFams.getOrPut(it) { mutableListOf() }.add(fam.famId) }
            }

            // 3. Emit INDI records
            persons.forEach { p ->
                val indiTag = indiTagById[p.id] ?: "I_${p.id.take(8)}"
                sb.appendLine("0 @$indiTag@ INDI")
                val given = listOf(p.firstName.trim(), p.middleName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                val surname = p.lastName.trim()
                val suffix = p.suffix.trim()
                val nameLine = buildString {
                    append(given)
                    append(" /").append(surname).append("/")
                    if (suffix.isNotEmpty()) append(" ").append(suffix)
                }.trim()
                sb.appendLine("1 NAME $nameLine")
                if (given.isNotEmpty()) sb.appendLine("2 GIVN $given")
                if (surname.isNotEmpty()) sb.appendLine("2 SURN $surname")
                if (suffix.isNotEmpty()) sb.appendLine("2 NSFX $suffix")
                sb.appendLine("1 SEX ${if (p.gender.equals("Female", ignoreCase = true)) "F" else "M"}")

                if (p.birthDate.isNotBlank() || p.birthPlace.isNotBlank()) {
                    sb.appendLine("1 BIRT")
                    if (p.birthDate.isNotBlank()) sb.appendLine("2 DATE ${p.birthDate}")
                    if (p.birthPlace.isNotBlank()) sb.appendLine("2 PLAC ${p.birthPlace}")
                }

                if (!p.isLiving || p.deathDate.isNotBlank() || p.deathPlace.isNotBlank()) {
                    sb.appendLine("1 DEAT Y")
                    if (p.deathDate.isNotBlank()) sb.appendLine("2 DATE ${p.deathDate}")
                    if (p.deathPlace.isNotBlank()) sb.appendLine("2 PLAC ${p.deathPlace}")
                }

                childToFamc[p.id]?.let { famId ->
                    sb.appendLine("1 FAMC @$famId@")
                }
                personToFams[p.id]?.distinct()?.forEach { famId ->
                    sb.appendLine("1 FAMS @$famId@")
                }
            }

            // 4. Emit FAM records
            familyUnits.values.forEach { fam ->
                sb.appendLine("0 @${fam.famId}@ FAM")
                fam.husbId?.let { hId ->
                    indiTagById[hId]?.let { sb.appendLine("1 HUSB @$it@") }
                }
                fam.wifeId?.let { wId ->
                    indiTagById[wId]?.let { sb.appendLine("1 WIFE @$it@") }
                }
                if (fam.marriageDate.isNotBlank()) {
                    sb.appendLine("1 MARR")
                    sb.appendLine("2 DATE ${fam.marriageDate}")
                }
                fam.childIds.forEach { cId ->
                    indiTagById[cId]?.let { sb.appendLine("1 CHIL @$it@") }
                }
            }

            sb.appendLine("0 TRLR")
            return sb.toString()
        }
    }
}
