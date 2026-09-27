package com.example.btproject2.ui.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.engine.ConsistencyValidator
import com.example.btproject2.engine.LinealConsanguinityCalculator
import com.example.btproject2.engine.TimelineGenerator
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.utils.NotificationHelper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.example.btproject2.utils.ThemePreferences
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.ParentRole
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.sync.MemberDetailSyncCoordinator
import java.util.Calendar
import java.util.Locale

class MemberDetailActivity : AppCompatActivity() {

    private val authHelper      = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private val validator       = ConsistencyValidator()
    private val timelineGen     = TimelineGenerator()
    private val familyService   = FamilyRelationshipService()
    private var treeId          = ""

    private var isOwner: Boolean = true
    private var isViewer: Boolean = false
    private var treePrivacy = PrivacySettings()

    private var currentPerson: Person? = null
    private var allMembers: List<Person> = emptyList()
    private var personId: String = ""
    private val linealCalculator = LinealConsanguinityCalculator()

    private val editMemberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            loadAllAndDisplay()
        }
    }

    private val addChildLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            loadAllAndDisplay()
        }
    }

    private val detailPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && currentPerson != null) {
            val result = com.example.btproject2.utils.DocumentHelper.saveImageToInternal(this, uri, currentPerson!!.id)
            if (result != null) {
                val (filePath, base64) = result
                firestoreHelper.updatePersonPhoto(
                    currentPerson!!.id,
                    base64,
                    filePath,
                    onSuccess = {
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                        val pName = "${currentPerson!!.firstName} ${currentPerson!!.lastName}".trim()
                        firestoreHelper.logActivity(
                            treeId = treeId,
                            userId = currentUserId,
                            userName = currentUserName,
                            type = "PHOTO",
                            description = "Updated profile photo for $pName",
                            targetId = currentPerson!!.id,
                            targetName = pName
                        )
                        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        loadAllAndDisplay()
                    },
                    onFailure = {
                        Toast.makeText(this, "Failed to update photo: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private val detailDocPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && currentPerson != null) {
            val doc = com.example.btproject2.utils.DocumentHelper.saveDocumentToInternal(this, uri, currentPerson!!.id)
            if (doc != null) {
                firestoreHelper.attachDocument(
                    currentPerson!!.id,
                    doc,
                    onSuccess = {
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                        val pName = "${currentPerson!!.firstName} ${currentPerson!!.lastName}".trim()
                        firestoreHelper.logActivity(
                            treeId = treeId,
                            userId = currentUserId,
                            userName = currentUserName,
                            type = "DOCUMENT",
                            description = "Attached document '${doc.name}' to $pName",
                            targetId = currentPerson!!.id,
                            targetName = pName
                        )
                        Toast.makeText(this, "Document attached successfully", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        loadAllAndDisplay()
                    },
                    onFailure = {
                        Toast.makeText(this, "Failed to attach document: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private lateinit var btnAddChildDirect: TextView
    private lateinit var btnLinkChildExisting: TextView
    private lateinit var containerDegree1: LinearLayout
    private lateinit var containerDegree2: LinearLayout
    private lateinit var containerDegree3: LinearLayout
    private lateinit var containerDegree4: LinearLayout

    private lateinit var ivDetailPhoto: ImageView
    private lateinit var btnDetailChangePhoto: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvFullName: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvAgeBadge: TextView
    private lateinit var tvLivingBadge: TextView
    private lateinit var tvDetailVerificationBadge: TextView
    private lateinit var layoutDetailDocsContainer: LinearLayout
    private lateinit var tvDetailNoDocs: TextView
    private lateinit var btnDetailAttachDoc: TextView
    private lateinit var tvDetailBirthDate: TextView
    private lateinit var tvDetailBirthPlace: TextView
    private lateinit var tvDetailDeathDate: TextView
    private lateinit var tvDetailDeathPlace: TextView
    private lateinit var rowDetailDeathDate: LinearLayout
    private lateinit var rowDetailDeathPlace: LinearLayout
    private lateinit var divDetailDeathDate: View
    private lateinit var divDetailDeathPlace: View
    private lateinit var tvFatherName: TextView
    private lateinit var tvMotherName: TextView
    private lateinit var rowFather: LinearLayout
    private lateinit var rowMother: LinearLayout
    private lateinit var tvSpouseName: TextView
    private lateinit var tvChildrenNames: TextView
    private lateinit var tvMarriageDate: TextView
    private lateinit var tvBiography: TextView
    private lateinit var etBiography: EditText
    private lateinit var btnEdit: Button
    private lateinit var btnAutoGenerate: Button
    private lateinit var btnSaveBio: Button
    private lateinit var btnCancelBio: Button
    private lateinit var layoutTimeline: LinearLayout
    private lateinit var layoutBioView: LinearLayout
    private lateinit var layoutBioEdit: LinearLayout
    private lateinit var rowSpouse: LinearLayout
    private lateinit var rowChildren: LinearLayout
    private lateinit var rowMarriageDate: LinearLayout
    private lateinit var tvStepParentsName: TextView
    private lateinit var rowStepParents: View
    private lateinit var dividerStepParents: View
    private lateinit var tvSiblingsNames: TextView
    private lateinit var rowSiblings: View
    private lateinit var btnDeleteMember: Button
    private lateinit var btnTopDeleteMember: TextView
    private lateinit var btnTopEditMember: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_member_detail)

        personId = intent.getStringExtra("personId") ?: return
        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        ivDetailPhoto       = findViewById(R.id.ivDetailPhoto)
        btnDetailChangePhoto= findViewById(R.id.btnDetailChangePhoto)
        tvAvatar            = findViewById(R.id.tvAvatar)
        tvFullName          = findViewById(R.id.tvFullName)
        tvSubtitle          = findViewById(R.id.tvSubtitle)
        tvAgeBadge          = findViewById(R.id.tvAgeBadge)
        tvLivingBadge       = findViewById(R.id.tvLivingBadge)
        tvDetailVerificationBadge = findViewById(R.id.tvDetailVerificationBadge)
        layoutDetailDocsContainer = findViewById(R.id.layoutDetailDocsContainer)
        tvDetailNoDocs      = findViewById(R.id.tvDetailNoDocs)
        btnDetailAttachDoc  = findViewById(R.id.btnDetailAttachDoc)

        btnDetailChangePhoto.setOnClickListener {
            detailPhotoPickerLauncher.launch("image/*")
        }
        btnDetailAttachDoc.setOnClickListener {
            detailDocPickerLauncher.launch("*/*")
        }

        tvDetailBirthDate   = findViewById(R.id.tvDetailBirthDate)
        tvDetailBirthPlace  = findViewById(R.id.tvDetailBirthPlace)
        tvDetailDeathDate   = findViewById(R.id.tvDetailDeathDate)
        tvDetailDeathPlace  = findViewById(R.id.tvDetailDeathPlace)
        rowDetailDeathDate  = findViewById(R.id.rowDetailDeathDate)
        rowDetailDeathPlace = findViewById(R.id.rowDetailDeathPlace)
        divDetailDeathDate  = findViewById(R.id.divDetailDeathDate)
        divDetailDeathPlace = findViewById(R.id.divDetailDeathPlace)
        tvFatherName        = findViewById(R.id.tvFatherName)
        tvMotherName        = findViewById(R.id.tvMotherName)
        rowFather           = findViewById(R.id.rowFather)
        rowMother           = findViewById(R.id.rowMother)
        tvSpouseName        = findViewById(R.id.tvSpouseName)
        tvChildrenNames     = findViewById(R.id.tvChildrenNames)
        tvMarriageDate      = findViewById(R.id.tvMarriageDate)
        tvStepParentsName   = findViewById(R.id.tvStepParentsName)
        rowStepParents      = findViewById(R.id.rowStepParents)
        dividerStepParents  = findViewById(R.id.dividerStepParents)
        tvSiblingsNames     = findViewById(R.id.tvSiblingsNames)
        rowSiblings         = findViewById(R.id.rowSiblings)
        tvBiography         = findViewById(R.id.tvBiography)
        etBiography         = findViewById(R.id.etBiography)
        btnEdit             = findViewById(R.id.btnEdit)
        btnAutoGenerate     = findViewById(R.id.btnAutoGenerate)
        btnSaveBio          = findViewById(R.id.btnSaveBio)
        btnCancelBio        = findViewById(R.id.btnCancelBio)
        layoutTimeline      = findViewById(R.id.layoutTimeline)
        layoutBioView       = findViewById(R.id.layoutBioView)
        layoutBioEdit       = findViewById(R.id.layoutBioEdit)
        rowSpouse           = findViewById(R.id.rowSpouse)
        rowChildren         = findViewById(R.id.rowChildren)
        rowMarriageDate     = findViewById(R.id.rowMarriageDate)
        btnDeleteMember     = findViewById(R.id.btnDeleteMember)
        btnTopDeleteMember  = findViewById(R.id.btnTopDeleteMember)
        btnTopEditMember    = findViewById(R.id.btnTopEditMember)

        val isDark = ThemePreferences.isDarkTheme(this)
        val density = resources.displayMetrics.density
        (btnDeleteMember as? MaterialButton)?.let { mb ->
            if (isDark) {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2B1214"))
                mb.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                mb.strokeWidth = (1.5f * density).toInt()
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.parseColor("#EF4444"))
            } else {
                mb.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B45309"))
                mb.strokeWidth = 0
                mb.cornerRadius = (14 * density).toInt()
                mb.setTextColor(Color.WHITE)
            }
        }

        btnAddChildDirect   = findViewById(R.id.btnAddChildDirect)
        btnLinkChildExisting= findViewById(R.id.btnLinkChildExisting)
        containerDegree1    = findViewById(R.id.containerDegree1)
        containerDegree2    = findViewById(R.id.containerDegree2)
        containerDegree3    = findViewById(R.id.containerDegree3)
        containerDegree4    = findViewById(R.id.containerDegree4)

        btnAddChildDirect.setOnClickListener {
            promptAddChildChoices(personId)
        }

        btnLinkChildExisting.setOnClickListener {
            currentPerson?.let { showChildrenPicker(it) }
        }

        // ── Navigation ────────────────────────────────────────────
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        btnDeleteMember.setOnClickListener { confirmDeletePerson() }
        btnTopDeleteMember.setOnClickListener { confirmDeletePerson() }
        btnTopEditMember.setOnClickListener {
            val intent = Intent(this, EditMemberActivity::class.java).apply {
                putExtra("personId", personId)
                putExtra("treeId", treeId)
            }
            editMemberLauncher.launch(intent)
        }

        // ── Quick Action Shortcuts (Figure 11) ────────────────────
        findViewById<TextView>(R.id.btnQuickTrace)?.setOnClickListener {
            val intent = Intent(this, TraceActivity::class.java).apply {
                putExtra("preselectedPersonA", personId)
            }
            startActivity(intent)
        }
        findViewById<TextView>(R.id.btnQuickTreeView)?.setOnClickListener {
            val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                putExtra("focalPedigreePersonId", personId)
                putExtra("viewMode", "PEDIGREE")
            }
            startActivity(intent)
        }

        // ── Relationship row clicks ───────────────────────────────
        rowFather.setOnClickListener       { currentPerson?.let { showFatherPicker(it) } }
        rowMother.setOnClickListener       { currentPerson?.let { showMotherPicker(it) } }
        rowSpouse.setOnClickListener       { currentPerson?.let { showSpousePicker(it) } }
        rowChildren.setOnClickListener     { currentPerson?.let { showChildrenPicker(it) } }
        rowMarriageDate.setOnClickListener { currentPerson?.let { showMarriageDatePicker(it) } }

        // ── Biography: Edit ───────────────────────────────────────
        btnEdit.setOnClickListener {
            etBiography.setText(currentPerson?.biography ?: "")
            layoutBioView.visibility = View.GONE
            layoutBioEdit.visibility = View.VISIBLE
        }

        // ── Biography: Cancel ─────────────────────────────────────
        btnCancelBio.setOnClickListener {
            layoutBioEdit.visibility = View.GONE
            layoutBioView.visibility = View.VISIBLE
        }

        // ── Biography: Save ───────────────────────────────────────
        btnSaveBio.setOnClickListener {
            val bio = etBiography.text.toString().trim()
            firestoreHelper.saveBiography(personId, bio,
                onSuccess = {
                    currentPerson = currentPerson?.copy(biography = bio)
                    tvBiography.text = bio.ifEmpty { "No biography yet." }
                    layoutBioEdit.visibility = View.GONE
                    layoutBioView.visibility = View.VISIBLE
                    Toast.makeText(this, "Biography saved", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    Toast.makeText(this, "Failed to save biography", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ── Biography: Auto-generate ──────────────────────────────
        btnAutoGenerate.setOnClickListener {
            val p      = currentPerson ?: return@setOnClickListener
            val spouse = allMembers.find { it.id == p.spouseId }
            val father = allMembers.find { it.id == p.fatherId }
            val mother = allMembers.find { it.id == p.motherId }
            val kids   = allMembers.filter { it.fatherId == p.id || it.motherId == p.id }

            val bio = buildString {
                val birthYear = p.birthDate.take(4)
                if (birthYear.isNotEmpty()) {
                    append("${p.firstName} ${p.lastName} was born in $birthYear. ")
                } else {
                    append("${p.firstName} ${p.lastName} is a member of this family tree. ")
                }
                val parentNames = listOfNotNull(
                    father?.let { "${it.firstName} ${it.lastName}" },
                    mother?.let { "${it.firstName} ${it.lastName}" }
                )
                if (parentNames.isNotEmpty()) {
                    append("${p.firstName} is the child of ${parentNames.joinToString(" and ")}. ")
                }
                if (spouse != null) {
                    append("${p.firstName} is married to ${spouse.firstName} ${spouse.lastName}")
                    if (p.marriageDate.isNotEmpty()) append(" (married ${p.marriageDate.take(4)})")
                    append(". ")
                }
                if (kids.isNotEmpty()) {
                    val names = kids.joinToString(", ") { it.firstName }
                    append("${p.firstName} has ${kids.size} child${if (kids.size > 1) "ren" else ""}: $names. ")
                }
                append(if (p.isLiving) "${p.firstName} is currently living." else "${p.firstName} is deceased.")
            }

            etBiography.setText(bio)
            layoutBioView.visibility = View.GONE
            layoutBioEdit.visibility = View.VISIBLE
        }

        // ── Initial data load ─────────────────────────────────────
        loadAllAndDisplay()
        loadRoleAndPrivacy()
    }

    internal val syncCoordinator by lazy {
        MemberDetailSyncCoordinator(
            personId = personId,
            treeId = treeId,
            listenerKey = "MemberDetailActivity@${personId}_${System.identityHashCode(this)}",
            currentPerson = currentPerson,
            allMembersList = allMembers,
            onPersonUpdated = { updatedPerson, updatedAllMembers, _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    allMembers = updatedAllMembers
                    if (updatedPerson != null) {
                        currentPerson = updatedPerson
                        displayPerson(updatedPerson)
                    }
                }
            },
            onCurrentPersonDeleted = { deletedId ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, "This member was removed from the family tree", Toast.LENGTH_SHORT).show()
                    val data = Intent().apply {
                        putExtra("deletedPersonId", deletedId)
                    }
                    setResult(RESULT_OK, data)
                    finish()
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        syncCoordinator.register()
    }

    override fun onResume() {
        super.onResume()
        if (personId.isNotBlank()) {
            reloadAndDisplay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncCoordinator.unregister()
    }

    // ══════════════════════════════════════════════════════════════
    // DATA LOADING
    // ══════════════════════════════════════════════════════════════

    /**
     * Always load members of the current tree so relationship names can be resolved,
     * then find and display the current person from that list.
     */
    private fun loadAllAndDisplay() {
        val effectiveTreeId = treeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        val handleMembers: (List<Person>) -> Unit = { rawMembers ->
            val members = FirestoreHelper.sanitizeTreeRecords(rawMembers)
            val person = members.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) }
            if (person != null) {
                val resolvedTreeId = person.treeId.ifBlank { effectiveTreeId }
                allMembers = if (resolvedTreeId.isNotBlank()) {
                    if (resolvedTreeId == "default_tree") {
                        members.filter { it.treeId.isBlank() || it.treeId == "default_tree" }
                    } else {
                        members.filter { it.treeId == resolvedTreeId }
                    }
                } else members
                currentPerson = person
                syncCoordinator.currentPerson = person
                syncCoordinator.allMembersList = allMembers
                displayPerson(person)
            } else {
                fetchIndividualPerson()
            }
        }

        if (effectiveTreeId.isNotBlank()) {
            firestoreHelper.getPersonsByTree(
                treeId = effectiveTreeId,
                onSuccess = handleMembers,
                onFailure = { fetchIndividualPerson() }
            )
        } else {
            fetchIndividualPerson()
        }
    }

    private fun fetchIndividualPerson() {
        firestoreHelper.getPerson(personId,
            onSuccess = { p ->
                if (p != null) {
                    currentPerson = p
                    syncCoordinator.currentPerson = p
                    val targetTreeId = p.treeId.ifBlank {
                        com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
                    }
                    if (targetTreeId.isNotBlank()) {
                        treeId = targetTreeId
                        firestoreHelper.getPersonsByTree(
                            treeId = targetTreeId,
                            onSuccess = { treeMembers ->
                                allMembers = FirestoreHelper.sanitizeTreeRecords(treeMembers)
                                syncCoordinator.allMembersList = allMembers
                                displayPerson(p)
                            },
                            onFailure = { displayPerson(p) }
                        )
                    } else {
                        displayPerson(p)
                    }
                }
            },
            onFailure = { currentPerson?.let { displayPerson(it) } }
        )
    }

    /**
     * After any write operation, reload all members of this tree from Firestore so the UI
     * reflects the latest state for BOTH the current person AND their relatives.
     */
    private fun reloadAndDisplay() {
        val effectiveTreeId = treeId.ifBlank {
            currentPerson?.treeId.orEmpty().ifBlank {
                com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
            }
        }
        val handleMembers: (List<Person>) -> Unit = { rawMembers ->
            val members = FirestoreHelper.sanitizeTreeRecords(rawMembers)
            val updated = members.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) }
            if (updated != null) {
                val resolvedTreeId = updated.treeId.ifBlank { effectiveTreeId }
                allMembers = if (resolvedTreeId.isNotBlank()) {
                    if (resolvedTreeId == "default_tree") {
                        members.filter { it.treeId.isBlank() || it.treeId == "default_tree" }
                    } else {
                        members.filter { it.treeId == resolvedTreeId }
                    }
                } else members
                currentPerson = updated
                syncCoordinator.currentPerson = updated
                syncCoordinator.allMembersList = allMembers
                displayPerson(updated)
            }
        }

        if (effectiveTreeId.isNotBlank()) {
            firestoreHelper.getPersonsByTree(
                treeId = effectiveTreeId,
                onSuccess = handleMembers,
                onFailure = { currentPerson?.let { displayPerson(it) } }
            )
        } else {
            firestoreHelper.getPerson(personId,
                onSuccess = { p ->
                    if (p != null) {
                        currentPerson = p
                        syncCoordinator.currentPerson = p
                        displayPerson(p)
                    }
                },
                onFailure = { currentPerson?.let { displayPerson(it) } }
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DISPLAY — populates EVERY view on the screen
    // ══════════════════════════════════════════════════════════════

    private fun displayPerson(person: Person) {

        // ── Avatar initials & Profile Photo ───────────────────────
        val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}".uppercase()
        tvAvatar.text = initials
        val rawBmp = com.example.btproject2.utils.DocumentHelper.decodeBase64Bitmap(person.photoBase64)
        if (rawBmp != null) {
            val circ = com.example.btproject2.utils.DocumentHelper.getCircularBitmap(rawBmp, 112)
            ivDetailPhoto.setImageBitmap(circ)
            ivDetailPhoto.visibility = View.VISIBLE
            tvAvatar.visibility = View.GONE
            if (!person.isLiving) {
                val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                ivDetailPhoto.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            } else {
                ivDetailPhoto.colorFilter = null
            }
        } else {
            ivDetailPhoto.visibility = View.GONE
            tvAvatar.visibility = View.VISIBLE
        }

        val shouldHideBirth = !isOwner && treePrivacy.hideBirthDates
        val shouldHideNotes = !isOwner && treePrivacy.hideNotes
        val shouldHideDeceased = !isOwner && treePrivacy.hideDeceasedStatus

        // ── Header text ───────────────────────────────────────────
        val fullDisplayName = buildString {
            append(person.firstName)
            if (person.middleName.isNotBlank()) append(" ${person.middleName}")
            if (person.lastName.isNotBlank()) append(" ${person.lastName}")
            if (person.suffix.isNotBlank()) append(" ${person.suffix}")
        }.trim().ifEmpty { "${person.firstName} ${person.lastName}" }
        tvFullName.text    = fullDisplayName
        val currentUser = authHelper.getCurrentUser()
        val focalPerson = allMembers.find {
            val uName = (currentUser?.displayName ?: "").lowercase()
            val fName = "${it.firstName} ${it.lastName}".lowercase()
            uName.isNotEmpty() && (fName.contains(uName) || uName.contains(it.firstName.lowercase()))
        } ?: allMembers.firstOrNull()
        val kinshipSub = com.example.btproject2.engine.KinshipTitleHelper.resolveSubtitle(person, focalPerson, allMembers)

        // Mutual spouse resolution (checks both directions)
        val resolvedSpouse = allMembers.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.spouseId) }
            ?: allMembers.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, person.id) }
        val effectiveMaritalStatus = if (resolvedSpouse != null) {
            if (person.maritalStatus.isBlank() || person.maritalStatus.equals("Single", ignoreCase = true)) {
                "Married"
            } else {
                person.maritalStatus
            }
        } else {
            person.maritalStatus
        }

        tvSubtitle.text    = if (shouldHideBirth) "$kinshipSub · ${person.gender}" else "$kinshipSub · ${person.gender} · $effectiveMaritalStatus"

        if (shouldHideDeceased) {
            tvLivingBadge.visibility = View.GONE
        } else {
            tvLivingBadge.visibility = View.VISIBLE
            if (person.isLiving) {
                tvLivingBadge.text = "Living ✎"
                tvLivingBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
            } else {
                tvLivingBadge.text = "🕊️ Deceased ✎"
                tvLivingBadge.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            }
            tvLivingBadge.isClickable = true
            tvLivingBadge.isFocusable = true
            tvLivingBadge.setOnClickListener {
                showQuickVitalStatusDialog(person)
            }
        }

        val tvTimelineConflictBadge = findViewById<TextView?>(R.id.tvTimelineConflictBadge)
        if (person.hasTimelineConflict) {
            tvTimelineConflictBadge?.visibility = View.VISIBLE
            tvTimelineConflictBadge?.text = "⚠️ Timeline Conflict (> 10 mos after father's death)"
        } else {
            tvTimelineConflictBadge?.visibility = View.GONE
        }

        // ── Age badge ─────────────────────────────────────────────
        if (!shouldHideBirth) {
            val ageStr = com.example.btproject2.utils.DocumentHelper.calculateAge(person.birthDate, person.deathDate, person.isLiving)
            if (ageStr.isNotEmpty()) {
                tvAgeBadge.text = ageStr
                tvAgeBadge.visibility = View.VISIBLE
            } else {
                tvAgeBadge.visibility = View.GONE
            }
        } else {
            tvAgeBadge.visibility = View.GONE
        }

        // ── Attached Proof Documents ──────────────────────────────
        renderAttachedDocuments(person)

        // ── Personal Details ──────────────────────────────────────
        if (shouldHideBirth) {
            tvDetailBirthDate.text = "Hidden"
            tvDetailBirthPlace.text = "Hidden"
        } else {
            tvDetailBirthDate.text = person.birthDate.ifBlank { "—" }
            tvDetailBirthPlace.text = person.birthPlace.ifBlank { "—" }
        }

        if (!person.isLiving && !shouldHideDeceased) {
            rowDetailDeathDate.visibility = View.VISIBLE
            divDetailDeathDate.visibility = View.VISIBLE
            tvDetailDeathDate.text = person.deathDate.ifBlank { "—" }

            if (person.deathPlace.isNotBlank()) {
                rowDetailDeathPlace.visibility = View.VISIBLE
                divDetailDeathPlace.visibility = View.VISIBLE
                tvDetailDeathPlace.text = person.deathPlace
            } else {
                rowDetailDeathPlace.visibility = View.GONE
                divDetailDeathPlace.visibility = View.GONE
            }
        } else {
            rowDetailDeathDate.visibility = View.GONE
            divDetailDeathDate.visibility = View.GONE
            rowDetailDeathPlace.visibility = View.GONE
            divDetailDeathPlace.visibility = View.GONE
        }

        // ── Biography ─────────────────────────────────────────────
        tvBiography.text = if (shouldHideNotes) {
            "Hidden by tree privacy settings."
        } else {
            person.biography.ifEmpty { "No biography yet." }
        }

        // ── Parents ───────────────────────────────────────────────
        val father = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(person.fatherId, allMembers)
        val mother = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(person.motherId, allMembers)

        rowFather.visibility = View.VISIBLE
        if (father != null) {
            tvFatherName.text = "${father.firstName} ${father.lastName}"
        } else {
            tvFatherName.text = "Tap to assign"
        }

        rowMother.visibility = View.VISIBLE
        if (mother != null) {
            tvMotherName.text = "${mother.firstName} ${mother.lastName}"
        } else {
            tvMotherName.text = "Tap to assign"
        }

        if (father != null && mother != null) {
            val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(father, mother, allMembers)
            if (!coParentCheck.isValid) {
                tvFatherName.text = "${father.firstName} ${father.lastName} ⚠️ (Prohibited Link)"
                tvFatherName.setTextColor(Color.parseColor("#EF4444"))
                tvMotherName.text = "${mother.firstName} ${mother.lastName} ⚠️ (Prohibited Link)"
                tvMotherName.setTextColor(Color.parseColor("#EF4444"))
            } else {
                tvFatherName.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
                tvMotherName.setTextColor(ContextCompat.getColor(this, R.color.mint_text))
            }
        }

        // ── Spouse (Mutual Resolution & Self-Healing) ────────────
        val spouse = resolvedSpouse
        if (spouse != null) {
            tvSpouseName.text = "${spouse.firstName} ${spouse.lastName}"
            // Always show marriage date row when a spouse is set
            rowMarriageDate.visibility = View.VISIBLE
            val effectiveMarriageDate = person.marriageDate.ifEmpty { spouse.marriageDate }
            tvMarriageDate.text =
                if (effectiveMarriageDate.isNotEmpty()) effectiveMarriageDate
                else "Tap to set date"

            // Self-healing: if person's record in database was missing reciprocal spouseId or status, silently repair it
            if (person.spouseId.isNullOrEmpty() || !com.example.btproject2.engine.FamilyLinkValidator.isSameId(person.spouseId, spouse.id) || !person.maritalStatus.equals("Married", ignoreCase = true)) {
                firestoreHelper.updateSpouse(person.id, spouse.id, "Married", onSuccess = {}, onFailure = {})
            }
        } else {
            tvSpouseName.text          = "Tap to select"
            rowMarriageDate.visibility = View.GONE
        }

        // ── Children (live from Firestore) ────────────────────────
        loadAndDisplayChildren(person)

        // ── Step-Parents (Rule 4) ─────────────────────────────────
        val stepFamily = com.example.btproject2.engine.KinshipTitleHelper.getStepFamilyForPerson(person, allMembers)
        if (stepFamily.stepParents.isNotEmpty()) {
            rowStepParents.visibility = View.VISIBLE
            dividerStepParents.visibility = View.VISIBLE
            tvStepParentsName.text = stepFamily.stepParents.joinToString(", ") { sp ->
                val title = if (sp.gender.equals("Female", ignoreCase = true)) "Stepmother" else "Stepfather"
                "${sp.firstName} ${sp.lastName} ($title)"
            }
        } else {
            rowStepParents.visibility = View.GONE
            dividerStepParents.visibility = View.GONE
        }

        // ── Siblings Breakdown (Rules 1, 2, 3) ───────────────────
        val siblingGroup = com.example.btproject2.engine.KinshipTitleHelper.getSiblingsForPerson(person, allMembers)
        if (siblingGroup.hasAny) {
            val parts = mutableListOf<String>()
            if (siblingGroup.fullSiblings.isNotEmpty()) {
                parts.add("${siblingGroup.fullSiblings.size} Full")
            }
            if (siblingGroup.halfSiblings.isNotEmpty()) {
                parts.add("${siblingGroup.halfSiblings.size} Half")
            }
            if (siblingGroup.stepSiblings.isNotEmpty()) {
                parts.add("${siblingGroup.stepSiblings.size} Step")
            }
            if (siblingGroup.adoptiveSiblings.isNotEmpty()) {
                parts.add("${siblingGroup.adoptiveSiblings.size} Adoptive")
            }
            tvSiblingsNames.text = parts.joinToString(" · ")
        } else {
            tvSiblingsNames.text = "None"
        }

        rowSiblings.setOnClickListener {
            showSiblingsBreakdownDialog(person, siblingGroup)
        }

        // ── Timeline ──────────────────────────────────────────────
        buildTimeline(person)

        // ── Lineal Consanguinity ──────────────────────────────────
        displayLinealConsanguinity(person)
    }

    private fun showSiblingsBreakdownDialog(
        person: Person,
        siblingGroup: com.example.btproject2.engine.KinshipTitleHelper.SiblingGroup
    ) {
        val message = buildString {
            if (!siblingGroup.hasAny) {
                append("No siblings or step-siblings found for ${person.firstName} in this family tree.")
                return@buildString
            }
            if (siblingGroup.fullSiblings.isNotEmpty()) {
                append("🟢 Full Siblings (Share both biological parents):\n")
                siblingGroup.fullSiblings.forEach {
                    append("  • ${it.firstName} ${it.lastName} (${it.gender})\n")
                }
                append("\n")
            }
            if (siblingGroup.halfSiblings.isNotEmpty()) {
                append("🟡 Half-Siblings (Share one biological parent):\n")
                siblingGroup.halfSiblings.forEach {
                    val sharedParentNames = listOfNotNull(
                        if (it.fatherId == person.fatherId && !it.fatherId.isNullOrBlank()) "Father" else null,
                        if (it.motherId == person.motherId && !it.motherId.isNullOrBlank()) "Mother" else null
                    ).joinToString(" & ")
                    append("  • ${it.firstName} ${it.lastName} (${it.gender}) — Shares $sharedParentNames\n")
                }
                append("\n")
            }
            if (siblingGroup.stepSiblings.isNotEmpty()) {
                append("🟣 Step-Siblings (Parents married, share no biological parent):\n")
                siblingGroup.stepSiblings.forEach {
                    append("  • ${it.firstName} ${it.lastName} (${it.gender})\n")
                }
                append("\n")
            }
            if (siblingGroup.adoptiveSiblings.isNotEmpty()) {
                append("🔵 Adoptive Siblings:\n")
                siblingGroup.adoptiveSiblings.forEach {
                    append("  • ${it.firstName} ${it.lastName} (${it.gender})\n")
                }
            }
        }.trim()

        AlertDialog.Builder(this)
            .setTitle("${person.firstName}'s Siblings")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════
    // CHILDREN DISPLAY
    // ══════════════════════════════════════════════════════════════

    private fun loadAndDisplayChildren(person: Person) {
        val actualKids = allMembers.filter {
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, person.id) ||
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, person.id)
        }.distinctBy { it.id.trim().lowercase() }
        tvChildrenNames.text =
            if (actualKids.isEmpty()) "Tap to add"
            else actualKids.joinToString(", ") { "${it.firstName} ${it.lastName}" }
    }

    private fun renderAttachedDocuments(person: Person) {
        layoutDetailDocsContainer.removeAllViews()
        val docs = person.documents

        if (docs.isEmpty()) {
            tvDetailNoDocs.visibility = View.VISIBLE
            tvDetailVerificationBadge.text = "⚠️ Unverified"
            tvDetailVerificationBadge.setTextColor(Color.parseColor("#BA7517"))
            tvDetailVerificationBadge.setBackgroundResource(R.drawable.badge_pill_dark)
        } else {
            tvDetailNoDocs.visibility = View.GONE
            tvDetailVerificationBadge.text = "✓ Verified (${docs.size} Docs)"
            tvDetailVerificationBadge.setTextColor(Color.parseColor("#10B981"))
            tvDetailVerificationBadge.setBackgroundResource(R.drawable.badge_pill_dark)

            docs.forEach { doc ->
                val docView = layoutInflater.inflate(R.layout.item_attached_document, layoutDetailDocsContainer, false)
                val tvName = docView.findViewById<TextView>(R.id.tvDocName)
                val tvCat = docView.findViewById<TextView>(R.id.tvDocCategory)
                val tvMeta = docView.findViewById<TextView>(R.id.tvDocMeta)
                val btnView = docView.findViewById<TextView>(R.id.btnViewDoc)
                val btnDelete = docView.findViewById<TextView>(R.id.btnDeleteDoc)

                tvName.text = doc.name
                tvCat.text = doc.category
                tvMeta.text = doc.fileSize.ifEmpty { "Attached" }

                btnView.setOnClickListener {
                    com.example.btproject2.utils.DocumentHelper.openDocument(this, doc)
                }

                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Remove Document")
                        .setMessage("Remove \"${doc.name}\" from ${person.firstName}'s profile?")
                        .setPositiveButton("Remove") { _, _ ->
                            firestoreHelper.removeDocument(person.id, doc.id,
                                onSuccess = {
                                    Toast.makeText(this, "Document removed", Toast.LENGTH_SHORT).show()
                                    setResult(RESULT_OK)
                                    loadAllAndDisplay()
                                },
                                onFailure = {
                                    Toast.makeText(this, "Failed to remove: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

                layoutDetailDocsContainer.addView(docView)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FATHER PICKER
    // ══════════════════════════════════════════════════════════════

    private fun showFatherPicker(person: Person) {
        val currentMother = allMembers.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.motherId) }
        val candidates = com.example.btproject2.engine.FamilyLinkValidator.getEligibleFathers(person, currentMother, allMembers)

        if (candidates.isEmpty() && person.fatherId == null) {
            Toast.makeText(this, "No eligible father candidates found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = mutableListOf("(None — remove father)")
        names.addAll(candidates.map { "${it.firstName} ${it.lastName}" })

        AlertDialog.Builder(this)
            .setTitle("Select Father")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    firestoreHelper.updateFather(person.id, null,
                        onSuccess = {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                            firestoreHelper.logActivity(
                                treeId = treeId,
                                userId = currentUserId,
                                userName = currentUserName,
                                type = "LINK",
                                description = "Removed father link for ${person.firstName} ${person.lastName}",
                                targetId = person.id,
                                targetName = "${person.firstName} ${person.lastName}"
                            )
                            Toast.makeText(this, "Father removed", Toast.LENGTH_SHORT).show()
                            reloadAndDisplay()
                        },
                        onFailure = {
                            Toast.makeText(this, "Failed to remove father", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    val selected = candidates[which - 1]
                    val treeMap = allMembers.associateBy { it.id }.toMutableMap()
                    treeMap[person.id] = person
                    val serviceCheck = familyService.checkProposedRelationship(
                        type = ProposedRelationshipType.BIOLOGICAL_PARENT,
                        primaryPersonId = person.id,
                        secondaryPersonId = selected.id,
                        tree = treeMap,
                        role = ParentRole.FATHER
                    )
                    if (!serviceCheck.isAllowed) {
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${person.firstName} ${person.lastName}".trim(),
                            conflictDetails = "Father link blocked: ${serviceCheck.reason}"
                        )
                        firestoreHelper.addNotification(notif)
                        AlertDialog.Builder(this)
                            .setTitle("Cannot Link Father")
                            .setMessage("${serviceCheck.reason}\n\n${serviceCheck.legalBasis}")
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }

                    val parentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(selected, person, "father", allMembers)
                    val existingMother = person.motherId?.trim()?.let { mId ->
                        allMembers.find { it.id.trim().equals(mId, ignoreCase = true) }
                    }
                    val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(selected, existingMother, allMembers)

                    if (!parentCheck.isValid || !coParentCheck.isValid) {
                        val firstIssue = parentCheck.errors.firstOrNull() ?: coParentCheck.errors.first()
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${person.firstName} ${person.lastName}".trim(),
                            conflictDetails = "Father link blocked: ${firstIssue.message}"
                        )
                        firestoreHelper.addNotification(notif)
                        AlertDialog.Builder(this)
                            .setTitle(firstIssue.title)
                            .setMessage(firstIssue.message)
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }

                    firestoreHelper.updateFather(person.id, selected.id,
                        onSuccess = {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                            firestoreHelper.logActivity(
                                treeId = treeId,
                                userId = currentUserId,
                                userName = currentUserName,
                                type = "LINK",
                                description = "Linked ${selected.firstName} ${selected.lastName} as father of ${person.firstName} ${person.lastName}",
                                targetId = person.id,
                                targetName = "${person.firstName} ${person.lastName}"
                            )
                            Toast.makeText(
                                this,
                                "Father linked: ${selected.firstName} ${selected.lastName}",
                                Toast.LENGTH_SHORT
                            ).show()
                            reloadAndDisplay()
                        },
                        onFailure = {
                            Toast.makeText(this, "Failed to link father", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════
    // MOTHER PICKER
    // ══════════════════════════════════════════════════════════════

    private fun showMotherPicker(person: Person) {
        val currentFather = allMembers.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.fatherId) }
        val candidates = com.example.btproject2.engine.FamilyLinkValidator.getEligibleMothers(person, currentFather, allMembers)

        if (candidates.isEmpty() && person.motherId == null) {
            Toast.makeText(this, "No eligible mother candidates found", Toast.LENGTH_SHORT).show()
            return
        }

        val names = mutableListOf("(None — remove mother)")
        names.addAll(candidates.map { "${it.firstName} ${it.lastName}" })

        AlertDialog.Builder(this)
            .setTitle("Select Mother")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    firestoreHelper.updateMother(person.id, null,
                        onSuccess = {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                            firestoreHelper.logActivity(
                                treeId = treeId,
                                userId = currentUserId,
                                userName = currentUserName,
                                type = "LINK",
                                description = "Removed mother link for ${person.firstName} ${person.lastName}",
                                targetId = person.id,
                                targetName = "${person.firstName} ${person.lastName}"
                            )
                            Toast.makeText(this, "Mother removed", Toast.LENGTH_SHORT).show()
                            reloadAndDisplay()
                        },
                        onFailure = {
                            Toast.makeText(this, "Failed to remove mother", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    val selected = candidates[which - 1]
                    val treeMap = allMembers.associateBy { it.id }.toMutableMap()
                    treeMap[person.id] = person
                    val serviceCheck = familyService.checkProposedRelationship(
                        type = ProposedRelationshipType.BIOLOGICAL_PARENT,
                        primaryPersonId = person.id,
                        secondaryPersonId = selected.id,
                        tree = treeMap,
                        role = ParentRole.MOTHER
                    )
                    if (!serviceCheck.isAllowed) {
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${person.firstName} ${person.lastName}".trim(),
                            conflictDetails = "Mother link blocked: ${serviceCheck.reason}"
                        )
                        firestoreHelper.addNotification(notif)
                        AlertDialog.Builder(this)
                            .setTitle("Cannot Link Mother")
                            .setMessage("${serviceCheck.reason}\n\n${serviceCheck.legalBasis}")
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }

                    val parentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(selected, person, "mother", allMembers)
                    val existingFather = person.fatherId?.trim()?.let { fId ->
                        allMembers.find { it.id.trim().equals(fId, ignoreCase = true) }
                    }
                    val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(existingFather, selected, allMembers)

                    if (!parentCheck.isValid || !coParentCheck.isValid) {
                        val firstIssue = parentCheck.errors.firstOrNull() ?: coParentCheck.errors.first()
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${person.firstName} ${person.lastName}".trim(),
                            conflictDetails = "Mother link blocked: ${firstIssue.message}"
                        )
                        firestoreHelper.addNotification(notif)
                        AlertDialog.Builder(this)
                            .setTitle(firstIssue.title)
                            .setMessage(firstIssue.message)
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }

                    firestoreHelper.updateMother(person.id, selected.id,
                        onSuccess = {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                            firestoreHelper.logActivity(
                                treeId = treeId,
                                userId = currentUserId,
                                userName = currentUserName,
                                type = "LINK",
                                description = "Linked ${selected.firstName} ${selected.lastName} as mother of ${person.firstName} ${person.lastName}",
                                targetId = person.id,
                                targetName = "${person.firstName} ${person.lastName}"
                            )
                            Toast.makeText(
                                this,
                                "Mother linked: ${selected.firstName} ${selected.lastName}",
                                Toast.LENGTH_SHORT
                            ).show()
                            reloadAndDisplay()
                        },
                        onFailure = {
                            Toast.makeText(this, "Failed to link mother", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════
    // SPOUSE PICKER
    // Bug fix: list was empty because allMembers was stale / filter too strict.
    // After selecting, we do a full reload so BOTH profiles update on screen.
    // ══════════════════════════════════════════════════════════════

    private fun showSpousePicker(person: Person) {
        val candidates = allMembers.filter { !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.id) }

        if (candidates.isEmpty() && person.spouseId == null) {
            Toast.makeText(this, "No other family members found in this tree", Toast.LENGTH_SHORT).show()
            return
        }

        val names = mutableListOf("(None — remove spouse)")
        names.addAll(candidates.map { candidate ->
            val genderTag = if (candidate.gender.isNotBlank()) " (${candidate.gender})" else ""
            val ageVal = com.example.btproject2.engine.MarriageValidationEngine.calculateAge(candidate)
            val ageTag = if (ageVal != null) ", age $ageVal" else ""
            "${candidate.firstName} ${candidate.lastName}$genderTag$ageTag"
        })

        AlertDialog.Builder(this)
            .setTitle("Select Spouse for ${person.firstName} ${person.lastName}")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    // ── Remove spouse (Atomic mutual removal) ──────
                    firestoreHelper.updateSpouse(person.id, null, "Single",
                        onSuccess = {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                            firestoreHelper.logActivity(
                                treeId = treeId,
                                userId = currentUserId,
                                userName = currentUserName,
                                type = "LINK",
                                description = "Removed spouse link for ${person.firstName} ${person.lastName}",
                                targetId = person.id,
                                targetName = "${person.firstName} ${person.lastName}"
                            )
                            Toast.makeText(this, "Spouse removed for both profiles", Toast.LENGTH_SHORT).show()
                            reloadAndDisplay()
                        },
                        onFailure = {
                            Toast.makeText(this, "Failed to remove spouse", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    // ── Set spouse ────────────────────────────────
                    val selected = candidates[which - 1]
                    val treeMap = allMembers.associateBy { it.id }.toMutableMap()
                    treeMap[person.id] = person
                    treeMap[selected.id] = selected

                    // 1-7. Central Service Validation (Age, Gender, Active Spouse, Consanguinity, Affinity)
                    val serviceCheck = familyService.checkProposedRelationship(
                        type = ProposedRelationshipType.SPOUSE,
                        primaryPersonId = person.id,
                        secondaryPersonId = selected.id,
                        tree = treeMap
                    )

                    // 8. If any validation fails, do not save and show clear user-friendly error
                    if (!serviceCheck.isAllowed) {
                        val formattedError = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(person, selected, serviceCheck)
                        val primaryMessage = com.example.btproject2.utils.SpouseValidationMessageHelper.getPrimaryErrorMessage(person, selected, serviceCheck)

                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${person.firstName} ${person.lastName}".trim(),
                            conflictDetails = "Spouse link blocked: $primaryMessage"
                        )
                        firestoreHelper.addNotification(notif)

                        AlertDialog.Builder(this)
                            .setTitle("Cannot Link Spouse")
                            .setMessage(formattedError)
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }

                    val spouseCheck = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(person, selected, allMembers)
                    if (!spouseCheck.isValid) {
                        val firstIssue = spouseCheck.errors.first()
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${person.firstName} ${person.lastName}".trim(),
                            conflictDetails = "Spouse link blocked: ${firstIssue.message}"
                        )
                        firestoreHelper.addNotification(notif)
                        AlertDialog.Builder(this)
                            .setTitle(firstIssue.title)
                            .setMessage(firstIssue.message)
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }

                    // FirestoreHelper.updateSpouse already writes the reciprocal
                    // (sets selected.spouseId = person.id) inside its own success callback.
                    firestoreHelper.updateSpouse(person.id, selected.id, "Married",
                        onSuccess = {
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                            firestoreHelper.logActivity(
                                treeId = treeId,
                                userId = currentUserId,
                                userName = currentUserName,
                                type = "LINK",
                                description = "Linked ${person.firstName} ${person.lastName} and ${selected.firstName} ${selected.lastName} as spouses",
                                targetId = person.id,
                                targetName = "${person.firstName} ${person.lastName}"
                            )
                            Toast.makeText(
                                this,
                                "Spouse set: ${selected.firstName} ${selected.lastName}",
                                Toast.LENGTH_SHORT
                            ).show()
                            // After setting spouse, prompt immediately to enter marriage date
                            reloadAndDisplay()
                            promptMarriageDate(person.id, selected.id)
                        },
                        onFailure = {
                            Toast.makeText(this, "Failed to set spouse", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════
    // MARRIAGE DATE PICKER
    // Bug fix: now updates BOTH the person AND the spouse in Firestore,
    // then reloads so both profiles reflect the change immediately.
    // ══════════════════════════════════════════════════════════════

    private fun showMarriageDatePicker(person: Person) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val dateStr = "%04d-%02d-%02d".format(year, month + 1, day)
            saveMarriageDateForBoth(person.id, person.spouseId, dateStr)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    /**
     * Called right after a new spouse is picked — opens the date picker immediately
     * so the user can set the marriage date in one smooth flow.
     */
    private fun promptMarriageDate(personId: String, spouseId: String) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val dateStr = "%04d-%02d-%02d".format(year, month + 1, day)
            saveMarriageDateForBoth(personId, spouseId, dateStr)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            setTitle("Set Marriage Date (optional)")
            // Allow dismissing without a date — just cancel
            setButton(
                DatePickerDialog.BUTTON_NEGATIVE, "Skip"
            ) { dialog, _ -> dialog.dismiss() }
        }.show()
    }

    /**
     * Writes the marriage date to BOTH the person and the spouse document,
     * then reloads the UI so both profiles show the updated date.
     */
    private fun saveMarriageDateForBoth(pid: String, spouseId: String?, dateStr: String) {
        val p = currentPerson
        val pName = if (p != null) "${p.firstName} ${p.lastName}".trim() else "member"
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

        firestoreHelper.updateMarriageDate(pid, dateStr,
            onSuccess = {
                firestoreHelper.logActivity(
                    treeId = treeId,
                    userId = currentUserId,
                    userName = currentUserName,
                    type = "UPDATE",
                    description = "Updated marriage date ($dateStr) for $pName",
                    targetId = pid,
                    targetName = pName
                )
                if (spouseId != null) {
                    // Update the spouse's record too
                    firestoreHelper.updateMarriageDate(spouseId, dateStr,
                        onSuccess = { reloadAndDisplay() },
                        onFailure = { reloadAndDisplay() }  // still reload even on spouse fail
                    )
                } else {
                    reloadAndDisplay()
                }
                Toast.makeText(this, "Marriage date saved for both profiles", Toast.LENGTH_SHORT).show()
            },
            onFailure = {
                Toast.makeText(this, "Failed to save marriage date", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ══════════════════════════════════════════════════════════════
    // CHILDREN PICKER
    // Bug fix: after adding, calls reloadAndDisplay() so the children
    // row in the profile updates immediately without restarting the screen.
    // ══════════════════════════════════════════════════════════════

    private fun showChildrenPicker(person: Person) {
        // Find existing children
        val currentKids = allMembers.filter { 
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, person.id) || 
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, person.id) 
        }
        val currentKidIds = currentKids.map { it.id }.toSet()

        // Find eligible additional children
        val eligibleToAdd = com.example.btproject2.engine.FamilyLinkValidator.getEligibleChildren(person, allMembers)
            .filter { it.id !in currentKidIds }

        val allOptions = (currentKids + eligibleToAdd).sortedBy { "${it.firstName} ${it.lastName}" }

        if (allOptions.isEmpty()) {
            Toast.makeText(this, "No eligible children found to link", Toast.LENGTH_SHORT).show()
            return
        }

        val names = allOptions.map { kid ->
            val isCurrent = kid.id in currentKidIds
            val marker = if (isCurrent) "✓ (Linked) " else "+ (Add) "
            "$marker${kid.firstName} ${kid.lastName} (${kid.gender})"
        }.toTypedArray()

        val checked = BooleanArray(allOptions.size) { i ->
            allOptions[i].id in currentKidIds
        }

        var dialogRef: AlertDialog? = null
        val builder = AlertDialog.Builder(this)
            .setTitle("Manage Children (${person.firstName})")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) {
                    val candidate = allOptions[which]
                    if (candidate.id !in currentKidIds) {
                        val treeMap = allMembers.associateBy { it.id }.toMutableMap()
                        treeMap[person.id] = person
                        val cCheck = familyService.checkProposedRelationship(
                            type = ProposedRelationshipType.BIOLOGICAL_CHILD,
                            primaryPersonId = person.id,
                            secondaryPersonId = candidate.id,
                            tree = treeMap
                        )
                        if (!cCheck.isAllowed) {
                            checked[which] = false
                            dialogRef?.listView?.setItemChecked(which, false)
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val notif = NotificationHelper.createValidationConflictNotification(
                                treeId = treeId,
                                userId = currentUserId,
                                memberName = "${candidate.firstName} ${candidate.lastName}".trim(),
                                conflictDetails = cCheck.reason
                            )
                            firestoreHelper.addNotification(notif)
                            AlertDialog.Builder(this)
                                .setTitle("Cannot Link Child")
                                .setMessage(cCheck.reason)
                                .setPositiveButton("Understood", null)
                                .show()
                            return@setMultiChoiceItems
                        }

                        val role = if (person.gender.lowercase() == "female") "mother" else "father"
                        val parentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(person, candidate, role, allMembers)
                        val existingOtherParentId = (if (person.gender.lowercase() == "female") candidate.fatherId else candidate.motherId)?.trim()
                        val existingOtherParent = if (!existingOtherParentId.isNullOrEmpty()) {
                            allMembers.find { it.id.trim().equals(existingOtherParentId, ignoreCase = true) }
                        } else null
                        val f = if (person.gender.lowercase() == "female") existingOtherParent else person
                        val m = if (person.gender.lowercase() == "female") person else existingOtherParent
                        val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(f, m, allMembers)

                        if (!parentCheck.isValid || !coParentCheck.isValid) {
                            checked[which] = false
                            dialogRef?.listView?.setItemChecked(which, false)
                            val firstIssue = parentCheck.errors.firstOrNull() ?: coParentCheck.errors.first()
                            val currentUserId = authHelper.getCurrentUserId().orEmpty()
                            val notif = NotificationHelper.createValidationConflictNotification(
                                treeId = treeId,
                                userId = currentUserId,
                                memberName = "${candidate.firstName} ${candidate.lastName}".trim(),
                                conflictDetails = firstIssue.message
                            )
                            firestoreHelper.addNotification(notif)
                            AlertDialog.Builder(this)
                                .setTitle(firstIssue.title)
                                .setMessage(firstIssue.message)
                                .setPositiveButton("Understood", null)
                                .show()
                            return@setMultiChoiceItems
                        }
                    }
                    checked[which] = true
                } else {
                    checked[which] = false
                }
            }
            .setPositiveButton("Save Changes") { _, _ ->
                val toAdd = allOptions.filterIndexed { i, opt -> checked[i] && opt.id !in currentKidIds }
                val toRemove = allOptions.filterIndexed { i, opt -> !checked[i] && opt.id in currentKidIds }

                if (toAdd.isEmpty() && toRemove.isEmpty()) {
                    return@setPositiveButton
                }

                // Pre-validate all newly added children
                for (child in toAdd) {
                    val role = if (person.gender.lowercase() == "female") "mother" else "father"
                    val parentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(person, child, role, allMembers)
                    val existingOtherParentId = (if (person.gender.lowercase() == "female") child.fatherId else child.motherId)?.trim()
                    val existingOtherParent = if (!existingOtherParentId.isNullOrEmpty()) {
                        allMembers.find { it.id.trim().equals(existingOtherParentId, ignoreCase = true) }
                    } else null
                    val f = if (person.gender.lowercase() == "female") existingOtherParent else person
                    val m = if (person.gender.lowercase() == "female") person else existingOtherParent
                    val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(f, m, allMembers)

                    if (!parentCheck.isValid || !coParentCheck.isValid) {
                        val firstIssue = parentCheck.errors.firstOrNull() ?: coParentCheck.errors.first()
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val notif = NotificationHelper.createValidationConflictNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            memberName = "${child.firstName} ${child.lastName}".trim(),
                            conflictDetails = "Child link blocked: ${firstIssue.message}"
                        )
                        firestoreHelper.addNotification(notif)
                        AlertDialog.Builder(this)
                            .setTitle(firstIssue.title)
                            .setMessage(firstIssue.message)
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setPositiveButton
                    }
                }

                var operationsPending = toAdd.size + toRemove.size
                fun checkComplete() {
                    operationsPending--
                    if (operationsPending <= 0) {
                        Toast.makeText(this, "Children updated successfully", Toast.LENGTH_SHORT).show()
                        reloadAndDisplay()
                    }
                }

                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

                // Unlink
                toRemove.forEach { child ->
                    firestoreHelper.logActivity(
                        treeId = treeId,
                        userId = currentUserId,
                        userName = currentUserName,
                        type = "LINK",
                        description = "Removed ${child.firstName} ${child.lastName} as child of ${person.firstName} ${person.lastName}",
                        targetId = child.id,
                        targetName = "${child.firstName} ${child.lastName}"
                    )
                    if (person.gender.equals("female", ignoreCase = true)) {
                        firestoreHelper.updateMother(child.id, null, onSuccess = { checkComplete() }, onFailure = { checkComplete() })
                    } else {
                        firestoreHelper.updateFather(child.id, null, onSuccess = { checkComplete() }, onFailure = { checkComplete() })
                    }
                }

                // Link
                toAdd.forEach { child ->
                    firestoreHelper.logActivity(
                        treeId = treeId,
                        userId = currentUserId,
                        userName = currentUserName,
                        type = "LINK",
                        description = "Linked ${child.firstName} ${child.lastName} as child of ${person.firstName} ${person.lastName}",
                        targetId = child.id,
                        targetName = "${child.firstName} ${child.lastName}"
                    )
                    firestoreHelper.setChildParent(child.id, person.id, person.gender,
                        onSuccess = { checkComplete() },
                        onFailure = { checkComplete() }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialogRef = it }
            .show()
    }

    // ══════════════════════════════════════════════════════════════
    // LINEAL CONSANGUINITY BREAKDOWN (1st to 4th Degree)
    // ══════════════════════════════════════════════════════════════

    private fun displayLinealConsanguinity(person: Person) {
        val report = linealCalculator.calculate(person, allMembers)

        renderDegreeContainer(containerDegree1, report.degree1)
        renderDegreeContainer(containerDegree2, report.degree2)
        renderDegreeContainer(containerDegree3, report.degree3)
        renderDegreeContainer(containerDegree4, report.degree4)
    }

    private fun renderDegreeContainer(
        container: LinearLayout,
        group: LinealConsanguinityCalculator.LinealDegreeGroup
    ) {
        container.removeAllViews()

        if (group.isEmpty) {
            val emptyTv = TextView(this).apply {
                text = "None recorded in this tree"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MemberDetailActivity, R.color.text_secondary))
                setPadding(0, 4, 0, 4)
            }
            container.addView(emptyTv)
            return
        }

        // Ascendants
        if (group.ascendants.isNotEmpty()) {
            val ascHeader = TextView(this).apply {
                text = "⬆ ${group.ascendantTitle} (${group.ascendants.size})"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MemberDetailActivity, R.color.gold_accent))
                setPadding(0, 6, 0, 4)
            }
            container.addView(ascHeader)

            for (asc in group.ascendants) {
                container.addView(createMemberLinealRow(asc, group.ascendantTitle.removeSuffix("s")))
            }
        }

        // Descendants
        if (group.descendants.isNotEmpty()) {
            val descHeader = TextView(this).apply {
                text = "⬇ ${group.descendantTitle} (${group.descendants.size})"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MemberDetailActivity, R.color.emerald_accent))
                setPadding(0, 6, 0, 4)
            }
            container.addView(descHeader)

            for (desc in group.descendants) {
                container.addView(createMemberLinealRow(desc, group.descendantTitle.removeSuffix("s")))
            }
        }
    }

    private fun createMemberLinealRow(relative: Person, relationLabel: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            setBackgroundResource(R.drawable.badge_pill_dark)
            isClickable = true
            isFocusable = true
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 4)
            }
            layoutParams = params

            setOnClickListener {
                val intent = Intent(this@MemberDetailActivity, MemberDetailActivity::class.java).apply {
                    putExtra("personId", relative.id)
                    putExtra("TREE_ID", treeId)
                    putExtra("treeId", treeId)
                }
                startActivity(intent)
            }
        }

        val tvName = TextView(this).apply {
            text = "${relative.firstName} ${relative.lastName}"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvRole = TextView(this).apply {
            val genderShort = if (relative.gender.isNotEmpty()) " · ${relative.gender}" else ""
            text = "$relationLabel$genderShort ›"
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@MemberDetailActivity, R.color.gold_accent))
        }

        row.addView(tvName)
        row.addView(tvRole)
        return row
    }

    // ══════════════════════════════════════════════════════════════
    // TIMELINE
    // ══════════════════════════════════════════════════════════════

    private fun buildTimeline(person: Person) {
        layoutTimeline.removeAllViews()

        firestoreHelper.getChildren(person.id, treeId,
            onSuccess = { children ->
                val events = timelineGen.generate(person, allMembers, children)
                if (events.isEmpty()) {
                    addTimelineRow("—", "No events yet.")
                } else {
                    events.forEach { addTimelineRow(it.year, "${it.icon} ${it.description}") }
                }
            },
            onFailure = {
                val events = timelineGen.generate(person, allMembers)
                events.forEach { addTimelineRow(it.year, "${it.icon} ${it.description}") }
            }
        )
    }

    private fun addTimelineRow(year: String, description: String) {
        val row = layoutInflater.inflate(R.layout.item_timeline_event, layoutTimeline, false)
        row.findViewById<TextView>(R.id.tvYear).text      = year
        row.findViewById<TextView>(R.id.tvEventDesc).text = description
        layoutTimeline.addView(row)
    }

    private fun confirmDeletePerson() {
        val p = currentPerson
        val targetId = p?.id?.takeIf { it.isNotBlank() } ?: personId
        if (targetId.isBlank()) {
            Toast.makeText(this, "Cannot delete: Member ID not found", Toast.LENGTH_SHORT).show()
            return
        }
        val name = if (p != null) "${p.firstName} ${p.lastName}".trim().ifEmpty { "this member" } else "this member"
        AlertDialog.Builder(this)
            .setTitle("Delete Member")
            .setMessage("Are you sure you want to delete $name from the family tree? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                firestoreHelper.deletePerson(
                    targetId,
                    onSuccess = {
                        val currentUserId = authHelper.getCurrentUserId() ?: ""
                        val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

                        firestoreHelper.logActivity(
                            treeId = treeId,
                            userId = currentUserId,
                            userName = currentUserName,
                            type = "DELETE",
                            description = "Deleted member $name from the family tree",
                            targetId = targetId,
                            targetName = name
                        )

                        firestoreHelper.addNotification(
                            treeId = treeId,
                            userId = currentUserId,
                            title = "Member Record Deleted",
                            message = "$name was removed from the family tree.",
                            type = "RECORDS",
                            targetId = targetId
                        )

                        Toast.makeText(this, "$name deleted", Toast.LENGTH_SHORT).show()
                        val resultData = Intent().apply {
                            putExtra("deletedPersonId", targetId)
                        }
                        setResult(RESULT_OK, resultData)
                        finish()
                    },
                    onFailure = { err ->
                        Toast.makeText(this, "Failed to delete: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadRoleAndPrivacy() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                firestoreHelper.getUserMembership(currentUserId, treeId,
                    onSuccess = { member ->
                        isViewer = !isOwner && member?.role.equals("Viewer", ignoreCase = true)
                        applyPermissionsToUI()
                        currentPerson?.let { displayPerson(it) }
                    },
                    onFailure = {
                        applyPermissionsToUI()
                        currentPerson?.let { displayPerson(it) }
                    }
                )
            },
            onFailure = {
                applyPermissionsToUI()
            }
        )

        firestoreHelper.getPrivacySettings(treeId,
            onSuccess = { settings ->
                treePrivacy = settings
                currentPerson?.let { displayPerson(it) }
            },
            onFailure = {}
        )
    }

    private fun applyPermissionsToUI() {
        if (isViewer) {
            btnEdit.visibility = View.GONE
            btnAutoGenerate.visibility = View.GONE
            btnDeleteMember.visibility = View.GONE
            btnTopDeleteMember.visibility = View.GONE
            btnTopEditMember.visibility = View.GONE
            val onRestrictedClick = View.OnClickListener {
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createPermissionRestrictedNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    actionAttempted = "Edit Member Details/Relationships",
                    currentRole = "Viewer"
                )
                firestoreHelper.addNotification(notif)
                Toast.makeText(this, "Permission restricted: Viewers have read-only access", Toast.LENGTH_SHORT).show()
            }
            rowSpouse.setOnClickListener(onRestrictedClick)
            rowChildren.setOnClickListener(onRestrictedClick)
            rowMarriageDate.setOnClickListener(onRestrictedClick)
        } else {
            btnEdit.visibility = View.VISIBLE
            btnAutoGenerate.visibility = View.VISIBLE
            btnDeleteMember.visibility = View.VISIBLE
            btnTopDeleteMember.visibility = View.VISIBLE
            btnTopEditMember.visibility = View.VISIBLE
            rowSpouse.setOnClickListener { currentPerson?.let { showSpousePicker(it) } }
            rowChildren.setOnClickListener { currentPerson?.let { showChildrenPicker(it) } }
            rowMarriageDate.setOnClickListener { currentPerson?.let { showMarriageDatePicker(it) } }
        }
    }

    /**
     * Clear choices when adding a child to a family member:
     * 1. Add a biological or adoptive child
     * 2. Add the spouse of an existing child (In-Law: son-in-law or daughter-in-law)
     */
    private fun promptAddChildChoices(parentId: String) {
        val parent = currentPerson ?: allMembers.find { it.id == parentId }
        val parentName = parent?.let { "${it.firstName} ${it.lastName}".trim() } ?: "this member"

        val choices = arrayOf(
            "Add a biological or adoptive child",
            "Add the spouse of an existing child"
        )

        AlertDialog.Builder(this)
            .setTitle("Add Child Connection ($parentName)")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        // Choice 1: Add a biological or adoptive child
                        val parent = allMembers.find { it.id == parentId }
                        if (parent != null && !parent.spouseId.isNullOrBlank()) {
                            com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.showIdentifyOtherParentDialog(
                                context = this,
                                parent = parent,
                                allMembers = allMembers,
                                onResult = { result ->
                                    com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.startAddMemberActivity(
                                        context = this,
                                        result = result,
                                        treeId = treeId,
                                        launcher = addChildLauncher
                                    )
                                }
                            )
                        } else {
                            val intent = Intent(this, AddMemberActivity::class.java).apply {
                                putExtra("presetParentId", parentId)
                                putExtra("parentId", parentId)
                                putExtra("TREE_ID", treeId)
                                putExtra("treeId", treeId)
                            }
                            addChildLauncher.launch(intent)
                        }
                    }
                    1 -> {
                        // Choice 2: Add the spouse of an existing child
                        promptSelectChildForSpouse(parentId, parentName)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptSelectChildForSpouse(parentId: String, parentName: String) {
        val children = allMembers.filter {
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, parentId) ||
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, parentId)
        }

        if (children.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Registered Children")
                .setMessage("$parentName does not have any children registered in the family tree yet.\n\nUnder Philippine law and genealogical rules, a son-in-law or daughter-in-law must be connected as the spouse of an existing child. They must never be connected directly as a biological child of the parent-in-law.\n\nPlease add a child to $parentName first.")
                .setPositiveButton("Add Child First") { _, _ ->
                    val intent = Intent(this, AddMemberActivity::class.java).apply {
                        putExtra("presetParentId", parentId)
                        putExtra("parentId", parentId)
                        putExtra("TREE_ID", treeId)
                        putExtra("treeId", treeId)
                    }
                    addChildLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val childNames = children.map { child ->
            val spouseNote = if (!child.spouseId.isNullOrBlank()) {
                val sp = allMembers.find { it.id == child.spouseId }
                val spName = sp?.let { "${it.firstName} ${it.lastName}" } ?: "existing spouse"
                " (Married to $spName)"
            } else ""
            "${child.firstName} ${child.lastName}$spouseNote"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Existing Child of $parentName")
            .setItems(childNames) { _, which ->
                val selectedChild = children[which]
                if (!selectedChild.spouseId.isNullOrBlank()) {
                    val sp = allMembers.find { it.id == selectedChild.spouseId }
                    val spName = sp?.let { "${it.firstName} ${it.lastName}" } ?: "another spouse"
                    AlertDialog.Builder(this)
                        .setTitle("Child Already Married")
                        .setMessage("Unable to add this person as a spouse because one member already has an active spouse relationship.\n\n${selectedChild.firstName} ${selectedChild.lastName} is already married to $spName (Philippine Family Code Articles 35(4) and 40).")
                        .setPositiveButton("Understood", null)
                        .show()
                    return@setItems
                }

                val intent = Intent(this, AddMemberActivity::class.java).apply {
                    putExtra("presetSpouseId", selectedChild.id)
                    putExtra("isChildSpouseMode", true)
                    putExtra("inLawParentId", parentId)
                    putExtra("TREE_ID", treeId)
                    putExtra("treeId", treeId)
                }
                addChildLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuickVitalStatusDialog(person: Person) {
        if (person.isLiving) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_mark_deceased, null, false)
            val etDate = dialogView.findViewById<EditText>(R.id.etDeceasedDate)
            val etPlace = dialogView.findViewById<EditText>(R.id.etDeceasedPlace)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDeceased)
            val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmDeceased)
            val tvDesc = dialogView.findViewById<TextView>(R.id.tvMarkDeceasedDesc)

            val pName = "${person.firstName} ${person.lastName}".trim()
            tvDesc.text = "Record passing details for $pName. If $pName is currently married, the surviving spouse's status will automatically be updated to 'Widowed'."

            var selectedDateStr = person.deathDate
            etDate.setText(person.deathDate)
            etPlace.setText(person.deathPlace)

            etDate.setOnClickListener {
                val cal = Calendar.getInstance()
                val datePicker = DatePickerDialog(
                    this,
                    { _, year, month, day ->
                        val formatted = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                        selectedDateStr = formatted
                        etDate.setText(formatted)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
                datePicker.show()
            }

            val alertDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()

            btnCancel.setOnClickListener { alertDialog.dismiss() }
            btnConfirm.setOnClickListener {
                val enteredDate = etDate.text.toString().trim()
                val effectiveDate = if (selectedDateStr.isNotBlank()) selectedDateStr else enteredDate
                val deathPlace = etPlace.text.toString().trim()

                // Chronological check against birthDate
                if (effectiveDate.isNotBlank() && person.birthDate.isNotBlank()) {
                    val birthCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(person.birthDate)
                    val deathCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(effectiveDate)
                    if (birthCal != null && deathCal != null && deathCal.before(birthCal)) {
                        Toast.makeText(this, "Date of passing cannot be earlier than birthdate (${person.birthDate}).", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }

                // If person is a mother, verify death date is not before any biological child's birth date
                val allPersons = allMembers.ifEmpty { FirestoreHelper.getCachedPersons(treeId).orEmpty() }
                val children = allPersons.filter { 
                    com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, person.id) &&
                    (it.motherRelationshipType.isBlank() || it.motherRelationshipType.equals("Biological", ignoreCase = true))
                }
                for (child in children) {
                    if (child.birthDate.isNotBlank() && effectiveDate.isNotBlank()) {
                        val childCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(child.birthDate)
                        val deathCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(effectiveDate)
                        if (childCal != null && deathCal != null && childCal.after(deathCal)) {
                            Toast.makeText(this, "Cannot set date of passing earlier than biological child ${child.firstName}'s birthdate (${child.birthDate}).", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                    }
                }

                // Marriage date check
                if (person.marriageDate.isNotBlank() && effectiveDate.isNotBlank()) {
                    val mCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(person.marriageDate)
                    val dCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(effectiveDate)
                    if (mCal != null && dCal != null && mCal.after(dCal)) {
                        Toast.makeText(this, "Date of passing cannot be earlier than marriage date (${person.marriageDate}).", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }

                btnConfirm.isEnabled = false
                btnConfirm.text = "Saving..."

                firestoreHelper.updateVitalStatus(
                    personId = person.id,
                    isLiving = false,
                    deathDate = effectiveDate,
                    deathPlace = deathPlace,
                    onSuccess = { updated ->
                        alertDialog.dismiss()
                        currentPerson = updated
                        Toast.makeText(this, "Marked as Deceased", Toast.LENGTH_SHORT).show()
                        loadAllAndDisplay()
                    },
                    onFailure = { err ->
                        btnConfirm.isEnabled = true
                        btnConfirm.text = "Mark Deceased"
                        Toast.makeText(this, "Failed to update: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
            alertDialog.show()
        } else {
            // Already Deceased -> Confirm revert to Living
            val pName = "${person.firstName} ${person.lastName}".trim()
            AlertDialog.Builder(this)
                .setTitle("Mark as Living")
                .setMessage("Are you sure you want to mark $pName as Living?\n\nThe recorded date and place of passing will be cleared, and if their spouse was marked as Widowed, their status will be restored to Married.")
                .setPositiveButton("Mark Living") { dialog, _ ->
                    firestoreHelper.updateVitalStatus(
                        personId = person.id,
                        isLiving = true,
                        deathDate = "",
                        deathPlace = "",
                        onSuccess = { updated ->
                            dialog.dismiss()
                            currentPerson = updated
                            Toast.makeText(this, "Marked as Living", Toast.LENGTH_SHORT).show()
                            loadAllAndDisplay()
                        },
                        onFailure = { err ->
                            Toast.makeText(this, "Failed to update: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}
