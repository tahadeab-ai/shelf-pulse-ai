#!/usr/bin/env bash
set -euo pipefail

# ShelfPulse AI - Model Fetcher
# Fetches trained NCNN model weights from GitHub Releases.
# If unavailable, downloads standard YOLO11n NCNN baseline weights for smoke testing.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MODEL_DIR="$PROJECT_ROOT/app/src/main/assets/models"
REPO="tahadeab-ai/shelf-pulse-ai"
TAG="${1:-models-v1.1.0}"

mkdir -p "$MODEL_DIR"

if [ -f "$MODEL_DIR/model.ncnn.param" ] && [ -f "$MODEL_DIR/model.ncnn.bin" ]; then
    echo "Models already exist at $MODEL_DIR. Skipping download."
    exit 0
fi

echo "Attempting to fetch model from GitHub Release $TAG in $REPO..."
DOWNLOAD_SUCCESS=0

if command -v gh &> /dev/null; then
    if gh release download "$TAG" -R "$REPO" -D "$MODEL_DIR" --pattern "*.ncnn.*" 2>/dev/null; then
        echo "Successfully fetched model from GitHub Release $TAG."
        DOWNLOAD_SUCCESS=1
    fi
fi

if [ "$DOWNLOAD_SUCCESS" -eq 0 ]; then
    echo "Release $TAG not found. Falling back to baseline YOLO11n NCNN smoke-test model..."
    FALLBACK_URL="https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11n_ncnn_model.zip"
    TEMP_ZIP="$MODEL_DIR/fallback_yolo11n.zip"

    if curl -sLf -o "$TEMP_ZIP" "$FALLBACK_URL"; then
        unzip -q -o "$TEMP_ZIP" -d "$MODEL_DIR"
        rm -f "$TEMP_ZIP"
        
        # Normalize file names if needed
        if [ -f "$MODEL_DIR/yolo11n_ncnn_model/model.ncnn.param" ]; then
            mv "$MODEL_DIR/yolo11n_ncnn_model/model.ncnn.param" "$MODEL_DIR/model.ncnn.param"
            mv "$MODEL_DIR/yolo11n_ncnn_model/model.ncnn.bin" "$MODEL_DIR/model.ncnn.bin"
            rm -rf "$MODEL_DIR/yolo11n_ncnn_model"
        fi
        echo "Baseline YOLO11n NCNN model installed to $MODEL_DIR."
    else
        echo "Notice: Could not download fallback archive. Model files must be trained and placed in $MODEL_DIR."
    fi
fi

if [ -f "$MODEL_DIR/model.ncnn.param" ] && [ -f "$MODEL_DIR/model.ncnn.bin" ]; then
    cd "$MODEL_DIR"
    sha256sum model.ncnn.param model.ncnn.bin > SHA256SUMS.txt || true
    echo "Model integrity checksums recorded."
fi
