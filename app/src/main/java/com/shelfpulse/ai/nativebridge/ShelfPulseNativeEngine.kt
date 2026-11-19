package com.shelfpulse.ai.nativebridge

import android.graphics.Bitmap
import android.util.Log
import com.shelfpulse.ai.domain.model.DetectionResult
import com.shelfpulse.ai.domain.model.Telemetry
import java.nio.ByteBuffer

class ShelfPulseNativeEngine {

    companion object {
        private const val TAG = "ShelfPulseNative"

        init {
            try {
                System.loadLibrary("_shelfpulse_native")
                Log.i(TAG, "Successfully loaded lib_shelfpulse_native.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load lib_shelfpulse_native.so", e)
            }
        }
    }

    private external fun nativeInit(paramPath: String, binPath: String, useGpu: Boolean): Boolean
    private external fun nativeDetectBitmap(
        bitmap: Bitmap,
        confThresh: Float,
        nmsThresh: Float,
        enableTracking: Boolean
    ): FloatArray

    private external fun nativeDetectYuv(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        rotationDegrees: Int,
        confThresh: Float,
        nmsThresh: Float,
        enableTracking: Boolean
    ): FloatArray

    private external fun nativeResetTracker()
    private external fun nativeRelease()

    fun initialize(paramPath: String, binPath: String, useGpu: Boolean): Boolean {
        return try {
            nativeInit(paramPath, binPath, useGpu)
        } catch (t: Throwable) {
            Log.e(TAG, "nativeInit exception", t)
            false
        }
    }

    fun detectBitmap(
        bitmap: Bitmap,
        confThresh: Float = 0.40f,
        nmsThresh: Float = 0.45f,
        enableTracking: Boolean = false
    ): Pair<List<DetectionResult>, Telemetry> {
        val rawArray = nativeDetectBitmap(bitmap, confThresh, nmsThresh, enableTracking)
        return parseRawOutput(rawArray)
    }

    fun detectYuv(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        rotationDegrees: Int,
        confThresh: Float = 0.40f,
        nmsThresh: Float = 0.45f,
        enableTracking: Boolean = true
    ): Pair<List<DetectionResult>, Telemetry> {
        val rawArray = nativeDetectYuv(
            yBuffer, uBuffer, vBuffer,
            width, height,
            yRowStride, uvRowStride, uvPixelStride,
            rotationDegrees,
            confThresh, nmsThresh, enableTracking
        )
        return parseRawOutput(rawArray)
    }

    fun resetTracker() {
        try {
            nativeResetTracker()
        } catch (t: Throwable) {
            Log.e(TAG, "nativeResetTracker exception", t)
        }
    }

    fun release() {
        try {
            nativeRelease()
        } catch (t: Throwable) {
            Log.e(TAG, "nativeRelease exception", t)
        }
    }

    private fun parseRawOutput(rawArray: FloatArray?): Pair<List<DetectionResult>, Telemetry> {
        if (rawArray == null || rawArray.size < 3) {
            return Pair(emptyList(), Telemetry())
        }

        val numDets = rawArray[0].toInt()
        val latencyMs = rawArray[1]
        val isGpu = rawArray[2] > 0.5f

        val fps = if (latencyMs > 0f) (1000f / latencyMs).coerceAtMost(120f) else 0f
        val telemetry = Telemetry(
            fps = fps,
            inferenceLatencyMs = latencyMs,
            isVulkanActive = isGpu
        )

        val detections = ArrayList<DetectionResult>(numDets)
        for (i in 0 until numDets) {
            val offset = 3 + i * 7
            if (offset + 6 < rawArray.size) {
                val x1 = rawArray[offset + 0]
                val y1 = rawArray[offset + 1]
                val x2 = rawArray[offset + 2]
                val y2 = rawArray[offset + 3]
                val score = rawArray[offset + 4]
                val classId = rawArray[offset + 5].toInt()
                val trackId = rawArray[offset + 6].toInt()

                detections.add(
                    DetectionResult(
                        x1 = x1,
                        y1 = y1,
                        x2 = x2,
                        y2 = y2,
                        score = score,
                        classId = classId,
                        trackId = trackId
                    )
                )
            }
        }

        return Pair(detections, telemetry)
    }
}
