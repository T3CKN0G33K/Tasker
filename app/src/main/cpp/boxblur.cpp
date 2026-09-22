#include "boxblur.h"
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "BoxBlur"
#ifdef NATIVEGAUSS_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void box_blur_h(
    const uint8_t* src,
    uint8_t* dst,
    int w, int h, int stride,
    int radius
) {
    int diameter = 2 * radius + 1;
    float inv = 1.0f / diameter;

    for (int y = 0; y < h; ++y) {
        const uint8_t* srcRow = src + y * stride;
        uint8_t* dstRow = dst + y * stride;

        int sumR = 0, sumG = 0, sumB = 0, sumA = 0;

        for (int i = -radius; i <= radius; ++i) {
            int x = std::max(0, std::min(w - 1, i));
            sumB += srcRow[x * 4 + 0];
            sumG += srcRow[x * 4 + 1];
            sumR += srcRow[x * 4 + 2];
            sumA += srcRow[x * 4 + 3];
        }

        for (int x = 0; x < w; ++x) {
            dstRow[x * 4 + 0] = static_cast<uint8_t>(sumB * inv + 0.5f);
            dstRow[x * 4 + 1] = static_cast<uint8_t>(sumG * inv + 0.5f);
            dstRow[x * 4 + 2] = static_cast<uint8_t>(sumR * inv + 0.5f);
            dstRow[x * 4 + 3] = static_cast<uint8_t>(sumA * inv + 0.5f);

            int xLeft = std::max(0, x - radius);
            int xRight = std::min(w - 1, x + radius + 1);

            sumB += srcRow[xRight * 4 + 0] - srcRow[xLeft * 4 + 0];
            sumG += srcRow[xRight * 4 + 1] - srcRow[xLeft * 4 + 1];
            sumR += srcRow[xRight * 4 + 2] - srcRow[xLeft * 4 + 2];
            sumA += srcRow[xRight * 4 + 3] - srcRow[xLeft * 4 + 3];
        }
    }
}

static void box_blur_v(
    const uint8_t* src,
    uint8_t* dst,
    int w, int h, int stride,
    int radius
) {
    int diameter = 2 * radius + 1;
    float inv = 1.0f / diameter;

    for (int x = 0; x < w; ++x) {
        int sumR = 0, sumG = 0, sumB = 0, sumA = 0;

        for (int i = -radius; i <= radius; ++i) {
            int y = std::max(0, std::min(h - 1, i));
            const uint8_t* pixel = src + y * stride + x * 4;
            sumB += pixel[0];
            sumG += pixel[1];
            sumR += pixel[2];
            sumA += pixel[3];
        }

        for (int y = 0; y < h; ++y) {
            uint8_t* dstPixel = dst + y * stride + x * 4;
            dstPixel[0] = static_cast<uint8_t>(sumB * inv + 0.5f);
            dstPixel[1] = static_cast<uint8_t>(sumG * inv + 0.5f);
            dstPixel[2] = static_cast<uint8_t>(sumR * inv + 0.5f);
            dstPixel[3] = static_cast<uint8_t>(sumA * inv + 0.5f);

            int yTop = std::max(0, y - radius);
            int yBottom = std::min(h - 1, y + radius + 1);

            const uint8_t* topPixel = src + yTop * stride + x * 4;
            const uint8_t* bottomPixel = src + yBottom * stride + x * 4;

            sumB += bottomPixel[0] - topPixel[0];
            sumG += bottomPixel[1] - topPixel[1];
            sumR += bottomPixel[2] - topPixel[2];
            sumA += bottomPixel[3] - topPixel[3];
        }
    }
}

void box_blur_single_pass(
    const uint8_t* src,
    uint8_t* dst,
    int w, int h, int stride,
    int radius
) {
    uint8_t* temp = new uint8_t[h * stride];
    box_blur_h(src, temp, w, h, stride, radius);
    box_blur_v(temp, dst, w, h, stride, radius);
    delete[] temp;
}

void box3_rgba8888_inplace(
    uint8_t* base,
    int w, int h, int stride,
    int radius
) {
    if (!base || w <= 0 || h <= 0 || stride < w * 4 || radius <= 0) return;
    if (radius > 50) radius = 50;

    uint8_t* temp = new uint8_t[h * stride];
    box_blur_single_pass(base, temp, w, h, stride, radius);
    box_blur_single_pass(temp, base, w, h, stride, radius);
    box_blur_single_pass(base, temp, w, h, stride, radius);
    memcpy(base, temp, h * stride);
    delete[] temp;
}

static void downsample_nearest(
    const uint8_t* src,
    uint8_t* dst,
    int srcWidth, int srcHeight, int srcStride,
    int dstWidth, int dstHeight, int dstStride
) {
    float scaleX = static_cast<float>(srcWidth) / dstWidth;
    float scaleY = static_cast<float>(srcHeight) / dstHeight;

    for (int y = 0; y < dstHeight; ++y) {
        int srcY = std::min(static_cast<int>(y * scaleY), srcHeight - 1);
        const uint8_t* srcRow = src + srcY * srcStride;
        uint8_t* dstRow = dst + y * dstStride;

        for (int x = 0; x < dstWidth; ++x) {
            int srcX = std::min(static_cast<int>(x * scaleX), srcWidth - 1);
            const uint8_t* srcPixel = srcRow + srcX * 4;
            uint8_t* dstPixel = dstRow + x * 4;

            dstPixel[0] = srcPixel[0];
            dstPixel[1] = srcPixel[1];
            dstPixel[2] = srcPixel[2];
            dstPixel[3] = srcPixel[3];
        }
    }
}

static void downsample_bilinear(
    const uint8_t* src,
    uint8_t* dst,
    int srcWidth, int srcHeight, int srcStride,
    int dstWidth, int dstHeight, int dstStride
) {
    float scaleX = static_cast<float>(srcWidth) / dstWidth;
    float scaleY = static_cast<float>(srcHeight) / dstHeight;

    for (int y = 0; y < dstHeight; ++y) {
        for (int x = 0; x < dstWidth; ++x) {
            float srcX = std::max(0.0f, std::min((x + 0.5f) * scaleX - 0.5f, srcWidth - 1.0f));
            float srcY = std::max(0.0f, std::min((y + 0.5f) * scaleY - 0.5f, srcHeight - 1.0f));

            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            int x1 = std::min(x0 + 1, srcWidth - 1);
            int y1 = std::min(y0 + 1, srcHeight - 1);

            float wx = srcX - x0;
            float wy = srcY - y0;

            const uint8_t* p00 = src + y0 * srcStride + x0 * 4;
            const uint8_t* p10 = src + y0 * srcStride + x1 * 4;
            const uint8_t* p01 = src + y1 * srcStride + x0 * 4;
            const uint8_t* p11 = src + y1 * srcStride + x1 * 4;

            uint8_t* dstPixel = dst + y * dstStride + x * 4;
            for (int c = 0; c < 4; ++c) {
                float v0 = p00[c] * (1 - wx) + p10[c] * wx;
                float v1 = p01[c] * (1 - wx) + p11[c] * wx;
                float v = v0 * (1 - wy) + v1 * wy;
                dstPixel[c] = static_cast<uint8_t>(v + 0.5f);
            }
        }
    }
}

void advanced_box_blur_rgba8888(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float radius,
    float downscale
) {
    if (!src || !dst || width <= 0 || height <= 0 || stride < width * 4) return;
    downscale = std::max(0.01f, std::min(1.0f, downscale));
    radius = std::max(0.0f, std::min(25.0f, radius));

    if (radius < 0.5f) {
        if (src != dst) {
            for (int y = 0; y < height; ++y) {
                memcpy(dst + y * stride, src + y * stride, width * 4);
            }
        }
        return;
    }

    int smallWidth = std::max(1, static_cast<int>(width * downscale + 0.5f));
    int smallHeight = std::max(1, static_cast<int>(height * downscale + 0.5f));
    int smallStride = smallWidth * 4;

    uint8_t* smallImage = new uint8_t[smallHeight * smallStride];
    uint8_t* blurredSmall = new uint8_t[smallHeight * smallStride];

    downsample_nearest(src, smallImage, width, height, stride, smallWidth, smallHeight, smallStride);
    int intRadius = std::max(1, static_cast<int>(radius * downscale + 0.5f));
    box_blur_single_pass(smallImage, blurredSmall, smallWidth, smallHeight, smallStride, intRadius);
    downsample_nearest(blurredSmall, dst, smallWidth, smallHeight, smallStride, width, height, stride);

    delete[] smallImage;
    delete[] blurredSmall;
}

void advanced_box_blur_rgba8888_hq(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float radius,
    float downscale
) {
    if (!src || !dst || width <= 0 || height <= 0 || stride < width * 4) return;
    downscale = std::max(0.01f, std::min(1.0f, downscale));
    radius = std::max(0.0f, std::min(25.0f, radius));

    if (radius < 0.5f) {
        if (src != dst) {
            for (int y = 0; y < height; ++y) {
                memcpy(dst + y * stride, src + y * stride, width * 4);
            }
        }
        return;
    }

    int smallWidth = std::max(1, static_cast<int>(width * downscale + 0.5f));
    int smallHeight = std::max(1, static_cast<int>(height * downscale + 0.5f));
    int smallStride = smallWidth * 4;

    uint8_t* smallImage = new uint8_t[smallHeight * smallStride];
    uint8_t* blurredSmall = new uint8_t[smallHeight * smallStride];

    downsample_bilinear(src, smallImage, width, height, stride, smallWidth, smallHeight, smallStride);
    int intRadius = std::max(1, static_cast<int>(radius * downscale + 0.5f));
    box_blur_single_pass(smallImage, blurredSmall, smallWidth, smallHeight, smallStride, intRadius);
    downsample_bilinear(blurredSmall, dst, smallWidth, smallHeight, smallStride, width, height, stride);

    delete[] smallImage;
    delete[] blurredSmall;
}
