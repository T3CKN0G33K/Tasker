package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

enum class DisplacementMode {
    STANDARD,
    POLAR,
    PROMINENT
}

data class Vec2(val x: Float, val y: Float)

class DisplacementMapGenerator(
    private val width: Int,
    private val height: Int
) {
    
    fun generate(mode: DisplacementMode = DisplacementMode.STANDARD): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val rawDisplacements = Array(height) { y ->
            FloatArray(width * 2) { x ->
                val pixelX = x / 2
                val uv = Vec2(pixelX.toFloat() / width, y.toFloat() / height)
                val displaced = when (mode) {
                    DisplacementMode.STANDARD -> fragmentShaderStandard(uv)
                    DisplacementMode.POLAR -> fragmentShaderPolar(uv)
                    DisplacementMode.PROMINENT -> fragmentShaderProminent(uv)
                }
                
                if (x % 2 == 0) {
                    displaced.x * width - pixelX
                } else {
                    displaced.y * height - y
                }
            }
        }
        
        var maxScale = 0f
        rawDisplacements.forEach { row ->
            row.forEach { value ->
                maxScale = max(maxScale, abs(value))
            }
        }
        maxScale = max(maxScale, 1f)
        
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val dx = rawDisplacements[y][x * 2]
                val dy = rawDisplacements[y][x * 2 + 1]

                val edgeDistance = min(min(x, y), min(width - x - 1, height - y - 1))
                val edgeFactor = min(1f, edgeDistance / 2f)

                val smoothedDx = dx * edgeFactor
                val smoothedDy = dy * edgeFactor

                val r = (smoothedDx / maxScale + 0.5f).coerceIn(0f, 1f)
                val g = (smoothedDy / maxScale + 0.5f).coerceIn(0f, 1f)

                pixels[rowOffset + x] = Color.argb(
                    255,
                    (r * 255).toInt(),
                    (g * 255).toInt(),
                    (g * 255).toInt()
                )
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return bitmap
    }
    
    private fun fragmentShaderStandard(uv: Vec2): Vec2 {
        val ix = uv.x - 0.5f
        val iy = uv.y - 0.5f
        
        val distanceToEdge = roundedRectSDF(ix, iy, 0.3f, 0.2f, 0.6f)
        val displacement = smoothStep(0.8f, 0f, distanceToEdge - 0.15f)
        val scaled = smoothStep(0f, 1f, displacement)
        
        return Vec2(
            ix * scaled + 0.5f,
            iy * scaled + 0.5f
        )
    }
    
    private fun fragmentShaderPolar(uv: Vec2): Vec2 {
        val ix = uv.x - 0.5f
        val iy = uv.y - 0.5f
        
        val radius = sqrt(ix * ix + iy * iy)
        val angle = atan2(iy, ix)
        
        val distanceToEdge = roundedRectSDF(ix, iy, 0.35f, 0.25f, 0.5f)
        val displacement = smoothStep(0.7f, 0f, distanceToEdge - 0.1f)
        val newRadius = radius * (1f - displacement * 0.3f)
        
        return Vec2(
            newRadius * cos(angle) + 0.5f,
            newRadius * sin(angle) + 0.5f
        )
    }
    
    private fun fragmentShaderProminent(uv: Vec2): Vec2 {
        val ix = uv.x - 0.5f
        val iy = uv.y - 0.5f
        
        val distanceToEdge = roundedRectSDF(ix, iy, 0.25f, 0.15f, 0.7f)
        val displacement = smoothStep(0.9f, 0f, distanceToEdge - 0.2f)
        val scaled = displacement.pow(1.5f)
        val pushFactor = 1f + scaled * 0.2f
        
        return Vec2(
            ix * pushFactor + 0.5f,
            iy * pushFactor + 0.5f
        )
    }
    
    private fun smoothStep(a: Float, b: Float, t: Float): Float {
        val clamped = ((t - a) / (b - a)).coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }
    
    private fun roundedRectSDF(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float
    ): Float {
        val qx = abs(x) - width + radius
        val qy = abs(y) - height + radius
        
        val len = sqrt(max(qx, 0f).pow(2) + max(qy, 0f).pow(2))
        return min(max(qx, qy), 0f) + len - radius
    }
    
    companion object {
        fun generateStandardMaps(width: Int = 270, height: Int = 69): Map<DisplacementMode, Bitmap> {
            val generator = DisplacementMapGenerator(width, height)
            return mapOf(
                DisplacementMode.STANDARD to generator.generate(DisplacementMode.STANDARD),
                DisplacementMode.POLAR to generator.generate(DisplacementMode.POLAR),
                DisplacementMode.PROMINENT to generator.generate(DisplacementMode.PROMINENT)
            )
        }
    }
}
