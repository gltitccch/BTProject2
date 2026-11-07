package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.TreeMember

class AuthorizedMemberAdapter(
    private val isOwner: Boolean,
    private val currentUserId: String = "",
    private val onSetRole: ((TreeMember, String) -> Unit)? = null,
    private val onRemoveMember: ((TreeMember) -> Unit)? = null,
    private val onRoleClick: (TreeMember) -> Unit = {}
) : RecyclerView.Adapter<AuthorizedMemberAdapter.ViewHolder>() {

    private val items = mutableListOf<TreeMember>()
    private var expandedMemberKey: String? = null

    fun submitList(members: List<TreeMember>) {
        items.clear()
        items.addAll(members)
        if (expandedMemberKey != null && items.none { memberKey(it) == expandedMemberKey }) {
            expandedMemberKey = null
        }
        notifyDataSetChanged()
    }

    private fun memberKey(member: TreeMember): String {
        return member.id.ifBlank { "${member.treeId}_${member.userId}_${member.userEmail}" }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_authorized_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = items[position]
        val key = memberKey(member)
        val isExpanded = (expandedMemberKey == key)
        holder.bind(
            member = member,
            isOwner = isOwner,
            currentUserId = currentUserId,
            isExpanded = isExpanded,
            onToggleExpand = {
                expandedMemberKey = if (expandedMemberKey == key) null else key
                notifyDataSetChanged()
            },
            onSetRole = { targetMember, newRole ->
                expandedMemberKey = null
                notifyDataSetChanged()
                if (onSetRole != null) {
                    onSetRole.invoke(targetMember, newRole)
                } else {
                    onRoleClick(targetMember)
                }
            },
            onRemoveMember = { targetMember ->
                expandedMemberKey = null
                notifyDataSetChanged()
                if (onRemoveMember != null) {
                    onRemoveMember.invoke(targetMember)
                } else {
                    onRoleClick(targetMember)
                }
            }
        )
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val rowMemberHeader: View? = view.findViewById(R.id.rowMemberHeader)
        private val tvAvatar: TextView = view.findViewById(R.id.tvMemberAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvMemberName)
        private val tvEmail: TextView = view.findViewById(R.id.tvMemberEmail)
        private val tvRole: TextView = view.findViewById(R.id.tvMemberRole)
        private val layoutInlineMemberActions: View? = view.findViewById(R.id.layoutInlineMemberActions)
        private val btnInlineSetEditor: Button? = view.findViewById(R.id.btnInlineSetEditor)
        private val btnInlineSetViewer: Button? = view.findViewById(R.id.btnInlineSetViewer)
        private val btnInlineRemoveMember: Button? = view.findViewById(R.id.btnInlineRemoveMember)

        fun bind(
            member: TreeMember,
            isOwner: Boolean,
            currentUserId: String,
            isExpanded: Boolean,
            onToggleExpand: () -> Unit,
            onSetRole: (TreeMember, String) -> Unit,
            onRemoveMember: (TreeMember) -> Unit
        ) {
            val context = itemView.context
            val initial = member.userName.firstOrNull()?.uppercase()
                ?: member.userEmail.firstOrNull()?.uppercase() ?: "U"
            tvAvatar.text = initial
            tvName.text = member.userName.ifBlank { member.userEmail.substringBefore("@") }
            tvEmail.text = member.userEmail

            val isSelfOwnerRow = member.role.equals("Owner", ignoreCase = true) &&
                (currentUserId.isBlank() || member.userId.isBlank() || member.userId == currentUserId)
            val canManageMember = isOwner && !isSelfOwnerRow

            val roleBadgeLabel = when {
                isSelfOwnerRow -> "🌳 Owner"
                member.role.equals("Editor", ignoreCase = true) -> if (isExpanded) "🌿 Editor ▴" else "🌿 Editor ▾"
                member.role.equals("Viewer", ignoreCase = true) -> if (isExpanded) "🌱 Viewer ▴" else "🌱 Viewer ▾"
                else -> if (isExpanded) "${member.role} ▴" else "${member.role} ▾"
            }
            tvRole.text = roleBadgeLabel

            if (canManageMember) {
                tvRole.setBackgroundResource(R.drawable.btn_outline_bg)
                val toggleClick = View.OnClickListener { onToggleExpand() }
                tvRole.setOnClickListener(toggleClick)
                rowMemberHeader?.setOnClickListener(toggleClick)
                itemView.setOnClickListener(toggleClick)

                layoutInlineMemberActions?.visibility = if (isExpanded) View.VISIBLE else View.GONE

                val isCurrentlyEditor = member.role.equals("Editor", ignoreCase = true)
                val isCurrentlyViewer = member.role.equals("Viewer", ignoreCase = true)

                btnInlineSetEditor?.apply {
                    text = if (isCurrentlyEditor) "✓ Editor" else "🌿 Set Editor"
                    setBackgroundResource(if (isCurrentlyEditor) R.drawable.btn_primary_bg else R.drawable.btn_outline_bg)
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (isCurrentlyEditor) R.color.btn_gold_text else R.color.text_primary
                        )
                    )
                    setOnClickListener {
                        if (!isCurrentlyEditor) onSetRole(member, "Editor") else onToggleExpand()
                    }
                }

                btnInlineSetViewer?.apply {
                    text = if (isCurrentlyViewer) "✓ Viewer" else "🌱 Set Viewer"
                    setBackgroundResource(if (isCurrentlyViewer) R.drawable.btn_primary_bg else R.drawable.btn_outline_bg)
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (isCurrentlyViewer) R.color.btn_gold_text else R.color.text_primary
                        )
                    )
                    setOnClickListener {
                        if (!isCurrentlyViewer) onSetRole(member, "Viewer") else onToggleExpand()
                    }
                }

                btnInlineRemoveMember?.setOnClickListener {
                    onRemoveMember(member)
                }
            } else {
                tvRole.setBackgroundResource(R.drawable.tag_lineal_bg)
                tvRole.setOnClickListener(null)
                rowMemberHeader?.setOnClickListener(null)
                itemView.setOnClickListener(null)
                layoutInlineMemberActions?.visibility = View.GONE
            }
        }
    }
}

