package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.Person
import com.example.btproject2.ui.adapters.ActivityAdapter

class RecentActivitiesActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private var treeId: String = ""
    private lateinit var adapter: ActivityAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var tvActivityCountBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_activities)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        tvActivityCountBadge = findViewById(R.id.tvActivityCountBadge)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmptyActivities)

        val rvActivities = findViewById<RecyclerView>(R.id.rvActivities)
        rvActivities.layoutManager = LinearLayoutManager(this)

        adapter = ActivityAdapter { activity ->
            if (activity.targetId.isNotEmpty() && activity.type.uppercase() != "DELETE" && activity.type.uppercase() != "MEMBER_DELETED") {
                val intent = Intent(this, MemberDetailActivity::class.java).apply {
                    putExtra("personId", activity.targetId)
                    putExtra("TREE_ID", treeId)
                }
                startActivity(intent)
            }
        }
        rvActivities.adapter = adapter

        loadActivities()
    }

    override fun onResume() {
        super.onResume()
        loadActivities()
    }

    private fun loadActivities() {
        progressBar.visibility = View.VISIBLE
        firestoreHelper.getRecentActivities(treeId, limit = 100,
            onSuccess = { activities ->
                if (activities.isNotEmpty()) {
                    progressBar.visibility = View.GONE
                    layoutEmpty.visibility = View.GONE
                    tvActivityCountBadge.text = "${activities.size} total"
                    adapter.submitList(activities)
                } else {
                    // Fallback to synthesizing activities from member records if empty
                    firestoreHelper.getAllPersons(
                        onSuccess = { persons ->
                            progressBar.visibility = View.GONE
                            val synthesized = synthesizeActivities(persons)
                            if (synthesized.isNotEmpty()) {
                                layoutEmpty.visibility = View.GONE
                                tvActivityCountBadge.text = "${synthesized.size} total"
                                adapter.submitList(synthesized)
                            } else {
                                layoutEmpty.visibility = View.VISIBLE
                                tvActivityCountBadge.text = "0 total"
                                adapter.submitList(emptyList())
                            }
                        },
                        onFailure = {
                            progressBar.visibility = View.GONE
                            layoutEmpty.visibility = View.VISIBLE
                        }
                    )
                }
            },
            onFailure = {
                progressBar.visibility = View.GONE
                layoutEmpty.visibility = View.VISIBLE
            }
        )
    }

    private fun synthesizeActivities(persons: List<Person>): List<ActivityRecord> {
        val list = mutableListOf<ActivityRecord>()
        val byId = persons.associateBy { it.id }

        // Member additions
        persons.forEach { p ->
            list.add(
                ActivityRecord(
                    id = "synth_add_${p.id}",
                    treeId = treeId,
                    type = "CREATE",
                    description = "Added family member: ${p.firstName} ${p.lastName}",
                    targetId = p.id,
                    targetName = "${p.firstName} ${p.lastName}",
                    timestamp = p.createdAt.takeIf { it > 0 } ?: (System.currentTimeMillis() - 86400000)
                )
            )
            // Spouse links
            val sId = p.spouseId
            if (!sId.isNullOrEmpty() && byId.containsKey(sId) && p.id < sId) {
                val s = byId[sId]!!
                list.add(
                    ActivityRecord(
                        id = "synth_spouse_${p.id}_${sId}",
                        treeId = treeId,
                        type = "LINK",
                        description = "Linked ${p.firstName} ${p.lastName} and ${s.firstName} ${s.lastName} as spouses",
                        targetId = p.id,
                        targetName = "${p.firstName} ${p.lastName}",
                        timestamp = if (p.createdAt > 0) p.createdAt else System.currentTimeMillis()
                    )
                )
            }
        }
        return list.sortedByDescending { it.timestamp }
    }
}
