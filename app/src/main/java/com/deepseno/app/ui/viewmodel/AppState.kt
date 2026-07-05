package com.enmooy.deepseno.ui.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmooy.deepseno.service.ApiClient
import com.enmooy.deepseno.service.AudioStreamer
import com.enmooy.deepseno.service.CacheManager
import com.enmooy.deepseno.service.CaptureQueue
import com.enmooy.deepseno.service.ConnectionManager
import com.enmooy.deepseno.service.ServerEvent
import com.enmooy.deepseno.service.SseClient
import com.enmooy.deepseno.service.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppState @Inject constructor(
    val apiClient: ApiClient,
    val webSocketManager: WebSocketManager,
    val connectionManager: ConnectionManager,
    val captureQueue: CaptureQueue,
    val cacheManager: CacheManager,
    val sseClient: SseClient,
    val audioStreamer: AudioStreamer,
    private val transcriptCorrector: com.enmooy.deepseno.service.TranscriptCorrector,
    private val notificationManager: com.enmooy.deepseno.service.TranscriptionNotificationManager,
    private val prefs: SharedPreferences,
) : ViewModel() {

    // ── Public State (read-only from ConnectionManager) ─────────

    /** True when connected via ANY path. */
    val isConnected: StateFlow<Boolean> = connectionManager.isConnected

    /** Current transport mode: "none" | "lan" | "relay" | "p2p" */
    val relayTransportMode: StateFlow<String> = connectionManager.transportMode

    /** True when network-connected (used by composite state flows). */
    val connectionActive: StateFlow<Boolean> = connectionManager.isConnected

    /** Host/port/token of the active connection (for display). */
    private val _connectionHost = MutableStateFlow<String?>(null)
    val connectionHost: StateFlow<String?> = _connectionHost

    private val _connectionPort = MutableStateFlow<Int?>(null)
    val connectionPort: StateFlow<Int?> = _connectionPort

    private val _connectionToken = MutableStateFlow<String?>(null)
    val connectionToken: StateFlow<String?> = _connectionToken

    private val _connectionSecure = MutableStateFlow(false)
    val connectionSecure: StateFlow<Boolean> = _connectionSecure

    private val _connectionFingerprint = MutableStateFlow<String?>(null)
    val connectionFingerprint: StateFlow<String?> = _connectionFingerprint

    /** true when a pairing (token) is saved in prefs. */
    private val _hasSavedPairing = MutableStateFlow(
        prefs.getString("deepseno_token", null) != null
    )
    val hasSavedPairing: StateFlow<Boolean> = _hasSavedPairing

    val pendingCount: Flow<Int> = captureQueue.pendingCount

    /** Chat prompt relay: set by Briefing, consumed by Chat. */
    private val _pendingChatPrompt = MutableStateFlow<String?>(null)
    val pendingChatPrompt: StateFlow<String?> = _pendingChatPrompt
    fun setPendingChatPrompt(prompt: String?) { _pendingChatPrompt.value = prompt }
    fun consumePendingChatPrompt(): String? {
        val p = _pendingChatPrompt.value; _pendingChatPrompt.value = null; return p
    }

    init {
        observeWebSocketEvents()
    }

    // ── Connection API (delegates to ConnectionManager) ─────────

    fun connectFromQR(jsonString: String): Boolean {
        return connectionManager.connectFromQR(jsonString)
    }

    fun connectWithSavedToken(host: String, port: Int) {
        val token = prefs.getString("deepseno_token", null) ?: return
        // Use the ConnectionManager's own internal logic
        connectionManager.connectFromQR("""{"host":"$host","port":$port,"token":"$token"}""")
    }

    fun disconnect() {
        connectionManager.disconnect()
        _connectionHost.value = null
        _connectionPort.value = null
        _connectionToken.value = null
        _connectionSecure.value = false
    }

    fun forget() {
        connectionManager.forget()
        disconnect()
        _hasSavedPairing.value = false
    }

    /** @deprecated Use connectionManager.connectFromQR() for unified path. */
    @Deprecated("Use connectFromQR")
    fun connect(
        host: String, port: Int, token: String,
        publicHost: String? = null, publicPort: Int? = null,
        fingerprint: String? = null,
        relayInfo: com.enmooy.deepseno.data.remote.model.RelayInfo? = null,
    ) {
        if (relayInfo != null && relayInfo.mid.isNotEmpty()) {
            connectionManager.connectFromQR(
                """{"host":"$host","port":$port,"token":"$token","relay":{"mid":"${relayInfo.mid}","pub":"${relayInfo.pub}","nonce":"${relayInfo.nonce}"}}"""
            )
        } else {
            connectionManager.connectFromQR("""{"host":"$host","port":$port,"token":"$token"}""")
        }
    }

    // ── WebSocket Events ───────────────────────────────────────

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            webSocketManager.lastEvent.collect { event ->
                if (event == null) return@collect
                audioStreamer.handleEvent(event)
                when (event) {
                    is ServerEvent.PipelineProgress -> {
                        if (event.step == "completed" || event.progress >= 1.0) {
                            notificationManager.sendTranscriptionComplete("Recording")
                        }
                    }
                    is ServerEvent.RecordingStatus -> {
                        if (event.status == "completed") {
                            notificationManager.sendTranscriptionComplete("Recording")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
