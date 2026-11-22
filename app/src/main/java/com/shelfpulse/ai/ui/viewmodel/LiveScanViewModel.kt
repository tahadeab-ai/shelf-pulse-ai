package com.shelfpulse.ai.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfpulse.ai.data.db.ProductCatalogEntity
import com.shelfpulse.ai.data.repository.InventoryRepository
import com.shelfpulse.ai.data.storage.SessionPhotoStore
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
    val confThresh: Float = 0.30f,
    val nmsThresh: Float = 0.45f,
    val isVulkanBackend: Boolean = false,
    val targetResolution: Int = 320,
    val depthMultiplier: Int = 1,
    val linkedBarcode: String? = null,
    val linkedProductName: String? = null,
    val linkedBrand: String? = null,
    val linkedExpectedStock: Int? = null,
    val linkedUnitPrice: Double? = null,
    val isSaved: Boolean = false,
    val lastSavedSessionId: Long? = null,
    val lastCapturedBitmap: Bitmap? = null
) {
    val frontFacings: Int
        get() = cumulativeTrackCount.coerceAtLeast(activeDetections.size)

    val totalCountWithDepth: Int
        get() = frontFacings * depthMultiplier.coerceIn(1, 20)

    val variance: Int?
        get() = linkedExpectedStock?.let { totalCountWithDepth - it }

    val totalFinancialValue: Double?
        get() = linkedUnitPrice?.let { totalCountWithDepth * it }
}

class LiveScanViewModel(
    private val nativeEngine: ShelfPulseNativeEngine,
    private val repository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveScanUiState())
    val uiState: StateFlow<LiveScanUiState> = _uiState.asStateFlow()

    private val isProcessing = AtomicBoolean(false)
    private val seenTrackIds = HashSet<Int>()

    private var lastFrameTimeNs: Long = 0L
    private var smoothedFps: Float = 0f

    fun processRgbaFrame(
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        rotationDegrees: Int
    ) {
        if (_uiState.value.isPaused) return
        if (!isProcessing.compareAndSet(false, true)) return

        try {
            val nowNs = System.nanoTime()
            val wallFps = if (lastFrameTimeNs > 0L) {
                val dtSec = (nowNs - lastFrameTimeNs) / 1_000_000_000.0f
                if (dtSec > 0f) (1.0f / dtSec).coerceAtMost(120f) else 0f
            } else 0f
            lastFrameTimeNs = nowNs

            // Exponential Moving Average (alpha = 0.2)
            smoothedFps = if (smoothedFps == 0f) {
                wallFps
            } else {
                0.2f * wallFps + 0.8f * smoothedFps
            }

            val orientedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
            val orientedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height

            val (detections, telem) = nativeEngine.detectRgba(
                rgbaBuffer = rgbaBuffer,
                width = width,
                height = height,
                rowStride = rowStride,
                rotationDegrees = rotationDegrees,
                confThresh = _uiState.value.confThresh,
                nmsThresh = _uiState.value.nmsThresh,
                enableTracking = true,
                targetSize = _uiState.value.targetResolution
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
            val updatedTelemetry = telem.copy(
                fps = if (smoothedFps > 0f) smoothedFps else telem.fps,
                targetResolution = _uiState.value.targetResolution
            )

            _uiState.update {
                it.copy(
                    activeDetections = detections,
                    frameWidth = orientedWidth,
                    frameHeight = orientedHeight,
                    cumulativeTrackCount = currentCumulative,
                    telemetry = updatedTelemetry,
                    isVulkanBackend = updatedTelemetry.isVulkanActive
                )
            }
        } finally {
            isProcessing.set(false)
        }
    }

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
                val nowNs = System.nanoTime()
                val wallFps = if (lastFrameTimeNs > 0L) {
                    val dtSec = (nowNs - lastFrameTimeNs) / 1_000_000_000.0f
                    if (dtSec > 0f) (1.0f / dtSec).coerceAtMost(120f) else 0f
                } else 0f
                lastFrameTimeNs = nowNs

                // Exponential Moving Average (alpha = 0.2)
                smoothedFps = if (smoothedFps == 0f) {
                    wallFps
                } else {
                    0.2f * wallFps + 0.8f * smoothedFps
                }

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
                val updatedTelemetry = telem.copy(
                    fps = if (smoothedFps > 0f) smoothedFps else telem.fps,
                    targetResolution = _uiState.value.targetResolution
                )

                _uiState.update {
                    it.copy(
                        activeDetections = detections,
                        frameWidth = orientedWidth,
                        frameHeight = orientedHeight,
                        cumulativeTrackCount = currentCumulative,
                        telemetry = updatedTelemetry,
                        isVulkanBackend = updatedTelemetry.isVulkanActive
                    )
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun toggleBackend() {
        val nextGpu = !_uiState.value.isVulkanBackend
        val success = nativeEngine.setBackend(nextGpu)
        if (success) {
            _uiState.update {
                it.copy(
                    isVulkanBackend = nextGpu,
                    telemetry = it.telemetry.copy(isVulkanActive = nextGpu)
                )
            }
        }
    }

    fun toggleResolution() {
        val nextRes = if (_uiState.value.targetResolution == 320) 640 else 320
        _uiState.update { it.copy(targetResolution = nextRes) }
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
                cumulativeTrackCount = 0,
                isSaved = false,
                lastSavedSessionId = null
            )
        }
    }

    fun setDepthMultiplier(depth: Int) {
        _uiState.update { it.copy(depthMultiplier = depth.coerceIn(1, 20)) }
    }

    fun linkSku(
        barcode: String,
        productName: String,
        brand: String?,
        expectedStock: Int?,
        unitPrice: Double?
    ) {
        _uiState.update {
            it.copy(
                linkedBarcode = barcode,
                linkedProductName = productName,
                linkedBrand = brand,
                linkedExpectedStock = expectedStock,
                linkedUnitPrice = unitPrice
            )
        }
    }

    fun clearLinkedProduct() {
        _uiState.update {
            it.copy(
                linkedBarcode = null,
                linkedProductName = null,
                linkedBrand = null,
                linkedExpectedStock = null,
                linkedUnitPrice = null
            )
        }
    }

    fun updateCapturedBitmap(bitmap: Bitmap?) {
        _uiState.update { it.copy(lastCapturedBitmap = bitmap) }
    }

    fun setConfidence(threshold: Float) {
        _uiState.update { it.copy(confThresh = threshold) }
    }

    fun setNms(threshold: Float) {
        _uiState.update { it.copy(nmsThresh = threshold) }
    }

    fun saveSession(
        shelfLocation: String,
        notes: String,
        initialSyncStatus: String = "LOCAL_ONLY",
        context: Context? = null,
        onComplete: (Long) -> Unit = {}
    ) {
        val state = _uiState.value
        val snapshotDetections = state.activeDetections
        val capturedBmp = state.lastCapturedBitmap

        viewModelScope.launch(Dispatchers.IO) {
            if (state.linkedBarcode != null && state.linkedProductName != null) {
                repository.insertProduct(
                    ProductCatalogEntity(
                        barcode = state.linkedBarcode,
                        name = state.linkedProductName,
                        brand = state.linkedBrand ?: "",
                        expectedStock = state.linkedExpectedStock ?: 0,
                        unitPrice = state.linkedUnitPrice ?: 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            val frontCount = state.frontFacings
            val depth = state.depthMultiplier.coerceIn(1, 20)
            val totalCount = state.totalCountWithDepth

            val session = InventorySession(
                shelfLocation = shelfLocation.ifBlank { "Live Shelf Scan" },
                totalCount = totalCount,
                operatorNotes = notes,
                mode = "LIVE_SCAN",
                rawBoxCount = state.telemetry.rawBoxCount,
                mergedBoxCount = state.telemetry.mergedBoxCount,
                tileCount = state.telemetry.tileCount,
                durationMs = state.telemetry.totalLatencyMs.toLong(),
                barcode = state.linkedBarcode,
                productName = state.linkedProductName,
                expectedStock = state.linkedExpectedStock,
                unitPrice = state.linkedUnitPrice,
                syncStatus = initialSyncStatus,
                depthMultiplier = depth
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

            val sessionId = repository.saveSession(session, records)

            if (context != null && capturedBmp != null) {
                val savedPath = SessionPhotoStore.savePhoto(context, sessionId, capturedBmp)
                if (savedPath != null) {
                    repository.updatePhotoPath(sessionId, savedPath)
                }
            }

            _uiState.update { it.copy(isSaved = true, lastSavedSessionId = sessionId) }
            onComplete(sessionId)
        }
    }
}
