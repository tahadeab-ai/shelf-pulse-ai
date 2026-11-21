package com.shelfpulse.ai.domain.model

data class InventorySession(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val shelfLocation: String,
    val totalCount: Int,
    val operatorNotes: String = "",
    val mode: String = "SNAP_AND_VERIFY",
    val rawBoxCount: Int = 0,
    val mergedBoxCount: Int = 0,
    val tileCount: Int = 0,
    val durationMs: Long = 0L,
    val barcode: String? = null,
    val productName: String? = null,
    val expectedStock: Int? = null,
    val unitPrice: Double? = null
) {
    val variance: Int?
        get() = expectedStock?.let { totalCount - it }

    val totalFinancialValue: Double?
        get() = unitPrice?.let { totalCount * it }
}

data class ShelfScanRecord(
    val id: Long = 0,
    val sessionId: Long,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int,
    val trackId: Int = -1,
    val isManual: Boolean = false,
    val skuId: String? = null,
    val embeddingBlob: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShelfScanRecord
        if (id != other.id) return false
        if (sessionId != other.sessionId) return false
        if (skuId != other.skuId) return false
        if (embeddingBlob != null) {
            if (other.embeddingBlob == null) return false
            if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false
        } else if (other.embeddingBlob != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + (skuId?.hashCode() ?: 0)
        result = 31 * result + (embeddingBlob?.contentHashCode() ?: 0)
        return result
    }
}
