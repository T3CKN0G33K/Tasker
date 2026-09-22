package com.example.liquidglass

import android.graphics.Canvas
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup

internal class BackdropCapture {

    private companion object {
        private const val TAG = "BackdropCapture"

        @Volatile
        private var isCapturing = false

        var depth = 0
    }

    private val hostLocation = IntArray(2)
    private val childLocation = IntArray(2)
    private val nestedHidden = ArrayList<View>()

    fun draw(canvas: Canvas, source: View, glass: View) {
        if (isCapturing) return
        isCapturing = true
        try {
            drawLevel(canvas, source, glass)
        } catch (e: Throwable) {
            Log.e(TAG, "BackdropCapture draw failed: ${e.message}", e)
        } finally {
            isCapturing = false
        }
    }

    private fun setViewVisibility(view: View, visibility: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.setTransitionVisibility(visibility)
        } else {
            view.visibility = visibility
        }
    }

    private fun drawLevel(canvas: Canvas, host: View, glass: View) {
        if (host === glass) return
        val branch = childOnPathTo(host, glass)
        if (branch === glass) {
            drawContent(canvas, host, glass)
            return
        }
        drawContent(canvas, host, branch ?: glass)
        if (branch == null) return

        host.getLocationOnScreen(hostLocation)
        branch.getLocationOnScreen(childLocation)
        val dx = (childLocation[0] - hostLocation[0]).toFloat()
        val dy = (childLocation[1] - hostLocation[1]).toFloat()
        val clip = (host as? ViewGroup)?.clipChildren != false

        val save = canvas.save()
        if (clip) canvas.clipRect(0f, 0f, host.width.toFloat(), host.height.toFloat())
        canvas.translate(dx, dy)
        if (clip) canvas.clipRect(0f, 0f, branch.width.toFloat(), branch.height.toFloat())
        drawLevel(canvas, branch, glass)
        canvas.restoreToCount(save)
    }

    private fun drawContent(canvas: Canvas, host: View, hidden: View) {
        val save = canvas.save()
        canvas.translate(-host.scrollX.toFloat(), -host.scrollY.toFloat())
        setViewVisibility(hidden, View.INVISIBLE)
        val nested = depth > 0
        if (nested) hideOtherGlass(host, hidden)
        depth++
        try {
            host.draw(canvas)
        } catch (e: Throwable) {
            Log.e(TAG, "drawContent failed on ${host.javaClass.simpleName}: ${e.message}", e)
        } finally {
            depth--
            if (nested) {
                for (v in nestedHidden) setViewVisibility(v, View.VISIBLE)
                nestedHidden.clear()
            }
            setViewVisibility(hidden, View.VISIBLE)
            canvas.restoreToCount(save)
        }
    }

    private fun hideOtherGlass(host: View, except: View) {
        if (host !is ViewGroup) return
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i)
            if (child === except) continue
            if (child is LiquidGlassView) {
                if (child.visibility == View.VISIBLE) {
                    setViewVisibility(child, View.INVISIBLE)
                    nestedHidden.add(child)
                }
            } else if (child is ViewGroup) {
                hideOtherGlass(child, except)
            }
        }
    }

    private fun childOnPathTo(host: View, descendant: View): View? {
        var child: View = descendant
        var p = descendant.parent
        while (p is View) {
            if (p === host) return child
            child = p
            p = p.parent
        }
        return null
    }
}
