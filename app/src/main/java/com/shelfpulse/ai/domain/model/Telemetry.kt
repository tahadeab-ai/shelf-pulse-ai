package com.shelfpulse.ai.domain.model

data class Telemetry(
    val fps: Float = 0.0f,
    val inferenceLatencyMs: Float = 0.0f,
    val preprocessMs: Float = 0.0f,
    val trackMs: Float = 0.0f,
    val nmsMs: Float = 0.0f,
    val totalLatencyMs: Float = 0.0f,
    val isVulkanActive: Boolean = false,
    val targetResolution: Int = 640,
    val tileCount: Int = 1,
    val rawBoxCount: Int = 0,
    val mergedBoxCount: Int = 0
)
