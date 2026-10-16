package com.example.pantry.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pantry.notifications.NotificationHelper
import com.example.pantry.ui.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class LowStockWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val user = UserPreferences.getUser(appContext)
            val hid = user?.householdId ?: return Result.success()

            Log.d("LowStockWorker", "Executing background low stock sync for household $hid...")

            val db = Firebase.firestore
            val snapshot = db.collection("households").document(hid)
                .collection("inventory")
                .get()
                .await()

            if (snapshot != null && !snapshot.isEmpty) {
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("name") ?: ""
                    val unit = doc.getString("unit") ?: "Units"
                    val qty = doc.getDouble("quantity") ?: doc.getLong("quantity")?.toDouble() ?: 0.0
                    val threshold = doc.getDouble("lowStockThreshold") ?: doc.getLong("lowStockThreshold")?.toDouble() ?: 1.0

                    val lastNotifiedQty = UserPreferences.getLastNotifiedStock(appContext, doc.id)
                    val isLowStock = qty <= threshold

                    if (isLowStock && name.isNotBlank()) {
                        if (lastNotifiedQty == null || qty < lastNotifiedQty) {
                            Log.d("LowStockWorker", "Low stock detected in background for $name ($qty $unit remaining)")
                            UserPreferences.saveLastNotifiedStock(appContext, doc.id, qty)
                            NotificationHelper.showLowStockNotification(
                                context = appContext,
                                itemName = name,
                                quantity = qty,
                                unit = unit
                            )
                        }
                    } else if (lastNotifiedQty != null && lastNotifiedQty <= threshold) {
                        UserPreferences.saveLastNotifiedStock(appContext, doc.id, Double.MAX_VALUE)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("LowStockWorker", "Error in background low stock worker: ${e.message}", e)
            Result.retry()
        }
    }
}
