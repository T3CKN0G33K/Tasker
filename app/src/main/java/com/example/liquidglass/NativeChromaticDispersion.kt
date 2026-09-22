package com.example.liquidglass

import android.graphics.Bitmap

object NativeChromaticDispersion {

    init {
        try {
            System.loadLibrary("nativegauss")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    external fun chromaticDispersionInplace(
        source: Bitmap,
        edgeDistance: Bitmap,
        normalMap: Bitmap?,
        result: Bitmap,
        refThickness: Float,
        refFactor: Float,
        refDispersion: Float,
        dpr: Float,
        useBilinear: Boolean
    )

    fun apply(
        source: Bitmap,
        edgeDistance: Bitmap,
        normalMap: Bitmap? = null,
        refThickness: Float = 100f,
        refFactor: Float = 1.5f,
        refDispersion: Float = 7f,
        dpr: Float = 1.0f,
        useBilinear: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)

        chromaticDispersionInplace(
            source,
            edgeDistance,
            normalMap,
            result,
            refThickness,
            refFactor,
            refDispersion,
            dpr,
            useBilinear
        )

        return result
    }
}
