package com.shelfpulse.ai.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InventorySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecords(records: List<ShelfScanRecordEntity>)

    @Transaction
    suspend fun saveFullSession(
        session: InventorySessionEntity,
        records: List<ShelfScanRecordEntity>
    ): Long {
        val sessionId = insertSession(session)
        val mappedRecords = records.map { it.copy(sessionId = sessionId) }
        insertScanRecords(mappedRecords)
        return sessionId
    }

    @Query("SELECT * FROM inventory_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<InventorySessionEntity>>

    @Query("SELECT * FROM inventory_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): InventorySessionEntity?

    @Query("SELECT * FROM shelf_scan_records WHERE sessionId = :sessionId")
    suspend fun getRecordsForSession(sessionId: Long): List<ShelfScanRecordEntity>

    @Query("DELETE FROM inventory_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
