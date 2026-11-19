#!/usr/bin/env bash
set -euo pipefail

# ShelfPulse AI - Native Dependencies Fetcher
# Pinned versions:
# - ncnn: 20241226 (vulkan + android)
# - opencv-mobile: 4.10.0
# - eigen: 3.4.0 (header-only)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TARGET_DIR="$PROJECT_ROOT/app/src/main/cpp/third_party"

mkdir -p "$TARGET_DIR"
cd "$TARGET_DIR"

echo "=== [1/3] Fetching ncnn-android-vulkan (20241226) ==="
NCNN_VERSION="20241226"
NCNN_URL="https://github.com/Tencent/ncnn/releases/download/${NCNN_VERSION}/ncnn-${NCNN_VERSION}-android-vulkan.zip"
if [ ! -d "ncnn-android-vulkan" ]; then
    echo "Downloading NCNN from $NCNN_URL..."
    curl -L -o ncnn.zip "$NCNN_URL"
    unzip -q ncnn.zip
    mv "ncnn-${NCNN_VERSION}-android-vulkan" ncnn-android-vulkan
    rm -f ncnn.zip
    echo "NCNN installed to $TARGET_DIR/ncnn-android-vulkan"
else
    echo "NCNN already present. Skipping."
fi

echo "=== [2/3] Fetching opencv-mobile (v29 / 4.10.0) ==="
OPENCV_MOBILE_VERSION="v29"
OPENCV_URL="https://github.com/nihui/opencv-mobile/releases/download/${OPENCV_MOBILE_VERSION}/opencv-mobile-4.10.0-android.zip"
if [ ! -d "opencv-mobile-android" ]; then
    echo "Downloading opencv-mobile from $OPENCV_URL..."
    curl -L -o opencv.zip "$OPENCV_URL"
    unzip -q opencv.zip
    mv "opencv-mobile-4.10.0-android" opencv-mobile-android
    rm -f opencv.zip
    echo "opencv-mobile installed to $TARGET_DIR/opencv-mobile-android"
else
    echo "opencv-mobile already present. Skipping."
fi

echo "=== [3/3] Fetching Eigen (3.4.0 Header-Only) ==="
EIGEN_VERSION="3.4.0"
EIGEN_URL="https://gitlab.com/libeigen/eigen/-/archive/${EIGEN_VERSION}/eigen-${EIGEN_VERSION}.zip"
if [ ! -d "eigen3" ]; then
    echo "Downloading Eigen from $EIGEN_URL..."
    curl -L -o eigen.zip "$EIGEN_URL"
    unzip -q eigen.zip
    mv "eigen-${EIGEN_VERSION}" eigen3
    rm -f eigen.zip
    echo "Eigen installed to $TARGET_DIR/eigen3"
else
    echo "Eigen already present. Skipping."
fi

echo "=== All native dependencies installed successfully ==="
