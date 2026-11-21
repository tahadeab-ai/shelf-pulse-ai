package com.shelfpulse.ai.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProductLookupResult(
    val barcode: String,
    val productName: String,
    val brand: String = "",
    val quantity: String = ""
)

object OpenFoodFactsClient {

    private const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
    private const val USER_AGENT = "ShelfPulseAI/2.1 (Android)"
    private const val TIMEOUT_MS = 5000

    fun parseProductJson(barcode: String, jsonString: String): Result<ProductLookupResult> {
        return try {
            val root = JSONObject(jsonString)
            val status = root.optInt("status", -1)
            if (status != 1) {
                return Result.failure(NoSuchElementException("Product not found on Open Food Facts (status: $status)"))
            }
            val productObj = root.optJSONObject("product")
                ?: return Result.failure(IllegalStateException("Response missing product object"))

            val name = productObj.optString("product_name", "").ifBlank {
                productObj.optString("product_name_en", "").ifBlank { "Unknown Product" }
            }
            val brand = productObj.optString("brands", "")
            val quantity = productObj.optString("quantity", "")

            Result.success(
                ProductLookupResult(
                    barcode = barcode,
                    productName = name,
                    brand = brand,
                    quantity = quantity
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun lookupProduct(barcode: String): Result<ProductLookupResult> = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Empty barcode"))
        }

        val urlString = "$BASE_URL/$trimmed.json?fields=product_name,brands,quantity"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(RuntimeException("HTTP $responseCode from Open Food Facts"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            parseProductJson(trimmed, responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
