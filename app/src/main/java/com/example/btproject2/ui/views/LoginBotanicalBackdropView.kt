package com.example.btproject2.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.PathParser
import com.example.btproject2.utils.ThemePreferences
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Full-screen botanical backdrop for LoginActivity:
 * - Warm Heritage Cream (Light Mode) or Dark Emerald & Gold (Dark Mode) radial gradient
 * - Lower Rooted Wooden Bough positioned cleanly in the open space between the Login Card
 *   and the Quote Pill, with entry branch growth, leaf sprout pop-in, continuous breeze flutter,
 *   and a swinging Family Node Charm with a rotating dashed gold ring.
 */
class LoginBotanicalBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class LeafKind { GREEN, GOLD, DUAL }

    private data class LeafPlacement(
        val x: Float,
        val y: Float,
        val rotationDeg: Float,
        val scale: Float,
        val kind: LeafKind,
        val phaseOffset: Float,
        val delayOrder: Int
    )

    private var isDark = true
    private var bgCenterColor = Color.parseColor("#132C1E")
    private var bgEdgeColor = Color.parseColor("#060D09")
    private var woodMainColor = Color.parseColor("#8F491F")
    private var woodDarkColor = Color.parseColor("#421D08")
    private var nodeBgColor = Color.parseColor("#0F2318")
    private var goldLeafColor = Color.parseColor("#E8AD3A")
    private var greenLeafColor = Color.parseColor("#26C958")
    private var greenLeafShineColor = Color.parseColor("#97F7A6")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
    }

    private val greenLeafBodyPath: Path = PathParser.createPathFromPathData(
        "M 0,0 C -8,-14 -4,-30 14,-38 C 12,-22 10,-8 0,0 Z"
    )
    private val greenLeafShinePath: Path = PathParser.createPathFromPathData(
        "M -2,-12 C -4,-20 0,-28 9,-33 C 5,-24 2,-16 -2,-12 Z"
    )
    private val greenLeafVeinsPath: Path = PathParser.createPathFromPathData(
        "M 0,0 Q 5,-18 13,-36 M 3,-10 L -3,-15 M 5,-18 L -1,-24 M 6,-14 L 10,-19 M 8,-23 L 12,-27"
    )

    private val goldLeafBodyPath: Path = PathParser.createPathFromPathData(
        "M 0,0 L -2,-6 L -8,-9 L -4,-15 L -9,-21 L -3,-26 L -6,-32 L 2,-30 L 6,-37 L 9,-29 L 14,-25 L 9,-19 L 13,-13 L 6,-9 L 3,-4 Z"
    )
    private val goldLeafVeinsPath: Path = PathParser.createPathFromPathData(
        "M 0,0 Q 3,-16 5,-32 M 2,-12 L -3,-16 M 3,-20 L 8,-23"
    )

    private val woodenBoughBodyPath: Path = PathParser.createPathFromPathData(
        "M 340,95 C 290,98 255,78 210,76 C 165,75 125,52 72,28 C 115,58 150,76 185,84 C 135,82 88,78 38,72 C 85,86 135,90 192,90 C 162,108 132,124 108,150 C 138,128 172,110 206,92 C 252,96 292,114 340,112 Z"
    )
    private val woodenBoughBarkPath: Path = PathParser.createPathFromPathData(
        "M 330,104 C 280,105 245,86 205,84 M 182,78 C 145,66 112,48 85,34 M 225,76 C 208,56 192,42 174,24 M 202,52 C 182,50 165,46 148,40 M 148,84 C 122,74 104,66 86,58 M 156,115 C 150,130 148,142 154,156"
    )

    private val nodeShouldersPath: Path = PathParser.createPathFromPathData(
        "M -4.5,4.8 C -4.5,1 4.5,1 4.5,4.8"
    )

    private val lowerBoughLeaves = listOf(
        LeafPlacement(248f, 592f, 32f, 0.74f, LeafKind.DUAL, 0.4f, 1),
        LeafPlacement(178f, 580f, -18f, 0.70f, LeafKind.DUAL, 1.1f, 2),
        LeafPlacement(108f, 558f, -25f, 0.74f, LeafKind.DUAL, 1.8f, 3),
        LeafPlacement(48f, 548f, -18f, 0.66f, LeafKind.GOLD, 2.3f, 4),
        LeafPlacement(274f, 558f, 52f, 0.68f, LeafKind.DUAL, 2.8f, 2),
        LeafPlacement(226f, 530f, 42f, 0.52f, LeafKind.GREEN, 1.5f, 5)
    )

    private var elapsedSec = 0f
    private var breezeAnimator: ValueAnimator? = null
    private val overshoot = OvershootInterpolator(1.45f)
    private val decelerate = DecelerateInterpolator()

    init {
        refreshThemePalette()
    }

    fun refreshThemePalette() {
        isDark = ThemePreferences.isDarkTheme(context)
        if (isDark) {
            bgCenterColor = Color.parseColor("#132C1E")
            bgEdgeColor = Color.parseColor("#060D09")
            woodMainColor = Color.parseColor("#8F491F")
            woodDarkColor = Color.parseColor("#421D08")
            nodeBgColor = Color.parseColor("#0F2318")
            goldLeafColor = Color.parseColor("#E8AD3A")
            greenLeafColor = Color.parseColor("#26C958")
            greenLeafShineColor = Color.parseColor("#97F7A6")
        } else {
            bgCenterColor = Color.parseColor("#FFF9DF")
            bgEdgeColor = Color.parseColor("#F2E2B0")
            woodMainColor = Color.parseColor("#7C3A16")
            woodDarkColor = Color.parseColor("#4A1F07")
            nodeBgColor = Color.parseColor("#FFFDF7")
            goldLeafColor = Color.parseColor("#E09F2D")
            greenLeafColor = Color.parseColor("#22B54C")
            greenLeafShineColor = Color.parseColor("#8CF09A")
        }
        updateShader(width, height)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshThemePalette()
        startGentleBreeze()
    }

    override fun onDetachedFromWindow() {
        breezeAnimator?.cancel()
        breezeAnimator = null
        super.onDetachedFromWindow()
    }

    private fun startGentleBreeze() {
        breezeAnimator?.cancel()
        val startNanos = System.nanoTime()
        breezeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000f
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader(w, h)
    }

    private fun updateShader(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        bgPaint.shader = RadialGradient(
            w * 0.5f,
            h * 0.35f,
            max(w, h) * 0.68f,
            bgCenterColor,
            bgEdgeColor,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Radial Heritage Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Map virtual 332x712 scene space proportionally
        val scaleX = w / 332f
        val scaleY = h / 712f
        val sceneScale = min(scaleX, scaleY)
        val offsetX = (w - 332f * sceneScale) * 0.5f
        val offsetY = (h - 712f * sceneScale) * 0.5f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(sceneScale, sceneScale)

        val branchEntry = decelerate.getInterpolation((elapsedSec / 0.70f).coerceIn(0f, 1f))
        val branchScale = 0.84f + 0.16f * branchEntry
        val sway = sin(elapsedSec * 1.6f) * 1.6f

        // 2. Lower Rooted Wooden Bough (Positioned cleanly above Quote Pill at y=505..595)
        canvas.save()
        canvas.scale(branchScale, branchScale, 0f, 575f)
        canvas.rotate(-sway, 0f, 575f)

        canvas.save()
        canvas.translate(332f, 616f)
        canvas.scale(-0.96f, -0.76f)
        drawWoodenBough(canvas)
        canvas.restore()

        // Cute Family Node Charm on Lower Bough (Swinging at x=226, y=548..510)
        val charmPop = overshoot.getInterpolation(((elapsedSec - 0.18f) / 0.55f).coerceIn(0f, 1f)).coerceAtLeast(0f)
        val charmSwing = sin(elapsedSec * 2.1f) * 5.5f
        canvas.save()
        canvas.rotate(charmSwing, 226f, 548f)
        canvas.scale(charmPop, charmPop, 226f, 548f)
        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.2f
        canvas.drawLine(226f, 548f, 226f, 510f, strokePaint)
        drawFamilyNodeCharm(canvas, 226f, 510f, elapsedSec * 28f)
        canvas.restore()

        for (leaf in lowerBoughLeaves) {
            drawLeafPlacement(canvas, leaf)
        }
        canvas.restore()

        canvas.restore()
    }

    private fun drawWoodenBough(canvas: Canvas) {
        fillPaint.color = woodMainColor
        canvas.drawPath(woodenBoughBodyPath, fillPaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.5f
        canvas.drawPath(woodenBoughBodyPath, strokePaint)

        strokePaint.strokeWidth = 1.6f
        canvas.drawPath(woodenBoughBarkPath, strokePaint)
    }

    private fun drawFamilyNodeCharm(canvas: Canvas, cx: Float, cy: Float, ringSpinDeg: Float) {
        canvas.save()
        canvas.translate(cx, cy)

        fillPaint.color = nodeBgColor
        canvas.drawCircle(0f, 0f, 12f, fillPaint)

        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.4f
        canvas.drawCircle(0f, 0f, 12f, strokePaint)

        canvas.save()
        canvas.rotate(ringSpinDeg)
        dashedPaint.color = goldLeafColor
        dashedPaint.strokeWidth = 1.2f
        canvas.drawCircle(0f, 0f, 8.2f, dashedPaint)
        canvas.restore()

        fillPaint.color = woodMainColor
        canvas.drawCircle(0f, -2.6f, 2.7f, fillPaint)
        canvas.drawPath(nodeShouldersPath, fillPaint)

        canvas.restore()
    }

    private fun drawLeafPlacement(canvas: Canvas, leaf: LeafPlacement) {
        val delaySec = 0.10f + leaf.delayOrder * 0.08f
        val raw = ((elapsedSec - delaySec) / 0.52f).coerceIn(0f, 1f)
        if (raw <= 0f) return
        val pop = overshoot.getInterpolation(raw).coerceAtLeast(0f)
        val breeze = sin(elapsedSec * 2.4f + leaf.phaseOffset) * 5.0f
        canvas.save()
        canvas.translate(leaf.x, leaf.y)
        canvas.rotate(leaf.rotationDeg + breeze)
        val s = leaf.scale * pop
        canvas.scale(s, s)
        when (leaf.kind) {
            LeafKind.GREEN -> drawSingleGreenLeaf(canvas)
            LeafKind.GOLD -> drawSingleGoldSerratedLeaf(canvas)
            LeafKind.DUAL -> drawDualLeafCluster(canvas)
        }
        canvas.restore()
    }

    private fun drawSingleGreenLeaf(canvas: Canvas) {
        fillPaint.color = greenLeafColor
        canvas.drawPath(greenLeafBodyPath, fillPaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.6f
        canvas.drawPath(greenLeafBodyPath, strokePaint)

        fillPaint.color = greenLeafShineColor
        fillPaint.alpha = 148
        canvas.drawPath(greenLeafShinePath, fillPaint)
        fillPaint.alpha = 255

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.1f
        canvas.drawPath(greenLeafVeinsPath, strokePaint)
    }

    private fun drawSingleGoldSerratedLeaf(canvas: Canvas) {
        fillPaint.color = goldLeafColor
        canvas.drawPath(goldLeafBodyPath, fillPaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.8f
        canvas.drawPath(goldLeafBodyPath, strokePaint)

        strokePaint.strokeWidth = 1.2f
        canvas.drawPath(goldLeafVeinsPath, strokePaint)

        fillPaint.color = woodDarkColor
        canvas.drawCircle(-1f, -19f, 1f, fillPaint)
        canvas.drawCircle(5f, -14f, 1f, fillPaint)
    }

    private fun drawDualLeafCluster(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-16f)
        canvas.scale(0.95f, 0.95f)
        drawSingleGoldSerratedLeaf(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(2f, 1f)
        canvas.rotate(12f)
        canvas.scale(0.95f, 0.95f)
        drawSingleGreenLeaf(canvas)
        canvas.restore()
    }
}
