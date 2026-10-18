// How the reference sizes an image for the vision encoder, so llama.cpp sees the same pixels.
// Ported from DigUp's Sources/LlamaRuntime/ImageSizing.swift (MIT, Copyright (c) 2026 Abdur Rahim).
//
// mtmd fixes the token budget per context and only rescales images outside its range, while the reference always
// fills the budget. So images are resized here, and mtmd takes them as they are.
#pragma once

#include <cstdint>
#include <optional>
#include <utility>
#include <vector>

namespace omnifind {

// transformers' Gemma 4 image processor: scale to fill `budget` soft tokens (each 3 × 3 patches of 16 px) keeping the
// aspect ratio, each side rounded down to a multiple of 48 px. Returns (height, width), or nothing for an image too thin.
std::optional<std::pair<int, int>> target_size(int height, int width, int budget);

// Pillow's bicubic resampling of packed RGB (a = −0.5, the kernel widened when shrinking, 22-bit fixed point).
std::vector<uint8_t> resize_bicubic(const uint8_t* source, int width, int height, int to_width, int to_height);

}  // namespace omnifind
