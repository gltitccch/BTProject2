package com.example.btproject2.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.btproject2.models.TraceResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    /**
     * Generates an official, printable A4 PDF Genealogical & Legal Relationship Report.
     * Returns a Pair of the generated File and its shareable FileProvider Content Uri.
     */
    fun generateReport(context: Context, result: TraceResult, treeName: String = "Family Tree"): Pair<File, Uri> {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842 pt)
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val pageWidth = 595f
        val pageHeight = 842f
        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        // ── Paints ────────────────────────────────────────────────
        val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1B12")
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pSubHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
        }
        val pGoldLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4A359")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        val pSectionTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9A6F29")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
        }
        val pCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val pCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val pFooter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }

        var curY = margin + 10f

        // 1. Header Banner
        canvas.drawText("KINTRACE GENEALOGICAL & LEGAL RELATIONSHIP REPORT", margin, curY, pHeader)
        curY += 15f
        canvas.drawText("STI College Marikina — Bachelor of Science in Information Technology", margin, curY, pSubHeader)
        curY += 12f
        val timestamp = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Tree: $treeName   •   Date Generated: $timestamp", margin, curY, pSubHeader)
        curY += 14f

        // Gold divider
        canvas.drawLine(margin, curY, pageWidth - margin, curY, pGoldLine)
        curY += 22f

        // 2. Subject Summary Box
        val subjectsRect = RectF(margin, curY, pageWidth - margin, curY + 68f)
        canvas.drawRoundRect(subjectsRect, 8f, 8f, pCardBg)
        canvas.drawRoundRect(subjectsRect, 8f, 8f, pCardBorder)

        val halfCol = contentWidth / 2f
        val col1X = margin + 14f
        val col2X = margin + halfCol + 10f

        // Person A
        canvas.drawText("PERSON A (REFERENCE):", col1X, curY + 18f, pLabel)
        val nameA = "${result.personA.firstName} ${result.personA.middleName} ${result.personA.lastName} ${result.personA.suffix}".trim()
        canvas.drawText(nameA, col1X, curY + 34f, pHeader.apply { textSize = 12f })
        val detailsA = "${result.personA.gender}  •  b. ${result.personA.birthDate.ifEmpty { "Unknown" }}  •  ${if (result.personA.isLiving) "Living" else "Deceased"}"
        canvas.drawText(detailsA, col1X, curY + 50f, pSubHeader)

        // Person B
        canvas.drawText("PERSON B (TARGET RELATIVE):", col2X, curY + 18f, pLabel)
        val nameB = "${result.personB.firstName} ${result.personB.middleName} ${result.personB.lastName} ${result.personB.suffix}".trim()
        canvas.drawText(nameB, col2X, curY + 34f, pHeader.apply { textSize = 12f })
        val detailsB = "${result.personB.gender}  •  b. ${result.personB.birthDate.ifEmpty { "Unknown" }}  •  ${if (result.personB.isLiving) "Living" else "Deceased"}"
        canvas.drawText(detailsB, col2X, curY + 50f, pSubHeader)

        curY += 86f

        // 3. Official Consanguinity & Legal Verdict Hero Box
        val verdictBgColor = if (result.isMarriageProhibited) Color.parseColor("#FEF2F2") else Color.parseColor("#F0FDF4")
        val verdictBorderColor = if (result.isMarriageProhibited) Color.parseColor("#FCA5A5") else Color.parseColor("#86EFAC")
        val pVerdictBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = verdictBgColor; style = Paint.Style.FILL }
        val pVerdictBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = verdictBorderColor; style = Paint.Style.STROKE; strokeWidth = 1.5f }

        val verdictRect = RectF(margin, curY, pageWidth - margin, curY + 88f)
        canvas.drawRoundRect(verdictRect, 10f, 10f, pVerdictBg)
        canvas.drawRoundRect(verdictRect, 10f, 10f, pVerdictBorder)

        // Title line
        canvas.drawText("CONSANGUINITY CLASSIFICATION & LEGAL ASSESSMENT", margin + 14f, curY + 20f, pSectionTitle)

        // Degree and Relationship Type
        val degreeLabel = if (result.degreeOfConsanguinity > 0) {
            "${result.relationshipType} (${result.degreeOfConsanguinity}${getOrdinal(result.degreeOfConsanguinity)} Civil Degree)"
        } else {
            result.relationshipType
        }
        val pDegreeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0A1B12")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(degreeLabel, margin + 14f, curY + 42f, pDegreeText)

        // Line category
        val categoryText = "Line of Consanguinity: ${result.relationshipCategory} Line (Roman Civil Method — Art. 963 NCC)"
        canvas.drawText(categoryText, margin + 14f, curY + 58f, pSubHeader)

        // Status Badge
        val statusText = if (result.isMarriageProhibited) "MARRIAGE PROHIBITED" else "NO LEGAL IMPEDIMENT"
        val statusColor = if (result.isMarriageProhibited) Color.parseColor("#DC2626") else Color.parseColor("#16A34A")
        val pStatus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = statusColor
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(statusText, pageWidth - margin - 16f, curY + 38f, pStatus)

        val pArticle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 9f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(result.legalArticle, pageWidth - margin - 16f, curY + 54f, pArticle)
        canvas.drawText(result.legalStatus, pageWidth - margin - 16f, curY + 68f, pArticle)

        curY += 106f

        // 4. Closest Shared Biological Ancestor (MRCA)
        val mrcaRect = RectF(margin, curY, pageWidth - margin, curY + 58f)
        canvas.drawRoundRect(mrcaRect, 8f, 8f, pCardBg)
        canvas.drawRoundRect(mrcaRect, 8f, 8f, pCardBorder)

        canvas.drawText("CLOSEST COMMON ANCESTOR (MRCA):", margin + 14f, curY + 18f, pSectionTitle)
        if (result.commonAncestor != null) {
            val caName = "${result.commonAncestor.firstName} ${result.commonAncestor.lastName}"
            val caInfo = "★ $caName  •  Generational Distance: ${result.distanceA} generation(s) from Person A, ${result.distanceB} generation(s) from Person B"
            canvas.drawText(caInfo, margin + 14f, curY + 36f, pValue.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            canvas.drawText("Total Generational Steps: ${result.distanceA + result.distanceB} step(s) (Degree = Generation A + Generation B)", margin + 14f, curY + 50f, pSubHeader)
        } else {
            canvas.drawText("No common ancestor recorded in this lineage branch.", margin + 14f, curY + 36f, pValue)
        }

        curY += 76f

        // 5. Natural Language Narrative & Step-by-Step Path
        canvas.drawText("STEP-BY-STEP RELATIONSHIP EXPLANATION", margin, curY, pSectionTitle)
        curY += 12f

        val textPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
            isAntiAlias = true
        }

        val narrative = result.explanation
        val staticLayout = StaticLayout.Builder.obtain(narrative, 0, narrative.length, textPaint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1f)
            .build()

        canvas.save()
        canvas.translate(margin, curY)
        staticLayout.draw(canvas)
        canvas.restore()

        curY += staticLayout.height + 20f

        // 6. Philippine Law Statutory Basis Box
        val lawRect = RectF(margin, curY, pageWidth - margin, curY + 70f)
        canvas.drawRoundRect(lawRect, 8f, 8f, pCardBg)
        canvas.drawRoundRect(lawRect, 8f, 8f, pCardBorder)

        canvas.drawText("PHILIPPINE STATUTORY BASIS & ARTICLE CITATION", margin + 14f, curY + 18f, pSectionTitle)
        val lawDetails = "${result.legalArticle}: ${result.legalDescription}"
        val lawLayout = StaticLayout.Builder.obtain(lawDetails, 0, lawDetails.length, textPaint, (contentWidth - 28f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()

        canvas.save()
        canvas.translate(margin + 14f, curY + 26f)
        lawLayout.draw(canvas)
        canvas.restore()

        curY += 88f

        // 7. Visual Path Ladder Summary
        if (result.pathFromA.isNotEmpty() || result.pathFromB.isNotEmpty()) {
            canvas.drawText("GENEALOGICAL DESCENT PATH", margin, curY, pSectionTitle)
            curY += 14f

            val pathAString = result.pathFromA.joinToString(" → ") { it.firstName }
            val pathBString = result.pathFromB.joinToString(" → ") { it.firstName }
            val mrcaName = result.commonAncestor?.firstName ?: "Root"

            val ladderStr = "Person A Path: $pathAString\nMRCA Junction: ★ $mrcaName\nPerson B Path: $pathBString"
            val ladderLayout = StaticLayout.Builder.obtain(ladderStr, 0, ladderStr.length, textPaint, contentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()

            canvas.save()
            canvas.translate(margin, curY)
            ladderLayout.draw(canvas)
            canvas.restore()

            curY += ladderLayout.height + 16f
        }

        // 8. Footer & Verification Watermark
        val footerY = pageHeight - margin - 15f
        canvas.drawLine(margin, footerY - 14f, pageWidth - margin, footerY - 14f, pCardBorder)
        canvas.drawText("KinTrace: Mobile-Based Genealogical Bloodline Tracing System  •  STI College Marikina", pageWidth / 2f, footerY, pFooter)
        canvas.drawText("Proponents: Renzy A. Bugarin & Ackerley Gabriel C. Doromal  •  Adviser: Dr. Frederic Yulo", pageWidth / 2f, footerY + 11f, pFooter)
        canvas.drawText("Computed via Breadth-First Search (BFS) and Roman Civil Method (Article 963, Civil Code of the Philippines)", pageWidth / 2f, footerY + 22f, pFooter)

        pdfDocument.finishPage(page)

        // ── Save to File ──────────────────────────────────────────
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val timeStampSlug = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFile = File(reportsDir, "KinTrace_Report_${timeStampSlug}.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        return Pair(pdfFile, contentUri)
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

