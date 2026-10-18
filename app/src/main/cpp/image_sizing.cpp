// Ported from DigUp's Sources/LlamaRuntime/ImageSizing.swift (MIT, Copyright (c) 2026 Abdur Rahim).
#include "image_sizing.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>

namespace omnifind {
namespace {

constexpr int kPrecision = 22;

double filter(double value) {
    const double x = std::abs(value), a = -0.5;
    if (x < 1) return ((a + 2) * x - (a + 3)) * x * x + 1;
    if (x < 2) return (((x - 5) * x + 8) * x - 4) * a;
    return 0;
}

struct Coefficients {
    std::vector<int> bounds;       // per output pixel: first input pixel, how many contribute
    std::vector<int32_t> weights;  // per output pixel: `size` weights
    int size = 0;
};

Coefficients coefficients(int in_size, int out_size) {
    const double scale = double(in_size) / double(out_size);
    const double filter_scale = std::max(scale, 1.0);
    const double support = 2 * filter_scale;
    Coefficients c;
    c.size = int(std::ceil(support)) * 2 + 1;
    c.bounds.assign(size_t(out_size) * 2, 0);
    c.weights.assign(size_t(out_size) * c.size, 0);
    std::vector<double> row(c.size, 0);
    for (int out = 0; out < out_size; out++) {
        const double center = (double(out) + 0.5) * scale;
        const int first = std::max(int(center - support + 0.5), 0);
        const int count = std::min(int(center + support + 0.5), in_size) - first;
        double total = 0;
        for (int x = 0; x < c.size; x++) {
            row[x] = x < count ? filter((double(x + first) - center + 0.5) / filter_scale) : 0;
            total += row[x];
        }
        for (int x = 0; x < c.size; x++) {
            const double weight = x < count && total != 0 ? row[x] / total : row[x];
            c.weights[size_t(out) * c.size + x] = int32_t(weight * double(1 << kPrecision) + (weight < 0 ? -0.5 : 0.5));
        }
        c.bounds[size_t(out) * 2] = first;
        c.bounds[size_t(out) * 2 + 1] = count;
    }
    return c;
}

// Wrapping arithmetic, as Swift's &+ and &* (and Pillow's C).
inline int32_t wrap_add(int32_t a, int32_t b) { return int32_t(uint32_t(a) + uint32_t(b)); }
inline int32_t wrap_mul(int32_t a, int32_t b) { return int32_t(uint32_t(a) * uint32_t(b)); }

inline uint8_t clip(int32_t value) {
    const int32_t v = value >> kPrecision;
    return uint8_t(std::clamp(v, 0, 255));
}

std::vector<uint8_t> horizontal(const uint8_t* src, int in_width, int rows, int out_width) {
    const Coefficients c = coefficients(in_width, out_width);
    std::vector<uint8_t> out(size_t(out_width) * rows * 3);
    for (int y = 0; y < rows; y++) {
        for (int x = 0; x < out_width; x++) {
            const uint8_t* p = src + (size_t(y) * in_width + c.bounds[size_t(x) * 2]) * 3;
            int32_t r = 1 << (kPrecision - 1), g = r, b = r;
            for (int k = 0; k < c.bounds[size_t(x) * 2 + 1]; k++) {
                const int32_t w = c.weights[size_t(x) * c.size + k];
                r = wrap_add(r, wrap_mul(int32_t(p[0]), w));
                g = wrap_add(g, wrap_mul(int32_t(p[1]), w));
                b = wrap_add(b, wrap_mul(int32_t(p[2]), w));
                p += 3;
            }
            const size_t o = (size_t(y) * out_width + x) * 3;
            out[o] = clip(r);
            out[o + 1] = clip(g);
            out[o + 2] = clip(b);
        }
    }
    return out;
}

std::vector<uint8_t> vertical(const uint8_t* src, int in_height, int row_width, int out_height) {
    const Coefficients c = coefficients(in_height, out_height);
    const size_t stride = size_t(row_width) * 3;
    std::vector<uint8_t> out(stride * out_height);
    std::vector<int32_t> sums(stride);
    for (int y = 0; y < out_height; y++) {
        std::fill(sums.begin(), sums.end(), 1 << (kPrecision - 1));
        for (int k = 0; k < c.bounds[size_t(y) * 2 + 1]; k++) {
            const uint8_t* row = src + size_t(c.bounds[size_t(y) * 2] + k) * stride;
            const int32_t w = c.weights[size_t(y) * c.size + k];
            for (size_t i = 0; i < stride; i++) sums[i] = wrap_add(sums[i], wrap_mul(int32_t(row[i]), w));
        }
        for (size_t i = 0; i < stride; i++) out[size_t(y) * stride + i] = clip(sums[i]);
    }
    return out;
}

}  // namespace

std::optional<std::pair<int, int>> target_size(int height, int width, int budget) {
    const int patch = 16, pooling = 3;
    const int max_patches = budget * pooling * pooling;
    const double factor = std::sqrt(double(max_patches * patch * patch) / double(height * width));
    const int side = pooling * patch;
    int target_height = int(std::floor(factor * double(height) / double(side))) * side;
    int target_width = int(std::floor(factor * double(width) / double(side))) * side;
    if (target_height == 0 && target_width == 0) return std::nullopt;
    const int max_side = (max_patches / (pooling * pooling)) * side;
    if (target_height == 0) {
        target_height = side;
        target_width = std::min(width / height * side, max_side);
    } else if (target_width == 0) {
        target_width = side;
        target_height = std::min(height / width * side, max_side);
    }
    return std::make_pair(target_height, target_width);
}

std::vector<uint8_t> resize_bicubic(const uint8_t* source, int width, int height, int to_width, int to_height) {
    const bool w = to_width != width, h = to_height != height;
    if (w && h) {
        const std::vector<uint8_t> pass = horizontal(source, width, height, to_width);
        return vertical(pass.data(), height, to_width, to_height);
    }
    if (w) return horizontal(source, width, height, to_width);
    if (h) return vertical(source, height, width, to_height);
    return std::vector<uint8_t>(source, source + size_t(width) * height * 3);
}

}  // namespace omnifind
