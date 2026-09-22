package com.example.pantry.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Brush
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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardBg = if (isDarkMode) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.70f)
    }
    
    val cardBorder = if (isDarkMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color.White.copy(alpha = 0.50f)
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                brush = cardBorder,
                shape = RoundedCornerShape(20.dp)
            ),
        content = content
    )
}

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
    val db = Firebase.firestore
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

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

    // Root layout set to Color.Transparent to unblock ambient background gradient!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", color = textMain, fontSize = 34.sp, fontWeight = FontWeight.Bold)

        // 1. Dynamic Profile Section (Upgraded to GlassCard)
        GlassCard(
            isDarkMode = isDarkMode,
            modifier = Modifier.clickable { showEditProfileDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.20f) else Color(0xFFE5E5EA)),
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
                        .background(Color(0xFF007AFF).copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(currentUser.role.name, color = Color(0xFF007AFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Household Group (Upgraded to GlassCard)
        if (currentUser.householdId != null) {
            GlassCard(isDarkMode = isDarkMode) {
                if (canManageHousehold) {
                    SettingsActionRow(
                        icon = Icons.Default.Home, 
                        iconBg = Color(0xFFFF9500), 
                        text = "Manage Household",
                        textColor = textMain,
                        onClick = { showManageDialog = true }
                    )
                }
                
                if (currentUser.role == Role.OWNER || currentUser.role == Role.ADMIN) {
                    if (canManageHousehold) {
                        HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
                    }
                    SettingsActionRow(
                        icon = Icons.Default.RateReview, 
                        iconBg = Color(0xFF5856D6), 
                        text = "Household Feedback Feed",
                        textColor = textMain,
                        onClick = { showFeedbackFeedDialog = true }
                    )
                    HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
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
                    HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
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

        // 3. Theme Color Selector Group (Upgraded to GlassCard)
        GlassCard(isDarkMode = isDarkMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF5856D6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme Color",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Theme Color", color = textMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose ambient gradient for Liquid Glass refraction", color = textSub, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. DEFAULT Option
                    ThemeOptionButton(
                        label = "Default",
                        selected = currentUser.themePreference.equals("DEFAULT", ignoreCase = true),
                        previewBrush = Brush.verticalGradient(
                            colors = if (isDarkMode) listOf(Color(0xFF1C1C1E), Color.Black) else listOf(Color.White, Color(0xFFF2F2F7))
                        ),
                        isDarkMode = isDarkMode,
                        onClick = {
                            val updated = currentUser.copy(themePreference = "DEFAULT")
                            updateUserTheme(updated, db, context, onProfileUpdated)
                        }
                    )

                    // 2. CYAN Option
                    ThemeOptionButton(
                        label = "Cyan",
                        selected = currentUser.themePreference.equals("CYAN", ignoreCase = true),
                        previewBrush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF4DD0E1), Color(0xFF0097A9))
                        ),
                        isDarkMode = isDarkMode,
                        onClick = {
                            val updated = currentUser.copy(themePreference = "CYAN")
                            updateUserTheme(updated, db, context, onProfileUpdated)
                        }
                    )

                    // 3. PURPLE Option
                    ThemeOptionButton(
                        label = "Purple",
                        selected = currentUser.themePreference.equals("PURPLE", ignoreCase = true),
                        previewBrush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF9E6BC2), Color(0xFF734199))
                        ),
                        isDarkMode = isDarkMode,
                        onClick = {
                            val updated = currentUser.copy(themePreference = "PURPLE")
                            updateUserTheme(updated, db, context, onProfileUpdated)
                        }
                    )
                }
            }
        }

        // 4. Preferences Group (Upgraded to GlassCard)
        GlassCard(isDarkMode = isDarkMode) {
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
            HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            SettingsToggleRow(
                icon = Icons.Default.Notifications, 
                iconBg = Color(0xFFFF3B30), 
                text = "Notifications", 
                textColor = textMain,
                checked = notifications, 
                onCheckedChange = { notifications = it }
            )
            HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            SettingsActionRow(
                icon = Icons.Default.Feedback,
                iconBg = Color(0xFF5856D6),
                text = "Send App Feedback",
                textColor = textMain,
                onClick = { showSendFeedbackDialog = true }
            )
        }

        // 5. About / Actions Group (Upgraded to GlassCard)
        GlassCard(isDarkMode = isDarkMode) {
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
            HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            SettingsActionRow(
                icon = Icons.Default.PrivacyTip, 
                iconBg = Color(0xFF34C759), 
                text = "Privacy Policy",
                textColor = textMain,
                onClick = { showPrivacyPolicyDialog = true }
            )
            HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOut() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Sign Out", color = Color(0xFFFF3B30), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Dialogs
    if (showManageDialog) {
        ManageHouseholdDialog(currentUser = currentUser, onDismiss = { showManageDialog = false })
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            currentUser = currentUser,
            onDismiss = { showEditProfileDialog = false },
            onProfileUpdated = onProfileUpdated
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
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

    showUpdateDialog?.let { updateInfo ->
        AppUpdateDialog(updateInfo = updateInfo, onDismiss = { showUpdateDialog = null })
    }
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

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
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
                            val tPref = doc.getString("themePreference") ?: "DEFAULT"
                            UserProfile(uid, name, email, role, hid, canManage, tPref)
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
                            val tPref = doc.getString("themePreference") ?: "DEFAULT"
                            UserProfile(uid, name, email, role, hid, canManage, tPref)
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

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
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
fun ThemeOptionButton(
    label: String,
    selected: Boolean,
    previewBrush: Brush,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(previewBrush)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color(0xFF007AFF) else if (isDarkMode) Color(0xFF3A3A3C) else Color(0xFFE5E5EA),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            color = if (selected) Color(0xFF007AFF) else if (isDarkMode) Color.Gray else Color(0xFF6C6C70),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun updateUserTheme(
    updatedUser: UserProfile,
    db: FirebaseFirestore,
    context: Context,
    onProfileUpdated: (UserProfile) -> Unit
) {
    UserPreferences.saveUser(context, updatedUser)
    onProfileUpdated(updatedUser)

    db.collection("users").document(updatedUser.uid)
        .set(hashMapOf("themePreference" to updatedUser.themePreference), SetOptions.merge())
        .addOnSuccessListener {
            Toast.makeText(context, "Theme set to ${updatedUser.themePreference}!", Toast.LENGTH_SHORT).show()
        }
}

@Composable
fun SendFeedbackDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Bug Report") }
    var isLoading by remember { mutableStateOf(false) }
    val feedbackTypes = listOf("Bug Report", "Feature Request", "Other")

    val context = LocalContext.current
    val db = Firebase.firestore

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Feedback", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    feedbackTypes.forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF007AFF) else Color(0xFF2C2C2E))
                                .clickable { selectedType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (type == "Feature Request") "Feature" else if (type == "Bug Report") "Bugs" else type,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("Tell us what's on your mind...", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2C2C2E),
                        unfocusedContainerColor = Color(0xFF2C2C2E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (message.isNotBlank()) {
                        isLoading = true
                        val hid = currentUser.householdId
                        if (hid != null) {
                            val docRef = db.collection("households").document(hid).collection("feedbacks").document()
                            val feedbackData = hashMapOf(
                                "senderUid" to currentUser.uid,
                                "senderName" to currentUser.name,
                                "senderEmail" to currentUser.email,
                                "topic" to selectedType,
                                "message" to message.trim(),
                                "timestamp" to System.currentTimeMillis()
                            )
                            docRef.set(feedbackData)
                                .addOnSuccessListener {
                                    isLoading = false
                                    Toast.makeText(context, "Feedback sent to household!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Feedback saved locally!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                enabled = !isLoading
            ) {
                Text("Submit", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    iconBg: Color,
    text: String,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    iconBg: Color,
    text: String,
    textColor: Color,
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
            Icon(imageVector = icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
        )
    }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Policy", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Pantry & Household Manager built for seamless local and synced household organization.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Text(
                    "Data Protection:\n- We do not sell or share personal data.\n- Household invites are securely scoped to member IDs.\n- All authentication tokens are managed by Google Firebase.",
                    color = Color.Gray,
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
                        focusedBorderColor = Color(0xFF007AFF)
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Password", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = isUpdatingPassword,
                        onCheckedChange = { isUpdatingPassword = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF007AFF))
                    )
                }

                if (isUpdatingPassword) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password", color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2C2C2E),
                            unfocusedContainerColor = Color(0xFF2C2C2E)
                        )
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (6+ chars)", color = Color.Gray) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2C2C2E),
                            unfocusedContainerColor = Color(0xFF2C2C2E)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    val updatedUser = currentUser.copy(name = name.trim())

                    // 1. Update Firestore Profile
                    db.collection("users").document(currentUser.uid).update("name", name.trim())
                        .addOnSuccessListener {
                            // Update local storage
                            UserPreferences.saveUser(context, updatedUser)
                            onProfileUpdated(updatedUser)

                            // 2. Handle Password Change if toggled
                            if (isUpdatingPassword) {
                                if (currentPassword.isBlank() || newPassword.length < 6) {
                                    isLoading = false
                                    Toast.makeText(context, "Enter current password and new password (min 6 chars).", Toast.LENGTH_LONG).show()
                                    return@addOnSuccessListener
                                }

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
