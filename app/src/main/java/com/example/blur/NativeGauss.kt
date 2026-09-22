package com.example.blur

import android.graphics.Bitmap

object NativeGauss {

    private val neonSupported: Boolean by lazy {
        try {
            hasNeonSupport()
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    init {
        try {
            System.loadLibrary("nativegauss")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    external fun hasNeonSupport(): Boolean

    external fun gaussianIIRInplace(
        bitmap: Bitmap,
        sigma: Float,
        linear: Boolean = false
    )

    external fun gaussianIIRNeonInplace(
        bitmap: Bitmap,
        sigma: Float,
        linear: Boolean = false
    )

    external fun box3Inplace(
        bitmap: Bitmap,
        radius: Int
    )

    external fun advancedBoxBlurInplace(
        bitmap: Bitmap,
        radius: Float,
        downscale: Float = 0.5f
    )

    external fun advancedBoxBlurInplaceHQ(
        bitmap: Bitmap,
        radius: Float,
        downscale: Float = 0.5f
    )

    fun radiusToSigma(radius: Int): Float {
        return radius / 3.0f
    }

    fun sigmaToRadius(sigma: Float): Int {
        return (sigma * 3.0f).toInt()
    }

    fun smartBlur(bitmap: Bitmap, sigma: Float, highQuality: Boolean = false) {
        val pixels = bitmap.width * bitmap.height
        if (pixels < 64 * 64 && sigma < 8.0f) {
            val radius = (sigma * 1.2f).toInt().coerceAtLeast(1)
            box3Inplace(bitmap, radius)
        } else {
            if (neonSupported) {
                gaussianIIRNeonInplace(bitmap, sigma, highQuality)
            } else {
                gaussianIIRInplace(bitmap, sigma, highQuality)
            }
        }
    }

    fun downsampleBlur(
        bitmap: Bitmap,
        sigma: Float,
        scale: Int = 2,
        highQuality: Boolean = false
    ): Bitmap {
        require(scale in 2..3) { "Scale must be 2 or 3" }

        val smallW = bitmap.width / scale
        val smallH = bitmap.height / scale

        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        val adjustedSigma = sigma / scale
        if (neonSupported) {
            gaussianIIRNeonInplace(small, adjustedSigma, highQuality)
        } else {
            gaussianIIRInplace(small, adjustedSigma, highQuality)
        }

        val result = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
        small.recycle()

        return result
    }
}
