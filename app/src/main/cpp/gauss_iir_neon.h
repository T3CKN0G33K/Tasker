#ifndef GAUSS_IIR_NEON_H
#define GAUSS_IIR_NEON_H

#include <cstdint>
#include <cstddef>

bool has_neon_support();

void gaussian_iir_rgba8888_neon(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma,
    bool doLinear
);

void gaussian_iir_rgba8888_neon_fast(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma
);

void gaussian_iir_rgba8888_neon_quality(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma
);

#endif // GAUSS_IIR_NEON_H
