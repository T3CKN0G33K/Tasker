#include "gauss_iir_neon.h"
#include <cmath>
#include <cstring>
#include <algorithm>
#include <android/log.h>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define NEON_AVAILABLE 1
#else
#define NEON_AVAILABLE 0
#endif

#define LOG_TAG "GaussIIR_NEON"
#ifdef NATIVEGAUSS_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if NEON_AVAILABLE
static inline float32x4_t srgb_to_linear_neon(float32x4_t srgb) {
    float32x4_t srgb2 = vmulq_f32(srgb, srgb);
    float32x4_t factor = vmlaq_n_f32(vdupq_n_f32(0.8f), srgb, 0.2f);
    return vmulq_f32(srgb2, factor);
}

static inline float32x4_t linear_to_srgb_neon(float32x4_t linear) {
    float32x4_t rsqrt = vrsqrteq_f32(linear);
    rsqrt = vmulq_f32(rsqrt, vrsqrtsq_f32(vmulq_f32(linear, rsqrt), rsqrt));
    float32x4_t sqrt_linear = vmulq_f32(linear, rsqrt);
    float32x4_t factor = vmlsq_n_f32(vdupq_n_f32(1.0f), linear, 0.2f);
    return vmulq_f32(sqrt_linear, factor);
}
#endif

struct DericheCoeffs {
    float a0, a1, a2, a3;
    float b1, b2;
    float coefp, coefn;
};

static DericheCoeffs compute_deriche_coeffs(float sigma) {
    DericheCoeffs c;
    double alpha = 1.695 / sigma;
    double ema = exp(-alpha);
    double ema2 = ema * ema;

    c.b1 = static_cast<float>(-2.0 * ema);
    c.b2 = static_cast<float>(ema2);

    double k = (1.0 - ema) * (1.0 - ema) / (1.0 + 2.0 * alpha * ema - ema2);

    c.a0 = static_cast<float>(k);
    c.a1 = static_cast<float>(k * ema * (alpha - 1.0));
    c.a2 = static_cast<float>(k * ema * (alpha + 1.0));
    c.a3 = static_cast<float>(-k * ema2);

    c.coefp = static_cast<float>((c.a0 + c.a1) / (1.0 + c.b1 + c.b2));
    c.coefn = static_cast<float>((c.a2 + c.a3) / (1.0 + c.b1 + c.b2));

    return c;
}

#if NEON_AVAILABLE
static void iir_filter_1d_neon(const float* src, float* dst, int len, const DericheCoeffs& c) {
    if (len <= 0) return;

    float32x4_t va0 = vdupq_n_f32(c.a0);
    float32x4_t va1 = vdupq_n_f32(c.a1);
    float32x4_t va2 = vdupq_n_f32(c.a2);
    float32x4_t va3 = vdupq_n_f32(c.a3);
    float32x4_t vb1 = vdupq_n_f32(c.b1);
    float32x4_t vb2 = vdupq_n_f32(c.b2);
    float32x4_t vcoefp = vdupq_n_f32(c.coefp);
    float32x4_t vcoefn = vdupq_n_f32(c.coefn);

    float32x4_t vxp1 = vld1q_f32(src);
    float32x4_t vyp1 = vmulq_f32(vxp1, vcoefp);
    float32x4_t vyp2 = vyp1;

    for (int i = 0; i < len; ++i) {
        float32x4_t vxc = vld1q_f32(src + i * 4);
        float32x4_t vyc = vmulq_f32(va0, vxc);
        vyc = vmlaq_f32(vyc, va1, vxp1);
        vyc = vmlsq_f32(vyc, vb1, vyp1);
        vyc = vmlsq_f32(vyc, vb2, vyp2);

        vst1q_f32(dst + i * 4, vyc);

        vxp1 = vxc;
        vyp2 = vyp1;
        vyp1 = vyc;
    }

    float32x4_t vxn1 = vld1q_f32(src + (len - 1) * 4);
    float32x4_t vxn2 = vxn1;
    float32x4_t vyn1 = vmulq_f32(vxn1, vcoefn);
    float32x4_t vyn2 = vyn1;

    for (int i = len - 1; i >= 0; --i) {
        float32x4_t vxc = vld1q_f32(src + i * 4);

        float32x4_t vyc = vmulq_f32(va2, vxn1);
        vyc = vmlaq_f32(vyc, va3, vxn2);
        vyc = vmlsq_f32(vyc, vb1, vyn1);
        vyc = vmlsq_f32(vyc, vb2, vyn2);

        float32x4_t vsum = vaddq_f32(vld1q_f32(dst + i * 4), vyc);
        vst1q_f32(dst + i * 4, vsum);

        vxn2 = vxn1;
        vxn1 = vxc;
        vyn2 = vyn1;
        vyn1 = vyc;
    }
}

static void blur_horizontal_neon(
    uint8_t* base,
    int w,
    int h,
    int stride,
    const DericheCoeffs& c,
    float* rowBuf,
    bool doLinear
) {
    const float inv255 = 1.0f / 255.0f;

    for (int y = 0; y < h; ++y) {
        uint8_t* row = base + y * stride;

        for (int x = 0; x < w; ++x) {
            uint32_t px = reinterpret_cast<uint32_t*>(row)[x];
            float a = ((px >> 24) & 0xFF) * inv255;
            float r = ((px >> 16) & 0xFF) * inv255;
            float g = ((px >> 8) & 0xFF) * inv255;
            float b = (px & 0xFF) * inv255;

            if (doLinear && a > 1e-5f) {
                float invA = 1.0f / a;
                r *= invA; g *= invA; b *= invA;

                float32x4_t vrgb = {r, g, b, 0.0f};
                vrgb = srgb_to_linear_neon(vrgb);
                r = vgetq_lane_f32(vrgb, 0);
                g = vgetq_lane_f32(vrgb, 1);
                b = vgetq_lane_f32(vrgb, 2);

                r *= a; g *= a; b *= a;
            }

            rowBuf[x * 4 + 0] = r;
            rowBuf[x * 4 + 1] = g;
            rowBuf[x * 4 + 2] = b;
            rowBuf[x * 4 + 3] = a;
        }

        iir_filter_1d_neon(rowBuf, rowBuf, w, c);

        for (int x = 0; x < w; ++x) {
            float r = rowBuf[x * 4 + 0];
            float g = rowBuf[x * 4 + 1];
            float b = rowBuf[x * 4 + 2];
            float a = rowBuf[x * 4 + 3];

            if (doLinear && a > 1e-5f) {
                float invA = 1.0f / a;
                r *= invA; g *= invA; b *= invA;

                float32x4_t vrgb = {r, g, b, 0.0f};
                vrgb = linear_to_srgb_neon(vrgb);
                r = vgetq_lane_f32(vrgb, 0);
                g = vgetq_lane_f32(vrgb, 1);
                b = vgetq_lane_f32(vrgb, 2);

                r *= a; g *= a; b *= a;
            }

            r = std::max(0.0f, std::min(1.0f, r));
            g = std::max(0.0f, std::min(1.0f, g));
            b = std::max(0.0f, std::min(1.0f, b));
            a = std::max(0.0f, std::min(1.0f, a));

            uint32_t px = (static_cast<uint32_t>(a * 255.0f + 0.5f) << 24) |
                          (static_cast<uint32_t>(r * 255.0f + 0.5f) << 16) |
                          (static_cast<uint32_t>(g * 255.0f + 0.5f) << 8) |
                          static_cast<uint32_t>(b * 255.0f + 0.5f);
            reinterpret_cast<uint32_t*>(row)[x] = px;
        }
    }
}

static void blur_vertical_neon(
    uint8_t* base,
    int w,
    int h,
    int stride,
    const DericheCoeffs& c,
    float* colBuf,
    bool doLinear
) {
    const float inv255 = 1.0f / 255.0f;

    for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) {
            uint8_t* row = base + y * stride;
            uint32_t px = reinterpret_cast<uint32_t*>(row)[x];
            float a = ((px >> 24) & 0xFF) * inv255;
            float r = ((px >> 16) & 0xFF) * inv255;
            float g = ((px >> 8) & 0xFF) * inv255;
            float b = (px & 0xFF) * inv255;

            if (doLinear && a > 1e-5f) {
                float invA = 1.0f / a;
                r *= invA; g *= invA; b *= invA;

                float32x4_t vrgb = {r, g, b, 0.0f};
                vrgb = srgb_to_linear_neon(vrgb);
                r = vgetq_lane_f32(vrgb, 0);
                g = vgetq_lane_f32(vrgb, 1);
                b = vgetq_lane_f32(vrgb, 2);

                r *= a; g *= a; b *= a;
            }

            colBuf[y * 4 + 0] = r;
            colBuf[y * 4 + 1] = g;
            colBuf[y * 4 + 2] = b;
            colBuf[y * 4 + 3] = a;
        }

        iir_filter_1d_neon(colBuf, colBuf, h, c);

        for (int y = 0; y < h; ++y) {
            float r = colBuf[y * 4 + 0];
            float g = colBuf[y * 4 + 1];
            float b = colBuf[y * 4 + 2];
            float a = colBuf[y * 4 + 3];

            if (doLinear && a > 1e-5f) {
                float invA = 1.0f / a;
                r *= invA; g *= invA; b *= invA;

                float32x4_t vrgb = {r, g, b, 0.0f};
                vrgb = linear_to_srgb_neon(vrgb);
                r = vgetq_lane_f32(vrgb, 0);
                g = vgetq_lane_f32(vrgb, 1);
                b = vgetq_lane_f32(vrgb, 2);

                r *= a; g *= a; b *= a;
            }

            r = std::max(0.0f, std::min(1.0f, r));
            g = std::max(0.0f, std::min(1.0f, g));
            b = std::max(0.0f, std::min(1.0f, b));
            a = std::max(0.0f, std::min(1.0f, a));

            uint8_t* row = base + y * stride;
            uint32_t px = (static_cast<uint32_t>(a * 255.0f + 0.5f) << 24) |
                          (static_cast<uint32_t>(r * 255.0f + 0.5f) << 16) |
                          (static_cast<uint32_t>(g * 255.0f + 0.5f) << 8) |
                          static_cast<uint32_t>(b * 255.0f + 0.5f);
            reinterpret_cast<uint32_t*>(row)[x] = px;
        }
    }
}
#endif

bool has_neon_support() {
#if NEON_AVAILABLE
    return true;
#else
    return false;
#endif
}

void gaussian_iir_rgba8888_neon(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma,
    bool doLinear
) {
#if NEON_AVAILABLE
    if (sigma <= 0.1f || w <= 0 || h <= 0 || !base) return;

    DericheCoeffs c = compute_deriche_coeffs(sigma);
    int maxDim = std::max(w, h);
    float* workBuf = new float[maxDim * 4];

    blur_horizontal_neon(base, w, h, stride, c, workBuf, doLinear);
    blur_vertical_neon(base, w, h, stride, c, workBuf, doLinear);

    delete[] workBuf;
#endif
}

void gaussian_iir_rgba8888_neon_fast(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma
) {
    gaussian_iir_rgba8888_neon(base, w, h, stride, sigma, false);
}

void gaussian_iir_rgba8888_neon_quality(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma
) {
    gaussian_iir_rgba8888_neon(base, w, h, stride, sigma, true);
}
