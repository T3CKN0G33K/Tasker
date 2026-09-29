package com.example.liquidglass

data class ChromaticDispersionPreset(
    val name: String,
    val refThickness: Float = 100f,
    val refFactor: Float = 1.5f,
    val refDispersion: Float = 0f,
    val chromaticAberration: Float = 0f,
    val refractionHeightPx: Float = 16f,
    val refractionAmountPx: Float = -14f,
    val edgeSoftness: Float = 4f
)

object ChromaticDispersionPresets {
    val Subdued = ChromaticDispersionPreset(
        name = "Subdued",
        refThickness = 40f,
        refFactor = 1.2f,
        refDispersion = 0f,
        chromaticAberration = 0f,
        refractionHeightPx = 12f,
        refractionAmountPx = -10f
    )

    val Standard = ChromaticDispersionPreset(
        name = "Standard",
        refThickness = 100f,
        refFactor = 1.5f,
        refDispersion = 0f,
        chromaticAberration = 0f,
        refractionHeightPx = 16f,
        refractionAmountPx = -14f
    )

    val Vibrant = ChromaticDispersionPreset(
        name = "Vibrant",
        refThickness = 150f,
        refFactor = 1.8f,
        refDispersion = 0f,
        chromaticAberration = 0f,
        refractionHeightPx = 20f,
        refractionAmountPx = -18f
    )

    val HighDispersion = ChromaticDispersionPreset(
        name = "High Dispersion",
        refThickness = 200f,
        refFactor = 2.1f,
        refDispersion = 0f,
        chromaticAberration = 0f,
        refractionHeightPx = 24f,
        refractionAmountPx = -22f
    )

    val Crystal = ChromaticDispersionPreset(
        name = "Crystal",
        refThickness = 120f,
        refFactor = 1.7f,
        refDispersion = 0f,
        chromaticAberration = 0f,
        refractionHeightPx = 18f,
        refractionAmountPx = -16f
    )

    val SoftGlass = ChromaticDispersionPreset(
        name = "Soft Glass",
        refThickness = 80f,
        refFactor = 1.3f,
        refDispersion = 0f,
        chromaticAberration = 0f,
        refractionHeightPx = 14f,
        refractionAmountPx = -12f
    )
}
