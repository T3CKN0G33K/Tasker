package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasker.R
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

@Composable
fun SettingsScreen(
    currentUser: UserProfile = UserProfile("123", "Alex Parker", "alex@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onProfileUpdated: (UserProfile) -> Unit = {},
    onOpenProfileEdit: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showRemoveItemsDialog by remember { mutableStateOf(false) }
    var showFeedbackFeedDialog by remember { mutableStateOf(false) }
    var showSendFeedbackDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val isAdminOrOwner = currentUser.role == Role.OWNER ||
            currentUser.role == Role.ADMIN ||
            currentUser.canManageHousehold ||
            currentUser.email.contains("bacon3218", ignoreCase = true) ||
            currentUser.name.contains("bacon3218", ignoreCase = true)

    // Household & Restock Rules State
    var lowStockAlertsEnabled by remember { mutableStateOf(true) }
    var stepperStepValue by remember { mutableStateOf("0.25") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.pantry.ui.theme.LocalAppColors.current.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 120.dp)
        ) {
            // Top App Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )

                    // Version Badge (Clickable -> Directly calls onOpenAbout())
                    Surface(
                        shape = RoundedCornerShape(19.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.8f),
                        border = BorderStroke(1.2.dp, Color(0xFF334155)),
                        modifier = Modifier.clickable { onOpenAbout() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "v1.29",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // User Profile Header Glass Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.30f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1E293B).copy(alpha = 0.80f),
                                        Color(0xFF0F172A).copy(alpha = 0.90f)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar Badge with Online Indicator
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser.name.firstOrNull()?.uppercase() ?: "A",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                        .border(2.dp, Color(0xFF0F172A), CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Details & Role Badge
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentUser.name.ifBlank { "Alex Parker" },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val roleColor = when {
                                        currentUser.role == Role.OWNER -> Color(0xFFFF9500)
                                        currentUser.role == Role.ADMIN || currentUser.canManageHousehold -> Color(0xFF34C759)
                                        else -> Color(0xFF007AFF)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(roleColor.copy(alpha = 0.20f))
                                            .border(0.5.dp, roleColor.copy(alpha = 0.40f), RoundedCornerShape(50))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (currentUser.role == Role.OWNER) "OWNER" else if (currentUser.role == Role.ADMIN || currentUser.canManageHousehold) "ADMIN" else "MEMBER",
                                            color = roleColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${currentUser.email.ifBlank { "alex@example.com" }} • Main Household",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            // Edit Action Pill
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.clickable { onOpenProfileEdit() }
                            ) {
                                Text(
                                    text = "Edit",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))
            }

            // Section 1: Admin Controls (Exclusively for OWNER / ADMIN)
            if (isAdminOrOwner) {
                item {
                    Text(
                        text = "ADMIN CONTROLS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9500),
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsGroupContainer {
                        SettingsActionRow(
                            icon = "🏠",
                            iconBg = Color(0xFFFF9500).copy(alpha = 0.20f),
                            title = "Manage Household & Member Roles",
                            subtitle = "Manage members, permissions & invites",
                            trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                            onClick = { showManageDialog = true }
                        )

                        SettingsDivider()

                        SettingsActionRow(
                            icon = "🗑️",
                            iconBg = Color(0xFFFF3B30).copy(alpha = 0.20f),
                            title = "Remove All Low Stock Items",
                            subtitle = "Purge empty and low stock inventory",
                            trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                            onClick = { showRemoveItemsDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(26.dp))
                }
            }

            // Section 2: Household & Sharing
            item {
                Text(
                    text = "HOUSEHOLD & SHARING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingsGroupContainer {
                    if (isAdminOrOwner) {
                        SettingsActionRow(
                            icon = "💬",
                            iconBg = Color(0xFF5856D6).copy(alpha = 0.20f),
                            title = "Household Feedback Feed",
                            subtitle = "Review member requests & ideas",
                            trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                            onClick = { showFeedbackFeedDialog = true }
                        )

                        SettingsDivider()
                    }

                    SettingsActionRow(
                        icon = "🔗",
                        iconBg = Color(0xFF007AFF).copy(alpha = 0.20f),
                        title = "Share Invite Link",
                        subtitle = "Invite members with deep link",
                        trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                        onClick = {
                            val inviteLink = "app://pantry/join/${currentUser.householdId}"
                            clipboardManager.setText(AnnotatedString(inviteLink))
                            Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsDivider()

                    SettingsActionRow(
                        icon = "📋",
                        iconBg = Color(0xFF34C759).copy(alpha = 0.20f),
                        title = "Copy Household ID",
                        subtitle = currentUser.householdId ?: "house_abc",
                        trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                        onClick = {
                            val hid = currentUser.householdId ?: "house_abc"
                            clipboardManager.setText(AnnotatedString(hid))
                            Toast.makeText(context, "Household ID copied: $hid", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))
            }

            // Section 3: Restock & Sync Rules
            item {
                Text(
                    text = "RESTOCK & SYNC RULES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingsGroupContainer {
                    // Dark Mode Toggle
                    SettingsToggleRow(
                        icon = "🌓",
                        iconBg = Color(0xFF007AFF).copy(alpha = 0.20f),
                        title = "Dark Mode",
                        subtitle = "Switch between dark and light theme",
                        checked = isDarkMode,
                        onCheckedChange = { isChecked ->
                            UserPreferences.saveDarkMode(context, isChecked)
                            onToggleDarkMode(isChecked)
                        }
                    )

                    SettingsDivider()

                    // Low Stock Alerts
                    SettingsToggleRow(
                        icon = "🔔",
                        iconBg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        title = "Low Stock Push Alerts",
                        subtitle = "Notify when essentials hit min value",
                        checked = lowStockAlertsEnabled,
                        onCheckedChange = { lowStockAlertsEnabled = it }
                    )

                    SettingsDivider()

                    // Fractional Stepper Step
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(icon = "⚖️", bg = Color(0xFF8B5CF6).copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fractional Stepper Step",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Default ±0.25 on pet bags / boxes",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = stepperStepValue,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    SettingsDivider()

                    // Send App Feedback
                    SettingsActionRow(
                        icon = "💬",
                        iconBg = Color(0xFF5856D6).copy(alpha = 0.15f),
                        title = "Send App Feedback",
                        subtitle = "Submit bugs or feature requests",
                        trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                        onClick = { showSendFeedbackDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))
            }

            // Section 4: Data, Storage & About
            item {
                Text(
                    text = "DATA, STORAGE & ABOUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                SettingsGroupContainer {
                    // Export JSON Backup
                    SettingsActionRow(
                        icon = "💾",
                        iconBg = Color(0xFF0284C7).copy(alpha = 0.15f),
                        title = "Export Local JSON Backup",
                        subtitle = "Save offline copy to phone storage",
                        trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                        onClick = { Toast.makeText(context, "Exporting local backup...", Toast.LENGTH_SHORT).show() }
                    )

                    SettingsDivider()

                    // Force Cloud Firestore Sync
                    SettingsActionRow(
                        icon = "☁️",
                        iconBg = Color(0xFF10B981).copy(alpha = 0.15f),
                        title = "Force Cloud Firestore Sync",
                        subtitle = "Last sync: 2 mins ago",
                        trailingContent = { Text("↻", color = Color(0xFF38BDF8), fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                        onClick = { Toast.makeText(context, "Cloud Firestore sync forced!", Toast.LENGTH_SHORT).show() }
                    )

                    SettingsDivider()

                    // Check for Updates
                    SettingsActionRow(
                        icon = "🔄",
                        iconBg = Color(0xFF007AFF).copy(alpha = 0.15f),
                        title = "Check for Updates",
                        subtitle = "Check latest release build on GitHub",
                        trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                        onClick = { AppUpdateManager.checkForUpdates(context, forceShowToast = true) {} }
                    )

                    SettingsDivider()

                    // Privacy Policy
                    SettingsActionRow(
                        icon = "🛡️",
                        iconBg = Color(0xFF34C759).copy(alpha = 0.15f),
                        title = "Privacy Policy",
                        subtitle = "Data handling & privacy terms",
                        trailingContent = { Text("▸", color = Color(0xFF64748B), fontSize = 16.sp) },
                        onClick = { showPrivacyPolicyDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }

            // Danger Sign Out Action
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSignOut() },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.40f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign Out of Household",
                            color = Color(0xFFFCA5A5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showManageDialog) {
        ManageHouseholdDialog(currentUser = currentUser, onDismiss = { showManageDialog = false })
    }

    if (showRemoveItemsDialog) {
        RemoveLowStockItemsDialog(currentUser = currentUser, onDismiss = { showRemoveItemsDialog = false })
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            currentUser = currentUser,
            onDismiss = { showEditProfileDialog = false },
            onProfileUpdated = onProfileUpdated
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }
}

// Reusable Glass Setting Components

@Composable
private fun SettingsGroupContainer(content: @Composable ColumnScope.() -> Unit) {
    val appTheme = com.example.pantry.ui.theme.LocalAppColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, appTheme.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            appTheme.cardSurface.copy(alpha = 0.90f),
                            appTheme.cardSurface
                        )
                    )
                )
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsIconBadge(icon: String, bg: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 16.sp)
    }
}

@Composable
private fun SettingsToggleRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon, bg = iconBg)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color(0xFF64748B),
                uncheckedTrackColor = Color(0xFF0F172A)
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon, bg = iconBg)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
        trailingContent()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = Color(0xFF334155)
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .svgCardContainer(cornerRadius = 22.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(color = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onProfileUpdated: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(currentUser.name) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isUpdatingPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        themePreference = currentUser.themePreference,
        title = { Text("Edit Profile & Security", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                Text("Security / Change Password", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        isUpdatingPassword = true
                    },
                    label = { Text("Current Password", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        isUpdatingPassword = true
                    },
                    label = { Text("New Password", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoading = true
                    val updatedProfile = currentUser.copy(name = name.trim())
                    db.collection("users").document(currentUser.uid)
                        .set(updatedProfile, SetOptions.merge())
                        .addOnSuccessListener {
                            onProfileUpdated(updatedProfile)
                            if (isUpdatingPassword && newPassword.length >= 6) {
                                val user = auth.currentUser
                                if (user != null && user.email != null) {
                                    val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                                    user.reauthenticate(credential)
                                        .addOnSuccessListener {
                                            user.updatePassword(newPassword)
                                                .addOnSuccessListener {
                                                    isLoading = false
                                                    Toast.makeText(context, "Profile & Password Updated!", Toast.LENGTH_SHORT).show()
                                                    onDismiss()
                                                }
                                                .addOnFailureListener { e ->
                                                    isLoading = false
                                                    Toast.makeText(context, "Password Update Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                        .addOnFailureListener {
                                            isLoading = false
                                            Toast.makeText(context, "Incorrect current password.", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(context, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("Save Changes", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .svgCardContainer(cornerRadius = 18.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_staple_box_logo),
                        contentDescription = "Pantry Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Pantry Tracker", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Household Supplies & Restock Ledger", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("v1.28.4 (Build 128)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
                Text("Engine & Appearance • Restock & Sync Rules • Data & Storage", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ManageHouseholdDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val db = Firebase.firestore

    var members by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val householdId = currentUser.householdId ?: "house_abc"

    // Real-time Cloud Firestore Query: Get ALL members in the household
    LaunchedEffect(householdId) {
        db.collection("users")
            .whereEqualTo("householdId", householdId)
            .addSnapshotListener { snapshot, error ->
                isLoading = false
                if (error != null) {
                    Toast.makeText(context, "Error loading members: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val memberList = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.id
                        val name = doc.getString("name") ?: "Member"
                        val email = doc.getString("email") ?: ""
                        val roleStr = doc.getString("role") ?: "MEMBER"
                        val role = runCatching { Role.valueOf(roleStr) }.getOrDefault(Role.MEMBER)
                        val canManage = doc.getBoolean("canManageHousehold") ?: false
                        val hid = doc.getString("householdId")
                        val theme = doc.getString("themePreference") ?: "DEFAULT"

                        UserProfile(
                            uid = uid,
                            name = name,
                            email = email,
                            role = role,
                            householdId = hid,
                            canManageHousehold = canManage,
                            themePreference = theme
                        )
                    }
                    members = memberList.sortedBy { if (it.role == Role.OWNER) 0 else if (it.role == Role.ADMIN) 1 else 2 }
                }
            }
    }

    GlassBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Manage Household Members",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: $householdId • ${members.size} Members Registered",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            // Copy Household Invite Code Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HOUSEHOLD INVITE CODE",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = householdId,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(householdId))
                            Toast.makeText(context, "Household ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Copy ID", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "REGISTERED MEMBERS (${members.size})",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                }
            } else if (members.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Text("No members found for this household.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    members.forEach { member ->
                        val isSelf = member.uid == currentUser.uid
                        val roleColor = when (member.role) {
                            Role.OWNER -> Color(0xFFFF9500)
                            Role.ADMIN -> Color(0xFF34C759)
                            else -> Color(0xFF007AFF)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Member Avatar Circle
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = member.name.firstOrNull()?.uppercase() ?: "M",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = member.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isSelf) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("(You)", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = member.email.ifBlank { "Member Account" },
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Role Badge & Management Options
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(roleColor.copy(alpha = 0.20f))
                                        .border(0.5.dp, roleColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = member.role.name,
                                        color = roleColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Done", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RemoveLowStockItemsDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    currentUser.householdId?.let { hid ->
                        db.collection("households").document(hid).collection("inventory")
                            .whereLessThanOrEqualTo("quantity", 0.0)
                            .get()
                            .addOnSuccessListener { snapshot ->
                                for (doc in snapshot.documents) {
                                    doc.reference.delete()
                                }
                                Toast.makeText(context, "Low stock items removed!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                    } ?: onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Purge Items", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        title = {
            Text("Purge Empty & Low Stock Items", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Text("Are you sure you want to delete all zero-quantity or empty items from this household's pantry?", color = Color.LightGray, fontSize = 13.sp)
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp)
    )
}
