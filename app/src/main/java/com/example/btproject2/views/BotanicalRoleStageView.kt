package com.example.btproject2.views

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.example.btproject2.utils.BotanicalRolePolicy
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 60fps Hardware-Accelerated Botanical Family-Tree Role Stage View (Direction 1).
 *
 * Draws the procedural 3-tier growing family tree (🌱 Sprout -> 🌿 Branch -> 🌳 Canopy)
 * with gently swaying leaves, glowing forest roots, and 6 continuously rising firefly spores.
 *
 * Uses a self-driving main-looper wall-clock ticker (`SystemClock.uptimeMillis()`) so the
 * breeze and fireflies animate continuously on their own without requiring touch or mouse movement,
 * while immediately shutting off to 0% CPU when the dialog closes ([onDetachedFromWindow]).
 */
class BotanicalRoleStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentTier: BotanicalRolePolicy.BotanicalTier = BotanicalRolePolicy.BotanicalTier.SPROUT_VIEWER
    private var isRunning = false
    private var startTimeMs = 0L
    private var animElapsedSeconds = 0f

    var onFrameTickListener: ((Float) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    // Pre-allocated Paints & Paths (Guardrail #1: 0 allocations in onDraw)
    private val soilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.1f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val rootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        color = Color.argb(120, 212, 175, 55)
        strokeCap = Paint.Cap.ROUND
    }

    private val branchUnlockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val branchLockedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(92, 212, 175, 55)
        pathEffect = DashPathEffect(floatArrayOf(3.2f * density, 3.2f * density), 0f)
    }

    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val leafVeinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(90, 6, 13, 9)
    }

    private val sporePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val sporeHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val reusableLeafPath = Path()
    private val reusableRootPath = Path()
    private val soilArcRect = RectF()

    private val frameTicker = object : Runnable {
        override fun run() {
            if (!isRunning || !isAttachedToWindow || visibility != VISIBLE) {
                stopAnimationLoop()
                return
            }

            val nowMs = SystemClock.uptimeMillis()
            if (startTimeMs == 0L) {
                startTimeMs = nowMs
            }

            val totalElapsedMs = nowMs - startTimeMs
            animElapsedSeconds = totalElapsedMs / 1000f

            onFrameTickListener?.invoke(animElapsedSeconds)
            invalidate()

            if (totalElapsedMs < BotanicalRolePolicy.AUTO_REST_TIMEOUT_MS) {
                mainHandler.postDelayed(this, 16L)
            } else {
                stopAnimationLoop()
            }
        }
    }

    init {
        setOnClickListener {
            restartGrowthAnimation()
        }
    }

    fun setBotanicalRole(rawRole: String?, isTreeOwner: Boolean = false) {
        currentTier = BotanicalRolePolicy.resolveTier(rawRole, isTreeOwner)
        restartGrowthAnimation()
    }

    fun setBotanicalTier(tier: BotanicalRolePolicy.BotanicalTier) {
        currentTier = tier
        restartGrowthAnimation()
    }

    fun restartGrowthAnimation() {
        startTimeMs = SystemClock.uptimeMillis()
        animElapsedSeconds = 0f
        startAnimationLoop()
    }

    private fun startAnimationLoop() {
        if (isRunning) return
        isRunning = true
        mainHandler.removeCallbacks(frameTicker)
        mainHandler.post(frameTicker)
    }

    fun stopAnimationLoop() {
        isRunning = false
        try {
            mainHandler.removeCallbacks(frameTicker)
        } catch (_: Throwable) {
            // Safe ignore in unit test or detached environments
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartGrowthAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimationLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            stopAnimationLoop()
        } else if (isAttachedToWindow) {
            startAnimationLoop()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Guardrail #5: Fail-safe try/catch around the entire draw pass
        try {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 10f || h <= 10f) return

            val cx = w * 0.54f
            val groundY = h * 0.89f
            val growthProgress = min(1f, animElapsedSeconds / 1.15f).coerceAtLeast(0.15f)
            val breeze = sin(animElapsedSeconds * 2.2f) * 0.065f

            // 1. Draw glowing forest soil mound & spreading golden roots
            soilPaint.color = if (currentTier == BotanicalRolePolicy.BotanicalTier.CANOPY_OWNER) {
                Color.argb(175, 212, 175, 55)
            } else {
                Color.argb(175, 52, 211, 153)
            }
            val moundRadius = 48f * density
            soilArcRect.set(
                cx - moundRadius,
                groundY - 4f * density,
                cx + moundRadius,
                groundY + moundRadius * 1.4f
            )
            canvas.drawArc(soilArcRect, 212f, 116f, false, soilPaint)

            for (r in -2..2) {
                if (r == 0) continue
                reusableRootPath.reset()
                reusableRootPath.moveTo(cx, groundY)
                reusableRootPath.quadTo(
                    cx + r * 14f * density,
                    groundY + 8f * density,
                    cx + r * 26f * density * growthProgress,
                    groundY + 14f * density
                )
                canvas.drawPath(reusableRootPath, rootPaint)
            }

            // 2. Draw 3-tier procedural growing family tree (Hard-capped at MAX_TREE_RECURSION_DEPTH = 3)
            val unlockedTier = currentTier.tierLevel
            val trunkLen = min(h * 0.25f, 36f * density)
            drawBranchRecursive(
                canvas = canvas,
                x = cx,
                y = groundY,
                len = trunkLen,
                angleRad = 0f,
                depth = BotanicalRolePolicy.MAX_TREE_RECURSION_DEPTH,
                maxDepth = BotanicalRolePolicy.MAX_TREE_RECURSION_DEPTH,
                growthProgress = growthProgress,
                breeze = breeze,
                unlockedTier = unlockedTier
            )

            // 3. Draw 6 continuously rising glowing firefly spores around the tree (matches Photo 2)
            val maxTravel = h * 0.75f
            for (i in 0 until 6) {
                val fy = groundY - (((animElapsedSeconds * 22f * density) + i * 24f * density) % maxTravel)
                val fx = cx + sin(animElapsedSeconds * 2.0f + i * 1.3f) * (66f * density)
                val isGreenSpore = (i % 2 == 0)
                if (isGreenSpore) {
                    sporeHaloPaint.color = Color.argb(50, 74, 222, 128)
                    sporePaint.color = Color.argb(210, 74, 222, 128)
                } else {
                    sporeHaloPaint.color = Color.argb(50, 212, 175, 55)
                    sporePaint.color = Color.argb(210, 212, 175, 55)
                }
                canvas.drawCircle(fx, fy, 4.4f * density, sporeHaloPaint)
                canvas.drawCircle(fx, fy, 2.3f * density, sporePaint)
            }

            // 4. Draw Left-Side Botanical Growth Stage Labels
            val labelX = 8f * density
            // Tier 1: Sprout (Viewer)
            labelPaint.color = Color.parseColor("#4ADE80")
            canvas.drawText("🌱 Sprout (Viewer)", labelX, h * 0.80f, labelPaint)

            // Tier 2: Branch (Editor)
            if (unlockedTier >= 2) {
                labelPaint.color = Color.parseColor("#34D399")
                canvas.drawText("🌿 Branch (Editor)", labelX, h * 0.51f, labelPaint)
            } else {
                labelPaint.color = Color.argb(165, 212, 175, 55)
                canvas.drawText("🔒 Branch (Editor)", labelX, h * 0.51f, labelPaint)
            }

            // Tier 3: Canopy (Owner)
            if (unlockedTier >= 3) {
                labelPaint.color = Color.parseColor("#D4AF37")
                canvas.drawText("🌳 Canopy (Owner)", labelX, h * 0.23f, labelPaint)
            } else {
                labelPaint.color = Color.argb(165, 212, 175, 55)
                canvas.drawText("🔒 Canopy (Owner)", labelX, h * 0.23f, labelPaint)
            }
        } catch (_: Throwable) {
            // Fail-safe: never allow a graphics exception to crash the host Activity
        }
    }

    private fun drawBranchRecursive(
        canvas: Canvas,
        x: Float,
        y: Float,
        len: Float,
        angleRad: Float,
        depth: Int,
        maxDepth: Int,
        growthProgress: Float,
        breeze: Float,
        unlockedTier: Int
    ) {
        if (depth <= 0) return

        val tierOfBranch = maxDepth - depth + 1
        val tierAppearT = ((growthProgress * 3f) - (tierOfBranch - 1) * 0.72f).coerceIn(0f, 1f)
        if (tierAppearT <= 0.01f) return

        val isUnlocked = tierOfBranch <= unlockedTier
        val curLen = len * tierAppearT
        val swayAngle = angleRad + breeze * tierOfBranch
        val nx = x + sin(swayAngle) * curLen
        val ny = y - cos(swayAngle) * curLen

        if (isUnlocked) {
            branchUnlockedPaint.color = if (tierOfBranch == 3) {
                Color.parseColor("#D4AF37")
            } else {
                Color.parseColor("#34D399")
            }
            branchUnlockedPaint.strokeWidth = depth * 2.15f * density
            canvas.drawLine(x, y, nx, ny, branchUnlockedPaint)
        } else {
            branchLockedPaint.strokeWidth = depth * 1.6f * density
            canvas.drawLine(x, y, nx, ny, branchLockedPaint)
        }

        // Draw pointed botanical leaf at the node
        if (tierAppearT > 0.58f) {
            val leafSize = (if (isUnlocked) 7.5f else 5.5f) * density
            val leafColor = if (isUnlocked) {
                if (tierOfBranch == 3) Color.parseColor("#D4AF37") else Color.parseColor("#4ADE80")
            } else {
                Color.argb(80, 212, 175, 55)
            }
            val leafRotDeg = Math.toDegrees((swayAngle + sin(animElapsedSeconds * 3f + depth) * 0.16f).toDouble()).toFloat()

            canvas.save()
            canvas.translate(nx, ny)
            canvas.rotate(leafRotDeg)
            drawBotanicalLeaf(canvas, leafSize, leafColor)
            canvas.restore()
        }

        if (depth > 1) {
            drawBranchRecursive(
                canvas, nx, ny, len * 0.74f, swayAngle - 0.48f,
                depth - 1, maxDepth, growthProgress, breeze, unlockedTier
            )
            drawBranchRecursive(
                canvas, nx, ny, len * 0.74f, swayAngle + 0.48f,
                depth - 1, maxDepth, growthProgress, breeze, unlockedTier
            )
        }
    }

    private fun drawBotanicalLeaf(canvas: Canvas, size: Float, color: Int) {
        reusableLeafPath.reset()
        reusableLeafPath.moveTo(0f, -size)
        reusableLeafPath.quadTo(size * 0.85f, 0f, 0f, size)
        reusableLeafPath.quadTo(-size * 0.85f, 0f, 0f, -size)
        reusableLeafPath.close()

        leafPaint.color = color
        canvas.drawPath(reusableLeafPath, leafPaint)
        canvas.drawLine(0f, -size * 0.75f, 0f, size * 0.75f, leafVeinPaint)
    }
}
