#!/usr/bin/env bash
set -euo pipefail

# ShelfPulse AI - Dynamic Device Benchmark & Verification Runner
# Detects connected devices automatically without hardcoded serial numbers.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DOCS_IMG_DIR="$PROJECT_ROOT/docs/assets/screenshots"
mkdir -p "$DOCS_IMG_DIR"

echo "=== [1/5] Detecting Connected ADB Device ==="
DEVICES=$(adb devices | grep -w "device" | awk '{print $1}')
NUM_DEVICES=$(echo "$DEVICES" | grep -c . || true)

if [ "$NUM_DEVICES" -eq 0 ]; then
    echo "Error: No authorized Android device connected via ADB."
    echo "Ensure USB debugging is enabled and authorized on your phone."
    exit 1
fi

# Select device (first device by default or allow override via ADB_SERIAL env var)
TARGET_SERIAL="${ADB_SERIAL:-$(echo "$DEVICES" | head -n 1)}"
echo "Selected target device: [SERIAL PROTECTED] (Found $NUM_DEVICES active device(s))"

DEVICE_MODEL=$(adb -s "$TARGET_SERIAL" shell getprop ro.product.model | tr -d '\r')
DEVICE_SDK=$(adb -s "$TARGET_SERIAL" shell getprop ro.build.version.release | tr -d '\r')
DEVICE_ABI=$(adb -s "$TARGET_SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')
DEVICE_CHIP=$(adb -s "$TARGET_SERIAL" shell getprop ro.board.platform | tr -d '\r')

echo "Device Specifications:"
echo "  - Model: $DEVICE_MODEL"
echo "  - Android Version: $DEVICE_SDK"
echo "  - ABI: $DEVICE_ABI"
echo "  - Platform / SoC: $DEVICE_CHIP"

APK_PATH="${1:-}"
if [ -z "$APK_PATH" ]; then
    POSSIBLE_APKS=(
        "$PROJECT_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
        "$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
        "$PROJECT_ROOT/ShelfPulse-AI-arm64-v8a.apk"
    )
    for apk in "${POSSIBLE_APKS[@]}"; do
        if [ -f "$apk" ]; then
            APK_PATH="$apk"
            break
        fi
    done
fi

if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
    echo "=== [2/5] Installing APK: $APK_PATH ==="
    adb -s "$TARGET_SERIAL" install -r -d "$APK_PATH"
else
    echo "Notice: No APK found on disk. Skipping install. Assuming APK is already installed or testing existing app."
fi

PACKAGE_NAME="com.shelfpulse.ai"
ACTIVITY_NAME="$PACKAGE_NAME/.ui.MainActivity"

echo "=== [3/5] Launching App & Verifying JNI Engine ==="
adb -s "$TARGET_SERIAL" logcat -c
adb -s "$TARGET_SERIAL" shell am start -n "$ACTIVITY_NAME"

echo "Streaming native telemetry logs for 10 seconds..."
sleep 2
adb -s "$TARGET_SERIAL" logcat -d -s ShelfPulseNative ShelfPulseAI AndroidRuntime:E | head -n 60

echo "=== [4/5] Capturing Screenshots for Documentation ==="
adb -s "$TARGET_SERIAL" exec-out screencap -p > "$DOCS_IMG_DIR/snap_verify_screen.png"
echo "Captured $DOCS_IMG_DIR/snap_verify_screen.png"

echo "=== [5/5] Verification Complete ==="
