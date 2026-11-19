package com.shelfpulse.ai.ui.viewmodel

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
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

data class LiveScanUiState(
    val activeDetections: List<DetectionResult> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val cumulativeTrackCount: Int = 0,
    val telemetry: Telemetry = Telemetry(),
    val isPaused: Boolean = false,
    val isTorchOn: Boolean = false,
    val confThresh: Float = 0.40f,
    val nmsThresh: Float = 0.45f
)

class LiveScanViewModel(
    private val nativeEngine: ShelfPulseNativeEngine,
    private val repository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveScanUiState())
    val uiState: StateFlow<LiveScanUiState> = _uiState.asStateFlow()

    private val isProcessing = AtomicBoolean(false)
    private val seenTrackIds = HashSet<Int>()

    fun processFrame(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        rotationDegrees: Int
    ) {
        if (_uiState.value.isPaused) return
        if (!isProcessing.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val orientedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
                val orientedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

                val (detections, telem) = nativeEngine.detectYuv(
                    yBuffer = yBuffer,
                    uBuffer = uBuffer,
                    vBuffer = vBuffer,
                    width = width,
                    height = height,
                    yRowStride = yRowStride,
                    uvRowStride = uvRowStride,
                    uvPixelStride = uvPixelStride,
                    rotationDegrees = rotationDegrees,
                    confThresh = _uiState.value.confThresh,
                    nmsThresh = _uiState.value.nmsThresh,
                    enableTracking = true
                )

                // Track unique cumulative track IDs
                synchronized(seenTrackIds) {
                    for (det in detections) {
                        if (det.trackId > 0) {
                            seenTrackIds.add(det.trackId)
                        }
                    }
                }

                val currentCumulative = synchronized(seenTrackIds) { seenTrackIds.size }

                _uiState.update {
                    it.copy(
                        activeDetections = detections,
                        frameWidth = orientedWidth,
                        frameHeight = orientedHeight,
                        cumulativeTrackCount = currentCumulative,
                        telemetry = telem
                    )
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun resetCounter() {
        synchronized(seenTrackIds) {
            seenTrackIds.clear()
        }
        nativeEngine.resetTracker()
        _uiState.update {
            it.copy(
                activeDetections = emptyList(),
                cumulativeTrackCount = 0
            )
        }
    }

    fun setConfidence(threshold: Float) {
        _uiState.update { it.copy(confThresh = threshold) }
    }

    fun setNms(threshold: Float) {
        _uiState.update { it.copy(nmsThresh = threshold) }
    }

    fun saveSession(shelfLocation: String, notes: String, onComplete: () -> Unit = {}) {
        val state = _uiState.value
        val snapshotDetections = state.activeDetections

        viewModelScope.launch(Dispatchers.IO) {
            val session = InventorySession(
                shelfLocation = shelfLocation.ifBlank { "Live Shelf Scan" },
                totalCount = state.cumulativeTrackCount.coerceAtLeast(snapshotDetections.size),
                operatorNotes = notes,
                mode = "LIVE_SCAN"
            )

            val records = snapshotDetections.map { det ->
                ShelfScanRecord(
                    sessionId = 0,
                    x1 = det.x1,
                    y1 = det.y1,
                    x2 = det.x2,
                    y2 = det.y2,
                    confidence = det.score,
                    classId = det.classId,
                    trackId = det.trackId,
                    isManual = false
                )
            }

            repository.saveSession(session, records)
            onComplete()
        }
    }
}
