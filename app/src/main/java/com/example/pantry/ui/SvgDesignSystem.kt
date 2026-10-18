package com.example.pantry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pantry.ui.theme.LocalAppColors

// Verified Solid-Vector Palette Extracted from SVGs
val SvgCanvasBackground = Color(0xFF090D16)
val SvgCardSurface = Color(0xFF0F172A)
val SvgInputSurface = Color(0xFF1E293B)
val SvgPrimaryBorder = Color(0xFF38BDF8).copy(alpha = 0.25f)
val SvgSubtleBorder = Color.White.copy(alpha = 0.08f)
val SvgAccentBlue = Color(0xFF007AFF)
val SvgAccentLightBlue = Color(0xFF0284C7)
val SvgRestockYellow = Color(0xFFFBBF24)

fun Modifier.svgSurface(
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color? = null,
    borderColor: Color? = null
): Modifier = composed {
    val themeColors = LocalAppColors.current
    val finalBg = backgroundColor ?: themeColors.cardSurface
    val finalBorder = borderColor ?: themeColors.cardBorder
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(finalBg)
        .border(1.dp, finalBorder, RoundedCornerShape(cornerRadius))
}

fun Modifier.svgCardContainer(
    cornerRadius: Dp = 28.dp,
    backgroundColor: Color? = null,
    borderColor: Color? = null
): Modifier = svgSurface(cornerRadius, backgroundColor, borderColor)
