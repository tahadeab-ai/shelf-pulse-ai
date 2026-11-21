package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.sync.ConnectorPreset
import com.shelfpulse.ai.data.sync.HttpSyncConnector
import com.shelfpulse.ai.data.sync.SyncConfig
import com.shelfpulse.ai.domain.model.InventorySession
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class SyncIntegrationTest {

    private lateinit var server: HttpServer
    private lateinit var serverUrl: String
    private val connector = HttpSyncConnector()

    private val testSession = InventorySession(
        id = 501L,
        timestamp = 1710000000000L,
        shelfLocation = "Aisle 9 - Shelf C",
        totalCount = 180,
        depthMultiplier = 3,
        mode = "SNAP_VERIFY_SAHI_3X3",
        operatorNotes = "Routine replenishment check",
        barcode = "7622210449283",
        productName = "Oreo Original Cookies 154g",
        expectedStock = 150,
        unitPrice = 2.25,
        syncStatus = "LOCAL_ONLY"
    )

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.start()
        val port = server.address.port
        serverUrl = "http://127.0.0.1:$port"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun pushSession_genericWebhook_verifiesHeadersAndJsonSchema() = runBlocking {
        var receivedMethod = ""
        var receivedAuth = ""
        var receivedContentType = ""
        var receivedBody = ""

        server.createContext("/webhook") { exchange ->
            receivedMethod = exchange.requestMethod
            receivedAuth = exchange.requestHeaders.getFirst("Authorization") ?: ""
            receivedContentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
            receivedBody = exchange.requestBody.bufferedReader().readText()

            val response = "{\"status\":\"ok\",\"received\":true}"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.GENERIC_WEBHOOK,
            endpointUrl = "$serverUrl/webhook",
            bearerToken = "test-token-123"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isSuccess).isTrue()

        // 1. Verify HTTP method and headers
        assertThat(receivedMethod).isEqualTo("POST")
        assertThat(receivedAuth).isEqualTo("Bearer test-token-123")
        assertThat(receivedContentType).contains("application/json")

        // 2. Verify JSON body contents and schema keys
        val root = JSONObject(receivedBody)
        assertThat(root.getString("schemaVersion")).isEqualTo("2.2")
        assertThat(root.getString("event")).isEqualTo("inventory.scanned")

        val sessionObj = root.getJSONObject("session")
        assertThat(sessionObj.getLong("id")).isEqualTo(501L)
        assertThat(sessionObj.getInt("depthMultiplier")).isEqualTo(3)
        assertThat(sessionObj.getInt("totalCount")).isEqualTo(180)
        assertThat(sessionObj.getInt("frontFacings")).isEqualTo(60)

        val skuObj = root.getJSONObject("sku")
        assertThat(skuObj.getString("barcode")).isEqualTo("7622210449283")
        assertThat(skuObj.getInt("variance")).isEqualTo(30) // 180 - 150 = +30
        assertThat(skuObj.getDouble("totalValue")).isWithin(0.01).of(405.0) // 180 * 2.25
    }

    @Test
    fun pushSession_invenTree_verifiesHeadersAndPayload() = runBlocking {
        var receivedMethod = ""
        var receivedAuth = ""
        var receivedBody = ""

        server.createContext("/api/stock/count/") { exchange ->
            receivedMethod = exchange.requestMethod
            receivedAuth = exchange.requestHeaders.getFirst("Authorization") ?: ""
            receivedBody = exchange.requestBody.bufferedReader().readText()

            val response = "{\"pk\":1,\"quantity\":180}"
            exchange.sendResponseHeaders(201, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.INVENTREE_REST,
            endpointUrl = "$serverUrl/api/stock/count/",
            bearerToken = "inventree-api-key"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isSuccess).isTrue()

        assertThat(receivedMethod).isEqualTo("POST")
        assertThat(receivedAuth).isEqualTo("Bearer inventree-api-key")

        val root = JSONObject(receivedBody)
        assertThat(root.getString("barcode")).isEqualTo("7622210449283")
        assertThat(root.getInt("quantity")).isEqualTo(180)
        assertThat(root.getString("location")).isEqualTo("Aisle 9 - Shelf C")
        assertThat(root.getString("notes")).contains("60 Facings x 3 Depth")
        assertThat(root.getString("notes")).contains("Variance: 30")
    }

    @Test
    fun pushSession_erpNext_verifiesHeadersAndPayload() = runBlocking {
        var receivedMethod = ""
        var receivedAuth = ""
        var receivedBody = ""

        server.createContext("/api/resource/Stock%20Reconciliation") { exchange ->
            receivedMethod = exchange.requestMethod
            receivedAuth = exchange.requestHeaders.getFirst("Authorization") ?: ""
            receivedBody = exchange.requestBody.bufferedReader().readText()

            val response = "{\"data\":{\"name\":\"REC-001\"}}"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.ERPNEXT_STOCK_RECONCILIATION,
            endpointUrl = "$serverUrl/api/resource/Stock%20Reconciliation",
            bearerToken = "token api_key:api_secret"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isSuccess).isTrue()

        assertThat(receivedMethod).isEqualTo("POST")
        assertThat(receivedAuth).isEqualTo("Bearer token api_key:api_secret")

        val root = JSONObject(receivedBody)
        assertThat(root.getString("doctype")).isEqualTo("Stock Reconciliation")
        val items = root.getJSONArray("items")
        assertThat(items.length()).isEqualTo(1)
        val item0 = items.getJSONObject(0)
        assertThat(item0.getString("item_code")).isEqualTo("7622210449283")
        assertThat(item0.getInt("qty")).isEqualTo(180)
        assertThat(item0.getDouble("valuation_rate")).isEqualTo(2.25)
        assertThat(root.getString("remarks")).contains("180 units [60x3]")
    }

    @Test
    fun testConnection_succeedsOnHttp200() = runBlocking {
        var testPingReceived = false

        server.createContext("/ping") { exchange ->
            val body = exchange.requestBody.bufferedReader().readText()
            testPingReceived = body.contains("\"test\":true")
            val resp = "{\"pong\":true}"
            exchange.sendResponseHeaders(200, resp.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(resp.toByteArray()) }
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.GENERIC_WEBHOOK,
            endpointUrl = "$serverUrl/ping",
            bearerToken = "test-token-123"
        )

        val result = connector.testConnection(config)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).contains("Connected successfully (HTTP 200)")
        assertThat(testPingReceived).isTrue()
    }

    @Test
    fun pushSession_failsGracefullyOnHttp401Unauthorized() = runBlocking {
        server.createContext("/unauthorized") { exchange ->
            val errorResp = "{\"error\":\"Invalid bearer token\"}"
            exchange.sendResponseHeaders(401, errorResp.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(errorResp.toByteArray()) }
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.GENERIC_WEBHOOK,
            endpointUrl = "$serverUrl/unauthorized",
            bearerToken = "bad-token"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("401")
    }

    @Test
    fun pushSession_failsGracefullyOnHttp500ServerError() = runBlocking {
        server.createContext("/error") { exchange ->
            val errorResp = "{\"error\":\"Internal database down\"}"
            exchange.sendResponseHeaders(500, errorResp.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(errorResp.toByteArray()) }
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.GENERIC_WEBHOOK,
            endpointUrl = "$serverUrl/error",
            bearerToken = "valid-token"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("500")
    }

    @Test
    fun pushSession_failsGracefullyOnUnreachablePort() = runBlocking {
        // Choose an unallocated localhost port that immediately refuses connection
        val unreachableConfig = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.GENERIC_WEBHOOK,
            endpointUrl = "http://127.0.0.1:1/nonexistent",
            bearerToken = "valid-token"
        )

        val result = connector.pushSession(testSession, unreachableConfig)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun testConnection_failsGracefullyOnHttp401AndUnreachablePort() = runBlocking {
        server.createContext("/auth-fail") { exchange ->
            exchange.sendResponseHeaders(401, 0)
            exchange.responseBody.close()
        }

        val config401 = SyncConfig(
            enabled = true,
            endpointUrl = "$serverUrl/auth-fail",
            bearerToken = "wrong"
        )
        val result401 = connector.testConnection(config401)
        assertThat(result401.isFailure).isTrue()
        assertThat(result401.exceptionOrNull()?.message).contains("401")

        val configUnreachable = SyncConfig(
            enabled = true,
            endpointUrl = "http://127.0.0.1:1/ping"
        )
        val resultUnreachable = connector.testConnection(configUnreachable)
        assertThat(resultUnreachable.isFailure).isTrue()
    }
}
