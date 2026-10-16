package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.firebase.firestore.firestore
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs

data class DashboardTabItem(
    val id: Int,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val dashboardTabItems = listOf(
    DashboardTabItem(0, "Pantry", Icons.Default.List),
    DashboardTabItem(1, "Calendar", Icons.Default.CalendarToday),
    DashboardTabItem(2, "Budget", Icons.Default.AccountBalance),
    DashboardTabItem(3, "Shifts", Icons.Default.Schedule),
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
    var ledgerEntries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }
    var monthlyBills by remember { mutableStateOf<List<BillItem>>(emptyList()) }

    val db = Firebase.firestore
    val backdrop = rememberLayerBackdrop()

    // Real-time Firestore Sync at Dashboard level
    LaunchedEffect(currentUser.householdId) {
        currentUser.householdId?.let { householdId ->
            // 1. Pantry Inventory Sync
            db.collection("households").document(householdId).collection("inventory")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(context, "Sync Error: ${error.message}", Toast.LENGTH_LONG).show()
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            val lowStockThreshold = doc.getDouble("lowStockThreshold") ?: doc.getLong("lowStockThreshold")?.toDouble() ?: 1.0
                            val quantity = doc.getDouble("quantity") ?: doc.getLong("quantity")?.toDouble() ?: 0.0
                            val price = doc.getDouble("price") ?: 0.0
                            val name = doc.getString("name") ?: ""
                            val unit = doc.getString("unit") ?: "Units"

                            val lastNotifiedQty = UserPreferences.getLastNotifiedStock(context, doc.id)
                            val isNowLow = quantity <= lowStockThreshold

                            if (isNowLow) {
                                if (lastNotifiedQty == null || quantity < lastNotifiedQty) {
                                    UserPreferences.saveLastNotifiedStock(context, doc.id, quantity)
                                    NotificationHelper.showLowStockNotification(
                                        context = context,
                                        itemName = name,
                                        quantity = quantity,
                                        unit = unit
                                    )
                                }
                            } else {
                                if (lastNotifiedQty != null && lastNotifiedQty <= lowStockThreshold) {
                                    UserPreferences.saveLastNotifiedStock(context, doc.id, Double.MAX_VALUE)
                                }
                            }

                            PantryItem(
                                id = doc.id,
                                name = name,
                                category = doc.getString("category") ?: "",
                                quantity = quantity,
                                unit = unit,
                                lowStockThreshold = lowStockThreshold,
                                price = price,
                                emoji = doc.getString("emoji")
                            )
                        }
                        pantryItems = items.sortedBy { it.name }
                    }
                }

            // 2. Bi-Weekly Bills Sync (Guarded by persistent hasSeededBankLedger flag)
            db.collection("households").document(householdId).collection("bills")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        if (snapshot.isEmpty && !UserPreferences.hasSeededBankLedger(context)) {
                            UserPreferences.setBankLedgerSeeded(context, true)
                            val batch = db.batch()
                            defaultBillsList.forEach { bill ->
                                val docRef = db.collection("households").document(householdId)
                                    .collection("bills").document(bill.id)
                                batch.set(
                                    docRef,
                                    hashMapOf(
                                        "id" to bill.id,
                                        "name" to bill.name,
                                        "amount" to bill.amount,
                                        "dueDate" to bill.dueDate,
                                        "paidPeriodIds" to bill.paidPeriodIds
                                    ),
                                    SetOptions.merge()
                                )
                            }
                            batch.commit()
                        } else {
                            val items = snapshot.documents.mapNotNull { doc ->
                                @Suppress("UNCHECKED_CAST")
                                val paidIds = (doc.get("paidPeriodIds") as? List<String>) ?: emptyList()
                                BillItem(
                                    id = doc.id,
                                    name = doc.getString("name") ?: "Bill",
                                    amount = doc.getDouble("amount") ?: 0.0,
                                    dueDate = doc.getString("dueDate") ?: "Due 1st",
                                    paidPeriodIds = paidIds
                                )
                            }
                            monthlyBills = items
                        }
                    }
                }

            // 3. Bank Ledger Transactions Sync (Guarded by persistent hasSeededBankLedger flag)
            db.collection("households").document(householdId).collection("transactions")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        if (snapshot.isEmpty && !UserPreferences.hasSeededBankLedger(context)) {
                            UserPreferences.setBankLedgerSeeded(context, true)
                            val batch = db.batch()
                            defaultLedgerEntries.forEach { tx ->
                                val docRef = db.collection("households").document(householdId)
                                    .collection("transactions").document(tx.id)
                                batch.set(
                                    docRef,
                                    hashMapOf(
                                        "id" to tx.id,
                                        "title" to tx.title,
                                        "amount" to tx.amount,
                                        "type" to tx.type.name,
                                        "timestamp" to tx.timestamp,
                                        "category" to tx.category
                                    ),
                                    SetOptions.merge()
                                )
                            }
                            batch.commit()
                        } else {
                            val items = snapshot.documents.mapNotNull { doc ->
                                val typeStr = doc.getString("type") ?: "EXPENSE"
                                val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.EXPENSE }
                                LedgerEntry(
                                    id = doc.id,
                                    title = doc.getString("title") ?: "Transaction",
                                    amount = doc.getDouble("amount") ?: 0.0,
                                    type = type,
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                    category = doc.getString("category") ?: "General"
                                )
                            }
                            ledgerEntries = items
                        }
                    }
                }
        }
    }

    // Outer root container
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // LAYER 1: ONLY the static wallpaper/background image is captured into backdrop:
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            AppleLuminousMeshBackground(
                themePreference = currentUser.themePreference,
                isDarkMode = isDarkMode
            )
        }

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
                                backdrop = backdrop,
                                isDarkMode = isDarkMode,
                                themePreference = currentUser.themePreference,
                                onItemUpdate = { updatedItem ->
                                    try {
                                        currentUser.householdId?.let { hid ->
                                            val itemMap = hashMapOf<String, Any?>(
                                                "name" to updatedItem.name,
                                                "category" to updatedItem.category,
                                                "quantity" to updatedItem.quantity,
                                                "unit" to updatedItem.unit,
                                                "lowStockThreshold" to updatedItem.lowStockThreshold,
                                                "price" to updatedItem.price,
                                                "emoji" to updatedItem.emoji
                                            )
                                            db.collection("households").document(hid)
                                                .collection("inventory").document(updatedItem.id)
                                                .set(itemMap, SetOptions.merge())
                                        }
                                    } catch (_: Exception) {}
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
                                            val docRef = db.collection("households").document(hid).collection("inventory").document()
                                            val itemMap = hashMapOf<String, Any?>(
                                                "name" to newItem.name,
                                                "category" to newItem.category,
                                                "quantity" to newItem.quantity,
                                                "unit" to newItem.unit,
                                                "lowStockThreshold" to newItem.lowStockThreshold,
                                                "price" to newItem.price,
                                                "emoji" to newItem.emoji
                                            )
                                            docRef.set(itemMap, SetOptions.merge())
                                        }
                                    } catch (_: Exception) {}
                                }
                            )
                            1 -> CalendarScreen(
                                currentUser = currentUser,
                                backdrop = backdrop,
                                isDarkMode = isDarkMode
                            )
                            2 -> BudgetScreen(
                                currentUser = currentUser,
                                backdrop = backdrop,
                                isDarkMode = isDarkMode,
                                ledgerEntries = ledgerEntries,
                                bills = monthlyBills,
                                onUpdateLedger = { updated -> ledgerEntries = updated },
                                onUpdateBills = { updated -> monthlyBills = updated }
                            )
                            3 -> ShiftsScreen(
                                currentUser = currentUser,
                                backdrop = backdrop,
                                isDarkMode = isDarkMode,
                                onProfileUpdated = onProfileUpdated
                            )
                            4 -> SettingsScreen(
                                currentUser = currentUser,
                                isDarkMode = isDarkMode,
                                backdrop = backdrop,
                                onToggleDarkMode = onToggleDarkMode,
                                onProfileUpdated = onProfileUpdated,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        // LAYER 2: The official Kyant Liquid Glass LiquidBottomTabs (Floating Capsule Frame)
        LiquidBottomTabs(
            selectedTabIndex = { selectedTab },
            onTabSelected = { newIndex -> selectedTab = newIndex },
            backdrop = backdrop,
            tabsCount = dashboardTabItems.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
        ) {
            dashboardTabItems.forEach { tab ->
                val isSelected = selectedTab == tab.id
                LiquidBottomTab(
                    onClick = { selectedTab = tab.id }
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (isSelected) {
                            if (currentUser.themePreference.equals("PURPLE", ignoreCase = true)) Color(0xFFC084FC) else Color.White
                        } else {
                            Color.White.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
