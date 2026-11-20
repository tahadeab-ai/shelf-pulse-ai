<div align="center">

<img src="docs/assets/logo.svg" alt="ShelfPulse AI Logo" width="120" height="120" />

# ShelfPulse AI

**Offline Edge-AI Android Retail Inventory Counter powered by YOLO11, NCNN (Vulkan C++17), ByteTrack-cpp, and Jetpack Compose.**

[![Android Release](https://github.com/tahadeab-ai/shelf-pulse-ai/actions/workflows/android-release.yml/badge.svg)](https://github.com/tahadeab-ai/shelf-pulse-ai/actions/workflows/android-release.yml)
[![Android CI](https://github.com/tahadeab-ai/shelf-pulse-ai/actions/workflows/android-ci.yml/badge.svg)](https://github.com/tahadeab-ai/shelf-pulse-ai/actions/workflows/android-ci.yml)
[![GitHub Release](https://img.shields.io/github/v/release/tahadeab-ai/shelf-pulse-ai?color=22c55e&label=release)](https://github.com/tahadeab-ai/shelf-pulse-ai/releases/latest)
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/shelf-pulse-ai/blob/main/notebooks/train_yolo11_sku110k_colab.ipynb)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen.svg)](https://developer.android.com)
[![NDK](https://img.shields.io/badge/NDK-r27_C++17-orange.svg)](https://developer.android.com/ndk)
[![Vulkan](https://img.shields.io/badge/GPU-Vulkan_Enabled-red.svg)](https://www.vulkan.org/)

</div>

---

## ⚡ The Challenge & Solution

Traditional retail shelf audits require manual tallying or bulky handheld barcode scanners. Barcode scanners fail when items face inward or are tightly packed, while cloud-dependent computer vision apps suffer from latency, privacy risks, and warehouse connectivity blackouts.

**ShelfPulse AI** brings state-of-the-art dense object detection and multi-object tracking directly into retail workers' pockets:
- **100% Offline:** No cloud API calls, no subscriptions, zero data transmission outside the device.
- **Hardware-Accelerated:** Runs **YOLO11 NCNN FP16** with dynamic **CPU (ARM NEON)** and **Vulkan GPU** runtime toggles, delivering 10–14 FPS on Snapdragon 855 and 30+ FPS on modern flagships.
- **Persistent Tracking:** Employs **ByteTrack-cpp** (8D Kalman filter + LAPJV Hungarian matching) to eliminate duplicate item counting during continuous camera pans.
- **Human-in-the-Loop:** Dual-mode design with **Live Scan** and interactive **Snap & Verify** (+1 tap-to-add, -1 tap-to-exclude).
- **International by Default:** Native bilingual interface with complete **English** and **Arabic (العربية)** Right-to-Left (RTL) localization.

---

## 📱 App Screenshots

<div align="center">
  <table style="width: 100%; text-align: center;">
    <tr>
      <td width="33%"><b>Live Scan HUD (Live Telemetry & Breakdown)</b></td>
      <td width="33%"><b>Snap & Verify (Interactive Tally)</b></td>
      <td width="33%"><b>Audit History & Arabic RTL</b></td>
    </tr>
    <tr>
      <td><img src="docs/assets/screenshots/live_scan.png" alt="Live Scan Screen" width="260"/></td>
      <td><img src="docs/assets/screenshots/snap_verify.png" alt="Snap & Verify Screen" width="260"/></td>
      <td><img src="docs/assets/screenshots/history_arabic.png" alt="Arabic RTL Screen" width="260"/></td>
    </tr>
  </table>
</div>

---

## 📊 Authentic Device Benchmarks

Every metric below was captured directly from a physical Android device (**Samsung Galaxy Note 10 / SM-N970U**, Qualcomm Snapdragon 855 with 1x Kryo 485 @ 2.84 GHz + 3x Kryo 485 @ 2.42 GHz + 4x Kryo 485 @ 1.78 GHz, Adreno 640 GPU) running **ShelfPulse AI v1.1.1 Release APK** via verified ADB logcat telemetry.

### Stage-by-Stage Latency Breakdown (640×640 YOLO11 NCNN Inference)

| Execution Pipeline Stage | CPU (4 Big Cores, ARM NEON FP16) | Vulkan GPU (Adreno 640 FP16) | Optimization Impact vs Baseline v1.0.0 |
| :--- | :--- | :--- | :--- |
| **CameraX Color & Preprocessing** | **1.6 – 2.5 ms** | **2.0 – 5.5 ms** | **~30× faster** (replaces scalar NV21 loop & `cv::rotate`) |
| **YOLO11 NCNN Inference** | **68.1 – 110.4 ms** (avg ~95 ms) | **153.2 – 176.8 ms** (avg ~165 ms) | CPU is **1.7× faster** than Adreno 640 GPU |
| **ByteTrack Tracking (8D Kalman)** | **0.01 – 0.05 ms** | **0.01 ms** | LAPJV & Kalman update runs in microsecond regime |
| **Total Native Frame Latency** | **70.6 – 114.2 ms** (avg ~98 ms) | **156.8 – 184.3 ms** (avg ~170 ms) | Predictable, stable, zero per-frame heap allocations |
| **Sustained Real-World FPS (EMA)** | **10.2 – 13.7 FPS** | **5.5 – 6.2 FPS** | **+163% throughput increase** over baseline 5.2 FPS |

### Architectural Insight: Why CPU NEON Outperforms Adreno 640 on Snapdragon 855

On older Adreno GPUs (such as Adreno 640), Vulkan FP16 execution incurs non-trivial command buffer submission overhead, shader pipeline dispatch latency, and memory synchronization penalties on sequential per-frame vision inference. By contrast, four big Kryo 485 cores running tuned **ARM NEON FP16 OpenMP** instructions keep intermediate layer tensors in high-speed L1/L2 caches, eliminating memory traffic and achieving lower end-to-end latency (**~95 ms** CPU vs **~165 ms** GPU). The app provides a live HUD toggle (`CPU NEON` / `GPU VULKAN`) allowing real-time benchmarking on any attached device.

### Benchmark Evolution (Baseline vs v1.1.1)

| Metric | Baseline v1.0.0 (Pre-Optimization) | v1.1.1 (Measured On-Device) | Target Threshold | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Live Scan Preprocessing** | > 60 ms (1080p YUV + NV21 copy + cv::rotate) | **1.6 – 2.5 ms** (640x480 RGBA + kanna_rotate_c4) | < 10 ms | 🚀 Exceeded |
| **Live Scan Total Latency** | 192.3 ms (~5.2 FPS) | **78.7 – 98.2 ms** (~10.2 – 13.7 FPS) | < 100 ms | ✅ Smooth |
| **Snap & Verify Latency** | ~280 ms | **95 ms (CPU) / 165 ms (GPU)** | < 300 ms | ⚡ Fast |
| **Peak Heap Allocations** | Per-frame dynamic std::vector reallocations | **0 B (Reusable member buffers)** | 0 dynamic realloc | 🛡️ Zero OOM |
| **Cold Startup Time** | ~450 ms | **380 ms** | < 1000 ms | ⚡ Instant |
| **APK Binary Size** | 19.8 MB (unminified) | **17.7 MB** (`isMinifyEnabled = true`, R8) | < 25 MB | 🍃 Lightweight |

---

## 🏗️ System Architecture

```mermaid
flowchart LR
    subgraph Capture ["Android Client (Kotlin + Jetpack Compose)"]
        Cam["CameraX Preview & ImageAnalysis<br/>(RGBA_8888 640x480 Keep-Latest)"]
        HUD["Material 3 HUD Canvas<br/>(Live Bounding Boxes, Stage Breakdown & Backend Toggle)"]
        DB[("Room SQLite DB<br/>Audit Sessions")]
    end

    subgraph NativeBridge ["JNI Native Bridge (C++17)"]
        DirectBuf["Reusable Pre-Allocated Buffers<br/>(kanna_rotate_c4 + Row Stride Handling)"]
    end

    subgraph VisionEngine ["Native Vision Engine (lib_shelfpulse_native.so)"]
        Yolo["YOLO11 NCNN FP16<br/>(NEON CPU 4-Thread / Vulkan GPU)"]
        Parser["Dynamic Tensor Parser<br/>([4+C, N] Dynamic Classes)"]
        Tracker["ByteTrack-cpp<br/>(Kalman 8D + LAPJV via Eigen 3.4)"]
    end

    Cam --> DirectBuf
    DirectBuf --> Yolo
    Yolo --> Parser
    Parser --> Tracker
    Tracker --> HUD
    HUD --> DB
```

For in-depth mathematical derivations of the Kalman Filter state vectors, dynamic class count detection, and zero-copy JNI plane re-construction, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 🚀 Quick Start Guide

### Option 1: Install Pre-Built Release APK (Fastest)
1. Download the latest `ShelfPulse-AI-arm64-v8a.apk` from [GitHub Releases](https://github.com/tahadeab-ai/shelf-pulse-ai/releases/latest).
2. Install via ADB or your phone's file manager:
   ```bash
   adb install -r ShelfPulse-AI-arm64-v8a.apk
   ```

### Option 2: Train Custom SKU Models on Google Colab
Train or fine-tune YOLO11 on the universal retail **SKU-110K** dataset with zero local GPU requirements:
1. Click the **[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/tahadeab-ai/shelf-pulse-ai/blob/main/notebooks/train_yolo11_sku110k_colab.ipynb)** badge.
2. Run all cells in `notebooks/train_yolo11_sku110k_colab.ipynb` to download SKU-110K, train with tuned retail data augmentations, evaluate mAP metrics, and export NCNN FP16 weights (`model.ncnn.param`, `model.ncnn.bin`).
3. Replace the weights in `app/src/main/assets/models/`.

### Option 3: Automated Cloud CI/CD Build
This project strictly enforces clean Git hygiene and zero local binary bloat:
1. Fork or clone the repository:
   ```bash
   git clone https://github.com/tahadeab-ai/shelf-pulse-ai.git
   cd shelf-pulse-ai
   ```
2. Trigger the automated GitHub Actions workflow:
   ```bash
   gh workflow run android-release.yml -f tag_name=v1.0.0
   ```
3. GitHub Actions builds the native C++17 library using cached NDK r27, NCNN, opencv-mobile, and Eigen, runs all unit tests, and publishes the signed artifact.

---

## 🛠️ Tech Stack & Credits

- **Object Detection:** [Ultralytics YOLO11](https://github.com/ultralytics/ultralytics) (Glenn Jocher et al.)
- **Neural Network Inference:** [Tencent NCNN](https://github.com/Tencent/ncnn) (nihui) with Vulkan GPU acceleration
- **Computer Vision Utilities:** [opencv-mobile](https://github.com/nihui/opencv-mobile) (nihui)
- **Multi-Object Tracking:** [ByteTrack](https://github.com/ifzhang/ByteTrack) (Zhang et al.)
- **Linear Algebra:** [Eigen 3.4.0](https://gitlab.com/libeigen/eigen)
- **Android Platform:** Kotlin 2.0.21, Jetpack Compose, CameraX, Room SQLite, Material 3
- **Dataset:** [SKU-110K Dense Object Detection](https://github.com/eg4000/SKU110K_CVPR19) (Goldman et al.)

---

## 📄 License & Compliance

- **Application & Native Engine:** Licensed under the [Apache License, Version 2.0](LICENSE).
- **Google Colab Training Notebook:** Utilizes Ultralytics YOLO11, licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). Training is strictly segregated to the cloud notebook to keep the core Android application free from copyleft encumbrance.
