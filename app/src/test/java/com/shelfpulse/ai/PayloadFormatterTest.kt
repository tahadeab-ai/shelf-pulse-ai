package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.sync.ConnectorPreset
import com.shelfpulse.ai.data.sync.PayloadFormatter
import com.shelfpulse.ai.domain.model.InventorySession
import org.json.JSONObject
import org.junit.Test

class PayloadFormatterTest {

    private val testSession = InventorySession(
        id = 301,
        timestamp = 1710000000000L,
        shelfLocation = "Aisle 4, Bay C",
        totalCount = 20,
        mode = "SNAP_VERIFY_SAHI",
        operatorNotes = "Routine shelf audit",
        durationMs = 285L,
        barcode = "7622210449283",
        productName = "Oreo Original Cookies 154g",
        expectedStock = 25,
        unitPrice = 1.99,
        syncStatus = "QUEUED"
    )

    @Test
    fun format_genericWebhook_generatesValidJsonWithAllFields() {
        val jsonStr = PayloadFormatter.format(testSession, ConnectorPreset.GENERIC_WEBHOOK)
        val root = JSONObject(jsonStr)

        assertThat(root.getString("schemaVersion")).isEqualTo("2.2")
        assertThat(root.getString("event")).isEqualTo("inventory.scanned")
        assertThat(root.getString("generator")).contains("ShelfPulse AI")
        assertThat(root.getLong("timestamp")).isEqualTo(1710000000000L)

        val sessionObj = root.getJSONObject("session")
        assertThat(sessionObj.getLong("id")).isEqualTo(301L)
        assertThat(sessionObj.getString("shelfLocation")).isEqualTo("Aisle 4, Bay C")
        assertThat(sessionObj.getInt("totalCount")).isEqualTo(20)
        assertThat(sessionObj.getString("mode")).isEqualTo("SNAP_VERIFY_SAHI")
        assertThat(sessionObj.getString("operatorNotes")).isEqualTo("Routine shelf audit")
        assertThat(sessionObj.getLong("durationMs")).isEqualTo(285L)

        val skuObj = root.getJSONObject("sku")
        assertThat(skuObj.getString("barcode")).isEqualTo("7622210449283")
        assertThat(skuObj.getString("productName")).isEqualTo("Oreo Original Cookies 154g")
        assertThat(skuObj.getInt("expectedStock")).isEqualTo(25)
        assertThat(skuObj.getInt("countedStock")).isEqualTo(20)
        assertThat(skuObj.getInt("variance")).isEqualTo(-5) // 20 - 25 = -5
        assertThat(skuObj.getDouble("unitPrice")).isEqualTo(1.99)
        assertThat(skuObj.getDouble("totalValue")).isWithin(0.01).of(39.80) // 20 * 1.99 = 39.80
    }

    @Test
    fun format_invenTree_generatesValidStockCountPayload() {
        val jsonStr = PayloadFormatter.format(testSession, ConnectorPreset.INVENTREE_REST)
        val root = JSONObject(jsonStr)

        assertThat(root.getString("barcode")).isEqualTo("7622210449283")
        assertThat(root.getString("sku")).isEqualTo("7622210449283")
        assertThat(root.getString("item_name")).isEqualTo("Oreo Original Cookies 154g")
        assertThat(root.getInt("quantity")).isEqualTo(20)
        assertThat(root.getString("location")).isEqualTo("Aisle 4, Bay C")
        assertThat(root.getString("notes")).contains("ShelfPulse")
        assertThat(root.getString("notes")).contains("Count: 20")
        assertThat(root.getString("notes")).contains("Variance: -5")
    }

    @Test
    fun format_erpNext_generatesValidStockReconciliationPayload() {
        val jsonStr = PayloadFormatter.format(testSession, ConnectorPreset.ERPNEXT_STOCK_RECONCILIATION)
        val root = JSONObject(jsonStr)

        assertThat(root.getString("doctype")).isEqualTo("Stock Reconciliation")
        assertThat(root.getString("purpose")).isEqualTo("Stock Reconciliation")
        assertThat(root.has("posting_date")).isTrue()

        val itemsArr = root.getJSONArray("items")
        assertThat(itemsArr.length()).isEqualTo(1)

        val firstItem = itemsArr.getJSONObject(0)
        assertThat(firstItem.getString("item_code")).isEqualTo("7622210449283")
        assertThat(firstItem.getString("item_name")).isEqualTo("Oreo Original Cookies 154g")
        assertThat(firstItem.getInt("qty")).isEqualTo(20)
        assertThat(firstItem.getDouble("valuation_rate")).isEqualTo(1.99)
        assertThat(firstItem.getString("warehouse")).isEqualTo("Aisle 4, Bay C")

        assertThat(root.getString("remarks")).contains("Session #301")
    }
}
