package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.roundToInt

class AdvancedFastBlur {

    private val bitmapPool = BitmapPool.getInstance()
    private var cachedPixels: IntArray? = null
    private var cachedTempPixels: IntArray? = null
    private var cachedSize = 0

    fun blur(
        bitmap: Bitmap, 
        radius: Float, 
        downscale: Float = 0.5f
    ): Bitmap {
        val clampedRadius = radius.coerceIn(0f, 25f)
        val clampedScale = downscale.coerceIn(0.01f, 1.0f)
        if (clampedRadius < 0.5f) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        return blurWithDownscale(bitmap, clampedRadius, clampedScale)
    }

    private fun blurWithDownscale(
        bitmap: Bitmap,
        radius: Float,
        scale: Float
    ): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val smallWidth = (originalWidth * scale).roundToInt().coerceAtLeast(1)
        val smallHeight = (originalHeight * scale).roundToInt().coerceAtLeast(1)

        val small = bitmapPool.get(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(small)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect = Rect(0, 0, originalWidth, originalHeight)
        val dstRect = Rect(0, 0, smallWidth, smallHeight)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        val scaledRadius = (radius * scale).coerceIn(1f, 25f)
        val blurred = blurWithBoxBlur(small, scaledRadius.roundToInt())

        if (small != blurred) {
            bitmapPool.put(small)
        }

        val result = bitmapPool.get(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val canvas2 = Canvas(result)
        val paint2 = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect2 = Rect(0, 0, smallWidth, smallHeight)
        val dstRect2 = Rect(0, 0, originalWidth, originalHeight)
        canvas2.drawBitmap(blurred, srcRect2, dstRect2, paint2)

        if (blurred != result) {
            bitmapPool.put(blurred)
        }

        return result
    }

    private fun blurWithBoxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        
        if (cachedSize != pixelCount) {
            cachedPixels = IntArray(pixelCount)
            cachedTempPixels = IntArray(pixelCount)
            cachedSize = pixelCount
        }
        
        val pixels = cachedPixels!!
        val tempPixels = cachedTempPixels!!
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        boxBlurHorizontal(pixels, tempPixels, width, height, radius)
        boxBlurVertical(tempPixels, pixels, width, height, radius)
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        
        return result
    }

    private fun boxBlurHorizontal(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius * 2 + 1
        
        for (y in 0 until height) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            
            val rowStart = y * width
            
            for (x in -radius..radius) {
                val px = x.coerceIn(0, width - 1)
                val color = input[rowStart + px]
                sumA += (color shr 24) and 0xFF
                sumR += (color shr 16) and 0xFF
                sumG += (color shr 8) and 0xFF
                sumB += color and 0xFF
            }
            
            for (x in 0 until width) {
                output[rowStart + x] = (
                    ((sumA / windowSize) shl 24) or
                    ((sumR / windowSize) shl 16) or
                    ((sumG / windowSize) shl 8) or
                    (sumB / windowSize)
                )
                
                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                
                val removeColor = input[rowStart + removeX]
                val addColor = input[rowStart + addX]
                
                sumA += ((addColor shr 24) and 0xFF) - ((removeColor shr 24) and 0xFF)
                sumR += ((addColor shr 16) and 0xFF) - ((removeColor shr 16) and 0xFF)
                sumG += ((addColor shr 8) and 0xFF) - ((removeColor shr 8) and 0xFF)
                sumB += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        val windowSize = radius * 2 + 1
        
        for (x in 0 until width) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            
            for (y in -radius..radius) {
                val py = y.coerceIn(0, height - 1)
                val color = input[py * width + x]
                sumA += (color shr 24) and 0xFF
                sumR += (color shr 16) and 0xFF
                sumG += (color shr 8) and 0xFF
                sumB += color and 0xFF
            }
            
            for (y in 0 until height) {
                output[y * width + x] = (
                    ((sumA / windowSize) shl 24) or
                    ((sumR / windowSize) shl 16) or
                    ((sumG / windowSize) shl 8) or
                    (sumB / windowSize)
                )
                
                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                
                val removeColor = input[removeY * width + x]
                val addColor = input[addY * width + x]
                
                sumA += ((addColor shr 24) and 0xFF) - ((removeColor shr 24) and 0xFF)
                sumR += ((addColor shr 16) and 0xFF) - ((removeColor shr 16) and 0xFF)
                sumG += ((addColor shr 8) and 0xFF) - ((removeColor shr 8) and 0xFF)
                sumB += (addColor and 0xFF) - (removeColor and 0xFF)
            }
        }
    }

    fun cleanup() {
        cachedPixels = null
        cachedTempPixels = null
        cachedSize = 0
    }
}
