package com.example.pantry.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    isDarkMode: Boolean = true
) {
    val dialogBg = if (isDarkMode) {
        Color(0xFF1C1C1E).copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.90f)
    }

    val dialogBorder = Brush.verticalGradient(
        colors = if (isDarkMode) {
            listOf(
                Color.White.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.15f)
            )
        } else {
            listOf(
                Color.White,
                Color.White.copy(alpha = 0.60f)
            )
        }
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = dialogBg,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = dialogBorder,
                shape = RoundedCornerShape(24.dp)
            ),
        title = title,
        text = text,
        confirmButton = {
            confirmButton?.invoke()
        },
        dismissButton = dismissButton?.let {
            { it.invoke() }
        }
    )
}
