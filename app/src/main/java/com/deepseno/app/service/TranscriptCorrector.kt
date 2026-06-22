package com.enmooy.deepseno.service

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates LLM correction of finalized transcript segments.
 *
 * Configured with the desktop connection by AppState.connect()/disconnect().
 * Wired to LiveTranscriber.onSegmentFinalized by CaptureViewModel while recording.
 * In-flight corrections are intentionally NOT cancelled on recording stop —
 * they finish so the transcript reflects late-arriving corrections.
 */
@Singleton
class TranscriptCorrector @Inject constructor(
    private val transcriber: LiveTranscriber,
    private val client: TranscriptCorrectionClient,
    private val prefs: SharedPreferences,
) {
    @Volatile private var host: String? = null
    @Volatile private var port: Int? = null
    @Volatile private var token: String? = null
    @Volatile private var secure: Boolean = false
    @Volatile private var fingerprint: String? = null

    // segmentIds with a correction in flight. add() on Main (enqueue),
    // remove() on IO (finalize) — needs a concurrent set.
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val contextWindowSize = 3      // preceding finalized segments sent as LLM context
    private val minLength = 4              // skip tiny fragments — LLM over-corrects them
    private val maxLengthMultiplier = 3    // reject corrections >3x raw — almost always hallucination

    // Cached so enqueue (Main thread, per finalized segment) never touches disk.
    // Kept in sync by a SharedPreferences listener.
    @Volatile private var enabled = prefs.getBoolean(CORRECTION_ENABLED_KEY, true)
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == CORRECTION_ENABLED_KEY) enabled = p.getBoolean(CORRECTION_ENABLED_KEY, true)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun configure(
        host: String,
        port: Int,
        token: String,
        secure: Boolean = false,
        fingerprint: String? = null,
    ) {
        this.host = host
        this.port = port
        this.token = token
        this.secure = secure
        this.fingerprint = fingerprint
    }

    fun clear() {
        host = null
        port = null
        token = null
        secure = false
        fingerprint = null
        inFlight.clear()
    }

    /** Called from LiveTranscriber.onSegmentFinalized (Main thread). Idempotent. */
    fun enqueue(segmentId: String, locale: String) {
        val h = host ?: return
        val p = port ?: return
        val t = token ?: return
        if (!enabled) return

        val all = transcriber.segments.value
        val idx = all.indexOfFirst { it.id == segmentId }
        if (idx < 0) return
        val segment = all[idx]
        if (segment.correctionState != CorrectionState.NONE) return
        if (!shouldCorrect(segment.text, minLength)) return
        if (!inFlight.add(segmentId)) return  // already running

        val context = all.subList(0, idx)
            .filter { it.isFinal }
            .takeLast(contextWindowSize)
            .map { it.displayText }
        val rawText = segment.text
        val sec = secure
        val fp = fingerprint

        transcriber.applyCorrection(segmentId, null, CorrectionState.PENDING)

        scope.launch {
            val builder = StringBuilder()
            try {
                client.stream(
                    h, p, t, segmentId, rawText, locale, context,
                    secure = sec, fingerprint = fp,
                ) { chunk ->
                    builder.append(chunk)
                    val acc = builder.toString()
                    // SSE token streams often lead with whitespace/newlines —
                    // don't let that blank out the raw text the user is reading.
                    // Pass null until the accumulation has visible content.
                    transcriber.applyCorrection(
                        segmentId,
                        if (acc.isNotBlank()) acc else null,
                        CorrectionState.STREAMING,
                    )
                }
                settle(segmentId, rawText, builder.toString(), success = true)
            } catch (_: Exception) {
                settle(segmentId, rawText, builder.toString(), success = false)
            }
        }
    }

    // The STREAMING chunk writes above and this terminal write both go through
    // LiveTranscriber.applyCorrection, which serializes them on the transcriber's
    // single-threaded Main scope — so DONE/FAILED reliably lands after the last chunk.
    private fun settle(segmentId: String, rawText: String, finalText: String, success: Boolean) {
        inFlight.remove(segmentId)
        // isNotBlank (not isNotEmpty): a whitespace-only result must never replace
        // the raw text — otherwise an "optimized" empty correction blanks the line.
        if (success && finalText.isNotBlank() && isLengthSane(rawText, finalText, maxLengthMultiplier)) {
            transcriber.applyCorrection(segmentId, finalText, CorrectionState.DONE)
        } else {
            // Hallucination / network / empty / whitespace-only — fall back to raw text silently.
            transcriber.applyCorrection(segmentId, null, CorrectionState.FAILED)
        }
    }

    companion object {
        const val CORRECTION_ENABLED_KEY = "transcription_correction_enabled"

        /** Pure: is this segment a candidate for correction? */
        fun shouldCorrect(text: String, minLength: Int): Boolean =
            text.trim().length >= minLength

        /** Pure: reject corrections whose length blows up vs the input. */
        fun isLengthSane(raw: String, corrected: String, multiplier: Int): Boolean {
            val rawLen = raw.trim().length
            if (rawLen <= 0) return false
            return corrected.trim().length <= rawLen * multiplier
        }
    }
}
