#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include "gauss_iir.h"
#include "gauss_iir_neon.h"
#include "boxblur.h"
#include "chromatic_aberration.h"

#define LOG_TAG "NativeGauss"
#ifdef NATIVEGAUSS_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool lock_bitmap(
    JNIEnv* env,
    jobject bitmap,
    AndroidBitmapInfo* info,
    void** pixels
) {
    int ret = AndroidBitmap_getInfo(env, bitmap, info);
    if (ret != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed: %d", ret);
        return false;
    }

    if (info->format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be ARGB_8888, got: %d", info->format);
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exClass, "Bitmap must be ARGB_8888 format");
        return false;
    }

    if (info->width <= 0 || info->height <= 0) {
        LOGE("Invalid bitmap size: %dx%d", info->width, info->height);
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exClass, "Bitmap size must be positive");
        return false;
    }

    ret = AndroidBitmap_lockPixels(env, bitmap, pixels);
    if (ret != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_lockPixels failed: %d", ret);
        jclass exClass = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(exClass, "Failed to lock bitmap pixels (bitmap may not be mutable)");
        return false;
    }

    if (*pixels == nullptr) {
        LOGE("Locked pixels is null");
        AndroidBitmap_unlockPixels(env, bitmap);
        jclass exClass = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(exClass, "Bitmap pixels is null");
        return false;
    }

    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_blur_NativeGauss_gaussianIIRInplace(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jfloat sigma,
    jboolean linear
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (!lock_bitmap(env, bitmap, &info, &pixels)) return;

    gaussian_iir_rgba8888_inplace(
        static_cast<uint8_t*>(pixels),
        static_cast<int>(info.width),
        static_cast<int>(info.height),
        static_cast<int>(info.stride),
        sigma,
        linear
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_blur_NativeGauss_gaussianIIRNeonInplace(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jfloat sigma,
    jboolean linear
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (!lock_bitmap(env, bitmap, &info, &pixels)) return;

    gaussian_iir_rgba8888_neon(
        static_cast<uint8_t*>(pixels),
        static_cast<int>(info.width),
        static_cast<int>(info.height),
        static_cast<int>(info.stride),
        sigma,
        linear
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_blur_NativeGauss_hasNeonSupport(
    JNIEnv* env,
    jobject /* this */
) {
    return has_neon_support();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_blur_NativeGauss_box3Inplace(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jint radius
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (!lock_bitmap(env, bitmap, &info, &pixels)) return;

    box3_rgba8888_inplace(
        static_cast<uint8_t*>(pixels),
        static_cast<int>(info.width),
        static_cast<int>(info.height),
        static_cast<int>(info.stride),
        radius
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_blur_NativeGauss_advancedBoxBlurInplace(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jfloat radius,
    jfloat downscale
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (!lock_bitmap(env, bitmap, &info, &pixels)) return;

    advanced_box_blur_rgba8888(
        static_cast<uint8_t*>(pixels),
        static_cast<uint8_t*>(pixels),
        static_cast<int>(info.width),
        static_cast<int>(info.height),
        static_cast<int>(info.stride),
        radius,
        downscale
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_blur_NativeGauss_advancedBoxBlurInplaceHQ(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jfloat radius,
    jfloat downscale
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (!lock_bitmap(env, bitmap, &info, &pixels)) return;

    advanced_box_blur_rgba8888_hq(
        static_cast<uint8_t*>(pixels),
        static_cast<uint8_t*>(pixels),
        static_cast<int>(info.width),
        static_cast<int>(info.height),
        static_cast<int>(info.stride),
        radius,
        downscale
    );

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_liquidglass_NativeChromaticAberration_chromaticAberrationInplace(
    JNIEnv* env,
    jobject /* this */,
    jobject source,
    jobject displacement,
    jobject result,
    jfloat intensity,
    jfloat scale,
    jfloat redOffset,
    jfloat greenOffset,
    jfloat blueOffset,
    jboolean useBilinear
) {
    AndroidBitmapInfo sourceInfo, displacementInfo, resultInfo;
    void* sourcePixels = nullptr;
    void* displacementPixels = nullptr;
    void* resultPixels = nullptr;

    if (!lock_bitmap(env, source, &sourceInfo, &sourcePixels)) return;
    if (!lock_bitmap(env, displacement, &displacementInfo, &displacementPixels)) {
        AndroidBitmap_unlockPixels(env, source);
        return;
    }
    if (!lock_bitmap(env, result, &resultInfo, &resultPixels)) {
        AndroidBitmap_unlockPixels(env, source);
        AndroidBitmap_unlockPixels(env, displacement);
        return;
    }

    if (sourceInfo.width != displacementInfo.width ||
        sourceInfo.height != displacementInfo.height ||
        sourceInfo.width != resultInfo.width ||
        sourceInfo.height != resultInfo.height) {
        AndroidBitmap_unlockPixels(env, source);
        AndroidBitmap_unlockPixels(env, displacement);
        AndroidBitmap_unlockPixels(env, result);
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exClass, "Source, displacement, and result bitmaps must have the same dimensions");
        return;
    }

    chromatic_aberration_rgba8888(
        static_cast<const uint8_t*>(sourcePixels),
        static_cast<const uint8_t*>(displacementPixels),
        static_cast<uint8_t*>(resultPixels),
        static_cast<int>(sourceInfo.width),
        static_cast<int>(sourceInfo.height),
        static_cast<int>(sourceInfo.stride),
        static_cast<int>(displacementInfo.stride),
        static_cast<int>(resultInfo.stride),
        intensity,
        scale,
        redOffset,
        greenOffset,
        blueOffset,
        useBilinear
    );

    AndroidBitmap_unlockPixels(env, source);
    AndroidBitmap_unlockPixels(env, displacement);
    AndroidBitmap_unlockPixels(env, result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_liquidglass_NativeChromaticDispersion_chromaticDispersionInplace(
    JNIEnv* env,
    jobject /* this */,
    jobject source,
    jobject edgeDistance,
    jobject normalMap,
    jobject result,
    jfloat refThickness,
    jfloat refFactor,
    jfloat refDispersion,
    jfloat dpr,
    jboolean useBilinear
) {
    AndroidBitmapInfo sourceInfo, edgeDistanceInfo, normalMapInfo, resultInfo;
    void* sourcePixels = nullptr;
    void* edgeDistancePixels = nullptr;
    void* normalMapPixels = nullptr;
    void* resultPixels = nullptr;

    if (!lock_bitmap(env, source, &sourceInfo, &sourcePixels)) return;
    if (!lock_bitmap(env, edgeDistance, &edgeDistanceInfo, &edgeDistancePixels)) {
        AndroidBitmap_unlockPixels(env, source);
        return;
    }

    bool hasNormalMap = (normalMap != nullptr);
    if (hasNormalMap) {
        if (!lock_bitmap(env, normalMap, &normalMapInfo, &normalMapPixels)) {
            AndroidBitmap_unlockPixels(env, source);
            AndroidBitmap_unlockPixels(env, edgeDistance);
            return;
        }
    }

    if (!lock_bitmap(env, result, &resultInfo, &resultPixels)) {
        AndroidBitmap_unlockPixels(env, source);
        AndroidBitmap_unlockPixels(env, edgeDistance);
        if (hasNormalMap) AndroidBitmap_unlockPixels(env, normalMap);
        return;
    }

    if (sourceInfo.width != edgeDistanceInfo.width ||
        sourceInfo.height != edgeDistanceInfo.height ||
        sourceInfo.width != resultInfo.width ||
        sourceInfo.height != resultInfo.height) {
        AndroidBitmap_unlockPixels(env, source);
        AndroidBitmap_unlockPixels(env, edgeDistance);
        if (hasNormalMap) AndroidBitmap_unlockPixels(env, normalMap);
        AndroidBitmap_unlockPixels(env, result);
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(exClass, "Source, edgeDistance, and result bitmaps must have the same dimensions");
        return;
    }

    chromatic_dispersion_rgba8888(
        static_cast<const uint8_t*>(sourcePixels),
        static_cast<const uint8_t*>(edgeDistancePixels),
        hasNormalMap ? static_cast<const uint8_t*>(normalMapPixels) : nullptr,
        static_cast<uint8_t*>(resultPixels),
        static_cast<int>(sourceInfo.width),
        static_cast<int>(sourceInfo.height),
        static_cast<int>(sourceInfo.stride),
        static_cast<int>(edgeDistanceInfo.stride),
        hasNormalMap ? static_cast<int>(normalMapInfo.stride) : 0,
        static_cast<int>(resultInfo.stride),
        refThickness,
        refFactor,
        refDispersion,
        dpr,
        useBilinear
    );

    AndroidBitmap_unlockPixels(env, source);
    AndroidBitmap_unlockPixels(env, edgeDistance);
    if (hasNormalMap) AndroidBitmap_unlockPixels(env, normalMap);
    AndroidBitmap_unlockPixels(env, result);
}
