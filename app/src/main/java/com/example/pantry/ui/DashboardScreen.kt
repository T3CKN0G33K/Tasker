package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.CalendarMonth
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassTabBarCompose
import com.example.liquidglass.LiquidGlassTabItem
import com.example.liquidglass.LiquidGlassView
import com.example.pantry.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

@Composable
fun LiquidGlassTabBarComponent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDarkMode: Boolean,
    themePreference: String = "DEFAULT",
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        LiquidGlassTabItem(0, "Pantry", Icons.AutoMirrored.Filled.List),
        LiquidGlassTabItem(1, "Calendar", Icons.Default.CalendarMonth),
        LiquidGlassTabItem(2, "Budget", Icons.Default.AccountBalanceWallet),
        LiquidGlassTabItem(3, "Shifts", Icons.Default.Schedule),
        LiquidGlassTabItem(4, "Settings", Icons.Default.Settings)
    )

    LiquidGlassTabBarCompose(
        tabs = tabs,
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        isDarkMode = isDarkMode,
        themePreference = themePreference,
        modifier = modifier
    )
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onProfileUpdated: (UserProfile) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var pantryItems by remember { mutableStateOf<List<PantryItem>>(emptyList()) }
    var budgetTransactions by remember { mutableStateOf<List<BudgetTransaction>>(emptyList()) }
    var monthlyBills by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var monthlyBudgetLimit by remember { mutableDoubleStateOf(1804.66) }

    val db = Firebase.firestore

    val previousStockState = remember { mutableMapOf<String, Double>() }

    // Real-time Firestore Sync at Dashboard level (Persists across tab switches)
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

            // 2. Budget Limit Sync
            db.collection("households").document(householdId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        monthlyBudgetLimit = doc.getDouble("budgetLimit") ?: 1804.66
                    }
                }

            // 3. Monthly Bills Sync
            db.collection("households").document(householdId).collection("bills")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            BillItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "Bill",
                                amount = doc.getDouble("amount") ?: 0.0,
                                dueDate = doc.getString("dueDate") ?: "Due 1st",
                                isPaid = doc.getBoolean("isPaid") ?: false
                            )
                        }
                        monthlyBills = items
                    }
                }

            // 4. Budget Transactions Sync
            db.collection("households").document(householdId).collection("transactions")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            BudgetTransaction(
                                id = doc.id,
                                title = doc.getString("title") ?: "Expense",
                                amount = doc.getDouble("amount") ?: 0.0,
                                date = doc.getString("date") ?: "Today",
                                account = doc.getString("account") ?: "Manual Entry"
                            )
                        }
                        budgetTransactions = items
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

    // 1. Root Container (Single Fullscreen Box for Floating Overlay)
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        // LAYER 1: Fullscreen Screen Content (Extends all the way behind floating liquid glass taskbar)
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
                            transactions = budgetTransactions,
                            bills = monthlyBills,
                            monthlyLimit = monthlyBudgetLimit,
                            onUpdateTransactions = { updatedTx -> budgetTransactions = updatedTx },
                            onUpdateBills = { updatedBills -> monthlyBills = updatedBills },
                            onUpdateMonthlyLimit = { updatedLimit -> monthlyBudgetLimit = updatedLimit }
                        )
                        3 -> ShiftsScreen(currentUser = currentUser, isDarkMode = isDarkMode)
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

        // LAYER 2: Floating Rebuilt LiquidGlassTabBar Component (Droplet Glass Lens Slider)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            LiquidGlassTabBarComponent(
                selectedTab = selectedTab,
                onTabSelected = { newIndex -> selectedTab = newIndex },
                isDarkMode = isDarkMode,
                themePreference = currentUser.themePreference
            )
        }
    }
}
