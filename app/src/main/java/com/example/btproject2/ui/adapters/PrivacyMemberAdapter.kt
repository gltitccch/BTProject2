package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.models.Person

class PrivacyMemberAdapter(
    private val onToggle: (Person, Boolean) -> Unit
) : RecyclerView.Adapter<PrivacyMemberAdapter.ViewHolder>() {

    private val items = mutableListOf<Person>()

    fun submitList(persons: List<Person>) {
        items.clear()
        items.addAll(persons)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_privacy_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onToggle)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        private val tvName: TextView   = view.findViewById(R.id.tvName)
        private val tvGender: TextView = view.findViewById(R.id.tvGender)
        private val swPrivate: Switch  = view.findViewById(R.id.swPrivate)

        fun bind(person: Person, onToggle: (Person, Boolean) -> Unit) {
            val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}".uppercase()
            tvAvatar.text = initials
            tvAvatar.setBackgroundResource(
                if (person.gender.lowercase() == "female") R.drawable.avatar_bg_female
                else R.drawable.avatar_bg
            )
            tvName.text   = "${person.firstName} ${person.lastName}"
            tvGender.text = person.gender

            // Prevent triggering listener while binding
            swPrivate.setOnCheckedChangeListener(null)
            swPrivate.isChecked = person.isPrivate
            swPrivate.setOnCheckedChangeListener { _, isChecked ->
                onToggle(person, isChecked)
            }
        }
    }
}
