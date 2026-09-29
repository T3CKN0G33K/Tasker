package com.example.pantry.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val rootDecorView = remember(activity) {
        activity?.window?.decorView
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.40f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(5.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.40f)
            ) {}
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            // LAYER 1: True Clear Liquid Glass Backdrop via Native NDK LiquidGlassView (Lens 2.0 Reference)
            AndroidView(
                factory = { ctx ->
                    val density = ctx.resources.displayMetrics.density
                    LiquidGlassView(ctx).apply {
                        cornerRadiusTopLeft = 28f * density
                        cornerRadiusTopRight = 28f * density
                        cornerRadiusBottomLeft = 0f
                        cornerRadiusBottomRight = 0f

                        // Exact Optics & Refraction Parameters (Lens 2.0 Reference):
                        bevelWidth = 24f
                        refractionHeight = 150f
                        refractionFalloff = 2.0f
                        dispersionStrength = 0.10f
                        adaptiveLensScale = true
                        material = GlassMaterial.REGULAR
                        glassTint = android.graphics.Color.TRANSPARENT // 0x00000000

                        // Blur & Specular Rim:
                        blurAmount = 8.0f
                        saturation = 1.4f // 140%
                        enableEdgeHighlight = true
                        edgeHighlightBorderWidth = 1.5f
                        edgeHighlightOpacity = 100f

                        // Backdrop Sourcing for Separate Modal Window:
                        rootDecorView?.let { sourceView ->
                            backdropSource = sourceView
                        }
                    }
                },
                update = { glass ->
                    rootDecorView?.let { sourceView ->
                        if (glass.backdropSource != sourceView) {
                            glass.backdropSource = sourceView
                        }
                    }
                    glass.invalidate()
                },
                modifier = Modifier.matchParentSize()
            )

            // LAYER 2: Crisp Content Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                content = content
            )
        }
    }
}
