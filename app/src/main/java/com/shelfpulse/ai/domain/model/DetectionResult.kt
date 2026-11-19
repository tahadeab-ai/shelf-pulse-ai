package com.shelfpulse.ai.domain.model

data class DetectionResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float,
    val classId: Int = 0,
    val trackId: Int = -1,
    val isManualMarker: Boolean = false,
    val isExcluded: Boolean = false
) {
    val width: Float get() = (x2 - x1).coerceAtLeast(0f)
    val height: Float get() = (y2 - y1).coerceAtLeast(0f)
    val centerX: Float get() = x1 + width / 2f
    val centerY: Float get() = y1 + height / 2f

    fun containsPoint(px: Float, py: Float, tolerance: Float = 15f): Boolean {
        return px >= (x1 - tolerance) && px <= (x2 + tolerance) &&
               py >= (y1 - tolerance) && py <= (y2 + tolerance)
    }
}
