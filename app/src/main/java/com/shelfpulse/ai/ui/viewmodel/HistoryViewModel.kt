package com.shelfpulse.ai.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfpulse.ai.data.csv.CsvExporter
import com.shelfpulse.ai.data.repository.InventoryRepository
import com.shelfpulse.ai.domain.model.InventorySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    fun exportAndShareCsv(context: Context, session: InventorySession) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = repository.getRecordsForSession(session.id)
            val fileName = "shelfpulse_session_${session.id}_${session.timestamp}.csv"
            val file = File(context.cacheDir, fileName)

            FileOutputStream(file).use { out ->
                CsvExporter.writeSessionCsvToStream(session, records, out)
            }

            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "ShelfPulse Inventory Report - Session #${session.id}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Inventory CSV"))
                } catch (e: Exception) {
                    // Fallback to plain text share if FileProvider fails
                    val csvString = CsvExporter.exportSessionToCsvString(session, records)
                    val textIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, csvString)
                        putExtra(Intent.EXTRA_SUBJECT, "ShelfPulse Inventory Report - Session #${session.id}")
                    }
                    context.startActivity(Intent.createChooser(textIntent, "Share Inventory Data"))
                }
            }
        }
    }
}
