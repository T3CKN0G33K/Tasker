package com.example.pantry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable
fun ProfileEditScreen(
    initialDisplayName: String = "Bartont4432",
    initialRole: String = "Household Manager (Admin)",
    initialEmail: String = "bartont4432@gmail.com",
    initialHouseholdGroup: String = "Main Household",
    activeSyncDevices: String = "Galaxy Z Fold, Desktop Server, Tablet",
    onBack: () -> Unit = {},
    onSaveChanges: (displayName: String) -> Unit = {},
    onLeaveOrTransferHousehold: () -> Unit = {}
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)) // Darkest canvas base
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 46.dp, bottom = 48.dp)
        ) {
            // Header Bar: Back button, Screen Title, and Top Cancel Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                                .border(1.2.dp, Color(0xFF334155), CircleShape)
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "‹",
                                color = Color(0xFFF8FAFC),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "Edit Profile",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.clickable { onBack() }
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))
            }

            // Avatar Change Hero Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                                )
                            )
                            .border(2.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.uppercase() ?: "B",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Change Photo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.clickable { /* Photo Picker */ }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }

            // Editable Display Name Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E293B).copy(alpha = 0.85f), Color(0xFF0F172A).copy(alpha = 0.95f))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = "DISPLAY NAME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.8f),
                                    unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.8f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Household Role (Read-Only)
            item {
                ProfileMetaCard(
                    label = "HOUSEHOLD ROLE",
                    value = initialRole,
                    trailingIcon = "🔒"
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Linked Account Email (Read-Only)
            item {
                ProfileMetaCard(
                    label = "LINKED ACCOUNT",
                    value = initialEmail,
                    trailingIcon = "🔒"
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Household Group & Sync Telemetry
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.40f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E293B).copy(alpha = 0.80f), Color(0xFF0F172A).copy(alpha = 0.90f))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "HOUSEHOLD GROUP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.6.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = initialHouseholdGroup,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "3 Online",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(thickness = 0.8.dp, color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Active Sync Devices",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeSyncDevices,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
            }

            // Leave or Transfer Household Danger Action
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLeaveOrTransferHousehold() },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF7F1D1D).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.40f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Leave or Transfer Household",
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Save Changes CTA
            item {
                Button(
                    onClick = {
                        if (displayName.isNotBlank()) {
                            onSaveChanges(displayName)
                            onBack()
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
                            text = "SAVE CHANGES",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Discard Changes Link
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Discard changes",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// Helper Meta Card

@Composable
private fun ProfileMetaCard(
    label: String,
    value: String,
    trailingIcon: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E293B).copy(alpha = 0.80f), Color(0xFF0F172A).copy(alpha = 0.90f))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1)
                )
            }

            Text(text = trailingIcon, fontSize = 14.sp)
        }
    }
}
