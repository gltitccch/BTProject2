package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.TreeMember

class PendingRequestAdapter(
    private val onApprove: (TreeMember) -> Unit,
    private val onReject: (TreeMember) -> Unit
) : RecyclerView.Adapter<PendingRequestAdapter.ViewHolder>() {

    private val items = mutableListOf<TreeMember>()

    fun submitList(members: List<TreeMember>) {
        items.clear()
        items.addAll(members)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onApprove, onReject)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAvatar: TextView = view.findViewById(R.id.tvPendingAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvPendingName)
        private val tvEmail: TextView = view.findViewById(R.id.tvPendingEmail)
        private val btnApprove: Button = view.findViewById(R.id.btnApprove)
        private val btnReject: Button = view.findViewById(R.id.btnReject)

        fun bind(
            member: TreeMember,
            onApprove: (TreeMember) -> Unit,
            onReject: (TreeMember) -> Unit
        ) {
            val initial = member.userName.firstOrNull()?.uppercase()
                ?: member.userEmail.firstOrNull()?.uppercase() ?: "U"
            tvAvatar.text = initial
            tvName.text = member.userName.ifBlank { member.userEmail.substringBefore("@") }
            tvEmail.text = member.userEmail

            btnApprove.setOnClickListener { onApprove(member) }
            btnReject.setOnClickListener { onReject(member) }
        }
    }
}

