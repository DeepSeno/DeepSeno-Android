package com.enmooy.deepseno.data.local.dao

import androidx.room.*
import com.enmooy.deepseno.data.local.entity.CaptureItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureItemDao {
    @Query("SELECT * FROM capture_items ORDER BY created_at DESC")
    fun getAll(): Flow<List<CaptureItemEntity>>

    @Query("SELECT * FROM capture_items WHERE status = 'pending' OR status = 'uploading' ORDER BY created_at ASC")
    suspend fun getPending(): List<CaptureItemEntity>

    @Query("SELECT COUNT(*) FROM capture_items WHERE status = 'pending' OR status = 'uploading'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM capture_items WHERE status = 'failed'")
    fun getFailedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CaptureItemEntity)

    @Update
    suspend fun update(item: CaptureItemEntity)

    @Delete
    suspend fun delete(item: CaptureItemEntity)

    @Query("UPDATE capture_items SET status = 'pending', retries = 0 WHERE status = 'failed'")
    suspend fun retryAllFailed()

    @Query("DELETE FROM capture_items")
    suspend fun deleteAll()

    /**
     * After an app crash mid-upload, items may be left in "uploading" status with
     * no corresponding active job. Flip them back to "pending" so the next
     * processQueue() pass picks them up.
     */
    @Query("UPDATE capture_items SET status = 'pending' WHERE status = 'uploading'")
    suspend fun resetStuckUploading()
}
