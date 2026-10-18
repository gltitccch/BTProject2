package com.example.btproject2.ui.dialogs

import com.example.btproject2.utils.KinTraceAlert

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

        fun show(activity: androidx.fragment.app.FragmentActivity, context: QuickAssistContext) {
            newInstance(context).show(activity.supportFragmentManager, "KinTraceQuickAssist")
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

        // Populate Pro Tips dynamically as UI 1 Numbered Minimalist Cards
        layoutProTips.removeAllViews()
        val ctx = requireContext()
        val textPrimaryColor = ContextCompat.getColor(ctx, R.color.text_primary)
        val goldColor = ContextCompat.getColor(ctx, R.color.gold_accent)
        val cardBgColor = ContextCompat.getColor(ctx, R.color.forest_card)
        val cardBorderColor = ContextCompat.getColor(ctx, R.color.forest_card_border)
        val numBgColor = ContextCompat.getColor(ctx, R.color.primary_light)
        val numStrokeColor = ContextCompat.getColor(ctx, R.color.primary_light_stroke)
        val density = resources.displayMetrics.density

        for ((index, tip) in payload.proTips.withIndex()) {
            val rowCard = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dpToPx(8)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(cardBgColor)
                    setStroke(dpToPx(1), cardBorderColor)
                }
            }

            val numBadge = TextView(ctx).apply {
                text = "%02d".format(index + 1)
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(goldColor)
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dpToPx(10)
                    topMargin = dpToPx(1)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 6f * density
                    setColor(numBgColor)
                    setStroke(dpToPx(1), numStrokeColor)
                }
            }

            val spannableTip = android.text.SpannableString(tip)
            val colonIdx = tip.indexOf(':')
            if (colonIdx > 0) {
                spannableTip.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0,
                    colonIdx + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val tipText = TextView(ctx).apply {
                text = spannableTip
                textSize = 12f
                setTextColor(textPrimaryColor)
                setLineSpacing(dpToPx(2).toFloat(), 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }

            rowCard.addView(numBadge)
            rowCard.addView(tipText)
            layoutProTips.addView(rowCard)
        }

        // Populate Legal Guardrails if present (UI 1 Left-Border Legal Bar)
        if (!payload.legalGuardrails.isNullOrEmpty()) {
            layoutLegal.visibility = View.VISIBLE
            val legalJoined = payload.legalGuardrails.joinToString("\n\n")
            tvLegalText.text = legalJoined
        } else {
            layoutLegal.visibility = View.GONE
        }

        // Close button dismissal
        btnClose.setOnClickListener {
            dismiss()
        }

        // Deep-link to KinAcademy Masterclass screen (preserving active TREE_ID / treeId)
        btnOpenAcademy.setOnClickListener {
            try {
                val activeTreeId = activity?.intent?.getStringExtra("TREE_ID")
                    ?: activity?.intent?.getStringExtra("treeId")
                    ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(ctx)
                val clazz = Class.forName("com.example.btproject2.ui.activities.KinAcademyActivity")
                val intent = Intent(ctx, clazz).apply {
                    putExtra("TARGET_MODULE_ID", payload.targetAcademyModuleId)
                    putExtra("TREE_ID", activeTreeId)
                    putExtra("treeId", activeTreeId)
                }
                startActivity(intent)
                dismiss()
            } catch (e: ClassNotFoundException) {
                KinTraceAlert.show(ctx, "Opening KinAcademy: Module ${payload.targetAcademyModuleId}")
                dismiss()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
