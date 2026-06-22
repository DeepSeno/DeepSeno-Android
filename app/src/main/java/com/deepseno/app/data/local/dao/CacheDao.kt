package com.enmooy.deepseno.data.local.dao

import androidx.room.*
import com.enmooy.deepseno.data.local.entity.CachedBriefingEntity
import com.enmooy.deepseno.data.local.entity.CachedRecordingEntity

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecording(recording: CachedRecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBriefing(briefing: CachedBriefingEntity)

    @Query("SELECT * FROM cached_recordings ORDER BY date_string DESC")
    suspend fun getAllRecordings(): List<CachedRecordingEntity>

    @Query("SELECT * FROM cached_briefings WHERE date_string = :date")
    suspend fun getBriefing(date: String): CachedBriefingEntity?

    @Query("DELETE FROM cached_recordings WHERE cached_at < :before")
    suspend fun deleteOldRecordings(before: Long)

    @Query("DELETE FROM cached_briefings WHERE cached_at < :before")
    suspend fun deleteOldBriefings(before: Long)
}
