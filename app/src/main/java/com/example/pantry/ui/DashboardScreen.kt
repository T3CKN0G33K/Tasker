package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantry.notifications.NotificationHelper
import com.example.tasker.R
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore


data class DashboardTabItem(
    val id: Int,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val dashboardTabItems = listOf(
    DashboardTabItem(0, "Pantry", Icons.Default.List),
    DashboardTabItem(1, "Calendar", Icons.Default.CalendarToday),
    DashboardTabItem(2, "Budget", Icons.Default.AccountBalance),
    DashboardTabItem(3, "Board", Icons.Default.Dashboard),
    DashboardTabItem(4, "Settings", Icons.Default.Settings)
)

@Composable
fun AppleLuminousMeshBackground(
    themePreference: String = "DEFAULT",
    isDarkMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isPurple = themePreference.equals("PURPLE", ignoreCase = true)
    val imageRes = if (isPurple) R.drawable.bg_luminous_blue else R.drawable.bg_luminous_purple

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun DashboardScreen(
    currentUser: UserProfile,
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onProfileUpdated: (UserProfile) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var pantryItems by remember { mutableStateOf<List<PantryItem>>(emptyList()) }
    var pantryLoading by remember { mutableStateOf(true) }
    var pantrySyncError by remember { mutableStateOf<String?>(null) }
    var pantryProductToOpen by remember { mutableStateOf<String?>(null) }
    var pantryFullScreenDestination by remember { mutableStateOf(false) }
    var forecastNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val purchasePriceHistoryByItem = remember { androidx.compose.runtime.mutableStateMapOf<String, List<Double>>() }
    var showAboutVersionScreen by remember { mutableStateOf(false) }
    var showProfileEditScreen by remember { mutableStateOf(false) }

    val db = Firebase.firestore

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            forecastNowMillis = System.currentTimeMillis()
        }
    }

    // Real-time Firestore Sync at Dashboard level
    DisposableEffect(currentUser.householdId) {
        purchasePriceHistoryByItem.clear()
        pantryItems = emptyList()
        pantryLoading = true
        pantrySyncError = null
        val householdId = currentUser.householdId
        if (householdId == null) {
            pantryLoading = false
            onDispose { }
        } else {
            val purchaseListeners = mutableMapOf<String, ListenerRegistration>()
            val inventoryListener = db.collection("households").document(householdId).collection("inventory")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        pantryLoading = false
                        pantrySyncError = error.localizedMessage ?: "Inventory could not be synchronized."
                        Toast.makeText(context, "Sync Error: ${error.message}", Toast.LENGTH_LONG).show()
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        pantryLoading = false
                        pantrySyncError = null
                        val activeIds = snapshot.documents.map { it.id }.toSet()
                        purchaseListeners.keys.filterNot(activeIds::contains).toList().forEach { staleId ->
                            purchaseListeners.remove(staleId)?.remove()
                            purchasePriceHistoryByItem.remove(staleId)
                        }
                        snapshot.documents.forEach { doc ->
                            if (doc.id !in purchaseListeners) {
                                purchaseListeners[doc.id] = doc.reference.collection("events")
                                    .whereEqualTo("type", PantryEventType.PURCHASE_RECORDED.name)
                                    .addSnapshotListener { eventSnapshot, _ ->
                                        if (eventSnapshot == null) return@addSnapshotListener
                                        val history = eventSnapshot.documents.mapNotNull { eventDoc ->
                                            val event = eventDoc.data.orEmpty()
                                            val metadata = event["metadata"] as? Map<*, *> ?: return@mapNotNull null
                                            val total = (metadata["purchaseTotal"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                                            val count = (metadata["packageCount"] as? String)?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
                                            val occurredAt = (event["occurredAt"] as? Number)?.toLong() ?: 0L
                                            occurredAt to (total / count)
                                        }.sortedBy { it.first }.takeLast(12).map { it.second }
                                        purchasePriceHistoryByItem[doc.id] = history
                                        pantryItems = pantryItems.map { item ->
                                            if (item.id == doc.id) item.copy(purchasePriceHistory = history) else item
                                        }
                                    }
                            }
                        }
                        val items = snapshot.documents.mapNotNull { doc ->
                            val pantryItem = pantryItemFromMap(
                                doc.id,
                                doc.data.orEmpty(),
                                UserPreferences.getDefaultInventoryStep(context).toDouble()
                            )
                            val lowStockThreshold = pantryItem.lowStockThreshold
                            val quantity = pantryItem.quantity
                            val name = pantryItem.name
                            val unit = pantryItem.unit

                            val lastNotifiedQty = UserPreferences.getLastNotifiedStock(context, doc.id)
                            val notificationEstimate = pantryItem.estimateAt(System.currentTimeMillis())
                            val notificationQuantity = notificationEstimate.daysRemaining ?: notificationEstimate.totalRemaining ?: quantity
                            val predictsDays = PantryTrackingMode.fromStored(pantryItem.trackingMode) in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)
                            val notificationThreshold = if (predictsDays) pantryItem.restockLeadDays else lowStockThreshold
                            val notificationUnit = if (predictsDays) "days" else unit
                            val isNowLow = pantryItem.isRunningLow(System.currentTimeMillis())

                            if (isNowLow && UserPreferences.isLowStockAlertsEnabled(context) && NotificationHelper.hasNotificationPermission(context)) {
                                if (UserPreferences.claimLowStockNotification(context, doc.id, notificationQuantity)) {
                                    NotificationHelper.showLowStockNotification(
                                        context = context,
                                        itemName = name,
                                        quantity = notificationQuantity,
                                        unit = notificationUnit
                                    )
                                }
                            } else if (isNowLow && !UserPreferences.isLowStockAlertsEnabled(context)) {
                                // Keep the baseline current while alerts are off so re-enabling
                                // them does not send stale notifications for old stock levels.
                                UserPreferences.claimLowStockNotification(context, doc.id, notificationQuantity)
                            } else if (!isNowLow) {
                                if (lastNotifiedQty != null && lastNotifiedQty <= notificationThreshold) {
                                    UserPreferences.saveLastNotifiedStock(context, doc.id, Double.MAX_VALUE)
                                }
                            }
                            pantryItem.copy(purchasePriceHistory = purchasePriceHistoryByItem[doc.id] ?: pantryItem.purchasePriceHistory)
                        }
                        pantryItems = items.sortedBy { it.name }
                    }
                }
            onDispose {
                inventoryListener.remove()
                purchaseListeners.values.forEach(ListenerRegistration::remove)
                purchaseListeners.clear()
            }
        }
    }

    val appColors = com.example.pantry.ui.theme.LocalAppColors.current

    // Outer root container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {

        // LAYER 2: The interactive UI content sits OUTSIDE layerBackdrop:
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith
                    fadeOut(animationSpec = tween(180))
                },
                label = "ScreenTransition"
            ) { targetTab ->
                key(targetTab) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (targetTab) {
                            0 -> PantryScreen(
                                items = pantryItems,
                                isLoading = pantryLoading,
                                loadError = pantrySyncError,
                                openItemId = pantryProductToOpen,
                                onItemOpenHandled = { pantryProductToOpen = null },
                                onFullScreenDestinationChange = { pantryFullScreenDestination = it },
                                isDarkMode = isDarkMode,
                                themePreference = currentUser.themePreference,
                                defaultQuantityStep = UserPreferences.getDefaultInventoryStep(context).toDouble(),
                                onItemUpdate = { updatedItem ->
                                    try {
                                        currentUser.householdId?.let { hid ->
                                            db.collection("households").document(hid)
                                                .collection("inventory").document(updatedItem.id)
                                                .set(updatedItem.toFirestoreMap(), SetOptions.merge())
                                                .addOnFailureListener { error ->
                                                    Toast.makeText(context, "Pantry settings not saved: ${error.localizedMessage ?: "check connection"}", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                    } catch (error: Exception) {
                                        Toast.makeText(context, "Pantry settings not saved: ${error.localizedMessage ?: "check connection"}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onInventoryEvent = { updatedItem, events ->
                                    currentUser.householdId?.let { hid ->
                                        val previousItem = pantryItems.firstOrNull { it.id == updatedItem.id }
                                        val stable = PantryTransition(updatedItem, events).withStableEventIds(previousItem)
                                            ?: return@let
                                        pantryItems = pantryItems.map { if (it.id == updatedItem.id) stable.item else it }
                                        val itemRef = db.collection("households").document(hid)
                                            .collection("inventory").document(updatedItem.id)
                                        val batch = db.batch()
                                        batch.set(itemRef, stable.item.toFirestoreMap(), SetOptions.merge())
                                        stable.events.forEach { event ->
                                            batch.set(itemRef.collection("events").document(event.id), event.toFirestoreMap())
                                        }
                                        batch.commit().addOnFailureListener { error ->
                                            if (previousItem != null) {
                                                pantryItems = pantryItems.map { if (it.id == updatedItem.id) previousItem else it }
                                            }
                                            Toast.makeText(context, "Pantry update not saved: ${error.localizedMessage ?: "check connection"}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onItemDelete = { itemId ->
                                    try {
                                        currentUser.householdId?.let { hid ->
                                            db.collection("households").document(hid)
                                                .collection("inventory").document(itemId)
                                                .delete()
                                        }
                                    } catch (_: Exception) {}
                                },
                                onAddItem = { newItem ->
                                    try {
                                        currentUser.householdId?.let { hid ->
                                            val docRef = db.collection("households").document(hid).collection("inventory").document(newItem.id)
                                            docRef.set(newItem.toFirestoreMap(), SetOptions.merge())
                                        }
                                    } catch (_: Exception) {}
                                },
                                onAddInventoryItem = { newItem, events ->
                                    currentUser.householdId?.let { hid ->
                                        val itemRef = db.collection("households").document(hid)
                                            .collection("inventory").document(newItem.id)
                                        pantryItems = pantryItems + newItem
                                        val batch = db.batch()
                                        batch.set(itemRef, newItem.toFirestoreMap(), SetOptions.merge())
                                        events.forEach { event ->
                                            batch.set(itemRef.collection("events").document(event.id), event.toFirestoreMap())
                                        }
                                        batch.commit().addOnFailureListener { error ->
                                            pantryItems = pantryItems.filterNot { it.id == newItem.id }
                                            Toast.makeText(context, "Pantry item not saved: ${error.localizedMessage ?: "check connection"}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                            1 -> CalendarScreen(
                                currentUser = currentUser,
                                isDarkMode = isDarkMode,
                                forecastEvents = PantryForecastEngine.recommendations(pantryItems, forecastNowMillis)
                                    .map { rec ->
                                        CalendarEvent(
                                            id = rec.id,
                                            title = "Restock ${rec.productName}",
                                            date = rec.suggestedDate.toString(),
                                            time = "All day",
                                            notes = "Estimated shopping suggestion. ${rec.reason}",
                                            category = "Restock",
                                            isForecast = true
                                        )
                                    }
                            )
                            2 -> BudgetScreen(
                                currentUser = currentUser,
                                isDarkMode = isDarkMode,
                                pantryItems = pantryItems,
                                onViewPantryItem = { productId ->
                                    pantryProductToOpen = productId
                                    selectedTab = 0
                                },
                                onRecordPantryPurchase = { item, count ->
                                    currentUser.householdId?.let { hid ->
                                        val now = System.currentTimeMillis()
                                        val eventId = "shopping-${item.id}-${java.time.LocalDate.now()}-$count"
                                        val transition = item.addPurchasedPackages(count, now, eventId)
                                        if (transition.events.isNotEmpty()) {
                                            val itemRef = db.collection("households").document(hid)
                                                .collection("inventory").document(item.id)
                                            val batch = db.batch()
                                            batch.set(itemRef, transition.item.toFirestoreMap(), SetOptions.merge())
                                            transition.events.forEach { event -> batch.set(itemRef.collection("events").document(event.id), event.toFirestoreMap()) }
                                            batch.commit().addOnFailureListener { error ->
                                                Toast.makeText(context, "Purchase was not saved: ${error.localizedMessage ?: "check connection"}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            )
                            3 -> HouseBoardScreen(
                                currentUser = currentUser,
                                isDarkMode = isDarkMode
                            )
                            4 -> SettingsScreen(
                                currentUser = currentUser,
                                isDarkMode = isDarkMode,
                                onToggleDarkMode = onToggleDarkMode,
                                onProfileUpdated = onProfileUpdated,
                                onOpenProfileEdit = { showProfileEditScreen = true },
                                onOpenAbout = { showAboutVersionScreen = true },
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        // Full-screen AboutVersionScreen overlay
        if (showAboutVersionScreen) {
            AboutVersionScreen(
                onBack = { showAboutVersionScreen = false }
            )
        }

        // Full-screen ProfileEditScreen overlay
        if (showProfileEditScreen) {
            ProfileEditScreen(
                currentUser = currentUser,
                onBack = { showProfileEditScreen = false },
                onSaveChanges = { updatedProfile ->
                    onProfileUpdated(updatedProfile)
                    showProfileEditScreen = false
                }
            )
        }

        // Keep the floating tab bar off full-screen overlays.
        if (!showAboutVersionScreen && !showProfileEditScreen && !pantryFullScreenDestination) Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .height(64.dp)
                .svgCardContainer(
                    cornerRadius = 32.dp,
                    backgroundColor = appColors.cardSurface.copy(alpha = 0.95f),
                    borderColor = appColors.cardBorder
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dashboardTabItems.forEach { tab ->
                    val isSelected = selectedTab == tab.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedTab = tab.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) appColors.primaryAccent else appColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) appColors.primaryAccent else appColors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
