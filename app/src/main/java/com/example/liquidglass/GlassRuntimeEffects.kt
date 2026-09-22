package com.example.liquidglass

import android.graphics.RuntimeColorFilter
import android.graphics.RuntimeXfermode
import android.os.Build
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast

internal object GlassRuntimeEffects {

    private const val TAG = "GlassRuntimeEffects"

    @Volatile
    private var broken = false

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && !broken

    private const val VIBRANCY_AGSL = """
        uniform half satFactor;

        half4 main(half4 inColor) {
            half a = inColor.a;
            half3 c = inColor.rgb;
            if (a > 0.0001) {
                c = clamp(c / a, 0.0, 1.0);
            }
            half lum = dot(c, half3(0.2126, 0.7152, 0.0722));
            half3 outC;
            if (satFactor <= 1.0) {
                outC = mix(half3(lum), c, satFactor);
            } else {
                half satNow = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
                half room = 1.0 - smoothstep(0.2, 0.85, satNow);
                half hl = 1.0 - smoothstep(0.75, 0.98, lum);
                half amount = 1.0 + (satFactor - 1.0) * mix(0.3, 1.0, room * hl);
                outC = clamp(mix(half3(lum), c, amount), 0.0, 1.0);
            }
            return half4(outC * a, a);
        }
    """

    private const val RIM_AGSL = """
        half4 main(half4 src, half4 dst) {
            half sa = src.a;
            half da = dst.a;
            if (sa < 0.002) {
                return dst;
            }
            half3 s = src.rgb;
            if (sa > 0.0001) {
                s = clamp(s / sa, 0.0, 1.0);
            }
            half3 d = dst.rgb;
            if (da > 0.0001) {
                d = clamp(d / da, 0.0, 1.0);
            }

            half3 sc = 1.0 - (1.0 - s) * (1.0 - d);
            half3 ov = mix(2.0 * s * d, 1.0 - 2.0 * (1.0 - s) * (1.0 - d), step(0.5, d));

            half3 res = mix(d, sc, sa * 0.1);
            res = mix(res, ov, sa);

            half lum = dot(d, half3(0.2126, 0.7152, 0.0722));
            half bright = smoothstep(0.72, 0.95, lum);
            res = mix(res, d * (1.0 - 0.22 * sa), bright);

            half outA = sa + da * (1.0 - sa);
            half3 blended = mix(s, res, da);
            return half4(blended * outA, outA);
        }
    """

    private const val ADAPTIVE_TINT_AGSL = """
        half4 main(half4 src, half4 dst) {
            half da = dst.a;
            half3 d = dst.rgb;
            if (da > 0.0001) {
                d = clamp(d / da, 0.0, 1.0);
            }
            half lum = dot(d, half3(0.2126, 0.7152, 0.0722));
            half e = smoothstep(0.35, 0.75, lum);
            half ta = (0.14 + 0.08 * e) * src.a;
            half3 res = mix(d, half3(1.0 - e), ta);
            return half4(res * da, da);
        }
    """

    fun createVibrancyFilter(): RuntimeColorFilter? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA || broken) return null
        return try {
            RuntimeColorFilter(VIBRANCY_AGSL)
        } catch (t: Throwable) {
            markBroken("vibrancy", t)
            null
        }
    }

    fun createRimBlender(): RuntimeXfermode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA || broken) return null
        return try {
            RuntimeXfermode(RIM_AGSL)
        } catch (t: Throwable) {
            markBroken("rim", t)
            null
        }
    }

    fun createAdaptiveTintBlender(): RuntimeXfermode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA || broken) return null
        return try {
            RuntimeXfermode(ADAPTIVE_TINT_AGSL)
        } catch (t: Throwable) {
            markBroken("adaptiveTint", t)
            null
        }
    }

    private fun markBroken(which: String, t: Throwable) {
        Log.e(TAG, "AGSL 运行时效果($which)创建失败，永久回退旧路径", t)
        broken = true
    }
}
