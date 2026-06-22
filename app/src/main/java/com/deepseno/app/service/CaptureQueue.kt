package com.enmooy.deepseno.service

import android.util.Log
import com.enmooy.deepseno.data.local.dao.CaptureItemDao
import com.enmooy.deepseno.data.local.entity.CaptureItemEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CaptureQueue"

@Singleton
class CaptureQueue @Inject constructor(
    private val dao: CaptureItemDao,
) {
    val pendingCount: Flow<Int> = dao.getPendingCount()
    val failedCount: Flow<Int> = dao.getFailedCount()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val processMutex = Mutex()

    // Store current API client so add() can trigger auto-upload
    @Volatile private var currentApi: ApiClient? = null

    suspend fun add(type: String, localPath: String, fileName: String, textContent: String? = null, bookmarks: String? = null) {
        dao.insert(CaptureItemEntity(type = type, localPath = localPath, fileName = fileName, textContent = textContent, bookmarksJson = bookmarks))
        currentApi?.let { processQueue(it) }
    }

    suspend fun addGroup(type: String, localPaths: List<String>, fileNames: List<String>, groupName: String) {
        val pathsJson = Json.encodeToString(localPaths)
        dao.insert(
            CaptureItemEntity(
                type = type,
                localPath = localPaths.firstOrNull() ?: "",
                fileName = groupName,
                groupPaths = pathsJson,
                groupName = groupName,
            )
        )
    }

    /**
     * Run the upload loop on the caller's coroutine. Cancellation propagates naturally
     * via [currentCoroutineContext]. Multiple concurrent calls are serialized by
     * [processMutex] — second caller waits, doesn't double-process.
     */
    suspend fun processQueue(apiClient: ApiClient) = withContext(Dispatchers.IO) {
        currentApi = apiClient
        processMutex.withLock {
            runProcessLoop(apiClient)
        }
    }

    private suspend fun runProcessLoop(apiClient: ApiClient) {
        _isProcessing.value = true
        try {
            try { dao.resetStuckUploading() } catch (e: Exception) { Log.w(TAG, "resetStuckUploading failed", e) }
            val items = dao.getPending()
            for (item in items) {
                if (!currentCoroutineContext().isActive) return
                try {
                    safeUpdate(item.copy(status = "uploading"))
                    when {
                        item.type == "text" && item.textContent != null -> {
                            apiClient.api?.createNote(
                                com.enmooy.deepseno.data.remote.model.NoteRequest(item.textContent)
                            )
                        }
                        item.groupPaths != null && item.groupName != null -> {
                            val paths = Json.decodeFromString<List<String>>(item.groupPaths)
                            val files = paths.map { File(it) }
                            val fNames = paths.indices.map { String.format("%02d.jpg", it + 1) }
                            apiClient.uploadImages(files, fNames, item.groupName)
                        }
                        else -> {
                            val file = File(item.localPath)
                            if (!file.exists()) {
                                Log.w(TAG, "file missing, marking failed: ${item.localPath}")
                                safeUpdate(item.copy(status = "failed"))
                                continue
                            }
                            apiClient.upload(file, item.fileName, item.bookmarksJson)
                        }
                    }
                    safeDelete(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "upload failed (retries=${item.retries}, type=${item.type})", e)
                    val newRetries = item.retries + 1
                    safeUpdate(item.copy(
                        retries = newRetries,
                        status = if (newRetries >= 3) "failed" else "pending",
                    ))
                    val delay = minOf(2.0 * (1 shl newRetries), 10.0).toLong() * 1000
                    delay(delay)
                }
            }
            Log.e(TAG, "processLoop: DONE")
        } finally {
            _isProcessing.value = false
        }
    }

    private suspend fun safeUpdate(item: CaptureItemEntity) {
        try { dao.update(item) } catch (e: Exception) { Log.e(TAG, "dao.update failed", e) }
    }

    private suspend fun safeDelete(item: CaptureItemEntity) {
        try { dao.delete(item) } catch (e: Exception) { Log.e(TAG, "dao.delete failed", e) }
    }

    suspend fun retryAll() {
        dao.retryAllFailed()
        Log.e(TAG, "retryAll: reset failed→pending, new pending=${dao.getPendingCount()}")
    }

    /** Retry all failed AND trigger upload immediately if API is available. */
    suspend fun retryAndProcess() {
        dao.retryAllFailed()
        currentApi?.let { processQueue(it) }
    }
    suspend fun clearAll() { dao.deleteAll() }
    fun getItems(): Flow<List<CaptureItemEntity>> = dao.getAll()
}
