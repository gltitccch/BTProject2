package com.example.btproject2.ui.dialogs

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.btproject2.R
import com.example.btproject2.services.MilestoneNotificationManager
import com.example.btproject2.ui.activities.EditMemberActivity
import com.example.btproject2.ui.activities.InteractiveTreeActivity
import com.example.btproject2.ui.views.HeritageCelebrationOverlayView
import com.example.btproject2.utils.HeritageMilestoneEngine
import com.example.btproject2.utils.ThemePreferences

/**
 * HeritageMilestoneDialog — Unified celebration & remembrance experience.
 *
 * Visually displays the celebration for 5 distinct family event categories
 * in both Dark Emerald (Forest Green & Gold) and Light Heritage Ivory modes:
 * 1. Living Birthday (Confetti & Greeting card generator)
 * 2. Sacred Memorial & Babang Luksa (Sampaguita petals, peace doves & 24h Virtual Candle)
 * 3. Milestone Wedding Anniversary (Silver/Golden confetti & couple tribute)
 * 4. Undas / All Souls' Day (Ancestral vigil altar & clan prayer)
 * 5. Clan Heritage Quest (Puzzle piece & missing profile completion)
 *
 * Guaranteed 100% dead-centered layout, full-screen particle cascade,
 * spring pop-in entry, and continuous 60fps hero micro-animations.
 */
object HeritageMilestoneDialog {

    fun show(
        context: Context,
        milestone: HeritageMilestoneEngine.Milestone,
        treeId: String = ""
    ) {
        val activity = context as? Activity
        if (activity != null && (activity.isFinishing || activity.isDestroyed)) return

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_heritage_milestone, null)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        // 100% Screen coverage for floating particle rain + perfect center alignment
        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setGravity(Gravity.CENTER)
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val scrimRoot = view.findViewById<View>(R.id.dialogMilestoneScrimRoot)
        val cardRoot = view.findViewById<LinearLayout>(R.id.cardMilestoneRoot)
        val tvPill = view.findViewById<TextView>(R.id.tvMilestonePill)
        val ivAvatar = view.findViewById<ImageView>(R.id.ivMilestoneAvatar)
        val tvAvatarEmojiFallback = view.findViewById<TextView>(R.id.tvAvatarEmojiFallback)
        val tvMemberName = view.findViewById<TextView>(R.id.tvMilestoneMemberName)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvMilestoneSubtitle)
        val layoutQuoteBox = view.findViewById<LinearLayout>(R.id.layoutMilestoneQuoteBox)
        val tvMessage = view.findViewById<TextView>(R.id.tvMilestoneMessage)
        val btnPrimary = view.findViewById<Button>(R.id.btnMilestonePrimary)
        val btnSecondary = view.findViewById<Button>(R.id.btnMilestoneSecondary)
        val btnDismiss = view.findViewById<TextView>(R.id.btnMilestoneDismiss)

        val heroBirthday = view.findViewById<View>(R.id.heroBirthdayAvatar)
        val heroMemorial = view.findViewById<View>(R.id.heroMemorialCandle)
        val tvMemorialCandleIcon = view.findViewById<TextView>(R.id.tvMemorialCandleIcon)
        val heroWedding = view.findViewById<View>(R.id.heroWeddingRings)
        val heroUndas = view.findViewById<View>(R.id.heroUndasAltar)
        val heroQuest = view.findViewById<View>(R.id.heroQuestPuzzle)
        val celebrationOverlay = view.findViewById<HeritageCelebrationOverlayView>(R.id.celebrationOverlayView)

        // Dismiss when tapping outside the card on the backdrop scrim
        scrimRoot.setOnClickListener {
            celebrationOverlay.stopAnimation()
            dialog.dismiss()
        }
        // Consume touches on card itself so inner clicks don't close dialog
        cardRoot.setOnClickListener { /* no-op */ }

        tvMemberName.text = milestone.personName

        // Hide all heroes initially
        heroBirthday.visibility = View.GONE
        heroMemorial.visibility = View.GONE
        heroWedding.visibility = View.GONE
        heroUndas.visibility = View.GONE
        heroQuest.visibility = View.GONE

        // Safely populate portrait if Base64 photo is available
        var hasValidPhoto = false
        if (milestone.photoBase64.isNotBlank()) {
            try {
                val decoded = Base64.decode(milestone.photoBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                if (bmp != null) {
                    ivAvatar.setImageBitmap(bmp)
                    ivAvatar.visibility = View.VISIBLE
                    tvAvatarEmojiFallback.visibility = View.GONE
                    hasValidPhoto = true
                }
            } catch (_: Throwable) {
            }
        }
        if (!hasValidPhoto) {
            ivAvatar.visibility = View.GONE
            tvAvatarEmojiFallback.visibility = View.VISIBLE
            tvAvatarEmojiFallback.text = "👩🏻"
        }

        // Determine current application theme
        val isDark = ThemePreferences.isDarkTheme(context)

        // Theme-aware palette styling
        if (isDark) {
            tvMemberName.setTextColor(Color.parseColor("#F5D77F")) // Heritage Gold
            tvSubtitle.setTextColor(Color.parseColor("#C8D6CD"))   // Mint Slate
            layoutQuoteBox.setBackgroundResource(R.drawable.bg_milestone_quote_box)
            tvMessage.setTextColor(Color.parseColor("#E2E8F0"))    // Soft White
            btnSecondary.setBackgroundResource(R.drawable.bg_milestone_btn_ghost)
            btnSecondary.setTextColor(Color.WHITE)
            btnDismiss.setTextColor(Color.parseColor("#8FA396"))
        } else {
            // Direction A: Heritage Ivory & Warm Gold Palette
            tvMemberName.setTextColor(Color.parseColor("#1C1917")) // Deep Stone Charcoal
            tvSubtitle.setTextColor(Color.parseColor("#57534E"))   // Warm Slate
            layoutQuoteBox.setBackgroundResource(R.drawable.bg_milestone_quote_box_light)
            tvMessage.setTextColor(Color.parseColor("#292524"))    // Deep Stone Charcoal
            btnSecondary.setBackgroundResource(R.drawable.bg_milestone_btn_ghost_light)
            btnSecondary.setTextColor(Color.parseColor("#292524"))
            btnDismiss.setTextColor(Color.parseColor("#78716C"))
        }

        var particleMode = HeritageCelebrationOverlayView.ParticleMode.NONE
        val animators = mutableListOf<Animator>()

        when (milestone.category) {
            HeritageMilestoneEngine.MilestoneCategory.BIRTHDAY -> {
                if (isDark) {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_birthday)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_birthday)
                    tvPill.setTextColor(Color.parseColor("#FDA4AF"))
                } else {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_light_birthday)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_birthday_light)
                    tvPill.setTextColor(Color.parseColor("#BE123C"))
                }
                heroBirthday.visibility = View.VISIBLE

                tvPill.text = "🎂 BIRTHDAY CELEBRATION"

                val turnsAge = if (milestone.milestoneYears > 0) "Turns ${milestone.milestoneYears} Today" else "Today"
                val relationship = milestone.relationshipSubtitle.ifBlank { "Family Member" }
                tvSubtitle.text = "$relationship • $turnsAge"

                tvMessage.text = "\"${milestone.eventSubtitle.removeSurrounding("\"")}\""

                btnPrimary.setBackgroundResource(R.drawable.bg_milestone_btn_gold)
                btnPrimary.setTextColor(Color.parseColor("#060D09"))
                btnPrimary.text = "💌 Send Greeting Card to Viber/FB"

                btnSecondary.text = "📍 Find ${milestone.personName.split(" ").firstOrNull() ?: "Member"} on Family Tree"

                particleMode = HeritageCelebrationOverlayView.ParticleMode.CONFETTI

                // Continuous pulse/breathing animation for birthday avatar
                val scaleX = ObjectAnimator.ofFloat(heroBirthday, View.SCALE_X, 1.0f, 1.07f).apply {
                    duration = 1300
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val scaleY = ObjectAnimator.ofFloat(heroBirthday, View.SCALE_Y, 1.0f, 1.07f).apply {
                    duration = 1300
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                scaleX.start()
                scaleY.start()
                animators.add(scaleX)
                animators.add(scaleY)

                btnPrimary.setOnClickListener {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Happy Birthday from our Family!")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "🎉 Happy Birthday ${milestone.personName}! Wishing you joy, blessings, and good health from our family clan. — Sent via KinTrace."
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Send Greeting Card via"))
                }

                btnSecondary.setOnClickListener {
                    celebrationOverlay.stopAnimation()
                    dialog.dismiss()
                    val treeIntent = Intent(context, InteractiveTreeActivity::class.java).apply {
                        putExtra("treeId", treeId)
                        putExtra("FOCUS_MEMBER_ID", milestone.personId)
                    }
                    context.startActivity(treeIntent)
                }
            }

            HeritageMilestoneEngine.MilestoneCategory.MEMORIAL -> {
                if (isDark) {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_memorial)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_memorial)
                    tvPill.setTextColor(Color.parseColor("#FED7AA"))
                } else {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_light_memorial)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_memorial_light)
                    tvPill.setTextColor(Color.parseColor("#B45309"))
                }
                heroMemorial.visibility = View.VISIBLE

                val isBabangLuksa = milestone.isBabangLuksa
                tvPill.text = if (isBabangLuksa) "🕊️ BABANG LUKSA (1ST YEAR)" else "🕊️ IN LOVING REMEMBRANCE"

                val relationship = milestone.relationshipSubtitle.ifBlank { "Departed Ancestor" }
                tvSubtitle.text = relationship

                val ordinal = HeritageMilestoneEngine.formatOrdinal(milestone.milestoneYears)
                val quoteHeader = if (isBabangLuksa) "1st Year Babang Luksa" else "$ordinal Death Anniversary"
                tvMessage.text = "$quoteHeader\n\"${milestone.eventSubtitle.removeSurrounding("\"")}\""

                particleMode = HeritageCelebrationOverlayView.ParticleMode.SAMPAGUITA_PETALS

                // Continuous gentle candle flame sway animation
                val candleSway = ObjectAnimator.ofFloat(tvMemorialCandleIcon, View.ROTATION, -4f, 4f).apply {
                    duration = 1000
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                candleSway.start()
                animators.add(candleSway)

                val isAlreadyLit = MilestoneNotificationManager.isCandleLit(context, milestone.personId)
                if (isAlreadyLit) {
                    btnPrimary.setBackgroundResource(if (isDark) R.drawable.bg_milestone_btn_ghost else R.drawable.bg_milestone_btn_ghost_light)
                    btnPrimary.text = "🕯️ Candle Lit for 24 Hours ✓"
                    btnPrimary.setTextColor(Color.parseColor("#FBBF24"))
                    btnPrimary.isEnabled = false
                } else {
                    btnPrimary.setBackgroundResource(R.drawable.bg_milestone_btn_amber)
                    btnPrimary.text = "🕯️ Light a Memorial Candle"
                    btnPrimary.setTextColor(Color.parseColor("#1A0D00"))
                    btnPrimary.setOnClickListener {
                        MilestoneNotificationManager.recordMemorialCandleLit(context, milestone.personId)
                        btnPrimary.setBackgroundResource(if (isDark) R.drawable.bg_milestone_btn_ghost else R.drawable.bg_milestone_btn_ghost_light)
                        btnPrimary.text = "🕯️ Candle Lit for 24 Hours ✓"
                        btnPrimary.setTextColor(Color.parseColor("#FBBF24"))
                        btnPrimary.isEnabled = false
                        Toast.makeText(
                            context,
                            "🕯️ Memorial candle lit for ${milestone.personName}. A glowing flame badge is now active on the family tree for 24 hours.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                btnSecondary.text = "🕊️ Share Tribute to Family GC"
                btnSecondary.setOnClickListener {
                    val tributeText = if (isBabangLuksa) {
                        "🕊️ Remembering ${milestone.personName} on their 1st Death Anniversary (Babang Luksa). Offering our prayers and thanksgiving for their blessed life. — KinTrace Clan Vault."
                    } else {
                        "🕯️ In Loving Memory of ${milestone.personName} on their ${HeritageMilestoneEngine.formatOrdinal(milestone.milestoneYears)} Death Anniversary. Forever rooted in our hearts and prayers. — KinTrace Clan Vault."
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "In Loving Memory")
                        putExtra(Intent.EXTRA_TEXT, tributeText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Memorial Tribute via"))
                }
            }

            HeritageMilestoneEngine.MilestoneCategory.WEDDING -> {
                if (isDark) {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_wedding)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_wedding)
                    tvPill.setTextColor(Color.parseColor("#F5D77F"))
                } else {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_light_wedding)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_wedding_light)
                    tvPill.setTextColor(Color.parseColor("#92400E"))
                }
                heroWedding.visibility = View.VISIBLE

                tvPill.text = "💍 GOLDEN MILESTONE"

                val years = milestone.milestoneYears
                tvSubtitle.text = "${HeritageMilestoneEngine.formatOrdinal(years)} Wedding Anniversary"

                tvMessage.text = "\"${milestone.eventSubtitle.removeSurrounding("\"")}\""

                btnPrimary.setBackgroundResource(R.drawable.bg_milestone_btn_gold)
                btnPrimary.setTextColor(Color.parseColor("#060D09"))
                btnPrimary.text = "🥂 Send Anniversary Tribute Card"

                btnSecondary.text = "📍 View Couple on Family Tree"

                particleMode = HeritageCelebrationOverlayView.ParticleMode.CONFETTI

                // Continuous subtle pulse on wedding rings
                val ringX = ObjectAnimator.ofFloat(heroWedding, View.SCALE_X, 1.0f, 1.06f).apply {
                    duration = 1500
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val ringY = ObjectAnimator.ofFloat(heroWedding, View.SCALE_Y, 1.0f, 1.06f).apply {
                    duration = 1500
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                ringX.start()
                ringY.start()
                animators.add(ringX)
                animators.add(ringY)

                btnPrimary.setOnClickListener {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Happy Wedding Anniversary!")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "💍 Congratulations to ${milestone.personName} on celebrating ${milestone.milestoneYears} years of blessed marriage! From your loving family clan. — KinTrace."
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Send Anniversary Tribute via"))
                }

                btnSecondary.setOnClickListener {
                    celebrationOverlay.stopAnimation()
                    dialog.dismiss()
                    val treeIntent = Intent(context, InteractiveTreeActivity::class.java).apply {
                        putExtra("treeId", treeId)
                        putExtra("FOCUS_MEMBER_ID", milestone.personId)
                    }
                    context.startActivity(treeIntent)
                }
            }

            HeritageMilestoneEngine.MilestoneCategory.UNDAS_REMEMBRANCE -> {
                if (isDark) {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_memorial)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_memorial)
                    tvPill.setTextColor(Color.parseColor("#FED7AA"))
                } else {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_light_memorial)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_memorial_light)
                    tvPill.setTextColor(Color.parseColor("#B45309"))
                }
                heroUndas.visibility = View.VISIBLE

                tvPill.text = "🕯️ UNDAS CLAN REMEMBRANCE"

                tvSubtitle.text = "Bugarin Clan • All Souls' Day"
                tvMessage.text = "\"Today our clan honors the souls of departed ancestors who paved the way for our family.\""

                btnPrimary.setBackgroundResource(R.drawable.bg_milestone_btn_amber)
                btnPrimary.setTextColor(Color.parseColor("#1A0D00"))
                btnPrimary.text = "🕯️ Light Ancestral Candles"

                btnSecondary.text = "📜 View Departed Ancestor Roll"

                particleMode = HeritageCelebrationOverlayView.ParticleMode.SAMPAGUITA_PETALS

                // Continuous subtle altar flicker
                val altarFlicker = ObjectAnimator.ofFloat(heroUndas, View.ALPHA, 0.82f, 1.0f).apply {
                    duration = 850
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                altarFlicker.start()
                animators.add(altarFlicker)

                btnPrimary.setOnClickListener {
                    MilestoneNotificationManager.recordMemorialCandleLit(context, "clan_undas")
                    btnPrimary.setBackgroundResource(if (isDark) R.drawable.bg_milestone_btn_ghost else R.drawable.bg_milestone_btn_ghost_light)
                    btnPrimary.text = "🕯️ Clan Candles Lit ✓"
                    btnPrimary.setTextColor(Color.parseColor("#FBBF24"))
                    btnPrimary.isEnabled = false
                    Toast.makeText(context, "🕯️ Offering memorial prayers for all departed clan ancestors.", Toast.LENGTH_SHORT).show()
                }

                btnSecondary.setOnClickListener {
                    celebrationOverlay.stopAnimation()
                    dialog.dismiss()
                    val treeIntent = Intent(context, InteractiveTreeActivity::class.java).apply {
                        putExtra("treeId", treeId)
                    }
                    context.startActivity(treeIntent)
                }
            }

            HeritageMilestoneEngine.MilestoneCategory.CLAN_QUEST -> {
                if (isDark) {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_quest)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_quest)
                    tvPill.setTextColor(Color.parseColor("#99F6E4"))
                } else {
                    cardRoot.setBackgroundResource(R.drawable.bg_milestone_card_light_quest)
                    tvPill.setBackgroundResource(R.drawable.bg_milestone_pill_quest_light)
                    tvPill.setTextColor(Color.parseColor("#0F766E"))
                }
                heroQuest.visibility = View.VISIBLE

                tvPill.text = "🧩 CLAN HERITAGE QUEST"

                tvSubtitle.text = "Missing Birthplace & Maiden Details"
                tvMessage.text = "\"Did you know? ${milestone.personName}'s branch is incomplete. Next time you gather with family, ask where they were born so our clan history is preserved!\""

                btnPrimary.setBackgroundResource(R.drawable.bg_milestone_btn_teal)
                btnPrimary.setTextColor(Color.WHITE)
                btnPrimary.text = "✏️ Complete ${milestone.personName.split(" ").firstOrNull() ?: "Member"}'s Profile"

                btnSecondary.text = "📢 Ask Family in Messenger GC"

                particleMode = HeritageCelebrationOverlayView.ParticleMode.NONE

                // Continuous smooth vertical hovering for quest puzzle piece
                val questFloat = ObjectAnimator.ofFloat(heroQuest, View.TRANSLATION_Y, 0f, -14f).apply {
                    duration = 1300
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                questFloat.start()
                animators.add(questFloat)

                btnPrimary.setOnClickListener {
                    celebrationOverlay.stopAnimation()
                    dialog.dismiss()
                    val editIntent = Intent(context, EditMemberActivity::class.java).apply {
                        putExtra("personId", milestone.personId)
                        putExtra("PERSON_ID", milestone.personId)
                        putExtra("TREE_ID", treeId)
                    }
                    context.startActivity(editIntent)
                }

                btnSecondary.setOnClickListener {
                    val questText = "🧩 KinTrace Family Quest: We are completing ${milestone.personName}'s profile in our family tree. Who remembers their birthplace or maiden details? Let's piece it together!"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "KinTrace Family Quest")
                        putExtra(Intent.EXTRA_TEXT, questText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Quest to Family via"))
                }
            }
        }

        btnDismiss.setOnClickListener {
            celebrationOverlay.stopAnimation()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            celebrationOverlay.stopAnimation()
            animators.forEach { it.cancel() }
            animators.clear()
        }

        // Spring Pop-In Entry Animation on the Modal Card
        cardRoot.scaleX = 0.82f
        cardRoot.scaleY = 0.82f
        cardRoot.alpha = 0f

        dialog.show()

        cardRoot.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.25f))
            .start()

        if (particleMode != HeritageCelebrationOverlayView.ParticleMode.NONE) {
            celebrationOverlay.startAnimation(particleMode)
        }
    }
}
