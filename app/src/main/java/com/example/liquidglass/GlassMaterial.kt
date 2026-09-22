package com.example.liquidglass

enum class GlassMaterial(
    val blurScale: Float,
    val dimAmount: Float,
    val adaptiveTint: Boolean,
    val baseTint: Int,
    val specBoost: Float
) {
    REGULAR(
        blurScale = 1.0f,
        dimAmount = 0.0f,
        adaptiveTint = true,
        baseTint = 0x24FFFFFF,
        specBoost = 1.0f
    ),

    CLEAR(
        blurScale = 0.35f,
        dimAmount = 0.16f,
        adaptiveTint = false,
        baseTint = 0x14FFFFFF,
        specBoost = 1.2f
    )
}
