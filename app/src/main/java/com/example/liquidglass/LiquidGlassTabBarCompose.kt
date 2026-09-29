package com.example.liquidglass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liquidglass.debug.LiquidGlassDebugState
import com.example.pantry.ui.refractionLens

data class LiquidGlassTabItem(
    val id: Int,
    val title: String,
    val icon: ImageVector
)

@Composable
fun LiquidGlassTabBarCompose(
    tabs: List<LiquidGlassTabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    preset: ChromaticDispersionPreset = ChromaticDispersionPresets.Standard,
    debugState: LiquidGlassDebugState? = null
) {
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val innerPaddingPx = with(LocalDensity.current) { 4.dp.toPx() }
    val availableWidth = (containerWidth - 2 * innerPaddingPx).coerceAtLeast(0f)
    val tabWidth = if (availableWidth > 0f && tabs.isNotEmpty()) availableWidth / tabs.size else 0f

    val activeIndex = if (isDragging && tabWidth > 0f) {
        ((dragOffsetX - innerPaddingPx) / tabWidth).toInt().coerceIn(0, tabs.size - 1)
    } else {
        selectedTab
    }

    val maxOffset = if (tabs.isNotEmpty()) (tabs.size - 1) * tabWidth else 0f

    val targetOffset = if (isDragging && tabWidth > 0f) {
        (dragOffsetX - innerPaddingPx - (tabWidth / 2f)).coerceIn(0f, maxOffset)
    } else if (tabWidth > 0f) {
        selectedTab * tabWidth
    } else {
        0f
    }

    val springStiffness = debugState?.stiffness ?: Spring.StiffnessMediumLow
    val springDamping = debugState?.dampingRatio ?: Spring.DampingRatioMediumBouncy

    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = springDamping, stiffness = springStiffness)
        },
        label = "LiquidGlassDropletTab"
    )

    val isPurpleTheme = themePreference.equals("PURPLE", ignoreCase = true)

    val activeAccentColor = if (isPurpleTheme) {
        Color(0xFFC084FC) // Electric Violet / Amethyst
    } else {
        Color(0xFF38BDF8) // Glowing Electric Cyan / Blue
    }

    val tintAlpha = debugState?.dropletTintAlpha ?: 0.35f

    val dropletBgBrush = if (isPurpleTheme) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFA855F7).copy(alpha = tintAlpha),
                Color(0x33C084FC),
                Color(0x18FFFFFF)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF06B6D4).copy(alpha = tintAlpha),
                Color(0x3338BDF8),
                Color(0x18FFFFFF)
            )
        )
    }

    val specularAlpha = debugState?.specularAlpha ?: 0.60f

    val dropletBorderBrush = if (isPurpleTheme) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFC084FC),
                Color.White.copy(alpha = specularAlpha)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF38BDF8),
                Color.White.copy(alpha = specularAlpha)
            )
        )
    }

    val containerBorderBrush = if (isPurpleTheme) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.12f),
                Color(0xFFC084FC).copy(alpha = 0.30f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.12f),
                Color(0xFF06B6D4).copy(alpha = 0.30f)
            )
        )
    }

    val refractionHeight = debugState?.refractionHeightPx ?: preset.refractionHeightPx
    val refractionAmount = debugState?.refractionAmountPx ?: preset.refractionAmountPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(64.dp)
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .clip(RoundedCornerShape(32.dp))
            .background(
                color = if (isDarkMode) Color(0x1A000000).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.70f)
            )
            .border(
                width = 1.2.dp,
                brush = containerBorderBrush,
                shape = RoundedCornerShape(32.dp)
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragOffsetX = offset.x
                    },
                    onDragEnd = {
                        if (isDragging && tabWidth > 0f) {
                            val finalIndex = ((dragOffsetX - innerPaddingPx) / tabWidth).toInt().coerceIn(0, tabs.size - 1)
                            onTabSelected(finalIndex)
                        }
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(innerPaddingPx, containerWidth - innerPaddingPx)
                    }
                )
            }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // LAYER 1: Sliding Liquid Glass Droplet Lens (Refraction Lens shader with 0f chromatic aberration!)
        if (containerWidth > 0f && tabWidth > 0f) {
            Box(
                modifier = Modifier
                    .offset(x = with(LocalDensity.current) { animatedOffset.toDp() })
                    .width(with(LocalDensity.current) { tabWidth.toDp() })
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .refractionLens(
                        refractionHeightPx = refractionHeight,
                        refractionAmountPx = refractionAmount,
                        chromaticAberration = 0f // Pure glass refraction with zero rainbow fringe!
                    )
                    .background(dropletBgBrush)
                    .border(
                        width = 1.dp,
                        brush = dropletBorderBrush,
                        shape = RoundedCornerShape(26.dp)
                    )
            )
        }

        // LAYER 2: Higher Z-Index Tab Icons & Labels (Taps register 100% cleanly!)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == activeIndex
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isDragging = false
                            onTabSelected(tab.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) {
                                activeAccentColor
                            } else if (isDarkMode) {
                                Color.White.copy(alpha = 0.55f)
                            } else {
                                Color.Black.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) {
                                if (isDarkMode) Color.White else Color.Black
                            } else if (isDarkMode) {
                                Color.White.copy(alpha = 0.55f)
                            } else {
                                Color.Black.copy(alpha = 0.55f)
                            },
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
