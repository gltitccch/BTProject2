package com.example.btproject2.ui.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.Person
import com.example.btproject2.sync.AddMemberSyncCoordinator
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.setDarkAdapter
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddMemberActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private val familyRelationshipService = com.example.btproject2.service.FamilyRelationshipService()

    private var allMembers = listOf<Person>()
    private var treeId = ""

    internal val syncCoordinator by lazy {
        AddMemberSyncCoordinator(
            treeId = treeId,
            listenerKey = "AddMemberActivity@${System.identityHashCode(this)}",
            allMembersList = allMembers,
            selectedChildrenList = selectedChildren,
            onCandidatesUpdated = { updatedAll, _, _, _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    allMembers = updatedAll
                    refreshCandidateSpinners()
                }
            },
            onChildRemovedFromSelection = { _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (selectedChildren.isEmpty()) {
                        tvSelectedChildren.text = "None selected (tap to select)"
                    } else {
                        tvSelectedChildren.text = "${selectedChildren.size} child(ren) selected"
                    }
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        syncCoordinator.register()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncCoordinator.unregister()
    }
    private var presetParentId: String? = null
    private var presetOtherParentId: String? = null
    private var coParentChoice: String? = null
    private var hasPromptedOtherParentDialog: Boolean = false
    private var presetSpouseId: String? = null
    private var isChildSpouseMode: Boolean = false
    private var inLawParentId: String? = null
    private var selectedDate = ""

    private var maleMembers = listOf<Person>()
    private var femaleMembers = listOf<Person>()
    private var eligibleSpouses = listOf<Person>()

    private lateinit var spinnerFather: Spinner
    private lateinit var spinnerFatherType: Spinner
    private lateinit var spinnerMother: Spinner
    private lateinit var spinnerMotherType: Spinner
    private lateinit var spinnerSpouse: Spinner
    private lateinit var spinnerGender: Spinner
    private lateinit var spinnerCivilStatus: Spinner
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var btnSave: Button
    private lateinit var layoutParentContextBanner: View
    private lateinit var tvParentBannerText: TextView
    private lateinit var btnSelectChildren: View
    private lateinit var tvSelectedChildren: TextView
    private val selectedChildren = mutableListOf<Person>()
    private var isSaving = false

    private lateinit var switchIsLiving: Switch
    private lateinit var tvVitalStatusDesc: TextView
    private lateinit var layoutDeathDetails: LinearLayout
    private lateinit var etDeathDate: EditText
    private var selectedDeathDate = ""

    private var attachedPhotoUri: String = ""
    private var attachedPhotoBase64: String = ""
    private val attachedDocumentsList = mutableListOf<com.example.btproject2.models.AttachedDocument>()
    private lateinit var tvAttachedPhotoStatus: TextView
    private lateinit var tvAttachedDocStatus: TextView

    private val addPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val tempId = "temp_${System.currentTimeMillis()}"
            val res = com.example.btproject2.utils.DocumentHelper.saveImageToInternal(this, uri, tempId)
            if (res != null) {
                attachedPhotoUri = res.first
                attachedPhotoBase64 = res.second
                tvAttachedPhotoStatus.text = "✓ Profile Photo Selected"
                tvAttachedPhotoStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
            }
        }
    }

    private val addDocPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val tempId = "temp_${System.currentTimeMillis()}"
            val doc = com.example.btproject2.utils.DocumentHelper.saveDocumentToInternal(this, uri, tempId)
            if (doc != null) {
                attachedDocumentsList.add(doc)
                tvAttachedDocStatus.text = "✓ ${doc.name} Attached"
                tvAttachedDocStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_member)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        presetParentId = intent.getStringExtra("presetParentId") ?: intent.getStringExtra("parentId")
        presetOtherParentId = intent.getStringExtra("presetOtherParentId")
        coParentChoice = intent.getStringExtra("coParentChoice")
        presetSpouseId = intent.getStringExtra("presetSpouseId")
        isChildSpouseMode = intent.getBooleanExtra("isChildSpouseMode", false)
        inLawParentId = intent.getStringExtra("inLawParentId")

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnSave = findViewById(R.id.btnSaveMember)

        layoutParentContextBanner = findViewById(R.id.layoutParentContextBanner)
        tvParentBannerText = findViewById(R.id.tvParentBannerText)
        btnSelectChildren = findViewById(R.id.btnSelectChildren)
        tvSelectedChildren = findViewById(R.id.tvSelectedChildren)

        val btnAttachPhoto = findViewById<View>(R.id.btnAttachPhoto)
        tvAttachedPhotoStatus = findViewById(R.id.tvAttachedPhotoStatus)
        val btnAttachDocument = findViewById<View>(R.id.btnAttachDocument)
        tvAttachedDocStatus = findViewById(R.id.tvAttachedDocStatus)

        btnAttachPhoto?.setOnClickListener { addPhotoPickerLauncher.launch("image/*") }
        btnAttachDocument?.setOnClickListener { addDocPickerLauncher.launch("*/*") }

        btnSelectChildren.setOnClickListener {
            showChildrenSelectionDialog()
        }

        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etBirthDate = findViewById(R.id.etBirthDate)
        spinnerGender = findViewById(R.id.spinnerGender)
        spinnerCivilStatus = findViewById(R.id.spinnerCivilStatus)
        spinnerFather = findViewById(R.id.spinnerFather)
        spinnerFatherType = findViewById(R.id.spinnerFatherType)
        spinnerMother = findViewById(R.id.spinnerMother)
        spinnerMotherType = findViewById(R.id.spinnerMotherType)
        spinnerSpouse = findViewById(R.id.spinnerSpouse)

        btnBack.setOnClickListener { finish() }

        val currentUserId = authHelper.getCurrentUserId() ?: ""
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (!isOwner) {
                    firestoreHelper.getUserMembership(currentUserId, treeId,
                        onSuccess = { member ->
                            if (member?.role.equals("Viewer", ignoreCase = true)) {
                                Toast.makeText(this, "Action restricted: Viewers cannot add members.", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        },
                        onFailure = {}
                    )
                }
            },
            onFailure = {}
        )

        spinnerGender.setDarkAdapter(this, listOf("Male", "Female"))
        spinnerCivilStatus.setDarkAdapter(this, listOf("Single", "Married", "Widowed", "Separated"))
        spinnerFatherType.setDarkAdapter(this, listOf("Biological", "Adoptive", "Step"))
        spinnerMotherType.setDarkAdapter(this, listOf("Biological", "Adoptive", "Step"))
        switchIsLiving = findViewById(R.id.switchIsLiving)
        tvVitalStatusDesc = findViewById(R.id.tvVitalStatusDesc)
        layoutDeathDetails = findViewById(R.id.layoutDeathDetails)
        etDeathDate = findViewById(R.id.etDeathDate)

        switchIsLiving.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvVitalStatusDesc.text = "Person is currently living"
                layoutDeathDetails.visibility = View.GONE
                selectedDeathDate = ""
                etDeathDate.text?.clear()
            } else {
                tvVitalStatusDesc.text = "Person is deceased"
                layoutDeathDetails.visibility = View.VISIBLE
            }
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
                    refreshCandidateSpinners()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).also {
                it.datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

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

        loadFamilyMembers()

        btnSave.setOnClickListener {
            if (isSaving) return@setOnClickListener

            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val gender = spinnerGender.selectedItem.toString()
            val civil = spinnerCivilStatus.selectedItem.toString()

            if (firstName.isEmpty() || lastName.isEmpty()) {
                showMessage("Error", "Please enter first and last name.")
                return@setOnClickListener
            }

            val fatherIdx = spinnerFather.selectedItemPosition
            val motherIdx = spinnerMother.selectedItemPosition
            val spouseIdx = spinnerSpouse.selectedItemPosition

            val fatherId = if (fatherIdx > 0) maleMembers[fatherIdx - 1].id else null
            val motherId = if (motherIdx > 0) femaleMembers[motherIdx - 1].id else null
            val spouseId = if (spouseIdx > 0) eligibleSpouses[spouseIdx - 1].id else null

            val fatherRelationshipType = if (fatherId != null) spinnerFatherType.selectedItem?.toString() ?: "Biological" else "Biological"
            val motherRelationshipType = if (motherId != null) spinnerMotherType.selectedItem?.toString() ?: "Biological" else "Biological"

            val isLiving = switchIsLiving.isChecked
            val deathDate = if (!isLiving) selectedDeathDate else ""

            val tempPerson = Person(
                firstName = firstName,
                lastName = lastName,
                gender = gender,
                birthDate = selectedDate,
                isLiving = isLiving,
                deathDate = deathDate,
                maritalStatus = if (!spouseId.isNullOrEmpty()) "Married" else civil,
                motherId = motherId,
                motherRelationshipType = motherRelationshipType,
                fatherId = fatherId,
                fatherRelationshipType = fatherRelationshipType,
                spouseId = spouseId,
                treeId = treeId,
                createdBy = authHelper.getCurrentUserId() ?: "",
                photoUri = attachedPhotoUri,
                photoBase64 = attachedPhotoBase64,
                documents = attachedDocumentsList
            )

            val effectiveMembers = getEffectiveTreeMembers()
            val treeMap = effectiveMembers.associateBy { it.id }.toMutableMap()
            treeMap[tempPerson.id] = tempPerson

            // Central Service Rule 8: In-law check
            if (isChildSpouseMode && !presetSpouseId.isNullOrEmpty()) {
                val child = effectiveMembers.find { it.id == presetSpouseId }
                val inLawIds = setOfNotNull(inLawParentId, child?.fatherId, child?.motherId)
                if (fatherId in inLawIds || motherId in inLawIds) {
                    AlertDialog.Builder(this)
                        .setTitle("Invalid In-Law Connection")
                        .setMessage("A son-in-law or daughter-in-law must be connected as the spouse of an existing child. They must never be connected as a biological child of the parent-in-law.")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Mandatory Step-Parent Rule: Step-parent cannot be saved as biological parent
            if (fatherRelationshipType.equals("Step", ignoreCase = true) || motherRelationshipType.equals("Step", ignoreCase = true)) {
                AlertDialog.Builder(this)
                    .setTitle("Cannot Save Step-Parent as Direct Parent")
                    .setMessage("Under Philippine family law and application rules, a step-parent must not be saved as a biological parent. A step-parent relationship arises automatically from marriage to the child's biological parent.\n\nA step-parent can only be recorded as a direct parent if there is an explicit legal adoption. If this person was legally adopted, please select 'Adoptive'.")
                    .setPositiveButton("Understood", null)
                    .show()
                return@setOnClickListener
            }

            val presetParent = presetParentId?.let { pid -> effectiveMembers.find { it.id == pid } }
            val spouseOfPreset = presetParent?.spouseId?.let { sid -> effectiveMembers.find { it.id == sid } }
            if (presetParent != null && spouseOfPreset != null && coParentChoice == "UNKNOWN") {
                val isSpouseFather = fatherId != null && fatherId == spouseOfPreset.id && fatherRelationshipType.equals("Biological", ignoreCase = true)
                val isSpouseMother = motherId != null && motherId == spouseOfPreset.id && motherRelationshipType.equals("Biological", ignoreCase = true)
                if (isSpouseFather || isSpouseMother) {
                    AlertDialog.Builder(this)
                        .setTitle("Cannot Link Step-Parent as Biological Parent")
                        .setMessage("${spouseOfPreset.firstName} ${spouseOfPreset.lastName} is the spouse of biological parent ${presetParent.firstName}. Under application rules, a step-parent must not be connected as a biological parent when the other parent is recorded as unknown, unless there is a separate legal adoption (choose 'Adoptive').")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Central Service: Father Validation
            if (fatherId != null) {
                val fType = if (fatherRelationshipType.equals("Adoptive", ignoreCase = true))
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.ADOPTIVE_PARENT
                else
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.BIOLOGICAL_PARENT
                val fValidation = familyRelationshipService.checkProposedRelationship(
                    type = fType,
                    primaryPersonId = tempPerson.id,
                    secondaryPersonId = fatherId,
                    tree = treeMap,
                    role = com.example.btproject2.service.FamilyRelationshipService.ParentRole.FATHER
                )
                if (!fValidation.isAllowed) {
                    AlertDialog.Builder(this)
                        .setTitle("Cannot Link Father")
                        .setMessage("${fValidation.reason}\n\n${fValidation.legalBasis}")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Central Service: Mother Validation
            if (motherId != null) {
                val mType = if (motherRelationshipType.equals("Adoptive", ignoreCase = true))
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.ADOPTIVE_PARENT
                else
                    com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.BIOLOGICAL_PARENT
                val mValidation = familyRelationshipService.checkProposedRelationship(
                    type = mType,
                    primaryPersonId = tempPerson.id,
                    secondaryPersonId = motherId,
                    tree = treeMap,
                    role = com.example.btproject2.service.FamilyRelationshipService.ParentRole.MOTHER
                )
                if (!mValidation.isAllowed) {
                    AlertDialog.Builder(this)
                        .setTitle("Cannot Link Mother")
                        .setMessage("${mValidation.reason}\n\n${mValidation.legalBasis}")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Central Service: Spouse Validation
            if (spouseId != null) {
                val sValidation = familyRelationshipService.checkProposedRelationship(
                    type = com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.SPOUSE,
                    primaryPersonId = tempPerson.id,
                    secondaryPersonId = spouseId,
                    tree = treeMap
                )
                if (!sValidation.isAllowed) {
                    val spouseCandidate = effectiveMembers.find { it.id == spouseId } ?: Person(id = spouseId)
                    val errorMsg = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(tempPerson, spouseCandidate, sValidation)
                    AlertDialog.Builder(this)
                        .setTitle("Cannot Link Spouse")
                        .setMessage(errorMsg)
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            // Central Service: Children Validation
            for (child in selectedChildren) {
                val cValidation = familyRelationshipService.checkProposedRelationship(
                    type = com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.BIOLOGICAL_CHILD,
                    primaryPersonId = tempPerson.id,
                    secondaryPersonId = child.id,
                    tree = treeMap
                )
                if (!cValidation.isAllowed) {
                    AlertDialog.Builder(this)
                        .setTitle("Cannot Link Child (${child.firstName})")
                        .setMessage(cValidation.reason)
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
                        treeId = treeId,
                        userId = currentUserId,
                        memberName = "${tempPerson.firstName} ${tempPerson.lastName}".trim().ifEmpty { "New Member" },
                        conflictDetails = "Prohibited Co-Parents: ${firstError.message}"
                    )
                    firestoreHelper.addNotification(notif)
                    AlertDialog.Builder(this)
                        .setTitle(firstError.title)
                        .setMessage(allErrors)
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            if (spouseId != null) {
                val spouseObj = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(spouseId, effectiveMembers)
                val spouseCheck = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(tempPerson, spouseObj, effectiveMembers)
                if (!spouseCheck.isValid) {
                    val firstError = spouseCheck.errors.first()
                    val allErrors = spouseCheck.errors.joinToString("\n\n") { "❌ ${it.message}" }
                    val currentUserId = authHelper.getCurrentUserId().orEmpty()
                    val notif = NotificationHelper.createValidationConflictNotification(
                        treeId = treeId,
                        userId = currentUserId,
                        memberName = "${tempPerson.firstName} ${tempPerson.lastName}".trim().ifEmpty { "New Member" },
                        conflictDetails = "Prohibited Spouse: ${firstError.message}"
                    )
                    firestoreHelper.addNotification(notif)
                    AlertDialog.Builder(this)
                        .setTitle(firstError.title)
                        .setMessage(allErrors)
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setOnClickListener
                }
            }

            val linkResult = com.example.btproject2.engine.FamilyLinkValidator.validatePersonComprehensive(
                tempPerson,
                effectiveMembers,
                selectedChildren
            )

            if (!linkResult.isValid) {
                val firstError = linkResult.errors.first()
                val allErrors = linkResult.errors.joinToString("\n\n") { "❌ ${it.message}" }
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createValidationConflictNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    memberName = "${tempPerson.firstName} ${tempPerson.lastName}".trim().ifEmpty { "New Member" },
                    conflictDetails = "Genealogical Conflict: ${firstError.message}"
                )
                firestoreHelper.addNotification(notif)
                AlertDialog.Builder(this)
                    .setTitle(firstError.title)
                    .setMessage(allErrors)
                    .setPositiveButton("Understood", null)
                    .show()
                return@setOnClickListener
            }

            // 2. Legacy / local validation
            val result = validatePerson(tempPerson)

            if (!result.isValid) {
                val errorText = result.errors.joinToString("\n\n") { "❌ ${it.message}" }
                AlertDialog.Builder(this)
                    .setTitle("Cannot Save Member")
                    .setMessage(errorText)
                    .setPositiveButton("Understood", null)
                    .show()
                return@setOnClickListener
            }

            // Check for duplicate member (same name and birth date)
            val existingMatch = allMembers.firstOrNull {
                it.firstName.trim().equals(firstName, ignoreCase = true) &&
                it.lastName.trim().equals(lastName, ignoreCase = true) &&
                (it.birthDate.isBlank() && selectedDate.isBlank() || it.birthDate == selectedDate)
            }

            if (existingMatch != null) {
                showDuplicateCompareDialog(tempPerson, existingMatch)
                return@setOnClickListener
            }

            val combinedWarnings = (linkResult.warnings.map { it.message } + result.warnings.map { it.message }).distinct()
            if (combinedWarnings.isNotEmpty()) {
                val hasPosthumous = linkResult.warnings.any { it.title.contains("Posthumous", ignoreCase = true) }
                val warningText = combinedWarnings.joinToString("\n") { "⚠️ $it" }
                AlertDialog.Builder(this)
                    .setTitle("Warnings Found")
                    .setMessage("$warningText\n\nDo you still want to save?")
                    .setPositiveButton("Save Anyway") { _, _ ->
                        // 8.b Validation Alert (DFD D7)
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val conflictMsg = linkResult.warnings.firstOrNull()?.message ?: result.warnings.firstOrNull()?.message ?: "Biological relationship warning."
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${tempPerson.firstName} ${tempPerson.lastName}".trim(),
                            conflictDetails = conflictMsg
                        )
                        firestoreHelper.addNotification(notif)

                        val personToSave = if (hasPosthumous) tempPerson.copy(hasTimelineConflict = true) else tempPerson
                        startSaveProcess(personToSave)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@setOnClickListener
            }

            startSaveProcess(tempPerson)
        }
    }

    private fun getEffectiveTreeMembers(): List<Person> {
        val effectiveTree = treeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        val cachedForTree = if (effectiveTree.isNotBlank()) {
            FirestoreHelper.getCachedPersons(effectiveTree).orEmpty()
        } else emptyList()

        return (allMembers + cachedForTree)
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
    }

    private fun loadFamilyMembers() {
        val effectiveTreeId = treeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        treeId = effectiveTreeId
        syncCoordinator.treeId = effectiveTreeId
        val handlePersons: (List<Person>) -> Unit = { persons ->
            val treeFiltered = if (effectiveTreeId.isEmpty()) {
                emptyList()
            } else if (effectiveTreeId == "default_tree") {
                persons.filter { it.treeId.isEmpty() || it.treeId == "default_tree" }
            } else {
                persons.filter { it.treeId == effectiveTreeId }
            }
            allMembers = treeFiltered
            syncCoordinator.allMembersList = allMembers

                val presetChildId = intent.getStringExtra("presetChildId")
                val presetGender = intent.getStringExtra("presetGender")
                if (!presetGender.isNullOrEmpty()) {
                    if (presetGender.equals("Female", ignoreCase = true)) {
                        spinnerGender.setSelection(1)
                    } else {
                        spinnerGender.setSelection(0)
                    }
                }
                if (!presetChildId.isNullOrEmpty()) {
                    val child = allMembers.find { it.id == presetChildId }
                    if (child != null) {
                        val existingParents = com.example.btproject2.engine.FamilyLinkValidator.getExistingParentsInfo(child, allMembers)
                        val isMale = !presetGender.equals("Female", ignoreCase = true)
                        val hasSlotFilled = if (isMale) existingParents.father != null else existingParents.mother != null

                        if (existingParents.hasTwoParents || hasSlotFilled) {
                            val childName = "${child.firstName} ${child.lastName}".trim()
                            val msg = if (existingParents.hasTwoParents) {
                                "It’s not possible to add another parent to $childName.\n\n$childName already has parents defined in this family tree: ${existingParents.fatherName} and ${existingParents.motherName}.\n\nA person who already has two parents cannot be added as another member’s child."
                            } else {
                                val role = if (isMale) "father" else "mother"
                                val existingName = if (isMale) existingParents.fatherName else existingParents.motherName
                                "It’s not possible to add another $role to $childName.\n\n$childName already has a $role defined in this family tree: $existingName.\n\nA person cannot have more than one biological $role."
                            }

                            AlertDialog.Builder(this)
                                .setTitle("Cannot Add Parent")
                                .setMessage(msg)
                                .setPositiveButton("Understood") { _, _ -> finish() }
                                .setCancelable(false)
                                .show()
                        } else if (selectedChildren.none { it.id == child.id }) {
                            selectedChildren.add(child)
                            tvSelectedChildren.text = "${selectedChildren.size} child(ren) selected (${child.firstName})"
                            tvSelectedChildren.setTextColor(resources.getColor(R.color.white, null))
                            layoutParentContextBanner.visibility = View.VISIBLE
                            tvParentBannerText.text = "Adding Parent to ${child.firstName} ${child.lastName}"
                        }
                    }
                }

                refreshCandidateSpinners()
            }

        if (effectiveTreeId.isNotBlank()) {
            firestoreHelper.getPersonsByTree(
                effectiveTreeId,
                onSuccess = handlePersons,
                onFailure = {
                    firestoreHelper.getAllPersons(handlePersons) {
                        showMessage("Error", "Failed to load existing members.")
                    }
                }
            )
        } else {
            firestoreHelper.getAllPersons(handlePersons) {
                showMessage("Error", "Failed to load existing members.")
            }
        }
    }

    private fun refreshCandidateSpinners() {
        val tempPerson = Person(
            id = "",
            firstName = etFirstName.text?.toString()?.trim().orEmpty(),
            lastName = etLastName.text?.toString()?.trim().orEmpty(),
            gender = spinnerGender.selectedItem?.toString() ?: "",
            birthDate = selectedDate,
            maritalStatus = spinnerCivilStatus.selectedItem?.toString() ?: "Single",
            treeId = treeId,
            createdBy = authHelper.getCurrentUserId() ?: ""
        )

        val effectiveMembers = getEffectiveTreeMembers()
        val presetParent = presetParentId?.let { pid -> effectiveMembers.find { it.id == pid } }
        val presetIsFather = presetParent?.gender.equals("male", ignoreCase = true)
        val presetIsMother = presetParent?.gender.equals("female", ignoreCase = true)

        val currentFatherIdx = spinnerFather.selectedItemPosition
        val currentFather = if (presetIsFather) presetParent else if (currentFatherIdx > 0 && currentFatherIdx - 1 < maleMembers.size) maleMembers[currentFatherIdx - 1] else null

        val currentMotherIdx = spinnerMother.selectedItemPosition
        val currentMother = if (presetIsMother) presetParent else if (currentMotherIdx > 0 && currentMotherIdx - 1 < femaleMembers.size) femaleMembers[currentMotherIdx - 1] else null

        isUpdatingSpinners = true
        maleMembers = getEligibleFathers(tempPerson, effectiveMembers, currentMother)
        femaleMembers = getEligibleMothers(tempPerson, effectiveMembers, currentFather)

        // Rule 8: If adding spouse of an existing child (son-in-law / daughter-in-law),
        // parent-in-law can never be connected as biological parent of this person!
        if (isChildSpouseMode && !presetSpouseId.isNullOrEmpty()) {
            val child = effectiveMembers.find { it.id == presetSpouseId }
            val inLawIds = setOfNotNull(inLawParentId, child?.fatherId, child?.motherId)
            maleMembers = maleMembers.filter { it.id !in inLawIds }
            femaleMembers = femaleMembers.filter { it.id !in inLawIds }
        }

        val fatherNames = mutableListOf("-- None --")
        fatherNames.addAll(maleMembers.map { "${it.firstName} ${it.lastName}" })
        spinnerFather.setDarkAdapter(this, fatherNames)
        if (currentFather != null) {
            val newIdx = maleMembers.indexOfFirst { it.id == currentFather.id }
            if (newIdx >= 0) spinnerFather.setSelection(newIdx + 1)
        }

        val motherNames = mutableListOf("-- None --")
        motherNames.addAll(femaleMembers.map { "${it.firstName} ${it.lastName}" })
        spinnerMother.setDarkAdapter(this, motherNames)
        if (currentMother != null) {
            val newIdx = femaleMembers.indexOfFirst { it.id == currentMother.id }
            if (newIdx >= 0) spinnerMother.setSelection(newIdx + 1)
        }

        eligibleSpouses = getEligibleSpouses(tempPerson, effectiveMembers)
        val spouseNames = mutableListOf("-- None --")
        spouseNames.addAll(eligibleSpouses.map { "${it.firstName} ${it.lastName}" })
        spinnerSpouse.setDarkAdapter(this, spouseNames)

        // Trigger Identify Other Parent dialog if member has a spouse and choice hasn't been made yet
        if (presetParent != null && !presetParent.spouseId.isNullOrBlank() && coParentChoice == null && !hasPromptedOtherParentDialog) {
            hasPromptedOtherParentDialog = true
            com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.showIdentifyOtherParentDialog(
                context = this,
                parent = presetParent,
                allMembers = effectiveMembers,
                onResult = { result ->
                    coParentChoice = result.choice.name
                    presetOtherParentId = result.otherParent?.id
                    runOnUiThread {
                        refreshCandidateSpinners()
                    }
                }
            )
        }

        // Pre-select presetParentId and configure biological other parent
        presetParentId?.let { pid ->
            val presetParentObj = presetParent
            val otherParentObj = presetOtherParentId?.let { opid -> effectiveMembers.find { it.id == opid } }
            val spouseObj = presetParentObj?.spouseId?.let { sid -> effectiveMembers.find { it.id == sid } }

            if (presetParentObj != null) {
                layoutParentContextBanner.visibility = View.VISIBLE
                val role = if (presetIsMother) "Mother" else "Father"

                when (coParentChoice) {
                    "CURRENT_SPOUSE" -> {
                        val spouseName = spouseObj?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Spouse"
                        tvParentBannerText.text = "Adding Child of ${presetParentObj.firstName} and $spouseName (Both biological parents)"
                    }
                    "ANOTHER_PERSON" -> {
                        val otherName = otherParentObj?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Other Parent"
                        val stepNote = if (spouseObj != null) " (${spouseObj.firstName} is step-parent)" else ""
                        tvParentBannerText.text = "Adding Child of ${presetParentObj.firstName} and $otherName (Both biological parents)$stepNote"
                    }
                    "UNKNOWN" -> {
                        val stepNote = if (spouseObj != null) " (${spouseObj.firstName} is step-parent, not biological parent)" else ""
                        tvParentBannerText.text = "Adding Child to $role: ${presetParentObj.firstName} ${presetParentObj.lastName} (Other parent: Unknown or not recorded)$stepNote"
                    }
                    else -> {
                        tvParentBannerText.text = "Adding Child to $role: ${presetParentObj.firstName} ${presetParentObj.lastName}"
                    }
                }
            }

            if (presetIsFather) {
                val fatherIdx = maleMembers.indexOfFirst { it.id == pid }
                if (fatherIdx >= 0) spinnerFather.setSelection(fatherIdx + 1)
                spinnerFatherType.setSelection(0) // Biological

                if (coParentChoice == "CURRENT_SPOUSE" || coParentChoice == "ANOTHER_PERSON") {
                    val targetMotherId = presetOtherParentId
                    if (targetMotherId != null) {
                        val motherIdx = femaleMembers.indexOfFirst { it.id == targetMotherId }
                        if (motherIdx >= 0) {
                            spinnerMother.setSelection(motherIdx + 1)
                            spinnerMotherType.setSelection(0) // Biological
                        }
                    }
                } else if (coParentChoice == "UNKNOWN") {
                    spinnerMother.setSelection(0) // None
                }
            } else if (presetIsMother) {
                val motherIdx = femaleMembers.indexOfFirst { it.id == pid }
                if (motherIdx >= 0) spinnerMother.setSelection(motherIdx + 1)
                spinnerMotherType.setSelection(0) // Biological

                if (coParentChoice == "CURRENT_SPOUSE" || coParentChoice == "ANOTHER_PERSON") {
                    val targetFatherId = presetOtherParentId
                    if (targetFatherId != null) {
                        val fatherIdx = maleMembers.indexOfFirst { it.id == targetFatherId }
                        if (fatherIdx >= 0) {
                            spinnerFather.setSelection(fatherIdx + 1)
                            spinnerFatherType.setSelection(0) // Biological
                        }
                    }
                } else if (coParentChoice == "UNKNOWN") {
                    spinnerFather.setSelection(0) // None
                }
            }
        }

        // Pre-select presetSpouseId if provided (including child spouse mode)
        presetSpouseId?.let { sid ->
            val spouseIdx = eligibleSpouses.indexOfFirst { it.id == sid }
            if (spouseIdx >= 0) {
                spinnerSpouse.setSelection(spouseIdx + 1)
                val marriedIdx = (0 until spinnerCivilStatus.count).firstOrNull {
                    spinnerCivilStatus.getItemAtPosition(it).toString().equals("Married", ignoreCase = true)
                } ?: -1
                if (marriedIdx >= 0) spinnerCivilStatus.setSelection(marriedIdx)
            }
            if (isChildSpouseMode) {
                val child = effectiveMembers.find { it.id == sid }
                val childName = child?.let { "${it.firstName} ${it.lastName}" } ?: "existing child"
                val inLawParent = inLawParentId?.let { pid -> effectiveMembers.find { it.id == pid } }
                val inLawName = inLawParent?.let { "${it.firstName} ${it.lastName}" } ?: "Parent"
                layoutParentContextBanner.visibility = View.VISIBLE
                tvParentBannerText.text = "Adding Spouse of $childName (In-Law of $inLawName). Connected by affinity, never as biological child."
            }
        }
        isUpdatingSpinners = false

        setupCoParentSpinnerListeners(tempPerson)
    }

    private var isUpdatingSpinners = false

    private fun setupCoParentSpinnerListeners(tempPerson: Person) {
        spinnerFather.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingSpinners) return
                val effectiveMembers = getEffectiveTreeMembers()
                val selectedFather = if (position > 0 && position - 1 < maleMembers.size) maleMembers[position - 1] else null
                val currentMother = if (spinnerMother.selectedItemPosition > 0 && spinnerMother.selectedItemPosition - 1 < femaleMembers.size) femaleMembers[spinnerMother.selectedItemPosition - 1] else null

                isUpdatingSpinners = true
                femaleMembers = getEligibleMothers(tempPerson, effectiveMembers, selectedFather)
                val motherNames = mutableListOf("-- None --")
                motherNames.addAll(femaleMembers.map { "${it.firstName} ${it.lastName}" })
                spinnerMother.setDarkAdapter(this@AddMemberActivity, motherNames)
                if (currentMother != null) {
                    val newIdx = femaleMembers.indexOfFirst { it.id == currentMother.id }
                    if (newIdx >= 0) {
                        spinnerMother.setSelection(newIdx + 1)
                    } else {
                        spinnerMother.setSelection(0)
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${currentMother.firstName} ${currentMother.lastName}".trim(),
                            conflictDetails = "Co-parenting blocked: ${currentMother.firstName} removed as mother because co-parenting is prohibited with ${selectedFather?.firstName} under Family Code Arts. 37/38."
                        )
                        firestoreHelper.addNotification(notif)
                        Toast.makeText(this@AddMemberActivity, "${currentMother.firstName} removed as mother (co-parenting prohibited with ${selectedFather?.firstName})", Toast.LENGTH_SHORT).show()
                    }
                }
                isUpdatingSpinners = false
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerMother.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingSpinners) return
                val effectiveMembers = getEffectiveTreeMembers()
                val selectedMother = if (position > 0 && position - 1 < femaleMembers.size) femaleMembers[position - 1] else null
                val currentFather = if (spinnerFather.selectedItemPosition > 0 && spinnerFather.selectedItemPosition - 1 < maleMembers.size) maleMembers[spinnerFather.selectedItemPosition - 1] else null

                isUpdatingSpinners = true
                maleMembers = getEligibleFathers(tempPerson, effectiveMembers, selectedMother)
                val fatherNames = mutableListOf("-- None --")
                fatherNames.addAll(maleMembers.map { "${it.firstName} ${it.lastName}" })
                spinnerFather.setDarkAdapter(this@AddMemberActivity, fatherNames)
                if (currentFather != null) {
                    val newIdx = maleMembers.indexOfFirst { it.id == currentFather.id }
                    if (newIdx >= 0) {
                        spinnerFather.setSelection(newIdx + 1)
                    } else {
                        spinnerFather.setSelection(0)
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${currentFather.firstName} ${currentFather.lastName}".trim(),
                            conflictDetails = "Co-parenting blocked: ${currentFather.firstName} removed as father because co-parenting is prohibited with ${selectedMother?.firstName} under Family Code Arts. 37/38."
                        )
                        firestoreHelper.addNotification(notif)
                        Toast.makeText(this@AddMemberActivity, "${currentFather.firstName} removed as father (co-parenting prohibited with ${selectedMother?.firstName})", Toast.LENGTH_SHORT).show()
                    }
                }
                isUpdatingSpinners = false
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun showChildrenSelectionDialog() {
        val fatherIdx = spinnerFather.selectedItemPosition
        val motherIdx = spinnerMother.selectedItemPosition
        val fatherId = if (fatherIdx > 0 && fatherIdx - 1 < maleMembers.size) maleMembers[fatherIdx - 1].id else null
        val motherId = if (motherIdx > 0 && motherIdx - 1 < femaleMembers.size) femaleMembers[motherIdx - 1].id else null

        val excludeIds = setOfNotNull(presetParentId, fatherId, motherId)
        val tempParentId = "temp_parent_${System.currentTimeMillis()}"
        val tempParent = Person(
            id = tempParentId,
            gender = spinnerGender.selectedItem?.toString() ?: "",
            birthDate = selectedDate
        )
        val effectiveMembers = getEffectiveTreeMembers()
        // Allow all existing tree members to be visible and clickable (except parents of the proposed member)
        val candidates = effectiveMembers.filter { it.id !in excludeIds }

        if (candidates.isEmpty()) {
            Toast.makeText(this, "No candidates available to link as children", Toast.LENGTH_SHORT).show()
            return
        }

        val names = candidates.map { "${it.firstName} ${it.lastName}" }.toTypedArray()
        val checked = BooleanArray(candidates.size) { i ->
            selectedChildren.any { it.id == candidates[i].id }
        }

        var dialogRef: AlertDialog? = null
        dialogRef = AlertDialog.Builder(this)
            .setTitle("Link Children (Optional)")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) {
                    val candidate = candidates[which]
                    val parentCheck = validateCandidateAsChild(tempParent, candidate, effectiveMembers)
                    if (!parentCheck.isValid) {
                        checked[which] = false
                        dialogRef?.listView?.setItemChecked(which, false)

                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${candidate.firstName} ${candidate.lastName}".trim(),
                            conflictDetails = parentCheck.errorMessage
                        )
                        firestoreHelper.addNotification(notif)

                        AlertDialog.Builder(this@AddMemberActivity)
                            .setTitle(parentCheck.errorTitle)
                            .setMessage(parentCheck.errorMessage)
                            .setPositiveButton("Understood", null)
                            .show()
                    } else {
                        checked[which] = true
                    }
                } else {
                    checked[which] = false
                }
            }
            .setPositiveButton("Done") { _, _ ->
                selectedChildren.clear()
                candidates.forEachIndexed { index, person ->
                    if (checked[index]) {
                        val parentCheck = validateCandidateAsChild(tempParent, person, effectiveMembers)
                        if (parentCheck.isValid) {
                            selectedChildren.add(person)
                        }
                    }
                }
                updateSelectedChildrenText()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private data class CandidateChildCheckResult(
        val isValid: Boolean,
        val errorTitle: String = "",
        val errorMessage: String = ""
    )

    private fun validateCandidateAsChild(
        parent: Person,
        child: Person,
        allPersons: List<Person>
    ): CandidateChildCheckResult {
        val childName = "${child.firstName} ${child.lastName}".trim().ifEmpty { "The selected person" }
        val existingParents = com.example.btproject2.engine.FamilyLinkValidator.getExistingParentsInfo(child, allPersons)

        // 1. Two Parents Defined Check
        if (existingParents.hasTwoParents) {
            val fName = existingParents.fatherName ?: "Father"
            val mName = existingParents.motherName ?: "Mother"
            return CandidateChildCheckResult(
                isValid = false,
                errorTitle = "Cannot Add Child: Already Has Two Parents",
                errorMessage = "It’s not possible to add $childName as a child of the new member.\n\n" +
                        "$childName already has parents defined in this family tree: $fName and $mName.\n\n" +
                        "A person who already has two parents cannot be added as another member’s child."
            )
        }

        // 2. Parent Slot of Same Role Already Occupied
        val isFather = parent.gender.lowercase() == "male"
        if (isFather && existingParents.father != null) {
            val fName = existingParents.fatherName ?: "Father"
            return CandidateChildCheckResult(
                isValid = false,
                errorTitle = "Cannot Add Child: Father Already Defined",
                errorMessage = "It’s not possible to add $childName as a child of the new member.\n\n" +
                        "$childName already has a father defined in this family tree: $fName.\n\n" +
                        "A person cannot have more than one biological father."
            )
        }
        if (!isFather && existingParents.mother != null) {
            val mName = existingParents.motherName ?: "Mother"
            return CandidateChildCheckResult(
                isValid = false,
                errorTitle = "Cannot Add Child: Mother Already Defined",
                errorMessage = "It’s not possible to add $childName as a child of the new member.\n\n" +
                        "$childName already has a mother defined in this family tree: $mName.\n\n" +
                        "A person cannot have more than one biological mother."
            )
        }

        // 3. Comprehensive Parent-Child Checks (incest, generational leap, circular loop, age gaps)
        val role = if (isFather) "father" else "mother"
        val pcCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(parent, child, role, allPersons)
        if (!pcCheck.isValid) {
            val firstErr = pcCheck.errors.first()
            return CandidateChildCheckResult(
                isValid = false,
                errorTitle = firstErr.title,
                errorMessage = firstErr.message
            )
        }

        // 4. Co-Parent Compatibility with Child's Existing Other Parent
        val otherParent = if (isFather) existingParents.mother else existingParents.father
        if (otherParent != null) {
            val f = if (isFather) parent else otherParent
            val m = if (isFather) otherParent else parent
            val coCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(f, m, allPersons)
            if (!coCheck.isValid) {
                val firstErr = coCheck.errors.first()
                return CandidateChildCheckResult(
                    isValid = false,
                    errorTitle = firstErr.title,
                    errorMessage = firstErr.message
                )
            }
        }

        return CandidateChildCheckResult(isValid = true)
    }

    private fun updateSelectedChildrenText() {
        if (selectedChildren.isEmpty()) {
            tvSelectedChildren.text = "Tap to select children (optional)"
            tvSelectedChildren.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
        } else {
            tvSelectedChildren.text = selectedChildren.joinToString(", ") { "${it.firstName} ${it.lastName}" }
            tvSelectedChildren.setTextColor(Color.WHITE)
        }
    }

    private fun startSaveProcess(person: Person) {
        if (isSaving) return
        setSavingState(true)
        savePerson(person)
    }

    private fun setSavingState(saving: Boolean) {
        isSaving = saving
        btnSave.isEnabled = !saving
        btnSave.text = if (saving) "Saving..." else "Save Member"
    }

    private fun clearFormFields() {
        etFirstName.text?.clear()
        etLastName.text?.clear()
        etBirthDate.text?.clear()
        selectedDate = ""
        spinnerGender.setSelection(0)
        spinnerCivilStatus.setSelection(0)
        spinnerFatherType.setSelection(0)
        spinnerMotherType.setSelection(0)
        attachedPhotoUri = ""
        attachedPhotoBase64 = ""
        attachedDocumentsList.clear()
        tvAttachedPhotoStatus.text = "Attach Profile Photo (Optional)"
        tvAttachedPhotoStatus.setTextColor(resources.getColor(R.color.text_hint, null))
        tvAttachedDocStatus.text = "Attach Birth Certificate / PDF (Optional)"
        tvAttachedDocStatus.setTextColor(resources.getColor(R.color.text_hint, null))
        etFirstName.clearFocus()
        etLastName.clearFocus()
        etBirthDate.clearFocus()
    }

    private fun savePerson(person: Person) {
        firestoreHelper.addPerson(
            person,
            onSuccess = { savedId ->
                val savedPerson = person.copy(id = savedId)

                val currentUserId = authHelper.getCurrentUserId() ?: ""
                val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

                // Log Activity (D8)
                firestoreHelper.logActivity(
                    treeId = treeId,
                    userId = currentUserId,
                    userName = currentUserName,
                    type = "CREATE",
                    description = "Added family member: ${savedPerson.firstName} ${savedPerson.lastName}",
                    targetId = savedId,
                    targetName = "${savedPerson.firstName} ${savedPerson.lastName}"
                )

                // Add Notification (D7)
                firestoreHelper.addNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    title = "New Member Added",
                    message = "${savedPerson.firstName} ${savedPerson.lastName} was added to the family tree.",
                    type = "RECORDS",
                    targetId = savedId
                )

                val onComplete = {
                    setSavingState(false)
                    Toast.makeText(this, "Family member saved", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    if (!presetParentId.isNullOrEmpty()) {
                        finish()
                    } else {
                        selectedChildren.clear()
                        updateSelectedChildrenText()
                        clearFormFields()
                        loadFamilyMembers()
                    }
                }

                var pendingOps = 0
                if (!savedPerson.spouseId.isNullOrEmpty()) pendingOps++
                pendingOps += selectedChildren.size

                if (pendingOps == 0) {
                    onComplete()
                } else {
                    fun opDone() {
                        pendingOps--
                        if (pendingOps <= 0) onComplete()
                    }

                    if (!savedPerson.spouseId.isNullOrEmpty()) {
                        firestoreHelper.updateSpouse(
                            personId = savedPerson.id,
                            spouseId = savedPerson.spouseId,
                            maritalStatus = "Married",
                            onSuccess = { opDone() },
                            onFailure = { opDone() }
                        )
                    }

                    selectedChildren.forEach { child ->
                        firestoreHelper.setChildParent(
                            childId = child.id,
                            parentId = savedId,
                            parentGender = savedPerson.gender,
                            onSuccess = { opDone() },
                            onFailure = { opDone() }
                        )
                    }
                }
            },
            onFailure = { error ->
                setSavingState(false)
                showMessage("Error", "Failed to save: ${error.message}")
            }
        )
    }

    private fun validatePerson(person: Person): ValidationSummary {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        if (!person.fatherId.isNullOrEmpty() && person.fatherId == person.id) {
            errors.add(ValidationIssue("fatherId", "A person cannot be their own father."))
        }

        if (!person.motherId.isNullOrEmpty() && person.motherId == person.id) {
            errors.add(ValidationIssue("motherId", "A person cannot be their own mother."))
        }

        if (!person.spouseId.isNullOrEmpty() && person.spouseId == person.id) {
            errors.add(ValidationIssue("spouseId", "A person cannot be their own spouse."))
        }

        if (!person.fatherId.isNullOrEmpty() &&
            !person.motherId.isNullOrEmpty() &&
            person.fatherId == person.motherId
        ) {
            errors.add(
                ValidationIssue(
                    "parents",
                    "The same person cannot be assigned as both father and mother."
                )
            )
        }

        val father = allMembers.firstOrNull { it.id == person.fatherId }
        val mother = allMembers.firstOrNull { it.id == person.motherId }
        val spouse = allMembers.firstOrNull { it.id == person.spouseId }

        if (father != null) {
            if (wouldCreateCycle(person.id, father.id)) {
                errors.add(
                    ValidationIssue(
                        "fatherId",
                        "This father assignment would create a circular ancestry loop."
                    )
                )
            }

            val parentCheck = validateParentChild(father, person, "father")
            errors.addAll(parentCheck.errors)
            warnings.addAll(parentCheck.warnings)
        }

        if (mother != null) {
            if (wouldCreateCycle(person.id, mother.id)) {
                errors.add(
                    ValidationIssue(
                        "motherId",
                        "This mother assignment would create a circular ancestry loop."
                    )
                )
            }

            val parentCheck = validateParentChild(mother, person, "mother")
            errors.addAll(parentCheck.errors)
            warnings.addAll(parentCheck.warnings)
        }

        if (spouse != null) {
            if (isAncestorOf(spouse.id, person.id) || isAncestorOf(person.id, spouse.id)) {
                errors.add(
                    ValidationIssue(
                        "spouseId",
                        "A person cannot be assigned as spouse to their ancestor or descendant."
                    )
                )
            }

            if (!person.birthDate.isNullOrBlank() && !spouse.birthDate.isNullOrBlank()) {
                val ageAtMarriageWarningGap = kotlin.math.abs(yearDiff(person.birthDate, spouse.birthDate))
                if (ageAtMarriageWarningGap > 40) {
                    warnings.add(
                        ValidationIssue(
                            "spouseId",
                            "Large age gap detected between spouses."
                        )
                    )
                }
            }
        }

        if (person.maritalStatus.equals("Single", ignoreCase = true) && !person.spouseId.isNullOrEmpty()) {
            warnings.add(
                ValidationIssue(
                    "maritalStatus",
                    "This member is marked Single but has a spouse selected."
                )
            )
        }

        if (person.maritalStatus.equals("Married", ignoreCase = true) && person.spouseId.isNullOrEmpty()) {
            warnings.add(
                ValidationIssue(
                    "maritalStatus",
                    "This member is marked Married but no spouse is selected."
                )
            )
        }

        return ValidationSummary(errors = errors, warnings = warnings)
    }

    private fun validateParentChild(parent: Person, child: Person, parentLabel: String): ValidationSummary {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        if (parent.id == child.id) {
            errors.add(
                ValidationIssue(parentLabel, "A person cannot be their own $parentLabel.")
            )
            return ValidationSummary(errors, warnings)
        }

        if (parent.birthDate.isBlank() || child.birthDate.isBlank()) {
            return ValidationSummary(errors, warnings)
        }

        if (parent.birthDate >= child.birthDate) {
            errors.add(
                ValidationIssue(parentLabel, "The $parentLabel must be older than the child.")
            )
            return ValidationSummary(errors, warnings)
        }

        val parentAgeAtBirth = yearDiff(parent.birthDate, child.birthDate)

        if (parentAgeAtBirth < 12) {
            errors.add(
                ValidationIssue(
                    parentLabel,
                    "The $parentLabel would be only $parentAgeAtBirth years old at the child's birth, which is biologically impossible."
                )
            )
        } else if (parentAgeAtBirth < 16) {
            warnings.add(
                ValidationIssue(
                    parentLabel,
                    "The $parentLabel would be only $parentAgeAtBirth years old at the child's birth."
                )
            )
        }

        if (parent.gender.equals("Female", ignoreCase = true) && parentAgeAtBirth > 55) {
            warnings.add(
                ValidationIssue(
                    parentLabel,
                    "The mother would be $parentAgeAtBirth years old at childbirth, which is unusually high."
                )
            )
        }

        if (parent.gender.equals("Male", ignoreCase = true) && parentAgeAtBirth > 80) {
            warnings.add(
                ValidationIssue(
                    parentLabel,
                    "The father would be $parentAgeAtBirth years old at childbirth, which is unusually high."
                )
            )
        }

        return ValidationSummary(errors, warnings)
    }

    private fun getEligibleFathers(person: Person, members: List<Person>, existingMother: Person? = null): List<Person> {
        return com.example.btproject2.engine.FamilyLinkValidator.getEligibleFathers(person, existingMother, members)
    }

    private fun getEligibleMothers(person: Person, members: List<Person>, existingFather: Person? = null): List<Person> {
        return com.example.btproject2.engine.FamilyLinkValidator.getEligibleMothers(person, existingFather, members)
    }

    private fun getEligibleSpouses(person: Person, members: List<Person>): List<Person> {
        return com.example.btproject2.engine.FamilyLinkValidator.getEligibleSpouses(person, members)
    }

    private fun wouldCreateCycle(childId: String, proposedParentId: String): Boolean {
        if (childId.isBlank() || proposedParentId.isBlank()) return false
        return isAncestorOf(childId, proposedParentId)
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

    private fun yearDiff(earlierDate: String, laterDate: String): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val earlier = format.parse(earlierDate)
            val later = format.parse(laterDate)

            if (earlier == null || later == null) return 0

            val cal1 = Calendar.getInstance().apply { time = earlier }
            val cal2 = Calendar.getInstance().apply { time = later }

            var years = cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR)

            if (
                cal2.get(Calendar.MONTH) < cal1.get(Calendar.MONTH) ||
                (cal2.get(Calendar.MONTH) == cal1.get(Calendar.MONTH) &&
                        cal2.get(Calendar.DAY_OF_MONTH) < cal1.get(Calendar.DAY_OF_MONTH))
            ) {
                years--
            }

            years
        } catch (e: Exception) {
            0
        }
    }

    private fun showDuplicateCompareDialog(newPerson: Person, existing: Person) {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        val notif = NotificationHelper.createDuplicateAlertNotification(
            treeId = treeId,
            userId = currentUserId,
            memberName = "${newPerson.firstName} ${newPerson.lastName}".trim(),
            existingId = existing.id
        )
        firestoreHelper.addNotification(notif)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_duplicate_compare, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // New person details
        dialogView.findViewById<TextView>(R.id.tvNewName).text = "${newPerson.firstName} ${newPerson.lastName}".trim()
        dialogView.findViewById<TextView>(R.id.tvNewGenderCivil).text = "${newPerson.gender} · ${newPerson.maritalStatus}"
        dialogView.findViewById<TextView>(R.id.tvNewBirthDate).text = if (newPerson.birthDate.isNotBlank()) newPerson.birthDate else "Not specified"

        val newFather = allMembers.find { it.id == newPerson.fatherId }?.let { "${it.firstName} ${it.lastName}" }
        val newMother = allMembers.find { it.id == newPerson.motherId }?.let { "${it.firstName} ${it.lastName}" }
        val newParents = listOfNotNull(newFather, newMother).joinToString(", ").ifEmpty { "None" }
        dialogView.findViewById<TextView>(R.id.tvNewParents).text = newParents

        val newSpouse = allMembers.find { it.id == newPerson.spouseId }?.let { "${it.firstName} ${it.lastName}" } ?: "None"
        dialogView.findViewById<TextView>(R.id.tvNewSpouse).text = newSpouse

        // Existing person details
        dialogView.findViewById<TextView>(R.id.tvExistingName).text = "${existing.firstName} ${existing.lastName}".trim()
        dialogView.findViewById<TextView>(R.id.tvExistingGenderCivil).text = "${existing.gender} · ${existing.maritalStatus}"
        dialogView.findViewById<TextView>(R.id.tvExistingBirthDate).text = if (existing.birthDate.isNotBlank()) existing.birthDate else "Not specified"

        val exFather = allMembers.find { it.id == existing.fatherId }?.let { "${it.firstName} ${it.lastName}" }
        val exMother = allMembers.find { it.id == existing.motherId }?.let { "${it.firstName} ${it.lastName}" }
        val exParents = listOfNotNull(exFather, exMother).joinToString(", ").ifEmpty { "None" }
        dialogView.findViewById<TextView>(R.id.tvExistingParents).text = exParents

        val exSpouse = allMembers.find { it.id == existing.spouseId }?.let { "${it.firstName} ${it.lastName}" } ?: "None"
        dialogView.findViewById<TextView>(R.id.tvExistingSpouse).text = exSpouse

        // Action: Use Existing Record
        dialogView.findViewById<Button>(R.id.btnUseExisting).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, MemberDetailActivity::class.java).apply {
                putExtra("personId", existing.id)
            }
            startActivity(intent)
        }

        // Action: Add as New Member Anyway
        dialogView.findViewById<Button>(R.id.btnAddAnyway).setOnClickListener {
            dialog.dismiss()
            startSaveProcess(newPerson)
        }

        // Action: Cancel & Edit Form
        dialogView.findViewById<TextView>(R.id.btnCancelCompare).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMessage(title: String, message: String, onDismiss: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { d, _ ->
                d.dismiss()
                onDismiss?.invoke()
            }
            .show()
    }
}

private data class ValidationIssue(
    val field: String,
    val message: String
)

private data class ValidationSummary(
    val errors: List<ValidationIssue> = emptyList(),
    val warnings: List<ValidationIssue> = emptyList()
) {
    val isValid: Boolean
        get() = errors.isEmpty()

    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()
}