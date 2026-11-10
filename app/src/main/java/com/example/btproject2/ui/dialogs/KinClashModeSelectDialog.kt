package com.example.btproject2.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.btproject2.R

/**
 * KinClashModeSelectDialog:
 * Mission selector modal matching the Retro 8-Bit Pixel Arcade aesthetic.
 * Replaces the generic grey Android AlertDialog with 3D chiseled cards
 * for "👑 Host New Game" and "🕹️ Enter PIN to Join".
 */
class KinClashModeSelectDialog : DialogFragment() {

    private var onHostCallback: (() -> Unit)? = null
    private var onJoinCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_kinclash_mode_select, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnClose = view.findViewById<View>(R.id.btnModeSelectClose)
        val btnCancel = view.findViewById<Button>(R.id.btnModeSelectCancel)
        val cardHost = view.findViewById<View>(R.id.cardSelectHost)
        val cardJoin = view.findViewById<View>(R.id.cardSelectJoin)

        btnClose.setOnClickListener { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        cardHost.setOnClickListener {
            dismiss()
            onHostCallback?.invoke()
        }

        cardJoin.setOnClickListener {
            dismiss()
            onJoinCallback?.invoke()
        }
    }

    companion object {
        const val TAG = "KinClashModeSelectDialog"

        fun show(
            fragmentManager: FragmentManager,
            onHost: () -> Unit,
            onJoin: () -> Unit
        ): KinClashModeSelectDialog {
            val existing = fragmentManager.findFragmentByTag(TAG) as? KinClashModeSelectDialog
            if (existing != null && existing.isAdded) {
                return existing
            }
            val dialog = KinClashModeSelectDialog().apply {
                this.onHostCallback = onHost
                this.onJoinCallback = onJoin
            }
            dialog.show(fragmentManager, TAG)
            return dialog
        }
    }
}
