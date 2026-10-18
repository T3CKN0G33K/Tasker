package com.example.pantry.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppThemeColors(
    val isSeasonal: Boolean,
    val background: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val fabColor: Color
)

// Default Solid Obsidian Palette
val DefaultObsidianColors = AppThemeColors(
    isSeasonal = false,
    background = Color(0xFF090D16),
    cardSurface = Color(0xFF0F172A),
    cardBorder = Color(0xFF38BDF8).copy(alpha = 0.20f),
    primaryAccent = Color(0xFF007AFF),
    secondaryAccent = Color(0xFFFBBF24),
    textPrimary = Color.White,
    textSecondary = Color(0xFF94A3B8),
    fabColor = Color(0xFF007AFF)
)

// Spooky Halloween Palette
val HalloweenColors = AppThemeColors(
    isSeasonal = true,
    background = Color(0xFF0F0716),                     // Midnight Purple Canvas
    cardSurface = Color(0xFF1E112A),                    // Dark Violet Obsidian Card
    cardBorder = Color(0xFFF97316),                     // Jack-O'-Lantern Orange
    primaryAccent = Color(0xFFF97316),                  // Orange Accent
    secondaryAccent = Color(0xFFA855F7),                // Neon Purple Accent
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFC084FC),
    fabColor = Color(0xFFF97316)                         // Pumpkin Orange FAB
)

// Harvest Thanksgiving Palette
val ThanksgivingColors = AppThemeColors(
    isSeasonal = true,
    background = Color(0xFF140D07),                     // Deep roasted espresso
    cardSurface = Color(0xFF24160C),                    // Warm rustic amber-bark
    cardBorder = Color(0xFFD97706),                     // Harvest burnt amber
    primaryAccent = Color(0xFFD97706),                  // Amber gold
    secondaryAccent = Color(0xFFEA580C),                // Spiced pumpkin orange
    textPrimary = Color(0xFFFEF3C7),
    textSecondary = Color(0xFFD4A373),
    fabColor = Color(0xFFD97706)                        // Harvest Amber FAB
)

// Festive Christmas Palette
val ChristmasColors = AppThemeColors(
    isSeasonal = true,
    background = Color(0xFF06140D),                     // Deep evergreen black
    cardSurface = Color(0xFF0E2418),                    // Pine needle dark green
    cardBorder = Color(0xFFDC2626),                     // Holiday crimson red
    primaryAccent = Color(0xFFDC2626),                  // Berry red
    secondaryAccent = Color(0xFF16A34A),                // Emerald wreath green
    textPrimary = Color(0xFFF0FDF4),
    textSecondary = Color(0xFF86EFAC),
    fabColor = Color(0xFFDC2626)                        // Crimson Red FAB
)

// New Year Frost & Gold Palette
val NewYearColors = AppThemeColors(
    isSeasonal = true,
    background = Color(0xFF080B14),                     // Midnight arctic blue
    cardSurface = Color(0xFF10172A),                    // Glacial dark navy
    cardBorder = Color(0xFFEAB308),                     // Champagne metallic gold
    primaryAccent = Color(0xFFEAB308),                  // Gold accent
    secondaryAccent = Color(0xFF38BDF8),                // Frost cyan
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF93C5FD),
    fabColor = Color(0xFFEAB308)                        // Champagne Gold FAB
)

val LocalAppColors = compositionLocalOf { DefaultObsidianColors }

fun Modifier.themedCard(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val colors = LocalAppColors.current
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(colors.cardSurface)
        .border(borderWidth, colors.cardBorder, RoundedCornerShape(cornerRadius))
}

@Composable
fun TaskerTheme(
    colors: AppThemeColors = DefaultObsidianColors,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppColors provides colors) {
        content()
    }
}
