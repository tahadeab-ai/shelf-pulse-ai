package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.domain.model.DetectionResult
import com.shelfpulse.ai.ui.viewmodel.SnapVerifyUiState
import org.junit.Test

class ViewModelStateTest {

    @Test
    fun snapVerifyUiState_effectiveCountFiltersExcludedDetections() {
        val state = SnapVerifyUiState(
            detections = listOf(
                DetectionResult(x1 = 0f, y1 = 0f, x2 = 10f, y2 = 10f, score = 0.9f, isExcluded = false),
                DetectionResult(x1 = 20f, y1 = 0f, x2 = 30f, y2 = 10f, score = 0.8f, isExcluded = true),
                DetectionResult(x1 = 40f, y1 = 0f, x2 = 50f, y2 = 10f, score = 0.7f, isExcluded = false),
                DetectionResult(x1 = 60f, y1 = 0f, x2 = 70f, y2 = 10f, score = 1.0f, isManualMarker = true, isExcluded = false)
            )
        )

        assertThat(state.detections.size).isEqualTo(4)
        assertThat(state.effectiveCount).isEqualTo(3)
    }

    @Test
    fun snapVerifyUiState_defaults_haveReasonableThresholds() {
        val defaultState = SnapVerifyUiState()

        assertThat(defaultState.confThresh).isGreaterThan(0.2f)
        assertThat(defaultState.nmsThresh).isGreaterThan(0.2f)
        assertThat(defaultState.isProcessing).isFalse()
        assertThat(defaultState.capturedBitmap).isNull()
    }
}
