package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Segment(
    val id: Int,
    @SerialName("recording_id") val recordingId: Int,
    @SerialName("speaker_id") val speakerId: Int? = null,
    @SerialName("start_time") val startTime: Double? = null,
    @SerialName("end_time") val endTime: Double? = null,
    @SerialName("raw_text") val rawText: String? = null,
    @SerialName("clean_text") val cleanText: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("speaker_name") val speakerName: String? = null,
) {
    val displayText: String get() = cleanText ?: rawText ?: ""

    val formattedTime: String? get() {
        val t = startTime ?: return null
        val m = (t / 60).toInt()
        val s = (t % 60).toInt()
        return String.format("%d:%02d", m, s)
    }
}
