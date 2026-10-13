package com.example.btproject2.ui.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.btproject2.R
import com.example.btproject2.models.DemoAccount
import com.example.btproject2.models.DemoAccountRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * DemoVaultBottomSheet:
 * Presents verified demo accounts (Augustine Russell & Karl Malone)
 * allowing users and defense evaluators to perform instant 1-tap sign-ins.
 */
class DemoVaultBottomSheet : BottomSheetDialogFragment() {

    var onAccountSelected: ((DemoAccount) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_demo_vault, container, false)
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

        val btnDismiss = view.findViewById<TextView>(R.id.btnDismissSheet)
        btnDismiss?.setOnClickListener {
            dismiss()
        }

        val container = view.findViewById<LinearLayout>(R.id.containerDemoCards) ?: return
        container.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val accounts = DemoAccountRepository.getDemoAccounts()

        for (account in accounts) {
            val cardView = inflater.inflate(R.layout.item_demo_account, container, false)

            val tvEmoji = cardView.findViewById<TextView>(R.id.tvDemoEmoji)
            val tvName = cardView.findViewById<TextView>(R.id.tvDemoName)
            val tvBadge = cardView.findViewById<TextView>(R.id.tvDemoBadge)
            val tvSummary = cardView.findViewById<TextView>(R.id.tvDemoSummary)

            tvEmoji.text = account.emojiIcon
            tvName.text = account.name
            tvBadge.text = account.roleBadge
            tvSummary.text = account.treeSummary

            cardView.setOnClickListener {
                dismiss()
                onAccountSelected?.invoke(account)
            }

            container.addView(cardView)
        }
    }

    companion object {
        const val TAG = "DemoVaultBottomSheet"

        fun newInstance(onSelected: (DemoAccount) -> Unit): DemoVaultBottomSheet {
            return DemoVaultBottomSheet().apply {
                this.onAccountSelected = onSelected
            }
        }
    }
}
