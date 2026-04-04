package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()
    private val treeId = "default_tree"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val tvProfileInitial = findViewById<TextView>(R.id.tvProfileInitial)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)

        val cardAddMember = findViewById<LinearLayout>(R.id.cardAddMember)
        val cardViewTree = findViewById<LinearLayout>(R.id.cardViewTree)
        val cardTrace = findViewById<LinearLayout>(R.id.cardTrace)
        val cardRecords = findViewById<LinearLayout>(R.id.cardRecords)
        val cardMerge = findViewById<LinearLayout>(R.id.cardMerge)
        val btnLogout = findViewById<TextView>(R.id.btnLogout)

        // Set greeting based on time of day
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvGreeting.text = when {
            hour < 12 -> "Good morning,"
            hour < 18 -> "Good afternoon,"
            else -> "Good evening,"
        }

        // Set user name and profile initial
        val email = authHelper.getCurrentUser()?.email ?: "User"
        val displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        tvUserName.text = displayName
        tvProfileInitial.text = displayName.first().uppercase()

        // Load stats
        loadStats(tvMemberCount)

        // Card click actions
        cardAddMember.setOnClickListener {
            startActivity(Intent(this, AddMemberActivity::class.java))
        }

        cardTrace.setOnClickListener {
            startActivity(Intent(this, TraceActivity::class.java))
        }

        cardViewTree.setOnClickListener {
            showMessage("Coming Soon", "Family Tree visualization is under development.")
        }

        cardRecords.setOnClickListener {
            startActivity(Intent(this, FamilyRecordsActivity::class.java))

        }

        cardMerge.setOnClickListener {
            showMessage("Coming Soon", "Branch merging is under development.")
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of KinTrace?")
                .setPositiveButton("Yes") { _, _ ->
                    authHelper.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        loadStats(tvMemberCount)
    }

    private fun loadStats(tvMemberCount: TextView) {
        firestoreHelper.getPersonsByTree(treeId,
            onSuccess = { persons ->
                tvMemberCount.text = persons.size.toString()
            },
            onFailure = {
                tvMemberCount.text = "0"
            }
        )
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}