package com.enmooy.deepseno.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures PCM audio via AudioRecord and streams it over WebSocket
 * for real-time transcription on the desktop backend.
 *
 * Runs alongside MediaRecorder — attempts concurrent mic access.
 * Gracefully disables streaming on devices that don't support it.
 *
 * WebSocket Protocol:
 *   Client → Server:
 *     {"type":"transcribe:start","sampleRate":16000,"channels":1,"format":"pcm_s16le"}
 *     [binary PCM Int16 LE frames, ~100ms per message]
 *     {"type":"transcribe:stop"}
 *   Server → Client:
 *     {"type":"transcribe:partial","text":"..."}
 *     {"type":"transcribe:final","text":"..."}
 */
@Singleton
class AudioStreamer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _liveText = MutableStateFlow("")
    val liveText: StateFlow<String> = _liveText

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var webSocketManager: WebSocketManager? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Send ~100ms of audio per message: 16000 samples/sec * 2 bytes * 0.1 sec = 3200 bytes
        private const val CHUNK_SIZE = 3200
    }

    fun start(webSocketManager: WebSocketManager) {
        if (_isStreaming.value) return
        if (!webSocketManager.isConnected.value) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        this.webSocketManager = webSocketManager
        _liveText.value = ""

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(CHUNK_SIZE * 2)

        val record = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )
        } catch (_: Exception) {
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        audioRecord = record

        try {
            record.startRecording()
        } catch (_: Exception) {
            record.release()
            audioRecord = null
            return
        }

        _isStreaming.value = true

        // Notify server
        webSocketManager.sendJson(buildJsonObject {
            put("type", "transcribe:start")
            put("sampleRate", SAMPLE_RATE)
            put("channels", 1)
            put("format", "pcm_s16le")
        })

        // Start capture loop
        captureJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(CHUNK_SIZE)
            while (isActive && _isStreaming.value) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val data = if (read == buffer.size) buffer else buffer.copyOf(read)
                    webSocketManager.sendBinary(data)
                } else if (read < 0) {
                    break
                }
            }
        }
    }

    fun stop() {
        if (_isStreaming.value) {
            webSocketManager?.sendJson(buildJsonObject {
                put("type", "transcribe:stop")
            })
        }

        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null

        _isStreaming.value = false
    }

    fun handleEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.TranscribePartial -> _liveText.value = event.text
            is ServerEvent.TranscribeFinal -> _liveText.value = event.text
            else -> {}
        }
    }
}
