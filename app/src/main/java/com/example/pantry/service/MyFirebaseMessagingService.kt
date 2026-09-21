package com.example.pantry.service

import android.R
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
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
        val title = remoteMessage.notification?.title ?: "Restock Alert"
        val body = remoteMessage.notification?.body ?: "An item is running low!"
        showLocalNotification(applicationContext, title, body)
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
        const val CHANNEL_ID = "restock_alerts_channel"

        var instance: MyFirebaseMessagingService? = null
            private set

        fun showLocalNotification(context: Context, title: String, message: String) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
