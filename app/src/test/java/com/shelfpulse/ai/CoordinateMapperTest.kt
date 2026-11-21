package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.ui.components.CoordinateMapper
import com.shelfpulse.ai.ui.components.computeFitViewport
import com.shelfpulse.ai.ui.components.mapBoxToCanvas
import com.shelfpulse.ai.ui.components.mapTapToNormalizedImage
import org.junit.Test
import kotlin.math.abs

class CoordinateMapperTest {

    private val tolerance = 0.5f

    @Test
    fun computeFitViewport_landscape16x9_onPortraitCanvas_letterboxesVertically() {
        val imageW = 1920
        val imageH = 1080
        val canvasW = 1080f
        val canvasH = 1920f

        val viewport = computeFitViewport(imageW, imageH, canvasW, canvasH)

        // Expected scale: 1080 / 1920 = 0.5625
        // Expected drawnWidth: 1080f, drawnHeight: 607.5f
        // Expected offsetX: 0f, offsetY: (1920 - 607.5) / 2 = 656.25f
        assertThat(abs(viewport.drawnWidth - 1080f)).isLessThan(tolerance)
        assertThat(abs(viewport.drawnHeight - 607.5f)).isLessThan(tolerance)
        assertThat(abs(viewport.offsetX - 0f)).isLessThan(tolerance)
        assertThat(abs(viewport.offsetY - 656.25f)).isLessThan(tolerance)

        // Map full image box [0f, 0f, 1f, 1f]
        val canvasBox = mapBoxToCanvas(0f, 0f, 1f, 1f, viewport)
        assertThat(abs(canvasBox.left - 0f)).isLessThan(tolerance)
        assertThat(abs(canvasBox.top - 656.25f)).isLessThan(tolerance)
        assertThat(abs(canvasBox.right - 1080f)).isLessThan(tolerance)
        assertThat(abs(canvasBox.bottom - 1263.75f)).isLessThan(tolerance)

        // Tap in top letterbox bar (e.g. y = 200f < 656.25f)
        assertThat(mapTapToNormalizedImage(540f, 200f, viewport)).isNull()
        // Tap in bottom letterbox bar (e.g. y = 1500f > 1263.75f)
        assertThat(mapTapToNormalizedImage(540f, 1500f, viewport)).isNull()

        // Tap in center of active image (540f, 960f)
        val centerTap = mapTapToNormalizedImage(540f, 960f, viewport)
        assertThat(centerTap).isNotNull()
        assertThat(abs(centerTap!!.first - 0.5f)).isLessThan(0.01f)
        assertThat(abs(centerTap.second - 0.5f)).isLessThan(0.01f)
    }

    @Test
    fun computeFitViewport_portrait9x16_onPortraitCanvas_fillsExactly() {
        val imageW = 1080
        val imageH = 1920
        val canvasW = 1080f
        val canvasH = 1920f

        val viewport = computeFitViewport(imageW, imageH, canvasW, canvasH)

        // Exact 1:1 match
        assertThat(abs(viewport.drawnWidth - 1080f)).isLessThan(tolerance)
        assertThat(abs(viewport.drawnHeight - 1920f)).isLessThan(tolerance)
        assertThat(abs(viewport.offsetX - 0f)).isLessThan(tolerance)
        assertThat(abs(viewport.offsetY - 0f)).isLessThan(tolerance)

        val canvasBox = mapBoxToCanvas(0.1f, 0.2f, 0.9f, 0.8f, viewport)
        assertThat(abs(canvasBox.left - 108f)).isLessThan(tolerance)
        assertThat(abs(canvasBox.top - 384f)).isLessThan(tolerance)
        assertThat(abs(canvasBox.right - 972f)).isLessThan(tolerance)
        assertThat(abs(canvasBox.bottom - 1536f)).isLessThan(tolerance)

        val tap = mapTapToNormalizedImage(108f, 384f, viewport)
        assertThat(tap).isNotNull()
        assertThat(abs(tap!!.first - 0.1f)).isLessThan(0.01f)
        assertThat(abs(tap.second - 0.2f)).isLessThan(0.01f)
    }

    @Test
    fun computeFitViewport_square1x1_onPortraitCanvas_letterboxesVertically() {
        val imageW = 1000
        val imageH = 1000
        val canvasW = 1080f
        val canvasH = 1920f

        val viewport = computeFitViewport(imageW, imageH, canvasW, canvasH)

        // Scale: 1080 / 1000 = 1.08
        // drawnWidth = 1080f, drawnHeight = 1080f
        // offsetX = 0f, offsetY = (1920 - 1080) / 2 = 420f
        assertThat(abs(viewport.drawnWidth - 1080f)).isLessThan(tolerance)
        assertThat(abs(viewport.drawnHeight - 1080f)).isLessThan(tolerance)
        assertThat(abs(viewport.offsetX - 0f)).isLessThan(tolerance)
        assertThat(abs(viewport.offsetY - 420f)).isLessThan(tolerance)

        // Top bar tap
        assertThat(mapTapToNormalizedImage(540f, 100f, viewport)).isNull()
        // Bottom bar tap
        assertThat(mapTapToNormalizedImage(540f, 1600f, viewport)).isNull()

        // Active center tap
        val tap = mapTapToNormalizedImage(540f, 960f, viewport)
        assertThat(tap).isNotNull()
        assertThat(abs(tap!!.first - 0.5f)).isLessThan(0.01f)
        assertThat(abs(tap.second - 0.5f)).isLessThan(0.01f)
    }

    @Test
    fun computeFitViewport_invalidDimensions_returnsZeroViewport() {
        val viewportZero = computeFitViewport(0, 0, 1080f, 1920f)
        assertThat(viewportZero.drawnWidth).isEqualTo(0f)
        assertThat(viewportZero.drawnHeight).isEqualTo(0f)
        assertThat(mapTapToNormalizedImage(100f, 100f, viewportZero)).isNull()
    }
}
