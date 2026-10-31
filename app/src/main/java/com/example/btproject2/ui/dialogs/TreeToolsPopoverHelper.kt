package com.example.btproject2.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.example.btproject2.R

/**
 * UI 1 Floating Popover Dropdown Menu ("Tools ▾") shared across
 * InteractiveTreeActivity and FamilyTreeActivity.
 */
object TreeToolsPopoverHelper {

    fun show(
        context: Context,
        anchor: View,
        showConnect: Boolean,
        showAudit: Boolean = true,
        showOrganize: Boolean = true,
        onConnect: () -> Unit,
        onAudit: () -> Unit,
        onExport: () -> Unit,
        onLegend: () -> Unit,
        onOrganize: () -> Unit = {}
    ) {
        val contentView = LayoutInflater.from(context).inflate(R.layout.popup_tree_tools, null)

        val rowConnect = contentView.findViewById<View>(R.id.rowToolConnect)
        val rowAudit = contentView.findViewById<View>(R.id.rowToolAudit)
        val rowExport = contentView.findViewById<View>(R.id.rowToolExport)
        val rowLegend = contentView.findViewById<View>(R.id.rowToolLegend)
        val rowOrganize = contentView.findViewById<View>(R.id.rowToolOrganizeLayout)

        rowConnect?.visibility = if (showConnect) View.VISIBLE else View.GONE
        rowAudit?.visibility = if (showAudit) View.VISIBLE else View.GONE
        rowOrganize?.visibility = if (showOrganize) View.VISIBLE else View.GONE

        val density = context.resources.displayMetrics.density
        val widthPx = (225 * density).toInt()

        val popupWindow = PopupWindow(
            contentView,
            widthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f * density
        }

        if (anchor is TextView) {
            anchor.text = "Tools ▴"
        }

        popupWindow.setOnDismissListener {
            if (anchor is TextView) {
                anchor.text = "Tools ▾"
            }
        }

        rowConnect?.setOnClickListener {
            popupWindow.dismiss()
            onConnect()
        }

        rowAudit?.setOnClickListener {
            popupWindow.dismiss()
            onAudit()
        }

        rowExport?.setOnClickListener {
            popupWindow.dismiss()
            onExport()
        }

        rowOrganize?.setOnClickListener {
            popupWindow.dismiss()
            onOrganize()
        }

        rowLegend?.setOnClickListener {
            popupWindow.dismiss()
            onLegend()
        }

        val yOff = (6 * density).toInt()
        val xOff = anchor.width - widthPx
        popupWindow.showAsDropDown(anchor, xOff, yOff, Gravity.START)
    }
}
