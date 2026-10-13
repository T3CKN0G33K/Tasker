package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.liquidglass.LiquidGlassTabBarCompose
import com.example.liquidglass.LiquidGlassTabItem
import com.example.pantry.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

val dashboardTabItems = listOf(
    LiquidGlassTabItem(0, "Pantry", Icons.Default.List),
    LiquidGlassTabItem(1, "Calendar", Icons.Default.CalendarToday),
    LiquidGlassTabItem(2, "Budget", Icons.Default.AccountBalance),
    LiquidGlassTabItem(3, "Shifts", Icons.Default.Schedule),
    LiquidGlassTabItem(4, "Settings", Icons.Default.Settings)
)

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
    val previousStockState = remember { mutableMapOf<String, Double>() }

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

                            val prevQty = previousStockState[doc.id]
                            val isNowLow = quantity <= lowStockThreshold
                            val wasLow = prevQty != null && prevQty <= lowStockThreshold

                            if (isNowLow && (!wasLow || (prevQty != null && quantity < prevQty))) {
                                NotificationHelper.showLowStockNotification(
                                    context = context,
                                    itemName = name,
                                    quantity = quantity,
                                    unit = unit
                                )
                            }
                            previousStockState[doc.id] = quantity

                            PantryItem(
                                id = doc.id,
                                name = name,
                                category = doc.getString("category") ?: "",
                                quantity = quantity,
                                unit = unit,
                                lowStockThreshold = lowStockThreshold,
                                price = price
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

    val ambientMeshGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0B132B),
            Color(0xFF083344).copy(alpha = 0.85f),
            Color(0xFF0F172A),
            Color(0xFF06B6D4).copy(alpha = 0.25f)
        )
    )

    val purpleMeshGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E0A3C),
            Color(0xFF3B1566).copy(alpha = 0.85f),
            Color(0xFF120524),
            Color(0xFF9E6BC2).copy(alpha = 0.30f)
        )
    )

    val bgModifier = when (currentUser.themePreference.uppercase()) {
        "PURPLE" -> Modifier.background(purpleMeshGradient)
        else -> if (isDarkMode) Modifier.background(ambientMeshGradient) else Modifier.background(Color(0xFFF2F2F7))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    when (targetTab) {
                        0 -> PantryScreen(
                            items = pantryItems,
                            isDarkMode = isDarkMode,
                            themePreference = currentUser.themePreference,
                            onItemUpdate = { updatedItem ->
                                try {
                                    currentUser.householdId?.let { hid ->
                                        val itemMap = hashMapOf(
                                            "name" to updatedItem.name,
                                            "category" to updatedItem.category,
                                            "quantity" to updatedItem.quantity,
                                            "unit" to updatedItem.unit,
                                            "lowStockThreshold" to updatedItem.lowStockThreshold,
                                            "price" to updatedItem.price
                                        )
                                        db.collection("households").document(hid)
                                            .collection("inventory").document(updatedItem.id)
                                            .set(itemMap)
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
                                        val itemMap = hashMapOf(
                                            "name" to newItem.name,
                                            "category" to newItem.category,
                                            "quantity" to newItem.quantity,
                                            "unit" to newItem.unit,
                                            "lowStockThreshold" to newItem.lowStockThreshold,
                                            "price" to newItem.price
                                        )
                                        docRef.set(itemMap)
                                    }
                                } catch (_: Exception) {}
                            }
                        )
                        1 -> CalendarScreen(
                            currentUser = currentUser,
                            isDarkMode = isDarkMode
                        )
                        2 -> BudgetScreen(
                            currentUser = currentUser,
                            isDarkMode = isDarkMode,
                            ledgerEntries = ledgerEntries,
                            bills = monthlyBills,
                            onUpdateLedger = { updated -> ledgerEntries = updated },
                            onUpdateBills = { updated -> monthlyBills = updated }
                        )
                        3 -> ShiftsScreen(
                            currentUser = currentUser,
                            isDarkMode = isDarkMode,
                            onProfileUpdated = onProfileUpdated
                        )
                        4 -> SettingsScreen(
                            currentUser = currentUser,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = onToggleDarkMode,
                            onProfileUpdated = onProfileUpdated,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            LiquidGlassTabBarCompose(
                tabs = dashboardTabItems,
                selectedTab = selectedTab,
                onTabSelected = { newIndex -> selectedTab = newIndex },
                isDarkMode = isDarkMode,
                themePreference = currentUser.themePreference
            )
        }
    }
}
