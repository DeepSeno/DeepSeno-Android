package com.enmooy.deepseno.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "capture_items")
data class CaptureItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "text_content") val textContent: String? = null,
    @ColumnInfo(name = "group_paths") val groupPaths: String? = null,
    @ColumnInfo(name = "group_name") val groupName: String? = null,
    @ColumnInfo(name = "bookmarks_json") val bookmarksJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val retries: Int = 0,
    val status: String = "pending",
)
