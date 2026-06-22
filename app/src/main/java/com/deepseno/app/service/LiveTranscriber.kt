package com.enmooy.deepseno.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class CorrectionState { NONE, PENDING, STREAMING, DONE, FAILED }

/** A single recognized speech segment with start/end timestamps. */
data class TranscriptSegment(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    val timestamp: Long = 0,      // start ms into recording
    var endTimestamp: Long = 0,    // end ms into recording
    var isFinal: Boolean = false,
    var correctedText: String? = null,
    var correctionState: CorrectionState = CorrectionState.NONE,
) {
    /** Corrected text when it has visible (non-whitespace) content, else the
     *  raw ASR draft. Guards against a blank correction silently wiping the row. */
    val displayText: String get() = correctedText?.takeIf { it.isNotBlank() } ?: text

    val formattedTime: String
        get() {
            val startSec = timestamp / 1000
            val m = startSec / 60
            val s = startSec % 60
            return String.format("%d:%02d", m, s)
        }

    val formattedTimeRange: String
        get() {
            val startSec = timestamp / 1000
            val endSec = endTimestamp / 1000
            val m1 = startSec / 60; val s1 = startSec % 60
            val m2 = endSec / 60; val s2 = endSec % 60
            return String.format("%d:%02d – %d:%02d", m1, s1, m2, s2)
        }
}

/**
 * On-device speech recognition during recording.
 *
 * Architecture: VAD (voice activity detection) via audio level drives the lifecycle.
 * Each speech segment gets its own SpeechRecognizer session — no text splitting needed,
 * the recognizer's output maps 1:1 to the segment.
 */
@Singleton
class LiveTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences,
) {
    private val _segments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val segments: StateFlow<List<TranscriptSegment>> = _segments

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private var speechRecognizer: SpeechRecognizer? = null
    private var shouldBeActive = false
    private var audioRecorder: AudioRecorder? = null

    /** Fired when a segment finalizes (isFinal flips true). Args: (segmentId, locale).
     *  Set by CaptureViewModel; invoked synchronously from the VAD loop / stop(). */
    var onSegmentFinalized: ((String, String) -> Unit)? = null

    /** Locale tag for the correction request, resolved at start(). */
    @Volatile
    var activeLocale: String = "en-US"
        private set

    /** Language tag passed to Android ASR (EXTRA_LANGUAGE), resolved at start().
     *  null = let the recognizer use the system default (auto / multilingual). */
    @Volatile
    private var recognizerLanguage: String? = null

    // VAD state
    private var isSpeaking = false
    private var silenceStartMs: Long = 0
    private val silenceThreshold = 0.05f
    private val silenceDurationMs = 800L
    private var vadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Current segment's recognized text (updated by SpeechRecognizer callback)
    @Volatile
    private var currentSegmentText = ""

    fun start(recorder: AudioRecorder) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        this.audioRecorder = recorder
        shouldBeActive = true
        _segments.value = emptyList()
        currentSegmentText = ""
        isSpeaking = false
        silenceStartMs = 0
        _isActive.value = true
        // Resolve the transcription language from the user preference (iOS parity:
        // "" = auto, "zh-Hans", "en-US", "multilingual"). `activeLocale` is the tag
        // sent to the desktop corrector; `recognizerLanguage` is what Android ASR uses
        // (note the deliberate "zh-Hans" vs "zh-CN" split — distinct tag conventions).
        when (prefs.getString(TRANSCRIPTION_LOCALE_KEY, "") ?: "") {
            "zh-Hans" -> { activeLocale = "zh-Hans"; recognizerLanguage = "zh-CN" }
            "en-US" -> { activeLocale = "en-US"; recognizerLanguage = "en-US" }
            // Android can't run parallel zh+en recognizers like iOS; multilingual uses
            // the system recognizer (best effort) and tells the desktop corrector not to
            // force a single output language (locale = "multilingual").
            "multilingual" -> { activeLocale = "multilingual"; recognizerLanguage = null }
            else -> {
                val zh = Locale.getDefault().language.contains("zh")
                activeLocale = if (zh) "zh-Hans" else "en-US"
                recognizerLanguage = if (zh) "zh-CN" else null
            }
        }
        startVAD()
    }

    fun stop() {
        shouldBeActive = false
        vadJob?.cancel()
        vadJob = null
        endCurrentRecognition()
        audioRecorder = null
        _isActive.value = false

        // Finalize last segment
        val segs = _segments.value.toMutableList()
        val lastIndex = segs.lastIndex
        var finalizedId: String? = null
        var finalizedText = ""
        if (lastIndex >= 0 && !segs[lastIndex].isFinal) {
            segs[lastIndex] = segs[lastIndex].copy(isFinal = true)
            finalizedId = segs[lastIndex].id
            finalizedText = segs[lastIndex].text
        }
        _segments.value = segs.filter { it.text.isNotBlank() }
        if (finalizedId != null && finalizedText.isNotBlank()) {
            onSegmentFinalized?.invoke(finalizedId, activeLocale)
        }
    }

    /** Called by TranscriptCorrector to write corrected text + state into a segment.
     *  Runs on the same single-threaded Main scope as the VAD loop. Safe ONLY because
     *  the VAD loop body has no suspension points between its `_segments.value` read and
     *  write — do not add a suspend call inside that read/modify/write window, or this
     *  launch could interleave and clobber the correction (the segment already finalized,
     *  so nothing would re-trigger it). */
    fun applyCorrection(segmentId: String, correctedText: String?, state: CorrectionState) {
        scope.launch {
            val segs = _segments.value.toMutableList()
            val idx = segs.indexOfFirst { it.id == segmentId }
            if (idx < 0) return@launch
            segs[idx] = segs[idx].copy(correctedText = correctedText, correctionState = state)
            _segments.value = segs
        }
    }

    private fun startVAD() {
        vadJob?.cancel()
        vadJob = scope.launch {
            while (isActive) {
                delay(100)
                val recorder = audioRecorder ?: continue
                val nowMs = recorder.durationMs.value
                val level = recorder.audioLevel.value

                val segs = _segments.value.toMutableList()

                if (level >= silenceThreshold) {
                    // Speaking
                    if (!isSpeaking) {
                        isSpeaking = true
                        // Create new segment with its own recognition task
                        segs.add(TranscriptSegment(timestamp = nowMs, endTimestamp = nowMs))
                        _segments.value = segs
                        startRecognition()
                    }
                    silenceStartMs = nowMs
                    val lastIndex = segs.lastIndex
                    if (lastIndex >= 0 && !segs[lastIndex].isFinal) {
                        segs[lastIndex] = segs[lastIndex].copy(endTimestamp = nowMs)
                    }
                } else if (isSpeaking) {
                    val silentFor = nowMs - silenceStartMs
                    if (silentFor >= silenceDurationMs) {
                        isSpeaking = false
                        val lastIndex = segs.lastIndex
                        var finalizedId: String? = null
                        var finalizedText = ""
                        if (lastIndex >= 0 && !segs[lastIndex].isFinal) {
                            segs[lastIndex] = segs[lastIndex].copy(
                                endTimestamp = silenceStartMs,
                                isFinal = true,
                            )
                            finalizedId = segs[lastIndex].id
                            finalizedText = segs[lastIndex].text
                        }
                        endCurrentRecognition()
                        // Remove empty finalized segments
                        _segments.value = segs.filter { !it.isFinal || it.text.isNotBlank() }
                        if (finalizedId != null && finalizedText.isNotBlank()) {
                            onSegmentFinalized?.invoke(finalizedId, activeLocale)
                        }
                        continue
                    }
                }

                // Update current segment text from its dedicated recognizer
                val text = currentSegmentText
                val lastNonFinal = segs.indexOfLast { !it.isFinal }
                if (lastNonFinal >= 0 && text.isNotEmpty() && segs[lastNonFinal].text != text) {
                    segs[lastNonFinal] = segs[lastNonFinal].copy(text = text)
                }

                _segments.value = segs
            }
        }
    }

    /** Start a fresh recognition session for the current segment. */
    private fun startRecognition() {
        endCurrentRecognition()
        currentSegmentText = ""

        try {
            val recognizer = if (Build.VERSION.SDK_INT >= 31) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        currentSegmentText = matches[0]
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        currentSegmentText = matches[0]
                    }
                    // VAD controls lifecycle — don't auto-restart here.
                }

                override fun onError(error: Int) {
                    // VAD controls lifecycle — don't auto-restart.
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                recognizerLanguage?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            }
            recognizer.startListening(intent)
        } catch (_: Exception) {}
    }

    /** Tear down the current recognition session. */
    private fun endCurrentRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    companion object {
        /** Persisted live-transcription language preference (iOS parity).
         *  "" = auto, "zh-Hans", "en-US", "multilingual". */
        const val TRANSCRIPTION_LOCALE_KEY = "transcription_locale"
    }
}
