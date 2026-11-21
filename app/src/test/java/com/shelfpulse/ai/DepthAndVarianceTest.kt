package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.db.InventorySessionEntity
import com.shelfpulse.ai.domain.model.InventorySession
import org.junit.Test

class DepthAndVarianceTest {

    @Test
    fun depthMultiplier_calculatesTotalUnitsCorrectly() {
        val frontFacings = 90
        val depth = 3

        val session = InventorySession.fromFacings(
            id = 101,
            shelfLocation = "Aisle 4 - Shelf B",
            frontFacings = frontFacings,
            depthMultiplier = depth,
            expectedStock = 250,
            unitPrice = 2.50
        )

        // 90 facings × 3 depth = 270 total units
        assertThat(session.frontFacings).isEqualTo(90)
        assertThat(session.depthMultiplier).isEqualTo(3)
        assertThat(session.totalCount).isEqualTo(270)
    }

    @Test
    fun depthMultiplier_defaultIsOne() {
        val session = InventorySession(
            id = 1,
            shelfLocation = "Shelf A",
            totalCount = 45
        )

        assertThat(session.depthMultiplier).isEqualTo(1)
        assertThat(session.frontFacings).isEqualTo(45)
        assertThat(session.totalCount).isEqualTo(45)
    }

    @Test
    fun depthMultiplier_clampsToValidRange() {
        // Less than 1 clamps to 1
        val sessionMin = InventorySession.fromFacings(
            shelfLocation = "Shelf Min",
            frontFacings = 10,
            depthMultiplier = 0
        )
        assertThat(sessionMin.depthMultiplier).isEqualTo(1)
        assertThat(sessionMin.totalCount).isEqualTo(10)

        // Greater than 20 clamps to 20
        val sessionMax = InventorySession.fromFacings(
            shelfLocation = "Shelf Max",
            frontFacings = 10,
            depthMultiplier = 25
        )
        assertThat(sessionMax.depthMultiplier).isEqualTo(20)
        assertThat(sessionMax.totalCount).isEqualTo(200)
    }

    @Test
    fun variance_calculatesSurplusShortageAndMatch() {
        // Surplus: 270 counted vs 250 expected = +20
        val surplusSession = InventorySession(
            shelfLocation = "Shelf Surplus",
            totalCount = 270,
            expectedStock = 250
        )
        assertThat(surplusSession.variance).isEqualTo(20)

        // Shortage: 270 counted vs 300 expected = -30
        val shortageSession = InventorySession(
            shelfLocation = "Shelf Shortage",
            totalCount = 270,
            expectedStock = 300
        )
        assertThat(shortageSession.variance).isEqualTo(-30)

        // Match: 270 counted vs 270 expected = 0
        val matchSession = InventorySession(
            shelfLocation = "Shelf Match",
            totalCount = 270,
            expectedStock = 270
        )
        assertThat(matchSession.variance).isEqualTo(0)

        // Null expected stock -> null variance
        val noExpectedSession = InventorySession(
            shelfLocation = "Shelf Unknown",
            totalCount = 270,
            expectedStock = null
        )
        assertThat(noExpectedSession.variance).isNull()
    }

    @Test
    fun totalFinancialValue_multipliesTotalCountByUnitPrice() {
        val session = InventorySession(
            shelfLocation = "Shelf Candy",
            totalCount = 270,
            unitPrice = 2.50
        )

        assertThat(session.totalFinancialValue).isWithin(0.001).of(675.0)

        val unpricedSession = InventorySession(
            shelfLocation = "Shelf Unpriced",
            totalCount = 270,
            unitPrice = null
        )
        assertThat(unpricedSession.totalFinancialValue).isNull()
    }

    @Test
    fun entityMapping_preservesDepthMultiplier() {
        val entity = InventorySessionEntity(
            id = 50,
            timestamp = 1710000000000L,
            shelfLocation = "Aisle 1 - Bay A",
            totalCount = 180,
            operatorNotes = "Depth verification",
            mode = "SNAP_VERIFY",
            depthMultiplier = 4
        )

        val session = InventorySession(
            id = entity.id,
            timestamp = entity.timestamp,
            shelfLocation = entity.shelfLocation,
            totalCount = entity.totalCount,
            operatorNotes = entity.operatorNotes,
            mode = entity.mode,
            depthMultiplier = entity.depthMultiplier
        )

        assertThat(session.depthMultiplier).isEqualTo(4)
        assertThat(session.totalCount).isEqualTo(180)
        assertThat(session.frontFacings).isEqualTo(45)
    }

    @Test
    fun geometricBoxFilter_boundsValidation() {
        // Geometric filter helper matching C++ rules
        fun isValidBox(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
            val w = x2 - x1
            val h = y2 - y1
            if (w <= 0.0f || h <= 0.0f) return false
            val area = w * h
            if (area > 0.45f) return false // > 45% of frame
            val aspect = w / h
            if (aspect > 8.0f || aspect < 0.125f) return false // extreme aspect ratios
            return true
        }

        // Standard shelf product (10% width, 15% height) -> valid
        assertThat(isValidBox(0.2f, 0.3f, 0.3f, 0.45f)).isTrue()

        // Tiny warehouse item (2% width, 3% height) -> valid
        assertThat(isValidBox(0.01f, 0.01f, 0.03f, 0.04f)).isTrue()

        // Huge wall banner covering 50% of frame -> filtered out
        assertThat(isValidBox(0.1f, 0.1f, 0.9f, 0.8f)).isFalse()

        // Ultra-wide shelf rail artifact (aspect ratio 12:1) -> filtered out
        assertThat(isValidBox(0.1f, 0.5f, 0.7f, 0.55f)).isFalse()

        // Ultra-tall vertical stripe artifact (aspect ratio 1:12) -> filtered out
        assertThat(isValidBox(0.5f, 0.1f, 0.55f, 0.7f)).isFalse()
    }
}
