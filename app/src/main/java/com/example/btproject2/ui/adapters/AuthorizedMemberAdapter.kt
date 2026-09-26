package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.TreeMember

class AuthorizedMemberAdapter(
    private val isOwner: Boolean,
    private val onRoleClick: (TreeMember) -> Unit
) : RecyclerView.Adapter<AuthorizedMemberAdapter.ViewHolder>() {

    private val items = mutableListOf<TreeMember>()

    fun submitList(members: List<TreeMember>) {
        items.clear()
        items.addAll(members)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_authorized_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], isOwner, onRoleClick)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAvatar: TextView = view.findViewById(R.id.tvMemberAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvMemberName)
        private val tvEmail: TextView = view.findViewById(R.id.tvMemberEmail)
        private val tvRole: TextView = view.findViewById(R.id.tvMemberRole)

        fun bind(member: TreeMember, isOwner: Boolean, onRoleClick: (TreeMember) -> Unit) {
            val initial = member.userName.firstOrNull()?.uppercase()
                ?: member.userEmail.firstOrNull()?.uppercase() ?: "U"
            tvAvatar.text = initial
            tvName.text = member.userName.ifBlank { member.userEmail.substringBefore("@") }
            tvEmail.text = member.userEmail
            tvRole.text = member.role

            if (isOwner && member.role != "Owner") {
                tvRole.setBackgroundResource(R.drawable.btn_outline_bg)
                tvRole.setOnClickListener { onRoleClick(member) }
            } else {
                tvRole.setBackgroundResource(R.drawable.tag_lineal_bg)
                tvRole.setOnClickListener(null)
            }
        }
    }
}

