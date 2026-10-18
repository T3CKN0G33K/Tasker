package com.example.pantry.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.svgSurface(
    cornerRadius: Dp = 24.dp
): Modifier = composed {
    val colors = LocalAppColors.current
    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(colors.cardSurface)
        .border(1.dp, colors.cardBorder, RoundedCornerShape(cornerRadius))
}
