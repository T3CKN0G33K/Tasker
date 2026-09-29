package com.example.liquidglass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiquidGlassListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    preset: ChromaticDispersionPreset = ChromaticDispersionPresets.Subdued,
    isDarkMode: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val containerBg = if (isDarkMode) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.70f)
    }

    val containerBorder = if (isDarkMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.12f)
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

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(clickableModifier)
    ) {
        // LAYER 1: Opaque Safe Glass Backdrop
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    try {
                        if (size.width > 0f && size.height > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val androidBlur = RenderEffect.createBlurEffect(
                                (18f * density).coerceAtLeast(1f),
                                (18f * density).coerceAtLeast(1f),
                                Shader.TileMode.CLAMP
                            )
                            renderEffect = androidBlur.asComposeRenderEffect()
                        }
                    } catch (_: Exception) {}
                }
                .background(containerBg)
                .border(
                    width = 1.dp,
                    brush = containerBorder,
                    shape = shape
                )
        )

        // LAYER 2: Crisp Content Layer (100% sharp text & icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (isDarkMode) Color.White else Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = if (isDarkMode) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.60f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}
