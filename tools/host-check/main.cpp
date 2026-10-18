// Runs omnifind::Embedder on the Mac's CPU over a job list and prints one vector per line.
//
//   host_check <text.gguf> <mmproj.gguf> <threads> < jobs.tsv
//
// jobs.tsv, one per line: name \t text|image|audio \t budget \t payload (the prompt-framed text, or a file path).
// Output, one per line: name \t seconds \t v0,v1,…  (run.py writes the jobs and compares the vectors).
#include <chrono>
#include <cstdio>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "embedder.h"

int main(int argc, char** argv) {
    if (argc < 4) {
        std::cerr << "usage: host_check <text.gguf> <mmproj.gguf> <threads> < jobs.tsv\n";
        return 2;
    }
    omnifind::EmbedderOptions options;
    options.text_model = argv[1];
    options.media_model = argv[2];
    options.text_only = false;
    options.threads = std::stoi(argv[3]);
    try {
        omnifind::Embedder embedder(options);
        std::cerr << "loaded in " << embedder.load_seconds() << " s, dimension " << embedder.dimension() << "\n";
        std::string line;
        while (std::getline(std::cin, line)) {
            if (line.empty()) continue;
            std::stringstream fields(line);
            std::string name, kind, budget, payload;
            std::getline(fields, name, '\t');
            std::getline(fields, kind, '\t');
            std::getline(fields, budget, '\t');
            std::getline(fields, payload);
            const auto started = std::chrono::steady_clock::now();
            std::vector<float> vector;
            if (kind == "text") vector = embedder.embed_texts({payload}).at(0);
            else if (kind == "image") vector = embedder.embed_image(payload, std::stoi(budget));
            else vector = embedder.embed_audio(payload);
            const double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
            std::printf("%s\t%.3f\t", name.c_str(), seconds);
            for (size_t i = 0; i < vector.size(); i++) std::printf(i ? ",%.8g" : "%.8g", double(vector[i]));
            std::printf("\n");
        }
    } catch (const std::exception& error) {
        std::cerr << "error: " << error.what() << "\n";
        return 1;
    }
    return 0;
}
