package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class Briefing(
    val summary: DailySummary? = null,
    val todos: List<ExtractedItem> = emptyList(),
    val items: List<ExtractedItem> = emptyList(),
)
