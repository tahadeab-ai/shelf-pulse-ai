package com.shelfpulse.ai.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface

object ImageImportHelper {
    private const val TAG = "ImageImportHelper"

    /**
     * Loads a high-resolution gallery photo from [uri], applying correct EXIF rotation
     * and strictly capping the longest dimension to [maxDimension] (default 2048px)
     * to ensure optimal SAHI 2x2 tiling performance with zero OOM risk.
     */
    fun loadCappedBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            // 1. Read EXIF Orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }

            // 2. Decode image bounds only
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW <= 0 || rawH <= 0) {
                Log.e(TAG, "Failed to decode bitmap bounds: w=$rawW, h=$rawH")
                return null
            }

            // 3. Compute inSampleSize power-of-2 sub-sampling
            var inSampleSize = 1
            val maxRawSide = maxOf(rawW, rawH)
            while ((maxRawSide / (inSampleSize * 2)) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Apply EXIF transformation matrix
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val orientedBitmap = if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(
                    sampledBitmap, 0, 0,
                    sampledBitmap.width, sampledBitmap.height,
                    matrix, true
                )
                if (rotated != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                rotated
            } else {
                sampledBitmap
            }

            // 5. Final dimension cap to maxDimension (2048px)
            val orientedMax = maxOf(orientedBitmap.width, orientedBitmap.height)
            if (orientedMax > maxDimension) {
                val scale = maxDimension.toFloat() / orientedMax
                val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
                val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
                val finalBitmap = Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
                if (finalBitmap != orientedBitmap) {
                    orientedBitmap.recycle()
                }
                Log.i(TAG, "Imported gallery photo capped: ${finalBitmap.width}x${finalBitmap.height} (from ${rawW}x${rawH})")
                finalBitmap
            } else {
                Log.i(TAG, "Imported gallery photo: ${orientedBitmap.width}x${orientedBitmap.height} (from ${rawW}x${rawH})")
                orientedBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing bitmap from uri: $uri", e)
            null
        }
    }
}
