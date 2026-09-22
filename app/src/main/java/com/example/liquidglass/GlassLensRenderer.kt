package com.example.liquidglass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassLensRenderer {

    companion object {
        private const val TAG = "GlassLensRenderer"

        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        private const val LENS_AGSL = """
            uniform shader content;
            uniform float  margin;
            uniform float2 viewSize;
            uniform float4 shape1;
            uniform float4 radii1;
            uniform float4 shape1L;
            uniform float  rimSoft;
            uniform float4 shape2;
            uniform float  radius2;
            uniform float  blendK;
            uniform float  bevel;
            uniform float  refractPx;
            uniform float  falloff;
            uniform float  refractDir;
            uniform float2 sampleLo;
            uniform float2 sampleHi;
            uniform float  dispersion;
            uniform float2 lightDir;
            uniform float  specStrength;
            uniform float  rimBandMax;
            uniform float4 tintColor;
            uniform float  adaptiveTint;
            uniform float4 glassTint;
            uniform float  dimAmount;
            uniform float  satFactor;
            uniform float  press;
            uniform float2 touchPos;
            uniform float  touchAmp;

            float sdRoundedBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            }

            float sdRoundedBox4(float2 p, float2 b, float4 r) {
                float rx = (p.x > 0.0) ? ((p.y > 0.0) ? r.z : r.y) : ((p.y > 0.0) ? r.w : r.x);
                float2 q = abs(p) - b + rx;
                return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rx;
            }

            float sminPoly(float a, float b, float k) {
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            float lensSDF(float2 p) {
                float d = sdRoundedBox4(p - shape1L.xy, shape1L.zw, radii1);
                if (shape2.z > 0.5) {
                    float d2 = sdRoundedBox(p - shape2.xy, shape2.zw, radius2);
                    if (blendK > 0.5) {
                        d = sminPoly(d, d2, blendK);
                    } else {
                        d = min(d, d2);
                    }
                }
                return d;
            }

            half4 main(float2 coord) {
                float2 p = coord - float2(margin, margin);
                if (p.x < 0.0 || p.y < 0.0 || p.x > viewSize.x || p.y > viewSize.y) {
                    return half4(0.0);
                }
                float d = lensSDF(p);

                float cov = clamp(0.5 - d / 1.5, 0.0, 1.0);
                if (cov <= 0.004) {
                    return half4(0.0);
                }

                float2 n = float2(
                    lensSDF(p + float2(1.0, 0.0)) - lensSDF(p - float2(1.0, 0.0)),
                    lensSDF(p + float2(0.0, 1.0)) - lensSDF(p - float2(0.0, 1.0))
                );
                float nLen = length(n);
                if (nLen > 0.0001) {
                    n = n / nLen;
                } else {
                    n = float2(0.0, -1.0);
                }

                float t = clamp(-d / max(bevel, 1.0), 0.0, 1.0);
                float edge = 1.0 - t;
                float slope;
                if (falloff > 0.001) {
                    float gB = pow(5.0, -falloff);
                    slope = (pow(1.0 + 4.0 * t, -falloff) - gB) / (1.0 - gB);
                } else {
                    slope = edge * edge;
                }

                float refr = refractPx * (1.0 + 0.6 * press);
                float2 offset = n * (refractDir * slope * refr);

                if (touchAmp > 0.001) {
                    float2 tp = p - touchPos;
                    float tr = length(tp);
                    float sigma = max(max(shape1.z, shape1.w), 1.0);
                    float bump = touchAmp * exp(-(tr * tr) / (sigma * sigma * 0.30));
                    if (tr > 1.0) {
                        offset -= (tp / tr) * (bump * refr * 0.5);
                    }
                }

                float2 cR = coord + offset * (1.0 - dispersion * slope);
                float2 cG = coord + offset;
                float2 cB = coord + offset * (1.0 + dispersion * slope);

                float2 lo = sampleLo;
                float2 hi = sampleHi;
                cR = clamp(cR, lo, hi);
                cG = clamp(cG, lo, hi);
                cB = clamp(cB, lo, hi);
                float3 col;
                if (rimSoft > 0.01 && slope > 0.001) {
                    float2 sm = n * (rimSoft * slope);
                    float2 cR1 = clamp(cR - sm, lo, hi);
                    float2 cR2 = clamp(cR + sm, lo, hi);
                    float2 cG1 = clamp(cG - sm, lo, hi);
                    float2 cG2 = clamp(cG + sm, lo, hi);
                    float2 cB1 = clamp(cB - sm, lo, hi);
                    float2 cB2 = clamp(cB + sm, lo, hi);
                    col = float3(
                        (content.eval(cR).r + content.eval(cR1).r + content.eval(cR2).r) / 3.0,
                        (content.eval(cG).g + content.eval(cG1).g + content.eval(cG2).g) / 3.0,
                        (content.eval(cB).b + content.eval(cB1).b + content.eval(cB2).b) / 3.0
                    );
                } else {
                    col = float3(
                        content.eval(cR).r,
                        content.eval(cG).g,
                        content.eval(cB).b
                    );
                }

                float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
                if (satFactor <= 1.0) {
                    col = mix(float3(lum), col, satFactor);
                } else {
                    float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
                    float room = 1.0 - smoothstep(0.2, 0.85, satNow);
                    float hl = 1.0 - smoothstep(0.75, 0.98, lum);
                    float amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                    col = clamp(mix(float3(lum), col, amount), float3(0.0), float3(1.0));
                }

                if (adaptiveTint > 0.5) {
                    float lumT = dot(col, float3(0.2126, 0.7152, 0.0722));
                    float e = smoothstep(0.35, 0.75, lumT);
                    col = mix(col, float3(1.0 - e), 0.14 + 0.08 * e);
                } else {
                    col = mix(col, tintColor.rgb, tintColor.a);
                }
                col = col * (1.0 - dimAmount);

                if (glassTint.a > 0.002) {
                    float lumTint = dot(col, float3(0.2126, 0.7152, 0.0722));
                    float3 absorbed = col * mix(float3(1.0), glassTint.rgb, 0.85);
                    float3 scattered = glassTint.rgb * (0.38 * (1.0 - lumTint));
                    col = mix(col, clamp(absorbed + scattered, float3(0.0), float3(1.0)), glassTint.a);
                }

                float facing = dot(n, -lightDir);
                float lobeF = pow(max(facing, 0.0), 4.5);
                float lobeB = pow(max(-facing, 0.0), 4.5);

                float bandW = clamp(bevel * 0.3, 2.0, rimBandMax);
                float glowIn = clamp((-d - 1.0) / 2.0, 0.0, 1.0);
                float glow = glowIn * pow(clamp(1.0 - (-d - 3.0) / bandW, 0.0, 1.0), 1.5) * cov;
                float hair = clamp(1.0 - abs(d + 1.0) / 2.0, 0.0, 1.0) * cov;
                float spec = (hair * 0.70 * (lobeF + lobeB) + glow * 0.10 * lobeF)
                             * specStrength * (1.0 - 0.35 * press);
                col += float3(spec);

                col = clamp(col, float3(0.0), float3(1.0));
                return half4(half3(col * cov), half(cov));
            }
        """
    }

    data class LensParams(
        val blurRadius: Float,
        val shape1CX: Float, val shape1CY: Float,
        val shape1HW: Float, val shape1HH: Float,
        val radius1TL: Float, val radius1TR: Float, val radius1BR: Float, val radius1BL: Float,
        val lens1CX: Float, val lens1CY: Float, val lens1HW: Float, val lens1HH: Float,
        val rimSoft: Float,
        val shape2CX: Float, val shape2CY: Float,
        val shape2HW: Float, val shape2HH: Float, val radius2: Float,
        val blendK: Float,
        val bevel: Float,
        val refract: Float,
        val falloff: Float,
        val outward: Boolean,
        val rimBandMax: Float,
        val dispersion: Float,
        val lightX: Float, val lightY: Float,
        val spec: Float,
        val tint: Int,
        val adaptiveTint: Boolean,
        val glassTint: Int,
        val dim: Float,
        val saturation: Float,
        val press: Float,
        val touchX: Float, val touchY: Float, val touchAmp: Float
    )

    private val renderNode = RenderNode("LiquidGlassLens")

    private val layerNode = RenderNode("LiquidGlassLensLayer").apply {
        setUseCompositingLayer(true, null)
    }

    private var sampleLoX = 0f
    private var sampleLoY = 0f
    private var sampleHiX = 0f
    private var sampleHiY = 0f

    private var shader: RuntimeShader? = null
    private var shaderBroken = false

    private var lastParams: LensParams? = null
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastMargin = -1

    private val location = IntArray(2)
    private val parentLocation = IntArray(2)

    private val backdropCapture = BackdropCapture()

    val isAvailable: Boolean
        get() = !shaderBroken

    fun draw(
        canvas: Canvas,
        glassView: LiquidGlassView,
        params: LensParams,
        margin: Int
    ): Boolean {
        if (shaderBroken) return false
        if (!canvas.isHardwareAccelerated) return false
        val parent = glassView.backdropView ?: return false
        val width = glassView.width
        val height = glassView.height
        if (width <= 0 || height <= 0) return false

        val sh = shader ?: try {
            RuntimeShader(LENS_AGSL).also { shader = it }
        } catch (e: Throwable) {
            Log.e(TAG, "Lens AGSL compile failed, falling back", e)
            shaderBroken = true
            return false
        }

        glassView.getLocationOnScreen(location)
        parent.getLocationOnScreen(parentLocation)
        val offsetX = (location[0] - parentLocation[0]).toFloat()
        val offsetY = (location[1] - parentLocation[1]).toFloat()

        val recW = width + 2 * margin
        val recH = height + 2 * margin
        val boundsChanged = updateSampleBounds(
            params.outward, margin, width, height, recW, recH,
            offsetX, offsetY, parent.width, parent.height
        )

        if (boundsChanged || params != lastParams ||
            width != lastWidth || height != lastHeight || margin != lastMargin
        ) {
            try {
                renderNode.setRenderEffect(buildEffect(sh, params, margin, width, height))
            } catch (e: Throwable) {
                Log.e(TAG, "Lens effect build failed, falling back", e)
                shaderBroken = true
                return false
            }
            lastParams = params
            lastWidth = width
            lastHeight = height
            lastMargin = margin
        }

        try {
            if (params.outward) {
                renderNode.setPosition(0, 0, recW, recH)
                recordBackdrop(parent, glassView, offsetX, offsetY, margin, recW, recH)
                layerNode.setPosition(-margin, -margin, width + margin, height + margin)
                val layerCanvas = layerNode.beginRecording(recW, recH)
                try {
                    layerCanvas.drawRenderNode(renderNode)
                } finally {
                    layerNode.endRecording()
                }
                canvas.drawRenderNode(layerNode)
            } else {
                renderNode.setPosition(-margin, -margin, width + margin, height + margin)
                recordBackdrop(parent, glassView, offsetX, offsetY, margin, recW, recH)
                canvas.drawRenderNode(renderNode)
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "GlassLensRenderer RenderNode draw failed, falling back: ${e.message}", e)
            return false
        }
    }

    private fun recordBackdrop(
        parent: View,
        glassView: LiquidGlassView,
        offsetX: Float,
        offsetY: Float,
        margin: Int,
        recW: Int,
        recH: Int
    ) {
        val recordingCanvas = renderNode.beginRecording(recW, recH)
        try {
            recordingCanvas.translate(margin - offsetX, margin - offsetY)
            glassView.isCapturingBackdrop = true
            LiquidGlassView.isGlobalCapturingBackdrop = true
            try {
                backdropCapture.draw(recordingCanvas, parent, glassView)
            } finally {
                LiquidGlassView.isGlobalCapturingBackdrop = false
                glassView.isCapturingBackdrop = false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "recordBackdrop failed: ${e.message}", e)
        } finally {
            renderNode.endRecording()
        }
    }

    private fun updateSampleBounds(
        outward: Boolean,
        margin: Int,
        width: Int,
        height: Int,
        recW: Int,
        recH: Int,
        offsetX: Float,
        offsetY: Float,
        parentW: Int,
        parentH: Int
    ): Boolean {
        val loX: Float
        val loY: Float
        val hiX: Float
        val hiY: Float
        if (outward) {
            val pl = margin - offsetX
            val pt = margin - offsetY
            loX = max(1f, floor(max(1f, pl + 1f) / 4f) * 4f)
            loY = max(1f, floor(max(1f, pt + 1f) / 4f) * 4f)
            hiX = max(loX, min(recW - 1f, ceil(min(recW - 1f, pl + parentW - 1f) / 4f) * 4f))
            hiY = max(loY, min(recH - 1f, ceil(min(recH - 1f, pt + parentH - 1f) / 4f) * 4f))
        } else {
            loX = margin + 1f
            loY = margin + 1f
            hiX = margin + width - 1f
            hiY = margin + height - 1f
        }
        val changed = loX != sampleLoX || loY != sampleLoY || hiX != sampleHiX || hiY != sampleHiY
        sampleLoX = loX
        sampleLoY = loY
        sampleHiX = hiX
        sampleHiY = hiY
        return changed
    }

    private fun buildEffect(
        sh: RuntimeShader,
        p: LensParams,
        margin: Int,
        width: Int,
        height: Int
    ): RenderEffect {
        sh.setFloatUniform("margin", margin.toFloat())
        sh.setFloatUniform("viewSize", width.toFloat(), height.toFloat())
        sh.setFloatUniform("shape1", p.shape1CX, p.shape1CY, p.shape1HW, p.shape1HH)
        sh.setFloatUniform("radii1", p.radius1TL, p.radius1TR, p.radius1BR, p.radius1BL)
        sh.setFloatUniform("shape1L", p.lens1CX, p.lens1CY, p.lens1HW, p.lens1HH)
        sh.setFloatUniform("rimSoft", p.rimSoft)
        sh.setFloatUniform("shape2", p.shape2CX, p.shape2CY, p.shape2HW, p.shape2HH)
        sh.setFloatUniform("radius2", p.radius2)
        sh.setFloatUniform("blendK", p.blendK)
        sh.setFloatUniform("bevel", p.bevel)
        sh.setFloatUniform("refractPx", p.refract)
        sh.setFloatUniform("falloff", p.falloff)
        sh.setFloatUniform("refractDir", if (p.outward) 1f else -1f)
        sh.setFloatUniform("sampleLo", sampleLoX, sampleLoY)
        sh.setFloatUniform("sampleHi", sampleHiX, sampleHiY)
        sh.setFloatUniform("dispersion", p.dispersion)
        sh.setFloatUniform("lightDir", p.lightX, p.lightY)
        sh.setFloatUniform("specStrength", p.spec)
        sh.setFloatUniform("rimBandMax", p.rimBandMax)
        sh.setFloatUniform(
            "tintColor",
            Color.red(p.tint) / 255f,
            Color.green(p.tint) / 255f,
            Color.blue(p.tint) / 255f,
            Color.alpha(p.tint) / 255f
        )
        sh.setFloatUniform("adaptiveTint", if (p.adaptiveTint) 1f else 0f)
        sh.setFloatUniform(
            "glassTint",
            Color.red(p.glassTint) / 255f,
            Color.green(p.glassTint) / 255f,
            Color.blue(p.glassTint) / 255f,
            Color.alpha(p.glassTint) / 255f
        )
        sh.setFloatUniform("dimAmount", p.dim)
        sh.setFloatUniform("satFactor", p.saturation / 100f)
        sh.setFloatUniform("press", p.press)
        sh.setFloatUniform("touchPos", p.touchX, p.touchY)
        sh.setFloatUniform("touchAmp", p.touchAmp)

        val lens = RenderEffect.createRuntimeShaderEffect(sh, "content")
        return if (p.blurRadius > 0.01f) {
            RenderEffect.createChainEffect(
                lens,
                RenderEffect.createBlurEffect(p.blurRadius, p.blurRadius, Shader.TileMode.CLAMP)
            )
        } else {
            lens
        }
    }

    fun release() {
        renderNode.discardDisplayList()
        layerNode.discardDisplayList()
        shader = null
        lastParams = null
    }
}
