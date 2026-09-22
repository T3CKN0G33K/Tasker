#ifndef GAUSS_IIR_H
#define GAUSS_IIR_H

#include <cstdint>
#include <cstddef>

void gaussian_iir_rgba8888_inplace(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma,
    bool doLinear
);

void gaussian_iir_rgba8888_fast(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma
);

void gaussian_iir_rgba8888_quality(
    uint8_t* base,
    int w,
    int h,
    int stride,
    float sigma
);

#endif // GAUSS_IIR_H
