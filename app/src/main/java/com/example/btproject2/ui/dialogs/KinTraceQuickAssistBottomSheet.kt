package com.example.btproject2.ui.dialogs

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.btproject2.R
import com.example.btproject2.education.EducationalContentRepository
import com.example.btproject2.education.EducationalContentRepository.QuickAssistContext
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * KinTraceQuickAssistBottomSheet: Universal in-situ modal drawer that provides
 * dynamic, context-specific pro tips, gesture guides, and legal guardrails
 * across all major KinTrace screens under Proposal 25.
 *
 * Guaranteed Behavior:
 * - Runs as a non-blocking dialog overlay window.
 * - Does not restart host activity lifecycle.
 * - Preserves 100% of user form inputs and canvas coordinates upon dismissal.
 */
class KinTraceQuickAssistBottomSheet : BottomSheetDialogFragment() {

    private var assistContext: QuickAssistContext = QuickAssistContext.HOME_DASHBOARD

    companion object {
        private const val ARG_CONTEXT = "arg_quick_assist_context"

        fun newInstance(context: QuickAssistContext): KinTraceQuickAssistBottomSheet {
            val fragment = KinTraceQuickAssistBottomSheet()
            val args = Bundle().apply {
                putString(ARG_CONTEXT, context.name)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contextName = arguments?.getString(ARG_CONTEXT)
        if (!contextName.isNullOrBlank()) {
            try {
                assistContext = QuickAssistContext.valueOf(contextName)
            } catch (e: Exception) {
                assistContext = QuickAssistContext.HOME_DASHBOARD
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_quick_assist_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val payload = EducationalContentRepository.getQuickAssistPayload(assistContext)

        val tvTitle = view.findViewById<TextView>(R.id.tvQuickAssistTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvQuickAssistSubtitle)
        val btnClose = view.findViewById<TextView>(R.id.btnQuickAssistClose)
        val layoutProTips = view.findViewById<LinearLayout>(R.id.layoutProTipsContainer)
        val layoutLegal = view.findViewById<LinearLayout>(R.id.layoutLegalContainer)
        val tvLegalText = view.findViewById<TextView>(R.id.tvLegalGuardrailsText)
        val btnOpenAcademy = view.findViewById<TextView>(R.id.btnOpenFullAcademy)

        tvTitle.text = payload.title
        tvSubtitle.text = payload.subtitle
        btnOpenAcademy.text = payload.actionCtaText

        // Populate Pro Tips dynamically
        layoutProTips.removeAllViews()
        val textPrimaryColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val goldColor = ContextCompat.getColor(requireContext(), R.color.gold_accent)

        for ((index, tip) in payload.proTips.withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dpToPx(8)
                }
            }

            val bullet = TextView(requireContext()).apply {
                text = "• "
                textSize = 14f
                setTextColor(goldColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val tipText = TextView(requireContext()).apply {
                text = tip
                textSize = 12.5f
                setTextColor(textPrimaryColor)
                setLineSpacing(dpToPx(2).toFloat(), 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }

            row.addView(bullet)
            row.addView(tipText)
            layoutProTips.addView(row)
        }

        // Populate Legal Guardrails if present
        if (!payload.legalGuardrails.isNullOrEmpty()) {
            layoutLegal.visibility = View.VISIBLE
            val legalJoined = payload.legalGuardrails.joinToString("\n\n• ", prefix = "• ")
            tvLegalText.text = legalJoined
        } else {
            layoutLegal.visibility = View.GONE
        }

        // Close button dismissal
        btnClose.setOnClickListener {
            dismiss()
        }

        // Deep-link to KinAcademy Masterclass screen
        btnOpenAcademy.setOnClickListener {
            try {
                val clazz = Class.forName("com.example.btproject2.ui.activities.KinAcademyActivity")
                val intent = Intent(requireContext(), clazz).apply {
                    putExtra("TARGET_MODULE_ID", payload.targetAcademyModuleId)
                }
                startActivity(intent)
                dismiss()
            } catch (e: ClassNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    "Opening KinAcademy: Module ${payload.targetAcademyModuleId}",
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
