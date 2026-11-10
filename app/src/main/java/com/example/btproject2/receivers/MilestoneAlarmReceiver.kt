package com.example.btproject2.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.btproject2.services.MilestoneNotificationManager

/**
 * MilestoneAlarmReceiver — Receives daily morning alarm events and device boot notifications
 * to inspect family milestones and trigger lock-screen reminders even when KinTrace is closed.
 */
class MilestoneAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        // 1. Check today's milestones and post lock-screen notifications if any match
        MilestoneNotificationManager.checkAndTriggerMilestones(context)

        // 2. Schedule the next morning's alarm at 8:00 AM
        MilestoneNotificationManager.scheduleDailyMorningAlarm(context)
    }
}
