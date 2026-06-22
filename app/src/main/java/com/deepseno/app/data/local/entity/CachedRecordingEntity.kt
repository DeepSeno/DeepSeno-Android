package com.enmooy.deepseno.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_recordings")
data class CachedRecordingEntity(
    @PrimaryKey @ColumnInfo(name = "recording_id") val recordingId: Int,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    val status: String,
    @ColumnInfo(name = "date_string") val dateString: String,
    val summary: String? = null,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
