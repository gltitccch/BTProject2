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
 * Renders the animated living wooden bough, fluttering serrated green/gold leaves,
 * and swinging Family Node Locket Charm tucked cleanly into the upper-right corner
 * of the Home Dashboard's Hero Heritage Card (Direction 1).
 */
class HeroBotanicalBoughView @JvmOverloads constructor(
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

    private var woodMainColor = Color.parseColor("#8B431A")
    private var woodDarkColor = Color.parseColor("#4A200A")
    private var nodeBgColor = Color.parseColor("#0B1F14")
    private var goldRingColor = Color.parseColor("#E5B94B")
    private var goldPersonColor = Color.parseColor("#D4A359")
    private var goldLeafColor = Color.parseColor("#E09F2D")
    private var greenLeafColor = Color.parseColor("#2CC956")
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

    // Main sculpted wooden branch & secondary fork (in 220x115 coordinate space)
    private val mainBranchPath: Path = PathParser.createPathFromPathData(
        "M 225,22 C 175,18 130,28 78,14"
    )
    private val lowerForkPath: Path = PathParser.createPathFromPathData(
        "M 175,21 C 150,38 128,46 102,44"
    )
    private val upperTwigPath: Path = PathParser.createPathFromPathData(
        "M 142,22 C 126,12 112,8 96,6"
    )

    // Authentic serrated green & gold leaf paths matching Splash & Login
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

    private val nodeShouldersPath: Path = PathParser.createPathFromPathData(
        "M -4.5,4.5 C -4.5,1 4.5,1 4.5,4.5"
    )

    private val heroLeaves = listOf(
        LeafPlacement(98f, 18f, -55f, 0.54f, LeafKind.DUAL, 0.2f, 1),
        LeafPlacement(136f, 20f, -38f, 0.50f, LeafKind.GREEN, 1.1f, 2),
        LeafPlacement(148f, 40f, -115f, 0.52f, LeafKind.DUAL, 1.9f, 3),
        LeafPlacement(112f, 44f, -95f, 0.46f, LeafKind.GOLD, 2.5f, 4),
        LeafPlacement(198f, 18f, -45f, 0.52f, LeafKind.DUAL, 0.8f, 2)
    )

    private var elapsedSec = 0f
    private var breezeAnimator: ValueAnimator? = null
    private val overshoot = OvershootInterpolator(1.45f)
    private val decelerate = DecelerateInterpolator()

    init {
        refreshThemePalette()
    }

    fun refreshThemePalette() {
        val isDark = ThemePreferences.isDarkTheme(context)
        if (isDark) {
            woodMainColor = Color.parseColor("#8B431A")
            woodDarkColor = Color.parseColor("#4A200A")
            nodeBgColor = Color.parseColor("#0B1F14")
            goldRingColor = Color.parseColor("#E5B94B")
            goldPersonColor = Color.parseColor("#D4A359")
        } else {
            woodMainColor = Color.parseColor("#7C3A16")
            woodDarkColor = Color.parseColor("#4A200A")
            nodeBgColor = Color.parseColor("#FFFDF7")
            goldRingColor = Color.parseColor("#D48B1E")
            goldPersonColor = Color.parseColor("#7C3A16")
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshThemePalette()
        startLivingBreezeLoop()
    }

    override fun onDetachedFromWindow() {
        breezeAnimator?.cancel()
        breezeAnimator = null
        super.onDetachedFromWindow()
    }

    private fun startLivingBreezeLoop() {
        breezeAnimator?.cancel()
        val startMs = System.currentTimeMillis()
        breezeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                elapsedSec = (System.currentTimeMillis() - startMs) / 1000f
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val sx = w / 220f
        val sy = h / 115f
        val s = min(sx, sy)

        canvas.save()
        // Align right-top in viewBox (220 x 115)
        canvas.translate(w - 220f * s, 0f)
        canvas.scale(s, s)

        val branchProgress = decelerate.getInterpolation((elapsedSec / 0.65f).coerceIn(0f, 1f))
        canvas.save()
        canvas.scale(0.92f + 0.08f * branchProgress, 0.92f + 0.08f * branchProgress, 220f, 20f)

        // Draw Upper Twig
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 4.2f
        canvas.drawPath(upperTwigPath, strokePaint)
        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 2.4f
        canvas.drawPath(upperTwigPath, strokePaint)

        // Draw Lower Fork
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 5.4f
        canvas.drawPath(lowerForkPath, strokePaint)
        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 3.2f
        canvas.drawPath(lowerForkPath, strokePaint)

        // Draw Main Wooden Branch
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 7.8f
        canvas.drawPath(mainBranchPath, strokePaint)
        strokePaint.color = woodMainColor
        strokePaint.strokeWidth = 4.6f
        canvas.drawPath(mainBranchPath, strokePaint)
        canvas.restore()

        // Draw Fluttering Serrated Leaves
        for (leaf in heroLeaves) {
            val startDelay = 0.12f + leaf.delayOrder * 0.07f
            val rawPop = ((elapsedSec - startDelay) / 0.45f).coerceIn(0f, 1f)
            if (rawPop > 0f) {
                val popScale = overshoot.getInterpolation(rawPop)
                val flutterDeg = sin((elapsedSec * 2.1f + leaf.phaseOffset).toDouble()).toFloat() * 4.8f
                val breatheScale = 1f + sin((elapsedSec * 1.7f + leaf.phaseOffset).toDouble()).toFloat() * 0.03f
                drawLeafCluster(
                    canvas = canvas,
                    x = leaf.x,
                    y = leaf.y,
                    rotationDeg = leaf.rotationDeg + flutterDeg,
                    scale = leaf.scale * popScale * breatheScale,
                    kind = leaf.kind
                )
            }
        }

        // Draw Cute Swinging Family Node Charm hanging at x=188, y=22..61
        val charmRaw = ((elapsedSec - 0.25f) / 0.55f).coerceIn(0f, 1f)
        if (charmRaw > 0f) {
            val charmPop = overshoot.getInterpolation(charmRaw)
            val swingDeg = sin((elapsedSec * 1.85f).toDouble()).toFloat() * 5.2f
            drawHangingFamilyCharm(
                canvas = canvas,
                anchorX = 188f,
                anchorY = 22f,
                nodeY = 61f,
                swingDeg = swingDeg,
                scale = charmPop
            )
        }

        canvas.restore()
    }

    private fun drawHangingFamilyCharm(
        canvas: Canvas,
        anchorX: Float,
        anchorY: Float,
        nodeY: Float,
        swingDeg: Float,
        scale: Float
    ) {
        canvas.save()
        canvas.rotate(swingDeg, anchorX, anchorY)
        canvas.scale(scale, scale, anchorX, anchorY)

        // Stem
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 2.4f
        canvas.drawLine(anchorX, anchorY, anchorX, nodeY - 11.5f, strokePaint)

        canvas.save()
        canvas.translate(anchorX, nodeY)

        // Outer Medallion Circle
        fillPaint.color = nodeBgColor
        canvas.drawCircle(0f, 0f, 11.5f, fillPaint)
        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 2.8f
        canvas.drawCircle(0f, 0f, 11.5f, strokePaint)

        // Slowly rotating dashed gold ring
        canvas.save()
        canvas.rotate((elapsedSec * 18f) % 360f)
        dashedPaint.color = goldRingColor
        dashedPaint.strokeWidth = 1.35f
        canvas.drawCircle(0f, 0f, 8.2f, dashedPaint)
        canvas.restore()

        // Person Silhouette
        fillPaint.color = goldPersonColor
        canvas.drawCircle(0f, -2.4f, 2.5f, fillPaint)
        strokePaint.color = goldPersonColor
        strokePaint.strokeWidth = 2.2f
        canvas.drawPath(nodeShouldersPath, strokePaint)

        canvas.restore()
        canvas.restore()
    }

    private fun drawLeafCluster(
        canvas: Canvas,
        x: Float,
        y: Float,
        rotationDeg: Float,
        scale: Float,
        kind: LeafKind
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotationDeg)
        canvas.scale(scale, scale)

        when (kind) {
            LeafKind.GREEN -> drawSingleGreenLeaf(canvas)
            LeafKind.GOLD -> drawSingleGoldLeaf(canvas)
            LeafKind.DUAL -> {
                canvas.save()
                canvas.translate(-2f, 2f)
                canvas.rotate(-22f)
                canvas.scale(0.92f, 0.92f)
                drawSingleGoldLeaf(canvas)
                canvas.restore()

                canvas.save()
                canvas.rotate(8f)
                drawSingleGreenLeaf(canvas)
                canvas.restore()
            }
        }
        canvas.restore()
    }

    private fun drawSingleGreenLeaf(canvas: Canvas) {
        fillPaint.color = greenLeafColor
        canvas.drawPath(greenLeafBodyPath, fillPaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 2.5f
        canvas.drawPath(greenLeafBodyPath, strokePaint)

        fillPaint.color = greenLeafShineColor
        canvas.drawPath(greenLeafShinePath, fillPaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.6f
        canvas.drawPath(greenLeafVeinsPath, strokePaint)
    }

    private fun drawSingleGoldLeaf(canvas: Canvas) {
        fillPaint.color = goldLeafColor
        canvas.drawPath(goldLeafBodyPath, fillPaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 2.4f
        canvas.drawPath(goldLeafBodyPath, strokePaint)

        strokePaint.color = woodDarkColor
        strokePaint.strokeWidth = 1.5f
        canvas.drawPath(goldLeafVeinsPath, strokePaint)
    }
}
