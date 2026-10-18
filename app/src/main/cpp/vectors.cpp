// Scores a query against every stored vector: rows × 768 float16 (little-endian, in a direct ByteBuffer) · 768 float32.
// Both sides are L2-normalized, so each dot product is a cosine. NEON on arm64 (ARMv8.0: the A53 has no fp16 math, but
// converting fp16 to fp32 is base AdvSIMD); plain C++ elsewhere (the x86_64 emulator).
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <vector>

#if defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace {

constexpr int kDimension = 768;

#if !defined(__aarch64__)
float half_to_float(uint16_t h) {
    const uint32_t sign = uint32_t(h & 0x8000) << 16;
    uint32_t exponent = (h >> 10) & 0x1F;
    uint32_t mantissa = h & 0x3FF;
    uint32_t bits;
    if (exponent == 0) {
        if (mantissa == 0) {
            bits = sign;
        } else {  // subnormal: normalize it
            exponent = 127 - 15 + 1;
            while ((mantissa & 0x400) == 0) {
                mantissa <<= 1;
                exponent--;
            }
            bits = sign | (exponent << 23) | ((mantissa & 0x3FF) << 13);
        }
    } else if (exponent == 31) {
        bits = sign | 0x7F800000 | (mantissa << 13);
    } else {
        bits = sign | ((exponent + 127 - 15) << 23) | (mantissa << 13);
    }
    float out;
    std::memcpy(&out, &bits, sizeof out);
    return out;
}
#endif

void score(const uint16_t* rows, int count, const float* query, float* out) {
    for (int r = 0; r < count; r++) {
        const uint16_t* v = rows + size_t(r) * kDimension;
#if defined(__aarch64__)
        float32x4_t a0 = vdupq_n_f32(0), a1 = vdupq_n_f32(0);
        for (int i = 0; i < kDimension; i += 8) {
            const float16x8_t h = vreinterpretq_f16_u16(vld1q_u16(v + i));
            a0 = vfmaq_f32(a0, vcvt_f32_f16(vget_low_f16(h)), vld1q_f32(query + i));
            a1 = vfmaq_f32(a1, vcvt_f32_f16(vget_high_f16(h)), vld1q_f32(query + i + 4));
        }
        out[r] = vaddvq_f32(vaddq_f32(a0, a1));
#else
        float sum = 0;
        for (int i = 0; i < kDimension; i++) sum += half_to_float(v[i]) * query[i];
        out[r] = sum;
#endif
    }
}

}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_omnifind_app_search_VectorMath_nativeScores(
    JNIEnv* env, jclass, jobject matrix, jint rows, jfloatArray query) {
    auto* data = static_cast<const uint16_t*>(env->GetDirectBufferAddress(matrix));
    if (!data || env->GetDirectBufferCapacity(matrix) < jlong(rows) * kDimension * 2 ||
        env->GetArrayLength(query) != kDimension) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "matrix or query of the wrong size");
        return nullptr;
    }
    std::vector<float> q(kDimension);
    env->GetFloatArrayRegion(query, 0, kDimension, q.data());
    std::vector<float> scores(static_cast<size_t>(rows));
    score(data, rows, q.data(), scores.data());
    jfloatArray out = env->NewFloatArray(rows);
    env->SetFloatArrayRegion(out, 0, rows, scores.data());
    return out;
}
