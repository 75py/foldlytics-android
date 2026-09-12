cmake_minimum_required(VERSION 3.22.1)
project(foldlytics_llama LANGUAGES C CXX)
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

include(FetchContent)
# llama.cpp b6500. Source is downloaded only by the developer's build, never the app.
FetchContent_Declare(llama
    URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/a7a98e0fffed794396b3fbad4dcdbbc184963645
    URL_HASH SHA256=4d78e6aa4a9124b58dff994416525294fbab2990fe905a640d4cbd26bf563a31
)
FetchContent_MakeAvailable(llama)

# Keep inference kernels optimized even in debug APKs: unoptimized quantized
# matrix multiplication is too slow for useful on-device integration tests.
foreach(inference_target llama ggml-base ggml-cpu)
    target_compile_options(${inference_target} PRIVATE $<$<CONFIG:Debug>:-O3>)
endforeach()

add_library(foldlytics_llama SHARED llama_insight_jni.cpp)
target_link_libraries(foldlytics_llama PRIVATE llama)
if(ANDROID)
    target_link_options(foldlytics_llama PRIVATE "-Wl,-z,max-page-size=16384")
else()
    # Allows compiling the same JNI bridge for local JVM smoke tests.
    find_package(JNI REQUIRED)
    target_include_directories(foldlytics_llama PRIVATE ${JNI_INCLUDE_DIRS})
    option(FOLDLYTICS_BUILD_SMOKE_TEST "Build a local model smoke test" OFF)
    if(FOLDLYTICS_BUILD_SMOKE_TEST)
        add_executable(foldlytics_inference_smoke smoke_test.cpp)
        target_include_directories(foldlytics_inference_smoke PRIVATE ${JNI_INCLUDE_DIRS})
        target_link_libraries(foldlytics_inference_smoke PRIVATE llama)
    endif()
endif()
