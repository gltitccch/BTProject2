package com.example.btproject2.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.Person

class MemberAdapter(
    private var members: List<Person>,
    private val onItemClick: (Person) -> Unit,
    private val onEditClick: (Person) -> Unit,
    private val onDeleteClick: (Person) -> Unit = {},
    private val onAddChildClick: (Person) -> Unit = {}
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    private val avatarColors = listOf(
        "#1D9E75", "#185FA5", "#BA7517", "#993556",
        "#7F77DD", "#D85A30", "#0F6E56", "#534AB7"
    )
    private val parsedAvatarColors = avatarColors.map { Color.parseColor(it) }

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
        val tvMemberInfo: TextView = view.findViewById(R.id.tvMemberInfo)
        val btnAddChild: TextView = view.findViewById(R.id.btnAddChild)
        val btnEdit: TextView = view.findViewById(R.id.btnEdit)
        val btnDelete: TextView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_family_member, parent, false)
        return MemberViewHolder(view)
    }

    var allMembers: List<Person> = emptyList()
    var focalPerson: Person? = null

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val person = members[position]

        // Set initials
        val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}"
        holder.tvAvatar.text = initials.uppercase()

        // Set avatar color and name based on living status
        if (!person.isLiving) {
            holder.tvAvatar.background?.setTint(android.graphics.Color.parseColor("#475569"))
            holder.tvMemberName.text = "${person.firstName} ${person.lastName} 🕊️"
        } else {
            val colorIndex = position % parsedAvatarColors.size
            holder.tvAvatar.background?.setTint(parsedAvatarColors[colorIndex])
            holder.tvMemberName.text = "${person.firstName} ${person.lastName}"
        }

        val effectiveFocal = focalPerson ?: allMembers.firstOrNull() ?: members.firstOrNull()
        val kinship = if (effectiveFocal != null && effectiveFocal.id != person.id && allMembers.isNotEmpty()) {
            com.example.btproject2.engine.KinshipTitleHelper.resolveTitle(person, effectiveFocal, allMembers)
        } else if (person.lineageRole.isNotBlank() && !person.lineageRole.equals("MEMBER", ignoreCase = true)) {
            person.lineageRole.uppercase()
        } else null
        val kinshipPrefix = if (!kinship.isNullOrEmpty()) "$kinship · " else ""

        // Set info
        val info = buildString {
            append(kinshipPrefix)
            append(person.gender)
            if (person.birthDate.isNotEmpty()) {
                append(" · ")
                append(person.birthDate)
            }
            if (!person.isLiving) {
                append(" · 🕊️ Deceased")
                if (person.deathDate.isNotEmpty()) {
                    append(" (d. ${person.deathDate})")
                }
            }
        }
        holder.tvMemberInfo.text = info

        // Row opens member detail
        holder.itemView.setOnClickListener { onItemClick(person) }

        // Add child directly opens add activity with parent preset
        holder.btnAddChild.setOnClickListener { onAddChildClick(person) }

        // Edit button opens edit activity
        holder.btnEdit.setOnClickListener { onEditClick(person) }

        // Delete click
        holder.btnDelete.setOnClickListener { onDeleteClick(person) }
    }

    override fun getItemCount() = members.size

    fun updateList(newMembers: List<Person>, all: List<Person> = allMembers, focal: Person? = focalPerson) {
        members = newMembers
        if (all.isNotEmpty()) allMembers = all
        focalPerson = focal
        notifyDataSetChanged()
    }
}