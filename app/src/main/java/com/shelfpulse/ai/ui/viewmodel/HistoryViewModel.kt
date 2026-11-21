package com.shelfpulse.ai.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfpulse.ai.data.export.ExportManager
import com.shelfpulse.ai.data.repository.InventoryRepository
import com.shelfpulse.ai.data.sync.HttpSyncConnector
import com.shelfpulse.ai.data.sync.SyncAuditWorker
import com.shelfpulse.ai.data.sync.SyncConfig
import com.shelfpulse.ai.data.sync.SyncPreferences
import com.shelfpulse.ai.domain.model.InventorySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportFormat {
    PDF,
    ANNOTATED_IMAGE,
    CSV,
    JSON
}

class HistoryViewModel(
    private val repository: InventoryRepository
) : ViewModel() {

    val sessions: StateFlow<List<InventorySession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(sessionId)
        }
    }

    fun exportAndShare(
        context: Context,
        session: InventorySession,
        format: ExportFormat,
        snapshotBitmap: Bitmap? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = repository.getRecordsForSession(session.id)
            val uri: Uri = when (format) {
                ExportFormat.CSV -> ExportManager.exportCsv(context, session, records)
                ExportFormat.JSON -> ExportManager.exportJson(context, session, records)
                ExportFormat.ANNOTATED_IMAGE -> {
                    if (snapshotBitmap != null) {
                        ExportManager.exportAnnotatedImage(context, snapshotBitmap, session, records)
                    } else {
                        ExportManager.exportCsv(context, session, records)
                    }
                }
                ExportFormat.PDF -> ExportManager.exportPdfReport(context, session, records, snapshotBitmap)
            }

            val mimeType = when (format) {
                ExportFormat.CSV -> "text/csv"
                ExportFormat.JSON -> "application/json"
                ExportFormat.ANNOTATED_IMAGE -> "image/jpeg"
                ExportFormat.PDF -> "application/pdf"
            }

            withContext(Dispatchers.Main) {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "ShelfPulse Inventory Report - Session #${session.id} (${session.shelfLocation})")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Inventory Report"))
                } catch (e: Exception) {
                    android.util.Log.e("HistoryViewModel", "Share failed", e)
                }
            }
        }
    }

    fun exportAndShareCsv(context: Context, session: InventorySession) {
        exportAndShare(context, session, ExportFormat.CSV)
    }

    fun triggerSessionSync(context: Context, session: InventorySession) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSyncStatus(session.id, "QUEUED")
            SyncAuditWorker.enqueueOneTimeSync(context)
        }
    }

    fun testConnection(config: SyncConfig, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val connector = HttpSyncConnector()
            val result = connector.testConnection(config)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun saveSyncConfig(context: Context, config: SyncConfig) {
        SyncPreferences(context).saveSyncConfig(config)
        if (config.enabled) {
            SyncAuditWorker.enqueuePeriodicSync(context)
            SyncAuditWorker.enqueueOneTimeSync(context)
        }
    }
}
