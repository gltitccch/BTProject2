package com.example.btproject2.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
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
import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.Person
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.NotificationHelper
import com.example.btproject2.utils.setDarkAdapter
import java.util.UUID

class MergeBranchesActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private val marriageEngine = MarriageValidationEngine(minimumSpouseAge = 18)

    private var activeTreeId = ""
    private var allPersonalTrees: List<FamilyTree> = emptyList()
    private var currentTreePersons: List<Person> = emptyList()
    private var isViewerOnly = false

    private lateinit var tvSubtitle: TextView
    private lateinit var tvMergeRoleBadge: TextView
    private lateinit var layoutViewerBanner: View
    private lateinit var btnOpenSynthesizeClanTree: Button
    private lateinit var tvClanTreeCount: TextView
    private lateinit var progressBarMerge: ProgressBar
    private lateinit var layoutEmptyClanState: View
    private lateinit var clanTreesContainer: LinearLayout
    private lateinit var btnOpenConnectBranches: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merge_branches)

        activeTreeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvMergeRoleBadge = findViewById(R.id.tvMergeRoleBadge)
        layoutViewerBanner = findViewById(R.id.layoutViewerBanner)
        btnOpenSynthesizeClanTree = findViewById(R.id.btnOpenSynthesizeClanTree)
        tvClanTreeCount = findViewById(R.id.tvClanTreeCount)
        progressBarMerge = findViewById(R.id.progressBarMerge)
        layoutEmptyClanState = findViewById(R.id.layoutEmptyClanState)
        clanTreesContainer = findViewById(R.id.clanTreesContainer)
        btnOpenConnectBranches = findViewById(R.id.btnOpenConnectBranches)

        btnOpenSynthesizeClanTree.setOnClickListener {
            if (isViewerOnly) {
                Toast.makeText(this, "Permission restricted: Viewers cannot synthesize clan trees.", Toast.LENGTH_SHORT).show()
            } else {
                showSynthesizeClanTreeDialog()
            }
        }

        btnOpenConnectBranches.setOnClickListener {
            if (isViewerOnly) {
                val currentUserId = authHelper.getCurrentUserId().orEmpty()
                val notif = NotificationHelper.createPermissionRestrictedNotification(
                    treeId = activeTreeId,
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
        if (activeTreeId.isNotEmpty()) {
            firestoreHelper.getTree(activeTreeId,
                onSuccess = { tree ->
                    val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                    if (isOwner) {
                        tvMergeRoleBadge.text = "★ Owner"
                        isViewerOnly = false
                        layoutViewerBanner.visibility = View.GONE
                    } else {
                        firestoreHelper.getUserMembership(currentUserId, activeTreeId,
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
                                tvMergeRoleBadge.text = "★ Owner"
                                isViewerOnly = false
                                layoutViewerBanner.visibility = View.GONE
                            }
                        )
                    }
                    loadPersonalTreesAndMembers()
                },
                onFailure = {
                    tvMergeRoleBadge.text = "★ Owner"
                    isViewerOnly = false
                    loadPersonalTreesAndMembers()
                }
            )
        } else {
            tvMergeRoleBadge.text = "★ Owner"
            isViewerOnly = false
            loadPersonalTreesAndMembers()
        }
    }

    private fun loadPersonalTreesAndMembers() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        // Load personal trees (excluding merged clan trees)
        firestoreHelper.getUserTrees(currentUserId, includeMergedClan = false,
            onSuccess = { trees ->
                allPersonalTrees = trees
            },
            onFailure = {}
        )

        // Load current active tree members for intra-tree tool
        if (activeTreeId.isNotEmpty()) {
            firestoreHelper.getPersonsByTree(activeTreeId,
                onSuccess = { persons ->
                    currentTreePersons = persons
                },
                onFailure = {}
            )
        }

        // Load synthesized clan trees
        loadClanTrees()
    }

    private fun loadClanTrees() {
        val currentUserId = authHelper.getCurrentUserId() ?: ""
        progressBarMerge.visibility = View.VISIBLE
        layoutEmptyClanState.visibility = View.GONE
        clanTreesContainer.removeAllViews()
        tvClanTreeCount.text = "Scanning..."

        firestoreHelper.getMergedClanTrees(currentUserId,
            onSuccess = { clanTrees ->
                progressBarMerge.visibility = View.GONE
                if (clanTrees.isEmpty()) {
                    tvClanTreeCount.text = "0 Clan Trees"
                    layoutEmptyClanState.visibility = View.VISIBLE
                } else {
                    tvClanTreeCount.text = "${clanTrees.size} Clan Tree(s)"
                    layoutEmptyClanState.visibility = View.GONE
                    val inflater = LayoutInflater.from(this)
                    for (tree in clanTrees) {
                        addClanTreeCard(inflater, tree)
                    }
                }
            },
            onFailure = {
                progressBarMerge.visibility = View.GONE
                tvClanTreeCount.text = "0 Clan Trees"
                layoutEmptyClanState.visibility = View.VISIBLE
            }
        )
    }

    private fun addClanTreeCard(inflater: LayoutInflater, tree: FamilyTree) {
        val card = inflater.inflate(R.layout.item_clan_tree_card, clanTreesContainer, false)

        val tvName = card.findViewById<TextView>(R.id.tvClanTreeName)
        val tvMembers = card.findViewById<TextView>(R.id.tvClanTreeMemberCount)
        val tvBridge = card.findViewById<TextView>(R.id.tvClanTreeBridge)
        val btnView = card.findViewById<Button>(R.id.btnViewClanCanvas)
        val btnDelete = card.findViewById<Button>(R.id.btnDeleteClanTree)

        tvName.text = tree.name
        tvMembers.text = "${tree.memberCount} Members • Master Clan Sandbox"

        if (tree.bridgeDescription.isNotBlank()) {
            tvBridge.text = tree.bridgeDescription
            tvBridge.visibility = View.VISIBLE
        } else {
            tvBridge.text = "💍 Master Clan Bridge Link"
            tvBridge.visibility = View.VISIBLE
        }

        btnView.setOnClickListener {
            val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                putExtra("TREE_ID", tree.id)
                putExtra("treeId", tree.id)
                putExtra("IS_CLAN_SPACE", true)
            }
            startActivity(intent)
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Master Clan Tree?")
                .setMessage("Are you sure you want to remove '${tree.name}'?\n\n🛡️ 100% Safe: Your original family trees will NOT be modified or deleted.")
                .setPositiveButton("Delete") { _, _ ->
                    progressBarMerge.visibility = View.VISIBLE
                    firestoreHelper.deleteTree(tree.id,
                        onSuccess = {
                            Toast.makeText(this, "Master Clan Tree deleted.", Toast.LENGTH_SHORT).show()
                            loadClanTrees()
                        },
                        onFailure = { error ->
                            progressBarMerge.visibility = View.GONE
                            Toast.makeText(this, "Failed to delete: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        clanTreesContainer.addView(card)
    }

    // =========================================================================
    // 3-STEP ZERO-RISK MASTER CLAN TREE SYNTHESIS WIZARD (PROPOSALS 15 & 16)
    // =========================================================================
    private fun showSynthesizeClanTreeDialog() {
        if (allPersonalTrees.isEmpty()) {
            Toast.makeText(this, "You need at least one family tree to synthesize a clan tree.", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_synthesize_clan_tree, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val spinnerTree1 = dialogView.findViewById<Spinner>(R.id.spinnerTree1)
        val rgTree2Source = dialogView.findViewById<RadioGroup>(R.id.rgTree2Source)
        val rbTree2MyTrees = dialogView.findViewById<RadioButton>(R.id.rbTree2MyTrees)
        val spinnerTree2 = dialogView.findViewById<Spinner>(R.id.spinnerTree2)
        val layoutTree2InviteCode = dialogView.findViewById<LinearLayout>(R.id.layoutTree2InviteCode)
        val etTree2InviteCode = dialogView.findViewById<EditText>(R.id.etTree2InviteCode)
        val btnLookupInviteCode = dialogView.findViewById<Button>(R.id.btnLookupInviteCode)
        val tvInviteCodeResult = dialogView.findViewById<TextView>(R.id.tvInviteCodeResult)
        val etMasterTreeName = dialogView.findViewById<EditText>(R.id.etMasterTreeName)

        val rgBridgeType = dialogView.findViewById<RadioGroup>(R.id.rgBridgeType)
        val rbBridgeSpouse = dialogView.findViewById<RadioButton>(R.id.rbBridgeSpouse)
        val rbBridgeParentChild = dialogView.findViewById<RadioButton>(R.id.rbBridgeParentChild)
        val rbBridgeSharedAncestor = dialogView.findViewById<RadioButton>(R.id.rbBridgeSharedAncestor)

        val tvAnchor1Label = dialogView.findViewById<TextView>(R.id.tvAnchor1Label)
        val spinnerAnchorPerson1 = dialogView.findViewById<Spinner>(R.id.spinnerAnchorPerson1)
        val tvAnchor2Label = dialogView.findViewById<TextView>(R.id.tvAnchor2Label)
        val spinnerAnchorPerson2 = dialogView.findViewById<Spinner>(R.id.spinnerAnchorPerson2)

        val layoutValidationWarning = dialogView.findViewById<LinearLayout>(R.id.layoutValidationWarning)
        val tvValidationWarningText = dialogView.findViewById<TextView>(R.id.tvValidationWarningText)
        val tvSynthesisSummary = dialogView.findViewById<TextView>(R.id.tvSynthesisSummary)
        val progressBarSynthesis = dialogView.findViewById<ProgressBar>(R.id.progressBarSynthesis)
        val btnExecuteSynthesis = dialogView.findViewById<Button>(R.id.btnExecuteSynthesis)
        val btnCancelSynthesis = dialogView.findViewById<Button>(R.id.btnCancelSynthesis)

        // Populate Tree 1 Spinner
        val tree1Names = allPersonalTrees.map { it.name }
        spinnerTree1.setDarkAdapter(this, tree1Names)

        // Populate Tree 2 Spinner
        val tree2Names = allPersonalTrees.map { it.name }
        spinnerTree2.setDarkAdapter(this, tree2Names)
        if (allPersonalTrees.size > 1) {
            spinnerTree2.setSelection(1)
        }

        var personsTree1 = listOf<Person>()
        var personsTree2 = listOf<Person>()
        var externalTreeLoaded: FamilyTree? = null

        fun updateDefaultMasterTreeName() {
            val t1Name = spinnerTree1.selectedItem?.toString().orEmpty()
            val t2Name = if (rbTree2MyTrees.isChecked) {
                spinnerTree2.selectedItem?.toString().orEmpty()
            } else {
                externalTreeLoaded?.name ?: "External Tree"
            }
            if (t1Name.isNotEmpty() && t2Name.isNotEmpty()) {
                val clean1 = t1Name.replace(" Family Tree", "").replace(" Tree", "")
                val clean2 = t2Name.replace(" Family Tree", "").replace(" Tree", "")
                etMasterTreeName.setText("$clean1 - $clean2 Master Clan Tree")
            }
        }

        fun updateSummaryAndValidation() {
            layoutValidationWarning.visibility = View.GONE
            val count1 = personsTree1.size
            val count2 = personsTree2.size
            val total = count1 + count2

            tvSynthesisSummary.text = "📊 Summary: $count1 members from Tree 1 + $count2 members from Tree 2 = $total total members in Master Tree C.\n🛡️ Zero-Risk: Original trees remain untouched."

            val idx1 = spinnerAnchorPerson1.selectedItemPosition
            val idx2 = spinnerAnchorPerson2.selectedItemPosition

            if (idx1 >= 0 && idx2 >= 0 && idx1 < personsTree1.size && idx2 < personsTree2.size) {
                val p1 = personsTree1[idx1]
                val p2 = personsTree2[idx2]

                if (rbBridgeSpouse.isChecked) {
                    val combinedMap = (personsTree1 + personsTree2).associateBy { it.id }
                    val result = marriageEngine.validateSpouseConnection(p1.id, p2.id, combinedMap)
                    if (!result.isAllowed) {
                        layoutValidationWarning.visibility = View.VISIBLE
                        tvValidationWarningText.text = "⚠️ Spousal impediment: ${result.reason}"
                    }
                }
            }
        }

        fun loadTree1Members() {
            val selPos = spinnerTree1.selectedItemPosition
            if (selPos in allPersonalTrees.indices) {
                val selectedTree = allPersonalTrees[selPos]
                tvAnchor1Label.text = "Member from ${selectedTree.name}"
                firestoreHelper.getPersonsByTree(selectedTree.id,
                    onSuccess = { list ->
                        personsTree1 = list
                        val names = list.map { "${it.firstName} ${it.lastName}" }
                        spinnerAnchorPerson1.setDarkAdapter(this, if (names.isEmpty()) listOf("No members recorded") else names)
                        updateSummaryAndValidation()
                    },
                    onFailure = {
                        personsTree1 = emptyList()
                        spinnerAnchorPerson1.setDarkAdapter(this, listOf("Failed to load"))
                    }
                )
            }
        }

        fun loadTree2Members() {
            if (rbTree2MyTrees.isChecked) {
                val selPos = spinnerTree2.selectedItemPosition
                if (selPos in allPersonalTrees.indices) {
                    val selectedTree = allPersonalTrees[selPos]
                    tvAnchor2Label.text = "Member from ${selectedTree.name}"
                    firestoreHelper.getPersonsByTree(selectedTree.id,
                        onSuccess = { list ->
                            personsTree2 = list
                            val names = list.map { "${it.firstName} ${it.lastName}" }
                            spinnerAnchorPerson2.setDarkAdapter(this, if (names.isEmpty()) listOf("No members recorded") else names)
                            updateSummaryAndValidation()
                        },
                        onFailure = {
                            personsTree2 = emptyList()
                            spinnerAnchorPerson2.setDarkAdapter(this, listOf("Failed to load"))
                        }
                    )
                }
            } else if (externalTreeLoaded != null) {
                tvAnchor2Label.text = "Member from ${externalTreeLoaded!!.name}"
                firestoreHelper.getPersonsByTree(externalTreeLoaded!!.id,
                    onSuccess = { list ->
                        personsTree2 = list
                        val names = list.map { "${it.firstName} ${it.lastName}" }
                        spinnerAnchorPerson2.setDarkAdapter(this, if (names.isEmpty()) listOf("No members recorded") else names)
                        updateSummaryAndValidation()
                    },
                    onFailure = {
                        personsTree2 = emptyList()
                        spinnerAnchorPerson2.setDarkAdapter(this, listOf("Failed to load"))
                    }
                )
            }
        }

        // Toggle Source for Tree 2
        rgTree2Source.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbTree2MyTrees) {
                spinnerTree2.visibility = View.VISIBLE
                layoutTree2InviteCode.visibility = View.GONE
                loadTree2Members()
            } else {
                spinnerTree2.visibility = View.GONE
                layoutTree2InviteCode.visibility = View.VISIBLE
                if (externalTreeLoaded == null) {
                    personsTree2 = emptyList()
                    spinnerAnchorPerson2.setDarkAdapter(this, listOf("Enter and verify code above"))
                } else {
                    loadTree2Members()
                }
            }
            updateDefaultMasterTreeName()
        }

        // Lookup Invite Code
        btnLookupInviteCode.setOnClickListener {
            val code = etTree2InviteCode.text.toString().trim().uppercase()
            if (code.length < 6) {
                Toast.makeText(this, "Please enter a valid 6-character invite code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnLookupInviteCode.isEnabled = false
            btnLookupInviteCode.text = "..."

            firestoreHelper.getInviteCodeRecord(code,
                onSuccess = { record ->
                    btnLookupInviteCode.isEnabled = true
                    btnLookupInviteCode.text = "Verify"
                    if (record == null) {
                        tvInviteCodeResult.text = "❌ No family tree found with invite code $code."
                        tvInviteCodeResult.setTextColor(Color.parseColor("#FF6B6B"))
                        tvInviteCodeResult.visibility = View.VISIBLE
                    } else {
                        firestoreHelper.getTree(record.treeId,
                            onSuccess = { tree ->
                                externalTreeLoaded = tree
                                tvInviteCodeResult.text = "✓ Found: ${record.treeName} (${tree?.memberCount ?: 0} members)"
                                tvInviteCodeResult.setTextColor(Color.parseColor("#4ECCA3"))
                                tvInviteCodeResult.visibility = View.VISIBLE
                                updateDefaultMasterTreeName()
                                loadTree2Members()
                            },
                            onFailure = {
                                tvInviteCodeResult.text = "✓ Found: ${record.treeName}"
                                tvInviteCodeResult.setTextColor(Color.parseColor("#4ECCA3"))
                                tvInviteCodeResult.visibility = View.VISIBLE
                                updateDefaultMasterTreeName()
                                loadTree2Members()
                            }
                        )
                    }
                },
                onFailure = { err ->
                    btnLookupInviteCode.isEnabled = true
                    btnLookupInviteCode.text = "Verify"
                    tvInviteCodeResult.text = "❌ Error looking up invite code: ${err.message}"
                    tvInviteCodeResult.setTextColor(Color.parseColor("#FF6B6B"))
                    tvInviteCodeResult.visibility = View.VISIBLE
                }
            )
        }

        spinnerTree1.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadTree1Members()
                updateDefaultMasterTreeName()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerTree2.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadTree2Members()
                updateDefaultMasterTreeName()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val anchorListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSummaryAndValidation()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerAnchorPerson1.onItemSelectedListener = anchorListener
        spinnerAnchorPerson2.onItemSelectedListener = anchorListener

        rgBridgeType.setOnCheckedChangeListener { _, _ -> updateSummaryAndValidation() }

        // Initial loads
        loadTree1Members()
        loadTree2Members()
        updateDefaultMasterTreeName()

        btnCancelSynthesis.setOnClickListener { dialog.dismiss() }

        btnExecuteSynthesis.setOnClickListener {
            val masterTreeName = etMasterTreeName.text.toString().trim()
            if (masterTreeName.isBlank()) {
                Toast.makeText(this, "Please enter a name for the Master Clan Tree.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (personsTree1.isEmpty() || personsTree2.isEmpty()) {
                Toast.makeText(this, "Both trees must contain members to synthesize a clan tree.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val idx1 = spinnerAnchorPerson1.selectedItemPosition
            val idx2 = spinnerAnchorPerson2.selectedItemPosition

            if (idx1 !in personsTree1.indices || idx2 !in personsTree2.indices) {
                Toast.makeText(this, "Please select anchor connection members from both trees.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val anchor1 = personsTree1[idx1]
            val anchor2 = personsTree2[idx2]

            btnExecuteSynthesis.isEnabled = false
            btnCancelSynthesis.isEnabled = false
            progressBarSynthesis.visibility = View.VISIBLE

            // =========================================================================
            // ZERO-RISK SYNTHESIS ENGINE: DEEP CLONING WITH ID REMAPPING
            // =========================================================================
            val masterTreeId = UUID.randomUUID().toString()
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            val currentUserName = authHelper.getCurrentUser()?.displayName ?: "Owner"

            // 1. Build UUID translation dictionary
            val idMap = mutableMapOf<String, String>()
            for (p in personsTree1 + personsTree2) {
                idMap[p.id] = UUID.randomUUID().toString()
            }

            // 2. Clone members into Tree C
            val clonedList = mutableListOf<Person>()
            fun clonePerson(p: Person): Person {
                return p.copy(
                    id = idMap[p.id]!!,
                    treeId = masterTreeId,
                    fatherId = idMap[p.fatherId] ?: p.fatherId,
                    motherId = idMap[p.motherId] ?: p.motherId,
                    spouseId = idMap[p.spouseId] ?: p.spouseId,
                    createdBy = currentUserId,
                    createdAt = System.currentTimeMillis()
                )
            }

            for (p in personsTree1) clonedList.add(clonePerson(p))
            for (p in personsTree2) clonedList.add(clonePerson(p))

            // 3. Apply Anchor Bridge via immutable copies
            val idxA = clonedList.indexOfFirst { it.id == idMap[anchor1.id] }
            val idxB = clonedList.indexOfFirst { it.id == idMap[anchor2.id] }
            var bridgeDesc = ""

            if (idxA != -1 && idxB != -1) {
                val clonedA = clonedList[idxA]
                val clonedB = clonedList[idxB]

                when {
                    rbBridgeSpouse.isChecked -> {
                        clonedList[idxA] = clonedA.copy(spouseId = clonedB.id)
                        clonedList[idxB] = clonedB.copy(spouseId = clonedA.id)
                        bridgeDesc = "💍 ${clonedA.firstName} ${clonedA.lastName} married to ${clonedB.firstName} ${clonedB.lastName}"
                    }
                    rbBridgeParentChild.isChecked -> {
                        if (clonedA.gender.equals("Female", ignoreCase = true)) {
                            clonedList[idxB] = clonedB.copy(motherId = clonedA.id)
                        } else {
                            clonedList[idxB] = clonedB.copy(fatherId = clonedA.id)
                        }
                        bridgeDesc = "👶 ${clonedA.firstName} ${clonedA.lastName} is Parent of ${clonedB.firstName} ${clonedB.lastName}"
                    }
                    rbBridgeSharedAncestor.isChecked -> {
                        for (i in clonedList.indices) {
                            var item = clonedList[i]
                            if (item.fatherId == clonedB.id) item = item.copy(fatherId = clonedA.id)
                            if (item.motherId == clonedB.id) item = item.copy(motherId = clonedA.id)
                            if (item.spouseId == clonedB.id) item = item.copy(spouseId = clonedA.id)
                            clonedList[i] = item
                        }
                        clonedList.removeAt(idxB)
                        bridgeDesc = "👥 Shared Ancestor: ${clonedA.firstName} ${clonedA.lastName}"
                    }
                }
            }

            // 4. Create Master Tree C record
            val masterTree = FamilyTree(
                id = masterTreeId,
                name = masterTreeName,
                ownerId = currentUserId,
                ownerName = currentUserName,
                memberCount = clonedList.size,
                createdAt = System.currentTimeMillis(),
                treeType = FamilyTree.TREE_TYPE_MERGED_CLAN,
                bridgeDescription = bridgeDesc
            )

            // 5. Commit atomic batch write
            firestoreHelper.saveMasterTreeAndMembers(masterTree, clonedList,
                onSuccess = { createdTree ->
                    CentralTreeSynchronizer.getInstance().startRealtimeListener(createdTree.id)
                    dialog.dismiss()
                    Toast.makeText(this, "Master Clan Tree synthesized successfully!", Toast.LENGTH_LONG).show()
                    loadClanTrees()

                    AlertDialog.Builder(this)
                        .setTitle("Master Clan Tree Created!")
                        .setMessage("'${createdTree.name}' has been created with ${createdTree.memberCount} members in your Merged Clan Space.\n\n🛡️ Your original family trees remain completely untouched.\n\nWould you like to explore the Clan Canvas now?")
                        .setPositiveButton("👁️ View Clan Canvas") { _, _ ->
                            val intent = Intent(this, InteractiveTreeActivity::class.java).apply {
                                putExtra("TREE_ID", createdTree.id)
                                putExtra("treeId", createdTree.id)
                                putExtra("IS_CLAN_SPACE", true)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("Done", null)
                        .show()
                },
                onFailure = { err ->
                    progressBarSynthesis.visibility = View.GONE
                    btnExecuteSynthesis.isEnabled = true
                    btnCancelSynthesis.isEnabled = true
                    AlertDialog.Builder(this)
                        .setTitle("Synthesis Failed")
                        .setMessage("An error occurred while saving the Master Clan Tree: ${err.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            )
        }

        dialog.show()
    }

    // =========================================================================
    // INTRA-TREE BRANCH CONNECTOR (CONNECT DISCONNECTED NODES IN ACTIVE TREE)
    // =========================================================================
    private fun showConnectBranchesDialog() {
        if (currentTreePersons.size < 2) {
            Toast.makeText(this, "At least 2 members are required to connect internal branches.", Toast.LENGTH_SHORT).show()
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

        val names = currentTreePersons.map { "${it.firstName} ${it.lastName}" }
        spinnerA.setDarkAdapter(this, names)
        spinnerB.setDarkAdapter(this, names)

        if (currentTreePersons.size > 1) {
            spinnerB.setSelection(1)
        }

        val connOptions = listOf(
            "Spouse (Marital Union)",
            "Member A is Father of Member B",
            "Member A is Mother of Member B",
            "Member A is Child of Member B"
        )
        spinnerConn.setDarkAdapter(this, connOptions)

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

            val pA = currentTreePersons[idxA]
            val pB = currentTreePersons[idxB]

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
                    val sCheck = FamilyLinkValidator.validateSpouse(pA, pB, currentTreePersons)
                    if (!sCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Prohibited Spouse: ${sCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                1 -> { // Father
                    val pCheck = FamilyLinkValidator.validateParentChild(pA, pB, "father", currentTreePersons)
                    if (!pCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Invalid Parent Link: ${pCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                2 -> { // Mother
                    val pCheck = FamilyLinkValidator.validateParentChild(pA, pB, "mother", currentTreePersons)
                    if (!pCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Invalid Parent Link: ${pCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
                        btnConfirm.isEnabled = false
                        return false
                    }
                }
                3 -> { // Child
                    val cCheck = FamilyLinkValidator.validateParentChild(pB, pA, "parent", currentTreePersons)
                    if (!cCheck.isValid) {
                        layoutCycleWarning.visibility = View.VISIBLE
                        tvCycleWarning.text = "⚠️ Invalid Child Link: ${cCheck.errors.firstOrNull()?.message ?: "Invalid connection"}"
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

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            if (!validateConnection()) return@setOnClickListener

            val idxA = spinnerA.selectedItemPosition
            val idxB = spinnerB.selectedItemPosition
            val connPos = spinnerConn.selectedItemPosition

            val pA = currentTreePersons[idxA]
            val pB = currentTreePersons[idxB]

            btnConfirm.isEnabled = false
            btnConfirm.text = "Connecting..."

            when (connPos) {
                0 -> { // Spouse
                    val updatedA = pA.copy(spouseId = pB.id)
                    val updatedB = pB.copy(spouseId = pA.id)
                    firestoreHelper.updatePerson(updatedA, {
                        firestoreHelper.updatePerson(updatedB, {
                            CentralTreeSynchronizer.getInstance().notifySpouseChanged(updatedA, updatedB)
                            dialog.dismiss()
                            Toast.makeText(this, "Spousal link created!", Toast.LENGTH_SHORT).show()
                            loadPersonalTreesAndMembers()
                        }, { dialog.dismiss() })
                    }, { dialog.dismiss() })
                }
                1 -> { // A is Father of B
                    val updatedB = pB.copy(fatherId = pA.id)
                    firestoreHelper.updatePerson(updatedB, {
                        CentralTreeSynchronizer.getInstance().notifyParentChildChanged(updatedB, pA, "father")
                        dialog.dismiss()
                        Toast.makeText(this, "Paternal branch connected!", Toast.LENGTH_SHORT).show()
                        loadPersonalTreesAndMembers()
                    }, { dialog.dismiss() })
                }
                2 -> { // A is Mother of B
                    val updatedB = pB.copy(motherId = pA.id)
                    firestoreHelper.updatePerson(updatedB, {
                        CentralTreeSynchronizer.getInstance().notifyParentChildChanged(updatedB, pA, "mother")
                        dialog.dismiss()
                        Toast.makeText(this, "Maternal branch connected!", Toast.LENGTH_SHORT).show()
                        loadPersonalTreesAndMembers()
                    }, { dialog.dismiss() })
                }
                3 -> { // A is Child of B
                    val updatedA = if (pB.gender.equals("Female", ignoreCase = true)) {
                        pA.copy(motherId = pB.id)
                    } else {
                        pA.copy(fatherId = pB.id)
                    }
                    firestoreHelper.updatePerson(updatedA, {
                        CentralTreeSynchronizer.getInstance().notifyParentChildChanged(updatedA, pB, "parent")
                        dialog.dismiss()
                        Toast.makeText(this, "Branch connected!", Toast.LENGTH_SHORT).show()
                        loadPersonalTreesAndMembers()
                    }, { dialog.dismiss() })
                }
            }
        }

        dialog.show()
    }
}
