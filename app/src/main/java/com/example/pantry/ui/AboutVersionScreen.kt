package com.example.pantry.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasker.BuildConfig
import com.example.tasker.R

@Composable
fun AboutVersionScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val colors = com.example.pantry.ui.theme.LocalAppColors.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.cardSurface)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("About", color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Pantry Tracker", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colors.cardSurface,
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(colors.primaryAccent.copy(alpha = 0.12f))
                                .padding(13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_staple_box_logo),
                                contentDescription = "Pantry Tracker logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Pantry Tracker",
                            color = colors.textPrimary,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "A shared place to keep up with household essentials.",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = colors.primaryAccent.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME}  ·  Build ${BuildConfig.VERSION_CODE}",
                                color = colors.primaryAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutSectionHeading("WHAT YOU CAN DO", colors.textSecondary)
                    AboutCard {
                        AboutFeatureRow(
                            icon = "📦",
                            title = "Track pantry stock",
                            subtitle = "See what you have and get low-stock alerts.",
                            accent = Color(0xFF10B981),
                            textColor = colors.textPrimary,
                            secondaryColor = colors.textSecondary
                        )
                        AboutDivider(colors.cardBorder)
                        AboutFeatureRow(
                            icon = "🧾",
                            title = "Keep a bill checklist",
                            subtitle = "Mark bills paid and review past months.",
                            accent = Color(0xFF0EA5E9),
                            textColor = colors.textPrimary,
                            secondaryColor = colors.textSecondary
                        )
                        AboutDivider(colors.cardBorder)
                        AboutFeatureRow(
                            icon = "📅",
                            title = "Coordinate at home",
                            subtitle = "Share calendar events, reminders, and household notes.",
                            accent = Color(0xFF8B5CF6),
                            textColor = colors.textPrimary,
                            secondaryColor = colors.textSecondary
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutSectionHeading("APP INFORMATION", colors.textSecondary)
                    AboutCard {
                        AboutInfoRow("App version", BuildConfig.VERSION_NAME, colors.textPrimary, colors.textSecondary)
                        AboutDivider(colors.cardBorder)
                        AboutInfoRow("Build number", BuildConfig.VERSION_CODE.toString(), colors.textPrimary, colors.textSecondary)
                        AboutDivider(colors.cardBorder)
                        AboutInfoRow(
                            "Android version",
                            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            colors.textPrimary,
                            colors.textSecondary
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutSectionHeading("HELP & PRIVACY", colors.textSecondary)
                    AboutCard {
                        AboutActionRow(
                            icon = Icons.Default.SystemUpdate,
                            title = "Check for updates",
                            subtitle = "Look for a newer release",
                            colors = colors,
                            onClick = {
                                AppUpdateManager.checkForUpdates(context, forceShowToast = true) { foundUpdate ->
                                    updateInfo = foundUpdate
                                }
                            }
                        )
                        AboutDivider(colors.cardBorder)
                        AboutActionRow(
                            icon = Icons.Default.PrivacyTip,
                            title = "Privacy & data",
                            subtitle = "How household information is used",
                            colors = colors,
                            onClick = { showPrivacyDialog = true }
                        )
                        AboutDivider(colors.cardBorder)
                        AboutActionRow(
                            icon = Icons.Default.ContentCopy,
                            title = "Copy diagnostic details",
                            subtitle = "Version, Android version, and device model",
                            colors = colors,
                            onClick = {
                                val info = """Pantry Tracker ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Package: ${BuildConfig.APPLICATION_ID}""".trimIndent()
                                clipboardManager.setText(AnnotatedString(info))
                                Toast.makeText(context, "Diagnostic details copied.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Pantry Tracker · Android",
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & data", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Household pantry, bill, budget, calendar, and feedback data is stored in Firebase so signed-in household members can share it.",
                        color = colors.textSecondary
                    )
                    Text(
                        "App preferences are stored on this device. Notifications are delivered by Android when enabled. Use the household backup option in Settings to export shared data.",
                        color = colors.textSecondary
                    )
                    Text(
                        "Do not put passwords or bank login credentials in household notes or feedback.",
                        color = colors.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Done") }
            },
            containerColor = colors.cardSurface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary
        )
    }

    updateInfo?.let { foundUpdate ->
        AppUpdateDialog(
            updateInfo = foundUpdate,
            onDismiss = { updateInfo = null }
        )
    }
}

@Composable
private fun AboutSectionHeading(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = com.example.pantry.ui.theme.LocalAppColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(content = content)
    }
}

@Composable
private fun AboutFeatureRow(
    icon: String,
    title: String,
    subtitle: String,
    accent: Color,
    textColor: Color,
    secondaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 20.sp) }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = secondaryColor, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String, primary: Color, secondary: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = secondary, fontSize = 13.sp)
        Text(value, color = primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AboutActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    colors: com.example.pantry.ui.theme.AppThemeColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(colors.primaryAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Text("›", color = colors.textSecondary, fontSize = 22.sp)
    }
}

@Composable
private fun AboutDivider(color: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = color
    )
}
