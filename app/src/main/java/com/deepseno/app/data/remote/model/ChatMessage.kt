package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessage(
    val id: Int,
    @SerialName("session_id") val sessionId: Int? = null,
    val role: String,
    val content: String,
    @SerialName("sources_json") val sourcesJson: String? = null,
    @SerialName("created_at") val createdAt: String = "",
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
}

data class StreamingMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val sources: List<Source> = emptyList(),
)

@Serializable
data class Source(
    @SerialName("segment_id") val segmentId: Int? = null,
    @SerialName("recording_id") val recordingId: Int? = null,
    val speaker: String? = null,
    val text: String? = null,
    val time: String? = null,
)
