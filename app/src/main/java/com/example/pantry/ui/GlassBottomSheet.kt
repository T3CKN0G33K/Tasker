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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    shape: Shape = RoundedCornerShape(28.dp),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val dimColor = Color.Black.copy(alpha = 0.55f)

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

            Column(
                modifier = modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .svgCardContainer(cornerRadius = 28.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
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
                    LocalTextStyle provides TextStyle(color = Color.White)
                ) {
                    if (content != null) {
                        content()
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
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .svgCardContainer(cornerRadius = 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(color = Color.White)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}