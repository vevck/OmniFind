// EmbeddingGemma 2 on llama.cpp, on the CPU. A port of DigUp's Sources/LlamaRuntime/LlamaEmbedder.swift
// (MIT, Copyright (c) 2026 Abdur Rahim).
//
// The image and audio encoders (mtmd) feed the text model, and its mean-pooled, L2-normalized output is the vector.
// Inputs are framed as the reference frames them: `<bos> text <eos>`, `<bos> <|image> soft tokens <image|> <eos>` and
// the same for audio. The model is non-causal with no KV cache, so each input goes through one llama_process call.
//
// One thread at a time.
#pragma once

#include <stdexcept>
#include <string>
#include <vector>

struct llama_model;
struct llama_context;
struct llama_vocab;
struct llama_batch_ext;
struct mtmd_context;
struct mtmd_bitmap;

namespace omnifind {

struct EmbedderOptions {
    std::string text_model;   // embeddinggemma-2-Q8_0.gguf
    std::string media_model;  // mmproj-embeddinggemma-2-Q8_0.gguf, or empty for text only
    bool text_only = true;
    int threads = 4;
    // One input must fit one micro-batch. 2048 is the reference's cut and matches DigUp's vectors on long text; queries
    // are short, so the query encoder reserves less. 0: 512 text-only, else 2048.
    int max_tokens = 0;
};

class EmbedderError : public std::runtime_error {
  public:
    using std::runtime_error::runtime_error;
};

class Embedder {
  public:
    static constexpr int kMaxSequences = 16;
    static constexpr int kBudgets[] = {70, 140, 280, 560, 1120};

    explicit Embedder(const EmbedderOptions& options);
    ~Embedder();
    Embedder(const Embedder&) = delete;
    Embedder& operator=(const Embedder&) = delete;

    int dimension() const { return embedding_size_; }
    bool has_media() const { return media_ != nullptr; }
    double load_seconds() const { return load_seconds_; }

    // One L2-normalized vector per text, in order (texts carry their task prompt already).
    std::vector<std::vector<float>> embed_texts(const std::vector<std::string>& texts);
    // `budget`: 70, 140, 280, 560 or 1120 vision soft tokens.
    std::vector<float> embed_image(const std::string& path, int budget);
    // The first 30 s (wav, mp3, flac: what miniaudio reads).
    std::vector<float> embed_audio(const std::string& path);

  private:
    std::vector<float> embed_media(const std::string& path, bool image, int budget);
    mtmd_bitmap* load(const std::string& path, bool image, int budget);
    std::vector<int> tokenize(const std::string& text) const;
    void begin();
    void reset();
    void add_token(int token);
    void add_rows(const float* rows, size_t count);
    void place(int index);
    std::vector<std::vector<float>> run();

    llama_model* model_ = nullptr;
    llama_context* context_ = nullptr;
    mtmd_context* media_ = nullptr;
    llama_batch_ext* batch_ = nullptr;
    const llama_vocab* vocab_ = nullptr;
    int embedding_size_ = 0;  // 768: the output projection
    int row_size_ = 0;        // the width of encoder rows fed to the text model
    int max_tokens_ = 0;
    int sequences_ = 0;       // in batch_
    int tokens_ = 0;          // in batch_
    int position_ = 0;
    double load_seconds_ = 0;
};

}  // namespace omnifind
