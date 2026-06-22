package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Structured payload the server *may* put inside [WeeklySummary.summaryJson].
 * When decoding succeeds + has content, the briefing UI renders rich
 * Themes / People / KeyMoments cards. Otherwise we fall back to showing the
 * raw string (legacy behavior).
 */
@Serializable
data class WeeklySummaryStructured(
    val overview: String? = null,
    val themes: List<Theme>? = null,
    val people: List<Person>? = null,
    @SerialName("key_moments") val keyMoments: List<KeyMoment>? = null,
) {
    @Serializable
    data class Theme(
        val title: String,
        val summary: String? = null,
        @SerialName("recording_ids") val recordingIds: List<Int>? = null,
    )

    @Serializable
    data class Person(
        val name: String,
        @SerialName("mention_count") val mentionCount: Int? = null,
        @SerialName("recording_ids") val recordingIds: List<Int>? = null,
    )

    @Serializable
    data class KeyMoment(
        @SerialName("recording_id") val recordingId: Int,
        @SerialName("segment_id") val segmentId: Int? = null,
        val summary: String,
        @SerialName("recording_title") val recordingTitle: String? = null,
        val date: String? = null,
    )

    /** True when at least one structured section has data worth rendering. */
    val hasContent: Boolean
        get() = !themes.isNullOrEmpty() ||
            !people.isNullOrEmpty() ||
            !keyMoments.isNullOrEmpty() ||
            !overview.isNullOrBlank()

    companion object {
        private val parser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        /**
         * Try to decode a structured payload from a raw JSON string. Returns null on
         * decode failure or when the result has no usable content.
         */
        fun tryDecode(jsonString: String): WeeklySummaryStructured? {
            return try {
                val decoded = parser.decodeFromString<WeeklySummaryStructured>(jsonString)
                if (decoded.hasContent) decoded else null
            } catch (_: Throwable) {
                null
            }
        }
    }
}
