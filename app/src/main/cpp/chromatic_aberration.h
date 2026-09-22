#ifndef CHROMATIC_ABERRATION_H
#define CHROMATIC_ABERRATION_H

#include <cstdint>
#include <cstddef>

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
    float redOffset = 0.0f,
    float greenOffset = -0.05f,
    float blueOffset = -0.1f,
    bool useBilinear = true
);

void chromatic_aberration_rgba8888_inplace(
    const uint8_t* source,
    const uint8_t* displacement,
    uint8_t* result,
    int width,
    int height,
    int stride,
    float intensity,
    float scale,
    float redOffset = 0.0f,
    float greenOffset = -0.05f,
    float blueOffset = -0.1f,
    bool useBilinear = true
);

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
    float dpr = 1.0f,
    bool useBilinear = true
);

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
    float dpr = 1.0f,
    bool useBilinear = true
);

#endif // CHROMATIC_ABERRATION_H
