package com.example.btproject2.ui.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.btproject2.utils.KinTraceAlert
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.ui.dialogs.ActionRestrictedDialog
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.TreePreferences
import com.example.btproject2.utils.setDarkAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditMemberActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private val familyRelationshipService = com.example.btproject2.service.FamilyRelationshipService()
    private var personId: String = ""
    private var currentPerson: Person? = null
    private var allMembers: List<Person> = emptyList()

    private var maleMembers: List<Person> = emptyList()
    private var femaleMembers: List<Person> = emptyList()
    private var eligibleSpouses: List<Person> = emptyList()

    private var selectedBirthDate = ""
    private var selectedDeathDate = ""
    private var selectedMarriageDate = ""
    private var isSaving = false
    private var isViewer = false
    private var currentTreeName = "Family Tree"

    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etSuffix: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var spinnerCivilStatus: Spinner
    private lateinit var switchIsLiving: Switch
    private lateinit var tvVitalStatusDesc: TextView
    private lateinit var etBirthDate: EditText
    private lateinit var etBirthPlace: EditText
    private lateinit var layoutDeathDetails: LinearLayout
    private lateinit var etDeathDate: EditText
    private lateinit var etDeathPlace: EditText
    private lateinit var spinnerFather: Spinner
    private lateinit var spinnerFatherType: Spinner
    private lateinit var spinnerMother: Spinner
    private lateinit var spinnerMotherType: Spinner
    private lateinit var spinnerSpouse: Spinner
    private lateinit var etMarriageDate: EditText
    private lateinit var etBiography: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_member)

        personId = intent.getStringExtra("personId") ?: intent.getStringExtra("PERSON_ID") ?: run {
            KinTraceAlert.warning(this, "Selection Notice", "No member selected")
            finish()
            return
        }

        val passedIsViewer = intent.getBooleanExtra("isViewer", false)
        if (passedIsViewer) {
            isViewer = true
            interceptViewerAccess()
            return
        }

        checkViewerPermissions()

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnQuickAssistEditMember)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.show(
                this,
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.EDIT_MEMBER
            )
        }

        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etSuffix = findViewById(R.id.etSuffix)
        spinnerGender = findViewById(R.id.spinnerGender)
        spinnerCivilStatus = findViewById(R.id.spinnerCivilStatus)
        switchIsLiving = findViewById(R.id.switchIsLiving)
        tvVitalStatusDesc = findViewById(R.id.tvVitalStatusDesc)
        etBirthDate = findViewById(R.id.etBirthDate)
        etBirthPlace = findViewById(R.id.etBirthPlace)
        layoutDeathDetails = findViewById(R.id.layoutDeathDetails)
        etDeathDate = findViewById(R.id.etDeathDate)
        etDeathPlace = findViewById(R.id.etDeathPlace)
        spinnerFather = findViewById(R.id.spinnerFather)
        spinnerFatherType = findViewById(R.id.spinnerFatherType)
        spinnerMother = findViewById(R.id.spinnerMother)
        spinnerMotherType = findViewById(R.id.spinnerMotherType)
        spinnerSpouse = findViewById(R.id.spinnerSpouse)
        etMarriageDate = findViewById(R.id.etMarriageDate)
        etBiography = findViewById(R.id.etBiography)
        btnSave = findViewById(R.id.btnSaveMember)

        spinnerGender.setDarkAdapter(this, listOf("Male", "Female"))
        spinnerCivilStatus.setDarkAdapter(this, listOf("Single", "Married", "Widowed", "Separated"))
        spinnerFatherType.setDarkAdapter(this, listOf("Biological", "Adoptive", "Step"))
        spinnerMotherType.setDarkAdapter(this, listOf("Biological", "Adoptive", "Step"))

        // Living / Deceased toggle
        switchIsLiving.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvVitalStatusDesc.text = "Person is currently living"
                layoutDeathDetails.visibility = View.GONE
                selectedDeathDate = ""
                etDeathDate.text?.clear()
                etDeathPlace.text?.clear()
            } else {
                tvVitalStatusDesc.text = "Person is deceased"
                layoutDeathDetails.visibility = View.VISIBLE
            }
        }

        // Birth Date Picker
        etBirthDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val c = Calendar.getInstance().also { it.set(y, m, d) }
                    selectedBirthDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                    etBirthDate.setText(
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(c.time)
                    )
                    refreshCandidateSpinners()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).also {
                it.datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        // Death Date Picker
        etDeathDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val c = Calendar.getInstance().also { it.set(y, m, d) }
                    selectedDeathDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                    etDeathDate.setText(
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

        // Marriage Date Picker
        etMarriageDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    val c = Calendar.getInstance().also { it.set(y, m, d) }
                    selectedMarriageDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.time)
                    etMarriageDate.setText(
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

        loadMemberData()

        btnSave.setOnClickListener {
            if (isViewer) {
                interceptViewerAccess()
                return@setOnClickListener
            }
            if (isSaving) return@setOnClickListener

            val firstName = etFirstName.text.toString().trim()
            val middleName = etMiddleName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val suffix = etSuffix.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()
            val civil = spinnerCivilStatus.selectedItem.toString()
            val birthPlace = etBirthPlace.text.toString().trim()
            val isLiving = switchIsLiving.isChecked
            val bio = etBiography.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty()) {
                showMessage("Validation Error", "First and last name are required.")
                return@setOnClickListener
            }

            val deathPlace = etDeathPlace.text.toString().trim()
            val enteredDeathDate = etDeathDate.text.toString().trim()
            val effectiveDeathDate = if (selectedDeathDate.isNotBlank()) selectedDeathDate else enteredDeathDate

            if (!isLiving && selectedBirthDate.isNotBlank() && effectiveDeathDate.isNotBlank()) {
                val cmp = com.example.btproject2.engine.FamilyLinkValidator.compareDatesChronologically(effectiveDeathDate, selectedBirthDate)
                if (cmp != null && cmp < 0) {
                    showMessage("Invalid Date", "Date of passing cannot be earlier than birthdate.")
                    return@setOnClickListener
                }
            }

            val fatherIdx = spinnerFather.selectedItemPosition
            val motherIdx = spinnerMother.selectedItemPosition
            val spouseIdx = spinnerSpouse.selectedItemPosition

            val fatherId = if (fatherIdx > 0 && fatherIdx - 1 < maleMembers.size) maleMembers[fatherIdx - 1].id else null
            val motherId = if (motherIdx > 0 && motherIdx - 1 < femaleMembers.size) femaleMembers[motherIdx - 1].id else null
            val spouseId = if (spouseIdx > 0 && spouseIdx - 1 < eligibleSpouses.size) eligibleSpouses[spouseIdx - 1].id else null

            val fatherRelationshipType = if (fatherId != null) spinnerFatherType.selectedItem?.toString() ?: "Biological" else "Biological"
            val motherRelationshipType = if (motherId != null) spinnerMotherType.selectedItem?.toString() ?: "Biological" else "Biological"

            val enteredMarriageDate = etMarriageDate.text.toString().trim()
            val effectiveMarriageDate = if (!spouseId.isNullOrEmpty()) {
                if (selectedMarriageDate.isNotBlank()) selectedMarriageDate else enteredMarriageDate
            } else ""

            val originalPerson = currentPerson ?: return@setOnClickListener

            val effectiveTree = currentPerson?.treeId.orEmpty().ifBlank {
                intent.getStringExtra("TREE_ID")
                    ?: intent.getStringExtra("treeId")
                    ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
            }
            val cachedForTree = if (effectiveTree.isNotBlank()) {
                FirestoreHelper.getCachedPersons(effectiveTree).orEmpty()
            } else emptyList()
            val effectiveMembers = (allMembers + cachedForTree)
                .filter { person ->
                    if (effectiveTree.isNotBlank()) {
                        if (effectiveTree == "default_tree") {
                            person.treeId.isBlank() || person.treeId == "default_tree"
                        } else {
                            person.treeId == effectiveTree
                        }
                    } else true
                }
                .distinctBy { it.id.trim().lowercase() }

            val selectedSpouseObj = spouseId?.let { sid -> effectiveMembers.find { it.id == sid } }
            val resolvedMaritalStatus = if (!spouseId.isNullOrEmpty()) {
                if (selectedSpouseObj != null && (!selectedSpouseObj.isLiving || selectedSpouseObj.deathDate.isNotBlank())) {
                    if (isLiving) "Widowed" else "Married"
                } else if (civil.equals("Separated", ignoreCase = true) ||
                    civil.equals("Annulled", ignoreCase = true) ||
                    civil.equals("Divorced", ignoreCase = true)
                ) {
                    civil
                } else {
                    "Married"
                }
            } else civil

            val updated = originalPerson.copy(
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                suffix = suffix,
                gender = gender,
                birthDate = selectedBirthDate,
                birthPlace = birthPlace,
                isLiving = isLiving,
                deathDate = if (!isLiving) effectiveDeathDate else "",
                deathPlace = if (!isLiving) deathPlace else "",
                maritalStatus = resolvedMaritalStatus,
                marriageDate = effectiveMarriageDate,
                fatherId = fatherId,
                fatherRelationshipType = fatherRelationshipType,
                motherId = motherId,
                motherRelationshipType = motherRelationshipType,
                spouseId = spouseId,
                biography = bio
            )

            val treeMap = effectiveMembers.associateBy { it.id }.toMutableMap()
            // Keep originalPerson.spouseId & maritalStatus in treeMap during pre-flight spouse check
            // so changing a living active spouse is properly evaluated against bigamy rules.
            treeMap[updated.id] = updated.copy(
                spouseId = originalPerson.spouseId,
                maritalStatus = originalPerson.maritalStatus
            )

            // Mandatory Step-Parent Rule: Step-parent cannot be saved as biological parent
            if ((fatherId != null && fatherRelationshipType.equals("Step", ignoreCase = true)) ||
                (motherId != null && motherRelationshipType.equals("Step", ignoreCase = true))
            ) {
                com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                    .setTitle("Cannot Save Step-Parent as Direct Parent")
                    .setMessage("Under Philippine family law and application rules, a step-parent must not be saved as a biological parent. A step-parent relationship arises automatically from marriage to the child's biological parent.\n\nA step-parent can only be recorded as a direct parent if there is an explicit legal adoption. If this person was legally adopted, please select 'Adoptive'.")
                    .setPositiveButton("Understood", null)
                    .show()
                return@setOnClickListener
            }

            // Central Service Father Validation
            if (fatherId != null) {
                val fType = if (fatherRelationshipType.equals("Adoptive", ignoreCase = true))
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.ADOPTIVE_PARENT
                else
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.BIOLOGICAL_PARENT
                val fValidation = familyRelationshipService.checkProposedRelationship(
                    type = fType,
                    primaryPersonId = updated.id,
                    secondaryPersonId = fatherId,
                    tree = treeMap,
                    role = com.example.btproject2.service.FamilyRelationshipService.ParentRole.FATHER
                )
                if (!fValidation.isAllowed) {
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle("Cannot Link Father")
                        .setMessage("${fValidation.reason}\n\n${fValidation.legalBasis}")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Central Service Mother Validation
            if (motherId != null) {
                val mType = if (motherRelationshipType.equals("Adoptive", ignoreCase = true))
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.ADOPTIVE_PARENT
                else
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.BIOLOGICAL_PARENT
                val mValidation = familyRelationshipService.checkProposedRelationship(
                    type = mType,
                    primaryPersonId = updated.id,
                    secondaryPersonId = motherId,
                    tree = treeMap,
                    role = com.example.btproject2.service.FamilyRelationshipService.ParentRole.MOTHER
                )
                if (!mValidation.isAllowed) {
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle("Cannot Link Mother")
                        .setMessage("${mValidation.reason}\n\n${mValidation.legalBasis}")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Central Service Spouse Validation
            if (spouseId != null) {
                val sValidation = familyRelationshipService.checkProposedRelationship(
                    type = com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.SPOUSE,
                    primaryPersonId = updated.id,
                    secondaryPersonId = spouseId,
                    tree = treeMap
                )
                if (!sValidation.isAllowed) {
                    val spouseCandidate = effectiveMembers.find { it.id == spouseId } ?: Person(id = spouseId)
                    val errorMsg = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(updated, spouseCandidate, sValidation)
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle("Cannot Link Spouse")
                        .setMessage(errorMsg)
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // 1. Authoritative Genealogical Validation (Incestuous co-parents/spouse, biological age gaps)
            val fatherObj = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(fatherId, effectiveMembers)
            val motherObj = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(motherId, effectiveMembers)
            if (fatherObj != null && motherObj != null) {
                val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(fatherObj, motherObj, effectiveMembers)
                if (!coParentCheck.isValid) {
                    val firstError = coParentCheck.errors.first()
                    val allErrors = coParentCheck.errors.joinToString("\n\n") { "❌ ${it.message}" }
                    val currentUserId = authHelper.getCurrentUserId().orEmpty()
                    val notif = NotificationHelper.createValidationConflictNotification(
                        treeId = updated.treeId.ifEmpty { TreePreferences.getActiveTreeId(this) },
                        userId = currentUserId,
                        memberName = "${updated.firstName} ${updated.lastName}".trim(),
                        conflictDetails = "Prohibited Co-Parents: ${firstError.message}"
                    )
                    firestoreHelper.addNotification(notif)
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle(firstError.title)
                        .setMessage(allErrors)
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            if (spouseId != null) {
                val spouseObj = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(spouseId, effectiveMembers)
                val spouseCheck = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(updated, spouseObj, effectiveMembers)
                if (!spouseCheck.isValid) {
                    val firstError = spouseCheck.errors.first()
                    val allErrors = spouseCheck.errors.joinToString("\n\n") { "❌ ${it.message}" }
                    val currentUserId = authHelper.getCurrentUserId().orEmpty()
                    val notif = NotificationHelper.createValidationConflictNotification(
                        treeId = updated.treeId.ifEmpty { TreePreferences.getActiveTreeId(this) },
                        userId = currentUserId,
                        memberName = "${updated.firstName} ${updated.lastName}".trim(),
                        conflictDetails = "Prohibited Spouse: ${firstError.message}"
                    )
                    firestoreHelper.addNotification(notif)
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle(firstError.title)
                        .setMessage(allErrors)
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            val linkResult = com.example.btproject2.engine.FamilyLinkValidator.validatePersonComprehensive(
                updated,
                effectiveMembers
            )

            if (!linkResult.isValid) {
                val firstError = linkResult.errors.first()
                val allErrors = linkResult.errors.joinToString("\n\n") { "❌ ${it.message}" }
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createValidationConflictNotification(
                    treeId = updated.treeId.ifEmpty { TreePreferences.getActiveTreeId(this) },
                    userId = currentUserId,
                    memberName = "${updated.firstName} ${updated.lastName}".trim(),
                    conflictDetails = "Genealogical Conflict: ${firstError.message}"
                )
                firestoreHelper.addNotification(notif)
                com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                    .setTitle(firstError.title)
                    .setMessage(allErrors)
                    .setPositiveButton("Understood", null)
                    .show()
                return@setOnClickListener
            }

            val validatedUpdated = if (!spouseId.isNullOrEmpty()) {
                val linked = originalPerson.withLinkedSpouse(spouseId, updated.maritalStatus)
                updated.copy(formerSpouseIds = linked.formerSpouseIds)
            } else {
                updated
            }

            if (linkResult.hasWarnings) {
                val hasPosthumousWarning = linkResult.warnings.any { it.title.contains("Posthumous", ignoreCase = true) }
                val warningText = linkResult.warnings.joinToString("\n") { "⚠️ ${it.message}" }
                com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                    .setTitle("Warnings Found")
                    .setMessage("$warningText\n\nDo you still want to save?")
                    .setPositiveButton("Save Anyway") { _, _ ->
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val conflictMsg = linkResult.warnings.firstOrNull()?.message ?: "Biological relationship warning."
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = validatedUpdated.treeId.ifEmpty { TreePreferences.getActiveTreeId(this) },
                            userId = currentUserId,
                            memberName = "${validatedUpdated.firstName} ${validatedUpdated.lastName}".trim(),
                            conflictDetails = conflictMsg
                        )
                        firestoreHelper.addNotification(notif)
                        val personToSave = if (hasPosthumousWarning) validatedUpdated.copy(hasTimelineConflict = true) else validatedUpdated
                        saveUpdatedMember(personToSave, originalPerson)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }

            saveUpdatedMember(validatedUpdated, originalPerson)
        }
    }

    private fun loadMemberData() {
        val preferredTreeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val processMembers: (List<Person>) -> Unit = { rawMembers ->
            val sanitized = FirestoreHelper.sanitizeTreeRecords(rawMembers)
            val target = sanitized.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) }
                ?: FirestoreHelper.getCachedPersons(preferredTreeId.ifBlank { null })?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) }
            if (target == null) {
                KinTraceAlert.warning(this, "Not Found", "Member not found")
                finish()
            } else {
                val effectiveTreeId = target.treeId.ifBlank { preferredTreeId }
                allMembers = if (effectiveTreeId.isNotBlank()) {
                    if (effectiveTreeId == "default_tree") {
                        sanitized.filter { it.treeId.isBlank() || it.treeId == "default_tree" }
                    } else {
                        sanitized.filter { it.treeId == effectiveTreeId }
                    }
                } else sanitized

                // Mutual spouse resolution: If target's spouseId is missing, resolve from partner pointing to target
                val resolvedSpouseId = target.spouseId?.ifEmpty { null }
                    ?: allMembers.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, target.id) }?.id
                val effectiveTarget = if (resolvedSpouseId != null && target.spouseId.isNullOrEmpty()) {
                    target.copy(spouseId = resolvedSpouseId, maritalStatus = "Married")
                } else target

                currentPerson = effectiveTarget
                populateFields(effectiveTarget)
                refreshCandidateSpinners()
            }
        }

        if (preferredTreeId.isNotBlank()) {
            firestoreHelper.getPersonsByTree(
                treeId = preferredTreeId,
                onSuccess = { members ->
                    if (members.any { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) }) {
                        processMembers(members)
                    } else {
                        firestoreHelper.getAllPersons(processMembers) {
                            KinTraceAlert.warning(this, "Load Error", "Failed to load member data")
                            finish()
                        }
                    }
                },
                onFailure = {
                    firestoreHelper.getAllPersons(processMembers) {
                        KinTraceAlert.warning(this, "Load Error", "Failed to load member data")
                        finish()
                    }
                }
            )
        } else {
            firestoreHelper.getAllPersons(processMembers) {
                KinTraceAlert.warning(this, "Load Error", "Failed to load member data")
                finish()
            }
        }
    }

    private fun populateFields(p: Person) {
        etFirstName.setText(p.firstName)
        etMiddleName.setText(p.middleName)
        etLastName.setText(p.lastName)
        etSuffix.setText(p.suffix)

        if (p.gender.equals("Female", ignoreCase = true)) {
            spinnerGender.setSelection(1)
        } else {
            spinnerGender.setSelection(0)
        }

        val civilOptions = listOf("Single", "Married", "Widowed", "Separated")
        val civilIdx = civilOptions.indexOfFirst { it.equals(p.maritalStatus, ignoreCase = true) }
        if (civilIdx >= 0) spinnerCivilStatus.setSelection(civilIdx)

        switchIsLiving.isChecked = p.isLiving
        if (p.isLiving) {
            tvVitalStatusDesc.text = "Person is currently living"
            layoutDeathDetails.visibility = View.GONE
        } else {
            tvVitalStatusDesc.text = "Person is deceased"
            layoutDeathDetails.visibility = View.VISIBLE
        }

        selectedBirthDate = p.birthDate
        etBirthDate.setText(p.birthDate)
        etBirthPlace.setText(p.birthPlace)

        selectedDeathDate = p.deathDate
        etDeathDate.setText(p.deathDate)
        etDeathPlace.setText(p.deathPlace)

        selectedMarriageDate = p.marriageDate
        etMarriageDate.setText(p.marriageDate)

        val relOptions = listOf("Biological", "Adoptive", "Step")
        val fatherRelIdx = relOptions.indexOfFirst { it.equals(p.fatherRelationshipType, ignoreCase = true) }
        spinnerFatherType.setSelection(if (fatherRelIdx >= 0) fatherRelIdx else 0)

        val motherRelIdx = relOptions.indexOfFirst { it.equals(p.motherRelationshipType, ignoreCase = true) }
        spinnerMotherType.setSelection(if (motherRelIdx >= 0) motherRelIdx else 0)

        etBiography.setText(p.biography)
    }

    private fun refreshCandidateSpinners() {
        val target = currentPerson ?: return
        val targetWithDate = target.copy(birthDate = selectedBirthDate, fatherId = null, motherId = null)

        val currentFather = allMembers.find { it.id == target.fatherId }
        val currentMother = allMembers.find { it.id == target.motherId }

        isUpdatingSpinners = true

        // Eligible Fathers using FamilyLinkValidator
        maleMembers = com.example.btproject2.engine.FamilyLinkValidator.getEligibleFathers(
            targetWithDate,
            currentMother,
            allMembers,
            preferredOtherParentId = target.fatherId
        )
        val fatherNames = mutableListOf("-- None --")
        fatherNames.addAll(maleMembers.map { "${it.firstName} ${it.lastName}" })
        spinnerFather.setDarkAdapter(this, fatherNames)
        val selectedFatherIdx = maleMembers.indexOfFirst { it.id == currentPerson?.fatherId }
        if (selectedFatherIdx >= 0) spinnerFather.setSelection(selectedFatherIdx + 1)

        // Eligible Mothers using FamilyLinkValidator
        femaleMembers = com.example.btproject2.engine.FamilyLinkValidator.getEligibleMothers(
            targetWithDate,
            currentFather,
            allMembers,
            preferredOtherParentId = target.motherId
        )
        val motherNames = mutableListOf("-- None --")
        motherNames.addAll(femaleMembers.map { "${it.firstName} ${it.lastName}" })
        spinnerMother.setDarkAdapter(this, motherNames)
        val selectedMotherIdx = femaleMembers.indexOfFirst { it.id == currentPerson?.motherId }
        if (selectedMotherIdx >= 0) spinnerMother.setSelection(selectedMotherIdx + 1)

        // Eligible Spouses using FamilyLinkValidator
        eligibleSpouses = com.example.btproject2.engine.FamilyLinkValidator.getEligibleSpouses(targetWithDate, allMembers)
        val spouseNames = mutableListOf("-- None --")
        spouseNames.addAll(eligibleSpouses.map { "${it.firstName} ${it.lastName}" })
        spinnerSpouse.setDarkAdapter(this, spouseNames)
        val selectedSpouseIdx = eligibleSpouses.indexOfFirst { it.id == currentPerson?.spouseId }
        if (selectedSpouseIdx >= 0) spinnerSpouse.setSelection(selectedSpouseIdx + 1)

        lastSelectedFatherId = if (selectedFatherIdx >= 0) maleMembers[selectedFatherIdx].id else null
        lastSelectedMotherId = if (selectedMotherIdx >= 0) femaleMembers[selectedMotherIdx].id else null

        isUpdatingSpinners = false

        setupCoParentSpinnerListeners(targetWithDate)
    }

    private var isUpdatingSpinners = false
    private var lastSelectedFatherId: String? = null
    private var lastSelectedMotherId: String? = null

    private fun setupCoParentSpinnerListeners(targetWithDate: Person) {
        spinnerFather.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingSpinners) return
                val selectedFather = if (position > 0 && position - 1 < maleMembers.size) maleMembers[position - 1] else null
                if (selectedFather?.id == lastSelectedFatherId) return
                lastSelectedFatherId = selectedFather?.id

                val currentMother = if (spinnerMother.selectedItemPosition > 0 && spinnerMother.selectedItemPosition - 1 < femaleMembers.size) femaleMembers[spinnerMother.selectedItemPosition - 1] else null

                isUpdatingSpinners = true
                femaleMembers = com.example.btproject2.engine.FamilyLinkValidator.getEligibleMothers(targetWithDate, selectedFather, allMembers)
                val motherNames = mutableListOf("-- None --")
                motherNames.addAll(femaleMembers.map { "${it.firstName} ${it.lastName}" })
                spinnerMother.setDarkAdapter(this@EditMemberActivity, motherNames)

                val linkedSpouse = com.example.btproject2.engine.FamilyLinkValidator.findLinkedSpouse(selectedFather, allMembers)
                val linkedSpouseIdx = if (linkedSpouse != null) {
                    femaleMembers.indexOfFirst { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, linkedSpouse.id) }
                } else -1

                if (selectedFather != null && linkedSpouseIdx >= 0) {
                    spinnerMother.setSelection(linkedSpouseIdx + 1)
                    lastSelectedMotherId = femaleMembers[linkedSpouseIdx].id
                } else if (currentMother != null) {
                    val newIdx = femaleMembers.indexOfFirst { it.id == currentMother.id }
                    if (newIdx >= 0) {
                        spinnerMother.setSelection(newIdx + 1)
                        lastSelectedMotherId = femaleMembers[newIdx].id
                    } else {
                        spinnerMother.setSelection(0)
                        lastSelectedMotherId = null
                        if (selectedFather != null && linkedSpouse == null) {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val notif = NotificationHelper.createValidationConflictNotification(
                                treeId = currentPerson?.treeId?.ifEmpty { TreePreferences.getActiveTreeId(this@EditMemberActivity) } ?: TreePreferences.getActiveTreeId(this@EditMemberActivity),
                                userId = currentUserId,
                                memberName = "${currentMother.firstName} ${currentMother.lastName}".trim(),
                                conflictDetails = "Co-parenting blocked: ${currentMother.firstName} removed as mother because co-parenting is prohibited with ${selectedFather.firstName} under Family Code Arts. 37/38."
                            )
                            firestoreHelper.addNotification(notif)
                            KinTraceAlert.warning(this@EditMemberActivity, "Rule Blocked", "${currentMother.firstName} removed as mother (co-parenting prohibited with ${selectedFather.firstName})")
                        }
                    }
                } else {
                    spinnerMother.setSelection(0)
                    lastSelectedMotherId = null
                }
                isUpdatingSpinners = false
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerMother.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingSpinners) return
                val selectedMother = if (position > 0 && position - 1 < femaleMembers.size) femaleMembers[position - 1] else null
                if (selectedMother?.id == lastSelectedMotherId) return
                lastSelectedMotherId = selectedMother?.id

                val currentFather = if (spinnerFather.selectedItemPosition > 0 && spinnerFather.selectedItemPosition - 1 < maleMembers.size) maleMembers[spinnerFather.selectedItemPosition - 1] else null

                isUpdatingSpinners = true
                maleMembers = com.example.btproject2.engine.FamilyLinkValidator.getEligibleFathers(targetWithDate, selectedMother, allMembers)
                val fatherNames = mutableListOf("-- None --")
                fatherNames.addAll(maleMembers.map { "${it.firstName} ${it.lastName}" })
                spinnerFather.setDarkAdapter(this@EditMemberActivity, fatherNames)

                val linkedSpouse = com.example.btproject2.engine.FamilyLinkValidator.findLinkedSpouse(selectedMother, allMembers)
                val linkedSpouseIdx = if (linkedSpouse != null) {
                    maleMembers.indexOfFirst { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, linkedSpouse.id) }
                } else -1

                if (selectedMother != null && linkedSpouseIdx >= 0) {
                    spinnerFather.setSelection(linkedSpouseIdx + 1)
                    lastSelectedFatherId = maleMembers[linkedSpouseIdx].id
                } else if (currentFather != null) {
                    val newIdx = maleMembers.indexOfFirst { it.id == currentFather.id }
                    if (newIdx >= 0) {
                        spinnerFather.setSelection(newIdx + 1)
                        lastSelectedFatherId = maleMembers[newIdx].id
                    } else {
                        spinnerFather.setSelection(0)
                        lastSelectedFatherId = null
                        if (selectedMother != null && linkedSpouse == null) {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val notif = NotificationHelper.createValidationConflictNotification(
                                treeId = currentPerson?.treeId?.ifEmpty { TreePreferences.getActiveTreeId(this@EditMemberActivity) } ?: TreePreferences.getActiveTreeId(this@EditMemberActivity),
                                userId = currentUserId,
                                memberName = "${currentFather.firstName} ${currentFather.lastName}".trim(),
                                conflictDetails = "Co-parenting blocked: ${currentFather.firstName} removed as father because co-parenting is prohibited with ${selectedMother.firstName} under Family Code Arts. 37/38."
                            )
                            firestoreHelper.addNotification(notif)
                            KinTraceAlert.warning(this@EditMemberActivity, "Rule Blocked", "${currentFather.firstName} removed as father (co-parenting prohibited with ${selectedMother.firstName})")
                        }
                    }
                } else {
                    spinnerFather.setSelection(0)
                    lastSelectedFatherId = null
                }
                isUpdatingSpinners = false
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun saveUpdatedMember(updatedPerson: Person, originalPerson: Person) {
        setSavingState(true)

        val onSaveCompleted = {
            val currentUserId = authHelper.getCurrentUserId() ?: ""
            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

            // Log Activity (D8)
            firestoreHelper.logActivity(
                treeId = updatedPerson.treeId.ifEmpty { TreePreferences.getActiveTreeId(this) },
                userId = currentUserId,
                userName = currentUserName,
                type = "UPDATE",
                description = "Updated details for ${updatedPerson.firstName} ${updatedPerson.lastName}",
                targetId = updatedPerson.id,
                targetName = "${updatedPerson.firstName} ${updatedPerson.lastName}"
            )

            // Add Notification (D7)
            firestoreHelper.addNotification(
                treeId = updatedPerson.treeId.ifEmpty { TreePreferences.getActiveTreeId(this) },
                userId = currentUserId,
                title = "Member Record Updated",
                message = "${updatedPerson.firstName} ${updatedPerson.lastName}'s information was updated.",
                type = "RECORDS",
                targetId = updatedPerson.id
            )

            setSavingState(false)
            KinTraceAlert.show(this, "Member updated successfully")
            val returnIntent = Intent().apply {
                putExtra("updatedPersonId", updatedPerson.id)
            }
            setResult(RESULT_OK, returnIntent)
            finish()
        }

        val vitalStatusChanged = updatedPerson.isLiving != originalPerson.isLiving ||
                updatedPerson.deathDate != originalPerson.deathDate ||
                updatedPerson.deathPlace != originalPerson.deathPlace

        val proceedWithUpdatePerson = {
            firestoreHelper.updatePerson(
                updatedPerson,
                onSuccess = {
                    // Ensure reciprocal spouse links are atomically synchronized and awaited
                    val spouseChanged = updatedPerson.spouseId != originalPerson.spouseId
                    val hasSpouse = !updatedPerson.spouseId.isNullOrEmpty()

                    if (spouseChanged || hasSpouse) {
                        firestoreHelper.updateSpouse(
                            personId = updatedPerson.id,
                            spouseId = updatedPerson.spouseId,
                            maritalStatus = updatedPerson.maritalStatus,
                            marriageDate = updatedPerson.marriageDate,
                            onSuccess = { onSaveCompleted() },
                            onFailure = { onSaveCompleted() } // Always complete gracefully
                        )
                    } else {
                        onSaveCompleted()
                    }
                },
                onFailure = { error ->
                    setSavingState(false)
                    showMessage("Error", "Failed to update member: ${error.message}")
                }
            )
        }

        if (vitalStatusChanged) {
            firestoreHelper.updateVitalStatus(
                personId = updatedPerson.id,
                isLiving = updatedPerson.isLiving,
                deathDate = updatedPerson.deathDate,
                deathPlace = updatedPerson.deathPlace,
                hasTimelineConflict = updatedPerson.hasTimelineConflict,
                onSuccess = {
                    proceedWithUpdatePerson()
                },
                onFailure = { error ->
                    setSavingState(false)
                    showMessage("Error", "Failed to update vital status: ${error.message}")
                }
            )
        } else {
            proceedWithUpdatePerson()
        }
    }

    private fun isAncestorOf(ancestorId: String, personId: String): Boolean {
        if (ancestorId.isBlank() || personId.isBlank()) return false
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(personId)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (!visited.add(currentId)) continue
            val current = allMembers.firstOrNull { it.id == currentId } ?: continue
            val parentIds = listOfNotNull(current.fatherId, current.motherId)
            for (parentId in parentIds) {
                if (parentId == ancestorId) return true
                queue.add(parentId)
            }
        }
        return false
    }

    private fun setSavingState(saving: Boolean) {
        isSaving = saving
        btnSave.isEnabled = !saving
        btnSave.text = if (saving) "Saving..." else "Save Changes"
    }

    private fun showMessage(title: String, message: String) {
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun interceptViewerAccess() {
        val effectiveTree = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        val dialog = ActionRestrictedDialog.newInstance(
            treeId = effectiveTree,
            treeName = currentTreeName,
            actionAttempted = "Edit Member Record"
        )
        dialog.onDismissAction = {
            if (!isFinishing) finish()
        }
        dialog.show(supportFragmentManager, ActionRestrictedDialog.TAG)
    }

    private fun checkViewerPermissions() {
        val effectiveTree = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (effectiveTree.isBlank() || currentUserId.isBlank()) return

        firestoreHelper.getTree(effectiveTree,
            onSuccess = { tree ->
                currentTreeName = tree?.name ?: "Family Tree"
                val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (!isOwner) {
                    firestoreHelper.getUserMembership(currentUserId, effectiveTree,
                        onSuccess = { member ->
                            if (member?.role.equals("Viewer", ignoreCase = true)) {
                                isViewer = true
                                interceptViewerAccess()
                            }
                        },
                        onFailure = {}
                    )
                }
            },
            onFailure = {}
        )
    }
}

