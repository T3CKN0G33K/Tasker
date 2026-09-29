package com.example.pantry.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object CalendarAlarmScheduler {

    fun scheduleReminder(
        context: Context,
        eventId: String,
        eventTitle: String,
        eventTime: String,
        eventNotes: String?,
        reminderEpochMillis: Long
    ) {
        if (reminderEpochMillis <= System.currentTimeMillis() || eventId.isBlank()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, CalendarAlarmReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("event_title", eventTitle)
            putExtra("event_time", eventTime)
            putExtra("event_notes", eventNotes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderEpochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderEpochMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderEpochMillis,
                    pendingIntent
                )
            }
            Log.d("ALARM_SCHEDULER", "Scheduled exact alarm for '$eventTitle' at $reminderEpochMillis (id=$eventId)")
        } catch (e: SecurityException) {
            Log.e("ALARM_SCHEDULER", "SecurityException scheduling exact alarm: ${e.message}", e)
        }
    }

    fun cancelReminder(context: Context, eventId: String) {
        if (eventId.isBlank()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, CalendarAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("ALARM_SCHEDULER", "Cancelled alarm for eventId=$eventId")
        }
    }
}
