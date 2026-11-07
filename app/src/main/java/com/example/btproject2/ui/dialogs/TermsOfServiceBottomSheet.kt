package com.example.btproject2.ui.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.example.btproject2.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * TermsOfServiceBottomSheet:
 * Presents the 16-clause KinTrace Terms and Conditions & Clan Data Stewardship agreement
 * structured around the Termly legal framework and Philippine law (RA 10173, EO 209, RA 386).
 *
 * Implements a strict "Must-Scroll-To-Bottom" verification engine during Registration:
 * The acceptance CTA remains locked (disabled) until the user scrolls through
 * 100% of the agreement, satisfying prior informed consent under RA 10173.
 *
 * When launched from Clan Member Profile or Privacy Controls in read-only mode
 * (alreadyAccepted = true and onTermsAccepted == null), the sheet opens unlocked for easy reading.
 */
class TermsOfServiceBottomSheet : BottomSheetDialogFragment() {

    var onTermsAccepted: (() -> Unit)? = null
    private var alreadyAccepted: Boolean = false
    private var hasScrolledToBottom = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_terms_of_service_sheet, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnClose = view.findViewById<TextView>(R.id.btnTermsClose)
        val pbScrollProgress = view.findViewById<ProgressBar>(R.id.pbScrollProgress)
        val tvScrollBadge = view.findViewById<TextView>(R.id.tvScrollBadge)
        val nsvTermsScroll = view.findViewById<NestedScrollView>(R.id.nsvTermsScroll)
        val btnAcceptTerms = view.findViewById<TextView>(R.id.btnAcceptTerms)

        // Direction 1: Quick-Jump Part Chips
        val chipPart1 = view.findViewById<TextView?>(R.id.chipTermsPart1)
        val chipPart2 = view.findViewById<TextView?>(R.id.chipTermsPart2)
        val chipPart3 = view.findViewById<TextView?>(R.id.chipTermsPart3)
        val chipPart4 = view.findViewById<TextView?>(R.id.chipTermsPart4)

        val headerPart1 = view.findViewById<View?>(R.id.tvTermsPart1Header)
        val headerPart2 = view.findViewById<View?>(R.id.tvTermsPart2Header)
        val headerPart3 = view.findViewById<View?>(R.id.tvTermsPart3Header)
        val headerPart4 = view.findViewById<View?>(R.id.tvTermsPart4Header)

        fun smoothJumpToHeader(targetView: View?) {
            if (targetView != null) {
                nsvTermsScroll.post {
                    nsvTermsScroll.smoothScrollTo(0, targetView.top)
                }
            }
        }

        chipPart1?.setOnClickListener { smoothJumpToHeader(headerPart1) }
        chipPart2?.setOnClickListener { smoothJumpToHeader(headerPart2) }
        chipPart3?.setOnClickListener { smoothJumpToHeader(headerPart3) }
        chipPart4?.setOnClickListener { smoothJumpToHeader(headerPart4) }

        btnClose.setOnClickListener {
            dismiss()
        }

        val isReadOnlyView = alreadyAccepted && onTermsAccepted == null

        fun unlockAcceptButton() {
            hasScrolledToBottom = true
            pbScrollProgress.progress = 100
            if (isReadOnlyView) {
                tvScrollBadge.text = "✓ Viewing 16-Clause KinTrace Legal & Clan Stewardship Covenant"
            } else {
                tvScrollBadge.text = "✓ All 16 sections read. You may now accept."
            }
            tvScrollBadge.setTextColor(requireContext().getColor(R.color.mint_text))

            btnAcceptTerms.isClickable = true
            btnAcceptTerms.isFocusable = true
            btnAcceptTerms.setBackgroundResource(R.drawable.btn_gold_primary)
            btnAcceptTerms.setTextColor(requireContext().getColor(R.color.btn_gold_text))
            btnAcceptTerms.text = if (isReadOnlyView) {
                "✓ Done Reading (Close)"
            } else {
                "✓ I Have Read & Agree to All 16 Terms"
            }
        }

        if (alreadyAccepted) {
            unlockAcceptButton()
        }

        // Mathematical scroll-to-bottom detection
        nsvTermsScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val canScrollDown = v.canScrollVertically(1)
            val child = v.getChildAt(0)
            val totalScrollable = if (child != null) child.measuredHeight - v.measuredHeight else 0
            val percent = if (totalScrollable > 0) {
                ((scrollY.toFloat() / totalScrollable) * 100).toInt().coerceIn(0, 100)
            } else {
                100
            }

            if (!hasScrolledToBottom) {
                pbScrollProgress.progress = percent
            }

            if (!canScrollDown || percent >= 95) {
                unlockAcceptButton()
            } else {
                if (!hasScrolledToBottom) {
                    val readCount = ((percent / 100f) * TOTAL_CLAUSES).toInt().coerceIn(1, TOTAL_CLAUSES)
                    tvScrollBadge.text = "⬇ Please scroll through all 16 sections to accept ($percent% • $readCount/$TOTAL_CLAUSES)"
                    btnAcceptTerms.text = "🔒 Scroll To The Bottom To Accept ($readCount/$TOTAL_CLAUSES)"
                }
            }
        })

        btnAcceptTerms.setOnClickListener {
            if (hasScrolledToBottom) {
                onTermsAccepted?.invoke()
                dismiss()
            }
        }
    }

    companion object {
        const val TAG = "TermsOfServiceBottomSheet"
        const val TOTAL_CLAUSES = 16

        fun newInstance(alreadyAccepted: Boolean = false, onAccepted: (() -> Unit)? = null): TermsOfServiceBottomSheet {
            return TermsOfServiceBottomSheet().apply {
                this.alreadyAccepted = alreadyAccepted
                this.onTermsAccepted = onAccepted
            }
        }
    }
}
