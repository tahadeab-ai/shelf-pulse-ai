package com.shelfpulse.ai.data.repository

import com.shelfpulse.ai.data.db.InventoryDao
import com.shelfpulse.ai.data.db.InventorySessionEntity
import com.shelfpulse.ai.data.db.ShelfScanRecordEntity
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InventoryRepository(private val dao: InventoryDao) {

    val allSessions: Flow<List<InventorySession>> = dao.getAllSessions().map { entities ->
        entities.map { entity ->
            InventorySession(
                id = entity.id,
                timestamp = entity.timestamp,
                shelfLocation = entity.shelfLocation,
                totalCount = entity.totalCount,
                operatorNotes = entity.operatorNotes,
                mode = entity.mode
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
            mode = session.mode
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

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteSession(sessionId)
    }
}
