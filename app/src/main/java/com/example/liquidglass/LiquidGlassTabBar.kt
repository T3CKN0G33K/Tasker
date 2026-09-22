package com.example.liquidglass

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.tasker.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

open class LiquidGlassTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    class TabItem(val title: CharSequence, val icon: Drawable? = null)

    var onTabSelected: ((index: Int) -> Unit)? = null

    var selectedTintColor: Int? = null
        set(value) {
            if (field != value) {
                field = value
                updateTabStyles()
            }
        }

    private var selected = 0

    var selectedIndex: Int
        get() = selected
        set(value) = selectTab(value, animate = true)

    private class TabHolder(val root: LinearLayout, val icon: ImageView?, val label: TextView)

    private val tabsRow = LinearLayout(context)
    private val tabs = mutableListOf<TabHolder>()

    private val droplet = LiquidGlassView(context)

    private var overLightAppearance = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var dragging = false
    private var settleAnimator: ValueAnimator? = null

    init {
        enablePressEffect = false
        material = GlassMaterial.CLEAR
        glassTint = Color.TRANSPARENT

        val pad = dp(4)
        setPadding(pad, pad, pad, pad)

        tabsRow.orientation = LinearLayout.HORIZONTAL
        addView(tabsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        droplet.apply {
            enablePressEffect = false
            enableBackdropBlur = false
            cornerRadius = 999f
            bevelWidth = dpF(8)
            refractionHeight = dpF(4)
            dispersionStrength = 0.04f
            material = GlassMaterial.CLEAR
            glassTint = Color.argb(35, 255, 255, 255)
            visibility = GONE
        }
        addView(droplet, LayoutParams(0, 0, Gravity.TOP or Gravity.START))

        overLightAppearance = isOverLightBackground
        attrs?.let { parseTabBarAttributes(context, it) }
    }

    private fun parseTabBarAttributes(context: Context, attrs: AttributeSet) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassTabBar)
        try {
            val entriesId = ta.getResourceId(R.styleable.LiquidGlassTabBar_glassTabEntries, 0)
            if (entriesId != 0) setTabs(resources.getTextArray(entriesId).toList())
        } catch (_: Exception) {
            // Ignore if attrs not provided
        } finally {
            ta.recycle()
        }
    }

    fun setTabs(items: List<TabItem>) {
        tabsRow.removeAllViews()
        tabs.clear()

        items.forEach { item ->
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            var icon: ImageView? = null
            val label: TextView
            if (item.icon != null) {
                root.setPadding(dp(2), dp(7), dp(2), dp(7))
                icon = ImageView(context).apply { setImageDrawable(item.icon) }
                root.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
                label = TextView(context).apply {
                    text = item.title
                    textSize = 10f
                    maxLines = 1
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                root.addView(
                    label,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(2) }
                )
            } else {
                root.setPadding(dp(4), dp(12), dp(4), dp(12))
                label = TextView(context).apply {
                    text = item.title
                    textSize = 14f
                    maxLines = 1
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                root.addView(label)
            }
            tabs += TabHolder(root, icon, label)
            tabsRow.addView(root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        selected = 0
        updateTabStyles()
        droplet.visibility = if (tabs.isEmpty()) GONE else VISIBLE
        requestLayout()
    }

    @JvmName("setTabTitles")
    fun setTabs(titles: List<CharSequence>) = setTabs(titles.map { TabItem(it) })

    private fun selectTab(index: Int, animate: Boolean) {
        val clamped = index.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        val changed = clamped != selected
        selected = clamped
        if (changed) updateTabStyles()
        if (animate) animateDropletTo(clamped) else syncDroplet()
        if (changed) onTabSelected?.invoke(clamped)
    }

    private fun updateTabStyles() {
        val selectedColor = selectedTintColor
            ?: if (overLightAppearance) 0xE6000000.toInt() else 0xFFFFFFFF.toInt()
        val normalColor = if (overLightAppearance) 0x8C000000.toInt() else 0xB8FFFFFF.toInt()

        tabs.forEachIndexed { index, tab ->
            val color = if (index == selected) selectedColor else normalColor
            tab.icon?.imageTintList = ColorStateList.valueOf(color)
            tab.label.setTextColor(color)
        }
        droplet.invalidate()
    }

    override fun onAppearanceChanged(isOverLight: Boolean) {
        overLightAppearance = isOverLight
        droplet.overLight = isOverLight
        updateTabStyles()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (settleAnimator?.isRunning != true && !dragging) syncDroplet()
    }

    private fun syncDroplet() {
        val tab = tabs.getOrNull(selected)?.root
        if (tab == null || tab.width <= 0) {
            droplet.visibility = GONE
            return
        }
        droplet.visibility = VISIBLE
        syncDropletSize(tab.width, tab.height)
        droplet.translationX = (tabsRow.left + tab.left - paddingLeft).toFloat()
        droplet.translationY = (tabsRow.top + tab.top - paddingTop).toFloat()
        droplet.scaleX = 1f
        droplet.scaleY = 1f
        droplet.invalidate()
    }

    private fun syncDropletSize(w: Int, h: Int) {
        val lp = droplet.layoutParams as LayoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            droplet.layoutParams = lp
        }
    }

    private fun animateDropletTo(index: Int) {
        val tab = tabs.getOrNull(index)?.root ?: return
        if (tab.width <= 0) return
        syncDropletSize(tab.width, tab.height)

        settleAnimator?.cancel()
        val startX = droplet.translationX
        val targetX = (tabsRow.left + tab.left - paddingLeft).toFloat()
        val targetY = (tabsRow.top + tab.top - paddingTop).toFloat()
        val dist = targetX - startX
        if (abs(dist) < 0.5f) {
            syncDroplet()
            return
        }

        val stretch = 0.22f * min(1f, abs(dist) / (tab.width * 3f))
        val overshoot = OvershootInterpolator(1.1f)

        settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val s = sin(PI.toFloat() * min(t * 1.15f, 1f))
                val sx = 1f + stretch * s
                droplet.scaleX = sx
                droplet.scaleY = 1f - stretch * 0.55f * s
                droplet.translationX =
                    clampDropletX(startX + dist * overshoot.getInterpolation(t), sx)
                droplet.translationY = targetY
                droplet.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!canceled) syncDroplet()
                }
            })
            start()
        }
    }

    private fun clampDropletX(x: Float, scaleX: Float): Float {
        val rowW = tabsRow.width.toFloat()
        val w = droplet.width.toFloat()
        if (rowW <= 0f || w <= 0f) return x
        val bulge = (scaleX - 1f) * w / 2f
        val minX = bulge
        val maxX = rowW - w - bulge
        return if (minX <= maxX) x.coerceIn(minX, maxX) else (rowW - w) / 2f
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.x - downX) > touchSlop) {
                    dragging = true
                    settleAnimator?.cancel()
                    droplet.scaleX = 1.06f
                    droplet.scaleY = 1.06f
                }
                if (dragging) dragDropletTo(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    selectTab(nearestTabIndex(), animate = true)
                } else {
                    selectTab(tabIndexAt(event.x), animate = true)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                animateDropletTo(selected)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dragDropletTo(x: Float) {
        if (tabsRow.width <= 0 || droplet.width <= 0) return
        val local = x - paddingLeft - droplet.width / 2f
        droplet.translationX = clampDropletX(local, droplet.scaleX)
        droplet.invalidate()
    }

    private fun nearestTabIndex(): Int {
        val centerX = droplet.translationX + droplet.width / 2f
        var best = selected
        var bestDist = Float.MAX_VALUE
        tabs.forEachIndexed { index, tab ->
            val tabCenter = tab.root.left + tab.root.width / 2f
            val d = abs(tabCenter - centerX)
            if (d < bestDist) {
                bestDist = d
                best = index
            }
        }
        return best
    }

    private fun tabIndexAt(x: Float): Int {
        val local = x - tabsRow.left
        tabs.forEachIndexed { index, tab ->
            if (local < tab.root.right) return index
        }
        return (tabs.size - 1).coerceAtLeast(0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Int): Float = v * resources.displayMetrics.density
}
