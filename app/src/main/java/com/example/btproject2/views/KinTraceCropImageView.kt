package com.example.btproject2.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * High-craft interactive image cropper designed specifically for KinTrace family tree portraits.
 * Provides smooth 60fps single-finger panning, two-finger pinch-to-zoom, and a circular gold
 * node mask overlay matching the exact heritage aesthetic of the KinTrace canvas.
 */
class KinTraceCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null

    // Transform State
    private var userScale: Float = 1.0f
    private var baseScale: Float = 1.0f
    private var transX: Float = 0f
    private var transY: Float = 0f
    private var rotationDegrees: Float = 0f

    // Viewport geometry
    private var circleCenterX: Float = 0f
    private var circleCenterY: Float = 0f
    private var circleRadius: Float = 0f

    // Rendering Paints
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6020704") // Deep semi-transparent dark emerald mask
        style = Paint.Style.FILL
    }
    private val goldRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37") // KinTrace Heritage Gold
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2.5f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val maskPath = Path()
    private val transformMatrix = Matrix()

    // Touch / Gestures
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isDragging = false

    var onZoomChangedListener: ((Float) -> Unit)? = null
    var onEmptyStateClickedListener: (() -> Unit)? = null

    // Empty State Paints - Clean Heritage Viewfinder
    private val emptyIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.displayMetrics.density * 34f
        textAlign = Paint.Align.CENTER
        alpha = 110 // Soft watermark
    }
    private val emptyTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8FA899")
        textSize = context.resources.displayMetrics.density * 13f
        textAlign = Paint.Align.CENTER
        alpha = 180
    }

    private var downTouchX = 0f
    private var downTouchY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            setZoom(userScale * factor)
            return true
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circleCenterX = w / 2f
        circleCenterY = h / 2f
        circleRadius = min(w, h) * 0.40f

        updateMaskPath()
        recalcBaseScale()
    }

    private fun updateMaskPath() {
        maskPath.reset()
        maskPath.fillType = Path.FillType.EVEN_ODD
        maskPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        maskPath.addCircle(circleCenterX, circleCenterY, circleRadius, Path.Direction.CW)
    }

    private fun drawEmptyStatePrompt(canvas: Canvas) {
        val density = context.resources.displayMetrics.density
        // Gentle, non-intrusive camera watermark and clean invitation
        canvas.drawText("📷", circleCenterX, circleCenterY - (4f * density), emptyIconPaint)
        canvas.drawText("Tap circle to choose photo", circleCenterX, circleCenterY + (22f * density), emptyTitlePaint)
    }

    fun setBitmap(bitmap: Bitmap?) {
        this.sourceBitmap = bitmap
        rotationDegrees = 0f
        userScale = 1.0f
        transX = 0f
        transY = 0f
        recalcBaseScale()
        invalidate()
    }

    private fun recalcBaseScale() {
        val bmp = sourceBitmap ?: return
        if (width <= 0 || height <= 0 || circleRadius <= 0f) return

        val diameter = circleRadius * 2f
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        // Base scale guarantees the image covers at least the diameter of the circle
        baseScale = max(diameter / bmpW, diameter / bmpH).coerceAtLeast(0.01f)
        invalidate()
    }

    fun setZoom(zoom: Float) {
        userScale = zoom.coerceIn(1.0f, 3.5f)
        onZoomChangedListener?.invoke(userScale)
        invalidate()
    }

    fun rotate90() {
        rotationDegrees = (rotationDegrees + 90f) % 360f
        invalidate()
    }

    fun resetTransform() {
        userScale = 1.0f
        transX = 0f
        transY = 0f
        rotationDegrees = 0f
        onZoomChangedListener?.invoke(userScale)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap

        if (bmp != null && !bmp.isRecycled) {
            transformMatrix.reset()

            // 1. Center bitmap on its origin
            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            transformMatrix.postTranslate(-bmpW / 2f, -bmpH / 2f)

            // 2. Rotate
            if (rotationDegrees != 0f) {
                transformMatrix.postRotate(rotationDegrees)
            }

            // 3. Scale
            val totalScale = baseScale * userScale
            transformMatrix.postScale(totalScale, totalScale)

            // 4. Translate to circle center + user pan
            transformMatrix.postTranslate(circleCenterX + transX, circleCenterY + transY)

            // Draw bitmap
            canvas.drawBitmap(bmp, transformMatrix, bitmapPaint)
        } else {
            // Draw welcoming, interactive Empty State inside the golden ring
            drawEmptyStatePrompt(canvas)
        }

        // Draw outer dark mask
        canvas.drawPath(maskPath, overlayPaint)

        // Always draw subtle 3x3 rule-of-thirds grid inside the golden circle (matching camera viewfinder)
        val step = (circleRadius * 2f) / 3f
        val left = circleCenterX - circleRadius
        val top = circleCenterY - circleRadius
        val right = circleCenterX + circleRadius
        val bottom = circleCenterY + circleRadius

        canvas.save()
        val clipCircle = Path().apply { addCircle(circleCenterX, circleCenterY, circleRadius, Path.Direction.CW) }
        canvas.clipPath(clipCircle)

        canvas.drawLine(left + step, top, left + step, bottom, gridPaint)
        canvas.drawLine(left + step * 2, top, left + step * 2, bottom, gridPaint)
        canvas.drawLine(left, top + step, right, top + step, gridPaint)
        canvas.drawLine(left, top + step * 2, right, top + step * 2, gridPaint)

        canvas.restore()

        // Draw golden circular guide ring
        canvas.drawCircle(circleCenterX, circleCenterY, circleRadius, goldRingPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTouchX = event.x
                downTouchY = event.y
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1 && !scaleDetector.isInProgress) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY

                    transX += dx
                    transY += dy
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = kotlin.math.abs(event.x - downTouchX)
                val dy = kotlin.math.abs(event.y - downTouchY)
                val distFromCenter = kotlin.math.hypot(event.x - circleCenterX, event.y - circleCenterY)
                if (sourceBitmap == null && distFromCenter <= circleRadius && dx < 25f && dy < 25f) {
                    onEmptyStateClickedListener?.invoke()
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }

    /**
     * Crops the exact visible circular region into a lightweight, high-fidelity square bitmap
     * ready for instant 60fps family tree rendering and Firestore persistence.
     */
    fun cropResultBitmap(outputSize: Int = 256): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (bmp.isRecycled || circleRadius <= 0f) return null

        val result = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Matrix mapping the visible circle bounding box into the target square
        val diameter = circleRadius * 2f
        val mapScale = outputSize / diameter

        val renderMatrix = Matrix()

        // 1. Recreate current screen transform
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        renderMatrix.postTranslate(-bmpW / 2f, -bmpH / 2f)

        if (rotationDegrees != 0f) {
            renderMatrix.postRotate(rotationDegrees)
        }

        val totalScale = baseScale * userScale
        renderMatrix.postScale(totalScale, totalScale)
        renderMatrix.postTranslate(circleCenterX + transX, circleCenterY + transY)

        // 2. Map view coordinates so (circleCenterX - circleRadius, circleCenterY - circleRadius) is (0, 0)
        renderMatrix.postTranslate(-(circleCenterX - circleRadius), -(circleCenterY - circleRadius))
        renderMatrix.postScale(mapScale, mapScale)

        canvas.drawBitmap(bmp, renderMatrix, bitmapPaint)
        return result
    }
}
