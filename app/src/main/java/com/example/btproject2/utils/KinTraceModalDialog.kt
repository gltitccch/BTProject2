package com.example.btproject2.utils

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.btproject2.R

/**
 * KinTraceModalDialog — Global Minimalist Centered Crest Card Modal System (Direction 1).
 *
 * Replaces stock Android grey rectangular AlertDialogs across KinTrace with a cohesive
 * 22dp floating card, 48dp semantic icon crest badge, uppercase kicker, serif title,
 * and balanced 50/50 pill buttons with full Dark & Light Mode support.
 */
object KinTraceModalDialog {

    /**
     * Strips any stray corrupted leading prefix characters (such as `"s  Clan Quota Expanded"`
     * or replacement characters `\uFFFD`) from notification or dialog titles.
     */
    @JvmStatic
    fun sanitizeTitle(rawTitle: CharSequence?): String {
        if (rawTitle == null) return ""
        val str = rawTitle.toString()
            .replace("\uFFFD", "")
            .trim()
        // Strip stray single-character prefix followed by 2+ spaces (e.g. "s  Clan Quota Expanded")
        return str.replace(Regex("^[a-zA-Z?]\\s{2,}"), "").trim()
    }

    data class SemanticStyle(
        val icon: String,
        val kicker: String,
        val isDangerCrest: Boolean,
        val isDangerPrimaryButton: Boolean
    )

    @JvmStatic
    fun resolveSemanticStyle(
        title: String,
        positiveText: String,
        explicitIcon: String? = null,
        explicitKicker: String? = null,
        explicitDanger: Boolean? = null
    ): SemanticStyle {
        val tLower = title.lowercase()
        val pLower = positiveText.lowercase()

        val isDestructiveAction = explicitDanger ?: (
            pLower.contains("sign out") ||
                pLower.contains("log out") ||
                pLower.contains("delete") ||
                pLower.contains("remove") ||
                pLower.contains("reject") ||
                pLower.contains("revoke") ||
                pLower.contains("clear") ||
                pLower.contains("unlink") ||
                tLower.contains("sign out") ||
                tLower.contains("delete") ||
                tLower.contains("remove")
            )

        val isWarningOrError = tLower.contains("error") ||
            tLower.contains("invalid") ||
            tLower.contains("expired") ||
            tLower.contains("revoked") ||
            tLower.contains("warning") ||
            tLower.contains("blocked") ||
            tLower.contains("cannot") ||
            tLower.contains("failed") ||
            tLower.contains("limit")

        val autoIcon = when {
            tLower.contains("sign out") || pLower.contains("sign out") -> "🚪"
            tLower.contains("delete") || tLower.contains("remove") || pLower.contains("delete") || pLower.contains("remove") -> "🗑️"
            tLower.contains("quota") || tLower.contains("capacity") || tLower.contains("expanded") -> "📜"
            tLower.contains("invite") || tLower.contains("code") -> "🔑"
            tLower.contains("password") || tLower.contains("email") || tLower.contains("security") || tLower.contains("auth") -> "🛡️"
            tLower.contains("merge") || tLower.contains("clan") || tLower.contains("branch") || tLower.contains("synthesis") -> "🌿"
            tLower.contains("offline") || tLower.contains("connection") -> "📡"
            isWarningOrError -> "⚠️"
            tLower.contains("select") || tLower.contains("choose") || tLower.contains("approve") -> "✨"
            else -> "📜"
        }

        val autoKicker = when {
            tLower.contains("sign out") || pLower.contains("sign out") -> "ACCOUNT SESSION"
            isDestructiveAction -> "CONFIRM ACTION"
            tLower.contains("quota") || tLower.contains("capacity") -> "CLAN CAPACITY UPDATE"
            tLower.contains("invite") || tLower.contains("code") -> "CLAN ACCESS"
            tLower.contains("password") || tLower.contains("email") || tLower.contains("security") -> "ACCOUNT SECURITY"
            tLower.contains("merge") || tLower.contains("synthesis") -> "MERGED CLAN SPACE"
            isWarningOrError -> "SYSTEM NOTICE"
            tLower.contains("select") || tLower.contains("choose") -> "SELECT OPTION"
            else -> "HERITAGE NOTICE"
        }

        return SemanticStyle(
            icon = explicitIcon ?: autoIcon,
            kicker = explicitKicker ?: autoKicker,
            isDangerCrest = isDestructiveAction || isWarningOrError,
            isDangerPrimaryButton = isDestructiveAction
        )
    }

    /**
     * Drop-in fluent replacement for [AlertDialog.Builder] that renders Direction 1
     * (Minimalist Centered Crest Card) with transparent rounded window corners.
     */
    class Builder(private val context: Context) {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var customIcon: String? = null
        private var customKicker: String? = null
        private var explicitDanger: Boolean? = null

        private var positiveText: CharSequence? = null
        private var positiveListener: DialogInterface.OnClickListener? = null
        private var positiveAutoDismiss: Boolean = true

        private var negativeText: CharSequence? = null
        private var negativeListener: DialogInterface.OnClickListener? = null

        private var neutralText: CharSequence? = null
        private var neutralListener: DialogInterface.OnClickListener? = null

        private var items: Array<out CharSequence>? = null
        private var itemsListener: DialogInterface.OnClickListener? = null
        private var checkedItem: Int = -1
        private var isSingleChoice: Boolean = false
        private var isMultiChoice: Boolean = false
        private var checkedItems: BooleanArray? = null
        private var multiChoiceListener: DialogInterface.OnMultiChoiceClickListener? = null

        private var customView: View? = null
        private var cancelable: Boolean = true
        private var onDismissListener: DialogInterface.OnDismissListener? = null
        private var onCancelListener: DialogInterface.OnCancelListener? = null

        fun setTitle(title: CharSequence?): Builder {
            this.title = title
            return this
        }

        fun setMessage(message: CharSequence?): Builder {
            this.message = message
            return this
        }

        fun setIconCrest(icon: String): Builder {
            this.customIcon = icon
            return this
        }

        fun setKicker(kicker: String): Builder {
            this.customKicker = kicker
            return this
        }

        fun setDanger(isDanger: Boolean): Builder {
            this.explicitDanger = isDanger
            return this
        }

        fun setPositiveButton(
            text: CharSequence?,
            listener: DialogInterface.OnClickListener? = null
        ): Builder {
            this.positiveText = text
            this.positiveListener = listener
            this.positiveAutoDismiss = true
            return this
        }

        fun setPositiveButtonNoAutoDismiss(
            text: CharSequence?,
            listener: (AlertDialog) -> Unit
        ): Builder {
            this.positiveText = text
            this.positiveAutoDismiss = false
            this.positiveListener = DialogInterface.OnClickListener { d, _ ->
                if (d is AlertDialog) listener(d)
            }
            return this
        }

        fun setNegativeButton(
            text: CharSequence?,
            listener: DialogInterface.OnClickListener? = null
        ): Builder {
            this.negativeText = text
            this.negativeListener = listener
            return this
        }

        fun setNeutralButton(
            text: CharSequence?,
            listener: DialogInterface.OnClickListener? = null
        ): Builder {
            this.neutralText = text
            this.neutralListener = listener
            return this
        }

        fun setItems(
            items: Array<out CharSequence>,
            listener: DialogInterface.OnClickListener?
        ): Builder {
            this.items = items
            this.itemsListener = listener
            this.isSingleChoice = false
            this.isMultiChoice = false
            return this
        }

        fun setSingleChoiceItems(
            items: Array<out CharSequence>,
            checkedItem: Int,
            listener: DialogInterface.OnClickListener?
        ): Builder {
            this.items = items
            this.checkedItem = checkedItem
            this.itemsListener = listener
            this.isSingleChoice = true
            this.isMultiChoice = false
            return this
        }

        fun setMultiChoiceItems(
            items: Array<out CharSequence>,
            checkedItems: BooleanArray?,
            listener: DialogInterface.OnMultiChoiceClickListener?
        ): Builder {
            this.items = items
            this.checkedItems = checkedItems
            this.multiChoiceListener = listener
            this.isSingleChoice = false
            this.isMultiChoice = true
            return this
        }

        fun setView(view: View?): Builder {
            this.customView = view
            return this
        }

        fun setCancelable(cancelable: Boolean): Builder {
            this.cancelable = cancelable
            return this
        }

        fun setOnDismissListener(listener: DialogInterface.OnDismissListener?): Builder {
            this.onDismissListener = listener
            return this
        }

        fun setOnCancelListener(listener: DialogInterface.OnCancelListener?): Builder {
            this.onCancelListener = listener
            return this
        }

        fun create(): AlertDialog {
            val cleanedTitle = sanitizeTitle(title)
            // If this is a self-contained custom layout dialog (no outer title or outer action buttons),
            // render the custom view directly with a transparent window background so it doesn't double-wrap.
            if (customView != null &&
                cleanedTitle.isEmpty() &&
                positiveText.isNullOrBlank() &&
                negativeText.isNullOrBlank() &&
                neutralText.isNullOrBlank() &&
                items == null
            ) {
                (customView?.parent as? ViewGroup)?.removeView(customView)
                val directDialog = AlertDialog.Builder(context)
                    .setView(customView)
                    .setCancelable(cancelable)
                    .create()
                onDismissListener?.let { directDialog.setOnDismissListener(it) }
                onCancelListener?.let { directDialog.setOnCancelListener(it) }
                directDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                return directDialog
            }

            val inflater = LayoutInflater.from(context)
            val rootView = inflater.inflate(R.layout.dialog_kintrace_modal, null)

            val tvIconCrest = rootView.findViewById<TextView>(R.id.tvModalIconCrest)
            val tvKicker = rootView.findViewById<TextView>(R.id.tvModalKicker)
            val tvTitle = rootView.findViewById<TextView>(R.id.tvModalTitle)
            val tvMessage = rootView.findViewById<TextView>(R.id.tvModalMessage)
            val layoutItems = rootView.findViewById<LinearLayout>(R.id.layoutModalItemsContainer)
            val layoutCustomView = rootView.findViewById<FrameLayout>(R.id.layoutModalCustomViewContainer)
            val btnPositive = rootView.findViewById<TextView>(R.id.btnModalPositive)
            val btnNegative = rootView.findViewById<TextView>(R.id.btnModalNegative)
            val btnNeutral = rootView.findViewById<TextView>(R.id.btnModalNeutral)
            val layoutActionRow = rootView.findViewById<LinearLayout>(R.id.layoutModalActionRow)

            val posLabel = positiveText?.toString().orEmpty()
            val style = resolveSemanticStyle(
                title = cleanedTitle,
                positiveText = posLabel,
                explicitIcon = customIcon,
                explicitKicker = customKicker,
                explicitDanger = explicitDanger
            )

            tvIconCrest.text = style.icon
            tvKicker.text = style.kicker
            if (style.isDangerCrest) {
                tvIconCrest.setBackgroundResource(R.drawable.bg_kintrace_modal_crest_danger)
                tvKicker.setTextColor(ContextCompat.getColor(context, R.color.danger))
            } else {
                tvIconCrest.setBackgroundResource(R.drawable.bg_kintrace_modal_crest_gold)
                tvKicker.setTextColor(ContextCompat.getColor(context, R.color.primary))
            }

            if (cleanedTitle.isNotEmpty()) {
                tvTitle.text = cleanedTitle
                tvTitle.visibility = View.VISIBLE
            } else {
                tvTitle.visibility = View.GONE
            }

            val msgText = message?.toString()?.trim().orEmpty()
            if (msgText.isNotEmpty()) {
                tvMessage.text = msgText
                tvMessage.visibility = View.VISIBLE
            } else {
                tvMessage.visibility = View.GONE
            }

            // Custom embedded view support
            customView?.let { child ->
                (child.parent as? ViewGroup)?.removeView(child)
                child.background = null
                child.setPadding(0, 0, 0, 0)
                layoutCustomView.addView(child)
                layoutCustomView.visibility = View.VISIBLE
                // If the custom view already provides its own header (e.g. dialog_change_password or dialog_invite_code)
                if (cleanedTitle.isEmpty()) {
                    tvIconCrest.visibility = View.GONE
                    tvKicker.visibility = View.GONE
                }
            }

            val dialog = AlertDialog.Builder(context)
                .setView(rootView)
                .setCancelable(cancelable)
                .create()

            onDismissListener?.let { dialog.setOnDismissListener(it) }
            onCancelListener?.let { dialog.setOnCancelListener(it) }
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // Populate list items, single-choice, or multi-choice items if present
            val itemArray = items
            if (!itemArray.isNullOrEmpty()) {
                layoutItems.visibility = View.VISIBLE
                layoutItems.removeAllViews()
                val density = context.resources.displayMetrics.density
                val padH = (14 * density).toInt()
                val padV = (12 * density).toInt()
                val marginB = (8 * density).toInt()

                itemArray.forEachIndexed { index, itemLabel ->
                    var isChecked = when {
                        isMultiChoice -> checkedItems?.getOrNull(index) == true
                        isSingleChoice -> index == checkedItem
                        else -> false
                    }
                    val row = TextView(context).apply {
                        fun updateRowAppearance() {
                            val prefix = when {
                                isMultiChoice && isChecked -> "☑  "
                                isMultiChoice -> "☐  "
                                isSingleChoice && isChecked -> "◉  "
                                isSingleChoice -> "○  "
                                else -> ""
                            }
                            text = "$prefix$itemLabel"
                            textSize = 13.5f
                            setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(
                                ContextCompat.getColor(
                                    context,
                                    if (isChecked) R.color.primary else R.color.text_primary
                                )
                            )
                            setBackgroundResource(
                                if (isChecked) R.drawable.bg_kintrace_modal_crest_gold
                                else R.drawable.bg_kintrace_modal_btn_ghost
                            )
                        }
                        updateRowAppearance()
                        setPadding(padH, padV, padH, padV)
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = marginB
                        }
                        setOnClickListener {
                            if (isMultiChoice) {
                                isChecked = !isChecked
                                if (checkedItems != null && index in checkedItems!!.indices) {
                                    checkedItems!![index] = isChecked
                                }
                                updateRowAppearance()
                                multiChoiceListener?.onClick(dialog, index, isChecked)
                            } else {
                                itemsListener?.onClick(dialog, index)
                                if (!isSingleChoice) {
                                    dialog.dismiss()
                                }
                            }
                        }
                    }
                    layoutItems.addView(row)
                }
            }

            // Configure Neutral Button
            if (!neutralText.isNullOrBlank()) {
                btnNeutral.visibility = View.VISIBLE
                btnNeutral.text = neutralText
                btnNeutral.setOnClickListener {
                    neutralListener?.onClick(dialog, DialogInterface.BUTTON_NEUTRAL)
                    dialog.dismiss()
                }
            } else {
                btnNeutral.visibility = View.GONE
            }

            // Configure Negative Button
            if (!negativeText.isNullOrBlank()) {
                btnNegative.visibility = View.VISIBLE
                btnNegative.text = negativeText
                btnNegative.setOnClickListener {
                    negativeListener?.onClick(dialog, DialogInterface.BUTTON_NEGATIVE)
                    dialog.dismiss()
                }
            } else {
                btnNegative.visibility = View.GONE
            }

            // Configure Positive Button
            if (!positiveText.isNullOrBlank()) {
                btnPositive.visibility = View.VISIBLE
                btnPositive.text = positiveText
                if (style.isDangerPrimaryButton) {
                    btnPositive.setBackgroundResource(R.drawable.bg_kintrace_modal_btn_danger)
                    btnPositive.setTextColor(ContextCompat.getColor(context, R.color.white))
                } else {
                    btnPositive.setBackgroundResource(R.drawable.bg_kintrace_modal_btn_primary)
                    btnPositive.setTextColor(ContextCompat.getColor(context, R.color.btn_gold_text))
                }
                btnPositive.setOnClickListener {
                    positiveListener?.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                    if (positiveAutoDismiss) {
                        dialog.dismiss()
                    }
                }
            } else {
                btnPositive.visibility = View.GONE
            }

            // If only one button is visible in the bottom row, remove end margin so it centers cleanly
            if (btnNegative.visibility == View.GONE || btnPositive.visibility == View.GONE) {
                (btnNegative.layoutParams as? LinearLayout.LayoutParams)?.marginEnd = 0
            }

            if (btnNegative.visibility == View.GONE && btnPositive.visibility == View.GONE) {
                layoutActionRow.visibility = View.GONE
            }

            return dialog
        }

        fun show(): AlertDialog {
            val dialog = create()
            dialog.show()
            return dialog
        }
    }
}
