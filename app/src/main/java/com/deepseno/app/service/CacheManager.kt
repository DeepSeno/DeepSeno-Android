package com.enmooy.deepseno.service

import com.enmooy.deepseno.data.local.dao.CacheDao
import com.enmooy.deepseno.data.local.entity.CachedBriefingEntity
import com.enmooy.deepseno.data.local.entity.CachedRecordingEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    private val cacheDao: CacheDao,
) {
    suspend fun syncOnConnect(apiClient: ApiClient) {
        val api = apiClient.api ?: return
        try {
            val recordings = api.getRecordings()
            recordings.take(30).forEach { rec ->
                cacheDao.upsertRecording(
                    CachedRecordingEntity(
                        recordingId = rec.id,
                        fileName = rec.fileName,
                        mediaType = rec.mediaType,
                        status = rec.status,
                        dateString = rec.recordedAt?.take(10) ?: "",
                        durationSeconds = rec.durationSeconds,
                    )
                )
            }
        } catch (_: Exception) {}

        try {
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            for (i in 0L..6L) {
                val date = today.minusDays(i).format(formatter)
                try {
                    val briefing = api.getBriefing(date)
                    cacheDao.upsertBriefing(
                        CachedBriefingEntity(
                            dateString = date,
                            summaryText = briefing.summary?.summaryText,
                        )
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    suspend fun clearOldCache() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        cacheDao.deleteOldRecordings(thirtyDaysAgo)
        cacheDao.deleteOldBriefings(thirtyDaysAgo)
    }
}
