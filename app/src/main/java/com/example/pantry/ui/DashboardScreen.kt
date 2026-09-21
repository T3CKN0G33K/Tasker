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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        // LAYER 2: Floating iPadOS Style Liquid Glass Taskbar pinned to bottom with precise inner padding offset
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val tabs = listOf(
                Triple(0, "Pantry", Icons.AutoMirrored.Filled.List),
                Triple(1, "Add", Icons.Default.Add),
                Triple(2, "Budget", Icons.Default.AccountBalanceWallet),
                Triple(3, "Shifts", Icons.Default.Schedule),
                Triple(4, "Settings", Icons.Default.Settings)
            )

            var containerWidth by remember { mutableFloatStateOf(0f) }
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            var isDragging by remember { mutableStateOf(false) }

            val innerPaddingPx = with(LocalDensity.current) { 4.dp.toPx() }
            val availableWidth = (containerWidth - 2 * innerPaddingPx).coerceAtLeast(0f)
            val tabWidth = if (availableWidth > 0f) availableWidth / tabs.size else 0f

            val activeIndex = if (isDragging && tabWidth > 0f) {
                ((dragOffsetX - innerPaddingPx) / tabWidth).toInt().coerceIn(0, tabs.size - 1)
            } else {
                selectedTab
            }

            val maxOffset = (tabs.size - 1) * tabWidth

            val targetOffset = if (isDragging && tabWidth > 0f) {
                (dragOffsetX - innerPaddingPx - (tabWidth / 2f)).coerceIn(0f, maxOffset)
            } else if (tabWidth > 0f) {
                selectedTab * tabWidth
            } else {
                0f
            }

            val animatedOffset by animateFloatAsState(
                targetValue = targetOffset,
                animationSpec = if (isDragging) {
                    spring(stiffness = Spring.StiffnessHigh)
                } else {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                },
                label = "GlassLensSlide"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(68.dp)
                    .onSizeChanged { containerWidth = it.width.toFloat() }
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkMode) {
                                    listOf(Color(0xFF2C2C2E).copy(alpha = 0.9f), Color(0xFF1C1C1E).copy(alpha = 0.98f))
                                } else {
                                    listOf(Color(0xFFE5E5EA).copy(alpha = 0.9f), Color(0xFFF2F2F7).copy(alpha = 0.98f))
                                }
                            ),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = if (isDarkMode) {
                                listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))
                            } else {
                                listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.05f))
                            }
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragOffsetX = offset.x.coerceIn(innerPaddingPx, containerWidth - innerPaddingPx)
                            },
                            onDragEnd = {
                                isDragging = false
                                if (tabWidth > 0f) {
                                    val finalIndex = ((dragOffsetX - innerPaddingPx) / tabWidth).toInt().coerceIn(0, tabs.size - 1)
                                    selectedTab = finalIndex
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(innerPaddingPx, containerWidth - innerPaddingPx)
                            }
                        )
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Base Layer: Static icons
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (index, label, icon) ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedTab = index
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isDarkMode) Color(0xFF8E8E93) else Color(0xFF6C6C70),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = if (isDarkMode) Color(0xFF8E8E93) else Color(0xFF6C6C70),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Floating Magnifying Glass Lens Layer
                if (containerWidth > 0f && tabWidth > 0f) {
                    Box(
                        modifier = Modifier
                            .offset(x = with(LocalDensity.current) { animatedOffset.toDp() })
                            .width(with(LocalDensity.current) { tabWidth.toDp() })
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isDarkMode) {
                                        listOf(Color(0xFF636366).copy(alpha = 0.95f), Color(0xFF48484A).copy(alpha = 0.95f))
                                    } else {
                                        listOf(Color(0xFFFFFFFF).copy(alpha = 0.98f), Color(0xFFF2F2F7).copy(alpha = 0.98f))
                                    }
                                )
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val activeTabInfo = tabs[activeIndex]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = activeTabInfo.third,
                                contentDescription = activeTabInfo.second,
                                tint = Color(0xFF0A84FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeTabInfo.second,
                                color = if (isDarkMode) Color.White else Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
