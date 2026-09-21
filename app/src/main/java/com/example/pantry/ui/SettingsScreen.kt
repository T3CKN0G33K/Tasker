package com.example.pantry.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

@Composable
fun SettingsScreen(
    currentUser: UserProfile = UserProfile(
        uid = "123", 
        name = "Thomas Barton", 
        email = "thomas@example.com", 
        role = Role.OWNER, 
        householdId = "house_abc"
    ),
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onProfileUpdated: (UserProfile) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(true) }
    var showManageDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSendFeedbackDialog by remember { mutableStateOf(false) }
    var showFeedbackFeedDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateInfo?>(null) }

    val canManageHousehold = currentUser.role == Role.OWNER || 
                           currentUser.role == Role.ADMIN || 
                           currentUser.canManageHousehold

    val screenBg = if (isDarkMode) Color.Black else Color(0xFFF2F2F7)
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.Gray else Color(0xFF6C6C70)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Text("Settings", color = textMain, fontSize = 34.sp, fontWeight = FontWeight.Bold)

        // Dynamic Profile Section (Clickable to Edit Profile)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .clickable { showEditProfileDialog = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentUser.name.take(1).uppercase(), 
                    color = textMain, 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(currentUser.name, color = textMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(currentUser.email, color = textSub, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Tap to edit profile & security", color = Color(0xFF007AFF), fontSize = 11.sp)
            }
            // Role Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF007AFF).copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(currentUser.role.name, color = Color(0xFF007AFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Household Group
        if (currentUser.householdId != null) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardBg)
            ) {
                // Permission Check: Show Manage Household if OWNER, ADMIN, or granted permission
                if (canManageHousehold) {
                    SettingsActionRow(
                        icon = Icons.Default.Home, 
                        iconBg = Color(0xFFFF9500), 
                        text = "Manage Household",
                        textColor = textMain,
                        onClick = { showManageDialog = true }
                    )
                }
                
                // Permission Check: Only Owners and Admins can view Feedback Feed and Share Link
                if (currentUser.role == Role.OWNER || currentUser.role == Role.ADMIN) {
                    if (canManageHousehold) {
                        HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
                    }
                    SettingsActionRow(
                        icon = Icons.Default.RateReview, 
                        iconBg = Color(0xFF5856D6), 
                        text = "Household Feedback Feed",
                        textColor = textMain,
                        onClick = { showFeedbackFeedDialog = true }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
                    SettingsActionRow(
                        icon = Icons.Default.PersonAdd, 
                        iconBg = Color(0xFF007AFF), 
                        text = "Share Invite Link",
                        textColor = textMain,
                        onClick = {
                            val inviteLink = "app://pantry/join/${currentUser.householdId}"
                            clipboardManager.setText(AnnotatedString(inviteLink))
                            Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
                    SettingsActionRow(
                        icon = Icons.Default.ContentCopy, 
                        iconBg = Color(0xFF34C759), 
                        text = "Copy Household ID",
                        textColor = textMain,
                        onClick = {
                            val hid = currentUser.householdId
                            clipboardManager.setText(AnnotatedString(hid))
                            Toast.makeText(context, "Household ID copied: $hid", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Preferences Group
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardBg)
        ) {
            SettingsToggleRow(
                icon = Icons.Default.Palette, 
                iconBg = Color(0xFF007AFF), 
                text = "Dark Mode", 
                textColor = textMain,
                checked = isDarkMode, 
                onCheckedChange = { isChecked ->
                    UserPreferences.saveDarkMode(context, isChecked)
                    onToggleDarkMode(isChecked)
                }
            )
            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            SettingsToggleRow(
                icon = Icons.Default.Notifications, 
                iconBg = Color(0xFFFF3B30), 
                text = "Notifications", 
                textColor = textMain,
                checked = notifications, 
                onCheckedChange = { notifications = it }
            )
            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            SettingsActionRow(
                icon = Icons.Default.Feedback,
                iconBg = Color(0xFF5856D6),
                text = "Send App Feedback",
                textColor = textMain,
                onClick = { showSendFeedbackDialog = true }
            )
        }

        // About / Actions Group
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardBg)
        ) {
            SettingsActionRow(
                icon = Icons.Default.SystemUpdate, 
                iconBg = Color(0xFF007AFF), 
                text = "Check for Updates",
                textColor = textMain,
                onClick = {
                    Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                    AppUpdateManager.checkForUpdates(context, forceShowToast = true) { update ->
                        showUpdateDialog = update
                    }
                }
            )
            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            SettingsActionRow(
                icon = Icons.Default.PrivacyTip, 
                iconBg = Color(0xFF34C759), 
                text = "Privacy Policy",
                textColor = textMain,
                onClick = { showPrivacyPolicyDialog = true }
            )
            HorizontalDivider(color = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOut() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Log Out", color = Color(0xFFFF3B30), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }

        // App Version & Build Footer
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.3"
        } catch (_: Exception) { "1.3" }
        val versionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (_: Exception) { 14L }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tasker v$versionName (Build $versionCode)",
                color = textSub,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            currentUser = currentUser,
            onDismiss = { showEditProfileDialog = false },
            onProfileUpdated = { updated ->
                onProfileUpdated(updated)
            }
        )
    }

    if (showManageDialog) {
        ManageHouseholdDialog(
            currentUser = currentUser,
            onDismiss = { showManageDialog = false }
        )
    }

    if (showSendFeedbackDialog) {
        SendFeedbackDialog(
            currentUser = currentUser,
            onDismiss = { showSendFeedbackDialog = false }
        )
    }

    if (showFeedbackFeedDialog) {
        HouseholdFeedbackFeedDialog(
            currentUser = currentUser,
            onDismiss = { showFeedbackFeedDialog = false }
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }

    showUpdateDialog?.let { updateInfo ->
        AppUpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { showUpdateDialog = null }
        )
    }
}

@Composable
fun SendFeedbackDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit
) {
    val db = Firebase.firestore
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }
    var selectedTopic by remember { mutableStateOf("Bug Report") }
    var isSubmitting by remember { mutableStateOf(false) }

    val topics = listOf("Bug Report", "Feature Request", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Send App Feedback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Let us know what's working or what needs fixing:", color = Color.Gray, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Topic Chips Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topics.forEach { topic ->
                        val isSelected = selectedTopic == topic
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF007AFF) else Color(0xFF2C2C2E))
                                .clickable { selectedTopic = topic }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (topic == "Feature Request") "Feature" else if (topic == "Bug Report") "Bug" else topic,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = { Text("Type your feedback here...", color = Color.DarkGray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF007AFF),
                        unfocusedBorderColor = Color(0xFF3A3A3C),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (feedbackText.isBlank()) return@Button
                    isSubmitting = true
                    currentUser.householdId?.let { hid ->
                        val feedbackData = hashMapOf(
                            "senderName" to currentUser.name,
                            "senderEmail" to currentUser.email,
                            "topic" to selectedTopic,
                            "message" to feedbackText.trim(),
                            "timestamp" to System.currentTimeMillis()
                        )
                        db.collection("households").document(hid).collection("feedbacks")
                            .add(feedbackData)
                            .addOnSuccessListener {
                                isSubmitting = false
                                Toast.makeText(context, "Feedback submitted successfully!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            .addOnFailureListener { e ->
                                isSubmitting = false
                                Toast.makeText(context, "Failed to submit: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } ?: run {
                        isSubmitting = false
                        Toast.makeText(context, "Feedback submitted!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                enabled = !isSubmitting && feedbackText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("Submit", color = Color.White, fontWeight = FontWeight.Bold)
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
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Privacy Policy", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Tasker Pantry Manager Data Protection", color = Color.Gray, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("1. Data Security & Household Isolation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "All inventory items, budgets, transactions, and member roles are linked strictly to your encrypted Household ID in Firebase Firestore. Only authenticated members of your household can access your pantry data.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Text("2. Authentication & Credentials", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "User credentials are secured through Firebase Authentication. We never store raw passwords or sensitive payment data.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Text("3. Device Permissions & Preferences", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "The app requests Android permissions for Restock Notifications and In-App Auto-Updates. Your preferences are cached locally on your device for instant offline access.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Text("4. No Third-Party Data Selling", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "We do not sell, rent, or trade your personal information or household inventory data with any third-party advertisers.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
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
fun HouseholdFeedbackFeedDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit
) {
    val db = Firebase.firestore
    var feedbackList by remember { mutableStateOf<List<FeedbackItem>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentUser.householdId) {
        currentUser.householdId?.let { hid ->
            db.collection("households").document(hid).collection("feedbacks")
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            FeedbackItem(
                                id = doc.id,
                                senderName = doc.getString("senderName") ?: "Member",
                                senderEmail = doc.getString("senderEmail") ?: "",
                                topic = doc.getString("topic") ?: "Other",
                                message = doc.getString("message") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        }
                        feedbackList = items.sortedByDescending { it.timestamp }
                    }
                }
        }
    }

    val filteredFeed = remember(feedbackList, selectedFilter) {
        if (selectedFilter == "All") feedbackList
        else feedbackList.filter { it.topic.equals(selectedFilter, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Household Feedback Feed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Real-time feedback submitted by household members", color = Color.Gray, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Categorized Filter Chips Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filterOptions = listOf("All", "Bug Report", "Feature Request", "Other")
                    filterOptions.forEach { option ->
                        val isSelected = selectedFilter == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF007AFF) else Color(0xFF2C2C2E))
                                .clickable { selectedFilter = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (option == "Feature Request") "Feature" else if (option == "Bug Report") "Bugs" else option,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                } else if (filteredFeed.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedFilter == "All") "No feedback submitted yet." else "No ${selectedFilter}s found.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFeed, key = { it.id }) { item ->
                            FeedbackCardItem(
                                item = item,
                                isOwnerOrAdmin = currentUser.role == Role.OWNER || currentUser.role == Role.ADMIN,
                                onTopicChanged = { newTopic ->
                                    currentUser.householdId?.let { hid ->
                                        db.collection("households").document(hid)
                                            .collection("feedbacks").document(item.id)
                                            .update("topic", newTopic)
                                    }
                                }
                            )
                        }
                    }
                }
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
fun FeedbackCardItem(
    item: FeedbackItem,
    isOwnerOrAdmin: Boolean = false,
    onTopicChanged: (newTopic: String) -> Unit = {}
) {
    var showTopicMenu by remember { mutableStateOf(false) }

    val topicColor = when (item.topic) {
        "Bug Report" -> Color(0xFFFF3B30)
        "Feature Request" -> Color(0xFF34C759)
        else -> Color(0xFF007AFF)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2C2C2E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Topic Tag Pill (Clickable for Owner/Admin to edit topic category)
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(topicColor.copy(alpha = 0.2f))
                            .clickable(enabled = isOwnerOrAdmin) { showTopicMenu = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = item.topic.uppercase() + if (isOwnerOrAdmin) " ▾" else "",
                            color = topicColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showTopicMenu,
                        onDismissRequest = { showTopicMenu = false },
                        modifier = Modifier.background(Color(0xFF1C1C1E))
                    ) {
                        listOf("Bug Report", "Feature Request", "Other").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    showTopicMenu = false
                                    if (option != item.topic) {
                                        onTopicChanged(option)
                                    }
                                }
                            )
                        }
                    }
                }

                Text(
                    text = item.senderName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = item.message,
                color = Color.White,
                fontSize = 14.sp
            )

            Text(
                text = item.senderEmail,
                color = Color.Gray,
                fontSize = 11.sp
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
    var email by remember { mutableStateOf(currentUser.email) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val auth = Firebase.auth
    val db = Firebase.firestore

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Text("Edit Profile & Security", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("ACCOUNT DETAILS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("SECURITY & PASSWORD", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password (for changes)", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password (Optional)", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser == null) return@Button

                    isLoading = true
                    val trimmedEmail = email.trim()
                    val emailChanged = trimmedEmail.isNotBlank() && trimmedEmail != currentUser.email
                    val passwordChanged = newPassword.isNotBlank()

                    if (emailChanged || passwordChanged) {
                        if (currentPassword.isBlank()) {
                            isLoading = false
                            Toast.makeText(context, "Current password required to save email/password changes.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val credential = EmailAuthProvider.getCredential(currentUser.email, currentPassword)
                        firebaseUser.reauthenticate(credential)
                            .addOnSuccessListener {
                                if (emailChanged) {
                                    firebaseUser.verifyBeforeUpdateEmail(trimmedEmail)
                                }
                                if (passwordChanged) {
                                    firebaseUser.updatePassword(newPassword)
                                }
                                updateProfileInFirestore(firebaseUser.uid, name, trimmedEmail, currentUser, db, context) { updated ->
                                    isLoading = false
                                    onProfileUpdated(updated)
                                    onDismiss()
                                }
                            }
                    } else {
                        updateProfileInFirestore(firebaseUser.uid, name, currentUser.email, currentUser, db, context) { updated ->
                            isLoading = false
                            onProfileUpdated(updated)
                            onDismiss()
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

private fun updateProfileInFirestore(
    uid: String,
    name: String,
    email: String,
    currentUser: UserProfile,
    db: FirebaseFirestore,
    context: Context,
    onSuccess: (UserProfile) -> Unit
) {
    val updatedProfile = currentUser.copy(name = name, email = email)

    val userMap = hashMapOf<String, Any>(
        "name" to name,
        "email" to email
    )

    // Update users/{uid}
    db.collection("users").document(uid).update(userMap)

    // Update households/{hid}/members/{uid}
    currentUser.householdId?.let { hid ->
        db.collection("households").document(hid)
            .collection("members").document(uid)
            .update(userMap)
    }

    UserPreferences.saveUser(context, updatedProfile)
    Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
    onSuccess(updatedProfile)
}

@Composable
fun ManageHouseholdDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit
) {
    val db = Firebase.firestore
    var memberList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var userList by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentUser.householdId) {
        currentUser.householdId?.let { hid ->
            // Source 1: Listen to households/{hid}/members sub-collection
            db.collection("households").document(hid).collection("members")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val members = snapshot.documents.mapNotNull { doc ->
                            val uid = doc.id
                            val name = doc.getString("name") ?: "Member"
                            val email = doc.getString("email") ?: ""
                            val roleStr = doc.getString("role") ?: "MEMBER"
                            val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.MEMBER }
                            val canManage = doc.getBoolean("canManageHousehold") ?: false
                            UserProfile(uid, name, email, role, hid, canManage)
                        }
                        memberList = members
                        isLoading = false
                    }
                }

            // Source 2: Listen to users collection where householdId == hid
            db.collection("users").whereEqualTo("householdId", hid)
                .addSnapshotListener { userSnapshot, _ ->
                    if (userSnapshot != null) {
                        val users = userSnapshot.documents.mapNotNull { doc ->
                            val uid = doc.id
                            val name = doc.getString("name") ?: "Member"
                            val email = doc.getString("email") ?: ""
                            val roleStr = doc.getString("role") ?: "MEMBER"
                            val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.MEMBER }
                            val canManage = doc.getBoolean("canManageHousehold") ?: false
                            UserProfile(uid, name, email, role, hid, canManage)
                        }
                        userList = users
                        isLoading = false
                    }
                }
        }
    }

    // Combine and deduplicate members from both sources
    val combinedMembers = remember(memberList, userList) {
        (memberList + userList).distinctBy { it.uid }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = {
            Column {
                Text("Household Members", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("ID: ${currentUser.householdId ?: ""}", color = Color(0xFF007AFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                }
            } else if (combinedMembers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("No other members found in household.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    combinedMembers.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2C2C2E))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF3A3A3C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(member.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(member.email, color = Color.Gray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = member.role.name + if (member.canManageHousehold && member.role == Role.MEMBER) " (Access Granted)" else "",
                                    color = Color(0xFF007AFF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Permission Toggle for Owner
                            if (currentUser.role == Role.OWNER && member.uid != currentUser.uid) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Manage Access", color = Color.Gray, fontSize = 9.sp)
                                    Switch(
                                        checked = member.canManageHousehold || member.role == Role.ADMIN,
                                        onCheckedChange = { isChecked ->
                                            val newRole = if (isChecked) Role.ADMIN else Role.MEMBER
                                            val updateMap = hashMapOf<String, Any>(
                                                "canManageHousehold" to isChecked,
                                                "role" to newRole.name
                                            )

                                            // Update households/{hid}/members/{member.uid}
                                            currentUser.householdId?.let { hid ->
                                                db.collection("households").document(hid)
                                                    .collection("members").document(member.uid)
                                                    .update(updateMap)
                                            }

                                            // Update users/{member.uid}
                                            db.collection("users").document(member.uid)
                                                .update(updateMap)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF34C759),
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color(0xFF3A3A3C)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector, 
    iconBg: Color, 
    text: String, 
    textColor: Color = Color.White,
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), 
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, 
                checkedTrackColor = Color(0xFF34C759), 
                uncheckedThumbColor = Color.White, 
                uncheckedTrackColor = Color(0xFF3A3A3C), 
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector, 
    iconBg: Color, 
    text: String,
    textColor: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), 
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF8E8E93))
    }
}
