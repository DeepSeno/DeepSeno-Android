package com.enmooy.deepseno.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeetingNotes(
    val title: String? = null,
    val participants: List<Participant>? = null,
    val decisions: List<String>? = null,
    @SerialName("action_items") val actionItems: List<ActionItem>? = null,
    @SerialName("discussion_summary") val discussionSummary: String? = null,
    @SerialName("key_topics") val keyTopics: List<String>? = null,
)

@Serializable
data class Participant(
    val name: String,
    @SerialName("speaking_time") val speakingTime: Double? = null,
)

@Serializable
data class ActionItem(
    val assignee: String? = null,
    val task: String,
    @SerialName("due_date") val dueDate: String? = null,
)
