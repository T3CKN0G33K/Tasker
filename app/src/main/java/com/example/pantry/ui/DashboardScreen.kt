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
    var showAboutVersionScreen by remember { mutableStateOf(false) }
    var showProfileEditScreen by remember { mutableStateOf(false) }

    val db = Firebase.firestore

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




        }
    }

    // Outer root container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
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
                                isDarkMode = isDarkMode
                            )
                            2 -> BudgetScreen(
                                currentUser = currentUser,
                                isDarkMode = isDarkMode
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
                initialDisplayName = currentUser.name.ifBlank { "Bartont4432" },
                initialRole = if (currentUser.role == Role.OWNER) "Household Owner" else if (currentUser.role == Role.ADMIN || currentUser.canManageHousehold) "Household Manager (Admin)" else "Member",
                initialEmail = currentUser.email.ifBlank { "bartont4432@gmail.com" },
                initialHouseholdGroup = "Main Household",
                activeSyncDevices = "Galaxy Z Fold, Desktop Server, Tablet",
                onBack = { showProfileEditScreen = false },
                onSaveChanges = { newName ->
                    val updated = currentUser.copy(name = newName)
                    onProfileUpdated(updated)
                    showProfileEditScreen = false
                }
            )
        }

        // Floating Solid Vector Bottom Bar
        val appColors = com.example.pantry.ui.theme.LocalAppColors.current
        Box(
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
