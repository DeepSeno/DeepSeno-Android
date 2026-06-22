package com.enmooy.deepseno.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.enmooy.deepseno.data.local.dao.CacheDao
import com.enmooy.deepseno.data.local.dao.CaptureItemDao
import com.enmooy.deepseno.data.local.entity.CachedBriefingEntity
import com.enmooy.deepseno.data.local.entity.CachedRecordingEntity
import com.enmooy.deepseno.data.local.entity.CaptureItemEntity

@Database(
    entities = [
        CaptureItemEntity::class,
        CachedRecordingEntity::class,
        CachedBriefingEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class DeepSenoDatabase : RoomDatabase() {
    abstract fun captureItemDao(): CaptureItemDao
    abstract fun cacheDao(): CacheDao
}
