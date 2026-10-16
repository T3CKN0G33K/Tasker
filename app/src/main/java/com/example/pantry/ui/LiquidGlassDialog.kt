package com.example.pantry.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    backdrop: Backdrop? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    shape: Shape = RoundedCornerShape(32.dp),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    GlassBottomSheet(
        onDismissRequest = onDismissRequest,
        backdrop = backdrop,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        isDarkMode = isDarkMode,
        themePreference = themePreference,
        shape = shape,
        content = content?.let { c -> { _ -> c() } }
    )
}
