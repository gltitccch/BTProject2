package com.example.btproject2.ui.adapters

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.education.EducationalContentRepository.KinAcademyModule

class KinAcademyModuleAdapter(
    private var modules: List<KinAcademyModule>,
    private var initiallyExpandedModuleId: Int = -1,
    private val onActionClick: (targetActivity: String) -> Unit
) : RecyclerView.Adapter<KinAcademyModuleAdapter.ModuleViewHolder>() {

    private val expandedModuleIds = mutableSetOf<Int>()

    init {
        if (initiallyExpandedModuleId > 0) {
            expandedModuleIds.add(initiallyExpandedModuleId)
        }
    }

    fun updateModules(newModules: List<KinAcademyModule>) {
        this.modules = newModules
        notifyDataSetChanged()
    }

    fun expandModule(moduleId: Int) {
        expandedModuleIds.add(moduleId)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kin_academy_module, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val module = modules[position]
        holder.bind(module, expandedModuleIds.contains(module.id))
    }

    override fun getItemCount(): Int = modules.size

    inner class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvModuleEmoji)
        private val tvNumber: TextView = itemView.findViewById(R.id.tvModuleNumber)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvModuleCategoryBadge)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvModuleTitle)
        private val tvTagline: TextView = itemView.findViewById(R.id.tvModuleTagline)
        private val tvChevron: TextView = itemView.findViewById(R.id.tvChevronIndicator)
        private val layoutHeader: View = itemView.findViewById(R.id.layoutModuleHeader)
        private val layoutBody: View = itemView.findViewById(R.id.layoutExpandableBody)
        private val tvSummary: TextView = itemView.findViewById(R.id.tvModuleSummary)
        private val layoutSections: LinearLayout = itemView.findViewById(R.id.layoutSectionsContainer)
        private val btnAction: TextView = itemView.findViewById(R.id.btnModuleActionShortcut)

        fun bind(module: KinAcademyModule, isExpanded: Boolean) {
            val context = itemView.context
            tvEmoji.text = module.iconEmoji
            tvNumber.text = "MODULE %02d".format(module.id)
            tvCategory.text = module.category.uppercase()
            tvTitle.text = module.title
            tvTagline.text = module.tagline
            tvSummary.text = module.summary

            if (isExpanded) {
                layoutBody.visibility = View.VISIBLE
                tvChevron.text = "▲"
            } else {
                layoutBody.visibility = View.GONE
                tvChevron.text = "▼"
            }

            val toggleExpand = View.OnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    if (expandedModuleIds.contains(module.id)) {
                        expandedModuleIds.remove(module.id)
                    } else {
                        expandedModuleIds.add(module.id)
                    }
                    notifyItemChanged(pos)
                }
            }
            layoutHeader.setOnClickListener(toggleExpand)
            itemView.setOnClickListener(toggleExpand)

            // Populate Detailed Sections
            layoutSections.removeAllViews()
            val textPrimary = ContextCompat.getColor(context, R.color.text_primary)
            val goldColor = ContextCompat.getColor(context, R.color.gold_accent)

            for (section in module.detailedSections) {
                // Section Heading
                val headingView = TextView(context).apply {
                    text = section.heading
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(goldColor)
                    setPadding(0, dpToPx(context, 8), 0, dpToPx(context, 4))
                }
                layoutSections.addView(headingView)

                // Paragraphs
                for (paragraph in section.paragraphs) {
                    val pView = TextView(context).apply {
                        text = paragraph
                        textSize = 12.5f
                        setTextColor(textPrimary)
                        setLineSpacing(dpToPx(context, 2).toFloat(), 1.0f)
                        setPadding(0, 0, 0, dpToPx(context, 6))
                    }
                    layoutSections.addView(pView)
                }

                // Bullets
                for (bullet in section.bulletPoints) {
                    val bulletRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 0, 0, dpToPx(context, 4))
                    }
                    val dot = TextView(context).apply {
                        text = "• "
                        textSize = 13f
                        setTextColor(goldColor)
                    }
                    val bulletText = TextView(context).apply {
                        text = bullet
                        textSize = 12f
                        setTextColor(textPrimary)
                        setLineSpacing(dpToPx(context, 2).toFloat(), 1.0f)
                    }
                    bulletRow.addView(dot)
                    bulletRow.addView(bulletText)
                    layoutSections.addView(bulletRow)
                }
            }

            // Action Shortcut Button
            if (!module.deepLinkTarget.isNullOrBlank() && !module.deepLinkActionText.isNullOrBlank()) {
                btnAction.visibility = View.VISIBLE
                btnAction.text = "${module.deepLinkActionText} →"
                btnAction.setOnClickListener {
                    onActionClick(module.deepLinkTarget)
                }
            } else {
                btnAction.visibility = View.GONE
            }
        }

        private fun dpToPx(context: Context, dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }
    }
}
