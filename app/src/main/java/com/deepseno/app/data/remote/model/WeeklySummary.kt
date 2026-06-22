package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeeklySummary(
    val id: Int? = null,
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    @SerialName("summary_json") val summaryJson: String? = null,
    /** ISO-8601 timestamp when the server generated this weekly digest. Optional. */
    @SerialName("generated_at") val generatedAt: String? = null,
)
