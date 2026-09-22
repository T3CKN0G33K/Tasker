package com.example.liquidglass

import android.graphics.*
import android.util.Log
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ChromaticAberrationEffect {

    enum class PerformanceMode {
        KOTLIN,
        CPP,
        AUTO
    }

    var performanceMode: PerformanceMode = PerformanceMode.AUTO
    var useBilinearInterpolation: Boolean = true

    private var cachedSourcePixels: IntArray? = null
    private var cachedMapPixels: IntArray? = null
    private var cachedResultPixels: IntArray? = null
    private var cachedSize = 0

    private val bitmapPool = BitmapPool.getInstance()
    
    fun apply(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float = 2f,
        scale: Float = 70f,
        downscale: Float = 0.5f,
        redOffset: Float = 0f,
        greenOffset: Float = -0.05f,
        blueOffset: Float = -0.1f
    ): Bitmap {
        return when (selectImplementation(source, downscale)) {
            Implementation.CPP -> applyCpp(
                source, displacementMap, intensity, scale,
                downscale, redOffset, greenOffset, blueOffset
            )
            Implementation.KOTLIN -> applyKotlin(
                source, displacementMap, intensity, scale,
                downscale, redOffset, greenOffset, blueOffset
            )
        }
    }

    private fun selectImplementation(source: Bitmap, downscale: Float): Implementation {
        return when (performanceMode) {
            PerformanceMode.KOTLIN -> Implementation.KOTLIN
            PerformanceMode.CPP -> Implementation.CPP
            PerformanceMode.AUTO -> {
                val pixels = source.width * source.height
                if (pixels > 256 * 256 || downscale >= 0.5f) {
                    Implementation.CPP
                } else {
                    Implementation.KOTLIN
                }
            }
        }
    }

    private fun applyCpp(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float,
        scale: Float,
        downscale: Float,
        redOffset: Float,
        greenOffset: Float,
        blueOffset: Float
    ): Bitmap {
        try {
            val originalWidth = source.width
            val originalHeight = source.height

            val processWidth = (originalWidth * downscale).toInt().coerceAtLeast(1)
            val processHeight = (originalHeight * downscale).toInt().coerceAtLeast(1)

            val smallSource = if (downscale < 1.0f) {
                val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(temp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val srcRect = Rect(0, 0, source.width, source.height)
                val dstRect = Rect(0, 0, processWidth, processHeight)
                canvas.drawBitmap(source, srcRect, dstRect, paint)
                temp
            } else {
                source.copy(Bitmap.Config.ARGB_8888, true) ?: source
            }

            val scaledMap = if (displacementMap.width != processWidth || displacementMap.height != processHeight) {
                val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(temp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val srcRect = Rect(0, 0, displacementMap.width, displacementMap.height)
                val dstRect = Rect(0, 0, processWidth, processHeight)
                canvas.drawBitmap(displacementMap, srcRect, dstRect, paint)
                temp
            } else {
                displacementMap.copy(Bitmap.Config.ARGB_8888, true) ?: displacementMap
            }

            val result = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)

            val adjustedScale = scale * downscale
            val adjustedRedOffset = redOffset * intensity * downscale
            val adjustedGreenOffset = greenOffset * intensity * downscale
            val adjustedBlueOffset = blueOffset * intensity * downscale

            NativeChromaticAberration.chromaticAberrationInplace(
                source = smallSource,
                displacement = scaledMap,
                result = result,
                intensity = intensity,
                scale = adjustedScale,
                redOffset = adjustedRedOffset,
                greenOffset = adjustedGreenOffset,
                blueOffset = adjustedBlueOffset,
                useBilinear = useBilinearInterpolation
            )

            if (scaledMap != displacementMap) {
                bitmapPool.put(scaledMap)
            }
            if (smallSource != source) {
                bitmapPool.put(smallSource)
            }

            val finalResult = if (downscale < 1.0f) {
                val upscaled = bitmapPool.get(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(upscaled)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val srcRect = Rect(0, 0, processWidth, processHeight)
                val dstRect = Rect(0, 0, originalWidth, originalHeight)
                canvas.drawBitmap(result, srcRect, dstRect, paint)
                bitmapPool.put(result)
                upscaled
            } else {
                result
            }

            return finalResult
        } catch (e: Exception) {
            Log.e(TAG, "C++ implementation failed, falling back to Kotlin", e)
            return applyKotlin(source, displacementMap, intensity, scale, downscale, redOffset, greenOffset, blueOffset)
        }
    }

    private fun applyKotlin(
        source: Bitmap,
        displacementMap: Bitmap,
        intensity: Float,
        scale: Float,
        downscale: Float,
        redOffset: Float,
        greenOffset: Float,
        blueOffset: Float
    ): Bitmap {
        val originalWidth = source.width
        val originalHeight = source.height

        val processWidth = (originalWidth * downscale).toInt().coerceAtLeast(1)
        val processHeight = (originalHeight * downscale).toInt().coerceAtLeast(1)

        val smallSource = if (downscale < 1.0f) {
            val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(temp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcRect = Rect(0, 0, source.width, source.height)
            val dstRect = Rect(0, 0, processWidth, processHeight)
            canvas.drawBitmap(source, srcRect, dstRect, paint)
            temp
        } else {
            source
        }

        val result = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)

        val scaledMap = if (displacementMap.width != processWidth || displacementMap.height != processHeight) {
            val temp = bitmapPool.get(processWidth, processHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(temp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcRect = Rect(0, 0, displacementMap.width, displacementMap.height)
            val dstRect = Rect(0, 0, processWidth, processHeight)
            canvas.drawBitmap(displacementMap, srcRect, dstRect, paint)
            temp
        } else {
            displacementMap
        }

        val pixelCount = processWidth * processHeight
        if (cachedSize != pixelCount) {
            cachedSourcePixels = IntArray(pixelCount)
            cachedMapPixels = IntArray(pixelCount)
            cachedResultPixels = IntArray(pixelCount)
            cachedSize = pixelCount
        }

        val sourcePixels = cachedSourcePixels!!
        val mapPixels = cachedMapPixels!!
        val resultPixels = cachedResultPixels!!

        smallSource.getPixels(sourcePixels, 0, processWidth, 0, 0, processWidth, processHeight)
        scaledMap.getPixels(mapPixels, 0, processWidth, 0, 0, processWidth, processHeight)

        val scaleFactor = (scale * downscale) / 255f
        val actualRedOffset = redOffset * intensity * downscale
        val actualGreenOffset = greenOffset * intensity * downscale
        val actualBlueOffset = blueOffset * intensity * downscale

        for (y in 0 until processHeight) {
            for (x in 0 until processWidth) {
                val index = y * processWidth + x
                val mapColor = mapPixels[index]

                val baseDx = (Color.red(mapColor) - 128) * scaleFactor
                val baseDy = (Color.green(mapColor) - 128) * scaleFactor

                val baseLen = sqrt(baseDx * baseDx + baseDy * baseDy)
                val dirX: Float
                val dirY: Float
                if (baseLen > 0.001f) {
                    dirX = baseDx / baseLen
                    dirY = baseDy / baseLen
                } else {
                    dirX = 0f
                    dirY = 0f
                }

                val rSrcX = x + baseDx + dirX * actualRedOffset
                val rSrcY = y + baseDy + dirY * actualRedOffset
                val gSrcX = x + baseDx + dirX * actualGreenOffset
                val gSrcY = y + baseDy + dirY * actualGreenOffset
                val bSrcX = x + baseDx + dirX * actualBlueOffset
                val bSrcY = y + baseDy + dirY * actualBlueOffset

                val r: Int
                val g: Int
                val b: Int

                if (useBilinearInterpolation) {
                    val rColor = sampleBilinear(sourcePixels, processWidth, processHeight, rSrcX, rSrcY)
                    val gColor = sampleBilinear(sourcePixels, processWidth, processHeight, gSrcX, gSrcY)
                    val bColor = sampleBilinear(sourcePixels, processWidth, processHeight, bSrcX, bSrcY)
                    r = Color.red(rColor)
                    g = Color.green(gColor)
                    b = Color.blue(bColor)
                } else {
                    val rColor = samplePixel(sourcePixels, processWidth, processHeight, rSrcX, rSrcY)
                    val gColor = samplePixel(sourcePixels, processWidth, processHeight, gSrcX, gSrcY)
                    val bColor = samplePixel(sourcePixels, processWidth, processHeight, bSrcX, bSrcY)
                    r = Color.red(rColor)
                    g = Color.green(gColor)
                    b = Color.blue(bColor)
                }

                val a = Color.alpha(sourcePixels[index])
                resultPixels[index] = Color.argb(a, r, g, b)
            }
        }

        result.setPixels(resultPixels, 0, processWidth, 0, 0, processWidth, processHeight)

        if (scaledMap != displacementMap) {
            bitmapPool.put(scaledMap)
        }
        if (smallSource != source) {
            bitmapPool.put(smallSource)
        }

        val finalResult = if (downscale < 1.0f) {
            val upscaled = bitmapPool.get(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(upscaled)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcRect = Rect(0, 0, processWidth, processHeight)
            val dstRect = Rect(0, 0, originalWidth, originalHeight)
            canvas.drawBitmap(result, srcRect, dstRect, paint)
            bitmapPool.put(result)
            upscaled
        } else {
            result
        }

        return finalResult
    }

    private fun sampleBilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        if (x < 0 || x >= width - 1 || y < 0 || y >= height - 1) {
            val clampedX = x.roundToInt().coerceIn(0, width - 1)
            val clampedY = y.roundToInt().coerceIn(0, height - 1)
            return pixels[clampedY * width + clampedX]
        }

        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)

        val fx = x - x0
        val fy = y - y0

        val c00 = pixels[y0 * width + x0]
        val c10 = pixels[y0 * width + x1]
        val c01 = pixels[y1 * width + x0]
        val c11 = pixels[y1 * width + x1]

        val a = interpolateChannel(Color.alpha(c00), Color.alpha(c10), Color.alpha(c01), Color.alpha(c11), fx, fy)
        val r = interpolateChannel(Color.red(c00), Color.red(c10), Color.red(c01), Color.red(c11), fx, fy)
        val g = interpolateChannel(Color.green(c00), Color.green(c10), Color.green(c01), Color.green(c11), fx, fy)
        val b = interpolateChannel(Color.blue(c00), Color.blue(c10), Color.blue(c01), Color.blue(c11), fx, fy)

        return Color.argb(a, r, g, b)
    }

    private fun interpolateChannel(c00: Int, c10: Int, c01: Int, c11: Int, fx: Float, fy: Float): Int {
        val c0 = c00 * (1 - fx) + c10 * fx
        val c1 = c01 * (1 - fx) + c11 * fx
        val result = c0 * (1 - fy) + c1 * fy
        return result.roundToInt().coerceIn(0, 255)
    }

    private fun samplePixel(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val clampedX = x.roundToInt().coerceIn(0, width - 1)
        val clampedY = y.roundToInt().coerceIn(0, height - 1)
        return pixels[clampedY * width + clampedX]
    }

    fun cleanup() {
        cachedSourcePixels = null
        cachedMapPixels = null
        cachedResultPixels = null
        cachedSize = 0
    }

    private enum class Implementation {
        KOTLIN, CPP
    }

    companion object {
        private const val TAG = "ChromaticAberration"
    }
}
