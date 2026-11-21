package com.shelfpulse.ai.data.repository

import com.shelfpulse.ai.data.db.InventoryDao
import com.shelfpulse.ai.data.db.InventorySessionEntity
import com.shelfpulse.ai.data.db.ProductCatalogDao
import com.shelfpulse.ai.data.db.ProductCatalogEntity
import com.shelfpulse.ai.data.db.ShelfScanRecordEntity
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class InventoryRepository(
    private val dao: InventoryDao,
    private val catalogDao: ProductCatalogDao? = null
) {

    val allSessions: Flow<List<InventorySession>> = dao.getAllSessions().map { entities ->
        entities.map { entity ->
            InventorySession(
                id = entity.id,
                timestamp = entity.timestamp,
                shelfLocation = entity.shelfLocation,
                totalCount = entity.totalCount,
                operatorNotes = entity.operatorNotes,
                mode = entity.mode,
                rawBoxCount = entity.rawBoxCount,
                mergedBoxCount = entity.mergedBoxCount,
                tileCount = entity.tileCount,
                durationMs = entity.durationMs,
                barcode = entity.barcode,
                productName = entity.productName,
                expectedStock = entity.expectedStock,
                unitPrice = entity.unitPrice,
                syncStatus = entity.syncStatus,
                lastSyncedAt = entity.lastSyncedAt,
                photoPath = entity.photoPath
            )
        }
    }

    suspend fun saveSession(session: InventorySession, records: List<ShelfScanRecord>): Long {
        val sessionEntity = InventorySessionEntity(
            id = session.id,
            timestamp = session.timestamp,
            shelfLocation = session.shelfLocation,
            totalCount = session.totalCount,
            operatorNotes = session.operatorNotes,
            mode = session.mode,
            rawBoxCount = session.rawBoxCount,
            mergedBoxCount = session.mergedBoxCount,
            tileCount = session.tileCount,
            durationMs = session.durationMs,
            barcode = session.barcode,
            productName = session.productName,
            expectedStock = session.expectedStock,
            unitPrice = session.unitPrice,
            syncStatus = session.syncStatus,
            lastSyncedAt = session.lastSyncedAt,
            photoPath = session.photoPath
        )

        val recordEntities = records.map { r ->
            ShelfScanRecordEntity(
                id = r.id,
                sessionId = session.id,
                x1 = r.x1,
                y1 = r.y1,
                x2 = r.x2,
                y2 = r.y2,
                confidence = r.confidence,
                classId = r.classId,
                trackId = r.trackId,
                isManual = r.isManual,
                skuId = r.skuId,
                embeddingBlob = r.embeddingBlob
            )
        }

        return dao.saveFullSession(sessionEntity, recordEntities)
    }

    suspend fun getRecordsForSession(sessionId: Long): List<ShelfScanRecord> {
        return dao.getRecordsForSession(sessionId).map { e ->
            ShelfScanRecord(
                id = e.id,
                sessionId = e.sessionId,
                x1 = e.x1,
                y1 = e.y1,
                x2 = e.x2,
                y2 = e.y2,
                confidence = e.confidence,
                classId = e.classId,
                trackId = e.trackId,
                isManual = e.isManual,
                skuId = e.skuId,
                embeddingBlob = e.embeddingBlob
            )
        }
    }

    suspend fun getSessionById(sessionId: Long): InventorySession? {
        return dao.getSessionById(sessionId)?.let { entity ->
            InventorySession(
                id = entity.id,
                timestamp = entity.timestamp,
                shelfLocation = entity.shelfLocation,
                totalCount = entity.totalCount,
                operatorNotes = entity.operatorNotes,
                mode = entity.mode,
                rawBoxCount = entity.rawBoxCount,
                mergedBoxCount = entity.mergedBoxCount,
                tileCount = entity.tileCount,
                durationMs = entity.durationMs,
                barcode = entity.barcode,
                productName = entity.productName,
                expectedStock = entity.expectedStock,
                unitPrice = entity.unitPrice,
                syncStatus = entity.syncStatus,
                lastSyncedAt = entity.lastSyncedAt,
                photoPath = entity.photoPath
            )
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSession(sessionId)
    }

    suspend fun updateSyncStatus(sessionId: Long, status: String, syncedAt: Long? = null) {
        dao.updateSyncStatus(sessionId, status, syncedAt)
    }

    suspend fun updatePhotoPath(sessionId: Long, photoPath: String) {
        dao.updatePhotoPath(sessionId, photoPath)
    }

    suspend fun getPendingSyncSessions(): List<InventorySession> {
        return dao.getPendingSyncSessions().map { entity ->
            InventorySession(
                id = entity.id,
                timestamp = entity.timestamp,
                shelfLocation = entity.shelfLocation,
                totalCount = entity.totalCount,
                operatorNotes = entity.operatorNotes,
                mode = entity.mode,
                rawBoxCount = entity.rawBoxCount,
                mergedBoxCount = entity.mergedBoxCount,
                tileCount = entity.tileCount,
                durationMs = entity.durationMs,
                barcode = entity.barcode,
                productName = entity.productName,
                expectedStock = entity.expectedStock,
                unitPrice = entity.unitPrice,
                syncStatus = entity.syncStatus,
                lastSyncedAt = entity.lastSyncedAt,
                photoPath = entity.photoPath
            )
        }
    }

    suspend fun upsertProduct(product: ProductCatalogEntity) {
        catalogDao?.upsertProduct(product)
    }

    suspend fun getProductByBarcode(barcode: String): ProductCatalogEntity? {
        return catalogDao?.getByBarcode(barcode)
    }

    suspend fun searchProducts(query: String): List<ProductCatalogEntity> {
        return catalogDao?.searchByName(query) ?: emptyList()
    }

    fun getAllProductsFlow(): Flow<List<ProductCatalogEntity>> {
        return catalogDao?.getAllFlow() ?: flowOf(emptyList())
    }

    suspend fun deleteProduct(barcode: String) {
        catalogDao?.delete(barcode)
    }
}
