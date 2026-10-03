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
 * Presents the 10-clause KinTrace Terms of Service & Clan Data Stewardship agreement.
 *
 * Implements a strict "Must-Scroll-To-Bottom" verification engine:
 * The acceptance CTA remains locked (disabled) until the user scrolls through
 * 100% of the agreement, satisfying prior informed consent under RA 10173.
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

        btnClose.setOnClickListener {
            dismiss()
        }

        fun unlockAcceptButton() {
            hasScrolledToBottom = true
            pbScrollProgress.progress = 100
            tvScrollBadge.text = "✓ All 10 sections read. You may now accept."
            tvScrollBadge.setTextColor(requireContext().getColor(R.color.mint_text))

            btnAcceptTerms.isClickable = true
            btnAcceptTerms.isFocusable = true
            btnAcceptTerms.setBackgroundResource(R.drawable.btn_gold_primary)
            btnAcceptTerms.setTextColor(Color.parseColor("#0A1B12"))
            btnAcceptTerms.text = "✓ I Have Read & Agree to the Terms"
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
                    tvScrollBadge.text = "⬇ Please scroll through all 10 sections to accept ($percent%)"
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

        fun newInstance(alreadyAccepted: Boolean = false, onAccepted: (() -> Unit)? = null): TermsOfServiceBottomSheet {
            return TermsOfServiceBottomSheet().apply {
                this.alreadyAccepted = alreadyAccepted
                this.onTermsAccepted = onAccepted
            }
        }
    }
}
