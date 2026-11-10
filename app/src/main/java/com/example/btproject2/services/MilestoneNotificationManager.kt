package com.example.btproject2.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import com.example.btproject2.receivers.MilestoneAlarmReceiver
import com.example.btproject2.ui.activities.HomeActivity
import com.example.btproject2.utils.HeritageMilestoneEngine
import com.example.btproject2.utils.TreePreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * MilestoneNotificationManager — Central manager for native Android push & lock screen notifications.
 *
 * Implements:
 * - High-priority Notification Channel with PUBLIC lock-screen visibility
 * - Daily 8:00 AM background alarm schedule via AlarmManager
 * - Offline member cache so notifications fire reliably even without internet
 * - Deep-link PendingIntent into HomeActivity to trigger celebration/memorial continuation
 */
object MilestoneNotificationManager {

    const val CHANNEL_ID = "kintrace_milestones"
    const val CHANNEL_NAME = "Clan Heritage & Milestones"
    const val PREFS_NAME = "kintrace_milestone_prefs"
    const val KEY_CACHED_MEMBERS_JSON = "cached_members_for_milestones"
    const val KEY_LAST_CHECKED_YMD = "last_checked_ymd"
    private const val PREFIX_CANDLE_LIT_TS = "candle_lit_ts_"
    const val CANDLE_DURATION_MS = 24 * 60 * 60 * 1000L

    /**
     * Records that a virtual memorial candle was lit for [personId] for 24 hours.
     */
    fun recordMemorialCandleLit(context: Context, personId: String) {
        if (personId.isBlank()) return
        getPrefs(context).edit().putLong("$PREFIX_CANDLE_LIT_TS$personId", System.currentTimeMillis()).apply()
    }

    /**
     * Checks if a virtual memorial candle is currently active (within 24 hours of being lit).
     */
    fun isCandleLit(context: Context, personId: String): Boolean {
        if (personId.isBlank()) return false
        val litAt = getPrefs(context).getLong("$PREFIX_CANDLE_LIT_TS$personId", 0L)
        if (litAt <= 0L) return false
        return (System.currentTimeMillis() - litAt) < CANDLE_DURATION_MS
    }

    // Deep-link intent extra keys
    const val EXTRA_OPEN_HERITAGE_DIALOG = "EXTRA_OPEN_HERITAGE_DIALOG"
    const val EXTRA_MILESTONE_CATEGORY = "EXTRA_MILESTONE_CATEGORY"
    const val EXTRA_MILESTONE_PERSON_ID = "EXTRA_MILESTONE_PERSON_ID"
    const val EXTRA_MILESTONE_PERSON_NAME = "EXTRA_MILESTONE_PERSON_NAME"
    const val EXTRA_MILESTONE_YEARS = "EXTRA_MILESTONE_YEARS"
    const val EXTRA_MILESTONE_TITLE = "EXTRA_MILESTONE_TITLE"
    const val EXTRA_MILESTONE_SUBTITLE = "EXTRA_MILESTONE_SUBTITLE"
    const val EXTRA_MILESTONE_BABANG_LUKSA = "EXTRA_MILESTONE_BABANG_LUKSA"
    const val EXTRA_MILESTONE_TREE_ID = "EXTRA_MILESTONE_TREE_ID"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the notification channel on Android 8.0+ (API 26+).
     * Configured for High Importance and Public Lockscreen Visibility.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily morning notifications for family birthdays, memorials, and wedding anniversaries."
                enableLights(true)
                lightColor = Color.parseColor("#D4AF37") // Gold
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules a daily morning alarm to check milestones at 8:00 AM.
     */
    fun scheduleDailyMorningAlarm(context: Context, targetHour: Int = 8, targetMinute: Int = 0) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, MilestoneAlarmReceiver::class.java).apply {
            action = "com.example.btproject2.ACTION_CHECK_MILESTONES"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If 8:00 AM already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }

    /**
     * Caches active family members into SharedPreferences so milestones can be computed
     * while the device is offline or the app is closed.
     */
    fun cacheTreeMembersForOfflineMilestones(context: Context, members: List<Person>) {
        if (members.isEmpty()) return
        try {
            val jsonArray = JSONArray()
            for (p in members) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("firstName", p.firstName)
                    put("middleName", p.middleName)
                    put("lastName", p.lastName)
                    put("birthDate", p.birthDate)
                    put("deathDate", p.deathDate)
                    put("marriageDate", p.marriageDate)
                    put("isLiving", p.isLiving)
                    put("spouseId", p.spouseId ?: "")
                    put("photoUri", p.photoUri)
                    put("photoBase64", p.photoBase64)
                    put("lineageRole", p.lineageRole)
                    put("treeId", p.treeId)
                }
                jsonArray.put(obj)
            }
            getPrefs(context).edit().putString(KEY_CACHED_MEMBERS_JSON, jsonArray.toString()).apply()
        } catch (_: Throwable) {
        }
    }

    /**
     * Retrieves the cached members from SharedPreferences.
     */
    fun getCachedTreeMembers(context: Context): List<Person> {
        val jsonStr = getPrefs(context).getString(KEY_CACHED_MEMBERS_JSON, null) ?: return emptyList()
        val list = mutableListOf<Person>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Person(
                        id = obj.optString("id"),
                        firstName = obj.optString("firstName"),
                        middleName = obj.optString("middleName"),
                        lastName = obj.optString("lastName"),
                        birthDate = obj.optString("birthDate"),
                        deathDate = obj.optString("deathDate"),
                        marriageDate = obj.optString("marriageDate"),
                        isLiving = obj.optBoolean("isLiving", true),
                        spouseId = obj.optString("spouseId").takeIf { it.isNotBlank() },
                        photoUri = obj.optString("photoUri"),
                        photoBase64 = obj.optString("photoBase64"),
                        lineageRole = obj.optString("lineageRole"),
                        treeId = obj.optString("treeId")
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return list
    }

    /**
     * Checks if today has any milestones and triggers the native lock-screen notification.
     * Prevents duplicate spamming if already checked today.
     */
    fun checkAndTriggerMilestones(context: Context, force: Boolean = false) {
        val todayCal = Calendar.getInstance()
        val todayYmd = "${todayCal.get(Calendar.YEAR)}_${todayCal.get(Calendar.MONTH)}_${todayCal.get(Calendar.DAY_OF_MONTH)}"

        val prefs = getPrefs(context)
        if (!force && prefs.getString(KEY_LAST_CHECKED_YMD, "") == todayYmd) {
            return // Already notified today
        }

        var members = getCachedTreeMembers(context)
        if (members.isEmpty()) {
            val inMem = FirestoreHelper.getCachedPersons()
            if (!inMem.isNullOrEmpty()) {
                members = inMem
                cacheTreeMembersForOfflineMilestones(context, members)
            }
        }

        if (members.isEmpty()) return

        val milestones = HeritageMilestoneEngine.findMilestonesForDate(members, todayCal, allowQuestFallback = true)
        android.util.Log.d("MilestoneNotification", "Found ${milestones.size} milestones for today. Force=$force")
        if (milestones.isNotEmpty()) {
            val activeTreeId = TreePreferences.getActiveTreeId(context)
            for ((index, m) in milestones.withIndex()) {
                showMilestoneNotification(context, m, activeTreeId, notificationId = 2000 + index)
            }
            prefs.edit().putString(KEY_LAST_CHECKED_YMD, todayYmd).apply()
        }
    }

    /**
     * Builds and posts the native Android Lock Screen notification.
     */
    fun showMilestoneNotification(
        context: Context,
        milestone: HeritageMilestoneEngine.Milestone,
        treeId: String,
        notificationId: Int = 2001
    ) {
        createNotificationChannel(context)

        // Intent to launch HomeActivity directly into the tribute dialog
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HERITAGE_DIALOG, true)
            putExtra(EXTRA_MILESTONE_CATEGORY, milestone.category.name)
            putExtra(EXTRA_MILESTONE_PERSON_ID, milestone.personId)
            putExtra(EXTRA_MILESTONE_PERSON_NAME, milestone.personName)
            putExtra(EXTRA_MILESTONE_YEARS, milestone.milestoneYears)
            putExtra(EXTRA_MILESTONE_TITLE, milestone.eventTitle)
            putExtra(EXTRA_MILESTONE_SUBTITLE, milestone.eventSubtitle)
            putExtra(EXTRA_MILESTONE_BABANG_LUKSA, milestone.isBabangLuksa)
            putExtra(EXTRA_MILESTONE_TREE_ID, treeId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val goldColor = Color.parseColor("#D4AF37")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_header_notification_bell)
            .setContentTitle(milestone.eventTitle)
            .setContentText(milestone.eventSubtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(milestone.eventSubtitle))
            .setColor(goldColor)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Prominent on Lock Screen!
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
