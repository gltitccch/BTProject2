package com.example.btproject2.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * HeritageCelebrationOverlayView — Lightweight zero-allocation celebration & memorial particle engine.
 *
 * Supports two distinct cultural modes:
 * - CONFETTI: Metallic gold, emerald, champagne, and rose confetti ribbons that flutter downward.
 * - SAMPAGUITA_PETALS: Soft white jasmine/sampaguita petals and gentle peace dove particles.
 */
class HeritageCelebrationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ParticleMode {
        NONE,
        CONFETTI,
        SAMPAGUITA_PETALS
    }

    private data class Particle(
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var w: Float = 20f,
        var h: Float = 36f,
        var rotation: Float = 0f,
        var vRot: Float = 0f,
        var color: Int = Color.YELLOW,
        var alpha: Int = 255,
        var isDove: Boolean = false
    )

    private val confettiColors = intArrayOf(
        Color.parseColor("#D4AF37"), // Heritage Gold
        Color.parseColor("#F5D77F"), // Bright Champagne
        Color.parseColor("#10B981"), // Emerald Green
        Color.parseColor("#FB7185"), // Rose Pink
        Color.parseColor("#FFFFFF"), // Crisp White
        Color.parseColor("#FFB703")  // Warm Amber
    )

    private val petalColors = intArrayOf(
        Color.parseColor("#FFFFFF"), // Pure White
        Color.parseColor("#FFFDF8"), // Cream Jasmine
        Color.parseColor("#FEF3C7"), // Soft Pearl
        Color.parseColor("#F1F5F9")  // Misty Pearl
    )

    private val density = context.resources.displayMetrics.density
    private val maxParticles = 36
    private val particles = Array(maxParticles) { Particle() }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f * density
        textAlign = Paint.Align.CENTER
    }
    private val tempRect = RectF()

    private var currentMode = ParticleMode.NONE
    private var isAnimating = false
    private var animStartTime = 0L
    private val maxDurationMs = 8000L // Run for up to 8 seconds

    init {
        // Prevent intercepting clicks intended for dialog buttons underneath
        isClickable = false
        isFocusable = false
    }

    fun startAnimation(mode: ParticleMode) {
        if (mode == ParticleMode.NONE) {
            stopAnimation()
            return
        }
        currentMode = mode
        animStartTime = SystemClock.uptimeMillis()
        isAnimating = true
        post {
            initParticles(scatterWholeScreen = true)
            invalidate()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        currentMode = ParticleMode.NONE
        invalidate()
    }

    private fun initParticles(scatterWholeScreen: Boolean) {
        val viewW = width.toFloat().coerceAtLeast(300f * density)
        val viewH = height.toFloat().coerceAtLeast(400f * density)
        val colors = if (currentMode == ParticleMode.CONFETTI) confettiColors else petalColors

        for (i in 0 until maxParticles) {
            val p = particles[i]
            p.x = Random.nextFloat() * viewW
            p.y = if (scatterWholeScreen) {
                Random.nextFloat() * viewH
            } else {
                -Random.nextFloat() * (120f * density)
            }
            p.vx = (Random.nextFloat() - 0.5f) * (2f * density)
            p.vy = if (currentMode == ParticleMode.CONFETTI) {
                (2.5f + Random.nextFloat() * 3.5f) * density
            } else {
                (1.2f + Random.nextFloat() * 1.8f) * density
            }
            p.w = if (currentMode == ParticleMode.CONFETTI) {
                (6f + Random.nextFloat() * 4f) * density
            } else {
                (9f + Random.nextFloat() * 5f) * density
            }
            p.h = if (currentMode == ParticleMode.CONFETTI) {
                (12f + Random.nextFloat() * 8f) * density
            } else {
                (11f + Random.nextFloat() * 6f) * density
            }
            p.rotation = Random.nextFloat() * 360f
            p.vRot = (Random.nextFloat() - 0.5f) * 7f
            p.color = colors[Random.nextInt(colors.size)]
            p.alpha = 220 + Random.nextInt(35)
            p.isDove = (currentMode == ParticleMode.SAMPAGUITA_PETALS && i % 7 == 0)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isAnimating) {
            initParticles(scatterWholeScreen = false)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAnimating || currentMode == ParticleMode.NONE) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val elapsed = SystemClock.uptimeMillis() - animStartTime

        // Check if duration expired
        if (elapsed > maxDurationMs) {
            isAnimating = false
            return
        }

        // Global fade-out during last 1.5 seconds
        val globalAlphaFactor = if (elapsed > maxDurationMs - 1500L) {
            ((maxDurationMs - elapsed).toFloat() / 1500f).coerceIn(0f, 1f)
        } else {
            1f
        }

        for (i in 0 until maxParticles) {
            val p = particles[i]
            p.y += p.vy
            p.x += p.vx
            p.rotation += p.vRot

            // Gentle horizontal fluttering for petals
            if (currentMode == ParticleMode.SAMPAGUITA_PETALS) {
                p.x += Math.sin((p.y / (20f * density)).toDouble()).toFloat() * (0.8f * density)
            }

            // Recycle to top if drifted past the bottom
            if (p.y > viewH + (20f * density)) {
                p.y = -(20f * density)
                p.x = Random.nextFloat() * viewW
            }

            val finalAlpha = (p.alpha * globalAlphaFactor).toInt().coerceIn(0, 255)
            if (finalAlpha <= 0) continue

            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)

            if (p.isDove) {
                textPaint.alpha = finalAlpha
                canvas.drawText("🕊️", 0f, 0f, textPaint)
            } else {
                particlePaint.color = p.color
                particlePaint.alpha = finalAlpha
                val rx = if (currentMode == ParticleMode.CONFETTI) 2f * density else 5f * density
                val ry = if (currentMode == ParticleMode.CONFETTI) 2f * density else 5f * density
                tempRect.set(-p.w / 2f, -p.h / 2f, p.w / 2f, p.h / 2f)
                canvas.drawRoundRect(tempRect, rx, ry, particlePaint)
            }

            canvas.restore()
        }

        // Keep 60fps loop running while active
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAnimating = false
    }
}
