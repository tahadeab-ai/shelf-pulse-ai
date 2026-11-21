package com.shelfpulse.ai.data.sync

import com.shelfpulse.ai.domain.model.InventorySession

enum class ConnectorPreset {
    GENERIC_WEBHOOK,
    INVENTREE_REST,
    ERPNEXT_STOCK_RECONCILIATION
}

data class SyncConfig(
    val enabled: Boolean = false,
    val preset: ConnectorPreset = ConnectorPreset.GENERIC_WEBHOOK,
    val endpointUrl: String = "",
    val bearerToken: String = "",
    val autoSyncOnSave: Boolean = false
)

interface InventorySyncConnector {
    suspend fun pushSession(session: InventorySession, config: SyncConfig): Result<Unit>
    suspend fun testConnection(config: SyncConfig): Result<String>
}
