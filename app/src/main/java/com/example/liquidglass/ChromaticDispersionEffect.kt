package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.Log
import kotlin.math.min

class ChromaticDispersionEffect {

    companion object {
        private const val TAG = "ChromaticDispersion"
    }

    var useBilinearInterpolation: Boolean = true
    var devicePixelRatio: Float = 1.0f

    private var cachedEdgeMap: Bitmap? = null
    private var cachedNormalMap: Bitmap? = null
    private var cachedSize = Pair(0, 0)

    fun apply(
        source: Bitmap,
        refThickness: Float = 100f,
        refFactor: Float = 1.5f,
        refDispersion: Float = 7f,
        downscale: Float = 0.5f,
        cornerRadius: Float = 0f,
        useNormalMap: Boolean = false,
        cornerRadii: FloatArray? = null
    ): Bitmap {
        val processWidth = (source.width * downscale).toInt().coerceAtLeast(1)
        val processHeight = (source.height * downscale).toInt().coerceAtLeast(1)

        val smallSource = if (downscale < 1.0f) {
            Bitmap.createScaledBitmap(source, processWidth, processHeight, true)
        } else {
            source
        }

        val maxRadius = min(processWidth, processHeight) / 2f
        val clippedSource = if (cornerRadii != null) {
            applyRoundedCornerClip(smallSource, FloatArray(8) { min(cornerRadii[it] * downscale, maxRadius) })
        } else if (cornerRadius > 0) {
            val scaledRadius = cornerRadius * downscale
            val clampedRadius = min(scaledRadius, maxRadius)
            applyRoundedCornerClip(smallSource, clampedRadius)
        } else {
            smallSource
        }

        val edgeMap = if (cachedEdgeMap != null && cachedSize == Pair(processWidth, processHeight)) {
            cachedEdgeMap!!
        } else {
            val map = DispersionMapGenerator.generateEdgeDistanceMapFromAlpha(clippedSource)
            cachedEdgeMap = map
            cachedSize = Pair(processWidth, processHeight)
            map
        }

        val normalMap = if (useNormalMap) {
            if (cachedNormalMap != null && cachedSize == Pair(processWidth, processHeight)) {
                cachedNormalMap!!
            } else {
                val map = DispersionMapGenerator.generateRadialNormalMap(processWidth, processHeight)
                cachedNormalMap = map
                map
            }
        } else {
            null
        }

        val result = Bitmap.createBitmap(processWidth, processHeight, Bitmap.Config.ARGB_8888)

        NativeChromaticDispersion.chromaticDispersionInplace(
            source = smallSource,
            edgeDistance = edgeMap,
            normalMap = normalMap,
            result = result,
            refThickness = refThickness * downscale,
            refFactor = refFactor,
            refDispersion = refDispersion,
            dpr = devicePixelRatio,
            useBilinear = useBilinearInterpolation
        )

        val finalResult = if (downscale < 1.0f) {
            val upscaled = Bitmap.createScaledBitmap(result, source.width, source.height, true)
            result.recycle()
            upscaled
        } else {
            result
        }

        if (downscale < 1.0f && smallSource != source) {
            smallSource.recycle()
        }

        return finalResult
    }

    private fun applyRoundedCornerClip(source: Bitmap, cornerRadius: Float): Bitmap =
        applyRoundedCornerClip(source, FloatArray(8) { cornerRadius })

    private fun applyRoundedCornerClip(source: Bitmap, radii: FloatArray): Bitmap {
        val width = source.width
        val height = source.height

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val path = Path()
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        path.addRoundRect(rect, radii, Path.Direction.CW)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    fun clearCache() {
        cachedEdgeMap?.recycle()
        cachedEdgeMap = null
        cachedNormalMap?.recycle()
        cachedNormalMap = null
        cachedSize = Pair(0, 0)
    }
}
