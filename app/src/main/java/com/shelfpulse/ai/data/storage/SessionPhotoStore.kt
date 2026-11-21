package com.shelfpulse.ai.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object SessionPhotoStore {
    private const val TAG = "SessionPhotoStore"
    private const val DIR_NAME = "session_photos"
    private const val MAX_SIDE_PX = 1280
    private const val JPEG_QUALITY = 85

    fun getPhotoFile(context: Context, sessionId: Long): File {
        val dir = File(context.filesDir, DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        return File(dir, "session_${sessionId}.jpg")
    }

    fun savePhoto(context: Context, sessionId: Long, bitmap: Bitmap): String? {
        return try {
            val file = getPhotoFile(context, sessionId)
            val scaledBitmap = scaleBitmapDown(bitmap, MAX_SIDE_PX)
            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session photo for session $sessionId", e)
            null
        }
    }

    fun loadPhoto(photoPath: String?): Bitmap? {
        if (photoPath.isNullOrBlank()) return null
        val file = File(photoPath)
        if (!file.exists() || !file.canRead()) return null
        return try {
            BitmapFactory.decodeFile(photoPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session photo from $photoPath", e)
            null
        }
    }

    fun loadPhoto(context: Context, sessionId: Long): Bitmap? {
        val file = getPhotoFile(context, sessionId)
        if (file.exists()) {
            return loadPhoto(file.absolutePath)
        }
        return null
    }

    fun loadThumbnail(photoPath: String?, targetWidth: Int = 160, targetHeight: Int = 160): Bitmap? {
        if (photoPath.isNullOrBlank()) return null
        val file = File(photoPath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(photoPath, boundsOptions)

            var sampleSize = 1
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth > targetWidth || origHeight > targetHeight) {
                val halfWidth = origWidth / 2
                val halfHeight = origHeight / 2
                while ((halfWidth / sampleSize) >= targetWidth && (halfHeight / sampleSize) >= targetHeight) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(photoPath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnail from $photoPath", e)
            null
        }
    }

    fun deletePhoto(context: Context, sessionId: Long): Boolean {
        return try {
            val file = getPhotoFile(context, sessionId)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete photo for session $sessionId", e)
            false
        }
    }

    fun deletePhoto(photoPath: String?): Boolean {
        if (photoPath.isNullOrBlank()) return false
        return try {
            val file = File(photoPath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete photo at $photoPath", e)
            false
        }
    }

    private fun scaleBitmapDown(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxSide && h <= maxSide) {
            return src
        }
        val ratio = minOf(maxSide.toFloat() / w.toFloat(), maxSide.toFloat() / h.toFloat())
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }
}
