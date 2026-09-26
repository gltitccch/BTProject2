package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.ActivityRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityAdapter(
    private val onItemClick: (ActivityRecord) -> Unit
) : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    private val items = mutableListOf<ActivityRecord>()

    fun submitList(newItems: List<ActivityRecord>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_activity, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon: TextView = itemView.findViewById(R.id.tvRecentIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvRecentTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvRecentSubtitle)
        private val tvBadge: TextView? = itemView.findViewById(R.id.tvRecentGenBadge)
        private val tvTime: TextView? = itemView.findViewById(R.id.tvRecentTime)

        fun bind(activity: ActivityRecord, onItemClick: (ActivityRecord) -> Unit) {
            when (activity.type.uppercase()) {
                "MEMBER_ADDED", "CREATE" -> {
                    tvIcon.text = "➕"
                    tvBadge?.text = "Added"
                }
                "MEMBER_UPDATED", "UPDATE" -> {
                    tvIcon.text = "✏️"
                    tvBadge?.text = "Edited"
                }
                "MEMBER_DELETED", "DELETE" -> {
                    tvIcon.text = "🗑️"
                    tvBadge?.text = "Deleted"
                }
                "RELATIONSHIP_LINKED", "LINK" -> {
                    tvIcon.text = "🔗"
                    tvBadge?.text = "Linked"
                }
                "TRACE_PERFORMED", "TRACE" -> {
                    tvIcon.text = "🧬"
                    tvBadge?.text = "Traced"
                }
                "BRANCH_MERGED", "MERGE" -> {
                    tvIcon.text = "🌿"
                    tvBadge?.text = "Merged"
                }
                "DOCUMENT_ATTACHED", "DOCUMENT" -> {
                    tvIcon.text = "📎"
                    tvBadge?.text = "Document"
                }
                "PHOTO_UPDATED", "PHOTO" -> {
                    tvIcon.text = "📷"
                    tvBadge?.text = "Photo"
                }
                else -> {
                    tvIcon.text = "📜"
                    tvBadge?.text = "Activity"
                }
            }

            tvTitle.text = activity.description
            val dateStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(activity.timestamp))
            val user = activity.userName.ifEmpty { "Family Tree" }
            tvSubtitle.text = "$user · $dateStr"

            val diffMs = System.currentTimeMillis() - activity.timestamp
            val diffMinutes = diffMs / (1000 * 60)
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24
            val timeText = when {
                diffMinutes < 1 -> "Just now"
                diffMinutes < 60 -> "${diffMinutes}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> "${diffDays}d ago"
            }
            tvTime?.text = timeText

            itemView.setOnClickListener {
                onItemClick(activity)
            }
        }
    }
}
