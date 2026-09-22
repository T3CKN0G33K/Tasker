package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs

object DispersionMapGenerator {

    private const val TAG = "DispersionMapGenerator"

    enum class Shape {
        CIRCLE,
        RECTANGLE,
        ELLIPSE
    }

    fun generateEdgeDistanceMapFromAlpha(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val edgePixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = source.getPixel(x, y)
                val alpha = Color.alpha(pixel)

                val isEdge = alpha < 255 || isAlphaEdge(source, x, y)
                if (isEdge) {
                    edgePixels.add(Pair(x, y))
                }
            }
        }

        if (edgePixels.isEmpty()) {
            bitmap.eraseColor(Color.WHITE)
            return bitmap
        }

        val maxDist = sqrt((width * width + height * height).toFloat()) / 2f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = source.getPixel(x, y)
                val alpha = Color.alpha(pixel)

                if (alpha < 10) {
                    bitmap.setPixel(x, y, Color.rgb(0, 0, 0))
                    continue
                }

                val distToLeft = x.toFloat()
                val distToRight = (width - 1 - x).toFloat()
                val distToTop = y.toFloat()
                val distToBottom = (height - 1 - y).toFloat()

                val minDistance = min(min(distToLeft, distToRight), min(distToTop, distToBottom))
                val normalizedDistance = (minDistance / maxDist * 255f).coerceIn(0f, 255f).toInt()

                bitmap.setPixel(x, y, Color.rgb(normalizedDistance, normalizedDistance, normalizedDistance))
            }
        }

        return bitmap
    }

    private fun isAlphaEdge(source: Bitmap, x: Int, y: Int): Boolean {
        val width = source.width
        val height = source.height
        val centerAlpha = Color.alpha(source.getPixel(x, y))

        val neighbors = listOf(
            Pair(x - 1, y),
            Pair(x + 1, y),
            Pair(x, y - 1),
            Pair(x, y + 1)
        )

        for ((nx, ny) in neighbors) {
            if (nx in 0 until width && ny in 0 until height) {
                val neighborAlpha = Color.alpha(source.getPixel(nx, ny))
                if (abs(centerAlpha - neighborAlpha) > 10) {
                    return true
                }
            }
        }

        return false
    }

    fun generateEdgeDistanceMap(
        width: Int,
        height: Int,
        shape: Shape = Shape.CIRCLE,
        centerX: Float = width / 2f,
        centerY: Float = height / 2f,
        maxDistance: Float? = null
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val maxDist = maxDistance ?: when (shape) {
            Shape.CIRCLE -> min(width, height) / 2f
            Shape.RECTANGLE -> sqrt((width / 2f) * (width / 2f) + (height / 2f) * (height / 2f))
            Shape.ELLIPSE -> max(width, height) / 2f
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY

                val distance = when (shape) {
                    Shape.CIRCLE -> sqrt(dx * dx + dy * dy)
                    Shape.RECTANGLE -> {
                        val distToLeft = x.toFloat()
                        val distToRight = (width - 1 - x).toFloat()
                        val distToTop = y.toFloat()
                        val distToBottom = (height - 1 - y).toFloat()
                        min(min(distToLeft, distToRight), min(distToTop, distToBottom))
                    }
                    Shape.ELLIPSE -> {
                        val normalizedDx = dx / (width / 2f)
                        val normalizedDy = dy / (height / 2f)
                        sqrt(normalizedDx * normalizedDx + normalizedDy * normalizedDy) * maxDist
                    }
                }

                val normalizedDistance = (distance / maxDist * 255f).coerceIn(0f, 255f).toInt()
                bitmap.setPixel(x, y, Color.rgb(normalizedDistance, normalizedDistance, normalizedDistance))
            }
        }

        return bitmap
    }

    fun generateRadialNormalMap(
        width: Int,
        height: Int,
        centerX: Float = width / 2f,
        centerY: Float = height / 2f,
        invert: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val len = sqrt(dx * dx + dy * dy)

                val nx = if (len > 0f) dx / len else 0f
                val ny = if (len > 0f) dy / len else 0f

                val finalNx = if (invert) -nx else nx
                val finalNy = if (invert) -ny else ny

                val r = ((finalNx + 1f) * 0.5f * 255f).coerceIn(0f, 255f).toInt()
                val g = ((finalNy + 1f) * 0.5f * 255f).coerceIn(0f, 255f).toInt()
                val b = 128

                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return bitmap
    }
}
