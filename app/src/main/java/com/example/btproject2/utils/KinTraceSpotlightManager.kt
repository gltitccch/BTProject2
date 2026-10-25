package com.example.btproject2.utils

import android.app.Activity
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.views.KinTraceSpotlightView
import com.example.btproject2.views.SpotlightStep
import java.lang.ref.WeakReference

/**
 * Central coordinator for the KinTrace Contextual First-Visit Mini-Tutorial System.
 *
 * Manages feature tutorial keys, coordinates with OnboardingPreferences + Cloud Firestore,
 * and provides canonical SpotlightStep presets for all major KinTrace screens:
 * - Home Dashboard (Stage 1 Empty State & Stage 2 Populated Dashboard)
 * - Privacy & RBAC Controls (PrivacyControlsActivity)
 * - Merge Clan Space (MergeBranchesActivity)
 * - Export Tree & Lineage (ExportTreeActivity)
 * - Trace a Bloodline (TraceActivity)
 * - Interactive Tree Canvas (InteractiveTreeActivity)
 * - Tree Health Guard & Guided Helper (TreeAuditActivity)
 * - Add Family Member (AddMemberActivity)
 */
object KinTraceSpotlightManager {

    enum class TutorialFeatureKey(val key: String) {
        HOME_STAGE1_EMPTY("tutorial_home_stage1_empty"),
        HOME_STAGE2_DASHBOARD("tutorial_home_stage2_dashboard"),
        PRIVACY_CONTROLS("tutorial_privacy_controls"),
        MERGE_CLAN_SPACE("tutorial_merge_clan_space"),
        EXPORT_TREE("tutorial_export_tree"),
        TRACE_BLOODLINE("tutorial_trace_bloodline"),
        FAMILY_TREE_PEDIGREE("tutorial_family_tree_pedigree"),
        FAN_CHART("tutorial_fan_chart"),
        INTERACTIVE_TREE("tutorial_interactive_tree"),
        TREE_HEALTH("tutorial_tree_health"),
        ADD_MEMBER("tutorial_add_member"),
        FAMILY_RECORDS("tutorial_family_records"),
        CREATE_TREE("tutorial_create_tree"),
        JOIN_TREE("tutorial_join_tree"),
        FAMILY_INSIGHTS("tutorial_family_insights")
    }

    private var activeOverlayRef: WeakReference<KinTraceSpotlightView>? = null
    private var activeActivityRef: WeakReference<Activity>? = null
    private var activeFeatureKey: TutorialFeatureKey? = null
    private val pendingFeatures = mutableSetOf<String>()
    private val pendingRunnables = mutableMapOf<String, Pair<WeakReference<View>, Runnable>>()

    /**
     * Launches the spotlight tutorial for [feature] if the current user has not yet seen it.
     */
    fun showTutorialIfNotSeen(
        activity: Activity,
        feature: TutorialFeatureKey,
        treeId: String? = null,
        steps: List<SpotlightStep> = getPresetSteps(feature),
        delayMs: Long = 420L,
        dialogProvider: (() -> android.app.Dialog?)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val resolvedTreeId = if (!treeId.isNullOrBlank()) {
            treeId
        } else {
            activity.intent?.getStringExtra("TREE_ID")
                ?: activity.intent?.getStringExtra("treeId")
                ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(activity)
        }

        if (OnboardingPreferences.hasSeenTutorial(activity, feature.key, treeId = resolvedTreeId)) return
        if (activeFeatureKey == feature && activeOverlayRef?.get()?.isAttachedToWindow == true) return
        val pendingKey = if (!resolvedTreeId.isNullOrBlank()) "${resolvedTreeId}_${feature.key}" else feature.key
        if (!pendingFeatures.add(pendingKey)) return

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot == null) {
            pendingFeatures.remove(pendingKey)
            return
        }
        val runnable = Runnable {
            pendingFeatures.remove(pendingKey)
            pendingRunnables.remove(pendingKey)
            if (activity.isFinishing || activity.isDestroyed) return@Runnable
            if (OnboardingPreferences.hasSeenTutorial(activity, feature.key, treeId = resolvedTreeId)) return@Runnable
            if (activeFeatureKey == feature && activeOverlayRef?.get()?.isAttachedToWindow == true) return@Runnable
            val effectiveDialogProvider = dialogProvider ?: { (activity as? com.example.btproject2.ui.activities.InteractiveTreeActivity)?.getActiveProfileDialog() }
            showTutorialForced(activity, feature, resolvedTreeId, steps, effectiveDialogProvider)
        }
        pendingRunnables[pendingKey] = Pair(WeakReference(contentRoot), runnable)
        contentRoot.postDelayed(runnable, delayMs)
    }

    /**
     * Immediately launches (or replays) the spotlight tutorial for [feature].
     */
    fun showTutorialForced(
        activity: Activity,
        feature: TutorialFeatureKey,
        treeId: String? = null,
        steps: List<SpotlightStep> = getPresetSteps(feature),
        dialogProvider: (() -> android.app.Dialog?)? = null
    ) {
        val resolvedTreeId = if (!treeId.isNullOrBlank()) {
            treeId
        } else {
            activity.intent?.getStringExtra("TREE_ID")
                ?: activity.intent?.getStringExtra("treeId")
                ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(activity)
        }
        val pendingKey = if (!resolvedTreeId.isNullOrBlank()) "${resolvedTreeId}_${feature.key}" else feature.key
        pendingFeatures.remove(pendingKey)
        if (activity.isFinishing || activity.isDestroyed || steps.isEmpty()) return
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Remove any existing active spotlight overlay
        dismissActiveOverlay()

        val effectiveDialogProvider = dialogProvider ?: { (activity as? com.example.btproject2.ui.activities.InteractiveTreeActivity)?.getActiveProfileDialog() }
        val spotlightView = KinTraceSpotlightView(activity)
        spotlightView.activeDialogProvider = effectiveDialogProvider
        contentRoot.addView(
            spotlightView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        spotlightView.bringToFront()
        activeOverlayRef = WeakReference(spotlightView)
        activeActivityRef = WeakReference(activity)
        activeFeatureKey = feature

        // Mark seen immediately when started/completed so user is never spammed
        markTutorialCompleted(activity, feature, resolvedTreeId)

        spotlightView.startTour(steps) {
            activeOverlayRef = null
            activeActivityRef = null
            activeFeatureKey = null
        }
    }

    fun isTourActive(): Boolean = activeOverlayRef?.get() != null

    fun getActiveSpotlightView(): KinTraceSpotlightView? = activeOverlayRef?.get()

    fun cancelPendingTutorials(feature: TutorialFeatureKey? = null, treeId: String? = null) {
        if (feature == null) {
            pendingRunnables.values.forEach { (viewRef, runnable) ->
                viewRef.get()?.removeCallbacks(runnable)
            }
            pendingRunnables.clear()
            pendingFeatures.clear()
        } else {
            val keyPrefix = if (!treeId.isNullOrBlank()) "${treeId}_${feature.key}" else feature.key
            val keysToRemove = pendingRunnables.keys.filter { it.startsWith(keyPrefix) || it.endsWith(feature.key) }
            keysToRemove.forEach { k ->
                val pair = pendingRunnables.remove(k)
                pair?.first?.get()?.removeCallbacks(pair.second)
                pendingFeatures.remove(k)
            }
        }
    }

    fun dismissActiveOverlay() {
        cancelPendingTutorials()
        try {
            val existing = activeOverlayRef?.get()
            if (existing != null) {
                (existing.parent as? ViewGroup)?.removeView(existing)
            }
        } catch (_: Exception) {}
        activeOverlayRef = null
        activeActivityRef = null
        activeFeatureKey = null
    }

    private fun markTutorialCompleted(activity: Activity, feature: TutorialFeatureKey, treeId: String? = null) {
        OnboardingPreferences.setTutorialSeen(activity, feature.key, true, treeId = treeId)
        try {
            val uid = AuthHelper().getCurrentUserId().orEmpty()
            if (uid.isNotEmpty()) {
                val updatedState = OnboardingPreferences.getOnboardingState(activity, uid)
                FirestoreHelper().saveOnboardingState(uid, updatedState)
            }
        } catch (_: Exception) {}
    }

    /**
     * Canonical step definitions mapped to verified layout resource IDs across KinTrace.
     * All instructions, titles, descriptions, and pro-tips are strictly formulated in
     * simple, friendly Layman's Terms so any family member can immediately understand.
     */
    private enum class FanCutoutType {
        CENTER_HUB,
        GENERATIONS_RINGS,
        FAMILY_WEDGE,
        ALL_CLAN_BUTTON,
        BOTTOM_LEGEND,
        ZOOM_HUD
    }

    private fun getFanChartCutout(
        spotlightView: KinTraceSpotlightView,
        type: FanCutoutType
    ): Pair<RectF, Float>? {
        val root = spotlightView.rootView ?: return null
        val webView = root.findViewById<View>(R.id.webViewFan) ?: return null
        if (!webView.isShown || webView.width == 0 || webView.height == 0) return null

        val targetLoc = IntArray(2)
        val selfLoc = IntArray(2)
        webView.getLocationOnScreen(targetLoc)
        spotlightView.getLocationOnScreen(selfLoc)

        val left = (targetLoc[0] - selfLoc[0]).toFloat()
        val top = (targetLoc[1] - selfLoc[1]).toFloat()
        val w = webView.width.toFloat()
        val h = webView.height.toFloat()
        val density = spotlightView.resources.displayMetrics.density

        val cx = left + w / 2f
        // Center of the Horseshoe fan circle inside WebView canvas
        val cy = top + h * 0.528f

        return when (type) {
            FanCutoutType.CENTER_HUB -> {
                val r = (w * 0.11f).coerceIn(40f * density, 62f * density)
                Pair(RectF(cx - r, cy - r, cx + r, cy + r), r)
            }
            FanCutoutType.GENERATIONS_RINGS -> {
                val rOuter = (w * 0.44f).coerceIn(120f * density, 185f * density)
                val bounds = RectF(
                    (cx - rOuter - 8f * density).coerceAtLeast(left + 8f * density),
                    cy - rOuter - 8f * density,
                    (cx + rOuter + 8f * density).coerceAtMost(left + w - 8f * density),
                    cy + 16f * density
                )
                Pair(bounds, 24f * density)
            }
            FanCutoutType.FAMILY_WEDGE -> {
                val wx = cx - w * 0.18f
                val wy = cy - h * 0.08f
                val r = 32f * density
                Pair(RectF(wx - r, wy - r, wx + r, wy + r), r)
            }
            FanCutoutType.ALL_CLAN_BUTTON -> {
                val pillLeft = left + (w - 169f * density).coerceAtLeast(left + 220f * density)
                val pillRight = left + (w - 88f * density).coerceAtMost(left + w - 80f * density)
                val pillTop = top + 17f * density
                val pillBottom = pillTop + 27f * density
                Pair(RectF(pillLeft, pillTop, pillRight, pillBottom), (pillBottom - pillTop) / 2f)
            }
            FanCutoutType.BOTTOM_LEGEND -> {
                val legH = 34f * density
                val bMargin = 10f * density
                val legBottom = top + h - bMargin
                val legTop = legBottom - legH
                Pair(RectF(left + 12f * density, legTop, left + w - 12f * density, legBottom), 10f * density)
            }
            FanCutoutType.ZOOM_HUD -> {
                val hudW = 145f * density
                val hudH = 34f * density
                val hudBottom = top + h - 56f * density
                val hudTop = hudBottom - hudH
                Pair(RectF(cx - hudW / 2f, hudTop, cx + hudW / 2f, hudBottom), hudH / 2f)
            }
        }
    }

    fun getPresetSteps(feature: TutorialFeatureKey): List<SpotlightStep> {
        return when (feature) {
            TutorialFeatureKey.HOME_STAGE1_EMPTY -> listOf(
                SpotlightStep(
                    targetViewId = R.id.cardQuestHud,
                    icon = "🌱",
                    title = "Quick Start Checklist",
                    description = "Follow these 4 simple steps to name your tree, add your first relatives, explore the tree view, and invite family.",
                    proTip = "💡 Tip: Tap any step button to jump straight to it."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnEmptyCreateTree,
                    icon = "🌳",
                    title = "Start Your Family Tree",
                    description = "Welcome to KinTrace! Tap here to give your family tree a name and choose if you want it private or open to family.",
                    proTip = "👑 Tip: You are the creator and owner of this family tree."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnEmptyJoinTree,
                    icon = "🔑",
                    title = "Join a Relative's Tree",
                    description = "If someone in your family already made a tree on KinTrace, enter their 6-letter invite code here to join them.",
                    proTip = "🛡️ Tip: Ask your relative for their invite code from their Privacy screen."
                )
            )

            TutorialFeatureKey.HOME_STAGE2_DASHBOARD -> listOf(
                SpotlightStep(
                    targetViewId = R.id.btnAddMemberHero,
                    icon = "➕",
                    title = "Add Family Members",
                    description = "Tap here to add a parent, child, spouse, or sibling. When you add someone, KinTrace connects both people together automatically.",
                    proTip = "💡 Tip: The app double-checks relationships so your tree stays accurate."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardViewTree,
                    icon = "🌳",
                    title = "View Your Family Tree",
                    description = "See your family laid out on a real family tree! Zoom in, move around, and switch between branch views and circular fan charts.",
                    proTip = "💡 Tip: Tap on anyone's photo or name to see more about them."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardRecords,
                    icon = "📋",
                    title = "Family Member List",
                    description = "A complete list of all your relatives in one place. Easily search by name or birth town, and filter between living or passed kin.",
                    proTip = "💡 Tip: Tap any relative to view or edit their birth story, photos, and records."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardTrace,
                    icon = "🧭",
                    title = "Trace How Two People Are Related",
                    description = "Pick any two relatives in your family tree, and KinTrace will tell you their relationship (like 2nd cousins, aunt and nephew, or in-laws).",
                    proTip = "💡 Tip: It also shows you the shared grandparent or ancestor who connects them."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardMerge,
                    icon = "🧬",
                    title = "Connect with a Relative's Tree",
                    description = "If a distant cousin or relative also uses KinTrace, you can swap a simple 6-letter code to link your two family branches together safely.",
                    proTip = "💡 Tip: You can preview the combined tree first to make sure everything looks right before saving."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnPrivacy,
                    icon = "🛡️",
                    title = "Protect Family Privacy",
                    description = "Decide who can see your family tree. You can invite relatives as Viewers (they can only look) or Editors (they can help add people).",
                    proTip = "💡 Tip: Exact birthdays of living family members are automatically kept private for safety."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardExportTree,
                    icon = "📤",
                    title = "Save & Print Your Tree",
                    description = "Download your family tree as a printable PDF book or poster to share with your family at reunions.",
                    proTip = "💡 Tip: You can also export a standard family file to use with other family history apps."
                )
            )

            TutorialFeatureKey.PRIVACY_CONTROLS -> listOf(
                SpotlightStep(
                    targetViewId = R.id.switchPrivateTree,
                    icon = "🛡️",
                    title = "Make Tree Private or Public",
                    description = "Turn this on so only relatives with your invite code can see your tree. Keep it private to protect your family's personal memories.",
                    proTip = "🔒 Tip: We recommend keeping active family trees private."
                ),
                SpotlightStep(
                    targetViewId = R.id.switchHideBirthDates,
                    icon = "🎂",
                    title = "Hide Birthdays of Living Relatives",
                    description = "Automatically keeps exact birth dates of living relatives private, so their identity stays safe while still celebrating your heritage.",
                    proTip = "💡 Tip: Birth dates of passed ancestors stay visible for family history."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnInviteMember,
                    icon = "🔑",
                    title = "Invite Family Members",
                    description = "Create a 6-letter invite code to share with relatives. You choose if they can only view the tree or also help add new relatives.",
                    proTip = "👥 Tip: You can change or cancel anyone's access anytime right here."
                )
            )

            TutorialFeatureKey.MERGE_CLAN_SPACE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.cardSynthesizeClanHero,
                    icon = "🧬",
                    title = "Safe Tree Synthesis",
                    description = "Combine two separate family trees (like your father's and mother's sides) into a new master clan tree without changing your original trees.",
                    proTip = "✨ Tip: Both original trees remain 100% untouched and safe in this sandbox."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardMyMergeCode,
                    icon = "🔑",
                    title = "Your Tree Link Code",
                    description = "Share this 6-letter code with a relative when you discover you share common great-grandparents.",
                    proTip = "📋 Tip: Tap Copy to easily send the code to your relative on chat or message."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnOpenConnectBranches,
                    icon = "🔗",
                    title = "Connect with a Relative's Tree",
                    description = "Got a link code from a relative? Tap here to propose linking your two branches together across a shared ancestor.",
                    proTip = "🛡️ Tip: Both tree owners must approve before branches connect."
                )
            )

            TutorialFeatureKey.EXPORT_TREE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.cardPdf,
                    icon = "📑",
                    title = "Choose What to Save",
                    description = "Pick PDF to create a printable family book, a photo image for a poster, or a family data file (GEDCOM) for other genealogy apps.",
                    proTip = "💡 Tip: The PDF book is perfect for family reunions."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnExport,
                    icon = "📤",
                    title = "Download & Share",
                    description = "Creates your family book or file in seconds and lets you send it via email, messaging, or save it directly to your phone.",
                    proTip = "⚡ Tip: Everything is safely prepared right on your phone."
                )
            )

            TutorialFeatureKey.TRACE_BLOODLINE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.cardSelectPersonA,
                    icon = "👤",
                    title = "Pick First Relative (Person A)",
                    description = "Tap here to search and pick the first family member you want to compare.",
                    proTip = "🔍 Tip: Type their first name, surname, or birth year."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardSelectPersonB,
                    icon = "🎯",
                    title = "Pick Second Relative (Person B)",
                    description = "Select the second family member. Tap the swap button (⇅) in the middle anytime to switch them.",
                    proTip = "🔄 Tip: Works for blood relatives as well as in-laws."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnTrace,
                    icon = "⚡",
                    title = "Find Relationship",
                    description = "Calculates exactly how they are related (like first cousins, aunt and nephew, or in-laws) and explains the connection step by step.",
                    proTip = "📜 Tip: Shows the shared ancestor who links both relatives together."
                )
            )

            TutorialFeatureKey.FAMILY_TREE_PEDIGREE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.tvGenLabel,
                    icon = "🧬",
                    title = "Family Levels (Generations)",
                    description = "This list organizes your family from youngest to oldest. Generation 1 is the starting person, Generation 2 is their parents, Generation 3 is grandparents, and so on.",
                    proTip = "💡 Tip: Scroll down the page to travel further back into your family's history."
                ),
                SpotlightStep(
                    targetViewId = R.id.layoutMemberCard,
                    icon = "📇",
                    title = "Each Ancestor's Card",
                    description = "Each box shows one ancestor with their birth year, death year, and who they are in the family. Tap any card to see their full profile.",
                    proTip = "🔒 Tip: For privacy, exact birthdays of living family members are kept private."
                ),
                SpotlightStep(
                    targetViewId = R.id.tvKinshipPill,
                    icon = "👑",
                    title = "Family Relationship Tag",
                    description = "These gold tags tell you at a glance who is a Grandfather, Grandmother, or original Family Founder.",
                    proTip = "✨ Tip: It makes it easy to see who is on your father's side versus your mother's side."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnFanChart,
                    icon = "🪭",
                    title = "360° Family Wheel",
                    description = "Tap the Fan Chart button to see your family in a circular wheel radiating outward in colorful rings.",
                    proTip = "🪭 Tip: Perfect for seeing father and mother branches side-by-side."
                )
            )

            TutorialFeatureKey.FAN_CHART -> listOf(
                SpotlightStep(
                    targetViewId = R.id.webViewFan,
                    icon = "👑",
                    title = "Clan Roots Hub (The Center)",
                    description = "The golden semi-circle in the middle shows the Clan Roots (Russell – Bennett). All generations of your family branch outward from this central foundation.",
                    proTip = "💡 Tip: The inner ring represents your oldest ancestors, and the outer rings represent newer generations.",
                    customCutoutProvider = { sv -> getFanChartCutout(sv, FanCutoutType.CENTER_HUB) }
                ),
                SpotlightStep(
                    targetViewId = R.id.webViewFan,
                    icon = "⭕",
                    title = "Generations Radiating Outward",
                    description = "Older ancestors sit in the inner green ring (Gen 1). As your family expands, each new tier grows outward: Gen 2 Teal, Gen 3 Steel Blue, and Gen 4 Navy.",
                    proTip = "🌳 Tip: You can see up to 5 generations of family growth all on one screen.",
                    customCutoutProvider = { sv -> getFanChartCutout(sv, FanCutoutType.GENERATIONS_RINGS) }
                ),
                SpotlightStep(
                    targetViewId = R.id.webViewFan,
                    icon = "👆",
                    title = "Tap Any Family Wedge",
                    description = "Tap on any relative's colored block (like Walter Bennett or George Russell) to instantly view their life years, parents, and stories in a quick popup.",
                    proTip = "✨ Tip: Try tapping on the wheel right now to test the popup card!",
                    customCutoutProvider = { sv -> getFanChartCutout(sv, FanCutoutType.FAMILY_WEDGE) }
                ),
                SpotlightStep(
                    targetViewId = R.id.webViewFan,
                    icon = "🔄",
                    title = "Center Fan Chart On Anyone (All Clan ▾)",
                    description = "Tap the 'All Clan ▾' button at the top to open the Center drawer! You can filter by generation and center the entire fan chart around any specific person, or tap 'All Clan' to return to the full clan overview.",
                    proTip = "🎯 Tip: Centering on a grandchild reveals their direct ancestral line going all the way back.",
                    customCutoutProvider = { sv -> getFanChartCutout(sv, FanCutoutType.ALL_CLAN_BUTTON) }
                ),
                SpotlightStep(
                    targetViewId = R.id.webViewFan,
                    icon = "🎨",
                    title = "Generation Legend & Member Counts",
                    description = "The strip at the bottom shows each generation's color dot and the exact number of relatives in that generation level (for example: Gen 3 has 7 members).",
                    proTip = "📊 Tip: This helps you see at a glance how balanced each generation of your family is.",
                    customCutoutProvider = { sv -> getFanChartCutout(sv, FanCutoutType.BOTTOM_LEGEND) }
                ),
                SpotlightStep(
                    targetViewId = R.id.webViewFan,
                    icon = "🔍",
                    title = "Zoom In & Center the Wheel",
                    description = "Outer rings can get small! Use the plus (+) and minus (-) buttons at the bottom to zoom in close, or tap the reset button (⟲) to jump back to the center.",
                    proTip = "⚡ Tip: You can also pinch with two fingers on your phone screen to zoom in and out smoothly.",
                    customCutoutProvider = { sv -> getFanChartCutout(sv, FanCutoutType.ZOOM_HUD) }
                )
            )

            TutorialFeatureKey.INTERACTIVE_TREE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.treeContainer,
                    icon = "🌳",
                    title = "Tap Any Family Member",
                    description = "Everyone in your family has their own card on this screen. Tap any person (or tap Next Step) to open their full profile and story!",
                    proTip = "💡 Tip: You can drag people around with your finger to arrange your family tree however you like.",
                    passThroughTouches = true
                ),
                SpotlightStep(
                    targetViewId = R.id.ivProfilePhoto,
                    icon = "👤",
                    title = "Name, Photo & Life Years",
                    description = "The top card shows their picture, full name, how long they lived, and whether they are still living today.",
                    proTip = "📷 Tip: Tap the little camera icon anytime to add a photo of them.",
                    onStepEnter = {
                        val act = activeActivityRef?.get() as? com.example.btproject2.ui.activities.InteractiveTreeActivity
                        act?.openTutorialFounderProfile()
                    }
                ),
                SpotlightStep(
                    targetViewId = R.id.tvProfileKinshipBadge,
                    icon = "👑",
                    title = "Their Role in the Family",
                    description = "KinTrace automatically figures out who this person is in the family—like Grandfather, Mother, Uncle, or Family Founder—based on who they are connected to.",
                    proTip = "👑 Tip: This helps everyone at family reunions easily know who started the family line."
                ),
                SpotlightStep(
                    targetViewId = R.id.tvProfileParents,
                    icon = "🔗",
                    title = "Parents, Partner & Children",
                    description = "See who their mom, dad, husband/wife, and children are. Tapping any name jumps right to them on your tree!",
                    proTip = "❤️ Tip: When you connect a parent or spouse, KinTrace links both people together automatically."
                ),
                SpotlightStep(
                    targetViewId = R.id.layoutProfileDocsContainer,
                    icon = "📑",
                    title = "Keep Certificates & Old Photos",
                    description = "Save birth certificates, wedding photos, and old letters right here so your family's precious memories never get lost.",
                    proTip = "📄 Tip: Once saved, your whole family can see and treasure these pictures."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnProfileToggleVitalStatus,
                    icon = "🌿",
                    title = "Update Living / Deceased Status",
                    description = "Tap this button to switch whether this person is living or deceased. You can record their passing date and resting place to honor their memory.",
                    proTip = "🕊️ Tip: When marked deceased, an eternal rest badge appears beside their name across your tree."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnProfileAddChild,
                    icon = "👶",
                    title = "+ Child: Add Sons & Daughters",
                    description = "Tap '+ Child' to instantly connect a son or daughter to this person. KinTrace places them on the branch below automatically.",
                    proTip = "🌱 Tip: KinTrace automatically links them to both parents when you add a child."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnProfileAddSpouse,
                    icon = "💍",
                    title = "+ Spouse: Add Husband or Wife",
                    description = "Tap '+ Spouse' to connect a marriage partner and record their wedding anniversary date.",
                    proTip = "❤️ Tip: Both spouses will be linked together side-by-side with a wedding knot."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnProfileEdit,
                    icon = "✏️",
                    title = "Edit Details & Full Life Story",
                    description = "Use 'Edit' to fix misspelled names, birth years, or change photos. Tap 'Full Profile' right beside it to open their complete timeline and family stories!",
                    proTip = "📖 Tip: The Full Profile screen lets you view their lifetime milestones and write personal stories."
                )
            )

            TutorialFeatureKey.TREE_HEALTH -> listOf(
                SpotlightStep(
                    targetViewId = R.id.tvSummaryHeadline,
                    icon = "🩺",
                    title = "Tree Health Checker",
                    description = "Scans your family tree for accidental mistakes—like someone's child being listed as older than the parent, or duplicate people.",
                    proTip = "🔍 Tip: Easily see if everything is accurate or if a few details need checking."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnLaunchConflictWizard,
                    icon = "🧭",
                    title = "Guided Family Helper",
                    description = "A friendly step-by-step assistant that asks you simple questions to fix any mistakes without messing up your family records.",
                    proTip = "⚡ Tip: Fixes missing marriages and parent links with a single tap."
                )
            )

            TutorialFeatureKey.ADD_MEMBER -> listOf(
                SpotlightStep(
                    targetViewId = R.id.etFirstName,
                    icon = "👤",
                    title = "Relative's Name & Details",
                    description = "Enter your relative's first name, last name, and birth date. KinTrace uses these to verify generation age and celebrate your clan history.",
                    proTip = "💡 Tip: For living relatives, exact birth dates can be kept private."
                ),
                SpotlightStep(
                    targetViewId = R.id.spinnerFather,
                    icon = "🔗",
                    title = "Connect to Parents or Spouse",
                    description = "Select who their father, mother, or spouse is. KinTrace links both sides of the relationship automatically in your family tree!",
                    proTip = "🛡️ Tip: If you are adding the clan founder, you can leave parents blank."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnSaveMember,
                    icon = "🌱",
                    title = "Plant into Family Tree",
                    description = "Tap Save to weave this relative into your tree. All connected branches, generation counts, and pedigree views update instantly in real time!",
                    proTip = "⚡ Tip: You can tap on their node anytime later to add photos or documents."
                )
            )

            TutorialFeatureKey.FAMILY_RECORDS -> listOf(
                SpotlightStep(
                    targetViewId = R.id.etSearch,
                    icon = "🔍",
                    title = "Search Family Members",
                    description = "Type any first name, maiden surname, or birthplace to instantly find a relative in your tree.",
                    proTip = "⚡ Tip: The directory updates in real-time as you type."
                ),
                SpotlightStep(
                    targetViewId = R.id.rvMembers,
                    icon = "📋",
                    title = "Family Member Directory",
                    description = "Browse all relatives in your family tree. Tap on any relative to view their story, life events, photos, and records.",
                    proTip = "💡 Tip: Exact birthdays of living relatives are automatically kept private for safety."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnQuickAssistRecords,
                    icon = "🎓",
                    title = "KinAcademy & Quick Help",
                    description = "Tap here anytime you need a quick reminder on how records work or want to replay this tutorial.",
                    proTip = "✨ Tip: Quick tips are available on every screen in the app."
                )
            )

            TutorialFeatureKey.CREATE_TREE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.etTreeName,
                    icon = "🌱",
                    title = "Name Your Family Tree",
                    description = "Type a name for your tree, like 'The Malone Family' or 'Santos Clan'. This is how your family will identify it.",
                    proTip = "💡 Tip: You can change the tree name later anytime in settings."
                ),
                SpotlightStep(
                    targetViewId = R.id.layoutFounderPills,
                    icon = "👑",
                    title = "Choose How You Start",
                    description = "Pick whether you are starting this tree as the Patriarch, Matriarch, or a Family Member.",
                    proTip = "✨ Tip: This sets the first person at the root of your family tree."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnCreateTree,
                    icon = "🌳",
                    title = "Plant Your Tree",
                    description = "Tap here to save your tree. Once created, you can immediately begin adding parents, children, and spouses!",
                    proTip = "⚡ Tip: Your tree is saved safely in your private account."
                )
            )

            TutorialFeatureKey.JOIN_TREE -> listOf(
                SpotlightStep(
                    targetViewId = R.id.etInviteCode,
                    icon = "🔑",
                    title = "Enter Family Invite Code",
                    description = "Type the 6-letter invite code given to you by a relative who already created a tree on KinTrace.",
                    proTip = "💡 Tip: Codes look like M2A4SX. Ask the tree owner for their code."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnRequestAccess,
                    icon = "🤝",
                    title = "Request Access",
                    description = "Tap here to send your join request. Once the tree owner approves, this family tree will appear on your home screen.",
                    proTip = "🛡️ Tip: Your request is sent securely directly to the tree owner."
                )
            )

            TutorialFeatureKey.FAMILY_INSIGHTS -> listOf(
                SpotlightStep(
                    targetViewId = R.id.tvTotalMembers,
                    icon = "📊",
                    title = "Family Clan Overview",
                    description = "See high-level counts of all documented relatives, living family members, and connected branches across generations.",
                    proTip = "💡 Tip: Data refreshes in real-time as your relatives add new people."
                ),
                SpotlightStep(
                    targetViewId = R.id.cardDataQualityScorecard,
                    icon = "🩺",
                    title = "Tree Health & Quality Score",
                    description = "Measures your family tree's accuracy and documentation completeness, flagging any missing parent links or unlinked spouses.",
                    proTip = "✨ Tip: Tap 'Audit Tree' anytime to let the guided helper fix mistakes automatically."
                ),
                SpotlightStep(
                    targetViewId = R.id.btnQuickAssistInsights,
                    icon = "🎓",
                    title = "KinAcademy Help & Replay",
                    description = "Tap here whenever you want to learn more about clan statistics or replay this tutorial.",
                    proTip = "🛡️ Tip: Quick assistance is always available on every screen."
                )
            )
        }
    }
}
