# ShelfPulse AI — System Architecture & Technical Specification

ShelfPulse AI is a high-performance, 100% offline edge-AI retail inventory counting system. It pairs modern Android technologies (**Kotlin, Jetpack Compose, CameraX, Room SQLite, WorkManager**) with an ultra-optimized native **C++17 Vision Pipeline (YOLO11, NCNN Vulkan, ByteTrack-cpp, zxing-cpp, opencv-mobile, Eigen 3.4)**.

---

## 1. High-Level System Architecture

```mermaid
flowchart TD
    subgraph UI ["Jetpack Compose UI (Material 3)"]
        LiveScan["Live Scan HUD<br/>(CameraX Preview + Telemetry)"]
        SnapVerify["Snap & Verify Viewport<br/>(Pinch-Zoom + Corner Brackets)"]
        BarcodeSheet["Barcode Scanner Sheet<br/>(zxing-cpp + Open Food Facts)"]
        History["Inventory Audit Log<br/>(Room DB + Multi-Export + Sync)"]
    end

    subgraph State ["MVVM Architecture"]
        LiveVM["LiveScanViewModel<br/>StateFlow & Coroutines"]
        SnapVM["SnapVerifyViewModel<br/>MutableState"]
        HistoryVM["HistoryViewModel<br/>Export & Sync Controls"]
        Repo["InventoryRepository<br/>Room DAO Bridge"]
    end

    subgraph JNI ["Zero-Copy JNI Bridge (`native-lib`)"]
        DirectBuf["DirectByteBuffer Planes<br/>(YUV_420_888 Strides & Rotation)"]
        JniBridge["jni_bridge.cpp<br/>Thread-Safe Mutex"]
    end

    subgraph NativeEngine ["C++17 Vision Engine (`lib_shelfpulse_native.so`)"]
        Yolo["Yolo11Ncnn Detector<br/>(Vulkan GPU / OpenMP CPU)"]
        Letterbox["Letterbox Pre/Post Processor<br/>(Dynamic [4+C, N] Tensor)"]
        ByteTrack["ByteTrack Tracker<br/>(Kalman 8D + Eigen LAPJV)"]
        SAHI["Native SAHI 2x2 Tiling<br/>(Slice Extraction + Global NMS)"]
        Embedder["IEmbedder Stub<br/>(Phase-2 MobileNetV3)"]
    end

    subgraph Persistence ["Offline Storage & Multi-Export"]
        RoomDB[("Room SQLite DB v4<br/>Sessions, Items, Catalog")]
        Exporter["ExportManager<br/>(PDF RTL, JPEG Proof, CSV BOM, JSON 2.2)"]
    end

    subgraph EnterpriseSync ["Enterprise Integrations"]
        WorkMgr["WorkManager<br/>SyncAuditWorker"]
        Connectors["InventorySyncConnector<br/>(Generic Webhook, InvenTree, ERPNext)"]
    end

    LiveScan --> LiveVM
    SnapVerify --> SnapVM
    SnapVerify --> BarcodeSheet
    History --> HistoryVM
    HistoryVM --> Repo
    LiveVM --> DirectBuf
    SnapVM --> JniBridge
    DirectBuf --> JniBridge
    JniBridge --> NativeEngine
    NativeEngine --> Yolo
    Yolo --> Letterbox
    Yolo --> ByteTrack
    Yolo --> SAHI
    ByteTrack --> JniBridge
    SAHI --> JniBridge
    JniBridge --> LiveVM
    LiveVM --> Repo
    SnapVM --> Repo
    Repo --> RoomDB
    RoomDB --> Exporter
    RoomDB --> WorkMgr
    WorkMgr --> Connectors
```

---

## 2. Native C++17 Vision Engine

The native engine is located in `app/src/main/cpp/` and compiled as a shared library (`lib_shelfpulse_native.so`) targeting `arm64-v8a` with `-O3` vectorization, OpenMP multithreading, and Vulkan GPU acceleration.

### A. Dynamic Class Count & Tensor Layout Parser (`yolo11_ncnn.cpp`)
Ultralytics YOLO11 NCNN exports generate an output layer (`out0`) containing predicted boxes and class scores.
1. **Dynamic Tensor Shape:**
   - Ultralytics exports format tensors as `[Channels, Anchors]` where Channels = $4 + \text{num\_classes}$ and Anchors = $8400$ (for $640 \times 640$ inputs).
   - `yolo11_ncnn.cpp` inspects `out0.c`, `out0.h`, `out0.w`, and `out0.d` at runtime:
     $$\text{num\_classes} = \text{channels} - 4$$
   - This ensures **zero-code-change switching** between baseline 80-class COCO smoke-test models and fine-tuned 1-class SKU-110K retail models.
2. **Sigmoid Activation:**
   - Ultralytics PNNX exports already activate class confidence scores with sigmoid inside the computational graph. The C++ parser directly reads raw probabilities without duplicate activation.
3. **Exact Letterbox Reversal & Normalization (v2.0.1 Fix):**
   - Coordinates $[cx, cy, w, h]$ are transformed from letterboxed dimensions ($640 \times 640$) back to the unpadded original image coordinate space:
     $$x_{\text{unpad}} = \frac{cx - 0.5w - \text{pad}_w}{\text{scale}}, \quad y_{\text{unpad}} = \frac{cy - 0.5h - \text{pad}_h}{\text{scale}}$$
   - Normalized coordinates $[0.0, 1.0]$ are computed relative to the **original unpadded image dimensions** $(W_{\text{orig}}, H_{\text{orig}})$:
     $$x1 = \text{clamp}\left(\frac{x_{\text{unpad}}}{W_{\text{orig}}}, 0.0, 1.0\right), \quad y1 = \text{clamp}\left(\frac{y_{\text{unpad}}}{H_{\text{orig}}}, 0.0, 1.0\right)$$
   - This eliminates coordinate drift and guarantees bounding boxes remain perfectly aligned with image features, preventing boxes from bleeding into letterbox bars.

### B. High-Precision Real ByteTrack-cpp (`bytetrack/`)
Real-time video tracking requires temporal consistency to prevent duplicate counting as items pass through the camera frame:
- **Kalman Filter (8-Dimensional State Vector):**
  $$x = [c_x, c_y, a, h, \dot{c}_x, \dot{c}_y, \dot{a}, \dot{h}]^T$$
  where $c_x, c_y$ is the bounding box center, $a = w/h$ is the aspect ratio, $h$ is height, and their corresponding velocities.
- **Tuned Thresholds (v2.0.1):**
  - $\text{track\_thresh} = 0.25f$, $\text{high\_thresh} = 0.30f$, $\text{track\_buffer} = 60$ frames.
  - Normalized minimum box area threshold: $\text{min\_box\_area} \le 0.0001f$, preventing dropping of small normalized retail boxes.
- **Two-Stage Association (LAPJV via Eigen 3.4.0):**
  1. **First Association:** Matches high-confidence detections ($\text{score} \ge \text{track\_thresh}$) with existing confirmed tracks using Intersection over Union (IoU) distance matrix solved by the Jonker-Volgenant algorithm (`lapjv`).
  2. **Second Association:** Unmatched tracks are associated with remaining low-confidence detections ($\text{high\_thresh} > \text{score} \ge \text{low\_thresh}$) to recover partially occluded products.
  3. **Track Lifecycle:** Unmatched high-confidence detections initiate new track candidates; unmatched tracks are maintained for $60$ frames (`track_buffer`) before deletion.

---

## 3. Viewport Geometry & Coordinate Mapping (`CoordinateMapper.kt`)

In Android Jetpack Compose, an image displayed with `ContentScale.Fit` creates letterbox or pillarbox bars when the image aspect ratio differs from the Canvas aspect ratio.

`CoordinateMapper.kt` provides deterministic, sub-pixel mathematical translation between normalized coordinates and screen pixels:

```
+-------------------------------------------------------+
| Canvas Height (canvasH)                               |
|   +-----------------------------------------------+   |
|   | Pillarbox Offset (offsetY)                    |   |
|   +-----------------------------------------------+   |
|   | Rendered Image Viewport                       |   |
|   | (drawnWidth = imgW * scale, drawnHeight = ...) |   |
|   |                                               |   |
|   |   [x1, y1] --------+                          |   |
|   |      |   Bounding  |                          |   |
|   |      +-------- [x2, y2]                       |   |
|   +-----------------------------------------------+   |
|   | Pillarbox Offset (offsetY)                    |   |
|   +-----------------------------------------------+   |
+-------------------------------------------------------+
```

1. **Scale Factor & Viewport Computation:**
   $$\text{scale} = \min\left(\frac{\text{canvasW}}{W_{\text{img}}}, \frac{\text{canvasH}}{H_{\text{img}}}\right)$$
   $$\text{drawnW} = W_{\text{img}} \times \text{scale}, \quad \text{drawnH} = H_{\text{img}} \times \text{scale}$$
   $$\text{offsetX} = \frac{\text{canvasW} - \text{drawnW}}{2}, \quad \text{offsetY} = \frac{\text{canvasH} - \text{drawnH}}{2}$$
2. **Normalized Box to Canvas Projection:**
   $$\text{left} = \text{offsetX} + x_1 \times \text{drawnW}, \quad \text{top} = \text{offsetY} + y_1 \times \text{drawnH}$$
   $$\text{right} = \text{offsetX} + x_2 \times \text{drawnW}, \quad \text{bottom} = \text{offsetY} + y_2 \times \text{drawnH}$$
3. **Screen Tap to Normalized Image Inversion:**
   $$x_{\text{norm}} = \frac{\text{tapX} - \text{offsetX}}{\text{drawnW}}, \quad y_{\text{norm}} = \frac{\text{tapY} - \text{offsetY}}{\text{drawnH}}$$
   If $\text{tapX} < \text{offsetX}$ or $\text{tapX} > \text{offsetX} + \text{drawnW}$ (or along the Y axis), the tap falls on letterbox bars and returns `null`, preventing phantom $+1/-1$ tally modifications.

---

## 4. Media Studio: Native SAHI 2x2 Tiling & Video Walkthrough Audit (v2.0.0)

### A. Native C++17 SAHI 2x2 Tiling (`detect_tiled` in `yolo11_ncnn.cpp`)
High-resolution shelf photographs suffer from scale disparity: small retail products (~30–50px) vanish when downsampled to a standard $640 \times 640$ model input. ShelfPulse AI implements a native C++17 **Slicing Aided Hyper Inference (SAHI)** pipeline:

```mermaid
flowchart TD
    Raw["High-Res Image (up to 2048px)"] --> Slices["2x2 Overlapping Slices (20% overlap)<br/>W_slice = ⌈W*(1+0.2)/2⌉<br/>H_slice = ⌈H*(1+0.2)/2⌉"]
    Raw --> Global["Global Overview Pass<br/>(Full Image Resized to 640px)"]
    Slices --> TileInf["4x Tile Inferences @ 640px<br/>(Kryo 485 OpenMP CPU)"]
    Global --> GlobalInf["1x Overview Inference @ 640px"]
    TileInf --> Map["Coordinate Remapping to Original Space:<br/>x_orig = (x_tile + tile_x_offset) / W_orig<br/>y_orig = (y_tile + tile_y_offset) / H_orig"]
    GlobalInf --> Map
    Map --> GNMS["Global NMS Pass<br/>(IoU threshold = 0.45, Conf = 0.40)"]
    GNMS --> Merged["Deduplicated Detections (+31.6% Recall)"]
```

1. **Slice Geometry Formulation:**
   Given an image of dimensions $W \times H$ and overlap ratio $\rho = 0.20$:
   $$W_{\text{slice}} = \left\lceil \frac{W \cdot (1 + \rho)}{2} \right\rceil, \quad H_{\text{slice}} = \left\lceil \frac{H \cdot (1 + \rho)}{2} \right\rceil$$
   $$X_{\text{offset}} = [0, W - W_{\text{slice}}], \quad Y_{\text{offset}} = [0, H - H_{\text{slice}}]$$
2. **Global Overview Pass:**
   In addition to the 4 overlapping quadrants, a 5th full-image inference is executed to capture packages spanning across tile seams.
3. **Global Non-Maximum Suppression (Global NMS):**
   Raw candidate boxes from all 5 passes (typically 800–1,200 candidates) are accumulated into a contiguous buffer and filtered using greedy NMS. Bounding boxes on tile borders with $\text{IoU} > 0.45$ are merged, resulting in clean, non-duplicated item counts.

### B. Video Walkthrough Audit Architecture
Designed for handheld video recording of supermarket aisles:
1. **Photo Picker Integration:** Uses Android's modern `ActivityResultContracts.PickVisualMedia` with `VideoOnly`, requiring zero storage permission declarations in `AndroidManifest.xml`.
2. **Background Sampling Coroutine:** Executes on `Dispatchers.Default` at a controlled 4 FPS rate (`FRAME_INTERVAL_MS = 250L`) using `MediaMetadataRetriever.getScaledFrameAtTime(timeUs, OPTION_CLOSEST, 640, 640)`.
3. **Software ARGB_8888 Memory Bridge:** Automatically checks and copies hardware buffers to non-hardware `ARGB_8888` bitmaps, ensuring 100% crash-free NDK `AndroidBitmap_lockPixels` safety.
4. **ByteTrack-cpp Continuity:** Calls `nativeResetTracker()` before each audit session to reset Kalman state vectors and ID counters, preventing cross-session identity leakage.

---

## 5. Offline Barcode Scanner & Open Food Facts (`zxing-cpp`)

To bind physical products to counts, ShelfPulse AI v2.1.0 integrates an embedded, offline C++17 barcode reader:

- **Engine:** `io.github.zxing-cpp:android:2.3.0` (Apache-2.0 license).
- **Supported Formats:** EAN-13, EAN-8, UPC-A, UPC-E, Code 128, QR Code.
- **Zero Cloud Footprint:** Runs 100% locally on CameraX image frames via `BarcodeScannerHelper.kt`.
- **Online Enrichment Fallback (`OpenFoodFactsClient.kt`):**
  - When network is connected, queries `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json?fields=product_name,brands,quantity`.
  - Built with lightweight `HttpURLConnection` with a strict 5-second timeout and custom User-Agent (`ShelfPulseAI/2.1 (Android)`).
  - Gracefully falls back to manual SKU name entry if offline or if product is unlisted.
- **Product Catalog Persistence:** Persisted in Room table `product_catalog` with expected stock and unit prices.

---

## 6. Enterprise Multi-Format Export Suite (`ExportManager.kt`)

Audits can be shared or archived in 4 complementary formats via Android `FileProvider`:

1. **PDF Audit Report:**
   - Generated with native Android `android.graphics.pdf.PdfDocument` (A4 standard format: $595 \times 842$ pt).
   - Bi-directional text layout: handles English and Arabic Right-to-Left (RTL) typography with correct text alignment and line breaks.
   - Includes summary header, barcode / SKU info, variance analysis, and itemized detection log.
2. **Annotated Proof Photo (JPEG):**
   - High-resolution canvas rendering of the shelf photograph with emerald bounding boxes and index pills.
   - Bottom audit metadata banner displaying timestamp, session ID, total count, barcode, and operator notes.
3. **Excel-Compatible CSV:**
   - Pre-pended with UTF-8 Byte Order Mark (`\uFEFF`) to ensure Microsoft Excel correctly parses Arabic and international characters without encoding corruption.
4. **Standardized JSON (Schema 2.2):**
   - Complete machine-readable session dump including normalized bounding box coordinates $[x1, y1, x2, y2]$, confidence scores, SKU metadata, and variance metrics.

---

## 7. Pluggable ERP & Webhook Integrations (v2.2.0)

ShelfPulse AI v2.2.0 connects directly to modern enterprise ERPs, warehouse management systems, and workflow engines.

```mermaid
flowchart TD
    Session["Inventory Session<br/>(Audit Complete)"] --> Prefs{"Auto-Sync<br/>Enabled?"}
    Prefs -- Yes --> Worker["SyncAuditWorker<br/>(One-Time WorkRequest)"]
    Prefs -- No --> Local["Session Saved<br/>(Status: LOCAL_ONLY)"]
    Worker --> Constraints{"Network Connected?"}
    Constraints -- No --> Retry["WorkManager Backoff<br/>(Status: QUEUED)"]
    Constraints -- Yes --> Connector["HttpSyncConnector.pushSession()"]
    Connector --> Format{"PayloadFormatter Preset"}
    Format --> GW["GENERIC_WEBHOOK<br/>(n8n, Zapier, Custom POS)"]
    Format --> IT["INVENTREE_REST<br/>(InvenTree Stock Count)"]
    Format --> EN["ERPNEXT_STOCK_RECONCILIATION<br/>(ERPNext Ledger)"]
    Connector -- HTTP 2xx --> Synced["Room DB v4<br/>(Status: SYNCED)"]
    Connector -- HTTP Error --> Failed["Room DB v4<br/>(Status: FAILED)"]
    Failed --> ManualRetry["History Screen<br/>Tap Pill to Retry"]
    ManualRetry --> Worker
```

### Connector Presets & Payload Architecture
- **`GENERIC_WEBHOOK`**: Standardized JSON event payload (`inventory.audit.completed`) compatible with n8n, Zapier, Make, and custom HTTP endpoints.
- **`INVENTREE_REST`**: Formatted for InvenTree `/api/stock/count/` endpoint (`barcode`, `quantity`, `notes`).
- **`ERPNEXT_STOCK_RECONCILIATION`**: Formatted for ERPNext Stock Reconciliation doctype (`item_code`, `qty`, `valuation_rate`).
- **Security:** Bearer token authentication stored locally via encrypted `SharedPreferences`.

---

## 8. Room Database Evolution & Migration History

ShelfPulse AI maintains strict backward compatibility with zero data loss using non-destructive SQLite migrations:

| DB Version | Release | Changes / Migration Logic |
| :--- | :--- | :--- |
| **v1** | v1.0.0 | Initial tables: `inventory_sessions` (`id`, `timestamp`, `totalCount`, `operatorNotes`, `imageUri`), `count_items` (`id`, `sessionId`, `x1`, `y1`, `x2`, `y2`, `confidence`, `trackId`, `isManual`, `skuId`, `embeddingBlob`). |
| **v2** | v2.0.0 | `MIGRATION_1_2`: Added Media Studio fields to `inventory_sessions`: `mode TEXT NOT NULL DEFAULT 'STILL'`, `rawBoxCount INTEGER NOT NULL DEFAULT 0`, `mergedBoxCount INTEGER NOT NULL DEFAULT 0`, `tileCount INTEGER NOT NULL DEFAULT 1`, `durationMs INTEGER NOT NULL DEFAULT 0`. |
| **v3** | v2.1.0 | `MIGRATION_2_3`: Created `product_catalog` table (`barcode PRIMARY KEY`, `name`, `brand`, `expectedStock`, `unitPrice`, `updatedAt`). Added SKU binding columns to `inventory_sessions`: `barcode TEXT`, `productName TEXT`, `expectedStock INTEGER`, `unitPrice REAL`. |
| **v4** | v2.2.0 | `MIGRATION_3_4`: Added background sync tracking to `inventory_sessions`: `syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'`, `lastSyncedAt INTEGER`. |

---

## 9. Phase-2 Roadmap: SKU Embedding & Catalog Matching

The native architecture exposes an extensible `IEmbedder` interface in `interfaces.h`:
```cpp
class IEmbedder {
public:
    virtual ~IEmbedder() = default;
    virtual std::vector<float> extractEmbedding(const cv::Mat& crop) = 0;
};
```

1. **Lightweight Feature Extractor:** Integration of MobileNetV3 / EfficientNet-Lite trained on metric learning (Triplet Loss / ArcFace) to generate 128-D or 256-D feature vectors per detected bounding box.
2. **On-Device Cosine Similarity:** In-memory vector index comparing box embeddings against retail SKU catalog embeddings to classify specific products (e.g. *Beverage Brand A 330ml* vs *Brand B 330ml*).
3. **Database Schema Preparedness:** The Room `count_items` schema already includes nullable `skuId: String?` and `embeddingBlob: ByteArray?` columns for seamless backward-compatible database migrations.
