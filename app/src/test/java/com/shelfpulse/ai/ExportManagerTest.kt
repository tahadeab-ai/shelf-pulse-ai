package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.export.ExportManager
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import org.json.JSONObject
import org.junit.Test

class ExportManagerTest {

    @Test
    fun generateCsvString_prependsUtf8BomAndIncludesSkuMetrics() {
        val session = InventorySession(
            id = 101,
            timestamp = 1710000000000L,
            shelfLocation = "Aisle 3, Shelf B",
            totalCount = 24,
            mode = "SINGLE_640",
            operatorNotes = "Morning audit",
            barcode = "7622210449283",
            productName = "Oreo Original Cookies 154g",
            expectedStock = 30,
            unitPrice = 1.99
        )

        val records = listOf(
            ShelfScanRecord(
                id = 1,
                sessionId = 101,
                x1 = 0.1f,
                y1 = 0.2f,
                x2 = 0.3f,
                y2 = 0.4f,
                confidence = 0.94f,
                classId = 0,
                trackId = 1,
                isManual = false
            ),
            ShelfScanRecord(
                id = 2,
                sessionId = 101,
                x1 = 0.35f,
                y1 = 0.2f,
                x2 = 0.55f,
                y2 = 0.4f,
                confidence = 1.0f,
                classId = 0,
                trackId = -1,
                isManual = true
            )
        )

        val csv = ExportManager.generateCsvString(session, records)

        // Verify UTF-8 BOM
        assertThat(csv.startsWith("\uFEFF")).isTrue()

        // Verify Header and Summary fields
        assertThat(csv).contains("Session ID,Timestamp,Shelf Location,Front Facings,Depth Multiplier,Total Count,Mode,Barcode,Product Name,Expected Stock,Variance,Unit Price,Total Value,Operator Notes")
        assertThat(csv).contains("101,")
        assertThat(csv).contains("\"Aisle 3, Shelf B\"")
        assertThat(csv).contains("7622210449283")
        assertThat(csv).contains("Oreo Original Cookies 154g")
        assertThat(csv).contains(",30,")
        // Variance: 24 - 30 = -6
        assertThat(csv).contains(",-6,")
        // Unit Price: 1.99
        assertThat(csv).contains("1.99")
        // Total Value: 24 * 1.99 = 47.76
        assertThat(csv).contains("47.76")

        // Verify Itemized Records
        assertThat(csv).contains("Item #,Class ID,Confidence,Track ID,Type,X1,Y1,X2,Y2,SKU ID")
        assertThat(csv).contains("AUTO_DETECT")
        assertThat(csv).contains("MANUAL_ADD")
    }

    @Test
    fun generateJsonString_producesStandardizedJsonWithEnterpriseKeys() {
        val session = InventorySession(
            id = 202,
            timestamp = 1710000000000L,
            shelfLocation = "Beverages Bay 2",
            totalCount = 15,
            mode = "SAHI_2X2",
            operatorNotes = "Restocked cooler",
            barcode = "5449000000996",
            productName = "Coca-Cola 330ml Can",
            expectedStock = 12,
            unitPrice = 1.25,
            durationMs = 4500,
            rawBoxCount = 18,
            mergedBoxCount = 15,
            tileCount = 4
        )

        val records = listOf(
            ShelfScanRecord(
                id = 11,
                sessionId = 202,
                x1 = 0.05f,
                y1 = 0.10f,
                x2 = 0.25f,
                y2 = 0.35f,
                confidence = 0.88f,
                classId = 0,
                trackId = 5,
                isManual = false
            )
        )

        val jsonString = ExportManager.generateJsonString(session, records)
        val root = JSONObject(jsonString)

        assertThat(root.getString("schemaVersion")).isEqualTo("2.1")
        assertThat(root.getString("generator")).contains("ShelfPulse AI")

        // Session block
        val sessionObj = root.getJSONObject("session")
        assertThat(sessionObj.getLong("id")).isEqualTo(202L)
        assertThat(sessionObj.getString("shelfLocation")).isEqualTo("Beverages Bay 2")
        assertThat(sessionObj.getInt("frontFacings")).isEqualTo(15)
        assertThat(sessionObj.getInt("depthMultiplier")).isEqualTo(1)
        assertThat(sessionObj.getInt("totalCount")).isEqualTo(15)
        assertThat(sessionObj.getString("mode")).isEqualTo("SAHI_2X2")
        assertThat(sessionObj.getInt("tileCount")).isEqualTo(4)

        // SKU block
        val skuObj = root.getJSONObject("sku")
        assertThat(skuObj.getString("barcode")).isEqualTo("5449000000996")
        assertThat(skuObj.getString("productName")).isEqualTo("Coca-Cola 330ml Can")
        assertThat(skuObj.getInt("expectedStock")).isEqualTo(12)
        assertThat(skuObj.getInt("countedStock")).isEqualTo(15)
        assertThat(skuObj.getInt("variance")).isEqualTo(3) // 15 - 12 = +3
        assertThat(skuObj.getDouble("unitPrice")).isEqualTo(1.25)
        assertThat(skuObj.getDouble("totalValue")).isEqualTo(18.75) // 15 * 1.25

        // Metrics block
        val metricsObj = root.getJSONObject("metrics")
        assertThat(metricsObj.getInt("totalCount")).isEqualTo(15)
        assertThat(metricsObj.getInt("autoCount")).isEqualTo(1)
        assertThat(metricsObj.getInt("manualCount")).isEqualTo(0)

        // Detections array
        val detectionsArr = root.getJSONArray("detections")
        assertThat(detectionsArr.length()).isEqualTo(1)
        val firstDet = detectionsArr.getJSONObject(0)
        assertThat(firstDet.getDouble("confidence")).isWithin(0.001).of(0.88)
        assertThat(firstDet.getString("skuId")).isEqualTo("5449000000996")
    }

    @Test
    fun generateAllSessionsCsvString_prependsUtf8BomAndIncludesAllSessions() {
        val session1 = InventorySession(
            id = 1,
            timestamp = 1710000000000L,
            shelfLocation = "Aisle 1 - Dairy",
            totalCount = 30,
            depthMultiplier = 2,
            mode = "SNAP_VERIFY_SAHI_2X2",
            barcode = "1111111111111",
            productName = "Whole Milk 1L",
            expectedStock = 25,
            unitPrice = 1.50,
            syncStatus = "SYNCED"
        )
        val session2 = InventorySession(
            id = 2,
            timestamp = 1710001000000L,
            shelfLocation = "Aisle 2 - Bakery",
            totalCount = 10,
            depthMultiplier = 1,
            mode = "SINGLE_640",
            barcode = "2222222222222",
            productName = "Sourdough Bread",
            expectedStock = 12,
            unitPrice = 3.00,
            syncStatus = "LOCAL_ONLY"
        )

        val csv = ExportManager.generateAllSessionsCsvString(listOf(session1, session2))

        // 1. Verify UTF-8 BOM
        assertThat(csv.startsWith("\uFEFF")).isTrue()

        // 2. Verify Header
        assertThat(csv).contains("Session ID,Date,Shelf/Aisle,Barcode,Product Name,Front Facings,Depth,Total Units,Expected,Variance,Unit Price,Total Value,Sync Status")

        // 3. Verify Session 1 row and totals
        assertThat(csv).contains("1,")
        assertThat(csv).contains("\"Aisle 1 - Dairy\"")
        assertThat(csv).contains("1111111111111")
        assertThat(csv).contains("Whole Milk 1L")
        assertThat(csv).contains(",15,2,30,25,5,1.50,45.00,SYNCED")

        // 4. Verify Session 2 row and totals
        assertThat(csv).contains("2,")
        assertThat(csv).contains("\"Aisle 2 - Bakery\"")
        assertThat(csv).contains("2222222222222")
        assertThat(csv).contains("Sourdough Bread")
        assertThat(csv).contains(",10,1,10,12,-2,3.00,30.00,LOCAL_ONLY")
    }
}
