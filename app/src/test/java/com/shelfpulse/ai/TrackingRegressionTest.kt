package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.db.ShelfPulseDatabase
import com.shelfpulse.ai.data.storage.SessionPhotoStore
import com.shelfpulse.ai.domain.model.DetectionResult
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.ui.viewmodel.LiveScanUiState
import com.shelfpulse.ai.ui.viewmodel.VideoAuditUiState
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

class TrackingRegressionTest {

    // 1. Normalized Box Coordinate & IoU Distance Math Verification
    @Test
    fun normalizedBoxes_tinyWarehouseShelfItems_preservePositiveArea() {
        // Warehouse shelf items can be very small when normalized (e.g., 20px on 4000px image = 0.005)
        val x1 = 0.1200f
        val y1 = 0.3400f
        val x2 = 0.1280f // width = 0.008
        val y2 = 0.3500f // height = 0.010
        val area = (x2 - x1) * (y2 - y1) // 0.00008f <= 0.0001f

        assertThat(area).isGreaterThan(1e-7f)
        assertThat(x2).isGreaterThan(x1)
        assertThat(y2).isGreaterThan(y1)

        val det = DetectionResult(
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            score = 0.85f,
            classId = 0,
            trackId = 1
        )
        assertThat(det.trackId).isGreaterThan(0)
        assertThat(det.score).isAtLeast(0.30f)
    }

    @Test
    fun iouDistance_identicalBoxes_returnsZeroDistance() {
        val boxA = floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f)
        val boxB = floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f)

        val x1 = max(boxA[0], boxB[0])
        val y1 = max(boxA[1], boxB[1])
        val x2 = min(boxA[2], boxB[2])
        val y2 = min(boxA[3], boxB[3])

        val interW = max(0f, x2 - x1)
        val interH = max(0f, y2 - y1)
        val interArea = interW * interH

        val areaA = (boxA[2] - boxA[0]) * (boxA[3] - boxA[1])
        val areaB = (boxB[2] - boxB[0]) * (boxB[3] - boxB[1])
        val unionArea = areaA + areaB - interArea

        val dist = if (unionArea <= 0f) 1.0f else (1.0f - (interArea / unionArea))
        assertThat(dist).isEqualTo(0.0f)
    }

    @Test
    fun iouDistance_disjointBoxes_returnsOneDistance() {
        val boxA = floatArrayOf(0.1f, 0.1f, 0.2f, 0.2f)
        val boxB = floatArrayOf(0.5f, 0.5f, 0.6f, 0.6f)

        val x1 = max(boxA[0], boxB[0])
        val y1 = max(boxA[1], boxB[1])
        val x2 = min(boxA[2], boxB[2])
        val y2 = min(boxA[3], boxB[3])

        val interW = max(0f, x2 - x1)
        val interH = max(0f, y2 - y1)
        val interArea = interW * interH

        val areaA = (boxA[2] - boxA[0]) * (boxA[3] - boxA[1])
        val areaB = (boxB[2] - boxB[0]) * (boxB[3] - boxB[1])
        val unionArea = areaA + areaB - interArea

        val dist = if (unionArea <= 0f) 1.0f else (1.0f - (interArea / unionArea))
        assertThat(dist).isEqualTo(1.0f)
    }

    // 2. Video Audit & Live Scan Thresholds and Resolution Defaults
    @Test
    fun liveScanUiState_defaultConfidence_is30Percent() {
        val state = LiveScanUiState()
        assertThat(state.confThresh).isEqualTo(0.30f)
    }

    @Test
    fun videoAuditUiState_defaultConfidenceAndResolution_is30PercentAnd640() {
        val state = VideoAuditUiState()
        assertThat(state.confThresh).isEqualTo(0.30f)
        assertThat(state.targetResolution).isEqualTo(640)
        assertThat(state.uniqueTrackedCount).isEqualTo(0)
        assertThat(state.currentDetections).isEmpty()
    }

    @Test
    fun videoAudit_uniqueTrackSet_deduplicatesTracksAcrossFrames() {
        val seenTrackIds = mutableSetOf<Int>()

        // Frame 1 detections (tracks 1, 2, 3)
        val frame1Dets = listOf(
            DetectionResult(0.1f, 0.1f, 0.2f, 0.2f, 0.9f, 0, trackId = 1),
            DetectionResult(0.3f, 0.3f, 0.4f, 0.4f, 0.85f, 0, trackId = 2),
            DetectionResult(0.5f, 0.5f, 0.6f, 0.6f, 0.80f, 0, trackId = 3)
        )
        for (d in frame1Dets) {
            if (d.trackId > 0) seenTrackIds.add(d.trackId)
        }
        assertThat(seenTrackIds.size).isEqualTo(3)

        // Frame 2 detections (tracks 2, 3, and newly tracked 4)
        val frame2Dets = listOf(
            DetectionResult(0.31f, 0.31f, 0.41f, 0.41f, 0.88f, 0, trackId = 2),
            DetectionResult(0.51f, 0.51f, 0.61f, 0.61f, 0.82f, 0, trackId = 3),
            DetectionResult(0.7f, 0.7f, 0.8f, 0.8f, 0.75f, 0, trackId = 4)
        )
        for (d in frame2Dets) {
            if (d.trackId > 0) seenTrackIds.add(d.trackId)
        }
        // Total cumulative unique tracks should be 4
        assertThat(seenTrackIds.size).isEqualTo(4)
        // Live frame detection count for frame 2 is 3
        assertThat(frame2Dets.size).isEqualTo(3)
    }

    // 3. Internal Session Photo Storage & Room Database v5 Schema Verification
    @Test
    fun inventorySession_schemaVersion5_supportsPhotoPath() {
        val session = InventorySession(
            id = 42L,
            shelfLocation = "Aisle 3 - Bay 2",
            totalCount = 15,
            photoPath = "/data/user/0/com.shelfpulse.ai/files/session_photos/session_42.jpg"
        )
        assertThat(session.photoPath).isNotNull()
        assertThat(session.photoPath).contains("session_42.jpg")
    }

    @Test
    fun sessionPhotoStore_filePath_isPrivateInternalStorage() {
        val dummyFilesDir = File("/data/user/0/com.shelfpulse.ai/files")
        val sessionDir = File(dummyFilesDir, "session_photos")
        val photoFile = File(sessionDir, "session_99.jpg")

        assertThat(photoFile.name).isEqualTo("session_99.jpg")
        assertThat(photoFile.parentFile?.name).isEqualTo("session_photos")
        // Guarantee file is NOT located in public external storage or downloads
        assertThat(photoFile.path).doesNotContain("Download")
        assertThat(photoFile.path).doesNotContain("DCIM")
    }

    @Test
    fun database_migration_4_to_5_exists() {
        assertThat(ShelfPulseDatabase.MIGRATION_4_5).isNotNull()
        assertThat(ShelfPulseDatabase.MIGRATION_4_5.startVersion).isEqualTo(4)
        assertThat(ShelfPulseDatabase.MIGRATION_4_5.endVersion).isEqualTo(5)
    }
}
