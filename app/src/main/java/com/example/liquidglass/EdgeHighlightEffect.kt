package com.example.liquidglass

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class EdgeHighlightEffect {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val borderPath = Path()
    private val innerPath = Path()
    private val overlayPath = Path()

    private var rimBlender: RuntimeXfermode? = null
    private var rimBlenderTried = false

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        cornerRadii: FloatArray,
        mouseOffset: PointF = PointF(0f, 0f),
        overLight: Boolean = false,
        borderWidth: Float = 1.5f,
        opacity: Float = 100f,
        apiLevelCap: Int = Int.MAX_VALUE
    ) {
        val normalizedOpacity = opacity / 100f

        if (overLight) {
            drawOverLightEffect(canvas, bounds, cornerRadii, normalizedOpacity)
        }

        if (canvas.isHardwareAccelerated && GlassRuntimeEffects.isSupported &&
            apiLevelCap >= Build.VERSION_CODES.BAKLAVA
        ) {
            if (!rimBlenderTried) {
                rimBlenderTried = true
                rimBlender = GlassRuntimeEffects.createRimBlender()
            }
            val blender = rimBlender
            if (blender != null) {
                drawBorderLayerFused(canvas, bounds, cornerRadii, mouseOffset, blender, normalizedOpacity, borderWidth)
                return
            }
        }

        drawBorderLayer(canvas, bounds, cornerRadii, mouseOffset, PorterDuff.Mode.SCREEN, 0.2f * normalizedOpacity, borderWidth)
        drawBorderLayer(canvas, bounds, cornerRadii, mouseOffset, PorterDuff.Mode.OVERLAY, 1.0f * normalizedOpacity, borderWidth)
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun drawBorderLayerFused(
        canvas: Canvas,
        bounds: RectF,
        cornerRadii: FloatArray,
        mouseOffset: PointF,
        blender: RuntimeXfermode,
        normalizedOpacity: Float,
        borderWidth: Float
    ) {
        val gradientAngle = 135f + mouseOffset.x * 1.2f
        val angleRad = Math.toRadians(gradientAngle.toDouble()).toFloat()

        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val radius = max(bounds.width(), bounds.height()) / 2f

        val startX = centerX - radius * cos(angleRad)
        val startY = centerY - radius * sin(angleRad)
        val endX = centerX + radius * cos(angleRad)
        val endY = centerY + radius * sin(angleRad)

        val opacity1 = 0.32f + abs(mouseOffset.x) * 0.008f
        val opacity2 = 0.6f + abs(mouseOffset.x) * 0.012f
        val position1 = max(0.1f, 0.33f + mouseOffset.y * 0.003f)
        val position2 = min(0.9f, 0.66f + mouseOffset.y * 0.004f)

        val gradient = LinearGradient(
            startX, startY, endX, endY,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb((opacity1 * 255).toInt(), 255, 255, 255),
                Color.argb((opacity2 * 255).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, position1, position2, 1f),
            Shader.TileMode.CLAMP
        )

        buildBorderPath(bounds, cornerRadii, borderWidth)

        borderPaint.reset()
        borderPaint.isAntiAlias = true
        borderPaint.shader = gradient
        borderPaint.alpha = (normalizedOpacity * 255).toInt()
        borderPaint.xfermode = blender

        canvas.drawPath(borderPath, borderPaint)

        borderPaint.xfermode = null
        borderPaint.shader = null
    }

    private fun buildBorderPath(bounds: RectF, cornerRadii: FloatArray, borderWidth: Float) {
        borderPath.reset()
        borderPath.addRoundRect(bounds, cornerRadii, Path.Direction.CW)

        innerPath.reset()
        val innerBounds = RectF(
            bounds.left + borderWidth,
            bounds.top + borderWidth,
            bounds.right - borderWidth,
            bounds.bottom - borderWidth
        )
        val innerRadii = FloatArray(8) { (cornerRadii[it] - borderWidth).coerceAtLeast(0f) }
        innerPath.addRoundRect(innerBounds, innerRadii, Path.Direction.CW)

        borderPath.op(innerPath, Path.Op.DIFFERENCE)
    }
    
    private fun drawOverLightEffect(canvas: Canvas, bounds: RectF, cornerRadii: FloatArray, opacity: Float) {
        overlayPath.reset()
        overlayPath.addRoundRect(bounds, cornerRadii, Path.Direction.CW)

        overlayPaint.reset()
        overlayPaint.isAntiAlias = true
        overlayPaint.color = Color.argb((0.2f * opacity * 255).toInt(), 0, 0, 0)
        canvas.drawPath(overlayPath, overlayPaint)

        overlayPaint.reset()
        overlayPaint.isAntiAlias = true
        overlayPaint.color = Color.argb((opacity * 255).toInt(), 0, 0, 0)
        overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        canvas.drawPath(overlayPath, overlayPaint)
        overlayPaint.xfermode = null
    }

    private fun drawBorderLayer(
        canvas: Canvas,
        bounds: RectF,
        cornerRadii: FloatArray,
        mouseOffset: PointF,
        blendMode: PorterDuff.Mode,
        baseOpacity: Float,
        borderWidth: Float
    ) {
        val gradientAngle = 135f + mouseOffset.x * 1.2f
        val angleRad = Math.toRadians(gradientAngle.toDouble()).toFloat()
        
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val radius = max(bounds.width(), bounds.height()) / 2f
        
        val startX = centerX - radius * cos(angleRad)
        val startY = centerY - radius * sin(angleRad)
        val endX = centerX + radius * cos(angleRad)
        val endY = centerY + radius * sin(angleRad)
        
        val opacity1 = if (blendMode == PorterDuff.Mode.SCREEN) {
            0.12f + abs(mouseOffset.x) * 0.008f
        } else {
            0.32f + abs(mouseOffset.x) * 0.008f
        }
        
        val opacity2 = if (blendMode == PorterDuff.Mode.SCREEN) {
            0.4f + abs(mouseOffset.x) * 0.012f
        } else {
            0.6f + abs(mouseOffset.x) * 0.012f
        }
        
        val position1 = max(0.1f, 0.33f + mouseOffset.y * 0.003f)
        val position2 = min(0.9f, 0.66f + mouseOffset.y * 0.004f)
        
        val gradient = LinearGradient(
            startX, startY, endX, endY,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb((opacity1 * 255).toInt(), 255, 255, 255),
                Color.argb((opacity2 * 255).toInt(), 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, position1, position2, 1f),
            Shader.TileMode.CLAMP
        )
        
        buildBorderPath(bounds, cornerRadii, borderWidth)

        borderPaint.reset()
        borderPaint.isAntiAlias = true
        borderPaint.shader = gradient
        borderPaint.alpha = (baseOpacity * 255).toInt()
        borderPaint.xfermode = PorterDuffXfermode(blendMode)
        
        canvas.drawPath(borderPath, borderPaint)
        
        borderPaint.xfermode = null
        borderPaint.shader = null
    }

    fun cleanup() {
        borderPath.reset()
        innerPath.reset()
    }
}
