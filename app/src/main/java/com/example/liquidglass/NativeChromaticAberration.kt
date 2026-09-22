package com.example.liquidglass

import android.graphics.Bitmap

object NativeChromaticAberration {

    init {
        try {
            System.loadLibrary("nativegauss")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    external fun chromaticAberrationInplace(
        source: Bitmap,
        displacement: Bitmap,
        result: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f,
        useBilinear: Boolean = true
    )

    fun apply(
        source: Bitmap,
        displacement: Bitmap,
        intensity: Float = 2.0f,
        scale: Float = 70.0f,
        redOffset: Float = 0.0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f,
        useBilinear: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        chromaticAberrationInplace(
            source,
            displacement,
            result,
            intensity,
            scale,
            redOffset,
            greenOffset,
            blueOffset,
            useBilinear
        )

        return result
    }
}
