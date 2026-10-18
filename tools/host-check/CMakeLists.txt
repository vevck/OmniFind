# Builds the Android embedder's C++ on the Mac (CPU only, like the phone) and checks its vectors against DigUp's
# reference vectors, without a phone or the Android SDK. See tools/host-check/README.md.
cmake_minimum_required(VERSION 3.22)
project(omnifind_host_check C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp)
set(LLAMA_DIR ${CPP}/llama.cpp)
include(${CPP}/llama-patch.cmake)

set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(GGML_METAL            OFF CACHE BOOL "" FORCE)   # CPU, as on the phone
set(GGML_BLAS             OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD      ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI        OFF CACHE BOOL "" FORCE)
set(MTMD_VIDEO            OFF CACHE BOOL "" FORCE)
add_subdirectory(${CPP}/llama.cpp llama.cpp EXCLUDE_FROM_ALL)

add_executable(host_check main.cpp ${CPP}/embedder.cpp ${CPP}/image_sizing.cpp)
target_include_directories(host_check PRIVATE ${CPP})
target_compile_options(host_check PRIVATE -O3 -Wall)
target_link_libraries(host_check PRIVATE llama mtmd ggml)
