package com.example.btproject2.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Lightweight, zero-dependency shimmer effect helper.
 * Provides smooth pulsing and 150ms crossfading between skeleton masks and live data.
 */
object KinTraceShimmerHelper {
    private val activeAnimators = mutableMapOf<View, ObjectAnimator>()

    /**
     * Starts a gentle, breathing pulse animation on the skeleton view.
     */
    fun startShimmer(shimmerView: View) {
        stopShimmer(shimmerView)
        shimmerView.visibility = View.VISIBLE
        shimmerView.alpha = 1.0f

        val pulse = ObjectAnimator.ofFloat(shimmerView, View.ALPHA, 0.45f, 0.95f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        activeAnimators[shimmerView] = pulse
        pulse.start()
    }

    /**
     * Cancels any running animation on the given shimmer view.
     */
    fun stopShimmer(shimmerView: View) {
        activeAnimators.remove(shimmerView)?.cancel()
    }

    /**
     * Crossfades smoothly from the skeleton shimmer placeholder to the populated content view.
     */
    fun crossfade(shimmerView: View, contentView: View, durationMs: Long = 150L) {
        stopShimmer(shimmerView)

        contentView.alpha = 0f
        contentView.visibility = View.VISIBLE
        contentView.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setListener(null)
            .start()

        shimmerView.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shimmerView.visibility = View.GONE
                }
            })
            .start()
    }

    /**
     * Instantly hides shimmer without animation.
     */
    fun hideInstantly(shimmerView: View) {
        stopShimmer(shimmerView)
        shimmerView.visibility = View.GONE
    }
}
