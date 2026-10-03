package com.example.btproject2.ui.activities

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import com.example.btproject2.models.UserProfile
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.OnboardingPreferences
import com.example.btproject2.utils.TreePreferences
import com.example.btproject2.utils.setDarkAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateTreeActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private var selectedDate = ""

    private lateinit var etTreeName: EditText
    private lateinit var etCreatorName: EditText
    private lateinit var cardPatriarch: LinearLayout
    private lateinit var cardMatriarch: LinearLayout
    private lateinit var layoutFounderPills: LinearLayout
    private lateinit var layoutYouthRole: LinearLayout
    private lateinit var tvPatriarchText: TextView
    private lateinit var tvMatriarchText: TextView
    private lateinit var spinnerGender: Spinner
    private lateinit var etBirthDate: EditText
    private lateinit var btnCreateTree: Button

    private var selectedFounderRole: String = "Patriarch"
    private var isUpdatingRoleProgrammatically: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_tree)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        etTreeName = findViewById(R.id.etTreeName)
        etCreatorName = findViewById(R.id.etCreatorName)
        cardPatriarch = findViewById(R.id.cardPatriarch)
        cardMatriarch = findViewById(R.id.cardMatriarch)
        layoutFounderPills = findViewById(R.id.layoutFounderPills)
        layoutYouthRole = findViewById(R.id.layoutYouthRole)
        tvPatriarchText = findViewById(R.id.tvPatriarchText)
        tvMatriarchText = findViewById(R.id.tvMatriarchText)
        spinnerGender = findViewById(R.id.spinnerGender)
        etBirthDate = findViewById(R.id.etBirthDate)
        btnCreateTree = findViewById(R.id.btnCreateTree)

        btnBack.setOnClickListener { finish() }

        spinnerGender.setDarkAdapter(this, listOf("Male", "Female"))

        // Set default founder role to Patriarch and sync UI
        updateFounderRoleUI("Patriarch", syncSpinner = false)

        // Patriarch card click -> sync role & switch spinner to Male
        cardPatriarch.setOnClickListener {
            updateFounderRoleUI("Patriarch", syncSpinner = true)
        }

        // Matriarch card click -> sync role & switch spinner to Female
        cardMatriarch.setOnClickListener {
            updateFounderRoleUI("Matriarch", syncSpinner = true)
        }

        // Gender spinner item selection -> sync role accordingly (if in adult mode)
        spinnerGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingRoleProgrammatically) return
                if (layoutYouthRole.visibility == View.VISIBLE) {
                    // Youth mode remains Member regardless of gender
                    selectedFounderRole = "Member"
                    return
                }
                val targetRole = if (position == 0) "Patriarch" else "Matriarch"
                updateFounderRoleUI(targetRole, syncSpinner = false)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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
                    etBirthDate.error = null
                    checkAndApplyAgeAdaptiveRole(c)
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

    private fun checkAndApplyAgeAdaptiveRole(birthCal: Calendar) {
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
            age--
        }

        if (age < 18) {
            // Under 18: Adapt role to Family Member (Youth / Descendant)
            selectedFounderRole = "Member"
            layoutFounderPills.visibility = View.GONE
            layoutYouthRole.visibility = View.VISIBLE
        } else {
            // 18 and older: Enable adult founder roles (Patriarch / Matriarch)
            layoutYouthRole.visibility = View.GONE
            layoutFounderPills.visibility = View.VISIBLE
            val targetRole = if (spinnerGender.selectedItemPosition == 1) "Matriarch" else "Patriarch"
            updateFounderRoleUI(targetRole, syncSpinner = false)
        }
    }

    private fun updateFounderRoleUI(role: String, syncSpinner: Boolean) {
        selectedFounderRole = role
        if (role == "Patriarch") {
            cardPatriarch.setBackgroundResource(R.drawable.badge_pill_gold)
            tvPatriarchText.setTextColor(Color.parseColor("#0A1B12"))

            cardMatriarch.setBackgroundResource(R.drawable.badge_pill_dark)
            tvMatriarchText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            if (syncSpinner && spinnerGender.selectedItemPosition != 0) {
                isUpdatingRoleProgrammatically = true
                spinnerGender.setSelection(0)
                isUpdatingRoleProgrammatically = false
            }
        } else {
            cardMatriarch.setBackgroundResource(R.drawable.badge_pill_gold)
            tvMatriarchText.setTextColor(Color.parseColor("#0A1B12"))

            cardPatriarch.setBackgroundResource(R.drawable.badge_pill_dark)
            tvPatriarchText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

            if (syncSpinner && spinnerGender.selectedItemPosition != 1) {
                isUpdatingRoleProgrammatically = true
                spinnerGender.setSelection(1)
                isUpdatingRoleProgrammatically = false
            }
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

        // Validate 18+ eligibility for Patriarch and Matriarch
        if (selectedFounderRole.equals("Patriarch", ignoreCase = true) || selectedFounderRole.equals("Matriarch", ignoreCase = true)) {
            if (selectedDate.isBlank()) {
                etBirthDate.error = "Birth date is required for $selectedFounderRole (must be 18+)"
                Toast.makeText(this, "Please select your birth date to verify 18+ eligibility for $selectedFounderRole.", Toast.LENGTH_LONG).show()
                return
            }
            val age = com.example.btproject2.engine.FamilyLinkValidator.calculateAgeYears(selectedDate)
            if (age == null || age < 18) {
                Toast.makeText(this, "A member must be at least 18 years old to be designated as $selectedFounderRole (current age: ${age ?: 0}).", Toast.LENGTH_LONG).show()
                return
            }
        }

        val inviteCode = generateInviteCode()

        btnCreateTree.isEnabled = false
        btnCreateTree.text = "Creating Tree..."

        val newTree = FamilyTree(
            name = treeName,
            ownerId = userId,
            ownerName = creatorName,
            inviteCode = inviteCode,
            isPrivate = false,
            founderRole = selectedFounderRole
        )

        firestoreHelper.createTree(newTree,
            onSuccess = { createdTree ->
                // Immediately establish active tree preferences and isolate cache
                TreePreferences.setActiveTree(this, createdTree.id, createdTree.name)
                FirestoreHelper.setCachedPersons(emptyList())

                // Mark Phase 2 Milestone 1 (Roots) as achieved
                OnboardingPreferences.setMilestoneRoots(this, true, userId)
                firestoreHelper.saveOnboardingState(userId, OnboardingPreferences.getOnboardingState(this, userId))

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

                // Add creator as first person (Patriarch or Matriarch) in the tree
                val selfPerson = Person(
                    firstName = firstName,
                    lastName = lastName,
                    gender = gender,
                    birthDate = selectedDate,
                    treeId = createdTree.id,
                    createdBy = userId,
                    lineageRole = selectedFounderRole
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
