package com.example.btproject2.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.service.FamilyRelationshipService.AuditSeverity
import com.example.btproject2.service.FamilyRelationshipService.TreeAuditIssue

class TreeAuditAdapter(
    private var items: List<TreeAuditIssue>,
    private val onViewPerson: (personId: String) -> Unit
) : RecyclerView.Adapter<TreeAuditAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIssueBadge: TextView = view.findViewById(R.id.tvIssueBadge)
        val tvIssueTitle: TextView = view.findViewById(R.id.tvIssueTitle)
        val tvIssueDescription: TextView = view.findViewById(R.id.tvIssueDescription)
        val tvIssueLegalBasis: TextView = view.findViewById(R.id.tvIssueLegalBasis)
        val layoutFamilyPath: LinearLayout = view.findViewById(R.id.layoutFamilyPath)
        val tvFamilyPathText: TextView = view.findViewById(R.id.tvFamilyPathText)
        val btnViewPerson: TextView = view.findViewById(R.id.btnViewPerson)
    }

    fun updateList(newItems: List<TreeAuditIssue>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audit_issue, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val issue = items[position]

        // 1. Badge Styling
        if (issue.severity == AuditSeverity.CRITICAL_ERROR) {
            holder.tvIssueBadge.text = "CRITICAL ERROR"
            holder.tvIssueBadge.setBackgroundColor(Color.parseColor("#991B1B")) // Crimson Red
            holder.tvIssueBadge.setTextColor(Color.WHITE)
        } else {
            holder.tvIssueBadge.text = "WARNING"
            holder.tvIssueBadge.setBackgroundColor(Color.parseColor("#B45309")) // Amber
            holder.tvIssueBadge.setTextColor(Color.WHITE)
        }

        // 2. Title & Description
        holder.tvIssueTitle.text = issue.title
        holder.tvIssueDescription.text = issue.description

        // 3. Legal Basis
        if (issue.legalBasis.isNotBlank()) {
            holder.tvIssueLegalBasis.visibility = View.VISIBLE
            holder.tvIssueLegalBasis.text = issue.legalBasis
        } else {
            holder.tvIssueLegalBasis.visibility = View.GONE
        }

        // 4. Family Path
        if (issue.familyPath.isNotEmpty()) {
            holder.layoutFamilyPath.visibility = View.VISIBLE
            val pathStr = issue.familyPath.joinToString(" ➔ ") { "${it.firstName} ${it.lastName}".trim() }
            holder.tvFamilyPathText.text = pathStr
        } else {
            holder.layoutFamilyPath.visibility = View.GONE
        }

        // 5. Action Button
        holder.btnViewPerson.text = "View ${issue.primaryPerson.firstName} ➔"
        holder.btnViewPerson.setOnClickListener {
            onViewPerson(issue.primaryPerson.id)
        }
    }

    override fun getItemCount(): Int = items.size
}

