package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailySummary(
    val id: Int? = null,
    val date: String = "",
    @SerialName("summary_text") val summaryText: String? = null,
    @SerialName("timeline_json") val timelineJson: String? = null,
    @SerialName("key_events_json") val keyEventsJson: String? = null,
    /** ISO-8601 timestamp when the server generated this briefing. Optional —
     *  older servers don't return it; UI hides the timestamp row in that case. */
    @SerialName("generated_at") val generatedAt: String? = null,
)
