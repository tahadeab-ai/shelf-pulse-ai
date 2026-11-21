package com.shelfpulse.ai.data.sync

import com.shelfpulse.ai.domain.model.InventorySession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PayloadFormatter {

    fun format(session: InventorySession, preset: ConnectorPreset): String {
        return when (preset) {
            ConnectorPreset.GENERIC_WEBHOOK -> formatGenericWebhook(session)
            ConnectorPreset.INVENTREE_REST -> formatInvenTree(session)
            ConnectorPreset.ERPNEXT_STOCK_RECONCILIATION -> formatErpNext(session)
        }
    }

    private fun formatGenericWebhook(session: InventorySession): String {
        val root = JSONObject()
        root.put("schemaVersion", "2.2")
        root.put("event", "inventory.scanned")
        root.put("generator", "ShelfPulse AI Enterprise")
        root.put("timestamp", session.timestamp)

        val sessionObj = JSONObject()
        sessionObj.put("id", session.id)
        sessionObj.put("shelfLocation", session.shelfLocation)
        sessionObj.put("frontFacings", session.frontFacings)
        sessionObj.put("depthMultiplier", session.depthMultiplier)
        sessionObj.put("totalCount", session.totalCount)
        sessionObj.put("mode", session.mode)
        sessionObj.put("operatorNotes", session.operatorNotes)
        sessionObj.put("durationMs", session.durationMs)
        root.put("session", sessionObj)

        val skuObj = JSONObject()
        skuObj.put("barcode", session.barcode ?: JSONObject.NULL)
        skuObj.put("productName", session.productName ?: JSONObject.NULL)
        skuObj.put("expectedStock", session.expectedStock ?: JSONObject.NULL)
        skuObj.put("countedStock", session.totalCount)
        skuObj.put("variance", session.variance ?: JSONObject.NULL)
        skuObj.put("unitPrice", session.unitPrice ?: JSONObject.NULL)
        skuObj.put("totalValue", session.totalFinancialValue ?: JSONObject.NULL)
        root.put("sku", skuObj)

        return root.toString(2)
    }

    private fun formatInvenTree(session: InventorySession): String {
        val root = JSONObject()
        val itemIdentifier = session.barcode?.ifBlank { null }
            ?: session.productName?.ifBlank { null }
            ?: "SKU-${session.id}"

        root.put("barcode", itemIdentifier)
        root.put("sku", itemIdentifier)
        root.put("item_name", session.productName ?: itemIdentifier)
        root.put("quantity", session.totalCount)
        root.put("location", session.shelfLocation.ifBlank { "Default Location" })

        val notesBuilder = StringBuilder()
        notesBuilder.append("ShelfPulse v2.3 Count: ").append(session.totalCount)
        if (session.depthMultiplier > 1) {
            notesBuilder.append(" (").append(session.frontFacings).append(" Facings x ").append(session.depthMultiplier).append(" Depth)")
        }
        session.variance?.let { v -> notesBuilder.append(" (Variance: ").append(v).append(")") }
        if (session.operatorNotes.isNotBlank()) {
            notesBuilder.append(" - ").append(session.operatorNotes)
        }
        root.put("notes", notesBuilder.toString())

        return root.toString(2)
    }

    private fun formatErpNext(session: InventorySession): String {
        val root = JSONObject()
        root.put("doctype", "Stock Reconciliation")
        root.put("purpose", "Stock Reconciliation")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        root.put("posting_date", dateFormat.format(Date(session.timestamp)))

        val itemCode = session.barcode?.ifBlank { null }
            ?: session.productName?.ifBlank { null }
            ?: "SKU-${session.id}"

        val itemsArray = JSONArray()
        val itemObj = JSONObject()
        itemObj.put("item_code", itemCode)
        itemObj.put("item_name", session.productName ?: itemCode)
        itemObj.put("qty", session.totalCount)
        itemObj.put("valuation_rate", session.unitPrice ?: 0.0)
        itemObj.put("warehouse", session.shelfLocation.ifBlank { "Stores - Default" })
        itemsArray.put(itemObj)

        root.put("items", itemsArray)
        root.put("remarks", "ShelfPulse v2.3 Scan (Session #${session.id} - ${session.shelfLocation})")

        return root.toString(2)
    }
}
