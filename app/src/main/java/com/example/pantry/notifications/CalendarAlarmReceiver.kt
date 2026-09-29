package com.example.pantry.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CalendarAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: ""
        val title = intent.getStringExtra("event_title") ?: "Upcoming Event"
        val time = intent.getStringExtra("event_time") ?: ""
        val notes = intent.getStringExtra("event_notes")

        Log.d("ALARM_RECEIVER", "Alarm received for event '$title' (id=$eventId)")

        NotificationHelper.showCalendarReminderNotification(
            context = context,
            eventId = eventId,
            title = title,
            time = time,
            notes = notes
        )
    }
}
