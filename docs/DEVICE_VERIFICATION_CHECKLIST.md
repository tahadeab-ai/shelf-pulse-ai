# Hardware Verification Checklist: Samsung SM-N970U (5-Minute Smoke Test)

This protocol must be executed upon reconnecting the physical Samsung Galaxy Note 10 (`SM-N970U`) test bench to validate v2.0.1–v2.3.0 edge changes against real camera hardware and Mali-G76 Vulkan drivers.

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

## 2. In-App Bilingual Switcher (Arabic RTL & English LTR)
- [ ] In the top app bar, tap the **Language (Globe)** icon:
  - [ ] Select **العربية**: confirm instant RTL layout transformation across all screens without changing system language.
  - [ ] Verify Arabic strings in Snap & Verify, Live Scan, Video Audit, History, and Settings.
  - [ ] Select **English**: confirm instant LTR layout transformation back to English.
  - [ ] Restart app: confirm selected language persists across restarts.

---

## 3. Real-Time Viewfinder & Live Scan (Vulkan GPU)
- [ ] Navigate to **Live Scan** tab:
  - [ ] Confirm Vulkan GPU engine activates (hardware telemetry pill displays `Vulkan GPU`, ~15–20 ms inference).
  - [ ] Verify bounding boxes track shelf products continuously without jitter (>25 FPS).
  - [ ] Tap **Torch** toggle button in the top bar: confirm camera LED illuminates smoothly and turns off on second tap.

---

## 4. Snap & Verify, Shelf Depth Multiplier & SAHI 3x3 Ultra
- [ ] Navigate to **Snap & Verify** tab:
  - [ ] Tap **Flash** button to cycle through: `Off` (gray) $\to$ `Auto` (amber) $\to$ `On` (solid).
  - [ ] **Mode Segmented Toggle:** Toggle between `[640px | SAHI 2×2 | SAHI 3×3]`.
    - [ ] On a dense shelf with small distant packages, select `SAHI 3×3`: confirm 10-pass execution (~1.2s on CPU) and high recall (+40%+ items detected).
    - [ ] Verify geometric false-positive filtering suppresses wall posters and long shelf rails.
  - [ ] **Coordinate Alignment:** Verify bounding boxes sit 100% locked onto displayed product boxes with zero vertical/horizontal offset in dark letterbox bars.
  - [ ] **Pinch-to-Zoom:** Pinch screen to zoom in (1.0x to 4.0x) and pan across shelf. Confirm bounding boxes scale and translate identically with the image.
  - [ ] Tap **Reset Zoom** chip: confirms viewport animates back to 1.0x.
  - [ ] **Live Confidence Slider:** Drag slider (`0.25 .. 0.75`, default `0.32f`): confirm count dynamically live-filters without re-running JNI.
  - [ ] **Shelf Depth Multiplier:**
    - [ ] Tap `+` on the Depth stepper in the Top HUD: change from `1×` to `3×`.
    - [ ] Confirm Top HUD displays: `X Facings × 3 Depth = (3X) Total Units`.
  - [ ] **Interactive Tally:** Tap an emerald box to exclude (turns translucent gray, tally decreases by 1). Tap an empty product area to add manual marker (cyan box appears, tally increases by 1).

---

## 5. Barcode SKU Linking & Open Food Facts
- [ ] In Snap & Verify Top HUD, tap **[+ Link SKU]**:
  - [ ] Point camera at a grocery barcode (e.g., Oreo or Coca-Cola can): confirm instant recognition via `zxing-cpp`.
  - [ ] Tap **Lookup Online (Open Food Facts)**: confirm Product Name and Brand auto-populate within 2 seconds.
  - [ ] Enter Expected Stock (e.g. `50`) and Unit Price (e.g. `2.50`): tap **Confirm & Link SKU**.
  - [ ] Verify Top HUD shows linked product name, Variance chip reflecting `Total Units - Expected Stock`, and calculated Total Financial Value.

---

## 6. Internal Photo Storage & Multi-Format Export Suite (Embedded PDF Proof)
- [ ] Tap **Save Session**, enter shelf name, and confirm save:
  - [ ] Verify shelf photo is saved into private internal storage (`session_photos/session_<id>.jpg`).
- [ ] Navigate to **History** tab:
  - [ ] Confirm saved card displays:
    - [ ] Thumbnail of captured shelf photo.
    - [ ] `Front Facings × Depth = Total Units`.
    - [ ] SKU Badge, Variance Chip, and Sync Status Pill (`Local`).
  - [ ] Tap the photo thumbnail: verify full-screen preview opens.
  - [ ] Tap **Export / Share**:
    - [ ] **PDF Audit Report:** Select PDF, open in viewer:
      - [ ] Verify high-res shelf photo is embedded directly in the PDF document.
      - [ ] Verify bilingual A4 layout, metrics summary (`Facings`, `Depth Multiplier`, `Total Count`), and Arabic RTL text rendering.
    - [ ] **Annotated Proof Photo:** Select JPEG, verify crisp exported photo with drawn emerald boxes and watermark banner.
    - [ ] **CSV (Excel):** Open in Microsoft Excel or Google Sheets, verify UTF-8 BOM encoding renders Arabic characters and depth multiplier columns cleanly.
    - [ ] **JSON:** Verify valid JSON schema 2.3 output including `depthMultiplier` and `totalCount`.

---

## 7. Video Walkthrough Audit (HD 640px & ByteTrack Frame-1 Activation)
- [ ] Navigate to **Video Audit** tab:
  - [ ] Select an aisle walkthrough video clip from the gallery.
  - [ ] Toggle resolution between `320px Fast` and `640px HD`.
  - [ ] Start audit: verify live frame detections AND cumulative unique tracked IDs (`Tracked: X`).
  - [ ] Verify ByteTrack activates tracks on frame 1 without returning 0 tracked items.

---

## 8. Offline WorkManager Sync & Webhook Connectors
- [ ] In History tab, tap the **Settings (Gear)** icon in the top header:
  - [ ] Toggle **Enable Cloud / ERP Sync** ON.
  - [ ] Select **Generic Webhook** (or test endpoint, e.g. `https://webhook.site/...`).
  - [ ] Tap **Test Connection**: verify green `Connected successfully (HTTP 200)` confirmation.
  - [ ] Toggle **Auto-sync upon saving session** ON and tap **Save Configuration**.
- [ ] Put device in **Airplane Mode** $\to$ capture and save a new session $\to$ verify History card displays `Queued` pill.
- [ ] Disable **Airplane Mode** (reconnect Wi-Fi/cellular): confirm `SyncAuditWorker` fires in the background and status transitions from `Queued` $\to$ `Synced` automatically.

---

## 9. Screenshot Recapture
- [ ] Recapture fresh screenshots for the `docs/assets/screenshots/` directory:
  - `snap_verify.png`: Dense shelf with aligned boxes, SAHI 3x3 toggle, and Depth stepper.
  - `live_scan.png`: Live Vulkan GPU scan at 30 FPS.
  - `history_v2.png`: History view showing photo thumbnails, depth calculation, and sync status.
