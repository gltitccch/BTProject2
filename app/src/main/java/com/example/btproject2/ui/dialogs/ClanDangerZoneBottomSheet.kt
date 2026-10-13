package com.example.btproject2.ui.dialogs

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.example.btproject2.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * ClanDangerZoneBottomSheet:
 * Modernized safe account deletion & stewardship modal dialog.
 *
 * Implements a tactile slide-to-confirm safety slider to eliminate accidental taps,
 * while presenting explicit impact breakdown cards distinguishing erased personal credentials
 * from preserved communal genealogy under Clan Stewardship (RA 10173 compliant).
 */
class ClanDangerZoneBottomSheet : BottomSheetDialogFragment() {

    var onConfirmDelete: (() -> Unit)? = null

    private var isConfirmed = false
    private var startRawX = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_clan_danger_zone_sheet, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
        }
        return dialog
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCancel = view.findViewById<TextView>(R.id.btnCancelDangerZone)
        val layoutTrack = view.findViewById<FrameLayout>(R.id.layoutSliderTrack)
        val viewFill = view.findViewById<View>(R.id.viewSliderFill)
        val tvHint = view.findViewById<TextView>(R.id.tvSliderHint)
        val viewThumb = view.findViewById<FrameLayout>(R.id.viewSliderThumb)

        btnCancel.setOnClickListener {
            dismiss()
        }

        // Tactile Slide-To-Delete Drag Engine
        viewThumb.setOnTouchListener { v, event ->
            if (isConfirmed) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX - v.translationX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val trackWidth = layoutTrack.width.toFloat()
                    val thumbWidth = v.width.toFloat()
                    val marginOffset = (layoutTrack.paddingStart + layoutTrack.paddingEnd).toFloat() + 8f
                    val maxTranslate = (trackWidth - thumbWidth - marginOffset).coerceAtLeast(1f)

                    val newTranslate = (event.rawX - startRawX).coerceIn(0f, maxTranslate)
                    v.translationX = newTranslate

                    // Expand dynamic fill width
                    val fillWidth = (thumbWidth + newTranslate).toInt()
                    viewFill.layoutParams.width = fillWidth
                    viewFill.requestLayout()

                    // Check confirmation threshold (90% travel distance)
                    val progress = newTranslate / maxTranslate
                    if (progress >= 0.90f && !isConfirmed) {
                        isConfirmed = true
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                        // Snap thumb to end
                        v.translationX = maxTranslate
                        viewFill.layoutParams.width = layoutTrack.width
                        viewFill.requestLayout()

                        tvHint.text = "✓ DELETION CONFIRMED"
                        tvHint.setTextColor(Color.WHITE)

                        v.postDelayed({
                            dismiss()
                            onConfirmDelete?.invoke()
                        }, 250)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isConfirmed) {
                        // Smoothly animate thumb and fill back to start (0f)
                        val currentX = v.translationX
                        val anim = ValueAnimator.ofFloat(currentX, 0f).apply {
                            duration = 220
                            interpolator = DecelerateInterpolator()
                            addUpdateListener { va ->
                                val x = va.animatedValue as Float
                                v.translationX = x
                                val thumbWidth = v.width.toFloat()
                                viewFill.layoutParams.width = (thumbWidth + x).toInt()
                                viewFill.requestLayout()
                            }
                        }
                        anim.start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        const val TAG = "ClanDangerZoneBottomSheet"

        fun newInstance(): ClanDangerZoneBottomSheet {
            return ClanDangerZoneBottomSheet()
        }
    }
}
