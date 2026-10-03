package com.example.btproject2.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.google.android.material.card.MaterialCardView

/**
 * Adapter for the 3-slide Welcome Storybook carousel (Phase 1 Onboarding).
 */
class WelcomeStoryAdapter(
    private val onPlantTreeClicked: () -> Unit,
    private val onJoinClanClicked: () -> Unit,
    private val onExploreFirstClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_STANDARD = 0
        const val VIEW_TYPE_FORK = 1
    }

    override fun getItemCount(): Int = 3

    override fun getItemViewType(position: Int): Int {
        return if (position == 1) VIEW_TYPE_FORK else VIEW_TYPE_STANDARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FORK) {
            val view = inflater.inflate(R.layout.item_welcome_slide_fork, parent, false)
            ForkViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_welcome_slide, parent, false)
            StandardViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is StandardViewHolder -> {
                if (position == 0) {
                    holder.tvEmoji.text = "🌳"
                    holder.tvTitle.text = "Welcome to KinTrace"
                    holder.tvTagline.text = "\"Ang hindi lumingon sa pinanggalingan, hindi makakarating sa paroroonan.\""

                    holder.tvBullet1Icon.text = "🌿"
                    holder.tvBullet1Title.text = "Direct Ancestral Lineage"
                    holder.tvBullet1Desc.text = "Document and honor your family roots across generations with permanent cloud preservation."

                    holder.tvBullet2Icon.text = "⚖️"
                    holder.tvBullet2Title.text = "Philippine Legal Compliance"
                    holder.tvBullet2Desc.text = "Automated consanguinity degree calculation (1° to 6°) and marriage validation under the Family Code."

                    holder.tvBullet3Icon.text = "🛡️"
                    holder.tvBullet3Title.text = "Zero-Risk Data Integrity"
                    holder.tvBullet3Desc.text = "Strict cycle-prevention and biological rules protect your tree from invalid connections."
                } else if (position == 2) {
                    holder.tvEmoji.text = "🗺️"
                    holder.tvTitle.text = "Smart Relational Intelligence"
                    holder.tvTagline.text = "Watch your family connections come to life automatically."

                    holder.tvBullet1Icon.text = "📊"
                    holder.tvBullet1Title.text = "Interactive Living Canvas"
                    holder.tvBullet1Desc.text = "Pan, zoom, and inspect generations with dynamic relationship vectors and kinship badges."

                    holder.tvBullet2Icon.text = "🧬"
                    holder.tvBullet2Title.text = "Automated Kinship Tracing"
                    holder.tvBullet2Desc.text = "Trace paths between any two relatives: cousins, uncles, aunts, and in-laws identified instantly."

                    holder.tvBullet3Icon.text = "🌱"
                    holder.tvBullet3Title.text = "Clan Builder Quest"
                    holder.tvBullet3Desc.text = "Follow your home checklist to establish roots, add relatives, and share your unique 6-character clan code."
                }
            }
            is ForkViewHolder -> {
                holder.cardPlantTree.setOnClickListener { onPlantTreeClicked() }
                holder.cardJoinClan.setOnClickListener { onJoinClanClicked() }
                holder.tvExploreFirst.setOnClickListener { onExploreFirstClicked() }
            }
        }
    }

    class StandardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEmoji: TextView = itemView.findViewById(R.id.tvSlideEmoji)
        val tvTitle: TextView = itemView.findViewById(R.id.tvSlideTitle)
        val tvTagline: TextView = itemView.findViewById(R.id.tvSlideTagline)
        val tvBullet1Icon: TextView = itemView.findViewById(R.id.tvBullet1Icon)
        val tvBullet1Title: TextView = itemView.findViewById(R.id.tvBullet1Title)
        val tvBullet1Desc: TextView = itemView.findViewById(R.id.tvBullet1Desc)
        val tvBullet2Icon: TextView = itemView.findViewById(R.id.tvBullet2Icon)
        val tvBullet2Title: TextView = itemView.findViewById(R.id.tvBullet2Title)
        val tvBullet2Desc: TextView = itemView.findViewById(R.id.tvBullet2Desc)
        val tvBullet3Icon: TextView = itemView.findViewById(R.id.tvBullet3Icon)
        val tvBullet3Title: TextView = itemView.findViewById(R.id.tvBullet3Title)
        val tvBullet3Desc: TextView = itemView.findViewById(R.id.tvBullet3Desc)
    }

    class ForkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardPlantTree: MaterialCardView = itemView.findViewById(R.id.cardPlantTree)
        val cardJoinClan: MaterialCardView = itemView.findViewById(R.id.cardJoinClan)
        val tvExploreFirst: TextView = itemView.findViewById(R.id.tvExploreFirst)
    }
}
