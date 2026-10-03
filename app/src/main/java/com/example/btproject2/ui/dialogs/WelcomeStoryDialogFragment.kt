package com.example.btproject2.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.ui.activities.CreateTreeActivity
import com.example.btproject2.ui.activities.JoinTreeActivity
import com.example.btproject2.utils.OnboardingPreferences

/**
 * Phase 1 Onboarding Modal: "Pamana: Preserving Your Family Legacy".
 *
 * Provides a 3-slide cultural introduction to KinTrace and embeds direct action cards
 * allowing users to immediately plant a tree or join an existing clan.
 */
class WelcomeStoryDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "WelcomeStoryDialogFragment"

        fun newInstance(): WelcomeStoryDialogFragment {
            return WelcomeStoryDialogFragment()
        }

        fun showIfNotSeen(activity: AppCompatActivity): Boolean {
            if (activity.isFinishing || activity.isDestroyed) return false
            if (activity.supportFragmentManager.isStateSaved) return false
            if (OnboardingPreferences.hasSeenWelcomeStory(activity)) return false
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return false

            val dialog = newInstance()
            dialog.show(activity.supportFragmentManager, TAG)
            return true
        }
    }

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return inflater.inflate(R.layout.dialog_welcome_storybook, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val vpStory = view.findViewById<ViewPager2>(R.id.vpWelcomeStory)
        val btnClose = view.findViewById<TextView>(R.id.btnStoryClose)
        val btnPrev = view.findViewById<Button>(R.id.btnStoryPrev)
        val btnNext = view.findViewById<Button>(R.id.btnStoryNext)
        val tvDot1 = view.findViewById<TextView>(R.id.tvDot1)
        val tvDot2 = view.findViewById<TextView>(R.id.tvDot2)
        val tvDot3 = view.findViewById<TextView>(R.id.tvDot3)

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val hintColor = ContextCompat.getColor(requireContext(), R.color.text_hint)

        val adapter = WelcomeStoryAdapter(
            onPlantTreeClicked = {
                markSeenAndSync()
                dismissAllowingStateLoss()
                startActivity(Intent(requireContext(), CreateTreeActivity::class.java))
            },
            onJoinClanClicked = {
                markSeenAndSync()
                dismissAllowingStateLoss()
                startActivity(Intent(requireContext(), JoinTreeActivity::class.java))
            },
            onExploreFirstClicked = {
                markSeenAndSync()
                dismissAllowingStateLoss()
            }
        )

        vpStory.adapter = adapter

        fun updateIndicators(position: Int) {
            when (position) {
                0 -> {
                    btnPrev.visibility = View.INVISIBLE
                    btnNext.text = "Next ›"
                    tvDot1.text = "●"; tvDot1.setTextColor(primaryColor)
                    tvDot2.text = "○"; tvDot2.setTextColor(hintColor)
                    tvDot3.text = "○"; tvDot3.setTextColor(hintColor)
                }
                1 -> {
                    btnPrev.visibility = View.VISIBLE
                    btnNext.text = "Next ›"
                    tvDot1.text = "○"; tvDot1.setTextColor(hintColor)
                    tvDot2.text = "●"; tvDot2.setTextColor(primaryColor)
                    tvDot3.text = "○"; tvDot3.setTextColor(hintColor)
                }
                2 -> {
                    btnPrev.visibility = View.VISIBLE
                    btnNext.text = "Get Started ➔"
                    tvDot1.text = "○"; tvDot1.setTextColor(hintColor)
                    tvDot2.text = "○"; tvDot2.setTextColor(hintColor)
                    tvDot3.text = "●"; tvDot3.setTextColor(primaryColor)
                }
            }
        }

        vpStory.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
            }
        })

        updateIndicators(0)

        btnClose.setOnClickListener {
            markSeenAndSync()
            dismissAllowingStateLoss()
        }

        btnPrev.setOnClickListener {
            val current = vpStory.currentItem
            if (current > 0) {
                vpStory.currentItem = current - 1
            }
        }

        btnNext.setOnClickListener {
            val current = vpStory.currentItem
            if (current < 2) {
                vpStory.currentItem = current + 1
            } else {
                markSeenAndSync()
                dismissAllowingStateLoss()
            }
        }
    }

    private fun markSeenAndSync() {
        val context = context ?: return
        OnboardingPreferences.setHasSeenWelcomeStory(context, true)

        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        if (currentUserId.isNotEmpty()) {
            val state = OnboardingPreferences.getOnboardingState(context)
            firestoreHelper.saveOnboardingState(currentUserId, state)
        }
    }
}
