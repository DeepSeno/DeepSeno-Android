package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtractedItem(
    val id: Int,
    @SerialName("segment_id") val segmentId: Int? = null,
    val type: String,
    val content: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("related_person") val relatedPerson: String? = null,
    val status: String = "active",
    val priority: String? = null,
    val assignee: String? = null,
    // Source attribution — server may populate so briefing items can jump back
    // to the originating recording / segment timestamp. nil means we hide the
    // "view source" affordance.
    @SerialName("recording_id") val recordingId: Int? = null,
    @SerialName("recording_title") val recordingTitle: String? = null,
    @SerialName("segment_start_time") val segmentStartTime: Double? = null,
) {
    val isTodo: Boolean get() = type == "todo"
    val isCompleted: Boolean get() = status == "completed"
    /** True when we know which recording produced this item — UI shows a tap-to-source row. */
    val hasSource: Boolean get() = recordingId != null

    val typeIcon: String get() = when (type) {
        "todo" -> "check_circle"
        "meeting" -> "groups"
        "decision" -> "gavel"
        "contact" -> "person"
        "number" -> "tag"
        "memo" -> "note"
        else -> "info"
    }
}
