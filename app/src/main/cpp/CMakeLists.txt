cmake_minimum_required(VERSION 3.22)
project(omnifind C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
include(llama-patch.cmake)

# llama.cpp: only the libraries and mtmd, as DigUp builds it, plus the Android switches from llama.cpp's docs/android.md.
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)   # cross-compiling: never tune for the build machine
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD      ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI        OFF CACHE BOOL "" FORCE)
set(MTMD_VIDEO            OFF CACHE BOOL "" FORCE)   # video goes in as frames + sound (later milestone)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # The Mi A1 (Cortex-A53) is plain ARMv8.0-A: dotprod, fp16 arithmetic or i8mm would crash it with SIGILL.
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
endif()
add_subdirectory(${LLAMA_DIR} llama.cpp EXCLUDE_FROM_ALL)

add_library(omnifind SHARED omnifind_jni.cpp embedder.cpp image_sizing.cpp vectors.cpp)
target_compile_options(omnifind PRIVATE -O3 -Wall)
target_link_libraries(omnifind PRIVATE llama mtmd ggml android log)
