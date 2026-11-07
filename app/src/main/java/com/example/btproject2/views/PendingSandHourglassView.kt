package com.example.btproject2.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Custom 60fps animated emblem view for Pending Tree Join Approval (Recommendation 1):
 * - Dark-gold circular heritage badge (#2B210B) with warm gold border (#D98A00).
 * - Flowing golden sand trickling from the top glass bulb into the bottom mound.
 * - Every 3.6 seconds when the sand completes falling, the hourglass smoothly flips 180°
 *   while emitting a subtle expanding golden ripple ring around the badge.
 * - Supports morphing into a glowing emerald checkmark crest when approved.
 */
class PendingSandHourglassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2B210B")
    }

    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#D98A00")
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#D4AF37")
    }

    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E2E8F0")
    }

    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(34, 186, 230, 253)
    }

    private val glassStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = Color.argb(195, 186, 230, 253)
    }

    private val sandFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F59E0B")
    }

    private val sandStreamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FBBF24")
    }

    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#4ADE80")
    }

    private val glassPath = Path()
    private val topSandPath = Path()
    private val bottomSandPath = Path()
    private val checkPath = Path()
    private val capRect = RectF()

    private var loopAnimator: ValueAnimator? = null
    private var elapsedSeconds = 0f
    private var isApprovedMode = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimationLoop()
    }

    override fun onDetachedFromWindow() {
        stopAnimationLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimationLoop()
        } else {
            stopAnimationLoop()
        }
    }

    fun setApprovedMode(approved: Boolean) {
        if (isApprovedMode != approved) {
            isApprovedMode = approved
            invalidate()
        }
    }

    private fun startAnimationLoop() {
        if (loopAnimator?.isRunning == true) return
        val startMs = System.currentTimeMillis()
        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000f
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimationLoop() {
        loopAnimator?.cancel()
        loopAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(cx, cy)
        val badgeRadius = maxRadius * 0.72f

        if (isApprovedMode) {
            circleFillPaint.color = Color.parseColor("#0F2E1E")
            circleStrokePaint.color = Color.parseColor("#2ECC71")
            val pulse = 1f + sin(elapsedSeconds * 4f) * 0.03f
            canvas.drawCircle(cx, cy, badgeRadius * pulse, circleFillPaint)
            canvas.drawCircle(cx, cy, badgeRadius * pulse, circleStrokePaint)

            checkPath.reset()
            checkPath.moveTo(cx - badgeRadius * 0.34f, cy + badgeRadius * 0.02f)
            checkPath.lineTo(cx - badgeRadius * 0.08f, cy + badgeRadius * 0.28f)
            checkPath.lineTo(cx + badgeRadius * 0.38f, cy - badgeRadius * 0.24f)
            canvas.drawPath(checkPath, checkPaint)
            return
        }

        circleFillPaint.color = Color.parseColor("#2B210B")
        circleStrokePaint.color = Color.parseColor("#D98A00")

        // 3.6s total cycle: 0..0.78 flowing sand, 0.78..1.0 smooth 180-degree flip + expanding gold ripple
        val cycleDuration = 3.6f
        val t = (elapsedSeconds % cycleDuration) / cycleDuration
        val sandPhase = min(1f, t / 0.78f)
        val flipProgress = if (t > 0.78f) (t - 0.78f) / 0.22f else 0f

        // Smooth ease-in-out cubic/quad for flip
        val easedFlip = if (flipProgress < 0.5f) {
            2f * flipProgress * flipProgress
        } else {
            1f - (-2f * flipProgress + 2f).pow(2) / 2f
        }
        val rotationDegrees = easedFlip * 180f

        // Draw expanding golden ripple ring during the 180° flip
        if (flipProgress > 0f) {
            val rippleRadius = badgeRadius + (maxRadius - badgeRadius) * easedFlip
            val rippleAlpha = ((1f - easedFlip) * 180f).toInt().coerceIn(0, 255)
            ripplePaint.alpha = rippleAlpha
            canvas.drawCircle(cx, cy, rippleRadius, ripplePaint)
        }

        // Draw circular dark-gold badge
        canvas.drawCircle(cx, cy, badgeRadius, circleFillPaint)
        canvas.drawCircle(cx, cy, badgeRadius, circleStrokePaint)

        // Draw hourglass inside badge
        val unit = badgeRadius / 42f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationDegrees)

        // Top and bottom silver-gold caps
        val capW = 17f * unit
        val capTopY = -23f * unit
        val capBotY = 19f * unit
        val capH = 4f * unit
        capRect.set(-capW, capTopY, capW, capTopY + capH)
        canvas.drawRoundRect(capRect, 2f * unit, 2f * unit, capPaint)
        capRect.set(-capW, capBotY, capW, capBotY + capH)
        canvas.drawRoundRect(capRect, 2f * unit, 2f * unit, capPaint)

        // Glass bulbs outline & fill
        glassPath.reset()
        glassPath.moveTo(-13.5f * unit, -19f * unit)
        glassPath.cubicTo(-13.5f * unit, -5.5f * unit, -3f * unit, -2f * unit, -1.8f * unit, 0f)
        glassPath.cubicTo(-3f * unit, 2f * unit, -13.5f * unit, 5.5f * unit, -13.5f * unit, 19f * unit)
        glassPath.lineTo(13.5f * unit, 19f * unit)
        glassPath.cubicTo(13.5f * unit, 5.5f * unit, 3f * unit, 2f * unit, 1.8f * unit, 0f)
        glassPath.cubicTo(3f * unit, -2f * unit, 13.5f * unit, -5.5f * unit, 13.5f * unit, -19f * unit)
        glassPath.close()
        canvas.drawPath(glassPath, glassFillPaint)
        canvas.drawPath(glassPath, glassStrokePaint)

        // Flowing sand inside top and bottom bulbs (only before flip starts)
        val topFill = max(0f, 1f - sandPhase)
        val botFill = min(1f, sandPhase)

        // Top sand drains down
        if (topFill > 0.04f) {
            val h = 13.5f * unit * topFill
            val w = 10.5f * unit * topFill.pow(0.45f)
            topSandPath.reset()
            topSandPath.moveTo(-w, -2.5f * unit - h)
            topSandPath.quadTo(0f, -0.8f * unit - h, w, -2.5f * unit - h)
            topSandPath.lineTo(1.4f * unit, -1f * unit)
            topSandPath.lineTo(-1.4f * unit, -1f * unit)
            topSandPath.close()
            canvas.drawPath(topSandPath, sandFillPaint)
        }

        // Falling golden sand stream in center
        if (sandPhase < 0.94f && flipProgress == 0f) {
            val grainOffset = (elapsedSeconds * 36f * unit) % (6f * unit)
            var y = -1f * unit + grainOffset * 0.4f
            while (y < 17.5f * unit) {
                val endY = min(18f * unit, y + 2.5f * unit)
                canvas.drawLine(0f, y, 0f, endY, sandStreamPaint)
                y += 4.8f * unit
            }
        }

        // Bottom sand mound grows
        if (botFill > 0.02f) {
            val bh = 13.5f * unit * botFill
            bottomSandPath.reset()
            bottomSandPath.moveTo(-11.5f * unit, 18.5f * unit)
            bottomSandPath.quadTo(0f, 18.5f * unit - bh * 1.35f, 11.5f * unit, 18.5f * unit)
            bottomSandPath.close()
            canvas.drawPath(bottomSandPath, sandFillPaint)
        }

        canvas.restore()
    }
}
