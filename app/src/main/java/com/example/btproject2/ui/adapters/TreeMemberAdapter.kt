package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.Person

class TreeMemberAdapter(
    private val onMemberClick: (Person) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class TreeItem {
        data class Header(val generation: Int, val count: Int = 0) : TreeItem()
        data class Member(val person: Person) : TreeItem()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MEMBER = 1
    }

    private val items = mutableListOf<TreeItem>()
    var allMembers: List<Person> = emptyList()
    var focalPerson: Person? = null
    private var cachedTitles: Map<String, String> = emptyMap()

    fun submitItems(newItems: List<TreeItem>, members: List<Person> = allMembers, focal: Person? = focalPerson) {
        allMembers = members
        focalPerson = focal
        val effectiveFocal = focal ?: members.firstOrNull()
        cachedTitles = if (members.isNotEmpty() && effectiveFocal != null) {
            com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(members, effectiveFocal)
        } else {
            emptyMap()
        }
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is TreeItem.Header -> TYPE_HEADER
        is TreeItem.Member -> TYPE_MEMBER
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_tree_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_tree_member, parent, false)
            MemberViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TreeItem.Header -> (holder as HeaderViewHolder).bind(item.generation, item.count)
            is TreeItem.Member -> (holder as MemberViewHolder).bind(item.person, allMembers, focalPerson, cachedTitles, onMemberClick)
        }
    }

    // ── ViewHolders ──────────────────────────────────────────────────

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvGenLabel)
        private val tvCount: TextView? = view.findViewById(R.id.tvGenMemberCount)
        fun bind(generation: Int, count: Int = 0) {
            tvLabel.text = "GENERATION $generation"
            tvCount?.text = if (count > 0) "($count ${if (count == 1) "member" else "members"})" else ""
        }
    }

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        private val tvName: TextView   = view.findViewById(R.id.tvName)
        private val tvInfo: TextView   = view.findViewById(R.id.tvInfo)
        private val tvKinshipPill: TextView? = view.findViewById(R.id.tvKinshipPill)

        fun bind(
            person: Person,
            allMembers: List<Person>,
            focalPerson: Person?,
            cachedTitles: Map<String, String> = emptyMap(),
            onClick: (Person) -> Unit
        ) {
            val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}".uppercase()
            tvAvatar.text = initials

            // Avatar color based on gender and living status
            if (!person.isLiving) {
                tvAvatar.setBackgroundResource(R.drawable.avatar_circle_dark)
                tvAvatar.setTextColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.text_secondary))
            } else {
                val avatarBg = if (person.gender.lowercase() == "female")
                    R.drawable.avatar_bg_female else R.drawable.avatar_bg
                tvAvatar.setBackgroundResource(avatarBg)
                tvAvatar.setTextColor(android.graphics.Color.WHITE)
            }

            tvName.text = "${person.firstName} ${person.lastName}".trim()

            val effectiveFocal = focalPerson ?: allMembers.firstOrNull()
            val kinship = if (effectiveFocal != null && effectiveFocal.id != person.id && allMembers.isNotEmpty()) {
                cachedTitles[person.id] ?: com.example.btproject2.engine.KinshipTitleHelper.resolveTitle(person, effectiveFocal, allMembers)
            } else if (person.lineageRole.isNotBlank() && !person.lineageRole.equals("MEMBER", ignoreCase = true)) {
                person.lineageRole
            } else if (effectiveFocal?.id == person.id) {
                "Root"
            } else null

            if (tvKinshipPill != null) {
                val roleLabel = kinship?.takeIf { it.isNotBlank() } ?: if (person.fatherId.isNullOrBlank() && person.motherId.isNullOrBlank()) "Founder" else "Relative"
                tvKinshipPill.text = roleLabel
                tvKinshipPill.visibility = View.VISIBLE
            }

            tvInfo.text = buildString {
                append(person.gender)
                if (person.birthDate.isNotEmpty()) append(" · b. ${person.birthDate}")
                if (!person.isLiving) {
                    if (person.deathDate.isNotEmpty()) append(" · d. ${person.deathDate}")
                    append(" · Deceased")
                } else {
                    append(" · Living")
                }
            }

            itemView.setOnClickListener { onClick(person) }
        }
    }
}
