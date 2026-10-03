package com.example.btproject2.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.education.EducationalContentRepository.KamagAnakTerm

class KamagAnakAdapter(
    private var terms: List<KamagAnakTerm>
) : RecyclerView.Adapter<KamagAnakAdapter.TermViewHolder>() {

    fun updateTerms(newTerms: List<KamagAnakTerm>) {
        this.terms = newTerms
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TermViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kamag_anak_term, parent, false)
        return TermViewHolder(view)
    }

    override fun onBindViewHolder(holder: TermViewHolder, position: Int) {
        holder.bind(terms[position])
    }

    override fun getItemCount(): Int = terms.size

    class TermViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTagalog: TextView = itemView.findViewById(R.id.tvTermTagalog)
        private val tvEnglish: TextView = itemView.findViewById(R.id.tvTermEnglish)
        private val tvCivilDegree: TextView = itemView.findViewById(R.id.tvTermCivilDegree)
        private val tvLegal: TextView = itemView.findViewById(R.id.tvTermLegal)
        private val tvDefinition: TextView = itemView.findViewById(R.id.tvTermDefinition)
        private val tvExample: TextView = itemView.findViewById(R.id.tvTermExample)

        fun bind(term: KamagAnakTerm) {
            tvTagalog.text = term.tagalogTerm
            tvEnglish.text = term.englishTranslation
            tvCivilDegree.text = term.civilDegree
            tvLegal.text = term.legalClassification
            tvDefinition.text = term.definition
            tvExample.text = term.practicalExample
        }
    }
}
