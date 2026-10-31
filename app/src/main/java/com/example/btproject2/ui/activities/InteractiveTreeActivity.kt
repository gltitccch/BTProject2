package com.example.btproject2.ui.activities

import com.example.btproject2.utils.KinTraceAlert
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

import androidx.appcompat.app.AlertDialog
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
import androidx.core.content.res.ResourcesCompat
import com.example.btproject2.R
import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AttachedDocument
import com.example.btproject2.models.Person
import com.example.btproject2.models.CustomTreeLayout
import com.example.btproject2.models.CustomNodePosition
import com.example.btproject2.models.CustomBusOffset
import com.example.btproject2.models.LayoutHistoryAction
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
import com.example.btproject2.ui.dialogs.ActionRestrictedDialog
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

    private lateinit var layoutOrganizeModeBar: LinearLayout
    private lateinit var btnUndoLayout: TextView
    private lateinit var btnResetLayout: TextView
    private lateinit var btnDoneOrganize: TextView
    private var btnToggleOrganizeMode: TextView? = null

    private var treeId = ""

    private var isOwner: Boolean = true
    private var isEditor: Boolean = false
    private var isViewer: Boolean = false
    fun isViewerUser(): Boolean = isViewer
    private var currentTreeName: String = "Family Tree"
    private var treePrivacy: com.example.btproject2.models.PrivacySettings = com.example.btproject2.models.PrivacySettings()
    private var tvInteractiveTreeRoleBadge: TextView? = null
    private var layoutInteractiveViewerBanner: View? = null
    private var btnConnectBranchesShortcut: View? = null

    internal var allPersonsList: List<Person> = emptyList()
    internal var currentMode = "MAIN" // "MAIN", "PEDIGREE", "TRACE_HIGHLIGHT"

    private var highlightPersonAId: String? = null
    private var highlightPersonBId: String? = null
    private var mrcaId: String? = null
    private var mrcaIds: Set<String> = emptySet()
    private var isAffinityTrace: Boolean = false
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
    private lateinit var btnResetFocal: TextView

    private lateinit var layoutConflictBanner: LinearLayout
    private lateinit var tvConflictBannerText: TextView
    private lateinit var btnCloseConflictBanner: TextView

    private var activeProfilePersonId: String? = null
    private var activeProfileDialog: AlertDialog? = null

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val targetPersonId = activeProfilePersonId
        if (uri != null && !targetPersonId.isNullOrEmpty()) {
            Thread {
                val result = DocumentHelper.saveImageToInternal(applicationContext, uri, targetPersonId)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (result != null) {
                        val (filePath, base64) = result
                        firestoreHelper.updatePersonPhoto(
                            targetPersonId,
                            base64,
                            filePath,
                            onSuccess = {
                                KinTraceAlert.show(this, "Profile photo updated")
                                if (activeProfilePersonId == null) {
                                    activeProfilePersonId = targetPersonId
                                }
                                showMemberProfileDialog(targetPersonId)
                            },
                            onFailure = {
                                KinTraceAlert.show(this, "Failed to update photo: ${it.message}")
                            }
                        )
                    }
                }
            }.start()
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val targetPersonId = activeProfilePersonId
        if (uri != null && !targetPersonId.isNullOrEmpty()) {
            Thread {
                val doc = DocumentHelper.saveDocumentToInternal(applicationContext, uri, targetPersonId)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (doc != null) {
                        firestoreHelper.attachDocument(
                            targetPersonId,
                            doc,
                            onSuccess = {
                                KinTraceAlert.show(this, "Document attached successfully")
                                if (activeProfilePersonId == null) {
                                    activeProfilePersonId = targetPersonId
                                }
                                showMemberProfileDialog(targetPersonId)
                            },
                            onFailure = {
                                KinTraceAlert.show(this, "Failed to attach document: ${it.message}")
                            }
                        )
                    } else {
                        KinTraceAlert.show(this, "Could not read the selected document file.")
                    }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interactive_tree)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnQuickAssistInteractiveTree)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.show(
                this,
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.CANVAS
            )
        }

        btnConnectBranchesShortcut = findViewById(R.id.btnConnectBranchesShortcut)
        btnConnectBranchesShortcut?.setOnClickListener {
            val activeTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this@InteractiveTreeActivity) }
            val intent = Intent(this, MergeBranchesActivity::class.java).apply {
                putExtra("TREE_ID", activeTreeId)
                putExtra("treeId", activeTreeId)
            }
            startActivity(intent)
        }
        tvInteractiveTreeRoleBadge = findViewById(R.id.tvInteractiveTreeRoleBadge)
        layoutInteractiveViewerBanner = findViewById(R.id.layoutInteractiveViewerBanner)
        loadRoleAndApplyPermissions(treeId)

        findViewById<TextView>(R.id.btnExportTreeShortcut)?.setOnClickListener {
            val intent = Intent(this, ExportTreeActivity::class.java).apply {
                val activeTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this@InteractiveTreeActivity) }
                putExtra("TREE_ID", activeTreeId)
                putExtra("treeId", activeTreeId)
            }
            startActivity(intent)
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

        findViewById<TextView>(R.id.btnInteractiveToolsMenu)?.setOnClickListener { anchorView ->
            com.example.btproject2.ui.dialogs.TreeToolsPopoverHelper.show(
                context = this,
                anchor = anchorView,
                showConnect = isOwner,
                showAudit = true,
                showOrganize = !isViewer,
                onConnect = { btnConnectBranchesShortcut?.performClick() },
                onAudit = { findViewById<TextView>(R.id.btnAuditTree)?.performClick() },
                onExport = { findViewById<TextView>(R.id.btnExportTreeShortcut)?.performClick() },
                onLegend = { findViewById<TextView>(R.id.btnInteractiveLegend)?.performClick() },
                onOrganize = { setOrganizeMode(true) }
            )
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
        btnResetFocal = findViewById(R.id.btnResetFocal)
        btnResetFocal.setOnClickListener {
            val defaultFocal = findBestMainTreeFocalMember(allPersonsList)
            if (defaultFocal != null) {
                focalPedigreePersonId = defaultFocal.id
                val idx = allPersonsList.indexOfFirst { it.id == defaultFocal.id }
                if (idx >= 0) spinnerPedigreeFocal.setSelection(idx)
                treeView.setMode(
                    mode = currentMode,
                    highlightIds = highlightPathIds,
                    mrcaId = mrcaId,
                    focalPedigreeId = focalPedigreePersonId,
                    personAId = highlightPersonAId,
                    personBId = highlightPersonBId,
                    mrcaIds = mrcaIds,
                    isAffinityTrace = isAffinityTrace
                )
                treeView.post {
                    treeView.centerOnPerson(defaultFocal.id, animate = true)
                }
                updateFocalResetButton()
                KinTraceAlert.show(this, "Perspective reset to ${defaultFocal.firstName}")
            }
        }

        layoutConflictBanner = findViewById(R.id.layoutConflictBanner)
        tvConflictBannerText = findViewById(R.id.tvConflictBannerText)
        btnCloseConflictBanner = findViewById(R.id.btnCloseConflictBanner)
        btnCloseConflictBanner.setOnClickListener {
            layoutConflictBanner.visibility = View.GONE
        }

        highlightPersonAId = intent.getStringExtra("highlightPersonAId")
        highlightPersonBId = intent.getStringExtra("highlightPersonBId")
        mrcaId = intent.getStringExtra("mrcaId")
        val mrcaList = intent.getStringArrayListExtra("mrcaIds")
        mrcaIds = buildSet {
            mrcaId?.takeIf { it.isNotBlank() }?.let { add(it) }
            mrcaList?.forEach { if (it.isNotBlank()) add(it) }
        }
        isAffinityTrace = intent.getBooleanExtra("isAffinityTrace", false)
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
                val activeSpotlight = com.example.btproject2.utils.KinTraceSpotlightManager.getActiveSpotlightView()
                if (activeSpotlight != null && activeSpotlight.getCurrentStepIndex() == 0) {
                    tutorialSelectedPersonId = personId
                    val nodeBounds = treeView.getNodeScreenBounds(personId)
                    if (nodeBounds != null) {
                        val density = resources.displayMetrics.density
                        val paddedRect = RectF(
                            nodeBounds.left - 6f * density,
                            nodeBounds.top - 6f * density,
                            nodeBounds.right + 6f * density,
                            nodeBounds.bottom + 6f * density
                        )
                        val cornerRadius = 16f * density
                        activeSpotlight.morphToTargetRect(paddedRect, cornerRadius, durationMs = 380L) {
                            window.decorView.postDelayed({
                                showMemberProfileDialog(personId)
                                activeSpotlight.nextStep()
                            }, 200L)
                        }
                    } else {
                        showMemberProfileDialog(personId)
                        activeSpotlight.nextStep()
                    }
                } else {
                    showMemberProfileDialog(personId)
                    if (activeSpotlight != null) {
                        activeSpotlight.nextStep()
                    }
                }
            },
            onAddChild = { parentId ->
                if (isViewer) {
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Add Child"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                } else {
                    promptAddChildChoices(parentId)
                }
            },
            onAddFirst = {
                if (isViewer) {
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Add Member"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                } else {
                    val intent = Intent(this, AddMemberActivity::class.java).apply {
                        putExtra("TREE_ID", treeId)
                    }
                    startActivity(intent)
                }
            },
            onAddAncestor = { childId, isFather ->
                if (isViewer) {
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Add Ancestor"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                } else {
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
                        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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

        layoutOrganizeModeBar = findViewById(R.id.layoutOrganizeModeBar)
        btnUndoLayout = findViewById(R.id.btnUndoLayout)
        btnResetLayout = findViewById(R.id.btnResetLayout)
        btnDoneOrganize = findViewById(R.id.btnDoneOrganize)
        btnToggleOrganizeMode = findViewById(R.id.btnToggleOrganizeMode)

        btnToggleOrganizeMode?.setOnClickListener {
            setOrganizeMode(!treeView.isOrganizeMode)
        }

        btnUndoLayout.setOnClickListener {
            treeView.undoLastAction()
        }

        btnResetLayout.setOnClickListener {
            treeView.resetCustomLayout()
            val effectiveTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this) }
            firestoreHelper.saveCustomTreeLayout(treeView.exportCustomLayout(effectiveTreeId))
        }

        btnDoneOrganize.setOnClickListener {
            setOrganizeMode(false)
            val effectiveTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this) }
            val layout = treeView.exportCustomLayout(effectiveTreeId)
            firestoreHelper.saveCustomTreeLayout(layout, onSuccess = {
                Toast.makeText(this, "Tree layout saved!", Toast.LENGTH_SHORT).show()
            })
        }


        chipMainTree.setOnClickListener { setMode("MAIN") }
        chipPedigree.setOnClickListener { setMode("PEDIGREE") }
        chipTraceHighlight.setOnClickListener { setMode("TRACE_HIGHLIGHT") }

        btnCloseTraceHighlight.setOnClickListener {
            setMode("MAIN")
        }

        findViewById<View>(R.id.btnOpenTraceReportFromBanner)?.setOnClickListener {
            val aId = highlightPersonAId
            val bId = highlightPersonBId
            if (!aId.isNullOrBlank() && !bId.isNullOrBlank()) {
                val intent = Intent(this, RelationshipReportActivity::class.java).apply {
                    putExtra("personAId", aId)
                    putExtra("personBId", bId)
                    putExtra("treeId", treeId)
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }
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

        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.INTERACTIVE_TREE,
            treeId = treeId,
            dialogProvider = { activeProfileDialog }
        )
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
            onTreeLayoutChanged = {
                val effectiveTreeId = treeId.ifBlank { com.example.btproject2.utils.TreePreferences.getActiveTreeId(this@InteractiveTreeActivity) }
                firestoreHelper.getCustomTreeLayout(effectiveTreeId, onSuccess = { layout ->
                    if (layout != null) {
                        treeView.applyCustomLayout(layout)
                    }
                })
            },
            onTreeUpdated = { updatedList, requiresRelayout ->
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
                if (::spinnerPedigreeFocal.isInitialized && requiresRelayout) refreshFocalSpinner()
                if (::treeView.isInitialized) {
                    treeView.setDataAndMode(
                        list = allPersonsList,
                        mode = currentMode,
                        highlightIds = highlightPathIds,
                        mrcaId = mrcaId,
                        focalPedigreeId = focalPedigreePersonId,
                        personAId = highlightPersonAId,
                        personBId = highlightPersonBId,
                        forceRefresh = requiresRelayout
                    )
                }
                if (currentMode == "TRACE_HIGHLIGHT" && ::layoutTraceBanner.isInitialized) {
                    updateTraceBannerText()
                }
                if (::layoutConflictBanner.isInitialized && requiresRelayout) {
                    checkForTreeConflicts(allPersonsList)
                }
            },
            onMemberEvictedFromAvatarCache = { id ->
                if (::treeView.isInitialized) treeView.evictAvatar(id)
            },
            onActiveProfileDismissed = { id ->
                if (activeProfilePersonId == id) {
                    val dialogToClose = activeProfileDialog
                    activeProfileDialog = null
                    activeProfilePersonId = null
                    dialogToClose?.setOnDismissListener(null)
                    dialogToClose?.dismiss()
                }
            },
            onActiveProfileRefreshed = { id ->
                if (activeProfilePersonId == id && activeProfileDialog?.isShowing == true) {
                    val dialogToClose = activeProfileDialog
                    activeProfileDialog = null
                    dialogToClose?.setOnDismissListener(null)
                    dialogToClose?.dismiss()
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
                KinTraceAlert.show(this, "The displayed family tree was removed.")
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

    
    private fun setOrganizeMode(enabled: Boolean) {
        if (isViewer && enabled) {
            Toast.makeText(this, "View-only access cannot customize tree layout", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentMode == "PEDIGREE" && enabled) {
            Toast.makeText(this, "Organize Mode is for Main Tree only", Toast.LENGTH_SHORT).show()
            return
        }
        treeView.isOrganizeMode = enabled
        layoutOrganizeModeBar.visibility = if (enabled) View.VISIBLE else View.GONE
        btnToggleOrganizeMode?.isSelected = enabled
        if (enabled) {
            Toast.makeText(this, "Organize Mode: Drag cards or gold elbow handles to rearrange", Toast.LENGTH_SHORT).show()
            if (!isViewer) {
                com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
                    activity = this,
                    feature = com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.ORGANIZE_MODE,
                    treeId = treeId
                )
            }
        } else {
            if (com.example.btproject2.utils.KinTraceSpotlightManager.getActiveFeatureKey() == com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.ORGANIZE_MODE) {
                com.example.btproject2.utils.KinTraceSpotlightManager.dismissActiveOverlay()
            }
        }
    }

    private fun setMode(mode: String) {
        currentMode = mode
        if (mode == "PEDIGREE" && allPersonsList.isNotEmpty()) {
            val byId = allPersonsList.associateBy { it.id }
            val currentFocal = focalPedigreePersonId?.let { byId[it] }
            val bestPedigreeFocal = findBestPedigreeFocalMember(allPersonsList)
            val currentDepth = currentFocal?.let { computeUpwardAncestorDepth(it, byId) } ?: 0
            val bestDepth = bestPedigreeFocal?.let { computeUpwardAncestorDepth(it, byId) } ?: 0
            if (bestPedigreeFocal != null && currentDepth < bestDepth) {
                focalPedigreePersonId = bestPedigreeFocal.id
                refreshFocalSpinner()
            }
        }
        updateModeChips(mode)
        treeView.setMode(
            mode = mode,
            highlightIds = highlightPathIds,
            mrcaId = mrcaId,
            focalPedigreeId = focalPedigreePersonId,
            personAId = highlightPersonAId,
            personBId = highlightPersonBId,
            mrcaIds = mrcaIds,
            isAffinityTrace = isAffinityTrace
        )
    }

    private fun computeUpwardAncestorDepth(person: Person, byId: Map<String, Person>, visited: MutableSet<String> = mutableSetOf()): Int {
        if (!visited.add(person.id) || visited.size > 6) return 0
        val fDepth = person.fatherId?.takeIf { it.isNotBlank() }?.let { byId[it] }?.let { 1 + computeUpwardAncestorDepth(it, byId, visited.toMutableSet()) } ?: 0
        val mDepth = person.motherId?.takeIf { it.isNotBlank() }?.let { byId[it] }?.let { 1 + computeUpwardAncestorDepth(it, byId, visited.toMutableSet()) } ?: 0
        return maxOf(fDepth, mDepth).coerceAtMost(4)
    }

    private fun countUpwardAncestorsWithin4Degrees(person: Person, byId: Map<String, Person>): Int {
        val ancestors = mutableSetOf<String>()
        var frontier = listOf(person)
        for (deg in 1..4) {
            val next = mutableListOf<Person>()
            for (curr in frontier) {
                curr.fatherId?.takeIf { it.isNotBlank() }?.let { byId[it] }?.let {
                    if (ancestors.add(it.id)) next.add(it)
                }
                curr.motherId?.takeIf { it.isNotBlank() }?.let { byId[it] }?.let {
                    if (ancestors.add(it.id)) next.add(it)
                }
            }
            frontier = next
            if (frontier.isEmpty()) break
        }
        return ancestors.size
    }

    private fun findBestPedigreeFocalMember(list: List<Person>): Person? {
        return com.example.btproject2.engine.GenerationHierarchyEngine.findBestPedigreeFocalMember(list)
    }

    /**
     * Picks the best starting focal person for the Main Tree ("Kinship relative to:"):
     * Prefers the central multi-generational hub (a member who has both upward ancestors and children/spouse/siblings,
     * with a tie-breaker favoring the logged-in user's first name if present in the tree).
     */
    private fun findBestMainTreeFocalMember(list: List<Person>): Person? {
        if (list.isEmpty()) return null
        val byId = list.associateBy { it.id }
        val userFirst = authHelper.getCurrentUser()?.displayName?.substringBefore(" ")?.trim().orEmpty()

        return list.maxWithOrNull(
            compareBy<Person> { p ->
                val hasParents = (!p.fatherId.isNullOrBlank() && byId.containsKey(p.fatherId)) ||
                    (!p.motherId.isNullOrBlank() && byId.containsKey(p.motherId))
                val childCount = list.count { it.fatherId == p.id || it.motherId == p.id }
                val hasSpouse = !p.spouseId.isNullOrBlank() && byId.containsKey(p.spouseId)
                val upCount = countUpwardAncestorsWithin4Degrees(p, byId)
                val userBonus = if (userFirst.isNotBlank() && p.firstName.equals(userFirst, ignoreCase = true) && (hasParents || childCount > 0)) 20 else 0
                val hubBonus = if (hasParents && childCount > 0) 15 else 0
                userBonus + hubBonus + upCount * 2 + childCount * 3 + (if (hasSpouse) 2 else 0)
            }.thenBy { it.firstName }
        )
    }

    private fun updateModeChips(mode: String) {
        val activeBg = R.drawable.bg_segmented_item_active
        val inactiveBg = android.R.color.transparent
        val activeText = androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text)
        val inactiveText = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)

        chipMainTree.setBackgroundResource(if (mode == "MAIN") activeBg else inactiveBg)
        chipMainTree.setTextColor(if (mode == "MAIN") activeText else inactiveText)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
            chipMainTree,
            android.content.res.ColorStateList.valueOf(if (mode == "MAIN") activeText else inactiveText)
        )

        chipPedigree.setBackgroundResource(if (mode == "PEDIGREE") activeBg else inactiveBg)
        chipPedigree.setTextColor(if (mode == "PEDIGREE") activeText else inactiveText)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
            chipPedigree,
            android.content.res.ColorStateList.valueOf(if (mode == "PEDIGREE") activeText else inactiveText)
        )

        chipTraceHighlight.setBackgroundResource(if (mode == "TRACE_HIGHLIGHT") activeBg else inactiveBg)
        chipTraceHighlight.setTextColor(if (mode == "TRACE_HIGHLIGHT") activeText else inactiveText)
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
            chipTraceHighlight,
            android.content.res.ColorStateList.valueOf(if (mode == "TRACE_HIGHLIGHT") activeText else inactiveText)
        )

        if (mode == "TRACE_HIGHLIGHT") {
            layoutTraceBanner.visibility = View.VISIBLE
            layoutPedigreeFocalBar.visibility = View.GONE
            updateTraceBannerText()
        } else if (mode == "PEDIGREE") {
            layoutTraceBanner.visibility = View.GONE
            layoutPedigreeFocalBar.visibility = View.VISIBLE
            tvFocalBarLabel.text = "Pedigree for:"
        } else {
            layoutTraceBanner.visibility = View.GONE
            layoutPedigreeFocalBar.visibility = View.VISIBLE
            tvFocalBarLabel.text = "Kinship relative to:"
        }
    }

    private fun updateTraceBannerText() {
        val byId = allPersonsList.associateBy { it.id }
        val nameA = highlightPersonAId?.let { byId[it]?.firstName } ?: "Person A"
        val nameB = highlightPersonBId?.let { byId[it]?.firstName } ?: "Person B"
        val aId = highlightPersonAId
        val bId = highlightPersonBId
        val relTitle = if (!aId.isNullOrBlank() && !bId.isNullOrBlank() && byId.containsKey(aId) && byId.containsKey(bId)) {
            com.example.btproject2.engine.BloodlineTracer().trace(aId, bId, byId).relationshipType
        } else null
        val mrcaNames = mrcaIds.mapNotNull { byId[it]?.firstName }.distinct()
            .ifEmpty { listOfNotNull(mrcaId?.let { byId[it]?.firstName }) }

        val titleSuffix = if (!relTitle.isNullOrBlank()) " ($relTitle)" else ""
        tvTraceBannerText.text = when {
            isAffinityTrace && mrcaNames.isNotEmpty() -> {
                "💍 $nameA ↔ $nameB$titleSuffix • Bridge: ${mrcaNames.joinToString(" & ")}"
            }
            mrcaNames.size >= 2 -> {
                "⭐ $nameA ↔ $nameB$titleSuffix • ★ ${mrcaNames.joinToString(" & ")}"
            }
            mrcaNames.size == 1 -> {
                "⭐ $nameA ↔ $nameB$titleSuffix • ★ ${mrcaNames.first()}"
            }
            else -> {
                "⭐ $nameA ↔ $nameB$titleSuffix"
            }
        }
    }

    private fun showTreeLegendDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_legend, null)
        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val isDark = ThemePreferences.isDarkTheme(this)
        val tvBioLineTitle = dialogView.findViewById<TextView>(R.id.tvLegendBioLineTitle)
        val tvMaritalTitle = dialogView.findViewById<TextView>(R.id.tvLegendMaritalTitle)
        val viewBioLine = dialogView.findViewById<View>(R.id.viewLegendBioLine)

        if (isDark) {
            tvBioLineTitle?.text = "Solid Emerald Green Line"
            tvMaritalTitle?.text = "Dashed Warm Gold Line + [ 💍 ] Pill"
            viewBioLine?.setBackgroundColor(Color.parseColor("#1D9E75"))
        } else {
            tvBioLineTitle?.text = "Solid Amber-Brown Line"
            tvMaritalTitle?.text = "Dashed Warm Amber Line + [ 💍 ] Pill"
            viewBioLine?.setBackgroundColor(Color.parseColor("#B45309"))
        }

        dialogView.findViewById<View>(R.id.btnCloseLegend)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnGotItLegend)?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun refreshFocalSpinner() {
        val list = allPersonsList
        if (list.isEmpty()) return
        val byId = list.associateBy { it.id }
        val names = list.map { p ->
            val depth = computeUpwardAncestorDepth(p, byId)
            if (depth > 0) {
                "${p.firstName} ${p.lastName} ($depth° Ancestors)"
            } else {
                "${p.firstName} ${p.lastName} (Founder / Root)"
            }
        }
        spinnerPedigreeFocal.setDarkAdapter(this, names)

        val defaultIdx = if (!focalPedigreePersonId.isNullOrEmpty()) {
            list.indexOfFirst { it.id == focalPedigreePersonId }.coerceAtLeast(0)
        } else if (currentMode == "PEDIGREE") {
            val best = findBestPedigreeFocalMember(list)
            best?.let { b -> list.indexOfFirst { it.id == b.id }.coerceAtLeast(0) } ?: 0
        } else {
            val bestMain = findBestMainTreeFocalMember(list)
            bestMain?.let { b -> list.indexOfFirst { it.id == b.id }.coerceAtLeast(0) } ?: 0
        }

        if (focalPedigreePersonId.isNullOrEmpty()) {
            focalPedigreePersonId = list[defaultIdx].id
        }
        spinnerPedigreeFocal.setSelection(defaultIdx)

        spinnerPedigreeFocal.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedId = allPersonsList.getOrNull(position)?.id
                if (!selectedId.isNullOrEmpty() && selectedId != focalPedigreePersonId) {
                    focalPedigreePersonId = selectedId
                    treeView.setMode(
                        mode = currentMode,
                        highlightIds = highlightPathIds,
                        mrcaId = mrcaId,
                        focalPedigreeId = focalPedigreePersonId,
                        personAId = highlightPersonAId,
                        personBId = highlightPersonBId,
                        mrcaIds = mrcaIds,
                        isAffinityTrace = isAffinityTrace
                    )
                    treeView.post {
                        treeView.centerOnPerson(selectedId, animate = true)
                    }
                    updateFocalResetButton()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        updateFocalResetButton()
    }

    private fun updateFocalResetButton() {
        if (!::btnResetFocal.isInitialized) return
        val defaultFocalId = findBestMainTreeFocalMember(allPersonsList)?.id
        val isCustomFocal = !focalPedigreePersonId.isNullOrEmpty() && focalPedigreePersonId != defaultFocalId
        btnResetFocal.visibility = if (isCustomFocal) View.VISIBLE else View.GONE
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
                personBId = highlightPersonBId,
                mrcaIds = mrcaIds,
                isAffinityTrace = isAffinityTrace
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
                if (currentMode == "PEDIGREE" && list.isNotEmpty()) {
                    val byId = list.associateBy { it.id }
                    val currentFocal = focalPedigreePersonId?.let { byId[it] }
                    val bestPedigreeFocal = findBestPedigreeFocalMember(list)
                    val currentDepth = currentFocal?.let { computeUpwardAncestorDepth(it, byId) } ?: 0
                    val bestDepth = bestPedigreeFocal?.let { computeUpwardAncestorDepth(it, byId) } ?: 0
                    if (bestPedigreeFocal != null && currentDepth < bestDepth) {
                        focalPedigreePersonId = bestPedigreeFocal.id
                    }
                } else if (currentMode == "MAIN" && focalPedigreePersonId.isNullOrEmpty() && list.isNotEmpty()) {
                    focalPedigreePersonId = findBestMainTreeFocalMember(list)?.id
                }
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
                    personBId = highlightPersonBId,
                    mrcaIds = mrcaIds,
                    isAffinityTrace = isAffinityTrace
                )
                if (!focalPedigreePersonId.isNullOrEmpty()) {
                    treeView.post {
                        treeView.centerOnPerson(focalPedigreePersonId!!, animate = false)
                    }
                }

                // Synchronously apply local cached custom layout immediately if available
                val cachedLayout = com.example.btproject2.firebase.FirestoreHelper.getCachedCustomLayout(effectiveTreeId)
                if (cachedLayout != null && (cachedLayout.nodePositions.isNotEmpty() || cachedLayout.busOffsets.isNotEmpty())) {
                    treeView.applyCustomLayout(cachedLayout)
                }
                // Asynchronously fetch latest custom layout from Firestore to guarantee persistence across app restarts
                firestoreHelper.getCustomTreeLayout(
                    treeId = effectiveTreeId,
                    onSuccess = { layout ->
                        if (layout != null && (layout.nodePositions.isNotEmpty() || layout.busOffsets.isNotEmpty())) {
                            treeView.applyCustomLayout(layout)
                        }
                    }
                )
                checkForTreeConflicts(list)
                DocumentHelper.compactOversizedInlineDocuments(this, list)

                if (refreshProfile && !activeProfilePersonId.isNullOrEmpty()) {
                    val activeId = activeProfilePersonId
                    val dialogToClose = activeProfileDialog
                    activeProfileDialog = null
                    dialogToClose?.setOnDismissListener(null)
                    dialogToClose?.dismiss()
                    if (activeId != null) {
                        showMemberProfileDialog(activeId)
                    }
                }
                loadRoleAndApplyPermissions(effectiveTreeId)
            },
            onFailure = {
                KinTraceAlert.show(this, "Failed to load tree")
            }
        )
    }

    private fun loadRoleAndApplyPermissions(targetTreeId: String = treeId) {
        val effectiveTreeId = targetTreeId.ifBlank {
            com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        }
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        if (effectiveTreeId.isBlank()) return

        firestoreHelper.getTree(effectiveTreeId,
            onSuccess = { tree ->
                currentTreeName = tree?.name ?: "Family Tree"
                val ownerMatch = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (ownerMatch) {
                    isOwner = true
                    isEditor = false
                    isViewer = false
                    applyRolePermissionsToUI()
                } else {
                    firestoreHelper.getUserMembership(currentUserId, effectiveTreeId,
                        onSuccess = { member ->
                            val role = member?.role ?: "Viewer"
                            isOwner = false
                            isEditor = role.equals("Editor", ignoreCase = true)
                            isViewer = !isEditor
                            applyRolePermissionsToUI()
                        },
                        onFailure = {
                            isOwner = false
                            isEditor = false
                            isViewer = true
                            applyRolePermissionsToUI()
                        }
                    )
                }
            },
            onFailure = {
                isOwner = false
                isEditor = false
                isViewer = true
                applyRolePermissionsToUI()
            }
        )

        firestoreHelper.getPrivacySettings(effectiveTreeId,
            onSuccess = { settings ->
                treePrivacy = settings
                if (::treeView.isInitialized) {
                    treeView.treePrivacy = settings
                    treeView.invalidate()
                }
            },
            onFailure = {}
        )
    }

    private fun applyRolePermissionsToUI() {
        val activeRoleName: String
        if (isOwner) {
            activeRoleName = "Owner"
            tvInteractiveTreeRoleBadge?.text = "🌳 Owner • Root & Canopy Keeper"
            layoutInteractiveViewerBanner?.visibility = View.GONE
            btnConnectBranchesShortcut?.visibility = View.VISIBLE
        } else if (isEditor) {
            activeRoleName = "Editor"
            tvInteractiveTreeRoleBadge?.text = "🌿 Editor • Branch Tender"
            layoutInteractiveViewerBanner?.visibility = View.GONE
            btnConnectBranchesShortcut?.visibility = View.GONE
        } else {
            activeRoleName = "Viewer"
            tvInteractiveTreeRoleBadge?.text = "🌱 Viewer • Young Sprout (Read-Only)"
            layoutInteractiveViewerBanner?.visibility = View.VISIBLE
            btnConnectBranchesShortcut?.visibility = View.GONE
        }
        btnToggleOrganizeMode?.visibility = if (isViewer) View.GONE else View.VISIBLE
        tvInteractiveTreeRoleBadge?.setOnClickListener {
            com.example.btproject2.utils.BotanicalRoleFlashManager.showRoleAssignedCeremony(
                activity = this,
                rawRole = activeRoleName,
                isTreeOwner = isOwner,
                treeName = currentTreeName
            )
        }
        layoutInteractiveViewerBanner?.setOnClickListener {
            com.example.btproject2.utils.BotanicalRoleFlashManager.showRestrictedActionFlash(
                activity = this,
                rawRole = activeRoleName,
                isTreeOwner = isOwner,
                featureTitle = "Graft & Edit Family Members",
                requiredTier = com.example.btproject2.utils.BotanicalRolePolicy.BotanicalTier.BRANCH_EDITOR,
                treeId = treeId,
                treeName = currentTreeName
            )
        }
        if (::treeView.isInitialized) {
            val viewerChanged = treeView.isViewerMode != isViewer
            treeView.isViewerMode = isViewer
            treeView.treePrivacy = treePrivacy
            if (viewerChanged && allPersonsList.isNotEmpty()) {
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
            } else {
                treeView.invalidate()
            }
        }
    }

    @Volatile
    private var conflictScanToken: Int = 0
    @Volatile
    private var isScanningConflicts: Boolean = false
    @Volatile
    private var pendingConflictScanPersons: List<Person>? = null

    private fun checkForTreeConflicts(persons: List<Person>) {
        if (persons.isEmpty()) {
            layoutConflictBanner.visibility = View.GONE
            return
        }
        val token = ++conflictScanToken
        if (isScanningConflicts) {
            pendingConflictScanPersons = persons
            return
        }
        isScanningConflicts = true
        Thread {
            try {
                var currentList = persons
                var currentToken = token
                while (true) {
                    val conflicts = com.example.btproject2.engine.ConflictScanner().scanAll(currentList)
                    if (currentToken == conflictScanToken) {
                        runOnUiThread {
                            if (isFinishing || isDestroyed || currentToken != conflictScanToken) return@runOnUiThread
                            if (conflicts.isNotEmpty()) {
                                val totalCount = conflicts.size
                                layoutConflictBanner.visibility = View.VISIBLE
                                tvConflictBannerText.text = if (totalCount == 1) {
                                    "🧭 1 family question to check • Tap for step-by-step guide"
                                } else {
                                    "🧭 $totalCount family questions to check • Tap for step-by-step guide"
                                }
                                layoutConflictBanner.setOnClickListener {
                                    showTreeConflictsDialog(conflicts)
                                }
                            } else {
                                layoutConflictBanner.visibility = View.GONE
                            }
                        }
                    }
                    val nextList = pendingConflictScanPersons
                    if (nextList != null) {
                        pendingConflictScanPersons = null
                        currentList = nextList
                        currentToken = conflictScanToken
                    } else {
                        break
                    }
                }
            } finally {
                isScanningConflicts = false
            }
        }.start()
    }

    private fun showTreeConflictsDialog(conflicts: List<com.example.btproject2.engine.ConflictScanner.Conflict>) {
        if (conflicts.isEmpty()) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_conflict_wizard, null, false)
        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvHeaderBadge = dialogView.findViewById<TextView>(R.id.tvWizardHeaderBadge)
        val btnFixAll = dialogView.findViewById<TextView>(R.id.btnWizardFixAll)
        val tvModalTitle = dialogView.findViewById<TextView>(R.id.tvWizardModalTitle)
        val tvModalSub = dialogView.findViewById<TextView>(R.id.tvWizardModalSub)
        val tvStepCounter = dialogView.findViewById<TextView>(R.id.tvWizardStepCounter)
        val tvStepPercent = dialogView.findViewById<TextView>(R.id.tvWizardStepPercent)
        val progressSteps = dialogView.findViewById<ProgressBar>(R.id.progressWizardSteps)

        val tvPersonName = dialogView.findViewById<TextView>(R.id.tvWizardPersonName)
        val tvStepTag = dialogView.findViewById<TextView>(R.id.tvWizardStepTag)
        val tvHeadline = dialogView.findViewById<TextView>(R.id.tvWizardQuestionHeadline)
        val tvExplain = dialogView.findViewById<TextView>(R.id.tvWizardQuestionExplain)
        val tvHelper = dialogView.findViewById<TextView>(R.id.tvWizardHelperPrompt)
        val layoutOptionsGroup = dialogView.findViewById<LinearLayout>(R.id.layoutWizardOptionsGroup)
        val btnPrimary = dialogView.findViewById<Button>(R.id.btnWizardPrimaryChoice)
        val btnSecondary = dialogView.findViewById<Button>(R.id.btnWizardSecondaryChoice)
        val btnPrev = dialogView.findViewById<Button>(R.id.btnWizardPrev)
        val btnSkip = dialogView.findViewById<Button>(R.id.btnWizardSkip)

        // Hide reckless blind bulk fix; always guide user through verified steps
        btnFixAll.visibility = View.GONE

        tvHeaderBadge.text = "🛡️ GUIDED RESOLUTION STEPPER"
        tvHeaderBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.warning))
        tvModalTitle.text = "Guided Family Tree Resolution"
        tvModalSub.text = "Step through relationship details with guided instructions."

        val activeConflicts = conflicts.toMutableList()
        var currentIndex = 0

        fun refreshCanvasAfterRepair() {
            val cached = FirestoreHelper.getCachedPersons(treeId.ifBlank { null })
            if (!cached.isNullOrEmpty()) {
                allPersonsList = cached.filter { it.treeId.isEmpty() || it.treeId == treeId }
            }
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
            checkForTreeConflicts(allPersonsList)
        }

        fun renderCompleteState() {
            tvHeaderBadge.text = "🏆 ALL ISSUES RESOLVED"
            tvHeaderBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
            btnFixAll.visibility = View.GONE
            tvModalTitle.text = "Tree Integrity Verified!"
            tvModalSub.text = "All relationship connections, spouses, and parent links are clean."
            tvStepCounter.text = "All Steps Complete"
            tvStepPercent.text = "100% Verified"
            progressSteps.progress = 100

            tvPersonName.text = "🎉 Family Tree Healthy"
            tvStepTag.text = "✓ Flawless"
            tvStepTag.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
            tvHeadline.text = "“Tree Integrity Verified!”"
            tvExplain.text = "All missing parent and spouse spots have been cleaned up. You can go back to exploring your family tree now."
            tvHelper.text = "✨ 0 issues remaining."
            layoutOptionsGroup?.removeAllViews()

            btnPrimary.isEnabled = true
            btnPrimary.text = "✓ Back to Family Tree"
            btnPrimary.setOnClickListener { dialog.dismiss() }
            btnSecondary.visibility = View.GONE
            btnPrev.visibility = View.GONE
            btnSkip.text = "Close"
            btnSkip.setOnClickListener { dialog.dismiss() }
        }

        fun renderCurrentStep() {
            if (activeConflicts.isEmpty()) {
                renderCompleteState()
                return
            }
            if (currentIndex >= activeConflicts.size) {
                currentIndex = activeConflicts.size - 1
            }
            if (currentIndex < 0) {
                currentIndex = 0
            }

            val total = activeConflicts.size
            val item = activeConflicts[currentIndex]
            val pct = (((currentIndex + 1) * 100) / total).coerceIn(1, 100)

            tvStepCounter.text = "Issue ${currentIndex + 1} of $total"
            tvStepPercent.text = "$pct% Done"
            progressSteps.progress = pct

            tvPersonName.text = "👤 ${item.personName}"
            tvStepTag.text = "Issue ${currentIndex + 1}"
            tvHeadline.text = item.questionHeadline
            tvExplain.text = item.simpleExplanation
            tvHelper.text = item.helperPrompt

            btnSecondary.visibility = View.GONE
            layoutOptionsGroup?.removeAllViews()

            val currentOptions = if (item.options.isNotEmpty()) {
                item.options
            } else {
                listOf(
                    com.example.btproject2.engine.ConflictScanner.ResolutionOption(
                        id = "default_safe_fix",
                        title = item.primaryActionLabel.replace("✨", "").replace("✅", "").trim(),
                        tag = "Safe Fix",
                        tagType = "safe",
                        description = item.simpleExplanation,
                        actionType = if (item.canAutoFix) com.example.btproject2.engine.ConflictScanner.ResolutionActionType.UNLINK else com.example.btproject2.engine.ConflictScanner.ResolutionActionType.EDIT_PROFILE,
                        targetField = item.field
                    )
                )
            }

            var selectedOptionIndex = currentOptions.indexOfFirst { it.actionType == com.example.btproject2.engine.ConflictScanner.ResolutionActionType.SET_ADOPTIVE }
            if (selectedOptionIndex < 0) selectedOptionIndex = 0

            val cardViews = mutableListOf<View>()

            fun updateSelectionUI() {
                val chosen = currentOptions.getOrNull(selectedOptionIndex) ?: currentOptions[0]
                btnPrimary.isEnabled = true
                btnPrimary.text = "✨ Apply: ${chosen.title}"

                cardViews.forEachIndexed { idx, cardView ->
                    val isSelected = idx == selectedOptionIndex
                    cardView.setBackgroundResource(
                        if (isSelected) R.drawable.bg_wizard_option_card_selected else R.drawable.bg_wizard_option_card_default
                    )
                    val ivRadio = cardView.findViewById<ImageView>(R.id.ivOptionRadio)
                    ivRadio?.setImageResource(
                        if (isSelected) R.drawable.ic_wizard_radio_checked else R.drawable.ic_wizard_radio_unchecked
                    )
                }
            }

            currentOptions.forEachIndexed { index, option ->
                val cardView = layoutInflater.inflate(R.layout.item_wizard_resolution_option, layoutOptionsGroup, false)
                val tvTitle = cardView.findViewById<TextView>(R.id.tvOptionTitle)
                val tvBadge = cardView.findViewById<TextView>(R.id.tvOptionBadge)
                val tvDesc = cardView.findViewById<TextView>(R.id.tvOptionDescription)

                tvTitle.text = option.title
                tvBadge.text = option.tag
                when (option.tagType) {
                    "legal" -> {
                        tvBadge.setBackgroundResource(R.drawable.badge_pill_mint)
                        tvBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.mint_text))
                    }
                    "edit" -> {
                        tvBadge.setBackgroundResource(R.drawable.badge_pill_slate)
                        tvBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary))
                    }
                    else -> {
                        tvBadge.setBackgroundResource(R.drawable.badge_pill_gold)
                        tvBadge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text))
                    }
                }
                tvDesc.text = option.description

                cardView.setOnClickListener {
                    selectedOptionIndex = index
                    updateSelectionUI()
                }

                cardViews.add(cardView)
                layoutOptionsGroup?.addView(cardView)
            }

            updateSelectionUI()

            btnPrev.visibility = if (total > 1) View.VISIBLE else View.GONE
            btnPrev.isEnabled = currentIndex > 0
            btnPrev.alpha = if (currentIndex > 0) 1.0f else 0.45f
            btnSkip.text = if (currentIndex < total - 1) "Skip for Now →" else "Close"

            btnPrimary.setOnClickListener {
                if (isViewer) {
                    dialog.dismiss()
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Update Family Connection"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                    return@setOnClickListener
                }

                val chosen = currentOptions.getOrNull(selectedOptionIndex) ?: currentOptions[0]
                if (chosen.actionType == com.example.btproject2.engine.ConflictScanner.ResolutionActionType.EDIT_PROFILE) {
                    dialog.dismiss()
                    val targetPersonId = if (item.field == "motherId" && chosen.id.contains("mother")) {
                        item.relatedPersonId.ifBlank { item.personId }
                    } else {
                        item.personId
                    }
                    val intent = Intent(this, EditMemberActivity::class.java).apply {
                        putExtra("personId", targetPersonId)
                        putExtra("TREE_ID", treeId)
                        putExtra("isViewer", isViewer)
                    }
                    startActivity(intent)
                    return@setOnClickListener
                }

                btnPrimary.isEnabled = false
                btnPrimary.text = "⏳ Applying Resolution..."
                firestoreHelper.executeResolutionAction(
                    conflict = item,
                    option = chosen,
                    allPersons = allPersonsList,
                    onSuccess = { updatedPerson ->
                        runOnUiThread {
                            if (updatedPerson != null) {
                                allPersonsList = allPersonsList.map { if (it.id == updatedPerson.id) updatedPerson else it }
                            }
                            activeConflicts.removeAt(currentIndex)
                            KinTraceAlert.conflict(this, "Resolution Applied", "${chosen.title} saved successfully.")
                            refreshCanvasAfterRepair()
                            renderCurrentStep()
                        }
                    },
                    onFailure = { err ->
                        runOnUiThread {
                            btnPrimary.isEnabled = true
                            btnPrimary.text = "✨ Apply: ${chosen.title}"
                            KinTraceAlert.warning(this, "Could Not Save", err.message ?: "Please try again.")
                        }
                    }
                )
            }

            btnSecondary.setOnClickListener {
                if (isViewer) {
                    dialog.dismiss()
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Edit Family Member"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                    return@setOnClickListener
                }
                dialog.dismiss()
                val intent = Intent(this, EditMemberActivity::class.java).apply {
                    putExtra("personId", item.personId)
                    putExtra("TREE_ID", treeId)
                    putExtra("isViewer", isViewer)
                }
                startActivity(intent)
            }
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                renderCurrentStep()
            }
        }

        btnSkip.setOnClickListener {
            if (currentIndex < activeConflicts.size - 1) {
                currentIndex++
                renderCurrentStep()
            } else {
                dialog.dismiss()
            }
        }

        btnFixAll.setOnClickListener {
            if (isViewer) {
                dialog.dismiss()
                ActionRestrictedDialog.newInstance(
                    treeId = treeId,
                    treeName = currentTreeName,
                    actionAttempted = "Auto-Clean Family Connections"
                ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                return@setOnClickListener
            }
            val fixable = activeConflicts.filter { it.canAutoFix }
            if (fixable.isEmpty()) {
                KinTraceAlert.show(this, "Please open the member's page to review birth dates.")
                return@setOnClickListener
            }
            btnFixAll.isEnabled = false
            btnFixAll.text = "⏳ Fixing..."
            btnPrimary.isEnabled = false
            firestoreHelper.autoRepairAllConflicts(
                conflicts = fixable,
                allPersons = allPersonsList,
                onSuccess = {
                    runOnUiThread {
                        activeConflicts.removeAll(fixable.toSet())
                        refreshCanvasAfterRepair()
                        renderCurrentStep()
                    }
                },
                onFailure = { err ->
                    runOnUiThread {
                        btnFixAll.isEnabled = true
                        btnFixAll.text = "✨ Fix All at Once"
                        btnPrimary.isEnabled = true
                        KinTraceAlert.warning(this, "Auto-Fix Failed", err.message ?: "Please try again.")
                    }
                }
            )
        }

        renderCurrentStep()
        dialog.show()
    }

    // ══════════════════════════════════════════════════════════════════
    // Automatic Profile Reveal Modal with Photos & Document Proofs
    // ══════════════════════════════════════════════════════════════════
    private var tutorialSelectedPersonId: String? = null

    fun openTutorialFounderProfile() {
        if (activeProfileDialog == null && !isFinishing && !isDestroyed) {
            val targetId = tutorialSelectedPersonId
                ?: treeView.getFirstVisiblePersonId()
                ?: allPersonsList.firstOrNull()?.id
            if (targetId != null) {
                showMemberProfileDialog(targetId)
            }
        }
    }

    fun closeTutorialProfileDialog() {
        if (!isFinishing && !isDestroyed) {
            val spotlightView = com.example.btproject2.utils.KinTraceSpotlightManager.getActiveSpotlightView()
            val activityRoot = findViewById<ViewGroup>(android.R.id.content)
            if (spotlightView != null && activityRoot != null && spotlightView.parent !== activityRoot) {
                try {
                    (spotlightView.parent as? ViewGroup)?.removeView(spotlightView)
                    activityRoot.addView(
                        spotlightView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    spotlightView.bringToFront()
                } catch (_: Exception) {}
            }

            activeProfileDialog?.let { dialog ->
                dialog.setOnDismissListener(null)
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
            activeProfileDialog = null
            activeProfilePersonId = null
        }
    }

    fun getActiveProfileDialog(): android.app.Dialog? = activeProfileDialog

    private fun showMemberProfileDialog(personId: String) {
        if (isFinishing || isDestroyed) return
        val person = allPersonsList.find { it.id == personId } ?: return
        activeProfileDialog?.let { prevDialog ->
            if (prevDialog.isShowing) {
                prevDialog.setOnDismissListener(null)
                prevDialog.dismiss()
            }
        }
        activeProfilePersonId = personId

        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_member_profile, null)
        val dialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setView(dialogView)
            .create()

        activeProfileDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)

        // Find views
        val btnClose = dialogView.findViewById<TextView>(R.id.btnProfileClose)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
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
        val circ = DocumentHelper.getCachedCircularAvatar(person.photoBase64, 144)
        if (circ != null) {
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
        val kinshipTitle = if (person.id == focalPedigreePersonId) {
            "\u2605 YOU (SELF)"
        } else {
            KinshipTitleHelper.resolveTitle(person, focalPerson, allPersonsList)
        }
        val kinshipSubtitle = if (person.id == focalPedigreePersonId) {
            "Focal Center (${person.firstName})"
        } else {
            KinshipTitleHelper.resolveSubtitle(person, focalPerson, allPersonsList)
        }

        val spouseObj = allPersonsList.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.spouseId) }
            ?: allPersonsList.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, person.id) }
        val linkedSpouses = com.example.btproject2.engine.FamilyLinkValidator.findLinkedSpouses(person, allPersonsList)

        val shouldHideBirth = !isOwner && treePrivacy.hideBirthDates
        val shouldHideDeceased = !isOwner && treePrivacy.hideDeceasedStatus

        tvKinshipBadge?.text = if (!person.isLiving && !shouldHideDeceased) "🕊️ $kinshipTitle" else kinshipTitle
        val livingStr = if (!shouldHideDeceased) {
            if (person.isLiving) "Living" else "🕊️ Deceased"
        } else ""
        val effectiveCivilStatus = if (spouseObj != null) {
            if ((!spouseObj.isLiving || spouseObj.deathDate.isNotBlank()) && person.isLiving) "Widowed" else "Married"
        } else {
            person.maritalStatus.ifEmpty { "Single" }
        }
        tvSubtitle.text = if (livingStr.isNotEmpty()) {
            "$kinshipSubtitle · $effectiveCivilStatus · $livingStr"
        } else {
            "$kinshipSubtitle · $effectiveCivilStatus"
        }
        if (!person.isLiving && !shouldHideDeceased) {
            tvSubtitle.setTextColor(resources.getColor(R.color.text_secondary, null))
        } else {
            tvSubtitle.setTextColor(resources.getColor(R.color.mint_text, null))
        }

        val btnToggleVital = dialogView.findViewById<TextView?>(R.id.btnProfileToggleVitalStatus)
        if (shouldHideDeceased || isViewer) {
            btnToggleVital?.visibility = View.GONE
        } else {
            btnToggleVital?.visibility = View.VISIBLE
            btnToggleVital?.text = if (person.isLiving) "🕊️ Mark as Deceased" else "🌱 Mark as Living"
            btnToggleVital?.setOnClickListener {
                showQuickVitalStatusDialog(person)
            }
        }

                val isCurrentFocal = person.id == focalPedigreePersonId
        btnSetAsFocal?.text = if (isCurrentFocal) {
            "\uD83C\uDFAF Center on ${person.firstName} (Current Perspective)"
        } else {
            "\uD83C\uDFAF Center & View Tree from ${person.firstName}"
        }
        btnSetAsFocal?.setOnClickListener {
            focalPedigreePersonId = person.id
            val idx = allPersonsList.indexOfFirst { it.id == person.id }
            if (idx >= 0) spinnerPedigreeFocal.setSelection(idx)
            treeView.setMode(currentMode, highlightPathIds, mrcaId, focalPedigreePersonId)
            treeView.post {
                treeView.centerOnPerson(person.id, animate = true)
            }
            updateFocalResetButton()
            KinTraceAlert.show(this, "Tree perspective centered on ${person.firstName}")
            dialog.dismiss()
        }

        // Direction 1 Subtle Shield: Age & Sex Badges
        if (shouldHideBirth) {
            tvAgeBadge.text = "🔒 Private"
            tvAgeBadge.setTextColor(resources.getColor(R.color.gold_accent, null))
        } else {
            val ageStr = DocumentHelper.calculateAge(person.birthDate, person.deathDate, person.isLiving)
            tvAgeBadge.text = ageStr.ifEmpty { "Age N/A" }
            tvAgeBadge.setTextColor(resources.getColor(R.color.gold_accent, null))
        }
        val isFem = person.gender.equals("Female", ignoreCase = true)
        tvSexBadge.text = "${person.gender} ${if (isFem) "♀" else "♂"}"
        tvSexBadge.setTextColor(if (isFem) resources.getColor(R.color.pink, null) else resources.getColor(R.color.emerald_accent, null))

        // Vital details: Direction 1 Subtle Shield
        if (shouldHideBirth) {
            tvBirth.text = "🔒 Hidden by Owner"
            tvBirth.setTextColor(resources.getColor(R.color.text_hint, null))
            tvBirth.setTypeface(null, android.graphics.Typeface.ITALIC)
        } else {
            val bDate = person.birthDate.ifEmpty { "Unknown" }
            val bPlace = if (person.birthPlace.isNotEmpty()) " · ${person.birthPlace}" else ""
            tvBirth.text = "$bDate$bPlace"
            tvBirth.setTextColor(resources.getColor(R.color.text_primary, null))
            tvBirth.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

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

        val spName = if (linkedSpouses.size > 1) {
            linkedSpouses.joinToString(" · ") { sp ->
                val isPrimary = com.example.btproject2.engine.FamilyLinkValidator.isSameId(sp.id, person.spouseId)
                val tag = if (isPrimary) "" else if (!sp.isLiving || sp.deathDate.isNotBlank()) " (Late)" else " (Former)"
                "${sp.firstName} ${sp.lastName}$tag"
            }
        } else {
            (spouseObj ?: linkedSpouses.firstOrNull())?.let { "${it.firstName} ${it.lastName}" } ?: "None"
        }
        tvSpouse.text = spName

        // Documents & Verification Badge
        renderProfileDocuments(person, layoutDocsContainer, tvNoDocs, tvVerificationBadge)

        val btnAddSpouse = dialogView.findViewById<TextView?>(R.id.btnProfileAddSpouse)
        val rowProfileSpouse = dialogView.findViewById<View?>(R.id.rowProfileSpouse)
        val rowProfileParents = dialogView.findViewById<View?>(R.id.rowProfileParents)

        if (isViewer) {
            btnChangePhoto.visibility = View.GONE
            btnAttachDoc.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnAddChild.visibility = View.GONE
            btnAddSpouse?.visibility = View.GONE
            btnToggleVital?.visibility = View.GONE
            tvSubtitle.setOnClickListener {
                dialog.dismiss()
                ActionRestrictedDialog.newInstance(
                    treeId = treeId,
                    treeName = currentTreeName,
                    actionAttempted = "Modify Vital Status"
                ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
            }
            rowProfileSpouse?.setOnClickListener {
                dialog.dismiss()
                ActionRestrictedDialog.newInstance(
                    treeId = treeId,
                    treeName = currentTreeName,
                    actionAttempted = "Modify Spouse Connection"
                ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
            }
            rowProfileParents?.setOnClickListener {
                dialog.dismiss()
                ActionRestrictedDialog.newInstance(
                    treeId = treeId,
                    treeName = currentTreeName,
                    actionAttempted = "Modify Parent Connection"
                ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
            }
        } else {
            tvSubtitle.setOnClickListener {
                showQuickVitalStatusDialog(person)
            }
            btnChangePhoto.visibility = View.VISIBLE
            btnAttachDoc.visibility = View.VISIBLE
            btnEdit.visibility = View.VISIBLE
            btnAddChild.visibility = View.VISIBLE
            btnAddSpouse?.visibility = View.VISIBLE
            btnAttachDoc.setOnClickListener {
                activeProfilePersonId = person.id
                documentPickerLauncher.launch("*/*")
            }
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
                    putExtra("isViewer", isViewer)
                }
                startActivity(intent)
            }
        }

        btnFullDetail.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, MemberDetailActivity::class.java).apply {
                putExtra("personId", person.id)
                putExtra("TREE_ID", treeId)
                putExtra("isViewer", isViewer)
            }
            startActivity(intent)
        }

        dialog.setOnDismissListener {
            if (activeProfileDialog === dialog) {
                activeProfileDialog = null
                if (activeProfilePersonId == person.id) {
                    activeProfilePersonId = null
                }
            }
            if (com.example.btproject2.utils.KinTraceSpotlightManager.isTourActive()) {
                com.example.btproject2.utils.KinTraceSpotlightManager.dismissActiveOverlay()
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

            val alertDialog = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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
                        KinTraceAlert.show(this, "Date of passing cannot be earlier than birthdate (${person.birthDate}).")
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
                            KinTraceAlert.show(this, "Cannot set date of passing earlier than biological child ${child.firstName}'s birthdate (${child.birthDate}).")
                            return@setOnClickListener
                        }
                    }
                }

                // Marriage date check
                if (person.marriageDate.isNotBlank() && effectiveDate.isNotBlank()) {
                    val mCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(person.marriageDate)
                    val dCal = com.example.btproject2.engine.FamilyLinkValidator.parseDateToCalendar(effectiveDate)
                    if (mCal != null && dCal != null && mCal.after(dCal)) {
                        KinTraceAlert.show(this, "Date of passing cannot be earlier than marriage date (${person.marriageDate}).")
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
                        activeProfileDialog?.setOnDismissListener(null)
                        activeProfileDialog?.dismiss()
                        activeProfileDialog = null
                        activeProfilePersonId = person.id
                        KinTraceAlert.show(this, "Marked as Deceased")
                        loadTree(refreshProfile = true)
                    },
                    onFailure = { err ->
                        btnConfirm.isEnabled = true
                        btnConfirm.text = "Mark Deceased"
                        KinTraceAlert.show(this, "Failed to update: ${err.message}")
                    }
                )
            }
            alertDialog.show()
        } else {
            val pName = "${person.firstName} ${person.lastName}".trim()
            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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
                            activeProfileDialog?.setOnDismissListener(null)
                            activeProfileDialog?.dismiss()
                            activeProfileDialog = null
                            activeProfilePersonId = person.id
                            KinTraceAlert.show(this, "Marked as Living")
                            loadTree(refreshProfile = true)
                        },
                        onFailure = { err ->
                            KinTraceAlert.show(this, "Failed to update: ${err.message}")
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
            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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

        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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
            KinTraceAlert.show(this, "No other members available in the tree.")
            return
        }

        val candidateNames = candidates.map { candidate ->
            val genderTag = if (candidate.gender.isNotBlank()) " (${candidate.gender})" else ""
            val ageVal = com.example.btproject2.engine.MarriageValidationEngine.calculateAge(candidate)
            val ageTag = if (ageVal != null) ", age $ageVal" else ""
            "${candidate.firstName} ${candidate.lastName}$genderTag$ageTag"
        }.toTypedArray()

        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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

                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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
                        KinTraceAlert.show(this, "Spouse relationship successfully saved!")
                        loadTree(refreshProfile = true)
                    },
                    onFailure = { err ->
                        KinTraceAlert.show(this, "Failed to save spouse: ${err.message}")
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

        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Add Child Connection ($parentName)")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        // Choice 1: Add a biological or adoptive child
                        if (parent != null && (parent.allSpouseIds().isNotEmpty() || com.example.btproject2.engine.FamilyLinkValidator.findLinkedSpouses(parent, allPersonsList).isNotEmpty())) {
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
            com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
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
                val isWidowed = sp != null && (!sp.isLiving || sp.deathDate.isNotBlank())
                if (isWidowed) " (Widowed · Late spouse: $spName)" else " (Married to $spName)"
            } else ""
            "${child.firstName} ${child.lastName}$spouseNote"
        }.toTypedArray()

        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Select Existing Child of $parentName")
            .setItems(childNames) { _, which ->
                val selectedChild = children[which]
                if (!selectedChild.spouseId.isNullOrBlank()) {
                    val sp = allPersonsList.find { it.id == selectedChild.spouseId }
                    val isActiveMarriage = sp != null && sp.isLiving && sp.deathDate.isBlank() &&
                        selectedChild.maritalStatus.equals("Married", ignoreCase = true)
                    if (isActiveMarriage) {
                        val spName = "${sp?.firstName.orEmpty()} ${sp?.lastName.orEmpty()}".trim().ifEmpty { "another spouse" }
                        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                            .setTitle("Child Already Married")
                            .setMessage("Unable to add this person as a spouse because one member already has an active spouse relationship.\n\n${selectedChild.firstName} ${selectedChild.lastName} is already married to $spName (Philippine Family Code Articles 35(4) and 40).")
                            .setPositiveButton("Understood", null)
                            .show()
                        return@setItems
                    }
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
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.audit_warning_text))
            badge.setBackgroundResource(R.drawable.badge_pill_dark)
        } else {
            tvNoDocs.visibility = View.GONE
            badge.text = "✓ Verified (${docs.size} Docs)"
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.status_mint_text))
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

                btnDelete.visibility = if (isViewer) View.GONE else View.VISIBLE
                btnDelete.setOnClickListener {
                    if (isViewer) {
                        KinTraceAlert.warning(this, "Read-Only Access", "Read-only Viewers cannot remove attached documents.")
                        return@setOnClickListener
                    }
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle("Remove Document")
                        .setMessage("Remove \"${doc.name}\" from ${person.firstName}'s profile?")
                        .setPositiveButton("Remove") { _, _ ->
                            val targetPersonId = person.id
                            firestoreHelper.removeDocument(targetPersonId, doc.id,
                                onSuccess = {
                                    KinTraceAlert.show(this, "Document removed")
                                    if (activeProfilePersonId == null) {
                                        activeProfilePersonId = targetPersonId
                                    }
                                    showMemberProfileDialog(targetPersonId)
                                },
                                onFailure = {
                                    KinTraceAlert.show(this, "Failed to remove: ${it.message}")
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

class FamilyTreeView @JvmOverloads constructor(
    context: Context,
    private val onOpenPerson:  (personId: String) -> Unit = {},
    private val onAddChild:    (parentId: String) -> Unit = {},
    private val onAddFirst:    () -> Unit = {},
    private val onAddAncestor: (childId: String, isFather: Boolean) -> Unit = { _, _ -> }
) : View(context) {

    var isExportMode: Boolean = false

    private val pWaypointGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#48D4AF37")
        style = Paint.Style.FILL
    }
    private val pWaypointInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.FILL
    }
    private val pWaypointBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val pWaypointPip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    data class TreeWaypointHandle(
        val id: String,
        val busId: String,
        var x: Float,
        var y: Float
    )
    val waypointHandles = mutableListOf<TreeWaypointHandle>()

    var isOrganizeMode: Boolean = false
        set(value) {
            field = value
            precomputeMainTreeConnectorsAndHitAreas()
            invalidate()
        }

    val customNodePositions = mutableMapOf<String, CustomNodePosition>()
    val customBusOffsets = mutableMapOf<String, CustomBusOffset>()
    val undoStack = java.util.ArrayDeque<LayoutHistoryAction>()

    var onUndoStackChanged: ((count: Int) -> Unit)? = null
    var onLayoutModified: (() -> Unit)? = null

    private var draggedHandle: TreeWaypointHandle? = null
    private var draggedCardPersonId: String? = null
    private var draggedSpouseId: String? = null
    private var dragStartTouchX = 0f
    private var dragStartTouchY = 0f
    private var dragStartCardX = 0f
    private var dragStartCardY = 0f
    private var dragStartSpouseX = 0f
    private var dragStartSpouseY = 0f
    private var dragStartBusY = 0f
    private var dragStartDropX = 0f
    private var isDraggingInOrganize = false

    var isViewerMode: Boolean = false
    var treePrivacy: com.example.btproject2.models.PrivacySettings = com.example.btproject2.models.PrivacySettings()

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

    // ── Choice 7: Minimalist Executive Typefaces ──────────────────
    private val tfSoraBold: Typeface = ResourcesCompat.getFont(context, R.font.sora_bold) ?: Typeface.DEFAULT_BOLD
    private val tfSoraSemiBold: Typeface = ResourcesCompat.getFont(context, R.font.sora_semibold) ?: Typeface.DEFAULT_BOLD
    private val tfInterRegular: Typeface = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    private val tfInterMedium: Typeface = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
    private val tfInterBold: Typeface = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD
    private val tfJetBrainsMono: Typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium) ?: Typeface.MONOSPACE

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
    private val pFocalPillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A1F08"); style = Paint.Style.FILL
    }
    private val pFocalGoldBeaconHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDE68A"); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val pFocalRestingBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37"); style = Paint.Style.STROKE; strokeWidth = 2.5f
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
    private val pLineAdoptive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B"); style = Paint.Style.STROKE
        strokeWidth = 3.0f; pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
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
        color = Color.parseColor("#F5D77F"); textSize = 13f; textAlign = Paint.Align.CENTER; typeface = tfSoraBold
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
        typeface = tfInterBold
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
        color = Color.parseColor("#A3B899"); textSize = 22f; textAlign = Paint.Align.CENTER; typeface = tfSoraBold
    }
    private val pDashedLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A9A88"); textSize = 20f; textAlign = Paint.Align.CENTER; typeface = tfInterMedium
    }

    private val pInitials = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f; textAlign = Paint.Align.CENTER; typeface = tfSoraBold
    }
    private val pFirstName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 16f; textAlign = Paint.Align.CENTER; typeface = tfSoraBold
    }
    private val pLastName = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A3B899"); textSize = 14f; textAlign = Paint.Align.CENTER; typeface = tfInterMedium
    }
    private val pYear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A9A88"); textSize = 12f; textAlign = Paint.Align.CENTER; typeface = tfJetBrainsMono
    }
    private val pMrcaBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); textSize = 12f; textAlign = Paint.Align.CENTER; typeface = tfInterBold
    }

    // Role Badges for Bloodline Tracing
    private val pPersonABadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4A359"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = tfInterBold
    }
    private val pPersonBBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4ECCA3"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = tfInterBold
    }
    private val pBloodlineBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5D77F"); textSize = 11f; textAlign = Paint.Align.CENTER; typeface = tfInterBold
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
    private val pLineAdoptiveDim = Paint(pLineAdoptive).apply { alpha = 70 }
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

    fun applyDarkPalette() {
        pCardBg.color = Color.parseColor("#0F241A")
        pCardBorder.color = Color.parseColor("#234E3B")
        pCardBgDim.color = Color.parseColor("#0F241A"); pCardBgDim.alpha = 70
        pCardBorderDim.color = Color.parseColor("#234E3B"); pCardBorderDim.alpha = 70
        pCardDeceasedBg.color = Color.parseColor("#334155")
        pCardDeceasedBorder.color = Color.parseColor("#94A3B8")
        pCardDeceasedBgDim.color = Color.parseColor("#1E293B"); pCardDeceasedBgDim.alpha = 70
        pCardDeceasedBorderDim.color = Color.parseColor("#475569"); pCardDeceasedBorderDim.alpha = 70
        pCardHighlightBg.color = Color.parseColor("#1B3B2B")
        pCardHighlightBorder.color = Color.parseColor("#D4A359")
        pCardMrcaBg.color = Color.parseColor("#2A1F08")
        pCardMrcaBorder.color = Color.parseColor("#F5D77F")
        pFirstName.color = Color.WHITE
        pFirstNameDim.color = Color.WHITE; pFirstNameDim.alpha = 80
        pLastName.color = Color.parseColor("#A3B899")
        pLastNameDim.color = Color.parseColor("#A3B899"); pLastNameDim.alpha = 80
        pYear.color = Color.parseColor("#7A9A88")
        pYearDim.color = Color.parseColor("#7A9A88"); pYearDim.alpha = 80
        pYearDeceased.color = Color.parseColor("#94A3B8")
        pYearDeceasedDim.color = Color.parseColor("#94A3B8"); pYearDeceasedDim.alpha = 80
        pDashedCircle.color = Color.parseColor("#5A6E60")
        pDashedLine.color = Color.parseColor("#344C3D")
        pPedigreeLine.color = Color.parseColor("#1D9E75")
        pPedigreeDashedLine.color = Color.parseColor("#344C3D")
        pDashedText.color = Color.parseColor("#A3B899")
        pDashedLabel.color = Color.parseColor("#7A9A88")
        pLine.color = Color.parseColor("#1D9E75")
        pLineDim.color = Color.parseColor("#1D9E75"); pLineDim.alpha = 70
        pLineAdoptive.color = Color.parseColor("#64748B")
        pLineAdoptiveDim.color = Color.parseColor("#64748B"); pLineAdoptiveDim.alpha = 70
        pSpouseLine.color = Color.parseColor("#D4A359")
        pSpouseLineDim.color = Color.parseColor("#D4A359"); pSpouseLineDim.alpha = 70
        pGoldPathLine.color = Color.parseColor("#D4A359")
        pMaritalPillBg.color = Color.parseColor("#1B2E24")
        pMaritalPillBorder.color = Color.parseColor("#D4A359")
        pMaritalPillText.color = Color.parseColor("#F5D77F")
        pMaritalPillBgDim.color = Color.parseColor("#1B2E24"); pMaritalPillBgDim.alpha = 80
        pMaritalPillBorderDim.color = Color.parseColor("#D4A359"); pMaritalPillBorderDim.alpha = 80
        pMaritalPillTextDim.color = Color.parseColor("#F5D77F"); pMaritalPillTextDim.alpha = 80
        pTitleBg.color = Color.parseColor("#132B20")
        pTitleBorder.color = Color.parseColor("#D4A359")
        pTitleText.color = Color.parseColor("#F5D77F")
        pTitleBgDim.color = Color.parseColor("#132B20"); pTitleBgDim.alpha = 70
        pTitleBorderDim.color = Color.parseColor("#D4A359"); pTitleBorderDim.alpha = 70
        pTitleTextDim.color = Color.parseColor("#F5D77F"); pTitleTextDim.alpha = 70
        pDeceasedTitleBg.color = Color.parseColor("#1E293B")
        pDeceasedTitleBorder.color = Color.parseColor("#64748B")
        pDeceasedTitleText.color = Color.parseColor("#CBD5E1")
        pDeceasedTitleBgDim.color = Color.parseColor("#1E293B"); pDeceasedTitleBgDim.alpha = 70
        pDeceasedTitleBorderDim.color = Color.parseColor("#64748B"); pDeceasedTitleBorderDim.alpha = 70
        pDeceasedTitleTextDim.color = Color.parseColor("#CBD5E1"); pDeceasedTitleTextDim.alpha = 70
        pMemBg.color = Color.parseColor("#334155")
        pPersonABadge.color = Color.parseColor("#D4A359")
        pPersonBBadge.color = Color.parseColor("#4ECCA3")
        pBloodlineBadge.color = Color.parseColor("#F5D77F")
        pMrcaBadge.color = Color.parseColor("#D4A359")
        pFab.color = Color.parseColor("#D4A359")
        pFabIcon.color = Color.parseColor("#0A1B12")
        pEmpty.color = Color.parseColor("#A3B899")
        pEmptySub.color = Color.parseColor("#7A9A88")
    }

    fun updateThemeColors() {
        val isDark = ThemePreferences.isDarkTheme(context)
        if (isDark) {
            applyDarkPalette()
        } else {
            pCardBg.color = Color.parseColor("#F5F7F1")
            pCardBorder.color = Color.parseColor("#D2D9CE")
            pCardBgDim.color = Color.parseColor("#E2E8F0"); pCardBgDim.alpha = 160
            pCardBorderDim.color = Color.parseColor("#CBD5E1"); pCardBorderDim.alpha = 160
            pCardDeceasedBg.color = Color.parseColor("#E2E8F0")
            pCardDeceasedBorder.color = Color.parseColor("#94A3B8")
            pCardDeceasedBgDim.color = Color.parseColor("#E2E8F0"); pCardDeceasedBgDim.alpha = 140
            pCardDeceasedBorderDim.color = Color.parseColor("#94A3B8"); pCardDeceasedBorderDim.alpha = 140
            pCardHighlightBg.color = Color.parseColor("#E6F6EF")
            pCardHighlightBorder.color = Color.parseColor("#B45309")
            pCardMrcaBg.color = Color.parseColor("#FEF3C7")
            pCardMrcaBorder.color = Color.parseColor("#B45309")
            pFirstName.color = Color.parseColor("#0F172A")
            pFirstNameDim.color = Color.parseColor("#0F172A"); pFirstNameDim.alpha = 110
            pLastName.color = Color.parseColor("#475569")
            pLastNameDim.color = Color.parseColor("#475569"); pLastNameDim.alpha = 110
            pYear.color = Color.parseColor("#64748B")
            pYearDim.color = Color.parseColor("#64748B"); pYearDim.alpha = 110
            pYearDeceased.color = Color.parseColor("#475569")
            pYearDeceasedDim.color = Color.parseColor("#475569"); pYearDeceasedDim.alpha = 110
            pDashedCircle.color = Color.parseColor("#64748B")
            pDashedLine.color = Color.parseColor("#64748B")
            pPedigreeLine.color = Color.parseColor("#B45309")
            pPedigreeDashedLine.color = Color.parseColor("#64748B")
            pDashedText.color = Color.parseColor("#475569")
            pDashedLabel.color = Color.parseColor("#64748B")
            pLine.color = Color.parseColor("#B45309")
            pLineDim.color = Color.parseColor("#B45309"); pLineDim.alpha = 90
            pLineAdoptive.color = Color.parseColor("#64748B")
            pLineAdoptiveDim.color = Color.parseColor("#64748B"); pLineAdoptiveDim.alpha = 90
            pSpouseLine.color = Color.parseColor("#B45309")
            pSpouseLineDim.color = Color.parseColor("#B45309"); pSpouseLineDim.alpha = 90
            pGoldPathLine.color = Color.parseColor("#B45309")
            pMaritalPillBg.color = Color.parseColor("#FEF3C7")
            pMaritalPillBorder.color = Color.parseColor("#B45309")
            pMaritalPillText.color = Color.parseColor("#92400E")
            pMaritalPillBgDim.color = Color.parseColor("#FEF3C7"); pMaritalPillBgDim.alpha = 130
            pMaritalPillBorderDim.color = Color.parseColor("#B45309"); pMaritalPillBorderDim.alpha = 130
            pMaritalPillTextDim.color = Color.parseColor("#92400E"); pMaritalPillTextDim.alpha = 130
            pTitleBg.color = Color.parseColor("#FEF3C7")
            pTitleBorder.color = Color.parseColor("#FDE68A")
            pTitleText.color = Color.parseColor("#92400E")
            pTitleBgDim.color = Color.parseColor("#FEF3C7"); pTitleBgDim.alpha = 130
            pTitleBorderDim.color = Color.parseColor("#FDE68A"); pTitleBorderDim.alpha = 130
            pTitleTextDim.color = Color.parseColor("#92400E"); pTitleTextDim.alpha = 130
            pDeceasedTitleBg.color = Color.parseColor("#CBD5E1")
            pDeceasedTitleBorder.color = Color.parseColor("#94A3B8")
            pDeceasedTitleText.color = Color.parseColor("#334155")
            pDeceasedTitleBgDim.color = Color.parseColor("#CBD5E1"); pDeceasedTitleBgDim.alpha = 130
            pDeceasedTitleBorderDim.color = Color.parseColor("#94A3B8"); pDeceasedTitleBorderDim.alpha = 130
            pDeceasedTitleTextDim.color = Color.parseColor("#334155"); pDeceasedTitleTextDim.alpha = 130
            pMemBg.color = Color.parseColor("#475569")
            pPersonABadge.color = Color.parseColor("#B45309")
            pPersonBBadge.color = Color.parseColor("#0F6E4B")
            pBloodlineBadge.color = Color.parseColor("#92400E")
            pMrcaBadge.color = Color.parseColor("#B45309")
            pFab.color = Color.parseColor("#B45309")
            pFabIcon.color = Color.WHITE
            pEmpty.color = Color.parseColor("#57635A")
            pEmptySub.color = Color.parseColor("#64748B")
        }
        invalidate()
    }

    // Reusable geometry buffers for zero GC allocation during onDraw
    private val tempCardRect = RectF()
    private val tempShadowRect = RectF()
    private val tempHaloRect = RectF()
    private val tempBarRect = RectF()
    private var cameraAnimator: ValueAnimator? = null
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
        val pillMidY: Float,
        val leftY: Float = y,
        val rightY: Float = y
    )

    private data class PrecomputedChildDrop(
        val childId: String,
        val x: Float,
        val yBus: Float,
        val targetY: Float,
        val isNonBiological: Boolean = false
    )

    private data class PrecomputedFamilyBus(
        val primaryId: String,
        val spouseId: String?,
        val dropX: Float,
        val dropStartY: Float,
        val yBus: Float,
        val busLeft: Float,
        val busRight: Float,
        val childDrops: List<PrecomputedChildDrop>,
        val bridgeTargetX: Float = (busLeft + busRight) / 2f,
        val bridgeStepY: Float = (dropStartY + yBus) / 2f,
        val hasSteppedBridge: Boolean = false
    )

    private data class PrecomputedFallbackLine(
        val parentId: String,
        val secondParentId: String? = null,
        val childId: String,
        val pX: Float,
        val pY: Float,
        val cX: Float,
        val cY: Float,
        val isStep: Boolean,
        val midY: Float,
        val isNonBiological: Boolean = false
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
    private var mrcaPersonIds: Set<String> = emptySet()
    private var isAffinityTraceMode: Boolean = false
    private var focalPersonId: String? = null
    private var focalBeaconStartTime: Long = 0L
    private var tracePersonAId: String? = null

    private fun getFocalBeaconMetrics(): Pair<Float, Float> {
        val elapsed = if (!focalPersonId.isNullOrEmpty() && focalBeaconStartTime > 0L) {
            System.currentTimeMillis() - focalBeaconStartTime
        } else {
            Long.MAX_VALUE
        }
        if (elapsed >= 3600L) {
            return Pair(0f, 0f)
        }
        val fadeAlpha = when {
            elapsed <= 3000L -> 1.0f
            else -> (1.0f - (elapsed - 3000L) / 600f).coerceIn(0f, 1f)
        }
        // Heart-throbbing organic rhythm (~75 bpm -> 800ms cycle)
        val cycleFrac = ((elapsed % 800L) / 800.0) * 2.0 * Math.PI
        val throb = kotlin.math.sin(cycleFrac).toFloat().coerceIn(-1f, 1f)
        return Pair(fadeAlpha, throb)
    }
    private var tracePersonBId: String? = null

    data class FamilyUnit(
        val primary: Person,
        val spouse: Person?,
        var generation: Int,
        val children: MutableList<FamilyUnit> = mutableListOf(),
        val formerSpouses: MutableList<Person> = mutableListOf()
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

    fun zoomIn() { scaleFactor = (scaleFactor * 1.3f).coerceIn(0.15f, 4.0f); invalidate() }
    fun zoomOut() { scaleFactor = (scaleFactor / 1.3f).coerceIn(0.15f, 4.0f); invalidate() }
    fun centerOnPerson(personId: String, animate: Boolean = true) {
        val pt = posMap[personId] ?: return
        if (width <= 0 || height <= 0) {
            post { centerOnPerson(personId, animate) }
            return
        }

        focalPersonId = personId
        focalBeaconStartTime = System.currentTimeMillis()
        postInvalidateOnAnimation()

        val targetScale = 1.05f.coerceIn(0.6f, 1.4f)
        val targetOffsetX = (width / 2f - pt.x) - (width - canvasW) / 2f
        val targetOffsetY = (height / 2f - pt.y) - MARGIN / 2f

        cameraAnimator?.cancel()

        if (!animate) {
            scaleFactor = targetScale
            offsetX = targetOffsetX
            offsetY = targetOffsetY
            invalidate()
            return
        }

        val startScale = scaleFactor
        val startOffsetX = offsetX
        val startOffsetY = offsetY

        cameraAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { va ->
                val frac = va.animatedFraction
                scaleFactor = startScale + (targetScale - startScale) * frac
                offsetX = startOffsetX + (targetOffsetX - startOffsetX) * frac
                offsetY = startOffsetY + (targetOffsetY - startOffsetY) * frac
                invalidate()
            }
            start()
        }
    }

    fun resetView() {
        if (posMap.isNotEmpty() && width > 0 && height > 0) {
            val minX = posMap.values.minOf { it.x } - CARD_W
            val maxX = posMap.values.maxOf { it.x } + CARD_W
            val minY = posMap.values.minOf { it.y } - CARD_H
            val maxY = posMap.values.maxOf { it.y } + CARD_H
            val treeW = maxOf(maxX - minX, CARD_W * 2)
            val treeH = maxOf(maxY - minY, CARD_H * 2)
            val fitScale = minOf(width * 0.92f / treeW, height * 0.85f / treeH).coerceIn(0.25f, 1.2f)
            scaleFactor = fitScale
            offsetX = 0f
            offsetY = 0f
        } else {
            scaleFactor = 1.0f
            offsetX = 0f
            offsetY = 0f
        }
        invalidate()
    }

    fun frameTracePath() {
        val targetIds = highlightIds + mrcaPersonIds + listOfNotNull(mrcaPersonId, tracePersonAId, tracePersonBId)
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
        val newScale = minOf(fitScaleX, fitScaleY).coerceIn(0.25f, 1.5f)

        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f

        scaleFactor = newScale
        offsetX = (width / 2f - midX) - (width - canvasW) / 2f
        offsetY = (height / 2f - midY) - MARGIN / 2f
        invalidate()
    }

    private fun hasSameTopology(oldList: List<Person>, newList: List<Person>): Boolean {
        if (oldList.size != newList.size) return false
        val oldMap = oldList.associateBy { it.id }
        for (np in newList) {
            val op = oldMap[np.id] ?: return false
            if (op.fatherId != np.fatherId ||
                op.motherId != np.motherId ||
                op.spouseId != np.spouseId ||
                op.formerSpouseIds != np.formerSpouseIds ||
                op.gender != np.gender ||
                op.fatherRelationshipType != np.fatherRelationshipType ||
                op.motherRelationshipType != np.motherRelationshipType
            ) {
                return false
            }
        }
        return true
    }

    fun setDataAndMode(
        list: List<Person>,
        mode: String,
        highlightIds: Set<String>,
        mrcaId: String?,
        focalPedigreeId: String?,
        personAId: String? = null,
        personBId: String? = null,
        mrcaIds: Set<String> = emptySet(),
        isAffinityTrace: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        val resolvedMrcaIds = if (mrcaIds.isNotEmpty()) mrcaIds else setOfNotNull(mrcaId)
        val sameModeAndHighlights =
                this.mode == mode &&
                this.highlightIds == highlightIds &&
                this.mrcaPersonId == mrcaId &&
                this.mrcaPersonIds == resolvedMrcaIds &&
                this.isAffinityTraceMode == isAffinityTrace &&
                this.focalPersonId == focalPedigreeId &&
                this.tracePersonAId == personAId &&
                this.tracePersonBId == personBId
        val sameData = !forceRefresh && allPersons == list && sameModeAndHighlights
        val sameTopologyOnly = !forceRefresh &&
                sameModeAndHighlights &&
                displayPersons.isNotEmpty() &&
                posMap.isNotEmpty() &&
                hasSameTopology(allPersons, list)

        allPersons = list
        this.mode = mode
        this.highlightIds = highlightIds
        this.mrcaPersonId = mrcaId
        this.mrcaPersonIds = resolvedMrcaIds
        this.isAffinityTraceMode = isAffinityTrace
        if (focalPedigreeId != null && focalPedigreeId != this.focalPersonId) {
            this.focalBeaconStartTime = System.currentTimeMillis()
        } else if (focalPedigreeId == null) {
            this.focalBeaconStartTime = 0L
        }
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

        if (sameTopologyOnly) {
            val byId = allPersons.associateBy { it.id }
            displayPersons = displayPersons.map { byId[it.id] ?: it }
            precomputeCardDisplayData()
            invalidate()
        } else if (!sameData || displayPersons.isEmpty()) {
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
        personBId: String? = null,
        mrcaIds: Set<String> = emptySet(),
        isAffinityTrace: Boolean = false
    ) {
        val resolvedMrcaIds = if (mrcaIds.isNotEmpty()) mrcaIds else setOfNotNull(mrcaId)
        if (this.mode == mode &&
            this.highlightIds == highlightIds &&
            this.mrcaPersonId == mrcaId &&
            this.mrcaPersonIds == resolvedMrcaIds &&
            this.isAffinityTraceMode == isAffinityTrace &&
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
        this.mrcaPersonIds = resolvedMrcaIds
        this.isAffinityTraceMode = isAffinityTrace
        if (focalPedigreeId != null && focalPedigreeId != this.focalPersonId) {
            this.focalBeaconStartTime = System.currentTimeMillis()
        } else if (focalPedigreeId == null) {
            this.focalBeaconStartTime = 0L
        }
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

            val shouldHideBirth = isViewerMode && treePrivacy.hideBirthDates
            val shouldHideDeceased = isViewerMode && treePrivacy.hideDeceasedStatus

            val yr = if (shouldHideBirth) "" else (com.example.btproject2.engine.FamilyLinkValidator.extractYear(p.birthDate)?.toString() ?: p.birthDate.take(4).trim())
            val dYr = if (!p.isLiving && !shouldHideDeceased) {
                val d = com.example.btproject2.engine.FamilyLinkValidator.extractYear(p.deathDate)?.toString() ?: p.deathDate.take(4).trim()
                if (d.isNotEmpty()) "-$d" else "🕊️"
            } else ""
            val yrStr = if (shouldHideBirth) {
                if (dYr.isNotEmpty()) dYr else "🔒"
            } else if (yr.isNotEmpty()) {
                if (dYr.isNotEmpty()) "$yr $dYr" else "b. $yr"
            } else dYr

            val rawTitle = if (p.id == focalPersonId) {
                "\u2605 YOU (SELF)"
            } else {
                allTitles[p.id] ?: KinshipTitleHelper.resolveTitle(p, focalPerson, allPersons)
            }
            val titleWithIcon = if (!p.isLiving && !shouldHideDeceased) "🕊️ $rawTitle" else rawTitle
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
            customNodePositions.forEach { (pId, customPos) ->
                posMap[pId]?.set(customPos.x, customPos.y)
            }
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

        fun isValidCanvasPair(a: Person, b: Person): Boolean {
            if (canon(a.id) == canon(b.id)) return false
            if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(a.id, b.fatherId) ||
                com.example.btproject2.engine.FamilyLinkValidator.isSameId(a.id, b.motherId) ||
                com.example.btproject2.engine.FamilyLinkValidator.isSameId(b.id, a.fatherId) ||
                com.example.btproject2.engine.FamilyLinkValidator.isSameId(b.id, a.motherId)
            ) return false
            if (com.example.btproject2.engine.FamilyLinkValidator.isAncestorOf(a.id, b.id, byCanonId) ||
                com.example.btproject2.engine.FamilyLinkValidator.isAncestorOf(b.id, a.id, byCanonId)
            ) return false
            val sameFather = !a.fatherId.isNullOrBlank() && com.example.btproject2.engine.FamilyLinkValidator.isSameId(a.fatherId, b.fatherId)
            val sameMother = !a.motherId.isNullOrBlank() && com.example.btproject2.engine.FamilyLinkValidator.isSameId(a.motherId, b.motherId)
            if (sameFather || sameMother) return false
            return true
        }

        // Mutual spouse resolution (active/current spouse takes priority over historical/deceased former spouse)
        val spouseMap = mutableMapOf<String, String>()
        // Pass A: Reciprocal primary spouse links (where neither party lists the other in formerSpouseIds)
        displayPersons.forEach { p ->
            val sId = p.spouseId?.trim()
            if (!sId.isNullOrBlank()) {
                val spObj = byCanonId[canon(sId)]
                if (spObj != null &&
                    isValidCanvasPair(p, spObj) &&
                    p.formerSpouseIds.none { canon(it) == canon(spObj.id) } &&
                    spObj.formerSpouseIds.none { canon(it) == canon(p.id) } &&
                    !spouseMap.containsKey(p.id) &&
                    !spouseMap.containsKey(spObj.id)
                ) {
                    spouseMap[p.id] = spObj.id
                    spouseMap[spObj.id] = p.id
                }
            }
        }
        // Pass B: Any remaining primary spouseId links where neither party is already paired
        displayPersons.forEach { p ->
            val sId = p.spouseId?.trim()
            if (!sId.isNullOrBlank()) {
                val spObj = byCanonId[canon(sId)]
                if (spObj != null && isValidCanvasPair(p, spObj) && !spouseMap.containsKey(p.id) && !spouseMap.containsKey(spObj.id)) {
                    spouseMap[p.id] = spObj.id
                    spouseMap[spObj.id] = p.id
                }
            }
        }
        displayPersons.forEach { p ->
            val fId = p.fatherId?.trim()
            val mId = p.motherId?.trim()
            if (!fId.isNullOrBlank() && !mId.isNullOrBlank()) {
                val fObj = byCanonId[canon(fId)]
                val mObj = byCanonId[canon(mId)]
                if (fObj != null && mObj != null) {
                    if (!spouseMap.containsKey(fObj.id) && !spouseMap.containsKey(mObj.id)) {
                        if (isValidCanvasPair(fObj, mObj)) {
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

        // Build Family Units (including Symmetric Triad Hub for remarried members with former/late spouses)
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

        // Attach unclaimed former/late spouses into their remarried partner's FamilyUnit to form a Symmetric Triad Hub
        val unitsToRemove = mutableSetOf<FamilyUnit>()
        allUnits.forEach { unit ->
            if (unit in unitsToRemove) return@forEach
            val hubCandidates = listOfNotNull(unit.primary, unit.spouse)
            hubCandidates.forEach { member ->
                val formerIds = member.formerSpouseIds.map { canon(it) }.filter { it.isNotBlank() }.toMutableSet()
                displayPersons.forEach { other ->
                    if (canon(other.id) != canon(member.id) &&
                        canon(other.id) != canon(unit.primary.id) &&
                        canon(other.id) != canon(unit.spouse?.id) &&
                        (canon(other.spouseId) == canon(member.id) || other.formerSpouseIds.any { canon(it) == canon(member.id) })
                    ) {
                        formerIds.add(canon(other.id))
                    }
                }
                formerIds.forEach { fCanonId ->
                    val formerPerson = byCanonId[fCanonId]
                    val formerUnit = formerPerson?.let { unitMap[it.id] ?: unitMap[canon(it.id)] }
                    if (formerPerson != null &&
                        formerUnit != null &&
                        formerUnit != unit &&
                        formerUnit.spouse == null &&
                        formerUnit.formerSpouses.isEmpty() &&
                        isValidCanvasPair(member, formerPerson)
                    ) {
                        unit.formerSpouses.add(formerPerson)
                        unitsToRemove.add(formerUnit)
                        unitMap[formerPerson.id] = unit
                        unitMap[canon(formerPerson.id)] = unit
                    }
                }
            }
        }
        allUnits.removeAll(unitsToRemove)

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

        // Pass 1: Coupled parent units (father + mother, plus any attached former/late spouses) get first priority
        allUnits.filter { it.spouse != null || it.formerSpouses.isNotEmpty() }.forEach { unit ->
            val parentKeys = buildList {
                add(unit.primary.id)
                add(canon(unit.primary.id))
                unit.spouse?.let {
                    add(it.id)
                    add(canon(it.id))
                }
                unit.formerSpouses.forEach { fs ->
                    add(fs.id)
                    add(canon(fs.id))
                }
            }
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
            // Sort children so children of former spouses appear on the left, followed by children of current spouse
            val formerCanonIds = unit.formerSpouses.map { canon(it.id) }.toSet()
            unit.children.sortWith(
                compareBy<FamilyUnit> { ch ->
                    val bioChild = getBiologicalChild(unit, ch)
                    val isFormerChild = formerCanonIds.contains(canon(bioChild.fatherId)) || formerCanonIds.contains(canon(bioChild.motherId))
                    if (isFormerChild) 0 else 1
                }.thenBy { getBiologicalChild(unit, it).birthDate.ifEmpty { getBiologicalChild(unit, it).firstName } }
            )
        }

        // Pass 2: Single-parent units claim any remaining unclaimed children
        allUnits.filter { it.spouse == null && it.formerSpouses.isEmpty() }.forEach { unit ->
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
            val adultCardCount = 1 + (if (u.spouse != null) 1 else 0) + u.formerSpouses.size
            val ownW = adultCardCount * CARD_W + (adultCardCount - 1) * COUPLE_GAP
            if (u.children.isEmpty()) {
                u.width = ownW
                return u.width
            }
            val allLeaves = u.children.all { it.children.isEmpty() }
            if (allLeaves && u.children.size >= 4) {
                val tier1Count = (u.children.size + 1) / 2
                var tier1W = 0f
                for (i in 0 until tier1Count) {
                    tier1W += computeWidth(u.children[i])
                    if (i > 0) tier1W += SIBLING_GAP
                }
                var tier2W = 0f
                for (i in tier1Count until u.children.size) {
                    tier2W += computeWidth(u.children[i])
                    if (i > tier1Count) tier2W += SIBLING_GAP
                }
                val kidsTotal = maxOf(tier1W, tier2W)
                u.width = maxOf(ownW, kidsTotal)
            } else {
                var kidsTotal = 0f
                u.children.forEachIndexed { idx, ch ->
                    kidsTotal += computeWidth(ch)
                    if (idx > 0) kidsTotal += SIBLING_GAP
                }
                u.width = maxOf(ownW, kidsTotal)
            }
            return u.width
        }

        rootUnits.forEach { computeWidth(it) }

        // Assign Coordinates recursively with cycle guard
        val visitedPos = mutableSetOf<FamilyUnit>()
        fun assignPos(u: FamilyUnit, centerX: Float, yOffset: Float = 0f) {
            if (!visitedPos.add(u)) return
            u.x = centerX
            val yPos = MARGIN + u.generation * ROW_GAP + CARD_H / 2f + yOffset

            if (u.formerSpouses.isNotEmpty() && u.spouse != null) {
                val (primaryFormers, spouseFormers) = u.formerSpouses.partition { fs ->
                    u.primary.hasSpouseId(fs.id) || fs.hasSpouseId(u.primary.id)
                }
                val orderedCards = buildList {
                    addAll(primaryFormers)
                    add(u.primary)
                    add(u.spouse)
                    addAll(spouseFormers)
                }
                val totalRowW = orderedCards.size * CARD_W + (orderedCards.size - 1) * COUPLE_GAP
                var curCardX = centerX - totalRowW / 2f + CARD_W / 2f
                orderedCards.forEach { cardPerson ->
                    posMap[cardPerson.id] = PointF(curCardX, yPos)
                    curCardX += CARD_W + COUPLE_GAP
                }
            } else if (u.formerSpouses.isNotEmpty()) {
                val orderedCards = if (u.formerSpouses.size >= 2) {
                    val half = u.formerSpouses.size / 2
                    buildList {
                        addAll(u.formerSpouses.subList(0, half))
                        add(u.primary)
                        addAll(u.formerSpouses.subList(half, u.formerSpouses.size))
                    }
                } else {
                    buildList {
                        addAll(u.formerSpouses)
                        add(u.primary)
                    }
                }
                val totalRowW = orderedCards.size * CARD_W + (orderedCards.size - 1) * COUPLE_GAP
                var curCardX = centerX - totalRowW / 2f + CARD_W / 2f
                orderedCards.forEach { cardPerson ->
                    posMap[cardPerson.id] = PointF(curCardX, yPos)
                    curCardX += CARD_W + COUPLE_GAP
                }
            } else if (u.spouse != null) {
                posMap[u.primary.id] = PointF(centerX - CARD_W / 2f - COUPLE_GAP / 2f, yPos)
                posMap[u.spouse.id] = PointF(centerX + CARD_W / 2f + COUPLE_GAP / 2f, yPos)
            } else {
                posMap[u.primary.id] = PointF(centerX, yPos)
            }

            if (u.children.isNotEmpty()) {
                val allLeaves = u.children.all { it.children.isEmpty() }
                if (allLeaves && u.children.size >= 4) {
                    val tier1Count = (u.children.size + 1) / 2
                    val tier1 = u.children.take(tier1Count)
                    val tier2 = u.children.drop(tier1Count)

                    val tier1W = tier1.sumOf { it.width.toDouble() }.toFloat() + (tier1.size - 1) * SIBLING_GAP
                    var curLeft1 = centerX - tier1W / 2f
                    tier1.forEach { ch ->
                        val cCenter = curLeft1 + ch.width / 2f
                        assignPos(ch, cCenter, yOffset = 0f)
                        curLeft1 += ch.width + SIBLING_GAP
                    }

                    val tier2W = tier2.sumOf { it.width.toDouble() }.toFloat() + (tier2.size - 1) * SIBLING_GAP
                    var curLeft2 = centerX - tier2W / 2f
                    val tier2YOffset = ROW_GAP
                    tier2.forEach { ch ->
                        val cCenter = curLeft2 + ch.width / 2f
                        assignPos(ch, cCenter, yOffset = tier2YOffset)
                        curLeft2 += ch.width + SIBLING_GAP
                    }
                } else {
                    val kidsTotalW = u.children.sumOf { it.width.toDouble() }.toFloat() +
                            (u.children.size - 1) * SIBLING_GAP
                    var curLeft = centerX - kidsTotalW / 2f
                    u.children.forEach { ch ->
                        val cCenter = curLeft + ch.width / 2f
                        assignPos(ch, cCenter, yOffset = 0f)
                        curLeft += ch.width + SIBLING_GAP
                    }
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

    private fun isNonBiologicalLink(child: Person, parentId1: String?, parentId2: String?): Boolean {
        val fType = child.fatherRelationshipType.trim()
        val mType = child.motherRelationshipType.trim()
        val isFatherNonBio = fType.equals("Adoptive", ignoreCase = true) || fType.equals("Step", ignoreCase = true)
        val isMotherNonBio = mType.equals("Adoptive", ignoreCase = true) || mType.equals("Step", ignoreCase = true)

        if (parentId1 != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId1, child.fatherId) && isFatherNonBio) return true
        if (parentId1 != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId1, child.motherId) && isMotherNonBio) return true
        if (parentId2 != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId2, child.fatherId) && isFatherNonBio) return true
        if (parentId2 != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId2, child.motherId) && isMotherNonBio) return true

        // Fallback: if connected to either parent in this unit, check if either parent type is non-biological
        if (parentId1 != null && (com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId1, child.fatherId) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId1, child.motherId))) {
            if (isFatherNonBio || isMotherNonBio) return true
        }
        if (parentId2 != null && (com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId2, child.fatherId) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(parentId2, child.motherId))) {
            if (isFatherNonBio || isMotherNonBio) return true
        }
        return false
    }

    private fun precomputeMainTreeConnectorsAndHitAreas() {
        waypointHandles.clear()
        precomputedMaritalLines.clear()
        precomputedBuses.clear()
        precomputedFallbackLines.clear()
        hitAreas.clear()

        // 1. Precompute Marital Lines and Buses (supports both active marriage and former/late marriages in a triad)
        familyUnits.forEach { unit ->
            val pPos = posMap[unit.primary.id] ?: return@forEach
            val sPos = unit.spouse?.let { posMap[it.id] }

            if (sPos != null && mode != "PEDIGREE") {
                val isPLeft = pPos.x <= sPos.x
                val leftPos = if (isPLeft) pPos else sPos
                val rightPos = if (isPLeft) sPos else pPos
                val leftX = leftPos.x + CARD_W / 2f
                val rightX = rightPos.x - CARD_W / 2f
                val pillMidX = (leftX + rightX) / 2f
                val pillMidY = (leftPos.y + rightPos.y) / 2f
                precomputedMaritalLines.add(
                    PrecomputedMaritalLine(
                        primaryId = unit.primary.id,
                        spouseId = unit.spouse.id,
                        leftX = leftX,
                        rightX = rightX,
                        y = leftPos.y,
                        pillMidX = pillMidX,
                        pillMidY = pillMidY,
                        leftY = leftPos.y,
                        rightY = rightPos.y
                    )
                )
            }

            // Render marital lines & buses for any attached former/late spouses
            unit.formerSpouses.forEach { fs ->
                val fsPos = posMap[fs.id]
                val hubPartner = if (unit.primary.hasSpouseId(fs.id) || fs.hasSpouseId(unit.primary.id)) {
                    unit.primary
                } else {
                    unit.spouse ?: unit.primary
                }
                val hubPos = posMap[hubPartner.id]
                if (fsPos != null && hubPos != null && mode != "PEDIGREE") {
                    val isHubLeft = hubPos.x <= fsPos.x
                    val leftPos = if (isHubLeft) hubPos else fsPos
                    val rightPos = if (isHubLeft) fsPos else hubPos
                    val leftX = leftPos.x + CARD_W / 2f
                    val rightX = rightPos.x - CARD_W / 2f
                    val pillMidX = (leftX + rightX) / 2f
                    val pillMidY = (leftPos.y + rightPos.y) / 2f
                    precomputedMaritalLines.add(
                        PrecomputedMaritalLine(
                            primaryId = hubPartner.id,
                            spouseId = fs.id,
                            leftX = leftX,
                            rightX = rightX,
                            y = leftPos.y,
                            pillMidX = pillMidX,
                            pillMidY = pillMidY,
                            leftY = leftPos.y,
                            rightY = rightPos.y
                        )
                    )
                }
            }

            if (unit.children.isNotEmpty()) {
                val yParent = pPos.y
                val parentCardBottom = yParent + CARD_H / 2f
                val currentPairMidX = if (sPos != null) (pPos.x + sPos.x) / 2f else unit.x
                val dropStartY = if (unit.spouse != null) pPos.y + 12f else parentCardBottom + ADD_BTN_R

                val allLeaves = unit.children.all { it.children.isEmpty() }
                val isTiered = allLeaves && unit.children.size >= 4

                if (isTiered) {
                    val tier1Count = (unit.children.size + 1) / 2
                    val tier1 = unit.children.take(tier1Count)
                    val tier2 = unit.children.drop(tier1Count)

                    // Tier 1 Bus
                    val tier1Drops = mutableListOf<PrecomputedChildDrop>()
                    var busLeft1 = currentPairMidX
                    var busRight1 = currentPairMidX
                    val yBus1 = parentCardBottom + V_GAP * 0.5f

                    tier1.forEach { ch ->
                        val targetPerson = getBiologicalChild(unit, ch)
                        val belongsToAttachedFormerSpouse = unit.formerSpouses.any { fs ->
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.fatherId) ||
                                com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.motherId)
                        }
                        if (!belongsToAttachedFormerSpouse) {
                            val cPos = posMap[targetPerson.id]
                            if (cPos != null) {
                                val targetY = cPos.y - CARD_H / 2f
                                val isNonBio = isNonBiologicalLink(targetPerson, unit.primary.id, unit.spouse?.id)
                                tier1Drops.add(
                                    PrecomputedChildDrop(
                                        childId = targetPerson.id,
                                        x = cPos.x,
                                        yBus = yBus1,
                                        targetY = targetY,
                                        isNonBiological = isNonBio
                                    )
                                )
                                if (cPos.x < busLeft1) busLeft1 = cPos.x
                                if (cPos.x > busRight1) busRight1 = cPos.x
                            }
                        }
                    }

                    if (tier1Drops.isNotEmpty()) {
                        val minX1 = tier1Drops.minOf { it.x }
                        val maxX1 = tier1Drops.maxOf { it.x }
                        val hasBridge1 = currentPairMidX < minX1 || currentPairMidX > maxX1
                        val bridgeTargetX1 = if (currentPairMidX < minX1) minX1 else if (currentPairMidX > maxX1) maxX1 else currentPairMidX
                        val bridgeStepY1 = (dropStartY + yBus1) / 2f
                        precomputedBuses.add(
                            PrecomputedFamilyBus(
                                primaryId = unit.primary.id,
                                spouseId = unit.spouse?.id,
                                dropX = currentPairMidX,
                                dropStartY = dropStartY,
                                yBus = yBus1,
                                busLeft = minX1,
                                busRight = maxX1,
                                childDrops = tier1Drops,
                                bridgeTargetX = bridgeTargetX1,
                                bridgeStepY = bridgeStepY1,
                                hasSteppedBridge = hasBridge1
                            )
                        )
                    }

                    // Tier 2 Bus (staggered directly below Tier 1)
                    val tier2Drops = mutableListOf<PrecomputedChildDrop>()
                    val firstTier1Y = tier1Drops.firstOrNull()?.targetY ?: (yBus1 + V_GAP * 0.5f)
                    val tier1CardBottom = firstTier1Y + CARD_H
                    val yBus2 = tier1CardBottom + V_GAP * 0.5f

                    tier2.forEach { ch ->
                        val targetPerson = getBiologicalChild(unit, ch)
                        val belongsToAttachedFormerSpouse = unit.formerSpouses.any { fs ->
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.fatherId) ||
                                com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.motherId)
                        }
                        if (!belongsToAttachedFormerSpouse) {
                            val cPos = posMap[targetPerson.id]
                            if (cPos != null) {
                                val targetY = cPos.y - CARD_H / 2f
                                val isNonBio = isNonBiologicalLink(targetPerson, unit.primary.id, unit.spouse?.id)
                                tier2Drops.add(
                                    PrecomputedChildDrop(
                                        childId = targetPerson.id,
                                        x = cPos.x,
                                        yBus = yBus2,
                                        targetY = targetY,
                                        isNonBiological = isNonBio
                                    )
                                )
                            }
                        }
                    }

                    if (tier2Drops.isNotEmpty()) {
                        val minX2 = tier2Drops.minOf { it.x }
                        val maxX2 = tier2Drops.maxOf { it.x }
                        val hasBridge2 = currentPairMidX < minX2 || currentPairMidX > maxX2
                        val bridgeTargetX2 = if (currentPairMidX < minX2) minX2 else if (currentPairMidX > maxX2) maxX2 else currentPairMidX
                        val bridgeStepY2 = (yBus1 + yBus2) / 2f
                        precomputedBuses.add(
                            PrecomputedFamilyBus(
                                primaryId = unit.primary.id,
                                spouseId = unit.spouse?.id,
                                dropX = currentPairMidX,
                                dropStartY = yBus1,
                                yBus = yBus2,
                                busLeft = minX2,
                                busRight = maxX2,
                                childDrops = tier2Drops,
                                bridgeTargetX = bridgeTargetX2,
                                bridgeStepY = bridgeStepY2,
                                hasSteppedBridge = hasBridge2
                            )
                        )
                    }
                } else {
                    val childDrops = mutableListOf<PrecomputedChildDrop>()

                    val avgChildY = unit.children.mapNotNull { ch ->
                        val bio = getBiologicalChild(unit, ch)
                        posMap[bio.id]?.y
                    }.average().toFloat().takeIf { !it.isNaN() } ?: (pPos.y + 100f)
                    val isParentsAbove = pPos.y <= avgChildY

                    val defaultDropStartY = if (isParentsAbove) {
                        if (unit.spouse != null) pPos.y + 12f else parentCardBottom + ADD_BTN_R
                    } else {
                        if (unit.spouse != null) pPos.y - 12f else (pPos.y - CARD_H / 2f) - 8f
                    }
                    val defaultYBus = if (isParentsAbove) {
                        parentCardBottom + V_GAP * 0.5f
                    } else {
                        (pPos.y - CARD_H / 2f) - V_GAP * 0.5f
                    }

                    val customBus = customBusOffsets[unit.primary.id]
                    val yBus = customBus?.yBus ?: defaultYBus
                    val dropX = customBus?.dropX ?: currentPairMidX
                    val dropStartY = defaultDropStartY

                    unit.children.forEach { ch ->
                        val targetPerson = getBiologicalChild(unit, ch)
                        val belongsToAttachedFormerSpouse = unit.formerSpouses.any { fs ->
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.fatherId) ||
                                com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.motherId)
                        }
                        val isJointOrSingle = !belongsToAttachedFormerSpouse
                        if (isJointOrSingle) {
                            val cPos = posMap[targetPerson.id]
                            if (cPos != null) {
                                val targetY = if (cPos.y >= yBus) {
                                    cPos.y - CARD_H / 2f
                                } else {
                                    cPos.y + CARD_H / 2f + ADD_BTN_R
                                }
                                val isNonBio = isNonBiologicalLink(targetPerson, unit.primary.id, unit.spouse?.id)
                                childDrops.add(
                                    PrecomputedChildDrop(
                                        childId = targetPerson.id,
                                        x = cPos.x,
                                        yBus = yBus,
                                        targetY = targetY,
                                        isNonBiological = isNonBio
                                    )
                                )
                            }
                        }
                    }

                    if (childDrops.isNotEmpty()) {
                        val minX = childDrops.minOf { it.x }
                        val maxX = childDrops.maxOf { it.x }
                        val hasBridge = dropX < minX || dropX > maxX
                        val bridgeTargetX = if (dropX < minX) minX else if (dropX > maxX) maxX else dropX
                        val bridgeStepY = (dropStartY + yBus) / 2f

                        precomputedBuses.add(
                            PrecomputedFamilyBus(
                                primaryId = unit.primary.id,
                                spouseId = unit.spouse?.id,
                                dropX = dropX,
                                dropStartY = dropStartY,
                                yBus = yBus,
                                busLeft = minX,
                                busRight = maxX,
                                childDrops = childDrops,
                                bridgeTargetX = bridgeTargetX,
                                bridgeStepY = bridgeStepY,
                                hasSteppedBridge = hasBridge
                            )
                        )
                        // Interactive gold grab handle for bending connector line
                        waypointHandles.add(
                            TreeWaypointHandle(
                                id = "handle_${unit.primary.id}",
                                busId = unit.primary.id,
                                x = dropX,
                                y = if (hasBridge) bridgeStepY else yBus
                            )
                        )
                    }
                }

                // Separate child drop buses for children born to each former/late spouse
                unit.formerSpouses.forEachIndexed { fsIdx, fs ->
                    val fsPos = posMap[fs.id] ?: return@forEachIndexed
                    val hubPartner = if (unit.primary.hasSpouseId(fs.id) || fs.hasSpouseId(unit.primary.id)) {
                        unit.primary
                    } else {
                        unit.spouse ?: unit.primary
                    }
                    val hubPos = posMap[hubPartner.id] ?: return@forEachIndexed
                    val fsMidX = (hubPos.x + fsPos.x) / 2f
                    val fsBusY = parentCardBottom + V_GAP * (0.42f + (fsIdx % 2) * 0.12f)
                    val fsDropStartY = hubPos.y + 12f

                    val fsDrops = mutableListOf<PrecomputedChildDrop>()

                    unit.children.forEach { ch ->
                        val targetPerson = getBiologicalChild(unit, ch)
                        val isFormerJoint =
                            (com.example.btproject2.engine.FamilyLinkValidator.isSameId(hubPartner.id, targetPerson.fatherId) ||
                             com.example.btproject2.engine.FamilyLinkValidator.isSameId(hubPartner.id, targetPerson.motherId)) &&
                            (com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.fatherId) ||
                             com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, targetPerson.motherId))
                        if (isFormerJoint) {
                            val cPos = posMap[targetPerson.id]
                            if (cPos != null) {
                                val targetY = cPos.y - CARD_H / 2f
                                val isNonBio = isNonBiologicalLink(targetPerson, hubPartner.id, fs.id)
                                fsDrops.add(
                                    PrecomputedChildDrop(
                                        childId = targetPerson.id,
                                        x = cPos.x,
                                        yBus = fsBusY,
                                        targetY = targetY,
                                        isNonBiological = isNonBio
                                    )
                                )
                            }
                        }
                    }

                    if (fsDrops.isNotEmpty()) {
                        val fsMinX = fsDrops.minOf { it.x }
                        val fsMaxX = fsDrops.maxOf { it.x }
                        val fsHasBridge = fsMidX < fsMinX || fsMidX > fsMaxX
                        val fsBridgeTargetX = if (fsMidX < fsMinX) fsMinX else if (fsMidX > fsMaxX) fsMaxX else fsMidX
                        val fsBridgeStepY = (fsDropStartY + fsBusY) / 2f

                        precomputedBuses.add(
                            PrecomputedFamilyBus(
                                primaryId = hubPartner.id,
                                spouseId = fs.id,
                                dropX = fsMidX,
                                dropStartY = fsDropStartY,
                                yBus = fsBusY,
                                busLeft = fsMinX,
                                busRight = fsMaxX,
                                childDrops = fsDrops,
                                bridgeTargetX = fsBridgeTargetX,
                                bridgeStepY = fsBridgeStepY,
                                hasSteppedBridge = fsHasBridge
                            )
                        )
                    }
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
                val isJointOrSingle = (u.spouse == null && u.formerSpouses.isEmpty()) || (
                    u.spouse != null &&
                    (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, cFather) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, cMother)) &&
                    (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse.id, cFather) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse.id, cMother))
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
                u.formerSpouses.forEach { fs ->
                    val hubPartner = if (u.primary.hasSpouseId(fs.id) || fs.hasSpouseId(u.primary.id)) u.primary else (u.spouse ?: u.primary)
                    val isFormerJoint =
                        (com.example.btproject2.engine.FamilyLinkValidator.isSameId(hubPartner.id, cFather) ||
                         com.example.btproject2.engine.FamilyLinkValidator.isSameId(hubPartner.id, cMother)) &&
                        (com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, cFather) ||
                         com.example.btproject2.engine.FamilyLinkValidator.isSameId(fs.id, cMother))
                    if (isFormerJoint) {
                        coveredParentChild.add(Pair(hubPartner.id, child.id))
                        coveredParentChild.add(Pair(fs.id, child.id))
                    }
                }
            }
        }

        displayPersons.forEach { p ->
            val cPos = posMap[p.id] ?: return@forEach
            val uncoveredParents = listOfNotNull(p.fatherId, p.motherId)
                .filter { it.isNotBlank() && !coveredParentChild.contains(Pair(it, p.id)) }
            if (uncoveredParents.isEmpty()) return@forEach

            // Check if two uncovered parents form a married FamilyUnit so we emit a single shared line from their marriage ring
            val sharedUnit = if (uncoveredParents.size >= 2) {
                val p1 = uncoveredParents[0]
                val p2 = uncoveredParents[1]
                familyUnits.find { u ->
                    (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, p1) &&
                     com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse?.id, p2)) ||
                    (com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.primary.id, p2) &&
                     com.example.btproject2.engine.FamilyLinkValidator.isSameId(u.spouse?.id, p1))
                }
            } else null

            if (sharedUnit != null) {
                val primaryPos = posMap[sharedUnit.primary.id]
                if (primaryPos != null) {
                    val isChildBelow = cPos.y >= primaryPos.y
                    val pY = if (isChildBelow) primaryPos.y + 12f else primaryPos.y - 12f
                    val cY = if (isChildBelow) cPos.y - CARD_H / 2f else cPos.y + CARD_H / 2f + ADD_BTN_R
                    val midY = (pY + cY) / 2f
                    val isNonBio = isNonBiologicalLink(p, sharedUnit.primary.id, sharedUnit.spouse?.id)
                    precomputedFallbackLines.add(
                        PrecomputedFallbackLine(
                            parentId = sharedUnit.primary.id,
                            secondParentId = sharedUnit.spouse?.id,
                            childId = p.id,
                            pX = sharedUnit.x,
                            pY = pY,
                            cX = cPos.x,
                            cY = cY,
                            isStep = true,
                            midY = midY,
                            isNonBiological = isNonBio
                        )
                    )
                }
            } else {
                uncoveredParents.forEach { pid ->
                    val pPos = posMap[pid]
                    if (pPos != null) {
                        val isChildBelow = cPos.y >= pPos.y
                        val pY = if (isChildBelow) pPos.y + CARD_H / 2f + ADD_BTN_R else (pPos.y - CARD_H / 2f) - 8f
                        val cY = if (isChildBelow) cPos.y - CARD_H / 2f else cPos.y + CARD_H / 2f + ADD_BTN_R
                        val midY = (pY + cY) / 2f
                        val isNonBioFallback = isNonBiologicalLink(p, pid, null)
                        precomputedFallbackLines.add(
                            PrecomputedFallbackLine(
                                parentId = pid,
                                secondParentId = null,
                                childId = p.id,
                                pX = pPos.x,
                                pY = pY,
                                cX = cPos.x,
                                cY = cY,
                                isStep = true,
                                midY = midY,
                                isNonBiological = isNonBioFallback
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
            if (mode == "MAIN" && !isViewerMode) {
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
            if (isViewerMode) {
                canvas.drawText("Browse only · No editing permissions", width / 2f, height / 2f + 20f, pEmptySub)
            } else {
                canvas.drawText("Tap + to add your first member", width / 2f, height / 2f + 20f, pEmptySub)
                val fx = width - 80f; val fy = height - 80f
                canvas.drawCircle(fx, fy, 50f, pFab)
                canvas.drawText("+", fx, fy + 18f, pFabIcon)
            }
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

        val isTraceMode = mode == "TRACE_HIGHLIGHT" && (highlightIds.isNotEmpty() || mrcaPersonId != null || mrcaPersonIds.isNotEmpty())

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
                if (!isViewerMode) {
                    canvas.drawText("+", cx, cy + pAddIcon.textSize / 3f, pDashedText)
                }

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
            val minMY = minOf(m.leftY, m.rightY)
            val maxMY = maxOf(m.leftY, m.rightY)
            if (m.rightX < cullLeft || m.leftX > cullRight || maxMY < cullTop || minMY > cullBottom) continue

            val isMaritalTrace = isTraceMode &&
                    ((highlightIds.contains(m.primaryId) && highlightIds.contains(m.spouseId)) ||
                     (mrcaPersonIds.contains(m.primaryId) && mrcaPersonIds.contains(m.spouseId)))
            val mPaint = when {
                isMaritalTrace -> pGoldPathLine
                isTraceMode -> pSpouseLineDim
                else -> pSpouseLine
            }

            if (kotlin.math.abs(m.leftY - m.rightY) < 6f) {
                if (isMaritalTrace) {
                    canvas.drawLine(m.leftX, m.y, m.rightX, m.y, pGoldPathGlow)
                }
                canvas.drawLine(m.leftX, m.y, m.rightX, m.y, mPaint)
            } else {
                sharedLinePath.reset()
                sharedLinePath.moveTo(m.leftX, m.leftY)
                sharedLinePath.lineTo(m.pillMidX, m.leftY)
                sharedLinePath.lineTo(m.pillMidX, m.rightY)
                sharedLinePath.lineTo(m.rightX, m.rightY)
                if (isMaritalTrace) {
                    canvas.drawPath(sharedLinePath, pGoldPathGlow)
                }
                canvas.drawPath(sharedLinePath, mPaint)
            }

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
            val minBusX = minOf(bus.dropX, bus.busLeft, bus.bridgeTargetX)
            val maxBusX = maxOf(bus.dropX, bus.busRight, bus.bridgeTargetX)
            val minBusY = minOf(bus.dropStartY, bus.yBus, bus.bridgeStepY)
            val maxBusY = maxOf(bus.dropStartY, bus.yBus, bus.bridgeStepY, bus.childDrops.maxOfOrNull { it.targetY } ?: bus.yBus)
            if (maxBusX < cullLeft || minBusX > cullRight || maxBusY < cullTop || minBusY > cullBottom) continue

            val isParentOnTrace = isTraceMode && (
                highlightIds.contains(bus.primaryId) ||
                (bus.spouseId != null && highlightIds.contains(bus.spouseId)) ||
                bus.primaryId == mrcaPersonId ||
                bus.spouseId == mrcaPersonId ||
                mrcaPersonIds.contains(bus.primaryId) ||
                (bus.spouseId != null && mrcaPersonIds.contains(bus.spouseId))
            )

            // Draw default parent drop & smart stepped highway bridge
            val busBasePaint = if (isTraceMode) pLineDim else pLine
            if (bus.hasSteppedBridge) {
                canvas.drawLine(bus.dropX, bus.dropStartY, bus.dropX, bus.bridgeStepY, busBasePaint)
                canvas.drawLine(bus.dropX, bus.bridgeStepY, bus.bridgeTargetX, bus.bridgeStepY, busBasePaint)
                canvas.drawLine(bus.bridgeTargetX, bus.bridgeStepY, bus.bridgeTargetX, bus.yBus, busBasePaint)
            } else {
                canvas.drawLine(bus.dropX, bus.dropStartY, bus.dropX, bus.yBus, busBasePaint)
            }

            // Draw local children's bus bar (strictly from busLeft to busRight)
            canvas.drawLine(bus.busLeft, bus.yBus, bus.busRight, bus.yBus, busBasePaint)

            // Draw vertical drop into top/bottom of each child card
            for (j in 0 until bus.childDrops.size) {
                val drop = bus.childDrops[j]
                val dropMinY = minOf(drop.yBus, drop.targetY)
                val dropMaxY = maxOf(drop.yBus, drop.targetY)
                if (drop.x < cullLeft || drop.x > cullRight || dropMaxY < cullTop || dropMinY > cullBottom) continue

                val isChildOnTrace = isTraceMode && (highlightIds.contains(drop.childId) || drop.childId == mrcaPersonId || mrcaPersonIds.contains(drop.childId))
                val isSegmentTrace = isParentOnTrace && isChildOnTrace

                if (isSegmentTrace) {
                    // Double-pass glowing bloodline path
                    sharedTracePath.reset()
                    if (bus.hasSteppedBridge) {
                        sharedTracePath.moveTo(bus.dropX, bus.dropStartY)
                        sharedTracePath.lineTo(bus.dropX, bus.bridgeStepY)
                        sharedTracePath.lineTo(bus.bridgeTargetX, bus.bridgeStepY)
                        sharedTracePath.lineTo(bus.bridgeTargetX, bus.yBus)
                    } else {
                        sharedTracePath.moveTo(bus.dropX, bus.dropStartY)
                        sharedTracePath.lineTo(bus.dropX, bus.yBus)
                    }
                    sharedTracePath.lineTo(drop.x, bus.yBus)
                    sharedTracePath.lineTo(drop.x, drop.targetY)
                    canvas.drawPath(sharedTracePath, pGoldPathGlow)
                    canvas.drawPath(sharedTracePath, pGoldPathLine)
                } else {
                    val cLinePaint = when {
                        drop.isNonBiological -> if (isTraceMode) pLineAdoptiveDim else pLineAdoptive
                        isTraceMode -> pLineDim
                        else -> pLine
                    }
                    canvas.drawLine(drop.x, drop.yBus, drop.x, drop.targetY, cLinePaint)
                }
            }
        }

        if (isOrganizeMode) {
            for (handle in waypointHandles) {
                canvas.drawCircle(handle.x, handle.y, 16f, pWaypointGlow)
                canvas.drawCircle(handle.x, handle.y, 10f, pWaypointInner)
                canvas.drawCircle(handle.x, handle.y, 10f, pWaypointBorder)
                canvas.drawCircle(handle.x, handle.y, 4f, pWaypointPip)
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

            val isPOnPath = highlightIds.contains(f.childId) || f.childId == mrcaPersonId || mrcaPersonIds.contains(f.childId)
            val isParentOnPath = highlightIds.contains(f.parentId) ||
                f.parentId == mrcaPersonId ||
                mrcaPersonIds.contains(f.parentId) ||
                (f.secondParentId != null && (highlightIds.contains(f.secondParentId) || f.secondParentId == mrcaPersonId || mrcaPersonIds.contains(f.secondParentId)))
            val isLineOnTrace = isTraceMode && isPOnPath && isParentOnPath

            sharedLinePath.reset()
            sharedLinePath.moveTo(f.pX, f.pY)
            sharedLinePath.lineTo(f.pX, f.midY)
            sharedLinePath.lineTo(f.cX, f.midY)
            sharedLinePath.lineTo(f.cX, f.cY)

            if (isLineOnTrace) {
                canvas.drawPath(sharedLinePath, pGoldPathGlow)
                canvas.drawPath(sharedLinePath, pGoldPathLine)
            } else {
                val currentLinePaint = when {
                    f.isNonBiological -> if (isTraceMode) pLineAdoptiveDim else pLineAdoptive
                    isTraceMode -> pLineDim
                    else -> pLine
                }
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

            val isMrca = p.id == mrcaPersonId || mrcaPersonIds.contains(p.id)
            val isOnPath = highlightIds.contains(p.id) || isMrca

            drawPersonCard(canvas, p, pos.x, pos.y, isTraceMode, isOnPath, isMrca)

            // Add Child Quick Button docked at bottom center of card (only in Main mode when not dimmed and not exporting)
            if (mode == "MAIN" && (!isTraceMode || isOnPath) && !isExportMode && !isViewerMode) {
                val btnY = pos.y + CARD_H / 2f
                canvas.drawCircle(pos.x, btnY, ADD_BTN_R, pAddBtn)
                canvas.drawCircle(pos.x, btnY, ADD_BTN_R, pAddBtnBorder)
                canvas.drawText("+", pos.x, btnY + pAddIcon.textSize / 3f, pAddIcon)
            }
        }

        // Continuous redraw for smooth 60/120fps heart-throbbing beacon & fade
        if (!focalPersonId.isNullOrEmpty() && focalBeaconStartTime > 0L) {
            val elapsed = System.currentTimeMillis() - focalBeaconStartTime
            if (elapsed < 3600L) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun getBiologicalChild(parentUnit: FamilyUnit, childUnit: FamilyUnit): Person {
        val parentIds = buildSet {
            add(parentUnit.primary.id.trim().lowercase())
            parentUnit.spouse?.id?.trim()?.lowercase()?.let { add(it) }
            parentUnit.formerSpouses.forEach { add(it.id.trim().lowercase()) }
        }
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
        val isFocal = p.id == focalPersonId
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

        // 1. Soft Drop Shadow & Focal Gold Halo
        tempShadowRect.set(cardLeft, cardTop + 4f, cardRight, cardBottom + 4f)
        canvas.drawRoundRect(tempShadowRect, CARD_CORNER, CARD_CORNER, pCardShadow)

        if (isFocal) {
            val (beaconAlpha, throb) = getFocalBeaconMetrics()
            if (beaconAlpha > 0.01f) {
                val expand = 4f + 3.5f * ((throb + 1f) / 2f)
                val alpha = (beaconAlpha * (0.6f + 0.4f * ((throb + 1f) / 2f)) * 255).toInt().coerceIn(0, 255)
                pFocalGoldBeaconHalo.alpha = alpha
                pFocalGoldBeaconHalo.strokeWidth = 3.5f + 2f * ((throb + 1f) / 2f)
                tempHaloRect.set(cardLeft - expand, cardTop - expand, cardRight + expand, cardBottom + expand)
                canvas.drawRoundRect(tempHaloRect, CARD_CORNER + 4f, CARD_CORNER + 4f, pFocalGoldBeaconHalo)
            }
        }

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
            isFocal -> pFocalRestingBorder
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
        } else if (isFocal) {
            val (beaconAlpha, throb) = getFocalBeaconMetrics()
            if (beaconAlpha > 0.01f) {
                val avatarExpand = 4f + 3f * ((throb + 1f) / 2f)
                val alpha = (beaconAlpha * (0.6f + 0.4f * ((throb + 1f) / 2f)) * 255).toInt().coerceIn(0, 255)
                pFocalGoldBeaconHalo.alpha = alpha
                pFocalGoldBeaconHalo.strokeWidth = 3.5f + 1.5f * ((throb + 1f) / 2f)
                canvas.drawCircle(cx, avatarCy, NODE_RADIUS + avatarExpand, pFocalGoldBeaconHalo)
            }
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
            isFocal -> pFocalRestingBorder
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
            isFocal -> pFocalPillBg
            !p.isLiving && shouldDim -> pDeceasedTitleBgDim
            !p.isLiving -> pDeceasedTitleBg
            shouldDim -> pTitleBgDim
            else -> pTitleBg
        }
        val titleBorderPaint = when {
            isFocal -> pCardMrcaBorder
            !p.isLiving && shouldDim -> pDeceasedTitleBorderDim
            !p.isLiving -> pDeceasedTitleBorder
            shouldDim -> pTitleBorderDim
            else -> pTitleBorder
        }
        val titleTxtPaint = when {
            isFocal -> pMrcaBadge
            !p.isLiving && shouldDim -> pDeceasedTitleTextDim
            !p.isLiving -> pDeceasedTitleText
            shouldDim -> pTitleTextDim
            else -> pTitleText
        }

        canvas.drawRoundRect(tempBarRect, barH / 2f, barH / 2f, titleBgPaint)
        canvas.drawRoundRect(tempBarRect, barH / 2f, barH / 2f, titleBorderPaint)
        canvas.drawText(displayTitle, cx, tempBarRect.centerY() + 4f, titleTxtPaint)

        // 6. Role Badge above card (especially in TRACE_HIGHLIGHT mode)
        val mrcaBadgeLabel = if (isAffinityTraceMode) "💍 MARRIAGE BRIDGE" else "★ COMMON ANCESTOR"
        if (isTraceMode && isOnPath) {
            when {
                isMrca -> canvas.drawText(mrcaBadgeLabel, cx, cardTop - 8f, pMrcaBadge)
                p.id == tracePersonAId -> canvas.drawText("🚩 PERSON A", cx, cardTop - 8f, pPersonABadge)
                p.id == tracePersonBId -> canvas.drawText("🎯 PERSON B", cx, cardTop - 8f, pPersonBBadge)
                else -> canvas.drawText("⚡ BLOODLINE", cx, cardTop - 8f, pBloodlineBadge)
            }
        } else if (isMrca) {
            canvas.drawText(mrcaBadgeLabel, cx, cardTop - 8f, pMrcaBadge)
        } else if (isFocal) {
            val (beaconAlpha, _) = getFocalBeaconMetrics()
            if (beaconAlpha > 0.05f) {
                val prevAlpha = pMrcaBadge.alpha
                pMrcaBadge.alpha = (beaconAlpha * 255).toInt()
                canvas.drawText("\u2605 YOU / FOCAL CENTER", cx, cardTop - 8f, pMrcaBadge)
                pMrcaBadge.alpha = prevAlpha
            }
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
        val borderPaint = if (!p.isLiving) pDeceasedBorder else if (p.id == focalPersonId) pFocalRestingBorder else pBorder

        canvas.drawCircle(cx, cy + 4f, NODE_RADIUS + 2f, pNodeShadow)
        if (p.id == focalPersonId) {
            val (beaconAlpha, throb) = getFocalBeaconMetrics()
            if (beaconAlpha > 0.01f) {
                val nodeExpand = 4f + 3f * ((throb + 1f) / 2f)
                val alpha = (beaconAlpha * (0.6f + 0.4f * ((throb + 1f) / 2f)) * 255).toInt().coerceIn(0, 255)
                pFocalGoldBeaconHalo.alpha = alpha
                pFocalGoldBeaconHalo.strokeWidth = 3.5f + 1.5f * ((throb + 1f) / 2f)
                canvas.drawCircle(cx, cy, NODE_RADIUS + nodeExpand, pFocalGoldBeaconHalo)
            }
        }

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
        if (isOrganizeMode && !isViewerMode && mode != "PEDIGREE") {
            val canvasX = toCanvasX(event.x)
            val canvasY = toCanvasY(event.y)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 1. Check if hit waypoint handle (radius ~ 32f)
                    val hitHandle = waypointHandles.find { h ->
                        val dx = canvasX - h.x
                        val dy = canvasY - h.y
                        kotlin.math.sqrt(dx * dx + dy * dy) <= 32f
                    }
                    if (hitHandle != null) {
                        draggedHandle = hitHandle
                        dragStartTouchX = canvasX
                        dragStartTouchY = canvasY
                        dragStartBusY = hitHandle.y
                        dragStartDropX = hitHandle.x
                        isDraggingInOrganize = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }

                    // 2. Check if hit any person card
                    val hitCard = hitAreas.reversed().find { h ->
                        !h.tag.startsWith("add:") && !h.tag.startsWith("ancestor:") &&
                            kotlin.math.abs(canvasX - h.cx) <= CARD_W / 2f &&
                            kotlin.math.abs(canvasY - h.cy) <= CARD_H / 2f
                    }
                    if (hitCard != null) {
                        draggedCardPersonId = hitCard.tag
                        val cardPos = posMap[hitCard.tag]
                        if (cardPos != null) {
                            dragStartCardX = cardPos.x
                            dragStartCardY = cardPos.y
                            dragStartTouchX = canvasX
                            dragStartTouchY = canvasY
                            draggedSpouseId = null // 100% Free Move: individual card movement with zero restrictions

                            isDraggingInOrganize = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingInOrganize) {
                        val dx = canvasX - dragStartTouchX
                        val dy = canvasY - dragStartTouchY

                        if (draggedHandle != null) {
                            val handle = draggedHandle!!
                            val newY = dragStartBusY + dy
                            handle.y = newY
                            customBusOffsets[handle.busId] = CustomBusOffset(
                                busId = handle.busId,
                                yBus = newY,
                                dropX = handle.x
                            )
                            precomputeMainTreeConnectorsAndHitAreas()
                            invalidate()
                            return true
                        }

                        if (draggedCardPersonId != null) {
                            val newCardX = dragStartCardX + dx
                            val newCardY = dragStartCardY + dy
                            posMap[draggedCardPersonId!!]?.set(newCardX, newCardY)
                            customNodePositions[draggedCardPersonId!!] = CustomNodePosition(newCardX, newCardY)

                            precomputeMainTreeConnectorsAndHitAreas()
                            invalidate()
                            return true
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingInOrganize) {
                        if (draggedHandle != null) {
                            val handle = draggedHandle!!
                            if (kotlin.math.abs(handle.y - dragStartBusY) > 3f) {
                                undoStack.push(
                                    LayoutHistoryAction.BendLine(
                                        busId = handle.busId,
                                        prevYBus = dragStartBusY,
                                        newYBus = handle.y,
                                        prevDropX = dragStartDropX,
                                        newDropX = handle.x
                                    )
                                )
                                onUndoStackChanged?.invoke(undoStack.size)
                                onLayoutModified?.invoke()
                            }
                        } else if (draggedCardPersonId != null) {
                            val cardPos = posMap[draggedCardPersonId!!]
                            if (cardPos != null && (kotlin.math.abs(cardPos.x - dragStartCardX) > 3f || kotlin.math.abs(cardPos.y - dragStartCardY) > 3f)) {
                                undoStack.push(
                                    LayoutHistoryAction.MoveNode(
                                        personId = draggedCardPersonId!!,
                                        spouseId = null,
                                        prevX = dragStartCardX,
                                        prevY = dragStartCardY,
                                        newX = cardPos.x,
                                        newY = cardPos.y
                                    )
                                )
                                onUndoStackChanged?.invoke(undoStack.size)
                                onLayoutModified?.invoke()
                            }
                        }

                        draggedHandle = null
                        draggedCardPersonId = null
                        draggedSpouseId = null
                        isDraggingInOrganize = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
            }
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun undoLastAction() {
        if (undoStack.isEmpty()) return
        when (val action = undoStack.pop()) {
            is LayoutHistoryAction.MoveNode -> {
                posMap[action.personId]?.set(action.prevX, action.prevY)
                customNodePositions[action.personId] = CustomNodePosition(action.prevX, action.prevY)
                if (action.spouseId != null && action.prevSpouseX != null && action.prevSpouseY != null) {
                    posMap[action.spouseId]?.set(action.prevSpouseX, action.prevSpouseY)
                    customNodePositions[action.spouseId] = CustomNodePosition(action.prevSpouseX, action.prevSpouseY)
                }
            }
            is LayoutHistoryAction.BendLine -> {
                val handle = waypointHandles.find { it.busId == action.busId }
                handle?.y = action.prevYBus
                customBusOffsets[action.busId] = CustomBusOffset(
                    busId = action.busId,
                    yBus = action.prevYBus,
                    dropX = action.prevDropX
                )
            }
        }
        precomputeMainTreeConnectorsAndHitAreas()
        invalidate()
        onUndoStackChanged?.invoke(undoStack.size)
        onLayoutModified?.invoke()
    }

    fun resetCustomLayout() {
        customNodePositions.clear()
        customBusOffsets.clear()
        undoStack.clear()
        computeLayout()
        invalidate()
        onUndoStackChanged?.invoke(0)
        onLayoutModified?.invoke()
    }

    fun exportCustomLayout(treeId: String): CustomTreeLayout {
        return CustomTreeLayout(
            treeId = treeId,
            nodePositions = customNodePositions.toMap(),
            busOffsets = customBusOffsets.toMap(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun applyCustomLayout(layout: CustomTreeLayout) {
        customNodePositions.clear()
        customNodePositions.putAll(layout.nodePositions)
        customBusOffsets.clear()
        customBusOffsets.putAll(layout.busOffsets)
        computeLayout()
        invalidate()
    }


    
    fun toCanvasX(screenX: Float): Float {
        val originX = offsetX + (width - canvasW) / 2f
        val pivotX = width / 2f
        return (screenX - pivotX) / scaleFactor + pivotX - originX
    }

    fun toCanvasY(screenY: Float): Float {
        val originY = offsetY + MARGIN / 2f
        val pivotY = height / 2f
        return (screenY - pivotY) / scaleFactor + pivotY - originY
    }

    private fun handleTap(screenX: Float, screenY: Float) {
        if (allPersons.isEmpty()) {
            if (!isViewerMode) {
                val fx = width - 80f; val fy = height - 80f
                val dx = screenX - fx; val dy = screenY - fy
                if (sqrt(dx * dx + dy * dy) <= 50f) { onAddFirst() }
            }
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

    /**
     * Renders a pristine visual bitmap of the genealogical family tree suitable for high-resolution
     * image export and embedding in PDF executive reports.
     */
    fun exportToBitmap(persons: List<Person>, treeName: String = "Family Tree"): Bitmap {
        isExportMode = true
        val prevMode = mode
        val prevFocal = focalPersonId
        val prevPersons = allPersons
        val wasDark = ThemePreferences.isDarkTheme(context)

        applyDarkPalette()
        mode = "MAIN"
        focalPersonId = null
        allPersons = persons

        val diameter = (NODE_RADIUS * 2).toInt()
        persons.forEach { p ->
            if (p.photoBase64.isNotEmpty() && !avatarBitmapCache.containsKey(p.id)) {
                val raw = DocumentHelper.decodeBase64Bitmap(p.photoBase64)
                if (raw != null) {
                    val circ = DocumentHelper.getCircularBitmap(raw, diameter)
                    if (raw != circ && !raw.isRecycled) {
                        raw.recycle()
                    }
                    avatarBitmapCache[p.id] = circ
                }
            }
        }

        updateDisplayPersons()

        var minX = Float.MAX_VALUE; var maxX = 0f
        var minY = Float.MAX_VALUE; var maxY = 0f
        if (posMap.isEmpty()) {
            minX = 0f; maxX = 1200f
            minY = 0f; maxY = 800f
        } else {
            posMap.values.forEach {
                minX = minOf(minX, it.x - CARD_W / 2f)
                maxX = maxOf(maxX, it.x + CARD_W / 2f)
                minY = minOf(minY, it.y - CARD_H / 2f)
                maxY = maxOf(maxY, it.y + CARD_H / 2f)
            }
        }
        val treeW = (maxX - minX).coerceAtLeast(800f)
        val treeH = (maxY - minY).coerceAtLeast(600f)

        val padding = 80f
        val headerH = 130f

        val bmpW = (treeW + padding * 2).toInt().coerceAtLeast(1400)
        val bmpH = (treeH + headerH + padding * 2).toInt().coerceAtLeast(900)

        val maxDim = 4096f
        val scale = if (bmpW > maxDim || bmpH > maxDim) {
            minOf(maxDim / bmpW, maxDim / bmpH)
        } else {
            1.0f
        }

        val finalW = (bmpW * scale).toInt()
        val finalH = (bmpH * scale).toInt()

        val bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (scale != 1.0f) {
            canvas.scale(scale, scale)
        }

        // Rich dark background matching KinTrace emerald theme
        canvas.drawColor(Color.parseColor("#06120D"))

        // Header Card
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0C2017")
            style = Paint.Style.FILL
        }
        val headerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D9E75")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val headerRect = RectF(padding, padding / 2, bmpW - padding, headerH + padding / 2)
        canvas.drawRoundRect(headerRect, 16f, 16f, headerPaint)
        canvas.drawRoundRect(headerRect, 16f, 16f, headerBorderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D9E75")
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 14f
        }

        val dateStr = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val livingCount = persons.count { it.isLiving }
        val deceasedCount = persons.size - livingCount

        canvas.drawText("🌳 KinTrace  ·  $treeName", padding + 24f, padding / 2 + 42f, titlePaint)
        canvas.drawText("AUTHENTIC GENEALOGICAL CHART", padding + 24f, padding / 2 + 74f, subPaint)
        canvas.drawText("Total Kinfolk: ${persons.size}   |   Living: $livingCount   |   Deceased: $deceasedCount   |   Exported: $dateStr", padding + 24f, padding / 2 + 104f, metaPaint)

        // Draw Tree
        canvas.save()
        val originX = padding - minX
        val originY = headerH + padding - minY
        canvas.translate(originX, originY)

        drawMainTree(
            canvas = canvas,
            isTraceMode = false,
            cullLeft = -100000f,
            cullRight = 100000f,
            cullTop = -100000f,
            cullBottom = 100000f
        )
        canvas.restore()

        // Watermark / footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 13f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Generated by KinTrace  ·  Certified Family Records System", bmpW - padding, bmpH - padding / 2, footerPaint)

        // Restore state if view was used interactively
        isExportMode = false
        mode = prevMode
        focalPersonId = prevFocal
        allPersons = prevPersons
        if (!wasDark) {
            updateThemeColors()
        }
        if (allPersons.isNotEmpty()) {
            updateDisplayPersons()
        }

        return bmp
    }

    fun getNodeScreenBounds(personId: String): RectF? {
        val h = hitAreas.find { it.tag == personId } ?: return null
        val originX = offsetX + (width - canvasW) / 2f
        val originY = offsetY + MARGIN / 2f
        val pivotX = width / 2f
        val pivotY = height / 2f

        val halfW = if (h.w > 0f) h.w / 2f else h.r
        val halfH = if (h.h > 0f) h.h / 2f else h.r

        val screenLeft = (h.cx - halfW + originX - pivotX) * scaleFactor + pivotX
        val screenTop = (h.cy - halfH + originY - pivotY) * scaleFactor + pivotY
        val screenRight = (h.cx + halfW + originX - pivotX) * scaleFactor + pivotX
        val screenBottom = (h.cy + halfH + originY - pivotY) * scaleFactor + pivotY

        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return RectF(
            screenLeft + loc[0],
            screenTop + loc[1],
            screenRight + loc[0],
            screenBottom + loc[1]
        )
    }

    fun getFirstVisiblePersonId(): String? {
        return hitAreas.firstOrNull { !it.tag.startsWith("add:") && !it.tag.startsWith("ancestor:") }?.tag
    }
}