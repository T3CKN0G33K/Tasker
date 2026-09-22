package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

internal class BackdropLuminanceMeter(
    private val view: LiquidGlassView,
    private val onLuminance: (Float) -> Unit
) {
    companion object {
        private const val SAMPLE_SIZE = 24
        private const val EMA_ALPHA = 0.35f
        private const val OVER_LIGHT_ON = 0.60f
        private const val OVER_LIGHT_OFF = 0.45f

        fun measureBitmap(bitmap: Bitmap): Float {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0 || bitmap.isRecycled) return 0.5f
            val grid = 8
            val stepX = (w / grid).coerceAtLeast(1)
            val stepY = (h / grid).coerceAtLeast(1)
            var sum = 0f
            var count = 0
            var y = stepY / 2
            while (y < h) {
                var x = stepX / 2
                while (x < w) {
                    val c = bitmap.getPixel(x, y)
                    sum += 0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)
                    count++
                    x += stepX
                }
                y += stepY
            }
            return if (count > 0) (sum / count / 255f).coerceIn(0f, 1f) else 0.5f
        }
    }

    var intervalMs = 350L

    var smoothedLuminance = 0.5f
        private set

    var isOverLight = false
        private set

    private var running = false
    private var sampleBitmap: Bitmap? = null
    private val location = IntArray(2)
    private val parentLocation = IntArray(2)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            sampleFromParent()
            view.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        view.postDelayed(tick, intervalMs)
    }

    fun stop() {
        running = false
        view.removeCallbacks(tick)
        sampleBitmap?.recycle()
        sampleBitmap = null
    }

    private fun sampleFromParent() {
        val parent = view.backdropView ?: return
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return

        val bmp = sampleBitmap?.takeIf { !it.isRecycled }
            ?: Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888).also { sampleBitmap = it }
        bmp.eraseColor(Color.GRAY)

        try {
            val canvas = Canvas(bmp)
            view.getLocationOnScreen(location)
            parent.getLocationOnScreen(parentLocation)
            val offsetX = (location[0] - parentLocation[0]).toFloat()
            val offsetY = (location[1] - parentLocation[1]).toFloat()

            canvas.scale(SAMPLE_SIZE.toFloat() / w, SAMPLE_SIZE.toFloat() / h)
            canvas.translate(-offsetX, -offsetY)

            view.isCapturingBackdrop = true
            LiquidGlassView.isGlobalCapturingBackdrop = true
            LiquidGlassView.isLuminanceSampling = true
            try {
                parent.draw(canvas)
            } finally {
                LiquidGlassView.isLuminanceSampling = false
                LiquidGlassView.isGlobalCapturingBackdrop = false
                view.isCapturingBackdrop = false
            }
        } catch (_: Exception) {
            return
        }

        submit(measureBitmap(bmp))
    }

    fun submit(luminance: Float) {
        smoothedLuminance += (luminance - smoothedLuminance) * EMA_ALPHA

        if (!isOverLight && smoothedLuminance > OVER_LIGHT_ON) {
            isOverLight = true
        } else if (isOverLight && smoothedLuminance < OVER_LIGHT_OFF) {
            isOverLight = false
        }

        onLuminance(smoothedLuminance)
    }
}
