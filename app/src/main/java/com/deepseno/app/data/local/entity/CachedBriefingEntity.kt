package com.enmooy.deepseno.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_briefings")
data class CachedBriefingEntity(
    @PrimaryKey @ColumnInfo(name = "date_string") val dateString: String,
    @ColumnInfo(name = "summary_text") val summaryText: String? = null,
    @ColumnInfo(name = "todos_json") val todosJson: String? = null,
    @ColumnInfo(name = "items_json") val itemsJson: String? = null,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
