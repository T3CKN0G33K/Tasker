package com.example.pantry.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantry.ui.dialogs.AddNoteDialog
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BoardNoteItem(
    val id: String,
    val title: String,
    val content: String,
    val category: String, // "EMERGENCY", "PET", "DELIVERY", "UPKEEP", "INFO"
    val timestamp: String,
    val author: String? = null,
    val subtext: String? = null,
    val isPinned: Boolean = false
)

@Composable
fun HouseBoardScreen(
    currentUser: UserProfile,
    isDarkMode: Boolean = true
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var boardNotes by remember { mutableStateOf<List<BoardNoteItem>>(emptyList()) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showWifiAdminDialog by remember { mutableStateOf(false) }
    var showWifiEditDialog by remember { mutableStateOf(false) }

    var wifiSSID by remember { mutableStateOf("Apartment_5G_Home") }
    var wifiPassword by remember { mutableStateOf("HomeSweetPantry2026!") }

    // Live Cloud Firestore Sync under households/{householdId}/board_notices & board_notes
    LaunchedEffect(currentUser.householdId) {
        currentUser.householdId?.let { hid ->
            db.collection("households").document(hid).collection("board_notices")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            BoardNoteItem(
                                id = doc.id,
                                title = doc.getString("title") ?: "Notice",
                                content = doc.getString("content") ?: "",
                                category = doc.getString("category") ?: "INFO",
                                timestamp = doc.getString("dateLabel") ?: "Today",
                                author = doc.getString("authorName"),
                                subtext = doc.getString("subtext"),
                                isPinned = doc.getBoolean("isPinned") ?: false
                            )
                        }
                        boardNotes = items
                    }
                }
        }
    }

    val activeNotes = boardNotes

    HouseBoardScreenContent(
        notes = activeNotes,
        wifiSsid = wifiSSID,
        wifiPasswordProvider = { wifiPassword },
        onOpenAddNote = { showAddNoteDialog = true },
        onAdminUnlocked = { showWifiAdminDialog = true },
        onDeleteNote = { noteId ->
            boardNotes = boardNotes.filter { it.id != noteId }
            currentUser.householdId?.let { hid ->
                db.collection("households").document(hid).collection("board_notices").document(noteId).delete()
                db.collection("households").document(hid).collection("board_notes").document(noteId).delete()
            }
        }
    )

    // Add Note Dialog
    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onPostNote = { title, content, category, isPinned, theme ->
                val noteId = "note_" + System.currentTimeMillis()
                val catTag = if (category.contains("Pet")) "PET" else if (category.contains("Urgent") || category.contains("Emergency")) "EMERGENCY" else if (category.contains("Upkeep")) "UPKEEP" else "INFO"
                val newNote = BoardNoteItem(
                    id = noteId,
                    title = title.trim(),
                    content = content.trim(),
                    category = catTag,
                    timestamp = if (isPinned) "Pinned" else "Today",
                    author = currentUser.name.ifBlank { "Household Member" },
                    isPinned = isPinned
                )
                boardNotes = listOf(newNote) + boardNotes
                currentUser.householdId?.let { hid ->
                    val map = hashMapOf(
                        "id" to noteId,
                        "category" to newNote.category,
                        "title" to newNote.title,
                        "content" to newNote.content,
                        "dateLabel" to newNote.timestamp,
                        "authorName" to newNote.author,
                        "isPinned" to newNote.isPinned
                    )
                    db.collection("households").document(hid).collection("board_notices").document(noteId).set(map, SetOptions.merge())
                    db.collection("households").document(hid).collection("board_notes").document(noteId).set(map, SetOptions.merge())
                }
                showAddNoteDialog = false
            }
        )
    }

    // Wi-Fi Master PIN Unlock Dialog
    if (showWifiAdminDialog) {
        WifiAdminUnlockDialog(
            onDismiss = { showWifiAdminDialog = false },
            onSuccess = {
                showWifiAdminDialog = false
                showWifiEditDialog = true
            }
        )
    }

    // Wi-Fi Credentials Editor Dialog
    if (showWifiEditDialog) {
        WifiEditDialog(
            currentSSID = wifiSSID,
            currentPassword = wifiPassword,
            onDismiss = { showWifiEditDialog = false },
            onSave = { newSSID, newPassword ->
                wifiSSID = newSSID
                wifiPassword = newPassword
                showWifiEditDialog = false
                Toast.makeText(context, "Wi-Fi network credentials updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun HouseBoardScreenContent(
    notes: List<BoardNoteItem>,
    wifiSsid: String = "Apartment_5G_Home",
    wifiPasswordProvider: () -> String = { "HomeSweetPantry2026!" },
    onOpenAddNote: () -> Unit,
    onAdminUnlocked: () -> Unit,
    onDeleteNote: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // 10-Tap Gesture State
    var tapCount by remember { mutableIntStateOf(0) }
    var resetJob by remember { mutableStateOf<Job?>(null) }

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
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "House Board",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Shared Household Ledger & Notices",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // "All Notes" Filter Capsule
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        border = BorderStroke(1.2.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = "📌 All Notes",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Household Network Card (NO card-level clickable, 10-tap isolated to icon)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1E293B).copy(alpha = 0.85f),
                                        Color(0xFF0F172A).copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 10-TAP ICON ONLY
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        tapCount++
                                        resetJob?.cancel()
                                        resetJob = coroutineScope.launch {
                                            delay(1500)
                                            tapCount = 0
                                        }
                                        if (tapCount >= 10) {
                                            tapCount = 0
                                            resetJob?.cancel()
                                            onAdminUnlocked()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📡", fontSize = 22.sp)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Network Details (Static Text)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HOUSEHOLD NETWORK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = wifiSsid,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Copy Password Button
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(wifiPasswordProvider()))
                                },
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Copy PW",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "SHARED HOUSEHOLD NOTES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Note Cards with Explicit Inlined Gradients
            items(notes, key = { it.id }) { note ->
                // Determine styling based on category
                val (topGrad, borderColor, tagColor) = when {
                    note.category.contains("EMERGENCY", ignoreCase = true) -> Triple(
                        Color(0xFF78350F).copy(alpha = 0.50f),
                        Color(0xFFFBBF24).copy(alpha = 0.45f),
                        Color(0xFFFBBF24)
                    )
                    note.category.contains("PET", ignoreCase = true) -> Triple(
                        Color(0xFF0369A1).copy(alpha = 0.50f),
                        Color(0xFF38BDF8).copy(alpha = 0.40f),
                        Color(0xFF38BDF8)
                    )
                    note.category.contains("DELIVERY", ignoreCase = true) -> Triple(
                        Color(0xFF065F46).copy(alpha = 0.50f),
                        Color(0xFF34D399).copy(alpha = 0.40f),
                        Color(0xFF34D399)
                    )
                    note.category.contains("UPKEEP", ignoreCase = true) -> Triple(
                        Color(0xFF4C1D95).copy(alpha = 0.50f),
                        Color(0xFFA78BFA).copy(alpha = 0.40f),
                        Color(0xFFA78BFA)
                    )
                    else -> Triple(
                        Color(0xFF1E293B).copy(alpha = 0.85f),
                        Color(0xFF334155).copy(alpha = 0.60f),
                        Color(0xFF38BDF8)
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.2.dp, borderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(topGrad, Color(0xFF0F172A).copy(alpha = 0.90f))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top Tag Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (note.isPinned) "📌 ${note.category.uppercase()}" else note.category.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tagColor,
                                    letterSpacing = 0.6.sp
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = note.timestamp,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "🗑",
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable { onDeleteNote(note.id) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Note Title
                            Text(
                                text = note.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Note Content
                            Text(
                                text = note.content,
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 17.sp
                            )

                            // Optional Highlight/Subtext
                            if (!note.subtext.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = note.subtext,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tagColor
                                )
                            }

                            if (!note.author.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Posted by ${note.author}",
                                    fontSize = 11.sp,
                                    color = tagColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = onOpenAddNote,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 20.dp)
                .size(56.dp),
            shape = CircleShape,
            containerColor = com.example.pantry.ui.theme.LocalAppColors.current.fabColor,
            contentColor = Color.White
        ) {
            Text(text = "+", fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun WifiAdminUnlockDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (pinText.trim() == "2026" || pinText.trim() == "1234") {
                        onSuccess()
                    } else {
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Unlock", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        title = {
            Text("Admin Security Unlock", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter Master Admin PIN to edit Wi-Fi network credentials.", color = Color.LightGray, fontSize = 13.sp)
                OutlinedTextField(
                    value = pinText,
                    onValueChange = {
                        pinText = it
                        isError = false
                    },
                    label = { Text("Master PIN (Default: 2026)", color = Color.Gray) },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text("Incorrect PIN. Please try again.", color = Color(0xFFFF3B30), fontSize = 12.sp)
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun WifiEditDialog(
    currentSSID: String,
    currentPassword: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var ssid by remember { mutableStateOf(currentSSID) }
    var password by remember { mutableStateOf(currentPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (ssid.isNotBlank()) {
                        onSave(ssid.trim(), password.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Credentials", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        title = {
            Text("Edit Wi-Fi Network", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Network SSID", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Network Password", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(24.dp)
    )
}
