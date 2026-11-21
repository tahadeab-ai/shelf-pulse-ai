package com.shelfpulse.ai

import com.google.common.truth.Truth.assertThat
import com.shelfpulse.ai.data.sync.ConnectorPreset
import com.shelfpulse.ai.data.sync.HttpSyncConnector
import com.shelfpulse.ai.data.sync.SyncConfig
import com.shelfpulse.ai.domain.model.InventorySession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SyncIntegrationTest {

    private lateinit var server: LocalHttpServer
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

    class RecordedRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    )

    class HttpResponse(
        val statusCode: Int = 200,
        val body: String = "",
        val contentType: String = "application/json"
    )

    class LocalHttpServer : AutoCloseable {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = serverSocket.localPort
        val baseUrl: String = "http://127.0.0.1:$port"

        private val handlers = ConcurrentHashMap<String, (RecordedRequest) -> HttpResponse>()
        val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()

        private val workerThread = Thread {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    handleSocket(client)
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        fun registerHandler(path: String, handler: (RecordedRequest) -> HttpResponse) {
            handlers[path] = handler
        }

        private fun handleSocket(socket: Socket) {
            socket.use { s ->
                val input = s.getInputStream()
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                val headers = mutableMapOf<String, String>()
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line.substring(0, colonIdx).trim().lowercase()
                        val value = line.substring(colonIdx + 1).trim()
                        headers[key] = value
                        if (key == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                }

                val bodyChars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val body = String(bodyChars, 0, totalRead)
                val req = RecordedRequest(method, path, headers, body)
                recordedRequests.add(req)

                val handler = handlers[path] ?: { HttpResponse(404, "Not Found") }
                val resp = handler(req)

                val respBytes = resp.body.toByteArray(Charsets.UTF_8)
                val statusText = when (resp.statusCode) {
                    200 -> "OK"
                    201 -> "Created"
                    401 -> "Unauthorized"
                    404 -> "Not Found"
                    500 -> "Internal Server Error"
                    else -> "Status"
                }

                val headerStr = "HTTP/1.1 ${resp.statusCode} $statusText\r\n" +
                        "Content-Type: ${resp.contentType}\r\n" +
                        "Content-Length: ${respBytes.size}\r\n" +
                        "Connection: close\r\n\r\n"

                val output = s.getOutputStream()
                output.write(headerStr.toByteArray(Charsets.UTF_8))
                if (respBytes.isNotEmpty()) {
                    output.write(respBytes)
                }
                output.flush()
            }
        }

        override fun close() {
            try {
                serverSocket.close()
            } catch (_: Exception) {}
        }
    }

    @Before
    fun setUp() {
        server = LocalHttpServer()
        serverUrl = server.baseUrl
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun pushSession_genericWebhook_verifiesHeadersAndJsonSchema() = runBlocking {
        var recorded: RecordedRequest? = null
        server.registerHandler("/webhook") { req ->
            recorded = req
            HttpResponse(200, "{\"status\":\"ok\",\"received\":true}")
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
        val req = recorded!!
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.headers["authorization"]).isEqualTo("Bearer test-token-123")
        assertThat(req.headers["content-type"]).contains("application/json")

        // 2. Verify JSON body contents and schema keys
        val root = JSONObject(req.body)
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
        var recorded: RecordedRequest? = null
        server.registerHandler("/api/stock/count/") { req ->
            recorded = req
            HttpResponse(201, "{\"pk\":1,\"quantity\":180}")
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.INVENTREE_REST,
            endpointUrl = "$serverUrl/api/stock/count/",
            bearerToken = "inventree-api-key"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isSuccess).isTrue()

        val req = recorded!!
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.headers["authorization"]).isEqualTo("Bearer inventree-api-key")

        val root = JSONObject(req.body)
        assertThat(root.getString("barcode")).isEqualTo("7622210449283")
        assertThat(root.getInt("quantity")).isEqualTo(180)
        assertThat(root.getString("location")).isEqualTo("Aisle 9 - Shelf C")
        assertThat(root.getString("notes")).contains("60 Facings x 3 Depth")
        assertThat(root.getString("notes")).contains("Variance: 30")
    }

    @Test
    fun pushSession_erpNext_verifiesHeadersAndPayload() = runBlocking {
        var recorded: RecordedRequest? = null
        server.registerHandler("/api/resource/Stock%20Reconciliation") { req ->
            recorded = req
            HttpResponse(200, "{\"data\":{\"name\":\"REC-001\"}}")
        }

        val config = SyncConfig(
            enabled = true,
            preset = ConnectorPreset.ERPNEXT_STOCK_RECONCILIATION,
            endpointUrl = "$serverUrl/api/resource/Stock%20Reconciliation",
            bearerToken = "token api_key:api_secret"
        )

        val result = connector.pushSession(testSession, config)
        assertThat(result.isSuccess).isTrue()

        val req = recorded!!
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.headers["authorization"]).isEqualTo("Bearer token api_key:api_secret")

        val root = JSONObject(req.body)
        assertThat(root.getString("doctype")).isEqualTo("Stock Reconciliation")
        val items = root.getJSONArray("items")
        assertThat(items.length()).isEqualTo(1)
        val item0 = items.getJSONObject(0)
        assertThat(item0.getString("item_code")).isEqualTo("7622210449283")
        assertThat(item0.getInt("qty")).isEqualTo(180)
        assertThat(item0.getDouble("valuation_rate")).isEqualTo(2.25)
        assertThat(item0.getString("warehouse")).isEqualTo("Aisle 9 - Shelf C")
        assertThat(root.getString("remarks")).contains("ShelfPulse")
        assertThat(root.getString("remarks")).contains("Session #501")
    }

    @Test
    fun testConnection_succeedsOnHttp200() = runBlocking {
        var testPingReceived = false

        server.registerHandler("/ping") { req ->
            testPingReceived = req.body.contains("\"test\":true")
            HttpResponse(200, "{\"pong\":true}")
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
        server.registerHandler("/unauthorized") {
            HttpResponse(401, "{\"error\":\"Invalid bearer token\"}")
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
        server.registerHandler("/error") {
            HttpResponse(500, "{\"error\":\"Internal database down\"}")
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
        server.registerHandler("/auth-fail") {
            HttpResponse(401, "")
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
