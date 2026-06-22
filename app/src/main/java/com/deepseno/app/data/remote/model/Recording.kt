package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Recording(
    val id: Int,
    @SerialName("file_path") val filePath: String = "",
    @SerialName("file_name") val fileName: String,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    val status: String = "",
    @SerialName("media_type") val mediaType: String = "audio",
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    @SerialName("speaker_count") val speakerCount: Int? = null,
    @SerialName("extracted_count") val extractedCount: Int? = null,
) {
    val mediaIcon: String get() = when (mediaType) {
        "audio" -> "mic"
        "video" -> "videocam"
        "document", "pdf" -> "description"
        "image" -> "image"
        "text" -> "text_snippet"
        else -> "insert_drive_file"
    }

    val formattedDuration: String? get() {
        val secs = durationSeconds ?: return null
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
