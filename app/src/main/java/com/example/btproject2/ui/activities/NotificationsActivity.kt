package com.example.btproject2.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.ui.adapters.NotificationAdapter
import com.example.btproject2.utils.NotificationHelper

class NotificationsActivity : AppCompatActivity() {

    private val firestoreHelper = FirestoreHelper()
    private val authHelper = AuthHelper()
    private var treeId = ""
    private var allNotifications = listOf<NotificationRecord>()
    private var selectedCategory = NotificationHelper.CATEGORY_ALL
    private var announcementListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onDestroy() {
        super.onDestroy()
        announcementListener?.remove()
        announcementListener = null
    }

    private lateinit var adapter: NotificationAdapter
    private lateinit var rvNotifications: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var tvSubtitle: TextView

    private lateinit var chipAll: TextView
    private lateinit var chipAccess: TextView
    private lateinit var chipRecords: TextView
    private lateinit var chipValidation: TextView
    private lateinit var chipSecurity: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        treeId = intent.getStringExtra("TREE_ID")
            ?: intent.getStringExtra("treeId")
            ?: com.example.btproject2.utils.TreePreferences.getActiveTreeId(this)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val btnMarkAllRead = findViewById<TextView>(R.id.btnMarkAllRead)
        tvSubtitle = findViewById(R.id.tvNotifSubtitle)
        rvNotifications = findViewById(R.id.rvNotifications)
        layoutEmpty = findViewById(R.id.layoutEmptyNotifications)

        chipAll = findViewById(R.id.chipAll)
        chipAccess = findViewById(R.id.chipAccess)
        chipRecords = findViewById(R.id.chipRecords)
        chipValidation = findViewById(R.id.chipValidation)
        chipSecurity = findViewById(R.id.chipSecurity)

        btnBack.setOnClickListener { finish() }

        adapter = NotificationAdapter(emptyList()) { item ->
            showNotificationDetail(item)
        }
        rvNotifications.layoutManager = LinearLayoutManager(this)
        rvNotifications.adapter = adapter

        btnMarkAllRead.setOnClickListener {
            markAllAsRead()
        }

        setupFilterChips()
        loadNotifications()
    }

    private fun setupFilterChips() {
        val chips = listOf(
            Triple(chipAll, NotificationHelper.CATEGORY_ALL, "All"),
            Triple(chipAccess, NotificationHelper.CATEGORY_ACCESS, "Access"),
            Triple(chipRecords, NotificationHelper.CATEGORY_RECORDS, "Records"),
            Triple(chipValidation, NotificationHelper.CATEGORY_VALIDATION, "Validation"),
            Triple(chipSecurity, NotificationHelper.CATEGORY_SECURITY, "Security")
        )

        chips.forEach { (view, category, _) ->
            view.setOnClickListener {
                selectedCategory = category
                updateChipStyles()
                applyFilter()
            }
        }
    }

    private fun updateChipStyles() {
        val chips = listOf(
            Pair(chipAll, NotificationHelper.CATEGORY_ALL),
            Pair(chipAccess, NotificationHelper.CATEGORY_ACCESS),
            Pair(chipRecords, NotificationHelper.CATEGORY_RECORDS),
            Pair(chipValidation, NotificationHelper.CATEGORY_VALIDATION),
            Pair(chipSecurity, NotificationHelper.CATEGORY_SECURITY)
        )

        chips.forEach { (view, cat) ->
            if (cat.equals(selectedCategory, ignoreCase = true)) {
                view.setBackgroundResource(R.drawable.badge_pill_gold)
                view.setTextColor(ContextCompat.getColor(this, R.color.btn_gold_text))
            } else {
                view.setBackgroundResource(R.drawable.badge_pill_dark)
                view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun loadNotifications() {
        val currentUserId = authHelper.getCurrentUserId().orEmpty()
        firestoreHelper.getNotifications(treeId, currentUserId,
            onSuccess = { list ->
                val baseList = list.ifEmpty {
                    val initialNotif = NotificationRecord(
                        treeId = treeId,
                        userId = currentUserId,
                        title = "Welcome to KinTrace Notifications",
                        message = "System alerts, tree updates, and access requests will appear here in real-time.",
                        type = NotificationHelper.CATEGORY_SECURITY,
                        isRead = false
                    )
                    listOf(initialNotif)
                }

                announcementListener?.remove()
                announcementListener = firestoreHelper.listenToActiveSystemAnnouncement { announcement ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        var combinedList = baseList.filter { !it.id.startsWith("SYS_ANN_") }
                        if (announcement != null && announcement.isActive && !announcement.isExpired) {
                            val isRead = com.example.btproject2.utils.AnnouncementPreferences.isAdvisoryReadInNotifications(
                                this@NotificationsActivity,
                                announcement.id,
                                announcement.createdAt
                            )
                            val windowText = if (announcement.timeWindow.isNotBlank()) "\n\n⏱️ Schedule: ${announcement.timeWindow}" else ""
                            val advisoryNotif = NotificationRecord(
                                id = "SYS_ANN_${announcement.id}_${announcement.createdAt}",
                                treeId = treeId,
                                userId = currentUserId,
                                title = "⚠️ [SYSTEM ADVISORY] ${announcement.title}",
                                message = "${announcement.message}$windowText",
                                type = "ADVISORY",
                                isRead = isRead,
                                targetId = announcement.id,
                                timestamp = announcement.createdAt
                            )
                            combinedList = listOf(advisoryNotif) + combinedList
                        }
                        allNotifications = combinedList
                        applyFilter()
                        checkInactivityReminders(currentUserId)
                    }
                }
            },
            onFailure = {
                layoutEmpty.visibility = View.VISIBLE
                rvNotifications.visibility = View.GONE
            }
        )
    }

    /**
     * Module 8.e — Inactivity Reminder Notifications:
     * Notifies tree owner when an editor/viewer has no recent activity and prompts review/contribution.
     */
    private fun checkInactivityReminders(currentUserId: String) {
        firestoreHelper.getTree(treeId,
            onSuccess = { tree ->
                val isOwner = tree?.ownerId.isNullOrEmpty() || tree?.ownerId == currentUserId
                if (isOwner) {
                    firestoreHelper.getTreeMembers(treeId,
                        onSuccess = { members ->
                            firestoreHelper.getRecentActivities(treeId, limit = 50,
                                onSuccess = { activities ->
                                    val reminders = NotificationHelper.evaluateMemberInactivity(
                                        treeId = treeId,
                                        ownerId = currentUserId,
                                        members = members,
                                        activities = activities,
                                        thresholdDays = 14
                                    )

                                    for (reminder in reminders) {
                                        val exists = allNotifications.any {
                                            it.title == reminder.title && it.targetId == reminder.targetId
                                        }
                                        if (!exists) {
                                            firestoreHelper.addNotification(reminder, onSuccess = { newId ->
                                                allNotifications = listOf(reminder.copy(id = newId)) + allNotifications
                                                applyFilter()
                                            })
                                        }
                                    }
                                },
                                onFailure = {}
                            )
                        },
                        onFailure = {}
                    )
                }
            },
            onFailure = {}
        )
    }

    private fun applyFilter() {
        val filtered = NotificationHelper.filterNotifications(allNotifications, selectedCategory)
        adapter.updateList(filtered)

        val unreadCount = NotificationHelper.getUnreadCount(allNotifications)
        tvSubtitle.text = if (unreadCount > 0) "$unreadCount unread notification${if (unreadCount > 1) "s" else ""}"
                          else "All caught up"

        if (filtered.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvNotifications.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvNotifications.visibility = View.VISIBLE
        }
    }

    private fun showNotificationDetail(item: NotificationRecord) {
        if (!item.isRead) {
            if (item.id.startsWith("SYS_ANN_")) {
                com.example.btproject2.utils.AnnouncementPreferences.setAdvisoryReadInNotifications(
                    this,
                    item.targetId,
                    item.timestamp
                )
            } else {
                firestoreHelper.markNotificationAsRead(item.id)
            }
            allNotifications = allNotifications.map {
                if (it.id == item.id) it.copy(isRead = true) else it
            }
            applyFilter()
        }

        val builder = com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle(com.example.btproject2.utils.NotificationHelper.sanitizeNotificationTitle(item.title))
            .setMessage(item.message)

        if (item.type == "role_upgrade_request" && item.targetId.isNotBlank()) {
            builder.setPositiveButton("Approve as Editor ✓") { d, _ ->
                d.dismiss()
                val targetMemberId = "${item.treeId}_${item.targetId}"
                firestoreHelper.updateTreeMemberRole(
                    memberId = targetMemberId,
                    role = "Editor",
                    status = "Approved",
                    onSuccess = {
                        val decisionNotif = com.example.btproject2.utils.NotificationHelper.createAccessDecisionNotification(
                            treeId = item.treeId,
                            applicantUserId = item.targetId,
                            treeName = "your family tree",
                            approved = true,
                            assignedRole = "Editor"
                        )
                        firestoreHelper.addNotification(decisionNotif)
                        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyTreeLayoutChanged(
                            treeId = item.treeId,
                            sourceScreen = "NotificationsActivity"
                        )
                        android.widget.Toast.makeText(this, "✓ Member approved as Editor!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        android.widget.Toast.makeText(this, "Approval failed: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
            builder.setNegativeButton("Close", null)
        } else {
            builder.setPositiveButton("Got It") { d, _ -> d.dismiss() }
        }

        builder.show()
    }

    private fun markAllAsRead() {
        val unreadList = allNotifications.filter { !it.isRead }
        if (unreadList.isEmpty()) return

        unreadList.forEach {
            if (it.id.startsWith("SYS_ANN_")) {
                com.example.btproject2.utils.AnnouncementPreferences.setAdvisoryReadInNotifications(
                    this,
                    it.targetId,
                    it.timestamp
                )
            } else {
                firestoreHelper.markNotificationAsRead(it.id)
            }
        }
        allNotifications = allNotifications.map { it.copy(isRead = true) }
        applyFilter()
    }
}
