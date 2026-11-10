package com.example.btproject2.ui.activities

import com.example.btproject2.utils.KinTraceAlert

import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
import com.example.btproject2.models.Person
import com.example.btproject2.ui.adapters.MemberAdapter

import android.view.View
import android.widget.ProgressBar
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.sync.RecordsSyncCoordinator
import com.example.btproject2.ui.dialogs.ActionRestrictedDialog

class FamilyRecordsActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private var treeId = ""
    private var allMembers = listOf<Person>()
    private val recentlyDeletedIds = mutableSetOf<String>()
    private var isOwner: Boolean = true
    private var isViewer: Boolean = false
    private var currentTreeName: String = "Family Tree"
    private var treePrivacy: PrivacySettings = PrivacySettings()

    private lateinit var adapter: MemberAdapter
    private lateinit var tvMemberCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var rvMembers: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmptyRecords: View
    private lateinit var tvEmptySubtitle: TextView
    private lateinit var btnEmptyAddMember: TextView
    private var chipFilterAll: TextView? = null
    private var chipFilterLiving: TextView? = null
    private var chipFilterDeceased: TextView? = null
    private var chipFilterMissingParents: TextView? = null
    private var chipSortAZ: TextView? = null
    private var chipSortOldest: TextView? = null
    private var chipSortYoungest: TextView? = null
    private var activeFilterMode: String = "ALL"
    private var activeSortMode: String = "AZ"
    private var isFirstLaunch = true

    internal val syncCoordinator by lazy {
        RecordsSyncCoordinator(
            treeId = treeId,
            listenerKey = "FamilyRecordsActivity@${System.identityHashCode(this)}",
            allMembersList = allMembers,
            onListUpdated = { updatedList, _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    allMembers = updatedList
                    applyFilter(etSearch.text?.toString().orEmpty())
                }
            },
            onMemberDeleted = { deletedId ->
                recentlyDeletedIds.add(deletedId)
            }
        )
    }

    private val networkListener: (com.example.btproject2.utils.NetworkStateMonitor.NetworkState) -> Unit = { state ->
        com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.updateNetworkState(this, state) {
            loadMembers()
        }
    }

    override fun onStart() {
        super.onStart()
        syncCoordinator.register()
        com.example.btproject2.utils.NetworkStateMonitor.startMonitoring(this)
        com.example.btproject2.utils.NetworkStateMonitor.addListener(networkListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        syncCoordinator.unregister()
        com.example.btproject2.utils.NetworkStateMonitor.removeListener(networkListener)
    }

    private val memberDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val deletedId = result.data?.getStringExtra("deletedPersonId")
            if (!deletedId.isNullOrEmpty()) {
                recentlyDeletedIds.add(deletedId)
                allMembers = allMembers.filter { it.id != deletedId }
                syncCoordinator.allMembersList = allMembers
                applyFilter(etSearch.text?.toString().orEmpty())
            } else {
                loadMembers(showLoading = false)
            }
        }
    }

    private val editMemberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadMembers(showLoading = false)
        }
    }

    private val addMemberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadMembers(showLoading = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_records)

        val passedTreeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)
        if (!passedTreeId.isNullOrEmpty()) {
            treeId = passedTreeId
        }

        val btnBack = findViewById<TextView>(R.id.btnBack)
        etSearch = findViewById(R.id.etSearch)
        tvMemberCount = findViewById(R.id.tvMemberCount)
        rvMembers = findViewById(R.id.rvMembers)
        progressBar = findViewById(R.id.progressBar)
        layoutEmptyRecords = findViewById(R.id.layoutEmptyRecords)
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle)
        btnEmptyAddMember = findViewById(R.id.btnEmptyAddMember)
        chipFilterAll = findViewById(R.id.chipFilterAll)
        chipFilterLiving = findViewById(R.id.chipFilterLiving)
        chipFilterDeceased = findViewById(R.id.chipFilterDeceased)
        chipFilterMissingParents = findViewById(R.id.chipFilterMissingParents)
        chipSortAZ = findViewById(R.id.chipSortAZ)
        chipSortOldest = findViewById(R.id.chipSortOldest)
        chipSortYoungest = findViewById(R.id.chipSortYoungest)

        val initialFilter = intent.getStringExtra("INITIAL_FILTER")?.uppercase().orEmpty()
        if (initialFilter in listOf("ALL", "LIVING", "DECEASED", "MISSING_PARENTS")) {
            activeFilterMode = initialFilter
        }
        val initialSort = intent.getStringExtra("INITIAL_SORT")?.uppercase().orEmpty()
        if (initialSort in listOf("AZ", "OLDEST", "YOUNGEST")) {
            activeSortMode = initialSort
        }
        setupFilterChips()

        // FIX: back button now calls finish() to return to the previous screen
        btnBack.setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnQuickAssistRecords)?.setOnClickListener {
            com.example.btproject2.ui.dialogs.KinTraceQuickAssistBottomSheet.show(
                this,
                com.example.btproject2.education.EducationalContentRepository.QuickAssistContext.RECORDS
            )
        }


        btnEmptyAddMember.setOnClickListener {
            if (isViewer) {
                ActionRestrictedDialog.newInstance(
                    treeId = treeId,
                    treeName = currentTreeName,
                    actionAttempted = "Add Family Member"
                ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                return@setOnClickListener
            }
            val intent = Intent(this, AddMemberActivity::class.java).apply {
                putExtra("TREE_ID", treeId)
                putExtra("treeId", treeId)
            }
            addMemberLauncher.launch(intent)
        }

        adapter = MemberAdapter(
            members = emptyList(),
            isViewer = isViewer,
            treePrivacy = treePrivacy,
            onItemClick = { person ->
                val intent = Intent(this, MemberDetailActivity::class.java).apply {
                    putExtra("personId", person.id)
                    putExtra("TREE_ID", treeId)
                    putExtra("treeId", treeId)
                    putExtra("isViewer", isViewer)
                }
                memberDetailLauncher.launch(intent)
            },
            onEditClick = { person ->
                if (isViewer) {
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Edit Member Record"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                } else {
                    val intent = Intent(this, EditMemberActivity::class.java).apply {
                        putExtra("personId", person.id)
                        putExtra("TREE_ID", treeId)
                        putExtra("treeId", treeId)
                        putExtra("isViewer", isViewer)
                    }
                    editMemberLauncher.launch(intent)
                }
            },
            onDeleteClick = { person ->
                if (isViewer) {
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Delete Member Record"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                } else {
                    confirmDeletePerson(person)
                }
            },
            onAddChildClick = { person ->
                if (isViewer) {
                    ActionRestrictedDialog.newInstance(
                        treeId = treeId,
                        treeName = currentTreeName,
                        actionAttempted = "Add Child Connection"
                    ).show(supportFragmentManager, ActionRestrictedDialog.TAG)
                } else if (!person.spouseId.isNullOrBlank()) {
                    com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.showIdentifyOtherParentDialog(
                        context = this,
                        parent = person,
                        allMembers = allMembers,
                        onResult = { result ->
                            com.example.btproject2.utils.AddChildBiologicalParentDialogHelper.startAddMemberActivity(
                                context = this,
                                result = result,
                                treeId = treeId,
                                launcher = addMemberLauncher
                            )
                        }
                    )
                } else {
                    val intent = Intent(this, AddMemberActivity::class.java).apply {
                        putExtra("presetParentId", person.id)
                        putExtra("parentId", person.id)
                        putExtra("TREE_ID", treeId)
                        putExtra("treeId", treeId)
                    }
                    addMemberLauncher.launch(intent)
                }
            }
        )
        rvMembers.layoutManager = LinearLayoutManager(this)
        rvMembers.setHasFixedSize(true)
        rvMembers.setItemViewCacheSize(25)
        rvMembers.adapter = adapter

        loadTreeRoleAndPrivacy()

        // 0ms instant render from memory cache if available
        val cached = FirestoreHelper.getCachedPersons(treeId)
        if (!cached.isNullOrEmpty()) {
            val active = cached.filter { it.id !in recentlyDeletedIds }
            allMembers = active.sortedBy { "${it.firstName} ${it.lastName}" }
            applyFilter("")
            progressBar.visibility = View.GONE
        } else {
            progressBar.visibility = View.VISIBLE
        }

        loadMembers(showLoading = allMembers.isEmpty())

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadTreeRoleAndPrivacy()
        com.example.btproject2.utils.KinTraceSpotlightManager.showTutorialIfNotSeen(
            this,
            com.example.btproject2.utils.KinTraceSpotlightManager.TutorialFeatureKey.FAMILY_RECORDS,
            treeId = treeId
        )
        if (isFirstLaunch) {
            isFirstLaunch = false
            return
        }
        // Background refresh without blanking UI
        loadMembers(showLoading = false)
    }

    private fun loadTreeRoleAndPrivacy() {
        if (treeId.isBlank()) return
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                currentTreeName = tree?.name ?: "Family Tree"
                val ownerMatch = tree == null || tree.isOwnedBy(currentUserId)
                if (ownerMatch) {
                    isOwner = true
                    isViewer = false
                    applyRoleGating()
                } else {
                    firestoreHelper.getUserMembership(currentUserId, treeId,
                        onSuccess = { member ->
                            isOwner = false
                            isViewer = !member?.role.equals("Editor", ignoreCase = true)
                            applyRoleGating()
                        },
                        onFailure = {
                            isOwner = false
                            isViewer = true
                            applyRoleGating()
                        }
                    )
                }
            },
            onFailure = {
                applyRoleGating()
            }
        )

        firestoreHelper.getPrivacySettings(treeId,
            onSuccess = { settings ->
                treePrivacy = settings
                applyRoleGating()
            },
            onFailure = {}
        )
    }

    private fun applyRoleGating() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            btnEmptyAddMember.visibility = if (isViewer) View.GONE else View.VISIBLE
            adapter.isViewer = isViewer
            adapter.treePrivacy = treePrivacy
            applyFilter(etSearch.text?.toString().orEmpty())
        }
    }

    private fun setupFilterChips() {
        val filterChips = listOf(
            "ALL" to chipFilterAll,
            "LIVING" to chipFilterLiving,
            "DECEASED" to chipFilterDeceased,
            "MISSING_PARENTS" to chipFilterMissingParents
        )
        val sortChips = listOf(
            "AZ" to chipSortAZ,
            "OLDEST" to chipSortOldest,
            "YOUNGEST" to chipSortYoungest
        )
        fun updateChipVisuals() {
            val activeTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.btn_gold_text)
            val inactiveTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
            filterChips.forEach { (mode, view) ->
                if (view != null) {
                    if (mode == activeFilterMode) {
                        view.setBackgroundResource(R.drawable.badge_pill_gold)
                        view.setTextColor(activeTextColor)
                    } else {
                        view.setBackgroundResource(R.drawable.badge_pill_dark)
                        view.setTextColor(inactiveTextColor)
                    }
                }
            }
            sortChips.forEach { (sortMode, view) ->
                if (view != null) {
                    if (sortMode == activeSortMode) {
                        view.setBackgroundResource(R.drawable.badge_pill_gold)
                        view.setTextColor(activeTextColor)
                    } else {
                        view.setBackgroundResource(R.drawable.badge_pill_dark)
                        view.setTextColor(inactiveTextColor)
                    }
                }
            }
        }
        updateChipVisuals()
        filterChips.forEach { (mode, view) ->
            view?.setOnClickListener {
                activeFilterMode = mode
                updateChipVisuals()
                applyFilter(etSearch.text?.toString().orEmpty())
            }
        }
        sortChips.forEach { (sortMode, view) ->
            view?.setOnClickListener {
                activeSortMode = sortMode
                updateChipVisuals()
                applyFilter(etSearch.text?.toString().orEmpty())
            }
        }
    }

    private fun updateFilterChipCounts(visibleMembers: List<Person>) {
        val total = visibleMembers.size
        val livingCount = visibleMembers.count { it.isLiving }
        val deceasedCount = visibleMembers.count { !it.isLiving }
        val validIds = visibleMembers.mapNotNull { it.id.trim().takeIf(String::isNotEmpty)?.lowercase() }.toSet()
        val missingParentsCount = visibleMembers.count { isMissingParents(it, validIds) }

        chipFilterAll?.text = "All ($total)"
        chipFilterLiving?.text = "Living ($livingCount)"
        chipFilterDeceased?.text = "Deceased ($deceasedCount)"
        chipFilterMissingParents?.text = "Missing Parents ($missingParentsCount)"
    }

    private fun applyFilter(rawQuery: String) {
        val query = rawQuery.lowercase().trim()
        val visibleMembers = allMembers.filter { it.id !in recentlyDeletedIds }
        updateFilterChipCounts(visibleMembers)
        val filtered = filterAndSortMembers(visibleMembers, activeFilterMode, query, activeSortMode)

        if (query.isEmpty() && activeFilterMode == "ALL") {
            tvMemberCount.text = "${visibleMembers.size} members"
        } else {
            tvMemberCount.text = "${filtered.size} of ${visibleMembers.size} members"
        }

        adapter.updateList(filtered, all = allMembers, viewerMode = isViewer, privacy = treePrivacy)

        if (filtered.isEmpty()) {
            rvMembers.visibility = View.GONE
            layoutEmptyRecords.visibility = View.VISIBLE
            if (query.isNotEmpty()) {
                tvEmptySubtitle.text = "No family members matching \"$rawQuery\""
            } else if (activeFilterMode == "MISSING_PARENTS") {
                tvEmptySubtitle.text = "Great job! Every member in this tree already has at least one parent linked."
            } else if (activeFilterMode != "ALL") {
                tvEmptySubtitle.text = "No members match the selected filter."
            } else {
                tvEmptySubtitle.text = "Start by adding the first member of your family tree."
            }
        } else {
            rvMembers.visibility = View.VISIBLE
            layoutEmptyRecords.visibility = View.GONE
        }
    }

    companion object {
        /**
         * Authoritative check for whether [person] is missing both biological/legal parent links
         * (either both `fatherId` and `motherId` are blank, or any recorded parent ID points to a
         * record that no longer exists in [validMemberIdsLower] when non-empty).
         */
        fun isMissingParents(person: Person, validMemberIdsLower: Set<String>? = null): Boolean {
            val fId = person.fatherId?.trim()?.lowercase().orEmpty()
            val mId = person.motherId?.trim()?.lowercase().orEmpty()
            val hasValidFather = fId.isNotEmpty() && (validMemberIdsLower.isNullOrEmpty() || validMemberIdsLower.contains(fId))
            val hasValidMother = mId.isNotEmpty() && (validMemberIdsLower.isNullOrEmpty() || validMemberIdsLower.contains(mId))
            return !hasValidFather && !hasValidMother
        }

        fun countMissingParents(members: List<Person>): Int {
            if (members.isEmpty()) return 0
            val validIds = members.mapNotNull { it.id.trim().takeIf(String::isNotEmpty)?.lowercase() }.toSet()
            return members.count { isMissingParents(it, validIds) }
        }

        fun filterMembers(members: List<Person>, filterMode: String, rawQuery: String): List<Person> {
            val validIds = members.mapNotNull { it.id.trim().takeIf(String::isNotEmpty)?.lowercase() }.toSet()
            val modeFiltered = when (filterMode.uppercase()) {
                "LIVING" -> members.filter { it.isLiving }
                "DECEASED" -> members.filter { !it.isLiving }
                "MISSING_PARENTS" -> members.filter { isMissingParents(it, validIds) }
                else -> members
            }
            val query = rawQuery.lowercase().trim()
            if (query.isEmpty()) return modeFiltered
            return modeFiltered.filter {
                val formalName = it.formalFullName.lowercase()
                val standardName = it.standardDisplayName.lowercase()
                val midName = it.middleName.lowercase()
                val midInit = it.middleInitial.lowercase()
                val suffixStr = it.suffix.lowercase()
                val name = "${it.firstName} ${it.lastName}".lowercase()
                val reverseName = "${it.lastName} ${it.firstName}".lowercase()
                val statusStr = if (it.isLiving) "living" else "deceased"
                val birthDate = it.birthDate.lowercase()
                val birthPlace = it.birthPlace.lowercase()
                formalName.contains(query) ||
                    standardName.contains(query) ||
                    midName.contains(query) ||
                    midInit.contains(query) ||
                    suffixStr.contains(query) ||
                    name.contains(query) ||
                    reverseName.contains(query) ||
                    statusStr.contains(query) ||
                    birthDate.contains(query) ||
                    birthPlace.contains(query)
            }
        }

        fun sortMembers(members: List<Person>, sortMode: String): List<Person> {
            val nameSelector = { it: Person ->
                if (it.standardDisplayName.isNotBlank()) it.standardDisplayName.lowercase() else "${it.firstName} ${it.lastName}".lowercase()
            }
            return when (sortMode.uppercase()) {
                "OLDEST" -> {
                    val (withYear, withoutYear) = members.partition {
                        com.example.btproject2.engine.FamilyLinkValidator.extractYear(it.birthDate) != null
                    }
                    withYear.sortedWith(
                        compareBy<Person> {
                            com.example.btproject2.engine.FamilyLinkValidator.extractYear(it.birthDate) ?: Int.MAX_VALUE
                        }.thenBy(nameSelector)
                    ) + withoutYear.sortedBy(nameSelector)
                }
                "YOUNGEST" -> {
                    val (withYear, withoutYear) = members.partition {
                        com.example.btproject2.engine.FamilyLinkValidator.extractYear(it.birthDate) != null
                    }
                    withYear.sortedWith(
                        compareByDescending<Person> {
                            com.example.btproject2.engine.FamilyLinkValidator.extractYear(it.birthDate) ?: Int.MIN_VALUE
                        }.thenBy(nameSelector)
                    ) + withoutYear.sortedBy(nameSelector)
                }
                else -> members.sortedBy(nameSelector)
            }
        }

        fun filterAndSortMembers(
            members: List<Person>,
            filterMode: String,
            rawQuery: String,
            sortMode: String = "AZ"
        ): List<Person> {
            return sortMembers(filterMembers(members, filterMode, rawQuery), sortMode)
        }
    }

    private fun loadMembers(showLoading: Boolean = allMembers.isEmpty()) {
        val cached = FirestoreHelper.getCachedPersons(treeId)?.filter { it.id !in recentlyDeletedIds }
        if (!cached.isNullOrEmpty() && allMembers.isEmpty()) {
            allMembers = cached.sortedBy { "${it.firstName} ${it.lastName}" }
            syncCoordinator.allMembersList = allMembers
            applyFilter(etSearch.text?.toString().orEmpty())
        }

        if (showLoading && allMembers.isEmpty()) {
            progressBar.visibility = View.VISIBLE
        }
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                progressBar.visibility = View.GONE
                val active = persons.filter { it.id !in recentlyDeletedIds }
                allMembers = active.sortedBy { "${it.firstName} ${it.lastName}" }
                syncCoordinator.allMembersList = allMembers
                applyFilter(etSearch.text?.toString().orEmpty())
            },
            onFailure = {
                progressBar.visibility = View.GONE
                val fallbackCache = FirestoreHelper.getCachedPersons(treeId)?.filter { it.id !in recentlyDeletedIds }
                if (!fallbackCache.isNullOrEmpty()) {
                    allMembers = fallbackCache.sortedBy { "${it.firstName} ${it.lastName}" }
                    syncCoordinator.allMembersList = allMembers
                    applyFilter(etSearch.text?.toString().orEmpty())
                } else if (allMembers.isEmpty()) {
                    com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
                        .setTitle("Offline Archive")
                        .setMessage("Unable to reach cloud servers. Connect to the internet to fetch latest records.")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                }
            }
        )
    }

    private fun confirmDeletePerson(person: Person) {
        val fullName = "${person.firstName} ${person.lastName}".trim().ifEmpty { "this member" }
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle("Delete Member")
            .setMessage("Are you sure you want to delete $fullName from the family tree? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val deletedId = person.id

                // 1. Optimistic instant deletion: Remove from local list immediately (0ms)
                recentlyDeletedIds.add(deletedId)
                allMembers = allMembers.filter { it.id != deletedId }
                syncCoordinator.allMembersList = allMembers
                applyFilter(etSearch.text?.toString().orEmpty())

                // 2. Dispatch deletion to Firestore in background
                firestoreHelper.deletePerson(
                    deletedId,
                    onSuccess = {
                        KinTraceAlert.show(this, "$fullName deleted")
                    },
                    onFailure = { err ->
                        KinTraceAlert.show(this, "Failed to delete: ${err.message}")
                        // Re-enable and reload on failure
                        recentlyDeletedIds.remove(deletedId)
                        loadMembers()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
