package com.enmooy.deepseno.service

import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.enmooy.deepseno.BuildConfig
import com.enmooy.deepseno.data.remote.model.ConnectionInfo
import com.enmooy.deepseno.data.remote.model.RelayInfo
import com.enmooy.deepseno.service.relay.RelayCrypto
import com.enmooy.deepseno.service.relay.RelayTunnel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for ALL remote communication.
 *
 * Everything that connects to or communicates with the desktop goes through
 * this class. No other class may call apiClient.configure(), webSocketManager.connect(),
 * or any other connection-related API directly.
 *
 * Connection paths (tried in order):
 *   1. LAN direct — same network, lowest latency
 *   2. Relay (encrypted) — different network, via server relay
 *   3. P2P (WebRTC) — NAT traversal, future
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val apiClient: ApiClient,
    private val webSocketManager: WebSocketManager,
    private val captureQueue: CaptureQueue,
    private val cacheManager: CacheManager,
    private val transcriptCorrector: TranscriptCorrector,
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val KEY_HOST = "deepseno_host"
        private const val KEY_PORT = "deepseno_port"
        private const val KEY_TOKEN = "deepseno_token"
    }

    private val serverBase: String get() = BuildConfig.RELAY_SERVER_BASE_URL

    // ── Public State ───────────────────────────────────────────

    /** True when connected via ANY path (LAN WebSocket, relay, or P2P). */
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    /** Current transport mode: "none" | "lan" | "relay" | "p2p" */
    private val _transportMode = MutableStateFlow("none")
    val transportMode: StateFlow<String> = _transportMode

    /** When `isConnected` goes false, a client can check this to determine the next action.
     *  It is normally "none" and only temporarily switches to a path identifier for one collection.
     */
    private val _connectionPath = MutableStateFlow<String?>(null)
    val connectionPath: StateFlow<String?> = _connectionPath

    // ── Internal State ─────────────────────────────────────────

    private var currentHost: String = ""
    private var currentPort: Int = 0
    private var currentToken: String = ""
    private var relayActive = false
    private var relayAesKey: ByteArray? = null
    private var relayTunnel: RelayTunnel? = null
    private var connectJob: Job? = null

    init {
        // Observe WebSocket state changes
        CoroutineScope(Dispatchers.Default).launch {
            webSocketManager.isConnected.collect { wsConnected ->
                updateConnectionState(wsConnected)
            }
        }

        // Restore saved pairing on startup
        val savedHost = prefs.getString(KEY_HOST, "") ?: ""
        val savedPort = prefs.getInt(KEY_PORT, 0)
        val savedToken = prefs.getString(KEY_TOKEN, null)
        if (savedToken != null && savedToken.isNotBlank() && savedHost.isNotBlank() && savedPort > 0) {
            currentHost = savedHost
            currentPort = savedPort
            currentToken = savedToken
            // Only auto-reconnect LAN (no relay — relay needs fresh QR scan)
            connectLan(savedHost, savedPort, savedToken)
        }
    }

    // ── Public API ─────────────────────────────────────────────

    /**
     * Connect to a desktop using the QR code contents.
     * Automatically chooses the best path: LAN first, relay if available.
     */
    fun connectFromQR(jsonString: String): Boolean {
        val raw = jsonString.trim()

        // Handle deepseno://pair?... URLs
        if (raw.startsWith("deepseno://pair?")) {
            return handlePairUrl(raw)
        }

        parsePairingLink(raw)?.let { info ->
            return connect(info)
        }

        return try {
            connect(json.decodeFromString<ConnectionInfo>(raw))
        } catch (e: Exception) {
            false
        }
    }

    /** Send any API request. Works regardless of connection mode. */
    fun getApi() = apiClient.api

    // ── Private ────────────────────────────────────────────────

    private fun connect(info: ConnectionInfo): Boolean {
        // Save token immediately
        prefs.edit()
            .putString(KEY_HOST, info.host)
            .putInt(KEY_PORT, info.port)
            .putString(KEY_TOKEN, info.token)
            .apply()
        currentToken = info.token
        currentHost = info.host
        currentPort = info.port

        // Cancel any in-progress connection attempt
        connectJob?.cancel()

        // Try LAN first (lower latency, no server dependency), fall back to relay
        connectJob = CoroutineScope(Dispatchers.IO).launch {
            val hasLan = info.host.isNotBlank() && info.port > 0
            val lanReachable = hasLan && probeLan(info.host, info.port)
            if (lanReachable) {
                Log.d(TAG, "LAN reachable → using LAN (${info.host}:${info.port})")
                withContext(Dispatchers.Main) {
                    connectLanDirect(info.host, info.port, info.token)
                }
            } else if (info.relay != null && info.relay.mid.isNotEmpty()) {
                Log.d(TAG, "LAN not reachable → falling back to relay")
                connectRelay(info.relay)
            } else {
                Log.e(TAG, "Neither LAN nor relay available")
            }
        }
        return true
    }

    private fun parsePairingLink(raw: String): ConnectionInfo? {
        val uri = try {
            Uri.parse(raw)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val normalizedPath = uri.path.orEmpty().trim('/')
        if (normalizedPath != "mobile/pair" && !normalizedPath.endsWith("/mobile/pair")) return null

        val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.getQueryParameter("host").orEmpty()
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
        val relay = buildRelayInfo(uri)

        if ((host.isBlank() || port <= 0) && relay == null) return null

        return ConnectionInfo(
            host = host,
            port = port,
            token = token,
            fingerprint = uri.getQueryParameter("fingerprint")?.takeIf { it.isNotBlank() },
            relay = relay,
        )
    }

    private fun handlePairUrl(url: String): Boolean {
        try {
            val relay = buildRelayInfo(Uri.parse(url)) ?: return false
            connectRelay(relay)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun buildRelayInfo(uri: Uri): RelayInfo? {
        val mid = firstQueryParameter(uri, "mid", "relay_mid", "relayMid") ?: return null
        val pub = firstQueryParameter(uri, "pub", "relay_pub", "relayPub") ?: return null
        val nonce = firstQueryParameter(uri, "nonce", "relay_nonce", "relayNonce") ?: return null
        if (mid.isBlank() || pub.isBlank() || nonce.isBlank()) return null
        return RelayInfo(mid = mid, pub = pub, nonce = nonce)
    }

    private fun firstQueryParameter(uri: Uri, vararg names: String): String? {
        for (name in names) {
            val value = uri.getQueryParameter(name)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun connectRelay(relay: RelayInfo) {
        connectJob?.cancel()
        connectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val (privKey, phonePubB64) = RelayCrypto.generateKeyPair()
                val aesKey = RelayCrypto.deriveSharedKey(privKey, relay.pub, relay.nonce)
                relayAesKey = aesKey
                // Store AES key for use across restarts (if needed)
                prefs.edit()
                    .putString("relay_aes_key", Base64.encodeToString(aesKey, Base64.NO_WRAP))
                    .putString("relay_machine_id", relay.mid)
                    .putString("relay_desktop_pub", relay.pub)
                    .putString("relay_phone_pub", phonePubB64)
                    .apply()

                // POST to /relay/pair
                val pairJson = org.json.JSONObject().apply {
                    put("machineId", relay.mid)
                    put("phonePubKey", phonePubB64)
                    put("nonce", relay.nonce)
                }.toString()
                val pairReq = Request.Builder()
                    .url("$serverBase/relay/pair")
                    .addHeader("X-Machine-Id", relay.mid)
                    .post(pairJson.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
                val pairResp = client.newCall(pairReq).execute()

                if (!pairResp.isSuccessful && pairResp.code != 202) {
                    return@launch
                }

                // Create relay WebSocket tunnel (handles presence + proxy)
                val wsUrl = serverBase
                    .replace("https://", "wss://")
                    .replace("http://", "ws://") + "/relay/client-ws?machine_id=${relay.mid}"
                val tunnelClient = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
                val tunnel = RelayTunnel(tunnelClient)
                tunnel.start(wsUrl, relay.mid)
                relayTunnel = tunnel

                // Configure relay mode — pass tunnel so proxy requests go through WS
                apiClient.configureRelay(serverBase, relay.mid, aesKey, currentToken, tunnel)
                relayActive = true
                _transportMode.value = "relay"
                _isConnected.value = true

                // Trigger queue processing + cache sync after relay connection
                CoroutineScope(Dispatchers.IO).launch {
                    captureQueue.processQueue(apiClient)
                    cacheManager.syncOnConnect(apiClient)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Relay connect failed", e)
            }
        }
    }

    private fun connectLan(host: String, port: Int, token: String) {
        connectJob?.cancel()
        currentHost = host; currentPort = port; currentToken = token

        connectJob = CoroutineScope(Dispatchers.IO).launch {
            // Quick LAN probe
            val lanReachable = probeLan(host, port)
            if (lanReachable) {
                withContext(Dispatchers.Main) {
                    connectLanDirect(host, port, token)
                }
            } else {
                // Don't try LAN WebSocket — it will time out. Wait for relay path.
            }
        }
    }

    private fun connectLanDirect(host: String, port: Int, token: String) {
        relayActive = false
        _isConnected.value = false
        _transportMode.value = "lan"

        // Unified WebSocket proxy protocol (same as relay, but with LAN-derived key)
        val aesKey = RelayCrypto.deriveLanKey(token)
        val wsUrl = "ws://$host:$port"
        val tunnel = RelayTunnel(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        )
        tunnel.start(wsUrl, "", token)  // LAN: no machineId, use token for auth
        relayTunnel = tunnel

        apiClient.configureLan(tunnel, aesKey, host, port, token)
        transcriptCorrector.configure(host, port, token, secure = false)
        webSocketManager.connect(host, port, token, secure = false)

        CoroutineScope(Dispatchers.IO).launch {
            captureQueue.processQueue(apiClient)
            cacheManager.syncOnConnect(apiClient)
        }
    }

    private suspend fun probeLan(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(3000L) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(3000, TimeUnit.MILLISECONDS)
                    .readTimeout(3000, TimeUnit.MILLISECONDS)
                    .build()
                val req = Request.Builder().url("http://$host:$port/api/ping").get().build()
                client.newCall(req).execute().use { true }
            } catch (_: Exception) { false }
        } ?: false
    }

    private fun updateConnectionState(wsConnected: Boolean) {
        _isConnected.value = wsConnected || relayActive
        if (wsConnected && !relayActive) {
            _transportMode.value = "lan"
        }
    }

    /** Expose WebSocket manager for audio streaming (LAN only). */
    fun getWebSocketManager() = webSocketManager

    /** Disconnect and clear all state. */
    fun disconnect() {
        connectJob?.cancel()
        relayTunnel?.stop()
        relayTunnel = null
        relayActive = false
        relayAesKey = null
        _isConnected.value = false
        _transportMode.value = "none"
        apiClient.clear()
        webSocketManager.disconnect()
        transcriptCorrector.clear()
    }

    /** Forget all saved pairings. */
    fun forget() {
        disconnect()
        prefs.edit()
            .remove(KEY_HOST).remove(KEY_PORT).remove(KEY_TOKEN)
            .remove("relay_aes_key").remove("relay_machine_id")
            .remove("relay_desktop_pub").remove("relay_phone_pub")
            .apply()
    }

    // JSON decoder for QR parsing
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private inline fun <reified T> String.decodeFromString(): T = json.decodeFromString(this)
}
