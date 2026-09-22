package com.example.liquidglass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.example.tasker.R
import kotlin.math.*

open class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LiquidGlassView"

        private const val ADAPTIVE_REF_DP = 110f
        private const val ADAPTIVE_BEVEL_RATIO = 0.3f
        private const val ADAPTIVE_REFRACT_RATIO = 0.7f
        private const val ADAPTIVE_RIM_RATIO = 0.05f
        private const val RIM_BAND_MAX_PX = 6f
        private const val ENABLE_PERFORMANCE_LOG = false
        private const val ENABLE_MEMORY_LOG = false

        private const val BACKDROP_SAMPLE_GRID = 8

        internal var isLuminanceSampling = false

        @Volatile
        internal var isGlobalCapturingBackdrop = false
    }

    var enableBackdropBlur = true
    var enableChromaticAberration = true
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }
    var enableChromaticDispersion = false
        set(value) {
            if (field != value) {
                field = value
                dispersionDirty = true
                invalidate()
            }
        }
    var enableShadow = false
    var enableEdgeHighlight = true

    var edgeHighlightBorderWidth = 1.5f
    var edgeHighlightOpacity = 100f

    var blurMethod = BlurMethod.SMART
        set(value) {
            if (field != value) {
                field = value
                enhancedBlurEffect.blurMethod = value
                blurDirty = true
                invalidate()
            }
        }

    var highQualityBlur = false
        set(value) {
            if (field != value) {
                field = value
                enhancedBlurEffect.highQuality = value
                blurDirty = true
                invalidate()
            }
        }

    var downsampleScale = 2
        set(value) {
            val clamped = value.coerceIn(2, 3)
            if (field != clamped) {
                field = clamped
                enhancedBlurEffect.downsampleScale = clamped
                blurDirty = true
                invalidate()
            }
        }

    var globalDownsampleFactor = 1.0f
        set(value) {
            val clamped = value.coerceIn(0.25f, 1.0f)
            if (field != clamped) {
                field = clamped
                blurDirty = true
                aberrationDirty = true
                invalidate()
            }
        }

    var enableOptimizedCapture = false
        set(value) {
            if (field != value) {
                field = value
                enhancedBlurEffect.enableOptimizedCapture = value
                blurDirty = true
                invalidate()
            }
        }

    var aberrationDownsample = 0.5f
        set(value) {
            val clamped = value.coerceIn(0.25f, 1.0f)
            if (field != clamped) {
                field = clamped
                aberrationDirty = true
                invalidate()
            }
        }

    var aberrationRedOffset = 0f
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }

    var aberrationGreenOffset = -0.05f
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }

    var aberrationBlueOffset = -0.1f
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }

    var dispersionThickness = 100f
        set(value) {
            if (field != value) {
                field = value
                dispersionDirty = true
                invalidate()
            }
        }

    var dispersionFactor = 1.5f
        set(value) {
            if (field != value) {
                field = value
                dispersionDirty = true
                invalidate()
            }
        }

    var dispersionGain = 7f
        set(value) {
            if (field != value) {
                field = value
                dispersionDirty = true
                invalidate()
            }
        }

    var dispersionDownsample = 0.5f
        set(value) {
            val clamped = value.coerceIn(0.25f, 1.0f)
            if (field != clamped) {
                field = clamped
                dispersionDirty = true
                invalidate()
            }
        }

    var displacementScale = 70f
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }

    var blurAmount = 0.0625f
        set(value) {
            if (field != value) {
                field = value
                blurDirty = true
                invalidate()
            }
        }

    var saturation = 140f
        set(value) {
            if (field != value) {
                field = value
                updateSaturationFilter()
                invalidate()
            }
        }

    var aberrationIntensity = 2f
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }

    var elasticity = 0.15f

    var enablePressEffect = true

    var pressScale = 0.95f
        set(value) {
            field = value.coerceIn(0.5f, 1.5f)
        }

    private var cornerTL = 999f
    private var cornerTR = 999f
    private var cornerBR = 999f
    private var cornerBL = 999f

    var cornerRadius = 999f
        set(value) {
            val changed = field != value ||
                cornerTL != value || cornerTR != value || cornerBR != value || cornerBL != value
            field = value
            if (changed) {
                cornerTL = value
                cornerTR = value
                cornerBR = value
                cornerBL = value
                onCornersChanged()
            }
        }

    var cornerRadiusTopLeft: Float
        get() = cornerTL
        set(value) = setCornerRadii(value, cornerTR, cornerBR, cornerBL)
    var cornerRadiusTopRight: Float
        get() = cornerTR
        set(value) = setCornerRadii(cornerTL, value, cornerBR, cornerBL)
    var cornerRadiusBottomRight: Float
        get() = cornerBR
        set(value) = setCornerRadii(cornerTL, cornerTR, value, cornerBL)
    var cornerRadiusBottomLeft: Float
        get() = cornerBL
        set(value) = setCornerRadii(cornerTL, cornerTR, cornerBR, value)

    fun setCornerRadii(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) {
        val tl = topLeft.coerceAtLeast(0f)
        val tr = topRight.coerceAtLeast(0f)
        val br = bottomRight.coerceAtLeast(0f)
        val bl = bottomLeft.coerceAtLeast(0f)
        if (tl == cornerTL && tr == cornerTR && br == cornerBR && bl == cornerBL) return
        cornerTL = tl
        cornerTR = tr
        cornerBR = br
        cornerBL = bl
        onCornersChanged()
    }

    private var flatTop = false
    private var flatRight = false
    private var flatBottom = false
    private var flatLeft = false

    fun setFlatEdges(top: Boolean, right: Boolean, bottom: Boolean, left: Boolean) {
        if (top == flatTop && right == flatRight && bottom == flatBottom && left == flatLeft) return
        flatTop = top
        flatRight = right
        flatBottom = bottom
        flatLeft = left
        updateClipPath()
        blurDirty = true
        aberrationDirty = true
        dispersionDirty = true
        invalidate()
    }

    val hasFlatEdges: Boolean
        get() = flatTop || flatRight || flatBottom || flatLeft

    private val flatEdgeExtend = 4096f

    private fun extendFlatEdges(bounds: RectF): RectF {
        if (!hasFlatEdges) return bounds
        val r = RectF(bounds)
        if (flatTop) r.top -= flatEdgeExtend
        if (flatBottom) r.bottom += flatEdgeExtend
        if (flatLeft) r.left -= flatEdgeExtend
        if (flatRight) r.right += flatEdgeExtend
        return r
    }

    private fun onCornersChanged() {
        updateClipPath()
        blurDirty = true
        aberrationDirty = true
        dispersionDirty = true
        invalidate()
    }

    internal fun cornerRadiiPx(inset: Float = 0f): FloatArray {
        val cap = if (width > 0 && height > 0) min(width, height) / 2f else Float.MAX_VALUE
        val tl = (cornerTL.coerceAtMost(cap) - inset).coerceAtLeast(0f)
        val tr = (cornerTR.coerceAtMost(cap) - inset).coerceAtLeast(0f)
        val br = (cornerBR.coerceAtMost(cap) - inset).coerceAtLeast(0f)
        val bl = (cornerBL.coerceAtMost(cap) - inset).coerceAtLeast(0f)
        return floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
    }

    var overLight = false
        set(value) {
            if (field != value) {
                field = value
                blurDirty = true
                aberrationDirty = true
                invalidate()
            }
        }

    var displacementMode = DisplacementMode.STANDARD
        set(value) {
            if (field != value) {
                field = value
                aberrationDirty = true
                invalidate()
            }
        }

    // Default to false for 100% crash-free Compose compatibility
    var useShaderPipeline = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) ensureDisplacementMaps()
                updateSensorRegistration()
                blurDirty = true
                aberrationDirty = true
                invalidate()
            }
        }

    var material = GlassMaterial.REGULAR
        set(value) {
            if (field != value) {
                field = value
                updateAdaptiveMeter()
                blurDirty = true
                invalidate()
            }
        }

    var bevelWidth = 48f
        set(value) {
            val clamped = value.coerceIn(2f, 200f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var refractionHeight = 160f
        set(value) {
            val clamped = value.coerceIn(0f, 300f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var edgeSoftness = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 40f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var adaptiveLensScale = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var refractionOutward = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var refractionNoFold = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var refractionFalloff = 2f
        set(value) {
            val clamped = value.coerceIn(0f, 4f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var dispersionStrength = 0.10f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var enableSensorHighlight = false
        set(value) {
            if (field != value) {
                field = value
                updateSensorRegistration()
                invalidate()
            }
        }

    var enableAdaptiveTint = false
        set(value) {
            if (field != value) {
                field = value
                updateAdaptiveMeter()
                invalidate()
            }
        }

    var glassTint: Int = Color.TRANSPARENT
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun setGlassTint(color: Int, strength: Float) {
        glassTint = Color.argb(
            (strength.coerceIn(0f, 1f) * 255f).roundToInt(),
            Color.red(color), Color.green(color), Color.blue(color)
        )
    }

    protected open val effectiveGlassTint: Int
        get() = glassTint

    var accessibilityMode = GlassAccessibilityMode.AUTO
        set(value) {
            if (field != value) {
                field = value
                refreshAccessibilityState()
            }
        }

    var glassAppearanceListener: ((isOverLight: Boolean) -> Unit)? = null

    protected open fun onAppearanceChanged(isOverLight: Boolean) {}

    val isOverLightBackground: Boolean
        get() = if (enableAdaptiveTint && material.adaptiveTint) adaptiveOverLight else overLight

    private var lensRenderer: GlassLensRenderer? = null
    private var primaryShape: RectF? = null
    private var primaryShapeCorner = 0f
    private var secondaryShape: RectF? = null
    private var secondaryShapeCorner = 999f
    private var shapeBlendSmoothing = 48f

    private var pressDepth = 0f
    private var pressAnimator: ValueAnimator? = null

    private var luminanceMeter: BackdropLuminanceMeter? = null
    private var adaptiveOverLight = false
    private var adaptiveTintColor = 0x24FFFFFF
    private var lastAppliedTint = 0

    private var adaptiveTintBlender: RuntimeXfermode? = null
    private var adaptiveTintBlenderTried = false
    private var vibrancySatFilter: RuntimeColorFilter? = null
    private var vibrancySatTried = false
    private var vibrancySatFor = Float.NaN
    private var paintFilterIsVibrancy = false

    private var a11yReducedTransparency = false
    private var a11yReducedMotion = false
    private var a11yPowerSave = false
    private val opaquePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val opaqueBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val tintOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val enhancedBlurEffect = EnhancedBlurEffect(this)
    private val chromaticAberrationEffect = ChromaticAberrationEffect()
    private val chromaticDispersionEffect = ChromaticDispersionEffect()
    private val edgeHighlightEffect = EdgeHighlightEffect()

    var chromaticAberrationMode: ChromaticAberrationEffect.PerformanceMode
        get() = chromaticAberrationEffect.performanceMode
        set(value) {
            if (chromaticAberrationEffect.performanceMode != value) {
                chromaticAberrationEffect.performanceMode = value
                aberrationDirty = true
                invalidate()
            }
        }

    var aberrationUseBilinearInterpolation: Boolean
        get() = chromaticAberrationEffect.useBilinearInterpolation
        set(value) {
            if (chromaticAberrationEffect.useBilinearInterpolation != value) {
                chromaticAberrationEffect.useBilinearInterpolation = value
                aberrationDirty = true
                invalidate()
            }
        }

    private var customBackdropCapture: ((RectF) -> Bitmap?)? = null

    var backdropSource: View? = null
        set(value) {
            if (field === value) return
            if (value != null && !isValidBackdropSource(value)) {
                Log.w(TAG, "backdropSource cannot be glass itself or child")
                return
            }
            unregisterBackdropScrollListener()
            field = value
            pendingBackdropSourceId = 0
            if (isAttachedToWindow) registerBackdropScrollListener()
            invalidate()
        }

    private var pendingBackdropSourceId = 0

    private val backdropScrollListener = ViewTreeObserver.OnScrollChangedListener { invalidate() }
    private var registeredBackdropObserver: ViewTreeObserver? = null

    internal val backdropView: View?
        get() = backdropSource ?: (parent as? View ?: rootView)

    private fun isValidBackdropSource(source: View): Boolean {
        if (source === this) return false
        var p = source.parent
        while (p != null) {
            if (p === this) return false
            p = p.parent
        }
        return true
    }

    private fun registerBackdropScrollListener() {
        val source = backdropSource ?: return
        if (registeredBackdropObserver != null) return
        if (!source.isAttachedToWindow) return
        val observer = source.viewTreeObserver
        if (!observer.isAlive) return
        observer.addOnScrollChangedListener(backdropScrollListener)
        registeredBackdropObserver = observer
    }

    private fun unregisterBackdropScrollListener() {
        val observer = registeredBackdropObserver ?: return
        if (observer.isAlive) observer.removeOnScrollChangedListener(backdropScrollListener)
        registeredBackdropObserver = null
    }

    private var displacementMaps: Map<DisplacementMode, Bitmap>? = null
    private var mapGenerationId = 0
    private var mapGenerationPending = false

    private var cachedBackdrop: Bitmap? = null
    private var cachedBlurred: Bitmap? = null
    private var cachedResult: Bitmap? = null

    private var lastBackdropHash: Int = 0
    private var lastBlurRadius: Float = -1f
    private var lastAberrationIntensity: Float = -1f

    private var blurDirty = true
    private var aberrationDirty = true
    private var dispersionDirty = true

    var enableDynamicBackground = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    invalidate()
                }
            }
        }

    data class FrameStats(
        val captureMs: Float,
        val blurMs: Float,
        val effectMs: Float,
        val finalizeMs: Float,
        val totalMs: Float,
        val effectName: String,
        val blurRecomputed: Boolean,
        val effectRecomputed: Boolean,
        val processedWidth: Int,
        val processedHeight: Int,
        val drawFps: Int
    )

    var collectFrameStats = true

    @Volatile
    var lastFrameStats: FrameStats? = null
        private set

    var frameStatsListener: ((FrameStats) -> Unit)? = null

    private var fpsWindowStartNs = 0L
    private var fpsFrameCount = 0
    private var measuredFps = 0

    private var hardwareBlur: HardwareBackdropBlur? = null

    var useHardwareBlurWhenPossible = true
        set(value) {
            if (field != value) {
                field = value
                if (!value) ensureDisplacementMaps()
                updateSensorRegistration()
                blurDirty = true
                aberrationDirty = true
                invalidate()
            }
        }

    var debugApiLevelCap: Int = Int.MAX_VALUE
        set(value) {
            val v = if (value <= 0) Int.MAX_VALUE else value
            if (field != v) {
                field = v
                updateSaturationFilter()
                if (!lensPathLikely()) ensureDisplacementMaps()
                updateSensorRegistration()
                blurDirty = true
                aberrationDirty = true
                invalidate()
            }
        }

    val effectiveApiLevel: Int
        get() = minOf(Build.VERSION.SDK_INT, debugApiLevelCap)

    private var touchX = 0f
    private var touchY = 0f
    private var isPressed = false

    private var basePressScale = 1f
    private var stretchScaleX = 1f
    private var stretchScaleY = 1f

    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    
    private var scaleAnimator: ValueAnimator? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val shapePath = Path()
    private val resultSrcRect = Rect()
    private val resultDstRect = Rect()

    internal var isCapturingBackdrop = false

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)

        attrs?.let { parseAttributes(context, it) }

        updateShadow()
        updateSaturationFilter()

        post {
            maybeGenerateDisplacementMaps()
        }
    }

    private fun parseAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassView)
        try {
            displacementScale = ta.getFloat(R.styleable.LiquidGlassView_displacementScale, displacementScale)
            blurAmount = ta.getFloat(R.styleable.LiquidGlassView_blurAmount, blurAmount)
            saturation = ta.getFloat(R.styleable.LiquidGlassView_saturation, saturation)
            aberrationIntensity = ta.getFloat(R.styleable.LiquidGlassView_aberrationIntensity, aberrationIntensity)
            elasticity = ta.getFloat(R.styleable.LiquidGlassView_elasticity, elasticity)
            cornerRadius = ta.getDimension(R.styleable.LiquidGlassView_cornerRadius, cornerRadius)
            if (ta.hasValue(R.styleable.LiquidGlassView_cornerRadiusTopLeft) ||
                ta.hasValue(R.styleable.LiquidGlassView_cornerRadiusTopRight) ||
                ta.hasValue(R.styleable.LiquidGlassView_cornerRadiusBottomRight) ||
                ta.hasValue(R.styleable.LiquidGlassView_cornerRadiusBottomLeft)
            ) {
                setCornerRadii(
                    ta.getDimension(R.styleable.LiquidGlassView_cornerRadiusTopLeft, cornerRadius),
                    ta.getDimension(R.styleable.LiquidGlassView_cornerRadiusTopRight, cornerRadius),
                    ta.getDimension(R.styleable.LiquidGlassView_cornerRadiusBottomRight, cornerRadius),
                    ta.getDimension(R.styleable.LiquidGlassView_cornerRadiusBottomLeft, cornerRadius)
                )
            }
            bevelWidth = ta.getDimension(R.styleable.LiquidGlassView_bevelWidth, bevelWidth)
            refractionHeight = ta.getDimension(R.styleable.LiquidGlassView_refractionHeight, refractionHeight)
            dispersionStrength = ta.getFloat(R.styleable.LiquidGlassView_dispersionStrength, dispersionStrength)
            edgeSoftness = ta.getDimension(R.styleable.LiquidGlassView_edgeSoftness, edgeSoftness)
            enableSensorHighlight = ta.getBoolean(R.styleable.LiquidGlassView_sensorHighlight, enableSensorHighlight)
            enableAdaptiveTint = ta.getBoolean(R.styleable.LiquidGlassView_adaptiveTint, enableAdaptiveTint)
            adaptiveLensScale = ta.getBoolean(R.styleable.LiquidGlassView_adaptiveLensScale, adaptiveLensScale)
            refractionOutward = ta.getBoolean(R.styleable.LiquidGlassView_refractionOutward, refractionOutward)
            refractionNoFold = ta.getBoolean(R.styleable.LiquidGlassView_refractionNoFold, refractionNoFold)
            refractionFalloff = ta.getFloat(R.styleable.LiquidGlassView_refractionFalloff, refractionFalloff)
            glassTint = ta.getColor(R.styleable.LiquidGlassView_glassTint, glassTint)
            if (ta.hasValue(R.styleable.LiquidGlassView_glassTintStrength)) {
                setGlassTint(glassTint, ta.getFloat(R.styleable.LiquidGlassView_glassTintStrength, 1f))
            }
            material = if (ta.getInt(R.styleable.LiquidGlassView_glassMaterial, 0) == 1) {
                GlassMaterial.CLEAR
            } else {
                GlassMaterial.REGULAR
            }
            val flags = ta.getInt(R.styleable.LiquidGlassView_flatEdges, 0)
            if (flags != 0) {
                setFlatEdges(flags and 1 != 0, flags and 2 != 0, flags and 4 != 0, flags and 8 != 0)
            }
            pendingBackdropSourceId = ta.getResourceId(R.styleable.LiquidGlassView_backdropSourceId, 0)
        } catch (_: Exception) {
            // Safe fallback if attrs not present in layout
        } finally {
            ta.recycle()
        }
    }

    fun setPrimaryShape(rect: RectF?, cornerRadiusPx: Float = cornerRadius) {
        primaryShape = rect?.let { RectF(it) }
        primaryShapeCorner = cornerRadiusPx
        invalidate()
    }

    fun setSecondaryShape(rect: RectF?, cornerRadiusPx: Float = 999f, smoothing: Float = 48f) {
        secondaryShape = rect?.let { RectF(it) }
        secondaryShapeCorner = cornerRadiusPx
        shapeBlendSmoothing = smoothing.coerceIn(0f, 200f)
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (isCapturingBackdrop || isGlobalCapturingBackdrop || isLuminanceSampling) return
        super.draw(canvas)
    }

    private fun updateSaturationFilter() {
        paint.colorFilter = if (saturation != 100f) {
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation / 100f) })
        } else {
            null
        }
        paintFilterIsVibrancy = false
    }

    private fun applySaturationFilter(hardwareCanvas: Boolean) {
        if (saturation == 100f) return
        if (!hardwareCanvas || Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA ||
            debugApiLevelCap < Build.VERSION_CODES.BAKLAVA
        ) {
            if (paintFilterIsVibrancy) updateSaturationFilter()
            return
        }
        if (!vibrancySatTried) {
            vibrancySatTried = true
            vibrancySatFilter = GlassRuntimeEffects.createVibrancyFilter()
        }
        val vib = vibrancySatFilter ?: return
        val factor = saturation / 100f
        if (!paintFilterIsVibrancy || vibrancySatFor != factor) {
            vib.setFloatUniform("satFactor", factor)
            paint.colorFilter = vib
            paintFilterIsVibrancy = true
            vibrancySatFor = factor
        }
    }
    
    private fun generateDisplacementMaps() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val genId = ++mapGenerationId
        mapGenerationPending = true
        Thread({
            val maps = DisplacementMapGenerator.generateStandardMaps(w, h)
            post {
                if (genId != mapGenerationId || w != width || h != height) {
                    maps.values.forEach { it.recycle() }
                    if (genId == mapGenerationId) mapGenerationPending = false
                    return@post
                }
                displacementMaps?.values?.forEach { it.recycle() }
                displacementMaps = maps
                mapGenerationPending = false
                aberrationDirty = true
                invalidate()
            }
        }, "LiquidGlass-DispMap").start()
    }

    private fun lensPathLikely(): Boolean =
        useShaderPipeline && useHardwareBlurWhenPossible &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            debugApiLevelCap >= Build.VERSION_CODES.TIRAMISU &&
            GlassLensRenderer.isSupported() && lensRenderer?.isAvailable != false

    private fun maybeGenerateDisplacementMaps() {
        if (lensPathLikely()) return
        generateDisplacementMaps()
    }

    private fun ensureDisplacementMaps() {
        if (displacementMaps == null && !mapGenerationPending && width > 0 && height > 0) {
            generateDisplacementMaps()
        }
    }
    
    private fun updateShadow() {
        val shadowRadius = if (overLight) 70f else 40f
        val shadowAlpha = if (overLight) 0.75f else 0.25f
        
        shadowPaint.color = Color.argb((shadowAlpha * 255).toInt(), 0, 0, 0)
        shadowPaint.maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w > 0 && h > 0) {
            maybeGenerateDisplacementMaps()
        }

        updateClipPath()

        cachedBackdrop?.recycle()
        cachedBlurred?.recycle()
        cachedResult?.recycle()
        cachedBackdrop = null
        cachedBlurred = null
        cachedResult = null

        lastBackdropHash = 0
        blurDirty = true
        aberrationDirty = true
    }

    private fun updateClipPath() {
        if (width > 0 && height > 0) {
            clipPath.reset()
            val rect = extendFlatEdges(RectF(0f, 0f, width.toFloat(), height.toFloat()))
            clipPath.addRoundRect(rect, cornerRadiiPx(), Path.Direction.CW)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        if (collectFrameStats) {
            val now = System.nanoTime()
            if (fpsWindowStartNs == 0L) fpsWindowStartNs = now
            fpsFrameCount++
            val elapsed = now - fpsWindowStartNs
            if (elapsed >= 1_000_000_000L) {
                measuredFps = (fpsFrameCount * 1_000_000_000L / elapsed).toInt()
                fpsFrameCount = 0
                fpsWindowStartNs = now
            }
        }

        drawShadow(canvas)
        drawGlassEffect(canvas)
    }
    
    private fun drawShadow(canvas: Canvas) {
        if (!enableShadow) return

        val shadowOffset = if (overLight) 16f else 12f
        val rect = RectF(0f, shadowOffset, width.toFloat(), height.toFloat() + shadowOffset)
        shapePath.reset()
        shapePath.addRoundRect(rect, cornerRadiiPx(), Path.Direction.CW)
        canvas.drawPath(shapePath, shadowPaint)
    }
    
    private fun drawGlassEffect(canvas: Canvas) {
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val calculatedBlurRadius = (if (overLight) 12f else 4f) + blurAmount * 32f

        if (shouldRenderOpaque()) {
            drawOpaqueFallback(canvas)
            return
        }

        if (tryDrawLensGlass(canvas, calculatedBlurRadius)) {
            if (enableDynamicBackground) {
                invalidate()
            }
            return
        }

        if (tryDrawHardwareBlur(canvas, calculatedBlurRadius)) {
            drawGlassTintOverlay(canvas)
            if (enableEdgeHighlight) {
                drawEdgeHighlight(canvas, bounds)
            }
            if (enableDynamicBackground) {
                invalidate()
            }
            return
        }

        if (enableChromaticAberration && displacementMaps == null) {
            ensureDisplacementMaps()
        }

        renderGlassEffectSync(bounds, calculatedBlurRadius)

        cachedResult?.let {
            if (!it.isRecycled) {
                val saveCount = canvas.save()
                canvas.clipPath(clipPath)

                resultSrcRect.set(0, 0, it.width, it.height)
                resultDstRect.set(0, 0, width, height)
                applySaturationFilter(canvas.isHardwareAccelerated)
                canvas.drawBitmap(it, resultSrcRect, resultDstRect, paint)

                var perPixelTinted = false
                if (material.adaptiveTint && enableAdaptiveTint &&
                    canvas.isHardwareAccelerated && GlassRuntimeEffects.isSupported &&
                    debugApiLevelCap >= Build.VERSION_CODES.BAKLAVA
                ) {
                    if (!adaptiveTintBlenderTried) {
                        adaptiveTintBlenderTried = true
                        adaptiveTintBlender = GlassRuntimeEffects.createAdaptiveTintBlender()
                    }
                    val blender = adaptiveTintBlender
                    if (blender != null) {
                        tintOverlayPaint.color = Color.WHITE
                        tintOverlayPaint.xfermode = blender
                        canvas.drawPath(clipPath, tintOverlayPaint)
                        tintOverlayPaint.xfermode = null
                        perPixelTinted = true
                    }
                }
                if (!perPixelTinted) {
                    val tint = currentTintColor()
                    if (Color.alpha(tint) > 0) {
                        tintOverlayPaint.color = tint
                        canvas.drawPath(clipPath, tintOverlayPaint)
                    }
                }
                drawGlassTintOverlay(canvas)

                canvas.restoreToCount(saveCount)
            }
        }

        if (enableEdgeHighlight) {
            drawEdgeHighlight(canvas, bounds)
        }

        if (enableDynamicBackground) {
            invalidate()
        }
    }

    private fun tryDrawLensGlass(canvas: Canvas, blurRadius: Float): Boolean {
        if (!useShaderPipeline || !useHardwareBlurWhenPossible) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (debugApiLevelCap < Build.VERSION_CODES.TIRAMISU) return false
        if (!GlassLensRenderer.isSupported()) return false
        if (!canvas.isHardwareAccelerated) return false
        if (customBackdropCapture != null) return false

        val renderer = lensRenderer ?: GlassLensRenderer().also { lensRenderer = it }
        if (!renderer.isAvailable) return false

        val startNs = if (collectFrameStats) System.nanoTime() else 0L

        val w = width.toFloat()
        val h = height.toFloat()

        val p1 = primaryShape
        val s1cx = p1?.centerX() ?: (w / 2f)
        val s1cy = p1?.centerY() ?: (h / 2f)
        val s1hw = ((p1?.width() ?: w) / 2f).coerceAtLeast(1f)
        val s1hh = ((p1?.height() ?: h) / 2f).coerceAtLeast(1f)
        val rCap = min(s1hw, s1hh)
        val r1TL: Float
        val r1TR: Float
        val r1BR: Float
        val r1BL: Float
        if (p1 != null) {
            val r = primaryShapeCorner.coerceIn(0f, rCap)
            r1TL = r
            r1TR = r
            r1BR = r
            r1BL = r
        } else {
            r1TL = cornerTL.coerceIn(0f, rCap)
            r1TR = cornerTR.coerceIn(0f, rCap)
            r1BR = cornerBR.coerceIn(0f, rCap)
            r1BL = cornerBL.coerceIn(0f, rCap)
        }

        val p2 = secondaryShape
        val s2hw = if (p2 != null) (p2.width() / 2f).coerceAtLeast(1f) else 0f
        val s2hh = if (p2 != null) (p2.height() / 2f).coerceAtLeast(1f) else 0f
        val r2 = if (p2 != null) secondaryShapeCorner.coerceIn(0f, min(s2hw, s2hh)) else 0f

        val bevelEff: Float
        val refractEff: Float
        val rimBandMax: Float
        if (adaptiveLensScale) {
            var minDim = 2f * min(s1hw, s1hh)
            if (p2 != null) minDim = min(minDim, 2f * min(s2hw, s2hh))
            val refPx = ADAPTIVE_REF_DP * resources.displayMetrics.density
            val refractCap = ADAPTIVE_REFRACT_RATIO * minDim * min(1f, minDim / refPx)
            bevelEff = min(bevelWidth, minDim * ADAPTIVE_BEVEL_RATIO).coerceAtLeast(2f)
            refractEff = min(refractionHeight, refractCap)
            rimBandMax = min(RIM_BAND_MAX_PX, minDim * ADAPTIVE_RIM_RATIO).coerceAtLeast(2f)
        } else {
            bevelEff = bevelWidth
            refractEff = refractionHeight
            rimBandMax = RIM_BAND_MAX_PX
        }
        val falloff = (refractionFalloff * 100f).toInt() / 100f
        val refractFinal = if (refractionNoFold) {
            val monoCap = if (falloff > 0.001f) {
                val gB = 5f.pow(-falloff)
                bevelEff * (1f - gB) / (4f * falloff)
            } else {
                bevelEff * 0.5f
            }
            min(refractEff, monoCap)
        } else {
            refractEff
        }

        var l1cx = s1cx
        var l1cy = s1cy
        var l1hw = s1hw
        var l1hh = s1hh
        if (hasFlatEdges) {
            val half = flatEdgeExtend / 2f
            if (flatTop) { l1cy -= half; l1hh += half }
            if (flatBottom) { l1cy += half; l1hh += half }
            if (flatLeft) { l1cx -= half; l1hw += half }
            if (flatRight) { l1cx += half; l1hw += half }
        }

        val disp = when {
            enableChromaticDispersion ->
                (dispersionStrength * (dispersionGain / 7f)).coerceIn(0f, 0.9f)
            enableChromaticAberration && aberrationIntensity > 0f ->
                (dispersionStrength * (aberrationIntensity / 2f)).coerceIn(0f, 0.9f)
            else -> 0f
        }

        val spec = if (enableEdgeHighlight) (edgeHighlightOpacity / 100f) * material.specBoost else 0f

        val sensorActive = enableSensorHighlight && !a11yReducedMotion && !a11yPowerSave
        val lx: Float
        val ly: Float
        if (sensorActive) {
            lx = (LightSourceController.lightDirX * 200f).toInt() / 200f
            ly = (LightSourceController.lightDirY * 200f).toInt() / 200f
        } else {
            lx = LightSourceController.DEFAULT_X
            ly = LightSourceController.DEFAULT_Y
        }

        val radius = if (enableBackdropBlur) {
            ((blurAmount * 32f * material.blurScale) * 2f).toInt() / 2f
        } else {
            0f
        }

        val press = (pressDepth * 100f).toInt() / 100f
        val tAmp = if (isPressed || press > 0f) press else 0f
        val tx = (touchX * 2f).toInt() / 2f
        val ty = (touchY * 2f).toInt() / 2f

        val rimSoft = (edgeSoftness * 2f).toInt() / 2f
        val margin = computeLensMargin(radius, if (refractionOutward) refractFinal + rimSoft + 8f else 0f)

        val adaptivePerPixel = material.adaptiveTint && enableAdaptiveTint

        val params = GlassLensRenderer.LensParams(
            blurRadius = radius,
            shape1CX = s1cx, shape1CY = s1cy, shape1HW = s1hw, shape1HH = s1hh,
            radius1TL = r1TL, radius1TR = r1TR, radius1BR = r1BR, radius1BL = r1BL,
            lens1CX = l1cx, lens1CY = l1cy, lens1HW = l1hw, lens1HH = l1hh,
            rimSoft = rimSoft,
            shape2CX = p2?.centerX() ?: 0f, shape2CY = p2?.centerY() ?: 0f,
            shape2HW = s2hw, shape2HH = s2hh, radius2 = r2,
            blendK = if (p2 != null) shapeBlendSmoothing else 0f,
            bevel = bevelEff,
            refract = refractFinal,
            falloff = falloff,
            outward = refractionOutward,
            rimBandMax = rimBandMax,
            dispersion = disp,
            lightX = lx, lightY = ly,
            spec = spec,
            tint = if (adaptivePerPixel) 0 else currentTintColor(),
            adaptiveTint = adaptivePerPixel,
            glassTint = effectiveGlassTint,
            dim = material.dimAmount,
            saturation = saturation,
            press = press,
            touchX = tx, touchY = ty, touchAmp = tAmp
        )

        val ok = renderer.draw(canvas, this, params, margin)

        if (ok && collectFrameStats) {
            val totalMs = (System.nanoTime() - startNs) / 1_000_000f
            val stats = FrameStats(
                captureMs = 0f,
                blurMs = 0f,
                effectMs = 0f,
                finalizeMs = 0f,
                totalMs = totalMs,
                effectName = "GPU Lens",
                blurRecomputed = false,
                effectRecomputed = false,
                processedWidth = width,
                processedHeight = height,
                drawFps = measuredFps
            )
            lastFrameStats = stats
            frameStatsListener?.invoke(stats)
        }
        return ok
    }

    private fun computeLensMargin(blurRadius: Float, refractReach: Float): Int {
        val need = max(max(blurRadius * 3f, 32f), refractReach)
        return (((need.toInt() + 15) / 16) * 16).coerceAtLeast(32)
    }

    private fun drawGlassTintOverlay(canvas: Canvas) {
        val tint = effectiveGlassTint
        if (Color.alpha(tint) == 0) return
        tintOverlayPaint.color = tint
        canvas.drawPath(clipPath, tintOverlayPaint)
    }

    private fun currentTintColor(): Int {
        if (material.adaptiveTint && enableAdaptiveTint) {
            return adaptiveTintColor
        }
        return if (overLight) 0x33000000 else material.baseTint
    }

    private fun onLuminanceSample(luminance: Float) {
        val s = ((luminance - 0.35f) / 0.40f).coerceIn(0f, 1f)
        val e = s * s * (3f - 2f * s)

        val channel = ((1f - e) * 255f).toInt()
        val alpha = ((0.14f + 0.08f * e) * 255f).toInt()
        adaptiveTintColor = Color.argb(alpha, channel, channel, channel)

        val meter = luminanceMeter
        if (meter != null && meter.isOverLight != adaptiveOverLight) {
            adaptiveOverLight = meter.isOverLight
            onAppearanceChanged(adaptiveOverLight)
            glassAppearanceListener?.invoke(adaptiveOverLight)
        }

        if (adaptiveTintColor != lastAppliedTint) {
            lastAppliedTint = adaptiveTintColor
            invalidate()
        }
    }

    private fun updateAdaptiveMeter() {
        val want = isAttachedToWindow && enableAdaptiveTint && material.adaptiveTint
        if (want) {
            val meter = luminanceMeter
                ?: BackdropLuminanceMeter(this) { onLuminanceSample(it) }.also { luminanceMeter = it }
            meter.intervalMs = if (a11yPowerSave) 1000L else 350L
            meter.start()
        } else {
            luminanceMeter?.stop()
        }
    }

    private fun updateSensorRegistration() {
        val want = isAttachedToWindow && enableSensorHighlight && useShaderPipeline &&
            useHardwareBlurWhenPossible &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            debugApiLevelCap >= Build.VERSION_CODES.TIRAMISU &&
            GlassLensRenderer.isSupported() &&
            !a11yReducedMotion && !a11yPowerSave
        if (want) {
            LightSourceController.register(this)
        } else {
            LightSourceController.unregister(this)
        }
    }

    fun refreshAccessibilityState() {
        a11yReducedTransparency = GlassAccessibility.prefersReducedTransparency(context)
        a11yReducedMotion = GlassAccessibility.prefersReducedMotion(context)
        a11yPowerSave = GlassAccessibility.isPowerSaveMode(context)
        updateSensorRegistration()
        updateAdaptiveMeter()
        blurDirty = true
        aberrationDirty = true
        invalidate()
    }

    private fun shouldRenderOpaque(): Boolean = when (accessibilityMode) {
        GlassAccessibilityMode.FORCE_OPAQUE -> true
        GlassAccessibilityMode.FORCE_FULL -> false
        GlassAccessibilityMode.AUTO -> a11yReducedTransparency
    }

    private fun drawOpaqueFallback(canvas: Canvas) {
        val over = isOverLightBackground
        val base = if (over) 0xFFF2F2F6.toInt() else 0xFF2A2A2E.toInt()
        val tint = effectiveGlassTint
        opaquePaint.color = blendOpaque(base, tint, Color.alpha(tint) / 255f * 0.45f)
        opaqueBorderPaint.color = if (over) 0x33000000 else 0x40FFFFFF
        val rect = RectF(0.75f, 0.75f, width - 0.75f, height - 0.75f)
        shapePath.reset()
        shapePath.addRoundRect(rect, cornerRadiiPx(0.75f), Path.Direction.CW)
        canvas.drawPath(shapePath, opaquePaint)
        canvas.drawPath(shapePath, opaqueBorderPaint)
    }

    private fun blendOpaque(base: Int, tint: Int, ratio: Float): Int {
        if (ratio <= 0f) return base
        val k = ratio.coerceIn(0f, 1f)
        fun mix(b: Int, t: Int) = (b + (t - b) * k).roundToInt().coerceIn(0, 255)
        return Color.argb(
            255,
            mix(Color.red(base), Color.red(tint)),
            mix(Color.green(base), Color.green(tint)),
            mix(Color.blue(base), Color.blue(tint))
        )
    }

    private fun tryDrawHardwareBlur(canvas: Canvas, blurRadius: Float): Boolean {
        if (!useHardwareBlurWhenPossible) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (debugApiLevelCap < Build.VERSION_CODES.S) return false
        if (!canvas.isHardwareAccelerated) return false
        if (enableChromaticDispersion) return false
        if (customBackdropCapture != null) return false

        val wantsAberration = enableChromaticAberration && aberrationIntensity > 0f
        var aberrationParams: HardwareBackdropBlur.AberrationParams? = null
        if (wantsAberration) {
            if (!HardwareBackdropBlur.supportsRuntimeShader()) return false
            if (debugApiLevelCap < Build.VERSION_CODES.TIRAMISU) return false
            val map = displacementMaps?.get(displacementMode) ?: return false
            val effectiveScale = if (overLight) displacementScale * 0.5f else displacementScale
            aberrationParams = HardwareBackdropBlur.AberrationParams(
                displacementMap = map,
                displacementScale = effectiveScale,
                redOffset = aberrationRedOffset * aberrationIntensity,
                greenOffset = aberrationGreenOffset * aberrationIntensity,
                blueOffset = aberrationBlueOffset * aberrationIntensity
            )
        }

        val startNs = if (collectFrameStats) System.nanoTime() else 0L

        val renderer = hardwareBlur ?: HardwareBackdropBlur().also { hardwareBlur = it }
        renderer.debugApiLevelCap = debugApiLevelCap
        val effectiveRadius = if (enableBackdropBlur) blurRadius else 0f
        val ok = renderer.draw(canvas, this, effectiveRadius, saturation, clipPath, aberrationParams)

        if (ok && collectFrameStats) {
            val totalMs = (System.nanoTime() - startNs) / 1_000_000f
            val stats = FrameStats(
                captureMs = 0f,
                blurMs = 0f,
                effectMs = 0f,
                finalizeMs = 0f,
                totalMs = totalMs,
                effectName = if (aberrationParams != null) "GPU Blur+CA" else "GPU Blur",
                blurRecomputed = false,
                effectRecomputed = false,
                processedWidth = width,
                processedHeight = height,
                drawFps = measuredFps
            )
            lastFrameStats = stats
            frameStatsListener?.invoke(stats)
        }
        return ok
    }

    private fun drawEdgeHighlight(canvas: Canvas, bounds: RectF) {
        val touchOffset = PointF(touchOffsetX, touchOffsetY)
        edgeHighlightEffect.draw(
            canvas = canvas,
            bounds = extendFlatEdges(bounds),
            cornerRadii = cornerRadiiPx(),
            mouseOffset = touchOffset,
            overLight = overLight,
            borderWidth = edgeHighlightBorderWidth,
            opacity = edgeHighlightOpacity,
            apiLevelCap = debugApiLevelCap
        )
    }
    
    fun setCustomBackdropCapture(capture: (RectF) -> Bitmap?) {
        customBackdropCapture = capture
    }

    private fun renderGlassEffectSync(bounds: RectF, blurRadius: Float) {
        val collectTiming = collectFrameStats || ENABLE_PERFORMANCE_LOG
        var blurRecomputed = false
        var effectRecomputed = false
        var t1 = 0L
        var t2 = 0L
        var t3 = 0L
        var t4 = 0L
        var t5 = 0L
        if (collectTiming) {
            t1 = System.nanoTime()
        }

        if (enableOptimizedCapture) {
            enhancedBlurEffect.cornerRadius = max(max(cornerTL, cornerTR), max(cornerBR, cornerBL))
            enhancedBlurEffect.cornerRadii = cornerRadiiPx()
            enhancedBlurEffect.captureMargin = blurRadius * 2f
        }

        var backdrop = if (customBackdropCapture != null) {
            customBackdropCapture?.invoke(bounds)?.let { full ->
                if (globalDownsampleFactor < 1.0f) {
                    val scaledWidth = (full.width * globalDownsampleFactor).toInt().coerceAtLeast(1)
                    val scaledHeight = (full.height * globalDownsampleFactor).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(full, scaledWidth, scaledHeight, true)
                    if (scaled != full) full.recycle()
                    scaled
                } else {
                    full
                }
            }
        } else {
            enhancedBlurEffect.captureBackdrop(bounds, globalDownsampleFactor)
        }

        if (backdrop == null) {
            val fallbackWidth = (width * globalDownsampleFactor).toInt().coerceAtLeast(1)
            val fallbackHeight = (height * globalDownsampleFactor).toInt().coerceAtLeast(1)
            backdrop = Bitmap.createBitmap(fallbackWidth, fallbackHeight, Bitmap.Config.ARGB_8888)
            backdrop.eraseColor(Color.argb(200, 255, 255, 255))
        }

        val backdropChanged = if (enableDynamicBackground) {
            true
        } else {
            val backdropHash = computeBackdropSignature(backdrop)
            if (backdropHash != lastBackdropHash) {
                lastBackdropHash = backdropHash
                true
            } else {
                false
            }
        }
        if (backdropChanged) {
            cachedBackdrop?.recycle()
            cachedBackdrop = backdrop
            blurDirty = true
            if (enableAdaptiveTint && material.adaptiveTint) {
                luminanceMeter?.submit(BackdropLuminanceMeter.measureBitmap(backdrop))
            }
        } else {
            backdrop.recycle()
        }

        if (collectTiming) t2 = System.nanoTime()

        val blurChanged = blurRadius != lastBlurRadius
        val aberrationChanged = aberrationIntensity != lastAberrationIntensity

        if (enableBackdropBlur && (blurDirty || blurChanged)) {
            cachedBackdrop?.let { backdropBitmap ->
                if (cachedBlurred != cachedBackdrop) {
                    cachedBlurred?.recycle()
                }
                cachedBlurred = enhancedBlurEffect.applyEffect(backdropBitmap, blurRadius)
                lastBlurRadius = blurRadius
                aberrationDirty = true
                blurRecomputed = true
            }
            blurDirty = false
        } else if (!enableBackdropBlur && cachedBackdrop != null) {
            if (cachedBlurred != cachedBackdrop) {
                cachedBlurred?.recycle()
            }
            cachedBlurred = cachedBackdrop
            aberrationDirty = true
        }

        if (collectTiming) t3 = System.nanoTime()

        val displacementMap = displacementMaps?.get(displacementMode)

        if (enableChromaticDispersion) {
            cachedBlurred?.let { blurred ->
                val dispersed = chromaticDispersionEffect.apply(
                    source = blurred,
                    refThickness = dispersionThickness,
                    refFactor = dispersionFactor,
                    refDispersion = dispersionGain,
                    downscale = dispersionDownsample,
                    cornerRadius = cornerRadius,
                    cornerRadii = cornerRadiiPx()
                )

                cachedResult?.recycle()
                cachedResult = dispersed
                effectRecomputed = true
            }

            if (collectTiming) t4 = System.nanoTime()

            aberrationDirty = false
            dispersionDirty = false
        }
        else if (enableChromaticAberration && (aberrationDirty || aberrationChanged) && aberrationIntensity > 0 && displacementMap != null) {
            cachedBlurred?.let { blurred ->
                val aberrated = chromaticAberrationEffect.apply(
                    source = blurred,
                    displacementMap = displacementMap,
                    intensity = aberrationIntensity,
                    scale = displacementScale,
                    downscale = aberrationDownsample,
                    redOffset = aberrationRedOffset,
                    greenOffset = aberrationGreenOffset,
                    blueOffset = aberrationBlueOffset
                )

                if (collectTiming) t4 = System.nanoTime()

                cachedResult?.recycle()
                cachedResult = aberrated
                lastAberrationIntensity = aberrationIntensity
                effectRecomputed = true
            }
            aberrationDirty = false
            dispersionDirty = false
        }
        else if (aberrationDirty || dispersionDirty || !enableChromaticAberration) {
            if (collectTiming) t4 = System.nanoTime()

            cachedBlurred?.let { blurred ->
                cachedResult?.recycle()
                cachedResult = blurred.copy(blurred.config ?: Bitmap.Config.ARGB_8888, true)
            }
            aberrationDirty = false
            dispersionDirty = false
        }

        if (collectTiming) {
            t5 = System.nanoTime()
            if (t4 == 0L) t4 = t3

            val captureTime = (t2 - t1) / 1_000_000f
            val blurTime = (t3 - t2) / 1_000_000f
            val aberrationTime = (t4 - t3) / 1_000_000f
            val finalizeTime = (t5 - t4) / 1_000_000f
            val totalTime = (t5 - t1) / 1_000_000f

            val effectName = when {
                enableChromaticDispersion -> "Dispersion"
                enableChromaticAberration -> "Aberration"
                else -> "None"
            }

            if (collectFrameStats) {
                val stats = FrameStats(
                    captureMs = captureTime,
                    blurMs = blurTime,
                    effectMs = aberrationTime,
                    finalizeMs = finalizeTime,
                    totalMs = totalTime,
                    effectName = effectName,
                    blurRecomputed = blurRecomputed,
                    effectRecomputed = effectRecomputed,
                    processedWidth = cachedBackdrop?.width ?: 0,
                    processedHeight = cachedBackdrop?.height ?: 0,
                    drawFps = measuredFps
                )
                lastFrameStats = stats
                frameStatsListener?.invoke(stats)
            }
        }
    }

    private fun computeBackdropSignature(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0

        var hash = w * 31 + h
        val stepX = (w / BACKDROP_SAMPLE_GRID).coerceAtLeast(1)
        val stepY = (h / BACKDROP_SAMPLE_GRID).coerceAtLeast(1)
        var y = stepY / 2
        while (y < h) {
            var x = stepX / 2
            while (x < w) {
                hash = hash * 31 + bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                isPressed = true
                updateTouchOffset(event.x, event.y)
                if (enablePressEffect) {
                    animateScale(true)
                    animatePress(true)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                updateTouchOffset(event.x, event.y)
                if (enablePressEffect) {
                    updateElasticScale()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                touchOffsetX = 0f
                touchOffsetY = 0f
                animateScale(false)
                animatePress(false)
                if (event.action == MotionEvent.ACTION_UP && isClickable &&
                    event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                ) {
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animatePress(pressed: Boolean) {
        if (a11yReducedMotion) {
            pressDepth = if (pressed) 1f else 0f
            invalidate()
            return
        }
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressDepth, if (pressed) 1f else 0f).apply {
            duration = if (pressed) 180 else 320
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                pressDepth = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateTouchOffset(x: Float, y: Float) {
        val centerX = width / 2f
        val centerY = height / 2f

        touchOffsetX = ((x - centerX) / width) * 200f
        touchOffsetY = ((y - centerY) / height) * 200f
    }
    
    private fun applyGlassScale() {
        scaleX = basePressScale * stretchScaleX
        scaleY = basePressScale * stretchScaleY
    }

    private fun updateElasticScale() {
        val centerX = width / 2f
        val centerY = height / 2f

        val deltaX = touchX - centerX
        val deltaY = touchY - centerY
        val centerDistance = sqrt(deltaX * deltaX + deltaY * deltaY)

        if (centerDistance < 1f) {
            stretchScaleX = 1f
            stretchScaleY = 1f
            applyGlassScale()
            return
        }

        val normalizedX = deltaX / centerDistance
        val normalizedY = deltaY / centerDistance
        val stretchIntensity = min(centerDistance / 300f, 1f) * elasticity

        stretchScaleX = max(0.8f, 1f + abs(normalizedX) * stretchIntensity * 0.3f - abs(normalizedY) * stretchIntensity * 0.15f)
        stretchScaleY = max(0.8f, 1f + abs(normalizedY) * stretchIntensity * 0.3f - abs(normalizedX) * stretchIntensity * 0.15f)

        applyGlassScale()
        invalidate()
    }

    private fun animateScale(pressed: Boolean) {
        scaleAnimator?.cancel()

        val target = if (pressed) pressScale else 1f
        val startBase = basePressScale
        val startStretchX = stretchScaleX
        val startStretchY = stretchScaleY

        scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                basePressScale = startBase + (target - startBase) * f
                if (!pressed) {
                    stretchScaleX = startStretchX + (1f - startStretchX) * f
                    stretchScaleY = startStretchY + (1f - startStretchY) * f
                }
                applyGlassScale()
                invalidate()
            }
            start()
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        post {
            if (displacementMaps == null && width > 0 && height > 0) {
                maybeGenerateDisplacementMaps()
            }
        }

        refreshAccessibilityState()

        if (backdropSource == null && pendingBackdropSourceId != 0) {
            val id = pendingBackdropSourceId
            rootView?.findViewById<View>(id)?.let { backdropSource = it }
                ?: Log.w(TAG, "backdropSourceId not found")
            pendingBackdropSourceId = 0
        }
        registerBackdropScrollListener()

        lastBackdropHash = 0
        blurDirty = true
        aberrationDirty = true
        dispersionDirty = true
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        scaleAnimator?.cancel()
        pressAnimator?.cancel()
        basePressScale = 1f
        stretchScaleX = 1f
        stretchScaleY = 1f
        applyGlassScale()
        pressDepth = 0f
        enhancedBlurEffect.release()

        LightSourceController.unregister(this)
        luminanceMeter?.stop()

        unregisterBackdropScrollListener()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lensRenderer?.release()
        }
        lensRenderer = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hardwareBlur?.release()
        }
        hardwareBlur = null

        chromaticAberrationEffect.cleanup()
        edgeHighlightEffect.cleanup()

        cachedBackdrop?.recycle()
        cachedBlurred?.recycle()
        cachedResult?.recycle()

        cachedBackdrop = null
        cachedBlurred = null
        cachedResult = null
    }
}
