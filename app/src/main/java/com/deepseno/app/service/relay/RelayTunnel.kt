package com.enmooy.deepseno.service.relay

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

// ── Shared types (used by RelayWebRTC and other relay classes) ──

enum class RelayTransportMode { NONE, RELAY, P2P }

interface RelayRequestHandler {
    suspend fun handleRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): RelayResponse
}

data class RelayResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

data class RelayTunnelState(
    val status: String = "disconnected",
    val transportMode: RelayTransportMode = RelayTransportMode.NONE,
    val paired: Boolean = false,
)

// ── Phone-side relay tunnel ─────────────────────────────────────

/**
 * Phone-side WebSocket tunnel to the DeepSeno relay server.
 *
 * Connects to /relay/client-ws and serves two purposes:
 *   1. Presence — the server notifies the desktop when this phone is online/offline
 *   2. Proxy — encrypted API requests go through this WS instead of HTTP POST
 *
 * The tunnel is created after ECDH pairing. All payloads are AES-GCM encrypted;
 * the server only sees ciphertext.
 */
class RelayTunnel(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private var ws: WebSocket? = null
    private var intentionallyClosed = false
    private var wsUrl: String = ""
    private var machineId: String = ""

    /** CompletableDeferred for each pending outgoing proxy request. */
    private val pendingRequests = LinkedHashMap<String, CompletableDeferred<RelayProxyResponse>>()

    data class RelayProxyResponse(
        val status: Int,
        val frames: List<ByteArray>,
        val error: String? = null,
    )

    /** Auth token to send on connect (LAN mode only). Null for relay. */
    private var authToken: String? = null

    /** Resolved when WebSocket onOpen fires. Reset on reconnect. */
    private var connected = CompletableDeferred<Unit>()

    fun start(wsUrl: String, machineId: String) {
        start(wsUrl, machineId, null)
    }

    /** Start with auth token for LAN mode. */
    fun start(wsUrl: String, machineId: String, authToken: String?) {
        this.wsUrl = wsUrl
        this.machineId = machineId
        this.authToken = authToken
        this.intentionallyClosed = false
        connected = CompletableDeferred()
        connect()
    }

    fun stop() {
        intentionallyClosed = true
        connected.cancel()
        // Fail all pending requests
        pendingRequests.values.forEach { it.complete(RelayProxyResponse(0, emptyList(), "tunnel stopped")) }
        pendingRequests.clear()
        ws?.close(1000, "tunnel stopped")
        ws = null
    }

    val isConnected: Boolean get() = ws != null

    /**
     * Send an encrypted relay proxy request through the WebSocket and wait
     * for the server to forward the desktop's response.
     *
     * @param frames encrypted frames from RelayCrypto.encryptRequest()
     * @return decrypted response frames (pass to RelayCrypto.decryptResponse())
     */
    suspend fun sendProxyRequest(frames: List<ByteArray>): RelayProxyResponse = withContext(Dispatchers.IO) {
        // Wait for WebSocket to connect (10s timeout)
        kotlinx.coroutines.withTimeoutOrNull(10_000L) {
            connected.await()
        } ?: return@withContext RelayProxyResponse(0, emptyList(), "WS connect timeout")

        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<RelayProxyResponse>()

        synchronized(pendingRequests) {
            pendingRequests[id] = deferred
        }

        val frameB64 = frames.map { Base64.encodeToString(it, Base64.NO_WRAP) }
        val totalLen = frameB64.sumOf { it.length }
        Log.e(TAG, "sendProxy: ${frames.size} frames, ${totalLen}B b64, id=$id")
        val payload = buildJsonObject {
            put("type", "proxy-req")
            put("id", id)
            putJsonArray("frames") { frameB64.forEach { add(it) } }
        }

        send(payload.toString())

        // Wait for response (45s timeout matching server)
        kotlinx.coroutines.withTimeoutOrNull(45_000L) {
            deferred.await()
        } ?: RelayProxyResponse(0, emptyList(), "proxy timeout")
    }

    // ── WebSocket connection ─────────────────────────────────────

    private fun connect() {
        connected = CompletableDeferred()
        val builder = Request.Builder().url(wsUrl)
        if (machineId.isNotEmpty()) builder.addHeader("X-Machine-Id", machineId)
        val request = builder.build()

        ws = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected.complete(Unit)
                    authToken?.let { token ->
                        send("""{"type":"auth","token":"$token"}""")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        handleMessage(text)
                    } catch (e: Exception) {
                        Log.e(TAG, "Message handling error", e)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "Relay WS failure: ${t.message}")
                    if (!intentionallyClosed) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!intentionallyClosed) connect()
                        }, 5000)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!intentionallyClosed) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!intentionallyClosed) connect()
                        }, 5000)
                    }
                }
            })
    }

    // ── Message handling ─────────────────────────────────────────

    private fun handleMessage(raw: String) {
        val msg = try { json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return }
        val type = msg["type"]?.jsonPrimitive?.content ?: return

        when (type) {
            "ws-id" -> { /* server assigned ID, no-op */ }
            "proxy-resp" -> {
                val id = msg["id"]?.jsonPrimitive?.content ?: return
                val deferred: CompletableDeferred<RelayProxyResponse>
                synchronized(pendingRequests) {
                    deferred = pendingRequests.remove(id) ?: return
                }
                val error = msg["error"]?.jsonPrimitive?.contentOrNull
                if (error != null) {
                    deferred.complete(RelayProxyResponse(0, emptyList(), error))
                } else {
                    val status = msg["status"]?.jsonPrimitive?.int ?: 200
                    val frames = msg["frames"]?.jsonArray?.map {
                        Base64.decode(it.jsonPrimitive.content, Base64.NO_WRAP)
                    } ?: emptyList()
                    deferred.complete(RelayProxyResponse(status, frames))
                }
            }
            "signal" -> {
                // Forward to WebRTC handler in the future
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun send(text: String) {
        ws?.send(text)
    }

    companion object {
        private const val TAG = "RelayTunnel"
    }
}
