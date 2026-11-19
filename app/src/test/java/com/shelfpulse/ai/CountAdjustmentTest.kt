package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.domain.model.DetectionResult
import org.junit.Test

class CountAdjustmentTest {

    @Test
    fun detectionResult_containsPoint_correctlyIdentifiesInsidePoint() {
        val detection = DetectionResult(
            x1 = 100f,
            y1 = 100f,
            x2 = 200f,
            y2 = 200f,
            score = 0.85f
        )

        // Inside center
        assertThat(detection.containsPoint(150f, 150f)).isTrue()
        // Outside
        assertThat(detection.containsPoint(50f, 50f)).isFalse()
        // Boundary within tolerance
        assertThat(detection.containsPoint(95f, 100f, tolerance = 10f)).isTrue()
    }

    @Test
    fun countCalculation_togglingExclusionDecreasesEffectiveCount() {
        val initialDetections = listOf(
            DetectionResult(x1 = 10f, y1 = 10f, x2 = 50f, y2 = 50f, score = 0.9f, isExcluded = false),
            DetectionResult(x1 = 60f, y1 = 10f, x2 = 100f, y2 = 50f, score = 0.8f, isExcluded = false),
            DetectionResult(x1 = 110f, y1 = 10f, x2 = 150f, y2 = 50f, score = 0.7f, isExcluded = false)
        )

        val totalCountBefore = initialDetections.count { !it.isExcluded }
        assertThat(totalCountBefore).isEqualTo(3)

        // Exclude 2nd item (-1)
        val adjustedDetections = initialDetections.mapIndexed { idx, det ->
            if (idx == 1) det.copy(isExcluded = true) else det
        }

        val totalCountAfter = adjustedDetections.count { !it.isExcluded }
        assertThat(totalCountAfter).isEqualTo(2)
    }

    @Test
    fun countCalculation_addingManualMarkerIncreasesEffectiveCount() {
        val initialDetections = listOf(
            DetectionResult(x1 = 10f, y1 = 10f, x2 = 50f, y2 = 50f, score = 0.9f)
        )

        val manualMarker = DetectionResult(
            x1 = 100f,
            y1 = 100f,
            x2 = 150f,
            y2 = 150f,
            score = 1.0f,
            isManualMarker = true
        )

        val combined = initialDetections + manualMarker
        assertThat(combined.count { !it.isExcluded }).isEqualTo(2)
        assertThat(combined.last().isManualMarker).isTrue()
    }
}
