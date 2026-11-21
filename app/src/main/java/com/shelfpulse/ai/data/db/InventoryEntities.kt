package com.shelfpulse.ai.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "inventory_sessions")
data class InventorySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val shelfLocation: String,
    val totalCount: Int,
    val operatorNotes: String,
    val mode: String,
    val rawBoxCount: Int = 0,
    val mergedBoxCount: Int = 0,
    val tileCount: Int = 0,
    val durationMs: Long = 0L,
    val barcode: String? = null,
    val productName: String? = null,
    val expectedStock: Int? = null,
    val unitPrice: Double? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long? = null,
    val photoPath: String? = null,
    val depthMultiplier: Int = 1
)

@Entity(tableName = "product_catalog")
data class ProductCatalogEntity(
    @PrimaryKey
    val barcode: String,
    val name: String,
    val brand: String = "",
    val expectedStock: Int = 0,
    val unitPrice: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "shelf_scan_records",
    foreignKeys = [
        ForeignKey(
            entity = InventorySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("skuId")
    ]
)
data class ShelfScanRecordEntity(
    @PrimaryKey(autoGenerate = true)
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

        other as ShelfScanRecordEntity
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
