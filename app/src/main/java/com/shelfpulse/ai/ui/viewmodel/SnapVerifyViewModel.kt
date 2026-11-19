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

data class SnapVerifyUiState(
    val capturedBitmap: Bitmap? = null,
    val detections: List<DetectionResult> = emptyList(),
    val telemetry: Telemetry = Telemetry(),
    val isProcessing: Boolean = false,
    val confThresh: Float = 0.40f,
    val nmsThresh: Float = 0.45f,
    val isSaved: Boolean = false,
    val lastSavedSessionId: Long? = null
) {
    val effectiveCount: Int
        get() = detections.count { !it.isExcluded }
}

class SnapVerifyViewModel(
    private val nativeEngine: ShelfPulseNativeEngine,
    private val repository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnapVerifyUiState())
    val uiState: StateFlow<SnapVerifyUiState> = _uiState.asStateFlow()

    fun processCapturedBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(isProcessing = true, capturedBitmap = bitmap) }

        viewModelScope.launch(Dispatchers.Default) {
            val (dets, telem) = nativeEngine.detectBitmap(
                bitmap = bitmap,
                confThresh = _uiState.value.confThresh,
                nmsThresh = _uiState.value.nmsThresh,
                enableTracking = false
            )

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
            if (index in current.detections.indices) {
                val updated = current.detections.toMutableList()
                val target = updated[index]
                updated[index] = target.copy(isExcluded = !target.isExcluded)
                current.copy(detections = updated)
            } else {
                current
            }
        }
    }

    fun addManualMarker(imgX: Float, imgY: Float) {
        _uiState.update { current ->
            val markerSize = 40f
            val manualDetection = DetectionResult(
                x1 = (imgX - markerSize).coerceAtLeast(0f),
                y1 = (imgY - markerSize).coerceAtLeast(0f),
                x2 = imgX + markerSize,
                y2 = imgY + markerSize,
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
        val validDetections = state.detections.filter { !it.isExcluded }

        viewModelScope.launch(Dispatchers.IO) {
            val session = InventorySession(
                shelfLocation = shelfLocation.ifBlank { "Unspecified Shelf" },
                totalCount = validDetections.size,
                operatorNotes = notes,
                mode = "SNAP_AND_VERIFY"
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
                confThresh = it.confThresh,
                nmsThresh = it.nmsThresh
            )
        }
    }
}
