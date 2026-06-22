package com.enmooy.deepseno.service

import android.util.Log
import com.enmooy.deepseno.data.remote.model.Recording
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WebSocketManager"

/**
 * Reconnect ceiling — after this many consecutive failures we stop trying.
 * Prevents battery drain when the server is down for an extended period.
 * User can force a fresh attempt by tapping Connect in Settings.
 */
private const val MAX_RECONNECT_ATTEMPTS = 10

sealed class ServerEvent {
    data class Connected(val serverVersion: String) : ServerEvent()
    data class RecordingNew(val recording: Recording) : ServerEvent()
    data class RecordingStatus(val recordingId: Int, val status: String, val progress: Double?) : ServerEvent()
    data class PipelineProgress(val taskId: String, val step: String, val progress: Double) : ServerEvent()
    data class TranscribePartial(val text: String) : ServerEvent()
    data class TranscribeFinal(val text: String) : ServerEvent()
}

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _lastEvent = MutableStateFlow<ServerEvent?>(null)
    val lastEvent: StateFlow<ServerEvent?> = _lastEvent

    /**
     * Invoked once when the reconnect budget is exhausted on a non-secure (LAN)
     * target. AppState uses this to re-probe and fall back to the pinned public
     * endpoint. No-op by default.
     */
    var onReconnectExhausted: (() -> Unit)? = null

    private var webSocket: WebSocket? = null
    private var shouldReconnect = false
    private var reconnectDelay = 1000L
    private var reconnectAttempts = 0
    private var host = ""
    private var port = 0
    private var token = ""
    private var secure = false
    private var fingerprint: String? = null
    private var reconnectJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(
        host: String,
        port: Int,
        token: String,
        secure: Boolean = false,
        fingerprint: String? = null,
    ) {
        // Close existing connection first to prevent leak
        closeExisting()
        this.host = host
        this.port = port
        this.token = token
        this.secure = secure
        this.fingerprint = fingerprint
        shouldReconnect = true
        reconnectDelay = 1000L
        reconnectAttempts = 0
        doConnect()
    }

    fun sendJson(payload: JsonObject) {
        val text = json.encodeToString(JsonObject.serializer(), payload)
        webSocket?.send(text)
    }

    fun sendBinary(data: ByteArray) {
        webSocket?.send(okio.ByteString.of(*data))
    }

    fun disconnect() {
        shouldReconnect = false
        closeExisting()
    }

    /** Close existing WebSocket without affecting reconnect flag. */
    private fun closeExisting() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
    }

    private fun doConnect() {
        // Cancel previous socket without triggering reconnect loop
        reconnectJob?.cancel()
        webSocket?.close(1000, null)
        webSocket = null

        val scheme = if (secure) "wss" else "ws"
        val request = Request.Builder().url("$scheme://$host:$port").build()
        // Public endpoint: pin the desktop's self-signed cert (hostname verification
        // bypassed since we hit a VPS IP). LAN keeps the shared plain-HTTP client.
        val client = if (secure && fingerprint != null) {
            TlsPinning.pinnedClient(okHttpClient, host, fingerprint!!)
        } else {
            okHttpClient
        }
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val authPayload = json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("type", "auth")
                        put("token", token)
                    }
                )
                webSocket.send(authPayload)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                if (ws !== this@WebSocketManager.webSocket) return
                _isConnected.value = false
                // 4xxx range = application-defined auth/protocol errors. Reconnecting
                // won't help (token bad / version mismatch) — stop and let user fix.
                if (code in 4000..4999) {
                    Log.w(TAG, "Server closed with auth/protocol code=$code reason=$reason — stopping reconnect")
                    shouldReconnect = false
                    return
                }
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure", t)
                if (ws !== this@WebSocketManager.webSocket) return
                _isConnected.value = false
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return

            when (type) {
                "connected" -> {
                    _isConnected.value = true
                    reconnectDelay = 1000L
                    reconnectAttempts = 0
                    val ver = obj["serverVersion"]?.jsonPrimitive?.contentOrNull ?: ""
                    _lastEvent.value = ServerEvent.Connected(ver)
                }
                "ping" -> webSocket?.send("""{"type":"pong"}""")
                "recording:new" -> {
                    val recObj = obj["recording"] ?: return
                    val recording = json.decodeFromJsonElement<Recording>(recObj)
                    _lastEvent.value = ServerEvent.RecordingNew(recording)
                }
                "recording:status" -> {
                    val rid = obj["recordingId"]?.jsonPrimitive?.intOrNull ?: return
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: return
                    val progress = obj["progress"]?.jsonPrimitive?.doubleOrNull
                    _lastEvent.value = ServerEvent.RecordingStatus(rid, status, progress)
                }
                "transcribe:partial" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return
                    _lastEvent.value = ServerEvent.TranscribePartial(text)
                }
                "transcribe:final" -> {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return
                    _lastEvent.value = ServerEvent.TranscribeFinal(text)
                }
                "pipeline:progress" -> {
                    val taskId = obj["taskId"]?.jsonPrimitive?.contentOrNull ?: return
                    val step = obj["step"]?.jsonPrimitive?.contentOrNull ?: return
                    val progress = obj["progress"]?.jsonPrimitive?.doubleOrNull ?: return
                    _lastEvent.value = ServerEvent.PipelineProgress(taskId, step, progress)
                }
            }
        } catch (e: Exception) {
            // Log instead of silently swallow — malformed server messages otherwise
            // disappear with no breadcrumb for prod debugging.
            Log.w(TAG, "handleMessage parse failed (len=${text.length})", e)
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Reached max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) — giving up")
            shouldReconnect = false
            // On the LAN (insecure) target, let AppState try the public fallback.
            // On the public target there's nowhere further to fall back to.
            if (!secure) onReconnectExhausted?.invoke()
            return
        }
        reconnectAttempts += 1
        // Exponential backoff with ±25% jitter — avoids thundering-herd when many
        // clients reconnect simultaneously after a server restart.
        val base = reconnectDelay
        val jitter = Random.nextLong(-base / 4, base / 4)
        val delayMs = (base + jitter).coerceAtLeast(500L)
        reconnectDelay = minOf(reconnectDelay * 2, 30000L)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (shouldReconnect && isActive) doConnect()
        }
    }

    /**
     * Permanently tear down: stop reconnects, close socket, cancel coroutines.
     * Call from application shutdown or test cleanup.
     */
    fun shutdown() {
        shouldReconnect = false
        closeExisting()
        scope.cancel()
    }
}
