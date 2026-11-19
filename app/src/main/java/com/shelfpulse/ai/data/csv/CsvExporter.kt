package com.shelfpulse.ai.data.csv

import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun exportSessionToCsvString(
        session: InventorySession,
        records: List<ShelfScanRecord>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateString = dateFormat.format(Date(session.timestamp))

        val sb = StringBuilder()
        // Prepend UTF-8 BOM
        sb.append("\uFEFF")

        // Metadata Header
        sb.append("Session ID,Timestamp,Shelf Location,Total Count,Mode,Operator Notes\n")
        sb.append(escape(session.id.toString())).append(",")
        sb.append(escape(dateString)).append(",")
        sb.append(escape(session.shelfLocation)).append(",")
        sb.append(session.totalCount).append(",")
        sb.append(escape(session.mode)).append(",")
        sb.append(escape(session.operatorNotes)).append("\n\n")

        // Records Table
        sb.append("Item #,Class ID,Confidence,Track ID,Type,X1,Y1,X2,Y2,SKU ID\n")
        records.forEachIndexed { index, record ->
            sb.append(index + 1).append(",")
            sb.append(record.classId).append(",")
            sb.append(String.format(Locale.US, "%.3f", record.confidence)).append(",")
            sb.append(record.trackId).append(",")
            sb.append(if (record.isManual) "MANUAL_ADD" else "AUTO_DETECT").append(",")
            sb.append(String.format(Locale.US, "%.1f", record.x1)).append(",")
            sb.append(String.format(Locale.US, "%.1f", record.y1)).append(",")
            sb.append(String.format(Locale.US, "%.1f", record.x2)).append(",")
            sb.append(String.format(Locale.US, "%.1f", record.y2)).append(",")
            sb.append(escape(record.skuId ?: "")).append("\n")
        }

        return sb.toString()
    }

    fun writeSessionCsvToStream(
        session: InventorySession,
        records: List<ShelfScanRecord>,
        outputStream: OutputStream
    ) {
        val csvContent = exportSessionToCsvString(session, records)
        // Ensure raw bytes start with BOM
        outputStream.write(csvContent.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    private fun escape(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
