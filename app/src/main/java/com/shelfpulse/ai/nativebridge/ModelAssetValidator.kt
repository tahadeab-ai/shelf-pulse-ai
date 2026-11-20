package com.shelfpulse.ai.nativebridge

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class ModelStatus(
    val isReady: Boolean,
    val paramPath: String? = null,
    val binPath: String? = null,
    val errorMessage: String? = null
)

object ModelAssetValidator {

    private const val TAG = "ModelValidator"
    private const val ASSET_PARAM = "models/model.ncnn.param"
    private const val ASSET_BIN = "models/model.ncnn.bin"

    fun prepareModels(context: Context): ModelStatus {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val destParam = File(modelsDir, "model.ncnn.param")
        val destBin = File(modelsDir, "model.ncnn.bin")

        val prefs = context.getSharedPreferences("model_asset_prefs", Context.MODE_PRIVATE)
        val lastExtractedVersion = prefs.getInt("extracted_version_code", -1)
        val currentVersion = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) {
            0
        }
        if (currentVersion != lastExtractedVersion) {
            destParam.delete()
            destBin.delete()
        }

        // Check if assets exist
        val hasAssetParam = assetExists(context, ASSET_PARAM)
        val hasAssetBin = assetExists(context, ASSET_BIN)

        if (!hasAssetParam || !hasAssetBin) {
            // Check if already in filesDir (e.g. pushed via adb or previously extracted)
            if (destParam.exists() && destBin.exists() && destParam.length() > 0 && destBin.length() > 0) {
                return ModelStatus(
                    isReady = true,
                    paramPath = destParam.absolutePath,
                    binPath = destBin.absolutePath
                )
            }

            val msg = "Missing required model files in assets: " +
                    (if (!hasAssetParam) "[$ASSET_PARAM] " else "") +
                    (if (!hasAssetBin) "[$ASSET_BIN] " else "")
            Log.w(TAG, msg)
            return ModelStatus(isReady = false, errorMessage = msg)
        }

        try {
            // Copy param if newer or missing
            copyAssetIfNeeded(context, ASSET_PARAM, destParam)
            copyAssetIfNeeded(context, ASSET_BIN, destBin)
            prefs.edit().putInt("extracted_version_code", currentVersion).apply()

            return ModelStatus(
                isReady = true,
                paramPath = destParam.absolutePath,
                binPath = destBin.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model assets", e)
            return ModelStatus(
                isReady = false,
                errorMessage = "Exception copying model files: ${e.message}"
            )
        }
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, destFile: File) {
        if (destFile.exists() && destFile.length() > 0) {
            // In release builds, could check version or hash
            return
        }

        context.assets.open(assetPath).use { input: InputStream ->
            FileOutputStream(destFile).use { output: FileOutputStream ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        Log.i(TAG, "Extracted asset $assetPath to ${destFile.absolutePath} (${destFile.length()} bytes)")
    }
}
