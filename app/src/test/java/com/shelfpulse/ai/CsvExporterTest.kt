package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.csv.CsvExporter
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class CsvExporterTest {

    @Test
    fun exportSession_prependsUtf8BomHeader() {
        val session = InventorySession(
            id = 1,
            timestamp = 1710000000000L,
            shelfLocation = "Aisle 4",
            totalCount = 2,
            operatorNotes = "Test note"
        )
        val records = listOf(
            ShelfScanRecord(id = 10, sessionId = 1, x1 = 10f, y1 = 20f, x2 = 100f, y2 = 200f, confidence = 0.95f, classId = 0)
        )

        val outputStream = ByteArrayOutputStream()
        CsvExporter.writeSessionCsvToStream(session, records, outputStream)
        val bytes = outputStream.toByteArray()

        // Verify UTF-8 BOM bytes: 0xEF, 0xBB, 0xBF
        assertThat(bytes.size).isAtLeast(3)
        assertThat(bytes[0]).isEqualTo(0xEF.toByte())
        assertThat(bytes[1]).isEqualTo(0xBB.toByte())
        assertThat(bytes[2]).isEqualTo(0xBF.toByte())
    }

    @Test
    fun exportSession_preservesArabicCharactersAccurately() {
        val arabicLocation = "الممر 4 - الرف ب"
        val arabicNotes = "تم جرد عبوات العصائر والمشروبات بنجاح"
        val session = InventorySession(
            id = 42,
            timestamp = 1710000000000L,
            shelfLocation = arabicLocation,
            totalCount = 1,
            operatorNotes = arabicNotes
        )
        val records = listOf(
            ShelfScanRecord(id = 1, sessionId = 42, x1 = 0f, y1 = 0f, x2 = 50f, y2 = 50f, confidence = 0.92f, classId = 0)
        )

        val csvString = CsvExporter.exportSessionToCsvString(session, records)

        assertThat(csvString).contains(arabicLocation)
        assertThat(csvString).contains(arabicNotes)
    }

    @Test
    fun exportSession_escapesQuotesAndCommasInNotes() {
        val session = InventorySession(
            id = 99,
            timestamp = 1710000000000L,
            shelfLocation = "Shelf \"A, B & C\"",
            totalCount = 0,
            operatorNotes = "Notes with, comma and \"quotes\""
        )

        val csvString = CsvExporter.exportSessionToCsvString(session, emptyList())

        assertThat(csvString).contains("\"Shelf \"\"A, B & C\"\"\"")
        assertThat(csvString).contains("\"Notes with, comma and \"\"quotes\"\"\"")
    }
}
