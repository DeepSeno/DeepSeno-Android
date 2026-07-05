package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PingResponse(
    val name: String,
    val version: String,
    val platform: String,
)

@Serializable
data class UploadResponse(
    val success: Boolean,
    @SerialName("filePath") val filePath: String? = null,
    @SerialName("taskId") val taskId: String? = null,
    val count: Int? = null,
)

@Serializable
data class QueryResponse(
    val answer: String? = null,
    val sources: List<Source>? = null,
)

@Serializable
data class ConnectionInfo(
    val host: String,
    val port: Int,
    val token: String,
    val publicHost: String? = null,
    val publicPort: Int? = null,
    val fingerprint: String? = null,   // SPKI SHA-256 UPPER hex (colon-separated)
    val relay: RelayInfo? = null,      // Relay pairing info (unified QR)
)

@Serializable
data class RelayInfo(
    val mid: String = "",    // Machine ID
    val pub: String = "",    // Desktop's ECDH public key (base64)
    val nonce: String = "",  // Pairing nonce (base64)
)

@Serializable
data class SearchResult(
    val id: Int,
    @SerialName("recording_id") val recordingId: Int = 0,
    @SerialName("clean_text") val cleanText: String? = null,
    @SerialName("raw_text") val rawText: String? = null,
    @SerialName("speaker_name") val speakerName: String? = null,
    @SerialName("recording_name") val recordingName: String? = null,
    @SerialName("start_time") val startTime: Double? = null,
) {
    val displayText: String get() = cleanText ?: rawText ?: ""
}

@Serializable
data class ImageInfo(
    val count: Int,
    val images: List<String> = emptyList(),
)

@Serializable
data class StatusUpdate(val status: String)

@Serializable
data class QueryRequest(val question: String, val sessionId: Int? = null)

@Serializable
data class NoteRequest(val content: String)

@Serializable
data class CreateSessionRequest(val title: String? = null)
