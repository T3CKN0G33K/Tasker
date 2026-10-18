package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutVersionScreen(
    versionName: String = "v1.29",
    buildNumber: Int = 30,
    onBack: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    onOpenLicenses: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)) // Darkest canvas background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 46.dp, bottom = 48.dp)
        ) {
            // Top App Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        text = "About App",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }

            // Hero Brand Identity Banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
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
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Hologram Box Icon Badge
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF0284C7).copy(alpha = 0.35f), Color(0xFF1E3A8A).copy(alpha = 0.50f))
                                        )
                                    )
                                    .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📦", fontSize = 34.sp)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Pantry Tracker",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = (-0.3).sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(
                                    text = "$versionName (Build $buildNumber)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))
            }

            // Section 1: System & Build Details
            item {
                Text(
                    text = "SYSTEM & BUILD DETAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                AboutGroupContainer {
                    AboutTelemetryRow(
                        icon = "⚡",
                        iconBg = Color(0xFF0284C7).copy(alpha = 0.20f),
                        title = "Engine Core",
                        subtitle = "Jetpack Compose AGSL Native",
                        trailingTag = "Active",
                        trailingTagColor = Color(0xFF10B981)
                    )

                    AboutDivider()

                    AboutTelemetryRow(
                        icon = "🚀",
                        iconBg = Color(0xFF10B981).copy(alpha = 0.15f),
                        title = "Check for Updates",
                        subtitle = "Up to date • Production Branch",
                        trailingTag = "v1.29",
                        trailingTagColor = Color(0xFF38BDF8)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))
            }

            // Section 2: Legal & Compliance
            item {
                Text(
                    text = "LEGAL & COMPLIANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                AboutGroupContainer {
                    AboutNavRow(
                        icon = "🛡️",
                        iconBg = Color(0xFF0284C7).copy(alpha = 0.15f),
                        title = "Privacy Policy",
                        subtitle = "How your household data is protected",
                        onClick = onOpenPrivacyPolicy
                    )

                    AboutDivider()

                    AboutNavRow(
                        icon = "📄",
                        iconBg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        title = "Terms of Service",
                        subtitle = "Usage agreements & local storage rules",
                        onClick = onOpenTerms
                    )

                    AboutDivider()

                    AboutNavRow(
                        icon = "📜",
                        iconBg = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        title = "Open Source Licenses",
                        subtitle = "Compose, Jetpack, Firebase SDKs",
                        onClick = onOpenLicenses
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Diagnostic Copy Button
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val diagnosticInfo = """
                                Pantry Tracker $versionName (Build $buildNumber)
                                Android API: ${android.os.Build.VERSION.SDK_INT}
                                Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                            """.trimIndent()
                            clipboardManager.setText(AnnotatedString(diagnosticInfo))
                            Toast.makeText(context, "Diagnostic info copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.70f),
                    border = BorderStroke(1.2.dp, Color(0xFF334155))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Copy Diagnostic Build Info",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Copyright & Encryption Note
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "© 2026 Pantry Tracker Inc.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "All household data remains encrypted on-device.",
                        fontSize = 10.sp,
                        color = Color(0xFF475569)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Auxiliary Components
// -------------------------------------------------------------------------

@Composable
private fun AboutGroupContainer(content: @Composable ColumnScope.() -> Unit) {
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
                        colors = listOf(
                            Color(0xFF1E293B).copy(alpha = 0.80f),
                            Color(0xFF0F172A).copy(alpha = 0.90f)
                        )
                    )
                )
        ) {
            content()
        }
    }
}

@Composable
private fun AboutTelemetryRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    trailingTag: String,
    trailingTagColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
        }
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
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = trailingTagColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, trailingTagColor.copy(alpha = 0.35f))
        ) {
            Text(
                text = trailingTag,
                color = trailingTagColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AboutNavRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
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
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 16.sp)
        }
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
        Text(text = "▸", color = Color(0xFF64748B), fontSize = 16.sp)
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = Color(0xFF334155)
    )
}
