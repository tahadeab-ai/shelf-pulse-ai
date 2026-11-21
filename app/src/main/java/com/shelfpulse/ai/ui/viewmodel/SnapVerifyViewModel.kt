package com.shelfpulse.ai.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfpulse.ai.data.repository.InventoryRepository
import com.shelfpulse.ai.domain.model.DetectionResult
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import com.shelfpulse.ai.domain.model.Telemetry
import com.shelfpulse.ai.nativebridge.ShelfPulseNativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanMode {
    SINGLE_640,
    SAHI_2X2
}

data class SnapVerifyUiState(
    val capturedBitmap: Bitmap? = null,
    val detections: List<DetectionResult> = emptyList(),
    val telemetry: Telemetry = Telemetry(),
    val isProcessing: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE_640,
    val confThresh: Float = 0.35f,
    val nmsThresh: Float = 0.45f,
    val isSaved: Boolean = false,
    val lastSavedSessionId: Long? = null,
    val liveConfFilter: Float = 0.35f,
    val showLabels: Boolean = false,
    val selectedBoxIndex: Int? = null,
    val flashMode: Int = 2, // ImageCapture.FLASH_MODE_OFF
    val isTorchOn: Boolean = false
) {
    val effectiveCount: Int
        get() = detections.count { !it.isExcluded }

    val visibleDetections: List<DetectionResult>
        get() = detections.filter { it.isManualMarker || it.score >= liveConfFilter }

    val visibleCount: Int
        get() = visibleDetections.count { !it.isExcluded }
}

class SnapVerifyViewModel(
    private val nativeEngine: ShelfPulseNativeEngine,
    private val repository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnapVerifyUiState())
    val uiState: StateFlow<SnapVerifyUiState> = _uiState.asStateFlow()

    fun setScanMode(mode: ScanMode) {
        if (_uiState.value.scanMode == mode) return
        _uiState.update { it.copy(scanMode = mode) }
        val currentBitmap = _uiState.value.capturedBitmap
        if (currentBitmap != null) {
            runInference(currentBitmap, mode)
        }
    }

    fun setLiveConfFilter(thresh: Float) {
        _uiState.update { it.copy(liveConfFilter = thresh.coerceIn(0.25f, 0.75f)) }
    }

    fun toggleLabels() {
        _uiState.update { it.copy(showLabels = !it.showLabels) }
    }

    fun selectBox(index: Int?) {
        _uiState.update { it.copy(selectedBoxIndex = index) }
    }

    fun cycleFlashMode() {
        _uiState.update { current ->
            // OFF (2) -> AUTO (0) -> ON (1) -> OFF (2)
            val nextMode = when (current.flashMode) {
                2 -> 0 // OFF -> AUTO
                0 -> 1 // AUTO -> ON
                else -> 2 // ON -> OFF
            }
            current.copy(flashMode = nextMode)
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun processCapturedBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(capturedBitmap = bitmap) }
        runInference(bitmap, _uiState.value.scanMode)
    }

    fun processImportedBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(capturedBitmap = bitmap) }
        runInference(bitmap, _uiState.value.scanMode)
    }

    private fun runInference(bitmap: Bitmap, mode: ScanMode) {
        _uiState.update { it.copy(isProcessing = true) }

        viewModelScope.launch(Dispatchers.Default) {
            val (dets, telem) = when (mode) {
                ScanMode.SINGLE_640 -> {
                    nativeEngine.detectBitmap(
                        bitmap = bitmap,
                        confThresh = 0.25f, // Run with lower threshold so liveConfFilter can slide smoothly up to 0.75
                        nmsThresh = _uiState.value.nmsThresh,
                        enableTracking = false
                    )
                }
                ScanMode.SAHI_2X2 -> {
                    nativeEngine.detectBitmapTiled(
                        bitmap = bitmap,
                        confThresh = 0.25f,
                        nmsThresh = _uiState.value.nmsThresh,
                        overlapRatio = 0.20f
                    )
                }
            }

            _uiState.update {
                it.copy(
                    detections = dets,
                    telemetry = telem,
                    isProcessing = false
                )
            }
        }
    }

    fun toggleBoxExclusion(index: Int) {
        _uiState.update { current ->
            val targetList = current.visibleDetections
            if (index in targetList.indices) {
                val target = targetList[index]
                val updated = current.detections.map { det ->
                    if (det.id == target.id) det.copy(isExcluded = !det.isExcluded) else det
                }
                current.copy(detections = updated, selectedBoxIndex = index)
            } else {
                current
            }
        }
    }

    fun addManualMarker(imgX: Float, imgY: Float) {
        _uiState.update { current ->
            val markerHalfSize = 0.025f
            val manualDetection = DetectionResult(
                x1 = (imgX - markerHalfSize).coerceIn(0f, 1f),
                y1 = (imgY - markerHalfSize).coerceIn(0f, 1f),
                x2 = (imgX + markerHalfSize).coerceIn(0f, 1f),
                y2 = (imgY + markerHalfSize).coerceIn(0f, 1f),
                score = 1.0f,
                classId = 0,
                trackId = -1,
                isManualMarker = true,
                isExcluded = false
            )
            current.copy(detections = current.detections + manualDetection)
        }
    }

    fun saveSession(shelfLocation: String, notes: String, onComplete: () -> Unit = {}) {
        val state = _uiState.value
        val validDetections = state.visibleDetections.filter { !it.isExcluded }

        viewModelScope.launch(Dispatchers.IO) {
            val session = InventorySession(
                shelfLocation = shelfLocation.ifBlank { "Unspecified Shelf" },
                totalCount = validDetections.size,
                operatorNotes = notes,
                mode = if (state.scanMode == ScanMode.SAHI_2X2) "SNAP_VERIFY_SAHI" else "SNAP_VERIFY",
                rawBoxCount = state.telemetry.rawBoxCount,
                mergedBoxCount = state.telemetry.mergedBoxCount,
                tileCount = state.telemetry.tileCount,
                durationMs = state.telemetry.totalLatencyMs.toLong()
            )

            val records = validDetections.map { det ->
                ShelfScanRecord(
                    sessionId = 0,
                    x1 = det.x1,
                    y1 = det.y1,
                    x2 = det.x2,
                    y2 = det.y2,
                    confidence = det.score,
                    classId = det.classId,
                    trackId = det.trackId,
                    isManual = det.isManualMarker
                )
            }

            val sessionId = repository.saveSession(session, records)
            _uiState.update { it.copy(isSaved = true, lastSavedSessionId = sessionId) }
            onComplete()
        }
    }

    fun reset() {
        _uiState.update {
            SnapVerifyUiState(
                scanMode = it.scanMode,
                confThresh = it.confThresh,
                nmsThresh = it.nmsThresh,
                liveConfFilter = it.liveConfFilter,
                showLabels = it.showLabels,
                flashMode = it.flashMode,
                isTorchOn = it.isTorchOn
            )
        }
    }
}
