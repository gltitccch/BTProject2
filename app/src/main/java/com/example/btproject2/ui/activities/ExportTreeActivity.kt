package com.example.btproject2.ui.activities

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
    private var persons: List<Person> = emptyList()

    private lateinit var tvTotalMembers: TextView
    private lateinit var tvParentLinks: TextView
    private lateinit var tvLivingMembers: TextView
    private lateinit var tvExportFormat: TextView
    private lateinit var btnExport: TextView
    private var selectedFormat: String? = null

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

        // Format selection
        cardPdf.setOnClickListener    { selectFormat("PDF",    cardPdf) }
        cardPng.setOnClickListener    { selectFormat("PNG",    cardPng) }
        cardGedcom.setOnClickListener { selectFormat("GEDCOM", cardGedcom) }
        cardCsv.setOnClickListener    { selectFormat("CSV",    cardCsv) }

        btnExport.setOnClickListener {
            when (selectedFormat) {
                "PDF"    -> exportPdf()
                "PNG"    -> exportPng()
                "GEDCOM" -> exportGedcom()
                "CSV"    -> exportCsv()
                else     -> Toast.makeText(this, "Please select a format first", Toast.LENGTH_SHORT).show()
            }
        }

        loadData()
    }

    private fun loadData() {
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { list ->
                persons = list
                val parentLinks = list.count { it.fatherId != null || it.motherId != null }
                val living = list.count { it.isLiving }
                tvTotalMembers.text  = list.size.toString()
                tvParentLinks.text   = parentLinks.toString()
                tvLivingMembers.text = living.toString()
            },
            onFailure = {}
        )
    }

    private fun selectFormat(format: String, card: LinearLayout) {
        selectedFormat = format
        tvExportFormat.text = format

        // Reset all cards
        listOf(cardPdf, cardPng, cardGedcom, cardCsv).forEach {
            it.setBackgroundResource(R.drawable.card_bg)
        }
        // Highlight selected
        card.setBackgroundResource(R.drawable.card_bg_selected)

        // Enable export button
        btnExport.setBackgroundResource(R.drawable.btn_primary_bg)
        btnExport.setTextColor(resources.getColor(R.color.white, null))
        btnExport.text = "Export as $format"
    }

    // ── Export implementations ────────────────────────────────────────

    private fun exportPdf() {
        if (persons.isEmpty()) { toast("No members to export"); return }

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Family Tree Report", 40f, 60f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Total members: ${persons.size}", 40f, 90f, paint)

        var y = 130f
        paint.textSize = 11f
        persons.forEach { p ->
            if (y > 800f) return@forEach // simple overflow guard
            canvas.drawText(
                "• ${p.firstName} ${p.lastName} | ${p.gender} | ${p.birthDate.ifEmpty { "No DOB" }}",
                40f, y, paint
            )
            y += 18f
        }

        pdfDoc.finishPage(page)
        saveFile("FamilyTree.pdf", "application/pdf") { out ->
            pdfDoc.writeTo(out)
            pdfDoc.close()
        }
    }

    private fun exportPng() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        val width = 800
        val rowH = 28
        val height = 80 + persons.size * rowH + 40
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(android.graphics.Color.parseColor("#FAFAF7"))
        paint.color = android.graphics.Color.parseColor("#1D9E75")
        paint.textSize = 28f; paint.isFakeBoldText = true
        canvas.drawText("Family Tree", 40f, 50f, paint)

        paint.color = android.graphics.Color.parseColor("#2C2A24")
        paint.textSize = 16f; paint.isFakeBoldText = false
        var y = 90f
        persons.forEach { p ->
            canvas.drawText(
                "${p.firstName} ${p.lastName}  ·  ${p.gender}  ·  ${p.birthDate.ifEmpty { "No DOB" }}",
                40f, y, paint
            )
            y += rowH
        }

        saveFile("FamilyTree.png", "image/png") { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun exportGedcom() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        val sb = StringBuilder()
        sb.appendLine("0 HEAD")
        sb.appendLine("1 GEDC")
        sb.appendLine("2 VERS 5.5.1")
        sb.appendLine("1 CHAR UTF-8")
        persons.forEach { p ->
            sb.appendLine("0 @I${p.id.take(8)}@ INDI")
            sb.appendLine("1 NAME ${p.firstName} /${p.lastName}/")
            sb.appendLine("1 SEX ${if (p.gender == "Female") "F" else "M"}")
            if (p.birthDate.isNotEmpty()) {
                sb.appendLine("1 BIRT")
                sb.appendLine("2 DATE ${p.birthDate}")
            }
        }
        sb.appendLine("0 TRLR")
        saveTextFile("FamilyTree.ged", sb.toString())
    }

    private fun exportCsv() {
        if (persons.isEmpty()) { toast("No members to export"); return }
        val sb = StringBuilder()
        sb.appendLine("ID,FirstName,LastName,Gender,BirthDate,FatherId,MotherId,SpouseId,IsLiving,MaritalStatus")
        persons.forEach { p ->
            sb.appendLine("${p.id},${p.firstName},${p.lastName},${p.gender},${p.birthDate}," +
                    "${p.fatherId ?: ""},${p.motherId ?: ""},${p.spouseId ?: ""},${p.isLiving},${p.maritalStatus}")
        }
        saveTextFile("FamilyTree.csv", sb.toString())
    }

    // ── File saving helpers ──────────────────────────────────────────

    private fun saveFile(fileName: String, mimeType: String, write: (OutputStream) -> Unit) {
        try {
            val out: OutputStream?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                out = uri?.let { contentResolver.openOutputStream(it) }
            } else {
                @Suppress("DEPRECATION")
                val file = java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                out = java.io.FileOutputStream(file)
            }
            out?.use { write(it) }
            toast("$fileName saved to Downloads")
        } catch (e: IOException) {
            toast("Export failed: ${e.message}")
        }
    }

    private fun saveTextFile(fileName: String, content: String) {
        saveFile(fileName, "text/plain") { out ->
            out.write(content.toByteArray())
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
