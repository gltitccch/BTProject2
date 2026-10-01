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
        data class Header(val generation: Int) : TreeItem()
        data class Member(val person: Person) : TreeItem()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MEMBER = 1
    }

    private val items = mutableListOf<TreeItem>()
    var allMembers: List<Person> = emptyList()
    var focalPerson: Person? = null

    fun submitItems(newItems: List<TreeItem>, members: List<Person> = allMembers, focal: Person? = focalPerson) {
        allMembers = members
        focalPerson = focal
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
            is TreeItem.Header -> (holder as HeaderViewHolder).bind(item.generation)
            is TreeItem.Member -> (holder as MemberViewHolder).bind(item.person, allMembers, focalPerson, onMemberClick)
        }
    }

    // ── ViewHolders ──────────────────────────────────────────────────

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvGenLabel)
        fun bind(generation: Int) {
            tvLabel.text = "GENERATION $generation"
        }
    }

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        private val tvName: TextView   = view.findViewById(R.id.tvName)
        private val tvInfo: TextView   = view.findViewById(R.id.tvInfo)

        fun bind(
            person: Person,
            allMembers: List<Person>,
            focalPerson: Person?,
            onClick: (Person) -> Unit
        ) {
            val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}".uppercase()
            tvAvatar.text = initials

            // Avatar color based on gender and living status
            if (!person.isLiving) {
                tvAvatar.setBackgroundResource(R.drawable.badge_pill_dark)
            } else {
                val avatarBg = if (person.gender.lowercase() == "female")
                    R.drawable.avatar_bg_female else R.drawable.avatar_bg
                tvAvatar.setBackgroundResource(avatarBg)
            }

            val memorialSuffix = if (!person.isLiving) " 🕊️" else ""
            tvName.text = "${person.firstName} ${person.lastName}$memorialSuffix"

            val effectiveFocal = focalPerson ?: allMembers.firstOrNull()
            val kinship = if (effectiveFocal != null && effectiveFocal.id != person.id && allMembers.isNotEmpty()) {
                com.example.btproject2.engine.KinshipTitleHelper.resolveTitle(person, effectiveFocal, allMembers)
            } else if (person.lineageRole.isNotBlank()) {
                person.lineageRole.uppercase()
            } else null
            val kinshipPrefix = if (!kinship.isNullOrEmpty()) "$kinship · " else ""

            tvInfo.text = buildString {
                append(kinshipPrefix)
                append(person.gender)
                if (person.birthDate.isNotEmpty()) append(" · b. ${person.birthDate}")
                if (!person.isLiving) {
                    if (person.deathDate.isNotEmpty()) append(" · d. ${person.deathDate}")
                    append(" (Deceased)")
                }
            }

            itemView.setOnClickListener { onClick(person) }
        }
    }
}
