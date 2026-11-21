# Model Card: YOLO11-SKU110K Edge Retail Detector

## Model Overview
- **Model Name:** `yolo11n-sku110k`
- **Architecture:** Ultralytics YOLO11 Nano (backbone: modified CSPDarknet with C3k2 and SPPF blocks)
- **Task:** Class-agnostic dense retail product detection and counting
- **Target Hardware:** Android ARM64 (`arm64-v8a`) mobile edge devices (CPU NEON + Vulkan Compute GPU)
- **Inference Engine:** Tencent `ncnn` (v20241226) with zero-copy JNI direct byte buffer bindings
- **Model License:** Apache 2.0 (weights and edge deployment pipeline)

---

## Technical Specifications

### Input & Preprocessing
| Parameter | Live Scan Mode | Snap & Verify (Single) | SAHI 2x2 Dense Tiling | SAHI 3x3 Ultra Tiling |
| :--- | :--- | :--- | :--- | :--- |
| **Input Shape** | `320 × 320 × 3` | `640 × 640 × 3` | $4 \times (640 \times 640 \times 3)$ | $9 \times (640 \times 640 \times 3)$ |
| **Color Space** | BGR (OpenCV format) | BGR (OpenCV format) | BGR (OpenCV format) | BGR (OpenCV format) |
| **Normalization** | Scale `1/255.0f`, mean `[0, 0, 0]` | Scale `1/255.0f`, mean `[0, 0, 0]` | Scale `1/255.0f`, mean `[0, 0, 0]` | Scale `1/255.0f`, mean `[0, 0, 0]` |
| **Padding** | Dynamic Letterbox (114 gray) | Dynamic Letterbox (114 gray) | 20% Crop Overlap | 20% Crop Overlap |

### Output Tensor Format
- **Raw Output:** `[1, 5, N]` (bounding box coordinates `cx, cy, w, h` + objectness confidence score)
- **C++ Post-Processing:**
  - Confidence threshold filtering (`conf >= 0.25f`, default UI slider `0.32f`)
  - Class-agnostic IoU Non-Maximum Suppression (NMS, `iou >= 0.45f`)
  - **Geometric False-Positive Filtering (v2.3.0):**
    - Max area filtering: $\text{box\_area} \le 0.45 \times \text{frame\_area}$ (suppresses giant non-product background regions)
    - Aspect ratio bounds: $0.125 \le \frac{w}{h} \le 8.0$ (suppresses elongated shelf rails and wall borders)
  - Letterbox offset inversion (`pad_w`, `pad_h`, `scale`)
  - Coordinate normalization strictly clamped to `[0.0f, 1.0f]` relative to the original unpadded input image:
    $$x_{norm} = \frac{x_{letterbox} - \text{pad}_w}{\text{scale} \cdot \text{width}_{orig}}$$
    $$y_{norm} = \frac{y_{letterbox} - \text{pad}_h}{\text{scale} \cdot \text{height}_{orig}}$$

---

## Weights Fetching & Packaging

The binary model parameters and network definition files are distributed via the official GitHub Releases under tag `models-v1.1.0`:
- `model.ncnn.param`: Network topology and layer definitions (~24 KB)
- `model.ncnn.bin`: Quantized FP16 weights (~5.8 MB)

### Automated Download
To populate model weights for local building and testing:
```bash
# Unix / macOS / Linux / CI
chmod +x scripts/fetch_models.sh
./scripts/fetch_models.sh

# Windows PowerShell
.\scripts\fetch_models.ps1
```
The script places weights into `app/src/main/assets/models/model.ncnn.{param,bin}`.

---

## Inference Modes & Edge Trade-offs

```
                  ┌──────────────────────────────────────────────┐
                  │          Camera / Frame Source               │
                  └──────────────────────┬───────────────────────┘
                                         │
                 ┌───────────────────────┴──────────────────────┐
                 │                                              │
         [Live Scan 30 FPS]                           [Snap & Verify]
                 │                                              │
         320px Direct Detect                           Select Inference Mode
                 │                                      /        |        \
          Vulkan GPU Engine                       Single 640px SAHI 2x2  SAHI 3x3 Ultra
         (15.9ms / 29.3 FPS)                         (~95ms)    (5 passes) (10 passes)
                 │                                      │        |        /
                 │                                      │     Geometric Filter
                 │                                      │        |        /
                 │                                      │      Global NMS
                 │                                      │        |        /
                 └───────────────────────┬──────────────┴────────┴────────┘
                                         │
                             CoordinateMapper (Fit Scale)
                                         │
                             Interactive Bounding Box Canvas
```

1. **320px Live Scan:** Optimized for continuous viewfinder scanning. Downsamples input frames to 320px for peak Vulkan GPU throughput (~15.9 ms inference), enabling fluid 30 FPS real-time feedback.
2. **640px Single Scan:** Balanced mode for normal shelf displays (10–50 items), providing sub-100ms response time with high localization precision.
3. **SAHI 2x2 Tiled High-Resolution Scan:** Splits high-resolution captures into four overlapping 640x640 windows + 1 global overview, processes each tile through the NCNN engine, maps coordinates back to root image space, and merges duplicate cross-tile detections using Global NMS. Ideal for dense retail displays (50–150 items).
4. **SAHI 3x3 Ultra Tiling (v2.3.0):** Deconstructs high-resolution images into 9 overlapping tiles (20% overlap) + 1 global overview pass (10 passes total) with geometric false-positive filtering. Designed for ultra-dense warehouse racking and supermarket aisles containing 150+ small items.

---

## Retraining Recipe: Google Colab YOLO11s/YOLO11m on SKU-110K

For upgrading weights or tailoring to custom retail SKUs, the repository includes a self-contained Google Colab notebook at `notebooks/train_yolo11_sku110k_colab.ipynb`:

1. **Base Architectures:** `yolo11s.pt` (recommended for edge accuracy/latency balance) or `yolo11m.pt` (maximum feature capacity).
2. **Retail Augmentations:**
   - `mosaic=1.0`: Multi-image mosaic blending to expose the network to diverse dense packings.
   - `copy_paste=0.3`: Synthetic product pasting to simulate occlusions.
   - `mixup=0.1`, `degrees=0.0`: Rotation disabled to preserve upright shelf orientation priors.
3. **PNNX NCNN Export with Nihui Dynamic Shape Patch:**
   - Directly converts PyTorch `.pt` to PNNX `.param` and `.bin`.
   - Patches the parameter definition so the model runs seamlessly across any dynamic input shape (`320x320`, `640x640`, `800x800`).
   - Quantized to FP16 with ARM NEON / Vulkan GPU kernel optimization.

---

## Performance Benchmarks (Samsung SM-N970U)

> [!NOTE]
> Physical device benchmarks were verified on Samsung Galaxy Note 10 (SM-N970U, Exynos 9825 / Mali-G76 MP12) running Android 12 prior to v2.0.1+. Pending re-benchmarking upon device reconnection.

| Engine Mode | Input Dimension | Inference Time | Total Frame Latency | Effective FPS |
| :--- | :--- | :--- | :--- | :--- |
| **Vulkan GPU (Live)** | `320 × 320` | **15.9 ms** | **30.9 ms** | **30.2 FPS** |
| **CPU NEON (Live)** | `320 × 320` | 78.7 ms | 98.2 ms | 10.2 FPS |
| **Snap & Verify (Single)** | `640 × 640` | 95.0 ms | 293.0 ms | Single Frame |
| **SAHI 2x2 HD (Tiled)** | $4 \times (640 \times 640)$ | 380.0 ms | 620.0 ms | Single Frame |

---

## Known Limitations & Mitigation

1. **Class-Agnostic Output:** The core YOLO11-SKU110K weights detect generic consumer packaging boundaries without granular SKU classification. ShelfPulse AI mitigates this via integrated offline barcode scanning (`zxing-cpp`), Open Food Facts metadata enrichment, and manual SKU catalog binding.
2. **Extreme Low-Light Conditions:** Dense packaging in poorly lit grocery aisles can lead to missed detections. Mitigated by integrated CameraX torch assist and auto-flash capture.
3. **Severe Perspective Distortion:** Extreme oblique angle scanning (>60° off-axis) can skew bounding box aspect ratios. Merchants are guided via the UI viewfinder to maintain roughly perpendicular alignment with shelf facings.

---

## Responsible AI & Data Privacy
- **100% Offline by Default:** YOLO11 inference, ByteTrack multi-object tracking, and barcode decoding execute entirely on the local device without network connectivity.
- **Merchant Data Sovereignty:** Inventory audit sessions, coordinates, and photo proofs are stored in an encrypted local SQLite/Room database and are only pushed to external ERP/webhooks if explicitly configured by the merchant.
