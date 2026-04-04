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
    private val onEditClick: (Person) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    private val avatarColors = listOf(
        "#1D9E75", "#185FA5", "#BA7517", "#993556",
        "#7F77DD", "#D85A30", "#0F6E56", "#534AB7"
    )

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvMemberName: TextView = view.findViewById(R.id.tvMemberName)
        val tvMemberInfo: TextView = view.findViewById(R.id.tvMemberInfo)
        val btnEdit: TextView = view.findViewById(R.id.btnEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_family_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val person = members[position]

        // Set initials
        val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}"
        holder.tvAvatar.text = initials.uppercase()

        // Set avatar color based on position
        val colorIndex = position % avatarColors.size
        holder.tvAvatar.background.setTint(Color.parseColor(avatarColors[colorIndex]))

        // Set name
        holder.tvMemberName.text = "${person.firstName} ${person.lastName}"

        // Set info
        val info = buildString {
            append(person.gender)
            if (person.birthDate.isNotEmpty()) {
                append(" · ")
                append(person.birthDate)
            }
        }
        holder.tvMemberInfo.text = info

        // Edit click
        holder.btnEdit.setOnClickListener {
            onEditClick(person)
        }
    }

    override fun getItemCount() = members.size

    fun updateList(newMembers: List<Person>) {
        members = newMembers
        notifyDataSetChanged()
    }
}