package com.example.pantry.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantry.ui.GlassBottomSheet

enum class NoteTheme(val primary: Color, val border: Color, val background: Color) {
    CYAN(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF0369A1).copy(alpha = 0.5f)),
    AMBER(Color(0xFFB45309), Color(0xFFFBBF24), Color(0xFF78350F).copy(alpha = 0.5f)),
    EMERALD(Color(0xFF047857), Color(0xFF34D399), Color(0xFF065F46).copy(alpha = 0.5f)),
    VIOLET(Color(0xFF6D28D9), Color(0xFFA78BFA), Color(0xFF4C1D95).copy(alpha = 0.5f)),
    SLATE(Color(0xFF334155), Color(0xFF64748B), Color(0xFF1E293B).copy(alpha = 0.5f))
}

@Composable
fun AddNoteDialog(
    onDismiss: () -> Unit,
    onPostNote: (title: String, content: String, category: String, isPinned: Boolean, theme: NoteTheme) -> Unit
) {
    var title by remember { mutableStateOf("Buddy & Clifford Feeding Routine") }
    var content by remember {
        mutableStateOf("Buddy wet food half-can in pantry cupboard.\nWater bowl refreshed daily at 7 PM.\nExtra dry bags on the lower utility shelf.")
    }
    var selectedCategory by remember { mutableStateOf("🐾 Pet Care") }
    var selectedTheme by remember { mutableStateOf(NoteTheme.CYAN) }
    var isPinned by remember { mutableStateOf(true) }

    val categories = listOf("🐾 Pet Care", "⚠️ Urgent", "🧹 Upkeep", "Info")

    GlassBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Post New Note",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pin instructions or notices to the household board",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
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
                    Text(text = "✕", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Note Color Theme Swatches
            Text(
                text = "NOTE COLOR THEME",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NoteTheme.entries.forEach { theme ->
                    val isSelected = selectedTheme == theme
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(theme.primary)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) theme.border else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedTheme = theme },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(text = "✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selector Pills
            Text(
                text = "CATEGORY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0xFF0369A1).copy(alpha = 0.4f) else Color(0xFF0F172A),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Preview Card with Inputs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(selectedTheme.background)
                    .border(1.dp, selectedTheme.border.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NOTE TITLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = selectedTheme.border,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                            unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "CONTENT & INSTRUCTIONS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = selectedTheme.border,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                            unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                            focusedTextColor = Color(0xFFCBD5E1),
                            unfocusedTextColor = Color(0xFFCBD5E1),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pin to Top Toggle Switch
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📌", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pin to Top of House Board",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keeps this note visible above recent cards",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Post Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onPostNote(title, content, selectedCategory, isPinned, selectedTheme)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "POST TO BOARD",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Discard / Cancel Link
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Discard Note",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
