// A port of DigUp's Sources/LlamaRuntime/LlamaEmbedder.swift (MIT, Copyright (c) 2026 Abdur Rahim).
#include "embedder.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <memory>

#include "image_sizing.h"
#include "llama.h"
#include "mtmd-helper.h"
#include "mtmd.h"

namespace omnifind {
namespace {

// llama.cpp's process-wide setup, once.
void init_backend() {
    static const bool done = [] {
        llama_backend_init();
        return true;
    }();
    (void)done;
}

struct ChunksDeleter {
    void operator()(mtmd_input_chunks* chunks) const { mtmd_input_chunks_free(chunks); }
};
struct BitmapDeleter {
    void operator()(mtmd_bitmap* bitmap) const { mtmd_bitmap_free(bitmap); }
};
using Bitmap = std::unique_ptr<mtmd_bitmap, BitmapDeleter>;

}  // namespace

Embedder::Embedder(const EmbedderOptions& options) {
    const auto started = std::chrono::steady_clock::now();
    init_backend();
    if (!options.text_only && options.media_model.empty()) {
        throw EmbedderError("the image and audio encoders (mmproj) aren't there yet");
    }
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // the Mi A1's Adreno 506 is no faster than its CPU
    model_ = llama_model_load_from_file(options.text_model.c_str(), model_params);
    if (!model_) throw EmbedderError("can't load " + options.text_model);

    max_tokens_ = options.max_tokens > 0 ? options.max_tokens : (options.text_only ? 512 : 2048);
    llama_context_params params = llama_context_default_params();
    params.n_ctx = uint32_t(max_tokens_);
    params.n_batch = uint32_t(max_tokens_);
    params.n_ubatch = uint32_t(max_tokens_);
    params.n_seq_max = kMaxSequences;
    params.kv_unified = true;  // each input may use the whole context, as in llama-embedding
    params.embeddings = true;
    params.pooling_type = LLAMA_POOLING_TYPE_MEAN;
    params.n_threads = options.threads;
    params.n_threads_batch = options.threads;
    context_ = llama_init_from_model(model_, params);
    if (!context_) {
        llama_model_free(model_);
        model_ = nullptr;
        throw EmbedderError("can't create a llama.cpp context");
    }
    if (!options.text_only) {
        mtmd_context_params media_params = mtmd_context_params_default();
        media_params.use_gpu = false;
        media_params.print_timings = false;
        media_params.warmup = true;
        media_params.image_min_tokens = 1;  // never rescale: images arrive at their final size (image_sizing)
        media_params.image_max_tokens = 1120;
        media_params.n_threads = options.threads;
        media_ = mtmd_init_from_file(options.media_model.c_str(), model_, media_params);
        if (!media_) {
            llama_free(context_);
            llama_model_free(model_);
            context_ = nullptr;
            model_ = nullptr;
            throw EmbedderError("can't load " + options.media_model);
        }
    }
    batch_ = llama_batch_ext_init(context_);
    vocab_ = llama_model_get_vocab(model_);
    embedding_size_ = llama_model_n_embd_out(model_);
    row_size_ = llama_model_n_embd_inp(model_);

    // Pay for the first call's setup now, not on the first search.
    embed_texts({"task: search result | query: warm up"});
    load_seconds_ = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
}

Embedder::~Embedder() {
    if (batch_) llama_batch_ext_free(batch_);
    if (media_) mtmd_free(media_);
    if (context_) llama_free(context_);
    if (model_) llama_model_free(model_);
}

// MARK: Public

std::vector<std::vector<float>> Embedder::embed_texts(const std::vector<std::string>& texts) {
    reset();
    std::vector<std::vector<float>> vectors;
    vectors.reserve(texts.size());
    for (const std::string& text : texts) {
        const std::vector<int> tokens = tokenize(text);
        if (sequences_ == kMaxSequences || tokens_ + int(tokens.size()) > max_tokens_) {
            for (auto& v : run()) vectors.push_back(std::move(v));
        }
        begin();
        for (int token : tokens) add_token(token);
    }
    if (sequences_ > 0) {
        for (auto& v : run()) vectors.push_back(std::move(v));
    }
    return vectors;
}

std::vector<float> Embedder::embed_image(const std::string& path, int budget) {
    if (!media_) throw EmbedderError("this embedder was started text-only");
    if (std::find(std::begin(kBudgets), std::end(kBudgets), budget) == std::end(kBudgets)) {
        throw EmbedderError("budget must be 70, 140, 280, 560 or 1120");
    }
    return embed_media(path, true, budget);
}

std::vector<float> Embedder::embed_audio(const std::string& path) {
    if (!media_) throw EmbedderError("this embedder was started text-only");
    return embed_media(path, false, 0);
}

// MARK: Media

std::vector<float> Embedder::embed_media(const std::string& path, bool image, int budget) {
    Bitmap bitmap(load(path, image, budget));
    std::unique_ptr<mtmd_input_chunks, ChunksDeleter> chunks(mtmd_input_chunks_init());
    const char* marker = mtmd_default_marker();
    mtmd_input_text text{marker, std::strlen(marker), /*add_special=*/true, /*parse_special=*/true};
    const mtmd_bitmap* bitmaps[] = {bitmap.get()};
    const int32_t status = mtmd_tokenize(media_, chunks.get(), &text, bitmaps, 1);
    if (status != 0) throw EmbedderError("can't tokenize " + path + " (" + std::to_string(status) + ")");
    reset();
    begin();
    const size_t count = mtmd_input_chunks_size(chunks.get());
    for (size_t index = 0; index < count; index++) {
        const mtmd_input_chunk* chunk = mtmd_input_chunks_get(chunks.get(), index);
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t n = 0;
            const llama_token* tokens = mtmd_input_chunk_get_tokens_text(chunk, &n);
            if (!tokens) continue;
            for (size_t i = 0; i < n; i++) add_token(tokens[i]);
        } else {
            if (mtmd_encode_chunk(media_, chunk) != 0) throw EmbedderError("the encoder failed on " + path);
            const float* rows = mtmd_get_output_embd(media_);
            if (!rows) throw EmbedderError("the encoder gave no output for " + path);
            add_rows(rows, mtmd_input_chunk_get_n_tokens(chunk));
        }
    }
    return run().at(0);
}

// Reads an image or audio file with mtmd's helpers (stb_image, miniaudio at 16 kHz), resizes images for `budget` and
// cuts audio at 30 s. The caller frees the bitmap.
mtmd_bitmap* Embedder::load(const std::string& path, bool image, int budget) {
    mtmd_helper_bitmap_wrapper wrapper =
        mtmd_helper_bitmap_init_from_file(media_, path.c_str(), false, mtmd_helper_init_opt_default());
    if (wrapper.video_ctx) {
        if (wrapper.bitmap) mtmd_bitmap_free(wrapper.bitmap);
        mtmd_helper_video_free(wrapper.video_ctx);
        throw EmbedderError("video goes in as frames and sound, not as a file: " + path);
    }
    if (!wrapper.bitmap) throw EmbedderError("can't read " + path);
    Bitmap bitmap(wrapper.bitmap);
    if (mtmd_bitmap_is_audio(bitmap.get()) == image) {
        throw EmbedderError(std::string("not ") + (image ? "an image" : "audio") + ": " + path);
    }
    if (image) {
        const int width = int(mtmd_bitmap_get_nx(bitmap.get())), height = int(mtmd_bitmap_get_ny(bitmap.get()));
        const auto target = target_size(height, width, budget);
        if (!target) throw EmbedderError("image too thin to embed: " + path);
        const auto [to_height, to_width] = *target;
        if (to_height == height && to_width == width) return bitmap.release();
        const std::vector<uint8_t> pixels =
            resize_bicubic(mtmd_bitmap_get_data(bitmap.get()), width, height, to_width, to_height);
        mtmd_bitmap* resized = mtmd_bitmap_init(uint32_t(to_width), uint32_t(to_height), pixels.data());
        if (!resized) throw EmbedderError("can't resize " + path);
        return resized;
    }
    const size_t samples = mtmd_bitmap_get_n_bytes(bitmap.get()) / sizeof(float);
    const size_t limit = size_t(30) * size_t(mtmd_get_audio_sample_rate(media_));
    if (samples <= limit) return bitmap.release();
    const float* data = reinterpret_cast<const float*>(mtmd_bitmap_get_data(bitmap.get()));
    mtmd_bitmap* prefix = mtmd_bitmap_init_from_audio(limit, data);
    if (!prefix) throw EmbedderError("can't cut " + path + " at 30 s");
    return prefix;
}

// MARK: Text model

// `<bos> text <eos>`, cut to max_tokens_ the way transformers cuts (keeping both special tokens).
std::vector<int> Embedder::tokenize(const std::string& text) const {
    std::vector<llama_token> tokens(text.size() + 8);
    int32_t count = llama_tokenize(vocab_, text.c_str(), int32_t(text.size()), tokens.data(), int32_t(tokens.size()),
                                   /*add_special=*/true, /*parse_special=*/true);
    if (count < 0) {
        tokens.resize(size_t(-count));
        count = llama_tokenize(vocab_, text.c_str(), int32_t(text.size()), tokens.data(), int32_t(tokens.size()), true,
                               true);
    }
    tokens.resize(size_t(std::max(count, 0)));
    if (int(tokens.size()) > max_tokens_) {
        tokens.resize(size_t(max_tokens_ - 1));
        tokens.push_back(llama_vocab_eos(vocab_));
    }
    return std::vector<int>(tokens.begin(), tokens.end());
}

// Starts the next input in the batch (each input is its own sequence).
void Embedder::begin() {
    sequences_ += 1;
    position_ = 0;
}

// Drops everything added so far, including an input that failed halfway.
void Embedder::reset() {
    llama_batch_ext_clear(batch_);
    sequences_ = 0;
    tokens_ = 0;
}

void Embedder::add_token(int token) { place(llama_batch_ext_add_token(batch_, sequences_ - 1, token)); }

void Embedder::add_rows(const float* rows, size_t count) {
    for (size_t row = 0; row < count; row++) {
        llama_embd embd{rows + row * size_t(row_size_), 1, size_t(row_size_)};  // copied by the batch
        place(llama_batch_ext_add_embd(batch_, sequences_ - 1, embd));
    }
}

void Embedder::place(int index) {
    if (index < 0) throw EmbedderError("batch full (" + std::to_string(index) + ")");
    llama_pos pos[4] = {position_, 0, 0, 0};
    if (!llama_batch_ext_set_pos(batch_, index, pos) || !llama_batch_ext_set_output_embd(batch_, index, true)) {
        throw EmbedderError("can't place token " + std::to_string(index));
    }
    position_ += 1;
    tokens_ += 1;
}

// Runs the text model over the batch and returns one L2-normalized vector per input, in order.
std::vector<std::vector<float>> Embedder::run() {
    struct ResetOnExit {
        Embedder* self;
        ~ResetOnExit() { self->reset(); }
    } guard{this};
    const int32_t status = llama_process(context_, LLAMA_PROCESS_TYPE_ENCODE, batch_);
    if (status != 0) throw EmbedderError("llama_process failed (" + std::to_string(status) + ")");
    std::vector<std::vector<float>> vectors;
    vectors.reserve(size_t(sequences_));
    for (int sequence = 0; sequence < sequences_; sequence++) {
        const float* pooled = llama_get_embeddings_seq(context_, sequence);
        if (!pooled) throw EmbedderError("no pooled embedding for input " + std::to_string(sequence));
        double sum = 0;
        for (int i = 0; i < embedding_size_; i++) sum += double(pooled[i]) * double(pooled[i]);
        const float scale = sum > 0 ? float(1 / std::sqrt(sum)) : 0.f;
        std::vector<float> vector(static_cast<size_t>(embedding_size_));
        for (int i = 0; i < embedding_size_; i++) vector[size_t(i)] = pooled[i] * scale;
        vectors.push_back(std::move(vector));
    }
    return vectors;
}

}  // namespace omnifind
