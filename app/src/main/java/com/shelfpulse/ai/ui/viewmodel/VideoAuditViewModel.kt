package com.shelfpulse.ai.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VideoAuditUiState(
    val videoUri: Uri? = null,
    val videoDurationMs: Long = 0L,
    val totalFrames: Int = 0,
    val currentFrame: Int = 0,
    val isAuditing: Boolean = false,
    val isComplete: Boolean = false,
    val currentPreviewBitmap: Bitmap? = null,
    val currentDetections: List<DetectionResult> = emptyList(),
    val uniqueTrackedCount: Int = 0,
    val telemetry: Telemetry = Telemetry(),
    val isSaved: Boolean = false,
    val lastSavedSessionId: Long? = null,
    val confThresh: Float = 0.30f,
    val nmsThresh: Float = 0.45f,
    val targetResolution: Int = 640,
    val depthMultiplier: Int = 1,
    val linkedBarcode: String? = null,
    val linkedProductName: String? = null,
    val linkedBrand: String? = null,
    val linkedExpectedStock: Int? = null,
    val linkedUnitPrice: Double? = null
) {
    val frontFacings: Int
        get() = uniqueTrackedCount

    val totalCountWithDepth: Int
        get() = frontFacings * depthMultiplier.coerceIn(1, 20)

    val variance: Int?
        get() = linkedExpectedStock?.let { totalCountWithDepth - it }

    val totalFinancialValue: Double?
        get() = linkedUnitPrice?.let { totalCountWithDepth * it }
}

class VideoAuditViewModel(
    private val nativeEngine: ShelfPulseNativeEngine,
    private val repository: InventoryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "VideoAuditViewModel"
        private const val FRAME_INTERVAL_MS = 250L // 4 FPS sampling for optimal tracking continuity
    }

    private val _uiState = MutableStateFlow(VideoAuditUiState())
    val uiState: StateFlow<VideoAuditUiState> = _uiState.asStateFlow()

    private var auditJob: Job? = null
    private val uniqueTrackIds = mutableSetOf<Int>()
    private val accumulatedRecords = mutableListOf<ShelfScanRecord>()

    fun setVideoUri(context: Context, uri: Uri) {
        cancelAudit()
        viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val frameCount = (durationMs / FRAME_INTERVAL_MS).toInt().coerceAtLeast(1)

                _uiState.update {
                    VideoAuditUiState(
                        videoUri = uri,
                        videoDurationMs = durationMs,
                        totalFrames = frameCount,
                        confThresh = it.confThresh,
                        nmsThresh = it.nmsThresh
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read video metadata from uri: $uri", e)
            } finally {
                retriever.release()
            }
        }
    }

    fun startAudit(context: Context) {
        val uri = _uiState.value.videoUri ?: return
        if (_uiState.value.isAuditing) return

        cancelAudit()
        uniqueTrackIds.clear()
        accumulatedRecords.clear()

        // Guardrail 2: Call resetTracker() JNI method before starting Video Audit
        nativeEngine.resetTracker()

        auditJob = viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isAuditing = true, isComplete = false, currentFrame = 0, uniqueTrackedCount = 0) }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = _uiState.value.videoDurationMs
                val totalFrames = _uiState.value.totalFrames
                val conf = _uiState.value.confThresh
                val nms = _uiState.value.nmsThresh
                val targetRes = _uiState.value.targetResolution

                var frameIdx = 0

                while (isActive && frameIdx < totalFrames) {
                    val timeUs = frameIdx * FRAME_INTERVAL_MS * 1000L

                    val rawFrame = try {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                            targetRes,
                            targetRes
                        ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } catch (e: Throwable) {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }

                    if (rawFrame != null) {
                        // Ensure software ARGB_8888 bitmap for NDK native processing
                        val swBmp = if (rawFrame.config == Bitmap.Config.HARDWARE || rawFrame.config != Bitmap.Config.ARGB_8888) {
                            val converted = rawFrame.copy(Bitmap.Config.ARGB_8888, false)
                            if (converted != null) {
                                rawFrame.recycle()
                                converted
                            } else {
                                val sw = Bitmap.createBitmap(rawFrame.width, rawFrame.height, Bitmap.Config.ARGB_8888)
                                val c = android.graphics.Canvas(sw)
                                c.drawBitmap(rawFrame, 0f, 0f, null)
                                rawFrame.recycle()
                                sw
                            }
                        } else {
                            rawFrame
                        }

                        val finalFrame = if (swBmp.width > targetRes || swBmp.height > targetRes) {
                            val maxSide = maxOf(swBmp.width, swBmp.height)
                            val scale = targetRes.toFloat() / maxSide.toFloat()
                            val scaled = Bitmap.createScaledBitmap(
                                swBmp,
                                (swBmp.width * scale).toInt().coerceAtLeast(1),
                                (swBmp.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                            if (scaled != swBmp) swBmp.recycle()
                            scaled
                        } else {
                            swBmp
                        }

                        // Pass to YOLO11 + ByteTrack-cpp pipeline
                        val (dets, telem) = nativeEngine.detectBitmap(
                            bitmap = finalFrame,
                            confThresh = conf,
                            nmsThresh = nms,
                            enableTracking = true,
                            targetSize = targetRes
                        )

                        // Update unique active tracks
                        for (d in dets) {
                            if (d.trackId > 0) {
                                if (uniqueTrackIds.add(d.trackId)) {
                                    accumulatedRecords.add(
                                        ShelfScanRecord(
                                            sessionId = 0,
                                            x1 = d.x1,
                                            y1 = d.y1,
                                            x2 = d.x2,
                                            y2 = d.y2,
                                            confidence = d.score,
                                            classId = d.classId,
                                            trackId = d.trackId
                                        )
                                    )
                                }
                            }
                        }

                        _uiState.update {
                            it.copy(
                                currentFrame = frameIdx + 1,
                                currentPreviewBitmap = finalFrame,
                                currentDetections = dets,
                                uniqueTrackedCount = uniqueTrackIds.size,
                                telemetry = telem
                            )
                        }
                    }

                    frameIdx++
                }

                _uiState.update {
                    it.copy(
                        isAuditing = false,
                        isComplete = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video audit processing exception", e)
                _uiState.update { it.copy(isAuditing = false) }
            } finally {
                retriever.release()
            }
        }
    }

    fun cancelAudit() {
        auditJob?.cancel()
        auditJob = null
        _uiState.update { it.copy(isAuditing = false) }
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

    fun saveSession(
        shelfLocation: String,
        notes: String,
        initialSyncStatus: String = "LOCAL_ONLY",
        context: Context? = null,
        onComplete: (Long) -> Unit = {}
    ) {
        val state = _uiState.value
        val uniqueCount = state.uniqueTrackedCount
        val bestFrame = state.currentPreviewBitmap

        viewModelScope.launch(Dispatchers.IO) {
            if (state.linkedBarcode != null && state.linkedProductName != null) {
                repository.upsertProduct(
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

            val depth = state.depthMultiplier.coerceIn(1, 20)
            val totalCount = state.totalCountWithDepth

            val session = InventorySession(
                shelfLocation = shelfLocation.ifBlank { "Aisle Video Audit" },
                totalCount = totalCount,
                operatorNotes = notes,
                mode = "VIDEO_AUDIT",
                rawBoxCount = accumulatedRecords.size,
                mergedBoxCount = uniqueCount,
                tileCount = 1,
                durationMs = state.videoDurationMs,
                barcode = state.linkedBarcode,
                productName = state.linkedProductName,
                expectedStock = state.linkedExpectedStock,
                unitPrice = state.linkedUnitPrice,
                syncStatus = initialSyncStatus,
                depthMultiplier = depth
            )

            val sessionId = repository.saveSession(session, accumulatedRecords.toList())

            if (context != null && bestFrame != null) {
                val savedPath = SessionPhotoStore.savePhoto(context, sessionId, bestFrame)
                if (savedPath != null) {
                    repository.updatePhotoPath(sessionId, savedPath)
                }
            }

            _uiState.update { it.copy(isSaved = true, lastSavedSessionId = sessionId) }
            onComplete(sessionId)
        }
    }

    fun toggleResolution() {
        val nextRes = if (_uiState.value.targetResolution == 640) 320 else 640
        _uiState.update { it.copy(targetResolution = nextRes) }
    }

    fun reset() {
        cancelAudit()
        uniqueTrackIds.clear()
        accumulatedRecords.clear()
        _uiState.value.currentPreviewBitmap?.recycle()
        _uiState.value = VideoAuditUiState()
    }

    override fun onCleared() {
        super.onCleared()
        cancelAudit()
        _uiState.value.currentPreviewBitmap?.recycle()
    }
}
