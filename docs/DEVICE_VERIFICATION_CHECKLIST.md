# Hardware Verification Checklist: Samsung SM-N970U (5-Minute Smoke Test)

This protocol must be executed upon reconnecting the physical Samsung Galaxy Note 10 (`SM-N970U`) test bench to validate v2.0.1–v2.2.0 edge changes against real camera hardware and Mali-G76 Vulkan drivers.

---

## 1. Prerequisites & Clean Install
- [ ] Connect device via USB and verify ADB authorization:
  ```bash
  adb devices -l
  # Output must show device authorized (SM-N970U / Exynos 9825)
  ```
- [ ] Download latest CI Release APK from GitHub Actions ([Artifacts](https://github.com/tahadeab-ai/shelf-pulse-ai/actions)) or run:
  ```bash
  adb install -r app-arm64-v8a-release.apk
  ```
- [ ] Launch **ShelfPulse AI** and confirm camera runtime permissions are granted.

---

## 2. Real-Time Viewfinder & Live Scan (Vulkan GPU)
- [ ] Navigate to **Live Scan** tab:
  - [ ] Confirm Vulkan GPU engine activates (hardware telemetry pill displays `Vulkan GPU`, ~15–20 ms inference).
  - [ ] Verify bounding boxes track shelf products continuously without jitter (>25 FPS).
  - [ ] Tap **Torch** toggle button in the top bar: confirm camera LED illuminates smoothly and turns off on second tap.

---

## 3. Snap & Verify Precision Overlay & Pinch-Zoom
- [ ] Navigate to **Snap & Verify** tab:
  - [ ] Tap **Flash** button to cycle through: `Off` (gray) $\to$ `Auto` (amber) $\to$ `On` (solid).
  - [ ] Capture a shelf with 15–30 products:
    - [ ] **Coordinate Alignment:** Verify bounding boxes sit 100% locked onto displayed product boxes with zero vertical/horizontal offset in dark letterbox bars.
    - [ ] **Pinch-to-Zoom:** Pinch screen to zoom in (1.0x to 4.0x) and pan across shelf. Confirm bounding boxes scale and translate identically with the image.
    - [ ] Tap **Reset Zoom** chip: confirms viewport animates back to 1.0x.
    - [ ] **Live Confidence Slider:** Drag slider (`0.25 .. 0.75`): confirm count dynamically live-filters without re-running JNI.
    - [ ] **Labels Toggle:** Toggle `[Labels]` ON/OFF: confirm compact index/score pills appear and disappear.
    - [ ] **Interactive Tally:** Tap an emerald box to exclude (turns translucent gray, tally decreases by 1). Tap an empty product area to add manual marker (cyan box appears, tally increases by 1).

---

## 4. Barcode SKU Linking & Open Food Facts
- [ ] In Snap & Verify Top HUD, tap **[+ Link SKU]**:
  - [ ] Point camera at a grocery barcode (e.g., Oreo or Coca-Cola can): confirm instant recognition via `zxing-cpp`.
  - [ ] Tap **Lookup Online (Open Food Facts)**: confirm Product Name and Brand auto-populate within 2 seconds.
  - [ ] Enter Expected Stock (e.g. `10`) and Unit Price (e.g. `2.50`): tap **Confirm & Link SKU**.
  - [ ] Verify Top HUD shows linked product name, Variance chip (e.g. `Δ +3 Surplus` in green or `Δ -2 Shortage` in red), and calculated Total Financial Value.

---

## 5. Multi-Format Export Suite
- [ ] Tap **Save Session**, enter shelf name, and confirm save.
- [ ] Navigate to **History** tab:
  - [ ] Confirm saved card displays Shelf Location, Count, SKU Badge, Variance Chip, and Sync Status Pill (`Local`).
  - [ ] Tap **Export / Share**:
    - [ ] **PDF Audit Report:** Select PDF, open in viewer, verify A4 layout, metrics summary, Arabic RTL text rendering, and itemized coordinate table.
    - [ ] **Annotated Proof Photo:** Select JPEG, verify crisp exported photo with drawn emerald boxes and watermark banner.
    - [ ] **CSV (Excel):** Open in Microsoft Excel or Google Sheets, verify UTF-8 BOM encoding renders Arabic characters and itemized columns cleanly.
    - [ ] **JSON:** Verify valid JSON schema 2.2 output.

---

## 6. Offline WorkManager Sync & Webhook Connectors
- [ ] In History tab, tap the **Settings (Gear)** icon in the top header:
  - [ ] Toggle **Enable Cloud / ERP Sync** ON.
  - [ ] Select **Generic Webhook** (or test endpoint, e.g. `https://webhook.site/...`).
  - [ ] Tap **Test Connection**: verify green `Connected successfully (HTTP 200)` confirmation.
  - [ ] Toggle **Auto-sync upon saving session** ON and tap **Save Configuration**.
- [ ] Put device in **Airplane Mode** $\to$ capture and save a new session $\to$ verify History card displays `Queued` pill.
- [ ] Disable **Airplane Mode** (reconnect Wi-Fi/cellular): confirm `SyncAuditWorker` fires in the background and status transitions from `Queued` $\to$ `Synced` automatically.

---

## 7. Screenshot Recapture
- [ ] Recapture fresh screenshots for the `docs/assets/screenshots/` directory:
  - `snap_verify.png`: Dense shelf with aligned boxes and SKU chip.
  - `live_scan.png`: Live Vulkan GPU scan at 30 FPS.
  - `history_v2.png`: History view showing variance badges, sync status, and export sheet.
