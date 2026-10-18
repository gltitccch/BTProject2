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
    private val onAddChildClick: (Person) -> Unit = {},
    var isViewer: Boolean = false,
    var treePrivacy: com.example.btproject2.models.PrivacySettings = com.example.btproject2.models.PrivacySettings()
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    private val avatarColors = listOf(
        "#1D9E75", "#185FA5", "#BA7517", "#993556",
        "#7F77DD", "#D85A30", "#0F6E56", "#534AB7"
    )
    private val parsedAvatarColors by lazy {
        avatarColors.map {
            try {
                Color.parseColor(it)
            } catch (_: Throwable) {
                0
            }
        }
    }

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
    private var cachedKinshipTitles: Map<String, String> = emptyMap()
    private var lastTitleFocalId: String? = null
    private var lastTitleMembersRef: List<Person>? = null

    private fun getPrecomputedKinshipTitle(person: Person, effectiveFocal: Person?): String? {
        if (effectiveFocal != null && effectiveFocal.id != person.id && allMembers.isNotEmpty()) {
            if (lastTitleFocalId != effectiveFocal.id || lastTitleMembersRef !== allMembers) {
                cachedKinshipTitles = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(
                    allMembers,
                    effectiveFocal
                )
                lastTitleFocalId = effectiveFocal.id
                lastTitleMembersRef = allMembers
            }
            return cachedKinshipTitles[person.id]
        } else if (person.lineageRole.isNotBlank() && !person.lineageRole.equals("MEMBER", ignoreCase = true)) {
            return person.lineageRole.uppercase()
        }
        return null
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val person = members[position]

        // Set initials
        val initials = "${person.firstName.firstOrNull() ?: ""}${person.lastName.firstOrNull() ?: ""}"
        holder.tvAvatar.text = initials.uppercase()

        val shouldHideDeceased = isViewer && treePrivacy.hideDeceasedStatus
        val shouldHideBirth = isViewer && treePrivacy.hideBirthDates

        // Set avatar color and name based on living status
        if (!person.isLiving && !shouldHideDeceased) {
            holder.tvAvatar.background?.setTint(android.graphics.Color.parseColor("#475569"))
            holder.tvMemberName.text = "${person.firstName} ${person.lastName} 🕊️"
        } else {
            val colorIndex = position % parsedAvatarColors.size
            holder.tvAvatar.background?.setTint(parsedAvatarColors[colorIndex])
            holder.tvMemberName.text = "${person.firstName} ${person.lastName}"
        }

        val effectiveFocal = focalPerson ?: allMembers.firstOrNull() ?: members.firstOrNull()
        val kinship = getPrecomputedKinshipTitle(person, effectiveFocal)
        val kinshipPrefix = if (!kinship.isNullOrEmpty()) "$kinship · " else ""

        // Set info with Direction 1 Subtle Shield privacy masking
        val info = buildString {
            append(kinshipPrefix)
            append(person.gender)
            if (shouldHideBirth) {
                append(" · 🔒 Hidden by Owner")
            } else if (person.birthDate.isNotEmpty()) {
                append(" · ")
                append(person.birthDate)
            }
            if (!person.isLiving && !shouldHideDeceased) {
                append(" · 🕊️ Deceased")
                if (person.deathDate.isNotEmpty() && !shouldHideBirth) {
                    append(" (d. ${person.deathDate})")
                }
            }
        }
        holder.tvMemberInfo.text = info

        // Role-gate action buttons for Viewers
        if (isViewer) {
            holder.btnAddChild.visibility = View.GONE
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnAddChild.visibility = View.VISIBLE
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        }

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

    fun updateList(
        newMembers: List<Person>,
        all: List<Person> = allMembers,
        focal: Person? = focalPerson,
        viewerMode: Boolean = isViewer,
        privacy: com.example.btproject2.models.PrivacySettings = treePrivacy
    ) {
        members = newMembers
        if (all.isNotEmpty()) allMembers = all
        focalPerson = focal
        isViewer = viewerMode
        treePrivacy = privacy
        val effectiveFocal = focalPerson ?: allMembers.firstOrNull() ?: members.firstOrNull()
        if (effectiveFocal != null && allMembers.isNotEmpty()) {
            cachedKinshipTitles = com.example.btproject2.engine.KinshipTitleHelper.resolveAllTitles(
                allMembers,
                effectiveFocal
            )
            lastTitleFocalId = effectiveFocal.id
            lastTitleMembersRef = allMembers
        } else {
            cachedKinshipTitles = emptyMap()
            lastTitleFocalId = null
            lastTitleMembersRef = null
        }
        notifyDataSetChanged()
    }
}