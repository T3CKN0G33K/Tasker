package com.example.pantry.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
import com.example.tasker.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

@Composable
fun SettingsScreen(
    currentUser: UserProfile,
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onProfileUpdated: (UserProfile) -> Unit = {},
    onOpenProfileEdit: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showManageDialog by remember { mutableStateOf(false) }
    var showRemoveItemsDialog by remember { mutableStateOf(false) }
    var showFeedbackFeedDialog by remember { mutableStateOf(false) }
    var showSendFeedbackDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val db = Firebase.firestore
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val householdId = currentUser.householdId
        if (uri != null && !householdId.isNullOrBlank()) {
            isExporting = true
            scope.launch {
                try {
                    val householdRef = db.collection("households").document(householdId)
                    val backup = JSONObject()
                    backup.put("exportedAt", System.currentTimeMillis())
                    backup.put("householdId", householdId)
                    backup.put("household", documentToJson(householdRef.get().await()))
                    val collectionNames = listOf("inventory", "bills", "events", "calendar_events", "feedback", "tickets")
                    for (collectionName in collectionNames) {
                        val docs = householdRef.collection(collectionName).get().await().documents
                        backup.put(collectionName, JSONArray().apply { docs.forEach { put(documentToJson(it)) } })
                    }
                    val budgets = householdRef.collection("budgets").get().await().documents
                    backup.put("budgets", JSONArray().apply {
                        budgets.forEach { budget ->
                            val budgetJson = documentToJson(budget)
                            val budgetBills = budget.reference.collection("bills").get().await().documents
                            budgetJson.put("bills", JSONArray().apply { budgetBills.forEach { put(documentToJson(it)) } })
                            put(budgetJson)
                        }
                    })
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.toString(2)) }
                        ?: error("Could not open the selected file")
                    Toast.makeText(context, "Household backup saved.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(context, "Backup failed: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val isAdminOrOwner = currentUser.role == Role.OWNER ||
            currentUser.role == Role.ADMIN ||
            currentUser.canManageHousehold

    // Household & Restock Rules State
    var lowStockAlertsEnabled by remember(currentUser.uid) {
        mutableStateOf(UserPreferences.isLowStockAlertsEnabled(context))
    }
    var stepperStepValue by remember(currentUser.uid) {
        mutableFloatStateOf(UserPreferences.getDefaultInventoryStep(context))
    }
    var showStepperOptions by remember { mutableStateOf(false) }

    val appColors = com.example.pantry.ui.theme.LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 34.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Settings",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = appColors.textPrimary,
                            letterSpacing = (-0.6).sp
                        )
                        Text(
                            text = "Your account and household preferences",
                            fontSize = 13.sp,
                            color = appColors.textSecondary
                        )
                    }
                    Surface(
                        onClick = onOpenAbout,
                        shape = RoundedCornerShape(14.dp),
                        color = appColors.cardSurface,
                        border = BorderStroke(1.dp, appColors.cardBorder)
                    ) {
                        Text(
                            text = "About  ·  ${BuildConfig.VERSION_NAME}",
                            color = appColors.primaryAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionHeading("ACCOUNT")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = appColors.cardSurface,
                        border = BorderStroke(1.dp, appColors.cardBorder)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(appColors.primaryAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser.name.trim().firstOrNull()?.uppercase() ?: "?",
                                        color = appColors.primaryAccent,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser.name.ifBlank { "Name not set" },
                                        color = appColors.textPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = currentUser.email.ifBlank { "Email not set" },
                                        color = appColors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = appColors.primaryAccent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = when {
                                            currentUser.role == Role.OWNER -> "Owner"
                                            currentUser.role == Role.ADMIN || currentUser.canManageHousehold -> "Admin"
                                            else -> "Member"
                                        },
                                        color = appColors.primaryAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onOpenProfileEdit,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, appColors.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary)
                            ) {
                                Text("Edit profile")
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionHeading("HOUSEHOLD")
                    SettingsGroupContainer {
                        if (isAdminOrOwner) {
                            SettingsActionRow(
                                icon = "👥",
                                iconBg = appColors.primaryAccent.copy(alpha = 0.14f),
                                title = "Members",
                                subtitle = "View household members and roles",
                                trailingContent = { SettingsChevron(appColors.textSecondary) },
                                onClick = {
                                    if (currentUser.householdId.isNullOrBlank()) {
                                        Toast.makeText(context, "Join a household first.", Toast.LENGTH_SHORT).show()
                                    } else showManageDialog = true
                                }
                            )
                            SettingsDivider()
                        }
                        SettingsActionRow(
                            icon = "🔗",
                            iconBg = Color(0xFF0284C7).copy(alpha = 0.14f),
                            title = "Invite someone",
                            subtitle = "Copy a link to join your household",
                            trailingContent = { SettingsChevron(appColors.textSecondary) },
                            onClick = {
                                val householdId = currentUser.householdId
                                if (householdId.isNullOrBlank()) {
                                    Toast.makeText(context, "Join a household before inviting members.", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboardManager.setText(AnnotatedString("app://pantry/join/$householdId"))
                                    Toast.makeText(context, "Invite link copied.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsActionRow(
                            icon = "#️⃣",
                            iconBg = Color(0xFF10B981).copy(alpha = 0.14f),
                            title = "Household ID",
                            subtitle = currentUser.householdId ?: "No household linked",
                            trailingContent = { SettingsChevron(appColors.textSecondary) },
                            onClick = {
                                val householdId = currentUser.householdId
                                if (householdId.isNullOrBlank()) {
                                    Toast.makeText(context, "No household ID is available.", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboardManager.setText(AnnotatedString(householdId))
                                    Toast.makeText(context, "Household ID copied.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        if (isAdminOrOwner) {
                            SettingsDivider()
                            SettingsActionRow(
                                icon = "💬",
                                iconBg = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                                title = "Household feedback",
                                subtitle = "Review requests and ideas from members",
                                trailingContent = { SettingsChevron(appColors.textSecondary) },
                                onClick = { showFeedbackFeedDialog = true }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionHeading("PREFERENCES")
                    SettingsGroupContainer {
                        SettingsToggleRow(
                            icon = "🌓",
                            iconBg = Color(0xFF6366F1).copy(alpha = 0.14f),
                            title = "Dark mode",
                            subtitle = "Use the dark appearance",
                            checked = isDarkMode,
                            onCheckedChange = { enabled ->
                                UserPreferences.saveDarkMode(context, enabled)
                                onToggleDarkMode(enabled)
                            }
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            icon = "🔔",
                            iconBg = Color(0xFFF59E0B).copy(alpha = 0.14f),
                            title = "Low-stock alerts",
                            subtitle = "Notify when an item reaches its threshold",
                            checked = lowStockAlertsEnabled,
                            onCheckedChange = { enabled ->
                                lowStockAlertsEnabled = enabled
                                UserPreferences.setLowStockAlertsEnabled(context, enabled)
                            }
                        )
                        SettingsDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsIconBadge("⚖️", Color(0xFF8B5CF6).copy(alpha = 0.14f))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Default quantity increment", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = appColors.textPrimary)
                                Text("Used for new items; set each item in Edit", fontSize = 12.sp, color = appColors.textSecondary)
                            }
                            Box {
                                OutlinedButton(
                                    onClick = { showStepperOptions = true },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, appColors.cardBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.textPrimary)
                                ) {
                                    Text("${stepperStepValue}")
                                }
                                DropdownMenu(expanded = showStepperOptions, onDismissRequest = { showStepperOptions = false }) {
                                    listOf(0.25f, 0.5f, 1.0f).forEach { step ->
                                        DropdownMenuItem(
                                            text = { Text(if (step == 1f) "1" else step.toString()) },
                                            onClick = {
                                                stepperStepValue = step
                                                UserPreferences.setDefaultInventoryStep(context, step)
                                                showStepperOptions = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionHeading("DATA & SUPPORT")
                    SettingsGroupContainer {
                        SettingsActionRow(
                            icon = "💾",
                            iconBg = Color(0xFF0284C7).copy(alpha = 0.14f),
                            title = "Export household data",
                            subtitle = if (isExporting) "Preparing your backup…" else "Save a JSON backup to a file",
                            trailingContent = { SettingsChevron(appColors.textSecondary) },
                            onClick = {
                                if (currentUser.householdId.isNullOrBlank()) {
                                    Toast.makeText(context, "Join a household before exporting its data.", Toast.LENGTH_SHORT).show()
                                } else if (!isExporting) {
                                    exportLauncher.launch("pantry-household-backup.json")
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsActionRow(
                            icon = "✉️",
                            iconBg = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                            title = "Send feedback",
                            subtitle = "Report a problem or suggest an improvement",
                            trailingContent = { SettingsChevron(appColors.textSecondary) },
                            onClick = { showSendFeedbackDialog = true }
                        )
                        SettingsDivider()
                        SettingsActionRow(
                            icon = "🛡️",
                            iconBg = Color(0xFF10B981).copy(alpha = 0.14f),
                            title = "Privacy & data",
                            subtitle = "See what household data is stored",
                            trailingContent = { SettingsChevron(appColors.textSecondary) },
                            onClick = { showPrivacyPolicyDialog = true }
                        )
                        SettingsDivider()
                        SettingsActionRow(
                            icon = "⬆️",
                            iconBg = Color(0xFF0EA5E9).copy(alpha = 0.14f),
                            title = "Check for updates",
                            subtitle = "Look for a newer app version",
                            trailingContent = { SettingsChevron(appColors.textSecondary) },
                            onClick = { AppUpdateManager.checkForUpdates(context, forceShowToast = true) {} }
                        )
                    }
                }
            }

            if (isAdminOrOwner) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSectionHeading("ADMIN TOOLS")
                        SettingsGroupContainer {
                            SettingsActionRow(
                                icon = "🗑️",
                                iconBg = Color(0xFFEF4444).copy(alpha = 0.14f),
                                title = "Remove low-stock items",
                                subtitle = "Permanently delete empty and low-stock inventory",
                                trailingContent = { SettingsChevron(Color(0xFFEF4444)) },
                                onClick = { showRemoveItemsDialog = true }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5))
                ) {
                    Text("Sign out")
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
    if (showSendFeedbackDialog) {
        SendFeedbackDialog(currentUser, onDismiss = { showSendFeedbackDialog = false })
    }
    if (showFeedbackFeedDialog) {
        HouseholdFeedbackDialog(currentUser, onDismiss = { showFeedbackFeedDialog = false })
    }
    if (showPrivacyPolicyDialog) {
        PrivacySummaryDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

}

// Reusable Glass Setting Components

@Composable
private fun SettingsSectionHeading(title: String) {
    val colors = com.example.pantry.ui.theme.LocalAppColors.current
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textSecondary,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun SettingsChevron(color: Color) {
    Text(text = "›", color = color, fontSize = 23.sp, lineHeight = 23.sp)
}

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
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 17.sp)
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
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
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
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = appColors.textSecondary,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = appColors.primaryAccent,
                uncheckedThumbColor = appColors.textSecondary,
                uncheckedTrackColor = appColors.background
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
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBadge(icon = icon, bg = iconBg)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = appColors.textSecondary,
                lineHeight = 16.sp
            )
        }
        trailingContent()
    }
}

@Composable
private fun SettingsDivider() {
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = appColors.cardBorder.copy(alpha = 0.75f)
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
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth
    val scope = rememberCoroutineScope()

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

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password", color = Color.Gray) },
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
                    val passwordRequested = currentPassword.isNotBlank() || newPassword.isNotBlank() || confirmPassword.isNotBlank()
                    if (name.isBlank()) {
                        Toast.makeText(context, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (passwordRequested && (currentPassword.isBlank() || newPassword.length < 6 || confirmPassword.isBlank())) {
                        Toast.makeText(context, "Enter your current password and a new password of at least 6 characters, then confirm it.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (passwordRequested && newPassword != confirmPassword) {
                        Toast.makeText(context, "New password and confirmation do not match.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (passwordRequested && (auth.currentUser?.email.isNullOrBlank())) {
                        Toast.makeText(context, "Password changes are unavailable for this sign-in method.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    isLoading = true
                    val updatedProfile = currentUser.copy(name = name.trim())
                    scope.launch {
                        try {
                            if (passwordRequested) {
                                val user = auth.currentUser ?: error("Please sign in again before changing your password.")
                                val email = user.email ?: error("Password changes are unavailable for this sign-in method.")
                                user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
                                user.updatePassword(newPassword).await()
                            }
                            db.collection("users").document(currentUser.uid)
                                .set(updatedProfile, SetOptions.merge()).await()
                            onProfileUpdated(updatedProfile)
                            Toast.makeText(context, if (passwordRequested) "Profile and password updated." else "Profile updated.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (error: Exception) {
                            Toast.makeText(context, "Couldn't update profile: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
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
                    Text("v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
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

    val householdId = currentUser.householdId?.takeIf { it.isNotBlank() } ?: return

    // Read the household-scoped member directory. Listing the global users
    // collection is intentionally denied by many Firestore security rules.
    DisposableEffect(householdId) {
        val registration = db.collection("households")
            .document(householdId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                isLoading = false
                if (error != null) {
                    Toast.makeText(context, "Couldn't load household members: ${error.localizedMessage ?: "permission denied"}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val memberList = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: doc.id
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
        onDispose { registration.remove() }
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
    val scope = rememberCoroutineScope()
    var isRemoving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isRemoving) onDismiss() },
        confirmButton = {
            Button(
                onClick = {
                    val hid = currentUser.householdId
                    if (hid.isNullOrBlank() || isRemoving) {
                        Toast.makeText(context, "Join a household before removing inventory.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isRemoving = true
                    scope.launch {
                        try {
                            val snapshot = db.collection("households").document(hid)
                                .collection("inventory").get().await()
                            val lowStockDocs = snapshot.documents.filter { doc ->
                                val quantity = (doc.get("currentQuantity") as? Number)?.toDouble()
                                    ?: (doc.get("quantity") as? Number)?.toDouble()
                                val threshold = (doc.get("alertThreshold") as? Number)?.toDouble()
                                    ?: (doc.get("lowStockThreshold") as? Number)?.toDouble()
                                    ?: 0.0
                                quantity != null && quantity <= threshold
                            }
                            lowStockDocs.chunked(450).forEach { docs ->
                                db.runBatch { batch -> docs.forEach { batch.delete(it.reference) } }.await()
                            }
                            Toast.makeText(context, "Removed ${lowStockDocs.size} low-stock item(s).", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (error: Exception) {
                            Toast.makeText(context, "Couldn't remove items: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                        } finally {
                            isRemoving = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isRemoving
            ) {
                Text(if (isRemoving) "Removing…" else "Remove Items", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        title = {
            Text("Remove Low Stock Items", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Text("This permanently deletes every inventory item at or below its low-stock threshold, including empty items. Household data is shared with all members.", color = Color.LightGray, fontSize = 13.sp)
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SendFeedbackDialog(currentUser: UserProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("FEATURE") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val householdId = currentUser.householdId

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Send feedback", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your message will be visible to household administrators.", color = Color.LightGray, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("FEATURE", "BUG").forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(if (option == "FEATURE") "Idea" else "Bug") }
                        )
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Details") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(enabled = !sending, onClick = {
                if (householdId.isNullOrBlank()) {
                    Toast.makeText(context, "Join a household before sending feedback.", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                if (title.isBlank() || description.isBlank()) {
                    Toast.makeText(context, "Add a title and details first.", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                sending = true
                scope.launch {
                    try {
                        val feedbackRef = Firebase.firestore.collection("households").document(householdId)
                            .collection("feedback").document()
                        feedbackRef.set(
                            mapOf(
                                "id" to feedbackRef.id,
                                "authorRole" to if (currentUser.role == Role.OWNER) "Owner" else "Member",
                                "type" to type,
                                "title" to title.trim(),
                                "description" to description.trim(),
                                "status" to "SUBMITTED",
                                "timestampText" to "Just Now",
                                "submittedAt" to System.currentTimeMillis()
                            )
                        ).await()
                        Toast.makeText(context, "Feedback sent.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (error: Exception) {
                        Toast.makeText(context, "Couldn't send feedback: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                    } finally {
                        sending = false
                    }
                }
            }) { Text(if (sending) "Sending…" else "Send") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") } },
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}

@Composable
private fun HouseholdFeedbackDialog(currentUser: UserProfile, onDismiss: () -> Unit) {
    val householdId = currentUser.householdId
    var rows by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(householdId) {
        if (householdId.isNullOrBlank()) {
            loading = false
            errorText = "Join a household to view its feedback."
            onDispose { }
        } else {
            val registration = Firebase.firestore.collection("households").document(householdId)
                .collection("feedback").addSnapshotListener { snapshot, error ->
                    loading = false
                    if (error != null) {
                        errorText = error.localizedMessage ?: "Unable to load feedback."
                    } else if (snapshot != null) {
                        errorText = null
                        rows = snapshot.documents.map { it.data.orEmpty() + ("_docId" to it.id) }
                            .sortedByDescending { (it["submittedAt"] as? Number)?.toLong() ?: 0L }
                    }
                }
            onDispose { registration.remove() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Household feedback", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            when {
                loading -> CircularProgressIndicator()
                errorText != null -> Text(errorText!!, color = Color.LightGray)
                rows.isEmpty() -> Text("No feedback has been submitted yet.", color = Color.LightGray)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(rows.size) { index ->
                        val row = rows[index]
                        Column(Modifier.fillMaxWidth().background(Color(0xFF1E293B), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(row["title"] as? String ?: "Untitled", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("${row["type"] ?: "FEEDBACK"} • ${row["authorRole"] ?: "Member"} • ${row["status"] ?: "SUBMITTED"}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(row["description"] as? String ?: "", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}

@Composable
private fun PrivacySummaryDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & data", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pantry Tracker stores household app data in Firebase so signed-in household members can share it. This can include pantry inventory, bills and budget cycles, calendar events, and feedback.", color = Color.LightGray, fontSize = 13.sp)
                Text("The app also stores settings on this device and may send local notifications when enabled. Use Sign Out to end this session. Household data is shared with members; contact the household owner for help managing it.", color = Color.LightGray, fontSize = 13.sp)
                Text("Do not enter passwords or bank credentials in feedback or household notes.", color = Color(0xFFFBBF24), fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray
    )
}

private fun documentToJson(document: DocumentSnapshot): JSONObject = JSONObject().apply {
    put("id", document.id)
    document.data.orEmpty().forEach { (key, value) -> put(key, jsonValue(value)) }
}

private fun jsonValue(value: Any?): Any? = when (value) {
    null -> JSONObject.NULL
    is Timestamp -> value.toDate().time
    is Date -> value.time
    is DocumentReference -> value.path
    is Map<*, *> -> JSONObject().apply { value.forEach { (key, item) -> if (key is String) put(key, jsonValue(item)) } }
    is Iterable<*> -> JSONArray().apply { value.forEach { put(jsonValue(it)) } }
    is Array<*> -> JSONArray().apply { value.forEach { put(jsonValue(it)) } }
    else -> value
}
