package com.example.btproject2.ui.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AttachedDocument
import com.example.btproject2.models.Person
import com.example.btproject2.sync.AffectedScreen
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.sync.ConflictChoice
import com.example.btproject2.sync.InteractiveTreeSyncCoordinator
import com.example.btproject2.sync.SyncChangeType
import com.example.btproject2.sync.SyncConflictEvent
import com.example.btproject2.sync.SyncEventListener
import com.example.btproject2.sync.TreeSyncEvent
import com.example.btproject2.utils.DocumentHelper
import com.example.btproject2.utils.setDarkAdapter
import com.example.btproject2.utils.ThemePreferences
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class InteractiveTreeActivity : AppCompatActivity(), SyncEventListener {

    override val subscriberKey: String
        get() = "InteractiveTreeActivity@${System.identityHashCode(this)}"

    override val screenType: AffectedScreen
        get() = AffectedScreen.INTERACTIVE_TREE

    override val interestedTreeId: String?
        get() = treeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this).ifBlank { null }
        }

    private val firestoreHelper = FirestoreHelper()
    private val centralFamilyService = com.example.btproject2.service.FamilyRelationshipService()
    private val authHelper = com.example.btproject2.firebase.AuthHelper()
    internal lateinit var treeView: FamilyTreeView
    private var treeId = ""

    internal var allPersonsList: List<Person> = emptyList()
    internal var currentMode = "MAIN" // "MAIN", "PEDIGREE", "TRACE_HIGHLIGHT"

    private var highlightPersonAId: String? = null
    private var highlightPersonBId: String? = null
    private var mrcaId: String? = null
    private var highlightPathIds: Set<String> = emptySet()
    internal var focalPedigreePersonId: String? = null

    private lateinit var chipMainTree: TextView
    private lateinit var chipPedigree: TextView
    private lateinit var chipTraceHighlight: TextView

    private lateinit var layoutTraceBanner: LinearLayout
    private lateinit var tvTraceBannerText: TextView
    private lateinit var btnCloseTraceHighlight: TextView

    private lateinit var layoutPedigreeFocalBar: LinearLayout
    private lateinit var tvFocalBarLabel: TextView
    private lateinit var spinnerPedigreeFocal: Spinner

    private lateinit var layoutConflictBanner: LinearLayout
    private lateinit var tvConflictBannerText: TextView
    private lateinit var btnCloseConflictBanner: TextView

    private var activeProfilePersonId: String? = null
    private var activeProfileDialog: AlertDialog? = null

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !activeProfilePersonId.isNullOrEmpty()) {
            val result = DocumentHelper.saveImageToInternal(this, uri, activeProfilePersonId!!)
            if (result != null) {
                val (filePath, base64) = result
                firestoreHelper.updatePersonPhoto(
                    activeProfilePersonId!!,
                    base64,
                    filePath,
                    onSuccess = {
                        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
                        loadTree(refreshProfile = true)
                    },
                    onFailure = {
                        Toast.makeText(this, "Failed to update photo: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !activeProfilePersonId.isNullOrEmpty()) {
            val doc = DocumentHelper.saveDocumentToInternal(this, uri, activeProfilePersonId!!)
            if (doc != null) {
                firestoreHelper.attachDocument(
                    activeProfilePersonId!!,
                    doc,
                    onSuccess = {
                        Toast.makeText(this, "Document attached successfully", Toast.LENGTH_SHORT).show()
                        loadTree(refreshProfile = true)
                    },
                    onFailure = {
                        Toast.makeText(this, "Failed to attach document: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interactive_tree)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnConnectBranchesShortcut)?.setOnClickListener {
            startActivity(Intent(this, MergeBranchesActivity::class.java))
        }

        findViewById<TextView>(R.id.btnInteractiveLegend)?.setOnClickListener {
            showTreeLegendDialog()
        }

        findViewById<TextView>(R.id.btnAuditTree)?.setOnClickListener {
            val intent = Intent(this, TreeAuditActivity::class.java).apply {
                putExtra("TREE_ID", treeId)
                putExtra("treeId", treeId)
            }
            startActivity(intent)
        }

        chipMainTree = findViewById(R.id.chipMainTree)
        chipPedigree = findViewById(R.id.chipPedigree)
        chipTraceHighlight = findViewById(R.id.chipTraceHighlight)

        layoutTraceBanner = findViewById(R.id.layoutTraceBanner)
        tvTraceBannerText = findViewById(R.id.tvTraceBannerText)
        btnCloseTraceHighlight = findViewById(R.id.btnCloseTraceHighlight)

        layoutPedigreeFocalBar = findViewById(R.id.layoutPedigreeFocalBar)
        tvFocalBarLabel = findViewById(R.id.tvFocalBarLabel)
        spinnerPedigreeFocal = findViewById(R.id.spinnerPedigreeFocal)

        layoutConflictBanner = findViewById(R.id.layoutConflictBanner)
        tvConflictBannerText = findViewById(R.id.tvConflictBannerText)
        btnCloseConflictBanner = findViewById(R.id.btnCloseConflictBanner)
        btnCloseConflictBanner.setOnClickListener {
            layoutConflictBanner.visibility = View.GONE
        }

        highlightPersonAId = intent.getStringExtra("highlightPersonAId")
        highlightPersonBId = intent.getStringExtra("highlightPersonBId")
        mrcaId = intent.getStringExtra("mrcaId")
        val pathList = intent.getStringArrayListExtra("highlightPathIds")
        if (pathList != null && pathList.isNotEmpty()) {
            highlightPathIds = pathList.toSet()
        }
        focalPedigreePersonId = intent.getStringExtra("focalPedigreePersonId")
        val requestedMode = intent.getStringExtra("viewMode")

        val container = findViewById<FrameLayout>(R.id.treeContainer)

        treeView = FamilyTreeView(
            context = this,
            onOpenPerson = { personId ->
                showMemberProfileDialog(personId)
            },
            onAddChild = { parentId ->
                promptAddChildChoices(parentId)
            },
            onAddFirst = {
                val intent = Intent(this, AddMemberActivity::class.java).apply {
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            },
            onAddAncestor = { childId, isFather ->
                val child = allPersonsList.find { it.id == childId }
                if (child != null) {
                    val existingParents = com.example.btproject2.engine.FamilyLinkValidator.getExistingParentsInfo(child, allPersonsList)
                    val hasSlotFilled = if (isFather) existingParents.father != null else existingParents.mother != null
                    if (existingParents.hasTwoParents || hasSlotFilled) {
                        val childName = "${child.firstName} ${child.lastName}".trim()
                        val msg = if (existingParents.hasTwoParents) {
                            "It’s not possible to add another parent to $childName.\n\n$childName already has parents defined in this family tree: ${existingParents.fatherName} and ${existingParents.motherName}.\n\nA person who already has two parents cannot be added as another member’s child."
                        } else {
                            val role = if (isFather) "father" else "mother"
                            val existingName = if (isFather) existingParents.fatherName else existingParents.motherName
                            "It’s not possible to add another $role to $childName.\n\n$childName already has a $role defined in this family tree: $existingName.\n\nA person cannot have more than one biological $role."
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Cannot Add Parent")
                            .setMessage(msg)
                            .setPositiveButton("Understood", null)
                            .show()
                        return@FamilyTreeView
                    }
                }
                val intent = Intent(this, AddMemberActivity::class.java).apply {
                    putExtra("TREE_ID", treeId)
                    // If adding father, preset child
                    putExtra("presetChildId", childId)
                    putExtra("presetGender", if (isFather) "Male" else "Female")
                }
                startActivity(intent)
            }
        )

        container.addView(
            treeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        findViewById<View>(R.id.btnZoomIn)?.setOnClickListener { treeView.zoomIn() }
        findViewById<View>(R.id.btnZoomOut)?.setOnClickListener { treeView.zoomOut() }
        findViewById<View>(R.id.btnResetView)?.setOnClickListener { treeView.resetView() }

        chipMainTree.setOnClickListener { setMode("MAIN") }
        chipPedigree.setOnClickListener { setMode("PEDIGREE") }
        chipTraceHighlight.setOnClickListener { setMode("TRACE_HIGHLIGHT") }

        btnCloseTraceHighlight.setOnClickListener {
            setMode("MAIN")
        }

        currentMode = when {
            requestedMode != null -> requestedMode
            highlightPathIds.isNotEmpty() || !mrcaId.isNullOrEmpty() -> "TRACE_HIGHLIGHT"
            !focalPedigreePersonId.isNullOrEmpty() -> "PEDIGREE"
            else -> "MAIN"
        }

        if (highlightPathIds.isNotEmpty() || !mrcaId.isNullOrEmpty()) {
            chipTraceHighlight.visibility = View.VISIBLE
        }

        loadTree()
    }

    override fun onResume() {
        super.onResume()
        if (::treeView.isInitialized) {
            treeView.updateThemeColors()
        }
        loadTree()
    }

    override fun onStart() {
        super.onStart()
        if (treeId.isBlank()) {
            treeId = com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        CentralTreeSynchronizer.getInstance().startRealtimeListener(treeId)
        CentralTreeSynchronizer.getInstance().registerListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeProfileDialog?.dismiss()
        activeProfileDialog = null
        activeProfilePersonId = null
        CentralTreeSynchronizer.getInstance().unregisterListener(this)
    }

    internal val syncCoordinator by lazy {
        InteractiveTreeSyncCoordinator(
            treeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this) },
            listenerKey = subscriberKey,
            allPersonsList = allPersonsList,
            focalPedigreePersonId = focalPedigreePersonId,
            onTreeUpdated = { updatedList, _ ->
                allPersonsList = updatedList
                if (highlightPersonAId?.let { id -> updatedList.none { it.id == id } } == true ||
                    highlightPersonBId?.let { id -> updatedList.none { it.id == id } } == true ||
                    mrcaId?.let { id -> updatedList.none { it.id == id } } == true) {
                    highlightPersonAId = null
                    highlightPersonBId = null
                    mrcaId = null
                    highlightPathIds = emptySet()
                    setMode("MAIN")
                }
                if (::spinnerPedigreeFocal.isInitialized) refreshFocalSpinner()
                if (::treeView.isInitialized) {
                    treeView.setDataAndMode(
                        list = allPersonsList,
                        mode = currentMode,
                        highlightIds = highlightPathIds,
                        mrcaId = mrcaId,
                        focalPedigreeId = focalPedigreePersonId,
                        personAId = highlightPersonAId,
                        personBId = highlightPersonBId,
                        forceRefresh = true
                    )
                }
                if (currentMode == "TRACE_HIGHLIGHT" && ::layoutTraceBanner.isInitialized) {
                    updateTraceBannerText()
                }
                if (::layoutConflictBanner.isInitialized) {
                    checkForTreeConflicts(allPersonsList)
                }
            },
            onMemberEvictedFromAvatarCache = { id ->
                if (::treeView.isInitialized) treeView.evictAvatar(id)
            },
            onActiveProfileDismissed = { id ->
                if (activeProfilePersonId == id) {
                    activeProfileDialog?.dismiss()
                    activeProfileDialog = null
                    activeProfilePersonId = null
                }
            },
            onActiveProfileRefreshed = { id ->
                if (activeProfilePersonId == id) {
                    activeProfilePersonId = null
                    activeProfileDialog?.dismiss()
                    activeProfileDialog = null
                    showMemberProfileDialog(id)
                }
            }
        )
    }

    override fun onConflictDetected(
        conflict: SyncConflictEvent,
        resolver: (ConflictChoice) -> Unit
    ) {
        syncCoordinator.onConflictDetected(conflict, resolver)
    }

    override fun onSyncEvent(event: TreeSyncEvent) {
        if (event.changeType == SyncChangeType.TREE_DELETED && (event.treeId == treeId || event.treeId == interestedTreeId)) {
            runOnUiThread {
                Toast.makeText(this, "The displayed family tree was removed.", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }
        try {
            runOnUiThread {
                if (isSafeToUpdateUI) {
                    applySyncEvent(event)
                }
            }
        } catch (_: Throwable) {
            if (isSafeToUpdateUI) {
                applySyncEvent(event)
            }
        }
    }

    private val isSafeToUpdateUI: Boolean
        get() = try {
            !isFinishing && !isDestroyed
        } catch (_: Throwable) {
            true
        }

    /**
     * Authoritatively synchronizes InteractiveTreeActivity with a confirmed tree mutation.
     * Updates the in-memory member list, evicts stale avatar caches, re-layouts FamilyTreeView,
     * re-binds focal spinner and open profile modals, and re-scans tree conflicts.
     */
    fun applySyncEvent(event: TreeSyncEvent) {
        if (!isSafeToUpdateUI) return
        syncCoordinator.allPersonsList = allPersonsList
        syncCoordinator.focalPedigreePersonId = focalPedigreePersonId
        syncCoordinator.applySyncEvent(event)
    }

    private fun setMode(mode: String) {
        currentMode = mode
        updateModeChips(mode)
        treeView.setMode(
            mode = mode,
            highlightIds = highlightPathIds,
            mrcaId = mrcaId,
            focalPedigreeId = focalPedigreePersonId,
            personAId = highlightPersonAId,
            personBId = highlightPersonBId
        )
    }

    private fun updateModeChips(mode: String) {
        val goldBg = R.drawable.btn_gold_primary
        val darkBg = R.drawable.badge_pill_dark
        val darkText = Color.parseColor("#0A1B12")
        val secText = Color.parseColor("#A3B899")

        chipMainTree.setBackgroundResource(if (mode == "MAIN") goldBg else darkBg)
        chipMainTree.setTextColor(if (mode == "MAIN") darkText else secText)

        chipPedigree.setBackgroundResource(if (mode == "PEDIGREE") goldBg else darkBg)
        chipPedigree.setTextColor(if (mode == "PEDIGREE") darkText else secText)

        chipTraceHighlight.setBackgroundResource(if (mode == "TRACE_HIGHLIGHT") goldBg else darkBg)
        chipTraceHighlight.setTextColor(if (mode == "TRACE_HIGHLIGHT") darkText else secText)

        if (mode == "TRACE_HIGHLIGHT") {
            layoutTraceBanner.visibility = View.VISIBLE
            layoutPedigreeFocalBar.visibility = View.GONE
            updateTraceBannerText()
        } else if (mode == "PEDIGREE") {
            layoutTraceBanner.visibility = View.GONE
            layoutPedigreeFocalBar.visibility = View.VISIBLE
            tvFocalBarLabel.text = "🌳 Pedigree for:"
        } else {
            layoutTraceBanner.visibility = View.GONE
            layoutPedigreeFocalBar.visibility = View.VISIBLE
            tvFocalBarLabel.text = "🌿 Kinship relative to:"
        }
    }

    private fun updateTraceBannerText() {
        val byId = allPersonsList.associateBy { it.id }
        val nameA = highlightPersonAId?.let { byId[it]?.firstName } ?: "Person A"
        val nameB = highlightPersonBId?.let { byId[it]?.firstName } ?: "Person B"
        val mrcaName = mrcaId?.let { byId[it]?.firstName }

        tvTraceBannerText.text = if (mrcaName != null) {
            "⭐ Bloodline Route: $nameA ➔ ★ $mrcaName (MRCA) ➔ $nameB"
        } else {
            "⭐ Bloodline Route: $nameA ➔ $nameB"
        }
    }

    private fun showTreeLegendDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_legend, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnCloseLegend)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnGotItLegend)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun refreshFocalSpinner() {
        val list = allPersonsList
        if (list.isEmpty()) return
        val names = list.map { "${it.firstName} ${it.lastName}" }
        spinnerPedigreeFocal.setDarkAdapter(this, names)

        val defaultIdx = if (!focalPedigreePersonId.isNullOrEmpty()) {
            list.indexOfFirst { it.id == focalPedigreePersonId }.coerceAtLeast(0)
        } else 0

        if (focalPedigreePersonId.isNullOrEmpty()) {
            focalPedigreePersonId = list[defaultIdx].id
        }
        spinnerPedigreeFocal.setSelection(defaultIdx)

        spinnerPedigreeFocal.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedId = allPersonsList.getOrNull(position)?.id
                if (!selectedId.isNullOrEmpty() && selectedId != focalPedigreePersonId) {
                    focalPedigreePersonId = selectedId
                    treeView.setMode(currentMode, highlightPathIds, mrcaId, focalPedigreePersonId)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadTree(refreshProfile: Boolean = false) {
        val effectiveTreeId = treeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        if (effectiveTreeId.isBlank()) {
            allPersonsList = emptyList()
            syncCoordinator.allPersonsList = emptyList()
            treeView.setDataAndMode(
                list = emptyList(),
                mode = currentMode,
                highlightIds = highlightPathIds,
                mrcaId = mrcaId,
                focalPedigreeId = focalPedigreePersonId,
                personAId = highlightPersonAId,
                personBId = highlightPersonBId
            )
            return
        }
        if (treeId.isBlank()) {
            treeId = effectiveTreeId
        }

        firestoreHelper.getPersonsByTree(
            treeId = effectiveTreeId,
            onSuccess = { rawList ->
                val list = com.example.btproject2.firebase.FirestoreHelper.sanitizeTreeRecords(rawList)
                allPersonsList = list
                syncCoordinator.allPersonsList = list
                syncCoordinator.focalPedigreePersonId = focalPedigreePersonId

                refreshFocalSpinner()

                updateModeChips(currentMode)
                treeView.setDataAndMode(
                    list = list,
                    mode = currentMode,
                    highlightIds = highlightPathIds,
                    mrcaId = mrcaId,
                    focalPedigreeId = focalPedigreePersonId,
                    personAId = highlightPersonAId,
                    personBId = highlightPersonBId
                )
                checkForTreeConflicts(list)

                if (refreshProfile && !activeProfilePersonId.isNullOrEmpty()) {
                    val activeId = activeProfilePersonId
                    activeProfilePersonId = null
                    activeProfileDialog?.dismiss()
                    activeProfileDialog = null
                    if (activeId != null) {
                        showMemberProfileDialog(activeId)
                    }
                }
            },
            onFailure = {
                Toast.makeText(this, "Failed to load tree", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun checkForTreeConflicts(persons: List<Person>) {
        if (persons.isEmpty()) {
            layoutConflictBanner.visibility = View.GONE
            return
        }
        Thread {
            val conflicts = com.example.btproject2.engine.ConflictScanner().scanAll(persons)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (conflicts.isNotEmpty()) {
                    val errorCount = conflicts.count { it.severity == "error" }
                    val totalCount = conflicts.size
                    layoutConflictBanner.visibility = View.VISIBLE
                    tvConflictBannerText.text = if (errorCount > 0) {
                        "⚠️ $errorCount invalid relationship(s) detected. Tap to inspect & fix."
                    } else {
                        "⚠️ $totalCount tree warning(s) detected. Tap to inspect."
                    }
                    layoutConflictBanner.setOnClickListener {
                        showTreeConflictsDialog(conflicts)
                    }
                } else {
                    layoutConflictBanner.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun showTreeConflictsDialog(conflicts: List<com.example.btproject2.engine.ConflictScanner.Conflict>) {
        val details = conflicts.joinToString("\n\n") { c ->
            val icon = if (c.severity == "error") "❌" else "⚠️"
            "$icon ${c.personName}: ${c.description}"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tree Relationship Inconsistencies")
            .setMessage(details)
            .setPositiveButton("Understood", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════════
    // Automatic Profile Reveal Modal with Photos & Document Proofs
    // ══════════════════════════════════════════════════════════════════
    private fun showMemberProfileDialog(personId: String) {
        val person = allPersonsList.find { it.id == personId } ?: return
        activeProfilePersonId = personId

        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_member_profile, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        activeProfileDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Find views
        val btnClose = dialogView.findViewById<TextView>(R.id.btnProfileClose)
        val ivPhoto = dialogView.findViewById<ImageView>(R.id.ivProfilePhoto)
        val tvAvatar = dialogView.findViewById<TextView>(R.id.tvProfileAvatar)
        val btnChangePhoto = dialogView.findViewById<TextView>(R.id.btnProfileChangePhoto)

        val tvFullName = dialogView.findViewById<TextView>(R.id.tvProfileFullName)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvProfileSubtitle)
        val tvKinshipBadge = dialogView.findViewById<TextView>(R.id.tvProfileKinshipBadge)
        val tvAgeBadge = dialogView.findViewById<TextView>(R.id.tvProfileAgeBadge)
        val tvSexBadge = dialogView.findViewById<TextView>(R.id.tvProfileSexBadge)
        val btnSetAsFocal = dialogView.findViewById<TextView>(R.id.btnProfileSetAsFocal)

        val tvBirth = dialogView.findViewById<TextView>(R.id.tvProfileBirth)
        val tvParents = dialogView.findViewById<TextView>(R.id.tvProfileParents)
        val tvSpouse = dialogView.findViewById<TextView>(R.id.tvProfileSpouse)

        val tvVerificationBadge = dialogView.findViewById<TextView>(R.id.tvProfileVerificationBadge)
        val layoutDocsContainer = dialogView.findViewById<LinearLayout>(R.id.layoutProfileDocsContainer)
        val tvNoDocs = dialogView.findViewById<TextView>(R.id.tvProfileNoDocs)
        val btnAttachDoc = dialogView.findViewById<TextView>(R.id.btnProfileAttachDoc)

        val btnAddChild = dialogView.findViewById<TextView>(R.id.btnProfileAddChild)
        val btnEdit = dialogView.findViewById<TextView>(R.id.btnProfileEdit)
        val btnFullDetail = dialogView.findViewById<TextView>(R.id.btnProfileFullDetail)

        // Bind Avatar
        val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}".uppercase()
        tvAvatar.text = initials
        val rawBmp = DocumentHelper.decodeBase64Bitmap(person.photoBase64)
        if (rawBmp != null) {
            val circ = DocumentHelper.getCircularBitmap(rawBmp, 144)
            ivPhoto.setImageBitmap(circ)
            ivPhoto.visibility = View.VISIBLE
            tvAvatar.visibility = View.GONE
            if (!person.isLiving) {
                val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                ivPhoto.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            } else {
                ivPhoto.colorFilter = null
            }
        } else {
            ivPhoto.visibility = View.GONE
            tvAvatar.visibility = View.VISIBLE
        }

        btnChangePhoto.setOnClickListener {
            activeProfilePersonId = person.id
            photoPickerLauncher.launch("image/*")
        }

        // Names & Status
        val fullNameStr = "${person.firstName} ${person.middleName} ${person.lastName} ${person.suffix}".replace(Regex("\\s+"), " ").trim()
        tvFullName.text = fullNameStr

        val focalPerson = allPersonsList.find { it.id == focalPedigreePersonId }
        val kinshipTitle = KinshipTitleHelper.resolveTitle(person, focalPerson, allPersonsList)
        val kinshipSubtitle = KinshipTitleHelper.resolveSubtitle(person, focalPerson, allPersonsList)

        val spouseObj = allPersonsList.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.spouseId) }
            ?: allPersonsList.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, person.id) }

        tvKinshipBadge?.text = if (!person.isLiving) "🕊️ $kinshipTitle" else kinshipTitle
        val livingStr = if (person.isLiving) "Living" else "🕊️ Deceased"
        val effectiveCivilStatus = if (spouseObj != null) "Married" else person.maritalStatus.ifEmpty { "Single" }
        tvSubtitle.text = "$kinshipSubtitle · $effectiveCivilStatus · $livingStr"
        if (!person.isLiving) {
            tvSubtitle.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        } else {
            tvSubtitle.setTextColor(resources.getColor(R.color.mint_text, null))
        }

        val btnToggleVital = dialogView.findViewById<TextView?>(R.id.btnProfileToggleVitalStatus)
        btnToggleVital?.text = if (person.isLiving) "🕊️ Mark as Deceased" else "🌱 Mark as Living"
        btnToggleVital?.setOnClickListener {
            showQuickVitalStatusDialog(person)
        }
        tvSubtitle.setOnClickListener {
            showQuickVitalStatusDialog(person)
        }

        btnSetAsFocal?.text = "🎯 View Kinship Relative to ${person.firstName}"
        btnSetAsFocal?.setOnClickListener {
            focalPedigreePersonId = person.id
            val idx = allPersonsList.indexOfFirst { it.id == person.id }
            if (idx >= 0) spinnerPedigreeFocal.setSelection(idx)
            treeView.setMode(currentMode, highlightPathIds, mrcaId, focalPedigreePersonId)
            Toast.makeText(this, "Tree kinship updated relative to ${person.firstName}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        val ageStr = DocumentHelper.calculateAge(person.birthDate, person.deathDate, person.isLiving)
        tvAgeBadge.text = ageStr.ifEmpty { "Age N/A" }
        tvSexBadge.text = "${person.gender} ${if (person.gender.lowercase() == "female") "♀" else "♂"}"

        // Vital details
        val bDate = person.birthDate.ifEmpty { "Unknown" }
        val bPlace = if (person.birthPlace.isNotEmpty()) " · ${person.birthPlace}" else ""
        tvBirth.text = "$bDate$bPlace"

        val fObj = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(person.fatherId, allPersonsList)
        val mObj = com.example.btproject2.engine.FamilyLinkValidator.findPersonInList(person.motherId, allPersonsList)
        val fName = fObj?.let { "${it.firstName} ${it.lastName}" }
        val mName = mObj?.let { "${it.firstName} ${it.lastName}" }
        val parentsText = when {
            fName != null && mName != null -> "$fName & $mName"
            fName != null -> fName
            mName != null -> mName
            else -> "None linked"
        }
        tvParents.text = parentsText

        val spName = spouseObj?.let { "${it.firstName} ${it.lastName}" } ?: "None"
        tvSpouse.text = spName

        // Documents & Verification Badge
        renderProfileDocuments(person, layoutDocsContainer, tvNoDocs, tvVerificationBadge)

        btnAttachDoc.setOnClickListener {
            activeProfilePersonId = person.id
            documentPickerLauncher.launch("*/*")
        }

        val btnAddSpouse = dialogView.findViewById<TextView>(R.id.btnProfileAddSpouse)
        val rowProfileSpouse = dialogView.findViewById<View>(R.id.rowProfileSpouse)

        // Actions
        btnClose.setOnClickListener { dialog.dismiss() }

        btnAddChild.setOnClickListener {
            dialog.dismiss()
            promptAddChildChoices(person.id)
        }

        btnAddSpouse?.setOnClickListener {
            dialog.dismiss()
            promptAddSpouse(person)
        }

        rowProfileSpouse?.setOnClickListener {
            dialog.dismiss()
            promptAddSpouse(person)
        }

        btnEdit.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, EditMemberActivity::class.java).apply {
                putExtra("personId", person.id)
                putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        btnFullDetail.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, MemberDetailActivity::class.java).apply {
                putExtra("personId", person.id)
                putExtra("TREE_ID", treeId)
            }
            startActivity(intent)
        }

        dialog.setOnDismissListener {
            if (activeProfilePersonId == person.id) {
                activeProfilePersonId = null
            }
            if (activeProfileDialog == dialog) {
                activeProfileDialog = null
            }
        }

        dialog.show()
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
                val cal = java.util.Calendar.getInstance()
                val datePicker = android.app.DatePickerDialog(
                    this,
                    { _, year, month, day ->
                        val formatted = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                        selectedDateStr = formatted
                        etDate.setText(formatted)
                    },
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
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
                val allPersons = allPersonsList.ifEmpty { FirestoreHelper.getCachedPersons(treeId).orEmpty() }
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
                        activeProfileDialog?.dismiss()
                        Toast.makeText(this, "Marked as Deceased", Toast.LENGTH_SHORT).show()
                        loadTree(refreshProfile = true)
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
                            activeProfileDialog?.dismiss()
                            Toast.makeText(this, "Marked as Living", Toast.LENGTH_SHORT).show()
                            loadTree(refreshProfile = true)
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

    /**
     * Clear choices when user selects "Add Spouse" on a family member.
     * Follows the 9-step statutory validation and gatekeeping rules:
     * 1. Get profile details.
     * 2. Confirm not the same person.
     * 3. Calculate and validate ages from DOB.
     * 4. Check minimum spouse age.
     * 5. Apply gender policy.
     * 6. Check active spouse relationship.
     * 7. Check blood relationship & legal impediments.
     * 8. Block if any validation fails and display clear user-friendly message.
     * 9. Save and update display if all pass.
     */
    private fun promptAddSpouse(person: Person) {
        val personName = "${person.firstName} ${person.lastName}".trim()
        val existingSpouse = allPersonsList.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.spouseId) }
            ?: allPersonsList.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, person.id) }

        if (existingSpouse != null && existingSpouse.isLiving && person.maritalStatus.equals("Married", ignoreCase = true)) {
            val spouseName = "${existingSpouse.firstName} ${existingSpouse.lastName}".trim()
            AlertDialog.Builder(this)
                .setTitle("Active Spouse Already Linked")
                .setMessage("Unable to add this person as a spouse because $personName already has an active spouse relationship with $spouseName.\n\nUnder Philippine law (Family Code Articles 35(4) and 40), bigamous or polygamous marriages are void from the beginning.")
                .setPositiveButton("View Spouse") { _, _ ->
                    showMemberProfileDialog(existingSpouse.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val choices = arrayOf(
            "Select Existing Family Member as Spouse",
            "Add New Member as Spouse"
        )

        AlertDialog.Builder(this)
            .setTitle("Add Spouse for $personName")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> promptSelectExistingMemberForSpouse(person)
                    1 -> {
                        val intent = Intent(this, AddMemberActivity::class.java).apply {
                            putExtra("presetSpouseId", person.id)
                            putExtra("TREE_ID", treeId)
                            putExtra("treeId", treeId)
                        }
                        startActivity(intent)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptSelectExistingMemberForSpouse(person: Person) {
        val personName = "${person.firstName} ${person.lastName}".trim()
        val candidates = allPersonsList.filter { !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.id) }

        if (candidates.isEmpty()) {
            Toast.makeText(this, "No other members available in the tree.", Toast.LENGTH_SHORT).show()
            return
        }

        val candidateNames = candidates.map { candidate ->
            val genderTag = if (candidate.gender.isNotBlank()) " (${candidate.gender})" else ""
            val ageVal = com.example.btproject2.engine.MarriageValidationEngine.calculateAge(candidate)
            val ageTag = if (ageVal != null) ", age $ageVal" else ""
            "${candidate.firstName} ${candidate.lastName}$genderTag$ageTag"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Spouse for $personName")
            .setItems(candidateNames) { _, which ->
                val candidate = candidates[which]
                val treeMap = allPersonsList.associateBy { it.id }.toMutableMap()
                treeMap[person.id] = person
                treeMap[candidate.id] = candidate

                // 1-7. Central Validation Service Checks
                val serviceCheck = centralFamilyService.checkProposedRelationship(
                    type = com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType.SPOUSE,
                    primaryPersonId = person.id,
                    secondaryPersonId = candidate.id,
                    tree = treeMap
                )

                // 8. If any validation fails, do not save and show clear user-friendly error
                if (!serviceCheck.isAllowed) {
                    val formattedError = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(person, candidate, serviceCheck)
                    val primaryMessage = com.example.btproject2.utils.SpouseValidationMessageHelper.getPrimaryErrorMessage(person, candidate, serviceCheck)

                    val currentUserId = authHelper.getCurrentUserId().orEmpty()
                    val notif = com.example.btproject2.utils.NotificationHelper.createValidationConflictNotification(
                        treeId = treeId,
                        userId = currentUserId,
                        memberName = personName,
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

                // 9. If all validations pass, save spouse relationship and update display
                firestoreHelper.updateSpouse(person.id, candidate.id, "Married",
                    onSuccess = {
                        val currentUserId = authHelper.getCurrentUserId().orEmpty()
                        val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"
                        firestoreHelper.logActivity(
                            treeId = treeId,
                            userId = currentUserId,
                            userName = currentUserName,
                            type = "LINK",
                            description = "Linked ${person.firstName} ${person.lastName} and ${candidate.firstName} ${candidate.lastName} as spouses",
                            targetId = person.id,
                            targetName = personName
                        )
                        Toast.makeText(this, "Spouse relationship successfully saved!", Toast.LENGTH_SHORT).show()
                        loadTree(refreshProfile = true)
                    },
                    onFailure = { err ->
                        Toast.makeText(this, "Failed to save spouse: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun promptAddChildChoices(parentId: String) {
        val parent = allPersonsList.find { it.id == parentId }
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
                        if (parent != null && !parent.spouseId.isNullOrBlank()) {
                            com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.showIdentifyOtherParentDialog(
                                context = this,
                                parent = parent,
                                allMembers = allPersonsList,
                                onResult = { result ->
                                    com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.startAddMemberActivity(
                                        context = this,
                                        result = result,
                                        treeId = treeId
                                    )
                                }
                            )
                        } else {
                            val intent = Intent(this, AddMemberActivity::class.java).apply {
                                putExtra("presetParentId", parentId)
                                putExtra("TREE_ID", treeId)
                                putExtra("treeId", treeId)
                            }
                            startActivity(intent)
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
        val children = allPersonsList.filter {
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
                        putExtra("TREE_ID", treeId)
                        putExtra("treeId", treeId)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val childNames = children.map { child ->
            val spouseNote = if (!child.spouseId.isNullOrBlank()) {
                val sp = allPersonsList.find { it.id == child.spouseId }
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
                    val sp = allPersonsList.find { it.id == selectedChild.spouseId }
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
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderProfileDocuments(
        person: Person,
        container: LinearLayout,
        tvNoDocs: TextView,
        badge: TextView
    ) {
        container.removeAllViews()
        val docs = person.documents

        if (docs.isEmpty()) {
            tvNoDocs.visibility = View.VISIBLE
            badge.text = "⚠️ Unverified"
            badge.setTextColor(Color.parseColor("#BA7517"))
            badge.setBackgroundResource(R.drawable.badge_pill_dark)
        } else {
            tvNoDocs.visibility = View.GONE
            badge.text = "✓ Verified (${docs.size} Docs)"
            badge.setTextColor(Color.parseColor("#10B981"))
            badge.setBackgroundResource(R.drawable.badge_pill_dark)

            docs.forEach { doc ->
                val docView = layoutInflater.inflate(R.layout.item_attached_document, container, false)
                val tvName = docView.findViewById<TextView>(R.id.tvDocName)
                val tvCat = docView.findViewById<TextView>(R.id.tvDocCategory)
                val tvMeta = docView.findViewById<TextView>(R.id.tvDocMeta)
                val btnView = docView.findViewById<TextView>(R.id.btnViewDoc)
                val btnDelete = docView.findViewById<TextView>(R.id.btnDeleteDoc)

                tvName.text = doc.name
                tvCat.text = doc.category
                tvMeta.text = doc.fileSize.ifEmpty { "Attached" }

                btnView.setOnClickListener {
                    DocumentHelper.openDocument(this, doc)
                }

                btnDelete.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Remove Document")
                        .setMessage("Remove \"${doc.name}\" from ${person.firstName}'s profile?")
                        .setPositiveButton("Remove") { _, _ ->
                            firestoreHelper.removeDocument(person.id, doc.id,
                                onSuccess = {
                                    Toast.makeText(this, "Document removed", Toast.LENGTH_SHORT).show()
                                    loadTree(refreshProfile = true)
                                },
                                onFailure = {
                                    Toast.makeText(this, "Failed to remove: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

                container.addView(docView)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// FamilyTreeView — Canvas with Photo Nodes, Ahnentafel Pedigree & Trace
// ══════════════════════════════════════════════════════════════════════

class FamilyTreeView(
    context: Context,
    private val onOpenPerson:  (personId: String) -> Unit,
    private val onAddChild:    (parentId: String) -> Unit,
    private val onAddFirst:    () -> Unit,
    private val onAddAncestor: (childId: String, isFather: Boolean) -> Unit
) : View(context) {

    private val CARD_W      = 150f
    private val CARD_H      = 172f
    private val CARD_CORNER  = 14f
    private val NODE_RADIUS  = 36f
    private val COUPLE_GAP   = 28f   // Compact spacing between couple cards
    private val SIBLING_GAP  = 36f   // Clean separation between sibling subtrees
    private val BRANCH_GAP   = 60f   // Separation between independent family branches
    private val COL_GAP      = 320f
    private val ROW_GAP      = 240f  // Generational pitch (CARD_H 172f + V_GAP 68f)
    private val V_GAP        = 68f   // Vertical distance between parent card bottom and child card top
    private val SLOT_H       = 130f
    private val MARGIN       = 120f
    private val ADD_BTN_R    = 14f

    // ── Paints ───────────────────────────────────────────────────
    private val pMale     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F6E56") }
    private val pFemale   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9D174D") }
    private val pDeceased = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#475569") }
    private val pMrcaNode = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B45309") }

    private val pBorder   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val pDeceasedBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val pGoldHighlightBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val pMrcaGoldHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDE68A"); style = Paint.Style.STROKE; strokeWidth = 6f
    }

    // Rectangular Node Card Paints (Matching Reference Diagram & Tree Legend)
    private val pCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F241A"); style = Paint.Style.FILL
    }
    private val pCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#234E3B"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val pCardDeceasedBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155"); style = Paint.Style.FILL // Slate Gray
    }
    private val pCardDeceasedBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8"); style = Paint.Style.STROKE; strokeWidth = 2.5f // Light Slate Gray
    }
    private val pCardDeceasedBgDim = Paint(pCardDeceasedBg).apply {
        color = Color.parseColor("#1E293B"); alpha = 70
    }
    private val pCardDeceasedBorderDim = Paint(pCardDeceasedBorder).apply {
        color = Color.parseColor("#475569"); alpha = 70
    }
    private val pCardHighlightBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3B2B"); style = Paint.Style.FILL
    }
    private val pCardHighlightBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val pCardMrcaBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A1F08"); style = Paint.Style.FILL
    }
    private val pCardMrcaBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5D77F"); style = Paint.Style.STROKE; strokeWidth = 3.5f
    }
    private val pCardShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000"); style = Paint.Style.FILL
    }

    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D9E75"); style = Paint.Style.STROKE; strokeWidth = 3.5f
    }
    private val pGoldPathLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); style = Paint.Style.STROKE; strokeWidth = 6f
    }
    private val pSpouseLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); style = Paint.Style.STROKE
        strokeWidth = 2.5f; pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    // Marital Center Pill Paints
    private val pMaritalPillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B2E24"); style = Paint.Style.FILL
    }
    private val pMaritalPillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val pMaritalPillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5D77F"); textSize = 13f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    // Node Drop Shadow Paint
    private val pNodeShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000"); style = Paint.Style.FILL
    }

    // Kinship Title Badge Pill Paints
    private val pTitleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#132B20")
        style = Paint.Style.FILL
    }
    private val pTitleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val pTitleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5D77F")
        textSize = 11f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Dashed circle and connector for empty/unadded ancestor slots
    private val pDashedCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A6E60")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val pDashedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#344C3D")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val pPedigreeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val pPedigreeDashedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val pDashedText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A3B899"); textSize = 22f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pDashedLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A9A88"); textSize = 20f; textAlign = Paint.Align.CENTER
    }

    private val pInitials = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pFirstName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pLastName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A3B899"); textSize = 14f; textAlign = Paint.Align.CENTER
    }
    private val pYear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A9A88"); textSize = 12f; textAlign = Paint.Align.CENTER
    }
    private val pMrcaBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); textSize = 12f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    // Role Badges for Bloodline Tracing
    private val pPersonABadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pPersonBBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4ECCA3"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pBloodlineBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5D77F"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pGoldPathGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44D4A359")
        style = Paint.Style.STROKE
        strokeWidth = 16f
    }

    // Pre-allocated Dimmed & State Paints for Zero-Allocation onDraw()
    private val pCardBgDim = Paint(pCardBg).apply { alpha = 70 }
    private val pCardBorderDim = Paint(pCardBorder).apply { alpha = 70 }
    private val pMaleDim = Paint(pMale).apply { alpha = 80 }
    private val pFemaleDim = Paint(pFemale).apply { alpha = 80 }
    private val pDeceasedDim = Paint(pDeceased).apply { alpha = 80 }
    private val pBorderDim = Paint(pBorder).apply { alpha = 80 }
    private val pDeceasedBorderDim = Paint(pDeceasedBorder).apply { alpha = 80 }
    private val pInitialsDim = Paint(pInitials).apply { alpha = 80 }
    private val pFirstNameDim = Paint(pFirstName).apply { textSize = 15f; alpha = 80 }
    private val pLastNameDim = Paint(pLastName).apply { textSize = 13f; alpha = 80 }
    private val pYearDim = Paint(pYear).apply { textSize = 11.5f; alpha = 80 }
    private val pYearDeceased = Paint(pYear).apply { textSize = 11.5f; color = Color.parseColor("#94A3B8") }
    private val pYearDeceasedDim = Paint(pYearDeceased).apply { alpha = 80 }
    private val pLineDim = Paint(pLine).apply { alpha = 70 }
    private val pSpouseLineDim = Paint(pSpouseLine).apply { alpha = 70 }
    private val pMaritalPillBgDim = Paint(pMaritalPillBg).apply { alpha = 80 }
    private val pMaritalPillBorderDim = Paint(pMaritalPillBorder).apply { alpha = 80 }
    private val pMaritalPillTextDim = Paint(pMaritalPillText).apply { alpha = 80 }
    private val pTitleBgDim = Paint(pTitleBg).apply { alpha = 70 }
    private val pTitleBorderDim = Paint(pTitleBorder).apply { alpha = 70 }
    private val pTitleTextDim = Paint(pTitleText).apply { textSize = 11f; alpha = 70 }

    private val pDeceasedTitleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); style = Paint.Style.FILL }
    private val pDeceasedTitleBgDim = Paint(pDeceasedTitleBg).apply { alpha = 70 }
    private val pDeceasedTitleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#64748B"); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val pDeceasedTitleBorderDim = Paint(pDeceasedTitleBorder).apply { alpha = 70 }
    private val pDeceasedTitleText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pDeceasedTitleTextDim = Paint(pDeceasedTitleText).apply { alpha = 70 }

    private val pMemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155") }
    private val pMemTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pCheckBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
    private val pCheckTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pBmpDim = Paint().apply { alpha = 90 }
    private val pBmpNormal = Paint()
    private val pBmpDeceasedNormal = Paint().apply {
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    private val pBmpDeceasedDim = Paint().apply {
        alpha = 90
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    private val pAddBtnBorder = Paint(pBorder).apply { strokeWidth = 2.5f }

    private val pAddBtn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
    private val pAddIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val pEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A3B899"); textSize = 40f; textAlign = Paint.Align.CENTER
    }
    private val pEmptySub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A9A88"); textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val pFab = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D4A359") }
    private val pFabIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A1B12"); textSize = 52f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    init {
        updateThemeColors()
    }

    fun updateThemeColors() {
        val isDark = ThemePreferences.isDarkTheme(context)
        if (isDark) {
            pCardBg.color = Color.parseColor("#0F241A")
            pCardBorder.color = Color.parseColor("#234E3B")
            pFirstName.color = Color.WHITE
            pLastName.color = Color.parseColor("#A3B899")
            pYear.color = Color.parseColor("#7A9A88")
            pDashedCircle.color = Color.parseColor("#5A6E60")
            pDashedLine.color = Color.parseColor("#344C3D")
            pPedigreeLine.color = Color.parseColor("#1D9E75")
            pPedigreeDashedLine.color = Color.parseColor("#344C3D")
            pDashedText.color = Color.parseColor("#A3B899")
            pDashedLabel.color = Color.parseColor("#7A9A88")
            pLine.color = Color.parseColor("#1D9E75")
            pEmpty.color = Color.parseColor("#A3B899")
            pEmptySub.color = Color.parseColor("#7A9A88")
        } else {
            pCardBg.color = Color.parseColor("#F5F7F1")
            pCardBorder.color = Color.parseColor("#D2D9CE")
            pFirstName.color = Color.parseColor("#0F172A")
            pLastName.color = Color.parseColor("#475569")
            pYear.color = Color.parseColor("#64748B")
            pDashedCircle.color = Color.parseColor("#475569")
            pDashedLine.color = Color.BLACK
            pPedigreeLine.color = Color.BLACK
            pPedigreeDashedLine.color = Color.BLACK
            pDashedText.color = Color.parseColor("#475569")
            pDashedLabel.color = Color.parseColor("#64748B")
            pLine.color = Color.parseColor("#B45309")
            pEmpty.color = Color.parseColor("#57635A")
            pEmptySub.color = Color.parseColor("#64748B")
        }
        invalidate()
    }

    // Reusable geometry buffers for zero GC allocation during onDraw
    private val tempCardRect = RectF()
    private val tempShadowRect = RectF()
    private val tempBarRect = RectF()
    private val tempMaritalRect = RectF()
    private val sharedTracePath = Path()
    private val sharedBranchPath = Path()
    private val sharedLinePath = Path()
    private val sharedPedigreePath = Path()

    // Precomputed Card Display Cache
    data class CardDisplayData(
        val displayFirstName: String,
        val displayLastName: String,
        val displayYear: String,
        val kinshipTitle: String,
        val initials: String,
        val displayFullName: String = ""
    )
    private val cardDisplayCache = mutableMapOf<String, CardDisplayData>()

    // Precomputed Connectors for Zero-Allocation Rendering
    private data class PrecomputedMaritalLine(
        val primaryId: String,
        val spouseId: String,
        val leftX: Float,
        val rightX: Float,
        val y: Float,
        val pillMidX: Float,
        val pillMidY: Float
    )

    private data class PrecomputedChildDrop(
        val childId: String,
        val x: Float,
        val yBus: Float,
        val targetY: Float
    )

    private data class PrecomputedFamilyBus(
        val primaryId: String,
        val spouseId: String?,
        val dropX: Float,
        val dropStartY: Float,
        val yBus: Float,
        val busLeft: Float,
        val busRight: Float,
        val childDrops: List<PrecomputedChildDrop>
    )

    private data class PrecomputedFallbackLine(
        val parentId: String,
        val childId: String,
        val pX: Float,
        val pY: Float,
        val cX: Float,
        val cY: Float,
        val isStep: Boolean,
        val midY: Float
    )

    private data class PrecomputedPedigreeConnector(
        val childIndex: Int,
        val parentIndex: Int,
        val cX: Float,
        val cY: Float,
        val pX: Float,
        val pY: Float,
        val midX: Float,
        val isFilled: Boolean
    )

    private val precomputedMaritalLines = mutableListOf<PrecomputedMaritalLine>()
    private val precomputedBuses = mutableListOf<PrecomputedFamilyBus>()
    private val precomputedFallbackLines = mutableListOf<PrecomputedFallbackLine>()
    private val precomputedPedigreeConnectors = mutableListOf<PrecomputedPedigreeConnector>()

    // ── State ─────────────────────────────────────────────────────
    private var allPersons: List<Person> = emptyList()
    private var displayPersons: List<Person> = emptyList()

    private var mode = "MAIN"
    private var highlightIds: Set<String> = emptySet()
    private var mrcaPersonId: String? = null
    private var focalPersonId: String? = null
    private var tracePersonAId: String? = null
    private var tracePersonBId: String? = null

    data class FamilyUnit(
        val primary: Person,
        val spouse: Person?,
        var generation: Int,
        val children: MutableList<FamilyUnit> = mutableListOf()
    ) {
        var width: Float = 0f
        var x: Float = 0f
    }
    private val familyUnits = mutableListOf<FamilyUnit>()

    private val posMap = mutableMapOf<String, PointF>()
    private var canvasW = 0f
    private var canvasH = 0f

    // In-memory circular bitmap cache
    private val avatarBitmapCache = mutableMapOf<String, Bitmap>()

    data class PedigreeSlot(
        val index: Int,            // Ahnentafel index 1..31
        val level: Int,            // 0..4
        val childIndex: Int?,      // Index of child slot
        val isFather: Boolean,
        val person: Person?,
        val childPerson: Person?,
        val relationLabel: String,
        val cx: Float,
        val cy: Float
    )
    private val pedigreeSlots = mutableListOf<PedigreeSlot>()

    private data class HitArea(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val tag: String,
        val w: Float = 0f,
        val h: Float = 0f
    )
    private val hitAreas = mutableListOf<HitArea>()

    private var offsetX = 0f
    private var offsetY = 0f
    private var scaleFactor = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                scaleFactor = (scaleFactor * d.scaleFactor).coerceIn(0.2f, 3.5f)
                val focusX = d.focusX
                val focusY = d.focusY
                val pivotX = width / 2f
                val pivotY = height / 2f

                offsetX += (focusX - pivotX) * (1f / scaleFactor - 1f / oldScale)
                offsetY += (focusY - pivotY) * (1f / scaleFactor - 1f / oldScale)
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                offsetX -= dx; offsetY -= dy; invalidate(); return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x, e.y); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scaleFactor > 1.6f) {
                    resetView()
                } else {
                    val oldScale = scaleFactor
                    scaleFactor = (scaleFactor * 1.6f).coerceAtMost(3.5f)
                    val focusX = e.x
                    val focusY = e.y
                    val pivotX = width / 2f
                    val pivotY = height / 2f
                    offsetX += (focusX - pivotX) * (1f / scaleFactor - 1f / oldScale)
                    offsetY += (focusY - pivotY) * (1f / scaleFactor - 1f / oldScale)
                    invalidate()
                }
                return true
            }
        })

    fun zoomIn() { scaleFactor = (scaleFactor * 1.3f).coerceIn(0.25f, 3.5f); invalidate() }
    fun zoomOut() { scaleFactor = (scaleFactor / 1.3f).coerceIn(0.25f, 3.5f); invalidate() }
    fun resetView() { scaleFactor = 1.0f; offsetX = 0f; offsetY = 0f; invalidate() }

    fun frameTracePath() {
        val targetIds = highlightIds + listOfNotNull(mrcaPersonId, tracePersonAId, tracePersonBId)
        val targetPoints = targetIds.mapNotNull { posMap[it] }
        if (targetPoints.isEmpty() || width <= 0 || height <= 0) return

        val minX = targetPoints.minOf { it.x } - CARD_W
        val maxX = targetPoints.maxOf { it.x } + CARD_W
        val minY = targetPoints.minOf { it.y } - CARD_H
        val maxY = targetPoints.maxOf { it.y } + CARD_H

        val pathW = maxOf(maxX - minX, CARD_W * 2)
        val pathH = maxOf(maxY - minY, CARD_H * 2)

        val fitScaleX = width * 0.85f / pathW
        val fitScaleY = height * 0.85f / pathH
        val newScale = minOf(fitScaleX, fitScaleY).coerceIn(0.4f, 1.4f)

        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        scaleFactor = newScale
        offsetX = (width / 2f - midX) - (width - canvasW) / 2f
        offsetY = (height / 2f - midY) - MARGIN / 2f
        invalidate()
    }

    fun setDataAndMode(
        list: List<Person>,
        mode: String,
        highlightIds: Set<String>,
        mrcaId: String?,
        focalPedigreeId: String?,
        personAId: String? = null,
        personBId: String? = null,
        forceRefresh: Boolean = false
    ) {
        val sameData = !forceRefresh &&
                allPersons == list &&
                this.mode == mode &&
                this.highlightIds == highlightIds &&
                this.mrcaPersonId == mrcaId &&
                this.focalPersonId == focalPedigreeId &&
                this.tracePersonAId == personAId &&
                this.tracePersonBId == personBId

        allPersons = list
        this.mode = mode
        this.highlightIds = highlightIds
        this.mrcaPersonId = mrcaId
        this.focalPersonId = focalPedigreeId
        this.tracePersonAId = personAId
        this.tracePersonBId = personBId

        // Evict removed persons' bitmaps to prevent native memory leaks
        val currentIds = list.map { it.id }.toSet()
        val it = avatarBitmapCache.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!currentIds.contains(entry.key)) {
                if (!entry.value.isRecycled) {
                    entry.value.recycle()
                }
                it.remove()
            }
        }
        updateAvatarBitmaps(list)

        if (!sameData || displayPersons.isEmpty()) {
            updateDisplayPersons()
        }
        if (mode == "TRACE_HIGHLIGHT") {
            post { frameTracePath() }
        }
    }

    fun evictAvatar(personId: String) {
        val bmp = avatarBitmapCache.remove(personId)
        if (bmp != null && !bmp.isRecycled) {
            bmp.recycle()
        }
    }

    fun getAllPersons(): List<Person> = allPersons

    fun setPersons(list: List<Person>) {
        if (allPersons == list && displayPersons.isNotEmpty()) return
        allPersons = list
        // Evict removed persons' bitmaps to prevent native memory leaks
        val currentIds = list.map { it.id }.toSet()
        val it = avatarBitmapCache.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!currentIds.contains(entry.key)) {
                if (!entry.value.isRecycled) {
                    entry.value.recycle()
                }
                it.remove()
            }
        }
        updateAvatarBitmaps(list)
        updateDisplayPersons()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        avatarBitmapCache.values.forEach { bmp ->
            if (!bmp.isRecycled) {
                bmp.recycle()
            }
        }
        avatarBitmapCache.clear()
        cardDisplayCache.clear()
    }

    private fun updateAvatarBitmaps(list: List<Person>) {
        val diameter = (NODE_RADIUS * 2).toInt()
        val toDecode = list.filter { it.photoBase64.isNotEmpty() && !avatarBitmapCache.containsKey(it.id) }
        if (toDecode.isEmpty()) return

        Thread {
            toDecode.forEach { p ->
                val raw = DocumentHelper.decodeBase64Bitmap(p.photoBase64)
                if (raw != null) {
                    val circ = DocumentHelper.getCircularBitmap(raw, diameter)
                    if (raw != circ && !raw.isRecycled) {
                        raw.recycle()
                    }
                    avatarBitmapCache[p.id] = circ
                }
            }
            postInvalidate()
        }.start()
    }

    fun setMode(
        mode: String,
        highlightIds: Set<String>,
        mrcaId: String?,
        focalPedigreeId: String?,
        personAId: String? = null,
        personBId: String? = null
    ) {
        if (this.mode == mode &&
            this.highlightIds == highlightIds &&
            this.mrcaPersonId == mrcaId &&
            this.focalPersonId == focalPedigreeId &&
            this.tracePersonAId == personAId &&
            this.tracePersonBId == personBId &&
            displayPersons.isNotEmpty()
        ) {
            return
        }
        this.mode = mode
        this.highlightIds = highlightIds
        this.mrcaPersonId = mrcaId
        this.focalPersonId = focalPedigreeId
        this.tracePersonAId = personAId
        this.tracePersonBId = personBId
        updateDisplayPersons()
        if (mode == "TRACE_HIGHLIGHT") {
            post { frameTracePath() }
        }
    }

    private fun updateDisplayPersons() {
        if (mode == "PEDIGREE" && !focalPersonId.isNullOrEmpty()) {
            val byId = allPersons.associateBy { it.id }
            val ancestorSet = mutableSetOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(focalPersonId!!)

            while (queue.isNotEmpty()) {
                val currId = queue.removeFirst()
                if (ancestorSet.add(currId)) {
                    val p = byId[currId] ?: continue
                    p.fatherId?.let { if (it.isNotEmpty()) queue.add(it) }
                    p.motherId?.let { if (it.isNotEmpty()) queue.add(it) }
                }
            }
            displayPersons = allPersons.filter { ancestorSet.contains(it.id) }
        } else {
            displayPersons = allPersons
        }

        computeLayout()
        invalidate()
    }

    private fun precomputeCardDisplayData() {
        cardDisplayCache.clear()
        val focalPerson = allPersons.find { it.id == focalPersonId }
        val maxTextWidth = CARD_W - 20f
        val tempPaintFirst = TextPaint(pFirstName).apply { textSize = 15f }
        val tempPaintLast = TextPaint(pLastName).apply { textSize = 13f }
        val tempPaintTitle = TextPaint(pTitleText).apply { textSize = 11f }
        val titleBarWidth = CARD_W - 16f - 8f

        // Fast O(N) single-pass title calculation
        val allTitles = KinshipTitleHelper.resolveAllTitles(allPersons, focalPerson)

        allPersons.forEach { p ->
            val first = TextUtils.ellipsize(p.firstName, tempPaintFirst, maxTextWidth, TextUtils.TruncateAt.END).toString()
            val last = TextUtils.ellipsize(p.lastName, tempPaintLast, maxTextWidth, TextUtils.TruncateAt.END).toString()

            val yr = p.birthDate.take(4)
            val dYr = if (!p.isLiving) {
                val d = p.deathDate.take(4)
                if (d.isNotEmpty()) "-$d" else "🕊️"
            } else ""
            val yrStr = if (yr.isNotEmpty()) {
                if (dYr.isNotEmpty()) "$yr $dYr" else "b. $yr"
            } else dYr

            val rawTitle = allTitles[p.id] ?: KinshipTitleHelper.resolveTitle(p, focalPerson, allPersons)
            val titleWithIcon = if (!p.isLiving) "🕊️ $rawTitle" else rawTitle
            val title = TextUtils.ellipsize(titleWithIcon, tempPaintTitle, titleBarWidth, TextUtils.TruncateAt.END).toString()

            val initials = "${p.firstName.firstOrNull() ?: ""}${p.lastName.firstOrNull() ?: ""}".uppercase()
            val fullName = "${p.firstName} ${p.lastName}".trim()
            val pedigreeFullName = TextUtils.ellipsize(fullName, tempPaintFirst, 140f, TextUtils.TruncateAt.END).toString()

            cardDisplayCache[p.id] = CardDisplayData(
                displayFirstName = first,
                displayLastName = last,
                displayYear = yrStr,
                kinshipTitle = title,
                initials = initials,
                displayFullName = pedigreeFullName
            )
        }
    }

    private fun computeLayout() {
        posMap.clear()
        pedigreeSlots.clear()
        if (allPersons.isEmpty()) return

        if (mode == "PEDIGREE" && !focalPersonId.isNullOrEmpty()) {
            computeHorizontalPedigreeLayout()
        } else {
            computeMainDownwardLayout()
        }
        precomputeCardDisplayData()
    }

    // ── Horizontal Ancestor Pedigree (Matching Reference Diagram) ─────
    private fun computeHorizontalPedigreeLayout() {
        val byId = allPersons.associateBy { it.id }
        val focal = byId[focalPersonId] ?: return

        // 5 Generation Ahnentafel binary tree: slots 1..31
        // Level 0: 1, Level 1: 2..3, Level 2: 4..7, Level 3: 8..15, Level 4: 16..31
        val maxLevel = 4
        val totalSlotsGen4 = 16
        canvasH = MARGIN * 2 + totalSlotsGen4 * SLOT_H
        canvasW = MARGIN * 2 + (maxLevel + 1) * COL_GAP

        // Calculate Y for Level 4 (indices 16..31)
        val yCoords = mutableMapOf<Int, Float>()
        for (k in 0 until 16) {
            val idx = 16 + k
            yCoords[idx] = MARGIN + (k + 0.5f) * SLOT_H
        }

        // Calculate Y upward from Level 3 to Level 0 as midpoint of father & mother
        for (idx in 15 downTo 1) {
            val yFather = yCoords[2 * idx] ?: (MARGIN + 0.5f * SLOT_H)
            val yMother = yCoords[2 * idx + 1] ?: (MARGIN + 1.5f * SLOT_H)
            yCoords[idx] = (yFather + yMother) / 2f
        }

        // Build slots map: Ahnentafel index -> Person?
        val personAtSlot = mutableMapOf<Int, Person>()
        val childAtSlot = mutableMapOf<Int, Person>()
        personAtSlot[1] = focal

        fun fillSlots(currIdx: Int, currPerson: Person, level: Int) {
            if (level >= maxLevel) return

            // Father slot
            val fIdx = 2 * currIdx
            childAtSlot[fIdx] = currPerson
            val father = currPerson.fatherId?.let { byId[it] }
            if (father != null) {
                personAtSlot[fIdx] = father
                fillSlots(fIdx, father, level + 1)
            }

            // Mother slot
            val mIdx = 2 * currIdx + 1
            childAtSlot[mIdx] = currPerson
            val mother = currPerson.motherId?.let { byId[it] }
            if (mother != null) {
                personAtSlot[mIdx] = mother
                fillSlots(mIdx, mother, level + 1)
            }
        }

        fillSlots(1, focal, 0)

        // Generate PedigreeSlot objects up to Level 4 (or where child person exists)
        for (idx in 1..31) {
            val level = getAhnentafelLevel(idx)
            val childIdx = if (idx == 1) null else idx / 2
            val isFather = idx % 2 == 0

            val person = personAtSlot[idx]
            val childPerson = childAtSlot[idx]

            // Only display slots that have a person, or whose direct child person exists
            if (person == null && childPerson == null) continue

            val cx = MARGIN + level * COL_GAP + NODE_RADIUS
            val cy = yCoords[idx] ?: (MARGIN + SLOT_H)

            val label = getAhnentafelRelation(idx)

            val slot = PedigreeSlot(
                index = idx,
                level = level,
                childIndex = childIdx,
                isFather = isFather,
                person = person,
                childPerson = childPerson,
                relationLabel = label,
                cx = cx,
                cy = cy
            )
            pedigreeSlots.add(slot)

            if (person != null) {
                posMap[person.id] = PointF(cx, cy)
            }
        }
        precomputePedigreeConnectorsAndHitAreas()
    }

    private fun precomputePedigreeConnectorsAndHitAreas() {
        precomputedPedigreeConnectors.clear()
        hitAreas.clear()

        val slotByIndex = pedigreeSlots.associateBy { it.index }
        pedigreeSlots.forEach { slot ->
            val childIdx = slot.childIndex
            if (childIdx != null) {
                val childSlot = slotByIndex[childIdx]
                if (childSlot != null) {
                    val cX = childSlot.cx + NODE_RADIUS
                    val cY = childSlot.cy
                    val pX = slot.cx - NODE_RADIUS
                    val pY = slot.cy
                    val midX = (cX + pX) / 2f
                    precomputedPedigreeConnectors.add(
                        PrecomputedPedigreeConnector(
                            childIndex = childIdx,
                            parentIndex = slot.index,
                            cX = cX,
                            cY = cY,
                            pX = pX,
                            pY = pY,
                            midX = midX,
                            isFilled = slot.person != null
                        )
                    )
                }
            }

            val cx = slot.cx
            val cy = slot.cy
            if (slot.person != null) {
                hitAreas.add(HitArea(cx, cy, NODE_RADIUS, slot.person.id))
            } else {
                val childId = slot.childPerson?.id ?: ""
                hitAreas.add(HitArea(cx, cy, NODE_RADIUS, "ancestor:$childId:${slot.isFather}"))
            }
        }
    }

    private fun getAhnentafelLevel(idx: Int): Int {
        return when {
            idx == 1 -> 0
            idx in 2..3 -> 1
            idx in 4..7 -> 2
            idx in 8..15 -> 3
            else -> 4
        }
    }

    private fun getAhnentafelRelation(idx: Int): String {
        return when (idx) {
            1 -> "Self"
            2 -> "Father"
            3 -> "Mother"
            4, 6 -> "Grandfather"
            5, 7 -> "Grandmother"
            in 8..15 -> if (idx % 2 == 0) "Great-GF" else "Great-GM"
            else -> if (idx % 2 == 0) "Gr-Gr-GF" else "Gr-Gr-GM"
        }
    }

    // ── Main Downward Genealogical Tree Layout ────────────────────────
    private fun computeMainDownwardLayout() {
        familyUnits.clear()
        if (displayPersons.isEmpty()) return

        fun canon(id: String?): String = id?.trim()?.lowercase().orEmpty()

        val byId = displayPersons.associateBy { it.id }
        val byCanonId = displayPersons.associateBy { canon(it.id) }
        val childrenOf = mutableMapOf<String, MutableSet<String>>()
        displayPersons.forEach { p ->
            listOfNotNull(p.fatherId, p.motherId).map { canon(it) }.filter { it.isNotBlank() }.forEach { pid ->
                val parentObj = byCanonId[pid]
                if (parentObj != null) {
                    childrenOf.getOrPut(canon(parentObj.id)) { mutableSetOf() }.add(p.id)
                    childrenOf.getOrPut(parentObj.id) { mutableSetOf() }.add(p.id)
                }
            }
        }

        // Mutual spouse resolution (explicit takes priority, inferred shared children only if valid and not prohibited)
        val spouseMap = mutableMapOf<String, String>()
        displayPersons.forEach { p ->
            val sId = p.spouseId?.trim()
            if (!sId.isNullOrBlank()) {
                val spObj = displayPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, sId) }
                if (spObj != null) {
                    val spCheck = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(p, spObj, displayPersons)
                    if (spCheck.isValid) {
                        spouseMap[p.id] = spObj.id
                        spouseMap[spObj.id] = p.id
                    }
                }
            }
        }
        displayPersons.forEach { p ->
            val fId = p.fatherId?.trim()
            val mId = p.motherId?.trim()
            if (!fId.isNullOrBlank() && !mId.isNullOrBlank()) {
                val fObj = displayPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, fId) }
                val mObj = displayPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, mId) }
                if (fObj != null && mObj != null) {
                    if (!spouseMap.containsKey(fObj.id) && !spouseMap.containsKey(mObj.id)) {
                        val coParentCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(fObj, mObj, displayPersons)
                        if (coParentCheck.isValid) {
                            spouseMap[fObj.id] = mObj.id
                            spouseMap[mObj.id] = fObj.id
                        }
                    }
                }
            }
        }

        // ── 1. Topological Generation Calculation (Global Universal Hierarchy Rule) ──
        // Enforces: parentGeneration < childGeneration for all parent-child relationships,
        // aligns married couples and co-parents to the same generation level,
        // and keeps siblings on identical generational tiers.
        val genMap = com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations0Based(
            persons = displayPersons,
            explicitSpouseMap = spouseMap
        )

        // Build Family Units
        val unitMap = mutableMapOf<String, FamilyUnit>()
        val allUnits = mutableListOf<FamilyUnit>()

        displayPersons.forEach { p ->
            if (unitMap.containsKey(p.id) || unitMap.containsKey(canon(p.id))) return@forEach

            val rawSp = spouseMap[p.id]?.let { byId[it] ?: byCanonId[canon(it)] }
            val sp = rawSp

            val (primary, spouse) = if (p.gender.equals("female", ignoreCase = true) && sp != null && sp.gender.equals("male", ignoreCase = true)) {
                sp to p
            } else {
                p to sp
            }

            val gen = maxOf(genMap[primary.id] ?: genMap[canon(primary.id)] ?: 0, spouse?.let { genMap[it.id] ?: genMap[canon(it.id)] } ?: 0)
            val unit = FamilyUnit(primary, spouse, gen)
            unitMap[primary.id] = unit
            unitMap[canon(primary.id)] = unit
            if (spouse != null) {
                unitMap[spouse.id] = unit
                unitMap[canon(spouse.id)] = unit
            }
            allUnits.add(unit)
        }

        // Cycle detection helper to prevent tree mangling
        fun wouldCauseCycle(parent: FamilyUnit, child: FamilyUnit): Boolean {
            if (parent == child) return true
            val visited = mutableSetOf<FamilyUnit>()
            val queue = ArrayDeque<FamilyUnit>()
            queue.add(child)
            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                if (curr == parent) return true
                if (visited.add(curr)) {
                    queue.addAll(curr.children)
                }
            }
            return false
        }

        // Connect parent units to child units with deduplication and cycle prevention:
        // A child unit must be claimed by at most ONE parent unit to prevent duplicate positioning and cross-tree bus distortion.
        val claimedChildUnits = mutableSetOf<FamilyUnit>()

        // Pass 1: Coupled parent units (father + mother) get first priority
        allUnits.filter { it.spouse != null }.forEach { unit ->
            val parentKeys = listOfNotNull(unit.primary.id, canon(unit.primary.id), unit.spouse?.id, canon(unit.spouse?.id))
            val childIds = parentKeys.flatMap { pid -> childrenOf[pid].orEmpty() }.distinct()

            val seenInUnit = mutableSetOf<FamilyUnit>()
            childIds.forEach { cId ->
                val cUnit = unitMap[cId] ?: unitMap[canon(cId)]
                if (cUnit != null && cUnit != unit && cUnit !in claimedChildUnits && seenInUnit.add(cUnit)) {
                    // Check that cUnit is NOT a grandchild whose direct parent unit already exists in the tree
                    val isGrandchildOfUnit = allUnits.any { intermediateUnit ->
                        intermediateUnit != unit &&
                        (intermediateUnit.primary.id == unit.primary.id || intermediateUnit.spouse?.id == unit.primary.id ||
                         parentKeys.any { pid -> childrenOf[pid]?.contains(intermediateUnit.primary.id) == true } ||
                         parentKeys.any { pid -> intermediateUnit.spouse != null && childrenOf[pid]?.contains(intermediateUnit.spouse.id) == true }) &&
                        (childrenOf[intermediateUnit.primary.id]?.contains(cId) == true ||
                         (intermediateUnit.spouse != null && childrenOf[intermediateUnit.spouse.id]?.contains(cId) == true))
                    }

                    if (!isGrandchildOfUnit && !wouldCauseCycle(unit, cUnit)) {
                        if (cUnit.generation <= unit.generation) {
                            cUnit.generation = unit.generation + 1
                        }
                        unit.children.add(cUnit)
                        claimedChildUnits.add(cUnit)
                    }
                }
            }
            unit.children.sortBy { it.primary.birthDate.ifEmpty { it.primary.firstName } }
        }

        // Pass 2: Single-parent units claim any remaining unclaimed children
        allUnits.filter { it.spouse == null }.forEach { unit ->
            val parentKeys = listOfNotNull(unit.primary.id, canon(unit.primary.id))
            val childIds = parentKeys.flatMap { pid -> childrenOf[pid].orEmpty() }.distinct()

            val seenInUnit = mutableSetOf<FamilyUnit>()
            childIds.forEach { cId ->
                val cUnit = unitMap[cId] ?: unitMap[canon(cId)]
                if (cUnit != null && cUnit != unit && cUnit !in claimedChildUnits && seenInUnit.add(cUnit)) {
                    if (!wouldCauseCycle(unit, cUnit)) {
                        if (cUnit.generation <= unit.generation) {
                            cUnit.generation = unit.generation + 1
                        }
                        unit.children.add(cUnit)
                        claimedChildUnits.add(cUnit)
                    }
                }
            }
            unit.children.sortBy { it.primary.birthDate.ifEmpty { it.primary.firstName } }
        }

        // Root units are those with no parent unit claiming them in the tree
        val childUnitsSet = mutableSetOf<FamilyUnit>()
        allUnits.forEach { childUnitsSet.addAll(it.children) }
        val rootUnits = (allUnits.filter { !childUnitsSet.contains(it) }.ifEmpty { listOfNotNull(allUnits.minByOrNull { it.generation }) }).sortedBy { it.generation }

        // Recursive push-down to guarantee parentGeneration < childGeneration for all tree descendants
        fun enforceDownwardGenerations(u: FamilyUnit, visited: MutableSet<FamilyUnit>) {
            if (!visited.add(u)) return
            u.children.forEach { ch ->
                if (ch.generation <= u.generation) {
                    ch.generation = u.generation + 1
                }
                enforceDownwardGenerations(ch, visited)
            }
        }
        val genPushVisited = mutableSetOf<FamilyUnit>()
        rootUnits.forEach { enforceDownwardGenerations(it, genPushVisited) }

        // Store units for connector rendering
        familyUnits.addAll(allUnits)

        // Compute Subtree Widths with cycle guard
        val visitedWidth = mutableSetOf<FamilyUnit>()
        fun computeWidth(u: FamilyUnit): Float {
            if (!visitedWidth.add(u)) return u.width
            val ownW = if (u.spouse != null) (2 * CARD_W + COUPLE_GAP) else CARD_W
            if (u.children.isEmpty()) {
                u.width = ownW
                return u.width
            }
            var kidsTotal = 0f
            u.children.forEachIndexed { idx, ch ->
                kidsTotal += computeWidth(ch)
                if (idx > 0) kidsTotal += SIBLING_GAP
            }
            u.width = maxOf(ownW, kidsTotal)
            return u.width
        }

        rootUnits.forEach { computeWidth(it) }

        // Assign Coordinates recursively with cycle guard
        val visitedPos = mutableSetOf<FamilyUnit>()
        fun assignPos(u: FamilyUnit, centerX: Float) {
            if (!visitedPos.add(u)) return
            u.x = centerX
            val yPos = MARGIN + u.generation * ROW_GAP + CARD_H / 2f

            if (u.spouse != null) {
                posMap[u.primary.id] = PointF(centerX - CARD_W / 2f - COUPLE_GAP / 2f, yPos)
                posMap[u.spouse.id] = PointF(centerX + CARD_W / 2f + COUPLE_GAP / 2f, yPos)
            } else {
                posMap[u.primary.id] = PointF(centerX, yPos)
            }

            if (u.children.isNotEmpty()) {
                val kidsTotalW = u.children.sumOf { it.width.toDouble() }.toFloat() +
                        (u.children.size - 1) * SIBLING_GAP
                var curLeft = centerX - kidsTotalW / 2f
                u.children.forEach { ch ->
                    val cCenter = curLeft + ch.width / 2f
                    assignPos(ch, cCenter)
                    curLeft += ch.width + SIBLING_GAP
                }
            }
        }

        var startX = MARGIN
        rootUnits.forEach { ru ->
            val center = startX + ru.width / 2f
            assignPos(ru, center)
            startX += ru.width + BRANCH_GAP
        }

        // Safety fallback for any unplaced persons
        displayPersons.forEach { p ->
            if (!posMap.containsKey(p.id)) {
                val g = genMap[p.id] ?: 0
                val maxXInGen = posMap.filter { (id, _) -> (genMap[id] ?: 0) == g }.values.maxOfOrNull { it.x } ?: (MARGIN - COL_GAP)
                posMap[p.id] = PointF(maxXInGen + COL_GAP, MARGIN + g * ROW_GAP + CARD_H / 2f)
            }
        }

        // Canvas dimensions & coordinate normalization
        val allX = posMap.values.map { it.x }
        val allY = posMap.values.map { it.y }
        val minX = (allX.minOrNull() ?: MARGIN) - CARD_W / 2f - MARGIN
        val maxX = (allX.maxOrNull() ?: MARGIN) + CARD_W / 2f + MARGIN
        val minY = (allY.minOrNull() ?: MARGIN) - CARD_H / 2f - MARGIN
        val maxY = (allY.maxOrNull() ?: MARGIN) + CARD_H / 2f + MARGIN

        val shiftX = if (minX < 0f) -minX else 0f
        val shiftY = if (minY < 0f) -minY else 0f

        if (shiftX > 0f || shiftY > 0f) {
            posMap.values.forEach {
                it.x += shiftX
                it.y += shiftY
            }
            familyUnits.forEach {
                it.x += shiftX
            }
        }

        canvasW = maxOf(maxX + shiftX, 1200f)
        canvasH = maxOf(maxY + shiftY, 800f)
        precomputeMainTreeConnectorsAndHitAreas()
    }

    private fun precomputeMainTreeConnectorsAndHitAreas() {
        precomputedMaritalLines.clear()
        precomputedBuses.clear()
        precomputedFallbackLines.clear()
        hitAreas.clear()

        // 1. Precompute Marital Lines and Buses
        familyUnits.forEach { unit ->
            val pPos = posMap[unit.primary.id] ?: return@forEach
            val sPos = unit.spouse?.let { posMap[it.id] }

            if (sPos != null && mode == "MAIN") {
                val leftCardX = minOf(pPos.x, sPos.x)
                val rightCardX = maxOf(pPos.x, sPos.x)
                val leftX = leftCardX + CARD_W / 2f
                val rightX = rightCardX - CARD_W / 2f
                val pillMidX = (leftX + rightX) / 2f
                val pillMidY = pPos.y
                precomputedMaritalLines.add(
                    PrecomputedMaritalLine(
                        primaryId = unit.primary.id,
                        spouseId = unit.spouse.id,
                        leftX = leftX,
                        rightX = rightX,
                        y = pPos.y,
                        pillMidX = pillMidX,
                        pillMidY = pillMidY
                    )
                )
            }

            if (unit.children.isNotEmpty()) {
                val yParent = pPos.y
                val parentCardBottom = yParent + CARD_H / 2f
                val yBus = parentCardBottom + V_GAP * 0.5f
                val dropStartY = if (unit.spouse != null) pPos.y + 12f else parentCardBottom + ADD_BTN_R

                val childDrops = mutableListOf<PrecomputedChildDrop>()
                var busLeft = unit.x
                var busRight = unit.x

                unit.children.forEach { ch ->
                    val targetPerson = getBiologicalChild(unit, ch)
                    val isJointOrSingle = (unit.spouse == null) || (
                        (com.example.btproject2.engine.FamilyLinkValidator.isSameId(unit.primary.id, targetPerson.fatherId) ||
                         com.example.btproject2.engine.FamilyLinkValidator.isSameId(unit.primary.id, targetPerson.motherId)) &&
                        (com.example.btproject2.engine.FamilyLinkValidator.isSameId(unit.spouse?.id, targetPerson.fatherId) ||
                         com.example.btproject2.engine.FamilyLinkValidator.isSameId(unit.spouse?.id, targetPerson.motherId))
                    )
                    if (isJointOrSingle) {
                        val cPos = posMap[targetPerson.id]
                        if (cPos != null) {
                            val targetY = cPos.y - CARD_H / 2f
                            childDrops.add(
                                PrecomputedChildDrop(
                                    childId = targetPerson.id,
                                    x = cPos.x,
                                    yBus = yBus,
                                    targetY = targetY
                                )
                            )
                            if (cPos.x < busLeft) busLeft = cPos.x
                            if (cPos.x > busRight) busRight = cPos.x
                        }
                    }
                }

                if (childDrops.isNotEmpty()) {
                    precomputedBuses.add(
                        PrecomputedFamilyBus(
                            primaryId = unit.primary.id,
                            spouseId = unit.spouse?.id,
                            dropX = unit.x,
                            dropStartY = dropStartY,
                            yBus = yBus,
                            busLeft = busLeft,
                            busRight = busRight,
                            childDrops = childDrops
                        )
                    )
                }
            }
        }

        // 2. Precompute Fallback Lines (O(1) set lookup instead of O(N^3) nested traversal per frame)
        val coveredParentChild = HashSet<Pair<String, String>>()
        familyUnits.forEach { u ->
            u.children.forEach { ch ->
                val child = getBiologicalChild(u, ch)
                val cFather = child.fatherId?.trim()
                val cMother = child.motherId?.trim()
                val isJointOrSingle = (u.spouse == null) || (
                    (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, cFather) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, cMother)) &&
                    (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse?.id, cFather) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse?.id, cMother))
                )
                if (isJointOrSingle) {
                    if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, cFather) ||
                        com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, cMother)
                    ) {
                        coveredParentChild.add(Pair(u.primary.id, child.id))
                    }
                    if (u.spouse != null && (
                        com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse.id, cFather) ||
                        com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse.id, cMother))
                    ) {
                        coveredParentChild.add(Pair(u.spouse.id, child.id))
                    }
                }
            }
        }

        displayPersons.forEach { p ->
            val cPos = posMap[p.id] ?: return@forEach
            val parents = listOfNotNull(p.fatherId, p.motherId).filter { it.isNotBlank() }
            parents.forEach { pid ->
                if (!coveredParentChild.contains(Pair(pid, p.id))) {
                    val pPos = posMap[pid]
                    if (pPos != null) {
                        val pY = pPos.y + CARD_H / 2f
                        val cY = cPos.y - CARD_H / 2f
                        val isStep = cY > pY
                        val midY = (pY + cY) / 2f
                        precomputedFallbackLines.add(
                            PrecomputedFallbackLine(
                                parentId = pid,
                                childId = p.id,
                                pX = pPos.x,
                                pY = pY,
                                cX = cPos.x,
                                cY = cY,
                                isStep = isStep,
                                midY = midY
                            )
                        )
                    }
                }
            }
        }

        // 3. Precompute Hit Areas for Cards and Quick Add Buttons
        displayPersons.forEach { p ->
            val pos = posMap[p.id] ?: return@forEach
            hitAreas.add(HitArea(pos.x, pos.y, NODE_RADIUS, p.id, w = CARD_W, h = CARD_H))
            if (mode == "MAIN") {
                val btnY = pos.y + CARD_H / 2f
                hitAreas.add(HitArea(pos.x, btnY, ADD_BTN_R + 10f, "add:${p.id}"))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Empty state
        if (allPersons.isEmpty()) {
            canvas.drawText("🌳  No members yet", width / 2f, height / 2f - 30f, pEmpty)
            canvas.drawText("Tap + to add your first member", width / 2f, height / 2f + 20f, pEmptySub)
            val fx = width - 80f; val fy = height - 80f
            canvas.drawCircle(fx, fy, 50f, pFab)
            canvas.drawText("+", fx, fy + 18f, pFabIcon)
            return
        }

        val originX = offsetX + (width  - canvasW) / 2f
        val originY = offsetY + MARGIN / 2f
        val pivotX  = width  / 2f
        val pivotY  = height / 2f

        // Calculate visible viewport frustum bounds in tree coordinates
        val visibleLeft   = (0f - pivotX) / scaleFactor + pivotX - originX
        val visibleRight  = (width.toFloat() - pivotX) / scaleFactor + pivotX - originX
        val visibleTop    = (0f - pivotY) / scaleFactor + pivotY - originY
        val visibleBottom = (height.toFloat() - pivotY) / scaleFactor + pivotY - originY

        val cullLeft   = visibleLeft - 200f
        val cullRight  = visibleRight + 200f
        val cullTop    = visibleTop - 200f
        val cullBottom = visibleBottom + 200f

        canvas.save()
        canvas.translate(pivotX, pivotY)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(-pivotX + originX, -pivotY + originY)

        val isTraceMode = mode == "TRACE_HIGHLIGHT" && (highlightIds.isNotEmpty() || mrcaPersonId != null)

        if (mode == "PEDIGREE") {
            drawHorizontalPedigree(canvas, cullLeft, cullRight, cullTop, cullBottom)
        } else {
            drawMainTree(canvas, isTraceMode, cullLeft, cullRight, cullTop, cullBottom)
        }

        canvas.restore()
    }

    // ── Drawing: Horizontal Pedigree Tree (User Diagram Matching) ────
    private fun drawHorizontalPedigree(canvas: Canvas, cullLeft: Float, cullRight: Float, cullTop: Float, cullBottom: Float) {
        // 1. Draw Orthogonal Stepped Branch Connectors (Zero allocation with Frustum Culling!)
        for (i in 0 until precomputedPedigreeConnectors.size) {
            val c = precomputedPedigreeConnectors[i]
            val minCX = minOf(c.cX, c.pX)
            val maxCX = maxOf(c.cX, c.pX)
            val minCY = minOf(c.cY, c.pY)
            val maxCY = maxOf(c.cY, c.pY)
            if (maxCX < cullLeft || minCX > cullRight || maxCY < cullTop || minCY > cullBottom) continue

            sharedPedigreePath.reset()
            sharedPedigreePath.moveTo(c.cX, c.cY)
            sharedPedigreePath.lineTo(c.midX, c.cY)
            sharedPedigreePath.lineTo(c.midX, c.pY)
            sharedPedigreePath.lineTo(c.pX, c.pY)

            val linePaint = if (c.isFilled) pPedigreeLine else pPedigreeDashedLine
            canvas.drawPath(sharedPedigreePath, linePaint)
        }

        // 2. Draw Nodes with Frustum Culling
        for (i in 0 until pedigreeSlots.size) {
            val slot = pedigreeSlots[i]
            val cx = slot.cx
            val cy = slot.cy

            if (cx < cullLeft || cx > cullRight || cy < cullTop || cy > cullBottom) {
                continue
            }

            if (slot.person != null) {
                // Filled Person Node (Pedigree Ahnentafel layout)
                drawPedigreeNode(canvas, slot.person, cx, cy, slot.relationLabel)
            } else {
                // Dashed Unadded Ancestor Slot (matching reference image)
                canvas.drawCircle(cx, cy, NODE_RADIUS, pDashedCircle)
                canvas.drawText("+", cx, cy + pAddIcon.textSize / 3f, pDashedText)

                // Labels
                canvas.drawText(slot.relationLabel, cx, cy + NODE_RADIUS + 34f, pDashedLabel)
                canvas.drawText("Name", cx, cy + NODE_RADIUS + 58f, pLastName)
            }
        }
    }

    // ── Drawing: Main Downward Tree & Trace Path ──────────────────────
    private fun drawMainTree(canvas: Canvas, isTraceMode: Boolean, cullLeft: Float, cullRight: Float, cullTop: Float, cullBottom: Float) {
        // 1. Marital Lines & Center Pills (Zero allocations!)
        for (i in 0 until precomputedMaritalLines.size) {
            val m = precomputedMaritalLines[i]
            if (m.rightX < cullLeft || m.leftX > cullRight || m.y < cullTop || m.y > cullBottom) continue

            val isMaritalTrace = isTraceMode &&
                    (highlightIds.contains(m.primaryId) && highlightIds.contains(m.spouseId))
            val mPaint = when {
                isMaritalTrace -> pGoldPathLine
                isTraceMode -> pSpouseLineDim
                else -> pSpouseLine
            }

            if (isMaritalTrace) {
                canvas.drawLine(m.leftX, m.y, m.rightX, m.y, pGoldPathGlow)
            }
            canvas.drawLine(m.leftX, m.y, m.rightX, m.y, mPaint)

            // Elegant Marital Center Pill: [ 💍 ]
            val mPillW = 36f
            val mPillH = 24f
            tempMaritalRect.set(m.pillMidX - mPillW / 2f, m.pillMidY - mPillH / 2f, m.pillMidX + mPillW / 2f, m.pillMidY + mPillH / 2f)
            val mBg = if (isTraceMode && !isMaritalTrace) pMaritalPillBgDim else pMaritalPillBg
            val mBorder = if (isMaritalTrace) pGoldHighlightBorder else if (isTraceMode) pMaritalPillBorderDim else pMaritalPillBorder
            val mTxt = if (isTraceMode && !isMaritalTrace) pMaritalPillTextDim else pMaritalPillText
            canvas.drawRoundRect(tempMaritalRect, mPillH / 2f, mPillH / 2f, mBg)
            canvas.drawRoundRect(tempMaritalRect, mPillH / 2f, mPillH / 2f, mBorder)
            canvas.drawText("💍", m.pillMidX, m.pillMidY + 5f, mTxt)
        }

        // 2. Parent Drop -> Horizontal Bus -> Child Drops (Zero allocations with Frustum Culling!)
        for (i in 0 until precomputedBuses.size) {
            val bus = precomputedBuses[i]
            val minBusX = minOf(bus.dropX, bus.busLeft)
            val maxBusX = maxOf(bus.dropX, bus.busRight)
            val minBusY = bus.dropStartY
            val maxBusY = bus.childDrops.maxOfOrNull { it.targetY } ?: bus.yBus
            if (maxBusX < cullLeft || minBusX > cullRight || maxBusY < cullTop || minBusY > cullBottom) continue

            val isParentOnTrace = isTraceMode && (
                highlightIds.contains(bus.primaryId) ||
                (bus.spouseId != null && highlightIds.contains(bus.spouseId)) ||
                bus.primaryId == mrcaPersonId ||
                bus.spouseId == mrcaPersonId
            )

            // Draw default parent drop & bus bar
            val busBasePaint = if (isTraceMode) pLineDim else pLine
            canvas.drawLine(bus.dropX, bus.dropStartY, bus.dropX, bus.yBus, busBasePaint)
            canvas.drawLine(bus.busLeft, bus.yBus, bus.busRight, bus.yBus, busBasePaint)

            // Draw vertical drop into top of each child card
            for (j in 0 until bus.childDrops.size) {
                val drop = bus.childDrops[j]
                if (drop.x < cullLeft || drop.x > cullRight || drop.targetY < cullTop || drop.yBus > cullBottom) continue

                val isChildOnTrace = isTraceMode && (highlightIds.contains(drop.childId) || drop.childId == mrcaPersonId)
                val isSegmentTrace = isParentOnTrace && isChildOnTrace

                if (isSegmentTrace) {
                    // Double-pass glowing bloodline path
                    sharedTracePath.reset()
                    sharedTracePath.moveTo(bus.dropX, bus.dropStartY)
                    sharedTracePath.lineTo(bus.dropX, bus.yBus)
                    sharedTracePath.lineTo(drop.x, bus.yBus)
                    sharedTracePath.lineTo(drop.x, drop.targetY)
                    canvas.drawPath(sharedTracePath, pGoldPathGlow)
                    canvas.drawPath(sharedTracePath, pGoldPathLine)
                } else {
                    if (drop.targetY > drop.yBus) {
                        val cLinePaint = if (isTraceMode) pLineDim else pLine
                        canvas.drawLine(drop.x, drop.yBus, drop.x, drop.targetY, cLinePaint)
                    }
                }
            }
        }

        // 3. Safety fallback for any parent-child link not covered by family units (Zero allocations with Frustum Culling!)
        for (i in 0 until precomputedFallbackLines.size) {
            val f = precomputedFallbackLines[i]
            val minFX = minOf(f.pX, f.cX)
            val maxFX = maxOf(f.pX, f.cX)
            val minFY = minOf(f.pY, f.cY)
            val maxFY = maxOf(f.pY, f.cY)
            if (maxFX < cullLeft || minFX > cullRight || maxFY < cullTop || minFY > cullBottom) continue

            val isPOnPath = highlightIds.contains(f.childId) || f.childId == mrcaPersonId
            val isParentOnPath = highlightIds.contains(f.parentId) || f.parentId == mrcaPersonId
            val isLineOnTrace = isTraceMode && isPOnPath && isParentOnPath

            sharedLinePath.reset()
            if (f.isStep) {
                sharedLinePath.moveTo(f.pX, f.pY)
                sharedLinePath.lineTo(f.pX, f.midY)
                sharedLinePath.lineTo(f.cX, f.midY)
                sharedLinePath.lineTo(f.cX, f.cY)
            } else {
                sharedLinePath.moveTo(f.pX, f.pY)
                val ctrlOffset = 100f
                sharedLinePath.cubicTo(
                    f.pX + ctrlOffset, f.pY,
                    f.cX + ctrlOffset, f.cY,
                    f.cX, f.cY
                )
            }

            if (isLineOnTrace) {
                canvas.drawPath(sharedLinePath, pGoldPathGlow)
                canvas.drawPath(sharedLinePath, pGoldPathLine)
            } else {
                val currentLinePaint = if (isTraceMode) pLineDim else pLine
                canvas.drawPath(sharedLinePath, currentLinePaint)
            }
        }

        // 4. Person Cards (With Viewport Frustum Culling!)
        for (i in 0 until displayPersons.size) {
            val p = displayPersons[i]
            val pos = posMap[p.id] ?: continue

            // Viewport Frustum Culling: skip rendering if offscreen!
            if (pos.x < cullLeft || pos.x > cullRight || pos.y < cullTop || pos.y > cullBottom) {
                continue
            }

            val isMrca = p.id == mrcaPersonId
            val isOnPath = highlightIds.contains(p.id) || isMrca

            drawPersonCard(canvas, p, pos.x, pos.y, isTraceMode, isOnPath, isMrca)

            // Add Child Quick Button docked at bottom center of card (only in Main mode when not dimmed)
            if (mode == "MAIN" && (!isTraceMode || isOnPath)) {
                val btnY = pos.y + CARD_H / 2f
                canvas.drawCircle(pos.x, btnY, ADD_BTN_R, pAddBtn)
                canvas.drawCircle(pos.x, btnY, ADD_BTN_R, pAddBtnBorder)
                canvas.drawText("+", pos.x, btnY + pAddIcon.textSize / 3f, pAddIcon)
            }
        }
    }

    private fun getBiologicalChild(parentUnit: FamilyUnit, childUnit: FamilyUnit): Person {
        val parentIds = setOfNotNull(
            parentUnit.primary.id.trim().lowercase(),
            parentUnit.spouse?.id?.trim()?.lowercase()
        )
        if (childUnit.spouse != null) {
            val s = childUnit.spouse
            val sFather = s.fatherId?.trim()?.lowercase()
            val sMother = s.motherId?.trim()?.lowercase()
            val spouseIsChild = parentIds.contains(sFather) || parentIds.contains(sMother)

            val p = childUnit.primary
            val pFather = p.fatherId?.trim()?.lowercase()
            val pMother = p.motherId?.trim()?.lowercase()
            val primaryIsChild = parentIds.contains(pFather) || parentIds.contains(pMother)

            if (spouseIsChild && !primaryIsChild) {
                return childUnit.spouse
            }
        }
        return childUnit.primary
    }

    // ── Helper: Render Unified Person Card (Main Tree Mode) ───────────
    private fun drawPersonCard(
        canvas: Canvas,
        p: Person,
        cx: Float,
        cy: Float,
        isTraceMode: Boolean,
        isOnPath: Boolean,
        isMrca: Boolean
    ) {
        val shouldDim = isTraceMode && !isOnPath
        val isFemale = p.gender.lowercase() == "female"
        val basePaint = when {
            isMrca -> pMrcaNode
            !p.isLiving -> if (shouldDim) pDeceasedDim else pDeceased
            isFemale -> if (shouldDim) pFemaleDim else pFemale
            else -> if (shouldDim) pMaleDim else pMale
        }

        val cardLeft = cx - CARD_W / 2f
        val cardTop = cy - CARD_H / 2f
        val cardRight = cx + CARD_W / 2f
        val cardBottom = cy + CARD_H / 2f
        tempCardRect.set(cardLeft, cardTop, cardRight, cardBottom)

        // 1. Soft Drop Shadow
        tempShadowRect.set(cardLeft, cardTop + 4f, cardRight, cardBottom + 4f)
        canvas.drawRoundRect(tempShadowRect, CARD_CORNER, CARD_CORNER, pCardShadow)

        // 2. Card Background & Border
        val bgPaint = when {
            isMrca -> pCardMrcaBg
            isOnPath && isTraceMode -> pCardHighlightBg
            !p.isLiving && shouldDim -> pCardDeceasedBgDim
            !p.isLiving -> pCardDeceasedBg
            shouldDim -> pCardBgDim
            else -> pCardBg
        }
        val borderPaint = when {
            isMrca -> pCardMrcaBorder
            isOnPath && isTraceMode -> pCardHighlightBorder
            !p.isLiving && shouldDim -> pCardDeceasedBorderDim
            !p.isLiving -> pCardDeceasedBorder
            shouldDim -> pCardBorderDim
            else -> pCardBorder
        }
        canvas.drawRoundRect(tempCardRect, CARD_CORNER, CARD_CORNER, bgPaint)
        canvas.drawRoundRect(tempCardRect, CARD_CORNER, CARD_CORNER, borderPaint)

        // 3. Circular Avatar inside Card
        val avatarCy = cardTop + 14f + NODE_RADIUS
        canvas.drawCircle(cx, avatarCy + 2f, NODE_RADIUS + 1f, pNodeShadow)

        if (isMrca) {
            canvas.drawCircle(cx, avatarCy, NODE_RADIUS + 6f, pMrcaGoldHalo)
        }

        val photoBitmap = avatarBitmapCache[p.id]
        if (photoBitmap != null) {
            val bmpPaint = when {
                !p.isLiving && shouldDim -> pBmpDeceasedDim
                !p.isLiving -> pBmpDeceasedNormal
                shouldDim -> pBmpDim
                else -> pBmpNormal
            }
            canvas.drawBitmap(photoBitmap, cx - NODE_RADIUS, avatarCy - NODE_RADIUS, bmpPaint)
        } else {
            canvas.drawCircle(cx, avatarCy, NODE_RADIUS, basePaint)
            val initials = cardDisplayCache[p.id]?.initials ?: "${p.firstName.firstOrNull() ?: ""}${p.lastName.firstOrNull() ?: ""}".uppercase()
            val initPaint = if (shouldDim) pInitialsDim else pInitials
            canvas.drawText(initials, cx, avatarCy + initPaint.textSize / 3f, initPaint)
        }

        val avatarBorderPaint = when {
            isMrca -> pMrcaGoldHalo
            isOnPath && isTraceMode -> pGoldHighlightBorder
            !p.isLiving -> if (shouldDim) pDeceasedBorderDim else pDeceasedBorder
            else -> if (shouldDim) pBorderDim else pBorder
        }
        canvas.drawCircle(cx, avatarCy, NODE_RADIUS, avatarBorderPaint)

        // Deceased Memorial badge on avatar († / 🕊️)
        if (!p.isLiving && !shouldDim) {
            val badgeX = cx - NODE_RADIUS * 0.7f
            val badgeY = avatarCy - NODE_RADIUS * 0.7f
            canvas.drawCircle(badgeX, badgeY, 13f, pMemBg)
            canvas.drawText("†", badgeX, badgeY + 4f, pMemTxt)
        }

        // Identity Verification checkmark icon badge on avatar
        if (p.documents.isNotEmpty() && !shouldDim) {
            val badgeX = cx + NODE_RADIUS * 0.7f
            val badgeY = avatarCy - NODE_RADIUS * 0.7f
            canvas.drawCircle(badgeX, badgeY, 13f, pCheckBg)
            canvas.drawText("✓", badgeX, badgeY + 4.5f, pCheckTxt)
        }

        // 4. Name & Date Texts (Retrieved from O(1) Cache, ZERO text allocation!)
        val cached = cardDisplayCache[p.id]
        val displayFirst = cached?.displayFirstName ?: p.firstName
        val displayLast = cached?.displayLastName ?: p.lastName
        val yrStr = cached?.displayYear ?: ""

        val firstPaint = if (shouldDim) pFirstNameDim else pFirstName
        val lastPaint = if (shouldDim) pLastNameDim else pLastName
        val yearPaint = when {
            !p.isLiving && shouldDim -> pYearDeceasedDim
            !p.isLiving -> pYearDeceased
            shouldDim -> pYearDim
            else -> pYear
        }

        canvas.drawText(displayFirst, cx, cardTop + 102f, firstPaint)
        canvas.drawText(displayLast, cx, cardTop + 118f, lastPaint)
        if (yrStr.isNotEmpty()) {
            canvas.drawText(yrStr, cx, cardTop + 133f, yearPaint)
        }

        // 5. Kinship Title Badge Pill (Retrieved from O(1) Cache, ZERO text allocation!)
        val displayTitle = cached?.kinshipTitle ?: ""
        val barMargin = 8f
        val barH = 24f
        tempBarRect.set(cardLeft + barMargin, cardBottom - barH - barMargin, cardRight - barMargin, cardBottom - barMargin)

        val titleBgPaint = when {
            !p.isLiving && shouldDim -> pDeceasedTitleBgDim
            !p.isLiving -> pDeceasedTitleBg
            shouldDim -> pTitleBgDim
            else -> pTitleBg
        }
        val titleBorderPaint = when {
            !p.isLiving && shouldDim -> pDeceasedTitleBorderDim
            !p.isLiving -> pDeceasedTitleBorder
            shouldDim -> pTitleBorderDim
            else -> pTitleBorder
        }
        val titleTxtPaint = when {
            !p.isLiving && shouldDim -> pDeceasedTitleTextDim
            !p.isLiving -> pDeceasedTitleText
            shouldDim -> pTitleTextDim
            else -> pTitleText
        }

        canvas.drawRoundRect(tempBarRect, barH / 2f, barH / 2f, titleBgPaint)
        canvas.drawRoundRect(tempBarRect, barH / 2f, barH / 2f, titleBorderPaint)
        canvas.drawText(displayTitle, cx, tempBarRect.centerY() + 4f, titleTxtPaint)

        // 6. Role Badge above card (especially in TRACE_HIGHLIGHT mode)
        if (isTraceMode && isOnPath) {
            when {
                isMrca -> canvas.drawText("★ COMMON ANCESTOR", cx, cardTop - 8f, pMrcaBadge)
                p.id == tracePersonAId -> canvas.drawText("🚩 PERSON A", cx, cardTop - 8f, pPersonABadge)
                p.id == tracePersonBId -> canvas.drawText("🎯 PERSON B", cx, cardTop - 8f, pPersonBBadge)
                else -> canvas.drawText("⚡ BLOODLINE", cx, cardTop - 8f, pBloodlineBadge)
            }
        } else if (isMrca) {
            canvas.drawText("★ COMMON ANCESTOR", cx, cardTop - 8f, pMrcaBadge)
        }
    }

    // ── Helper: Render Pedigree Circular Node ─────────────────────────
    private fun drawPedigreeNode(canvas: Canvas, p: Person, cx: Float, cy: Float, relationLabel: String) {
        val isFemale = p.gender.lowercase() == "female"
        val basePaint = when {
            !p.isLiving -> pDeceased
            isFemale -> pFemale
            else -> pMale
        }
        val borderPaint = if (!p.isLiving) pDeceasedBorder else pBorder

        canvas.drawCircle(cx, cy + 4f, NODE_RADIUS + 2f, pNodeShadow)

        val photoBitmap = avatarBitmapCache[p.id]
        if (photoBitmap != null) {
            val bmpPaint = if (!p.isLiving) pBmpDeceasedNormal else pBmpNormal
            canvas.drawBitmap(photoBitmap, cx - NODE_RADIUS, cy - NODE_RADIUS, bmpPaint)
        } else {
            canvas.drawCircle(cx, cy, NODE_RADIUS, basePaint)
            val initials = cardDisplayCache[p.id]?.initials ?: "${p.firstName.firstOrNull() ?: ""}${p.lastName.firstOrNull() ?: ""}".uppercase()
            canvas.drawText(initials, cx, cy + pInitials.textSize / 3f, pInitials)
        }
        canvas.drawCircle(cx, cy, NODE_RADIUS, borderPaint)

        // Deceased Memorial Symbol (†)
        if (!p.isLiving) {
            val badgeX = cx - NODE_RADIUS * 0.7f
            val badgeY = cy - NODE_RADIUS * 0.7f
            canvas.drawCircle(badgeX, badgeY, 13f, pMemBg)
            canvas.drawText("†", badgeX, badgeY + 4f, pMemTxt)
        }
        // Verified ✓
        if (p.documents.isNotEmpty()) {
            val badgeX = cx + NODE_RADIUS * 0.7f
            val badgeY = cy - NODE_RADIUS * 0.7f
            canvas.drawCircle(badgeX, badgeY, 13f, pCheckBg)
            canvas.drawText("✓", badgeX, badgeY + 4.5f, pCheckTxt)
        }

        // Labels
        canvas.drawText(relationLabel, cx, cy + NODE_RADIUS + 34f, pDashedLabel)
        val fullName = "${p.firstName} ${p.lastName}".trim()
        val displayPedigreeName = cardDisplayCache[p.id]?.displayFullName ?: fullName
        canvas.drawText(displayPedigreeName, cx, cy + NODE_RADIUS + 58f, pFirstName)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun handleTap(screenX: Float, screenY: Float) {
        if (allPersons.isEmpty()) {
            val fx = width - 80f; val fy = height - 80f
            val dx = screenX - fx; val dy = screenY - fy
            if (sqrt(dx * dx + dy * dy) <= 50f) { onAddFirst() }
            return
        }

        val originX = offsetX + (width  - canvasW) / 2f
        val originY = offsetY + MARGIN / 2f
        val pivotX  = width  / 2f
        val pivotY  = height / 2f

        val cx = (screenX - pivotX) / scaleFactor + pivotX - originX
        val cy = (screenY - pivotY) / scaleFactor + pivotY - originY

        hitAreas.reversed().forEach { h ->
            val dx = cx - h.cx
            val dy = cy - h.cy
            val isInside = if (h.w > 0f && h.h > 0f) {
                abs(dx) <= h.w / 2f && abs(dy) <= h.h / 2f
            } else {
                sqrt(dx * dx + dy * dy) <= h.r
            }
            if (isInside) {
                when {
                    h.tag.startsWith("add:") -> {
                        val isTraceMode = mode == "TRACE_HIGHLIGHT" && (highlightIds.isNotEmpty() || mrcaPersonId != null)
                        val targetId = h.tag.removePrefix("add:")
                        if (!isTraceMode || highlightIds.contains(targetId) || targetId == mrcaPersonId) {
                            onAddChild(targetId)
                        }
                    }
                    h.tag.startsWith("ancestor:") -> {
                        val parts = h.tag.removePrefix("ancestor:").split(":")
                        val childId = parts.getOrNull(0).orEmpty()
                        val isFather = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true
                        onAddAncestor(childId, isFather)
                    }
                    else -> {
                        onOpenPerson(h.tag)
                    }
                }
                return
            }
        }
    }
}
