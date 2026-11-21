package com.shelfpulse.ai.data.barcode

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

object BarcodeScannerHelper {

    private val reader = BarcodeReader().apply {
        options = BarcodeReader.Options(
            formats = setOf(
                BarcodeReader.Format.EAN_13,
                BarcodeReader.Format.EAN_8,
                BarcodeReader.Format.UPC_A,
                BarcodeReader.Format.UPC_E,
                BarcodeReader.Format.CODE_128,
                BarcodeReader.Format.QR_CODE
            ),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true
        )
    }

    fun scanBitmap(bitmap: Bitmap): String? {
        return try {
            val results = reader.read(bitmap)
            results.firstOrNull()?.text
        } catch (t: Throwable) {
            null
        }
    }

    fun scanImage(imageProxy: ImageProxy): String? {
        return try {
            val results = reader.read(imageProxy)
            results.firstOrNull()?.text
        } catch (t: Throwable) {
            null
        }
    }
}
