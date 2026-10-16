package com.example.pantry.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.pantry.notifications.NotificationHelper
import com.example.tasker.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    init {
        instance = this
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Restock Alert"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "An item in your household is running low!"

        Log.d("FCM", "Received FCM message in background/foreground: $title - $body")

        NotificationHelper.postNotification(
            context = applicationContext,
            channelId = NotificationHelper.LOW_STOCK_CHANNEL_ID,
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            message = body,
            intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_screen", "pantry")
            }
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New FCM token: $token")
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .addOnFailureListener {
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .set(mapOf("fcmToken" to token), SetOptions.merge())
                    }
            }
        } catch (e: Exception) {
            Log.e("FCM", "Failed to save token to Firestore", e)
        }
    }

    companion object {
        const val CHANNEL_ID = NotificationHelper.LOW_STOCK_CHANNEL_ID

        var instance: MyFirebaseMessagingService? = null
            private set

        fun showLocalNotification(context: Context, title: String, message: String) {
            NotificationHelper.postNotification(
                context = context,
                channelId = NotificationHelper.LOW_STOCK_CHANNEL_ID,
                notificationId = System.currentTimeMillis().toInt(),
                title = title,
                message = message,
                intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("target_screen", "pantry")
                }
            )
        }
    }
}
