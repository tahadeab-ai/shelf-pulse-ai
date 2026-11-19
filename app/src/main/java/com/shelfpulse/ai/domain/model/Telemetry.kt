package com.shelfpulse.ai.domain.model

data class Telemetry(
    val fps: Float = 0.0f,
    val inferenceLatencyMs: Float = 0.0f,
    val isVulkanActive: Boolean = false
)
