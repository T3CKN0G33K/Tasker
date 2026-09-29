package com.example.liquidglass.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantry.ui.GlassCard
import java.util.Locale

@Composable
fun LiquidGlassDebugOverlay(
    state: LiquidGlassDebugState,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (!isExpanded) {
            // Floating trigger badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF).copy(alpha = 0.85f))
                    .clickable { isExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Open Glass Debugger",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GlassCard(
                isDarkMode = true,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Liquid Glass Tuner",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    val summary = """
                                        // Baked Liquid Glass Preset
                                        stiffness = ${state.stiffness}f
                                        dampingRatio = ${state.dampingRatio}f
                                        refractionHeightPx = ${state.refractionHeightPx}f
                                        refractionAmountPx = ${state.refractionAmountPx}f
                                        dispersionStrength = ${state.dispersionStrength}f
                                        specularAlpha = ${state.specularAlpha}f
                                        dropletTintAlpha = ${state.dropletTintAlpha}f
                                    """.trimIndent()
                                    clipboardManager.setText(AnnotatedString(summary))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Config",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Physics Sliders
                    DebugSlider(
                        label = "Spring Stiffness",
                        value = state.stiffness,
                        range = 50f..1000f,
                        onValueChange = { state.stiffness = it }
                    )

                    DebugSlider(
                        label = "Damping Ratio",
                        value = state.dampingRatio,
                        range = 0.2f..1.2f,
                        onValueChange = { state.dampingRatio = it }
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.5.dp)

                    // Shader & Optics Sliders
                    DebugSlider(
                        label = "Refraction Height",
                        value = state.refractionHeightPx,
                        range = 0f..60f,
                        onValueChange = { state.refractionHeightPx = it }
                    )

                    DebugSlider(
                        label = "Refraction Amount",
                        value = state.refractionAmountPx,
                        range = -60f..0f,
                        onValueChange = { state.refractionAmountPx = it }
                    )

                    DebugSlider(
                        label = "Dispersion Strength",
                        value = state.dispersionStrength,
                        range = 0f..0.20f,
                        onValueChange = { state.dispersionStrength = it }
                    )

                    DebugSlider(
                        label = "Specular Rim Alpha",
                        value = state.specularAlpha,
                        range = 0.05f..0.90f,
                        onValueChange = { state.specularAlpha = it }
                    )

                    DebugSlider(
                        label = "Droplet Tint Alpha",
                        value = state.dropletTintAlpha,
                        range = 0.02f..0.50f,
                        onValueChange = { state.dropletTintAlpha = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Text(
                String.format(Locale.US, "%.2f", value),
                color = Color(0xFF007AFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF007AFF),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
