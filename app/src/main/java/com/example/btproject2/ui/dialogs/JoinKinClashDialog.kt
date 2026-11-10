package com.example.btproject2.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper

/**
 * JoinKinClashDialog:
 * Allows visiting relatives, guests, and friends to connect their mobile phone to a live
 * KinClash party trivia room by entering the 4-digit Room PIN and a codename.
 *
 * Safe Validation Rules:
 * - Room PIN is trimmed, converted to uppercase, and must be at least 4 characters.
 * - Nickname must contain at least 2 characters.
 * - Prevents multiple dialog instances and handles cancellation gracefully.
 */
class JoinKinClashDialog : DialogFragment() {

    private var initialPin: String = ""
    private var onJoinCallback: ((pin: String, nickname: String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_join_kinclash, container, false)
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

        val btnClose = view.findViewById<View>(R.id.btnKinClashClose)
        val btnCancel = view.findViewById<Button>(R.id.btnKinClashCancel)
        val btnSubmit = view.findViewById<Button>(R.id.btnKinClashSubmit)
        val etPin = view.findViewById<EditText>(R.id.etKinClashPin)
        val etNickname = view.findViewById<EditText>(R.id.etKinClashNickname)
        val tvError = view.findViewById<TextView>(R.id.tvKinClashError)

        // Pre-fill initial PIN if provided
        if (initialPin.isNotBlank()) {
            etPin.setText(initialPin.uppercase())
        }

        // Auto pre-fill nickname from current signed-in profile if blank
        val currentUser = AuthHelper().getCurrentUser()
        val defaultName = currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: ""
        if (defaultName.isNotBlank() && etNickname.text.isNullOrBlank()) {
            etNickname.setText(defaultName)
        }

        btnClose.setOnClickListener { dismiss() }
        btnCancel.setOnClickListener { dismiss() }

        btnSubmit.setOnClickListener {
            val pin = etPin.text?.toString()?.trim()?.uppercase().orEmpty()
            val nickname = etNickname.text?.toString()?.trim().orEmpty()

            // Safe Validations
            when {
                pin.length < 4 -> {
                    tvError.text = "Please enter a valid 4-digit Room PIN (e.g. 7821)."
                    tvError.visibility = View.VISIBLE
                    etPin.requestFocus()
                }
                nickname.length < 2 -> {
                    tvError.text = "Please enter a nickname or codename (at least 2 letters)."
                    tvError.visibility = View.VISIBLE
                    etNickname.requestFocus()
                }
                else -> {
                    tvError.visibility = View.GONE
                    onJoinCallback?.invoke(pin, nickname)
                    dismiss()
                }
            }
        }
    }

    companion object {
        const val TAG = "JoinKinClashDialog"

        fun show(
            fragmentManager: FragmentManager,
            initialPin: String = "",
            onJoin: (pin: String, nickname: String) -> Unit
        ): JoinKinClashDialog {
            val existing = fragmentManager.findFragmentByTag(TAG) as? JoinKinClashDialog
            if (existing != null && existing.isAdded) {
                return existing
            }
            val dialog = JoinKinClashDialog().apply {
                this.initialPin = initialPin
                this.onJoinCallback = onJoin
            }
            dialog.show(fragmentManager, TAG)
            return dialog
        }
    }
}
