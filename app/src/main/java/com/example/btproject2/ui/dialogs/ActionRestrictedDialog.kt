package com.example.btproject2.ui.dialogs

import com.example.btproject2.utils.KinTraceAlert

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.utils.NotificationHelper

/**
 * ActionRestrictedDialog:
 * Implements the Capstone Figure 16 (Page 54) "Action restricted" modal alert.
 *
 * Appears when a user with a read-only Viewer role attempts an action requiring
 * Editor or Owner privileges (e.g. adding, editing, or deleting members, or merging branches).
 * Provides an explicit "Request Access" CTA that dispatches an in-app permission upgrade
 * request to the tree owner's Notification Center.
 */
class ActionRestrictedDialog : DialogFragment() {

    var onRequestAccess: (() -> Unit)? = null
    var onDismissAction: (() -> Unit)? = null
    var treeId: String = ""
    var treeName: String = ""
    var actionAttempted: String = "Edit Member Records"

    private val authHelper by lazy { AuthHelper() }
    private val firestoreHelper by lazy { FirestoreHelper() }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissAction?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val argTreeId = it.getString(ARG_TREE_ID, "")
            if (argTreeId.isNotBlank()) treeId = argTreeId
            val argTreeName = it.getString(ARG_TREE_NAME, "Family Tree")
            if (argTreeName.isNotBlank()) treeName = argTreeName
            val argAction = it.getString(ARG_ACTION_ATTEMPTED, "Edit Member Records")
            if (argAction.isNotBlank()) actionAttempted = argAction
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            requestFeature(Window.FEATURE_NO_TITLE)
        }
        return inflater.inflate(R.layout.layout_action_restricted_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRequestAccess = view.findViewById<TextView>(R.id.btnRequestAccess)
        val btnDismiss = view.findViewById<TextView>(R.id.btnDismissRestricted)
        val tvMessage = view.findViewById<TextView>(R.id.tvRestrictedMessage)

        if (actionAttempted.isNotBlank()) {
            tvMessage.text = "You do not have permission to perform '$actionAttempted'. Only the tree owner or authorized editors can modify records."
        }

        btnDismiss.setOnClickListener {
            dismiss()
        }

        btnRequestAccess.setOnClickListener {
            val currentUserId = authHelper.getCurrentUserId().orEmpty()
            if (treeId.isNotBlank() && currentUserId.isNotBlank()) {
                val notif = NotificationHelper.createPermissionRestrictedNotification(
                    treeId = treeId,
                    userId = currentUserId,
                    actionAttempted = actionAttempted,
                    currentRole = "Viewer"
                )
                firestoreHelper.addNotification(notif)
                KinTraceAlert.show(context, "Access request sent to tree owner.")
            }
            onRequestAccess?.invoke()
            dismiss()
        }
    }

    companion object {
        const val TAG = "ActionRestrictedDialog"
        const val ARG_TREE_ID = "arg_tree_id"
        const val ARG_TREE_NAME = "arg_tree_name"
        const val ARG_ACTION_ATTEMPTED = "arg_action_attempted"

        fun newInstance(
            treeId: String = "",
            treeName: String = "",
            actionAttempted: String = "Edit Member Records"
        ): ActionRestrictedDialog {
            return ActionRestrictedDialog().apply {
                this.treeId = treeId
                this.treeName = treeName
                this.actionAttempted = actionAttempted
            }
        }
    }
}
