package com.example.btproject2.ui.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddMemberActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private var familyMembers = listOf<Person>()
    private val treeId = "default_tree"
    private var selectedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_member)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerGender)
        val spinnerCivilStatus = findViewById<Spinner>(R.id.spinnerCivilStatus)
        val etBirthDate = findViewById<EditText>(R.id.etBirthDate)
        val spinnerFather = findViewById<Spinner>(R.id.spinnerFather)
        val spinnerMother = findViewById<Spinner>(R.id.spinnerMother)
        val spinnerSpouse = findViewById<Spinner>(R.id.spinnerSpouse)
        val btnSave = findViewById<Button>(R.id.btnSaveMember)

        // Back button
        btnBack.setOnClickListener { finish() }

        // Gender dropdown
        val genders = listOf("Male", "Female")
        spinnerGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        // Civil Status dropdown
        val civilStatuses = listOf("Single", "Married", "Widowed", "Separated")
        spinnerCivilStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, civilStatuses)

        // Date picker
        etBirthDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val cal = Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, selectedDay)
                val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val saveFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = saveFormat.format(cal.time)
                etBirthDate.setText(displayFormat.format(cal.time))
            }, year, month, day)

            datePicker.datePicker.maxDate = System.currentTimeMillis()
            datePicker.show()
        }

        // Load existing members for parent/spouse selection
        loadFamilyMembers(spinnerFather, spinnerMother, spinnerSpouse)

        // Save button
        btnSave.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()

            if (firstName.isEmpty() || lastName.isEmpty()) {
                showMessage("Error", "Please enter first and last name.")
                return@setOnClickListener
            }

            val fatherIndex = spinnerFather.selectedItemPosition
            val motherIndex = spinnerMother.selectedItemPosition
            val spouseIndex = spinnerSpouse.selectedItemPosition

            val fatherId = if (fatherIndex > 0) familyMembers[fatherIndex - 1].id else null
            val motherId = if (motherIndex > 0) familyMembers[motherIndex - 1].id else null
            val spouseId = if (spouseIndex > 0) familyMembers[spouseIndex - 1].id else null

            val person = Person(
                firstName = firstName,
                lastName = lastName,
                gender = gender,
                birthDate = selectedDate,
                motherId = motherId,
                fatherId = fatherId,
                spouseId = spouseId,
                treeId = treeId,
                createdBy = authHelper.getCurrentUserId() ?: ""
            )

            firestoreHelper.addPerson(person,
                onSuccess = {
                    showMessage("Success", "$firstName $lastName has been added to the family tree.") {
                        finish()
                    }
                },
                onFailure = { error ->
                    showMessage("Error", "Failed to save: ${error.message}")
                }
            )
        }
    }

    private fun loadFamilyMembers(spinnerFather: Spinner, spinnerMother: Spinner, spinnerSpouse: Spinner) {
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                familyMembers = persons

                val names = mutableListOf("-- None --")
                names.addAll(persons.map { "${it.firstName} ${it.lastName}" })

                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                spinnerFather.adapter = adapter
                spinnerMother.adapter = adapter
                spinnerSpouse.adapter = adapter
            },
            onFailure = {
                showMessage("Error", "Failed to load existing members.")
            }
        )
    }

    private fun showMessage(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .show()
    }
}