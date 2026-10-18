// JNI bridge for com.omnifind.app.engine.Embedder. Every call comes from one Kotlin thread at a time.
#include <android/log.h>
#include <jni.h>

#include <string>
#include <vector>

#include "embedder.h"
#include "llama.h"
#include "mtmd-helper.h"

namespace {

constexpr const char* kTag = "OmniFind/llama";

// llama.cpp logs a lot at load time: warnings and errors go to logcat, the rest is dropped.
void log_callback(ggml_log_level level, const char* text, void*) {
    static ggml_log_level last = GGML_LOG_LEVEL_INFO;  // CONT continues the previous message, at its level
    if (level != GGML_LOG_LEVEL_CONT) last = level;
    if (last >= GGML_LOG_LEVEL_WARN && text) {
        __android_log_write(last == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN, kTag, text);
    }
}

std::string string_of(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

// Java's modified UTF-8 mangles characters outside the BMP (emoji), so texts cross as UTF-8 bytes.
std::string string_of(JNIEnv* env, jbyteArray bytes) {
    const jsize length = env->GetArrayLength(bytes);
    std::string out(size_t(length), '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(out.data()));
    return out;
}

void throw_java(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("com/omnifind/app/engine/EmbedderException");
    if (type) env->ThrowNew(type, message);
}

omnifind::Embedder* from(jlong handle) { return reinterpret_cast<omnifind::Embedder*>(handle); }

jfloatArray array_of(JNIEnv* env, const std::vector<float>& vector) {
    jfloatArray out = env->NewFloatArray(jsize(vector.size()));
    env->SetFloatArrayRegion(out, 0, jsize(vector.size()), vector.data());
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_omnifind_app_engine_Embedder_nativeLoad(JNIEnv* env, jclass, jstring text_model,
                                                                         jstring media_model, jboolean text_only,
                                                                         jint threads, jint max_tokens) {
    static const bool logging = [] {
        llama_log_set(log_callback, nullptr);
        mtmd_helper_log_set(log_callback, nullptr);
        return true;
    }();
    (void)logging;
    try {
        omnifind::EmbedderOptions options;
        options.text_model = string_of(env, text_model);
        options.media_model = string_of(env, media_model);
        options.text_only = text_only;
        options.threads = threads;
        options.max_tokens = max_tokens;
        return reinterpret_cast<jlong>(new omnifind::Embedder(options));
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return 0;
    }
}

JNIEXPORT void JNICALL Java_com_omnifind_app_engine_Embedder_nativeFree(JNIEnv*, jclass, jlong handle) {
    delete from(handle);
}

JNIEXPORT jdouble JNICALL Java_com_omnifind_app_engine_Embedder_nativeLoadSeconds(JNIEnv*, jclass, jlong handle) {
    return from(handle)->load_seconds();
}

JNIEXPORT jint JNICALL Java_com_omnifind_app_engine_Embedder_nativeDimension(JNIEnv*, jclass, jlong handle) {
    return from(handle)->dimension();
}

// texts: UTF-8 byte arrays. Returns the vectors back to back (n × dimension).
JNIEXPORT jfloatArray JNICALL Java_com_omnifind_app_engine_Embedder_nativeEmbedTexts(JNIEnv* env, jclass,
                                                                                     jlong handle,
                                                                                     jobjectArray texts) {
    try {
        std::vector<std::string> inputs;
        const jsize count = env->GetArrayLength(texts);
        inputs.reserve(size_t(count));
        for (jsize i = 0; i < count; i++) {
            auto bytes = static_cast<jbyteArray>(env->GetObjectArrayElement(texts, i));
            inputs.push_back(string_of(env, bytes));
            env->DeleteLocalRef(bytes);
        }
        std::vector<float> flat;
        for (const auto& vector : from(handle)->embed_texts(inputs)) flat.insert(flat.end(), vector.begin(), vector.end());
        return array_of(env, flat);
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return nullptr;
    }
}

JNIEXPORT jfloatArray JNICALL Java_com_omnifind_app_engine_Embedder_nativeEmbedImage(JNIEnv* env, jclass,
                                                                                     jlong handle, jstring path,
                                                                                     jint budget) {
    try {
        return array_of(env, from(handle)->embed_image(string_of(env, path), budget));
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return nullptr;
    }
}

JNIEXPORT jfloatArray JNICALL Java_com_omnifind_app_engine_Embedder_nativeEmbedAudio(JNIEnv* env, jclass,
                                                                                     jlong handle, jstring path) {
    try {
        return array_of(env, from(handle)->embed_audio(string_of(env, path)));
    } catch (const std::exception& error) {
        throw_java(env, error.what());
        return nullptr;
    }
}

JNIEXPORT jstring JNICALL Java_com_omnifind_app_engine_Embedder_nativeSystemInfo(JNIEnv* env, jclass) {
    return env->NewStringUTF(llama_print_system_info());
}

}  // extern "C"
