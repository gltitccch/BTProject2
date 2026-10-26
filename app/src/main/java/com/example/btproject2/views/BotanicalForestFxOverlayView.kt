package com.example.btproject2.views

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.example.btproject2.utils.BotanicalRolePolicy
import kotlin.math.sin
import kotlin.random.Random

/**
 * 60fps Zero-Allocation Botanical Particle & Messenger Leaf Overlay View.
 *
 * Uses a fixed pre-allocated array of [BotanicalRolePolicy.MAX_PARTICLE_POOL_SIZE] leaf slots
 * so zero objects are allocated during the 60fps main-looper loop.
 * Automatically stops its frame callback the moment all leaves fade out or the view detaches.
 */
class BotanicalForestFxOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    // Pre-allocated Fixed Particle Pool (20 slots max -> 0 GC pressure)
    private val poolSize = BotanicalRolePolicy.MAX_PARTICLE_POOL_SIZE
    private val leafActive = BooleanArray(poolSize)
    private val leafX = FloatArray(poolSize)
    private val leafY = FloatArray(poolSize)
    private val leafVx = FloatArray(poolSize)
    private val leafVy = FloatArray(poolSize)
    private val leafPhase = FloatArray(poolSize)
    private val leafSwayAmp = FloatArray(poolSize)
    private val leafSize = FloatArray(poolSize)
    private val leafColor = IntArray(poolSize)
    private val leafRotDeg = FloatArray(poolSize)
    private val leafVRotDeg = FloatArray(poolSize)
    private val leafAlpha = FloatArray(poolSize)

    // Pre-allocated single Tree-Trunk Ring Ripple
    private var rippleActive = false
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleRadius = 0f
    private var rippleMaxRadius = 0f
    private var rippleAlpha = 0f
    private var rippleColor = Color.parseColor("#D4AF37")

    // Pre-allocated Wind-Borne Golden Messenger Leaf
    private var messengerActive = false
    private var messengerT = 0f
    private var messengerStartX = 0f
    private var messengerStartY = 0f
    private var messengerCtrlX = 0f
    private var messengerCtrlY = 0f
    private var messengerEndX = 0f
    private var messengerEndY = 0f
    private var messengerArriveCallback: (() -> Unit)? = null

    private var loopRunning = false

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(90, 6, 13, 9)
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val reusableLeafPath = Path()

    private val palette = intArrayOf(
        Color.parseColor("#D4AF37"),
        Color.parseColor("#34D399"),
        Color.parseColor("#4ADE80"),
        Color.parseColor("#E6C762")
    )

    private val frameTicker = object : Runnable {
        override fun run() {
            if (!loopRunning || !isAttachedToWindow || visibility != VISIBLE) {
                stopFxLoop()
                return
            }

            val stillAnimating = stepPhysics()
            invalidate()

            if (stillAnimating) {
                mainHandler.postDelayed(this, 16L)
            } else {
                stopFxLoop()
            }
        }
    }

    fun triggerTreeRingAndLeaves(centerX: Float, centerY: Float, accentColor: Int) {
        rippleActive = true
        rippleX = centerX
        rippleY = centerY
        rippleRadius = 14f * density
        rippleMaxRadius = 380f * density
        rippleAlpha = 0.82f
        rippleColor = accentColor

        spawnDriftingLeaves(count = 16, originX = centerX, originY = centerY, primaryColor = accentColor)
        ensureFxLoop()
    }

    fun launchGoldenMessengerLeaf(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        onArrive: () -> Unit
    ) {
        messengerActive = true
        messengerT = 0f
        messengerStartX = startX
        messengerStartY = startY
        messengerCtrlX = (startX * 0.35f)
        messengerCtrlY = (startY + endY) * 0.45f
        messengerEndX = endX
        messengerEndY = endY
        messengerArriveCallback = onArrive
        ensureFxLoop()
    }

    private fun spawnDriftingLeaves(count: Int, originX: Float, originY: Float, primaryColor: Int) {
        var spawned = 0
        val safeCount = count.coerceAtMost(poolSize)
        for (i in 0 until poolSize) {
            if (spawned >= safeCount) break
            leafActive[i] = true
            leafX[i] = originX + (Random.nextFloat() - 0.5f) * 240f * density
            leafY[i] = originY + (Random.nextFloat() - 0.5f) * 160f * density
            leafVx[i] = (Random.nextFloat() - 0.5f) * 1.8f * density
            leafVy[i] = (0.8f + Random.nextFloat() * 2.2f) * density
            leafPhase[i] = Random.nextFloat() * 6.28f
            leafSwayAmp[i] = (0.8f + Random.nextFloat() * 1.4f) * density
            leafSize[i] = (5.5f + Random.nextFloat() * 4.5f) * density
            leafColor[i] = if (i % 3 == 0) primaryColor else palette[i % palette.size]
            leafRotDeg[i] = Random.nextFloat() * 360f
            leafVRotDeg[i] = (Random.nextFloat() - 0.5f) * 4.5f
            leafAlpha[i] = 1f
            spawned++
        }
    }

    private fun stepPhysics(): Boolean {
        var anyActive = false

        if (rippleActive) {
            rippleRadius += (rippleMaxRadius - rippleRadius) * 0.12f
            rippleAlpha *= 0.91f
            if (rippleAlpha < 0.03f) {
                rippleActive = false
            } else {
                anyActive = true
            }
        }

        for (i in 0 until poolSize) {
            if (!leafActive[i]) continue
            leafPhase[i] += 0.06f
            leafX[i] += leafVx[i] + sin(leafPhase[i]) * leafSwayAmp[i]
            leafY[i] += leafVy[i]
            leafRotDeg[i] += leafVRotDeg[i]
            leafAlpha[i] -= 0.015f
            if (leafAlpha[i] <= 0.02f) {
                leafActive[i] = false
            } else {
                anyActive = true
            }
        }

        if (messengerActive) {
            messengerT += 0.032f
            if (messengerT >= 1f) {
                messengerActive = false
                spawnDriftingLeaves(
                    count = 18,
                    originX = messengerEndX,
                    originY = messengerEndY,
                    primaryColor = Color.parseColor("#34D399")
                )
                val cb = messengerArriveCallback
                messengerArriveCallback = null
                cb?.invoke()
            }
            anyActive = true
        }

        return anyActive
    }

    private fun ensureFxLoop() {
        if (loopRunning) return
        loopRunning = true
        mainHandler.removeCallbacks(frameTicker)
        mainHandler.post(frameTicker)
    }

    fun stopFxLoop() {
        loopRunning = false
        try {
            mainHandler.removeCallbacks(frameTicker)
        } catch (_: Throwable) {}
    }

    override fun onDetachedFromWindow() {
        stopFxLoop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            if (rippleActive) {
                ripplePaint.color = rippleColor
                ripplePaint.alpha = (rippleAlpha * 255).toInt().coerceIn(0, 255)
                ripplePaint.strokeWidth = rippleAlpha * 10f * density
                canvas.drawCircle(rippleX, rippleY, rippleRadius, ripplePaint)
            }

            for (i in 0 until poolSize) {
                if (!leafActive[i]) continue
                val alphaInt = (leafAlpha[i] * 255).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(leafX[i], leafY[i])
                canvas.rotate(leafRotDeg[i])
                drawBotanicalLeaf(canvas, leafSize[i], leafColor[i], alphaInt)
                canvas.restore()
            }

            if (messengerActive) {
                val t = messengerT.coerceIn(0f, 1f)
                val inv = 1f - t
                val x = inv * inv * messengerStartX +
                        2f * inv * t * messengerCtrlX +
                        t * t * messengerEndX +
                        sin(t * 14f) * (10f * density)
                val y = inv * inv * messengerStartY +
                        2f * inv * t * messengerCtrlY +
                        t * t * messengerEndY

                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(sin(t * 10f) * 28f)
                drawBotanicalLeaf(canvas, 13f * density, Color.parseColor("#D4AF37"), 255)
                canvas.restore()
            }
        } catch (_: Throwable) {}
    }

    private fun drawBotanicalLeaf(canvas: Canvas, size: Float, color: Int, alpha: Int) {
        reusableLeafPath.reset()
        reusableLeafPath.moveTo(0f, -size)
        reusableLeafPath.quadTo(size * 0.85f, 0f, 0f, size)
        reusableLeafPath.quadTo(-size * 0.85f, 0f, 0f, -size)
        reusableLeafPath.close()

        leafPaint.color = color
        leafPaint.alpha = alpha
        canvas.drawPath(reusableLeafPath, leafPaint)

        veinPaint.alpha = (alpha * 0.45f).toInt()
        canvas.drawLine(0f, -size * 0.75f, 0f, size * 0.75f, veinPaint)
    }
}
