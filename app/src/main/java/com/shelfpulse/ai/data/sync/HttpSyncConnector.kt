package com.shelfpulse.ai.data.sync

import com.shelfpulse.ai.domain.model.InventorySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HttpSyncConnector : InventorySyncConnector {

    override suspend fun pushSession(session: InventorySession, config: SyncConfig): Result<Unit> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim()
        if (endpoint.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Sync endpoint URL is not configured"))
        }

        val jsonPayload = PayloadFormatter.format(session, config.preset)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "ShelfPulseAI/2.2 (Android)")
                if (config.bearerToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.bearerToken.trim()}")
                }
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(RuntimeException("HTTP $code: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun testConnection(config: SyncConfig): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = config.endpointUrl.trim()
        if (endpoint.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter an endpoint URL"))
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "ShelfPulseAI/2.2 (Android)")
                if (config.bearerToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.bearerToken.trim()}")
                }
            }

            val testPayload = "{\"test\":true,\"source\":\"ShelfPulseAI/2.2\",\"ping\":${System.currentTimeMillis()}}"
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(testPayload)
                writer.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                Result.success("Connected successfully (HTTP $code)")
            } else {
                Result.failure(RuntimeException("HTTP $code response from endpoint"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
