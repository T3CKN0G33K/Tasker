#include "chromatic_aberration.h"
#include <algorithm>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "ChromaticAberration"
#ifdef NATIVEGAUSS_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static inline uint8_t sample_bilinear_channel(
    const uint8_t* pixels,
    int width,
    int height,
    int stride,
    float x,
    float y,
    int channelOffset
) {
    if (x < 0.0f || x >= width - 1.0f || y < 0.0f || y >= height - 1.0f) {
        int clampedX = std::max(0, std::min(width - 1, static_cast<int>(x + 0.5f)));
        int clampedY = std::max(0, std::min(height - 1, static_cast<int>(y + 0.5f)));
        return pixels[clampedY * stride + clampedX * 4 + channelOffset];
    }

    int x0 = static_cast<int>(x);
    int y0 = static_cast<int>(y);
    int x1 = std::min(x0 + 1, width - 1);
    int y1 = std::min(y0 + 1, height - 1);

    float fx = x - x0;
    float fy = y - y0;

    const uint8_t* row0 = pixels + y0 * stride;
    const uint8_t* row1 = pixels + y1 * stride;

    float c00 = row0[x0 * 4 + channelOffset];
    float c10 = row0[x1 * 4 + channelOffset];
    float c01 = row1[x0 * 4 + channelOffset];
    float c11 = row1[x1 * 4 + channelOffset];

    float c0 = c00 * (1.0f - fx) + c10 * fx;
    float c1 = c01 * (1.0f - fx) + c11 * fx;
    float result = c0 * (1.0f - fy) + c1 * fy;

    return static_cast<uint8_t>(std::max(0.0f, std::min(255.0f, result + 0.5f)));
}

static inline uint8_t sample_nearest_channel(
    const uint8_t* pixels,
    int width,
    int height,
    int stride,
    float x,
    float y,
    int channelOffset
) {
    int clampedX = std::max(0, std::min(width - 1, static_cast<int>(x + 0.5f)));
    int clampedY = std::max(0, std::min(height - 1, static_cast<int>(y + 0.5f)));

    return pixels[clampedY * stride + clampedX * 4 + channelOffset];
}

void chromatic_aberration_rgba8888(
    const uint8_t* source,
    const uint8_t* displacement,
    uint8_t* result,
    int width,
    int height,
    int sourceStride,
    int displacementStride,
    int resultStride,
    float intensity,
    float scale,
    float redOffset,
    float greenOffset,
    float blueOffset,
    bool useBilinear
) {
    if (!source || !displacement || !result) return;
    if (width <= 0 || height <= 0) return;
    if (sourceStride < width * 4 || displacementStride < width * 4 || resultStride < width * 4) return;

    const float scaleFactor = scale / 255.0f;
    const float actualRedOffset = redOffset;
    const float actualGreenOffset = greenOffset;
    const float actualBlueOffset = blueOffset;

    for (int y = 0; y < height; ++y) {
        const uint8_t* displacementRow = displacement + y * displacementStride;
        uint8_t* resultRow = result + y * resultStride;

        for (int x = 0; x < width; ++x) {
            const uint8_t* mapPixel = displacementRow + x * 4;
            uint8_t mapR = mapPixel[0];
            uint8_t mapG = mapPixel[1];

            float baseDx = (static_cast<float>(mapR) - 128.0f) * scaleFactor;
            float baseDy = (static_cast<float>(mapG) - 128.0f) * scaleFactor;

            float baseLen = std::sqrt(baseDx * baseDx + baseDy * baseDy);
            float dirX = 0.0f;
            float dirY = 0.0f;
            if (baseLen > 0.001f) {
                dirX = baseDx / baseLen;
                dirY = baseDy / baseLen;
            }

            float rSrcX = x + baseDx + dirX * actualRedOffset;
            float rSrcY = y + baseDy + dirY * actualRedOffset;
            float gSrcX = x + baseDx + dirX * actualGreenOffset;
            float gSrcY = y + baseDy + dirY * actualGreenOffset;
            float bSrcX = x + baseDx + dirX * actualBlueOffset;
            float bSrcY = y + baseDy + dirY * actualBlueOffset;

            uint8_t r, g, b;
            if (useBilinear) {
                r = sample_bilinear_channel(source, width, height, sourceStride, rSrcX, rSrcY, 2);
                g = sample_bilinear_channel(source, width, height, sourceStride, gSrcX, gSrcY, 1);
                b = sample_bilinear_channel(source, width, height, sourceStride, bSrcX, bSrcY, 0);
            } else {
                r = sample_nearest_channel(source, width, height, sourceStride, rSrcX, rSrcY, 2);
                g = sample_nearest_channel(source, width, height, sourceStride, gSrcX, gSrcY, 1);
                b = sample_nearest_channel(source, width, height, sourceStride, bSrcX, bSrcY, 0);
            }

            const uint8_t* sourcePixel = source + y * sourceStride + x * 4;
            uint8_t alpha = sourcePixel[3];

            uint8_t* outPixel = resultRow + x * 4;
            outPixel[0] = b;
            outPixel[1] = g;
            outPixel[2] = r;
            outPixel[3] = alpha;
        }
    }
}

void chromatic_aberration_rgba8888_inplace(
    const uint8_t* source,
    const uint8_t* displacement,
    uint8_t* result,
    int width,
    int height,
    int stride,
    float intensity,
    float scale,
    float redOffset,
    float greenOffset,
    float blueOffset,
    bool useBilinear
) {
    chromatic_aberration_rgba8888(
        source, displacement, result,
        width, height,
        stride, stride, stride,
        intensity, scale,
        redOffset, greenOffset, blueOffset,
        useBilinear
    );
}

void chromatic_dispersion_rgba8888(
    const uint8_t* source,
    const uint8_t* edgeDistance,
    const uint8_t* normalMap,
    uint8_t* result,
    int width,
    int height,
    int sourceStride,
    int edgeDistanceStride,
    int normalMapStride,
    int resultStride,
    float refThickness,
    float refFactor,
    float refDispersion,
    float dpr,
    bool useBilinear
) {
    if (!source || !edgeDistance || !result) return;
    if (width <= 0 || height <= 0) return;
    if (sourceStride < width * 4 || edgeDistanceStride < width * 4 || resultStride < width * 4) return;

    const float N_R = 0.98f;
    const float N_G = 1.0f;
    const float N_B = 1.02f;

    const float centerX = width * 0.5f;
    const float centerY = height * 0.5f;

    for (int y = 0; y < height; ++y) {
        const uint8_t* edgeRow = edgeDistance + y * edgeDistanceStride;
        uint8_t* resultRow = result + y * resultStride;

        for (int x = 0; x < width; ++x) {
            const uint8_t* edgePixel = edgeRow + x * 4;
            float distanceToEdge = edgePixel[2] / 255.0f * 500.0f;
            float nmerged = distanceToEdge;

            float edgeFactor = 0.0f;
            if (nmerged < refThickness) {
                float x_R_ratio = 1.0f - nmerged / refThickness;
                float thetaI = asinf(powf(x_R_ratio, 2.0f));
                float thetaT = asinf(1.0f / refFactor * sinf(thetaI));
                edgeFactor = -tanf(thetaT - thetaI);
                if (edgeFactor < 0.0f) edgeFactor = 0.0f;
            }

            float normalX, normalY;
            if (normalMap != nullptr) {
                const uint8_t* normalPixel = normalMap + y * normalMapStride + x * 4;
                normalX = (normalPixel[2] / 255.0f) * 2.0f - 1.0f;
                normalY = (normalPixel[1] / 255.0f) * 2.0f - 1.0f;
            } else {
                float dx = x - centerX;
                float dy = y - centerY;
                float len = sqrtf(dx * dx + dy * dy);
                if (len > 0.0f) {
                    normalX = dx / len;
                    normalY = dy / len;
                } else {
                    normalX = 0.0f;
                    normalY = 0.0f;
                }
            }

            float aspectRatio = static_cast<float>(height) / static_cast<float>(width);
            float baseOffsetX = -normalX * edgeFactor * 5.0f * dpr * aspectRatio;
            float baseOffsetY = -normalY * edgeFactor * 5.0f * dpr;

            float offsetR_x = baseOffsetX * (1.0f - (N_R - 1.0f) * refDispersion);
            float offsetR_y = baseOffsetY * (1.0f - (N_R - 1.0f) * refDispersion);

            float offsetG_x = baseOffsetX * (1.0f - (N_G - 1.0f) * refDispersion);
            float offsetG_y = baseOffsetY * (1.0f - (N_G - 1.0f) * refDispersion);

            float offsetB_x = baseOffsetX * (1.0f - (N_B - 1.0f) * refDispersion);
            float offsetB_y = baseOffsetY * (1.0f - (N_B - 1.0f) * refDispersion);

            uint8_t r, g, b;
            if (useBilinear) {
                r = sample_bilinear_channel(source, width, height, sourceStride, x + offsetR_x, y + offsetR_y, 2);
                g = sample_bilinear_channel(source, width, height, sourceStride, x + offsetG_x, y + offsetG_y, 1);
                b = sample_bilinear_channel(source, width, height, sourceStride, x + offsetB_x, y + offsetB_y, 0);
            } else {
                r = sample_nearest_channel(source, width, height, sourceStride, x + offsetR_x, y + offsetR_y, 2);
                g = sample_nearest_channel(source, width, height, sourceStride, x + offsetG_x, y + offsetG_y, 1);
                b = sample_nearest_channel(source, width, height, sourceStride, x + offsetB_x, y + offsetB_y, 0);
            }

            const uint8_t* sourcePixel = source + y * sourceStride + x * 4;
            uint8_t alpha = sourcePixel[3];

            uint8_t* outPixel = resultRow + x * 4;
            outPixel[0] = b;
            outPixel[1] = g;
            outPixel[2] = r;
            outPixel[3] = alpha;
        }
    }
}

void chromatic_dispersion_rgba8888_inplace(
    const uint8_t* source,
    const uint8_t* edgeDistance,
    const uint8_t* normalMap,
    uint8_t* result,
    int width,
    int height,
    int stride,
    float refThickness,
    float refFactor,
    float refDispersion,
    float dpr,
    bool useBilinear
) {
    chromatic_dispersion_rgba8888(
        source, edgeDistance, normalMap, result,
        width, height,
        stride, stride, stride, stride,
        refThickness, refFactor, refDispersion, dpr,
        useBilinear
    );
}
