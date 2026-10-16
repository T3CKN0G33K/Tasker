package com.example.pantry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    shape: Shape = RoundedCornerShape(32.dp),
    content: (@Composable ColumnScope.(exportedBackdrop: Backdrop) -> Unit)? = null
) {
    val currentBackdrop = backdrop ?: rememberLayerBackdrop()
    val bottomSheetBackdrop = rememberLayerBackdrop()
    val isLightTheme = !isDarkMode
    val containerColor = if (isLightTheme) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.12f)
    val dimColor = if (isLightTheme) Color(0xFF29293A).copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.45f)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Scrim / Dim Background Area (Tapping outside dismisses sheet)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest
                    )
            )

            // Liquid Glass Bottom Sheet Container
            Column(
                modifier = modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept clicks inside the sheet body
                    )
                    .drawBackdrop(
                        backdrop = currentBackdrop,
                        shape = { shape },
                        effects = {
                            colorControls(
                                brightness = if (isDarkMode) 0f else 0.15f,
                                saturation = 1.3f
                            )
                            vibrancy()
                            blur(12.dp.toPx())
                            lens(
                                refractionHeight = 20.dp.toPx(),
                                refractionAmount = 36.dp.toPx(),
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight(width = 1.2.dp, alpha = 0.45f) },
                        shadow = { Shadow(radius = 20.dp, alpha = 0.30f, offset = DpOffset(0.dp, 8.dp)) },
                        innerShadow = { InnerShadow(radius = 10.dp, alpha = 0.22f) },
                        exportedBackdrop = bottomSheetBackdrop,
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Drag Handle Accent Pill
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                        .align(Alignment.CenterHorizontally)
                )

                CompositionLocalProvider(
                    LocalTextStyle provides TextStyle(
                        color = Color.White,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.60f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    )
                ) {
                    if (content != null) {
                        content(bottomSheetBackdrop)
                    } else {
                        if (title != null) {
                            title()
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (text != null) {
                            text()
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        if (confirmButton != null || dismissButton != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (dismissButton != null) {
                                    dismissButton()
                                }
                                if (confirmButton != null) {
                                    confirmButton()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isDarkMode
    val containerColor = if (isLightTheme) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(20.dp.toPx(), 36.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.50f) },
                shadow = { Shadow(alpha = 0.30f, radius = 12.dp) },
                innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.20f) },
                onDrawSurface = { drawRect(containerColor) }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f
                )
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
