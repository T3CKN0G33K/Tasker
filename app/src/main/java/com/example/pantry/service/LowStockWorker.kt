package com.example.pantry.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pantry.notifications.NotificationHelper
import com.example.pantry.ui.PantryTrackingMode
import com.example.pantry.ui.UserPreferences
import com.example.pantry.ui.estimateAt
import com.example.pantry.ui.isRunningLow
import com.example.pantry.ui.pantryItemFromMap
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
            val alertsEnabled = UserPreferences.isLowStockAlertsEnabled(appContext)

            Log.d("LowStockWorker", "Executing background low stock sync for household $hid...")

            val db = Firebase.firestore
            val snapshot = db.collection("households").document(hid)
                .collection("inventory")
                .get()
                .await()

            if (snapshot != null && !snapshot.isEmpty) {
                snapshot.documents.forEach { doc ->
                    val item = pantryItemFromMap(doc.id, doc.data.orEmpty())
                    val name = item.name
                    val unit = item.unit
                    val estimate = item.estimateAt(System.currentTimeMillis())
                    val notificationQuantity = estimate.daysRemaining ?: estimate.totalRemaining ?: item.quantity
                    val predictsDays = PantryTrackingMode.fromStored(item.trackingMode) in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)
                    val notificationThreshold = if (predictsDays) item.restockLeadDays else item.lowStockThreshold
                    val notificationUnit = if (predictsDays) "days" else unit
                    val lastNotifiedQty = UserPreferences.getLastNotifiedStock(appContext, doc.id)
                    val isLowStock = item.isRunningLow(System.currentTimeMillis())

                    if (isLowStock && name.isNotBlank() && alertsEnabled && NotificationHelper.hasNotificationPermission(appContext)) {
                        if (UserPreferences.claimLowStockNotification(appContext, doc.id, notificationQuantity)) {
                            Log.d("LowStockWorker", "Low stock detected in background for $name ($notificationQuantity $notificationUnit remaining)")
                            NotificationHelper.showLowStockNotification(
                                context = appContext,
                                itemName = name,
                                quantity = notificationQuantity,
                                unit = notificationUnit
                            )
                        }
                    } else if (isLowStock && !alertsEnabled) {
                        UserPreferences.claimLowStockNotification(appContext, doc.id, notificationQuantity)
                    } else if (!isLowStock && lastNotifiedQty != null && lastNotifiedQty <= notificationThreshold) {
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
