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
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT"
) {
    val dialogBg = when (themePreference.uppercase()) {
        "PURPLE" -> if (isDarkMode) {
            Color(0xFF532873).copy(alpha = 0.90f) // Translucent deep purple glass
        } else {
            Color(0xFF8B53AF).copy(alpha = 0.92f)
        }
        "CYAN" -> if (isDarkMode) {
            Color(0xFF006978).copy(alpha = 0.90f) // Translucent deep cyan glass
        } else {
            Color(0xFF26C6DA).copy(alpha = 0.92f)
        }
        else -> if (isDarkMode) {
            Color(0xFF1C1C1E).copy(alpha = 0.88f)
        } else {
            Color.White.copy(alpha = 0.92f)
        }
    }

    val dialogBorder = Brush.verticalGradient(
        colors = when (themePreference.uppercase()) {
            "PURPLE" -> listOf(
                Color(0xFFD8B4FE).copy(alpha = 0.65f),
                Color(0xFF9E6BC2).copy(alpha = 0.35f)
            )
            "CYAN" -> listOf(
                Color(0xFFB2EBF2).copy(alpha = 0.65f),
                Color(0xFF0097A9).copy(alpha = 0.35f)
            )
            else -> if (isDarkMode) {
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
