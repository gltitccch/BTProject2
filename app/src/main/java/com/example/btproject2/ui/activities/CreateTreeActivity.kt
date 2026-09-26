package com.example.btproject2.ui.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import com.example.btproject2.utils.setDarkAdapter
import com.example.btproject2.models.UserProfile
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.TreePreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateTreeActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private var selectedDate = ""

    private lateinit var etTreeName: EditText
    private lateinit var etCreatorName: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var etBirthDate: EditText
    private lateinit var btnCreateTree: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_tree)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        etTreeName = findViewById(R.id.etTreeName)
        etCreatorName = findViewById(R.id.etCreatorName)
        spinnerGender = findViewById(R.id.spinnerGender)
        etBirthDate = findViewById(R.id.etBirthDate)
        btnCreateTree = findViewById(R.id.btnCreateTree)

        btnBack.setOnClickListener { finish() }

        spinnerGender.setDarkAdapter(this, listOf("Male", "Female"))

        // Pre-fill name from auth profile if available
        val currentName = authHelper.getCurrentUser()?.displayName
        if (!currentName.isNullOrBlank()) {
            etCreatorName.setText(currentName)
        }

        etBirthDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val c = Calendar.getInstance().also { it.set(y, m, d) }
                    selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                    etBirthDate.setText(
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(c.time)
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).also {
                it.datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        btnCreateTree.setOnClickListener {
            handleCreateTree()
        }
    }

    private fun handleCreateTree() {
        val treeName = etTreeName.text.toString().trim()
        val creatorName = etCreatorName.text.toString().trim()
        val gender = spinnerGender.selectedItem.toString()

        if (treeName.isEmpty()) {
            Toast.makeText(this, "Please enter a tree name", Toast.LENGTH_SHORT).show()
            return
        }

        if (creatorName.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = authHelper.getCurrentUserId().orEmpty()
        if (userId.isEmpty()) {
            Toast.makeText(this, "Please sign in to create a family tree", Toast.LENGTH_SHORT).show()
            return
        }
        val inviteCode = generateInviteCode()

        btnCreateTree.isEnabled = false
        btnCreateTree.text = "Creating Tree..."

        val newTree = FamilyTree(
            name = treeName,
            ownerId = userId,
            ownerName = creatorName,
            inviteCode = inviteCode,
            isPrivate = false
        )

        firestoreHelper.createTree(newTree,
            onSuccess = { createdTree ->
                // Immediately establish active tree preferences and isolate cache
                TreePreferences.setActiveTree(this, createdTree.id, createdTree.name)
                FirestoreHelper.setCachedPersons(emptyList())

                // Update user profile currentTreeId immediately
                val profile = UserProfile(
                    id = userId,
                    displayName = creatorName,
                    email = authHelper.getCurrentUser()?.email ?: "",
                    currentTreeId = createdTree.id
                )
                firestoreHelper.saveUserProfile(profile, onSuccess = {}, onFailure = {})

                // Split name into first and last
                val parts = creatorName.split(" ", limit = 2)
                val firstName = parts[0]
                val lastName = if (parts.size > 1) parts[1] else ""

                // Add creator as first person in the tree
                val selfPerson = Person(
                    firstName = firstName,
                    lastName = lastName,
                    gender = gender,
                    birthDate = selectedDate,
                    treeId = createdTree.id,
                    createdBy = userId
                )

                firestoreHelper.addPerson(selfPerson,
                    onSuccess = {
                        CentralTreeSynchronizer.getInstance().startRealtimeListener(createdTree.id)
                        Toast.makeText(this, "Family tree created! Invite code: $inviteCode", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onFailure = {
                        CentralTreeSynchronizer.getInstance().startRealtimeListener(createdTree.id)
                        Toast.makeText(this, "Tree created, but failed to add first member", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            },
            onFailure = { error ->
                btnCreateTree.isEnabled = true
                btnCreateTree.text = "Create tree"
                AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(error.message ?: "Failed to create family tree.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}

