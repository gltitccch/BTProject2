package com.example.btproject2.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.btproject2.R

/**
 * KinClashArcadeConfirmDialog:
 * 3D Retro arcade confirmation modal replacing generic system AlertDialogs.
 */
class KinClashArcadeConfirmDialog : DialogFragment() {

    private var dialogTitle: String = "Exit KinClash Room?"
    private var dialogMessage: String = "Are you sure you want to exit?"
    private var confirmButtonText: String = "Exit Room"
    private var iconText: String = "⚠️"
    private var onConfirmAction: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_arcade_confirm, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvIcon = view.findViewById<TextView>(R.id.tvConfirmIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvConfirmTitle)
        val tvMsg = view.findViewById<TextView>(R.id.tvConfirmMessage)
        val btnExit = view.findViewById<Button>(R.id.btnConfirmExit)
        val btnStay = view.findViewById<Button>(R.id.btnConfirmStay)

        tvIcon.text = iconText
        tvTitle.text = dialogTitle
        tvMsg.text = dialogMessage
        btnExit.text = confirmButtonText

        btnStay.setOnClickListener { dismiss() }

        btnExit.setOnClickListener {
            dismiss()
            onConfirmAction?.invoke()
        }
    }

    companion object {
        const val TAG = "KinClashArcadeConfirmDialog"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String,
            confirmText: String = "Exit Room",
            icon: String = "⚠️",
            onConfirm: () -> Unit
        ): KinClashArcadeConfirmDialog {
            val existing = fragmentManager.findFragmentByTag(TAG) as? KinClashArcadeConfirmDialog
            if (existing != null && existing.isAdded) {
                return existing
            }
            val dialog = KinClashArcadeConfirmDialog().apply {
                this.dialogTitle = title
                this.dialogMessage = message
                this.confirmButtonText = confirmText
                this.iconText = icon
                this.onConfirmAction = onConfirm
            }
            dialog.show(fragmentManager, TAG)
            return dialog
        }
    }
}
