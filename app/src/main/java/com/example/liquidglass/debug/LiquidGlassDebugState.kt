package com.example.liquidglass.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

class LiquidGlassDebugState {
    // Droplet Spring Physics
    var stiffness by mutableFloatStateOf(300f) // Spring.StiffnessMediumLow ~ 400f
    var dampingRatio by mutableFloatStateOf(0.75f) // Spring.DampingRatioMediumBouncy ~ 0.75f

    // Shader & Lens Parameters
    var refractionHeightPx by mutableFloatStateOf(24f)
    var refractionAmountPx by mutableFloatStateOf(-24f)
    var dispersionStrength by mutableFloatStateOf(0.04f)
    var specularAlpha by mutableFloatStateOf(0.40f)
    var dropletTintAlpha by mutableFloatStateOf(0.15f)
}
