package com.example.btproject2.ui.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.ui.adapters.MemberAdapter

class FamilyRecordsActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val treeId = "default_tree"
    private var allMembers = listOf<Person>()
    private lateinit var adapter: MemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_records)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val rvMembers = findViewById<RecyclerView>(R.id.rvMembers)

        btnBack.setOnClickListener { finish() }

        // Setup RecyclerView
        adapter = MemberAdapter(emptyList()) { person ->
            showMemberDetail(person)
        }
        rvMembers.layoutManager = LinearLayoutManager(this)
        rvMembers.adapter = adapter

        // Load members
        loadMembers(tvMemberCount)

        // Search filter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase().trim()
                if (query.isEmpty()) {
                    adapter.updateList(allMembers)
                    tvMemberCount.text = "${allMembers.size} members"
                } else {
                    val filtered = allMembers.filter {
                        "${it.firstName} ${it.lastName}".lowercase().contains(query)
                    }
                    adapter.updateList(filtered)
                    tvMemberCount.text = "${filtered.size} of ${allMembers.size} members"
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        loadMembers(tvMemberCount)
    }

    private fun loadMembers(tvMemberCount: TextView) {
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                allMembers = persons.sortedBy { "${it.firstName} ${it.lastName}" }
                adapter.updateList(allMembers)
                tvMemberCount.text = "${allMembers.size} members"
            },
            onFailure = {
                showMessage("Error", "Failed to load family members.")
            }
        )
    }

    private fun showMemberDetail(person: Person) {
        val details = buildString {
            appendLine("Name: ${person.firstName} ${person.lastName}")
            appendLine("Gender: ${person.gender}")
            if (person.birthDate.isNotEmpty()) appendLine("Birthdate: ${person.birthDate}")
            appendLine()
            appendLine("Mother ID: ${person.motherId ?: "Not set"}")
            appendLine("Father ID: ${person.fatherId ?: "Not set"}")
            appendLine("Spouse ID: ${person.spouseId ?: "Not set"}")
        }

        AlertDialog.Builder(this)
            .setTitle("${person.firstName} ${person.lastName}")
            .setMessage(details)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}