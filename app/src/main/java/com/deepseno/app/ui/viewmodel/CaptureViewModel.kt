package com.enmooy.deepseno.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmooy.deepseno.service.AudioRecorder
import com.enmooy.deepseno.service.AudioStreamer
import com.enmooy.deepseno.service.CaptureQueue
import com.enmooy.deepseno.service.ConnectionManager
import com.enmooy.deepseno.service.LiveTranscriber
import com.enmooy.deepseno.service.RecordingForegroundService
import com.enmooy.deepseno.service.TranscriptCorrector
import com.enmooy.deepseno.service.WebSocketManager
import com.enmooy.deepseno.ui.util.Haptics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    val recorder: AudioRecorder,
    val streamer: AudioStreamer,
    val transcriber: LiveTranscriber,
    private val transcriptCorrector: TranscriptCorrector,
    private val captureQueue: CaptureQueue,
    private val connectionManager: ConnectionManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val showTextMemo = MutableStateFlow(false)
    val memoText = MutableStateFlow("")
    val showFilePicker = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    private val _bookmarkFeedback = MutableStateFlow(false)
    val bookmarkFeedback: StateFlow<Boolean> = _bookmarkFeedback

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    // Re-entrance guard for double-tap on the record button while startRecording()
    // is still in progress (permission prompt etc.).
    @Volatile private var isStartingRecording = false

    fun toggleRecording(savedLabel: String, recordingIncompleteMessage: String? = null) {
        if (recorder.isRecording.value) {
            val finalDuration = recorder.formattedDuration

            // Stop everything
            transcriber.stop()
            // Null AFTER stop() so the last force-finalized segment's hook still
            // reaches the corrector.
            transcriber.onSegmentFinalized = null
            streamer.stop()

            val file = recorder.stopRecording()
            RecordingForegroundService.stop(appContext)
            Haptics.medium(appContext)

            // Surface write/stop failure to the user instead of silently queueing
            // a possibly-truncated file.
            if (recorder.stopFailed.value) {
                errorMessage.value = recordingIncompleteMessage
                    ?: "Recording stopped abnormally — file may be incomplete"
                recorder.consumeStopFailed()
            }

            if (file != null && file.exists()) {
                val bookmarks = recorder.bookmarks.value
                val bookmarksJson = if (bookmarks.isNotEmpty()) Json.encodeToString(bookmarks) else null
                viewModelScope.launch {
                    captureQueue.add(
                        type = "audio",
                        localPath = file.absolutePath,
                        fileName = file.name,
                        bookmarks = bookmarksJson,
                    )
                }
                // Show toast
                showToast("$savedLabel · $finalDuration")
            }
        } else {
            if (isStartingRecording) return
            isStartingRecording = true
            try {
                recorder.startRecording()
                RecordingForegroundService.start(appContext)
                Haptics.medium(appContext)
                // Start live transcription (pass recorder for VAD)
                transcriber.start(recorder)
                // Route finalized segments to the corrector. enqueue() self-gates on
                // connection + the enabled pref, so we wire it unconditionally.
                transcriber.onSegmentFinalized = { id, locale ->
                    transcriptCorrector.enqueue(id, locale)
                }
                // Start desktop streaming if directly connected (LAN — not relay)
                if (connectionManager.isConnected.value && connectionManager.transportMode.value != "relay") {
                    streamer.start(connectionManager.getWebSocketManager())
                }
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to start recording"
            } finally {
                isStartingRecording = false
            }
        }
    }

    fun togglePause() {
        if (recorder.isPaused.value) {
            recorder.resumeRecording()
        } else {
            recorder.pauseRecording()
        }
    }

    fun addBookmark() {
        recorder.addBookmark()
        Haptics.light(appContext)
        _bookmarkFeedback.value = true
        viewModelScope.launch {
            delay(1500)
            _bookmarkFeedback.value = false
        }
    }

    fun submitMemo() {
        val text = memoText.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            captureQueue.add(
                type = "text",
                localPath = "",
                fileName = "memo-${System.currentTimeMillis() / 1000}.txt",
                textContent = text,
            )
        }
        memoText.value = ""
        showTextMemo.value = false
    }

    fun handleFileImport(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "import-${System.currentTimeMillis()}"
                val cacheFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Cannot open file")
                captureQueue.add(
                    type = "file",
                    localPath = cacheFile.absolutePath,
                    fileName = cacheFile.name,
                )
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Failed to import file"
            }
        }
    }

    fun handleMultiPhotos(paths: List<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch {
            if (paths.size == 1) {
                captureQueue.add(
                    type = "photo",
                    localPath = paths.first(),
                    fileName = File(paths.first()).name,
                )
            } else {
                val groupName = "photos-${System.currentTimeMillis() / 1000}"
                val fileNames = paths.indices.map { String.format("%02d.jpg", it + 1) }
                captureQueue.addGroup(
                    type = "photo",
                    localPaths = paths,
                    fileNames = fileNames,
                    groupName = groupName,
                )
            }
        }
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
        viewModelScope.launch {
            delay(3000)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }
}
