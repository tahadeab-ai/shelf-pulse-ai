package com.shelfpulse.ai.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.shelfpulse.ai.data.storage.SessionPhotoStore
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.domain.model.ShelfScanRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    fun generateCsvString(session: InventorySession, records: List<ShelfScanRecord>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateString = dateFormat.format(Date(session.timestamp))

        val sb = StringBuilder()
        // Prepend UTF-8 BOM for Microsoft Excel compatibility
        sb.append("\uFEFF")

        // Summary Header
        sb.append("Session ID,Timestamp,Shelf Location,Front Facings,Depth Multiplier,Total Count,Mode,Barcode,Product Name,Expected Stock,Variance,Unit Price,Total Value,Operator Notes\n")
        sb.append(escape(session.id.toString())).append(",")
        sb.append(escape(dateString)).append(",")
        sb.append(escape(session.shelfLocation)).append(",")
        sb.append(session.frontFacings).append(",")
        sb.append(session.depthMultiplier).append(",")
        sb.append(session.totalCount).append(",")
        sb.append(escape(session.mode)).append(",")
        sb.append(escape(session.barcode ?: "")).append(",")
        sb.append(escape(session.productName ?: "")).append(",")
        sb.append(session.expectedStock?.toString() ?: "").append(",")
        sb.append(session.variance?.toString() ?: "").append(",")
        sb.append(session.unitPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(",")
        sb.append(session.totalFinancialValue?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(",")
        sb.append(escape(session.operatorNotes)).append("\n\n")

        // Itemized Records
        sb.append("Item #,Class ID,Confidence,Track ID,Type,X1,Y1,X2,Y2,SKU ID\n")
        records.forEachIndexed { index, record ->
            sb.append(index + 1).append(",")
            sb.append(record.classId).append(",")
            sb.append(String.format(Locale.US, "%.3f", record.confidence)).append(",")
            sb.append(record.trackId).append(",")
            sb.append(if (record.isManual) "MANUAL_ADD" else "AUTO_DETECT").append(",")
            sb.append(String.format(Locale.US, "%.4f", record.x1)).append(",")
            sb.append(String.format(Locale.US, "%.4f", record.y1)).append(",")
            sb.append(String.format(Locale.US, "%.4f", record.x2)).append(",")
            sb.append(String.format(Locale.US, "%.4f", record.y2)).append(",")
            sb.append(escape(record.skuId ?: (session.barcode ?: ""))).append("\n")
        }

        return sb.toString()
    }

    fun generateAllSessionsCsvString(sessions: List<InventorySession>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        // Prepend UTF-8 BOM for Microsoft Excel compatibility
        sb.append("\uFEFF")

        // Consolidated Summary Header
        sb.append("Session ID,Date,Shelf/Aisle,Barcode,Product Name,Front Facings,Depth,Total Units,Expected,Variance,Unit Price,Total Value,Sync Status\n")

        sessions.forEach { s ->
            val dateString = dateFormat.format(Date(s.timestamp))
            sb.append(escape(s.id.toString())).append(",")
            sb.append(escape(dateString)).append(",")
            sb.append(escape(s.shelfLocation)).append(",")
            sb.append(escape(s.barcode ?: "")).append(",")
            sb.append(escape(s.productName ?: "")).append(",")
            sb.append(s.frontFacings).append(",")
            sb.append(s.depthMultiplier).append(",")
            sb.append(s.totalCount).append(",")
            sb.append(s.expectedStock?.toString() ?: "").append(",")
            sb.append(s.variance?.toString() ?: "").append(",")
            sb.append(s.unitPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(",")
            sb.append(s.totalFinancialValue?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(",")
            sb.append(escape(s.syncStatus)).append("\n")
        }

        return sb.toString()
    }

    fun generateJsonString(session: InventorySession, records: List<ShelfScanRecord>): String {
        val root = JSONObject()
        root.put("schemaVersion", "2.1")
        root.put("generator", "ShelfPulse AI Enterprise")

        val sessionObj = JSONObject()
        sessionObj.put("id", session.id)
        sessionObj.put("timestamp", session.timestamp)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sessionObj.put("dateIso", dateFormat.format(Date(session.timestamp)))
        sessionObj.put("shelfLocation", session.shelfLocation)
        sessionObj.put("frontFacings", session.frontFacings)
        sessionObj.put("depthMultiplier", session.depthMultiplier)
        sessionObj.put("totalCount", session.totalCount)
        sessionObj.put("mode", session.mode)
        sessionObj.put("operatorNotes", session.operatorNotes)
        sessionObj.put("durationMs", session.durationMs)
        sessionObj.put("rawBoxCount", session.rawBoxCount)
        sessionObj.put("mergedBoxCount", session.mergedBoxCount)
        sessionObj.put("tileCount", session.tileCount)
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

        val manualCount = records.count { it.isManual }
        val autoCount = records.size - manualCount
        val avgConf = if (records.isNotEmpty()) records.map { it.confidence }.average() else 0.0

        val metricsObj = JSONObject()
        metricsObj.put("totalCount", session.totalCount)
        metricsObj.put("manualCount", manualCount)
        metricsObj.put("autoCount", autoCount)
        metricsObj.put("avgConfidence", avgConf)
        root.put("metrics", metricsObj)

        val detsArray = JSONArray()
        records.forEachIndexed { idx, r ->
            val dObj = JSONObject()
            dObj.put("index", idx + 1)
            dObj.put("x1", r.x1)
            dObj.put("y1", r.y1)
            dObj.put("x2", r.x2)
            dObj.put("y2", r.y2)
            dObj.put("confidence", r.confidence)
            dObj.put("classId", r.classId)
            dObj.put("trackId", r.trackId)
            dObj.put("isManual", r.isManual)
            dObj.put("skuId", r.skuId ?: (session.barcode ?: JSONObject.NULL))
            detsArray.put(dObj)
        }
        root.put("detections", detsArray)

        return root.toString(2)
    }

    fun exportCsv(context: Context, session: InventorySession, records: List<ShelfScanRecord>): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "shelfpulse_session_${session.id}_${session.timestamp}.csv")
        val content = generateCsvString(session, records)

        FileOutputStream(file).use { out ->
            out.write(content.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportAllSessionsCsv(context: Context, sessions: List<InventorySession>): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val file = File(exportsDir, "shelfpulse_store_summary_$timestamp.csv")
        val content = generateAllSessionsCsvString(sessions)

        FileOutputStream(file).use { out ->
            out.write(content.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportJson(context: Context, session: InventorySession, records: List<ShelfScanRecord>): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "shelfpulse_session_${session.id}_${session.timestamp}.json")
        val content = generateJsonString(session, records)

        FileOutputStream(file).use { out ->
            out.write(content.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun createAnnotatedBitmap(
        baseBitmap: Bitmap,
        session: InventorySession,
        records: List<ShelfScanRecord>
    ): Bitmap {
        val workingBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(workingBitmap)
        val w = workingBitmap.width.toFloat()
        val h = workingBitmap.height.toFloat()

        val boxPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = (w * 0.003f).coerceIn(2.5f, 6f)
        }

        records.forEach { r ->
            val left = r.x1 * w
            val top = r.y1 * h
            val right = r.x2 * w
            val bottom = r.y2 * h

            boxPaint.color = if (r.isManual) 0xFF06B6D4.toInt() else 0xFF10B981.toInt()
            val bracketLen = minOf((right - left) / 3f, (bottom - top) / 3f).coerceAtLeast(10f)

            // Draw corner brackets
            canvas.drawLine(left, top, left + bracketLen, top, boxPaint)
            canvas.drawLine(left, top, left, top + bracketLen, boxPaint)

            canvas.drawLine(right, top, right - bracketLen, top, boxPaint)
            canvas.drawLine(right, top, right, top + bracketLen, boxPaint)

            canvas.drawLine(left, bottom, left + bracketLen, bottom, boxPaint)
            canvas.drawLine(left, bottom, left, bottom - bracketLen, boxPaint)

            canvas.drawLine(right, bottom, right - bracketLen, bottom, boxPaint)
            canvas.drawLine(right, bottom, right, bottom - bracketLen, boxPaint)
        }

        // Bottom Watermark Bar
        val barHeight = (h * 0.05f).coerceIn(40f, 90f)
        val barPaint = Paint().apply {
            color = 0xDD0F172A.toInt() // dark translucent slate
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, h - barHeight, w, h, barPaint)

        val textPaint = Paint().apply {
            color = 0xFFF1F5F9.toInt()
            isAntiAlias = true
            textSize = (barHeight * 0.40f).coerceIn(14f, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(session.timestamp))
        val skuStr = session.productName?.let { " • $it" } ?: ""
        val watermarkText = "ShelfPulse AI • ${session.shelfLocation} • Count: ${session.totalCount}$skuStr • $dateStr"
        canvas.drawText(watermarkText, 20f, h - (barHeight * 0.32f), textPaint)

        return workingBitmap
    }

    fun exportAnnotatedImage(
        context: Context,
        bitmap: Bitmap?,
        session: InventorySession,
        records: List<ShelfScanRecord>
    ): Uri {
        val baseBmp = bitmap ?: SessionPhotoStore.loadPhoto(session.photoPath)
        if (baseBmp == null) {
            return exportCsv(context, session, records)
        }
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "shelfpulse_session_${session.id}_proof.jpg")

        val workingBitmap = createAnnotatedBitmap(baseBmp, session, records)

        FileOutputStream(file).use { out ->
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportPdfReport(
        context: Context,
        session: InventorySession,
        records: List<ShelfScanRecord>,
        snapshotBitmap: Bitmap? = null
    ): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "shelfpulse_session_${session.id}_report.pdf")

        val baseBmp = snapshotBitmap ?: SessionPhotoStore.loadPhoto(session.photoPath)
        val imageToEmbed = if (baseBmp != null) {
            createAnnotatedBitmap(baseBmp, session, records)
        } else null

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 (595x842 pt)
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // Background
        val bgPaint = Paint().apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

        // Top Header Banner
        val headerPaint = Paint().apply { color = 0xFF0F172A.toInt() }
        canvas.drawRect(0f, 0f, 595f, 75f, headerPaint)

        val titlePaint = Paint().apply {
            color = 0xFF10B981.toInt()
            textSize = 20f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("ShelfPulse AI", 30f, 38f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            textSize = 10f
            isAntiAlias = true
        }
        canvas.drawText("Retail Inventory Audit & Visual Verification Report", 30f, 56f, subTitlePaint)

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(session.timestamp))
        val datePaint = Paint().apply {
            color = 0xFF94A3B8.toInt()
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateStr, 565f, 44f, datePaint)

        var curY = 105f
        val labelPaint = Paint().apply {
            color = 0xFF64748B.toInt()
            textSize = 9f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint().apply {
            color = 0xFF0F172A.toInt()
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Shelf & Session Info Box
        canvas.drawText("SHELF / AISLE LOCATION", 30f, curY, labelPaint)
        canvas.drawText(session.shelfLocation, 30f, curY + 16f, valuePaint)

        canvas.drawText("SCAN MODE & DEPTH", 220f, curY, labelPaint)
        val modeDepthText = if (session.depthMultiplier > 1) {
            "${session.mode} (Depth: ${session.depthMultiplier}×)"
        } else {
            session.mode
        }
        canvas.drawText(modeDepthText, 220f, curY + 16f, valuePaint)

        canvas.drawText("TOTAL COUNTED", 420f, curY, labelPaint)
        val countPaint = Paint().apply {
            color = 0xFF059669.toInt()
            textSize = 16f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val countText = if (session.depthMultiplier > 1) {
            "${session.totalCount} items (${session.frontFacings}×${session.depthMultiplier})"
        } else {
            "${session.totalCount} items"
        }
        canvas.drawText(countText, 420f, curY + 16f, countPaint)

        curY += 45f

        // SKU & Variance Box (Card)
        val cardPaint = Paint().apply { color = 0xFFF8FAFC.toInt() }
        val strokePaint = Paint().apply {
            color = 0xFFE2E8F0.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(30f, curY, 565f, curY + 70f, 8f, 8f, cardPaint)
        canvas.drawRoundRect(30f, curY, 565f, curY + 70f, 8f, 8f, strokePaint)

        val skuTitle = session.productName?.let { "$it (${session.barcode ?: "No Barcode"})" } ?: "No Barcode / SKU Linked"
        canvas.drawText("PRODUCT SKU / BARCODE", 45f, curY + 20f, labelPaint)
        canvas.drawText(skuTitle, 45f, curY + 36f, valuePaint)

        canvas.drawText("EXPECTED", 300f, curY + 20f, labelPaint)
        canvas.drawText("${session.expectedStock ?: "—"}", 300f, curY + 36f, valuePaint)

        canvas.drawText("VARIANCE", 390f, curY + 20f, labelPaint)
        val varianceVal = session.variance
        val varPaint = Paint().apply {
            color = when {
                varianceVal == null -> 0xFF64748B.toInt()
                varianceVal >= 0 -> 0xFF059669.toInt()
                else -> 0xFFDC2626.toInt()
            }
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val varText = when {
            varianceVal == null -> "—"
            varianceVal > 0 -> "+$varianceVal (Surplus)"
            varianceVal < 0 -> "$varianceVal (Shortage)"
            else -> "0 (Match)"
        }
        canvas.drawText(varText, 390f, curY + 36f, varPaint)

        canvas.drawText("UNIT PRICE / TOTAL VALUE", 45f, curY + 54f, labelPaint)
        val finText = if (session.unitPrice != null && session.totalFinancialValue != null) {
            String.format(Locale.US, "$%.2f / $%.2f", session.unitPrice, session.totalFinancialValue)
        } else {
            "—"
        }
        canvas.drawText(finText, 200f, curY + 54f, valuePaint)

        curY += 85f

        // Embedded Shelf Photo if present
        if (imageToEmbed != null) {
            val maxW = 535f
            val maxH = 280f
            val aspect = imageToEmbed.width.toFloat() / imageToEmbed.height.toFloat()
            val (destW, destH) = if (aspect >= maxW / maxH) {
                Pair(maxW, maxW / aspect)
            } else {
                Pair(maxH * aspect, maxH)
            }
            val startX = 30f + (maxW - destW) / 2f
            val srcRect = Rect(0, 0, imageToEmbed.width, imageToEmbed.height)
            val dstRect = RectF(startX, curY, startX + destW, curY + destH)

            canvas.drawRoundRect(dstRect, 6f, 6f, strokePaint)
            canvas.drawBitmap(imageToEmbed, srcRect, dstRect, null)
            curY += destH + 18f
        } else {
            curY += 15f
        }

        // Operator Notes
        if (session.operatorNotes.isNotBlank()) {
            canvas.drawText("OPERATOR NOTES", 30f, curY, labelPaint)
            canvas.drawText(session.operatorNotes, 30f, curY + 16f, valuePaint)
            curY += 35f
        }

        // Summary Breakdown Footer
        val autoCount = records.count { !it.isManual }
        val manualCount = records.count { it.isManual }
        val avgConf = if (records.isNotEmpty()) records.map { it.confidence }.average() else 0.0

        canvas.drawText(
            "Audit Breakdown: $autoCount AI Detections • $manualCount Manual Additions (+1) • Avg Confidence: ${(avgConf * 100).toInt()}%",
            30f,
            curY,
            labelPaint
        )

        // Footer Brand
        val footerPaint = Paint().apply {
            color = 0xFF94A3B8.toInt()
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated by ShelfPulse AI Enterprise • 100% Offline Edge Intelligence", 297f, 820f, footerPaint)

        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun escape(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
