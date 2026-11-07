package com.example.btproject2.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.PathParser
import com.example.btproject2.utils.ThemePreferences
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the exact Georgia Bold "KinTrace" wordmark (with 2px 3D bevel highlight)
 * wrapped in sculpted upper and lower wooden branches, layered dual-leaf clusters,
 * and the two cute dangling Family Node Locket Charms with full living animations:
 * - Entry branch growth + leaf sprout pop-in
 * - Continuous leaf breeze flutter on all 11 leaf clusters
 * - Gentle pendulum swing + rotating gold ring on the 2 hanging Family Node Charms
 */
class KinTraceWrappedHeaderView @JvmOverloads constructor(
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
        val delayOrder: Int
    )

    private var woodMainColor = Color.parseColor("#8F491F")
    private var woodDarkColor = Color.parseColor("#421D08")
    private var titleColor = Color.parseColor("#F6EFE2")
    private var titleBevelColor = Color.argb(115, 255, 255, 255)
    private var titleAmbientShadowColor = Color.argb(48, 0, 0, 0)
    private var nodeBgColor = Color.parseColor("#0F2318")
    private var goldLeafColor = Color.parseColor("#E8AD3A")
    private var greenLeafColor = Color.parseColor("#26C958")
    private var greenLeafShineColor = Color.parseColor("#97F7A6")

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

    private val titleUpperBranchPath: Path = PathParser.createPathFromPathData(
        "M 18,46 C 42,16 98,10 160,14 C 212,17 252,25 282,44 C 250,30 208,24 158,22 C 98,20 48,26 18,46 Z"
    )
    private val titleUpperTwigsPath: Path = PathParser.createPathFromPathData(
        "M 76,20 C 62,11 48,6 34,4 M 196,20 C 212,10 226,6 242,6"
    )
    private val titleLowerBranchPath: Path = PathParser.createPathFromPathData(
        "M 12,72 C 46,100 108,106 172,101 C 224,97 262,85 286,65 C 256,79 218,88 168,92 C 108,96 50,90 12,72 Z"
    )
    private val titleLowerTwigsPath: Path = PathParser.createPathFromPathData(
        "M 28,78 C 8,64 6,42 24,32 M 272,74 C 290,60 288,38 270,30 M 118,96 C 136,106 154,110 174,108"
    )

    private val nodeShouldersPath: Path = PathParser.createPathFromPathData(
        "M -4.2,4.5 C -4.2,1 4.2,1 4.2,4.5"
    )

    private val kintraceGeorgiaBoldPath: Path = PathParser.createPathFromPathData(
        KINTRACE_GEORGIA_BOLD_SVG
    ).apply {
        fillType = Path.FillType.EVEN_ODD
    }

    private val headerLeaves = listOf(
        LeafPlacement(18f, 42f, -52f, 0.72f, LeafKind.DUAL, 1),
        LeafPlacement(56f, 15f, -25f, 0.68f, LeafKind.DUAL, 2),
        LeafPlacement(118f, 14f, 15f, 0.64f, LeafKind.GREEN, 3),
        LeafPlacement(182f, 14f, -18f, 0.64f, LeafKind.GOLD, 4),
        LeafPlacement(232f, 14f, 28f, 0.68f, LeafKind.DUAL, 2),
        LeafPlacement(280f, 42f, 52f, 0.72f, LeafKind.DUAL, 5),
        LeafPlacement(42f, 90f, -135f, 0.70f, LeafKind.DUAL, 3),
        LeafPlacement(98f, 98f, -115f, 0.64f, LeafKind.GREEN, 1),
        LeafPlacement(162f, 104f, 115f, 0.72f, LeafKind.DUAL, 4),
        LeafPlacement(216f, 96f, 125f, 0.64f, LeafKind.GOLD, 2),
        LeafPlacement(262f, 84f, 138f, 0.70f, LeafKind.DUAL, 5)
    )

    private var elapsedSec = 0f
    private var headerAnimator: ValueAnimator? = null
    private val overshoot = OvershootInterpolator(1.45f)
    private val decelerate = DecelerateInterpolator()

    init {
        refreshThemePalette()
    }

    fun refreshThemePalette() {
        val isDark = ThemePreferences.isDarkTheme(context)
        if (isDark) {
            woodMainColor = Color.parseColor("#8F491F")
            woodDarkColor = Color.parseColor("#421D08")
            titleColor = Color.parseColor("#F6EFE2")
            titleBevelColor = Color.argb(115, 255, 255, 255)
            titleAmbientShadowColor = Color.argb(48, 0, 0, 0)
            nodeBgColor = Color.parseColor("#0F2318")
            goldLeafColor = Color.parseColor("#E8AD3A")
            greenLeafColor = Color.parseColor("#26C958")
            greenLeafShineColor = Color.parseColor("#97F7A6")
        } else {
            woodMainColor = Color.parseColor("#7C3A16")
            woodDarkColor = Color.parseColor("#4A1F07")
            titleColor = Color.parseColor("#6B2D06")
            titleBevelColor = Color.argb(145, 255, 255, 255)
            titleAmbientShadowColor = Color.argb(36, 107, 45, 6)
            nodeBgColor = Color.parseColor("#FFFDF7")
            goldLeafColor = Color.parseColor("#E09F2D")
            greenLeafColor = Color.parseColor("#22B54C")
            greenLeafShineColor = Color.parseColor("#8CF09A")
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshThemePalette()
        startHeaderAnimation()
    }

    override fun onDetachedFromWindow() {
        headerAnimator?.cancel()
        headerAnimator = null
        super.onDetachedFromWindow()
    }

    private fun startHeaderAnimation() {
        headerAnimator?.cancel()
        val startNanos = System.nanoTime()
        headerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Use 340 x 134 viewBox so the two cute dangling Family Node Charms on the left & right fit cleanly!
        val scale = min(w / 340f, h / 134f)
        val dx = (w - 300f * scale) * 0.5f
        val dy = (h - 126f * scale) * 0.4f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        // Entry growth + continuous subtle branch breathing sway
        val branchEntry = decelerate.getInterpolation((elapsedSec / 0.65f).coerceIn(0f, 1f))
        val branchScale = 0.86f + 0.14f * branchEntry
        val upperSway = sin(elapsedSec * 1.8f) * 1.3f
        val lowerSway = -sin(elapsedSec * 1.7f + 0.6f) * 1.3f

        // Upper Branch & Twigs
        canvas.save()
        canvas.scale(branchScale, branchScale, 150f, 46f)
        canvas.rotate(upperSway, 150f, 30f)
        fillPaint.color = woodMainColor
        canvas.drawPath(titleUpperBranchPath, fillPaint)
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.4f
        canvas.drawPath(titleUpperBranchPath, strokePaint)

        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.2f
        canvas.drawPath(titleUpperTwigsPath, strokePaint)
        canvas.restore()

        // Lower Branch & Twigs
        canvas.save()
        canvas.scale(branchScale, branchScale, 150f, 72f)
        canvas.rotate(lowerSway, 150f, 84f)
        fillPaint.color = woodMainColor
        canvas.drawPath(titleLowerBranchPath, fillPaint)
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.4f
        canvas.drawPath(titleLowerBranchPath, strokePaint)

        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.4f
        canvas.drawPath(titleLowerTwigsPath, strokePaint)
        canvas.restore()

        // Cute Dangling Family Node Charm #1 (Swinging pendulum from left branch tip at x=12, y=70..114)
        val charmPop = overshoot.getInterpolation(((elapsedSec - 0.15f) / 0.55f).coerceIn(0f, 1f)).coerceAtLeast(0f)
        val leftSwingDeg = sin(elapsedSec * 2.2f) * 6.5f
        canvas.save()
        canvas.rotate(leftSwingDeg, 12f, 70f)
        canvas.scale(charmPop, charmPop, 12f, 70f)
        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.1f
        canvas.drawLine(12f, 70f, 12f, 114f, strokePaint)
        drawFamilyNodeCharm(canvas, 12f, 114f, elapsedSec * 28f)
        canvas.restore()

        // Cute Dangling Family Node Charm #2 (Swinging pendulum from right branch tip at x=286, y=64..114)
        val rightSwingDeg = -sin(elapsedSec * 2.2f + 0.8f) * 6.5f
        canvas.save()
        canvas.rotate(rightSwingDeg, 286f, 64f)
        canvas.scale(charmPop, charmPop, 286f, 64f)
        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.1f
        canvas.drawLine(286f, 64f, 286f, 114f, strokePaint)
        drawFamilyNodeCharm(canvas, 286f, 114f, -elapsedSec * 28f)
        canvas.restore()

        // Animated Leaves Sprouting & Fluttering Around "KinTrace"
        for ((idx, leaf) in headerLeaves.withIndex()) {
            val delaySec = 0.08f + leaf.delayOrder * 0.07f
            val raw = ((elapsedSec - delaySec) / 0.50f).coerceIn(0f, 1f)
            if (raw > 0f) {
                val pop = overshoot.getInterpolation(raw).coerceAtLeast(0f)
                val breezeDeg = sin(elapsedSec * 2.5f + idx * 0.7f) * 5.2f
                canvas.save()
                canvas.translate(leaf.x, leaf.y)
                canvas.rotate(leaf.rotationDeg + breezeDeg)
                val s = leaf.scale * pop
                canvas.scale(s, s)
                when (leaf.kind) {
                    LeafKind.GREEN -> drawSingleGreenLeaf(canvas)
                    LeafKind.GOLD -> drawSingleGoldSerratedLeaf(canvas)
                    LeafKind.DUAL -> drawDualLeafCluster(canvas)
                }
                canvas.restore()
            }
        }

        // Exact Georgia Bold "KinTrace" Wordmark with 2px 3D Bevel Highlight & Gentle Breathing Float
        val wordPop = decelerate.getInterpolation(((elapsedSec - 0.04f) / 0.48f).coerceIn(0f, 1f))
        val ws = 0.90f + 0.10f * wordPop
        val floatY = sin(elapsedSec * 1.9f) * 1.1f
        canvas.save()
        canvas.translate(0f, floatY)
        canvas.scale(ws, ws, 150f, 58f)

        canvas.save()
        canvas.translate(0f, 3.6f)
        fillPaint.color = titleAmbientShadowColor
        canvas.drawPath(kintraceGeorgiaBoldPath, fillPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, 1.85f)
        fillPaint.color = titleBevelColor
        canvas.drawPath(kintraceGeorgiaBoldPath, fillPaint)
        canvas.restore()

        fillPaint.color = titleColor
        canvas.drawPath(kintraceGeorgiaBoldPath, fillPaint)
        canvas.restore()

        canvas.restore()
    }

    private fun drawFamilyNodeCharm(canvas: Canvas, cx: Float, cy: Float, ringSpinDeg: Float) {
        canvas.save()
        canvas.translate(cx, cy)

        fillPaint.color = nodeBgColor
        canvas.drawCircle(0f, 0f, 11.5f, fillPaint)

        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.3f
        canvas.drawCircle(0f, 0f, 11.5f, strokePaint)

        // Slowly rotating inner gold dashed ring
        canvas.save()
        canvas.rotate(ringSpinDeg)
        dashedPaint.color = goldLeafColor
        dashedPaint.strokeWidth = 1.2f
        canvas.drawCircle(0f, 0f, 7.8f, dashedPaint)
        canvas.restore()

        fillPaint.color = woodMainColor
        canvas.drawCircle(0f, -2.5f, 2.5f, fillPaint)
        canvas.drawPath(nodeShouldersPath, fillPaint)

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

    companion object {
        private const val KINTRACE_GEORGIA_BOLD_SVG =
            "M 89.69,72.06 L 79.26,72.06 C 77.69,69.65 76.28,67.64 75.01,66.02 C 73.75,64.40 72.14,62.36 70.17,59.91 L 69.21,59.91 L 69.21,68.19 C 69.21,68.63 69.29,69.01 69.45,69.32 C 69.61,69.63 69.93,69.89 70.41,70.10 C 70.64,70.20 70.97,70.30 71.41,70.39 C 71.84,70.49 72.28,70.57 72.71,70.62 L 72.71,72.06 L 58.72,72.06 L 58.72,70.62 C 59.06,70.59 59.53,70.56 60.14,70.51 C 60.76,70.46 61.19,70.38 61.43,70.28 C 61.88,70.11 62.19,69.87 62.36,69.56 C 62.53,69.25 62.62,68.86 62.62,68.39 L 62.62,49.72 C 62.62,49.29 62.55,48.93 62.40,48.63 C 62.25,48.33 61.93,48.07 61.43,47.83 C 61.05,47.66 60.59,47.52 60.04,47.41 C 59.50,47.29 59.06,47.22 58.72,47.18 L 58.72,45.74 L 73.18,45.74 L 73.18,47.18 C 72.79,47.22 72.33,47.27 71.80,47.34 C 71.26,47.41 70.80,47.50 70.41,47.63 C 69.93,47.79 69.61,48.04 69.45,48.39 C 69.29,48.74 69.21,49.12 69.21,49.54 L 69.21,58.41 L 69.99,58.41 C 70.95,57.53 72.08,56.47 73.36,55.24 C 74.65,54.00 75.65,52.98 76.35,52.19 C 77.34,51.09 78.07,50.22 78.53,49.58 C 78.99,48.93 79.23,48.38 79.23,47.91 C 79.23,47.70 79.00,47.54 78.56,47.42 C 78.11,47.31 77.43,47.23 76.52,47.18 L 76.52,45.74 L 87.97,45.74 L 87.97,47.18 C 86.58,47.33 85.42,47.58 84.48,47.93 C 83.54,48.29 82.61,48.88 81.68,49.71 C 80.36,50.89 79.27,51.92 78.41,52.79 C 77.56,53.66 76.46,54.78 75.11,56.14 C 76.30,57.58 77.72,59.36 79.39,61.46 C 81.06,63.55 82.72,65.60 84.38,67.59 C 84.80,68.10 85.28,68.59 85.82,69.05 C 86.36,69.51 86.96,69.89 87.62,70.17 C 87.93,70.31 88.28,70.41 88.67,70.49 C 89.07,70.56 89.41,70.60 89.69,70.62 Z M 101.83,72.06 L 90.10,72.06 L 90.10,70.71 C 90.42,70.69 90.74,70.65 91.04,70.62 C 91.35,70.58 91.62,70.52 91.84,70.43 C 92.24,70.28 92.52,70.07 92.68,69.79 C 92.84,69.51 92.92,69.15 92.92,68.69 L 92.92,57.89 C 92.92,57.46 92.82,57.08 92.62,56.75 C 92.42,56.42 92.18,56.16 91.88,55.96 C 91.66,55.81 91.32,55.67 90.87,55.53 C 90.42,55.40 90.01,55.31 89.63,55.27 L 89.63,53.92 L 98.73,53.44 L 99.00,53.71 L 99.00,68.45 C 99.00,68.88 99.10,69.25 99.28,69.55 C 99.47,69.85 99.73,70.08 100.08,70.23 C 100.33,70.34 100.60,70.44 100.90,70.52 C 101.19,70.61 101.50,70.67 101.83,70.71 Z M 99.12,46.76 C 99.12,47.70 98.75,48.50 98.03,49.16 C 97.31,49.82 96.45,50.15 95.46,50.15 C 94.46,50.15 93.60,49.82 92.87,49.16 C 92.15,48.50 91.79,47.70 91.79,46.76 C 91.79,45.82 92.15,45.01 92.87,44.34 C 93.60,43.68 94.46,43.34 95.46,43.34 C 96.46,43.34 97.32,43.68 98.04,44.34 C 98.76,45.01 99.12,45.82 99.12,46.76 Z M 128.52,72.06 L 117.37,72.06 L 117.37,70.71 C 117.61,70.69 117.89,70.65 118.23,70.62 C 118.56,70.58 118.81,70.52 118.99,70.43 C 119.25,70.30 119.43,70.10 119.54,69.84 C 119.66,69.58 119.71,69.19 119.71,68.69 L 119.71,60.00 C 119.71,58.74 119.48,57.79 119.01,57.16 C 118.55,56.52 117.86,56.20 116.96,56.20 C 116.46,56.20 116.00,56.27 115.57,56.42 C 115.14,56.57 114.72,56.79 114.32,57.07 C 113.96,57.32 113.67,57.57 113.45,57.83 C 113.23,58.09 113.05,58.32 112.90,58.52 L 112.90,68.48 C 112.90,68.92 112.96,69.28 113.09,69.57 C 113.21,69.86 113.41,70.08 113.70,70.23 C 113.90,70.34 114.14,70.44 114.44,70.52 C 114.74,70.61 115.03,70.67 115.31,70.71 L 115.31,72.06 L 104.03,72.06 L 104.03,70.71 C 104.34,70.69 104.65,70.65 104.97,70.61 C 105.28,70.56 105.55,70.51 105.78,70.43 C 106.17,70.30 106.44,70.09 106.59,69.80 C 106.74,69.52 106.82,69.15 106.82,68.69 L 106.82,57.89 C 106.82,57.51 106.72,57.13 106.52,56.76 C 106.32,56.39 106.06,56.08 105.74,55.85 C 105.52,55.70 105.24,55.58 104.90,55.48 C 104.55,55.38 104.18,55.31 103.77,55.27 L 103.77,53.92 L 112.62,53.44 L 112.90,53.71 L 112.90,56.40 L 112.98,56.40 C 113.32,56.10 113.74,55.72 114.24,55.29 C 114.73,54.86 115.19,54.51 115.61,54.23 C 116.07,53.94 116.67,53.68 117.41,53.47 C 118.15,53.26 118.94,53.16 119.79,53.16 C 121.80,53.16 123.31,53.73 124.32,54.87 C 125.33,56.02 125.83,57.54 125.83,59.43 L 125.83,68.52 C 125.83,68.98 125.90,69.34 126.04,69.62 C 126.17,69.89 126.43,70.10 126.80,70.26 C 127.00,70.35 127.24,70.44 127.53,70.52 C 127.82,70.61 128.15,70.67 128.52,70.71 Z M 155.43,53.27 L 153.98,53.27 C 153.60,52.14 152.98,50.94 152.14,49.65 C 151.29,48.36 150.51,47.67 149.80,47.57 C 149.40,47.52 148.91,47.48 148.33,47.44 C 147.75,47.41 147.25,47.39 146.83,47.39 L 145.96,47.39 L 145.96,68.04 C 145.96,68.48 146.05,68.88 146.21,69.22 C 146.37,69.56 146.68,69.83 147.15,70.02 C 147.44,70.13 147.88,70.25 148.48,70.37 C 149.08,70.48 149.59,70.57 150.01,70.62 L 150.01,72.06 L 135.33,72.06 L 135.33,70.62 C 135.69,70.58 136.20,70.53 136.85,70.46 C 137.49,70.39 137.94,70.31 138.17,70.21 C 138.65,70.01 138.98,69.75 139.14,69.41 C 139.30,69.08 139.38,68.68 139.38,68.22 L 139.38,47.39 L 138.51,47.39 C 138.09,47.39 137.59,47.41 137.01,47.44 C 136.43,47.48 135.94,47.52 135.55,47.57 C 134.83,47.67 134.05,48.36 133.20,49.65 C 132.36,50.94 131.74,52.14 131.36,53.27 L 129.92,53.27 L 129.92,45.74 L 155.43,45.74 Z M 175.73,57.05 C 175.73,57.99 175.43,58.82 174.85,59.53 C 174.27,60.24 173.51,60.60 172.55,60.60 C 171.65,60.60 170.89,60.37 170.26,59.91 C 169.64,59.46 169.32,58.79 169.32,57.90 C 169.32,57.47 169.36,57.13 169.42,56.89 C 169.48,56.65 169.53,56.43 169.58,56.22 C 169.02,56.24 168.42,56.44 167.78,56.81 C 167.15,57.18 166.60,57.67 166.11,58.28 L 166.11,68.46 C 166.11,68.90 166.18,69.26 166.32,69.54 C 166.45,69.83 166.68,70.05 167.00,70.21 C 167.28,70.35 167.72,70.46 168.33,70.55 C 168.94,70.65 169.39,70.70 169.66,70.71 L 169.66,72.06 L 157.25,72.06 L 157.25,70.71 C 157.55,70.69 157.87,70.65 158.18,70.61 C 158.50,70.56 158.77,70.51 158.99,70.43 C 159.39,70.30 159.66,70.09 159.81,69.80 C 159.95,69.52 160.03,69.15 160.03,68.69 L 160.03,57.89 C 160.03,57.44 159.92,57.04 159.71,56.69 C 159.50,56.34 159.25,56.06 158.95,55.85 C 158.73,55.70 158.45,55.58 158.11,55.48 C 157.77,55.38 157.39,55.31 156.99,55.27 L 156.99,53.92 L 165.84,53.44 L 166.11,53.71 L 166.11,56.40 L 166.19,56.40 C 167.04,55.41 167.95,54.64 168.91,54.08 C 169.87,53.51 170.86,53.23 171.90,53.23 C 173.05,53.23 173.98,53.59 174.68,54.30 C 175.38,55.01 175.73,55.93 175.73,57.05 Z M 198.71,71.67 C 198.17,71.91 197.53,72.13 196.79,72.32 C 196.05,72.52 195.26,72.62 194.44,72.62 C 192.92,72.62 191.80,72.40 191.08,71.94 C 190.37,71.49 189.88,70.85 189.62,70.00 L 189.51,70.00 C 189.09,70.43 188.68,70.78 188.29,71.08 C 187.90,71.38 187.49,71.63 187.04,71.84 C 186.46,72.11 185.90,72.32 185.36,72.45 C 184.82,72.59 184.07,72.66 183.10,72.66 C 181.66,72.66 180.44,72.23 179.42,71.39 C 178.40,70.54 177.90,69.41 177.90,68.01 C 177.90,67.21 178.01,66.52 178.23,65.96 C 178.45,65.40 178.77,64.90 179.18,64.46 C 179.55,64.07 179.98,63.74 180.47,63.48 C 180.96,63.22 181.49,62.98 182.08,62.75 C 183.19,62.34 184.53,61.92 186.09,61.48 C 187.65,61.04 188.79,60.65 189.51,60.30 L 189.51,58.52 C 189.51,58.21 189.49,57.80 189.44,57.29 C 189.39,56.79 189.28,56.36 189.12,56.01 C 188.95,55.62 188.68,55.28 188.30,55.01 C 187.93,54.74 187.44,54.60 186.84,54.60 C 186.29,54.60 185.84,54.68 185.47,54.84 C 185.11,54.99 184.81,55.15 184.59,55.31 C 184.63,55.56 184.73,55.93 184.91,56.44 C 185.08,56.95 185.17,57.44 185.17,57.91 C 185.17,58.12 185.11,58.38 185.00,58.70 C 184.89,59.01 184.73,59.26 184.52,59.45 C 184.23,59.69 183.91,59.89 183.56,60.02 C 183.21,60.16 182.66,60.23 181.93,60.23 C 180.89,60.23 180.10,59.98 179.57,59.48 C 179.03,58.97 178.77,58.32 178.77,57.52 C 178.77,56.82 179.04,56.20 179.59,55.65 C 180.14,55.10 180.81,54.65 181.61,54.29 C 182.39,53.92 183.28,53.63 184.30,53.43 C 185.31,53.22 186.25,53.12 187.12,53.12 C 188.32,53.12 189.42,53.19 190.42,53.33 C 191.42,53.48 192.32,53.77 193.13,54.21 C 193.90,54.65 194.51,55.28 194.96,56.10 C 195.41,56.92 195.63,57.99 195.63,59.32 C 195.63,60.72 195.61,62.37 195.55,64.29 C 195.49,66.21 195.47,67.47 195.47,68.08 C 195.47,68.63 195.55,69.07 195.72,69.39 C 195.88,69.71 196.14,69.96 196.49,70.12 C 196.70,70.22 197.03,70.27 197.48,70.29 C 197.93,70.31 198.34,70.32 198.71,70.32 Z M 189.51,62.01 C 188.82,62.24 188.13,62.51 187.44,62.82 C 186.76,63.12 186.20,63.44 185.77,63.77 C 185.31,64.12 184.95,64.57 184.69,65.10 C 184.43,65.64 184.30,66.28 184.30,67.01 C 184.30,67.98 184.52,68.68 184.96,69.11 C 185.40,69.55 185.94,69.76 186.59,69.76 C 187.27,69.76 187.83,69.62 188.27,69.35 C 188.71,69.07 189.10,68.72 189.44,68.31 Z M 211.24,72.66 C 209.52,72.66 208.00,72.41 206.68,71.91 C 205.36,71.41 204.28,70.72 203.43,69.86 C 202.57,68.98 201.94,67.94 201.51,66.74 C 201.09,65.54 200.88,64.23 200.88,62.82 C 200.88,61.54 201.14,60.31 201.65,59.15 C 202.17,57.99 202.86,56.97 203.74,56.09 C 204.64,55.19 205.77,54.47 207.11,53.93 C 208.45,53.39 209.93,53.12 211.55,53.12 C 213.83,53.12 215.63,53.55 216.95,54.41 C 218.28,55.27 218.94,56.43 218.94,57.89 C 218.94,58.78 218.70,59.50 218.23,60.04 C 217.76,60.59 216.95,60.86 215.80,60.86 C 214.87,60.86 214.10,60.63 213.47,60.19 C 212.85,59.74 212.54,59.16 212.54,58.45 C 212.54,57.84 212.64,57.24 212.83,56.65 C 213.03,56.05 213.15,55.60 213.19,55.29 C 213.05,55.07 212.88,54.90 212.67,54.78 C 212.46,54.66 212.13,54.60 211.68,54.60 C 211.13,54.60 210.62,54.73 210.16,54.99 C 209.70,55.25 209.28,55.71 208.88,56.37 C 208.50,56.99 208.19,57.81 207.96,58.85 C 207.73,59.89 207.62,61.14 207.62,62.60 C 207.62,64.80 208.14,66.58 209.17,67.93 C 210.20,69.28 211.44,69.95 212.87,69.95 C 214.24,69.95 215.33,69.64 216.15,69.03 C 216.96,68.42 217.68,67.55 218.31,66.42 L 219.70,67.39 C 219.31,68.14 218.86,68.83 218.33,69.46 C 217.79,70.08 217.16,70.64 216.41,71.12 C 215.63,71.63 214.84,72.01 214.04,72.27 C 213.24,72.53 212.30,72.66 211.24,72.66 Z M 241.28,67.63 C 240.84,68.33 240.32,69.00 239.71,69.62 C 239.10,70.25 238.40,70.78 237.62,71.23 C 236.79,71.70 235.95,72.06 235.11,72.30 C 234.26,72.54 233.29,72.66 232.18,72.66 C 230.35,72.66 228.77,72.40 227.44,71.88 C 226.11,71.36 225.02,70.66 224.15,69.78 C 223.28,68.90 222.64,67.87 222.21,66.67 C 221.78,65.48 221.57,64.18 221.57,62.79 C 221.57,61.53 221.80,60.32 222.27,59.16 C 222.73,58.00 223.39,56.97 224.24,56.07 C 225.08,55.19 226.15,54.48 227.43,53.94 C 228.71,53.39 230.14,53.12 231.72,53.12 C 233.45,53.12 234.88,53.33 235.99,53.75 C 237.10,54.17 238.02,54.75 238.75,55.50 C 239.46,56.20 239.97,57.03 240.29,57.99 C 240.61,58.95 240.78,59.95 240.78,61.01 L 240.78,62.21 L 228.23,62.21 C 228.23,64.75 228.72,66.68 229.70,68.00 C 230.68,69.32 232.23,69.99 234.36,69.99 C 235.52,69.99 236.56,69.67 237.49,69.03 C 238.42,68.39 239.21,67.59 239.87,66.61 Z M 234.19,60.58 C 234.19,59.84 234.15,59.08 234.08,58.30 C 234.00,57.52 233.87,56.88 233.69,56.39 C 233.48,55.80 233.19,55.37 232.82,55.07 C 232.46,54.77 232.00,54.62 231.44,54.62 C 230.49,54.62 229.73,55.11 229.16,56.07 C 228.59,57.04 228.28,58.56 228.23,60.65 Z"
    }
}
