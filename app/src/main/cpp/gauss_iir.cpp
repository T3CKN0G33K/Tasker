#include "gauss_iir.h"
#include <cmath>
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "GaussIIR"
#ifdef NATIVEGAUSS_VERBOSE
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void)0)
#endif
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static inline float srgb_to_linear(float srgb) {
    return srgb * srgb * (srgb * 0.2f + 0.8f);
}

static inline float linear_to_srgb(float linear) {
    float x = sqrtf(linear);
    return x * (1.0f - 0.2f * linear);
}

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

static void iir_filter_1d(const float* src, float* dst, int len, const DericheCoeffs& c) {
    if (len <= 0) return;

    float yp1 = src[0] * c.coefp;
    float yp2 = yp1;
    float xp1 = src[0];

    for (int i = 0; i < len; ++i) {
        float xc = src[i];
        float yc = c.a0 * xc + c.a1 * xp1 - c.b1 * yp1 - c.b2 * yp2;
        dst[i] = yc;
        xp1 = xc;
        yp2 = yp1; yp1 = yc;
    }

    float yn1 = src[len - 1] * c.coefn;
    float yn2 = yn1;
    float xn1 = src[len - 1];
    float xn2 = xn1;

    for (int i = len - 1; i >= 0; --i) {
        float xc = src[i];
        float yc = c.a2 * xn1 + c.a3 * xn2 - c.b1 * yn1 - c.b2 * yn2;
        dst[i] += yc;
        xn2 = xn1; xn1 = xc;
        yn2 = yn1; yn1 = yc;
    }
}

static void blur_horizontal(
    uint8_t* base,
    int w, int h, int stride,
    const DericheCoeffs& c,
    float* buffer,
    bool doLinear
) {
    for (int y = 0; y < h; ++y) {
        uint8_t* row = base + y * stride;
        for (int x = 0; x < w; ++x) {
            uint8_t b = row[x * 4 + 0];
            uint8_t g = row[x * 4 + 1];
            uint8_t r = row[x * 4 + 2];
            uint8_t a = row[x * 4 + 3];

            float fa = a / 255.0f;
            float fr = r / 255.0f;
            float fg = g / 255.0f;
            float fb = b / 255.0f;

            if (doLinear) {
                if (fa > 0.001f) {
                    fr = srgb_to_linear(fr / fa);
                    fg = srgb_to_linear(fg / fa);
                    fb = srgb_to_linear(fb / fa);
                } else {
                    fr = fg = fb = 0.0f;
                }
            }

            buffer[x] = fr;
            buffer[w + x] = fg;
            buffer[2 * w + x] = fb;
            buffer[3 * w + x] = fa;
        }

        iir_filter_1d(buffer, buffer, w, c);
        iir_filter_1d(buffer + w, buffer + w, w, c);
        iir_filter_1d(buffer + 2 * w, buffer + 2 * w, w, c);
        iir_filter_1d(buffer + 3 * w, buffer + 3 * w, w, c);

        for (int x = 0; x < w; ++x) {
            float fr = buffer[x];
            float fg = buffer[w + x];
            float fb = buffer[2 * w + x];
            float fa = buffer[3 * w + x];

            fa = std::max(0.0f, std::min(1.0f, fa));

            if (doLinear) {
                fr = linear_to_srgb(fr) * fa;
                fg = linear_to_srgb(fg) * fa;
                fb = linear_to_srgb(fb) * fa;
            }

            int r = static_cast<int>(std::max(0.0f, std::min(255.0f, fr * 255.0f + 0.5f)));
            int g = static_cast<int>(std::max(0.0f, std::min(255.0f, fg * 255.0f + 0.5f)));
            int b = static_cast<int>(std::max(0.0f, std::min(255.0f, fb * 255.0f + 0.5f)));
            int a = static_cast<int>(fa * 255.0f + 0.5f);

            row[x * 4 + 0] = static_cast<uint8_t>(b);
            row[x * 4 + 1] = static_cast<uint8_t>(g);
            row[x * 4 + 2] = static_cast<uint8_t>(r);
            row[x * 4 + 3] = static_cast<uint8_t>(a);
        }
    }
}

static void blur_vertical(
    uint8_t* base,
    int w, int h, int stride,
    const DericheCoeffs& c,
    float* buffer,
    bool doLinear
) {
    for (int x = 0; x < w; ++x) {
        for (int y = 0; y < h; ++y) {
            uint8_t* pixel = base + y * stride + x * 4;
            uint8_t b = pixel[0];
            uint8_t g = pixel[1];
            uint8_t r = pixel[2];
            uint8_t a = pixel[3];

            float fa = a / 255.0f;
            float fr = r / 255.0f;
            float fg = g / 255.0f;
            float fb = b / 255.0f;

            if (doLinear) {
                if (fa > 0.001f) {
                    fr = srgb_to_linear(fr / fa);
                    fg = srgb_to_linear(fg / fa);
                    fb = srgb_to_linear(fb / fa);
                } else {
                    fr = fg = fb = 0.0f;
                }
            }

            buffer[y] = fr;
            buffer[h + y] = fg;
            buffer[2 * h + y] = fb;
            buffer[3 * h + y] = fa;
        }

        iir_filter_1d(buffer, buffer, h, c);
        iir_filter_1d(buffer + h, buffer + h, h, c);
        iir_filter_1d(buffer + 2 * h, buffer + 2 * h, h, c);
        iir_filter_1d(buffer + 3 * h, buffer + 3 * h, h, c);

        for (int y = 0; y < h; ++y) {
            float fr = buffer[y];
            float fg = buffer[h + y];
            float fb = buffer[2 * h + y];
            float fa = buffer[3 * h + y];

            fa = std::max(0.0f, std::min(1.0f, fa));

            if (doLinear) {
                fr = linear_to_srgb(fr) * fa;
                fg = linear_to_srgb(fg) * fa;
                fb = linear_to_srgb(fb) * fa;
            }

            int r = static_cast<int>(std::max(0.0f, std::min(255.0f, fr * 255.0f + 0.5f)));
            int g = static_cast<int>(std::max(0.0f, std::min(255.0f, fg * 255.0f + 0.5f)));
            int b = static_cast<int>(std::max(0.0f, std::min(255.0f, fb * 255.0f + 0.5f)));
            int a = static_cast<int>(fa * 255.0f + 0.5f);

            uint8_t* pixel = base + y * stride + x * 4;
            pixel[0] = static_cast<uint8_t>(b);
            pixel[1] = static_cast<uint8_t>(g);
            pixel[2] = static_cast<uint8_t>(r);
            pixel[3] = static_cast<uint8_t>(a);
        }
    }
}

void gaussian_iir_rgba8888_inplace(
    uint8_t* base,
    int w, int h, int stride,
    float sigma,
    bool doLinear
) {
    if (!base || w <= 0 || h <= 0 || stride < w * 4) return;
    if (sigma <= 0.1f) return;
    if (sigma > 50.0f) sigma = 50.0f;

    DericheCoeffs c = compute_deriche_coeffs(sigma);
    int maxDim = std::max(w, h);
    float* buffer = new float[maxDim * 4];

    blur_horizontal(base, w, h, stride, c, buffer, doLinear);
    blur_vertical(base, w, h, stride, c, buffer, doLinear);

    delete[] buffer;
}

void gaussian_iir_rgba8888_fast(uint8_t* base, int w, int h, int stride, float sigma) {
    gaussian_iir_rgba8888_inplace(base, w, h, stride, sigma, false);
}

void gaussian_iir_rgba8888_quality(uint8_t* base, int w, int h, int stride, float sigma) {
    gaussian_iir_rgba8888_inplace(base, w, h, stride, sigma, true);
}
