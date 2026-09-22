package com.example.liquidglass

import android.graphics.*
import android.util.Log
import android.view.View
import com.example.blur.NativeGauss

class EnhancedBlurEffect(
    private val view: View
) {
    companion object {
        private const val TAG = "EnhancedBlurEffect"
        private const val RADIUS_TO_SIGMA = 0.33f
    }

    private val fastBlur = AdvancedFastBlur()

    var blurMethod = BlurMethod.SMART
    var highQuality = false

    var downsampleScale = 2
        set(value) {
            field = value.coerceIn(2, 3)
        }

    var enableOptimizedCapture = false
    var cornerRadius = 0f
    var cornerRadii: FloatArray? = null
    var captureMargin = 0f

    private val neonSupported: Boolean by lazy {
        try {
            NativeGauss.hasNeonSupport()
        } catch (e: Exception) {
            Log.w(TAG, "NEON support check failed: ${e.message}")
            false
        }
    }

    fun captureBackdrop(bounds: RectF, downsample: Float = 1f): Bitmap? {
        val parent = (view as? LiquidGlassView)?.backdropView
            ?: view.parent as? View
            ?: return null

        val captureBounds = if (enableOptimizedCapture && cornerRadius > 0) {
            calculateOptimizedBounds(bounds)
        } else {
            bounds
        }

        val scale = downsample.coerceIn(0.01f, 1f)
        val width = (captureBounds.width() * scale).toInt().coerceAtLeast(1)
        val height = (captureBounds.height() * scale).toInt().coerceAtLeast(1)
        val backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(backdrop)

        try {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val parentLocation = IntArray(2)
            parent.getLocationOnScreen(parentLocation)

            val offsetX = (location[0] - parentLocation[0]).toFloat()
            val offsetY = (location[1] - parentLocation[1]).toFloat()

            if (scale < 1f) {
                canvas.scale(scale, scale)
            }
            canvas.translate(-offsetX - captureBounds.left, -offsetY - captureBounds.top)

            if (enableOptimizedCapture && cornerRadius > 0) {
                val clipPath = Path()
                val clipRect = RectF(
                    offsetX + captureBounds.left,
                    offsetY + captureBounds.top,
                    offsetX + captureBounds.right,
                    offsetY + captureBounds.bottom
                )
                val radii = cornerRadii
                if (radii != null) {
                    clipPath.addRoundRect(clipRect, radii, Path.Direction.CW)
                } else {
                    clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
            }

            val glassView = view as? LiquidGlassView
            if (glassView != null) {
                glassView.isCapturingBackdrop = true
                LiquidGlassView.isGlobalCapturingBackdrop = true
                try {
                    parent.draw(canvas)
                } finally {
                    LiquidGlassView.isGlobalCapturingBackdrop = false
                    glassView.isCapturingBackdrop = false
                }
            } else {
                val wasVisible = view.visibility
                view.visibility = View.INVISIBLE
                parent.draw(canvas)
                view.visibility = wasVisible
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture backdrop: ${e.message}")
            canvas.drawColor(Color.argb(200, 255, 255, 255))
        }

        return backdrop
    }

    private fun calculateOptimizedBounds(bounds: RectF): RectF {
        val margin = captureMargin.coerceAtLeast(0f)
        return RectF(
            (bounds.left - margin).coerceAtLeast(0f),
            (bounds.top - margin).coerceAtLeast(0f),
            bounds.right + margin,
            bounds.bottom + margin
        )
    }

    fun applyEffect(
        backdrop: Bitmap,
        blurRadius: Float
    ): Bitmap {
        return if (blurRadius > 0f) {
            applyBlur(backdrop, blurRadius)
        } else {
            backdrop
        }
    }

    private fun applyBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val clampedRadius = radius.coerceIn(0f, 25f)
        val sigma = clampedRadius * RADIUS_TO_SIGMA

        return when (blurMethod) {
            BlurMethod.BOX_BLUR -> applyBoxBlur(bitmap, clampedRadius)
            BlurMethod.BOX_BLUR_CPP -> applyBoxBlurCpp(bitmap, clampedRadius)
            BlurMethod.IIR_GAUSSIAN -> applyIIRGaussian(bitmap, sigma)
            BlurMethod.IIR_GAUSSIAN_NEON -> applyIIRGaussianNeon(bitmap, sigma)
            BlurMethod.BOX3 -> applyBox3(bitmap, sigma)
            BlurMethod.SMART -> applySmartBlur(bitmap, sigma)
            BlurMethod.DOWNSAMPLE -> applyDownsampleBlur(bitmap, sigma)
        }
    }

    private fun applyBoxBlur(bitmap: Bitmap, radius: Float): Bitmap {
        return fastBlur.blur(
            bitmap = bitmap,
            radius = radius,
            downscale = 0.5f
        )
    }

    private fun applyBoxBlurCpp(bitmap: Bitmap, radius: Float): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        try {
            NativeGauss.advancedBoxBlurInplace(
                bitmap = mutableBitmap,
                radius = radius,
                downscale = 0.5f
            )
        } catch (e: Exception) {
            Log.e(TAG, "C++ Box Blur failed: ${e.message}")
            return applyBoxBlur(bitmap, radius)
        }
        return mutableBitmap
    }

    private fun applyIIRGaussian(bitmap: Bitmap, sigma: Float): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        try {
            NativeGauss.gaussianIIRInplace(mutableBitmap, sigma, highQuality)
        } catch (e: Exception) {
            Log.e(TAG, "IIR Gaussian blur failed: ${e.message}")
            return applyBoxBlur(bitmap, sigma * 3f)
        }
        return mutableBitmap
    }

    private fun applyIIRGaussianNeon(bitmap: Bitmap, sigma: Float): Bitmap {
        if (!neonSupported) {
            Log.w(TAG, "NEON not supported, fallback to scalar IIR")
            return applyIIRGaussian(bitmap, sigma)
        }
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        try {
            NativeGauss.gaussianIIRNeonInplace(mutableBitmap, sigma, highQuality)
        } catch (e: Exception) {
            Log.e(TAG, "IIR Gaussian NEON blur failed: ${e.message}")
            return applyIIRGaussian(bitmap, sigma)
        }
        return mutableBitmap
    }

    private fun applyBox3(bitmap: Bitmap, sigma: Float): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val radius = (sigma * 1.2f).toInt().coerceAtLeast(1)
        try {
            NativeGauss.box3Inplace(mutableBitmap, radius)
        } catch (e: Exception) {
            Log.e(TAG, "Box3 blur failed: ${e.message}")
            return applyBoxBlur(bitmap, sigma * 3f)
        }
        return mutableBitmap
    }

    private fun applySmartBlur(bitmap: Bitmap, sigma: Float): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        try {
            NativeGauss.smartBlur(mutableBitmap, sigma, highQuality)
        } catch (e: Exception) {
            Log.e(TAG, "Smart blur failed: ${e.message}")
            return applyBoxBlur(bitmap, sigma * 3f)
        }
        return mutableBitmap
    }

    private fun applyDownsampleBlur(bitmap: Bitmap, sigma: Float): Bitmap {
        try {
            return NativeGauss.downsampleBlur(bitmap, sigma, downsampleScale, highQuality)
        } catch (e: Exception) {
            Log.e(TAG, "Downsample blur failed: ${e.message}")
            return applySmartBlur(bitmap, sigma)
        }
    }

    fun release() {
        fastBlur.cleanup()
    }
}
