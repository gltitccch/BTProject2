package com.example.btproject2.views

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.example.btproject2.R
import com.example.btproject2.utils.ThemePreferences

/**
 * Data model representing a single contextual spotlight tutorial step.
 */
data class SpotlightStep(
    val targetViewId: Int = 0,
    val icon: String = "\u2728",
    val title: String,
    val description: String,
    val proTip: String? = null,
    val customViewProvider: (() -> View?)? = null,
    val customCutoutProvider: ((spotlightView: KinTraceSpotlightView) -> Pair<RectF, Float>?)? = null,
    val onStepEnter: (() -> Unit)? = null,
    val passThroughTouches: Boolean = false
)

/**
 * Hardware-accelerated Spotlight Coachmark Overlay for KinTrace.
 *
 * Punches a crisp rounded rectangle cutout over live Android views using PorterDuff.Mode.CLEAR,
 * renders an animated golden border + numbered beacon badge, and anchors an adaptive tooltip card
 * with interactive breadcrumb dots.
 */
class KinTraceSpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DF060D09") // 88% alpha deep heritage emerald/black
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }

    private val pulseHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55D4AF37")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val beaconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.FILL
    }

    private val beaconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#060D09")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val beaconTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#060D09")
        textSize = 11.5f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    /**
     * Dynamically synchronizes coachmark scrim and beacon colors with the active application theme.
     * In Dark Mode: Preserves KinTrace's signature deep emerald-black scrim (#DF060D09).
     * In Light Mode: Uses a soft neutral charcoal-sage dim (~62% opacity) to eliminate murky green tinting
     * over light cream cards while maintaining strong visual contrast.
     */
    fun updateAdaptiveThemeColors() {
        val isDark = ThemePreferences.isDarkTheme(context)
        if (isDark) {
            scrimPaint.color = Color.parseColor("#DF060D09")
            beaconStrokePaint.color = Color.parseColor("#060D09")
            beaconTextPaint.color = Color.parseColor("#060D09")
        } else {
            scrimPaint.color = Color.argb(158, 20, 28, 23) // Soft neutral sage-charcoal focus dim (62%)
            beaconStrokePaint.color = Color.parseColor("#FFFFFF")
            beaconTextPaint.color = Color.parseColor("#060D09")
        }
    }

    private val currentCutoutRect = RectF()
    private val pulseRect = RectF()
    private var cutoutRadius = 14f * density
    private var targetRadius = 14f * density
    private var pulseProgress = 0f

    private var currentTargetViewRef: java.lang.ref.WeakReference<View>? = null
    private val scrollChangedListener = android.view.ViewTreeObserver.OnScrollChangedListener {
        syncCutoutWithTarget(animate = false)
    }
    private val globalLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        syncCutoutWithTarget(animate = false)
    }

    private var transitionAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private var steps: List<SpotlightStep> = emptyList()
    private var currentStepIndex = 0
    private var onCompleteCallback: (() -> Unit)? = null
    var activeDialogProvider: (() -> android.app.Dialog?)? = null

    private val tooltipView: View
    private val layoutBreadcrumbs: LinearLayout
    private val tvStepBadge: TextView
    private val tvIcon: TextView
    private val tvTitle: TextView
    private val tvDescription: TextView
    private val layoutProTip: LinearLayout
    private val tvProTip: TextView
    private val btnDismiss: TextView
    private val btnPrev: TextView
    private val btnNext: TextView
    private val btnOptions: View?
    private val btnCloseHeader: View?

    init {
        setWillNotDraw(false)
        elevation = 100f * density
        translationZ = 100f * density
        isClickable = true
        isFocusable = true

        tooltipView = LayoutInflater.from(context).inflate(R.layout.layout_spotlight_tooltip, this, false)
        layoutBreadcrumbs = tooltipView.findViewById(R.id.layoutSpotlightBreadcrumbs)
        tvStepBadge = tooltipView.findViewById(R.id.tvSpotlightStepBadge)
        tvIcon = tooltipView.findViewById(R.id.tvSpotlightIcon)
        tvTitle = tooltipView.findViewById(R.id.tvSpotlightTitle)
        tvDescription = tooltipView.findViewById(R.id.tvSpotlightDescription)
        layoutProTip = tooltipView.findViewById(R.id.layoutSpotlightProTip)
        tvProTip = tooltipView.findViewById(R.id.tvSpotlightProTip)
        btnDismiss = tooltipView.findViewById(R.id.btnSpotlightDismiss)
        btnPrev = tooltipView.findViewById(R.id.btnSpotlightPrev)
        btnNext = tooltipView.findViewById(R.id.btnSpotlightNext)
        btnOptions = tooltipView.findViewById(R.id.btnSpotlightOptions)
        btnCloseHeader = tooltipView.findViewById(R.id.btnSpotlightCloseHeader)

        val lp = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            val hMargin = (16 * density).toInt()
            leftMargin = hMargin
            rightMargin = hMargin
        }
        addView(tooltipView, lp)

        btnDismiss.setOnClickListener {
            finishTour()
        }

        btnCloseHeader?.setOnClickListener {
            finishTour()
        }

        btnOptions?.setOnClickListener {
            showRemoveGuideCrestDialog()
        }

        btnPrev.setOnClickListener {
            previousStep()
        }

        btnNext.setOnClickListener {
            nextStep()
        }

        updateAdaptiveThemeColors()
        startPulseAnimation()
    }

    private fun showRemoveGuideCrestDialog() {
        val act = context as? Activity ?: return
        com.example.btproject2.utils.KinTraceModalDialog.Builder(act)
            .setTitle("Remove Guide Crest?")
            .setMessage("Are you sure you want to remove the center Guide Crest from your navigation island?\n\n🎓 You can restore this Guide Crest anytime inside KinAcademy at the top right of your screen.")
            .setIconCrest("🛡️")
            .setKicker("NAVIGATION PREFERENCE")
            .setPositiveButton("Remove Crest", android.content.DialogInterface.OnClickListener { _, _ ->
                com.example.btproject2.utils.OnboardingPreferences.setGuideCrestDismissed(act, true)
                com.example.btproject2.ui.navigation.FloatingNavigationCoordinator.updateGuideCrestVisibility(act)

                val uid = com.example.btproject2.firebase.AuthHelper().getCurrentUserId().orEmpty()
                if (uid.isNotEmpty()) {
                    val state = com.example.btproject2.utils.OnboardingPreferences.getOnboardingState(act, uid)
                    com.example.btproject2.firebase.FirestoreHelper().saveOnboardingState(uid, state)
                }

                finishTour()
                com.example.btproject2.utils.KinTraceAlert.show(act, "Guide Crest removed. Restore anytime in KinAcademy (🎓)")
            })
            .setNegativeButton("Keep on Island", null)
            .show()
    }

    fun startTour(
        stepList: List<SpotlightStep>,
        onComplete: () -> Unit = {}
    ) {
        if (stepList.isEmpty()) {
            onComplete()
            return
        }
        this.steps = stepList
        this.onCompleteCallback = onComplete
        this.currentStepIndex = 0
        alpha = 0f
        animate().alpha(1f).setDuration(240).start()
        post {
            showStep(0, immediate = true)
        }
    }

    fun showStep(index: Int, immediate: Boolean = false) {
        if (steps.isEmpty()) return
        updateAdaptiveThemeColors()
        currentStepIndex = index.coerceIn(0, steps.size - 1)
        val step = steps[currentStepIndex]

        // If stepping back to Step 1 (canvas card), dismiss any lingering modal
        if (currentStepIndex == 0) {
            try {
                activeDialogProvider?.invoke()?.dismiss()
            } catch (_: Exception) {}
        }

        step.onStepEnter?.invoke()

        val bindStep = {
            val activeDialog = activeDialogProvider?.invoke()
            if (activeDialog != null && activeDialog.isShowing) {
                val dialogDecor = activeDialog.window?.decorView as? ViewGroup
                if (dialogDecor != null && this.parent !== dialogDecor) {
                    try {
                        (this.parent as? ViewGroup)?.removeView(this)
                        dialogDecor.addView(
                            this,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        this.bringToFront()
                    } catch (_: Exception) {}
                }
            } else {
                val activityRoot = (context as? Activity)?.findViewById<ViewGroup>(android.R.id.content)
                if (activityRoot != null && this.parent !== activityRoot) {
                    try {
                        (this.parent as? ViewGroup)?.removeView(this)
                        activityRoot.addView(
                            this,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        this.bringToFront()
                    } catch (_: Exception) {}
                }
            }

            // Update Tooltip Texts
            tvStepBadge.text = "Step " + (currentStepIndex + 1) + " of " + steps.size
            tvIcon.text = step.icon
            tvTitle.text = step.title
            tvDescription.text = step.description

            if (!step.proTip.isNullOrBlank()) {
                layoutProTip.visibility = View.VISIBLE
                tvProTip.text = step.proTip
            } else {
                layoutProTip.visibility = View.GONE
            }

            btnPrev.visibility = if (currentStepIndex > 0) View.VISIBLE else View.GONE
            btnNext.text = if (currentStepIndex == steps.size - 1) "Finish Tour 🎉" else "Next Step →"

            renderBreadcrumbs()

            val activity = context as? Activity

            fun applyTarget(attempt: Int = 0) {
                if (step.customCutoutProvider != null) {
                    val customCutout = step.customCutoutProvider.invoke(this@KinTraceSpotlightView)
                    if (customCutout != null) {
                        currentTargetViewRef = null
                        val fit = TargetFit(customCutout.first, customCutout.second)
                        animateCutoutAndPositionTooltip(fit, immediate)
                        return
                    } else if (attempt < 5) {
                        postDelayed({ applyTarget(attempt + 1) }, 80L)
                        return
                    }
                }
                var resolvedTarget = step.customViewProvider?.invoke()
                    ?: (if (step.targetViewId != 0) {
                        activeDialogProvider?.invoke()?.findViewById<View>(step.targetViewId)
                            ?: activity?.findViewById<View>(step.targetViewId)
                    } else null)

                if (step.targetViewId == R.id.ivProfilePhoto && (resolvedTarget == null || !resolvedTarget.isShown)) {
                    val avatar = activeDialogProvider?.invoke()?.findViewById<View>(R.id.tvProfileAvatar)
                    if (avatar != null && avatar.isShown) {
                        resolvedTarget = avatar
                    }
                }

                if (step.targetViewId == R.id.layoutProfileDocsContainer && (resolvedTarget == null || resolvedTarget.height == 0 || !resolvedTarget.isShown)) {
                    val docBtn = activeDialogProvider?.invoke()?.findViewById<View>(R.id.btnProfileAttachDoc)
                    val docCard = docBtn?.parent as? View
                    if (docCard != null && docCard.isShown) {
                        resolvedTarget = docCard
                    } else if (docBtn != null && docBtn.isShown) {
                        resolvedTarget = docBtn
                    }
                }

                currentTargetViewRef = if (resolvedTarget != null) java.lang.ref.WeakReference(resolvedTarget) else null

                if (resolvedTarget != null && resolvedTarget.isShown && resolvedTarget.width > 0) {
                    val neededScroll = scrollTargetIntoViewIfNeeded(resolvedTarget)
                    if (neededScroll) {
                        postDelayed({
                            if (!isAttachedToWindow) return@postDelayed
                            val fit = computeTargetFit(resolvedTarget)
                            animateCutoutAndPositionTooltip(fit, immediate)
                        }, 100L)
                    } else {
                        post {
                            if (!isAttachedToWindow) return@post
                            val fit = computeTargetFit(resolvedTarget)
                            animateCutoutAndPositionTooltip(fit, immediate)
                        }
                    }
                } else if (resolvedTarget != null && attempt < 5) {
                    postDelayed({ applyTarget(attempt + 1) }, 80L)
                } else {
                    post {
                        if (!isAttachedToWindow) return@post
                        transitionAnimator?.cancel()
                        currentCutoutRect.setEmpty()
                        invalidate()
                        positionTooltipCentered(animate = !immediate)
                    }
                }
            }

            applyTarget(0)
        }

        if (step.onStepEnter != null) {
            postDelayed({ bindStep() }, 180L)
        } else {
            val activeDialog = activeDialogProvider?.invoke()
            if (activeDialog != null && !activeDialog.isShowing) {
                postDelayed({ bindStep() }, 120L)
            } else {
                post { bindStep() }
            }
        }
    }

    private fun renderBreadcrumbs() {
        layoutBreadcrumbs.removeAllViews()
        for (i in steps.indices) {
            val isActive = i == currentStepIndex
            val dotView = View(context).apply {
                val dotW = if (isActive) (20 * density).toInt() else (8 * density).toInt()
                val dotH = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(dotW, dotH).apply {
                    rightMargin = (6 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 4f * density
                    setColor(
                        if (isActive) Color.parseColor("#D4AF37")
                        else Color.parseColor("#4D9EB3A6")
                    )
                }
                setOnClickListener {
                    showStep(i)
                }
            }
            layoutBreadcrumbs.addView(dotView)
        }
    }

    private fun scrollTargetIntoViewIfNeeded(target: View): Boolean {
        try {
            var parentView = target.parent
            while (parentView != null) {
                if (parentView is ScrollView || parentView is NestedScrollView) {
                    val scrollParent = parentView as View
                    val targetRect = Rect()
                    val isVis = target.getGlobalVisibleRect(targetRect)
                    val parentRect = Rect()
                    scrollParent.getGlobalVisibleRect(parentRect)

                    val safeTop = parentRect.top + (48 * density).toInt()
                    val safeBottom = parentRect.bottom - (64 * density).toInt()

                    // If already comfortably visible inside the scroll view, don't trigger unnecessary scroll!
                    if (isVis && targetRect.top >= safeTop && targetRect.bottom <= safeBottom) {
                        return false
                    }

                    val rect = Rect(0, 0, target.width, target.height)
                    if (parentView is ScrollView) {
                        parentView.offsetDescendantRectToMyCoords(target, rect)
                        val targetCenterY = rect.centerY()
                        val desiredScrollY = (targetCenterY - parentView.height / 3).coerceAtLeast(0)
                        parentView.smoothScrollTo(0, desiredScrollY)
                        return true
                    } else if (parentView is NestedScrollView) {
                        parentView.offsetDescendantRectToMyCoords(target, rect)
                        val targetCenterY = rect.centerY()
                        val desiredScrollY = (targetCenterY - parentView.height / 3).coerceAtLeast(0)
                        parentView.smoothScrollTo(0, desiredScrollY)
                        return true
                    }
                    break
                }
                parentView = parentView.parent
            }
        } catch (_: Exception) {}
        return false
    }

    private data class TargetFit(val bounds: RectF, val cornerRadius: Float)

    private fun computeTargetFit(target: View): TargetFit {
        val targetLoc = IntArray(2)
        val selfLoc = IntArray(2)
        target.getLocationOnScreen(targetLoc)
        this.getLocationOnScreen(selfLoc)

        val rawLeft = (targetLoc[0] - selfLoc[0]).toFloat()
        val rawTop = (targetLoc[1] - selfLoc[1]).toFloat()
        val targetW = target.width.toFloat()
        val targetH = target.height.toFloat()

        val isRoughlySquare = kotlin.math.abs(targetW - targetH) <= 8f * density && kotlin.math.max(targetW, targetH) <= 60f * density
        val isCapsulePill = targetH in (30f * density)..(56f * density) && targetW > targetH * 1.25f
        val isCardContainer = targetW > 56f * density && targetH > 52f * density

        val cornerRadius: Float
        val bounds: RectF

        when {
            isRoughlySquare -> {
                // Circular button / Quick Assist icon (e.g. btnQuickAssistRecords)
                val maxDim = kotlin.math.max(targetW, targetH)
                val pad = 4f * density
                val radius = (maxDim / 2f) + pad
                val cx = rawLeft + targetW / 2f
                val cy = rawTop + targetH / 2f
                bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                cornerRadius = radius
            }
            isCapsulePill -> {
                // Capsule pill button (e.g. + Add Member hero button)
                val padH = 4f * density
                val padV = 3f * density
                val effectiveH = targetH + padV * 2f
                cornerRadius = effectiveH / 2f
                bounds = RectF(rawLeft - padH, rawTop - padV, rawLeft + targetW + padH, rawTop + targetH + padV)
            }
            isCardContainer -> {
                // Heritage card / forest container (e.g. cardViewTree)
                val padH = 3f * density
                val padV = 3f * density
                cornerRadius = 20f * density // Matches bg_forest_card.xml
                bounds = RectF(rawLeft - padH, rawTop - padV, rawLeft + targetW + padH, rawTop + targetH + padV)
            }
            else -> {
                // Standard rectangular control / text field
                val padH = 4f * density
                val padV = 3f * density
                cornerRadius = 14f * density
                bounds = RectF(rawLeft - padH, rawTop - padV, rawLeft + targetW + padH, rawTop + targetH + padV)
            }
        }

        val clampedBounds = RectF(
            bounds.left.coerceAtLeast(8f * density),
            bounds.top.coerceAtLeast(8f * density),
            bounds.right.coerceAtMost(width.toFloat() - 8f * density),
            bounds.bottom.coerceAtMost(height.toFloat() - 8f * density)
        )

        return TargetFit(clampedBounds, cornerRadius)
    }

    private fun syncCutoutWithTarget(animate: Boolean) {
        val currentStep = steps.getOrNull(currentStepIndex)
        if (currentStep?.customCutoutProvider != null) {
            val customCutout = currentStep.customCutoutProvider.invoke(this)
            if (customCutout != null) {
                val fit = TargetFit(customCutout.first, customCutout.second)
                if (animate) {
                    animateCutoutAndPositionTooltip(fit, immediate = false)
                } else {
                    currentCutoutRect.set(fit.bounds)
                    cutoutRadius = fit.cornerRadius
                    targetRadius = fit.cornerRadius
                    invalidate()
                    positionTooltipRelativeTo(fit.bounds, animate = false)
                }
                return
            }
        }
        val target = currentTargetViewRef?.get() ?: return
        if (!target.isShown || !isAttachedToWindow || width == 0 || height == 0) return
        val fit = computeTargetFit(target)
        if (animate) {
            animateCutoutAndPositionTooltip(fit, immediate = false)
        } else {
            currentCutoutRect.set(fit.bounds)
            cutoutRadius = fit.cornerRadius
            targetRadius = fit.cornerRadius
            invalidate()
            positionTooltipRelativeTo(fit.bounds, animate = false)
        }
    }

    private fun animateCutoutAndPositionTooltip(targetFit: TargetFit, immediate: Boolean) {
        transitionAnimator?.cancel()
        targetRadius = targetFit.cornerRadius

        if (immediate || currentCutoutRect.isEmpty) {
            currentCutoutRect.set(targetFit.bounds)
            cutoutRadius = targetFit.cornerRadius
            invalidate()
            positionTooltipRelativeTo(targetFit.bounds, animate = false)
        } else {
            val startLeft = currentCutoutRect.left
            val startTop = currentCutoutRect.top
            val startRight = currentCutoutRect.right
            val startBottom = currentCutoutRect.bottom
            val startRadius = cutoutRadius

            transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 280L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val frac = anim.animatedValue as Float
                    currentCutoutRect.set(
                        startLeft + (targetFit.bounds.left - startLeft) * frac,
                        startTop + (targetFit.bounds.top - startTop) * frac,
                        startRight + (targetFit.bounds.right - startRight) * frac,
                        startBottom + (targetFit.bounds.bottom - startBottom) * frac
                    )
                    cutoutRadius = startRadius + (targetFit.cornerRadius - startRadius) * frac
                    invalidate()
                }
                start()
            }
            positionTooltipRelativeTo(targetFit.bounds, animate = true)
        }
    }

    private fun positionTooltipRelativeTo(targetRect: RectF, animate: Boolean) {
        tooltipView.post {
            val screenW = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val screenH = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            if (tooltipView.height == 0) {
                val wSpec = MeasureSpec.makeMeasureSpec((screenW - 32 * density).toInt().coerceAtLeast(100), MeasureSpec.EXACTLY)
                val hSpec = MeasureSpec.makeMeasureSpec(screenH, MeasureSpec.AT_MOST)
                tooltipView.measure(wSpec, hSpec)
            }
            val tooltipH = tooltipView.height.takeIf { it > 0 }
                ?: tooltipView.measuredHeight.takeIf { it > 0 }
                ?: (210 * density).toInt()
            val gap = 14f * density
            val minTop = 44f * density
            val maxTop = (screenH - tooltipH - 24f * density).coerceAtLeast(minTop)

            val spaceBelow = screenH - targetRect.bottom
            val desiredTop = if (spaceBelow >= tooltipH + gap + 24f * density) {
                (targetRect.bottom + gap).coerceIn(minTop, maxTop)
            } else {
                (targetRect.top - tooltipH - gap).coerceIn(minTop, maxTop)
            }

            if (animate) {
                tooltipView.animate()
                    .translationY(desiredTop)
                    .alpha(1f)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                tooltipView.animate().cancel()
                tooltipView.translationY = desiredTop
                tooltipView.alpha = 1f
            }
        }
    }

    private fun positionTooltipCentered(animate: Boolean) {
        tooltipView.post {
            val screenW = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val screenH = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            if (tooltipView.height == 0) {
                val wSpec = MeasureSpec.makeMeasureSpec((screenW - 32 * density).toInt().coerceAtLeast(100), MeasureSpec.EXACTLY)
                val hSpec = MeasureSpec.makeMeasureSpec(screenH, MeasureSpec.AT_MOST)
                tooltipView.measure(wSpec, hSpec)
            }
            val tooltipH = tooltipView.height.takeIf { it > 0 }
                ?: tooltipView.measuredHeight.takeIf { it > 0 }
                ?: (210 * density).toInt()

            val desiredTop = ((screenH - tooltipH) / 2f).coerceAtLeast(44f * density)

            if (animate) {
                tooltipView.animate()
                    .translationY(desiredTop)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                tooltipView.animate().cancel()
                tooltipView.translationY = desiredTop
            }
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                pulseProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        updateAdaptiveThemeColors()
        val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // 1. Draw Dark Emerald Scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // 2. Punch out rounded rectangle & draw golden spotlight ring + beacon
        if (!currentCutoutRect.isEmpty) {
            canvas.drawRoundRect(currentCutoutRect, cutoutRadius, cutoutRadius, clearPaint)

            // Expanding pulse halo
            val expand = 8f * density * pulseProgress
            pulseRect.set(
                currentCutoutRect.left - expand,
                currentCutoutRect.top - expand,
                currentCutoutRect.right + expand,
                currentCutoutRect.bottom + expand
            )
            pulseHaloPaint.alpha = ((1f - pulseProgress) * 140).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(pulseRect, cutoutRadius + expand / 2f, cutoutRadius + expand / 2f, pulseHaloPaint)

            // Crisp gold border
            canvas.drawRoundRect(currentCutoutRect, cutoutRadius, cutoutRadius, borderPaint)

            // Step number beacon at top-right of cutout
            val beaconR = 11.5f * density
            val bx = (currentCutoutRect.right - 4f * density).coerceIn(beaconR + 4f, width - beaconR - 4f)
            val by = (currentCutoutRect.top + 2f * density).coerceIn(beaconR + 4f, height - beaconR - 4f)
            canvas.drawCircle(bx, by, beaconR, beaconBgPaint)
            canvas.drawCircle(bx, by, beaconR, beaconStrokePaint)

            val stepNumText = (currentStepIndex + 1).toString()
            val textOffset = (beaconTextPaint.descent() + beaconTextPaint.ascent()) / 2f
            canvas.drawText(stepNumText, bx, by - textOffset, beaconTextPaint)
        }

        canvas.restoreToCount(layerId)

        // 3. Draw child views (Tooltip Card)
        super.dispatchDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

        var isMorphing: Boolean = false
        private set

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // If touch falls within the visible tooltip card, route directly to tooltip card
        if (tooltipView.visibility == View.VISIBLE && tooltipView.alpha > 0.05f) {
            val inTooltip = ev.x >= tooltipView.left && ev.x <= tooltipView.right &&
                    ev.y >= tooltipView.top && ev.y <= tooltipView.bottom
            if (inTooltip) {
                return super.dispatchTouchEvent(ev)
            }
        }

        // While morphing golden highlight down to a node, consume touches so double-clicks don't interrupt
        if (isMorphing) {
            return true
        }

        // Always prioritize touches on the spotlight tooltip card (Next Step, Prev Step, Dismiss)
        val tooltipRect = android.graphics.Rect()
        tooltipView.getHitRect(tooltipRect)
        if (tooltipView.visibility == View.VISIBLE && tooltipRect.contains(ev.x.toInt(), ev.y.toInt())) {
            return super.dispatchTouchEvent(ev)
        }

        // If the active step allows pass-through touches (or targets treeContainer in Step 1) and touch is inside cutout:
        val currentStep = steps.getOrNull(currentStepIndex)
        val shouldPassThrough = currentStep?.passThroughTouches == true || currentStep?.targetViewId == R.id.treeContainer
        if (shouldPassThrough && currentCutoutRect.contains(ev.x, ev.y)) {
            // Allow underlying view (e.g. tree canvas) to handle the touch directly!
            return false
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentStep = steps.getOrNull(currentStepIndex)
        val isPassThrough = currentStep?.passThroughTouches == true || currentStep?.targetViewId == R.id.treeContainer
        // If user taps the highlighted cutout view directly (for non-passThrough steps), advance to next step
        if (!isPassThrough && event.action == MotionEvent.ACTION_UP && currentCutoutRect.contains(event.x, event.y)) {
            if (currentStepIndex < steps.size - 1) {
                showStep(currentStepIndex + 1)
            } else {
                finishTour()
            }
            return true
        }
        // Consume touches on scrim so underlying buttons aren't accidentally triggered mid-tour
        return true
    }

    fun getCurrentStepIndex(): Int = currentStepIndex

    fun morphToTargetRect(
        targetRect: RectF,
        cornerRadius: Float,
        durationMs: Long = 380L,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        transitionAnimator?.cancel()
        currentTargetViewRef = null
        targetRadius = cornerRadius
        isMorphing = true

        // Convert raw screen coordinates to local spotlight view coordinates if needed
        val selfLoc = IntArray(2)
        getLocationOnScreen(selfLoc)
        val localTargetRect = if (selfLoc[0] != 0 || selfLoc[1] != 0) {
            RectF(
                targetRect.left - selfLoc[0],
                targetRect.top - selfLoc[1],
                targetRect.right - selfLoc[0],
                targetRect.bottom - selfLoc[1]
            )
        } else {
            targetRect
        }

        // Smoothly fade out tooltip card during the node focus morph
        tooltipView.animate().alpha(0f).setDuration(160L).start()

        val startLeft = currentCutoutRect.left
        val startTop = currentCutoutRect.top
        val startRight = currentCutoutRect.right
        val startBottom = currentCutoutRect.bottom
        val startRadius = cutoutRadius

        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val frac = anim.animatedValue as Float
                currentCutoutRect.set(
                    startLeft + (localTargetRect.left - startLeft) * frac,
                    startTop + (localTargetRect.top - startTop) * frac,
                    startRight + (localTargetRect.right - startRight) * frac,
                    startBottom + (localTargetRect.bottom - startBottom) * frac
                )
                cutoutRadius = startRadius + (cornerRadius - startRadius) * frac
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isMorphing = false
                    onAnimationEnd?.invoke()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    isMorphing = false
                }
            })
            start()
        }
    }

    fun nextStep() {
        if (currentStepIndex < steps.size - 1) {
            showStep(currentStepIndex + 1)
        } else {
            finishTour()
        }
    }

    fun previousStep() {
        if (currentStepIndex > 0) {
            showStep(currentStepIndex - 1)
        }
    }

    fun finishTour() {
        try {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
                viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        } catch (_: Exception) {}
        currentTargetViewRef = null
        transitionAnimator?.cancel()
        pulseAnimator?.cancel()
        try {
            activeDialogProvider?.invoke()?.dismiss()
        } catch (_: Exception) {}
        animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                try {
                    (parent as? ViewGroup)?.removeView(this)
                } catch (_: Exception) {}
                onCompleteCallback?.invoke()
            }
            .start()
    }

    override fun onDetachedFromWindow() {
        try {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
                viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        } catch (_: Exception) {}
        currentTargetViewRef = null
        transitionAnimator?.cancel()
        pulseAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
