package com.example.liquidglass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
internal class HardwareBackdropBlur {

    companion object {
        fun supportsRuntimeShader(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        private const val ABERRATION_AGSL = """
            uniform shader content;
            uniform shader dispMap;
            uniform float2 dispCoordScale;
            uniform float displacementScale;
            uniform float3 channelOffsets;

            half4 main(float2 coord) {
                half4 m = dispMap.eval(coord * dispCoordScale);
                float2 base = float2((float(m.r) - 0.5) * displacementScale,
                                     (float(m.g) - 0.5) * displacementScale);
                float bl = length(base);
                float2 dir = float2(0.0);
                if (bl > 0.001) {
                    dir = base / bl;
                }
                half r = content.eval(coord + base + dir * channelOffsets.x).r;
                half g = content.eval(coord + base + dir * channelOffsets.y).g;
                half b = content.eval(coord + base + dir * channelOffsets.z).b;
                half a = content.eval(coord).a;
                return half4(r, g, b, a);
            }
        """
    }

    data class AberrationParams(
        val displacementMap: Bitmap,
        val displacementScale: Float,
        val redOffset: Float,
        val greenOffset: Float,
        val blueOffset: Float
    )

    private val renderNode = RenderNode("LiquidGlassBackdrop")

    var debugApiLevelCap: Int = Int.MAX_VALUE

    private var lastBlurRadius = Float.NaN
    private var lastSaturation = Float.NaN
    private var lastAberration: AberrationParams? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastApiCap = Int.MAX_VALUE
    private var effectBuilt = false

    private var aberrationShader: RuntimeShader? = null
    private var vibrancyFilter: RuntimeColorFilter? = null

    private val location = IntArray(2)
    private val parentLocation = IntArray(2)

    private val backdropCapture = BackdropCapture()

    fun draw(
        canvas: Canvas,
        glassView: LiquidGlassView,
        blurRadius: Float,
        saturation: Float,
        clipPath: Path,
        aberration: AberrationParams? = null
    ): Boolean {
        if (!canvas.isHardwareAccelerated) return false
        if (aberration != null && !supportsRuntimeShader()) return false
        val parent = glassView.backdropView ?: return false
        val width = glassView.width
        val height = glassView.height
        if (width <= 0 || height <= 0) return false

        if (!effectBuilt ||
            blurRadius != lastBlurRadius ||
            saturation != lastSaturation ||
            aberration != lastAberration ||
            width != lastWidth ||
            height != lastHeight ||
            debugApiLevelCap != lastApiCap
        ) {
            renderNode.setRenderEffect(buildEffect(blurRadius, saturation, aberration, width, height))
            lastBlurRadius = blurRadius
            lastSaturation = saturation
            lastAberration = aberration
            lastWidth = width
            lastHeight = height
            lastApiCap = debugApiLevelCap
            effectBuilt = true
        }

        glassView.getLocationOnScreen(location)
        parent.getLocationOnScreen(parentLocation)
        val offsetX = (location[0] - parentLocation[0]).toFloat()
        val offsetY = (location[1] - parentLocation[1]).toFloat()

        try {
            renderNode.setPosition(0, 0, width, height)
            val recordingCanvas = renderNode.beginRecording(width, height)
            try {
                recordingCanvas.translate(-offsetX, -offsetY)
                glassView.isCapturingBackdrop = true
                LiquidGlassView.isGlobalCapturingBackdrop = true
                try {
                    backdropCapture.draw(recordingCanvas, parent, glassView)
                } finally {
                    LiquidGlassView.isGlobalCapturingBackdrop = false
                    glassView.isCapturingBackdrop = false
                }
            } finally {
                renderNode.endRecording()
            }

            val saveCount = canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRenderNode(renderNode)
            canvas.restoreToCount(saveCount)
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    private fun buildEffect(
        blurRadius: Float,
        saturation: Float,
        aberration: AberrationParams?,
        width: Int,
        height: Int
    ): RenderEffect? {
        var effect: RenderEffect? = if (blurRadius > 0f) {
            RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        } else {
            null
        }

        if (aberration != null && supportsRuntimeShader()) {
            val aberrationEffect = buildAberrationEffect(aberration, width, height)
            effect = if (effect != null) {
                RenderEffect.createChainEffect(aberrationEffect, effect)
            } else {
                aberrationEffect
            }
        }

        if (saturation != 100f) {
            val satEffect = RenderEffect.createColorFilterEffect(
                saturationFilter(saturation / 100f)
            )
            effect = if (effect != null) {
                RenderEffect.createChainEffect(satEffect, effect)
            } else {
                satEffect
            }
        }

        return effect
    }

    private fun saturationFilter(factor: Float): ColorFilter {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            debugApiLevelCap >= Build.VERSION_CODES.BAKLAVA
        ) {
            val vib = vibrancyFilter ?: GlassRuntimeEffects.createVibrancyFilter()?.also { vibrancyFilter = it }
            if (vib != null) {
                vib.setFloatUniform("satFactor", factor)
                return vib
            }
        }
        return ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(factor) })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun buildAberrationEffect(params: AberrationParams, width: Int, height: Int): RenderEffect {
        val shader = aberrationShader ?: RuntimeShader(ABERRATION_AGSL).also { aberrationShader = it }

        shader.setInputShader(
            "dispMap",
            BitmapShader(params.displacementMap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        )
        shader.setFloatUniform(
            "dispCoordScale",
            params.displacementMap.width.toFloat() / width,
            params.displacementMap.height.toFloat() / height
        )
        shader.setFloatUniform("displacementScale", params.displacementScale)
        shader.setFloatUniform(
            "channelOffsets",
            params.redOffset,
            params.greenOffset,
            params.blueOffset
        )

        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    fun release() {
        renderNode.discardDisplayList()
        aberrationShader = null
    }
}
