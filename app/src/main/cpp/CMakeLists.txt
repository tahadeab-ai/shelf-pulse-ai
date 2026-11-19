cmake_minimum_required(VERSION 3.22.1)
project(shelfpulse_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for ARM64 NEON
add_compile_options(-O3 -fvisibility=hidden -fvisibility-inlines-hidden)

# Paths for dependencies fetched via scripts/fetch_native_deps.sh
set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party)

# 1. Eigen3 (Header-only)
set(EIGEN3_INCLUDE_DIR ${THIRD_PARTY_DIR}/eigen3)
if (EXISTS ${EIGEN3_INCLUDE_DIR})
    include_directories(${EIGEN3_INCLUDE_DIR})
    message(STATUS "Eigen3 include path found: ${EIGEN3_INCLUDE_DIR}")
else()
    message(WARNING "Eigen3 path missing: ${EIGEN3_INCLUDE_DIR}. Run scripts/fetch_native_deps.sh")
endif()

# 2. opencv-mobile
set(OpenCV_DIR ${THIRD_PARTY_DIR}/opencv-mobile-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)
include_directories(${OpenCV_INCLUDE_DIRS})
message(STATUS "OpenCV found. Include dirs: ${OpenCV_INCLUDE_DIRS}")

# 3. ncnn-android-vulkan
set(ncnn_DIR ${THIRD_PARTY_DIR}/ncnn-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)
message(STATUS "ncnn found for ABI: ${ANDROID_ABI}")

# Project Include Directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/bytetrack
)

# Source files
set(SOURCE_FILES
    src/yolo11_ncnn.cpp
    src/bytetrack_wrapper.cpp
    src/jni_bridge.cpp
)

add_library(_shelfpulse_native SHARED ${SOURCE_FILES})

# Link libraries
target_link_libraries(_shelfpulse_native
    ncnn
    ${OpenCV_LIBS}
    jnigraphics
    log
)
