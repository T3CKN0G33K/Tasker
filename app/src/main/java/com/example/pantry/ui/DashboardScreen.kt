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
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

@Composable
fun LiquidGlassTabBarComponent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
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
        label = "DropletLensSlide"
    )

    // Container: Lightweight Translucent Pill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(64.dp)
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .clip(RoundedCornerShape(32.dp))
            .background(
                color = if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isDarkMode) {
                        listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.10f))
                    } else {
                        listOf(Color.White, Color.White.copy(alpha = 0.50f))
                    }
                ),
                shape = RoundedCornerShape(32.dp)
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
                            onTabSelected(finalIndex)
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
        // LAYER 1: Sliding "Droplet" Glass Lens (ONLY part that uses Native JNI Glass/Blur!)
        if (containerWidth > 0f && tabWidth > 0f) {
            val cornerPx = with(LocalDensity.current) { 26.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset(x = with(LocalDensity.current) { animatedOffset.toDp() })
                    .width(with(LocalDensity.current) { tabWidth.toDp() })
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(26.dp))
            ) {
                AndroidView(
                    factory = { context ->
                        LiquidGlassView(context).apply {
                            cornerRadius = cornerPx
                            enableBackdropBlur = true
                            enableChromaticAberration = true
                            enableEdgeHighlight = true
                            edgeHighlightBorderWidth = 1.0f
                            edgeHighlightOpacity = 100f
                            bevelWidth = 16f
                            refractionHeight = 24f
                            dispersionStrength = 0.08f
                            material = GlassMaterial.CLEAR
                            glassTint = android.graphics.Color.argb(45, 255, 255, 255)
                            useShaderPipeline = false
                        }
                    },
                    update = { droplet ->
                        droplet.cornerRadius = cornerPx
                        droplet.invalidate()
                    },
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        // LAYER 2: Higher Z-index Tab Icons & Text (Sit crisply ON TOP of sliding droplet lens)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (index, label, icon) ->
                val isSelected = index == activeIndex
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(index)
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
                            tint = if (isSelected) {
                                Color(0xFF007AFF)
                            } else if (isDarkMode) {
                                Color.White.copy(alpha = 0.65f)
                            } else {
                                Color.Black.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = if (isSelected) {
                                if (isDarkMode) Color.White else Color.Black
                            } else if (isDarkMode) {
                                Color.White.copy(alpha = 0.65f)
                            } else {
                                Color.Black.copy(alpha = 0.55f)
                            },
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
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

    val bgModifier = when (currentUser.themePreference.uppercase()) {
        "PURPLE" -> Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF9E6BC2), Color(0xFF734199))
            )
        )
        "CYAN" -> Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF4DD0E1), Color(0xFF0097A9))
            )
        )
        else -> Modifier.background(screenBg)
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
                        onProfileUpdated = onProfileUpdated,
                        onSignOut = onSignOut
                    )
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
                isDarkMode = isDarkMode
            )
        }
    }
}
