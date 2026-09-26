package com.example.btproject2.utils

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import com.example.btproject2.R
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.ui.activities.AddMemberActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Central dialog helper for the Philippine Family Tree Application's "Add Child" flow.
 *
 * Implements the mandatory architectural requirement:
 * When adding a child to a family member who has a spouse or partner, ask the user to identify
 * the child’s other biological parent:
 * 1. Unknown or not recorded
 * 2. The member’s current spouse or partner
 * 3. Another person (select existing person or create new person)
 *
 * Enforces:
 * - Current spouse is NOT automatically connected as biological parent when "Unknown" or "Another person" is selected.
 * - The spouse of a child's biological parent is treated as a step-parent.
 * - Central relationship-validation service and central synchronization process are respected.
 */
object AddChildBiologicalParentDialogHelper {

    enum class CoParentChoice {
        UNKNOWN,
        CURRENT_SPOUSE,
        ANOTHER_PERSON
    }

    data class BiologicalParentSelectionResult(
        val choice: CoParentChoice,
        val primaryParent: Person,
        val otherParent: Person? = null,
        val isNewPersonCreated: Boolean = false
    )

    /**
     * Prompts the user to identify the other biological parent if [parent] has a spouse or partner.
     * If [parent] has no spouse, invokes [onResult] directly with [CoParentChoice.UNKNOWN].
     */
    fun showIdentifyOtherParentDialog(
        context: Context,
        parent: Person,
        allMembers: List<Person>,
        onResult: (BiologicalParentSelectionResult) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val spouse = parent.spouseId?.let { sId ->
            allMembers.find { FamilyLinkValidator.isSameId(it.id, sId) }
        }

        // If member has no recorded spouse, other parent is unknown/not recorded by default
        if (spouse == null) {
            onResult(
                BiologicalParentSelectionResult(
                    choice = CoParentChoice.UNKNOWN,
                    primaryParent = parent,
                    otherParent = null
                )
            )
            return
        }

        val parentName = "${parent.firstName} ${parent.lastName}".trim()
        val spouseName = "${spouse.firstName} ${spouse.lastName}".trim()

        val choices = arrayOf(
            "1. Unknown or not recorded\n   (Connect only $parentName as biological parent)",
            "2. Current spouse or partner: $spouseName\n   (Connect both as biological parents)",
            "3. Another person\n   (Select existing person or create new person)"
        )

        AlertDialog.Builder(context)
            .setTitle("Identify Other Biological Parent")
            .setMessage("$parentName is currently linked with spouse $spouseName.\n\nWho is the other biological parent of this child?")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        // Choice 1: Unknown or not recorded
                        onResult(
                            BiologicalParentSelectionResult(
                                choice = CoParentChoice.UNKNOWN,
                                primaryParent = parent,
                                otherParent = null
                            )
                        )
                    }
                    1 -> {
                        // Choice 2: Current spouse or partner
                        onResult(
                            BiologicalParentSelectionResult(
                                choice = CoParentChoice.CURRENT_SPOUSE,
                                primaryParent = parent,
                                otherParent = spouse
                            )
                        )
                    }
                    2 -> {
                        // Choice 3: Another person
                        showSelectOrCreateOtherParentDialog(
                            context = context,
                            parent = parent,
                            spouse = spouse,
                            allMembers = allMembers,
                            onResult = onResult,
                            onCancel = onCancel
                        )
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ -> onCancel?.invoke() }
            .show()
    }

    /**
     * Sub-dialog for Choice 3: Allows selecting an existing person or creating a new person.
     */
    private fun showSelectOrCreateOtherParentDialog(
        context: Context,
        parent: Person,
        spouse: Person,
        allMembers: List<Person>,
        onResult: (BiologicalParentSelectionResult) -> Unit,
        onCancel: (() -> Unit)?
    ) {
        val parentName = "${parent.firstName} ${parent.lastName}".trim()
        val isParentMale = parent.gender.equals("male", ignoreCase = true)

        // Filter eligible candidate co-parents:
        // Must not be the primary parent, must not be current spouse, must not violate incestuous co-parenting (Arts. 37/38)
        val candidates = allMembers.filter { candidate ->
            !FamilyLinkValidator.isSameId(candidate.id, parent.id) &&
            !FamilyLinkValidator.isSameId(candidate.id, spouse.id) &&
            (if (isParentMale) candidate.gender.equals("female", ignoreCase = true) else candidate.gender.equals("male", ignoreCase = true)) &&
            FamilyLinkValidator.validateCoParents(
                if (isParentMale) parent else candidate,
                if (isParentMale) candidate else parent,
                allMembers
            ).isValid
        }

        val options = mutableListOf("+ Create New Person")
        options.addAll(candidates.map { "${it.firstName} ${it.lastName}" })

        AlertDialog.Builder(context)
            .setTitle("Select Second Biological Parent")
            .setMessage("Select the other biological parent of $parentName's child:")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    // Option: Create New Person
                    showCreateOtherParentDialog(
                        context = context,
                        parent = parent,
                        onCreated = { newPerson ->
                            onResult(
                                BiologicalParentSelectionResult(
                                    choice = CoParentChoice.ANOTHER_PERSON,
                                    primaryParent = parent,
                                    otherParent = newPerson,
                                    isNewPersonCreated = true
                                )
                            )
                        },
                        onCancel = onCancel
                    )
                } else {
                    // Option: Select Existing Candidate
                    val selected = candidates[which - 1]
                    onResult(
                        BiologicalParentSelectionResult(
                            choice = CoParentChoice.ANOTHER_PERSON,
                            primaryParent = parent,
                            otherParent = selected
                        )
                    )
                }
            }
            .setNegativeButton("Cancel") { _, _ -> onCancel?.invoke() }
            .show()
    }

    /**
     * Dialog to quickly create a new person to act as the second biological parent.
     */
    private fun showCreateOtherParentDialog(
        context: Context,
        parent: Person,
        onCreated: (Person) -> Unit,
        onCancel: (() -> Unit)?
    ) {
        val isParentMale = parent.gender.equals("male", ignoreCase = true)
        val defaultGender = if (isParentMale) "Female" else "Male"
        val roleLabel = if (isParentMale) "Mother" else "Father"

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_quick_add_person, null)
        val etFirst = view.findViewById<EditText>(R.id.etQuickFirstName)
        val etLast = view.findViewById<EditText>(R.id.etQuickLastName)
        val spinnerGender = view.findViewById<Spinner>(R.id.spinnerQuickGender)
        val etBirthDate = view.findViewById<EditText>(R.id.etQuickBirthDate)
        val tvHeader = view.findViewById<TextView>(R.id.tvQuickAddHeader)

        tvHeader.text = "Add $roleLabel (Second Biological Parent)"
        etLast.setText(parent.lastName) // Pre-fill family surname convenience

        val genderOptions = if (isParentMale) listOf("Female", "Male") else listOf("Male", "Female")
        spinnerGender.setDarkAdapter(context, genderOptions)

        var selectedBirthDate = ""
        etBirthDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val c = Calendar.getInstance().also { it.set(y, m, d) }
                    selectedBirthDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                    etBirthDate.setText(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(c.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).also { it.datePicker.maxDate = System.currentTimeMillis() }.show()
        }

        AlertDialog.Builder(context)
            .setTitle("Create Second Biological Parent")
            .setView(view)
            .setPositiveButton("Create & Connect") { _, _ ->
                val fName = etFirst.text.toString().trim()
                val lName = etLast.text.toString().trim()
                val gender = spinnerGender.selectedItem?.toString() ?: defaultGender

                if (fName.isEmpty() || lName.isEmpty()) {
                    Toast.makeText(context, "First and last name are required", Toast.LENGTH_SHORT).show()
                    onCancel?.invoke()
                    return@setPositiveButton
                }

                val firestoreHelper = FirestoreHelper()
                val authHelper = com.example.btproject2.firebase.AuthHelper()
                val newPerson = Person(
                    firstName = fName,
                    lastName = lName,
                    gender = gender,
                    birthDate = selectedBirthDate,
                    treeId = parent.treeId.ifEmpty { TreePreferences.getActiveTreeId(context) },
                    createdBy = authHelper.getCurrentUserId() ?: ""
                )

                firestoreHelper.addPerson(
                    person = newPerson,
                    onSuccess = { savedId ->
                        val savedPerson = newPerson.copy(id = savedId)
                        Toast.makeText(context, "$fName $lName created successfully", Toast.LENGTH_SHORT).show()
                        onCreated(savedPerson)
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "Failed to create person: ${err.message}", Toast.LENGTH_LONG).show()
                        onCancel?.invoke()
                    }
                )
            }
            .setNegativeButton("Cancel") { _, _ -> onCancel?.invoke() }
            .show()
    }

    /**
     * Helper to start [AddMemberActivity] with the appropriate biological parent parameters.
     */
    fun startAddMemberActivity(
        context: Context,
        result: BiologicalParentSelectionResult,
        treeId: String,
        launcher: ActivityResultLauncher<Intent>? = null
    ) {
        val intent = Intent(context, AddMemberActivity::class.java).apply {
            putExtra("presetParentId", result.primaryParent.id)
            putExtra("coParentChoice", result.choice.name)
            result.otherParent?.id?.let { putExtra("presetOtherParentId", it) }
            putExtra("TREE_ID", treeId)
            putExtra("treeId", treeId)
        }

        if (launcher != null) {
            launcher.launch(intent)
        } else {
            context.startActivity(intent)
        }
    }
}

