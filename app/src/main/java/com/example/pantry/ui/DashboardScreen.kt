package com.example.pantry.ui

import android.app.Activity
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassTabBar
import com.example.tasker.R
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var pantryItems by remember { mutableStateOf<List<PantryItem>>(emptyList()) }
    var budgetTransactions by remember { mutableStateOf<List<BudgetTransaction>>(emptyList()) }
    var monthlyBills by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var monthlyBudgetLimit by remember { mutableDoubleStateOf(1804.66) }

    val db = Firebase.firestore

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
                            PantryItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                category = doc.getString("category") ?: "",
                                quantity = quantity,
                                unit = doc.getString("unit") ?: "Units",
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

    val screenBg = if (isDarkMode) Color.Black else Color(0xFFF2F2F7)

    // 1. Root Container (Scaffoldless Fullscreen Box)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // LAYER 1: Fullscreen Screen Content (Extends all the way behind floating liquid glass taskbar)
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(250)) togetherWith 
                fadeOut(animationSpec = tween(250))
            },
            label = "ScreenTransition"
        ) { targetTab ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                when (targetTab) {
                    0 -> PantryScreen(
                        items = pantryItems,
                        isDarkMode = isDarkMode,
                        onItemUpdate = { updatedItem ->
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
                        },
                        onItemDelete = { itemId ->
                            currentUser.householdId?.let { hid ->
                                db.collection("households").document(hid)
                                    .collection("inventory").document(itemId)
                                    .delete()
                            }
                        }
                    )
                    1 -> AddScreen(
                        isDarkMode = isDarkMode,
                        onAddItem = { newItem ->
                            if (currentUser.householdId == null) {
                                Toast.makeText(context, "Error: No Household ID found on profile.", Toast.LENGTH_LONG).show()
                                return@AddScreen
                            }

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
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Saved to Pantry!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                            selectedTab = 0 // Auto-route back to pantry
                        }
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
                        onSignOut = onSignOut
                    )
                }
            }
        }

        // LAYER 2: Rebuilt taskbar using LiquidGlassTabBar (iOS 26 Liquid Glass Droplet Indicator with Root Backdrop Sampling)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AndroidView(
                factory = { ctx ->
                    val activity = ctx as? Activity
                    val rootContent = activity?.window?.decorView?.findViewById<View>(android.R.id.content)
                        ?: activity?.window?.decorView

                    LiquidGlassTabBar(ctx).apply {
                        if (rootContent != null) {
                            backdropSource = rootContent
                        }
                        enableBackdropBlur = true
                        enableChromaticAberration = true
                        enableEdgeHighlight = true
                        edgeHighlightBorderWidth = 1.0f
                        edgeHighlightOpacity = 100f
                        bevelWidth = 24f
                        refractionHeight = 36f
                        dispersionStrength = 0.08f
                        enableDynamicBackground = true
                        material = GlassMaterial.CLEAR
                        glassTint = android.graphics.Color.TRANSPARENT
                        selectedTintColor = 0xFF007AFF.toInt()

                        setTabs(
                            listOf(
                                LiquidGlassTabBar.TabItem("Pantry", ctx.getDrawable(R.drawable.ic_tab_pantry)),
                                LiquidGlassTabBar.TabItem("Add", ctx.getDrawable(R.drawable.ic_tab_add)),
                                LiquidGlassTabBar.TabItem("Budget", ctx.getDrawable(R.drawable.ic_tab_budget)),
                                LiquidGlassTabBar.TabItem("Shifts", ctx.getDrawable(R.drawable.ic_tab_shifts)),
                                LiquidGlassTabBar.TabItem("Settings", ctx.getDrawable(R.drawable.ic_tab_settings))
                            )
                        )

                        onTabSelected = { index ->
                            selectedTab = index
                        }
                    }
                },
                update = { tabBar ->
                    if (tabBar.selectedIndex != selectedTab) {
                        tabBar.selectedIndex = selectedTab
                    }
                    tabBar.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(68.dp)
            )
        }
    }
}
