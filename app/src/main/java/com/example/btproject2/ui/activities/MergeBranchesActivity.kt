package com.example.btproject2.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.engine.MergePreviewEngine
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.utils.DuplicateDetector
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.setDarkAdapter

class MergeBranchesActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private val duplicateDetector = DuplicateDetector()
    private val mergeEngine = MergePreviewEngine()

    private var treeId = ""
    private var allPersons: List<Person> = emptyList()
    private var isViewerOnly = false

    private lateinit var tvSubtitle: TextView
    private lateinit var tvMergeRoleBadge: TextView
    private lateinit var layoutViewerBanner: View
    private lateinit var tvMatchCount: TextView
    private lateinit var progressBarMerge: ProgressBar
    private lateinit var layoutEmptyState: View
    private lateinit var matchContainer: LinearLayout
    private lateinit var btnOpenConnectBranches: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merge_branches)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvMergeRoleBadge = findViewById(R.id.tvMergeRoleBadge)
        layoutViewerBanner = findViewById(R.id.layoutViewerBanner)
        tvMatchCount = findViewById(R.id.tvMatchCount)
        progressBarMerge = findViewById(R.id.progressBarMerge)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        matchContainer = findViewById(R.id.matchContainer)
        btnOpenConnectBranches = findViewById(R.id.btnOpenConnectBranches)

        btnOpenConnectBranches.setOnClickListener {
            if (isViewerOnly) {
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createPermissionRestrictedNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    actionAttempted = "Connect Disconnected Branches",
                    currentRole = "Viewer"
                )
                firestoreHelper.addNotification(notif)
                Toast.makeText(this, "Permission restricted: Viewers have read-only access.", Toast.LENGTH_SHORT).show()
            } else {
                showConnectBranchesDialog()
            }
        }

        checkRoleAndLoadData()
    }

    private fun checkRoleAndLoadData() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (isOwner) {
                    tvMergeRoleBadge.text = "★ Owner"
                    isViewerOnly = false
                    layoutViewerBanner.visibility = View.GONE
                } else {
                    firestoreHelper.getUserMembership(currentUserId, treeId,
                        onSuccess = { member ->
                            val role = member?.role ?: "Viewer"
                            if (role.equals("editor", ignoreCase = true)) {
                                tvMergeRoleBadge.text = "✏ Editor"
                                isViewerOnly = false
                                layoutViewerBanner.visibility = View.GONE
                            } else {
                                tvMergeRoleBadge.text = "👁 Viewer"
                                isViewerOnly = true
                                layoutViewerBanner.visibility = View.VISIBLE
                            }
                        },
                        onFailure = {
                            tvMergeRoleBadge.text = "👁 Viewer"
                            isViewerOnly = true
                            layoutViewerBanner.visibility = View.VISIBLE
                        }
                    )
                }
                loadTreeMembers()
            },
            onFailure = {
                loadTreeMembers()
            }
        )
    }

    private fun loadTreeMembers() {
        progressBarMerge.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE
        matchContainer.removeAllViews()
        tvMatchCount.text = "Scanning..."

        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                progressBarMerge.visibility = View.GONE
                allPersons = persons
                val matches = findAllDuplicates(persons)

                if (matches.isEmpty()) {
                    tvSubtitle.text = "All branch nodes appear distinct"
                    tvMatchCount.text = "0 matches found"
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    tvSubtitle.text = "${matches.size} candidate duplicate(s) across branches"
                    tvMatchCount.text = "${matches.size} candidate(s)"
                    layoutEmptyState.visibility = View.GONE

                    val inflater = LayoutInflater.from(this)
                    for (match in matches) {
                        addMatchCard(inflater, match.first, match.second, match.third)
                    }
                }
            },
            onFailure = { error ->
                progressBarMerge.visibility = View.GONE
                tvSubtitle.text = "Failed to load members: ${error.message}"
                tvMatchCount.text = "Error"
            }
        )
    }

    /**
     * Scans all pairs for duplicates with similarity score.
     */
    private fun findAllDuplicates(persons: List<Person>): List<Triple<Person, Person, Double>> {
        val matches = mutableListOf<Triple<Person, Person, Double>>()

        for (i in persons.indices) {
            for (j in i + 1 until persons.size) {
                val a = persons[i]
                val b = persons[j]

                val nameA = "${a.firstName} ${a.lastName}".trim().lowercase()
                val nameB = "${b.firstName} ${b.lastName}".trim().lowercase()

                // Calculate similarity
                val duplicates = duplicateDetector.findPossibleDuplicates(a, listOf(b), 0.70)
                if (duplicates.isNotEmpty() || (a.birthDate.isNotEmpty() && a.birthDate == b.birthDate && nameA == nameB)) {
                    val sim = if (nameA == nameB) 0.95 else 0.78
                    matches.add(Triple(a, b, sim))
                }
            }
        }
        return matches
    }

    private fun addMatchCard(
        inflater: LayoutInflater,
        personA: Person,
        personB: Person,
        similarity: Double
    ) {
        val card = inflater.inflate(R.layout.item_merge_match, matchContainer, false)

        val tvScore = card.findViewById<TextView>(R.id.tvSimilarityScore)
        val tvNameA = card.findViewById<TextView>(R.id.tvMatchNameA)
        val tvDetailsA = card.findViewById<TextView>(R.id.tvMatchDetailsA)
        val tvNameB = card.findViewById<TextView>(R.id.tvMatchNameB)
        val tvDetailsB = card.findViewById<TextView>(R.id.tvMatchDetailsB)

        val btnReview = card.findViewById<Button>(R.id.btnCardReview)
        val btnMerge = card.findViewById<Button>(R.id.btnCardMerge)
        val btnIgnore = card.findViewById<TextView>(R.id.btnCardIgnore)

        val pct = (similarity * 100).toInt()
        tvScore.text = "$pct% Match"

        tvNameA.text = "${personA.firstName} ${personA.lastName}"
        val bDateA = if (personA.birthDate.isNotEmpty()) "b. ${personA.birthDate}" else "b. Unrecorded"
        tvDetailsA.text = "${personA.gender} • $bDateA"

        tvNameB.text = "${personB.firstName} ${personB.lastName}"
        val bDateB = if (personB.birthDate.isNotEmpty()) "b. ${personB.birthDate}" else "b. Unrecorded"
        tvDetailsB.text = "${personB.gender} • $bDateB"

        if (isViewerOnly) {
            btnMerge.alpha = 0.5f
            btnMerge.setOnClickListener {
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createPermissionRestrictedNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    actionAttempted = "Merge Family Branch Records",
                    currentRole = "Viewer"
                )
                firestoreHelper.addNotification(notif)
                Toast.makeText(this, "Permission restricted: Viewers cannot merge branch records.", Toast.LENGTH_SHORT).show()
            }
        } else {
            btnMerge.setOnClickListener {
                showMergePreviewDialog(personA, personB)
            }
        }

        btnReview.setOnClickListener {
            showMergePreviewDialog(personA, personB)
        }

        btnIgnore.setOnClickListener {
            matchContainer.removeView(card)
            if (matchContainer.childCount == 0) {
                layoutEmptyState.visibility = View.VISIBLE
            }
        }

        matchContainer.addView(card)
    }

    // =========================================================================
    // MERGE PREVIEW & CONFLICT RESOLUTION MODAL (FIGURE 13, FR-08)
    // =========================================================================
    private fun showMergePreviewDialog(personA: Person, personB: Person) {
        val preview = mergeEngine.preview(personA, personB, allPersons)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_merge_preview, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 1. Headers & Visual Chips
        val initA = "${personA.firstName.firstOrNull() ?: 'A'}${personA.lastName.firstOrNull() ?: ""}".uppercase()
        val initB = "${personB.firstName.firstOrNull() ?: 'B'}${personB.lastName.firstOrNull() ?: ""}".uppercase()

        dialogView.findViewById<TextView>(R.id.tvPreviewAvatarA).text = initA
        dialogView.findViewById<TextView>(R.id.tvPreviewNameA).text = "${personA.firstName} ${personA.lastName}"

        dialogView.findViewById<TextView>(R.id.tvPreviewAvatarB).text = initB
        dialogView.findViewById<TextView>(R.id.tvPreviewNameB).text = "${personB.firstName} ${personB.lastName}"

        // 2. Matching summary
        val tvMatching = dialogView.findViewById<TextView>(R.id.tvMatchingFieldsSummary)
        if (preview.matchingFields.isNotEmpty()) {
            tvMatching.text = "✓ Matching fields: " + preview.matchingFields.joinToString(", ")
            tvMatching.visibility = View.VISIBLE
        } else {
            tvMatching.visibility = View.GONE
        }

        // 3. Conflict resolution container
        val conflictContainer = dialogView.findViewById<LinearLayout>(R.id.layoutConflictContainer)
        conflictContainer.removeAllViews()

        val selectedOverrides = mutableMapOf<String, String>()

        if (preview.conflicts.isEmpty()) {
            val tvNoConflicts = TextView(this).apply {
                text = "No divergent attributes. Records align seamlessly."
                textSize = 12f
                setTextColor(Color.parseColor("#A3B899"))
                setPadding(0, 8, 0, 8)
            }
            conflictContainer.addView(tvNoConflicts)
        } else {
            for (conflict in preview.conflicts) {
                val conflictView = createConflictRow(conflict.fieldName, conflict.valueA, conflict.valueB) { chosenVal ->
                    selectedOverrides[conflict.fieldName] = chosenVal
                }
                // default to A
                selectedOverrides[conflict.fieldName] = conflict.valueA
                conflictContainer.addView(conflictView)
            }
        }

        // 4. Affected children & spouse info
        val tvChildren = dialogView.findViewById<TextView>(R.id.tvAffectedChildrenCount)
        val childNames = preview.affectedChildren.joinToString(", ") { "${it.firstName} ${it.lastName}" }
        if (preview.affectedChildren.isNotEmpty()) {
            tvChildren.text = "👶 ${preview.affectedChildren.size} child(ren) will be reparented: $childNames"
            tvChildren.setTextColor(Color.parseColor("#4ECCA3"))
        } else {
            tvChildren.text = "👶 No dependent children to reparent"
            tvChildren.setTextColor(Color.parseColor("#E0E0E0"))
        }

        // 5. Buttons
        val btnExecute = dialogView.findViewById<Button>(R.id.btnConfirmExecuteMerge)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelMergeDialog)

        if (isViewerOnly) {
            btnExecute.isEnabled = false
            btnExecute.alpha = 0.5f
            btnExecute.text = "Read-Only (Viewer)"
        } else {
            btnExecute.setOnClickListener {
                dialog.dismiss()
                executeMerge(personA, personB, preview.affectedChildren, selectedOverrides)
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun createConflictRow(
        fieldName: String,
        valA: String,
        valB: String,
        onSelected: (String) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.badge_pill_dark)
            setPadding(12, 10, 12, 10)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            layoutParams = params
        }

        val tvField = TextView(this).apply {
            text = fieldName.uppercase()
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#D4A359"))
            setPadding(0, 0, 0, 4)
        }
        row.addView(tvField)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val rbA = RadioButton(this).apply {
            text = "Record A: $valA"
            setTextColor(Color.WHITE)
            textSize = 11f
            isChecked = true
        }

        val rbB = RadioButton(this).apply {
            text = "Record B: $valB"
            setTextColor(Color.WHITE)
            textSize = 11f
        }

        radioGroup.addView(rbA)
        radioGroup.addView(rbB)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbA.id) {
                onSelected(valA)
            } else {
                onSelected(valB)
            }
        }

        row.addView(radioGroup)
        return row
    }

    private fun executeMerge(
        personA: Person,
        personB: Person,
        affectedChildren: List<Person>,
        selectedOverrides: Map<String, String>
    ) {
        val mergedPerson = mergeEngine.buildCustomMergedPerson(personA, personB, selectedOverrides)

        progressBarMerge.visibility = View.VISIBLE
        Toast.makeText(this, "Executing atomic merge...", Toast.LENGTH_SHORT).show()

        firestoreHelper.mergePersons(
            treeId = treeId,
            mergedPerson = mergedPerson,
            deletedPersonId = personB.id,
            affectedChildren = affectedChildren,
            onSuccess = {
                val currentUserId = authHelper.getCurrentUserId() ?: ""
                val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

                // Log Activity (D8)
                firestoreHelper.logActivity(
                    treeId = treeId,
                    userId = currentUserId,
                    userName = currentUserName,
                    type = "BRANCH_MERGED",
                    description = "Merged duplicate records: ${personB.firstName} ${personB.lastName} into ${mergedPerson.firstName} ${mergedPerson.lastName}",
                    targetId = mergedPerson.id,
                    targetName = "${mergedPerson.firstName} ${mergedPerson.lastName}"
                )

                // Add Notification (D7)
                firestoreHelper.addNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    title = "Family Branch Merged",
                    message = "${personB.firstName} ${personB.lastName} was merged into ${mergedPerson.firstName} ${mergedPerson.lastName}.",
                    type = "RECORDS",
                    targetId = mergedPerson.id
                )

                Toast.makeText(this, "Branch records merged successfully!", Toast.LENGTH_LONG).show()
                loadTreeMembers()
            },
            onFailure = { error ->
                progressBarMerge.visibility = View.GONE
                AlertDialog.Builder(this)
                    .setTitle("Merge Failed")
                    .setMessage("An error occurred during branch merge: ${error.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    // =========================================================================
    // MANUAL BRANCH CONNECTOR (CONNECT DISCONNECTED LINEAGES)
    // =========================================================================
    private fun showConnectBranchesDialog() {
        if (allPersons.size < 2) {
            Toast.makeText(this, "At least 2 members are required to connect branches.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connect_branches, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val spinnerA = dialogView.findViewById<Spinner>(R.id.spinnerBranchMemberA)
        val spinnerB = dialogView.findViewById<Spinner>(R.id.spinnerBranchMemberB)
        val spinnerConn = dialogView.findViewById<Spinner>(R.id.spinnerConnectionType)
        val layoutCycleWarning = dialogView.findViewById<LinearLayout>(R.id.layoutCycleWarning)
        val tvCycleWarning = dialogView.findViewById<TextView>(R.id.tvCycleWarningText)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmConnect)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelConnect)

        val names = allPersons.map { "${it.firstName} ${it.lastName}" }
        spinnerA.setDarkAdapter(this, names)
        spinnerB.setDarkAdapter(this, names)

        if (allPersons.size > 1) {
            spinnerB.setSelection(1)
        }

        val connOptions = listOf(
            "Spouse (Marital Union)",
            "Member A is Father of Member B",
            "Member A is Mother of Member B",
            "Member A is Child of Member B"
        )
        spinnerConn.setDarkAdapter(this, connOptions)

        val allPersonsMap = allPersons.associateBy { it.id }

        fun validateConnection(): Boolean {
            val idxA = spinnerA.selectedItemPosition
            val idxB = spinnerB.selectedItemPosition
            val connPos = spinnerConn.selectedItemPosition

            if (idxA < 0 || idxB < 0 || idxA == idxB) {
                layoutCycleWarning.visibility = View.VISIBLE
                tvCycleWarning.text = "⚠️ Please select two different family members."
                btnConfirm.isEnabled = false
                return false
            }

            val pA = allPersons[idxA]
            val pB = allPersons[idxB]

            when (connPos) {
                0 -> { // Spouse
                    if (pA.spouseId != null && pA.spouseId != pB.id) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ ${pA.firstName} is already married to another member."
                        btnConfirm.isEnabled = false
                        return false
                    }
                    if (pB.spouseId != null && pB.spouseId != pA.id) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ ${pB.firstName} is already married to another member."
                        btnConfirm.isEnabled = false
                        return false
                    }
                    val sCheck = com.example.btproject2.engine.FamilyLinkValidator.validateSpouse(pA, pB, allPersons)
                    if (!sCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Prohibited Spouse: ${sCheck.errors.first().message}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                1, 2 -> { // Member A is Parent of Member B
                    val hasCycle = mergeEngine.detectCycle(pA.id, pB.id, allPersonsMap)
                    if (hasCycle) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Biological Loop Detected: ${pB.firstName} is already an ancestor of ${pA.firstName}."
                        btnConfirm.isEnabled = false
                        return false
                    }
                    val role = if (pA.gender.equals("female", ignoreCase = true)) "mother" else "father"
                    val pCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(pA, pB, role, allPersons)
                    if (!pCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Prohibited Parent-Child Link: ${pCheck.errors.first().message}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                3 -> { // Member A is Child of Member B
                    val hasCycle = mergeEngine.detectCycle(pB.id, pA.id, allPersonsMap)
                    if (hasCycle) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Biological Loop Detected: ${pA.firstName} is already an ancestor of ${pB.firstName}."
                        btnConfirm.isEnabled = false
                        return false
                    }
                    val role = if (pB.gender.equals("female", ignoreCase = true)) "mother" else "father"
                    val pCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(pB, pA, role, allPersons)
                    if (!pCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Prohibited Parent-Child Link: ${pCheck.errors.first().message}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
            }

            layoutCycleWarning.visibility = View.GONE
            btnConfirm.isEnabled = true
            return true
        }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                validateConnection()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerA.onItemSelectedListener = listener
        spinnerB.onItemSelectedListener = listener
        spinnerConn.onItemSelectedListener = listener

        validateConnection()

        btnConfirm.setOnClickListener {
            if (!validateConnection()) return@setOnClickListener

            val pA = allPersons[spinnerA.selectedItemPosition]
            val pB = allPersons[spinnerB.selectedItemPosition]
            val connType = when (spinnerConn.selectedItemPosition) {
                0 -> "SPOUSE"
                1, 2 -> "PARENT_OF"
                3 -> "CHILD_OF"
                else -> "SPOUSE"
            }

            dialog.dismiss()
            progressBarMerge.visibility = View.VISIBLE

            firestoreHelper.connectBranchMembers(
                treeId = treeId,
                personAId = pA.id,
                personBId = pB.id,
                connectionType = connType,
                personAGender = pA.gender,
                personBGender = pB.gender,
                onSuccess = {
                    val currentUserId = authHelper.getCurrentUserId() ?: ""
                    val currentUserName = authHelper.getCurrentUser()?.displayName ?: "User"

                    val connDesc = when (connType) {
                        "SPOUSE" -> "Spousal marriage union"
                        "PARENT_OF" -> "${pA.firstName} as parent of ${pB.firstName}"
                        else -> "${pA.firstName} as child of ${pB.firstName}"
                    }

                    firestoreHelper.logActivity(
                        treeId = treeId,
                        userId = currentUserId,
                        userName = currentUserName,
                        type = "BRANCH_MERGED",
                        description = "Connected branch members: ${pA.firstName} & ${pB.firstName} ($connDesc)",
                        targetId = "${pA.id}|${pB.id}",
                        targetName = "${pA.firstName} & ${pB.firstName}"
                    )

                    Toast.makeText(this, "Branch connection established successfully!", Toast.LENGTH_SHORT).show()
                    loadTreeMembers()
                },
                onFailure = { err ->
                    progressBarMerge.visibility = View.GONE
                    Toast.makeText(this, "Failed to connect branches: ${err.message}", Toast.LENGTH_LONG).show()
                }
            )
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
