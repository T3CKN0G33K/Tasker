#ifndef BOXBLUR_H
#define BOXBLUR_H

#include <cstdint>
#include <cstddef>

void box3_rgba8888_inplace(
    uint8_t* base,
    int w,
    int h,
    int stride,
    int radius
);

void box_blur_single_pass(
    const uint8_t* src,
    uint8_t* dst,
    int w,
    int h,
    int stride,
    int radius
);

void advanced_box_blur_rgba8888(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float radius,
    float downscale
);

void advanced_box_blur_rgba8888_hq(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height,
    int stride,
    float radius,
    float downscale
);

#endif // BOXBLUR_H
