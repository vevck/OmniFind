# Applies DigUp's one-line llama.cpp patch, once and idempotently: without it, every input token reserves 1 MiB of
# logits an embedding model never makes (Gemma's 262k vocabulary), about 2 GB for a 2,048-token input.
# Also refuses a llama.cpp other than the commit DigUp's reference vectors were made with.
# In: LLAMA_DIR.
set(LLAMA_COMMIT 4d756bc72bf00a4aacf410ae15a2d315f3db400d)   # tag b11461, as DigUp's scripts/build-llama.sh
set(LLAMA_PATCH ${CMAKE_CURRENT_LIST_DIR}/../../../../patches/llama.cpp-no-logits.patch)

if (NOT EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp is missing: run scripts/setup-llama.sh")
endif()
execute_process(COMMAND git -C ${LLAMA_DIR} rev-parse HEAD OUTPUT_VARIABLE LLAMA_HEAD OUTPUT_STRIP_TRAILING_WHITESPACE)
if (NOT LLAMA_HEAD STREQUAL LLAMA_COMMIT)
    message(FATAL_ERROR "llama.cpp is at ${LLAMA_HEAD}, not ${LLAMA_COMMIT}: run scripts/setup-llama.sh")
endif()
execute_process(COMMAND git -C ${LLAMA_DIR} apply --reverse --check ${LLAMA_PATCH}
                RESULT_VARIABLE PATCH_APPLIED OUTPUT_QUIET ERROR_QUIET)
if (NOT PATCH_APPLIED EQUAL 0)
    execute_process(COMMAND git -C ${LLAMA_DIR} apply ${LLAMA_PATCH} RESULT_VARIABLE PATCH_RESULT)
    if (NOT PATCH_RESULT EQUAL 0)
        message(FATAL_ERROR "${LLAMA_PATCH} doesn't apply to ${LLAMA_DIR}")
    endif()
endif()
