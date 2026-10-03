package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.NotificationRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var items: List<NotificationRecord>,
    private val onItemClick: (NotificationRecord) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvNotifIcon)
        val tvCategory: TextView = view.findViewById(R.id.tvNotifCategory)
        val tvTime: TextView = view.findViewById(R.id.tvNotifTime)
        val tvTitle: TextView = view.findViewById(R.id.tvNotifTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvNotifMessage)
        val viewUnreadDot: View = view.findViewById(R.id.viewUnreadDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Category icon & label
        when (item.type.uppercase()) {
            "ACCESS" -> {
                holder.tvIcon.text = "🔑"
                holder.tvCategory.text = "ACCESS"
            }
            "SECURITY" -> {
                holder.tvIcon.text = "🛡️"
                holder.tvCategory.text = "SECURITY"
            }
            "VALIDATION" -> {
                holder.tvIcon.text = "⚠️"
                holder.tvCategory.text = "VALIDATION"
            }
            "EDUCATIONAL" -> {
                holder.tvIcon.text = "🎓"
                holder.tvCategory.text = "ACADEMY"
            }
            else -> {
                holder.tvIcon.text = "📜"
                holder.tvCategory.text = "RECORDS"
            }
        }

        holder.tvTitle.text = item.title
        holder.tvMessage.text = item.message
        holder.tvTime.text = formatTimestamp(item.timestamp)
        holder.viewUnreadDot.visibility = if (item.isRead) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<NotificationRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timeMs: Long): String {
        if (timeMs <= 0) return "Recently"
        val diff = System.currentTimeMillis() - timeMs
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timeMs))
        }
    }
}

