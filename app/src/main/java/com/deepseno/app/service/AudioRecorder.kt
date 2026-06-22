package com.enmooy.deepseno.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _bookmarks = MutableStateFlow<List<Long>>(emptyList())
    val bookmarks: StateFlow<List<Long>> = _bookmarks

    private var recorder: MediaRecorder? = null
    private var timer: Timer? = null
    private var startTime: Long = 0
    private var pausedDurationMs: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val _stopFailed = MutableStateFlow(false)
    /** True if the last stopRecording() raised an exception; file may be truncated. */
    val stopFailed: StateFlow<Boolean> = _stopFailed
    var currentFile: File? = null
        private set

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_isRecording.value && !_isPaused.value) {
                    Log.d(TAG, "Audio focus lost, pausing recording")
                    pauseRecording()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (_isRecording.value && _isPaused.value) {
                    Log.d(TAG, "Audio focus regained, resuming recording")
                    resumeRecording()
                }
            }
        }
    }

    fun startRecording(): File {
        // Request audio focus
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioManager.requestAudioFocus(focusReq)
        audioFocusRequest = focusReq

        // Acquire wake lock
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "deepseno:recording").apply {
            acquire(4 * 60 * 60 * 1000L) // 4h max
        }

        val fileName = "deepseno-${System.currentTimeMillis() / 1000}.m4a"
        val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(recordingsDir, fileName)
        currentFile = file

        recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        startTime = System.currentTimeMillis()
        pausedDurationMs = 0
        _isRecording.value = true
        _isPaused.value = false
        _durationMs.value = 0
        _bookmarks.value = emptyList()

        startTimer()
        return file
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        if (Build.VERSION.SDK_INT >= 24) {
            recorder?.pause()
        }
        timer?.cancel()
        timer = null
        pausedDurationMs = _durationMs.value
        _isPaused.value = true
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        if (Build.VERSION.SDK_INT >= 24) {
            recorder?.resume()
        }
        startTime = System.currentTimeMillis()
        _isPaused.value = false
        startTimer()
    }

    fun stopRecording(): File? {
        timer?.cancel()
        timer = null
        var failed = false
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // MediaRecorder.stop() throws IllegalStateException if the recording is
            // too short or the device killed it. File may be incomplete — signal up.
            Log.w(TAG, "MediaRecorder.stop failed (file likely truncated)", e)
            failed = true
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder.release failed", e)
        }
        recorder = null

        // Release wake lock
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release failed", e)
        }
        wakeLock = null

        // Release audio focus
        try {
            audioFocusRequest?.let {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.abandonAudioFocusRequest(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "abandonAudioFocus failed", e)
        }
        audioFocusRequest = null

        _isRecording.value = false
        _isPaused.value = false
        _durationMs.value = 0
        _audioLevel.value = 0f
        _stopFailed.value = failed
        return currentFile
    }

    /** Clear the stopFailed flag after the UI has consumed it. */
    fun consumeStopFailed() { _stopFailed.value = false }

    fun addBookmark() {
        if (!_isRecording.value) return
        _bookmarks.value = _bookmarks.value + _durationMs.value
    }

    val formattedDuration: String get() {
        val total = (_durationMs.value / 1000).toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    companion object {
        private const val TAG = "AudioRecorder"
    }

    private fun startTimer() {
        timer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    _durationMs.value = pausedDurationMs + (System.currentTimeMillis() - startTime)
                    // Read amplitude and normalize to 0..1
                    val maxAmplitude = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                    val level = if (maxAmplitude > 0) {
                        val db = 20 * log10(maxAmplitude.toDouble() / 32767.0)
                        max(0f, min(1f, ((db + 60) / 60).toFloat()))
                    } else 0f
                    _audioLevel.value = level
                }
            }, 50, 50)
        }
    }
}
