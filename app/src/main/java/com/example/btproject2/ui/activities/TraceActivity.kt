package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.models.TraceResult

class TraceActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val bloodlineTracer = BloodlineTracer()
    private var familyMembers = listOf<Person>()
    private val treeId = "default_tree"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val spinnerA = findViewById<Spinner>(R.id.spinnerPersonA)
        val spinnerB = findViewById<Spinner>(R.id.spinnerPersonB)
        val btnTrace = findViewById<Button>(R.id.btnTrace)
        val resultContainer = findViewById<LinearLayout>(R.id.resultContainer)

        btnBack.setOnClickListener { finish() }
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

            firestoreHelper.loadEntireTree(treeId,
                onSuccess = { personMap ->
                    val result = bloodlineTracer.trace(personAId, personBId, personMap)
                    displayResult(result)
                },
                onFailure = { error ->
                    showMessage("Error", "Failed to load family: ${error.message}")
                }
            )
        }

        // Trace Again button
        findViewById<Button>(R.id.btnTraceAgain).setOnClickListener {
            resultContainer.visibility = View.GONE
        }

        // Learn More button
        findViewById<Button>(R.id.btnLearnMore).setOnClickListener {
            startActivity(Intent(this, RelationshipTypesActivity::class.java))
        }
    }

    private fun displayResult(result: TraceResult) {
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

        if (result.isRelated) {
            resultCard.setBackgroundResource(R.drawable.result_card_bg)
            tvResultLabel.text = "RELATIONSHIP FOUND"
            tvResultLabel.setTextColor(resources.getColor(R.color.primary_dark, null))
            tvType.text = result.relationshipType
            tvType.setTextColor(resources.getColor(R.color.primary_dark, null))
            tvExplanation.text = result.explanation

            if (result.commonAncestor != null) {
                ancestorContainer.visibility = View.VISIBLE
                tvAncestor.text = "${result.commonAncestor.firstName} ${result.commonAncestor.lastName}"
            } else {
                ancestorContainer.visibility = View.GONE
            }

            detailsCard.visibility = View.VISIBLE
            tvCategory.text = "Type: ${result.relationshipCategory} (via ${if (result.relationshipCategory == "Lineal") "direct path" else "shared ancestor"})"
            tvDegree.text = "Degree: ${result.degreeOfConsanguinity}${getOrdinalSuffix(result.degreeOfConsanguinity)} Degree Consanguinity"
            tvSteps.text = "Steps from ancestor: ${result.distanceA} and ${result.distanceB}"
        } else {
            resultCard.setBackgroundResource(R.drawable.result_card_danger_bg)
            tvResultLabel.text = "NO BLOOD RELATION DETECTED"
            tvResultLabel.setTextColor(resources.getColor(R.color.danger, null))
            tvType.text = "Not Related"
            tvType.setTextColor(resources.getColor(R.color.danger, null))
            tvExplanation.text = result.explanation
            ancestorContainer.visibility = View.GONE
            detailsCard.visibility = View.GONE
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
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                familyMembers = persons
                val names = persons.map { "${it.firstName} ${it.lastName}" }
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                spinnerA.adapter = adapter
                spinnerB.adapter = adapter
            },
            onFailure = {
                showMessage("Error", "Failed to load family members.")
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
}