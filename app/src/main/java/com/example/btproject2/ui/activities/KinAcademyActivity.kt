package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.education.EducationalContentRepository
import com.example.btproject2.education.EducationalContentRepository.KinAcademyModule
import com.example.btproject2.ui.adapters.KamagAnakAdapter
import com.example.btproject2.ui.adapters.KinAcademyModuleAdapter

/**
 * KinAcademyActivity: Dedicated interactive knowledge hub and masterclass screen
 * under Proposal 25.
 *
 * Provides:
 * - 10 comprehensive curriculum modules.
 * - Searchable Philippine Kamag-anak Lexicon dictionary.
 * - Real-time keyword search across lessons, legal rules, and terms.
 * - Deep-link action shortcuts into all primary app tools.
 */
class KinAcademyActivity : AppCompatActivity() {

    private lateinit var rvModules: RecyclerView
    private lateinit var rvGlossary: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: TextView

    private lateinit var moduleAdapter: KinAcademyModuleAdapter
    private lateinit var glossaryAdapter: KamagAnakAdapter

    private var activeFilterCategory: String = "ALL"
    private var allModules: List<KinAcademyModule> = emptyList()

    // Filter Chips
    private lateinit var chipAll: TextView
    private lateinit var chipFoundations: TextView
    private lateinit var chipCanvas: TextView
    private lateinit var chipBloodline: TextView
    private lateinit var chipArchival: TextView
    private lateinit var chipLexicon: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kin_academy)

        val targetModuleId = intent.getIntExtra("TARGET_MODULE_ID", -1)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        allModules = EducationalContentRepository.getAllModules()

        rvModules = findViewById(R.id.rvAcademyModules)
        rvGlossary = findViewById(R.id.rvKamagAnakGlossary)
        layoutEmpty = findViewById(R.id.layoutEmptyResults)
        etSearch = findViewById(R.id.etSearchAcademy)
        btnClearSearch = findViewById(R.id.btnClearSearch)

        chipAll = findViewById(R.id.chipFilterAll)
        chipFoundations = findViewById(R.id.chipFilterFoundations)
        chipCanvas = findViewById(R.id.chipFilterCanvas)
        chipBloodline = findViewById(R.id.chipFilterBloodline)
        chipArchival = findViewById(R.id.chipFilterArchival)
        chipLexicon = findViewById(R.id.chipFilterLexicon)

        setupRecyclerViews(targetModuleId)
        setupSearch()
        setupCategoryChips()

        if (targetModuleId > 0 && targetModuleId <= allModules.size) {
            rvModules.post {
                rvModules.smoothScrollToPosition(targetModuleId - 1)
            }
        }
    }

    private fun setupRecyclerViews(targetModuleId: Int) {
        rvModules.layoutManager = LinearLayoutManager(this)
        moduleAdapter = KinAcademyModuleAdapter(
            modules = allModules,
            initiallyExpandedModuleId = targetModuleId,
            onActionClick = { targetActivity ->
                launchActionTarget(targetActivity)
            }
        )
        rvModules.adapter = moduleAdapter

        rvGlossary.layoutManager = LinearLayoutManager(this)
        glossaryAdapter = KamagAnakAdapter(
            terms = EducationalContentRepository.getKamagAnakDictionary()
        )
        rvGlossary.adapter = glossaryAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilterAndSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.text?.clear()
        }
    }

    private fun setupCategoryChips() {
        val chips = listOf(chipAll, chipFoundations, chipCanvas, chipBloodline, chipArchival, chipLexicon)

        chipAll.setOnClickListener { selectCategory("ALL", chips, chipAll) }
        chipFoundations.setOnClickListener { selectCategory("FOUNDATIONS", chips, chipFoundations) }
        chipCanvas.setOnClickListener { selectCategory("CANVAS", chips, chipCanvas) }
        chipBloodline.setOnClickListener { selectCategory("BLOODLINE", chips, chipBloodline) }
        chipArchival.setOnClickListener { selectCategory("ARCHIVAL", chips, chipArchival) }
        chipLexicon.setOnClickListener { selectCategory("LEXICON", chips, chipLexicon) }
    }

    private fun selectCategory(category: String, allChips: List<TextView>, selectedChip: TextView) {
        activeFilterCategory = category

        val activeBg = ContextCompat.getDrawable(this, R.drawable.btn_gold_primary)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.badge_pill_dark)
        val activeTextColor = ContextCompat.getColor(this, R.color.forest_bg)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.text_primary)

        for (chip in allChips) {
            if (chip == selectedChip) {
                chip.background = activeBg
                chip.setTextColor(activeTextColor)
            } else {
                chip.background = inactiveBg
                chip.setTextColor(inactiveTextColor)
            }
        }

        applyFilterAndSearch(etSearch.text?.toString()?.trim() ?: "")
    }

    private fun applyFilterAndSearch(query: String) {
        if (activeFilterCategory == "LEXICON") {
            rvModules.visibility = View.GONE
            rvGlossary.visibility = View.VISIBLE

            val filteredGlossary = if (query.isEmpty()) {
                EducationalContentRepository.getKamagAnakDictionary()
            } else {
                EducationalContentRepository.searchGlossary(query)
            }
            glossaryAdapter.updateTerms(filteredGlossary)

            layoutEmpty.visibility = if (filteredGlossary.isEmpty()) View.VISIBLE else View.GONE
        } else {
            rvGlossary.visibility = View.GONE
            rvModules.visibility = View.VISIBLE

            val baseList = when (activeFilterCategory) {
                "FOUNDATIONS" -> allModules.filter { it.category.equals("Foundations", ignoreCase = true) }
                "CANVAS" -> allModules.filter { it.category.contains("Visual", ignoreCase = true) || it.title.contains("Canvas", ignoreCase = true) }
                "BLOODLINE" -> allModules.filter { it.category.contains("Kinship", ignoreCase = true) || it.title.contains("Bloodline", ignoreCase = true) }
                "ARCHIVAL" -> allModules.filter { it.category.contains("Archival", ignoreCase = true) || it.category.contains("Export", ignoreCase = true) }
                else -> allModules
            }

            val filteredModules = if (query.isEmpty()) {
                baseList
            } else {
                EducationalContentRepository.searchModules(query).filter { baseList.contains(it) }
            }
            moduleAdapter.updateModules(filteredModules)

            layoutEmpty.visibility = if (filteredModules.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun launchActionTarget(target: String) {
        val intent = when (target) {
            "CreateTreeActivity" -> Intent(this, CreateTreeActivity::class.java)
            "FamilyTreeActivity" -> Intent(this, FamilyTreeActivity::class.java)
            "TraceActivity" -> Intent(this, TraceActivity::class.java)
            "FamilyRecordsActivity" -> Intent(this, FamilyRecordsActivity::class.java)
            "MergeBranchesActivity" -> Intent(this, MergeBranchesActivity::class.java)
            "PrivacyControlsActivity" -> Intent(this, PrivacyControlsActivity::class.java)
            "TreeAuditActivity" -> Intent(this, TreeAuditActivity::class.java)
            "ExportTreeActivity" -> Intent(this, ExportTreeActivity::class.java)
            "InsightsActivity" -> Intent(this, InsightsActivity::class.java)
            else -> null
        }

        if (intent != null) {
            startActivity(intent)
        }
    }
}
