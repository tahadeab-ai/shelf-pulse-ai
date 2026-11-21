package com.shelfpulse.ai.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfpulse.ai.data.repository.InventoryRepository
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
    val confThresh: Float = 0.40f,
    val nmsThresh: Float = 0.45f
)

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

                var frameIdx = 0

                while (isActive && frameIdx < totalFrames) {
                    val timeUs = frameIdx * FRAME_INTERVAL_MS * 1000L

                    // Guardrail 2: Use getScaledFrameAtTime(timeUs, OPTION_CLOSEST, 640, 640)
                    val rawFrame = retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        640,
                        640
                    )

                    if (rawFrame != null) {
                        // Ensure software ARGB_8888 bitmap for NDK native processing
                        val frameBitmap = if (rawFrame.config != Bitmap.Config.ARGB_8888) {
                            val converted = rawFrame.copy(Bitmap.Config.ARGB_8888, false)
                            rawFrame.recycle()
                            converted ?: rawFrame
                        } else {
                            rawFrame
                        }

                        // Pass to YOLO11 + ByteTrack-cpp pipeline
                        val (dets, telem) = nativeEngine.detectBitmap(
                            bitmap = frameBitmap,
                            confThresh = conf,
                            nmsThresh = nms,
                            enableTracking = true
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
                                currentPreviewBitmap = frameBitmap,
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

    fun saveSession(shelfLocation: String, notes: String, onComplete: () -> Unit = {}) {
        val state = _uiState.value
        val uniqueCount = state.uniqueTrackedCount

        viewModelScope.launch(Dispatchers.IO) {
            val session = InventorySession(
                shelfLocation = shelfLocation.ifBlank { "Aisle Video Audit" },
                totalCount = uniqueCount,
                operatorNotes = notes,
                mode = "VIDEO_AUDIT",
                rawBoxCount = accumulatedRecords.size,
                mergedBoxCount = uniqueCount,
                tileCount = 1,
                durationMs = state.videoDurationMs
            )

            val sessionId = repository.saveSession(session, accumulatedRecords.toList())
            _uiState.update { it.copy(isSaved = true, lastSavedSessionId = sessionId) }
            onComplete()
        }
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
