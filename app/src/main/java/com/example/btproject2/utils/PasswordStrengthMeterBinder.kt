package com.example.btproject2.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import com.example.btproject2.R

/**
 * Reusable dynamic UI binder for Direction 1 Password Strength Meter & Criteria Badges.
 * Provides 60fps real-time visual feedback across Registration and Change Password dialogs.
 */
class PasswordStrengthMeterBinder(
    private val container: View,
    private val etPassword: EditText,
    private val etConfirmPassword: EditText? = null,
    private val tvMatchPill: TextView? = null,
    private val onStateChanged: ((evaluation: PasswordEvaluation, isMatching: Boolean) -> Unit)? = null
) {
    private val tvScoreLabel: TextView? = container.findViewById(R.id.tvStrengthScoreLabel)
    private val seg1: View? = container.findViewById(R.id.viewMeterSeg1)
    private val seg2: View? = container.findViewById(R.id.viewMeterSeg2)
    private val seg3: View? = container.findViewById(R.id.viewMeterSeg3)
    private val seg4: View? = container.findViewById(R.id.viewMeterSeg4)

    private val badgeLen: View? = container.findViewById(R.id.layoutCriterionLength)
    private val iconLen: TextView? = container.findViewById(R.id.tvCritIconLength)
    private val textLen: TextView? = container.findViewById(R.id.tvCritTextLength)

    private val badgeUpper: View? = container.findViewById(R.id.layoutCriterionUpper)
    private val iconUpper: TextView? = container.findViewById(R.id.tvCritIconUpper)
    private val textUpper: TextView? = container.findViewById(R.id.tvCritTextUpper)

    private val badgeNum: View? = container.findViewById(R.id.layoutCriterionNumber)
    private val iconNum: TextView? = container.findViewById(R.id.tvCritIconNumber)
    private val textNum: TextView? = container.findViewById(R.id.tvCritTextNumber)

    private val badgeSym: View? = container.findViewById(R.id.layoutCriterionSymbol)
    private val iconSym: TextView? = container.findViewById(R.id.tvCritIconSymbol)
    private val textSym: TextView? = container.findViewById(R.id.tvCritTextSymbol)

    init {
        etPassword.doOnTextChanged { text, _, _, _ ->
            updateUi()
        }
        etConfirmPassword?.doOnTextChanged { _, _, _, _ ->
            updateUi()
        }
        updateUi()
    }

    fun updateUi() {
        val passText = etPassword.text?.toString().orEmpty()
        val confirmText = etConfirmPassword?.text?.toString().orEmpty()

        val evaluation = PasswordSecurityPolicy.evaluate(passText)
        val activeColor = Color.parseColor(evaluation.colorHex)
        val inactiveColor = Color.parseColor("#152B1E")

        // 1. Update Strength Label
        tvScoreLabel?.text = evaluation.label
        tvScoreLabel?.setTextColor(activeColor)

        // 2. Update 4-Segment Progress Bar
        val segments = listOf(seg1, seg2, seg3, seg4)
        for (i in segments.indices) {
            val seg = segments[i] ?: continue
            val isActive = (i < evaluation.score)
            val colorToApply = if (isActive) activeColor else inactiveColor
            (seg.background?.mutate() as? GradientDrawable)?.setColor(colorToApply)
                ?: seg.setBackgroundColor(colorToApply)
        }

        // 3. Update Criteria Badges
        updateBadge(badgeLen, iconLen, textLen, evaluation.hasMinLength)
        updateBadge(badgeUpper, iconUpper, textUpper, evaluation.hasUppercase)
        updateBadge(badgeNum, iconNum, textNum, evaluation.hasDigit)
        updateBadge(badgeSym, iconSym, textSym, evaluation.hasSpecialChar)

        // 4. Update Confirm Password Match Indicator
        val isMatching = if (confirmText.isNotEmpty()) {
            val matches = (passText == confirmText)
            tvMatchPill?.visibility = View.VISIBLE
            if (matches) {
                tvMatchPill?.setBackgroundResource(R.drawable.bg_pill_password_match)
                tvMatchPill?.text = "✓ Passwords Match"
                tvMatchPill?.setTextColor(Color.parseColor("#4ADE80"))
            } else {
                tvMatchPill?.setBackgroundResource(R.drawable.bg_pill_password_mismatch)
                tvMatchPill?.text = "✕ Passwords Do Not Match"
                tvMatchPill?.setTextColor(Color.parseColor("#F87171"))
            }
            matches
        } else {
            tvMatchPill?.visibility = View.GONE
            false
        }

        onStateChanged?.invoke(evaluation, isMatching)
    }

    private fun updateBadge(badge: View?, icon: TextView?, text: TextView?, isMet: Boolean) {
        if (badge == null || icon == null || text == null) return
        if (isMet) {
            badge.setBackgroundResource(R.drawable.bg_criterion_badge_met)
            icon.setBackgroundResource(R.drawable.bg_criterion_icon_met)
            icon.text = "✓"
            icon.setTextColor(Color.parseColor("#050B07"))
            text.setTextColor(Color.parseColor("#E2E8F0"))
        } else {
            badge.setBackgroundResource(R.drawable.bg_criterion_badge_default)
            icon.setBackgroundResource(R.drawable.bg_criterion_icon_default)
            icon.text = "✕"
            icon.setTextColor(Color.parseColor("#64748B"))
            text.setTextColor(Color.parseColor("#64748B"))
        }
    }
}
