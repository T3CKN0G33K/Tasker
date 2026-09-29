package com.example.pantry.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tasker.MainActivity
import com.example.tasker.R

object NotificationHelper {
    const val LOW_STOCK_CHANNEL_ID = "low_stock_channel"
    const val CALENDAR_CHANNEL_ID = "calendar_reminders_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val lowStockChannel = NotificationChannel(
                LOW_STOCK_CHANNEL_ID,
                "Low Stock Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts when pantry inventory items fall below stock thresholds"
                enableVibration(true)
            }

            val calendarChannel = NotificationChannel(
                CALENDAR_CHANNEL_ID,
                "Calendar & Shift Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Exact scheduled reminders for events, appointments, and shifts"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(lowStockChannel)
            notificationManager.createNotificationChannel(calendarChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        intent: Intent? = null
    ) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    fun showLowStockNotification(context: Context, itemName: String, quantity: Double, unit: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_screen", "pantry")
        }

        val qtyStr = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
        val notificationId = ("low_stock_" + itemName).hashCode()

        postNotification(
            context = context,
            channelId = LOW_STOCK_CHANNEL_ID,
            notificationId = notificationId,
            title = "Low Stock Alert: $itemName",
            message = "$itemName is low in stock ($qtyStr $unit remaining). Tap to restock.",
            intent = intent
        )
    }

    fun showCalendarReminderNotification(
        context: Context,
        eventId: String,
        title: String,
        time: String,
        notes: String?
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_screen", "calendar")
        }

        val notificationId = ("calendar_" + eventId).hashCode()
        val message = "Scheduled for $time${if (!notes.isNullOrBlank()) " • $notes" else ""}"

        postNotification(
            context = context,
            channelId = CALENDAR_CHANNEL_ID,
            notificationId = notificationId,
            title = "Upcoming: $title",
            message = message,
            intent = intent
        )
    }
}
