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

    @Test
    fun snapVerifyUiState_defaultScanModeIsSingle640() {
        val state = SnapVerifyUiState()
        assertThat(state.scanMode).isEqualTo(com.shelfpulse.ai.ui.viewmodel.ScanMode.SINGLE_640)
    }

    @Test
    fun videoAuditUiState_defaults_areIdle() {
        val state = com.shelfpulse.ai.ui.viewmodel.VideoAuditUiState()
        assertThat(state.isAuditing).isFalse()
        assertThat(state.isComplete).isFalse()
        assertThat(state.uniqueTrackedCount).isEqualTo(0)
        assertThat(state.depthMultiplier).isEqualTo(1)
        assertThat(state.totalCountWithDepth).isEqualTo(0)
    }

    @Test
    fun liveScanUiState_depthAndSkuCalculations() {
        val state = com.shelfpulse.ai.ui.viewmodel.LiveScanUiState(
            cumulativeTrackCount = 15,
            depthMultiplier = 3,
            linkedBarcode = "628100123456",
            linkedProductName = "Almarai Fresh Milk 1L",
            linkedExpectedStock = 40,
            linkedUnitPrice = 1.75
        )

        // 15 front facings * 3 depth = 45 total count
        assertThat(state.frontFacings).isEqualTo(15)
        assertThat(state.totalCountWithDepth).isEqualTo(45)
        // 45 counted - 40 expected = +5 variance (surplus)
        assertThat(state.variance).isEqualTo(5)
        // 45 * $1.75 = $78.75
        assertThat(state.totalFinancialValue).isEqualTo(78.75)
    }

    @Test
    fun videoAuditUiState_depthAndSkuCalculations() {
        val state = com.shelfpulse.ai.ui.viewmodel.VideoAuditUiState(
            uniqueTrackedCount = 20,
            depthMultiplier = 4,
            linkedBarcode = "628700987654",
            linkedProductName = "Lipton Green Tea 100s",
            linkedExpectedStock = 100,
            linkedUnitPrice = 3.50
        )

        // 20 front facings * 4 depth = 80 total count
        assertThat(state.frontFacings).isEqualTo(20)
        assertThat(state.totalCountWithDepth).isEqualTo(80)
        // 80 counted - 100 expected = -20 variance (shortage)
        assertThat(state.variance).isEqualTo(-20)
        // 80 * $3.50 = $280.00
        assertThat(state.totalFinancialValue).isEqualTo(280.0)
    }
}
